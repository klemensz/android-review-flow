package com.zleptnig.reviewflow.core

/**
 * Supplies the current user-visible app version for once-per-version rules.
 *
 * Returning `null` deliberately bypasses the once-per-version check.
 */
interface AppVersionProvider {
    fun versionName(): String?
}
