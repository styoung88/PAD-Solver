package com.example.phoneapp.ui

import android.graphics.RectF
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.phoneapp.awaitNativeObject
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardAnalyzer
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.Orb
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.MatchPattern
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.PadZeroTeamSkill
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
    fun previewCaptionTracksTheDetectedBoardSize() {
        val current = mutableStateOf(recognized)
        compose.setContent { MaterialTheme { BoardPreviewScreen(current.value, false, SolveSettings(), {}, {}) } }
        for (size in BoardSize.entries) {
            compose.runOnIdle {
                current.value = recognized.copy(board = Board(size.columns, size.rows,
                    List(size.cellCount) { Orb(OrbColor.WOOD) }))
            }
            compose.onNodeWithText("Check the detected ${size.displayName} layout").assertIsDisplayed()
        }
    }

    @Test
    fun longPadZeroSettingsKeepActionsVisibleWhileScrolling() {
        val settings = mutableStateOf(SolveSettings(
            game = com.example.phoneapp.game.GameVariant.PAD_ZERO,
            padZeroTeamSkill = PadZeroTeamSkill.MULTICOLOR,
        ))
        var applied = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.height(480.dp)) {
                SolveSettingsDrawer(settings.value, false, { settings.value = it },
                    "Apply and solve", { applied++ }, board = recognized.board) { open ->
                    Button(onClick = open) { Text("Open drawer") }
                }
            }
        } }
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("+1 badges add combos when matched.").assertDoesNotExist()
        compose.onNodeWithText("Apply and solve").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Advanced settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Search complexity").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apply and solve").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Team skill").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apply and solve").performClick()
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test
    fun smallBoardKeepsSavedTargetAndExplainsItsLimit() {
        val settings = mutableStateOf(SolveSettings(targetComboCount = 10))
        val small = Board(5, 4, List(20) { Orb(OrbColor.FIRE) })
        compose.setContent {
            MaterialTheme {
                SolveSettingsDrawer(settings.value, false, { settings.value = it }, "Apply and solve", {}, board = small) { open ->
                    Button(onClick = open) { Text("Open drawer") }
                }
            }
        }
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("10 (exceeds board limit)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This board has enough orbs for at most 6 combos without skyfall. Choose a lower minimum or capture a new board.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Minimum combos").performScrollTo().performClick()
        compose.onNode(hasText("7") and hasAnyAncestor(isPopup())).assertDoesNotExist()
        compose.onNode(hasText("6") and hasAnyAncestor(isPopup())).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(6, settings.value.targetComboCount) }
    }

    @Test
    fun comboBonusTargetsCanExceedThePhysicalMatchLimit() {
        val settings = mutableStateOf(SolveSettings(game = com.example.phoneapp.game.GameVariant.PAD_ZERO))
        val board = Board(3, 1, List(3) { Orb(OrbColor.FIRE, setOf(com.example.phoneapp.board.OrbModifier.COMBO_BONUS)) })
        compose.setContent {
            MaterialTheme {
                SolveSettingsDrawer(settings.value, false, { settings.value = it }, "Apply and solve", {}, board = board) { open ->
                    Button(onClick = open) { Text("Open drawer") }
                }
            }
        }
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Minimum combos").performScrollTo().performClick()
        compose.onNode(hasText("4") and hasAnyAncestor(isPopup())).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(4, settings.value.targetComboCount) }
        compose.onNodeWithText("4 (exceeds board limit)").assertDoesNotExist()
    }

    @Test
    fun bonusResultsExplainEffectiveTotalAndPhysicalMatches() {
        val board = Board(3, 1, List(3) { Orb(OrbColor.FIRE, setOf(com.example.phoneapp.board.OrbModifier.COMBO_BONUS)) })
        val analysis = BoardAnalyzer.resolveCascades(board)
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), analysis, true)
        compose.setContent {
            MaterialTheme {
                SolutionScreen(recognized.copy(board = board), solution, false, {}, {}, {},
                    game = com.example.phoneapp.game.GameVariant.PAD_ZERO)
            }
        }
        compose.onNodeWithText("4 combos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 match +3 bonus").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show combo breakdown").performClick()
        compose.onNodeWithText("1 match +3 bonus").assertIsDisplayed()
        compose.onNodeWithText("Combo breakdown").assertDoesNotExist()
        compose.onNodeWithText("Close").assertDoesNotExist()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        checkNotNull(device.awaitNativeObject(By.text("1 match +3 bonus"), 10_000))
        device.pressBack()
        compose.onNodeWithText("1 match +3 bonus").assertDoesNotExist()
        compose.onNodeWithText("Show route in PAD 0").performScrollTo().assertIsDisplayed()
    }

    @Test fun teamSkillResultsShowAttackReductionAndFixedBonusSeparately() {
        val analysis = BoardAnalysis(OrbColor.attackingColors.map { color ->
            com.example.phoneapp.board.OrbMatch(color, (0..2).map { BoardPosition(0, it) }.toSet())
        })
        val team = PadZeroTeamSkill.FIVE_COLOR_ERASURE.evaluate(analysis, OrbColor.attackingColors.toSet())
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), analysis, true, teamSkillEvaluation = team)
        compose.setContent { MaterialTheme {
            SolutionScreen(recognized, solution, false, {}, {}, {}, game = com.example.phoneapp.game.GameVariant.PAD_ZERO)
        } }
        compose.onNodeWithText("5 combos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show combo breakdown").assertDoesNotExist()
        compose.onNodeWithText("Team ATK ×25").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Damage reduction: 50%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bonus attack: 10 damage").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("15 combos").assertDoesNotExist()
    }

    @Test fun unmetDamageReductionHidesRouteEvenWhenAttackIsActive() {
        val analysis = BoardAnalysis(OrbColor.attackingColors.map { color ->
            com.example.phoneapp.board.OrbMatch(color, (0..2).map { BoardPosition(0, it) }.toSet())
        })
        val team = PadZeroTeamSkill.EXPERT_MULTICOLOR.evaluate(analysis, OrbColor.attackingColors.toSet())
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), analysis, false,
            teamSkillEvaluation = team, damageReductionRequired = true)
        compose.setContent { MaterialTheme {
            SolutionScreen(recognized, solution, false, {}, {}, {}, game = com.example.phoneapp.game.GameVariant.PAD_ZERO)
        } }
        compose.onNodeWithText("Team ATK ×10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Damage reduction: inactive").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Could not find a route that meets damage reduction. Showing the best attempt.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show route in PAD 0").assertDoesNotExist()
        compose.onNodeWithText("Adjust settings").performScrollTo().assertIsDisplayed()
    }

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
        compose.onNodeWithText("Best attempt: 0 combos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Could not find a route that meets at least 5 combos and a Wood TPA match. Showing the best attempt.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Adjust settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, settingsOpened) }
    }

    @Test
    fun provenImpossibleRequirementsExplainTheBoardLimitAndOfferAdjustment() {
        val board = Board(orbs = ("DDDFFF" + "FFDFFF" + "DFFFDD" + "DFFFFD" + "FDFFFD")
            .map { Orb(if (it == 'D') OrbColor.DARK else OrbColor.FIRE) })
        val analysis = BoardAnalyzer.resolveCascades(board)
        assertEquals(3, analysis.comboCount)
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), analysis,
            goalSatisfied = false, preferredColor = OrbColor.LIGHT,
            targetComboCount = 7, comboMinimumSatisfied = false,
            matchPatternFocus = MatchPattern.L_SHAPE, focusedPatternMatchCount = 0, matchPatternSatisfied = false,
            requirementsPossible = false)
        compose.setContent { MaterialTheme { SolutionScreen(recognized.copy(board = board), solution, false, {}, {}, {}) } }
        compose.onNodeWithText("No solution for these settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 combos").assertDoesNotExist()
        compose.onNodeWithText("Moves").assertDoesNotExist()
        compose.onNodeWithText("Cascades").assertDoesNotExist()
        compose.onNodeWithText("Light").assertDoesNotExist()
        compose.onNodeWithText("L shape").assertDoesNotExist()
        compose.onNodeWithText("The captured board does not have enough orbs or space to meet the selected requirements. Adjust settings to try again.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show route in PAD").assertDoesNotExist()
        compose.onNodeWithText("Adjust settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun busySolutionKeepsGameSettingsAvailableButDisablesRouteAndNewBoard() {
        val solution = BoardSolution(listOf(BoardPosition(0, 0)), BoardAnalysis(emptyList()), goalSatisfied = true)
        compose.setContent { MaterialTheme { SolutionScreen(recognized, solution, true, {}, {}, {}) } }
        compose.onNodeWithText("Show route in PAD").assertIsNotEnabled()
        compose.onNodeWithText("New board").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Open solve settings").assertIsEnabled()
        compose.onNodeWithText("Recalculating route…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun busyBoardKeepsGameSettingsAvailableAndShowsProgress() {
        compose.setContent { MaterialTheme { BoardPreviewScreen(recognized, true, SolveSettings(), {}, {}) } }
        compose.onNode(hasText("Solve settings") and !hasContentDescription("Open solve settings")).assertIsNotEnabled()
        compose.onNodeWithText("New board").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Open solve settings").assertIsEnabled()
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

    @Test
    fun settingsActionsRemainReachableInAShortWindow() {
        var applies = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.height(420.dp)) {
                    SolveSettingsDrawer(SolveSettings(), false, {}, "Apply and solve", { applies++ }) { open ->
                        Button(onClick = open) { Text("Open drawer") }
                    }
                }
            }
        }
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Board size").assertDoesNotExist()
        compose.onNodeWithText("Path style").assertDoesNotExist()
        compose.onNodeWithText("Route appearance").assertDoesNotExist()
        compose.onNodeWithText("Close").assertIsDisplayed()
        compose.onNodeWithText("Apply and solve").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, applies) }
    }
}
