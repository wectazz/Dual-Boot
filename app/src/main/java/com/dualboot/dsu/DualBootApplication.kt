package com.dualboot.dsu

import android.app.Application
import com.topjohnwu.superuser.Shell

class DualBootApplication : Application() {
    init {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}
