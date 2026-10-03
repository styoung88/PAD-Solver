package com.example.phoneapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.phoneapp.board.Board
import com.example.phoneapp.board.BoardAnalysis
import com.example.phoneapp.board.BoardPosition
import com.example.phoneapp.board.RouteStepMarkerLayout
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.capture.BoardRecognition
import com.example.phoneapp.solver.BoardSolution

@Composable
fun HomeScreen(onCapture: () -> Unit) {
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
    onSolve: () -> Unit,
    onRetry: () -> Unit,
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
            Button(onClick = onRetry) { Text("Capture again") }
        }

        is BoardRecognition.Recognized -> Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Review board", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Check the detected 6×5 layout before solving. Editing is not available yet.")
            Spacer(Modifier.height(12.dp))
            OrbColorDropdown(
                selectedColor = preferredColor,
                enabled = !isSolving,
                onColorSelected = onPreferredColorChanged,
            )
            Text(
                "Estimate uses match size and total combos only; team and enemy modifiers are not included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OrbBoard(board = recognition.board)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onSolve, enabled = !isSolving) {
                Text(if (isSolving) "Finding a route…" else "Solve board")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry, enabled = !isSolving) { Text("Capture again") }
        }
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
    onHideRoute: () -> Unit,
    onNewBoard: () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Suggested route", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("${solution.comboCount} combos · ${solution.orbMoves} orb moves")
        solution.preferredColor?.let { color ->
            val colorMatches = solution.predictedMatches.matches.count { it.color == color }
            Text("${orbColorName(color)} focus · $colorMatches matched combos")
        }
        Spacer(Modifier.height(16.dp))
        OrbBoard(board = board, matches = solution.predictedMatches, path = solution.path)
        Spacer(Modifier.height(12.dp))
        Text("Start at 1, then follow each number along the path.")
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = { optionsExpanded = !optionsExpanded }) {
            Text(if (optionsExpanded) "Hide solve options" else "Solve options")
        }
        if (optionsExpanded) {
            Spacer(Modifier.height(8.dp))
            Text("Optimize color damage for", style = MaterialTheme.typography.labelLarge)
            OrbColorDropdown(
                selectedColor = preferredColor,
                enabled = !isSolving,
                onColorSelected = onPreferredColorChanged,
            )
            Text(
                "Estimate uses match size and total combos only; team and enemy modifiers are not included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onRecalculate(preferredColor) },
                enabled = !isSolving,
            ) {
                Text(if (isSolving) "Recalculating route…" else "Recalculate route")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onShowInGame, enabled = !isSolving) { Text("Show route in PAD") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHideRoute) { Text("Hide route overlay") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onNewBoard) { Text("New board") }
    }
}

@Composable
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
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
            Text(colorChoices.first { it.first == selectedColor }.second)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
