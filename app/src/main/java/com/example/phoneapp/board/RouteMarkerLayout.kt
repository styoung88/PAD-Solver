package com.example.phoneapp.board

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RouteMarkerPlacement(
    val pathIndex: Int,
    val position: BoardPosition,
    /** Offset from the cell center, in fractions of the cell dimensions. */
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    /** Radius in fractions of the cell's smaller dimension. */
    val radiusFraction: Float,
    val visitCount: Int,
    val repeatedVisit: Boolean,
)

/** Keeps labels for separate visits to the same board cell from covering each other. */
object RouteMarkerLayout {
    fun place(path: List<BoardPosition>): List<RouteMarkerPlacement> {
        val visitCounts = path.groupingBy { it }.eachCount()
        val visitsSeen = mutableMapOf<BoardPosition, Int>()

        return path.mapIndexed { pathIndex, position ->
            val visitCount = visitCounts.getValue(position)
            if (visitCount == 1) {
                RouteMarkerPlacement(
                    pathIndex = pathIndex,
                    position = position,
                    offsetXFraction = 0f,
                    offsetYFraction = 0f,
                    radiusFraction = 0f,
                    visitCount = visitCount,
                    repeatedVisit = false,
                )
            } else {
                val visitIndex = visitsSeen.getOrDefault(position, 0)
                visitsSeen[position] = visitIndex + 1
                val angle = -PI / 2.0 + 2.0 * PI * visitIndex / visitCount
                RouteMarkerPlacement(
                    pathIndex = pathIndex,
                    position = position,
                    offsetXFraction = (cos(angle) * REPEATED_MARKER_OFFSET_FRACTION).toFloat(),
                    offsetYFraction = (sin(angle) * REPEATED_MARKER_OFFSET_FRACTION).toFloat(),
                    radiusFraction = minOf(
                        MAX_REPEATED_MARKER_RADIUS_FRACTION,
                        REPEATED_MARKER_OFFSET_FRACTION * sin(PI / visitCount) * MARKER_SPACING_FACTOR,
                    ).toFloat(),
                    visitCount = visitCount,
                    repeatedVisit = true,
                )
            }
        }
    }

    private const val REPEATED_MARKER_OFFSET_FRACTION = 0.32
    private const val MAX_REPEATED_MARKER_RADIUS_FRACTION = 0.14
    private const val MARKER_SPACING_FACTOR = 0.8
}
