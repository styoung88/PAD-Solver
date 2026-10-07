package com.example.phoneapp.board

import kotlin.math.abs
import kotlin.math.hypot

/** Board-space geometry: each traversal owns a lane, including reverse traversals. */
internal object RouteGeometry {
    data class Point(val x: Float, val y: Float) {
        operator fun plus(other: Point) = Point(x + other.x, y + other.y)
        operator fun minus(other: Point) = Point(x - other.x, y - other.y)
        operator fun times(scale: Float) = Point(x * scale, y * scale)
        fun distance(other: Point) = hypot(x - other.x, y - other.y)
    }

    data class Move(val from: Point, val to: Point, val direction: Point, val laneSpacing: Float)
    data class Curve(val from: Point, val control1: Point, val control2: Point, val to: Point) {
        fun point(t: Float): Point {
            val s = 1f - t
            return from * (s * s * s) + control1 * (3f * s * s * t) +
                control2 * (3f * s * t * t) + to * (t * t * t)
        }
    }
    data class Layout(val moves: List<Move>, val visits: List<Curve>)

    private data class Edge(val first: BoardPosition, val second: BoardPosition)
    private const val PORT_DISTANCE = 0.32f

    fun prepare(path: List<BoardPosition>): Layout {
        if (path.size < 2) return Layout(emptyList(), emptyList())
        val edges = path.zipWithNext { from, to ->
            require(abs(from.row - to.row) + abs(from.column - to.column) == 1) {
                "Route moves must connect neighboring orbs."
            }
            if (from.row < to.row || from.column < to.column) Edge(from, to) else Edge(to, from)
        }
        val traversals = edges.indices.groupBy { edges[it] }
        val ranks = IntArray(edges.size)
        traversals.values.forEach { indices -> indices.forEachIndexed { rank, index -> ranks[index] = rank } }
        val flipped = mutableSetOf<Edge>()
        fun layout(): Layout {
            val moves = edges.indices.map { index ->
                val from = center(path[index])
                val to = center(path[index + 1])
                val direction = to - from
                val count = traversals.getValue(edges[index]).size
                val spacing = if (count == 1) 0.18f else minOf(0.18f, 0.60f / (count - 1))
                val laneDirection = if (edges[index] in flipped) -1f else 1f
                val offset = (ranks[index] - (count - 1) / 2f) * spacing * laneDirection
                // Canonical normal never flips when the route reverses direction.
                val normal = if (path[index].row == path[index + 1].row) Point(0f, 1f) else Point(1f, 0f)
                Move(from + direction * PORT_DISTANCE + normal * offset,
                    to - direction * PORT_DISTANCE + normal * offset, direction, spacing)
            }
            val visits = path.indices.map { index ->
                val position = center(path[index])
                val incoming = moves.getOrNull(index - 1)
                val outgoing = moves.getOrNull(index)
                when {
                    incoming == null -> Curve(position, position, outgoing!!.from, outgoing.from)
                    outgoing == null -> Curve(incoming.to, incoming.to, position, position)
                    else -> Curve(incoming.to, incoming.to + incoming.direction * 0.24f,
                        outgoing.from - outgoing.direction * 0.24f, outgoing.from)
                }
            }
            return Layout(moves, visits)
        }
        val visitsByCell = (1 until path.lastIndex).groupBy { path[it] }.values.filter { it.size > 1 }
        fun crossings(layout: Layout): Int {
            fun cross(a: Point, b: Point) = a.x * b.y - a.y * b.x
            fun intersects(a: Curve, b: Curve): Boolean {
                val ab = a.to - a.from
                val cd = b.to - b.from
                return cross(ab, b.from - a.from) * cross(ab, b.to - a.from) < -0.000001f &&
                    cross(cd, a.from - b.from) * cross(cd, a.to - b.from) < -0.000001f
            }
            return visitsByCell.sumOf { indices ->
                indices.indices.sumOf { first ->
                    (first + 1 until indices.size).count { second ->
                        intersects(layout.visits[indices[first]], layout.visits[indices[second]])
                    }
                }
            }
        }
        var result = layout()
        var cost = crossings(result)
        // Ordering lanes independently can make nested loops cross at every turn.
        // Flip whole edge bundles to reduce crossings inside revisited cells without
        // changing any move, lane spacing, or the route's connections.
        repeat(4) {
            var improved = false
            for ((edge, indices) in traversals) {
                if (indices.size < 2) continue
                if (!flipped.add(edge)) flipped.remove(edge)
                val candidate = layout()
                val candidateCost = crossings(candidate)
                if (candidateCost < cost) {
                    result = candidate
                    cost = candidateCost
                    improved = true
                } else if (!flipped.add(edge)) flipped.remove(edge)
            }
            if (!improved) return result
        }
        return result
    }

    fun center(position: BoardPosition) = Point(position.column + 0.5f, position.row + 0.5f)
}
