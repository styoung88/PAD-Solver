package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BoardSizeSolverTest {
    @Test
    fun supportedBoardsUseRequestedMoveLimitsAndReplayLegally() = runBlocking {
        for ((size, limit) in listOf(BoardSize.SMALL to 30, BoardSize.STANDARD to 40, BoardSize.LARGE to 50)) {
            val board = Board(size.columns, size.rows, List(size.cellCount) {
                Orb(OrbColor.entries[it % 6])
            }.shuffled(Random(42)))
            val options = SolveOptions.forBoard(board, SolveSettings(matchPatternFocus = MatchPattern.ROW).toGoal()).copy(beamWidth = 24)
            assertEquals(limit, options.maxOrbMoves)
            assertEquals(192, SolveOptions.forBoard(board).beamWidth)
            for (selected in listOf(options, options.copy(maxOrbMoves = 2))) {
                val result = BoardSolver().solve(board, selected)
                assertTrue(result.orbMoves <= selected.maxOrbMoves)
                val replay = board.orbs.toMutableList()
                result.path.forEach { board.indexOf(it) }
                for ((from, to) in result.path.zipWithNext()) {
                    assertEquals(1, abs(from.row - to.row) + abs(from.column - to.column))
                    val first = board.indexOf(from)
                    val second = board.indexOf(to)
                    val held = replay[first]
                    replay[first] = replay[second]
                    replay[second] = held
                }
                val analysis = BoardAnalyzer.resolveCascades(board.copy(orbs = replay))
                assertEquals(analysis, result.predictedMatches)
                assertEquals(selected.goal.evaluate(analysis).satisfied, result.goalSatisfied)
            }
        }
    }

    @Test
    fun comboFeasibilityUsesEachBoardsInventory() {
        for ((size, capacity) in listOf(BoardSize.SMALL to 6, BoardSize.STANDARD to 10, BoardSize.LARGE to 14)) {
            val board = Board(size.columns, size.rows, List(size.cellCount) { Orb(OrbColor.FIRE) })
            assertTrue(ComboMinimum(capacity).isPossible(board))
            assertTrue(!ComboMinimum(capacity + 1).isPossible(board))
        }
    }
}
