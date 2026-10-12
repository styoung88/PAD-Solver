package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.board.RouteArrowRenderer
import com.example.phoneapp.game.GameVariant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class PadZeroBoardRecognizerTest {
    @Test
    fun suppliedCapturesMatchAnnotatedColorsMarkersAndCellCenters() {
        for (fixture in fixtures()) {
            val bitmap = load(fixture)
            try { assertFixture(bitmap, fixture, 1f, 1f) } finally { bitmap.recycle() }
        }
    }

    @Test
    fun geometryAndMarkersSurviveResizingAndIndependentVerticalScaling() {
        for (fixture in fixtures()) {
            val source = load(fixture)
            try {
                for ((width, vertical) in listOf(420 to 1f, 1080 to 0.98f, 720 to 1.03f)) {
                    val height = (source.height * width.toFloat() / source.width * vertical).roundToInt()
                    val bitmap = Bitmap.createScaledBitmap(source, width, height, true)
                    try { assertFixture(bitmap, fixture, width.toFloat() / source.width, height.toFloat() / source.height) }
                    finally { bitmap.recycle() }
                }
            } finally { source.recycle() }
        }
    }

    @Test
    fun unreadableCellRequiresAnotherCaptureInsteadOfUsingBattleArt() {
        val source = load(fixtures().first { it.getString("file") == "1000013017.jpg" })
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        source.recycle()
        try {
            Canvas(bitmap).drawRect(202f, 816f, 280f, 895f, Paint().apply { color = Color.BLACK })
            assertTrue(PadZeroBoardRecognizer().recognize(bitmap) is BoardRecognition.Uncertain)
        } finally { bitmap.recycle() }
    }

    @Test
    fun ordinaryColorsDoNotRequireTheirReferenceArtwork() {
        for ((color, pixel) in listOf(OrbColor.FIRE to Color.RED, OrbColor.WATER to Color.BLUE,
            OrbColor.WOOD to Color.GREEN, OrbColor.LIGHT to Color.YELLOW,
            OrbColor.DARK to Color.MAGENTA, OrbColor.HEART to Color.rgb(255, 0, 128))) {
            val source = load(fixtures().first())
            val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle()
            try {
                val canvas = Canvas(bitmap)
                // The same simple circle has each ordinary color, without the game's symbol.
                canvas.drawCircle(517f, 856f, 40f, Paint().apply { this.color = pixel })
                canvas.drawCircle(517f, 856f, 12f, Paint().apply { this.color = Color.GRAY })
                val result = PadZeroBoardRecognizer().recognize(bitmap)
                assertTrue("$color: $result", result is BoardRecognition.Recognized)
                assertEquals(color, (result as BoardRecognition.Recognized).board.orbs[5].color)
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun emptyOrFragmentImagesRemainUncertain() {
        for ((width, height) in listOf(574 to 1280, 574 to 480)) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                for (color in listOf(Color.BLACK, Color.YELLOW)) {
                    bitmap.eraseColor(color)
                    assertTrue(PadZeroBoardRecognizer().recognize(bitmap) is BoardRecognition.Uncertain)
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun routeCornersStayCenteredInCaptureAndOverlayCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (fixture in fixtures()) {
            val bitmap = load(fixture)
            try {
                val recognized = BoardRecognizer.forGame(GameVariant.PAD_ZERO).recognize(bitmap) as BoardRecognition.Recognized
                val indices = intArrayOf(0, 6, 12, 18, 24, 25, 26, 27, 28, 29, 23, 17, 11, 5)
                val path = indices.map { recognized.board.positionOf(it) }
                val preview = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                try {
                    RouteArrowRenderer.draw(Canvas(preview), path, recognized.boardBounds, 6, 5)
                    for (scale in listOf(0.75f, 1.5f)) {
                        val overlay = Bitmap.createBitmap((bitmap.width * scale).roundToInt(),
                            (bitmap.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                        try {
                            instrumentation.runOnMainSync {
                                val view = SolutionOverlayService.RouteOverlayView(instrumentation.targetContext,
                                    recognized.boardBounds, indices, bitmap.width, bitmap.height, 6, 5)
                                view.layout(0, 0, overlay.width, overlay.height)
                                view.draw(Canvas(overlay))
                            }
                            assertBadge(overlay, 56f * scale, 856f * scale, 0xFF4CAF50.toInt())
                            assertBadge(overlay, 517f * scale, 856f * scale, 0xFFEF4444.toInt())
                        } finally { overlay.recycle() }
                    }
                    File(instrumentation.targetContext.getExternalFilesDir(null),
                        "pad-zero-route-${fixture.getString("file")}.png").outputStream().use {
                        assertTrue(preview.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { preview.recycle() }
            } finally { bitmap.recycle() }
        }
    }

    private fun assertFixture(bitmap: Bitmap, fixture: JSONObject, scaleX: Float, scaleY: Float) {
        val result = BoardRecognizer.forGame(GameVariant.PAD_ZERO).recognize(bitmap)
        assertTrue("${fixture.getString("file")} ${bitmap.width}x${bitmap.height}: $result", result is BoardRecognition.Recognized)
        result as BoardRecognition.Recognized
        assertEquals(6, result.board.columns)
        assertEquals(5, result.board.rows)
        val codes = mapOf('F' to OrbColor.FIRE, 'W' to OrbColor.WATER, 'G' to OrbColor.WOOD,
            'L' to OrbColor.LIGHT, 'D' to OrbColor.DARK, 'H' to OrbColor.HEART, 'J' to OrbColor.JAMMER)
        assertEquals("Colors, bounds=${result.boardBounds}", fixture.getString("colors").map { codes.getValue(it) },
            result.board.orbs.map { it.color })
        val bonuses = fixture.getJSONArray("comboBonus").indicesSet()
        result.board.orbs.forEachIndexed { index, orb ->
            assertEquals("Modifiers at $index, ${fixture.getString("file")}, ${bitmap.width}x${bitmap.height}, bounds=${result.boardBounds}", buildSet {
                if (index in bonuses) add(OrbModifier.COMBO_BONUS)
            }, orb.modifiers)
        }
        for (column in 0 until 6) for (row in 0 until 5) {
            assertEquals("column=$column bounds=${result.boardBounds}",
                fixture.getJSONArray("columnCenters").getDouble(column).toFloat() * scaleX,
                result.boardBounds.left + (column + 0.5f) * result.boardBounds.width() / 6, 4f * scaleX)
            assertEquals("row=$row bounds=${result.boardBounds}",
                fixture.getJSONArray("rowCenters").getDouble(row).toFloat() * scaleY,
                result.boardBounds.top + (row + 0.5f) * result.boardBounds.height() / 5, 4f * scaleY)
        }
    }

    private fun assertBadge(bitmap: Bitmap, x: Float, y: Float, color: Int) {
        var left = Int.MAX_VALUE
        var right = -1
        var top = Int.MAX_VALUE
        var bottom = -1
        for (py in 0 until bitmap.height) for (px in 0 until bitmap.width) {
            if (bitmap.getPixel(px, py) == color) {
                left = minOf(left, px); right = maxOf(right, px)
                top = minOf(top, py); bottom = maxOf(bottom, py)
            }
        }
        assertTrue("Endpoint must be visible", right >= left && bottom >= top)
        assertEquals(x, (left + right + 1) / 2f, 4f)
        assertEquals(y, (top + bottom + 1) / 2f, 4f)
    }

    private fun JSONArray.indicesSet() = (0 until length()).map { getInt(it) }.toSet()
    private fun fixtures(): List<JSONObject> {
        val context = InstrumentationRegistry.getInstrumentation().context
        val array = JSONArray(context.assets.open("pad-zero/annotations.json").bufferedReader().use { it.readText() })
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun load(fixture: JSONObject) = checkNotNull(InstrumentationRegistry.getInstrumentation().context.assets
        .open("pad-zero/${fixture.getString("file")}").use(BitmapFactory::decodeStream))
}
