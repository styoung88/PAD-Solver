package com.example.phoneapp.board

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PadBoardRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import java.io.File

/** Checks overlay endpoint geometry against orb centers measured in a real PAD screenshot. */
@RunWith(AndroidJUnit4::class)
class RouteArrowRendererScreenshotTest {
    @Test
    fun endpointMarkersStayCenteredOnMeasuredOrbCenters() {
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
            )

            assertMarkerCenter(overlay, measuredOrbCenter(screenshot, start), START_BADGE_COLOR)
            assertMarkerCenter(overlay, measuredOrbCenter(screenshot, finish), END_BADGE_COLOR)
        } finally {
            overlay.recycle()
            screenshot.recycle()
        }
    }

    private fun measuredOrbCenter(screenshot: Bitmap, position: BoardPosition) = PointF(
        screenshot.width * (position.column + 0.5f) / Board.DEFAULT_COLUMNS,
        MEASURED_ROW_CENTERS[position.row] * screenshot.height / REFERENCE_HEIGHT,
    )

    @Test
    fun denseRevisitsRenderIdenticallyWithPreparedGeometryAndExportPreviews() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = listOf(
            listOf(29, 28, 27, 26, 25, 24, 18, 12, 13, 7, 8, 14, 13, 12, 6, 0, 1, 2, 8, 7, 6),
            listOf(8, 14, 15, 21, 20, 26, 27, 28, 22, 21, 27, 28, 29, 23, 22, 21, 20, 19, 18,
                12, 6, 7, 13, 12, 18, 19, 13, 7, 1, 2, 1, 0, 6, 12, 13, 14, 8, 2),
            listOf(0, 1, 7, 6, 0, 1, 7, 6, 0, 1, 7, 6, 0),
        ).map { route -> route.map { BoardPosition(it / 6, it % 6) } }
        val colors = listOf("DWFFHG", "DLHFLD", "HGWLHD", "HFLDFF", "WWDLGD").joinToString("")
        for ((fixture, route) in routes.withIndex()) {
            val bounds = RectF(0f, 0f, 1080f, 900f)
            val prepared = RouteArrowRenderer.prepareRoute(route, 6, 5)
            val direct = Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888)
            val cached = Bitmap.createBitmap(1080, 900, Bitmap.Config.ARGB_8888)
            try {
                fun render(bitmap: Bitmap, geometry: RouteArrowRenderer.PreparedRoute?) {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(0xFF202631.toInt())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (index in 0 until 30) {
                        paint.color = when (colors[index]) {
                            'W' -> 0xFF4DA8E8.toInt()
                            'G' -> 0xFF55B66A.toInt()
                            'D' -> 0xFF9B6BCC.toInt()
                            'F' -> 0xFFE94B43.toInt()
                            'L' -> 0xFFF3D447.toInt()
                            else -> 0xFFE78EB5.toInt()
                        }
                        canvas.drawCircle((index % 6 + 0.5f) * 180, (index / 6 + 0.5f) * 180, 84.6f, paint)
                    }
                    RouteArrowRenderer.draw(canvas, route, bounds, 6, 5, preparedRoute = geometry)
                }
                render(direct, null)
                render(cached, prepared)
                assertTrue("Prepared rendering differs for fixture $fixture", direct.sameAs(cached))
                File(context.getExternalFilesDir(null), "route-preview-$fixture.png").outputStream().use {
                    assertTrue(direct.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                if (fixture == 1) exportGameOverlay(route, prepared)
            } finally {
                direct.recycle()
                cached.recycle()
            }
        }
    }

    @Test
    fun stalePreparedRouteCannotDrawADifferentPathOfTheSameLength() {
        val first = listOf(BoardPosition(0, 0), BoardPosition(0, 1), BoardPosition(1, 1))
        val second = listOf(BoardPosition(4, 5), BoardPosition(4, 4), BoardPosition(3, 4))
        val stale = RouteArrowRenderer.prepareRoute(first, 6, 5)
        val direct = Bitmap.createBitmap(600, 500, Bitmap.Config.ARGB_8888)
        val cached = Bitmap.createBitmap(600, 500, Bitmap.Config.ARGB_8888)
        try {
            val bounds = RectF(0f, 0f, 600f, 500f)
            RouteArrowRenderer.draw(Canvas(direct), second, bounds, 6, 5)
            RouteArrowRenderer.draw(Canvas(cached), second, bounds, 6, 5, preparedRoute = stale)
            assertTrue("Stale geometry must be replaced", direct.sameAs(cached))
        } finally {
            direct.recycle()
            cached.recycle()
        }
    }

    @Test
    fun unlabeledBadgesShareTheOrbCenterWhenTheRouteReturnsToItsStart() {
        val overlay = Bitmap.createBitmap(600, 500, Bitmap.Config.ARGB_8888)
        try {
            RouteArrowRenderer.draw(Canvas(overlay), listOf(BoardPosition(2, 2), BoardPosition(2, 3),
                BoardPosition(3, 3), BoardPosition(3, 2), BoardPosition(2, 2)), RectF(0f, 0f, 600f, 500f), 6, 5)
            assertMarkerCenter(overlay, PointF(250f, 250f), START_BADGE_COLOR, 1f)
            assertMarkerCenter(overlay, PointF(250f, 250f), END_BADGE_COLOR, 1f)
        } finally {
            overlay.recycle()
        }
    }

    @Test
    fun latestScreenshotBadgesStayCenteredAtOppositeEndsOfTheBoard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.context.assets.open("pad-endpoint-alignment-reference.jpg")
            .use(BitmapFactory::decodeStream))
        val overlay = Bitmap.createBitmap(screenshot.width, screenshot.height, Bitmap.Config.ARGB_8888)
        try {
            val recognition = PadBoardRecognizer().recognize(screenshot) as BoardRecognition.Recognized
            val route = listOf(29, 28, 27, 21, 15, 9, 3).map { BoardPosition(it / 6, it % 6) }
            RouteArrowRenderer.draw(Canvas(overlay), route, recognition.boardBounds, 6, 5)
            assertMarkerCenter(overlay, PointF(526.2f, 1200f), START_BADGE_COLOR, 4f)
            assertMarkerCenter(overlay, PointF(334.8f, 822f), END_BADGE_COLOR, 4f)
            val composite = screenshot.copy(Bitmap.Config.ARGB_8888, true)
            try {
                Canvas(composite).drawBitmap(overlay, 0f, 0f, null)
                File(instrumentation.targetContext.getExternalFilesDir(null), "route-endpoint-alignment.png")
                    .outputStream().use { assertTrue(composite.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                composite.recycle()
            }
        } finally {
            overlay.recycle()
            screenshot.recycle()
        }
    }

    private fun exportGameOverlay(route: List<BoardPosition>, prepared: RouteArrowRenderer.PreparedRoute) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.context.assets.open(COLOR_VARIANCE_SCREENSHOT).use(BitmapFactory::decodeStream))
        var composite: Bitmap? = null
        try {
            val recognized = PadBoardRecognizer().recognize(screenshot) as BoardRecognition.Recognized
            val expectedColors = OrbColor.attackingColors.toSet() + OrbColor.HEART
            assertEquals("The overlay fixture must exercise all six orb colors", expectedColors,
                recognized.board.orbs.map { it.color }.toSet())
            assertEquals("The dense route must pass over all six orb colors", expectedColors,
                route.map { recognized.board[it].color }.toSet())
            composite = screenshot.copy(Bitmap.Config.ARGB_8888, true)
            RouteArrowRenderer.draw(Canvas(composite), route, recognized.boardBounds, 6, 5, preparedRoute = prepared)
            File(instrumentation.targetContext.getExternalFilesDir(null), "route-overlay-vivid.png").outputStream().use {
                assertTrue(composite.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val bounds = Rect().also { recognized.boardBounds.round(it) }
            val boardPreview = Bitmap.createBitmap(composite, bounds.left, bounds.top, bounds.width(), bounds.height())
            try {
                File(instrumentation.targetContext.getExternalFilesDir(null), "route-overlay-board-vivid.png")
                    .outputStream().use { assertTrue(boardPreview.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                boardPreview.recycle()
            }
        } finally {
            composite?.recycle()
            screenshot.recycle()
        }
    }

    private fun assertMarkerCenter(bitmap: Bitmap, expected: PointF, color: Int, tolerance: Float = CENTER_TOLERANCE_PX) {
        val radius = bitmap.width.toFloat() / Board.DEFAULT_COLUMNS * 0.30f
        var minX = bitmap.width
        var minY = bitmap.height
        var maxX = -1
        var maxY = -1
        for (y in (expected.y - radius).roundToInt()..(expected.y + radius).roundToInt()) {
            for (x in (expected.x - radius).roundToInt()..(expected.x + radius).roundToInt()) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height && bitmap.getPixel(x, y) == color) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        assertTrue("Endpoint badge must remain visible", maxX >= minX && maxY >= minY)
        assertEquals("Badge horizontal center", expected.x, (minX + maxX + 1) / 2f, tolerance)
        assertEquals("Badge vertical center", expected.y, (minY + maxY + 1) / 2f, tolerance)
    }

    private companion object {
        const val REFERENCE_SCREENSHOT = "Screenshot_20261004-003057.png"
        const val COLOR_VARIANCE_SCREENSHOT = "pad-color-variance-reference.jpg"
        const val REFERENCE_HEIGHT = 2142f
        const val START_BADGE_COLOR = 0xFF4CAF50.toInt()
        const val END_BADGE_COLOR = 0xFFEF4444.toInt()
        const val CENTER_TOLERANCE_PX = 6f
        // Midpoints of the visible top/bottom orb rims at x=80 in the source:
        // 1304..1451, 1461..1608, 1618..1766, 1775..1923, 1933..2080.
        // Vertical pitch is about 157.5px, independently of the 160px column pitch.
        val MEASURED_ROW_CENTERS = floatArrayOf(1377.5f, 1534.5f, 1692f, 1849f, 2006.5f)
    }
}
