package com.zleptnig.reviewflow.sampleapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewLoggingDiagnosticsTest {

    @Test
    fun `parse no-op true from review info string`() {
        val value = parseNoOpFlagFromReviewInfoString(
            "ReviewInfo{pendingIntent=PendingIntent{...}, isNoOp=true}",
        )
        assertEquals(true, value)
    }

    @Test
    fun `parse no-op false from review info string`() {
        val value = parseNoOpFlagFromReviewInfoString(
            "ReviewInfo{pendingIntent=PendingIntent{...}, isNoOp=false}",
        )
        assertEquals(false, value)
    }

    @Test
    fun `return null when no no-op flag exists`() {
        val value = parseNoOpFlagFromReviewInfoString(
            "ReviewInfo{pendingIntent=PendingIntent{...}}",
        )
        assertNull(value)
    }

    @Test
    fun `return null for empty input`() {
        val value = parseNoOpFlagFromReviewInfoString("")
        assertNull(value)
    }

    @Test
    fun `parse no-op with whitespace around equals`() {
        val value = parseNoOpFlagFromReviewInfoString(
            "ReviewInfo{pendingIntent=PendingIntent{...}, isNoOp = true}",
        )
        assertEquals(true, value)
    }
}
