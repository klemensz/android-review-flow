package com.zleptnig.reviewflow.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

internal class UserDefaultsReviewStateStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ReviewStateStore {
    private val mutex = Mutex()

    override suspend fun read(): ReviewSnapshot = mutex.withLock {
        readUnlocked()
    }

    override suspend fun update(transform: (ReviewSnapshot) -> ReviewSnapshot) {
        mutex.withLock {
            writeUnlocked(transform(readUnlocked()))
        }
    }

    private fun readUnlocked(): ReviewSnapshot {
        return ReviewSnapshot(
            appStarts = defaults.integerForKey(Keys.APP_STARTS).toInt(),
            successMoments = defaults.integerForKey(Keys.SUCCESS_MOMENTS).toInt(),
            lastAttemptEpochMs = defaults.integerForKey(Keys.LAST_ATTEMPT_EPOCH_MS),
            lastRequestCompletedVersion = defaults.stringForKey(Keys.LAST_REQUEST_COMPLETED_VERSION),
        )
    }

    private fun writeUnlocked(snapshot: ReviewSnapshot) {
        defaults.setInteger(snapshot.appStarts.toLong(), Keys.APP_STARTS)
        defaults.setInteger(snapshot.successMoments.toLong(), Keys.SUCCESS_MOMENTS)
        defaults.setInteger(snapshot.lastAttemptEpochMs, Keys.LAST_ATTEMPT_EPOCH_MS)
        val version = snapshot.lastRequestCompletedVersion
        if (version == null) {
            defaults.removeObjectForKey(Keys.LAST_REQUEST_COMPLETED_VERSION)
        } else {
            defaults.setObject(version, Keys.LAST_REQUEST_COMPLETED_VERSION)
        }
    }

    private object Keys {
        const val APP_STARTS = "com.zleptnig.reviewflow.app_starts"
        const val SUCCESS_MOMENTS = "com.zleptnig.reviewflow.success_moments"
        const val LAST_ATTEMPT_EPOCH_MS = "com.zleptnig.reviewflow.last_attempt_epoch_ms"
        const val LAST_REQUEST_COMPLETED_VERSION =
            "com.zleptnig.reviewflow.last_request_completed_version"
    }
}
