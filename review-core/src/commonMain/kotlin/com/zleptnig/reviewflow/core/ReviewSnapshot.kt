package com.zleptnig.reviewflow.core

/**
 * Persisted inputs used by the review eligibility rules.
 */
data class ReviewSnapshot(
    val appStarts: Int = 0,
    val successMoments: Int = 0,
    val lastAttemptEpochMs: Long = 0L,
    val lastRequestCompletedVersion: String? = null,
)
