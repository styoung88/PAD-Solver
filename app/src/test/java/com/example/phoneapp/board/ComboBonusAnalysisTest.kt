package com.example.phoneapp.board

import com.example.phoneapp.solver.ColorDamageEstimate
import com.example.phoneapp.solver.ComboMinimum
import com.example.phoneapp.solver.comboCapacity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboBonusAnalysisTest {
    @Test
    fun multipleBonusOrbsInOneMatchAndSeparateMatchesAddIndividually() {
        val board = board(listOf("FFF", "WWW"), setOf(0, 1, 4))
        val result = BoardAnalyzer.resolveCascades(board)
        assertEquals(2, result.physicalComboCount)
        assertEquals(3, result.bonusComboCount)
        assertEquals(5, result.comboCount)
        assertEquals(listOf(2, 1), result.allMatches.map { it.comboBonusOrbCount })
        assertTrue(ComboMinimum(5).evaluate(result).satisfied)
        assertFalse(ComboMinimum(6).evaluate(result).satisfied)
    }

    @Test
    fun crossingRunsCountEachMarkedOrbOnceAndUnmatchedMarkersStayInactive() {
        val result = BoardAnalyzer.analyze(board(listOf("GFG", "FFF", "GFG"), setOf(0, 4, 5)))
        assertEquals(1, result.physicalComboCount)
        assertEquals(2, result.bonusComboCount)
        assertEquals(3, result.comboCount)
        assertEquals(5, result.matches.single().size)
    }

    @Test
    fun bonusOrbsSurviveGravityAndCountOnlyInTheirOwnClearWave() {
        // The W cross clears first. Three F orbs in each outer column then fall
        // together; the marked G remains alone at the bottom of the middle column.
        val result = BoardAnalyzer.resolveCascades(board(listOf("FWF", "WWW", "FWF", "FGF"), setOf(0, 4, 10, 11)))
        assertEquals(listOf(1, 2), result.cascadeWaves.map { it.size })
        assertEquals(listOf(1, 2), result.cascadeWaves.map { wave -> wave.sumOf { it.comboBonusOrbCount } })
        assertEquals(3, result.physicalComboCount)
        assertEquals(3, result.bonusComboCount)
        assertEquals(6, result.comboCount)
        assertEquals(1, result.cascadeCount)
    }

    @Test
    fun inventoryCapacityIncludesBonusesOnlyForColorsWithEnoughOrbsToMatch() {
        val board = board(listOf("FFFW"), setOf(0, 1, 2, 3))
        assertEquals(4, board.comboCapacity)
        assertTrue(ComboMinimum(4).isPossible(board))
        assertFalse(ComboMinimum(5).isPossible(board))
    }

    @Test
    fun relativeDamageUsesEffectiveCombosWithoutCreatingExtraAttackingMatches() {
        val result = BoardAnalyzer.resolveCascades(board(listOf("FFF"), setOf(0, 1, 2)))
        assertEquals(1, result.allMatches.size)
        assertEquals(4, result.comboCount)
        // Three fire orbs: base 1x match * 1.75x combo factor, scaled by 1600.
        assertEquals(2800L, ColorDamageEstimate.scoreRelativeDamage(result, OrbColor.FIRE))
    }

    private fun board(rows: List<String>, bonuses: Set<Int>) = Board(
        columns = rows.first().length, rows = rows.size,
        orbs = rows.joinToString("").mapIndexed { index, code -> Orb(when (code) {
            'F' -> OrbColor.FIRE
            'W' -> OrbColor.WATER
            'G' -> OrbColor.WOOD
            'H' -> OrbColor.HEART
            else -> OrbColor.UNKNOWN
        }, if (index in bonuses) setOf(OrbModifier.COMBO_BONUS) else emptySet()) },
    )
}
