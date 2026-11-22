package app.slate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object Slate {
    val Ink = Color(0xFFF7F7FA)
    val Muted = Color(0xFFB9BAC4)
    val Faint = Color(0xFF7D7F8C)
    /** Frosted glass over the wallpaper. */
    val Glass = Color(0x59000000)
    val GlassLight = Color(0x33FFFFFF)
    /** Solid sheets (settings, menus). */
    val Sheet = Color(0xF21C1C21)
    val SheetRaised = Color(0xFF2A2A31)
    val Accent = Color(0xFF8EA2FF)
    val Danger = Color(0xFFFF6B6B)
    val LabelShadow = Shadow(Color(0x99000000), Offset(0f, 1.5f), 4f)
    /** Frosted cards (widgets, library boxes): light enough to read on any wallpaper. */
    val Card = Color(0x2BFFFFFF)
    val CardEdge = Color(0x1FFFFFFF)
}

/** The same superellipse the icons are cut to, for shadows and tiles. */
val Squircle: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val rx = size.width / 2f
    val ry = size.height / 2f
    val n = 5.0
    for (i in 0..96) {
        val t = 2 * Math.PI * i / 96
        val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
        val x = rx + rx * Math.signum(c) * Math.pow(kotlin.math.abs(c), 2 / n)
        val y = ry + ry * Math.signum(s) * Math.pow(kotlin.math.abs(s), 2 / n)
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    color: Color = Slate.Ink,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    shadow: Boolean = false,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, textAlign = align,
            shadow = if (shadow) Slate.LabelShadow else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Press-to-dim click without ripples. */
@Composable
fun Modifier.press(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.55f else 1f, tween(110), label = "press")
    return graphicsLayer { alpha = a }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

fun Modifier.glass(shape: Shape, color: Color = Slate.Glass): Modifier = clip(shape).background(color)
