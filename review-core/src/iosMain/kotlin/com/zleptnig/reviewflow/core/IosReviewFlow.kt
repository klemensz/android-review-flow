package com.zleptnig.reviewflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS factory for [ReviewFlow].
 *
 * Install a Swift [IosReviewRequest] that calls StoreKit's modern review request API and returns `false`
 * when no active presentation scene is available.
 */
object IosReviewFlow {
    fun create(request: IosReviewRequest): ReviewFlow {
        return create(
            request = request,
            rules = ReviewRules(),
            clock = SystemClock,
        )
    }

    fun create(
        request: IosReviewRequest,
        rules: ReviewRules = ReviewRules(),
        clock: Clock = SystemClock,
    ): ReviewFlow {
        return ReviewFlow(
            presenter = IosStoreKitPresenter(request),
            store = UserDefaultsReviewStateStore(),
            versionProvider = IosAppVersionProvider(),
            rules = rules,
            clock = clock,
        )
    }
}

/**
 * Small reverse-interop contract implemented in Swift so Kotlin never imports Swift-only StoreKit
 * APIs directly.
 */
interface IosReviewRequest {
    fun requestReview(): Boolean
}

class IosStoreKitPresenter(
    private val request: IosReviewRequest,
) : ReviewPresenter {
    override suspend fun requestReview(): ReviewPresentationResult {
        return withContext(Dispatchers.Main) {
            if (request.requestReview()) {
                ReviewPresentationResult.Completed
            } else {
                ReviewPresentationResult.Unavailable
            }
        }
    }
}
