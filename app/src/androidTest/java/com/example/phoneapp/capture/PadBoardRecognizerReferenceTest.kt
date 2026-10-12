package com.example.phoneapp.capture

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.game.GameVariant
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
            val startNanos = SystemClock.elapsedRealtimeNanos()
            val result = BoardRecognizer.forGame(GameVariant.PAD).recognize(screenshot)
            val elapsedMillis = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000
            Log.i(BENCHMARK_TAG, "Reference recognition took ${elapsedMillis}ms")
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
        OrbColor.JAMMER -> 'J'
        OrbColor.POISON -> 'P'
        OrbColor.LETHAL_POISON -> 'M'
        OrbColor.BOMB -> 'X'
        OrbColor.UNKNOWN -> '?'
    }

    private companion object {
        const val REFERENCE_SCREENSHOT = "pad-board-reference.png"
        const val BENCHMARK_TAG = "PadBoardBenchmark"
        val EXPECTED_ROWS = listOf(
            "GDGDHB",
            "BRRBDD",
            "GGHDBD",
            "RDHBHB",
            "DBGRRB",
        )
    }
}
