package com.dualboot.dsu.util

import android.os.Build
import com.topjohnwu.superuser.Shell

data class OsEntryInfo(
    val name: String,
    val versionLine: String,
    val isPrimary: Boolean,
)

object SystemInfoUtils {

    fun getPrimaryOsInfo(): OsEntryInfo {
        val hyperOsVer = DevicePropUtils.getSystemProperty("ro.mi.os.version.name")
        val miuiVer = DevicePropUtils.getSystemProperty("ro.build.version.incremental")
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val osName = when {
            hyperOsVer.isNotEmpty() -> "Xiaomi HyperOS"
            miuiVer.isNotEmpty() && brand.equals("Xiaomi", ignoreCase = true) -> "MIUI"
            else -> "$brand OS"
        }
        val verCode = when {
            hyperOsVer.isNotEmpty() -> hyperOsVer.removePrefix("OS")
            else -> Build.DISPLAY.take(16)
        }
        val androidVer = "Android ${Build.VERSION.RELEASE}"
        return OsEntryInfo(
            name = osName,
            versionLine = "$verCode · $androidVer",
            isPrimary = true,
        )
    }

    fun getSecondaryOsLabel(): OsEntryInfo {
        // Inspect /data/gsi/dsu metadata via root if available
        val result = Shell.cmd("ls -1 /data/gsi/dsu 2>/dev/null").exec()
        val hasDsuFiles = result.isSuccess && result.out.isNotEmpty()
        val desc = if (hasDsuFiles) "Dynamic System (DSU)" else "DSU Secondary Slot"
        return OsEntryInfo(
            name = desc,
            versionLine = "GSI · Dynamic Partition",
            isPrimary = false,
        )
    }

    fun getDsuUsedBytes(): Long {
        val res = Shell.cmd("du -sb /data/gsi/dsu 2>/dev/null | cut -f1").exec()
        if (res.isSuccess && res.out.isNotEmpty()) {
            return res.out.first().trim().toLongOrNull() ?: 0L
        }
        return 0L
    }
}
