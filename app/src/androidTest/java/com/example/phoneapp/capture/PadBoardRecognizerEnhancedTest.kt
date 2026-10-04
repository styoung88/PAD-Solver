package com.example.phoneapp.capture

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbModifier
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PadBoardRecognizerEnhancedTest {
    @Test
    fun recognizesEnhancedMarkersInSuppliedScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtures = listOf(
            Fixture("Screenshot_20261003-222639.png", setOf(BoardPosition(1, 0), BoardPosition(4, 2))),
            Fixture(
                "Screenshot_20261003-224338.png",
                setOf(BoardPosition(0, 1), BoardPosition(4, 0), BoardPosition(4, 2)),
            ),
        )

        val mismatches = mutableListOf<String>()
        for (fixture in fixtures) {
            val bitmap = instrumentation.context.assets.open(fixture.fileName).use { BitmapFactory.decodeStream(it) }
                ?: error("Could not decode fixture: ${fixture.fileName}")
            val result = PadBoardRecognizer().recognize(bitmap) as? BoardRecognition.Recognized
                ?: error("Board recognition was uncertain for ${fixture.fileName}")
            val enhancedPositions = result.board.orbs.mapIndexedNotNull { index, orb ->
                if (OrbModifier.ENHANCED in orb.modifiers) result.board.positionOf(index) else null
            }.toSet()

            if (fixture.expectedPositions != enhancedPositions) {
                mismatches += "${fixture.fileName} (${bitmap.width}x${bitmap.height}, bounds=${result.boardBounds}): " +
                    "expected ${fixture.expectedPositions}, detected $enhancedPositions"
            }
            bitmap.recycle()
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    private data class Fixture(val fileName: String, val expectedPositions: Set<BoardPosition>)
}
