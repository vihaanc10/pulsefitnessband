package app.pulse.spike.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pulse.spike.data.formatStrain
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import app.pulse.spike.ui.theme.recoveryColor
import app.pulse.spike.ui.theme.strainColor
import kotlin.math.roundToInt

/**
 * A thin circular progress ring that sweeps in when it appears and re-animates when [progress] changes.
 * [content] receives the animated fraction (0 up to [progress]) so numbers can count up with the ring.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 5.dp,
    durationMillis: Int = 1400,
    content: @Composable BoxScope.(animatedFraction: Float) -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val animated = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animated.animateTo(target, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke * 1.2f // leaves room for the soft glow
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

            drawArc(
                color = PulseColors.Track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke),
            )
            val sweep = 360f * animated.value
            if (sweep > 0.5f) {
                drawArc(
                    color = color.copy(alpha = 0.16f), startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke * 2.4f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content(animated.value)
    }
}

@Composable
private fun BigNumber(text: String, unit: String?, style: androidx.compose.ui.text.TextStyle, unitTopPadding: Dp) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text, style = style)
        if (unit != null) {
            Text(unit, style = PulseType.Unit, modifier = Modifier.padding(start = 2.dp, top = unitTopPadding))
        }
    }
}

@Composable
fun RecoveryRing(score: Int, modifier: Modifier = Modifier) {
    ProgressRing(progress = score / 100f, color = recoveryColor(score), modifier = modifier) { fraction ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Recovery")
            Spacer(Modifier.height(6.dp))
            BigNumber("${(fraction * 100).roundToInt()}", "%", PulseType.Hero, 16.dp)
        }
    }
}

@Composable
fun SleepRing(performance: Int, modifier: Modifier = Modifier, subtitle: String? = null) {
    ProgressRing(progress = performance / 100f, color = PulseColors.Sleep, modifier = modifier) { fraction ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Sleep")
            Spacer(Modifier.height(4.dp))
            BigNumber("${(fraction * 100).roundToInt()}", "%", PulseType.Display, 10.dp)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = PulseType.Caption)
            }
        }
    }
}

@Composable
fun StrainRing(strain: Double, modifier: Modifier = Modifier) {
    ProgressRing(progress = (strain / 21.0).toFloat(), color = strainColor(strain), modifier = modifier) { fraction ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Label("Strain")
            Spacer(Modifier.height(4.dp))
            BigNumber(formatStrain(fraction * 21.0), null, PulseType.Display, 0.dp)
            Spacer(Modifier.height(4.dp))
            Text("of 21", style = PulseType.Caption)
        }
    }
}
