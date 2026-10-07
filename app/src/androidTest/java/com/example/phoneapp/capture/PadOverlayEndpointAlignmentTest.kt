package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadOverlayEndpointAlignmentTest {
    @Test
    fun rowAlignmentSurvivesIndependentVerticalScreenshotScaling() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val screenshot = checkNotNull(context.assets.open("Screenshot_20261004-003057.png")
            .use(BitmapFactory::decodeStream))
        try {
            val measuredRows = floatArrayOf(1377.5f, 1534.5f, 1692f, 1849f, 2006.5f)
            for (scale in listOf(0.98f, 1f, 1.03f)) {
                val scaled = Bitmap.createScaledBitmap(screenshot, screenshot.width,
                    (screenshot.height * scale).toInt(), true)
                try {
                    val recognized = PadBoardRecognizer().recognize(scaled) as BoardRecognition.Recognized
                    val bounds = recognized.boardBounds
                    measuredRows.forEachIndexed { row, measured ->
                        assertEquals("Row $row at vertical scale $scale, bounds=$bounds",
                            measured * scaled.height / screenshot.height,
                            bounds.top + (row + 0.5f) * bounds.height() / 5f, 4f)
                    }
                } finally {
                    if (scaled !== screenshot) scaled.recycle()
                }
            }
        } finally {
            screenshot.recycle()
        }
    }

    @Test
    fun latestOverlayScreenshotKeepsAllRowsCenteredOnTheirOrbs() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val screenshot = checkNotNull(context.assets.open("pad-endpoint-alignment-reference.jpg").use(BitmapFactory::decodeStream))
        try {
            val result = PadBoardRecognizer().recognize(screenshot)
            assertTrue("Expected board recognition, got $result", result is BoardRecognition.Recognized)
            val bounds = (result as BoardRecognition.Recognized).boardBounds
            val measuredRows = floatArrayOf(822f, 916.5f, 1011f, 1105.5f, 1200f)
            measuredRows.forEachIndexed { row, measured ->
                val center = bounds.top + (row + 0.5f) * bounds.height() / 5f
                assertEquals("Row $row center from the latest screenshot bounds=$bounds", measured, center, 4f)
            }
        } finally {
            screenshot.recycle()
        }
    }

    @Test
    fun recognizedEndpointCentersMatchOrbCentersInTheExactOverlayScreenshot() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("pad-overlay-live.png").use(BitmapFactory::decodeStream)
        val screenshot = checkNotNull(bitmap)
        try {
            val result = PadBoardRecognizer().recognize(screenshot)
            assertTrue("Expected board recognition, got $result", result is BoardRecognition.Recognized)
            val bounds = (result as BoardRecognition.Recognized).boardBounds
            val endOrbCenterY = bounds.top + (1 + 0.5f) * bounds.height() / 5f
            val startOrbCenterY = bounds.top + (2 + 0.5f) * bounds.height() / 5f
            assertEquals("E center from the recognized screenshot bounds=$bounds", 1534f, endOrbCenterY, 8f)
            assertEquals("S center from the recognized screenshot bounds=$bounds", 1692f, startOrbCenterY, 8f)
        } finally {
            screenshot.recycle()
        }
    }
}
