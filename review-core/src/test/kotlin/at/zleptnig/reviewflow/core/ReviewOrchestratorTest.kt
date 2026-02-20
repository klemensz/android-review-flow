package at.zleptnig.reviewflow.core

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.review.ReviewInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
class ReviewOrchestratorTest {

    @Test
    fun `tryShow skips when app starts are below threshold`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val snapshot = store.getSnapshot()
        var requestCalled = false
        val orchestrator = newOrchestrator(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    requestCalled = true
                    throw IllegalStateException("must not be called")
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
            },
            store = store,
            rules = ReviewRules(
                minAppStarts = snapshot.appStarts + 1,
                minSuccessMoments = 0,
                cooldown = Duration.ZERO,
                oncePerVersion = false,
            ),
        )

        val events = captureEvents(orchestrator) { orchestrator.tryShow(newActivity()) }

        assertTrue(events.contains(ReviewEvent.Attempted))
        assertTrue(events.contains(ReviewEvent.Skipped(SkipReason.NotEnoughAppStarts)))
        assertFalse(requestCalled)
    }

    @Test
    fun `tryShow skips when success moments are below threshold`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val snapshot = store.getSnapshot()
        var requestCalled = false
        val orchestrator = newOrchestrator(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    requestCalled = true
                    throw IllegalStateException("must not be called")
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
            },
            store = store,
            rules = ReviewRules(
                minAppStarts = 0,
                minSuccessMoments = snapshot.successMoments + 1,
                cooldown = Duration.ZERO,
                oncePerVersion = false,
            ),
        )

        val events = captureEvents(orchestrator) { orchestrator.tryShow(newActivity()) }

        assertTrue(events.contains(ReviewEvent.Attempted))
        assertTrue(events.contains(ReviewEvent.Skipped(SkipReason.NotEnoughSuccessMoments)))
        assertFalse(requestCalled)
    }

    @Test
    fun `tryShow skips when once per version already shown`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val version = "1.0-test"
        store.markShown(version)
        var requestCalled = false
        val orchestrator = newOrchestrator(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    requestCalled = true
                    throw IllegalStateException("must not be called")
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
            },
            store = store,
            rules = ReviewRules(
                minAppStarts = 0,
                minSuccessMoments = 0,
                cooldown = Duration.ZERO,
                oncePerVersion = true,
            ),
            versionProvider = object : AppVersionProvider {
                override fun versionName(): String = version
            },
        )

        val events = captureEvents(orchestrator) { orchestrator.tryShow(newActivity()) }

        assertTrue(events.contains(ReviewEvent.Attempted))
        assertTrue(events.contains(ReviewEvent.Skipped(SkipReason.AlreadyShownForThisVersion)))
        assertFalse(requestCalled)
    }

    @Test
    fun `tryShow skips when cooldown is active`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        store.markAttempt(1_000L)
        var requestCalled = false
        val orchestrator = newOrchestrator(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    requestCalled = true
                    throw IllegalStateException("must not be called")
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
            },
            store = store,
            rules = ReviewRules(
                minAppStarts = 0,
                minSuccessMoments = 0,
                cooldown = 1.days,
                oncePerVersion = false,
            ),
            clock = object : Clock {
                override fun nowEpochMs(): Long = 1_001L
            },
        )

        val events = captureEvents(orchestrator) { orchestrator.tryShow(newActivity()) }

        assertTrue(events.contains(ReviewEvent.Attempted))
        assertTrue(events.contains(ReviewEvent.Skipped(SkipReason.CooldownActive)))
        assertFalse(requestCalled)
    }

    @Test
    fun `tryShow does not skip when elapsed time exactly equals cooldown`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val cooldown = 1.days
        store.markAttempt(1_000L)
        val orchestrator = newOrchestrator(
            client = stubbedClient(),
            store = store,
            rules = ReviewRules(
                minAppStarts = 0,
                minSuccessMoments = 0,
                cooldown = cooldown,
                oncePerVersion = false,
            ),
            clock = object : Clock {
                override fun nowEpochMs(): Long = 1_000L + cooldown.inWholeMilliseconds
            },
        )

        val events = captureEvents(orchestrator) { orchestrator.tryShow(newActivity()) }

        assertFalse(events.any { it == ReviewEvent.Skipped(SkipReason.CooldownActive) })
    }

    @Test
    fun `tryShow emits failed and sets error state on non-cancellation errors`() = runTest {
        val clientError = IllegalStateException("boom")
        val client = object : ReviewClient {
            override suspend fun requestReviewInfo(): ReviewInfo = throw clientError
            override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
        }
        val orchestrator = newOrchestrator(client = client)

        val events = captureEvents(orchestrator) {
            val result = orchestrator.tryShow(newActivity())
            assertFalse(result)
        }

        assertTrue(events.any { it is ReviewEvent.Failed && it.throwable === clientError })
        assertTrue(orchestrator.state.value is ReviewState.Error)
    }

    @Test
    fun `onAppStart persists counter`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val before = store.getSnapshot().appStarts
        val orchestrator = newOrchestrator(
            client = stubbedClient(),
            store = store,
        )

        orchestrator.onAppStart()

        val after = store.getSnapshot().appStarts
        assertEquals(before + 1, after)
    }

    @Test
    fun `onSuccessMoment persists counter`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        val before = store.getSnapshot().successMoments
        val orchestrator = newOrchestrator(
            client = stubbedClient(),
            store = store,
        )

        orchestrator.onSuccessMoment()

        val after = store.getSnapshot().successMoments
        assertEquals(before + 1, after)
    }

    @Test
    fun `tryShow rethrows cancellation exceptions`() = runTest {
        val client = object : ReviewClient {
            override suspend fun requestReviewInfo(): ReviewInfo {
                throw CancellationException("cancelled")
            }

            override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
        }
        val orchestrator = newOrchestrator(client)
        val events = mutableListOf<ReviewEvent>()
        val collector = launch { orchestrator.events.collect { events += it } }

        try {
            orchestrator.tryShow(newActivity())
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        } finally {
            collector.cancel()
        }

        assertFalse(events.any { it is ReviewEvent.Failed })
        assertTrue(orchestrator.state.value is ReviewState.Idle)
    }

    @Test
    fun `tryShow rejects queued concurrent calls with in-flight skip`() = runTest {
        val enteredRequest = CompletableDeferred<Unit>()
        val releaseRequest = CompletableDeferred<Unit>()
        val client = object : ReviewClient {
            override suspend fun requestReviewInfo(): ReviewInfo {
                enteredRequest.complete(Unit)
                releaseRequest.await()
                throw IllegalStateException("fail first call")
            }

            override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
        }
        val orchestrator = newOrchestrator(client)
        val events = mutableListOf<ReviewEvent>()
        val collector = launch { orchestrator.events.collect { events += it } }

        val first = async { orchestrator.tryShow(newActivity()) }
        enteredRequest.await()

        val second = orchestrator.tryShow(newActivity())
        assertFalse(second)

        releaseRequest.complete(Unit)
        assertFalse(first.await())
        collector.cancel()

        assertTrue(events.any { it == ReviewEvent.Skipped(SkipReason.InFlight) })
    }

    @Test
    fun `ReviewStore has no instance DataStore delegate field`() {
        val fields = ReviewStore::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("dataStore") && it.endsWith("\$delegate") })
    }

    private fun newOrchestrator(
        client: ReviewClient,
        store: ReviewStore = ReviewStore(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
        ),
        rules: ReviewRules = ReviewRules(
            minAppStarts = 0,
            minSuccessMoments = 0,
            cooldown = Duration.ZERO,
            oncePerVersion = false,
        ),
        versionProvider: AppVersionProvider = object : AppVersionProvider {
            override fun versionName(): String? = null
        },
        clock: Clock = object : Clock {
            override fun nowEpochMs(): Long = 1_000L
        },
    ): ReviewOrchestrator {
        return ReviewOrchestrator(
            store = store,
            client = client,
            rules = rules,
            versionProvider = versionProvider,
            clock = clock,
        )
    }

    private suspend fun captureEvents(
        orchestrator: ReviewOrchestrator,
        block: suspend () -> Unit,
    ): List<ReviewEvent> = coroutineScope {
        val events = mutableListOf<ReviewEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            orchestrator.events.collect { events += it }
        }
        try {
            block()
            yield()
        } finally {
            collector.cancel()
        }
        events
    }

    private fun stubbedClient(): ReviewClient = object : ReviewClient {
        override suspend fun requestReviewInfo(): ReviewInfo {
            throw IllegalStateException("not used")
        }

        override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
    }

    private fun newActivity(): Activity {
        return Robolectric.buildActivity(Activity::class.java).setup().get()
    }
}
