package com.example.phoneapp.capture

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.OrbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadBoardRecognizerLimitedPaletteTest {
    @Test
    fun recognizesBoardsWithOnlyFireAndDarkOrbs() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val expectedRows = listOf(
            "RDDRRD",
            "RRDDRR",
            "RDRRRR",
            "DRRDDR",
            "RRRDDD",
        )

        for (fixture in FIXTURES) {
            val bitmap = context.assets.open(fixture).use(BitmapFactory::decodeStream)
                ?: error("Could not decode fixture: $fixture")
            try {
                val result = PadBoardRecognizer().recognize(bitmap)
                assertTrue("Expected $fixture to be recognized, got $result", result is BoardRecognition.Recognized)
                val board = (result as BoardRecognition.Recognized).board
                val rows = board.orbs.chunked(board.columns).map { row ->
                    row.joinToString("") { orb -> orb.color.toReferenceCode().toString() }
                }
                assertEquals("Incorrect orb colors in $fixture", expectedRows, rows)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun OrbColor.toReferenceCode(): Char = when (this) {
        OrbColor.FIRE -> 'R'
        OrbColor.DARK -> 'D'
        else -> '?'
    }

    private companion object {
        val FIXTURES = listOf(
            "Screenshot_20261004-003057.png",
            "Screenshot_20261004-003953.png",
        )
    }
}
