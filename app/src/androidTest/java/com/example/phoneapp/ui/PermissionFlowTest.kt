package com.example.phoneapp.ui

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.phoneapp.awaitNativeObject
import com.example.phoneapp.MainActivity
import com.example.phoneapp.capture.ScreenCaptureService
import com.example.phoneapp.capture.SolutionOverlayService
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.SolveSettingsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import java.io.File
import java.util.regex.Pattern

class PermissionFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val preferences = context.getSharedPreferences("capture_preferences", Context.MODE_PRIVATE)
    private val settings = SolveSettingsStore(context)
    private lateinit var originalSettings: SolveSettings
    private var originalScreenshotChoice: Boolean? = null
    private var originalOverlayMode = AppOpsManager.MODE_DEFAULT
    private val timeout = 15_000L

    @Before fun prepare() {
        context.stopService(Intent(context, ScreenCaptureService::class.java))
        context.stopService(Intent(context, SolutionOverlayService::class.java))
        instrumentation.waitForIdleSync()
        originalScreenshotChoice = if (preferences.contains("screenshot_shortcut")) preferences.getBoolean("screenshot_shortcut", false) else null
        preferences.edit().putBoolean("screenshot_shortcut", false).commit()
        originalSettings = settings.load()
        settings.save(SolveSettings())
        @Suppress("DEPRECATION")
        originalOverlayMode = (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, context.applicationInfo.uid, context.packageName)
    }

    @After fun restore() {
        context.stopService(Intent(context, ScreenCaptureService::class.java))
        context.stopService(Intent(context, SolutionOverlayService::class.java))
        settings.save(originalSettings)
        preferences.edit().apply {
            originalScreenshotChoice?.let { putBoolean("screenshot_shortcut", it) } ?: remove("screenshot_shortcut")
        }.commit()
        setOverlayMode(when (originalOverlayMode) {
            AppOpsManager.MODE_ALLOWED -> "allow"
            AppOpsManager.MODE_IGNORED -> "ignore"
            AppOpsManager.MODE_ERRORED -> "deny"
            else -> "default"
        })
    }

    @Test fun launchingRequestsNoPermissionsAndOffersBothEntryPoints() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Open PAD overlay")
            awaitText("Choose existing screenshot").assertIsDisplayed()
            assertNotNull("The app must remain visible without a system permission dialog", device.awaitNativeObject(By.pkg(context.packageName).depth(0), timeout))
            assertEquals(context.packageName, device.currentPackageName)
            assertFalse(ScreenCaptureService.active)
            assertFalse(SolutionOverlayService.enabled)
        }
    }

    @Test fun overlayAccessExplanationCanBeDeclinedWithoutStartingOtherPermissionFlows() {
        setOverlayMode("deny")
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Open PAD overlay").performClick()
            awaitText("Show the PAD overlay", scroll = false, clickable = false)
            awaitText("Not now", scroll = false).performClick()
            assertNotNull("The app must remain visible without a system permission dialog", device.awaitNativeObject(By.pkg(context.packageName).depth(0), timeout))
            assertEquals(context.packageName, device.currentPackageName)
            assertFalse(ScreenCaptureService.active)
            assertFalse(SolutionOverlayService.enabled)
        }
    }

    @Test fun openingOverlayRequiresNoScreenSharingAndUsesCompactIconControls() {
        setOverlayMode("allow")
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("Open PAD overlay").performClick()
            assertNotNull(device.awaitNativeObject(By.desc("Capture board"), timeout))
            assertNotNull(device.findObject(By.desc("Open solve settings")))
            assertNotNull(device.findObject(By.desc("Close overlay")))
            assertTrue(SolutionOverlayService.enabled)
            assertFalse(ScreenCaptureService.active)
            assertFalse(device.hasObject(By.text("Capture")))
            assertFalse(device.hasObject(By.text("Screenshots disabled. Tap Capture instead.")))
        }
    }

    @Test fun captureAsksForScreenSharingAndDenialPreservesOverlay() {
        setOverlayMode("allow")
        // The overlay delivers a new intent to this activity, so ActivityScenario's
        // original-intent lifecycle tracking cannot own this particular flow.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            awaitText("Open PAD overlay").performClick()
            checkNotNull(device.awaitNativeObject(By.desc("Capture board"), timeout)).click()
            val cancel = device.awaitNativeObject(By.pkg("com.android.systemui").text(Pattern.compile("(?i)cancel")), timeout)
            assertNotNull("Capture must open Android's sharing consent", cancel)
            assertFalse(ScreenCaptureService.active)
            cancel!!.click()
            assertNotNull(device.awaitNativeObject(By.desc("Capture board"), timeout))
            assertTrue(SolutionOverlayService.enabled)
            assertFalse(ScreenCaptureService.active)
        } finally {
            instrumentation.runOnMainSync {
                for (stage in listOf(Stage.RESUMED, Stage.STARTED, Stage.PAUSED, Stage.STOPPED)) {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                        .filterIsInstance<MainActivity>().toList().forEach { it.finish() }
                }
            }
        }
    }

    @Test fun selectedImageCanBeSolvedAndShownInPadWithoutScreenSharing() {
        setOverlayMode("allow")
        val image = File(context.cacheDir, "permission-flow-board.png")
        instrumentation.context.assets.open("Screenshot_20261004-003953.png").use { input ->
            image.outputStream().use { input.copyTo(it) }
        }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setData(Uri.fromFile(image))).use {
                // Loading also offers settings; wait until recognition has produced the board.
                awaitText("Board captured", scroll = false, clickable = false, timeoutMillis = 60_000)
                awaitText("Solve settings").performClick()
                awaitText("Apply and solve", scroll = false).assertIsDisplayed().performClick()
                // Software rendering and shared CI CPUs need more time for the real solve.
                awaitText("Show route in PAD", timeoutMillis = 60_000).performClick()
                assertNotNull(device.awaitNativeObject(By.desc("Capture board"), timeout))
                assertTrue(SolutionOverlayService.enabled)
                assertFalse(ScreenCaptureService.active)
            }
        } finally { image.delete() }
    }

    private fun awaitText(text: String, scroll: Boolean = true, clickable: Boolean = true, timeoutMillis: Long = timeout): SemanticsNodeInteraction {
        val selector = if (clickable) hasText(text) and hasClickAction() else hasText(text)
        try {
            compose.waitUntil(timeoutMillis = timeoutMillis) {
                compose.onAllNodes(selector).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Missing app UI: $text\n${compose.onRoot().printToString()}", error)
        }
        return compose.onNode(selector).also { if (scroll) it.performScrollTo() }
    }

    private fun setOverlayMode(mode: String) {
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode").use {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
    }
}
