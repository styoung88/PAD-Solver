package com.example.phoneapp

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PadBoardRecognizer
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.ui.BoardPreviewScreen
import com.example.phoneapp.ui.HomeScreen
import com.example.phoneapp.ui.SolutionScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var capturePath by mutableStateOf<String?>(null)
    private var recognition by mutableStateOf<BoardRecognition?>(null)
    private var solution by mutableStateOf<BoardSolution?>(null)
    private var isReadingCapture by mutableStateOf(false)
    private var isSolving by mutableStateOf(false)
    private var pendingRoutePermission = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
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
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchMediaProjectionConsent()
        else Toast.makeText(
            this,
            "Allow notifications to use the capture action while Puzzles & Dragons is open.",
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH)?.let(::loadCapture)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        isReadingCapture -> LoadingMessage("Reading the captured board…")
                        solution != null && recognition is BoardRecognition.Recognized -> {
                            val board = (recognition as BoardRecognition.Recognized).board
                            SolutionScreen(
                                board = board,
                                solution = solution!!,
                                onShowInGame = { showSolutionInPad(solution!!) },
                                onHideRoute = { stopService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_HIDE)) },
                                onNewBoard = { resetToHome() },
                            )
                        }
                        recognition != null -> BoardPreviewScreen(
                            recognition = recognition!!,
                            isSolving = isSolving,
                            onSolve = { solveBoard() },
                            onRetry = { resetToHome(); startCaptureFlow() },
                        )
                        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            HomeScreen(onCapture = ::startCaptureFlow)
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
    }

    private fun startCaptureFlow() {
        requestScreenCapturePermission()
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
        capturePath = path
        recognition = null
        solution = null
        isReadingCapture = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                if (bitmap == null) {
                    BoardRecognition.Uncertain(0)
                } else {
                    try {
                        PadBoardRecognizer().recognize(bitmap)
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
        isSolving = true
        lifecycleScope.launch {
            solution = runCatching { BoardSolver().solve(board) }
                .onFailure {
                    Toast.makeText(this@MainActivity, "Could not find a route. Please try again.", Toast.LENGTH_LONG).show()
                }
                .getOrNull()
            isSolving = false
        }
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
        val overlayIntent = Intent(this, SolutionOverlayService::class.java).apply {
            action = SolutionOverlayService.ACTION_SHOW
            putExtra(SolutionOverlayService.EXTRA_BOARD_BOUNDS, recognized.boardBounds)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_WIDTH, recognized.captureWidth)
            putExtra(SolutionOverlayService.EXTRA_CAPTURE_HEIGHT, recognized.captureHeight)
            putExtra(SolutionOverlayService.EXTRA_PATH, result.path.map(recognized.board::indexOf).toIntArray())
        }
        startService(overlayIntent)

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
        capturePath = null
        recognition = null
        solution = null
        isReadingCapture = false
        isSolving = false
        stopService(Intent(this, SolutionOverlayService::class.java).setAction(SolutionOverlayService.ACTION_HIDE))
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
