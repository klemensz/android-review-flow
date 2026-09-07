package com.zleptnig.reviewflow.core

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo

/**
 * Abstraction for testability.
 */
interface ReviewClient {
    suspend fun requestReviewInfo(): ReviewInfo
    suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo)
}
