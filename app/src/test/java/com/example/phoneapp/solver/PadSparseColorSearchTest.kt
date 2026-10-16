package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class PadSparseColorSearchTest {
    @Test
    fun extendedMoveLimitsRetainOrImproveWaterDamage() = runBlocking {
        val board = reportedBoard()
        for (complexity in listOf(SearchComplexity.MEDIUM, SearchComplexity.VERY_HIGH)) {
            var previousScore = 0L
            for (moves in listOf(50, 63, 100, 150, 200)) {
                val settings = SolveSettings(preferredColor = OrbColor.WATER, searchComplexity = complexity)
                val options = settings.toOptions(board).copy(maxOrbMoves = moves)
                val result = BoardSolver().solve(board, options)
                assertValidSolution(board, options, result)
                val score = options.goal.evaluate(result.predictedMatches).primaryScore
                assertTrue("Extending the search must retain the best result: $complexity / $moves", score >= previousScore)
                previousScore = score
            }
        }
    }

    @Test
    fun waterFocusEscapesTheTwoComboRouteOnTheReportedBoard() = runBlocking {
        val board = reportedBoard()
        for (movement in OrbMovement.entries) for (complexity in SearchComplexity.entries) {
            val settings = SolveSettings(preferredColor = OrbColor.WATER,
                orbMovement = movement, searchComplexity = complexity)
            val options = settings.toOptions(board)
            val result = BoardSolver().solve(board, options)
            assertValidSolution(board, options, result)
            assertTrue("Water focus must beat the reported two-combo route: $movement / $complexity",
                result.comboCount >= 5)
            // The reported Water triple plus Heart triple scores 2000 relative Water damage.
            assertTrue("The extra combos must also improve Water damage: $movement / $complexity",
                options.goal.evaluate(result.predictedMatches).primaryScore > 2000L)
        }
    }

    private fun reportedBoard(): Board = Board(7, 6,
        ("FLHDDFG" + "FGDLDFL" + "WFLHLWG" + "LHHGWHD" + "WFLHFLG" + "GHLFGFL")
            .mapIndexed { index, code -> Orb(when (code) {
                'F' -> OrbColor.FIRE
                'L' -> OrbColor.LIGHT
                'H' -> OrbColor.HEART
                'D' -> OrbColor.DARK
                'G' -> OrbColor.WOOD
                'W' -> OrbColor.WATER
                else -> error("Unknown fixture color")
            }, if (index in setOf(2, 26)) setOf(OrbModifier.ENHANCED) else emptySet()) })
}
