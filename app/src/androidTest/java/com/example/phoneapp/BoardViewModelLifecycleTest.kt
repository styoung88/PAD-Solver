package com.example.phoneapp

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BoardViewModelLifecycleTest {
    @Test
    fun sizeChangesRereadTheCaptureCancelSolvingAndResetForgetTheImage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previous = SolveSettingsStore(context).load()
        val file = File(context.filesDir, "board-size-reread-test.png")
        instrumentation.context.assets.open("pad-board-reference.png").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            lateinit var model: BoardViewModel
            try {
                scenario.onActivity {
                    model = ViewModelProvider(it)[BoardViewModel::class.java]
                    model.updateSolveSettings(SolveSettings())
                    model.loadCapture(file.absolutePath)
                }
                fun awaitCapture() {
                    val deadline = System.nanoTime() + 30_000_000_000L
                    var reading = true
                    while (reading && System.nanoTime() < deadline) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { reading = model.isReadingCapture }
                        if (reading) android.os.SystemClock.sleep(20)
                    }
                    assertFalse("Recognition must finish", reading)
                }
                awaitCapture()
                val original = model.recognition
                assertTrue(original is BoardRecognition.Recognized)
                scenario.onActivity {
                    model.solveBoard()
                    assertTrue(model.isSolving)
                    model.updateSolveSettings(model.solveSettings.copy(boardSizeOverride = BoardSize.STANDARD, targetComboCount = 14))
                    assertTrue(model.isReadingCapture)
                    assertFalse(model.isSolving)
                    assertNull(model.recognition)
                    assertNull(model.solution)
                }
                awaitCapture()
                scenario.onActivity {
                    assertNotSame(original, model.recognition)
                    assertEquals(6, (model.recognition as BoardRecognition.Recognized).board.columns)
                    assertEquals(BoardSize.STANDARD, SolveSettingsStore(context).load().boardSizeOverride)
                    assertEquals(14, model.solveSettings.targetComboCount)
                    model.reset()
                    model.updateSolveSettings(model.solveSettings.copy(boardSizeOverride = BoardSize.SMALL))
                    assertFalse(model.isReadingCapture)
                    assertNull(model.recognition)
                }
            } finally {
                scenario.onActivity { model.reset() }
                SolveSettingsStore(context).save(previous)
                file.delete()
            }
        }
    }

    @Test
    fun recreationRetainsBoardAndResetCancelsPendingLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val captureIntent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .setData(Uri.parse("file:///missing-board-image.png"))
        ActivityScenario.launch<MainActivity>(captureIntent).use { scenario ->
            lateinit var original: BoardViewModel
            scenario.onActivity {
                original = ViewModelProvider(it)[BoardViewModel::class.java]
            }
            val deadline = System.nanoTime() + 5_000_000_000L
            var reading = true
            while (reading && System.nanoTime() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { reading = original.isReadingCapture }
            }
            assertFalse("Image loading must finish", reading)
            scenario.recreate()
            scenario.onActivity {
                val retained = ViewModelProvider(it)[BoardViewModel::class.java]
                assertSame(original, retained)
                assertTrue(retained.recognition is BoardRecognition.Uncertain)
                retained.loadCapture("/another-missing-board.png")
                retained.reset()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertNull(original.recognition)
                assertNull(original.solution)
                assertFalse(original.isReadingCapture)
                assertFalse(original.isSolving)
                val fresh = BoardViewModel(it.application)
                assertNull(fresh.recognition)
                assertNull(fresh.solution)
            }
        }
    }
}
