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
class PadBoardRecognizerReferenceTest {
    @Test
    fun recognizesTheConfirmedRowsFromTheOriginalPadScreenshot() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val bitmap = testContext.assets.open(REFERENCE_SCREENSHOT).use { BitmapFactory.decodeStream(it) }
        val screenshot = checkNotNull(bitmap) { "Could not decode the local reference screenshot." }

        try {
            val result = PadBoardRecognizer().recognize(screenshot)
            assertTrue("Expected a recognized board, got $result", result is BoardRecognition.Recognized)
            val recognized = result as BoardRecognition.Recognized
            val rows = recognized.board.orbs.chunked(recognized.board.columns).map { row ->
                row.joinToString("") { orb -> orb.color.toReferenceCode().toString() }
            }

            assertEquals(
                "Detected bounds=${recognized.boardBounds} in ${recognized.captureWidth}x${recognized.captureHeight}",
                EXPECTED_ROWS,
                rows,
            )
        } finally {
            screenshot.recycle()
        }
    }

    private fun OrbColor.toReferenceCode(): Char = when (this) {
        OrbColor.FIRE -> 'R'
        OrbColor.WATER -> 'B'
        OrbColor.WOOD -> 'G'
        OrbColor.LIGHT -> 'L'
        OrbColor.DARK -> 'D'
        OrbColor.HEART -> 'H'
        OrbColor.UNKNOWN -> '?'
    }

    private companion object {
        const val REFERENCE_SCREENSHOT = "pad-board-reference.png"
        val EXPECTED_ROWS = listOf(
            "GDGDHB",
            "BRRBDD",
            "GGHDBD",
            "RDHBHB",
            "DBGRRB",
        )
    }
}
