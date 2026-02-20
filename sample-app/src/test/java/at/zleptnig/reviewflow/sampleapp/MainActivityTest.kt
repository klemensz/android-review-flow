package at.zleptnig.reviewflow.sampleapp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTest {

    @Test
    fun `requestReviewFromSample calls success moment before trigger`() = runTest {
        val calls = mutableListOf<String>()

        requestReviewFromSample(
            onSuccessMoment = { calls += "success" },
            setTrigger = { value -> calls += "trigger:$value" },
        )

        assertEquals(listOf("success", "trigger:true"), calls)
    }

    @Test
    fun `requestReviewFromSample sets trigger exactly once`() = runTest {
        var triggerCalls = 0
        var lastTrigger = false

        requestReviewFromSample(
            onSuccessMoment = null,
            setTrigger = { value ->
                triggerCalls += 1
                lastTrigger = value
            },
        )

        assertEquals(1, triggerCalls)
        assertEquals(true, lastTrigger)
    }
}
