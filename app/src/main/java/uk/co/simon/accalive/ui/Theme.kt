package uk.co.simon.accalive.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import uk.co.simon.accalive.data.LegState
import uk.co.simon.accalive.data.TeamInfo

object C {
    val Bg = Color(0xFF0A120F)
    val Surface = Color(0xFF131E19)
    val Raised = Color(0xFF1B2A23)
    val Line = Color(0xFF253A30)
    val Brand = Color(0xFF00A651)
    val BrandDeep = Color(0xFF00573A)
    val BrandDark = Color(0xFF013322)
    val Yellow = Color(0xFFFFE01B)
    val Text = Color(0xFFF4F7F5)
    val Sub = Color(0xFF9DB0A6)
    val Mute = Color(0xFF63766C)
    val Win = Color(0xFF2EE07A)
    val Lose = Color(0xFFFF4D5E)
    val Level = Color(0xFFFFB020)
    val Live = Color(0xFFFF3B47)
}

val Tnum = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun AccaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Brand,
            onPrimary = Color.White,
            secondary = C.Yellow,
            onSecondary = Color.Black,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Raised,
            onSurfaceVariant = C.Sub,
            surfaceContainer = C.Surface,
            surfaceContainerHigh = C.Raised,
            surfaceContainerLow = C.Bg,
            outline = C.Line,
            error = C.Lose
        ),
        content = content
    )
}

fun stateColor(s: LegState): Color = when (s) {
    LegState.WON, LegState.WINNING -> C.Win
    LegState.LOST, LegState.LOSING -> C.Lose
    LegState.LEVEL -> C.Level
    else -> C.Sub
}

fun probColor(p: Double): Color = when {
    p >= 0.6 -> C.Win
    p >= 0.35 -> C.Level
    else -> C.Lose
}

/** Team colour, lifted when it's too dark to read on the near-black background. */
fun teamColor(t: TeamInfo): Color {
    val c = Color(t.color)
    if (c.luminance() >= 0.05f) return c
    val alt = Color(t.alt)
    return if (alt.luminance() >= 0.05f) alt else C.Sub
}
