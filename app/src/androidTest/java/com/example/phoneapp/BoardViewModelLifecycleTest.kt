package com.example.phoneapp

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.game.GameVariant
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import org.junit.Assert.*
import org.junit.Test

class BoardViewModelLifecycleTest {
    @Test
    fun changingGameClearsTheBoardAndCancelsSolvingAndLoading() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val store = SolveSettingsStore(instrumentation.targetContext)
        val previousSettings = store.load()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BoardViewModel
            scenario.onActivity { model = BoardViewModel(it.application) }
            val file = java.io.File(instrumentation.targetContext.cacheDir, "game-selection-board.png")
            instrumentation.context.assets.open("pad-board-reference.png").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            try {
                scenario.onActivity {
                    model.updateSolveSettings(SolveSettings())
                    model.loadCapture(file.absolutePath)
                }
                val deadline = System.nanoTime() + 10_000_000_000L
                var reading = true
                while (reading && System.nanoTime() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { reading = model.isReadingCapture }
                }
                scenario.onActivity {
                    assertTrue(model.recognition is BoardRecognition.Recognized)
                    model.solveBoard()
                    assertTrue(model.isSolving)
                    model.updateSolveSettings(SolveSettings(game = GameVariant.PAD_ZERO))
                    assertNull(model.recognition)
                    assertNull(model.solution)
                    assertFalse(model.isSolving)
                    model.updateSolveSettings(SolveSettings())
                    model.loadCapture(file.absolutePath)
                    model.updateSolveSettings(SolveSettings(game = GameVariant.PAD_ZERO))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertNull(model.recognition)
                    assertNull(model.solution)
                    assertFalse(model.isReadingCapture)
                    assertFalse(model.isSolving)
                }
            } finally {
                scenario.onActivity {
                    model.reset()
                    store.save(previousSettings)
                }
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
