package com.zleptnig.reviewflow.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class ReviewFlowTest {
    @Test
    fun skipsWhenAppStartsAreBelowThreshold() = runTest {
        var presenterCalled = false
        val flow = newFlow(
            presenter = presenter {
                presenterCalled = true
                ReviewPresentationResult.Completed
            },
            rules = rules(minAppStarts = 1),
        )

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertEquals(
            listOf(
                ReviewFlowEvent.Attempted,
                ReviewFlowEvent.Skipped(SkipReason.NotEnoughAppStarts),
            ),
            events,
        )
        assertFalse(presenterCalled)
    }

    @Test
    fun skipsWhenSuccessMomentsAreBelowThreshold() = runTest {
        val flow = newFlow(rules = rules(minSuccessMoments = 1))

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertTrue(events.contains(ReviewFlowEvent.Skipped(SkipReason.NotEnoughSuccessMoments)))
    }

    @Test
    fun skipsWhenRequestCompletedForCurrentVersion() = runTest {
        val store = InMemoryReviewStateStore(
            ReviewSnapshot(lastRequestCompletedVersion = "2.0"),
        )
        val flow = newFlow(
            store = store,
            versionProvider = versionProvider("2.0"),
            rules = rules(oncePerVersion = true),
        )

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertTrue(events.contains(ReviewFlowEvent.Skipped(SkipReason.AlreadyShownForThisVersion)))
    }

    @Test
    fun nullVersionBypassesOncePerVersion() = runTest {
        val store = InMemoryReviewStateStore(
            ReviewSnapshot(lastRequestCompletedVersion = "2.0"),
        )
        val flow = newFlow(
            store = store,
            versionProvider = versionProvider(null),
            rules = rules(oncePerVersion = true),
        )

        assertTrue(flow.tryRequest())
    }

    @Test
    fun cooldownIsActiveBeforeBoundary() = runTest {
        val store = InMemoryReviewStateStore(
            ReviewSnapshot(lastAttemptEpochMs = 1_000L),
        )
        val flow = newFlow(
            store = store,
            rules = rules(cooldown = 1.days),
            clock = fixedClock(1_001L),
        )

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertTrue(events.contains(ReviewFlowEvent.Skipped(SkipReason.CooldownActive)))
    }

    @Test
    fun cooldownAllowsRequestAtExactBoundary() = runTest {
        val cooldown = 1.days
        val store = InMemoryReviewStateStore(
            ReviewSnapshot(lastAttemptEpochMs = 1_000L),
        )
        val flow = newFlow(
            store = store,
            rules = rules(cooldown = cooldown),
            clock = fixedClock(1_000L + cooldown.inWholeMilliseconds),
        )

        assertTrue(flow.tryRequest())
    }

    @Test
    fun completedRequestPersistsVersionAndEmitsCompletion() = runTest {
        val store = InMemoryReviewStateStore()
        val flow = newFlow(
            store = store,
            versionProvider = versionProvider("2.0"),
        )

        val events = captureEvents(flow) { assertTrue(flow.tryRequest()) }

        assertEquals("2.0", store.read().lastRequestCompletedVersion)
        assertTrue(events.contains(ReviewFlowEvent.RequestCompleted))
        assertIs<ReviewFlowState.Done>(flow.state.value)
    }

    @Test
    fun unavailableRequestDoesNotStartCooldownOrCompleteVersion() = runTest {
        val store = InMemoryReviewStateStore(
            ReviewSnapshot(lastAttemptEpochMs = 100L),
        )
        var available = false
        val flow = newFlow(
            presenter = presenter {
                if (available) {
                    ReviewPresentationResult.Completed
                } else {
                    ReviewPresentationResult.Unavailable
                }
            },
            store = store,
            versionProvider = versionProvider("2.0"),
            clock = fixedClock(4_200L),
        )

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertEquals(100L, store.read().lastAttemptEpochMs)
        assertEquals(null, store.read().lastRequestCompletedVersion)
        assertTrue(events.contains(ReviewFlowEvent.RequestUnavailable))
        assertIs<ReviewFlowState.Unavailable>(flow.state.value)

        available = true
        assertTrue(flow.tryRequest())
        assertEquals(4_200L, store.read().lastAttemptEpochMs)
        assertEquals("2.0", store.read().lastRequestCompletedVersion)
    }

    @Test
    fun failedResultEmitsFailureAndSetsErrorState() = runTest {
        val error = IllegalStateException("boom")
        val store = InMemoryReviewStateStore()
        val flow = newFlow(
            presenter = presenter { ReviewPresentationResult.Failed(error) },
            store = store,
            clock = fixedClock(4_200L),
        )

        val events = captureEvents(flow) { assertFalse(flow.tryRequest()) }

        assertTrue(events.any { it is ReviewFlowEvent.Failed && it.throwable === error })
        assertTrue((flow.state.value as ReviewFlowState.Error).throwable === error)
        assertEquals(4_200L, store.read().lastAttemptEpochMs)
    }

    @Test
    fun cancellationIsRethrownAndResetsState() = runTest {
        val flow = newFlow(
            presenter = presenter { throw CancellationException("cancelled") },
        )
        val events = mutableListOf<ReviewFlowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            flow.events.collect { events += it }
        }

        assertFailsWith<CancellationException> { flow.tryRequest() }
        collector.cancel()

        assertFalse(events.any { it is ReviewFlowEvent.Failed })
        assertIs<ReviewFlowState.Idle>(flow.state.value)
    }

    @Test
    fun concurrentRequestIsRejectedWithoutWaiting() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val flow = newFlow(
            presenter = presenter {
                entered.complete(Unit)
                release.await()
                ReviewPresentationResult.Completed
            },
        )
        val events = mutableListOf<ReviewFlowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            flow.events.collect { events += it }
        }

        val first = async { flow.tryRequest() }
        entered.await()
        assertFalse(flow.tryRequest())
        release.complete(Unit)
        assertTrue(first.await())
        collector.cancel()

        assertTrue(events.contains(ReviewFlowEvent.Skipped(SkipReason.InFlight)))
    }

    @Test
    fun counterUpdatesUseAtomicStoreTransform() = runTest {
        val store = InMemoryReviewStateStore()
        val flow = newFlow(store = store)

        coroutineScope {
            repeat(20) {
                launch { flow.onAppStart() }
                launch { flow.onSuccessMoment() }
            }
        }

        assertEquals(20, store.read().appStarts)
        assertEquals(20, store.read().successMoments)
    }

    private fun newFlow(
        presenter: ReviewPresenter = presenter { ReviewPresentationResult.Completed },
        store: ReviewStateStore = InMemoryReviewStateStore(),
        versionProvider: AppVersionProvider = versionProvider(null),
        rules: ReviewRules = rules(),
        clock: Clock = fixedClock(1_000L),
    ): ReviewFlow {
        return ReviewFlow(
            presenter = presenter,
            store = store,
            versionProvider = versionProvider,
            rules = rules,
            clock = clock,
        )
    }

    private fun rules(
        minAppStarts: Int = 0,
        minSuccessMoments: Int = 0,
        cooldown: Duration = Duration.ZERO,
        oncePerVersion: Boolean = false,
    ) = ReviewRules(
        minAppStarts = minAppStarts,
        minSuccessMoments = minSuccessMoments,
        cooldown = cooldown,
        oncePerVersion = oncePerVersion,
    )

    private fun presenter(
        request: suspend () -> ReviewPresentationResult,
    ): ReviewPresenter = object : ReviewPresenter {
        override suspend fun requestReview(): ReviewPresentationResult = request()
    }

    private fun versionProvider(version: String?): AppVersionProvider {
        return object : AppVersionProvider {
            override fun versionName(): String? = version
        }
    }

    private fun fixedClock(now: Long): Clock = object : Clock {
        override fun nowEpochMs(): Long = now
    }

    private suspend fun captureEvents(
        flow: ReviewFlow,
        block: suspend () -> Unit,
    ): List<ReviewFlowEvent> = coroutineScope {
        val events = mutableListOf<ReviewFlowEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            flow.events.collect { events += it }
        }
        try {
            block()
            yield()
        } finally {
            collector.cancel()
        }
        events
    }
}

private class InMemoryReviewStateStore(
    initial: ReviewSnapshot = ReviewSnapshot(),
) : ReviewStateStore {
    private val mutex = Mutex()
    private var snapshot = initial

    override suspend fun read(): ReviewSnapshot = mutex.withLock { snapshot }

    override suspend fun update(transform: (ReviewSnapshot) -> ReviewSnapshot) {
        mutex.withLock {
            snapshot = transform(snapshot)
        }
    }
}
