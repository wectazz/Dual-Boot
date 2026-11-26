package com.dualboot.dsu.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns

object StorageUtils {

    data class StorageInfo(
        val totalBytes: Long,
        val availableBytes: Long,
        val hasAvailableStorage: Boolean,
        val maxAllocationGb: Int,
    )

    fun getStorageInfo(allowedPercentage: Float = 0.40F): StorageInfo {
        val statFs = StatFs(Environment.getDataDirectory().absolutePath)
        val blockSize = statFs.blockSizeLong
        val totalSize = statFs.blockCountLong * blockSize
        val availableSize = statFs.availableBlocksLong * blockSize
        val hasAvailable = availableSize.toFloat() / totalSize.toFloat() > allowedPercentage
        var availGb = (availableSize / 1024L / 1024L / 1024L).toInt()
        if (availGb >= 6) availGb -= 4
        return StorageInfo(
            totalBytes = totalSize,
            availableBytes = availableSize,
            hasAvailableStorage = hasAvailable,
            maxAllocationGb = (availGb / 2).coerceAtLeast(2),
        )
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            String.format("%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format("%.0f MB", mb)
        }
    }
}

object FilenameUtils {
    fun queryName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getString(idx) ?: "unknown"
            }
        }
        return uri.lastPathSegment ?: "unknown"
    }

    fun querySize(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(idx)
            }
        }
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }
}
