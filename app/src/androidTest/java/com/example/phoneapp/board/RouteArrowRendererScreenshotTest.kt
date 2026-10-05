package com.example.phoneapp.board

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PadBoardRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Checks overlay endpoint geometry against orb centers measured in a real PAD screenshot. */
@RunWith(AndroidJUnit4::class)
class RouteArrowRendererScreenshotTest {
    @Test
    fun endpointBadgesStayCenteredOnMeasuredOrbCenters() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val screenshot = checkNotNull(
            context.assets.open(REFERENCE_SCREENSHOT).use(BitmapFactory::decodeStream),
        ) { "Could not decode the PAD screenshot fixture." }
        val overlay = Bitmap.createBitmap(screenshot.width, screenshot.height, Bitmap.Config.ARGB_8888)
        try {
            val recognition = PadBoardRecognizer().recognize(screenshot)
            assertTrue("Expected recognized PAD board, got $recognition", recognition is BoardRecognition.Recognized)
            val board = recognition as BoardRecognition.Recognized
            assertEquals("PAD grid starts at the screenshot's left edge", 0f, board.boardBounds.left, 0.5f)
            assertEquals("PAD grid spans the screenshot width", screenshot.width.toFloat(), board.boardBounds.right, 0.5f)

            val start = BoardPosition(3, 0)
            val finish = BoardPosition(0, 5)
            RouteArrowRenderer.draw(
                canvas = Canvas(overlay),
                path = listOf(
                    start,
                    BoardPosition(4, 0),
                    BoardPosition(4, 1),
                    BoardPosition(4, 2),
                    BoardPosition(4, 3),
                    BoardPosition(4, 4),
                    BoardPosition(3, 4),
                    BoardPosition(2, 4),
                    BoardPosition(1, 4),
                    BoardPosition(0, 4),
                    finish,
                ),
                bounds = board.boardBounds,
                columns = board.board.columns,
                rows = board.board.rows,
                density = context.resources.displayMetrics.density,
            )

            assertBadgeCenter(overlay, measuredOrbCenter(screenshot, start), START_BADGE_COLOR)
            assertBadgeCenter(overlay, measuredOrbCenter(screenshot, finish), END_BADGE_COLOR)
        } finally {
            overlay.recycle()
            screenshot.recycle()
        }
    }

    private fun measuredOrbCenter(screenshot: Bitmap, position: BoardPosition) = PointF(
        screenshot.width * (position.column + 0.5f) / Board.DEFAULT_COLUMNS,
        MEASURED_ROW_CENTERS[position.row] * screenshot.height / REFERENCE_HEIGHT,
    )

    private fun assertBadgeCenter(bitmap: Bitmap, expected: PointF, color: Int) {
        val radius = (bitmap.width.toFloat() / Board.DEFAULT_COLUMNS * 0.32f).roundToInt()
        val left = (expected.x - radius).roundToInt().coerceAtLeast(0)
        val right = (expected.x + radius).roundToInt().coerceAtMost(bitmap.width - 1)
        val top = (expected.y - radius).roundToInt().coerceAtLeast(0)
        val bottom = (expected.y + radius).roundToInt().coerceAtMost(bitmap.height - 1)
        var sumX = 0L
        var sumY = 0L
        var pixels = 0
        for (y in top..bottom) for (x in left..right) {
            if (bitmap.getPixel(x, y) == color) {
                sumX += x
                sumY += y
                pixels++
            }
        }
        assertTrue("Expected badge fill near measured orb center $expected", pixels > 0)
        assertEquals("Badge X must match the orb center", expected.x, sumX.toFloat() / pixels, CENTER_TOLERANCE_PX)
        assertEquals("Badge Y must match the orb center", expected.y, sumY.toFloat() / pixels, CENTER_TOLERANCE_PX)
    }

    private companion object {
        const val REFERENCE_SCREENSHOT = "Screenshot_20261004-003057.png"
        const val REFERENCE_HEIGHT = 2142f
        const val START_BADGE_COLOR = 0xFF4CAF50.toInt()
        const val END_BADGE_COLOR = 0xFFEF4444.toInt()
        const val CENTER_TOLERANCE_PX = 6f
        val MEASURED_ROW_CENTERS = floatArrayOf(1377f, 1537f, 1697f, 1857f, 2017f)
    }
}
