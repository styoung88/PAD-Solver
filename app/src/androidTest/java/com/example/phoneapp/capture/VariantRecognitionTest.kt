package com.example.phoneapp.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneapp.board.BoardSize
import com.example.phoneapp.board.OrbColor
import com.example.phoneapp.board.OrbModifier
import com.example.phoneapp.game.GameVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VariantRecognitionTest {
    @Test
    fun bothGamesRecognizeOrdinaryColorsAcrossDifferentInteriorPatterns() {
        val colors = listOf(OrbColor.FIRE to Color.RED, OrbColor.WATER to Color.BLUE,
            OrbColor.WOOD to Color.GREEN, OrbColor.LIGHT to Color.YELLOW,
            OrbColor.DARK to Color.MAGENTA, OrbColor.HEART to Color.rgb(255, 0, 128))
        for (recognizer in listOf(PadOrbRecognizer(), PadZeroOrbRecognizer())) {
            for (size in listOf(60, 100, 180)) for (pattern in 0 until 3) for ((color, pixel) in colors) {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    val center = size / 2f
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    canvas.drawColor(Color.BLACK)
                    paint.color = pixel
                    canvas.drawCircle(center, center, size * 0.44f, paint)
                    paint.color = Color.GRAY
                    when (pattern) {
                        0 -> canvas.drawCircle(center, center, size * 0.12f, paint)
                        1 -> {
                            canvas.drawRect(center - size * 0.05f, center - size * 0.18f,
                                center + size * 0.05f, center + size * 0.18f, paint)
                            canvas.drawRect(center - size * 0.18f, center - size * 0.05f,
                                center + size * 0.18f, center + size * 0.05f, paint)
                        }
                        2 -> for (dx in listOf(-0.12f, 0.12f)) for (dy in listOf(-0.12f, 0.12f)) {
                            canvas.drawCircle(center + size * dx, center + size * dy, size * 0.055f, paint)
                        }
                    }
                    val image = recognizer.prepareImage(bitmap)
                    val sample = recognizer.sampleOrbColor(image, center, center, size.toFloat())
                    val context = "${recognizer.javaClass.simpleName}: $color size=$size pattern=$pattern"
                    assertEquals(context, color, sample.color)
                    assertEquals("refined $context", color,
                        recognizer.refineColor(image, center, center, size.toFloat(), sample.color))
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test
    fun sharedFlowUsesChildLayoutPaletteAndModifiers() {
        // A deliberately different test style: inset 5x4 board, blue hearts and green wood.
        // Its independently drawn positions and labels exercise the extension points.
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            for (index in 0 until 20) {
                paint.color = if (index % 2 == 0) Color.BLUE else Color.GREEN
                val x = 60f + (index % 5 + 0.5f) * 96f
                val y = 200f + (index / 5 + 0.5f) * 96f
                canvas.drawOval(x - 45f, y - 45f, x + 45f, y + 45f, paint)
            }
            val result = TestBoardRecognizer().recognize(bitmap)
            assertTrue("$result", result is BoardRecognition.Recognized)
            result as BoardRecognition.Recognized
            assertEquals(5, result.board.columns)
            assertEquals(4, result.board.rows)
            val expected = RectF(60f, 200f, 540f, 584f)
            assertEquals(expected.left, result.boardBounds.left, 4f)
            assertEquals(expected.top, result.boardBounds.top, 4f)
            assertEquals(expected.right, result.boardBounds.right, 4f)
            assertEquals(expected.bottom, result.boardBounds.bottom, 4f)
            result.board.orbs.forEachIndexed { index, orb ->
                assertEquals(if (index % 2 == 0) OrbColor.HEART else OrbColor.WOOD, orb.color)
                assertEquals(if (index % 2 == 0) setOf(OrbModifier.COMBO_BONUS) else emptySet<OrbModifier>(),
                    orb.modifiers)
            }
        } finally { bitmap.recycle() }
    }

    private class TestBoardRecognizer : BaseBoardRecognizer(
        game = GameVariant.PAD_ZERO,
        geometry = BoardGeometry(
            sizes = listOf(BoardSize.SMALL),
            minimumCaptureWidth = 200,
            minimumCaptureHeight = 300,
            minimumAspectRatio = 1f,
            leftFraction = 0.1f,
            widthFraction = 0.8f,
            minimumCenterYMargin = 0f,
            bottomSearchOvershootCells = 0f,
            centerYStep = 0.006f,
            preferredCenterY = 392f / 900f,
            minimumAverageColorSupport = 0.30f,
            minimumGridGapSupport = 0.15f,
            minimumSizeScoreMargin = 1f,
            recognitionWeight = 30f,
            varietyBonus = 0.45f,
            colorSupportWeight = 18f,
            centerPenalty = 4f,
            gridGapWeight = 12f,
            minimumFrameInsetFraction = 0.015f,
        ),
        orbRecognizer = TestOrbRecognizer(),
    )

    private class TestOrbRecognizer : BaseOrbRecognizer(
        sampling = OrbSamplingParameters(
            patchRadiusFraction = 0.025f,
            stepFraction = 0.01f,
            patchOffsets = listOf(-0.2f to 0f, 0.2f to 0f, 0f to -0.2f, 0f to 0.2f),
            patchVoteFraction = 0.55f,
            minimumColorVoteFraction = 0.38f,
            alignmentScales = floatArrayOf(1f),
            alignmentOffsets = floatArrayOf(0f),
            minimumRecoverySupport = 0.35f,
            minimumRecoverySamples = 1,
            minimumRecoveryVoteRatio = 2,
        ),
        hazards = null,
    ) {
        override fun classifyColor(hsv: FloatArray): OrbColor? = when {
            hsv[1] < 0.5f || hsv[2] < 0.5f -> null
            hsv[0] in 220f..260f -> OrbColor.HEART
            hsv[0] in 100f..140f -> OrbColor.WOOD
            else -> null
        }

        override fun detectModifiers(
            bitmap: Bitmap, centerX: Float, centerY: Float, cellSize: Float, color: OrbColor,
            image: ClassifiedImage?,
        ): Set<OrbModifier> = if (color == OrbColor.HEART) setOf(OrbModifier.COMBO_BONUS) else emptySet()
    }
}
