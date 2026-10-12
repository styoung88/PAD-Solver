package com.example.phoneapp.ui

import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.phoneapp.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteArrowRenderer
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PhotoAccess
import com.example.phoneapp.game.GameVariant
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.MatchPattern
import com.example.phoneapp.solver.SolveSettings
import com.example.phoneapp.solver.OrbMovement
import com.example.phoneapp.solver.SearchComplexity
import com.example.phoneapp.solver.PadZeroTeamSkill
import com.example.phoneapp.solver.comboCapacity

@Composable
internal fun PhotoAccessSettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable screenshots") },
        text = {
            val permission = when {
                android.os.Build.VERSION.SDK_INT >= 33 -> "Photos and videos"
                android.os.Build.VERSION.SDK_INT >= 30 -> "Files and media"
                else -> "Storage"
            }
            Text("To detect new screenshots, PAD Solver needs access to all photos. In Settings, tap Permissions, then $permission, and allow access to all photos. You can keep using Capture without changing this.")
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep using Capture") } },
    )
}

@Composable
internal fun OverlayPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Show the PAD overlay") },
        text = { Text("Allow PAD Solver to appear over other apps so it can display the floating toolbar and your route. In Settings, turn on Allow display over other apps for PAD Solver, then return here.") },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
internal fun HomeScreen(
    onCapture: () -> Unit,
    onChooseScreenshot: () -> Unit,
    screenshotShortcutEnabled: Boolean,
    photoAccess: PhotoAccess,
    onScreenshotShortcutChanged: (Boolean) -> Unit,
    onManagePhotoAccess: () -> Unit,
    onOpenSettings: () -> Unit = {},
    game: GameVariant = GameVariant.PAD,
) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val resources = LocalResources.current
        val privacyVisible = remember { mutableStateOf(false) }
        if (privacyVisible.value) AlertDialog(
            onDismissRequest = { privacyVisible.value = false },
            title = { Text("Privacy policy") },
            text = {
                val policy = remember(resources) { resources.openRawResource(com.example.phoneapp.R.raw.privacy_policy).bufferedReader().use { it.readText() } }
                Text(policy, modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { privacyVisible.value = false }) { Text("Close") } },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SolverAppBar(onOpenSettings, enabled = true)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Find your next route.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Solve a board and follow the route in ${game.displayName}.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Solve while you play", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Tap Capture in the floating toolbar, or enable screenshots below to solve screenshots you take while playing.")
                    Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Open ${game.displayName} overlay") }
                }
            }
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Solve a saved board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Pick a screenshot to review and solve. Show the route in ${game.displayName} when you’re ready.")
                    OutlinedButton(onClick = onChooseScreenshot, modifier = Modifier.fillMaxWidth()) { Text("Choose existing screenshot") }
                }
            }
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable screenshots", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(checked = screenshotShortcutEnabled, onCheckedChange = onScreenshotShortcutChanged,
                            modifier = Modifier.semantics { contentDescription = "Enable screenshots" })
                    }
                    Text("Take a screenshot while the overlay is open to solve your board.")
                    if (screenshotShortcutEnabled && photoAccess != PhotoAccess.FULL) {
                        TextButton(onClick = onManagePhotoAccess) { Text("Manage photo access") }
                    }
                }
            }
        }
        TextButton(onClick = { privacyVisible.value = true }, modifier = Modifier.align(Alignment.End)) {
            Text("Privacy policy")
        }
    }
}

@Composable
fun BoardPreviewScreen(
    recognition: BoardRecognition,
    isSolving: Boolean,
    settings: SolveSettings,
    onOpenSettings: () -> Unit,
    onNewBoard: () -> Unit,
) {
    when (recognition) {
        is BoardRecognition.Unsupported -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${recognition.game.displayName} support", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(recognition.game.recognitionUnavailableMessage)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpenSettings) { Text("Change game") }
            OutlinedButton(onClick = onNewBoard) { Text("New board") }
        }

        is BoardRecognition.Uncertain -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Board not clear enough", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Capture a clear board and try again.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNewBoard) { Text("New board") }
        }

        is BoardRecognition.Recognized -> {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SolverAppBar(onOpenSettings, enabled = true)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Board captured", style = MaterialTheme.typography.titleLarge)
                            Text("Check the detected orbs below, then choose your solve settings.")
                            SolveSettingsSummary(settings.preferredColor, settings.targetComboCount, settings.matchPatternFocus,
                                settings.padZeroTeamSkill.takeIf { settings.game == GameVariant.PAD_ZERO })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenSettings, enabled = !isSolving, modifier = Modifier.weight(1f)) {
                            Text("Solve settings")
                        }
                        OutlinedButton(onClick = onNewBoard, enabled = !isSolving) { Text("New board") }
                    }
                    if (isSolving) SolveProgressIndicator("Finding a route…")
                }
                BoardCaption("Review board", "Check the detected ${recognition.board.columns}×${recognition.board.rows} layout")
                CapturedOrbBoard(recognition = recognition)
            }
        }
    }
}

@Composable
fun SolveSettingsDrawer(
    settings: SolveSettings,
    isSolving: Boolean,
    onSettingsChanged: (SolveSettings) -> Unit,
    actionLabel: String,
    onApply: () -> Unit,
    openRequested: Boolean = false,
    onOpenHandled: () -> Unit = {},
    board: Board? = null,
    content: @Composable (openSettings: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(openRequested) {
        if (openRequested) {
            drawerState.open()
            onOpenHandled()
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSolving,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Solve settings", style = MaterialTheme.typography.headlineSmall)
                        Text("Choose how the route is optimized",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ChoiceDropdown(
                            selectedValue = settings.game,
                            choices = GameVariant.entries.map { it to it.displayName },
                            label = "Game",
                            enabled = true,
                            onSelected = { game -> game?.let { onSettingsChanged(settings.copy(game = it)) } },
                        )
                        if (settings.game == GameVariant.PAD_ZERO) {
                            PadZeroTeamSkillSettings(settings, !isSolving, onSettingsChanged)
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Route goals", style = MaterialTheme.typography.titleMedium)
                                ComboTargetDropdown(
                                    selectedTarget = settings.targetComboCount,
                                    maximumTarget = board?.comboCapacity?.coerceAtLeast(1)
                                        ?: if (settings.game == GameVariant.PAD_ZERO) BoardSize.STANDARD.cellCount * 4 / 3
                                        else BoardSize.LARGE.cellCount / 3,
                                    enabled = !isSolving,
                                    onTargetSelected = { onSettingsChanged(settings.copy(targetComboCount = it)) },
                                )
                                if (board != null && settings.targetComboCount?.let { it > board.comboCapacity } == true) {
                                    Text("This board has enough orbs for at most ${board.comboCapacity} combos without skyfall. Choose a lower minimum or capture a new board.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                MatchPatternDropdown(
                                    selectedPattern = settings.matchPatternFocus,
                                    enabled = !isSolving,
                                    onPatternSelected = { onSettingsChanged(settings.copy(matchPatternFocus = it)) },
                                )
                                OrbColorDropdown(
                                    selectedColor = settings.preferredColor,
                                    enabled = !isSolving,
                                    onColorSelected = { onSettingsChanged(settings.copy(preferredColor = it)) },
                                )
                                Text(
                                    if (settings.game == GameVariant.PAD_ZERO && settings.padZeroTeamSkill != PadZeroTeamSkill.NONE)
                                        "Meet your requirements, then maximize relative damage with the team skill. Any color gives attacking colors equal weight."
                                    else "Meet the combo minimum and at least one selected pattern match, then maximize color damage or combos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Advanced settings", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                ChoiceDropdown(
                                    selectedValue = settings.orbMovement,
                                    choices = OrbMovement.entries.map { it to it.displayName },
                                    label = "Orb movement count",
                                    enabled = !isSolving,
                                    onSelected = { value -> value?.let { onSettingsChanged(settings.copy(orbMovement = it)) } },
                                )
                                if (board != null) Text(
                                    "Up to ${settings.toOptions(board).maxOrbMoves} moves.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (settings.orbMovement == OrbMovement.HIGH) {
                                    Text("More orb moves can make the route harder to read and increase solve time.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(Modifier.height(16.dp))
                                ChoiceDropdown(
                                    selectedValue = settings.searchComplexity,
                                    choices = SearchComplexity.entries.map { it to it.displayName },
                                    label = "Search complexity",
                                    enabled = !isSolving,
                                    onSelected = { value -> value?.let { onSettingsChanged(settings.copy(searchComplexity = it)) } },
                                )
                                Text("A more complex search explores more routes.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (settings.searchComplexity == SearchComplexity.HIGH || settings.searchComplexity == SearchComplexity.VERY_HIGH) {
                                    Text("Increasing search complexity can increase solve time. Better results are not guaranteed.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    onApply()
                                }
                            },
                            enabled = !isSolving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(actionLabel)
                        }
                        TextButton(
                            onClick = { scope.launch { drawerState.close() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Close") }
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            content {
                if (!isSolving) scope.launch { drawerState.open() }
            }

        }
    }
}

@Composable
fun SolutionScreen(
    recognition: BoardRecognition.Recognized,
    solution: BoardSolution,
    isSolving: Boolean,
    onOpenSettings: () -> Unit,
    onShowInGame: () -> Unit,
    onNewBoard: () -> Unit,
    game: GameVariant = GameVariant.PAD,
) {
    val unmetRequirements = buildList {
        if (solution.targetComboCount != null && solution.comboMinimumSatisfied != true) {
            add("at least ${solution.targetComboCount} combos")
        }
        if (solution.matchPatternFocus != null && solution.matchPatternSatisfied != true) {
            add("a ${solution.preferredColor?.let { "${orbColorName(it)} " }.orEmpty()}${solution.matchPatternFocus.displayName} match")
        }
        if (solution.damageReductionRequired && solution.teamSkillEvaluation?.damageReductionPercent == 0) {
            add("damage reduction")
        }
    }
    val preferredColorComboCount = solution.preferredColor?.let { color ->
        solution.predictedMatches.allMatches.count { it.color == color }
    }
    var showComboBreakdown by remember(solution) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SolverAppBar(onOpenSettings, enabled = true)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val resultTitle = when {
                        !solution.requirementsPossible -> "No solution for these settings"
                        !solution.goalSatisfied -> "Best attempt: ${solution.comboCount} combos"
                        else -> "${solution.comboCount} combos"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BoxWithConstraints(Modifier.weight(1f)) {
                            val headingStyle = if (maxWidth < 180.dp) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.headlineMedium
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(resultTitle, style = headingStyle,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                                if (solution.requirementsPossible && solution.predictedMatches.bonusComboCount > 0) {
                                    Box {
                                        IconButton(onClick = { showComboBreakdown = true }, modifier = Modifier.size(24.dp)) {
                                            Icon(painterResource(R.drawable.ic_info), contentDescription = "Show combo breakdown",
                                                modifier = Modifier.size(18.dp))
                                        }
                                        if (showComboBreakdown) {
                                            val matches = solution.predictedMatches.physicalComboCount
                                            val bonuses = solution.predictedMatches.bonusComboCount
                                            val tooltipOffset = with(LocalDensity.current) { 32.dp.roundToPx() }
                                            Popup(
                                                alignment = Alignment.TopCenter,
                                                offset = IntOffset(0, tooltipOffset),
                                                onDismissRequest = { showComboBreakdown = false },
                                                properties = PopupProperties(focusable = true),
                                            ) {
                                                Surface(shape = MaterialTheme.shapes.extraSmall,
                                                    color = MaterialTheme.colorScheme.inverseSurface,
                                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                                    shadowElevation = 4.dp) {
                                                    Text("$matches ${if (matches == 1) "match" else "matches"} +$bonuses bonus",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        solution.teamSkillEvaluation?.let { team ->
                            Text(team.attackSummary, style = MaterialTheme.typography.titleMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                    val damageReduction = solution.teamSkillEvaluation?.damageReductionSummary
                    damageReduction?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (solution.requirementsPossible) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RouteStat("${solution.orbMoves}", "Moves",
                                Modifier.weight(1f).wrapContentWidth(Alignment.Start))
                            RouteStat("${solution.predictedMatches.cascadeCount}", "Cascades", Modifier.weight(1f))
                            if (solution.preferredColor != null && preferredColorComboCount != null) {
                                RouteStat("$preferredColorComboCount", orbColorName(solution.preferredColor), Modifier.weight(1f))
                            }
                            solution.matchPatternFocus?.let {
                                RouteStat("${solution.focusedPatternMatchCount ?: 0}", it.displayName, Modifier.weight(1f))
                            }
                        }
                    }
                    SolveSettingsSummary(solution.preferredColor, solution.targetComboCount, solution.matchPatternFocus,
                        solution.teamSkillEvaluation?.skill)
                    solution.teamSkillEvaluation?.let { team ->
                        if (team.bonusAttackDamage > 0) {
                            Text("Bonus attack: ${team.bonusAttackDamage} damage", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val hazardLabels = recognition.board.orbs.mapNotNull { orb -> when (orb.color) {
                        OrbColor.JAMMER -> "J: Jammer"
                        OrbColor.POISON -> "P: Poison"
                        OrbColor.LETHAL_POISON -> "P!: Lethal poison"
                        OrbColor.BOMB -> "B: Bomb"
                        else -> null
                    } }.distinct()
                    if (hazardLabels.isNotEmpty()) {
                        Text(hazardLabels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    if (recognition.board.orbs.any { it.color in listOf(OrbColor.POISON, OrbColor.LETHAL_POISON, OrbColor.BOMB) }) {
                        Text("Poison damage and bomb explosions are not included.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (unmetRequirements.isNotEmpty()) {
                        val message = if (solution.requirementsPossible) {
                            "Could not find a route that meets ${unmetRequirements.joinToString(" and ")}. Showing the best attempt."
                        } else {
                            "The captured board does not have enough orbs or space to meet the selected requirements. Adjust settings to try again."
                        }
                        Text(message,
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (unmetRequirements.isEmpty()) {
                    Button(onClick = onShowInGame, enabled = !isSolving, modifier = Modifier.weight(1f)) {
                        Text("Show route in ${game.displayName}")
                    }
                } else {
                    Button(onClick = onOpenSettings, enabled = !isSolving, modifier = Modifier.weight(1f)) {
                        Text("Adjust settings")
                    }
                }
                OutlinedButton(onClick = onNewBoard, enabled = !isSolving) { Text("New board") }
            }
            if (isSolving) SolveProgressIndicator("Recalculating route…")
            Spacer(Modifier.height(8.dp))
        }
        BoardCaption(
            if (unmetRequirements.isEmpty()) "Suggested route" else "Captured board",
            if (unmetRequirements.isEmpty()) "Start at green and end at red" else "Adjust settings to try another route",
        )
        CapturedOrbBoard(recognition, if (unmetRequirements.isEmpty()) solution.path else emptyList())
    }
}

@Composable
private fun SolverAppBar(onSettings: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("PAD Solver", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Open solve settings" }) {
            Text("⚙", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PadZeroTeamSkillSettings(
    settings: SolveSettings,
    enabled: Boolean,
    onSettingsChanged: (SolveSettings) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Team setup", style = MaterialTheme.typography.titleMedium)
            ChoiceDropdown(
                selectedValue = settings.padZeroTeamSkill,
                choices = PadZeroTeamSkill.entries.map { it to it.displayName },
                label = "Team skill",
                enabled = enabled,
                onSelected = { value -> value?.let {
                    onSettingsChanged(settings.copy(padZeroTeamSkill = it, requireDamageReduction = false))
                } },
            )
            Text(settings.padZeroTeamSkill.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.padZeroTeamSkill != PadZeroTeamSkill.NONE) {
                Text("Move-time bonuses and recovery are not included.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (settings.padZeroTeamSkill.usesTeamAttributeSelection) {
                Text("Team attack attributes", style = MaterialTheme.typography.titleSmall)
                Text("Include your team's main and sub-attributes.", style = MaterialTheme.typography.bodySmall)
                OrbColor.attackingColors.forEach { color ->
                    val selected = color in settings.teamAttackingAttributes
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = selected, enabled = enabled, role = Role.Checkbox,
                        onValueChange = { checked -> onSettingsChanged(settings.copy(
                            teamAttackingAttributes = if (checked) settings.teamAttackingAttributes + color
                                else settings.teamAttackingAttributes - color,
                        )) },
                    ).semantics { contentDescription = "${orbColorName(color)} attack attribute" },
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
                        Text(orbColorName(color))
                    }
                }
                val minimum = settings.padZeroTeamSkill.attributeTiers.first().first
                if (settings.teamAttackingAttributes.size < minimum) {
                    Text("This skill needs at least $minimum attacking attributes on the team to activate its ATK bonus.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (settings.preferredColor != null && settings.preferredColor !in settings.teamAttackingAttributes) {
                    Text("The focused color is not one of this team's attacking attributes. Choose another color focus or update the team attributes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (settings.padZeroTeamSkill.hasIndependentDamageReduction) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Damage reduction", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = settings.requireDamageReduction, enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Damage reduction" },
                        onCheckedChange = { onSettingsChanged(settings.copy(requireDamageReduction = it)) })
                }
                Text("Require a route that activates the team's damage reduction.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RouteStat(value: String, label: String, modifier: Modifier) {
    Column(modifier.testTag("route-stat-$label"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SolveSettingsSummary(color: OrbColor?, target: Int?, pattern: MatchPattern?, teamSkill: PadZeroTeamSkill? = null) {
    val summary = listOfNotNull(
        color?.let { orbColorName(it) } ?: "Any color",
        target?.let { "$it combos" },
        pattern?.displayName,
        teamSkill?.takeIf { it != PadZeroTeamSkill.NONE }?.displayName,
    ).joinToString(" · ")
    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BoardCaption(title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(hint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun CapturedOrbBoard(
    recognition: BoardRecognition.Recognized,
    path: List<BoardPosition> = emptyList(),
) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)
    // Match the working overlay: its window excludes the bottom navigation bar,
    // and screenshot Y coordinates are scaled into that usable window height.
    val overlayHeight = (windowHeight - navigationBarHeight).coerceAtLeast(1)
    val scaleY = overlayHeight.toFloat() / recognition.captureHeight.coerceAtLeast(1)
    val boardHeight = with(density) { (recognition.boardBounds.height() * scaleY).toDp() }
    val bottomGap = with(density) {
        (navigationBarHeight +
            (recognition.captureHeight - recognition.boardBounds.bottom).coerceAtLeast(0f) * scaleY).toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val previewAspectRatio = maxWidth.value / boardHeight.value.coerceAtLeast(1f)
        Column {
            OrbBoard(
                board = recognition.board,
                path = path,
                boardAspectRatio = previewAspectRatio,
            )
            Spacer(Modifier.height(bottomGap))
        }
    }
}
@Composable
private fun SolveProgressIndicator(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(message)
    }
}

@Composable
private fun OrbColorDropdown(
    selectedColor: OrbColor?,
    enabled: Boolean,
    onColorSelected: (OrbColor?) -> Unit,
) {
    ChoiceDropdown(
        selectedValue = selectedColor,
        choices = listOf(null to "Any color") + OrbColor.attackingColors.map { it to orbColorName(it) },
        label = "Optimize color damage for",
        enabled = enabled,
        onSelected = onColorSelected,
    )
}

@Composable
private fun ComboTargetDropdown(
    selectedTarget: Int?,
    maximumTarget: Int,
    enabled: Boolean,
    onTargetSelected: (Int?) -> Unit,
) {
    val targets = ((1..maximumTarget).toList() + listOfNotNull(selectedTarget)).distinct().sorted()
    ChoiceDropdown(
        selectedValue = selectedTarget,
        choices = listOf(null to "No minimum") + targets.map {
            it to if (it > maximumTarget) "$it (exceeds board limit)" else "$it"
        },
        label = "Minimum combos",
        enabled = enabled,
        onSelected = onTargetSelected,
    )
}

@Composable
private fun MatchPatternDropdown(
    selectedPattern: MatchPattern?,
    enabled: Boolean,
    onPatternSelected: (MatchPattern?) -> Unit,
) {
    ChoiceDropdown(
        selectedValue = selectedPattern,
        choices = listOf(null to "No pattern requirement") + MatchPattern.entries.map { it to it.displayName },
        label = "Match pattern",
        enabled = enabled,
        onSelected = onPatternSelected,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> ChoiceDropdown(
    selectedValue: T?,
    choices: List<Pair<T?, String>>,
    label: String,
    enabled: Boolean,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = choices.firstOrNull { it.first == selectedValue }?.second ?: choices.first().second,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.menuAnchor(
                type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = enabled,
            ).fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, choiceLabel) ->
                DropdownMenuItem(
                    text = { Text(choiceLabel) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
@Composable
fun OrbBoard(
    board: Board,
    path: List<BoardPosition> = emptyList(),
    modifier: Modifier = Modifier,
    boardAspectRatio: Float = board.columns.toFloat() / board.rows.toFloat(),
) {
    val preparedRoute = remember(path, board.columns, board.rows) {
        if (path.isEmpty()) null else RouteArrowRenderer.prepareRoute(path, board.columns, board.rows)
    }
    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(boardAspectRatio)
            .background(Color(0xFF202631), MaterialTheme.shapes.medium),
    ) {
        val cellWidth = size.width / board.columns
        val cellHeight = size.height / board.rows
        val native = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        // PAD's round orbs nearly fill each cell, with a narrow gap between neighbors.
        val radius = minOf(cellWidth, cellHeight) * 0.47f

        for (row in 0 until board.rows) {
            for (column in 0 until board.columns) {
                val centerX = (column + 0.5f) * cellWidth
                val centerY = (row + 0.5f) * cellHeight
                val orb = board.orbs[row * board.columns + column]
                val color = orbUiColor(orb.color)
                paint.color = color.toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                native.drawCircle(centerX, centerY, radius, paint)

                val hazardLabel = when (orb.color) {
                    OrbColor.JAMMER -> "J"
                    OrbColor.POISON -> "P"
                    OrbColor.LETHAL_POISON -> "P!"
                    OrbColor.BOMB -> "B"
                    else -> null
                }
                if (hazardLabel != null) {
                    paint.color = android.graphics.Color.WHITE
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    paint.textSize = radius * 0.85f
                    native.drawText(hazardLabel, centerX, centerY + radius * 0.3f, paint)
                }
                if (OrbModifier.COMBO_BONUS in orb.modifiers) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.color = android.graphics.Color.rgb(20, 31, 44)
                    native.drawCircle(centerX + radius * 0.55f, centerY - radius * 0.55f, radius * 0.34f, paint)
                    paint.color = android.graphics.Color.rgb(100, 230, 210)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    paint.textSize = radius * 0.42f
                    native.drawText("+1", centerX + radius * 0.55f, centerY - radius * 0.40f, paint)
                }
                if (OrbModifier.ENHANCED in orb.modifiers || OrbModifier.WEAKENED in orb.modifiers) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.color = android.graphics.Color.rgb(20, 31, 44)
                    native.drawCircle(centerX + radius * 0.55f, centerY + radius * 0.55f, radius * 0.29f, paint)
                    paint.color = if (OrbModifier.WEAKENED in orb.modifiers) android.graphics.Color.rgb(100, 180, 255)
                        else android.graphics.Color.rgb(255, 221, 74)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    paint.textSize = radius * 0.58f
                    native.drawText(if (OrbModifier.WEAKENED in orb.modifiers) "−" else "+",
                        centerX + radius * 0.55f, centerY + radius * 0.75f, paint)
                }
            }
        }

        if (path.isNotEmpty()) {
            RouteArrowRenderer.draw(
                canvas = native,
                path = path,
                bounds = android.graphics.RectF(0f, 0f, size.width, size.height),
                columns = board.columns,
                rows = board.rows,
                preparedRoute = preparedRoute,
            )
        }
    }
}

private fun orbUiColor(color: OrbColor): Color = when (color) {
    OrbColor.FIRE -> Color(0xFFE94B43)
    OrbColor.WATER -> Color(0xFF4DA8E8)
    OrbColor.WOOD -> Color(0xFF55B66A)
    OrbColor.LIGHT -> Color(0xFFF3D447)
    OrbColor.DARK -> Color(0xFF9B6BCC)
    OrbColor.HEART -> Color(0xFFE78EB5)
    OrbColor.JAMMER -> Color(0xFF355A79)
    OrbColor.POISON -> Color(0xFF754290)
    OrbColor.LETHAL_POISON -> Color(0xFF382044)
    OrbColor.BOMB -> Color(0xFF263C3A)
    OrbColor.UNKNOWN -> Color(0xFF777777)
}

private fun orbColorName(color: OrbColor): String = when (color) {
    OrbColor.FIRE -> "Fire"
    OrbColor.WATER -> "Water"
    OrbColor.WOOD -> "Wood"
    OrbColor.LIGHT -> "Light"
    OrbColor.DARK -> "Dark"
    OrbColor.HEART -> "Heart"
    OrbColor.JAMMER -> "Jammer"
    OrbColor.POISON -> "Poison"
    OrbColor.LETHAL_POISON -> "Lethal poison"
    OrbColor.BOMB -> "Bomb"
    OrbColor.UNKNOWN -> "Unknown"
}
