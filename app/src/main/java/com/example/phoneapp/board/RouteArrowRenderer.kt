package com.example.phoneapp.board

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.hypot

/** Shared arrow-first route styling for the in-app preview and PAD overlay. */
object RouteArrowRenderer {
    private const val ROUTE_COLOR = 0xFF36E0D0.toInt()
    private const val OUTLINE_COLOR = 0xE817202B.toInt()
    private const val START_COLOR = 0xFF102A3A.toInt()
    private const val FINISH_COLOR = 0xFFFFD34D.toInt()

    fun draw(
        canvas: Canvas,
        path: List<BoardPosition>,
        bounds: RectF,
        columns: Int,
        rows: Int,
        density: Float,
    ) {
        if (path.isEmpty() || columns <= 0 || rows <= 0) return
        val cellWidth = bounds.width() / columns
        val cellHeight = bounds.height() / rows
        val cellSize = minOf(cellWidth, cellHeight)
        val routePoints = path.map { position ->
            (bounds.left + (position.column + 0.5f) * cellWidth) to
                (bounds.top + (position.row + 0.5f) * cellHeight)
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(5f * density, cellSize * 0.19f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROUTE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(3f * density, cellSize * 0.095f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val arrowOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = maxOf(4f * density, cellSize * 0.12f)
            strokeJoin = Paint.Join.ROUND
        }
        val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ROUTE_COLOR
            style = Paint.Style.FILL
        }

        val edgeVisits = mutableMapOf<Long, Int>()
        for (index in 0 until routePoints.lastIndex) {
            val fromPosition = path[index]
            val toPosition = path[index + 1]
            val from = routePoints[index]
            val to = routePoints[index + 1]
            val fromCell = fromPosition.row * columns + fromPosition.column
            val toCell = toPosition.row * columns + toPosition.column
            val edgeKey = (minOf(fromCell, toCell).toLong() shl 32) or maxOf(fromCell, toCell).toLong()
            val visit = edgeVisits.getOrDefault(edgeKey, 0)
            edgeVisits[edgeKey] = visit + 1

            // Keep repeated traversals on small parallel lanes, consistent even
            // when a later pass traverses the same edge in the reverse direction.
            val canonicalStart = if (fromCell < toCell) from else to
            val canonicalEnd = if (fromCell < toCell) to else from
            val canonicalDx = canonicalEnd.first - canonicalStart.first
            val canonicalDy = canonicalEnd.second - canonicalStart.second
            val canonicalLength = hypot(canonicalDx, canonicalDy).coerceAtLeast(1f)
            val laneNumber = if (visit == 0) 0f else ((visit + 1) / 2).toFloat() *
                if (visit % 2 == 1) 1f else -1f
            val lane = laneNumber * cellSize * 0.13f
            val offsetX = -canonicalDy / canonicalLength * lane
            val offsetY = canonicalDx / canonicalLength * lane
            val startX = from.first + offsetX
            val startY = from.second + offsetY
            val endX = to.first + offsetX
            val endY = to.second + offsetY

            canvas.drawLine(startX, startY, endX, endY, outlinePaint)
            canvas.drawLine(startX, startY, endX, endY, routePaint)

            val dx = to.first - from.first
            val dy = to.second - from.second
            val length = hypot(dx, dy).coerceAtLeast(1f)
            val perpendicularX = -dy / length
            val perpendicularY = dx / length
            val arrowTipX = startX + dx * 0.72f
            val arrowTipY = startY + dy * 0.72f
            val arrowBaseX = startX + dx * 0.43f
            val arrowBaseY = startY + dy * 0.43f
            val wing = cellSize * 0.16f
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
        val start = routePoints.first().let { point ->
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

        val finish = routePoints.last().let { point ->
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
}
