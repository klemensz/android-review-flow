package com.zleptnig.reviewflow.core

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration

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

    @Test
    fun `presenter does not launch when activity disappears during info request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reviewManager = FakeReviewManager(context)
        var currentActivity: Activity? = Robolectric.buildActivity(Activity::class.java).setup().get()
        var launched = false
        val presenter = PlayCoreReviewPresenter(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    val info = reviewManager.requestReviewFlow().await()
                    currentActivity = null
                    return info
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) {
                    launched = true
                }
            },
            activityProvider = { currentActivity },
        )

        assertEquals(ReviewPresentationResult.Unavailable, presenter.requestReview())
        assertFalse(launched)
    }

    @Test
    fun `presenter launches with the current activity after info request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val reviewManager = FakeReviewManager(context)
        val replacement = Robolectric.buildActivity(Activity::class.java).setup().get()
        var currentActivity: Activity? = Robolectric.buildActivity(Activity::class.java).setup().get()
        var launchedWith: Activity? = null
        val presenter = PlayCoreReviewPresenter(
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo {
                    val info = reviewManager.requestReviewFlow().await()
                    currentActivity = replacement
                    return info
                }

                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) {
                    launchedWith = activity
                }
            },
            activityProvider = { currentActivity },
        )

        assertEquals(ReviewPresentationResult.Completed, presenter.requestReview())
        assertTrue(launchedWith === replacement)
    }

    @Test
    fun `legacy facade and new factory share persistence without consuming unavailable request`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ReviewStore(context)
        store.update { ReviewSnapshot() }
        val legacy = ReviewOrchestrator.create(
            context,
            client = object : ReviewClient {
                override suspend fun requestReviewInfo(): ReviewInfo = error("unused")
                override suspend fun launchReviewFlow(activity: Activity, reviewInfo: ReviewInfo) = Unit
            },
        )
        val rules = ReviewRules(
            minAppStarts = 1,
            minSuccessMoments = 1,
            cooldown = Duration.ZERO,
            oncePerVersion = false,
        )
        val flow = AndroidReviewFlow.create(context, activityProvider = { null }, rules = rules)

        assertFalse(flow.tryRequest())
        legacy.onAppStart()
        flow.onSuccessMoment()
        assertFalse(flow.tryRequest())
        assertTrue(flow.state.value is ReviewFlowState.Unavailable)

        // Read through a new adapter instance, as a recreated application would.
        assertEquals(
            ReviewSnapshot(appStarts = 1, successMoments = 1),
            ReviewStore(context).read(),
        )
    }
}
