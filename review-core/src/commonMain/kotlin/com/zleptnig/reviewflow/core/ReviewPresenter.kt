package com.zleptnig.reviewflow.core

/**
 * Platform boundary that submits one in-app review request.
 */
interface ReviewPresenter {
    /**
     * Completes after the platform request API completes.
     *
     * [ReviewPresentationResult.Completed] does not guarantee that a dialog was visible.
     */
    suspend fun requestReview(): ReviewPresentationResult
}

sealed interface ReviewPresentationResult {
    /**
     * The platform review request completed, possibly as a system-controlled no-op.
     */
    data object Completed : ReviewPresentationResult

    /**
     * The request could not start because a required presentation context was unavailable. This
     * result does not consume the cooldown.
     */
    data object Unavailable : ReviewPresentationResult

    data class Failed(val throwable: Throwable) : ReviewPresentationResult
}
