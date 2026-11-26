package com.dualboot.dsu.installer.root

import android.app.Application
import android.gsi.IGsiService
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.Log
import com.dualboot.dsu.model.DSUInstallationSource
import com.dualboot.dsu.model.InstallationStep
import com.dualboot.dsu.model.SourceType
import com.dualboot.dsu.service.PrivilegedProvider
import com.dualboot.dsu.util.FilenameUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class DSUInstaller(
    private val application: Application,
    private val userdataSize: Long,
    private val sourceUri: Uri,
    private val fileName: String,
    private val fileSize: Long,
    private val installationJob: Job = Job(),
    private val onError: (message: String) -> Unit,
    private val onProgressUpdate: (progress: Float, partition: String) -> Unit,
    private val onStepUpdate: (step: InstallationStep, detail: String) -> Unit,
    private val onSuccess: () -> Unit,
) : DynamicSystemImpl() {

    private val tag = "DSUInstaller"

    private object Constants {
        const val DEFAULT_SLOT = "dsu"
        const val SHARED_MEM_SIZE = 524288
        const val MIN_PROGRESS_TO_PUBLISH = (1L shl 27)
    }

    private class MappedMemoryBuffer(var buffer: ByteBuffer?) : AutoCloseable {
        override fun close() {
            if (buffer != null) {
                SharedMemory.unmap(buffer!!)
                buffer = null
            }
        }
    }

    private val unsupportedPartitions = setOf(
        "vbmeta", "boot", "userdata", "dtbo", "super_empty", "system_other", "scratch"
    )

    private fun isPartitionSupported(partitionName: String): Boolean =
        partitionName !in unsupportedPartitions

    private fun getFdDup(sharedMemory: SharedMemory): ParcelFileDescriptor {
        return HiddenApiBypass.invoke(
            sharedMemory.javaClass,
            sharedMemory,
            "getFdDup",
        ) as ParcelFileDescriptor
    }

    private fun shouldInstallEntry(name: String): Boolean {
        if (!name.endsWith(".img")) return false
        val partitionName = name.substringBeforeLast(".img").substringAfterLast("/")
        return isPartitionSupported(partitionName)
    }

    private fun publishProgress(bytesRead: Long, totalBytes: Long, partition: String) {
        val progress = if (totalBytes > 0L && bytesRead > 0L) {
            (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
        onProgressUpdate(progress, partition)
    }

    private fun createNewPartition(partition: String, partitionSize: Long, readOnly: Boolean): Boolean {
        val result = createPartition(partition, partitionSize, readOnly)
        if (result != IGsiService.INSTALL_OK) {
            Log.e(tag, "Failed to create $partition partition, error code: $result")
            installationJob.cancel()
            onError("Failed to create $partition partition (code $result)")
            return false
        }
        return true
    }

    private fun installWritablePartition(partition: String, partitionSize: Long) {
        onStepUpdate(InstallationStep.CREATING_PARTITION, partition)
        val job = Job()
        CoroutineScope(Dispatchers.IO + job).launch {
            createNewPartition(partition, partitionSize, false)
            job.complete()
        }
        publishProgress(0L, partitionSize, partition)
        var prevInstalledSize = 0L
        while (job.isActive) {
            if (installationJob.isCancelled) return
            val installedSize = installationProgress.bytes_processed
            if (installedSize > prevInstalledSize + Constants.MIN_PROGRESS_TO_PUBLISH) {
                prevInstalledSize = installedSize
                publishProgress(installedSize, partitionSize, partition)
            }
            runBlocking { delay(100) }
        }
        if (!closePartition()) {
            onError("Failed to close $partition partition")
            return
        }
        publishProgress(partitionSize, partitionSize, partition)
    }

    private fun installImage(
        partition: String,
        uncompressedSize: Long,
        inputStream: InputStream,
        readOnly: Boolean = true,
    ) {
        val sis = SparseInputStream(BufferedInputStream(inputStream))
        val partitionSize = if (sis.unsparseSize != -1L) sis.unsparseSize else uncompressedSize
        onStepUpdate(InstallationStep.CREATING_PARTITION, partition)
        if (!createNewPartition(partition, partitionSize, readOnly)) return

        onStepUpdate(InstallationStep.INSTALLING, partition)
        SharedMemory.create("dsu_buffer_$partition", Constants.SHARED_MEM_SIZE).use { sharedMemory ->
            MappedMemoryBuffer(sharedMemory.mapReadWrite()).use { mapped ->
                val fdDup = getFdDup(sharedMemory)
                setAshmem(fdDup, sharedMemory.size.toLong())
                publishProgress(0L, partitionSize, partition)
                var installedSize = 0L
                val readBuffer = ByteArray(sharedMemory.size)
                val buffer = mapped.buffer!!
                var numBytesRead: Int
                while (sis.read(readBuffer, 0, readBuffer.size).also { numBytesRead = it } > 0) {
                    if (installationJob.isCancelled) return
                    buffer.position(0)
                    buffer.put(readBuffer, 0, numBytesRead)
                    submitFromAshmem(numBytesRead.toLong())
                    installedSize += numBytesRead.toLong()
                    publishProgress(installedSize, partitionSize, partition)
                }
                publishProgress(partitionSize, partitionSize, partition)
            }
        }

        if (!closePartition()) {
            onError("Failed to close $partition partition")
            return
        }
        Log.d(tag, "Partition $partition installed ($partitionSize bytes)")
    }

    private fun installStreamingZip(inputStream: InputStream) {
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                if (shouldInstallEntry(entryName)) {
                    val partName = entryName.substringBeforeLast(".img").substringAfterLast("/")
                    installImage(partName, entry!!.size, zis)
                }
                if (installationJob.isCancelled) break
            }
        }
    }

    private fun decompressToCache(ext: String): File {
        onStepUpdate(InstallationStep.DECOMPRESSING, fileName)
        val tempFile = File(application.cacheDir, "dsu_unpacked.img")
        if (tempFile.exists()) tempFile.delete()
        val rawStream = application.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("Cannot open selected file")
        val compressedStream: InputStream = when (ext) {
            "xz" -> XZCompressorInputStream(BufferedInputStream(rawStream))
            "gz", "gzip" -> GzipCompressorInputStream(BufferedInputStream(rawStream))
            else -> throw IllegalArgumentException("Unsupported archive: $ext")
        }
        val totalCompressed = if (fileSize > 0) fileSize else 1L
        compressedStream.use { input ->
            tempFile.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    if (installationJob.isCancelled) break
                    output.write(buf, 0, read)
                    val count = when (input) {
                        is XZCompressorInputStream -> input.compressedCount
                        is GzipCompressorInputStream -> input.compressedCount
                        else -> 0L
                    }
                    publishProgress(count, totalCompressed, fileName)
                }
            }
        }
        return tempFile
    }

    fun execute() {
        var tempFile: File? = null
        try {
            PrivilegedProvider.getService().setDynProp()
            if (isInUse) {
                onError("A DSU system is currently running. Reboot to primary OS first.")
                return
            }
            if (isInstalled) {
                remove()
                forceStopDSU()
            }

            val ext = fileName.substringAfterLast('.', "").lowercase()
            val dsuSource: DSUInstallationSource = when (ext) {
                "img" -> DSUInstallationSource.SingleSystemImage(sourceUri, fileSize)
                "zip" -> DSUInstallationSource.DsuPackage(sourceUri)
                "xz", "gz", "gzip" -> {
                    tempFile = decompressToCache(ext)
                    if (installationJob.isCancelled) {
                        onError("Installation cancelled")
                        return
                    }
                    DSUInstallationSource.SingleSystemImage(Uri.fromFile(tempFile), tempFile.length())
                }
                else -> {
                    onError("Unsupported file format: .$ext")
                    return
                }
            }

            forceStopDSU()
            if (!startInstallation(Constants.DEFAULT_SLOT)) {
                onError("Failed to start DSU installation")
                return
            }

            installWritablePartition("userdata", userdataSize)
            if (installationJob.isCancelled) {
                remove()
                return
            }

            when (dsuSource.type) {
                SourceType.SINGLE_SYSTEM_IMAGE -> {
                    val stream = if (tempFile != null) {
                        FileInputStream(tempFile)
                    } else {
                        application.contentResolver.openInputStream(dsuSource.uri)!!
                    }
                    stream.use { installImage("system", dsuSource.fileSize, it) }
                }
                SourceType.DSU_PACKAGE -> {
                    application.contentResolver.openInputStream(dsuSource.uri)!!.use {
                        installStreamingZip(it)
                    }
                }
                else -> {}
            }

            if (installationJob.isCancelled) {
                remove()
                onError("Installation cancelled")
                return
            }

            onStepUpdate(InstallationStep.FINISHING, "dsu")
            if (!finishInstallation()) {
                onError("Failed to finalize DSU installation")
                return
            }
            onStepUpdate(InstallationStep.SUCCESS, "dsu")
            onSuccess()
        } catch (e: Exception) {
            Log.e(tag, "DSU installation failed", e)
            runCatching { remove() }
            onError(e.message ?: "Unknown installation error")
        } finally {
            tempFile?.delete()
        }
    }
}
