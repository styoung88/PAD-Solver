package com.example.phoneapp.capture

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
