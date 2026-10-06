package com.example.phoneapp.capture

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.MainActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test

class CaptureServiceLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun stopServices() {
        context.stopService(Intent(context, ScreenCaptureService::class.java))
        context.stopService(Intent(context, SolutionOverlayService::class.java))
        awaitOverlay(false)
    }

    @Test
    fun stopSharingAlsoStopsOverlayAndCancelsPendingSolve() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startService(Intent(activity, SolutionOverlayService::class.java)
                    .setAction(SolutionOverlayService.ACTION_PROCESSING))
            }
            awaitOverlay(true)
            scenario.onActivity { activity ->
                activity.startService(Intent(activity, SolutionOverlayService::class.java).apply {
                    action = SolutionOverlayService.ACTION_SOLVE_CAPTURE
                    putExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH, "missing-capture.png")
                })
                activity.startService(Intent(activity, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_STOP))
            }
            awaitOverlay(false)
            assertFalse(ScreenCaptureService.active)
        }
    }

    @Test
    fun malformedRouteIntentStopsOverlayInsteadOfAttachingAWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startService(Intent(activity, SolutionOverlayService::class.java)
                    .setAction(SolutionOverlayService.ACTION_PROCESSING))
            }
            awaitOverlay(true)
            scenario.onActivity { activity ->
                activity.startService(Intent(activity, SolutionOverlayService::class.java)
                    .setAction(SolutionOverlayService.ACTION_SHOW))
            }
            awaitOverlay(false)
        }
    }

    @Test
    fun repeatedCaptureWithoutConsentCannotStartProjection() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                repeat(3) {
                    activity.startService(Intent(activity, ScreenCaptureService::class.java)
                        .setAction(ScreenCaptureService.ACTION_CAPTURE_NOW))
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(ScreenCaptureService.active)
            assertFalse(SolutionOverlayService.enabled)
        }
    }

    private fun awaitOverlay(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SolutionOverlayService.enabled != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(10)
        }
        org.junit.Assert.assertEquals("Overlay lifecycle did not settle", expected, SolutionOverlayService.enabled)
    }
}
