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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.phoneapp.MainActivity
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SolveSettingsDrawerTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private var captureFile: File? = null

    @After
    fun cleanUp() {
        captureFile?.delete()
    }

    @Test
    fun boardSettingsAndRecalculationUseOneDrawer() {
        loadCapture(openSettings = false)
        waitForText("Board captured")
        compose.onNodeWithContentDescription("Open solve settings").performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        selectChoice("Minimum combos", "No minimum")
        selectChoice("Match pattern", "No pattern requirement")
        selectChoice("Optimize color damage for", "Wood")
        compose.onNodeWithText("Apply and solve").performClick()

        waitForText("Show route in PAD")
        compose.onNodeWithContentDescription("Open solve settings").performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Wood").assertIsDisplayed()
        compose.onNodeWithText("Apply and recalculate").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Show route in PAD").assertIsDisplayed()
    }

    @Test
    fun overlayRequestOpensTheSameDrawerAsTheBoardButton() {
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Board captured").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open solve settings").performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Apply and solve").assertIsDisplayed()
    }

    private fun selectChoice(label: String, choice: String) {
        compose.onNodeWithText(label).performClick()
        compose.onNode(hasText(choice) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun loadCapture(openSettings: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "settings-drawer-test.png")
        captureFile = file
        instrumentation.context.assets.open("pad-board-reference.png").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
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
