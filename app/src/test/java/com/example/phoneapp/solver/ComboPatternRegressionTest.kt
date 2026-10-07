package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboPatternRegressionTest {
    @Test
    fun nonbindingMinimumKeepsScoreAndRouteCostAcrossDifferentBoardsAndColors() = runBlocking {
        for (seed in 0..7) {
            val random = kotlin.random.Random(seed)
            val colors = listOf(OrbColor.FIRE, OrbColor.WATER, OrbColor.DARK)
            val board = Board(4, 3, List(12) { index ->
                Orb(colors[random.nextInt(colors.size)],
                    if (index % 3 == 0) setOf(OrbModifier.ENHANCED) else emptySet())
            })
            for (color in listOf(OrbColor.FIRE, OrbColor.WATER)) {
                val settings = SolveSettings(color)
                val baseline = BoardSolver().solve(board, SolveOptions(8, 24, settings.toGoal()))
                assertTrue(baseline.comboCount > 0)
                val goal = settings.copy(targetComboCount = baseline.comboCount).toGoal()
                val constrained = BoardSolver().solve(board, SolveOptions(8, 24, goal))
                assertEquals("seed=$seed color=$color", goal.evaluate(baseline.predictedMatches),
                    goal.evaluate(constrained.predictedMatches))
                assertEquals(baseline.orbMoves, constrained.orbMoves)
            }
        }
    }

    @Test
    fun darkTpaComboMinimumPreservesOrImprovesTheFinalScore() = runBlocking {
        val enhanced = setOf(0, 3, 4, 5, 6, 8, 9, 10, 11, 12, 15, 17, 20, 21, 29)
        val board = Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .mapIndexed { index, color -> Orb(
                if (color == 'D') OrbColor.DARK else OrbColor.FIRE,
                if (index in enhanced) setOf(OrbModifier.ENHANCED) else emptySet(),
            ) })
        val settings = SolveSettings(OrbColor.DARK, matchPatternFocus = MatchPattern.TPA)
        val baseline = BoardSolver().solve(board, SolveOptions(goal = settings.toGoal()))
        val minimumGoal = settings.copy(targetComboCount = 5).toGoal()
        assertTrue("The original route already meets the added minimum", minimumGoal.evaluate(baseline.predictedMatches).satisfied)
        val constrained = BoardSolver().solve(board, SolveOptions(goal = minimumGoal))
        val baselineScore = minimumGoal.evaluate(baseline.predictedMatches)
        val constrainedScore = minimumGoal.evaluate(constrained.predictedMatches)
        for (solution in listOf(baseline, constrained)) {
            val replay = board.orbs.toMutableList()
            solution.path.zipWithNext().forEach { (from, to) ->
                assertEquals(1, kotlin.math.abs(from.row - to.row) + kotlin.math.abs(from.column - to.column))
                val fromIndex = board.indexOf(from)
                val toIndex = board.indexOf(to)
                val held = replay[fromIndex]
                replay[fromIndex] = replay[toIndex]
                replay[toIndex] = held
            }
            assertEquals(BoardAnalyzer.resolveCascades(board.copy(orbs = replay)), solution.predictedMatches)
            assertTrue(minimumGoal.evaluate(solution.predictedMatches).satisfied)
        }
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
        for (minimum in listOf(1, 6)) {
            val goal = settings.copy(targetComboCount = minimum).toGoal()
            val result = BoardSolver().solve(board, SolveOptions(goal = goal))
            assertEquals(goal.evaluate(baseline.predictedMatches), goal.evaluate(result.predictedMatches))
            assertEquals(baseline.orbMoves, result.orbMoves)
            assertEquals(turns(baseline), turns(result))
        }
    }

    @Test
    fun fiveComboMinimumStillFindsWoodTpaOnScreenshotBoard() {
        runBlocking {
            val board = Board(orbs = listOf(
                Orb(OrbColor.FIRE), Orb(OrbColor.HEART, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.DARK), Orb(OrbColor.WOOD), Orb(OrbColor.DARK), Orb(OrbColor.WATER),
                Orb(OrbColor.LIGHT), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.WOOD),
                Orb(OrbColor.HEART), Orb(OrbColor.WATER), Orb(OrbColor.HEART), Orb(OrbColor.LIGHT), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD),
                Orb(OrbColor.FIRE), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD), Orb(OrbColor.HEART), Orb(OrbColor.DARK), Orb(OrbColor.LIGHT),
                Orb(OrbColor.DARK), Orb(OrbColor.DARK), Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT),
            ))
            val patternOnly = BoardSolver().solve(
                board,
                SolveOptions(goal = SolveSettings(OrbColor.WOOD, matchPatternFocus = MatchPattern.TPA).toGoal()),
            )
            assertTrue("The screenshot board has a Wood TPA route", patternOnly.goalSatisfied)

            val minimumFive = BoardSolver().solve(
                board,
                SolveOptions(goal = SolveSettings(OrbColor.WOOD, 5, MatchPattern.TPA).toGoal()),
            )
            assertTrue("The five-combo target and Wood TPA should both be satisfied", minimumFive.goalSatisfied)
            assertTrue(minimumFive.comboCount >= 5)
            assertEquals(1, minimumFive.focusedPatternMatchCount)
        }
    }
}
