package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboPatternRegressionTest {
    @Test
    fun nonbindingMinimumRetainsDamageAndRouteCostOnTheDarkTpaFixture() = runBlocking {
        val enhanced = setOf(0, 3, 4, 5, 6, 8, 9, 10, 11, 12, 15, 17, 20, 21, 29)
        val board = Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .mapIndexed { index, color -> Orb(
                if (color == 'D') OrbColor.DARK else OrbColor.FIRE,
                if (index in enhanced) setOf(OrbModifier.ENHANCED) else emptySet(),
            ) })
        val settings = SolveSettings(OrbColor.DARK, matchPatternFocus = MatchPattern.TPA)
        val options = SolveOptions(goal = settings.toGoal())
        val baseline = BoardSolver().solve(board, options)
        val minimumGoal = settings.copy(targetComboCount = 5).toGoal()
        assertTrue("The original route already meets the added minimum", minimumGoal.evaluate(baseline.predictedMatches).satisfied)
        val constrainedOptions = options.copy(goal = minimumGoal)
        val constrained = BoardSolver().solve(board, constrainedOptions)
        val baselineScore = minimumGoal.evaluate(baseline.predictedMatches)
        val constrainedScore = minimumGoal.evaluate(constrained.predictedMatches)
        assertValidSolution(board, options, baseline)
        assertValidSolution(board, constrainedOptions, constrained)
        assertTrue(constrained.goalSatisfied)
        assertTrue("Recover at least the better damage route previously found with a minimum", baselineScore.primaryScore >= 12888L)
        assertEquals("A nonbinding minimum should produce the same final score", baselineScore, constrainedScore)
        assertEquals(baseline.orbMoves, constrained.orbMoves)
        val turns: (BoardSolution) -> Int = { solution ->
            solution.path.windowed(3).count { (first, middle, last) ->
                middle.row - first.row != last.row - middle.row ||
                    middle.column - first.column != last.column - middle.column
            }
        }
        assertEquals(turns(baseline), turns(constrained))
    }

    @Test
    fun fiveComboMinimumStillFindsWoodTpaOnScreenshotBoard() = runBlocking {
        val board = Board(orbs = listOf(
            Orb(OrbColor.FIRE), Orb(OrbColor.HEART, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.DARK), Orb(OrbColor.WOOD), Orb(OrbColor.DARK), Orb(OrbColor.WATER),
            Orb(OrbColor.LIGHT), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.WOOD),
            Orb(OrbColor.HEART), Orb(OrbColor.WATER), Orb(OrbColor.HEART), Orb(OrbColor.LIGHT), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD),
            Orb(OrbColor.FIRE), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD), Orb(OrbColor.HEART), Orb(OrbColor.DARK), Orb(OrbColor.LIGHT),
            Orb(OrbColor.DARK), Orb(OrbColor.DARK), Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT),
        ))
        val options = SolveOptions(goal = SolveSettings(OrbColor.WOOD, 5, MatchPattern.TPA).toGoal())
        val minimumFive = BoardSolver().solve(board, options)
        assertValidSolution(board, options, minimumFive)
        assertTrue("The five-combo target and Wood TPA should both be satisfied", minimumFive.goalSatisfied)
        assertTrue(minimumFive.comboCount >= 5)
        assertEquals(1, minimumFive.focusedPatternMatchCount)
    }
}
