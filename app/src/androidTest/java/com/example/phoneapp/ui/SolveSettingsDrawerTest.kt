package com.example.phoneapp.ui

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.MainActivity
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SolveSettingsDrawerTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private var captureFile: File? = null
    private lateinit var previousSettings: SolveSettings

    @Before
    fun rememberSettings() {
        previousSettings = SolveSettingsStore(compose.activity).load()
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After
    fun cleanUp() {
        SolveSettingsStore(compose.activity).save(previousSettings)
        captureFile?.delete()
    }

    @Test
    fun boardSettingsAndRecalculationUseOneDrawer() {
        loadCapture(openSettings = false)
        waitForText("Board captured")
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        selectChoice("Minimum combos", "No minimum")
        selectChoice("Match pattern", "No pattern requirement")
        selectChoice("Optimize color damage for", "Wood")
        compose.onNodeWithText("Apply and solve").performScrollTo().performClick()

        waitForText("Show route in PAD")
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Wood").performScrollTo().assertIsDisplayed()
        selectChoice("Optimize color damage for", "Fire")
        compose.onNodeWithText("Apply and recalculate").performScrollTo().performClick()
        waitForText("Fire combos")
        compose.onNodeWithText("Wood combos").assertDoesNotExist()
        compose.onNodeWithText("Show route in PAD").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overlayRequestOpensTheSameDrawerAsTheBoardButton() {
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Board captured").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Apply and solve").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsIncludingNonPresetTargetSurviveActivityRecreation() {
        SolveSettingsStore(compose.activity).save(SolveSettings(targetComboCount = 6))
        compose.activityRule.scenario.recreate()
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        compose.onNodeWithText("6").performScrollTo().assertIsDisplayed()
        selectChoice("Optimize color damage for", "Dark")
        assertEquals(com.example.phoneapp.board.OrbColor.DARK, SolveSettingsStore(compose.activity).load().preferredColor)
        selectChoice("Match pattern", "Cross")
        assertEquals(com.example.phoneapp.board.OrbColor.DARK, SolveSettingsStore(compose.activity).load().preferredColor)
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        // The capture intent retains the overlay's request to open settings.
        waitForText("Apply and solve")
        compose.onNodeWithText("6").performScrollTo().assertIsDisplayed()
        assertEquals(com.example.phoneapp.board.OrbColor.DARK, SolveSettingsStore(compose.activity).load().preferredColor)
        compose.onNodeWithText("Dark").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cross").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun corruptCaptureShowsRetryAndNewCaptureReplacesIt() {
        val file = File(compose.activity.filesDir, "invalid-capture.png")
        file.writeText("Not an image")
        try {
            sendCapture(file, openSettings = false)
            waitForText("Board not clear enough")
            compose.onNodeWithContentDescription("Open solve settings").assertDoesNotExist()
            loadCapture(openSettings = false)
            waitForText("Board captured")
            compose.onNodeWithText("Board not clear enough").assertDoesNotExist()
        } finally {
            file.delete()
        }
    }

    @Test
    fun aNewCaptureCancelsThePreviousSolveAndCannotDisplayItsRoute() {
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        selectChoice("Minimum combos", "No minimum")
        selectChoice("Match pattern", "No pattern requirement")
        compose.onNodeWithText("Apply and solve").performScrollTo().performClick()
        val missing = File(compose.activity.filesDir, "missing-capture.png")
        sendCapture(missing, openSettings = false)
        waitForText("Board not clear enough")
        compose.waitForIdle()
        compose.onNodeWithText("Board not clear enough").assertIsDisplayed()
        compose.onNodeWithText("Show route in PAD").assertDoesNotExist()
    }

    private fun selectChoice(label: String, choice: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNode(hasText(choice) and hasAnyAncestor(isPopup())).performScrollTo().performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val node = compose.onNodeWithText(text)
        if (text != "Board not clear enough") node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun loadCapture(openSettings: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "settings-drawer-test.png")
        captureFile = file
        instrumentation.context.assets.open("pad-board-reference.png").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        sendCapture(file, openSettings)
    }

    private fun sendCapture(file: File, openSettings: Boolean) {
        compose.runOnUiThread {
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.startActivity(Intent(compose.activity.intent).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ScreenCaptureService.EXTRA_CAPTURE_PATH, file.absolutePath)
                putExtra(SolutionOverlayService.EXTRA_OPEN_SETTINGS, openSettings)
            })
        }
    }
}
