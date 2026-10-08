package com.example.phoneapp

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.view.WindowCompat
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.capture.PhotoAccess
import com.example.phoneapp.capture.ScreenshotShortcut
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.ui.BoardPreviewScreen
import com.example.phoneapp.ui.HomeScreen
import com.example.phoneapp.ui.PhotoAccessSettingsDialog
import com.example.phoneapp.ui.OverlayPermissionDialog
import com.example.phoneapp.ui.SolveSettingsDrawer
import com.example.phoneapp.ui.SolutionScreen

class MainActivity : ComponentActivity() {
    private val boardState by lazy { androidx.lifecycle.ViewModelProvider(this)[BoardViewModel::class.java] }
    private val recognition get() = boardState.recognition
    private val solution get() = boardState.solution
    private val solveSettings get() = boardState.solveSettings
    private val isReadingCapture get() = boardState.isReadingCapture
    private val isSolving get() = boardState.isSolving
    private var overlaySettingsRequested by mutableStateOf(false)
    private var pendingRoutePermission = false
    private var pendingCapture = false
    private var pendingOverlayStart = false
    private val screenshotShortcut by lazy { ScreenshotShortcut(this) }
    private var screenshotShortcutEnabled by mutableStateOf(false)
    private var photoAccess by mutableStateOf(PhotoAccess.NONE)
    private var photoSettingsHelpVisible by mutableStateOf(false)
    private var overlayHelpVisible by mutableStateOf(false)
    private val photoPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPhotoAccess()
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (pendingOverlayStart) {
            if (Settings.canDrawOverlays(this)) startOverlay()
            else {
                pendingOverlayStart = false
                Toast.makeText(this, "To use the floating toolbar, allow PAD Solver to appear over other apps.", Toast.LENGTH_LONG).show()
            }
            return@registerForActivityResult
        }
        if (pendingRoutePermission) {
            pendingRoutePermission = false
            if (Settings.canDrawOverlays(this)) solution?.let(::showSolutionInPad)
            else Toast.makeText(this, "To show your route, allow PAD Solver to appear over other apps.", Toast.LENGTH_LONG).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (pendingCapture && SolutionOverlayService.enabled && result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_READY_RECEIVER, object : android.os.ResultReceiver(android.os.Handler(mainLooper)) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        pendingCapture = false
                        if (resultCode == 1 && SolutionOverlayService.enabled) {
                            openPad()
                            android.os.Handler(mainLooper).postDelayed({
                                if (SolutionOverlayService.enabled) startService(Intent(this@MainActivity, ScreenCaptureService::class.java)
                                    .setAction(ScreenCaptureService.ACTION_CAPTURE_NOW))
                            }, 500)
                        } else {
                            Toast.makeText(this@MainActivity, "Screen capture couldn't start. Try Capture again.", Toast.LENGTH_LONG).show()
                            moveTaskToBack(true)
                        }
                    }
                })
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            pendingCapture = false
            moveTaskToBack(true)
        }
    }

    private val screenshotPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::loadCapture) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        pendingCapture = savedInstanceState?.getBoolean("pending_capture") ?: false
        pendingOverlayStart = savedInstanceState?.getBoolean("pending_overlay") ?: false
        pendingRoutePermission = savedInstanceState?.getBoolean("pending_route") ?: false
        screenshotShortcutEnabled = screenshotShortcut.enabled
        refreshPhotoAccess()
        boardState.refreshSettings()
        if (savedInstanceState == null) consumeBoardIntent(intent)
        overlaySettingsRequested = intent.getBooleanExtra(SolutionOverlayService.EXTRA_OPEN_SETTINGS, false)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (overlayHelpVisible) OverlayPermissionDialog(
                        onOpenSettings = {
                            overlayHelpVisible = false
                            overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        },
                        onDismiss = {
                            overlayHelpVisible = false
                            pendingOverlayStart = false
                            pendingRoutePermission = false
                        },
                    )
                    if (photoSettingsHelpVisible) {
                        PhotoAccessSettingsDialog(
                            onOpenSettings = {
                                photoSettingsHelpVisible = false
                                openPhotoSettings()
                            },
                            onDismiss = { photoSettingsHelpVisible = false },
                        )
                    }
                    SolveSettingsDrawer(
                        settings = solveSettings,
                        isSolving = isSolving || isReadingCapture,
                        board = (recognition as? BoardRecognition.Recognized)?.board,
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
                                    onCapture = ::startOverlay,
                                    onChooseScreenshot = ::chooseExistingScreenshot,
                                    screenshotShortcutEnabled = screenshotShortcutEnabled,
                                    photoAccess = photoAccess,
                                    onScreenshotShortcutChanged = ::updateScreenshotShortcut,
                                    onManagePhotoAccess = ::managePhotoAccess,
                                    onOpenSettings = openSettings,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        handleCaptureRequest()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("pending_capture", pendingCapture)
        outState.putBoolean("pending_overlay", pendingOverlayStart)
        outState.putBoolean("pending_route", pendingRoutePermission)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaptureRequest()
        consumeBoardIntent(intent)
        if (intent.getBooleanExtra(SolutionOverlayService.EXTRA_OPEN_SETTINGS, false)) {
            boardState.cancelSolve()
            overlaySettingsRequested = true
        }
    }

    private fun startOverlay() {
        pendingOverlayStart = true
        if (!Settings.canDrawOverlays(this)) {
            overlayHelpVisible = true
            return
        }
        pendingOverlayStart = false
        ContextCompat.startForegroundService(this, Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_ENABLE))
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
        if (photoAccess == PhotoAccess.FULL) photoSettingsHelpVisible = false
        if (SolutionOverlayService.enabled) {
            startService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_REFRESH_SHORTCUT))
        }
    }

    private fun updateScreenshotShortcut(enabled: Boolean) {
        screenshotShortcut.enabled = enabled
        screenshotShortcutEnabled = enabled
        refreshPhotoAccess()
        if (enabled && photoAccess != PhotoAccess.FULL) {
            managePhotoAccess()
        }
    }

    private fun managePhotoAccess() {
        refreshPhotoAccess()
        if (photoAccess == PhotoAccess.FULL) return
        val permissions = PhotoAccess.requestedPermissions()
        if (screenshotShortcut.shouldOpenPhotoSettings(photoAccess, shouldShowRequestPermissionRationale(permissions.first()))) {
            photoSettingsHelpVisible = true
        } else {
            screenshotShortcut.photoAccessRequested = true
            photoPermissionLauncher.launch(permissions)
        }
    }

    private fun openPhotoSettings() {
        startActivity(PhotoAccess.settingsIntent(packageName))
    }

    private fun handleCaptureRequest() {
        if (!intent.getBooleanExtra(SolutionOverlayService.EXTRA_REQUEST_CAPTURE, false)) return
        intent.removeExtra(SolutionOverlayService.EXTRA_REQUEST_CAPTURE)
        if (pendingCapture || !SolutionOverlayService.enabled) return
        pendingCapture = true
        launchMediaProjectionConsent()
    }

    private fun launchMediaProjectionConsent() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun loadCapture(uri: Uri) = boardState.loadCapture(uri)
    private fun solveBoard() = boardState.solveBoard()
    private fun updateSolveSettings(settings: SolveSettings) = boardState.updateSolveSettings(settings)

    private fun showSolutionInPad(result: BoardSolution) {
        val recognized = recognition as? BoardRecognition.Recognized ?: return
        if (!Settings.canDrawOverlays(this)) {
            pendingRoutePermission = true
            overlayHelpVisible = true
            return
        }
        val overlayIntent = Intent(this, SolutionOverlayService::class.java).apply {
            action = SolutionOverlayService.ACTION_SHOW
            putExtra(SolutionOverlayService.EXTRA_BOARD_BOUNDS, recognized.boardBounds)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_WIDTH, recognized.captureWidth)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_HEIGHT, recognized.captureHeight)
            putExtra(SolutionOverlayService.EXTRA_BOARD_COLUMNS, recognized.board.columns)
            putExtra(SolutionOverlayService.EXTRA_BOARD_ROWS, recognized.board.rows)
            putExtra(SolutionOverlayService.EXTRA_PATH, result.path.map(recognized.board::indexOf).toIntArray())
            putExtra(SolutionOverlayService.EXTRA_COMBO_COUNT, result.comboCount)
        }
        ContextCompat.startForegroundService(this, overlayIntent)

        openPad()
    }

    private fun openPad() {
        val padIntent = findPadLaunchIntent()
        if (padIntent != null) {
            startActivity(padIntent)
        } else {
            Toast.makeText(this, "Switch to Puzzles & Dragons to use the overlay.", Toast.LENGTH_LONG).show()
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
        boardState.reset()
        stopService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_HIDE))
    }

    private fun consumeBoardIntent(source: Intent) {
        source.getStringExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH)?.let(boardState::loadCapture)
        source.data?.let(boardState::loadCapture)
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
