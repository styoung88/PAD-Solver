package com.example.phoneapp.board

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RouteStepMarkerLayoutTest {
    @Test
    fun screenshotRouteNumbersEveryMoveOnItsSegment() {
        val path = screenshotRoute()
        val markers = RouteStepMarkerLayout.place(path)

        assertEquals((1..26).toList(), markers.map { it.stepNumber })
        assertTrue(markers.first().isStart)
        assertFalse(markers.drop(1).any { it.isStart })
        for ((from, to) in path.zipWithNext()) {
            assertEquals(1, abs(from.row - to.row) + abs(from.column - to.column))
        }
        for (index in 1 until path.size) {
            assertTrue(markers[index].gridX in 0.2f..5.8f)
            assertTrue(markers[index].gridY in 0.2f..4.8f)
        }
    }

    @Test
    fun repeatedTraversalsOfSameSegmentUseSeparateMarkerPositions() {
        val path = listOf(BoardPosition(0, 0), BoardPosition(0, 1), BoardPosition(1, 1), BoardPosition(0, 1), BoardPosition(0, 0))
        val markers = RouteStepMarkerLayout.place(path)

        assertEquals(markers[2].gridX, markers[3].gridX, 0.001f)
        assertTrue(markers[2].gridY < markers[3].gridY)
    }

    @Test
    fun laterLabelOnRepeatedSegmentStacksBelowEarlierLabel() {
        val a = BoardPosition(1, 1)
        val b = BoardPosition(1, 2)
        val path = listOf(a, b, a)
        val markers = RouteStepMarkerLayout.place(path)
        val earlier = markers.single { it.stepNumber == 2 }
        val later = markers.single { it.stepNumber == 3 }

        assertEquals(earlier.gridX, later.gridX, 0.001f)
        assertTrue("Earlier label should sit above the retraced-segment label", earlier.gridY < later.gridY)
        assertTrue(abs(earlier.gridY - later.gridY) >= 0.30f)
    }

    private fun screenshotRoute() = listOf(
        BoardPosition(3, 4), BoardPosition(3, 5), BoardPosition(2, 5), BoardPosition(2, 4),
        BoardPosition(1, 4), BoardPosition(1, 3), BoardPosition(2, 3), BoardPosition(2, 2),
        BoardPosition(3, 2), BoardPosition(3, 1), BoardPosition(3, 0), BoardPosition(4, 0),
        BoardPosition(4, 1), BoardPosition(3, 1), BoardPosition(2, 1), BoardPosition(2, 2),
        BoardPosition(1, 2), BoardPosition(1, 1), BoardPosition(0, 1), BoardPosition(0, 2),
        BoardPosition(0, 3), BoardPosition(0, 4), BoardPosition(0, 5), BoardPosition(1, 5),
        BoardPosition(1, 4), BoardPosition(0, 4),
    )
}
