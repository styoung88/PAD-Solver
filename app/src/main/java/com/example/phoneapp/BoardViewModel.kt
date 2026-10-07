package com.example.phoneapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.BoardImageLoader
import com.example.phoneapp.capture.PadBoardRecognizer
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.BoardSolver
import com.example.phoneapp.solver.SolveOptions
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BoardViewModel(application: Application) : AndroidViewModel(application) {
    var recognition by mutableStateOf<BoardRecognition?>(null)
        private set
    var solution by mutableStateOf<BoardSolution?>(null)
        private set
    var solveSettings by mutableStateOf(SolveSettingsStore(application).load())
        private set
    private val settingsStore by lazy { SolveSettingsStore(application) }
    var isReadingCapture by mutableStateOf(false)
        private set
    var isSolving by mutableStateOf(false)
        private set
    private var solveRunId = 0
    private var solveJob: kotlinx.coroutines.Job? = null
    private var boardLoadJob: kotlinx.coroutines.Job? = null
    fun loadCapture(path: String) {
        loadImage { android.graphics.BitmapFactory.decodeFile(path) }
    }

    fun loadCapture(uri: Uri) {
        loadImage { BoardImageLoader.read(getApplication(), uri) }
    }

    private fun loadImage(decodeBitmap: () -> android.graphics.Bitmap?) {
        solveJob?.cancel()
        boardLoadJob?.cancel()
        isSolving = false
        recognition = null
        solution = null
        isReadingCapture = true
        boardLoadJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
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
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(SOLVER_LOG_TAG, "Board recognition failed", error)
                BoardRecognition.Uncertain(0)
            }
            recognition = result
            isReadingCapture = false
        }
    }

    fun solveBoard() {
        val board = (recognition as? BoardRecognition.Recognized)?.board ?: return
        if (isSolving) return
        val settings = solveSettings
        isSolving = true
        solveJob = viewModelScope.launch {
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
                        getApplication<Application>().stopService(Intent(getApplication<Application>(), SolutionOverlayService::class.java)
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
                    Toast.makeText(getApplication<Application>(), "Could not find a route. Please try again.", Toast.LENGTH_LONG).show()
                }
            isSolving = false
        }
    }

    fun updateSolveSettings(settings: SolveSettings) {
        solveSettings = settings
        settingsStore.save(settings)
    }

    fun refreshSettings() {
        solveSettings = settingsStore.load()
    }

    fun cancelSolve() {
        solveJob?.cancel()
        solveJob = null
        isSolving = false
    }
    fun reset() {
        boardLoadJob?.cancel()
        boardLoadJob = null
        cancelSolve()
        recognition = null
        solution = null
        isReadingCapture = false
    }
    private companion object {
        const val SOLVER_LOG_TAG = "PadSolverSearch"
    }
}
