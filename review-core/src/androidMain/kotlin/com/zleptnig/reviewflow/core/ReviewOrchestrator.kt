package com.zleptnig.reviewflow.core

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android compatibility facade for the original ReviewFlow API.
 *
 * - Coroutine/Flow-first
 * - Single-flight guarded
 * - Rule engine + persistence
 * - Emits StateFlow + SharedFlow events
 */
class ReviewOrchestrator internal constructor(
    private val store: ReviewStateStore,
    private val client: ReviewClient,
    private val rules: ReviewRules,
    private val versionProvider: AppVersionProvider,
    private val clock: Clock,
) {
    internal constructor(
        store: ReviewStore,
        client: ReviewClient,
        rules: ReviewRules,
        versionProvider: AppVersionProvider,
        clock: Clock,
    ) : this(
        store = store as ReviewStateStore,
        client = client,
        rules = rules,
        versionProvider = versionProvider,
        clock = clock,
    )

    private val _state = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReviewEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val runner = ReviewAttemptRunner(
        store = store,
        rules = rules,
        versionProvider = versionProvider,
        clock = clock,
    )
    private val observer = AndroidCompatibilityObserver()

    /**
     * Call once per cold start (or per "session").
     */
    suspend fun onAppStart() {
        runner.onAppStart()
    }

    /**
     * Call after meaningful positive actions (e.g., checkout complete, trip saved, milestone).
     */
    suspend fun onSuccessMoment() {
        runner.onSuccessMoment()
    }

    /**
     * Checks rules and tries to submit the Play Core review request.
     * Returns true only if the Play Core flow completed successfully.
     *
     * **`oncePerVersion` and null version names:** If [AppVersionProvider.versionName] returns
     * `null` (e.g. the package name is not found), the once-per-version check is bypassed and
     * the review may be requested again.
     *
     * A successful result and [ReviewEvent.Shown] do not guarantee that a dialog was visible.
     */
    suspend fun tryShow(activity: Activity): Boolean {
        return runner.tryRequest(
            attempt = AndroidReviewAttempt(
                client = client,
                activity = activity,
                onReady = { reviewInfo ->
                    _state.value = ReviewState.Ready(reviewInfo)
                },
                onPresenting = {
                    _state.value = ReviewState.Showing
                },
            ),
            observer = observer,
        )
    }

    private inner class AndroidCompatibilityObserver : ReviewAttemptObserver {
        override fun onAttempted() {
            _events.tryEmit(ReviewEvent.Attempted)
        }

        override fun onSkipped(reason: SkipReason) {
            _events.tryEmit(ReviewEvent.Skipped(reason))
        }

        override fun onRequesting() {
            _state.value = ReviewState.Loading
        }

        override fun onCompleted() {
            _events.tryEmit(ReviewEvent.Shown)
            _state.value = ReviewState.Done
        }

        override fun onUnavailable() = Unit

        override fun onFailed(throwable: Throwable) {
            _events.tryEmit(ReviewEvent.Failed(throwable))
            _state.value = ReviewState.Error(throwable)
        }

        override fun onCancelled() {
            _state.value = ReviewState.Idle
        }
    }

    companion object {
        /**
         * Factory for production Android usage.
         */
        fun create(
            context: android.content.Context,
            rules: ReviewRules = ReviewRules(),
            client: ReviewClient = PlayCoreReviewClient(context.applicationContext),
            versionProvider: AppVersionProvider = DefaultAppVersionProvider(context.applicationContext),
            clock: Clock = SystemClock,
        ): ReviewOrchestrator {
            return ReviewOrchestrator(
                store = ReviewStore(context.applicationContext),
                client = client,
                rules = rules,
                versionProvider = versionProvider,
                clock = clock,
            )
        }
    }
}

private class AndroidReviewAttempt(
    private val client: ReviewClient,
    private val activity: Activity,
    private val onReady: (ReviewInfo) -> Unit,
    private val onPresenting: () -> Unit,
) : ReviewAttempt {
    override suspend fun request(): ReviewPresentationResult {
        val reviewInfo = client.requestReviewInfo()
        onReady(reviewInfo)
        onPresenting()
        client.launchReviewFlow(activity, reviewInfo)
        return ReviewPresentationResult.Completed
    }
}
