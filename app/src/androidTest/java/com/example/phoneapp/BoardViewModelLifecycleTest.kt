package com.example.phoneapp

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.phoneapp.capture.BoardRecognition
import org.junit.Assert.*
import org.junit.Test

class BoardViewModelLifecycleTest {
    @Test
    fun recreationRetainsBoardAndResetCancelsPendingLoad() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var original: BoardViewModel
            scenario.onActivity {
                original = ViewModelProvider(it)[BoardViewModel::class.java]
                original.loadCapture("/missing-board-image.png")
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
