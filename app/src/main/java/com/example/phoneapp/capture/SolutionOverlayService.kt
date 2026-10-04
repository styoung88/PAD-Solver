package com.example.phoneapp.capture

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteArrowRenderer
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
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (path.isEmpty()) return
            val scaleX = width.toFloat() / captureWidth
            val scaleY = height.toFloat() / captureHeight
            val positions = path.map { index ->
                BoardPosition(index / Board.DEFAULT_COLUMNS, index % Board.DEFAULT_COLUMNS)
            }
            RouteArrowRenderer.draw(
                canvas = canvas,
                path = positions,
                bounds = RectF(
                    boardBounds.left * scaleX,
                    boardBounds.top * scaleY,
                    boardBounds.right * scaleX,
                    boardBounds.bottom * scaleY,
                ),
                columns = Board.DEFAULT_COLUMNS,
                rows = Board.DEFAULT_ROWS,
                density = resources.displayMetrics.density,
            )
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
