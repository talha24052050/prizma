package com.prizma.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prizma.core.DX
import com.prizma.core.DY
import com.prizma.core.Kind
import com.prizma.core.Level
import com.prizma.core.ONEWAY
import com.prizma.core.Trace
import kotlin.math.sin

/** Milliseconds on the frame clock, updated every frame. Everything animated on a board reads this. */
@Composable
fun rememberFrameMillis(): State<Long> = produceState(0L) {
    while (true) withFrameMillis { value = it }
}

/**
 * Draws a board as large as fits, up to [maxCell] per cell.
 *
 * @param letters letters for red, green and blue, drawn on coloured pieces when not null
 * @param onTap called with the cell index as soon as a finger lands on the board
 */
@Composable
fun Board(
    level: Level,
    trace: Trace,
    clock: State<Long>,
    modifier: Modifier = Modifier,
    turns: Map<Int, Turn> = emptyMap(),
    hint: Int = -1,
    hintAt: Long = 0,
    sparks: MutableList<Spark>? = null,
    letters: List<String>? = null,
    maxCell: Dp = 88.dp,
    onTap: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTap)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = minOf(maxWidth / level.w, maxHeight / level.h, maxCell)
        Canvas(
            Modifier
                .size(cell * level.w, cell * level.h)
                .testTag(if (onTap != null) "board" else "demo")
                .pointerInput(level) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val s = size.width / level.w.toFloat()
                        val x = (down.position.x / s).toInt()
                        val y = (down.position.y / s).toInt()
                        if (x in 0 until level.w && y in 0 until level.h) tap?.invoke(y * level.w + x)
                    }
                },
        ) {
            // Reading the clock here redraws the canvas every frame without recomposing anything.
            drawBoard(level, trace, clock.value, turns, hint, hintAt, sparks, letters, measurer)
        }
    }
}

private fun lettersOf(mask: Int, letters: List<String>): String =
    buildString { for (b in 0..2) if (mask and (1 shl b) != 0) append(letters[b]) }

private val BEAM_PASSES = arrayOf(0.30f to 0.12f, 0.14f to 0.20f, 0.06f to 0.68f) // width to alpha; alphas sum to 1

private fun DrawScope.drawBoard(
    lv: Level, tr: Trace, now: Long, turns: Map<Int, Turn>, hint: Int, hintAt: Long,
    sparks: MutableList<Spark>?, letters: List<String>?, measurer: TextMeasurer,
) {
    val w = lv.w
    val s = size.width / w
    fun center(i: Int) = Offset((i % w + 0.5f) * s, (i / w + 0.5f) * s)

    val gap = 1.5.dp.toPx()
    for (i in 0 until lv.size) {
        drawRoundRect(
            Palette.Cell,
            topLeft = Offset(i % w * s + gap, i / w * s + gap),
            size = Size(s - 2 * gap, s - 2 * gap),
            cornerRadius = CornerRadius(s * 0.12f),
        )
    }

    // Beams. Additive blending makes overlapping beams show their mixed colour.
    val period = s * 0.5f
    val flow = PathEffect.dashPathEffect(
        floatArrayOf(s * 0.06f, s * 0.44f),
        period - (now % 100_000) / 1000f * s * 1.5f % period,
    )
    for (i in 0 until lv.size) {
        for (d in 0..3) {
            val mask = tr.out[i * 4 + d]
            if (mask == 0) continue
            val nx = i % w + DX[d]
            val ny = i / w + DY[d]
            val len = if (nx < 0 || ny < 0 || nx >= w || ny >= lv.h) 0.5f else {
                val j = ny * w + nx
                when (lv.kind[j]) {
                    Kind.WALL, Kind.EMIT -> 0.5f
                    Kind.ONEWAY -> if (ONEWAY[lv.state[j]][d] < 0) 0.82f else 1f
                    else -> 1f
                }
            }
            val from = center(i)
            val to = Offset(from.x + DX[d] * len * s, from.y + DY[d] * len * s)
            val color = hueColor(mask)
            for ((width, alpha) in BEAM_PASSES) {
                drawLine(color, from, to, strokeWidth = s * width, cap = StrokeCap.Round, alpha = alpha, blendMode = BlendMode.Plus)
            }
            drawLine(Color.White, from, to, strokeWidth = s * 0.025f, cap = StrokeCap.Round, pathEffect = flow, alpha = 0.3f, blendMode = BlendMode.Plus)
        }
    }

    for (i in 0 until lv.size) {
        val kind = lv.kind[i]
        if (kind == Kind.EMPTY) continue
        val c = center(i)
        translate(c.x, c.y) {
            when (kind) {
                Kind.WALL -> {
                    val box = Size(s * 0.84f, s * 0.84f)
                    val at = Offset(-s * 0.42f, -s * 0.42f)
                    drawRoundRect(Palette.Wall, at, box, CornerRadius(s * 0.1f))
                    drawRoundRect(Palette.WallEdge, at, box, CornerRadius(s * 0.1f), style = Stroke(1.5.dp.toPx()))
                }
                Kind.EMIT -> {
                    val color = hueColor(lv.hue[i])
                    val box = Size(s * 0.68f, s * 0.68f)
                    val at = Offset(-s * 0.34f, -s * 0.34f)
                    drawRoundRect(Color(0xFF171D36), at, box, CornerRadius(s * 0.14f))
                    drawRoundRect(color, at, box, CornerRadius(s * 0.14f), style = Stroke(s * 0.05f))
                    rotate(lv.arg[i] * 90f, Offset.Zero) {
                        val arrow = Path().apply {
                            moveTo(s * 0.2f, 0f); lineTo(-s * 0.1f, -s * 0.17f); lineTo(-s * 0.1f, s * 0.17f); close()
                        }
                        drawPath(arrow, color)
                    }
                }
                Kind.FILTER -> {
                    val color = hueColor(lv.hue[i])
                    val box = Size(s * 0.6f, s * 0.6f)
                    val at = Offset(-s * 0.3f, -s * 0.3f)
                    drawRoundRect(color, at, box, CornerRadius(s * 0.08f), alpha = 0.28f)
                    drawRoundRect(color, at, box, CornerRadius(s * 0.08f), style = Stroke(s * 0.04f))
                }
                Kind.TARGET -> {
                    val color = hueColor(lv.hue[i])
                    val got = tr.recv[i]
                    val lit = got == lv.hue[i]
                    val pulse = if (lit) 1f + 0.06f * sin(now / 180f) else 1f
                    if (lit) {
                        drawCircle(
                            Brush.radialGradient(listOf(color, Color.Transparent), Offset.Zero, s * 0.5f),
                            s * 0.5f, Offset.Zero, alpha = 0.55f,
                        )
                    }
                    drawCircle(Color(0xFF0B0F1E), s * 0.27f * pulse, Offset.Zero)
                    drawCircle(color, s * 0.27f * pulse, Offset.Zero, style = Stroke(s * 0.075f))
                    // The dot shows the colour arriving right now.
                    if (got != 0) drawCircle(hueColor(got), s * (if (lit) 0.15f else 0.09f), Offset.Zero)
                }
                Kind.PORTAL -> {
                    val color = PORTAL_COLORS[lv.hue[i] % PORTAL_COLORS.size]
                    drawCircle(Color(0xFF0B0F1E), s * 0.33f, Offset.Zero)
                    drawCircle(color, s * 0.33f, Offset.Zero, style = Stroke(s * 0.06f))
                    val swirl = PathEffect.dashPathEffect(floatArrayOf(s * 0.12f, s * 0.09f), (now % 100_000) / 1000f * s * 0.3f)
                    drawCircle(color, s * 0.2f, Offset.Zero, alpha = 0.75f, style = Stroke(s * 0.045f, pathEffect = swirl))
                    drawCircle(color, s * 0.06f, Offset.Zero, alpha = 0.9f)
                }
                else -> { // mirror, splitter, one-sided mirror: a bar turned to -45 degrees plus 90 per state
                    val turn = turns[i]
                    val t = if (turn == null) 1f else ((now - turn.at) / 140f).coerceIn(0f, 1f)
                    val offset = if (turn == null) 0f else (if (turn.back) 90f else -90f) * (1f - t)
                    rotate(-45f + 90f * lv.state[i] + offset, Offset.Zero) {
                        val a = Offset(-s * 0.4f, 0f)
                        val b = Offset(s * 0.4f, 0f)
                        val color = if (kind == Kind.SPLIT) Palette.Splitter else Palette.Mirror
                        when (kind) {
                            Kind.SPLIT -> drawLine(
                                color, a, b, s * 0.1f, StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.1f, s * 0.2f), s * 0.05f),
                            )
                            Kind.ONEWAY -> {
                                // dark, hatched back below the bright reflecting face
                                val back = Color(0xFF566089)
                                drawLine(back, Offset(-s * 0.38f, s * 0.085f), Offset(s * 0.38f, s * 0.085f), s * 0.11f)
                                for (k in 0..5) {
                                    val x = -s * 0.32f + k * s * 0.13f
                                    drawLine(back, Offset(x, s * 0.12f), Offset(x - s * 0.07f, s * 0.24f), s * 0.035f, StrokeCap.Round)
                                }
                                drawLine(color, a, b, s * 0.075f, StrokeCap.Round)
                            }
                            else -> drawLine(color, a, b, s * 0.1f, StrokeCap.Round)
                        }
                        if (kind != Kind.ONEWAY) {
                            drawCircle(Palette.Bg, s * 0.085f, Offset.Zero)
                            drawCircle(color, s * 0.085f, Offset.Zero, style = Stroke(s * 0.035f))
                        }
                    }
                }
            }
        }
        if (letters != null && (kind == Kind.EMIT || kind == Kind.FILTER || kind == Kind.TARGET)) {
            // Colour letters sit on small dark badges: the piece's colour top-left, and for a target
            // that is not lit, the colour arriving now bottom-right.
            val style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = (s * 0.2f).toSp())
            val corner = Offset(i % w * s, i / w * s)
            fun badge(mask: Int, right: Boolean) {
                val text = measurer.measure(lettersOf(mask, letters), style.copy(color = hueColor(mask)))
                val pad = s * 0.035f
                val box = Size(text.size.width + 2 * pad, text.size.height.toFloat())
                val at = if (right) Offset(corner.x + s - box.width - gap, corner.y + s - box.height - gap)
                else Offset(corner.x + gap, corner.y + gap)
                drawRoundRect(Palette.Bg, at, box, CornerRadius(s * 0.06f), alpha = 0.88f)
                drawText(text, topLeft = Offset(at.x + pad, at.y))
            }
            badge(lv.hue[i], right = false)
            val got = tr.recv[i]
            if (kind == Kind.TARGET && got != 0 && got != lv.hue[i]) badge(got, right = true)
        }
    }

    if (hint >= 0 && now - hintAt < 4000) {
        drawCircle(
            Palette.Gold, s * 0.46f, center(hint),
            alpha = 0.55f + 0.45f * sin(now / 130f), style = Stroke(3.dp.toPx()),
        )
    }

    if (sparks != null) {
        sparks.removeAll { now - it.born >= 700 }
        for (p in sparks) {
            val t = (now - p.born) / 1000f
            drawCircle(
                hueColor(p.hue), s * 0.045f, Offset((p.x + p.vx * t) * s, (p.y + p.vy * t) * s),
                alpha = (1f - t / 0.7f).coerceIn(0f, 1f), blendMode = BlendMode.Plus,
            )
        }
    }
}
