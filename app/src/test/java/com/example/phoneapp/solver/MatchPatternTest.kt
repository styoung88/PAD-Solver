package com.example.phoneapp.solver

import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPatternTest {
    @Test
    fun rowsRequireSixContiguousCellsAndCountEachCompleteRow() {
        assertEquals(1, count(MatchPattern.ROW, (0..5).map { 2 to it }))
        assertEquals(0, count(MatchPattern.ROW, (0..4).map { 2 to it }))
        assertEquals(0, count(MatchPattern.ROW, listOf(0, 1, 2, 4, 5, 6).map { 2 to it }))
        assertEquals(0, count(MatchPattern.ROW, (0..5).map { it to 2 }))
        assertEquals(2, count(MatchPattern.ROW, (1..2).flatMap { row -> (0..5).map { row to it } }))
    }

    @Test
    fun tpaRequiresExactlyFourClearedOrbsInAStraightRun() {
        assertEquals(1, count(MatchPattern.TPA, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3)))
        assertEquals(0, count(MatchPattern.TPA, listOf(0 to 0, 1 to 0, 2 to 0, 2 to 1)))
        assertEquals(0, count(MatchPattern.TPA, (0..4).map { 0 to it }))
        assertEquals(0, count(MatchPattern.TPA, (0..2).map { 0 to it }))
    }

    @Test
    fun tpaBoardFixturesAgreeWithTheClearedCellsRatherThanAttachedOrbs() {
        val straight = board("FFFFUU")
        assertEquals(1, MatchPattern.TPA.countMatches(BoardAnalyzer.resolveCascades(straight)))
        val bent = board("FFF", "UUF")
        assertEquals(3, BoardAnalyzer.analyze(bent).matches.single().size)
        assertEquals(0, MatchPattern.TPA.countMatches(BoardAnalyzer.resolveCascades(bent)))
        assertEquals(0, MatchPattern.TPA.countMatches(BoardAnalyzer.resolveCascades(board("FFFFFU"))))
    }

    @Test
    fun attackingPatternsNeverCountHeartOrUnknownMatches() {
        val square = (0..2).flatMap { row -> (0..2).map { BoardPosition(row, it) } }.toSet()
        val analysis = BoardAnalysis(listOf(OrbMatch(OrbColor.HEART, square), OrbMatch(OrbColor.UNKNOWN, square)))
        for (pattern in MatchPattern.entries) assertEquals(0, pattern.countMatches(analysis))
        val hearts = Board(4, 1, List(4) { Orb(OrbColor.HEART) })
        assertEquals(0, MatchPattern.TPA.countMatches(BoardAnalyzer.analyze(hearts)))
        assertEquals(0, MatchPattern.TPA.potentialScore(hearts, OrbColor.HEART))
        assertFalse(MatchPattern.TPA.canForm(hearts))
    }

    @Test
    fun feasibilityUsesBothInventoryAndSpaceAndIgnoresImpossibleColorsInPotential() {
        assertFalse(MatchPattern.VDP.canForm(board("FFFFFFFFF"), OrbColor.FIRE))
        assertTrue(MatchPattern.VDP.canForm(board("FFF", "FFF", "FFF"), OrbColor.FIRE))
        assertFalse(MatchPattern.VDP.canForm(board("FFF", "FFF", "FFU"), OrbColor.FIRE))
        assertFalse(MatchPattern.ROW.canForm(board("FFFFF")))
        val input = board("FFFUUU", "UWWWWU")
        assertEquals(MatchPattern.TPA.potentialScore(input, OrbColor.WATER), MatchPattern.TPA.potentialScore(input))
    }

    @Test
    fun lShapesWorkInEveryOrientationButRejectAttachedAndMisplacedCells() {
        for (vertical in listOf(-1, 1)) for (horizontal in listOf(-1, 1)) {
            val shape = listOf(3 to 3, 3 + vertical to 3, 3 + 2 * vertical to 3,
                3 to 3 + horizontal, 3 to 3 + 2 * horizontal)
            assertEquals(1, count(MatchPattern.L_SHAPE, shape))
            assertEquals(0, count(MatchPattern.L_SHAPE, shape + (3 + vertical to 3 + horizontal)))
            assertEquals(0, count(MatchPattern.L_SHAPE, shape.dropLast(1) + (3 + vertical to 3 + horizontal)))
        }
    }

    @Test
    fun crossAndVdpRequireCompleteExactShapes() {
        val cross = listOf(2 to 2, 1 to 2, 3 to 2, 2 to 1, 2 to 3)
        val square = (1..3).flatMap { row -> (2..4).map { row to it } }
        for ((pattern, shape) in listOf(MatchPattern.CROSS to cross, MatchPattern.VDP to square)) {
            assertEquals(1, count(pattern, shape))
            assertEquals(0, count(pattern, shape.dropLast(1)))
            assertEquals(0, count(pattern, shape + (0 to 2)))
            assertEquals(0, count(pattern, shape.dropLast(1) + (0 to 2)))
        }
    }

    @Test
    fun colorFilterAndPatternCountsIncludeAllCascadeWaves() {
        val positions = (0..3).map { BoardPosition(0, it) }.toSet()
        val wood = OrbMatch(OrbColor.WOOD, positions)
        val fire = OrbMatch(OrbColor.FIRE, positions)
        val analysis = BoardAnalysis(listOf(wood), listOf(listOf(wood), listOf(fire, wood)))
        assertEquals(3, MatchPattern.TPA.countMatches(analysis))
        assertEquals(2, MatchPattern.TPA.countMatches(analysis, OrbColor.WOOD))
        assertEquals(1, MatchPattern.TPA.countMatches(analysis, OrbColor.FIRE))
        assertEquals(0, MatchPattern.TPA.countMatches(analysis, OrbColor.WATER))
    }

    private fun count(pattern: MatchPattern, cells: List<Pair<Int, Int>>): Int =
        pattern.countMatches(BoardAnalysis(listOf(OrbMatch(OrbColor.WOOD,
            cells.map { BoardPosition(it.first, it.second) }.toSet()))))

    private fun board(vararg rows: String): Board = Board(rows.first().length, rows.size, rows.flatMap { row ->
        row.map { Orb(when (it) {
            'F' -> OrbColor.FIRE
            'W' -> OrbColor.WATER
            else -> OrbColor.UNKNOWN
        }) }
    })
}
