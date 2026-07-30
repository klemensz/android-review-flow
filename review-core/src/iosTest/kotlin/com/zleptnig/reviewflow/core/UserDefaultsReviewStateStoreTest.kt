package com.zleptnig.reviewflow.core

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class UserDefaultsReviewStateStoreTest {
    @Test
    fun roundTripsSnapshotInIsolatedSuite() = runTest {
        val suiteName = "com.zleptnig.reviewflow.test.${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        val store = UserDefaultsReviewStateStore(defaults)

        try {
            store.update {
                ReviewSnapshot(
                    appStarts = 4,
                    successMoments = 2,
                    lastAttemptEpochMs = 9_000L,
                    lastRequestCompletedVersion = "2.0",
                )
            }

            assertEquals(
                ReviewSnapshot(
                    appStarts = 4,
                    successMoments = 2,
                    lastAttemptEpochMs = 9_000L,
                    lastRequestCompletedVersion = "2.0",
                ),
                store.read(),
            )
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun removesCompletedVersionWhenTransformClearsIt() = runTest {
        val suiteName = "com.zleptnig.reviewflow.test.${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        val store = UserDefaultsReviewStateStore(defaults)

        try {
            store.update { it.copy(lastRequestCompletedVersion = "2.0") }
            store.update { it.copy(lastRequestCompletedVersion = null) }

            assertEquals(null, store.read().lastRequestCompletedVersion)
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }
}
