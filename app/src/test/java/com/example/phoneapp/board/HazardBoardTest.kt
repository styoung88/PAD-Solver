package com.example.phoneapp.board

import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.solver.ColorDamageEstimate
import com.example.phoneapp.solver.SolveOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HazardBoardTest {
    @Test
    fun hazardsAreSeparateMatchingTypesAndContributeOnlyComboDamageMultiplier() {
        val types = listOf(OrbColor.JAMMER, OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB, OrbColor.FIRE)
        val board = Board(3, 5, types.flatMap { color -> List(3) { Orb(color) } })
        val analysis = BoardAnalyzer.resolveCascades(board)
        assertEquals(types, analysis.matches.map { it.color })
        assertEquals(5, analysis.comboCount)
        assertEquals(3200L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE))
        val mixed = Board(3, 1, listOf(Orb(OrbColor.POISON), Orb(OrbColor.LETHAL_POISON), Orb(OrbColor.POISON)))
        assertEquals(0, BoardAnalyzer.analyze(mixed).comboCount)
    }

    @Test
    fun cascadePreservesMinusAndLockOnSurvivingOrbs() {
        val board = Board(3, 4, listOf(
            Orb(OrbColor.FIRE, setOf(OrbModifier.WEAKENED, OrbModifier.LOCKED)),
            Orb(OrbColor.WATER), Orb(OrbColor.FIRE),
            Orb(OrbColor.JAMMER), Orb(OrbColor.WATER), Orb(OrbColor.JAMMER),
            Orb(OrbColor.JAMMER), Orb(OrbColor.WATER), Orb(OrbColor.JAMMER),
            Orb(OrbColor.JAMMER), Orb(OrbColor.FIRE), Orb(OrbColor.JAMMER),
        ))
        val analysis = BoardAnalyzer.resolveCascades(board)
        assertEquals(4, analysis.comboCount)
        assertEquals(OrbColor.FIRE, analysis.cascadeWaves[1].single().color)
        assertEquals(1, analysis.cascadeWaves[1].single().weakenedOrbCount)
        assertEquals(1400L, ColorDamageEstimate.scoreRelativeDamage(analysis, OrbColor.FIRE))
    }

    @Test
    fun expandedEncodingKeepsAllHazardsAndModifiersDuringSearch() = runBlocking {
        val colors = listOf(OrbColor.JAMMER, OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB, OrbColor.FIRE)
        val board = Board(4, 5, colors.flatMap { color -> listOf(
            Orb(color, setOf(OrbModifier.LOCKED, OrbModifier.WEAKENED)), Orb(OrbColor.WATER), Orb(color), Orb(color),
        ) })
        val result = BoardSolver().solve(board, SolveOptions(3, 24))
        val replay = board.orbs.toMutableList()
        for ((from, to) in result.path.zipWithNext()) {
            val a = board.indexOf(from)
            val b = board.indexOf(to)
            val held = replay[a]
            replay[a] = replay[b]
            replay[b] = held
        }
        assertEquals(BoardAnalyzer.resolveCascades(board.copy(orbs = replay)), result.predictedMatches)
    }
}
