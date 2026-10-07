package com.example.phoneapp.board

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.example.phoneapp.board.RouteGeometry.Point
import kotlin.math.hypot

/** Shared numbered route styling for the in-app preview and PAD overlay. */
object RouteArrowRenderer {
    private const val OUTLINE_COLOR = 0xFF101824.toInt()
    private const val START_BADGE_COLOR = 0xFF4CAF50.toInt()
    private const val END_BADGE_COLOR = 0xFFEF4444.toInt()
    private val VIVID_COLORS = intArrayOf(0xFF00E5B5.toInt(), 0xFF00B8FF.toInt(), 0xFFAC69FF.toInt(), 0xFFFF3988.toInt())

    class PreparedRoute internal constructor(
        internal val path: List<BoardPosition>,
        internal val columns: Int,
        internal val rows: Int,
        internal val layout: RouteGeometry.Layout,
    )

    fun prepareRoute(path: List<BoardPosition>, columns: Int, rows: Int): PreparedRoute {
        require(columns > 0 && rows > 0)
        require(path.all { it.column in 0 until columns && it.row in 0 until rows })
        return PreparedRoute(path.toList(), columns, rows, RouteGeometry.prepare(path))
    }

    fun draw(
        canvas: Canvas,
        path: List<BoardPosition>,
        bounds: RectF,
        columns: Int,
        rows: Int,
        endpointCenters: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null,
        preparedRoute: PreparedRoute? = null,
    ) {
        if (path.isEmpty() || columns <= 0 || rows <= 0 || bounds.isEmpty) return
        val geometry = preparedRoute?.takeIf { it.path == path && it.columns == columns && it.rows == rows }
            ?: prepareRoute(path, columns, rows)
        val cellWidth = bounds.width() / columns
        val cellHeight = bounds.height() / rows
        val cellSize = minOf(cellWidth, cellHeight)
        fun screen(point: Point) = Point(bounds.left + point.x * cellWidth, bounds.top + point.y * cellHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        fun drawText(label: String, point: Point, size: Float) {
            text.textSize = size
            canvas.drawText(label, point.x, point.y - (text.ascent() + text.descent()) / 2f, text)
        }
        fun stroke(trace: Path, color: Int, spacing: Float = 0.18f) {
            // Shrink crowded lanes together with their casing rather than let outlines merge.
            val width = cellSize * minOf(0.056f, spacing * 0.31f)
            val casing = width
            paint.style = Paint.Style.STROKE
            paint.color = OUTLINE_COLOR
            paint.strokeWidth = width + casing
            canvas.drawPath(trace, paint)
            paint.color = color
            paint.strokeWidth = width
            canvas.drawPath(trace, paint)
        }
        fun visitPath(curve: RouteGeometry.Curve): Path = Path().apply {
            val first = screen(curve.from)
            val a = screen(curve.control1)
            val b = screen(curve.control2)
            val end = screen(curve.to)
            moveTo(first.x, first.y)
            cubicTo(a.x, a.y, b.x, b.y, end.x, end.y)
        }
        val moves = geometry.layout.moves
        val start = endpointCenters?.first?.let { Point(it.first, it.second) }
            ?: screen(RouteGeometry.center(path.first()))
        val end = endpointCenters?.second?.let { Point(it.first, it.second) }
            ?: screen(RouteGeometry.center(path.last()))
        val sharedEndpoint = path.first() == path.last()
        val startRadius = cellSize * if (sharedEndpoint) 0.16f else 0.20f
        val endRadius = cellSize * if (sharedEndpoint) 0.24f else 0.20f
        fun badge(center: Point, radius: Float, color: Int, square: Boolean) {
            paint.style = Paint.Style.FILL
            fun fill(size: Float, fillColor: Int) {
                paint.color = fillColor
                if (square) canvas.drawRoundRect(center.x - size, center.y - size, center.x + size, center.y + size,
                    cellSize * 0.035f, cellSize * 0.035f, paint)
                else canvas.drawCircle(center.x, center.y, size, paint)
            }
            fill(radius + cellSize * 0.025f, OUTLINE_COLOR)
            fill(radius, color)
        }
        // Badges share the exact endpoint centers. Draw beneath the route so
        // revisits can flow across them, including nested badges in a shared cell.
        badge(end, endRadius, END_BADGE_COLOR, true)
        badge(start, startRadius, START_BADGE_COLOR, false)
        for (index in geometry.layout.visits.indices) {
            val color = routeColor(index.coerceAtMost(moves.lastIndex), moves.size)
            val spacing = minOf(moves.getOrNull(index - 1)?.laneSpacing ?: 0.18f,
                moves.getOrNull(index)?.laneSpacing ?: 0.18f)
            // Each pass has an opaque casing: unavoidable crossings read as
            // overpasses instead of blending into a branching junction.
            stroke(visitPath(geometry.layout.visits[index]), color, spacing)
            val move = moves.getOrNull(index) ?: continue
            val from = screen(move.from)
            val to = screen(move.to)
            stroke(Path().apply { moveTo(from.x, from.y); lineTo(to.x, to.y) }, color, move.laneSpacing)
        }
        // Number every move at the cell boundary, where the orb artwork leaves the
        // most space. Upright numbers disambiguate crossings and repeated loops.
        for ((index, move) in moves.withIndex()) {
            val center = screen((move.from + move.to) * 0.5f)
            val color = routeColor(index, moves.size)
            val radius = cellSize * minOf(0.091f, move.laneSpacing * 0.43f)
            paint.style = Paint.Style.FILL
            paint.color = OUTLINE_COLOR
            canvas.drawCircle(center.x, center.y, radius, paint)
            drawText((index + 1).toString(), center, radius * 1.32f)

            val vector = Point(move.direction.x * cellWidth, move.direction.y * cellHeight)
            val direction = vector * (1f / hypot(vector.x, vector.y))
            val normal = Point(-direction.y, direction.x)
            val base = center + direction * (radius + cellSize * 0.025f)
            val tip = base + direction * (cellSize * 0.105f)
            val wing = cellSize * minOf(0.062f, move.laneSpacing * 0.34f)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(base.x + normal.x * wing, base.y + normal.y * wing)
                lineTo(base.x - normal.x * wing, base.y - normal.y * wing)
                close()
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cellSize * 0.027f
            paint.color = OUTLINE_COLOR
            canvas.drawPath(arrow, paint)
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawPath(arrow, paint)
        }
    }

    private fun routeColor(step: Int, count: Int): Int {
        val colors = VIVID_COLORS
        val position = if (count <= 1) 0f else step.toFloat() / (count - 1) * colors.lastIndex
        val index = position.toInt().coerceIn(0, colors.lastIndex)
        val from = colors[index]
        val to = colors[minOf(index + 1, colors.lastIndex)]
        val fraction = position - index
        fun blend(a: Int, b: Int) = (a + (b - a) * fraction).toInt()
        return Color.rgb(blend(Color.red(from), Color.red(to)),
            blend(Color.green(from), Color.green(to)), blend(Color.blue(from), Color.blue(to)))
    }
}
