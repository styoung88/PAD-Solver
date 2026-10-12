package com.example.phoneapp.ui

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
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
import com.example.phoneapp.game.GameVariant
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import com.example.phoneapp.solver.PadZeroTeamSkill
import com.example.phoneapp.board.OrbColor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        SolveSettingsStore(compose.activity).save(SolveSettings())
        compose.activityRule.scenario.recreate()
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
        compose.onNodeWithText("Apply and solve").performClick()

        waitForText("Show route in PAD")
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Wood").performScrollTo().assertIsDisplayed()
        selectChoice("Optimize color damage for", "Fire")
        compose.onNodeWithText("Apply and recalculate").performClick()
        waitForText("Fire combos")
        compose.onNodeWithText("Wood combos").assertDoesNotExist()
        compose.onNodeWithText("Show route in PAD").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualGameChoiceClearsCapturedBoardAndSurvivesRecreation() {
        SolveSettingsStore(compose.activity).save(SolveSettings())
        compose.activityRule.scenario.recreate()
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        selectChoice("Game", "PAD 0")
        assertEquals(GameVariant.PAD_ZERO, SolveSettingsStore(compose.activity).load().game)
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Game: PAD 0").assertDoesNotExist()
        compose.onNodeWithText("Board captured").assertDoesNotExist()
        compose.onNodeWithText("Open PAD 0 overlay").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        closeSettingsIfOpen()
        compose.onNodeWithText("Game: PAD 0").assertDoesNotExist()
        compose.onNodeWithText("Open PAD 0 overlay").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        selectChoice("Game", "PAD")
        assertEquals(GameVariant.PAD, SolveSettingsStore(compose.activity).load().game)
    }

    @Test
    fun padZeroCaptureRecognizesBonusesAndOffersARoute() {
        SolveSettingsStore(compose.activity).save(SolveSettings(game = GameVariant.PAD_ZERO,
            orbMovement = com.example.phoneapp.solver.OrbMovement.LOW,
            searchComplexity = com.example.phoneapp.solver.SearchComplexity.LOW))
        compose.activityRule.scenario.recreate()
        closeSettingsIfOpen()
        loadCapture(openSettings = false, fixture = "pad-zero/1000013017.jpg")
        waitForText("Board captured")
        compose.onNodeWithText("Top-right +1 badges add one combo per marked orb cleared in a match.")
            .assertDoesNotExist()
        exportScreenshot("pad-zero-board-preview.png")
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        // The inventory bound includes the two marked orbs: 8 physical + 2 bonus.
        selectChoice("Minimum combos", "10")
        assertEquals(10, SolveSettingsStore(compose.activity).load().targetComboCount)
        selectChoice("Minimum combos", "No minimum")
        selectChoice("Team skill", "Expert Combo")
        compose.onNodeWithText("Apply and solve").performClick()
        waitForText("Show route in PAD 0")
        compose.onNodeWithText("Team ATK ×10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Damage reduction: 50%").performScrollTo().assertIsDisplayed()
        exportScreenshot("pad-zero-solution.png")
        compose.onNodeWithText("Game: PAD 0").assertDoesNotExist()
        compose.onNodeWithText("Show route in PAD").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        selectChoice("Game", "PAD")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Game: PAD").assertDoesNotExist()
        compose.onNodeWithText("Open PAD overlay").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show route in PAD 0").assertDoesNotExist()
    }

    @Test fun teamSkillControlsPersistAndOnlyOfferRelevantRequirements() {
        SolveSettingsStore(compose.activity).save(SolveSettings(game = GameVariant.PAD_ZERO))
        compose.activityRule.scenario.recreate()
        closeSettingsIfOpen()
        compose.onNodeWithContentDescription("Open solve settings").performClick()
        selectChoice("Team skill", "Multicolor")
        compose.onNodeWithContentDescription("Dark attack attribute").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Damage reduction").performScrollTo().performClick()
        val saved = SolveSettingsStore(compose.activity).load()
        assertEquals(PadZeroTeamSkill.MULTICOLOR, saved.padZeroTeamSkill)
        assertEquals(OrbColor.attackingColors.toSet() - OrbColor.DARK, saved.teamAttackingAttributes)
        assertTrue(saved.requireDamageReduction)
        compose.onNodeWithText("Close").performClick()
        compose.activityRule.scenario.recreate()
        closeSettingsIfOpen()
        compose.onNodeWithContentDescription("Open solve settings").performClick()
        compose.onNodeWithText("Multicolor").performScrollTo().assertIsDisplayed()
        assertEquals(saved, SolveSettingsStore(compose.activity).load())
        selectChoice("Team skill", "Expert Multicolor")
        compose.onNodeWithContentDescription("Dark attack attribute").assertDoesNotExist()
        compose.onNodeWithContentDescription("Damage reduction").performScrollTo().assertIsDisplayed()
        assertEquals(saved.teamAttackingAttributes, SolveSettingsStore(compose.activity).load().teamAttackingAttributes)
        selectChoice("Team skill", "5 Color Erasure")
        compose.onNodeWithContentDescription("Dark attack attribute").assertDoesNotExist()
        compose.onNodeWithContentDescription("Damage reduction").assertDoesNotExist()
        assertEquals(saved.teamAttackingAttributes, SolveSettingsStore(compose.activity).load().teamAttackingAttributes)
        selectChoice("Team skill", "Combo")
        compose.onNodeWithContentDescription("Damage reduction").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dark attack attribute").assertDoesNotExist()
        assertEquals(false, SolveSettingsStore(compose.activity).load().requireDamageReduction)
        selectChoice("Game", "PAD")
        compose.onNodeWithText("Team skill").assertDoesNotExist()
        selectChoice("Game", "PAD 0")
        compose.onNodeWithText("Combo").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overlayRequestOpensTheSameDrawerAsTheBoardButton() {
        loadCapture(openSettings = true)
        waitForText("Apply and solve")
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Board captured").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open solve settings").performScrollTo().performClick()
        compose.onAllNodesWithText("Minimum combos").assertCountEquals(1)
        compose.onNodeWithText("Apply and solve").assertIsDisplayed()
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
        compose.onNodeWithText("Close").performClick()
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
        compose.onNodeWithText("Apply and solve").performClick()
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

    private fun exportScreenshot(name: String) {
        compose.onNodeWithText("PAD Solver").performScrollTo()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertTrue(androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
            .takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), name)))
    }

    private fun closeSettingsIfOpen() {
        compose.waitForIdle()
        val close = compose.onNodeWithText("Close")
        // Check horizontal visibility because the drawer remains composed when closed.
        if (close.getUnclippedBoundsInRoot().right > 0.dp) {
            close.performClick()
            compose.waitForIdle()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val node = compose.onNodeWithText(text)
        if (text !in setOf("Board not clear enough", "Apply and solve")) node.performScrollTo()
        node.assertIsDisplayed()
    }

    private fun loadCapture(openSettings: Boolean, fixture: String = "pad-board-reference.png") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "settings-drawer-test.png")
        captureFile = file
        instrumentation.context.assets.open(fixture).use { input ->
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
