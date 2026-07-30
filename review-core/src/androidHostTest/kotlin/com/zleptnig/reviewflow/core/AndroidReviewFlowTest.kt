package com.zleptnig.reviewflow.core

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidReviewFlowTest {
    @Test
    fun `presenter is unavailable without foreground activity`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val presenter = PlayCoreReviewPresenter(context) { null }

        assertEquals(
            ReviewPresentationResult.Unavailable,
            presenter.requestReview(),
        )
    }
}
