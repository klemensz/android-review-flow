package at.zleptnig.reviewflow.sampleapp

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import at.zleptnig.reviewflow.compose.ReviewEffect
import at.zleptnig.reviewflow.core.ReviewOrchestrator
import at.zleptnig.reviewflow.core.ReviewState
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
            SampleScreen(orchestrator)
        }
    }
}

@Composable
private fun SampleScreen(orchestrator: ReviewOrchestrator? = null) {
    var trigger by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = {
                scope.launch {
                    requestReviewFromSample(
                        onSuccessMoment = orchestrator?.let { { it.onSuccessMoment() } },
                        setTrigger = { trigger = it },
                    )
                }
            },
        ) {
            Text("Ask for review")
        }

        orchestrator?.let {
            ReviewEffect(
                orchestrator = it,
                trigger = trigger,
                onConsumed = { trigger = false },
            )
        }
    }
}

internal suspend fun requestReviewFromSample(
    onSuccessMoment: (suspend () -> Unit)?,
    setTrigger: (Boolean) -> Unit,
) {
    onSuccessMoment?.invoke()
    setTrigger(true)
}


@Preview(showSystemUi = true, device = "id:pixel_9")
@Composable
private fun SampleScreenPreview() {
    SampleScreen()
}
