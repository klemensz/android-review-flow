package com.zleptnig.reviewflow.core

import android.app.Activity
import android.content.Context
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.tasks.await

class PlayCoreReviewClient(
    context: Context,
) : ReviewClient {

    private val manager = ReviewManagerFactory.create(context)

    override suspend fun requestReviewInfo(): ReviewInfo =
        manager.requestReviewFlow().await()

    override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) {
        // Completes when the dialog is dismissed or cannot be shown.
        manager.launchReviewFlow(activity, reviewInfo).await()
    }
}
