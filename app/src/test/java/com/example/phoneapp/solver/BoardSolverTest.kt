package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BoardSolverTest {
    private val board = Board(columns = 4, rows = 3, orbs = listOf(
        Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED, OrbModifier.LOCKED)),
        Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD),
        Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT),
        Orb(OrbColor.WATER), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT), Orb(OrbColor.DARK),
    ))

    @Test
    fun routesReplayToReportedMatchesAndRespectSearchBoundsForEveryGoal() = runBlocking {
        val goals = listOf(MaximizeCombos, MaximizeColorDamage(OrbColor.FIRE),
            MaximizeMatchPattern(MatchPattern.TPA, OrbColor.WOOD),
            TargetComboCount(2), TargetComboCount(2, OrbColor.FIRE, MatchPattern.L_SHAPE))
        val original = board.orbs.toList()
        for (goal in goals) {
            val result = BoardSolver().solve(board, SolveOptions(maxOrbMoves = 4, beamWidth = 24, goal = goal))
            assertTrue(result.path.isNotEmpty())
            assertTrue(result.orbMoves <= 4)
            val replay = board.orbs.toMutableList()
            result.path.forEach { board.indexOf(it) }
            result.path.zipWithNext().forEach { (from, to) ->
                assertEquals(1, abs(from.row - to.row) + abs(from.column - to.column))
                val fromIndex = board.indexOf(from)
                val toIndex = board.indexOf(to)
                val held = replay[fromIndex]
                replay[fromIndex] = replay[toIndex]
                replay[toIndex] = held
            }
            val analysis = BoardAnalyzer.resolveCascades(board.copy(orbs = replay))
            assertEquals(analysis, result.predictedMatches)
            assertEquals(goal.evaluate(analysis).satisfied, result.goalSatisfied)
            assertEquals(goal.preferredColor, result.preferredColor)
            assertEquals(goal.matchPatternFocus, result.matchPatternFocus)
            assertEquals(goal.targetComboCount, result.targetComboCount)
            assertEquals(goal.targetComboCount?.let { analysis.comboCount >= it }, result.comboMinimumSatisfied)
            assertEquals(goal.matchPatternFocus?.countMatches(analysis, goal.preferredColor), result.focusedPatternMatchCount)
            assertEquals(goal.matchPatternFocus?.let { it.countMatches(analysis, goal.preferredColor) > 0 }, result.matchPatternSatisfied)
        }
        assertEquals(original, board.orbs)
    }

    @Test
    fun zeroMoveSearchAnalyzesExistingBoardAndReportsImpossibleGoal() = runBlocking {
        val goal = TargetComboCount(99, OrbColor.WOOD, MatchPattern.VDP)
        val result = BoardSolver().solve(board, SolveOptions(maxOrbMoves = 0, goal = goal))
        assertEquals(1, result.path.size)
        assertEquals(0, result.orbMoves)
        assertEquals(BoardAnalyzer.resolveCascades(board), result.predictedMatches)
        assertFalse(result.goalSatisfied)
        assertEquals(false, result.comboMinimumSatisfied)
        assertEquals(false, result.matchPatternSatisfied)
    }

    @Test
    fun oneMoveFixtureFindsAvailableComboWithoutChangingModifiers() = runBlocking {
        val input = Board(columns = 4, rows = 1, orbs = listOf(
            Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.WATER),
            Orb(OrbColor.FIRE), Orb(OrbColor.FIRE)))
        val result = BoardSolver().solve(input, SolveOptions(maxOrbMoves = 1, goal = TargetComboCount(1)))
        assertTrue(result.goalSatisfied)
        assertEquals(1, result.orbMoves)
        assertEquals(1, result.predictedMatches.matches.single().enhancedOrbCount)
    }

    @Test
    fun cancellingAnActiveSearchPropagatesCancellation() = runBlocking {
        // Cancel from the first evaluation, so this tests an active search
        // without depending on machine speed or a wall-clock delay.
        val parentJob = coroutineContext[kotlinx.coroutines.Job]!!
        val worker = kotlinx.coroutines.Job(parentJob)
        var evaluated = false
        val goal = SolveGoal { analysis ->
            evaluated = true
            worker.cancel()
            MaximizeCombos.evaluate(analysis)
        }
        val search = async(worker, start = CoroutineStart.DEFAULT) {
            BoardSolver().solve(board, SolveOptions(goal = goal))
        }
        try {
            search.await()
            fail("Cancelled search must not return a route")
        } catch (_: CancellationException) {
            assertTrue(evaluated)
        } finally {
            worker.cancel()
        }
    }

    @Test
    fun searchOptionsRejectInvalidBounds() {
        for (options in listOf<() -> SolveOptions>({ SolveOptions(maxOrbMoves = -1) }, { SolveOptions(beamWidth = 0) })) {
            assertThrows(IllegalArgumentException::class.java) { options() }
        }
    }
}
