package com.example.phoneapp.board

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RouteMarkerLayoutTest {
    @Test
    fun revisitedCellsGetSeparateMarkersWithoutChangingTheirStepNumbers() {
        val path = listOf(
            BoardPosition(3, 4),
            BoardPosition(3, 5),
            BoardPosition(2, 5),
            BoardPosition(2, 4),
            BoardPosition(1, 4),
            BoardPosition(1, 3),
            BoardPosition(2, 3),
            BoardPosition(2, 2),
            BoardPosition(3, 2),
            BoardPosition(3, 1),
            BoardPosition(3, 0),
            BoardPosition(4, 0),
            BoardPosition(4, 1),
            BoardPosition(3, 1),
            BoardPosition(2, 1),
            BoardPosition(2, 2),
            BoardPosition(1, 2),
            BoardPosition(1, 1),
            BoardPosition(0, 1),
            BoardPosition(0, 2),
            BoardPosition(0, 3),
            BoardPosition(0, 4),
            BoardPosition(0, 5),
            BoardPosition(1, 5),
            BoardPosition(1, 4),
            BoardPosition(0, 4),
        )

        val placements = RouteMarkerLayout.place(path)
        val repeatedStepGroups = placements
            .filter { it.repeatedVisit }
            .groupBy { it.position }
            .values
            .map { markers -> markers.map { it.pathIndex + 1 }.toSet() }
            .toSet()

        assertEquals(
            setOf(setOf(5, 25), setOf(8, 16), setOf(10, 14), setOf(22, 26)),
            repeatedStepGroups,
        )
        assertEquals(path.indices.toList(), placements.map { it.pathIndex })
        for ((from, to) in path.zipWithNext()) {
            assertEquals(1, abs(from.row - to.row) + abs(from.column - to.column))
        }
        for (markers in placements.filter { it.repeatedVisit }.groupBy { it.position }.values) {
            assertEquals(markers.size, markers.map { it.offsetXFraction to it.offsetYFraction }.distinct().size)
            assertTrue(markers.all { it.radiusFraction > 0f })
        }
    }
}
