package com.example.phoneapp.board

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.hypot

/** Shared continuous route styling for the in-app preview and PAD overlay. */
object RouteArrowRenderer {
    private const val ROUTE_COLOR = 0xFF36E0D0.toInt()
    private val ROUTE_GRADIENT = intArrayOf(
        ROUTE_COLOR,
        0xFF29B6F6.toInt(),
        0xFF7E57C2.toInt(),
        0xFFEC407A.toInt(),
    )
    private const val OUTLINE_COLOR = 0xE817202B.toInt()
    private const val START_COLOR = 0xFF102A3A.toInt()
    private const val FINISH_COLOR = 0xFFFFD34D.toInt()
    private const val GRADIENT_SPEED_MULTIPLIER = 1.5f
    private const val CURVE_TIGHTNESS = 0.14f

    fun draw(
        canvas: Canvas,
        path: List<BoardPosition>,
        bounds: RectF,
        columns: Int,
        rows: Int,
        density: Float,
        endpointCenters: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null,
    ) {
        if (path.isEmpty() || columns <= 0 || rows <= 0) return
        val cellWidth = bounds.width() / columns
        val cellHeight = bounds.height() / rows
        val cellSize = minOf(cellWidth, cellHeight)
        val routePoints = path.map { position ->
            (bounds.left + (position.column + 0.5f) * cellWidth) to
                (bounds.top + (position.row + 0.5f) * cellHeight)
        }
        val cellVisitCounts = mutableMapOf<Int, Int>()
        val cellAnchors = mutableMapOf<Int, MutableList<Pair<Float, Float>>>()
        val routeAnchors = mutableListOf<Pair<Float, Float>>()
        val priorSegments = mutableListOf<Pair<Pair<Float, Float>, Pair<Float, Float>>>()
        path.forEachIndexed { index, position ->
            val cell = position.row * columns + position.column
            val visitOrdinal = cellVisitCounts.getOrDefault(cell, 0)
            cellVisitCounts[cell] = visitOrdinal + 1
            val center = routePoints[index]
            val priorAnchors = cellAnchors.getOrPut(cell) { mutableListOf() }
            val anchor = if (visitOrdinal == 0) {
                center
            } else {
                val previous = routeAnchors.lastOrNull() ?: center
                val next = routePoints.getOrNull(index + 1) ?: center
                revisitCandidates(center, cellSize).maxBy { candidate ->
                    revisitAnchorScore(candidate, center, previous, next, priorAnchors, priorSegments, cellSize)
                }
            }
            routeAnchors += anchor
            priorAnchors += anchor
            if (index > 0) priorSegments += routeAnchors[index - 1] to anchor
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(3f * density, cellSize * 0.11f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROUTE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f * density, cellSize * 0.055f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val continuousRoute = Path().apply {
            moveTo(routeAnchors.first().first, routeAnchors.first().second)
            for (index in 0 until routeAnchors.lastIndex) {
                val (control1, control2) = curveControls(routeAnchors, index)
                val end = routeAnchors[index + 1]
                cubicTo(control1.first, control1.second, control2.first, control2.second, end.first, end.second)
            }
        }
        // Draw one rounded outline under all colored moves, so repeated or
        // overlapping segments can never create gaps in the route itself.
        canvas.drawPath(continuousRoute, outlinePaint)

        val arrowOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f * density, cellSize * 0.055f)
            strokeJoin = Paint.Join.ROUND
        }
        val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROUTE_COLOR
            style = Paint.Style.FILL
        }

        val segmentCount = routePoints.lastIndex
        val gradientStopCount = when {
            segmentCount <= 10 -> 2
            segmentCount <= 20 -> 3
            else -> ROUTE_GRADIENT.size
        }
        for (index in 0 until segmentCount) {
            val from = routeAnchors[index]
            val to = routeAnchors[index + 1]
            val (control1, control2) = curveControls(routeAnchors, index)
            val segmentColor = routeColorForStep(index, segmentCount, gradientStopCount)
            routePaint.color = segmentColor
            arrowFill.color = segmentColor
            val segmentPath = Path().apply {
                moveTo(from.first, from.second)
                cubicTo(control1.first, control1.second, control2.first, control2.second, to.first, to.second)
            }
            canvas.drawPath(segmentPath, routePaint)

            val arrowProgress = 0.72f
            val arrowPoint = cubicPoint(from, control1, control2, to, arrowProgress)
            val (dx, dy) = cubicTangent(from, control1, control2, to, arrowProgress)
            val length = hypot(dx, dy).coerceAtLeast(1f)
            val perpendicularX = -dy / length
            val perpendicularY = dx / length
            val arrowTipX = arrowPoint.first
            val arrowTipY = arrowPoint.second
            val arrowBaseX = arrowTipX - dx / length * cellSize * 0.14f
            val arrowBaseY = arrowTipY - dy / length * cellSize * 0.14f
            val wing = cellSize * 0.075f
            val arrow = Path().apply {
                moveTo(arrowTipX, arrowTipY)
                lineTo(arrowBaseX + perpendicularX * wing, arrowBaseY + perpendicularY * wing)
                lineTo(arrowBaseX - perpendicularX * wing, arrowBaseY - perpendicularY * wing)
                close()
            }
            canvas.drawPath(arrow, arrowOutline)
            canvas.drawPath(arrow, arrowFill)
        }

        val sameStartAndFinish = path.first() == path.last()
        val start = endpointCenters?.first ?: routePoints.first().let { point ->
            if (sameStartAndFinish) point.first to point.second - cellSize * 0.19f else point
        }
        val startRadius = cellSize * 0.23f
        canvas.drawCircle(start.first, start.second, startRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE_COLOR })
        canvas.drawCircle(start.first, start.second, startRadius * 0.78f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FINISH_COLOR })
        val startTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = START_COLOR
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.23f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("S", start.first, start.second - (startTextPaint.ascent() + startTextPaint.descent()) / 2f, startTextPaint)

        val finish = endpointCenters?.second ?: routePoints.last().let { point ->
            if (sameStartAndFinish) point.first to point.second + cellSize * 0.19f else point
        }
        val finishRadius = cellSize * 0.23f
        canvas.drawCircle(finish.first, finish.second, finishRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTLINE_COLOR })
        canvas.drawCircle(finish.first, finish.second, finishRadius * 0.78f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = START_COLOR })
        val finishTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = cellSize * 0.23f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        }
        canvas.drawText("E", finish.first, finish.second - (finishTextPaint.ascent() + finishTextPaint.descent()) / 2f, finishTextPaint)
    }

    private fun curveControls(points: List<Pair<Float, Float>>, index: Int): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val from = points[index]
        val to = points[index + 1]
        val before = points.getOrNull(index - 1) ?: from
        val after = points.getOrNull(index + 2) ?: to
        val control1 = (from.first + (to.first - before.first) * CURVE_TIGHTNESS) to
            (from.second + (to.second - before.second) * CURVE_TIGHTNESS)
        val control2 = (to.first - (after.first - from.first) * CURVE_TIGHTNESS) to
            (to.second - (after.second - from.second) * CURVE_TIGHTNESS)
        return control1 to control2
    }

    private fun revisitCandidates(center: Pair<Float, Float>, cellSize: Float): List<Pair<Float, Float>> = buildList {
        val diagonal = 0.70710677f
        val directions = listOf(
            -1f to 0f, -diagonal to -diagonal, 0f to -1f, diagonal to -diagonal,
            1f to 0f, diagonal to diagonal, 0f to 1f, -diagonal to diagonal,
        )
        for (radius in listOf(0.19f, 0.29f)) {
            directions.forEach { (x, y) -> add((center.first + x * cellSize * radius) to (center.second + y * cellSize * radius)) }
        }
    }

    private fun revisitAnchorScore(
        candidate: Pair<Float, Float>,
        center: Pair<Float, Float>,
        previous: Pair<Float, Float>,
        next: Pair<Float, Float>,
        priorAnchors: List<Pair<Float, Float>>,
        priorSegments: List<Pair<Pair<Float, Float>, Pair<Float, Float>>>,
        cellSize: Float,
    ): Float {
        var score = -hypot(candidate.first - center.first, candidate.second - center.second) / cellSize * 0.12f
        priorAnchors.forEach { anchor ->
            val separation = hypot(candidate.first - anchor.first, candidate.second - anchor.second) / cellSize
            score += separation.coerceAtMost(0.6f) * 1.5f
        }

        // Favor a lane with breathing room from the already-drawn route. A
        // strong penalty discourages crossing or retracing earlier segments.
        priorSegments.dropLast(1).forEach { (start, end) ->
            val clearance = segmentDistance(previous, candidate, start, end) / cellSize
            score += clearance.coerceAtMost(0.45f) * 0.5f
            if (clearance < 0.13f) score -= 10f
        }

        val inX = candidate.first - previous.first
        val inY = candidate.second - previous.second
        val outX = next.first - candidate.first
        val outY = next.second - candidate.second
        val inLength = hypot(inX, inY).coerceAtLeast(1f)
        val outLength = hypot(outX, outY).coerceAtLeast(1f)
        score += ((inX * outX + inY * outY) / (inLength * outLength)) * 0.18f
        return score
    }

    private fun segmentDistance(
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        c: Pair<Float, Float>,
        d: Pair<Float, Float>,
    ): Float {
        fun cross(p: Pair<Float, Float>, q: Pair<Float, Float>, r: Pair<Float, Float>) =
            (q.first - p.first) * (r.second - p.second) - (q.second - p.second) * (r.first - p.first)
        val abC = cross(a, b, c)
        val abD = cross(a, b, d)
        val cdA = cross(c, d, a)
        val cdB = cross(c, d, b)
        val boundsOverlap = maxOf(minOf(a.first, b.first), minOf(c.first, d.first)) <=
            minOf(maxOf(a.first, b.first), maxOf(c.first, d.first)) &&
            maxOf(minOf(a.second, b.second), minOf(c.second, d.second)) <=
            minOf(maxOf(a.second, b.second), maxOf(c.second, d.second))
        if (boundsOverlap && abC * abD <= 0f && cdA * cdB <= 0f) return 0f

        fun pointDistance(point: Pair<Float, Float>, start: Pair<Float, Float>, end: Pair<Float, Float>): Float {
            val dx = end.first - start.first
            val dy = end.second - start.second
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0f) return hypot(point.first - start.first, point.second - start.second)
            val projection = (((point.first - start.first) * dx + (point.second - start.second) * dy) / lengthSquared)
                .coerceIn(0f, 1f)
            return hypot(point.first - (start.first + projection * dx), point.second - (start.second + projection * dy))
        }

        return minOf(pointDistance(a, c, d), pointDistance(b, c, d), pointDistance(c, a, b), pointDistance(d, a, b))
    }

    private fun cubicPoint(
        start: Pair<Float, Float>,
        control1: Pair<Float, Float>,
        control2: Pair<Float, Float>,
        end: Pair<Float, Float>,
        t: Float,
    ): Pair<Float, Float> {
        val inverse = 1f - t
        val startWeight = inverse * inverse * inverse
        val control1Weight = 3f * inverse * inverse * t
        val control2Weight = 3f * inverse * t * t
        val endWeight = t * t * t
        return (
            start.first * startWeight + control1.first * control1Weight +
                control2.first * control2Weight + end.first * endWeight
            ) to (
            start.second * startWeight + control1.second * control1Weight +
                control2.second * control2Weight + end.second * endWeight
            )
    }

    private fun cubicTangent(
        start: Pair<Float, Float>,
        control1: Pair<Float, Float>,
        control2: Pair<Float, Float>,
        end: Pair<Float, Float>,
        t: Float,
    ): Pair<Float, Float> {
        val inverse = 1f - t
        val startWeight = 3f * inverse * inverse
        val control1Weight = 6f * inverse * t
        val control2Weight = 3f * t * t
        return (
            startWeight * (control1.first - start.first) +
                control1Weight * (control2.first - control1.first) +
                control2Weight * (end.first - control2.first)
            ) to (
            startWeight * (control1.second - start.second) +
                control1Weight * (control2.second - control1.second) +
                control2Weight * (end.second - control2.second)
            )
    }

    private fun routeColorForStep(step: Int, segmentCount: Int, stopCount: Int): Int {
        if (segmentCount <= 1 || stopCount <= 1) return ROUTE_GRADIENT.first()
        val progress = step.toFloat() / (segmentCount - 1)
        // Advance through the palette 50% faster, wrapping from its last
        // active stop back to the first so long routes remain continuous.
        val colorPosition = (progress * (stopCount - 1) * GRADIENT_SPEED_MULTIPLIER) % stopCount
        val startIndex = colorPosition.toInt().coerceIn(0, stopCount - 1)
        val endIndex = (startIndex + 1) % stopCount
        val fraction = colorPosition - startIndex
        val startColor = ROUTE_GRADIENT[startIndex]
        val endColor = ROUTE_GRADIENT[endIndex]
        fun channel(start: Int, end: Int) = (start + (end - start) * fraction).toInt()
        return Color.rgb(
            channel(Color.red(startColor), Color.red(endColor)),
            channel(Color.green(startColor), Color.green(endColor)),
            channel(Color.blue(startColor), Color.blue(endColor)),
        )
    }
}
