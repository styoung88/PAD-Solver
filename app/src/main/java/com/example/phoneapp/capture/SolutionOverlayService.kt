package com.example.phoneapp.capture

import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.phoneapp.R
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteArrowRenderer
import com.example.phoneapp.MainActivity
import com.example.phoneapp.game.GameVariant
import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.solver.SolveSettingsStore
import com.example.phoneapp.util.dpToPx
import com.example.phoneapp.util.parcelableExtra

class SolutionOverlayService : Service() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var solveJob: kotlinx.coroutines.Job? = null
    private var latestCaptureUri: Uri? = null
    private var latestSolvedBoard: SolvedBoard? = null
    private var capturePending = false
    private var captureGame = GameVariant.PAD
    private var game = GameVariant.PAD
    private var stopObservingGame: (() -> Unit)? = null
    private val toolbarPreferences by lazy { getSharedPreferences("overlay_toolbar", MODE_PRIVATE) }
    private val settingsStore by lazy { SolveSettingsStore(this) }
    private var toolbarLayoutParams: WindowManager.LayoutParams? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val checkOverlayPermission = object : Runnable {
        override fun run() {
            if ((toolbarView != null || routeView != null) && !Settings.canDrawOverlays(this@SolutionOverlayService)) {
                stopService(Intent(this@SolutionOverlayService, ScreenCaptureService::class.java))
                stopSelf()
                return
            }
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        game = settingsStore.load().game
        stopObservingGame = settingsStore.observeGameChanges(::refreshGame)
        enabled = true
        startAsForeground()
        handler.post(checkOverlayPermission)
    }

    private fun requestCapture() {
        if (ScreenCaptureService.active) {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_CAPTURE_NOW))
        } else {
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_CAPTURE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    private fun clearRoute() {
        solveJob?.cancel()
        routeView?.let { runCatching { windowManager?.removeView(it) } }
        routeView = null
        latestSolvedBoard = null
        toolbarView?.setRouteVisibility(available = false)
    }

    private fun toggleRouteVisibility() {
        val route = routeView ?: return
        val visible = route.visibility != View.VISIBLE
        route.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        toolbarView?.setRouteVisibility(available = true, visible = visible)
    }

    private fun refreshGame() {
        val selected = settingsStore.load().game
        if (selected == game) return
        game = selected
        clearRoute()
        latestCaptureUri = null
        capturePending = false
        stopService(Intent(this, ScreenCaptureService::class.java))
        toolbarView?.visibility = View.VISIBLE
        toolbarView?.setStatus("Capture a new board.")
    }

    private fun solveCapture(path: String) {
        solveImage(Uri.fromFile(java.io.File(path)))
    }

    private fun solveImage(uri: Uri) {
        val settings = settingsStore.load()
        clearRoute()
        capturePending = false
        latestCaptureUri = uri

        toolbarView?.visibility = View.VISIBLE
        toolbarView?.setStatus("Reading board…", busy = true)
        solveJob = scope.launch {
            try {
                val recognized = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bitmap = BoardImageLoader.read(this@SolutionOverlayService, uri) ?: return@withContext null
                    try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        BoardRecognizer.forGame(settings.game).recognize(bitmap) as? BoardRecognition.Recognized
                    } }
                    finally { bitmap.recycle() }
                }
                if (recognized == null) {
                    toolbarView?.setStatus("Board not recognized. Try another capture.", isError = true)
                    return@launch
                }
                toolbarView?.setStatus("Finding your route…", busy = true)
                val result = BoardSolver().solve(recognized.board, settings.toOptions(recognized.board))
                latestSolvedBoard = SolvedBoard(settings.game, recognized, result)
                if (result.goalSatisfied) {
                    showRoute(recognized.boardBounds, result.path.map(recognized.board::indexOf).toIntArray(),
                        recognized.captureWidth, recognized.captureHeight, recognized.board.columns, recognized.board.rows)
                    toolbarView?.setResult(result.routeSummary, result.routeDetails)
                } else {
                    toolbarView?.setStatus("No matching route. Adjust your settings.", isError = true)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("RouteOverlay", "Automatic solve failed", error)

                toolbarView?.setStatus("Solve failed. Adjust settings or try again.", isError = true)
            }
        }
    }

    private var routeView: RouteOverlayView? = null
    private var toolbarView: OverlayControlsView? = null
    private var windowManager: WindowManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENABLE -> {
                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                showToolbar()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_FAILED -> {
                capturePending = false
                toolbarView?.visibility = View.VISIBLE
                toolbarView?.setStatus("Capture failed. Tap Capture to retry.", isError = true)
                return START_NOT_STICKY
            }
            ACTION_CAPTURE_STOPPED -> {
                // Keep the toolbar and existing route available when sharing ends.
                toolbarView?.visibility = View.VISIBLE
                if (capturePending) {
                    capturePending = false
                    toolbarView?.setStatus("Capture stopped. Tap Capture to try again.", isError = true)
                }
                return START_NOT_STICKY
            }
            ACTION_PROCESSING -> {

                clearRoute()
                latestCaptureUri = null
                capturePending = true
                captureGame = game
                toolbarView?.visibility = View.INVISIBLE
                return START_NOT_STICKY
            }
            ACTION_SOLVE_CAPTURE -> {
                if (!capturePending || captureGame != game) return START_NOT_STICKY
                capturePending = false
                intent.getStringExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH)?.let(::solveCapture)
                return START_NOT_STICKY
            }
        }
        if (intent?.action == ACTION_HIDE) {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SHOW && GameVariant.fromName(intent.getStringExtra(EXTRA_GAME_VARIANT)) != game) {
            clearRoute()
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
        val columns = intent.getIntExtra(EXTRA_BOARD_COLUMNS, Board.DEFAULT_COLUMNS)
        val rows = intent.getIntExtra(EXTRA_BOARD_ROWS, Board.DEFAULT_ROWS)
        if (captureWidth <= 0 || captureHeight <= 0 || positions.isEmpty() ||
            BoardSize.of(columns, rows) == null || positions.any { it !in 0 until columns * rows }) {
            stopSelf()
            return START_NOT_STICKY
        }
        latestSolvedBoard = SolvedBoardHandoff.takeFrom(intent)?.takeIf { it.game == game }
        latestCaptureUri = null
        showRoute(bounds, positions, captureWidth, captureHeight, columns, rows)
        if (intent.hasExtra(EXTRA_COMBO_COUNT)) {
            toolbarView?.setResult(intent.getStringExtra(EXTRA_ROUTE_SUMMARY) ?: "${intent.getIntExtra(EXTRA_COMBO_COUNT, 0)}c",
                intent.getStringExtra(EXTRA_ROUTE_DETAILS).orEmpty())
        }
        return START_NOT_STICKY
    }

    private fun showRoute(bounds: RectF, positions: IntArray, captureWidth: Int, captureHeight: Int, columns: Int, rows: Int) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        routeView?.let { runCatching { windowManager?.removeView(it) } }
        val view = RouteOverlayView(this, bounds, positions, captureWidth, captureHeight, columns, rows)
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
            .onFailure { android.util.Log.e("RouteOverlay", "Could not attach route overlay view", it) }

        showToolbar()
    }

    private fun showToolbar() {

        toolbarView?.let { runCatching { windowManager?.removeView(it) } }
        val toolbar = OverlayControlsView(this,
            onCapture = ::requestCapture,
            onSettings = ::openLatestCapture,
            onToggleRoute = ::toggleRouteVisibility,
            onClose = {
                stopService(Intent(this, ScreenCaptureService::class.java))
                stopSelf()
            },
            initiallyLocked = toolbarPreferences.getBoolean("locked", false),
            onLockChanged = { toolbarPreferences.edit().putBoolean("locked", it).apply() },
            onDrag = { dx, dy ->
                val params = toolbarLayoutParams
                val view = toolbarView
                if (params != null && view != null) {
                    val metrics = resources.displayMetrics
                    // END gravity measures the horizontal offset from the right edge.
                    params.x = (params.x - dx.toInt()).coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
                    params.y = (params.y + dy.toInt()).coerceIn(0, (metrics.heightPixels - view.height - resources.dpToPx(32)).coerceAtLeast(0))
                    windowManager?.updateViewLayout(view, params)
                }
            },
            onDragFinished = {
                toolbarLayoutParams?.let { toolbarPreferences.edit().putInt("x", it.x).putInt("y", it.y).apply() }
            },
        ).apply {
            setRouteVisibility(available = routeView != null, visible = routeView?.visibility == View.VISIBLE)
        }
        toolbarView = toolbar
        val toolbarParams = WindowManager.LayoutParams(
            resources.dpToPx(176),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            alpha = 1f
            x = toolbarPreferences.getInt("x", resources.dpToPx(8)).coerceIn(0, (resources.displayMetrics.widthPixels - resources.dpToPx(176)).coerceAtLeast(0))
            y = toolbarPreferences.getInt("y", resources.dpToPx(48)).coerceIn(0, (resources.displayMetrics.heightPixels - resources.dpToPx(180)).coerceAtLeast(0))
        }
        toolbarLayoutParams = toolbarParams
        runCatching { windowManager?.addView(toolbar, toolbarParams) }
    }

    private fun openLatestCapture() {
        if (solveJob?.isActive == true) {
            solveJob?.cancel()
            toolbarView?.setStatus("")
        }
        routeView?.visibility = View.INVISIBLE
        toolbarView?.setRouteVisibility(available = routeView != null, visible = false)
        startActivity(Intent(this, MainActivity::class.java).apply {
            latestCaptureUri?.let { uri ->
                if (uri.scheme == "file") putExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH, uri.path)
                else data = uri
            }
            latestSolvedBoard?.let { SolvedBoardHandoff.attachTo(this, it) }
            putExtra(EXTRA_OPEN_SETTINGS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    override fun onDestroy() {
        enabled = false
        stopObservingGame?.invoke()
        stopObservingGame = null
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        routeView?.let { runCatching { windowManager?.removeView(it) } }
        routeView = null
        toolbarView?.let { runCatching { windowManager?.removeView(it) } }
        toolbarView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val channel = "pad_overlay"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(channel, "Floating toolbar", NotificationManager.IMPORTANCE_LOW),
        )
        val close = PendingIntent.getService(this, 43,
            Intent(this, SolutionOverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Floating toolbar")
            .setContentText("Ready to capture a board.")
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "Close overlay", close)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(43, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(43, notification)
    }

    internal class RouteOverlayView(
        context: android.content.Context,
        private val boardBounds: RectF,
        private val path: IntArray,
        private val captureWidth: Int,
        private val captureHeight: Int,
        private val columns: Int,
        private val rows: Int,
    ) : View(context) {
        private val positions = path.map { index ->
            BoardPosition(index / columns, index % columns)
        }
        private val preparedRoute = RouteArrowRenderer.prepareRoute(
            positions,
            columns,
            rows,
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (path.isEmpty()) return
            val scaleX = width.toFloat() / captureWidth
            val scaleY = height.toFloat() / captureHeight
            // Preserve the overlay coordinates from before arrow rendering
            // (57fff16), calculated in screenshot space before scaling.
            val points = positions.map { position ->
                ((boardBounds.left + (position.column + 0.5f) * boardBounds.width() / columns) * scaleX) to
                    ((boardBounds.top + (position.row + 0.5f) * boardBounds.height() / rows) * scaleY)
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
                columns = columns,
                rows = rows,
                endpointCenters = points.first() to points.last(),
                preparedRoute = preparedRoute,
            )
        }
    }

    companion object {
        const val EXTRA_COMBO_COUNT = "combo_count"
        const val EXTRA_ROUTE_SUMMARY = "route_summary"
        const val EXTRA_ROUTE_DETAILS = "route_details"
        const val EXTRA_GAME_VARIANT = "game_variant"
        @Volatile var enabled = false
            private set
        const val ACTION_PROCESSING = "com.example.phoneapp.action.PROCESSING"
        const val ACTION_ENABLE = "com.example.phoneapp.action.ENABLE_OVERLAY"
        const val ACTION_CAPTURE_FAILED = "com.example.phoneapp.action.CAPTURE_FAILED"
        const val ACTION_CAPTURE_STOPPED = "com.example.phoneapp.action.CAPTURE_STOPPED"
        const val EXTRA_REQUEST_CAPTURE = "request_screen_capture"
        const val ACTION_SOLVE_CAPTURE = "com.example.phoneapp.action.SOLVE_CAPTURE"
        const val ACTION_SHOW = "com.example.phoneapp.action.SHOW_ROUTE"
        const val ACTION_HIDE = "com.example.phoneapp.action.HIDE_ROUTE"
        const val EXTRA_PATH = "route_path"
        const val EXTRA_OPEN_SETTINGS = "open_solve_settings"
        const val EXTRA_BOARD_BOUNDS = "route_board_bounds"
        const val EXTRA_CAPTURE_WIDTH = "route_capture_width"
        const val EXTRA_CAPTURE_HEIGHT = "route_capture_height"
        const val EXTRA_BOARD_COLUMNS = "route_board_columns"
        const val EXTRA_BOARD_ROWS = "route_board_rows"
    }
}
