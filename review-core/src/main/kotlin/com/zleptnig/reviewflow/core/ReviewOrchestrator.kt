package com.zleptnig.reviewflow.core

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.milliseconds

/**
 * Orchestrates when/how In-App Review is shown.
 *
 * - Coroutine/Flow-first
 * - Single-flight guarded (Mutex)
 * - Rule engine + persistence
 * - Emits StateFlow + SharedFlow events
 */
class ReviewOrchestrator internal constructor(
    private val store: ReviewStore,
    private val client: ReviewClient,
    private val rules: ReviewRules,
    private val versionProvider: AppVersionProvider,
    private val clock: Clock,
) {
    private val _state = MutableStateFlow<ReviewState>(ReviewState.Idle)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<ReviewEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val mutex = Mutex()

    /**
     * Call once per cold start (or per "session").
     */
    suspend fun onAppStart() {
        store.incrementAppStarts()
    }

    /**
     * Call after meaningful positive actions (e.g., checkout complete, trip saved, milestone).
     */
    suspend fun onSuccessMoment() {
        store.incrementSuccessMoments()
    }

    /**
     * Checks rules and tries to show the review dialog.
     * Returns true only if the flow was launched successfully.
     *
     * **`oncePerVersion` and null version names:** If [AppVersionProvider.versionName] returns
     * `null` (e.g. the package name is not found), the once-per-version check is bypassed and
     * the review may be shown again. This is intentional – prefer showing over silently blocking.
     */
    suspend fun tryShow(activity: Activity): Boolean {
        if (!mutex.tryLock()) {
            _events.tryEmit(ReviewEvent.Skipped(SkipReason.InFlight))
            return false
        }

        return try {
            _events.tryEmit(ReviewEvent.Attempted)

            val snapshot = store.getSnapshot()
            val version = versionProvider.versionName()

            if (rules.oncePerVersion && version != null && snapshot.lastShownVersion == version) {
                _events.tryEmit(ReviewEvent.Skipped(SkipReason.AlreadyShownForThisVersion))
                return false
            }

            if (snapshot.appStarts < rules.minAppStarts) {
                _events.tryEmit(ReviewEvent.Skipped(SkipReason.NotEnoughAppStarts))
                return false
            }

            if (snapshot.successMoments < rules.minSuccessMoments) {
                _events.tryEmit(ReviewEvent.Skipped(SkipReason.NotEnoughSuccessMoments))
                return false
            }

            val now = clock.nowEpochMs()
            val lastAttempt = snapshot.lastAttemptEpochMs
            val elapsed = (now - lastAttempt).coerceAtLeast(0L).milliseconds

            if (lastAttempt != 0L && elapsed < rules.cooldown) {
                _events.tryEmit(ReviewEvent.Skipped(SkipReason.CooldownActive))
                return false
            }

            // Mark attempt BEFORE requesting info (so spam / rapid taps still count).
            store.markAttempt(now)

            _state.value = ReviewState.Loading
            val info = client.requestReviewInfo()

            _state.value = ReviewState.Ready(info)
            _state.value = ReviewState.Showing
            client.launchReviewFlow(activity, info)

            store.markShown(version)
            _events.tryEmit(ReviewEvent.Shown)
            _state.value = ReviewState.Done
            true
        } catch (t: Throwable) {
            if (t is CancellationException) {
                _state.value = ReviewState.Idle
                throw t
            }
            _events.tryEmit(ReviewEvent.Failed(t))
            _state.value = ReviewState.Error(t)
            false
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /**
         * Factory for production usage.
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
