package com.example.phoneapp.solver

import com.example.phoneapp.board.Board
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboPatternRegressionTest {
    @Test
    fun fiveComboMinimumStillFindsWoodTpaOnScreenshotBoard() {
        runBlocking {
            val board = Board(orbs = listOf(
                Orb(OrbColor.FIRE), Orb(OrbColor.HEART, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.DARK), Orb(OrbColor.WOOD), Orb(OrbColor.DARK), Orb(OrbColor.WATER),
                Orb(OrbColor.LIGHT), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.FIRE), Orb(OrbColor.WATER), Orb(OrbColor.WOOD),
                Orb(OrbColor.HEART), Orb(OrbColor.WATER), Orb(OrbColor.HEART), Orb(OrbColor.LIGHT), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD),
                Orb(OrbColor.FIRE), Orb(OrbColor.FIRE), Orb(OrbColor.WOOD), Orb(OrbColor.HEART), Orb(OrbColor.DARK), Orb(OrbColor.LIGHT),
                Orb(OrbColor.DARK), Orb(OrbColor.DARK), Orb(OrbColor.FIRE, setOf(OrbModifier.ENHANCED)), Orb(OrbColor.LIGHT), Orb(OrbColor.WOOD), Orb(OrbColor.LIGHT),
            ))
            val patternOnly = BoardSolver().solve(
                board,
                SolveOptions(goal = MaximizeMatchPattern(MatchPattern.TPA, OrbColor.WOOD)),
            )
            assertTrue("The screenshot board has a Wood TPA route", patternOnly.goalSatisfied)

            val minimumFive = BoardSolver().solve(
                board,
                SolveOptions(goal = TargetComboCount(5, OrbColor.WOOD, MatchPattern.TPA)),
            )
            assertTrue("The five-combo target and Wood TPA should both be satisfied", minimumFive.goalSatisfied)
            assertTrue(minimumFive.comboCount >= 5)
            assertEquals(1, minimumFive.focusedPatternMatchCount)
        }
    }
}
