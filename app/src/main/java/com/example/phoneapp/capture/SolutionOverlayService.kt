package com.example.phoneapp.capture

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardPosition
import android.widget.TextView

class SolutionOverlayService : Service() {
    private var routeView: RouteOverlayView? = null
    private var closeControl: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }
        val bounds = intent?.parcelableExtra<RectF>(EXTRA_BOARD_BOUNDS) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val positions = intent.getIntArrayExtra(EXTRA_PATH) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val captureWidth = intent.getIntExtra(EXTRA_CAPTURE_WIDTH, 0)
        val captureHeight = intent.getIntExtra(EXTRA_CAPTURE_HEIGHT, 0)
        if (captureWidth <= 0 || captureHeight <= 0 || positions.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        showRoute(bounds, positions, captureWidth, captureHeight)
        return START_NOT_STICKY
    }

    private fun showRoute(bounds: RectF, positions: IntArray, captureWidth: Int, captureHeight: Int) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        routeView?.let { runCatching { windowManager?.removeView(it) } }
        val view = RouteOverlayView(this, bounds, positions, captureWidth, captureHeight)
        routeView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        runCatching { windowManager?.addView(view, params) }

        val close = TextView(this).apply {
            text = "×"
            textSize = 23f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dp(12), dp(5), dp(12), dp(7))
            setBackgroundColor(0xDD202A3A.toInt())
            setOnClickListener { stopSelf() }
        }
        closeControl = close
        val closeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(48)
        }
        runCatching { windowManager?.addView(close, closeParams) }
    }

    override fun onDestroy() {
        routeView?.let { runCatching { windowManager?.removeView(it) } }
        routeView = null
        closeControl?.let { runCatching { windowManager?.removeView(it) } }
        closeControl = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java) else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    private class RouteOverlayView(
        context: android.content.Context,
        private val boardBounds: RectF,
        private val path: IntArray,
        private val captureWidth: Int,
        private val captureHeight: Int,
    ) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 7f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(4f, 1f, 2f, 0xFF111827.toInt())
        }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE522354A.toInt()
            style = Paint.Style.FILL
        }
        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 15f * resources.displayMetrics.density
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setShadowLayer(2f, 0f, 1f, android.graphics.Color.BLACK)
        }
        private val badgeRadius = 13f * resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (path.isEmpty()) return
            val scaleX = width.toFloat() / captureWidth
            val scaleY = height.toFloat() / captureHeight
            val cellWidth = boardBounds.width() / Board.DEFAULT_COLUMNS * scaleX
            val cellHeight = boardBounds.height() / Board.DEFAULT_ROWS * scaleY
            val points = path.map { index ->
                val position = BoardPosition(index / Board.DEFAULT_COLUMNS, index % Board.DEFAULT_COLUMNS)
                ((boardBounds.left + (position.column + 0.5f) * boardBounds.width() / Board.DEFAULT_COLUMNS) * scaleX) to
                    ((boardBounds.top + (position.row + 0.5f) * boardBounds.height() / Board.DEFAULT_ROWS) * scaleY)
            }
            for (index in 0 until points.lastIndex) {
                val from = points[index]
                val to = points[index + 1]
                canvas.drawLine(from.first, from.second, to.first, to.second, linePaint)
            }
            points.forEachIndexed { index, point ->
                val radius = minOf(cellWidth, cellHeight) * 0.22f
                canvas.drawCircle(point.first, point.second, maxOf(radius, badgeRadius), badgePaint)
                val textY = point.second - (numberPaint.ascent() + numberPaint.descent()) / 2f
                canvas.drawText((index + 1).toString(), point.first, textY, numberPaint)
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.example.phoneapp.action.SHOW_ROUTE"
        const val ACTION_HIDE = "com.example.phoneapp.action.HIDE_ROUTE"
        const val EXTRA_PATH = "route_path"
        const val EXTRA_BOARD_BOUNDS = "route_board_bounds"
        const val EXTRA_CAPTURE_WIDTH = "route_capture_width"
        const val EXTRA_CAPTURE_HEIGHT = "route_capture_height"
    }
}
