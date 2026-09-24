package com.zleptnig.reviewflow.core

/**
 * Persistence boundary for review-rule state.
 *
 * [update] must apply the transform atomically so concurrent counter updates cannot be lost.
 */
interface ReviewStateStore {
    suspend fun read(): ReviewSnapshot

    suspend fun update(transform: (ReviewSnapshot) -> ReviewSnapshot)
}
