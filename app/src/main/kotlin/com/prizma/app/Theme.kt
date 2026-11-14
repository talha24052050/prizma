package com.prizma.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One dark look: beams glow on a dark optical bench. */
object Palette {
    val Bg = Color(0xFF090C17)
    val Panel = Color(0xFF121729)
    val Cell = Color(0xFF10152A)
    val Line = Color(0xFF262D49)
    val Fg = Color(0xFFE8EBF7)
    val Muted = Color(0xFF8891B3)
    val Gold = Color(0xFFFFD83B)
    val GoldDim = Color(0xFF5B5020)
    val OnGold = Color(0xFF1A1500)
    val Mirror = Color(0xFFE6EBFF)
    val Splitter = Color(0xFF8FB0FF)
    val Wall = Color(0xFF2B3355)
    val WallEdge = Color(0xFF3D476F)
    val Danger = Color(0xFFFF6B7A)
}

private val HUES = arrayOf(
    Color.Transparent,
    Color(0xFFFF4055), // red
    Color(0xFF35E27F), // green
    Color(0xFFFFD83B), // red + green
    Color(0xFF4A86FF), // blue
    Color(0xFFFF58E4), // red + blue
    Color(0xFF3FE9FF), // green + blue
    Color(0xFFFFFFFF), // all three
)

/** Display colour of a light colour mask (see [com.prizma.core.Hue]). */
fun hueColor(mask: Int): Color = HUES[mask and 7]

/** Portal pairs get colours no beam can have, so they are never mistaken for light. */
val PORTAL_COLORS = arrayOf(Color(0xFFB48CFF), Color(0xFFFF9D4A))

val Display = FontFamily(
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)
val Body = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

object Type {
    val Body = TextStyle(fontFamily = com.prizma.app.Body, fontSize = 16.sp, lineHeight = 23.sp, color = Palette.Fg)
    val Small = Body.copy(fontSize = 14.sp, lineHeight = 20.sp, color = Palette.Muted)
    val Button = Body.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val Heading = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp,
        letterSpacing = 0.6.sp, color = Palette.Fg,
    )
    val Title = Heading.copy(fontSize = 50.sp, lineHeight = 54.sp, letterSpacing = 7.sp)
    val Eyebrow = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp,
        letterSpacing = 2.sp, color = Palette.Gold,
    )
    val Number = Heading.copy(fontSize = 20.sp)
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.Body,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, textAlign = align ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Shrinks slightly while pressed, the only press feedback the app uses. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .graphicsLayer {
            val s = if (pressed && role != null) 0.96f else 1f
            scaleX = s; scaleY = s
            alpha = if (enabled) 1f else 0.38f
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

enum class ButtonKind { Panel, Primary, Ghost }

@Composable
fun PButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Panel,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val base = when (kind) {
        ButtonKind.Panel -> Modifier.background(Palette.Panel, shape).border(1.dp, Palette.Line, shape)
        ButtonKind.Primary -> Modifier.background(Palette.Gold, shape)
        ButtonKind.Ghost -> Modifier
    }
    Box(
        modifier
            .pressable(enabled, onClick = onClick)
            .clip(shape)
            .then(base)
            .padding(PaddingValues(horizontal = if (kind == ButtonKind.Ghost) 4.dp else 14.dp, vertical = 12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            text,
            style = Type.Button,
            color = when (kind) {
                ButtonKind.Primary -> Palette.OnGold
                ButtonKind.Ghost -> Palette.Muted
                ButtonKind.Panel -> Palette.Fg
            },
            maxLines = 1,
        )
    }
}

/** A panel with the app's standard border and radius. */
fun Modifier.panel(border: Color = Palette.Line): Modifier {
    val shape = RoundedCornerShape(12.dp)
    return this.clip(shape).background(Palette.Panel, shape).border(1.dp, border, shape)
}

@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(46.dp, 26.dp)) {
        val r = size.height / 2
        drawRoundRect(
            if (checked) Palette.Gold else Palette.Line,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        drawCircle(
            if (checked) Palette.OnGold else Palette.Muted,
            radius = r - 4.dp.toPx(),
            center = Offset(if (checked) size.width - r else r, r),
        )
    }
}

/** A row of three stars; [count] of them filled. Drawn, so it never depends on a font having the glyph. */
@Composable
fun Stars(count: Int, modifier: Modifier = Modifier, size: Dp = 12.dp, scales: List<Float>? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(size / 5)) {
        repeat(3) { k ->
            Canvas(Modifier.size(size)) {
                val scale = scales?.getOrNull(k) ?: 1f
                val c = Offset(this.size.width / 2, this.size.height / 2)
                val outer = this.size.width / 2 * scale
                val path = Path()
                for (p in 0 until 10) {
                    val radius = if (p % 2 == 0) outer else outer * 0.42f
                    val a = -PI / 2 + p * PI / 5
                    val x = c.x + (cos(a) * radius).toFloat()
                    val y = c.y + (sin(a) * radius).toFloat()
                    if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (k < count) drawPath(path, Palette.Gold)
                else drawPath(path, Palette.Line, style = Stroke(width = this.size.width / 10))
            }
        }
    }
}
