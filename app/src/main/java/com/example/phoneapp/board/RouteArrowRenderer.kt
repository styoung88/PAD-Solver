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

    /** Geometry prepared once per path and reused by preview and overlay draws. */
    class PreparedRoute internal constructor(internal val anchors: List<Pair<Float, Float>>)

    fun prepareRoute(path: List<BoardPosition>, columns: Int, rows: Int): PreparedRoute {
        require(columns > 0 && rows > 0)
        val centerline = path.map { position -> (position.column + 0.5f) to (position.row + 0.5f) }
        return PreparedRoute(separateOverlappingRuns(centerline, 1f))
    }

    fun draw(
        canvas: Canvas,
        path: List<BoardPosition>,
        bounds: RectF,
        columns: Int,
        rows: Int,
        density: Float,
        endpointCenters: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null,
        preparedRoute: PreparedRoute? = null,
    ) {
        if (path.isEmpty() || columns <= 0 || rows <= 0) return
        val cellWidth = bounds.width() / columns
        val cellHeight = bounds.height() / rows
        val cellSize = minOf(cellWidth, cellHeight)
        val routePoints = path.map { position ->
            (bounds.left + (position.column + 0.5f) * cellWidth) to
                (bounds.top + (position.row + 0.5f) * cellHeight)
        }
        // Keep the solver's route as the canonical trace first. Then detect
        // collisions between non-adjacent portions of that trace and move a
        // later overlapping run into the clearest nearby lane. Shared anchor
        // offsets keep each adjusted run joined to the rest of the route.
        val routeAnchors = preparedRoute
            ?.takeIf { it.anchors.size == routePoints.size }
            ?.anchors
            ?.map { (column, row) ->
                (bounds.left + column * cellWidth) to (bounds.top + row * cellHeight)
            }
            ?: separateOverlappingRuns(routePoints, cellSize)

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

    private data class RouteSample(val segmentIndex: Int, val point: Pair<Float, Float>)

    /**
     * Builds a center-to-center trace, detects close non-adjacent portions, and
     * nudges later portions sideways. Offsets are shared across anchor points
     * so the final spline remains a single continuous route.
     */
    private fun separateOverlappingRuns(
        centerline: List<Pair<Float, Float>>,
        cellSize: Float,
    ): List<Pair<Float, Float>> {
        if (centerline.size < 4) return centerline

        val segmentOffsets = MutableList(centerline.lastIndex) { 0f to 0f }
        val earlierSamples = mutableListOf<RouteSample>()
        val collisionDistance = cellSize * 0.19f
        val candidateLanes = floatArrayOf(-0.29f, -0.18f, 0.18f, 0.29f)

        for (segmentIndex in 0 until centerline.lastIndex) {
            val (control1, control2) = curveControls(centerline, segmentIndex)
            val start = centerline[segmentIndex]
            val end = centerline[segmentIndex + 1]
            val trace = sampleCurve(start, control1, control2, end)
            val prior = earlierSamples.filter { it.segmentIndex < segmentIndex - 1 }

            if (prior.isNotEmpty() && minimumClearance(trace, prior) < collisionDistance) {
                val (dx, dy) = cubicTangent(start, control1, control2, end, 0.5f)
                val tangentLength = hypot(dx, dy).coerceAtLeast(1f)
                val normalX = -dy / tangentLength
                val normalY = dx / tangentLength
                var bestOffset = 0f to 0f
                var bestClearance = minimumClearance(trace, prior)

                for (lane in candidateLanes) {
                    val offset = (normalX * cellSize * lane) to (normalY * cellSize * lane)
                    val shiftedTrace = trace.map { (x, y) -> (x + offset.first) to (y + offset.second) }
                    val clearance = minimumClearance(shiftedTrace, prior)
                    if (clearance > bestClearance + cellSize * 0.015f) {
                        bestClearance = clearance
                        bestOffset = offset
                    }
                }
                segmentOffsets[segmentIndex] = bestOffset
            }

            val offset = segmentOffsets[segmentIndex]
            sampleCurve(start, control1, control2, end).forEach { (x, y) ->
                earlierSamples += RouteSample(segmentIndex, (x + offset.first) to (y + offset.second))
            }
        }

        // Average neighboring segment lanes at shared vertices. This blends a
        // lane transition across turns while keeping the path topologically
        // connected. Keep the trace endpoints pinned to the orb centers.
        return centerline.indices.map { index ->
            if (index == 0 || index == centerline.lastIndex) return@map centerline[index]
            val incoming = segmentOffsets.getOrNull(index - 1) ?: (0f to 0f)
            val outgoing = segmentOffsets.getOrNull(index) ?: (0f to 0f)
            (centerline[index].first + (incoming.first + outgoing.first) * 0.5f) to
                (centerline[index].second + (incoming.second + outgoing.second) * 0.5f)
        }
    }

    private fun sampleCurve(
        start: Pair<Float, Float>,
        control1: Pair<Float, Float>,
        control2: Pair<Float, Float>,
        end: Pair<Float, Float>,
    ): List<Pair<Float, Float>> = (1..9).map { sample ->
        cubicPoint(start, control1, control2, end, sample / 10f)
    }

    private fun minimumClearance(
        current: List<Pair<Float, Float>>,
        previous: List<RouteSample>,
    ): Float {
        if (current.isEmpty() || previous.isEmpty()) return Float.MAX_VALUE
        var clearance = Float.MAX_VALUE
        for ((x, y) in current) {
            for (sample in previous) {
                clearance = minOf(clearance, hypot(x - sample.point.first, y - sample.point.second))
            }
        }
        return clearance
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
