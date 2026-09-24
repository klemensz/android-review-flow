package com.zleptnig.reviewflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class UserDefaultsReviewStateStoreTest {
    @Test
    fun counterUpdatesAcrossStoreInstancesAreAtomic() = runTest {
        val suiteName = "com.zleptnig.reviewflow.test.${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        val stores = List(8) { UserDefaultsReviewStateStore(defaults) }

        try {
            coroutineScope {
                stores.map { store ->
                    async(Dispatchers.Default) {
                        repeat(100) { store.update { it.copy(appStarts = it.appStarts + 1) } }
                    }
                }.awaitAll()
            }

            assertEquals(800, stores.first().read().appStarts)
        } finally {
            defaults.removePersistentDomainForName(suiteName)
        }
    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun unavailableAndCompletedRequestsSurviveFlowRecreation() = runTest {
        // The native test runner blocks the actual main queue. Keep the presenter's
        // Main dispatch deterministic while testing persistence and rule behavior.
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val suiteName = "com.zleptnig.reviewflow.test.${NSUUID().UUIDString}"
        val defaults = requireNotNull(NSUserDefaults(suiteName = suiteName))
        defaults.removePersistentDomainForName(suiteName)
        var available = false
        var platformCalls = 0
        val request = object : IosReviewRequest {
            override fun requestReview(): Boolean {
                platformCalls++
                return available
            }
        }
        val rules = ReviewRules(
            minAppStarts = 1,
            minSuccessMoments = 1,
            cooldown = 1.days,
            oncePerVersion = true,
        )
        val version = object : AppVersionProvider {
            override fun versionName(): String = "2.0"
        }
        val clock = object : Clock {
            override fun nowEpochMs(): Long = 1_000L
        }
        fun newFlow() = ReviewFlow(
            presenter = IosStoreKitPresenter(request),
            store = UserDefaultsReviewStateStore(defaults),
            versionProvider = version,
            rules = rules,
            clock = clock,
        )

        try {
            val first = newFlow()
            first.onAppStart()
            first.onSuccessMoment()
            assertFalse(first.tryRequest())
            assertIs<ReviewFlowState.Unavailable>(first.state.value)
            assertEquals(0L, UserDefaultsReviewStateStore(defaults).read().lastAttemptEpochMs)

            available = true
            val recreated = newFlow()
            assertTrue(recreated.tryRequest())
            assertEquals("2.0", UserDefaultsReviewStateStore(defaults).read().lastRequestCompletedVersion)

            assertFalse(newFlow().tryRequest())
            assertEquals(2, platformCalls)
        } finally {
            defaults.removePersistentDomainForName(suiteName)
            Dispatchers.resetMain()
        }
    }
}
