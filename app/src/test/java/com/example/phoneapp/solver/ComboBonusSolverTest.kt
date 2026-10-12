package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.game.GameVariant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboBonusSolverTest {
    @Test
    fun swappingIdenticalColorsCanMoveABonusIntoAMatchAndReachAnOtherwiseImpossibleMinimum() = runBlocking {
        val original = Board(3, 2, listOf(
            Orb(OrbColor.FIRE), Orb(OrbColor.FIRE), Orb(OrbColor.FIRE),
            Orb(OrbColor.FIRE, setOf(OrbModifier.COMBO_BONUS, OrbModifier.ENHANCED)),
            Orb(OrbColor.WOOD), Orb(OrbColor.HEART),
        ))
        assertEquals(1, BoardAnalyzer.resolveCascades(original).comboCount)
        val options = SolveOptions(1, 12, SolveSettings(targetComboCount = 2, game = GameVariant.PAD_ZERO).toGoal())
        val result = BoardSolver().solve(original, options)
        assertValidSolution(original, options, result)
        assertTrue(result.requirementsPossible)
        assertTrue(result.goalSatisfied)
        assertEquals(1, result.orbMoves)
        assertEquals(1, result.predictedMatches.physicalComboCount)
        assertEquals(1, result.predictedMatches.bonusComboCount)
        assertEquals(1, result.predictedMatches.allMatches.single().enhancedOrbCount)
        val replay = original.orbs.toMutableList()
        result.path.zipWithNext().forEach { (from, to) ->
            val a = original.indexOf(from)
            val b = original.indexOf(to)
            val held = replay[a]; replay[a] = replay[b]; replay[b] = held
        }
        // The colors are identical before and after. The flag must distinguish
        // these layouts in both the drag frontier and the evaluation cache.
        assertEquals(original.orbs.map { it.color }, replay.map { it.color })
    }

    @Test
    fun comboTargetsCanExceedTheOldCellCountLimit() = runBlocking {
        val board = Board(3, 1, List(3) { Orb(OrbColor.FIRE, setOf(OrbModifier.COMBO_BONUS)) })
        val options = SolveOptions(0, 12, SolveSettings(targetComboCount = 4, game = GameVariant.PAD_ZERO).toGoal())
        val result = BoardSolver().solve(board, options)
        assertValidSolution(board, options, result)
        assertTrue(result.requirementsPossible)
        assertTrue(result.goalSatisfied)
        assertEquals(4, result.comboCount)
    }
}
