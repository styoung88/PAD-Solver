package com.example.phoneapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteArrowRenderer
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.capture.PhotoAccess
import com.example.phoneapp.solver.BoardSolution
import com.example.phoneapp.solver.MatchPattern
import com.example.phoneapp.solver.SolveSettings

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
) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SolverAppBar(onOpenSettings, enabled = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Find your next route.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Solve a 6×5 board and follow the route in PAD.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Solve while you play", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Tap Capture in the floating toolbar, or enable screenshots below to solve screenshots you take while playing.")
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Open PAD overlay") }
                Text("Screen sharing is requested only when you tap Capture.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Solve a saved board", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pick a screenshot to review and solve. Show the route in PAD when you’re ready.")
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
                Text("Solve new screenshots while the overlay is open. Needs full photo access.",
                    style = MaterialTheme.typography.bodyMedium)
                Text(when {
                    !screenshotShortcutEnabled -> "Use Capture in the overlay, or choose a saved screenshot."
                    photoAccess == PhotoAccess.FULL -> "Take a screenshot while the overlay is open to solve your board."
                    photoAccess == PhotoAccess.SELECTED -> "To detect new screenshots, PAD Solver needs access to all photos. You’ve allowed selected photos only. You can still tap Capture."
                    else -> "Allow access to all photos so PAD Solver can detect new screenshots. Capture works without photo access."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (screenshotShortcutEnabled && photoAccess != PhotoAccess.FULL) {
                    TextButton(onClick = onManagePhotoAccess) { Text("Manage photo access") }
                }
            }
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
        is BoardRecognition.Uncertain -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Board not clear enough", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Move to a normal board and capture again. No board changes were made.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNewBoard) { Text("New board") }
        }

        is BoardRecognition.Recognized -> {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SolverAppBar(onOpenSettings, !isSolving)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Board captured", style = MaterialTheme.typography.titleLarge)
                            Text("Check the detected orbs below, then choose your solve settings.")
                            SolveSettingsSummary(settings.preferredColor, settings.targetComboCount, settings.matchPatternFocus)
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
                BoardCaption("Review board", "Check the detected 6×5 layout")
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
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text("Solve settings", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Choose how the route is optimized. Your captured board stays available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Combo target", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            ComboTargetDropdown(
                                selectedTarget = settings.targetComboCount,
                                enabled = !isSolving,
                                onTargetSelected = { onSettingsChanged(settings.copy(targetComboCount = it)) },
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Required match pattern", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            MatchPatternDropdown(
                                selectedPattern = settings.matchPatternFocus,
                                enabled = !isSolving,
                                onPatternSelected = { onSettingsChanged(settings.copy(matchPatternFocus = it)) },
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Color focus", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OrbColorDropdown(
                                selectedColor = settings.preferredColor,
                                enabled = !isSolving,
                                onColorSelected = { onSettingsChanged(settings.copy(preferredColor = it)) },
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Selected combo and pattern requirements must both be met. Color damage ranks routes that meet them. " +
                                    "The estimate includes match size, combos, and + orbs; team and enemy modifiers are excluded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { scope.launch { drawerState.close() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Close") }
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
) {
    val unmetRequirements = buildList {
        if (solution.targetComboCount != null && solution.comboMinimumSatisfied != true) {
            add("at least ${solution.targetComboCount} combos")
        }
        if (solution.matchPatternFocus != null && solution.matchPatternSatisfied != true) {
            add("a ${solution.preferredColor?.let { "${orbColorName(it)} " }.orEmpty()}${solution.matchPatternFocus.displayName} match")
        }
    }
    val preferredColorComboCount = solution.preferredColor?.let { color ->
        solution.predictedMatches.allMatches.count { it.color == color }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SolverAppBar(onOpenSettings, !isSolving)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${solution.comboCount} combos", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RouteStat("${solution.orbMoves}", "Orb moves", Modifier.weight(1f))
                        RouteStat("${solution.predictedMatches.cascadeCount}", "Cascades", Modifier.weight(1f))
                        if (solution.preferredColor != null && preferredColorComboCount != null) {
                            RouteStat("$preferredColorComboCount", "${orbColorName(solution.preferredColor)} combos", Modifier.weight(1f))
                        }
                    }
                    solution.matchPatternFocus?.let {
                        Text("${solution.focusedPatternMatchCount ?: 0} ${it.displayName} matches",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    SolveSettingsSummary(solution.preferredColor, solution.targetComboCount, solution.matchPatternFocus)
                    if (unmetRequirements.isNotEmpty()) {
                        Text("Could not find a route that meets ${unmetRequirements.joinToString(" and ")}. Showing the best attempt.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (unmetRequirements.isEmpty()) {
                    Button(onClick = onShowInGame, enabled = !isSolving, modifier = Modifier.weight(1f)) {
                        Text("Show route in PAD")
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
            if (unmetRequirements.isEmpty()) "Start at green, end at red" else "Adjust settings to try another route",
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
private fun RouteStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SolveSettingsSummary(color: OrbColor?, target: Int?, pattern: MatchPattern?) {
    val summary = listOfNotNull(
        color?.let { "${orbColorName(it)} focus" } ?: "Any color",
        target?.let { "Minimum $it combos" },
        pattern?.displayName,
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
    enabled: Boolean,
    onTargetSelected: (Int?) -> Unit,
) {
    val targets = listOfNotNull(5, 7, 10, selectedTarget).distinct().sorted()
    ChoiceDropdown(
        selectedValue = selectedTarget,
        choices = listOf(null to "No minimum") + targets.map { it to "$it combos" },
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
                val color = orbUiColor(board.orbs[row * board.columns + column].color)
                paint.color = color.toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                native.drawCircle(centerX, centerY, radius, paint)

                if (OrbModifier.ENHANCED in board.orbs[row * board.columns + column].modifiers) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.color = android.graphics.Color.rgb(20, 31, 44)
                    native.drawCircle(centerX + radius * 0.55f, centerY + radius * 0.55f, radius * 0.29f, paint)
                    paint.color = android.graphics.Color.rgb(255, 221, 74)
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    paint.textSize = radius * 0.58f
                    native.drawText("+", centerX + radius * 0.55f, centerY + radius * 0.75f, paint)
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
                density = density,
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
    OrbColor.UNKNOWN -> Color(0xFF777777)
}

private fun orbColorName(color: OrbColor): String = when (color) {
    OrbColor.FIRE -> "Fire"
    OrbColor.WATER -> "Water"
    OrbColor.WOOD -> "Wood"
    OrbColor.LIGHT -> "Light"
    OrbColor.DARK -> "Dark"
    OrbColor.HEART -> "Heart"
    OrbColor.UNKNOWN -> "Unknown"
}
