package com.dualboot.dsu.service

import android.app.IActivityManager
import android.content.Intent
import android.content.pm.IPackageManager
import android.gsi.GsiProgress
import android.gsi.IGsiService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemProperties
import android.os.image.IDynamicSystemService
import android.os.storage.IStorageManager
import android.os.storage.VolumeInfo
import android.util.Log
import com.dualboot.dsu.BuildConfig
import com.dualboot.dsu.IPrivilegedService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.system.exitProcess

class PrivilegedService : IPrivilegedService.Stub() {

    override fun exit() = destroy()

    override fun destroy() {
        exitProcess(0)
    }

    private fun getBinder(service: String): IBinder {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = HiddenApiBypass.invoke(serviceManager, null, "getService", service)
        return binder as IBinder
    }

    private fun setProp(key: String, value: String) {
        try {
            SystemProperties.set(key, value)
        } catch (e: Exception) {
            Log.w(BuildConfig.APPLICATION_ID, e.stackTraceToString())
        }
    }

    override fun setDynProp() {
        setProp("persist.sys.fflag.override.settings_dynamic_system", "true")
    }

    override fun getUid(): Int = Process.myUid()

    private var activityManager: IActivityManager? = null

    private fun requiresActivityManager() {
        if (activityManager == null) {
            activityManager = IActivityManager.Stub.asInterface(getBinder("activity"))
        }
    }

    override fun startActivity(intent: Intent?) {
        requiresActivityManager()
        val callerPackage =
            if (uid == 2000 || uid == 0) "com.android.shell" else BuildConfig.APPLICATION_ID
        if (Build.VERSION.SDK_INT > 29) {
            activityManager!!.startActivityAsUserWithFeature(
                null, callerPackage, null, intent, null, null, null, 0, 0, null, null, 0
            )
        } else {
            activityManager!!.startActivityAsUser(
                null, callerPackage, intent, null, null, null, 0, 0, null, null, 0
            )
        }
    }

    override fun forceStopPackage(packageName: String?) {
        requiresActivityManager()
        activityManager!!.forceStopPackage(packageName, 0)
    }

    private var packageManager: IPackageManager? = null

    private fun requiresPackageManager() {
        if (packageManager == null) {
            packageManager = IPackageManager.Stub.asInterface(getBinder("package"))
        }
    }

    override fun grantPermission(permissionName: String?) {
        requiresPackageManager()
        packageManager!!.grantRuntimePermission(BuildConfig.APPLICATION_ID, permissionName, 0)
    }

    private var storageManager: IStorageManager? = null

    private fun requiresStorageManager() {
        if (storageManager == null) {
            storageManager = IStorageManager.Stub.asInterface(getBinder("mount"))
        }
    }

    override fun getVolumes(): List<VolumeInfo> {
        requiresStorageManager()
        return ArrayList<VolumeInfo>().apply { addAll(storageManager!!.getVolumes(0)) }
    }

    override fun unmount(volId: String?) {
        requiresStorageManager()
        storageManager!!.unmount(volId)
    }

    override fun mount(volId: String?) {
        requiresStorageManager()
        storageManager!!.mount(volId)
    }

    private var dynamicSystem: IDynamicSystemService? = null

    private fun requiresDynamicSystem() {
        if (dynamicSystem == null) {
            dynamicSystem = IDynamicSystemService.Stub.asInterface(getBinder("dynamic_system"))
        }
    }

    override fun closePartition(): Boolean {
        if (Build.VERSION.SDK_INT <= 30) return true
        requiresDynamicSystem()
        return dynamicSystem!!.closePartition()
    }

    override fun finishInstallation(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.finishInstallation()
    }

    override fun getInstallationProgress(): GsiProgress? {
        requiresDynamicSystem()
        return dynamicSystem!!.installationProgress
    }

    override fun abort(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.abort()
    }

    override fun isEnabled(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.isEnabled
    }

    override fun remove(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.remove()
    }

    override fun setEnable(enable: Boolean, oneShot: Boolean): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.setEnable(enable, oneShot)
    }

    override fun startInstallation(dsuSlot: String?): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.startInstallation(dsuSlot)
    }

    override fun createPartition(name: String?, size: Long, readOnly: Boolean): Int {
        requiresDynamicSystem()
        if (Build.VERSION.SDK_INT < 33) {
            val result = HiddenApiBypass.invoke(
                dynamicSystem!!.javaClass,
                dynamicSystem!!,
                "createPartition",
                name,
                size,
                readOnly,
            )
            return if (result as Boolean) IGsiService.INSTALL_OK else IGsiService.INSTALL_ERROR_GENERIC
        }
        return dynamicSystem!!.createPartition(name, size, readOnly)
    }

    override fun setAshmem(fd: ParcelFileDescriptor?, size: Long): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.setAshmem(fd, size)
    }

    override fun submitFromAshmem(bytes: Long): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.submitFromAshmem(bytes)
    }

    override fun suggestScratchSize(): Long {
        requiresDynamicSystem()
        return dynamicSystem!!.suggestScratchSize()
    }

    override fun isInUse(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.isInUse
    }

    override fun isInstalled(): Boolean {
        requiresDynamicSystem()
        return dynamicSystem!!.isInstalled
    }
}
