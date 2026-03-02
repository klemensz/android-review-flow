package com.zleptnig.reviewflow.sampleapp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {

    @Test
    fun `requestReviewFromSample calls success moment before tryShow`() = runTest {
        val calls = mutableListOf<String>()

        val launched = requestReviewFromSample(
            onSuccessMoment = { calls += "success" },
            tryShow = {
                calls += "tryShow"
                true
            },
        )

        assertTrue(launched)
        assertEquals(listOf("success", "tryShow"), calls)
    }

    @Test
    fun `requestReviewFromSample calls both functions exactly once`() = runTest {
        var successCalls = 0
        var tryShowCalls = 0

        val launched = requestReviewFromSample(
            onSuccessMoment = {
                successCalls += 1
            },
            tryShow = {
                tryShowCalls += 1
                false
            },
        )

        assertFalse(launched)
        assertEquals(1, successCalls)
        assertEquals(1, tryShowCalls)
    }

    @Test
    fun `requestReviewWithEffectTrigger calls success moment before trigger`() = runTest {
        val calls = mutableListOf<String>()

        requestReviewWithEffectTrigger(
            onSuccessMoment = { calls += "success" },
            setTrigger = { value -> calls += "trigger:$value" },
        )

        assertEquals(listOf("success", "trigger:true"), calls)
    }

    @Test
    fun `requestReviewWithEffectTrigger sets trigger exactly once`() = runTest {
        var successCalls = 0
        var triggerCalls = 0
        var lastTrigger = false

        requestReviewWithEffectTrigger(
            onSuccessMoment = {
                successCalls += 1
            },
            setTrigger = { value ->
                triggerCalls += 1
                lastTrigger = value
            },
        )

        assertEquals(1, successCalls)
        assertEquals(1, triggerCalls)
        assertTrue(lastTrigger)
    }
}
