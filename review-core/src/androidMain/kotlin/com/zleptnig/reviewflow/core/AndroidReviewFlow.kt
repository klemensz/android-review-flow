package com.zleptnig.reviewflow.core

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CancellationException

/**
 * Android factory for the multiplatform [ReviewFlow] API.
 */
object AndroidReviewFlow {
    /**
     * Creates an application-scoped review flow.
     *
     * [activityProvider] is retained for the lifetime of the returned flow. It must resolve the
     * current foreground activity without capturing an [Activity] strongly. Use a lifecycle-aware
     * holder backed by a weak reference, and return `null` while no activity is resumed.
     */
    fun create(
        context: Context,
        activityProvider: () -> Activity?,
        rules: ReviewRules = ReviewRules(),
        clock: Clock = SystemClock,
    ): ReviewFlow {
        val applicationContext = context.applicationContext
        return ReviewFlow(
            presenter = PlayCoreReviewPresenter(applicationContext, activityProvider),
            store = ReviewStore(applicationContext),
            versionProvider = DefaultAppVersionProvider(applicationContext),
            rules = rules,
            clock = clock,
        )
    }
}

/**
 * Play Core implementation for the multiplatform [ReviewPresenter] boundary.
 *
 * [activityProvider] must not strongly capture an [Activity] when this presenter is stored beyond
 * that activity's lifecycle.
 */
class PlayCoreReviewPresenter(
    context: Context,
    private val activityProvider: () -> Activity?,
) : ReviewPresenter {
    private val client: ReviewClient = PlayCoreReviewClient(context.applicationContext)

    override suspend fun requestReview(): ReviewPresentationResult {
        val activity = activityProvider() ?: return ReviewPresentationResult.Unavailable
        return try {
            val reviewInfo = client.requestReviewInfo()
            client.launchReviewFlow(activity, reviewInfo)
            ReviewPresentationResult.Completed
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ReviewPresentationResult.Failed(throwable)
        }
    }
}
