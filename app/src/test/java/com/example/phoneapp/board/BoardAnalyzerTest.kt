package com.example.phoneapp.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardAnalyzerTest {
    @Test
    fun fourMoveRouteHasFourInitialMatchesAndThreeNoSkyfallCascades() {
        // Expected waves were independently traced by clearing runs and dropping columns.
        val original = board("FWLLDF", "DLDGLD", "DHFFWF", "DDHHFF", "HHDDDD")
        val path = listOf(11, 5, 4, 10, 9)
        val replay = original.orbs.toMutableList()
        for ((from, to) in path.zipWithNext()) {
            val held = replay[from]
            replay[from] = replay[to]
            replay[to] = held
        }
        val moved = original.copy(orbs = replay)
        assertEquals(board("FWLLLD", "DLDDGF", "DHFFWF", "DDHHFF", "HHDDDD"), moved)
        val analysis = BoardAnalyzer.resolveCascades(moved)
        val expectedWaves = listOf(
            listOf(
                OrbMatch(OrbColor.LIGHT, positions(2, 3, 4)),
                OrbMatch(OrbColor.DARK, positions(6, 12, 18)),
                OrbMatch(OrbColor.FIRE, positions(11, 17, 23)),
                OrbMatch(OrbColor.DARK, positions(26, 27, 28, 29)),
            ),
            listOf(OrbMatch(OrbColor.HEART, positions(24, 25, 26, 27))),
            listOf(OrbMatch(OrbColor.FIRE, positions(26, 27, 28))),
            listOf(OrbMatch(OrbColor.DARK, positions(25, 26, 27))),
        )
        assertEquals(expectedWaves, analysis.cascadeWaves)
        assertEquals(4, analysis.matches.size)
        assertEquals(7, analysis.comboCount)
        assertEquals(3, analysis.cascadeCount)
    }

    private fun positions(vararg indices: Int): Set<BoardPosition> =
        indices.map { BoardPosition(it / 6, it % 6) }.toSet()

    @Test
    fun horizontalAndVerticalRunsMatchButPairsAndUnknownDoNot() {
        val board = board("FFFUUU", "WLUUUU", "WDUUUU", "WHHHUU")
        val matches = BoardAnalyzer.analyze(board).matches
        assertEquals(setOf(OrbColor.FIRE, OrbColor.WATER, OrbColor.HEART), matches.map { it.color }.toSet())
        assertTrue(matches.all { it.size == 3 })
        assertTrue(matches.none { it.color == OrbColor.UNKNOWN })
        assertTrue(BoardAnalyzer.analyze(board("FF", "WW")).matches.isEmpty())
    }

    @Test
    fun intersectingRunsAndAdjacentMatchedRunsFormOneCombo() {
        val cross = BoardAnalyzer.analyze(board("UFUU", "FFFU", "UFUU"))
        assertEquals(1, cross.comboCount)
        assertEquals(5, cross.matches.single().size)
        val square = BoardAnalyzer.analyze(board("FFF", "FFF", "FFF"))
        assertEquals(1, square.comboCount)
        assertEquals(9, square.matches.single().size)
    }

    @Test
    fun separatedGroupsOfTheSameColorRemainSeparateCombos() {
        val analysis = BoardAnalyzer.analyze(board("FFFUFFF"))
        assertEquals(2, analysis.comboCount)
        assertEquals(listOf(3, 3), analysis.matches.map { it.size })
    }

    @Test
    fun unmatchedAttachedOrbsAreNotIncludedInTheClear() {
        val analysis = BoardAnalyzer.analyze(board("FFF", "UFU"))
        assertEquals(3, analysis.matches.single().size)
        assertEquals(setOf(BoardPosition(0, 0), BoardPosition(0, 1), BoardPosition(0, 2)),
            analysis.matches.single().positions)
    }

    @Test
    fun simultaneousClearDropsSurvivorsAndPreservesTheirModifiersInNextWave() {
        // Both first-wave triples clear together. The three staggered Wood orbs
        // then fall to the bottom row, producing the only second-wave combo.
        val original = board("GGDL", "FWLD", "FWGL", "FWWW").let { board ->
            board.copy(orbs = board.orbs.mapIndexed { index, orb ->
                if (index == 0) orb.copy(modifiers = setOf(OrbModifier.ENHANCED, OrbModifier.LOCKED)) else orb
            })
        }
        val snapshot = original.orbs.toList()
        val analysis = BoardAnalyzer.resolveCascades(original)
        assertEquals(3, analysis.comboCount)
        assertEquals(1, analysis.cascadeCount)
        assertEquals(listOf(setOf(OrbColor.FIRE, OrbColor.WATER), setOf(OrbColor.WOOD)),
            analysis.cascadeWaves.map { wave -> wave.map { it.color }.toSet() })
        val wood = analysis.cascadeWaves[1].single()
        assertEquals(setOf(BoardPosition(3, 0), BoardPosition(3, 1), BoardPosition(3, 2)), wood.positions)
        assertEquals(1, wood.enhancedOrbCount)
        assertEquals(snapshot, original.orbs)
    }

    @Test
    fun enhancedCountsOnlyIncludeClearedOrbsAndEmptyBoardDoesNotSkyfall() {
        val input = board("FFF", "UFU").let { board -> board.copy(orbs = board.orbs.map {
            if (it.color == OrbColor.FIRE) it.copy(modifiers = setOf(OrbModifier.ENHANCED)) else it
        }) }
        val analysis = BoardAnalyzer.resolveCascades(input)
        assertEquals(3, analysis.matches.single().enhancedOrbCount)
        assertEquals(0, analysis.cascadeCount)
        assertTrue(BoardAnalyzer.resolveCascades(board("UUU", "UUU")).cascadeWaves.isEmpty())
    }

    private fun board(vararg rows: String): Board = Board(
        columns = rows.first().length, rows = rows.size,
        orbs = rows.flatMap { row -> row.map { Orb(when (it) {
            'F' -> OrbColor.FIRE
            'W' -> OrbColor.WATER
            'G' -> OrbColor.WOOD
            'L' -> OrbColor.LIGHT
            'D' -> OrbColor.DARK
            'H' -> OrbColor.HEART
            else -> OrbColor.UNKNOWN
        }) } },
    )
}
