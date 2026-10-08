package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadBoardRecognizerHazardTest {
    @Test
    fun unresolvedCellDoesNotFallBackToBattleArtwork() {
        val source = load("seven-by-six-route-capture.jpg")
        val image = source.copy(Bitmap.Config.ARGB_8888, true)
        source.recycle()
        try {
            // Completely hide a heart; its neighbors still establish the real grid.
            Canvas(image).drawRect(404f, 1011f, 482f, 1090f, Paint().apply { color = Color.BLACK })
            assertTrue("An unreadable board must require another capture",
                PadBoardRecognizer().recognize(image) is BoardRecognition.Uncertain)
        } finally { image.recycle() }
    }

    @Test
    fun locatesSevenBySixBoardBelowBattleArtDespiteExistingRoute() {
        val image = load("seven-by-six-route-capture.jpg")
        try {
            val result = PadBoardRecognizer().recognize(image)
            assertTrue("$result", result is BoardRecognition.Recognized)
            result as BoardRecognition.Recognized
            assertEquals(7, result.board.columns)
            assertEquals(6, result.board.rows)
            assertEquals("bounds=${result.boardBounds}", 777f, result.boardBounds.top, 4f)
            assertEquals("bounds=${result.boardBounds}", 1247f, result.boardBounds.bottom, 4f)
            for (row in 0 until 6) for (column in 0 until 7) {
                assertEquals("column=$column bounds=${result.boardBounds}", 52f + column * 78.3f,
                    result.boardBounds.left + (column + 0.5f) * result.boardBounds.width() / 7, 4f)
                assertEquals("row=$row bounds=${result.boardBounds}", 816f + row * 78f,
                    result.boardBounds.top + (row + 0.5f) * result.boardBounds.height() / 6, 4f)
            }
            val colors = mapOf('F' to OrbColor.FIRE, 'W' to OrbColor.WATER, 'G' to OrbColor.WOOD,
                'L' to OrbColor.LIGHT, 'D' to OrbColor.DARK, 'H' to OrbColor.HEART, 'J' to OrbColor.JAMMER)
            assertEquals(listOf("GLLWGHH", "GDHJDDW", "DDWHHLF", "JFLJWHW", "LHGWDGW", "GHWWGGD")
                .joinToString("").map { colors.getValue(it) }, result.board.orbs.map { it.color })
            assertEquals(setOf(5, 6, 9, 18, 26, 29, 36).associateWith { setOf(OrbModifier.ENHANCED) },
                result.board.orbs.withIndex().filter { it.value.modifiers.isNotEmpty() }
                    .associate { it.index to it.value.modifiers })
        } finally { image.recycle() }
    }

    @Test
    fun recognizesHazardsInUserCapturesIncludingRouteOverlay() {
        val captures = listOf(
            Triple("poison-route-capture.jpg", "WGFFPGDLHGDFHDLFLLFWDWHFPFHGWP", setOf(3, 28)),
            Triple("jammer-capture.jpg", "JDGLLJJJLLHLLHDDHGDDHHLHGLGHDD", setOf(1, 2, 3, 14, 18, 24, 29)),
        )
        val colors = mapOf('F' to OrbColor.FIRE, 'W' to OrbColor.WATER, 'G' to OrbColor.WOOD,
            'L' to OrbColor.LIGHT, 'D' to OrbColor.DARK, 'H' to OrbColor.HEART,
            'J' to OrbColor.JAMMER, 'P' to OrbColor.POISON)
        for ((file, expected, enhanced) in captures) {
            val image = load(file)
            try {
                val result = PadBoardRecognizer().recognize(image)
                assertTrue("$file: $result", result is BoardRecognition.Recognized)
                result as BoardRecognition.Recognized
                assertEquals("$file bounds=${result.boardBounds}", expected.map { colors.getValue(it) },
                    result.board.orbs.map { it.color })
                assertEquals("$file modifiers", enhanced.map { it to setOf(OrbModifier.ENHANCED) }.toMap(),
                    result.board.orbs.withIndex().filter { it.value.modifiers.isNotEmpty() }
                        .associate { it.index to it.value.modifiers })
            } finally { image.recycle() }
        }
    }

    @Test
    fun recognizesMixedHazardBoardsAtEverySize() {
        for (size in BoardSize.entries) for (width in listOf(420, 720)) {
            val image = referenceGrid(size, width)
            try {
                val result = PadBoardRecognizer().recognize(image)
                assertTrue("$size width=$width: $result", result is BoardRecognition.Recognized)
                val board = (result as BoardRecognition.Recognized).board
                assertEquals(size.columns, board.columns)
                assertEquals(size.rows, board.rows)
                assertEquals("$size width=$width bounds=${result.boardBounds}",
                    List(size.cellCount) { TYPES[it % TYPES.size] }, board.orbs.map { it.color })
                assertTrue("$size width=$width unexpected modifiers: ${board.orbs.withIndex().filter { it.value.modifiers.isNotEmpty() }}",
                    board.orbs.all { it.modifiers.isEmpty() })
            } finally { image.recycle() }
        }
    }

    @Test
    fun recognizesIndependentJammerAndBombSamples() {
        for (file in listOf("J0 x 9.png", "J10 x 9.png", "J11 x 9.png", "J13 x 9.png", "bomb.jpg")) {
            val image = referenceGrid(BoardSize.STANDARD, 600, file)
            try {
                val result = PadBoardRecognizer().recognize(image)
                assertTrue("$file: $result", result is BoardRecognition.Recognized)
                val board = (result as BoardRecognition.Recognized).board
                assertEquals("$file bounds=${result.boardBounds}",
                    if (file == "bomb.jpg") OrbColor.BOMB else OrbColor.JAMMER, board.orbs[6].color)
            } finally { image.recycle() }
        }
    }

    @Test
    fun identifiesWeakenedAndLockedMarkersInPublishedGameCrops() {
        val recognizer = PadBoardRecognizer()
        val weakened = load("weakened.png")
        val locked = load("locked.jpg")
        val mismatches = mutableListOf<String>()
        try {
            val pitch = weakened.width / 6f
            val expectedMinus = setOf(0, 2, 4, 6, 7, 12)
            for (index in 0 until 18) {
                val detected = recognizer.hasWeakenedMarker(weakened, (index % 6 + 0.5f) * pitch,
                    (index / 6 + 0.5f) * weakened.height / 3f, pitch)
                if (detected != (index in expectedMinus)) mismatches += "minus $index detected=$detected"
            }
            val lockPitch = locked.width / 6f
            for (index in 0 until 18) {
                val detected = recognizer.hasLockedMarker(locked, (index % 6 + 0.5f) * lockPitch,
                    (index / 6 + 0.5f) * locked.height / 3f, lockPitch)
                if (detected != (index != 10)) mismatches += "lock $index detected=$detected"
            }
            assertTrue(mismatches.joinToString("; "), mismatches.isEmpty())
        } finally { weakened.recycle(); locked.recycle() }
    }

    @Test
    fun capturesPreserveColorAndCombinedModifiers() {
        for (file in listOf("weakened.png", "locked.jpg")) {
            val crop = load(file)
            val image = Bitmap.createBitmap(600, 1200, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image)
                canvas.drawColor(Color.BLACK)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                for (index in 0 until 30) {
                    // Skip the obscured cell in the lock reference; all other cells are visible.
                    val source = if (file == "locked.jpg" && index % 18 == 10) 0 else index % 18
                    val column = source % 6
                    val row = source / 6
                    val sourceRect = android.graphics.Rect((column * crop.width / 6f).toInt(), (row * crop.height / 3f).toInt(),
                        ((column + 1) * crop.width / 6f).toInt(), ((row + 1) * crop.height / 3f).toInt())
                    canvas.drawBitmap(crop, sourceRect, RectF(index % 6 * 100f, 680f + index / 6 * 100f,
                        (index % 6 + 1) * 100f, 680f + (index / 6 + 1) * 100f), paint)
                }
                val result = PadBoardRecognizer().recognize(image)
                assertTrue("$file: $result", result is BoardRecognition.Recognized)
                val board = (result as BoardRecognition.Recognized).board
                for ((index, orb) in board.orbs.withIndex()) {
                    if (file == "locked.jpg") {
                        assertTrue("lock $index: $orb", OrbModifier.LOCKED in orb.modifiers)
                    } else {
                        val source = index % 18
                        assertEquals("minus $index: $orb", source in setOf(0, 2, 4, 6, 7, 12), OrbModifier.WEAKENED in orb.modifiers)
                        assertEquals("plus $index: $orb", source in setOf(5, 13, 14, 16), OrbModifier.ENHANCED in orb.modifiers)
                        assertEquals("color $index", MINUS_COLORS[source], orb.color)
                    }
                }
            } finally { crop.recycle(); image.recycle() }
        }
    }

    private fun referenceGrid(size: BoardSize, width: Int, independent: String? = null): Bitmap {
        val height = width * 2
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.BLACK)
        val pitch = width / size.columns.toFloat()
        val top = height - 20f - pitch * size.rows
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (index in 0 until size.cellCount) {
            val row = index / size.columns
            val column = index % size.columns
            paint.color = if ((row + column) % 2 == 0) Color.rgb(53, 33, 28) else Color.rgb(76, 48, 39)
            canvas.drawRect(column * pitch, top + row * pitch, (column + 1) * pitch, top + (row + 1) * pitch, paint)
            val file = if (index == 6 && independent != null) independent else "drop_${(index % TYPES.size + 1).toString().padStart(2, '0')}.png"
            val sprite = load(file)
            try {
                val sourceIsCell = file.startsWith("J") || file == "bomb.jpg"
                val scale = if (sourceIsCell) pitch / sprite.width else pitch / 50f
                val w = sprite.width * scale
                val h = sprite.height * scale
                val x = (column + 0.5f) * pitch
                val y = top + (row + 0.5f) * pitch
                canvas.drawBitmap(sprite, null, RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), paint)
            } finally { sprite.recycle() }
        }
        return image
    }

    private fun load(file: String) = checkNotNull(InstrumentationRegistry.getInstrumentation().context.assets
        .open("orb-references/$file").use(BitmapFactory::decodeStream))

    private companion object {
        val TYPES = listOf(OrbColor.FIRE, OrbColor.WATER, OrbColor.WOOD, OrbColor.LIGHT, OrbColor.DARK,
            OrbColor.HEART, OrbColor.JAMMER, OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB)
        val MINUS_COLORS = listOf(OrbColor.FIRE, OrbColor.FIRE, OrbColor.DARK, OrbColor.WATER, OrbColor.FIRE, OrbColor.HEART,
            OrbColor.FIRE, OrbColor.DARK, OrbColor.FIRE, OrbColor.DARK, OrbColor.DARK, OrbColor.FIRE,
            OrbColor.HEART, OrbColor.WATER, OrbColor.HEART, OrbColor.FIRE, OrbColor.WATER, OrbColor.DARK)
    }
}
