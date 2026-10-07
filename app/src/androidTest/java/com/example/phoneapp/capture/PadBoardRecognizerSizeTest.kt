package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic grids exercise size selection; real 6×5 screenshots remain separate regressions. */
@RunWith(AndroidJUnit4::class)
class PadBoardRecognizerSizeTest {
    @Test
    fun recognizesUserSevenBySixScreenshotsAndEnhancedOrbs() {
        val fixtures = listOf(
            Triple("pad-7x6-user-2877.jpg",
                listOf("FFDHHDH", "FHDFHFF", "DDDFDDF", "FHHFHHF", "DFFDHDH", "HDHDDHF"),
                setOf(1, 2, 4, 5, 6, 7, 10, 13, 18, 19, 21, 22, 26, 30, 32, 33, 35, 39, 40)),
            Triple("pad-7x6-user-2876.jpg",
                listOf("DFDFFHH", "DFFFFFD", "FHFHHHD", "FHFFHHF", "HDDFHDD", "FFDHHFH"),
                setOf(3, 10, 14, 16, 17, 21, 22, 25, 27, 34, 38, 39, 41)),
        )
        val context = InstrumentationRegistry.getInstrumentation().context
        for ((file, rows, enhanced) in fixtures) {
            val bitmap = checkNotNull(context.assets.open(file).use(BitmapFactory::decodeStream))
            try {
                for (override in listOf(null, BoardSize.LARGE)) {
                    val result = PadBoardRecognizer().recognize(bitmap, override)
                    assertTrue("$file override=$override: $result", result is BoardRecognition.Recognized)
                    val recognized = result as BoardRecognition.Recognized
                    assertEquals(7, recognized.board.columns)
                    assertEquals(6, recognized.board.rows)
                    assertEquals("$file override=$override bounds=${recognized.boardBounds}", rows.joinToString("").map { when (it) {
                        'F' -> OrbColor.FIRE
                        'D' -> OrbColor.DARK
                        else -> OrbColor.HEART
                    } }, recognized.board.orbs.map { it.color })
                    assertEquals("$file enhanced orbs", enhanced,
                        recognized.board.orbs.withIndex().filter {
                            OrbModifier.ENHANCED in it.value.modifiers
                        }.map { it.index }.toSet())
                    // Centers measured in these 574×1280 screenshots.
                    for (row in 0 until 6) for (column in 0 until 7) {
                        val bounds = recognized.boardBounds
                        assertEquals("$file column=$column bounds=$bounds", 52f + column * 78.3f,
                            bounds.left + (column + 0.5f) * bounds.width() / 7f, 4f)
                        assertEquals("$file row=$row", 816f + row * 78f,
                            bounds.top + (row + 0.5f) * bounds.height() / 6f, 4f)
                    }
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun recognizesConfirmedColorsOnRealSevenBySixScreenshots() {
        val fixtures = mapOf(
            "pad-7x6-reference.jpg" to listOf("BFGDDBF", "DFDLGBL", "LHGFHDB", "BBGLLGD", "LGFGFDF", "LGLFFHB"),
            "pad-7x6-enhanced-reference.jpg" to listOf("LLHDDGF", "DBGGBDB", "BHHDDBB", "LDDFDLF", "DDDFDLF", "GLDGLFB"),
        )
        val context = InstrumentationRegistry.getInstrumentation().context
        for ((file, expectedRows) in fixtures) {
            val bitmap = checkNotNull(context.assets.open(file).use(BitmapFactory::decodeStream))
            try {
                val result = PadBoardRecognizer().recognize(bitmap)
                assertTrue("$file: $result", result is BoardRecognition.Recognized)
                val board = (result as BoardRecognition.Recognized).board
                assertEquals(7, board.columns)
                assertEquals(6, board.rows)
                assertEquals(expectedRows.joinToString("").map { when (it) {
                    'B' -> OrbColor.WATER
                    'F' -> OrbColor.FIRE
                    'G' -> OrbColor.WOOD
                    'D' -> OrbColor.DARK
                    'L' -> OrbColor.LIGHT
                    else -> OrbColor.HEART
                } }, board.orbs.map { it.color })
                if (file == "pad-7x6-enhanced-reference.jpg") {
                    val bounds = result.boardBounds
                    // Orb centers measured in the 747×1280 source, including its frame.
                    assertEquals("First column, bounds=$bounds", 68f, bounds.left + bounds.width() / 14f, 6f)
                    assertEquals("Last column, bounds=$bounds", 680f, bounds.left + bounds.width() * 6.5f / 7f, 6f)
                    assertEquals("First row, bounds=$bounds", 716f, bounds.top + bounds.height() / 12f, 6f)
                    assertEquals("Last row, bounds=$bounds", 1227f, bounds.top + bounds.height() * 5.5f / 6f, 6f)
                }
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun detectsEverySizeAcrossPalettesAndVerticalScaling() {
        for (size in BoardSize.entries) for (palette in listOf(6, 2, 1)) for (scale in listOf(1f, 1.02f)) {
            val bitmap = grid(size, palette, scale)
            try {
                val result = PadBoardRecognizer().recognize(bitmap)
                assertTrue("$size palette=$palette scale=$scale: $result", result is BoardRecognition.Recognized)
                val recognized = result as BoardRecognition.Recognized
                assertEquals(size.columns, recognized.board.columns)
                assertEquals(size.rows, recognized.board.rows)
                assertEquals(List(size.cellCount) { OrbColor.entries[colorIndex(it, palette)] }, recognized.board.orbs.map { it.color })
                assertEquals(0f, recognized.boardBounds.left, 1f)
                assertEquals(WIDTH.toFloat(), recognized.boardBounds.right, 1f)
                assertEquals(top(size, scale), recognized.boardBounds.top, 4f)
                assertEquals(HEIGHT - BOTTOM_MARGIN, recognized.boardBounds.bottom, 4f)
            } finally { bitmap.recycle() }
        }
    }

    @Test
    fun manualSizeOverrideUsesThatGridAndBlankImagesRemainUncertain() {
        for (size in BoardSize.entries) {
            val bitmap = grid(size, 6, 1f)
            try {
                val result = PadBoardRecognizer().recognize(bitmap, size)
                assertTrue(result is BoardRecognition.Recognized)
                assertEquals(size.columns, (result as BoardRecognition.Recognized).board.columns)
                assertEquals(size.rows, result.board.rows)
            } finally { bitmap.recycle() }
        }
        val blank = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            blank.eraseColor(Color.BLACK)
            assertTrue(PadBoardRecognizer().recognize(blank) is BoardRecognition.Uncertain)
            blank.eraseColor(Color.RED)
            assertTrue(PadBoardRecognizer().recognize(blank) is BoardRecognition.Uncertain)
        } finally { blank.recycle() }
    }

    private fun grid(size: BoardSize, palette: Int, scale: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pitch = WIDTH.toFloat() / size.columns
        for (index in 0 until size.cellCount) {
            paint.color = COLORS[colorIndex(index, palette)]
            val x = (index % size.columns + 0.5f) * pitch
            val y = top(size, scale) + (index / size.columns + 0.5f) * pitch * scale
            canvas.drawOval(x - pitch * 0.47f, y - pitch * scale * 0.47f,
                x + pitch * 0.47f, y + pitch * scale * 0.47f, paint)
        }
        return bitmap
    }

    private fun top(size: BoardSize, scale: Float): Float = HEIGHT - BOTTOM_MARGIN - WIDTH.toFloat() / size.columns * size.rows * scale
    private fun colorIndex(index: Int, palette: Int): Int = if (palette == 2) if (index % 3 == 0) 4 else 0 else index % palette

    private companion object {
        const val WIDTH = 700
        const val HEIGHT = 1500
        const val BOTTOM_MARGIN = 40f
        val COLORS = intArrayOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.MAGENTA, Color.rgb(255, 0, 120))
    }
}
