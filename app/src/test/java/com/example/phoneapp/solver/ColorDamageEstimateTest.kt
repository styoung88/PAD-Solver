package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorDamageEstimateTest {
    @Test
    fun weakenedPenaltiesAndEnhancementCancellationAreLocalToEachMatch() {
        val cells = (0 until 3).map { BoardPosition(0, it) }.toSet()
        fun score(plus: Int, minus: Int) = ColorDamageEstimate.scoreRelativeDamage(
            BoardAnalysis(listOf(OrbMatch(OrbColor.FIRE, cells, plus, minus))), OrbColor.FIRE)
        assertEquals(800L, score(0, 1))
        assertEquals(0L, score(0, 2))
        assertEquals(1600L, score(1, 1))
        assertEquals(1696L, score(2, 1))
        assertEquals(800L, score(1, 2))
        val analysis = BoardAnalysis(listOf(OrbMatch(OrbColor.FIRE, cells, 0, 2),
            OrbMatch(OrbColor.FIRE, cells, 1, 0)))
        assertEquals(2, analysis.comboCount)
        assertEquals(2120L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE))
    }

    @Test
    fun matchSizeComboAndEnhancedFactorsHaveIndependentlyCalculatedScores() {
        // 1600 × size factor 1.25 × combo factor 1.5 × enhancement factor 1.12 = 3360.
        val wood = match(OrbColor.WOOD, 4, 2)
        val fire = match(OrbColor.FIRE, 3)
        val analysis = BoardAnalysis(listOf(wood), listOf(listOf(wood), listOf(fire, match(OrbColor.HEART, 3))))
        assertEquals(3360L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.WOOD))
        assertEquals(2400L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE))
        assertEquals(0L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.WATER))
    }

    @Test
    fun enhancedBonusesApplyToTheirIndividualMatchesInsteadOfTheWholeColor() {
        // Two combos: (1 + 1.18) × 1.25 × 1600 = 4360.
        val analysis = BoardAnalysis(listOf(match(OrbColor.WOOD, 3, 3), match(OrbColor.WOOD, 3)))
        assertEquals(4360L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.WOOD))
    }

    private fun match(color: OrbColor, size: Int, enhanced: Int = 0) =
        OrbMatch(color, (0 until size).map { BoardPosition(0, it) }.toSet(), enhanced)
}
