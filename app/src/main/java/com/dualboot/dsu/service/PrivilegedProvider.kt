package com.dualboot.dsu.service

import android.util.Log
import com.dualboot.dsu.IPrivilegedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PrivilegedProvider {
    private const val TAG = "PrivilegedProvider"

    val connection = Connection()

    fun run(
        onFail: () -> Unit = {},
        onConnected: suspend IPrivilegedService.() -> Unit,
    ) {
        fun service() = connection.SERVICE!!
        CoroutineScope(Dispatchers.IO).launch {
            if (isConnected()) {
                onConnected(service())
                return@launch
            }
            var timeout = 0
            while (!isConnected()) {
                timeout += 500
                if (timeout > 20000) {
                    Log.e(TAG, "Root service unavailable.")
                    onFail()
                    return@launch
                }
                delay(500)
            }
            onConnected(service())
        }
    }

    fun getService(): IPrivilegedService {
        var timeout = 0
        while (!isConnected()) {
            timeout += 200
            if (timeout > 20000) {
                throw IllegalStateException("Root service unavailable.")
            }
            Thread.sleep(200)
        }
        return connection.SERVICE!!
    }

    fun isConnected(): Boolean = connection.SERVICE != null
}
