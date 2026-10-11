package com.example.phoneapp.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RouteGeometryTest {
    @Test
    fun reverseTraversalsUseDistinctParallelLanes() {
        val left = BoardPosition(0, 0)
        val right = BoardPosition(0, 1)
        val layout = RouteGeometry.prepare(listOf(left, right, left, right, left, right))
        assertEquals(5, layout.moves.size)
        val offsets = layout.moves.map { it.from.y }.sorted()
        for ((first, second) in offsets.zipWithNext()) assertTrue(second - first >= 0.149f)
        layout.moves.forEach { move ->
            assertEquals(move.from.y, move.to.y, 0.00001f)
            assertEquals(0.36f, abs(move.to.x - move.from.x), 0.00001f)
        }
    }

    @Test
    fun everyVisitConnectsItsOwnIncomingAndOutgoingLane() {
        val path = densePath()
        val layout = RouteGeometry.prepare(path)
        assertEquals(path.size, layout.visits.size)
        assertEquals(RouteGeometry.center(path.first()), layout.visits.first().from)
        assertEquals(RouteGeometry.center(path.last()), layout.visits.last().to)
        layout.moves.forEachIndexed { index, move ->
            assertEquals(move.from, layout.visits[index].to)
            assertEquals(move.to, layout.visits[index + 1].from)
        }
    }

    @Test
    fun denseTurnsAndBorderRevisitsStayInsideTheirOrbCells() {
        val path = densePath()
        val layout = RouteGeometry.prepare(path)
        layout.visits.forEachIndexed { index, curve ->
            val cell = path[index]
            for (sample in 0..50) {
                val point = curve.point(sample / 50f)
                assertTrue("Visit $index left column ${cell.column}: $point", point.x in cell.column.toFloat()..cell.column + 1f)
                assertTrue("Visit $index left row ${cell.row}: $point", point.y in cell.row.toFloat()..cell.row + 1f)
            }
        }
    }

    @Test
    fun repeatedCircularMotionHasNestedConnectionsAtEveryCorner() {
        val loop = listOf(BoardPosition(0, 0), BoardPosition(0, 1), BoardPosition(1, 1), BoardPosition(1, 0))
        val path = List(13) { loop[it % loop.size] }
        val layout = RouteGeometry.prepare(path)
        val visitsByCell = (1 until path.lastIndex).groupBy { path[it] }
        visitsByCell.values.forEach { indices ->
            for (a in indices.indices) for (b in a + 1 until indices.size) {
                val first = layout.visits[indices[a]]
                val second = layout.visits[indices[b]]
                val distance = (0..20).minOf { i -> (0..20).minOf { j -> first.point(i / 20f).distance(second.point(j / 20f)) } }
                assertTrue("Repeated loop turns should remain separated: $distance", distance > 0.045f)
            }
        }
    }

    @Test
    fun emptyAndStationaryRoutesNeedNoMoves() {
        assertTrue(RouteGeometry.prepare(emptyList()).moves.isEmpty())
        assertTrue(RouteGeometry.prepare(listOf(BoardPosition(0, 0))).moves.isEmpty())
    }

    private fun densePath() = listOf(
        8, 14, 15, 21, 20, 26, 27, 28, 22, 21, 27, 28, 29, 23, 22, 21, 20, 19, 18,
        12, 6, 7, 13, 12, 18, 19, 13, 7, 1, 2, 1, 0, 6, 12, 13, 14, 8, 2,
    ).map { BoardPosition(it / 6, it % 6) }
}
