package com.example.phoneapp

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PadBoardRecognizer
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.capture.PhotoAccess
import com.example.phoneapp.capture.ScreenshotShortcut
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.solver.SolveOptions
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import com.example.phoneapp.ui.BoardPreviewScreen
import com.example.phoneapp.ui.HomeScreen
import com.example.phoneapp.ui.SolveSettingsDrawer
import com.example.phoneapp.ui.SolutionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var recognition by mutableStateOf<BoardRecognition?>(null)
    private var solution by mutableStateOf<BoardSolution?>(null)
    private var solveSettings by mutableStateOf(SolveSettings())
    private val settingsStore by lazy { SolveSettingsStore(this) }
    private var isReadingCapture by mutableStateOf(false)
    private var isSolving by mutableStateOf(false)
    private var solveRunId = 0
    private var solveJob: kotlinx.coroutines.Job? = null
    private var boardLoadJob: kotlinx.coroutines.Job? = null
    private var overlaySettingsRequested by mutableStateOf(false)
    private var pendingRoutePermission = false
    private var pendingCaptureRoute = false
    private var pendingOverlayStart = false
    private val screenshotShortcut by lazy { ScreenshotShortcut(this) }
    private var screenshotShortcutEnabled by mutableStateOf(true)
    private var photoAccess by mutableStateOf(PhotoAccess.NONE)
    private val photoPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPhotoAccess()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingOverlayStart) {
            if (Settings.canDrawOverlays(this)) startCaptureFlow()
            else {
                pendingOverlayStart = false
                Toast.makeText(this, "Allow draw-over-apps access to open the PAD overlay.", Toast.LENGTH_LONG).show()
            }
            return@registerForActivityResult
        }
        if (pendingRoutePermission) {
            pendingRoutePermission = false
            if (Settings.canDrawOverlays(this)) solution?.let(::showSolutionInPad)
            else Toast.makeText(this, "Allow draw-over-apps access to show the route.", Toast.LENGTH_LONG).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_READY_RECEIVER, object : android.os.ResultReceiver(android.os.Handler(mainLooper)) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (resultCode != 1) return
                        if (pendingOverlayStart) startCaptureFlow()
                        else if (pendingCaptureRoute) {
                            pendingCaptureRoute = false
                            solution?.let(::showSolutionInPad)
                        }
                    }
                })
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            pendingOverlayStart = false
            pendingCaptureRoute = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchMediaProjectionConsent()
        else Toast.makeText(
            this,
            "Allow notifications to keep the PAD overlay capture session active.",
            Toast.LENGTH_LONG,
        ).show()
    }

    private val screenshotPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::loadCapture) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenshotShortcutEnabled = screenshotShortcut.enabled
        refreshPhotoAccess()
        solveSettings = settingsStore.load()
        intent.getStringExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH)?.let(::loadCapture)
        overlaySettingsRequested = intent.getBooleanExtra(SolutionOverlayService.EXTRA_OPEN_SETTINGS, false)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SolveSettingsDrawer(
                        settings = solveSettings,
                        isSolving = isSolving,
                        onSettingsChanged = ::updateSolveSettings,
                        actionLabel = when {
                            solution != null -> if (isSolving) "Recalculating route…" else "Apply and recalculate"
                            recognition is BoardRecognition.Recognized -> if (isSolving) "Solving…" else "Apply and solve"
                            else -> "Done"
                        },
                        onApply = ::solveBoard,
                        openRequested = overlaySettingsRequested && !isReadingCapture,
                        onOpenHandled = { overlaySettingsRequested = false },
                    ) { openSettings ->
                        when {
                            isReadingCapture -> LoadingMessage("Reading the captured board…")
                            solution != null && recognition is BoardRecognition.Recognized -> {
                                SolutionScreen(
                                    recognition = recognition as BoardRecognition.Recognized,
                                    solution = solution!!,
                                    isSolving = isSolving,
                                    onOpenSettings = openSettings,
                                    onShowInGame = { showSolutionInPad(solution!!) },
                                    onNewBoard = { resetToHome() },
                                )
                            }
                            recognition != null -> BoardPreviewScreen(
                                recognition = recognition!!,
                                isSolving = isSolving,
                                settings = solveSettings,
                                onOpenSettings = openSettings,
                                onNewBoard = ::resetToHome,
                            )
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                HomeScreen(
                                    onCapture = ::startCaptureFlow,
                                    onChooseScreenshot = ::chooseExistingScreenshot,
                                    screenshotShortcutEnabled = screenshotShortcutEnabled,
                                    photoAccess = photoAccess,
                                    onScreenshotShortcutChanged = ::updateScreenshotShortcut,
                                    onRequestPhotoAccess = { photoPermissionLauncher.launch(PhotoAccess.requestedPermissions()) },
                                    onPhotoSettings = {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH)?.let(::loadCapture)
        if (intent.getBooleanExtra(SolutionOverlayService.EXTRA_OPEN_SETTINGS, false)) {
            solveJob?.cancel()
            isSolving = false
            overlaySettingsRequested = true
        }
    }

    private fun startCaptureFlow() {
        pendingOverlayStart = true
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!ScreenCaptureService.active) {
            requestScreenCapturePermission()
            return
        }
        pendingOverlayStart = false
        startService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_ENABLE))
        openPad()
    }

    private fun chooseExistingScreenshot() {
        screenshotPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    override fun onResume() {
        super.onResume()
        refreshPhotoAccess()
    }

    private fun refreshPhotoAccess() {
        photoAccess = PhotoAccess.current(this)
        if (SolutionOverlayService.enabled) {
            startService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_REFRESH_SHORTCUT))
        }
    }

    private fun updateScreenshotShortcut(enabled: Boolean) {
        screenshotShortcut.enabled = enabled
        screenshotShortcutEnabled = enabled
        refreshPhotoAccess()
        if (enabled && photoAccess != PhotoAccess.FULL) {
            photoPermissionLauncher.launch(PhotoAccess.requestedPermissions())
        }
    }

    private fun requestScreenCapturePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchMediaProjectionConsent()
    }

    private fun launchMediaProjectionConsent() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun loadCapture(path: String) {
        loadImage { android.graphics.BitmapFactory.decodeFile(path) }
    }

    private fun loadCapture(uri: Uri) {
        loadImage {
            contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun loadImage(decodeBitmap: () -> android.graphics.Bitmap?) {
        solveJob?.cancel()
        boardLoadJob?.cancel()
        isSolving = false
        recognition = null
        solution = null
        isReadingCapture = true
        boardLoadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val bitmap = runCatching { decodeBitmap() }.getOrNull()
                if (bitmap == null) {
                    BoardRecognition.Uncertain(0)
                } else {
                    try {
                        withContext(Dispatchers.Default) {
                            PadBoardRecognizer().recognize(bitmap)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            recognition = result
            isReadingCapture = false
        }
    }

    private fun solveBoard() {
        val board = (recognition as? BoardRecognition.Recognized)?.board ?: return
        if (isSolving) return
        val settings = solveSettings
        isSolving = true
        solveJob = lifecycleScope.launch {
            val runId = ++solveRunId
            val startedAt = SystemClock.elapsedRealtime()
            val goal = settings.toGoal()
            Log.i(
                SOLVER_LOG_TAG,
                "solve[$runId] start color=${settings.preferredColor} " +
                    "targetCombos=${settings.targetComboCount} pattern=${settings.matchPatternFocus}",
            )
            runCatching { BoardSolver().solve(board, SolveOptions(goal = goal)) }
                .onSuccess { result ->
                    Log.i(
                        SOLVER_LOG_TAG,
                        "solve[$runId] complete elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                            "satisfied=${result.goalSatisfied} moves=${result.orbMoves} combos=${result.comboCount}",
                    )
                    solution = result
                    if (!result.goalSatisfied) {
                        stopService(Intent(this@MainActivity, SolutionOverlayService::class.java)
                            .setAction(SolutionOverlayService.ACTION_HIDE))
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    Log.e(
                        SOLVER_LOG_TAG,
                        "solve[$runId] failed elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
                        error,
                    )
                    Toast.makeText(this@MainActivity, "Could not find a route. Please try again.", Toast.LENGTH_LONG).show()
                }
            isSolving = false
        }
    }

    private fun updateSolveSettings(settings: SolveSettings) {
        solveSettings = settings
        settingsStore.save(settings)
    }

    private fun showSolutionInPad(result: BoardSolution) {
        val recognized = recognition as? BoardRecognition.Recognized ?: return
        if (!Settings.canDrawOverlays(this)) {
            pendingRoutePermission = true
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            overlayPermissionLauncher.launch(settingsIntent)
            return
        }
        if (!ScreenCaptureService.active) {
            pendingCaptureRoute = true
            requestScreenCapturePermission()
            return
        }
        val overlayIntent = Intent(this, SolutionOverlayService::class.java).apply {
            action = SolutionOverlayService.ACTION_SHOW
            putExtra(SolutionOverlayService.EXTRA_BOARD_BOUNDS, recognized.boardBounds)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_WIDTH, recognized.captureWidth)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_HEIGHT, recognized.captureHeight)
            putExtra(SolutionOverlayService.EXTRA_PATH, result.path.map(recognized.board::indexOf).toIntArray())
        }
        startService(overlayIntent)

        openPad()
    }

    private fun openPad() {
        val padIntent = findPadLaunchIntent()
        if (padIntent != null) {
            startActivity(padIntent)
        } else {
            Toast.makeText(this, "Route is showing. Switch to Puzzles & Dragons.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }

    private fun findPadLaunchIntent(): Intent? {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = packageManager.queryIntentActivities(launcher, 0)
        val padApp = apps.firstOrNull { item ->
            val label = item.loadLabel(packageManager).toString().lowercase()
            "puzzle" in label && "dragon" in label
        } ?: return null
        return Intent(launcher).setClassName(padApp.activityInfo.packageName, padApp.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun resetToHome() {
        recognition = null
        solution = null
        isReadingCapture = false
        isSolving = false
        stopService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_HIDE))
    }

    private companion object {
        const val SOLVER_LOG_TAG = "PadSolverSearch"
    }
}

@androidx.compose.runtime.Composable
private fun LoadingMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(message)
    }
}
