package com.zleptnig.reviewflow.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

interface AppVersionProvider {
    fun versionName(): String?
}

class DefaultAppVersionProvider(
    private val context: Context,
) : AppVersionProvider {
    override fun versionName(): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
