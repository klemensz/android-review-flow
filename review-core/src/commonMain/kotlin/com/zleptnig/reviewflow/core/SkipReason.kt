package com.zleptnig.reviewflow.core

sealed interface SkipReason {
    data object CooldownActive : SkipReason
    data object NotEnoughAppStarts : SkipReason
    data object NotEnoughSuccessMoments : SkipReason
    data object AlreadyShownForThisVersion : SkipReason
    data object InFlight : SkipReason
}
