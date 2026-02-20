package com.zleptnig.reviewflow.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.zleptnig.reviewflow.core.ReviewOrchestrator
import com.zleptnig.reviewflow.core.ReviewRules

/**
 * Remembers one [ReviewOrchestrator] per `(applicationContext, rules)` value pair.
 *
 * Pass a stable [ReviewRules] value to avoid recreating the orchestrator when rule values change
 * unintentionally across recompositions.
 */
@Composable
fun rememberReviewOrchestrator(
    rules: ReviewRules = ReviewRules(),
): ReviewOrchestrator {
    val context = LocalContext.current.applicationContext
    return remember(context, rules) {
        ReviewOrchestrator.create(context = context, rules = rules)
    }
}
