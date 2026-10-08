package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.BoardSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class RouteOverlaySizeTest {
    @Test
    fun routeIndicesAndEndpointCentersUseEachGridAtDifferentScreenScales() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (size in BoardSize.entries) for (scale in listOf(0.75f, 1.25f)) {
            val width = (700 * scale).roundToInt()
            val height = (1500 * scale).roundToInt()
            val bounds = RectF(0f, 700f, 700f, 700f + 700f / size.columns * size.rows)
            val path = ((size.rows - 1 downTo 0).map { it * size.columns + size.columns - 1 } +
                (size.columns - 2 downTo 0).toList()).toIntArray()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                instrumentation.runOnMainSync {
                    val view = SolutionOverlayService.RouteOverlayView(instrumentation.targetContext,
                        bounds, path, 700, 1500, size.columns, size.rows)
                    view.layout(0, 0, width, height)
                    view.draw(Canvas(bitmap))
                }
                val startX = (size.columns - 0.5f) * bounds.width() / size.columns * scale
                val startY = (bounds.top + (size.rows - 0.5f) * bounds.height() / size.rows) * scale
                val finishX = 0.5f * bounds.width() / size.columns * scale
                val finishY = (bounds.top + 0.5f * bounds.height() / size.rows) * scale
                assertBadge(bitmap, startX, startY, 0xFF4CAF50.toInt())
                assertBadge(bitmap, finishX, finishY, 0xFFEF4444.toInt())
            } finally { bitmap.recycle() }
        }
    }

    private fun assertBadge(bitmap: Bitmap, x: Float, y: Float, color: Int) {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (py in 0 until bitmap.height) for (px in 0 until bitmap.width) {
            if (bitmap.getPixel(px, py) == color) {
                minX = minOf(minX, px)
                minY = minOf(minY, py)
                maxX = maxOf(maxX, px)
                maxY = maxOf(maxY, py)
            }
        }
        assertTrue("Endpoint badge must be visible", maxX >= minX && maxY >= minY)
        assertEquals(x, (minX + maxX + 1) / 2f, 2f)
        assertEquals(y, (minY + maxY + 1) / 2f, 2f)
    }
}
