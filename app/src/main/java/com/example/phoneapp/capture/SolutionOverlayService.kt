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
import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.solver.SolveOptions
import com.example.phoneapp.solver.SolveSettingsStore
import com.example.phoneapp.util.dpToPx
import com.example.phoneapp.util.parcelableExtra

class SolutionOverlayService : Service() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var solveJob: kotlinx.coroutines.Job? = null
    private var latestCaptureUri: Uri? = null
    private var screenshotReadJob: kotlinx.coroutines.Job? = null
    private var capturePending = false
    private val toolbarPreferences by lazy { getSharedPreferences("overlay_toolbar", MODE_PRIVATE) }
    private val settingsStore by lazy { SolveSettingsStore(this) }
    private val screenshotShortcut by lazy { ScreenshotShortcut(this) }
    private val screenshots by lazy { ScreenshotSource(contentResolver) }
    private var screenshotObserverRegistered = false
    private var toolbarLayoutParams: WindowManager.LayoutParams? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val screenshotObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(checkScreenshots)
            handler.postDelayed(checkScreenshots, 600)
        }
    }
    private val checkScreenshots = Runnable {
        refreshScreenshotShortcut()
        if (!enabled || !screenshotShortcut.canWatch(PhotoAccess.current(this))) return@Runnable
        screenshotReadJob?.cancel()
        screenshotReadJob = scope.launch {
            try {
                val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { screenshots.next() }
                if (image != null && screenshotShortcut.canWatch(PhotoAccess.current(this@SolutionOverlayService))) solveImage(image)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                refreshScreenshotShortcut()
                toolbarView?.setStatus("Couldn't read the screenshot. Try Capture.", isError = true)
                android.util.Log.w("RouteOverlay", "Screenshot observation unavailable", error)
            }
        }
    }

    private val refreshShortcut = object : Runnable {
        override fun run() {
            refreshScreenshotShortcut()
            if ((toolbarView != null || routeView != null) && !Settings.canDrawOverlays(this@SolutionOverlayService)) {
                stopService(Intent(this@SolutionOverlayService, ScreenCaptureService::class.java))
                stopSelf()
                return
            }
            handler.postDelayed(this, 5000)
        }
    }

    private fun refreshScreenshotShortcut() {
        val access = PhotoAccess.current(this)
        val canWatch = screenshotShortcut.canWatch(access)
        if (canWatch && !screenshotObserverRegistered) {
            screenshotObserverRegistered = runCatching {
                // Only watch screenshots taken after enabling this overlay session.
                screenshots.startSession()
                contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, screenshotObserver)
            }.isSuccess
        } else if (!canWatch && screenshotObserverRegistered) {
            contentResolver.unregisterContentObserver(screenshotObserver)
            screenshotObserverRegistered = false
            handler.removeCallbacks(checkScreenshots)
            screenshotReadJob?.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()
        enabled = true
        startAsForeground()
        handler.post(refreshShortcut)
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
    }

    private fun solveCapture(path: String) {
        solveImage(Uri.fromFile(java.io.File(path)))
    }

    private fun solveImage(uri: Uri) {
        clearRoute()
        capturePending = false
        latestCaptureUri = uri

        toolbarView?.visibility = View.VISIBLE
        toolbarView?.setStatus("Reading board…", busy = true)
        solveJob = scope.launch {
            try {
                val settings = settingsStore.load()
                val recognized = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bitmap = BoardImageLoader.read(this@SolutionOverlayService, uri) ?: return@withContext null
                    try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        PadBoardRecognizer().recognize(bitmap) as? BoardRecognition.Recognized
                    } }
                    finally { bitmap.recycle() }
                }
                if (recognized == null) {
                    toolbarView?.setStatus("Board not recognized. Try another capture.", isError = true)
                    return@launch
                }
                toolbarView?.setStatus("Finding your route…", busy = true)
                val result = BoardSolver().solve(recognized.board, SolveOptions.forBoard(recognized.board, settings.toGoal()))
                if (result.goalSatisfied) {
                    showRoute(recognized.boardBounds, result.path.map(recognized.board::indexOf).toIntArray(),
                        recognized.captureWidth, recognized.captureHeight, recognized.board.columns, recognized.board.rows)
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
            ACTION_REFRESH_SHORTCUT -> {
                refreshScreenshotShortcut()
                return START_NOT_STICKY
            }
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
                // Preserve screenshot solving and existing routes when projection ends.
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
                toolbarView?.visibility = View.INVISIBLE
                return START_NOT_STICKY
            }
            ACTION_SOLVE_CAPTURE -> {
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
        showRoute(bounds, positions, captureWidth, captureHeight, columns, rows)
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
            setStatus("")
        }
        toolbarView = toolbar
        refreshScreenshotShortcut()
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
        startActivity(Intent(this, MainActivity::class.java).apply {
            latestCaptureUri?.let { uri ->
                if (uri.scheme == "file") putExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH, uri.path)
                else data = uri
            }
            putExtra(EXTRA_OPEN_SETTINGS, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        // Remove the controls from the settings screen, retaining the sharing session
        // so showing the adjusted route does not require another consent prompt.
        stopSelf()
    }

    override fun onDestroy() {
        enabled = false
        handler.removeCallbacksAndMessages(null)
        if (screenshotObserverRegistered) contentResolver.unregisterContentObserver(screenshotObserver)
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
            .setContentText("Ready to process your screenshots or capture a board.")
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
        @Volatile var enabled = false
            private set
        const val ACTION_PROCESSING = "com.example.phoneapp.action.PROCESSING"
        const val ACTION_ENABLE = "com.example.phoneapp.action.ENABLE_OVERLAY"
        const val ACTION_REFRESH_SHORTCUT = "com.example.phoneapp.action.REFRESH_SCREENSHOT_SHORTCUT"
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
