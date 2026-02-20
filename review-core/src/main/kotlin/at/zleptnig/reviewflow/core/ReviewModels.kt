package at.zleptnig.reviewflow.core

import com.google.android.play.core.review.ReviewInfo

sealed interface ReviewState {
    data object Idle : ReviewState
    data object Loading : ReviewState
    data class Ready(val reviewInfo: ReviewInfo) : ReviewState
    data object Showing : ReviewState
    data object Done : ReviewState
    data class Error(val throwable: Throwable) : ReviewState
}

sealed interface ReviewEvent {
    data object Attempted : ReviewEvent
    data class Skipped(val reason: SkipReason) : ReviewEvent
    data object Shown : ReviewEvent
    data class Failed(val throwable: Throwable) : ReviewEvent
}

sealed interface SkipReason {
    data object CooldownActive : SkipReason
    data object NotEnoughAppStarts : SkipReason
    data object NotEnoughSuccessMoments : SkipReason
    data object AlreadyShownForThisVersion : SkipReason
    data object InFlight : SkipReason
}
