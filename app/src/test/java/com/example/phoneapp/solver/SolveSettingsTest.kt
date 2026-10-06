package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolveSettingsTest {
    private val analysis = BoardAnalysis(listOf(
        OrbMatch(OrbColor.WOOD, (0..3).map { BoardPosition(0, it) }.toSet()),
        OrbMatch(OrbColor.FIRE, (0..2).map { BoardPosition(2, it) }.toSet()),
    ))

    @Test
    fun sharedSettingsPreserveGoalPriorityAndScores() {
        val cases = listOf(
            SolveSettings() to GoalEvaluation(true, primaryScore = 2),
            SolveSettings(OrbColor.WOOD) to GoalEvaluation(true, primaryScore = 2500, secondaryScore = 2),
            SolveSettings(matchPatternFocus = MatchPattern.TPA) to
                GoalEvaluation(true, primaryScore = 1, secondaryScore = 2),
            SolveSettings(OrbColor.WOOD, matchPatternFocus = MatchPattern.TPA) to
                GoalEvaluation(true, primaryScore = 1, secondaryScore = 2500),
            SolveSettings(targetComboCount = 1) to GoalEvaluation(true, primaryScore = 2, secondaryScore = 2),
            SolveSettings(OrbColor.WOOD, 1) to GoalEvaluation(true, primaryScore = 2500, secondaryScore = 2),
            SolveSettings(targetComboCount = 1, matchPatternFocus = MatchPattern.TPA) to
                GoalEvaluation(true, primaryScore = 1, secondaryScore = 2),
            SolveSettings(OrbColor.WOOD, 1, MatchPattern.TPA) to
                GoalEvaluation(true, primaryScore = 1, secondaryScore = 2500),
            SolveSettings(OrbColor.WOOD, 3, MatchPattern.TPA) to
                GoalEvaluation(false, primaryScore = 2, secondaryScore = 2),
            SolveSettings(OrbColor.WOOD, 1, MatchPattern.CROSS) to
                GoalEvaluation(false, primaryCriterionSatisfied = true, primaryScore = 0, secondaryScore = 2500),
        )
        for ((settings, expected) in cases) {
            val goal = settings.toGoal()
            assertEquals(settings.toString(), expected, goal.evaluate(analysis))
            assertEquals(settings.preferredColor, goal.preferredColor)
            assertEquals(settings.targetComboCount, goal.targetComboCount)
            assertEquals(settings.matchPatternFocus, goal.matchPatternFocus)
        }
    }

    @Test
    fun onlyAttackingColorsAndPositiveTargetsAreValidSettings() {
        for (color in OrbColor.attackingColors) {
            assertEquals(color, SolveSettings(color, 6).toGoal().preferredColor)
        }
        for (color in listOf(OrbColor.HEART, OrbColor.UNKNOWN)) {
            assertThrows(IllegalArgumentException::class.java) { SolveSettings(color) }
        }
        for (target in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) { SolveSettings(targetComboCount = target) }
        }
    }
}
