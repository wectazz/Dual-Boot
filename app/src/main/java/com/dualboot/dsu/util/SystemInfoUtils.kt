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
        val customRom = detectCustomRom()
        val oemSkin = if (customRom == null) detectOemSkin() else null
        val (osName, verCode) = customRom ?: oemSkin ?: ("AOSP" to Build.DISPLAY.take(16))
        val androidVer = "Android ${Build.VERSION.RELEASE}"
        return OsEntryInfo(
            name = osName,
            versionLine = "$verCode · $androidVer",
            isPrimary = true,
        )
    }

    private fun detectOemSkin(): Pair<String, String>? {
        val brand = Build.BRAND
        val hyperOsVer = DevicePropUtils.getSystemProperty("ro.mi.os.version.name")
        val miuiVerName = DevicePropUtils.getSystemProperty("ro.miui.ui.version.name")
        val miuiVerCode = DevicePropUtils.getSystemProperty("ro.miui.ui.version.code")
        val isRealXiaomiSkin = miuiVerCode.isNotEmpty() || miuiVerName.isNotEmpty()

        if (hyperOsVer.isNotEmpty() && isRealXiaomiSkin) {
            return "Xiaomi HyperOS" to hyperOsVer.removePrefix("OS")
        }
        if (miuiVerName.isNotEmpty() && brand.equals("Xiaomi", ignoreCase = true)) {
            return "MIUI" to miuiVerName
        }

        // OPlus (ColorOS / OxygenOS / realme UI)
        val oxygenVer = DevicePropUtils.getSystemProperty("ro.oxygen.version")
        if (oxygenVer.isNotEmpty()) {
            return "OxygenOS" to oxygenVer.take(16)
        }
        val realmeUiVer = DevicePropUtils.getSystemProperty("ro.build.version.realmeui")
        if (realmeUiVer.isNotEmpty()) {
            return "realme UI" to realmeUiVer.take(16)
        }
        val oplusVer = DevicePropUtils.getSystemProperty("ro.build.version.oplusrom")
            .ifEmpty { DevicePropUtils.getSystemProperty("ro.build.version.opporom") }
        if (oplusVer.isNotEmpty()) {
            val name = when {
                brand.equals("OnePlus", ignoreCase = true) -> "OxygenOS"
                brand.equals("realme", ignoreCase = true) -> "realme UI"
                else -> "ColorOS"
            }
            return name to oplusVer.take(16)
        }

        // Honor MagicOS / Huawei EMUI
        val magicVer = DevicePropUtils.getSystemProperty("ro.build.version.magic")
        if (magicVer.isNotEmpty()) {
            return "MagicOS" to magicVer.take(16)
        }
        val emuiVer = DevicePropUtils.getSystemProperty("ro.build.version.emui")
        if (emuiVer.isNotEmpty()) {
            return "EMUI" to emuiVer.removePrefix("EmotionUI_").take(16)
        }

        // Vivo OriginOS / Funtouch OS
        val vivoOsName = DevicePropUtils.getSystemProperty("ro.vivo.os.name")
        val vivoOsVer = DevicePropUtils.getSystemProperty("ro.vivo.os.version")
        if (vivoOsVer.isNotEmpty() || vivoOsName.isNotEmpty()) {
            val name = if (vivoOsName.contains("Origin", ignoreCase = true)) "OriginOS" else "Funtouch OS"
            return name to vivoOsVer.ifEmpty { vivoOsName }.take(16)
        }

        // Samsung One UI
        val oneUiVer = DevicePropUtils.getSystemProperty("ro.build.version.oneui")
        if (oneUiVer.isNotEmpty()) {
            val formatted = oneUiVer.toIntOrNull()?.let { code ->
                val major = code / 10000
                val minor = (code % 10000) / 100
                "$major.$minor"
            } ?: oneUiVer.take(16)
            return "One UI" to formatted
        }

        // Nothing OS
        val nothingVer = DevicePropUtils.getSystemProperty("ro.build.version.nothing")
            .ifEmpty { DevicePropUtils.getSystemProperty("ro.nothing.version.id") }
        if (nothingVer.isNotEmpty()) {
            return "Nothing OS" to nothingVer.take(16)
        }

        // Meizu Flyme
        val flymeVer = DevicePropUtils.getSystemProperty("ro.flyme.version.id")
        if (flymeVer.isNotEmpty() || Build.DISPLAY.contains("Flyme", ignoreCase = true)) {
            return "Flyme" to flymeVer.ifEmpty { Build.DISPLAY }.take(16)
        }

        return null
    }

    private fun detectCustomRom(): Pair<String, String>? {
        val romProps = listOf(
            "ro.evolution.version" to "Evolution X",
            "ro.evolution.build.version" to "Evolution X",
            "ro.evolution.display.version" to "Evolution X",
            "org.evolution.version" to "Evolution X",
            "ro.lineage.version" to "LineageOS",
            "ro.lineage.build.version" to "LineageOS",
            "ro.crdroid.version" to "crDroid",
            "ro.pixelos.version" to "PixelOS",
            "ro.rising.version" to "RisingOS",
            "ro.derpfest.version" to "DerpFest",
            "ro.aospa.version" to "Paranoid Android",
        )
        for ((prop, name) in romProps) {
            val valProp = DevicePropUtils.getSystemProperty(prop)
            if (valProp.isNotEmpty()) {
                return name to valProp.take(16)
            }
        }
        val flavor = DevicePropUtils.getSystemProperty("ro.build.flavor").lowercase()
        val display = Build.DISPLAY.lowercase()
        return when {
            "evolution" in flavor || "evolution" in display -> "Evolution X" to Build.DISPLAY.take(16)
            "lineage" in flavor || "lineage" in display -> "LineageOS" to Build.DISPLAY.take(16)
            "aosp" in flavor -> "AOSP" to Build.DISPLAY.take(16)
            else -> null
        }
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
