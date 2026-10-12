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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.atomic.AtomicReference

class BoardSolverTest {
    @Test
    fun cancellingFromAParallelWorkerCancelsTheWholeSearch() = runBlocking {
        val worker = kotlinx.coroutines.Job(coroutineContext[kotlinx.coroutines.Job]!!)
        val searchJob = AtomicReference<Job>()
        val cancelledChild = AtomicReference<Job>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                val job = context[Job]!!
                searchJob.compareAndSet(null, job)
                Dispatchers.Default.dispatch(context, Runnable {
                    // Cancel an exploration child, independent of dispatch order or count.
                    if (job !== searchJob.get() && cancelledChild.compareAndSet(null, job)) job.cancel()
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
            assertNotNull("The solve must have started a parallel exploration", cancelledChild.get())
            cancelledChild.get().join()
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
        val solver = BoardSolver()
        val expected = solver.solve(input, options)
        val results = coroutineScope {
            List(4) { async { solver.solve(input, options) } }.awaitAll()
        }
        results.forEach { assertEquals(expected, it) }
    }

    private val board = Board(columns = 4, rows = 3, orbs = listOf(
        Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED)),
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
            val options = SolveOptions(maxOrbMoves = 4, beamWidth = 24, goal = goal)
            val result = BoardSolver().solve(board, options)
            assertValidSolution(board, options, result)
        }
        assertEquals(original, board.orbs)
    }

    @Test
    fun zeroMoveSearchAnalyzesExistingBoardWithoutRejectingAFeasibleGoal() = runBlocking {
        val goal = SolveSettings(OrbColor.FIRE, 2).toGoal()
        val result = BoardSolver().solve(board, SolveOptions(maxOrbMoves = 0, goal = goal))
        assertEquals(1, result.path.size)
        assertEquals(0, result.orbMoves)
        assertEquals(BoardAnalyzer.resolveCascades(board), result.predictedMatches)
        assertTrue(result.requirementsPossible)
        assertFalse(result.goalSatisfied)
        assertEquals(false, result.comboMinimumSatisfied)
        assertNull(result.matchPatternSatisfied)
    }

    @Test
    fun oneMoveSearchPreservesEveryMatchingColorAndScoredModifier() = runBlocking {
        for (color in OrbColor.entries.filter { it != OrbColor.UNKNOWN }) {
            val blocker = if (color == OrbColor.WATER) OrbColor.FIRE else OrbColor.WATER
            val input = Board(4, 1, listOf(
                Orb(color, setOf(OrbModifier.ENHANCED, OrbModifier.WEAKENED)),
                Orb(blocker), Orb(color, setOf(OrbModifier.WEAKENED)), Orb(color, setOf(OrbModifier.WEAKENED)),
            ))
            val options = SolveOptions(maxOrbMoves = 1, goal = SolveSettings(targetComboCount = 1).toGoal())
            val result = BoardSolver().solve(input, options)
            assertValidSolution(input, options, result)
            assertTrue("No combo found for $color", result.goalSatisfied)
            assertEquals(1, result.orbMoves)
            val match = result.predictedMatches.matches.single()
            assertEquals(color, match.color)
            assertEquals(1, match.enhancedOrbCount)
            assertEquals(3, match.weakenedOrbCount)
        }
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
    fun provenImpossibleRequirementsReturnWithoutExploringAndDoNotRejectPossibleOnes() = runBlocking {
        val insufficientColor = Board(4, 1, "FFFH".map { Orb(if (it == 'F') OrbColor.FIRE else OrbColor.HEART) })
        for (color in listOf(OrbColor.FIRE, OrbColor.LIGHT)) {
            val request = SolveSettings(color, 1, MatchPattern.TPA).toGoal()
            val result = BoardSolver().solve(insufficientColor, SolveOptions(goal = request))
            assertFalse(result.requirementsPossible)
            assertFalse(result.goalSatisfied)
            assertEquals(0, result.orbMoves)
            assertEquals(1, result.comboCount)
            assertEquals(0, result.focusedPatternMatchCount)
        }
        // Six orbs cannot produce three deterministic combos, even across multiple cascades.
        val tooFewCombos = Board(3, 2, List(6) { Orb(OrbColor.FIRE) })
        val comboResult = BoardSolver().solve(tooFewCombos, SolveOptions(goal = SolveSettings(targetComboCount = 3).toGoal()))
        assertFalse(comboResult.requirementsPossible)
        val possible = Board(4, 1, List(4) { Orb(OrbColor.FIRE) })
        val request = SolveSettings(OrbColor.FIRE, matchPatternFocus = MatchPattern.TPA).toGoal()
        assertTrue(BoardSolver().solve(possible, SolveOptions(goal = request)).requirementsPossible)
    }

    @Test
    fun selectedComboMinimumWinsOverAHigherDamageRoute() = runBlocking {
        val input = Board(6, 2, ("FFFFFF" + "WWWHHH").mapIndexed { index, code -> Orb(when (code) {
            'F' -> OrbColor.FIRE
            'W' -> OrbColor.WATER
            else -> OrbColor.HEART
        }, if (index == 4) setOf(OrbModifier.WEAKENED) else emptySet()) })
        val unconstrainedOptions = SolveOptions(1, 64, SolveSettings(OrbColor.FIRE).toGoal())
        val constrainedOptions = unconstrainedOptions.copy(goal = SolveSettings(OrbColor.FIRE, 3).toGoal())
        val unconstrained = BoardSolver().solve(input, unconstrainedOptions)
        val constrained = BoardSolver().solve(input, constrainedOptions)
        assertValidSolution(input, unconstrainedOptions, unconstrained)
        assertValidSolution(input, constrainedOptions, constrained)
        assertEquals(2, unconstrained.comboCount)
        assertEquals(3, constrained.comboCount)
        assertTrue(constrained.goalSatisfied)
        assertEquals(true, constrained.comboMinimumSatisfied)
        assertTrue(ColorDamageEstimate.scoreRelativeDamage(unconstrained.predictedMatches, OrbColor.FIRE) >
            ColorDamageEstimate.scoreRelativeDamage(constrained.predictedMatches, OrbColor.FIRE))
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
