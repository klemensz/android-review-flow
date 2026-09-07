package com.zleptnig.reviewflow.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration.Companion.milliseconds

internal interface ReviewAttempt {
    suspend fun request(): ReviewPresentationResult
}

internal interface ReviewAttemptObserver {
    fun onAttempted()
    fun onSkipped(reason: SkipReason)
    fun onRequesting()
    fun onCompleted()
    fun onUnavailable()
    fun onFailed(throwable: Throwable)
    fun onCancelled()
}

internal class ReviewAttemptRunner(
    private val store: ReviewStateStore,
    private val rules: ReviewRules,
    private val versionProvider: AppVersionProvider,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun onAppStart() {
        store.update { snapshot ->
            snapshot.copy(appStarts = snapshot.appStarts + 1)
        }
    }

    suspend fun onSuccessMoment() {
        store.update { snapshot ->
            snapshot.copy(successMoments = snapshot.successMoments + 1)
        }
    }

    suspend fun tryRequest(
        attempt: ReviewAttempt,
        observer: ReviewAttemptObserver,
    ): Boolean {
        if (!mutex.tryLock()) {
            observer.onSkipped(SkipReason.InFlight)
            return false
        }

        return try {
            tryRequestLocked(attempt, observer)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun tryRequestLocked(
        attempt: ReviewAttempt,
        observer: ReviewAttemptObserver,
    ): Boolean {
        return try {
            observer.onAttempted()

            val snapshot = store.read()
            val version = versionProvider.versionName()
            val now = clock.nowEpochMs()
            val skipReason = evaluateSkipReason(snapshot, version, now)
            if (skipReason != null) {
                observer.onSkipped(skipReason)
                return false
            }

            store.update { current ->
                current.copy(lastAttemptEpochMs = now)
            }

            observer.onRequesting()
            when (val result = attempt.request()) {
                ReviewPresentationResult.Completed -> {
                    if (version != null) {
                        store.update { current ->
                            current.copy(lastRequestCompletedVersion = version)
                        }
                    }
                    observer.onCompleted()
                    true
                }

                ReviewPresentationResult.Unavailable -> {
                    store.update { current ->
                        current.copy(lastAttemptEpochMs = snapshot.lastAttemptEpochMs)
                    }
                    observer.onUnavailable()
                    false
                }

                is ReviewPresentationResult.Failed -> {
                    observer.onFailed(result.throwable)
                    false
                }
            }
        } catch (cancellation: CancellationException) {
            observer.onCancelled()
            throw cancellation
        } catch (throwable: Throwable) {
            observer.onFailed(throwable)
            false
        }
    }

    private fun evaluateSkipReason(
        snapshot: ReviewSnapshot,
        version: String?,
        nowEpochMs: Long,
    ): SkipReason? {
        if (
            rules.oncePerVersion &&
            version != null &&
            snapshot.lastRequestCompletedVersion == version
        ) {
            return SkipReason.AlreadyShownForThisVersion
        }
        if (snapshot.appStarts < rules.minAppStarts) {
            return SkipReason.NotEnoughAppStarts
        }
        if (snapshot.successMoments < rules.minSuccessMoments) {
            return SkipReason.NotEnoughSuccessMoments
        }

        val lastAttempt = snapshot.lastAttemptEpochMs
        val elapsed = (nowEpochMs - lastAttempt).coerceAtLeast(0L).milliseconds
        if (lastAttempt != 0L && elapsed < rules.cooldown) {
            return SkipReason.CooldownActive
        }
        return null
    }
}
