package com.example.phoneapp.board

import kotlin.math.abs

/** Places the first step at the starting orb and later steps on the move segments. */
object RouteStepMarkerLayout {
    data class Marker(
        val stepNumber: Int,
        val gridX: Float,
        val gridY: Float,
        val isStart: Boolean,
    )

    fun place(path: List<BoardPosition>): List<Marker> {
        if (path.isEmpty()) return emptyList()
        val markers = ArrayList<Marker>(path.size)
        markers += Marker(1, path.first().column + 0.5f, path.first().row + 0.5f, isStart = true)

        for (index in 1 until path.size) {
            val from = path[index - 1]
            val to = path[index]
            val dx = to.column - from.column
            val dy = to.row - from.row
            require(abs(dx) + abs(dy) == 1) { "Route positions must be adjacent." }

            val marker = Marker(
                stepNumber = index + 1,
                gridX = (from.column + to.column + 1f) / 2f,
                gridY = (from.row + to.row + 1f) / 2f,
                isStart = false,
            )
            markers += stackIfOverlapping(marker, markers)
        }
        return markers
    }

    private fun stackIfOverlapping(marker: Marker, placed: List<Marker>): Marker {
        val halfWidth = if (marker.stepNumber >= 10) 0.19f else 0.13f
        val colliding = placed.filter {
            val otherHalfWidth = if (it.stepNumber >= 10) 0.19f else 0.13f
            abs(marker.gridX - it.gridX) < halfWidth + otherHalfWidth && abs(marker.gridY - it.gridY) < 0.30f
        }
        if (colliding.isEmpty()) return marker

        // Keep the earlier number above the later number, aligned into a compact vertical stack.
        val anchor = colliding.minBy { abs(marker.gridX - it.gridX) + abs(marker.gridY - it.gridY) }
        val stackX = anchor.gridX
        val below = colliding.maxOf { it.gridY } + 0.30f
        val above = colliding.minOf { it.gridY } - 0.30f
        val stackY = if (below <= Board.DEFAULT_ROWS - 0.15f) below else above
        return marker.copy(gridX = stackX, gridY = stackY)
    }
}
