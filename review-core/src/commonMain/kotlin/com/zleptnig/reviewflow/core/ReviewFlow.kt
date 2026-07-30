package com.zleptnig.reviewflow.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multiplatform in-app review orchestration.
 *
 * Rule checks are completed before [ReviewPresenter] is invoked. A platform attempt starts the
 * cooldown before the presenter runs; [ReviewPresentationResult.Unavailable] restores the previous
 * timestamp because no platform request could start. Concurrent calls are single-flight: callers
 * arriving during an active request return immediately and emit [SkipReason.InFlight].
 */
class ReviewFlow(
    presenter: ReviewPresenter,
    store: ReviewStateStore,
    versionProvider: AppVersionProvider,
    rules: ReviewRules = ReviewRules(),
    clock: Clock = SystemClock,
) {
    private val _state = MutableStateFlow<ReviewFlowState>(ReviewFlowState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReviewFlowEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val runner = ReviewAttemptRunner(
        store = store,
        rules = rules,
        versionProvider = versionProvider,
        clock = clock,
    )
    private val attempt = PresenterReviewAttempt(presenter)
    private val observer = FlowObserver()

    suspend fun onAppStart() {
        runner.onAppStart()
    }

    suspend fun onSuccessMoment() {
        runner.onSuccessMoment()
    }

    /**
     * Returns `true` only when the platform request API completed.
     *
     * The operating system may suppress the review dialog, so completion does not mean that a
     * dialog was visible.
     */
    suspend fun tryRequest(): Boolean = runner.tryRequest(attempt, observer)

    private inner class FlowObserver : ReviewAttemptObserver {
        override fun onAttempted() {
            _events.tryEmit(ReviewFlowEvent.Attempted)
        }

        override fun onSkipped(reason: SkipReason) {
            _events.tryEmit(ReviewFlowEvent.Skipped(reason))
        }

        override fun onRequesting() {
            _state.value = ReviewFlowState.Requesting
        }

        override fun onCompleted() {
            _events.tryEmit(ReviewFlowEvent.RequestCompleted)
            _state.value = ReviewFlowState.Done
        }

        override fun onUnavailable() {
            _events.tryEmit(ReviewFlowEvent.RequestUnavailable)
            _state.value = ReviewFlowState.Unavailable
        }

        override fun onFailed(throwable: Throwable) {
            _events.tryEmit(ReviewFlowEvent.Failed(throwable))
            _state.value = ReviewFlowState.Error(throwable)
        }

        override fun onCancelled() {
            _state.value = ReviewFlowState.Idle
        }
    }
}

private class PresenterReviewAttempt(
    private val presenter: ReviewPresenter,
) : ReviewAttempt {
    override suspend fun request(): ReviewPresentationResult = presenter.requestReview()
}
