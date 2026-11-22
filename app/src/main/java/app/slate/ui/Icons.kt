package app.slate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.slate.data.App
import app.slate.data.IconCache
import app.slate.data.IconStyle

val LocalIconCache = staticCompositionLocalOf<IconCache> { error("no icon cache") }
val LocalIconStyle = staticCompositionLocalOf { IconStyle.Colour }

@Composable
fun AppIcon(app: App, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    val style = LocalIconStyle.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState(cache.peek(app, px, style), app.key, px, style) { value = cache.load(app, px, style) }
    val b = bmp
    val shadowBmp = remember(px) { if (size > 40.dp) cache.shadow(px) else null }
    val shadow = Modifier.drawBehind {
        shadowBmp?.let { sb ->
            val o = (sb.width - px) / 2f
            drawImage(sb, topLeft = androidx.compose.ui.geometry.Offset(-o, -o))
        }
    }
    if (b == null) {
        Box(modifier.size(size).clip(Squircle).background(Slate.GlassLight))
    } else {
        Image(b, contentDescription = app.label, modifier = modifier.size(size).then(if (size > 40.dp) shadow else Modifier))
    }
}

/** A frosted tile showing up to 9 tiny icons. */
@Composable
fun FolderIcon(apps: List<App>, size: Dp, modifier: Modifier = Modifier) {
    val pad = size * 0.12f
    val gap = size * 0.05f
    val mini = (size - pad * 2 - gap * 2) / 3
    Box(
        modifier.size(size).clip(Squircle).background(Slate.Card).padding(pad),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { AppIcon(it, mini) }
                }
            }
        }
    }
}

/** Icon with its name underneath, as on the home grid. */
@Composable
fun LabeledIcon(
    label: String,
    iconSize: Dp,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        if (showLabel) {
            Spacer(Modifier.size(6.dp))
            Box(Modifier.height(18.dp).width(iconSize + 30.dp), contentAlignment = Alignment.Center) {
                Txt(label, size = 12.5.sp, align = TextAlign.Center, maxLines = 1, shadow = true)
            }
        }
    }
}
