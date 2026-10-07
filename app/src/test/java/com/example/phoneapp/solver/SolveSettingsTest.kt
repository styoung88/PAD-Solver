package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import org.junit.Assert.*
import org.junit.Test

class SolveSettingsTest {
    private val analysis = BoardAnalysis(listOf(match(OrbColor.WOOD, 4), match(OrbColor.FIRE, 3)))

    @Test
    fun requirementsDoNotChangeTheSelectedDamageOrComboObjective() {
        for (color in listOf(null, OrbColor.WOOD)) {
            for (target in listOf(null, 1)) {
                for (pattern in listOf(null, MatchPattern.TPA)) {
                    val settings = SolveSettings(color, target, pattern)
                    val goal = settings.toGoal()
                    val result = goal.evaluate(analysis)
                    assertTrue(result.satisfied)
                    assertEquals(if (color == null) 2L else 2500L, result.primaryScore)
                    assertEquals(2, result.secondaryScore)
                    assertEquals(color, goal.preferredColor)
                    assertEquals(target, goal.targetComboCount)
                    assertEquals(pattern, goal.matchPatternFocus)
                }
            }
        }
    }

    @Test
    fun aRequiredPatternDoesNotRewardExtraPatternsAheadOfDamage() {
        val goal = SolveSettings(OrbColor.WOOD, matchPatternFocus = MatchPattern.TPA).toGoal()
        val twoTpas = BoardAnalysis(listOf(match(OrbColor.WOOD, 4), match(OrbColor.WOOD, 4)))
        val higherDamage = BoardAnalysis(listOf(match(OrbColor.WOOD, 4), match(OrbColor.WOOD, 6), match(OrbColor.FIRE, 3)))
        assertEquals(5000L, goal.evaluate(twoTpas).primaryScore)
        assertEquals(7200L, goal.evaluate(higherDamage).primaryScore)
        assertTrue(goal.evaluate(higherDamage) > goal.evaluate(twoTpas))
    }

    @Test
    fun allRequirementsMustPassAndUnmetComboProgressRetainsPriority() {
        val goal = SolveSettings(OrbColor.WOOD, 3, MatchPattern.TPA).toGoal()
        val belowMinimum = goal.evaluate(analysis)
        assertFalse(belowMinimum.satisfied)
        assertEquals(listOf(RequirementEvaluation(2, 3), RequirementEvaluation(1, 1)), belowMinimum.requirements)
        val threeCombosNoTpa = goal.evaluate(BoardAnalysis(List(3) { match(OrbColor.WOOD, 3) }))
        assertFalse(threeCombosNoTpa.satisfied)
        assertTrue(threeCombosNoTpa > belowMinimum)
        val fourCombos = goal.evaluate(BoardAnalysis(List(4) { match(OrbColor.WOOD, 3) }))
        assertTrue(fourCombos > threeCombosNoTpa)
        val oneComboWithTpa = goal.evaluate(BoardAnalysis(listOf(match(OrbColor.WOOD, 4))))
        val twoCombosNoTpa = goal.evaluate(BoardAnalysis(List(2) { match(OrbColor.WOOD, 3) }))
        assertTrue(twoCombosNoTpa > oneComboWithTpa)
    }

    @Test
    fun onlyAttackingColorsAndPositiveTargetsAreValidSettings() {
        for (color in OrbColor.attackingColors) assertEquals(color, SolveSettings(color, 6).toGoal().preferredColor)
        for (color in listOf(OrbColor.HEART, OrbColor.UNKNOWN)) {
            assertThrows(IllegalArgumentException::class.java) { SolveSettings(color) }
        }
        for (target in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) { SolveSettings(targetComboCount = target) }
        }
    }

    private fun match(color: OrbColor, size: Int): OrbMatch =
        OrbMatch(color, (0 until size).map { BoardPosition(0, it) }.toSet())
}
