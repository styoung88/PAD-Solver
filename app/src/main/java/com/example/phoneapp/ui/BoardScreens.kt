package com.example.phoneapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteStepMarkerLayout
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.solver.BoardSolution

@Composable
fun HomeScreen(onCapture: () -> Unit, onChooseScreenshot: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PAD Solver", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Capture a standard 6×5 board, review the detected orbs, and find a high-combo route.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCapture) { Text("Capture board") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onChooseScreenshot) { Text("Choose existing screenshot") }
        Spacer(Modifier.height(12.dp))
        Text(
            "Screen capture and notifications are used to capture the board. Draw-over-apps access is only needed to show the route.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BoardPreviewScreen(
    recognition: BoardRecognition,
    isSolving: Boolean,
    preferredColor: OrbColor?,
    onPreferredColorChanged: (OrbColor?) -> Unit,
    onSolve: (OrbColor?) -> Unit,
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

        is BoardRecognition.Recognized -> SolveSettingsDrawer(
            preferredColor = preferredColor,
            isSolving = isSolving,
            onPreferredColorChanged = onPreferredColorChanged,
            actionLabel = if (isSolving) "Solving…" else "Apply and solve",
            onApply = { onSolve(preferredColor) },
        ) { openSettings ->
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
            Text("Review board", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Check the detected 6×5 layout before solving. Editing is not available yet.")
            Spacer(Modifier.height(12.dp))
            OrbBoard(board = recognition.board)
            Spacer(Modifier.height(12.dp))
            SettingsDrawerTrigger(onClick = openSettings, enabled = !isSolving)
            Spacer(Modifier.height(12.dp))
            if (isSolving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(8.dp))
                Text("Finding a route…")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onNewBoard, enabled = !isSolving) { Text("New board") }
            }
        }
    }
}

@Composable
private fun SolveSettingsDrawer(
    preferredColor: OrbColor?,
    isSolving: Boolean,
    onPreferredColorChanged: (OrbColor?) -> Unit,
    actionLabel: String,
    onApply: () -> Unit,
    content: @Composable (openSettings: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                            Text("Color focus", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OrbColorDropdown(
                                selectedColor = preferredColor,
                                enabled = !isSolving,
                                onColorSelected = onPreferredColorChanged,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Estimate uses match size and total combos only. Team and enemy modifiers are not included.",
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
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "More solve preferences can be added here later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            content {
                if (!isSolving) scope.launch { drawerState.open() }
            }
            DrawerEdgeIndicator(
                modifier = Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 2.dp, top = 8.dp),
                onClick = { if (!isSolving) scope.launch { drawerState.open() } },
            )
        }
    }
}

@Composable
private fun DrawerEdgeIndicator(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
    Box(
        modifier = modifier
            .size(width = 20.dp, height = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Open solve settings drawer" },
        contentAlignment = Alignment.Center,
    ) {
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun SolutionScreen(
    board: Board,
    solution: BoardSolution,
    preferredColor: OrbColor?,
    isSolving: Boolean,
    onPreferredColorChanged: (OrbColor?) -> Unit,
    onRecalculate: (OrbColor?) -> Unit,
    onShowInGame: () -> Unit,
    onNewBoard: () -> Unit,
) {
    SolveSettingsDrawer(
        preferredColor = preferredColor,
        isSolving = isSolving,
        onPreferredColorChanged = onPreferredColorChanged,
        actionLabel = if (isSolving) "Recalculating route…" else "Apply and recalculate",
        onApply = { onRecalculate(preferredColor) },
    ) { openSettings ->
        Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Suggested route", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("${solution.comboCount} combos · ${solution.orbMoves} orb moves")
        Spacer(Modifier.height(16.dp))
        OrbBoard(board = board, matches = solution.predictedMatches, path = solution.path)
        Spacer(Modifier.height(12.dp))
        Text("Start at 1, then follow each number along the path.")
        Spacer(Modifier.height(18.dp))
        SettingsDrawerTrigger(onClick = openSettings, enabled = !isSolving)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onShowInGame, enabled = !isSolving) { Text("Show route in PAD") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNewBoard) { Text("New board") }
    }
}
}

@Composable
private fun SettingsDrawerTrigger(onClick: () -> Unit, enabled: Boolean) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
                val strokeWidth = size.height * 0.12f
                listOf(0.2f, 0.5f, 0.8f).forEach { fraction ->
                    val y = size.height * fraction
                    drawLine(
                        color = iconColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text("Solve settings")
            Text("›")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OrbColorDropdown(
    selectedColor: OrbColor?,
    enabled: Boolean,
    onColorSelected: (OrbColor?) -> Unit,
) {
    val colorChoices = listOf(
        null to "Any color",
        OrbColor.FIRE to "Fire",
        OrbColor.WATER to "Water",
        OrbColor.WOOD to "Wood",
        OrbColor.LIGHT to "Light",
        OrbColor.DARK to "Dark",
    )
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = colorChoices.first { it.first == selectedColor }.second,
            onValueChange = {},
            readOnly = true,
            label = { Text("Optimize color damage for") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(
                type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                enabled = enabled,
            ).fillMaxWidth(),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            colorChoices.forEach { (color, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onColorSelected(color)
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
    matches: BoardAnalysis? = null,
    path: List<BoardPosition> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val matchedPositions = matches?.matches?.flatMap { it.positions }?.toSet().orEmpty()
    Canvas(
        modifier = modifier.fillMaxWidth().aspectRatio(board.columns.toFloat() / board.rows.toFloat())
            .background(Color(0xFF202631), MaterialTheme.shapes.medium).padding(4.dp),
    ) {
        val cellWidth = size.width / board.columns
        val cellHeight = size.height / board.rows
        val native = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val radius = minOf(cellWidth, cellHeight) * 0.38f

        for (row in 0 until board.rows) {
            for (column in 0 until board.columns) {
                val centerX = (column + 0.5f) * cellWidth
                val centerY = (row + 0.5f) * cellHeight
                val color = orbUiColor(board.orbs[row * board.columns + column].color)
                paint.color = color.toArgb()
                paint.style = android.graphics.Paint.Style.FILL
                native.drawCircle(centerX, centerY, radius, paint)

                if (BoardPosition(row, column) in matchedPositions) {
                    paint.color = android.graphics.Color.WHITE
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = radius * 0.13f
                    native.drawCircle(centerX, centerY, radius * 0.86f, paint)
                }
            }
        }

        if (path.isNotEmpty()) {
            paint.color = android.graphics.Color.WHITE
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = radius * 0.15f
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeJoin = android.graphics.Paint.Join.ROUND
            for (index in 0 until path.lastIndex) {
                val from = path[index]
                val to = path[index + 1]
                native.drawLine(
                    (from.column + 0.5f) * cellWidth,
                    (from.row + 0.5f) * cellHeight,
                    (to.column + 0.5f) * cellWidth,
                    (to.row + 0.5f) * cellHeight,
                    paint,
                )
            }
            paint.style = android.graphics.Paint.Style.FILL
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            val markers = RouteStepMarkerLayout.place(path)
            val normalTextSize = radius * 0.62f
            markers.forEach { marker ->
                val x = marker.gridX * cellWidth
                val y = marker.gridY * cellHeight
                val markerRadius = if (marker.isStart) radius * 0.31f else radius * 0.29f
                paint.textSize = normalTextSize
                paint.color = android.graphics.Color.rgb(20, 31, 44)
                native.drawCircle(x, y, markerRadius, paint)
                paint.color = android.graphics.Color.WHITE
                native.drawText(marker.stepNumber.toString(), x, y + paint.textSize * 0.34f, paint)
            }
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
