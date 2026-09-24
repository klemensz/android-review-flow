package com.zleptnig.reviewflow.core

sealed interface ReviewFlowState {
    data object Idle : ReviewFlowState
    data object Requesting : ReviewFlowState
    data object Done : ReviewFlowState
    data object Unavailable : ReviewFlowState
    data class Error(val throwable: Throwable) : ReviewFlowState
}

/**
 * One-shot diagnostics emitted by [ReviewFlow].
 *
 * Events use a non-replaying [kotlinx.coroutines.flow.SharedFlow].
 */
sealed interface ReviewFlowEvent {
    data object Attempted : ReviewFlowEvent
    data class Skipped(val reason: SkipReason) : ReviewFlowEvent

    /**
     * The platform request completed; a visible dialog is not guaranteed.
     */
    data object RequestCompleted : ReviewFlowEvent

    data object RequestUnavailable : ReviewFlowEvent
    data class Failed(val throwable: Throwable) : ReviewFlowEvent
}
