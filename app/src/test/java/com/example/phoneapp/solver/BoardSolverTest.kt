package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicInteger

class BoardSolverTest {
    @Test
    fun cancellingFromAParallelWorkerCancelsTheWholeSearch() = runBlocking {
        val worker = kotlinx.coroutines.Job(coroutineContext[kotlinx.coroutines.Job]!!)
        val starts = AtomicInteger()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                Dispatchers.Default.dispatch(context, Runnable {
                    // Initial search dispatch, then each of its two exploration workers.
                    if (starts.incrementAndGet() == 3) worker.cancel()
                    block.run()
                })
            }
        }
        val input = Board(6, 2, ("FFFWWW" + "FFWWFF")
            .map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.WATER) })
        val search = async(worker) {
            BoardSolver(dispatcher).solve(input, SolveOptions(8, 24,
                SolveSettings(OrbColor.FIRE, matchPatternFocus = MatchPattern.TPA).toGoal()))
        }
        try {
            search.await()
            fail("Cancelling either exploration must cancel the solve")
        } catch (_: CancellationException) {
            worker.join()
            assertTrue(starts.get() >= 3)
            assertFalse(worker.children.any { it.isActive })
        } finally {
            worker.cancel()
        }
    }

    @Test
    fun concurrentSearchesProduceIndependentDeterministicResults() = runBlocking {
        val input = Board(6, 2, ("FFFWWW" + "FFWWFF")
            .map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.WATER) })
        val options = SolveOptions(8, 24, SolveSettings(OrbColor.FIRE, matchPatternFocus = MatchPattern.TPA).toGoal())
        val expected = BoardSolver().solve(input, options)
        val results = coroutineScope {
            List(4) { async { BoardSolver().solve(input, options) } }.awaitAll()
        }
        results.forEach { assertEquals(expected, it) }
    }

    private val board = Board(columns = 4, rows = 3, orbs = listOf(
        Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED, OrbModifier.LOCKED)),
        Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD),
        Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT),
        Orb(OrbColor.WATER), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT), Orb(OrbColor.DARK),
    ))

    @Test
    fun routesReplayToReportedMatchesAndRespectSearchBoundsForEveryGoal() = runBlocking {
        val goals = listOf(MaximizeCombos, SolveSettings(OrbColor.FIRE).toGoal(),
            SolveSettings(OrbColor.WOOD, matchPatternFocus = MatchPattern.TPA).toGoal(),
            SolveSettings(targetComboCount = 2).toGoal(), SolveSettings(OrbColor.FIRE, 2, MatchPattern.L_SHAPE).toGoal())
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
        val goal = SolveSettings(OrbColor.WOOD, 99, MatchPattern.VDP).toGoal()
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
        val result = BoardSolver().solve(input, SolveOptions(maxOrbMoves = 1, goal = SolveSettings(targetComboCount = 1).toGoal()))
        assertTrue(result.goalSatisfied)
        assertEquals(1, result.orbMoves)
        assertEquals(1, result.predictedMatches.matches.single().enhancedOrbCount)
    }

    @Test
    fun completedRoutesPreferTheirActualScoreOverPatternPotential() = runBlocking {
        val input = Board(6, 1, "FFFWWF".map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.WATER) })
        val goal = object : SolveGoal {
            override fun evaluate(analysis: BoardAnalysis) = GoalEvaluation(
                primaryScore = analysis.comboCount.toLong(),
                secondaryScore = ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE).toInt(),
            )
            override fun patternPotential(board: Board): Int =
                if (board.orbs.last().color == OrbColor.FIRE) 10_000 else 0
        }
        val result = BoardSolver().solve(input, SolveOptions(maxOrbMoves = 2, beamWidth = 64, goal = goal))
        assertEquals(1, result.comboCount)
        assertEquals(4, result.predictedMatches.matches.single().size)
        assertEquals(2000L, ColorDamageEstimate.scoreRelativeDamage(result.predictedMatches, OrbColor.FIRE))
        assertEquals(2, result.orbMoves)
    }

    @Test
    fun theBestAttemptIsRecordedEvenWhenTheSearchFrontierPrunesIt() = runBlocking {
        val input = Board(6, 1, "FFFWFF".map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.WATER) })
        val goal = object : SolveGoal {
            override fun evaluate(analysis: BoardAnalysis) = GoalEvaluation(
                requirements = listOf(RequirementEvaluation(analysis.comboCount, 99)),
                primaryScore = ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE),
            )
            override fun patternPotential(board: Board): Int =
                if (board.orbs[3].color == OrbColor.WATER) 10_000 else 0
        }
        val result = BoardSolver().solve(input, SolveOptions(maxOrbMoves = 1, beamWidth = 1, goal = goal))
        assertFalse(result.goalSatisfied)
        assertEquals(4, result.predictedMatches.matches.single().size)
        assertEquals(2000L, ColorDamageEstimate.scoreRelativeDamage(result.predictedMatches, OrbColor.FIRE))
        assertEquals(1, result.orbMoves)
    }

    @Test
    fun equivalentLayoutsShareEvaluationAcrossDragPositionsAndDepths() = runBlocking {
        var evaluations = 0
        val goal = SolveGoal { analysis ->
            evaluations++
            MaximizeCombos.evaluate(analysis)
        }
        val input = Board(4, 1, List(4) { Orb(OrbColor.FIRE) })
        val result = BoardSolver().solve(input, SolveOptions(maxOrbMoves = 4, goal = goal))
        assertEquals(1, evaluations)
        assertEquals(1, result.comboCount)
        assertEquals(0, result.orbMoves)
    }

    @Test
    fun provenImpossibleRequirementsReturnWithoutExploringAndDoNotRejectPossibleOnes() = runBlocking {
        val insufficientColor = Board(4, 1, "FFFH".map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.HEART) })
        val request = SolveSettings(OrbColor.FIRE, matchPatternFocus = MatchPattern.TPA).toGoal()
        val result = BoardSolver().solve(insufficientColor, SolveOptions(goal = request))
        assertFalse(result.requirementsPossible)
        assertFalse(result.goalSatisfied)
        assertEquals(0, result.orbMoves)
        // Six orbs cannot produce three deterministic combos, even across multiple cascades.
        val tooFewCombos = Board(3, 2, List(6) { Orb(OrbColor.FIRE) })
        val comboResult = BoardSolver().solve(tooFewCombos, SolveOptions(goal = SolveSettings(targetComboCount = 3).toGoal()))
        assertFalse(comboResult.requirementsPossible)
        val possible = Board(4, 1, List(4) { Orb(OrbColor.FIRE) })
        assertTrue(BoardSolver().solve(possible, SolveOptions(goal = request)).requirementsPossible)
    }

    @Test
    fun existingCombosDoNotMakeAnAbsentColorPatternPossible() = runBlocking {
        val input = Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .map { Orb(if (it == 'D') OrbColor.DARK else OrbColor.FIRE) })
        val result = BoardSolver().solve(input, SolveOptions(
            goal = SolveSettings(OrbColor.LIGHT, 7, MatchPattern.L_SHAPE).toGoal()))
        assertEquals(3, result.comboCount)
        assertEquals(0, result.orbMoves)
        assertFalse(result.requirementsPossible)
        assertFalse(result.goalSatisfied)
        assertEquals(0, result.focusedPatternMatchCount)
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
