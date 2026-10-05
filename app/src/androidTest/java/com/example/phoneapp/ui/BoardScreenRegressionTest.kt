package com.example.phoneapp.ui

import android.graphics.RectF
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.MatchPattern
import com.example.phoneapp.solver.SolveSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class BoardScreenRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepScreenAwake() {
        compose.runOnUiThread { compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private val recognized = BoardRecognition.Recognized(
        board = Board(orbs = List(30) { Orb(OrbColor.WOOD) }),
        boardBounds = RectF(0f, 300f, 600f, 800f), captureWidth = 600, captureHeight = 1000,
    )

    @Test
    fun unmetRequirementsOfferAdjustmentAndHideRouteAction() {
        var settingsOpened = 0
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), BoardAnalysis(emptyList()),
            goalSatisfied = false, preferredColor = OrbColor.WOOD,
            targetComboCount = 5, comboMinimumSatisfied = false,
            matchPatternFocus = MatchPattern.TPA, focusedPatternMatchCount = 0, matchPatternSatisfied = false)
        compose.setContent {
            MaterialTheme {
                SolutionScreen(recognized, solution, false, { settingsOpened++ },
                    { error("An unmet goal must never offer a route") }, {})
            }
        }
        compose.onNodeWithText("Show route in PAD").assertDoesNotExist()
        compose.onNodeWithText("Could not find a route that meets at least 5 combos and a Wood TPA match. Showing the best attempt.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Adjust settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settingsOpened) }
    }

    @Test
    fun busySolutionDisablesRouteSettingsAndNewBoard() {
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), BoardAnalysis(emptyList()), goalSatisfied = true)
        compose.setContent { MaterialTheme { SolutionScreen(recognized, solution, true, {}, {}, {}) } }
        compose.onNodeWithText("Show route in PAD").assertIsNotEnabled()
        compose.onNodeWithText("New board").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Open solve settings").assertIsNotEnabled()
        compose.onNodeWithText("Recalculating route…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun busyBoardDisablesActionsAndShowsProgress() {
        compose.setContent { MaterialTheme { BoardPreviewScreen(recognized, true, SolveSettings(), {}, {}) } }
        compose.onNodeWithText("Solve settings").assertIsNotEnabled()
        compose.onNodeWithText("New board").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Open solve settings").assertIsNotEnabled()
        compose.onNodeWithText("Finding a route…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun uncertainBoardOffersNewCaptureAndNoSolveOrRoute() {
        var resets = 0
        compose.setContent {
            MaterialTheme { BoardPreviewScreen(BoardRecognition.Uncertain(0), false, SolveSettings(), {}, { resets++ }) }
        }
        compose.onNodeWithText("Board not clear enough").assertIsDisplayed()
        compose.onNodeWithText("Solve settings").assertDoesNotExist()
        compose.onNodeWithText("Show route in PAD").assertDoesNotExist()
        compose.onNodeWithText("New board").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }
}
