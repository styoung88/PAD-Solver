package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSizeSolverTest {
    @Test
    fun supportedBoardSizesFindAOneMoveMatchAtTheBottomRightEdge() = runBlocking {
        for (size in BoardSize.entries) {
            val cells = MutableList(size.cellCount) { Orb(OrbColor.UNKNOWN) }
            val bottom = (size.rows - 1) * size.columns
            cells[bottom + size.columns - 3] = Orb(OrbColor.FIRE)
            cells[bottom + size.columns - 2] = Orb(OrbColor.WATER)
            cells[bottom + size.columns - 1] = Orb(OrbColor.FIRE)
            cells[bottom - 2] = Orb(OrbColor.FIRE)
            val board = Board(size.columns, size.rows, cells)
            val options = SolveOptions(maxOrbMoves = 1, goal = SolveSettings(targetComboCount = 1).toGoal())
            val result = BoardSolver().solve(board, options)
            assertValidSolution(board, options, result)
            assertTrue("The edge match must be reachable on $size", result.goalSatisfied)
        }
    }

    @Test
    fun comboFeasibilityCountsEachColorSeparatelyAndIgnoresUnknownCells() {
        // Only Fire and Jammer have triples. Pairs and unknown cells cannot
        // increase capacity, even though the board contains twelve cells.
        val board = Board(4, 3, listOf(
            OrbColor.FIRE, OrbColor.FIRE, OrbColor.FIRE,
            OrbColor.JAMMER, OrbColor.JAMMER, OrbColor.JAMMER,
            OrbColor.WATER, OrbColor.WATER, OrbColor.WOOD, OrbColor.WOOD,
            OrbColor.UNKNOWN, OrbColor.UNKNOWN,
        ).map(::Orb))
        assertTrue(ComboMinimum(2).isPossible(board))
        assertFalse(ComboMinimum(3).isPossible(board))
    }
}
