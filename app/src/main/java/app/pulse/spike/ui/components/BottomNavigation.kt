package app.pulse.spike.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.R
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType

enum class PulseTab(val label: String) {
    Home("Home"),
    Sleep("Sleep"),
    Health("Health"),
    Coach("Coach"),
    Profile("Profile"),
}

@Composable
fun BottomNavigation(selected: PulseTab, onSelect: (PulseTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(PulseColors.Background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PulseColors.Border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseTab.entries.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(
                    if (active) PulseColors.TextPrimary else PulseColors.TextTertiary, tween(220), label = "navTint",
                )
                val indicator by animateColorAsState(
                    if (active) PulseColors.Green else Color.Transparent, tween(220), label = "navIndicator",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(tab)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.width(16.dp).height(2.dp).background(indicator))
                    Spacer(Modifier.height(9.dp))
                    NavIcon(tab, tint, Modifier.size(22.dp))
                    Spacer(Modifier.height(5.dp))
                    Text(tab.label, style = PulseType.Label.copy(fontSize = 9.5.sp, letterSpacing = 0.6.sp), color = tint)
                }
            }
        }
    }
}

/** Icons are drawn by hand so the app needs no icon library. */
@Composable
fun NavIcon(tab: PulseTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            PulseTab.Home -> {
                val roof = Path().apply {
                    moveTo(w * 0.08f, h * 0.48f); lineTo(w * 0.5f, h * 0.12f); lineTo(w * 0.92f, h * 0.48f)
                }
                val body = Path().apply {
                    moveTo(w * 0.2f, h * 0.42f); lineTo(w * 0.2f, h * 0.88f)
                    lineTo(w * 0.8f, h * 0.88f); lineTo(w * 0.8f, h * 0.42f)
                }
                drawPath(roof, tint, style = stroke)
                drawPath(body, tint, style = stroke)
            }
            PulseTab.Sleep -> {
                val outer = Path().apply { addOval(Rect(w * 0.12f, h * 0.12f, w * 0.88f, h * 0.88f)) }
                val cut = Path().apply { addOval(Rect(w * 0.36f, h * 0.0f, w * 1.06f, h * 0.7f)) }
                drawPath(Path.combine(PathOperation.Difference, outer, cut), tint, style = stroke)
            }
            PulseTab.Health -> {
                val beat = Path().apply {
                    moveTo(w * 0.04f, h * 0.52f); lineTo(w * 0.3f, h * 0.52f); lineTo(w * 0.42f, h * 0.18f)
                    lineTo(w * 0.58f, h * 0.84f); lineTo(w * 0.7f, h * 0.52f); lineTo(w * 0.96f, h * 0.52f)
                }
                drawPath(beat, tint, style = stroke)
            }
            PulseTab.Coach -> {
                val spark = Path().apply {
                    moveTo(w * 0.5f, h * 0.06f); lineTo(w * 0.62f, h * 0.38f); lineTo(w * 0.94f, h * 0.5f)
                    lineTo(w * 0.62f, h * 0.62f); lineTo(w * 0.5f, h * 0.94f); lineTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.06f, h * 0.5f); lineTo(w * 0.38f, h * 0.38f); close()
                }
                drawPath(spark, tint, style = stroke)
            }
            PulseTab.Profile -> {
                drawCircle(tint, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.3f), style = stroke)
                drawArc(
                    color = tint, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.58f), size = Size(w * 0.72f, h * 0.72f), style = stroke,
                )
            }
        }
    }
}

/** The Pulse logo mark (the heartbeat line turning into a P). Set a height; the width follows. */
@Composable
fun PulseMark(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.logo_mark), contentDescription = "Pulse", modifier = modifier)
}

/** The full logo: the mark with the PULSE lettering under it. Set a height; the width follows. */
@Composable
fun PulseLockup(modifier: Modifier = Modifier) {
    Image(painter = painterResource(R.drawable.logo_lockup), contentDescription = "Pulse", modifier = modifier)
}
