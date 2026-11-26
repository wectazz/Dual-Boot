package com.dualboot.dsu.util

import org.lsposed.hiddenapibypass.HiddenApiBypass

object DevicePropUtils {
    fun getGsidBinaryAllowedPerc(): Float {
        val minAllowed = getSystemProperty("ro.vegabobo.dsusideloader.gsid_min_alloc")
        return if (minAllowed.isNotEmpty()) minAllowed.toFloatOrNull() ?: 0.40F else 0.40F
    }

    fun hasDynamicPartitions(): Boolean {
        return getSystemProperty("ro.boot.dynamic_partitions") == "true"
    }

    fun getSystemProperty(key: String): String {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val value = HiddenApiBypass.invoke(systemPropertiesClass, null, "get", key)
            value?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
