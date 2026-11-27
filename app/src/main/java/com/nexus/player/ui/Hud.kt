package com.nexus.player.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.nexus.player.R
import androidx.tv.material3.Text

object Hud {
    val Obsidian = Color(0xFF0B0E14)
    val Panel = Color(0xCC121822)
    val Line = Color(0x2900F2FE)
    val TextC = Color(0xFFE6EDF3)
    val Muted = Color(0xFF8B98A9)
    val Mint = Color(0xFF10B981)
    val Amber = Color(0xFFF59E0B)
    val Cyan = Color(0xFF00F2FE)
    val Violet = Color(0xFF7C3AED)
    /** Cinema Glass: 75 % obsidian + 12 % white hairline. Android has no backdrop blur over SurfaceView. */
    val Glass = Color(0xBF0B0E14)
    val GlassLine = Color(0x1FFFFFFF)

    val InterFamily = FontFamily(Font(R.font.inter_400), Font(R.font.inter_600, FontWeight.SemiBold))
    val OutfitFamily = FontFamily(Font(R.font.outfit_600, FontWeight.SemiBold))
    val MonoFamily = FontFamily(Font(R.font.jetbrains_mono_400), Font(R.font.jetbrains_mono_600, FontWeight.SemiBold))
    val Ui = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, color = TextC)
    val Title = TextStyle(fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = TextC)
    val Mono = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp, color = Cyan)
    val Label = Ui.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted, letterSpacing = 1.sp)

    val Shape = RoundedCornerShape(6.dp)
    const val FOCUS_SCALE = 1.05f
}

fun Modifier.glass(): Modifier = this
    .background(Hud.Glass, RoundedCornerShape(10.dp))
    .border(1.dp, Hud.GlassLine, RoundedCornerShape(10.dp))

/** Compose TV button: 1.05× focus scale, 3 dp cyan focus border, ≥44 dp target; selected = violet. Optional leading icon. */
@Composable
fun HudButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    label: String? = null,
    content: @Composable RowScope.() -> Unit = {},
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        // tv-material handles only D-pad/Enter; add touch taps without adding a second focus target.
        modifier = modifier.heightIn(min = 44.dp)
            .pointerInput(enabled, onClick) { if (enabled) detectTapGestures { onClick() } },
        shape = ButtonDefaults.shape(Hud.Shape),
        scale = ButtonDefaults.scale(focusedScale = Hud.FOCUS_SCALE),
        glow = ButtonDefaults.glow(),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) Hud.Violet else Hud.GlassLine), shape = Hud.Shape),
            focusedBorder = Border(BorderStroke(3.dp, Hud.Cyan), shape = Hud.Shape),
        ),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) Hud.Violet.copy(alpha = 0.22f) else Color.Transparent,
            contentColor = Hud.TextC,
            focusedContainerColor = Color(0x1A00F2FE),
            focusedContentColor = Hud.Cyan,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Hud.Muted,
        ),
        contentPadding = PaddingValues(horizontal = if (icon != null) 12.dp else 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        if (label != null) Text(label, modifier = if (icon != null) Modifier.padding(start = 8.dp) else Modifier)
        content()
    }
}

/**
 * D-pad + touch slider. Focused: Left/Right step the value. Touch: tap or drag to set.
 */
@Composable
fun StepSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    display: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Hud.Mint,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(1) }
    fun set(v: Float) { if (enabled) onChange(((v / step).let { Math.round(it) } * step).coerceIn(min, max)) }
    fun fromX(x: Float) = set(min + (x / widthPx).coerceIn(0f, 1f) * (max - min))
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = Hud.Ui, modifier = Modifier.width(112.dp))
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .graphicsLayer { val s = if (focused) Hud.FOCUS_SCALE else 1f; scaleX = s; scaleY = s }
                .border(if (focused) 3.dp else 1.dp, if (focused) Hud.Cyan else Hud.Line, Hud.Shape)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent {
                    if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (it.key) {
                        Key.DirectionLeft -> { set(value - step); true }
                        Key.DirectionRight -> { set(value + step); true }
                        else -> false
                    }
                }
                .focusable(enabled)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(min, max, step) {
                    detectTapGestures { fromX(it.x) }
                }
                .pointerInput(min, max, step) {
                    detectHorizontalDragGestures { change, _ -> fromX(change.position.x) }
                }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp, max = 6.dp).background(Color(0x14FFFFFF), Hud.Shape))
            Box(
                Modifier.fillMaxWidth(((value - min) / (max - min)).coerceIn(0f, 1f))
                    .heightIn(min = 6.dp, max = 6.dp).background(if (enabled) accent else Hud.Muted, Hud.Shape),
            )
        }
        Text(display, style = Hud.Mono.copy(color = accent), modifier = Modifier.width(80.dp))
    }
}

/** Vertical volume bar; amber segment above 100 %. */
@Composable
fun VolumeBar(volume: Float, modifier: Modifier = Modifier) {
    Box(modifier.width(8.dp).fillMaxHeight().background(Color(0x14FFFFFF), Hud.Shape), contentAlignment = Alignment.BottomCenter) {
        val v = volume.coerceIn(0f, 2f) / 2f
        Box(Modifier.fillMaxWidth().fillMaxHeight(v).background(if (volume > 1f) Hud.Amber else Hud.Mint, Hud.Shape))
        Box(Modifier.fillMaxWidth().fillMaxHeight(minOf(v, 0.5f)).background(Hud.Mint, Hud.Shape))
    }
}
