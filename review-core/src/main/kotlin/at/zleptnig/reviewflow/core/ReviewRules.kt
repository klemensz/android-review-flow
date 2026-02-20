package at.zleptnig.reviewflow.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class ReviewRules(
    val minAppStarts: Int = 3,
    val minSuccessMoments: Int = 1,
    val cooldown: Duration = 30.days,
    val oncePerVersion: Boolean = true,
)
