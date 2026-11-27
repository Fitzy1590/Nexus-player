package com.nexus.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** 24×24 HUD icons. Stroked 2 px unless [filled]; tinted by the caller. */
object HudIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                val nodes = PathParser().parsePathString(d).toNodes()
                if (filled) addPath(nodes, fill = SolidColor(Color.White))
                else addPath(
                    nodes, stroke = SolidColor(Color.White), strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Play = icon("play", "M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z", filled = true)
    val Pause = icon("pause", "M6 5h4v14H6zM14 5h4v14h-4z", filled = true)
    val Back = icon("back", "M11 17l-5-5 5-5M18 17l-5-5 5-5")
    val Forward = icon("forward", "M13 17l5-5-5-5M6 17l5-5-5-5")
    val Sound = icon("sound", "M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2")
    val Audio = icon("audio", "M9 18V6l10-2v12M9 18a3 3 0 1 1-6 0a3 3 0 0 1 6 0zM19 16a3 3 0 1 1-6 0a3 3 0 0 1 6 0z")
    val Subs = icon("subs", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM7 12h4M13 12h4M7 15h10")
    val Speed = icon("speed", "M12 14l4-4M4.9 19a9 9 0 1 1 14.2 0")
    val Stats = icon("stats", "M4 20V10M10 20V4M16 20v-7M22 20H2")
    val Settings = icon("settings", "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h10M18 18h2M16 4v4M10 10v4M18 16v4")
}
