package com.zleptnig.reviewflow.demo

import com.zleptnig.reviewflow.core.IosReviewFlow
import com.zleptnig.reviewflow.core.IosReviewRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Demo-only Swift adapter. Create, invoke, and close on the main thread. */
class DemoSession(
    request: IosReviewRequest,
    onState: (String) -> Unit,
    private val onLog: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val flow = IosReviewFlow.create(request)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.state.collect { onState(it.toString()) }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flow.events.collect { onLog("Event: $it") }
        }
    }

    fun recordAppStart() = action("App start") { flow.onAppStart() }
    fun recordSuccess() = action("Success moment") { flow.onSuccessMoment() }
    fun requestReview() = action("Request") { onLog("Request returned: ${flow.tryRequest()}") }

    private fun action(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                onLog("$label finished")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onLog("$label failed: ${error.message ?: error.toString()}")
            }
        }
    }

    /** Cancels pending actions and both collectors when the owning view disappears. */
    fun close() {
        scope.cancel()
    }
}
