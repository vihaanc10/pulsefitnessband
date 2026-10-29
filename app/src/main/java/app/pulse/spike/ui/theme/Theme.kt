package app.pulse.spike.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PulseColors {
    val Background = Color(0xFF050505)
    val Card = Color(0xFF111111)
    val CardRaised = Color(0xFF1A1A1A)
    val Border = Color(0xFF1F1F1F)
    val Track = Color(0xFF1B1B1D)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)
    val TextTertiary = Color(0xFF55555A)

    val Green = Color(0xFF00D26A)
    val Yellow = Color(0xFFFFD43B)
    val Red = Color(0xFFFF453A)

    val Sleep = Color(0xFF8FA6C9)
    val Strain = Color(0xFF4DA3FF)

    val StageAwake = Color(0xFF45454A)
    val StageLight = Color(0xFF8FA6C9)
    val StageDeep = Color(0xFF5568C8)

    val Zones = listOf(Color(0xFF5B6573), Color(0xFF4DA3FF), Color(0xFF00D26A), Color(0xFFFFD43B), Color(0xFFFF453A))
}

fun recoveryColor(score: Int): Color = when {
    score >= 67 -> PulseColors.Green
    score >= 34 -> PulseColors.Yellow
    else -> PulseColors.Red
}

fun strainColor(strain: Double): Color = if (strain >= 18.0) PulseColors.Red else PulseColors.Strain

object PulseType {
    private val sans = FontFamily.SansSerif

    val Hero = TextStyle(fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 84.sp, letterSpacing = (-2).sp, color = PulseColors.TextPrimary)
    val Display = TextStyle(fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 60.sp, letterSpacing = (-1.5).sp, color = PulseColors.TextPrimary)
    val Headline = TextStyle(fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 30.sp, letterSpacing = (-0.5).sp, color = PulseColors.TextPrimary)
    val Value = TextStyle(fontFamily = sans, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.5).sp, color = PulseColors.TextPrimary)
    val Title = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = PulseColors.TextPrimary)
    val Body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFFB4B4B9))
    val Unit = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = PulseColors.TextSecondary)
    val Label = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp, color = PulseColors.TextSecondary)
    val Caption = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = PulseColors.TextSecondary)
}

/** Always dark. Also themes the Bluetooth lab screens. */
@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PulseColors.Green,
            onPrimary = Color.Black,
            tertiary = PulseColors.Strain,
            background = PulseColors.Background,
            onBackground = PulseColors.TextPrimary,
            surface = PulseColors.Card,
            onSurface = PulseColors.TextPrimary,
            surfaceVariant = PulseColors.CardRaised,
            onSurfaceVariant = PulseColors.TextSecondary,
            outline = PulseColors.Border,
            error = PulseColors.Red,
        ),
        content = content,
    )
}
