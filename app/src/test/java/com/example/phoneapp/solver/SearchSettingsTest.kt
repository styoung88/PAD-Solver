package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSettingsTest {
    @Test
    fun levelsScaleEachBoardAndKeepMovementIndependentOfSearchBreadth() {
        val limits = mapOf(
            BoardSize.SMALL to listOf(23, 30, 38),
            BoardSize.STANDARD to listOf(30, 40, 50),
            BoardSize.LARGE to listOf(38, 50, 63),
        )
        for ((size, expectedMoves) in limits) {
            val board = Board(size.columns, size.rows, List(size.cellCount) { Orb(OrbColor.FIRE) })
            for ((index, movement) in OrbMovement.entries.withIndex()) {
                for ((complexity, width) in listOf(SearchComplexity.LOW to 150,
                    SearchComplexity.MEDIUM to 200, SearchComplexity.HIGH to 250, SearchComplexity.VERY_HIGH to 300)) {
                    val settings = SolveSettings(OrbColor.FIRE, 2, MatchPattern.TPA, movement, complexity)
                    val options = settings.toOptions(board)
                    assertEquals(expectedMoves[index], options.maxOrbMoves)
                    assertEquals(width, options.beamWidth)
                    assertEquals(OrbColor.FIRE, options.goal.preferredColor)
                    assertEquals(listOf(ComboMinimum(2), RequiredPattern(MatchPattern.TPA, OrbColor.FIRE)), options.goal.requirements)
                }
            }
            val defaults = SolveSettings().toOptions(board)
            assertEquals(size.maxOrbMoves, defaults.maxOrbMoves)
            assertEquals(200, defaults.beamWidth)
        }
    }
}
