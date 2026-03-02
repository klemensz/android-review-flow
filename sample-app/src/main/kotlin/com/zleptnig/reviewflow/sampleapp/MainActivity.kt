package com.zleptnig.reviewflow.sampleapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zleptnig.reviewflow.compose.ReviewEffect
import com.zleptnig.reviewflow.core.ReviewOrchestrator
import com.zleptnig.reviewflow.core.ReviewState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: ReviewOrchestrator
    private var lastNoOpHint: Boolean? = null

    companion object {
        private const val LOG_TAG = "ReviewFlowSample"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orchestrator = ReviewOrchestrator.create(this)

        lifecycleScope.launch { orchestrator.onAppStart() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    orchestrator.state.collect { state ->
                        when (state) {
                            is ReviewState.Ready -> {
                                val readyStateString = state.toString()
                                lastNoOpHint = parseNoOpFlagFromReviewInfoString(readyStateString)
                                val noOpHintText = lastNoOpHint?.toString() ?: "unknown"
                                Log.d(LOG_TAG, "state=Ready noOpHint=$noOpHintText raw=$readyStateString")
                            }

                            is ReviewState.Done -> {
                                Log.d(LOG_TAG, "state=Done")
                                val result = when (lastNoOpHint) {
                                    true -> "NO_OP"
                                    false -> "DIALOG_POSSIBLE"
                                    null -> "UNKNOWN"
                                }
                                Log.d(LOG_TAG, "result=$result")
                                lastNoOpHint = null
                            }

                            is ReviewState.Error -> {
                                Log.d(LOG_TAG, "state=$state")
                                lastNoOpHint = null
                            }

                            else -> Log.d(LOG_TAG, "state=$state")
                        }
                    }
                }
                launch {
                    orchestrator.events.collect { event ->
                        Log.d(LOG_TAG, "event=$event")
                    }
                }
            }
        }

        setContent {
            SampleScreen(
                orchestrator = orchestrator,
                onAskForSimpleReview = {
                    lifecycleScope.launch {
                        requestReviewFromSample(
                            onSuccessMoment = { orchestrator.onSuccessMoment() },
                            tryShow = { orchestrator.tryShow(this@MainActivity) },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SampleScreen(
    orchestrator: ReviewOrchestrator? = null,
    onAskForSimpleReview: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "ReviewFlow Demo App",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        SimpleReviewDemo(onAskForSimpleReview = onAskForSimpleReview)
        orchestrator?.let { AdvancedReviewEffectDemo(orchestrator = it) }
    }
}

@Composable
private fun SimpleReviewDemo(onAskForSimpleReview: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Simple demo: direct button action")
        Button(onClick = { onAskForSimpleReview?.invoke() }) {
            Text("Ask for review (simple)")
        }
    }
}

@Composable
private fun AdvancedReviewEffectDemo(orchestrator: ReviewOrchestrator) {
    var trigger by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Advanced demo: event trigger with ReviewEffect")
        Button(
            onClick = {
                scope.launch {
                    requestReviewWithEffectTrigger(
                        onSuccessMoment = { orchestrator.onSuccessMoment() },
                        setTrigger = { trigger = it },
                    )
                }
            },
        ) {
            Text("Ask for review (effect)")
        }
    }

    ReviewEffect(
        orchestrator = orchestrator,
        trigger = trigger,
        onConsumed = { trigger = false },
    )
}

internal suspend fun requestReviewFromSample(
    onSuccessMoment: suspend () -> Unit,
    tryShow: suspend () -> Boolean,
): Boolean {
    onSuccessMoment()
    return tryShow()
}

internal suspend fun requestReviewWithEffectTrigger(
    onSuccessMoment: suspend () -> Unit,
    setTrigger: (Boolean) -> Unit,
) {
    onSuccessMoment()
    setTrigger(true)
}


@Preview(showSystemUi = true, device = "id:pixel_9")
@Composable
private fun SampleScreenPreview() {
    SampleScreen()
}
