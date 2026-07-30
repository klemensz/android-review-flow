package com.zleptnig.reviewflow.core

import platform.Foundation.NSBundle

internal class IosAppVersionProvider(
    private val bundle: NSBundle = NSBundle.mainBundle,
) : AppVersionProvider {
    override fun versionName(): String? {
        return bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    }
}
