package com.example.phoneapp.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.phoneapp.solver.OrbMovement
import com.example.phoneapp.solver.SearchComplexity
import com.example.phoneapp.solver.SolveSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchSettingsDrawerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectionsUpdateIndependentlyAndWarningsFollowHighLevels() {
        val settings = mutableStateOf(SolveSettings())
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.setContent {
            MaterialTheme {
                SolveSettingsDrawer(settings.value, false, { settings.value = it }, "Apply and solve", {}) { open ->
                    Button(onClick = open) { Text("Open drawer") }
                }
            }
        }
        val movementWarning = "More orb moves can make the route harder to read and increase solve time."
        val complexityWarning = "Increasing search complexity can increase solve time. Better results are not guaranteed."
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText(movementWarning).assertDoesNotExist()
        compose.onNodeWithText(complexityWarning).assertDoesNotExist()
        compose.onNodeWithText("Orb movement").performScrollTo().performClick()
        compose.onNode(hasText("High") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText(movementWarning).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(OrbMovement.HIGH, settings.value.orbMovement)
            assertEquals(SearchComplexity.MEDIUM, settings.value.searchComplexity)
        }
        for ((label, level) in listOf("High" to SearchComplexity.HIGH, "Very high" to SearchComplexity.VERY_HIGH)) {
            compose.onNodeWithText("Search complexity").performScrollTo().performClick()
            compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
            compose.onNodeWithText(complexityWarning).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertEquals(level, settings.value.searchComplexity) }
        }
        compose.onNodeWithText("Orb movement").performScrollTo().performClick()
        compose.onNode(hasText("Low") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText(movementWarning).assertDoesNotExist()
        compose.onNodeWithText("Search complexity").performScrollTo().performClick()
        compose.onNode(hasText("Medium") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText(complexityWarning).assertDoesNotExist()
    }
}
