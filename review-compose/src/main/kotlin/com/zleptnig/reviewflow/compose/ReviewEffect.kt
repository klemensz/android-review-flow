package com.zleptnig.reviewflow.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.zleptnig.reviewflow.core.ReviewOrchestrator

/**
 * Compose-friendly trigger:
 * - When [trigger] becomes true, tries once.
 * - Keyed by trigger => avoids repeated launch on recomposition.
 *
 * Usage: set trigger=true once (e.g. after success moment), then set it back to false.
 */
@Composable
fun ReviewEffect(
    orchestrator: ReviewOrchestrator,
    trigger: Boolean,
    onConsumed: (() -> Unit)? = null,
) {
    val activity = LocalContext.current.findActivity() ?: return

    LaunchedEffect(trigger) {
        if (trigger) {
            orchestrator.tryShow(activity)
            onConsumed?.invoke()
        }
    }
}
