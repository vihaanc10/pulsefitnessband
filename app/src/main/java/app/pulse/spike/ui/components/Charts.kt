package app.pulse.spike.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulse.spike.data.HrPoint
import app.pulse.spike.data.SleepStages
import app.pulse.spike.data.formatDuration
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import kotlin.math.max
import kotlin.math.min

/** Heart rate across a 24-hour day. Draws itself left to right. */
@Composable
fun HeartRateChart(
    points: List<HrPoint>,
    restingHr: Int,
    modifier: Modifier = Modifier,
    color: Color = PulseColors.Red,
    sleepSpan: IntRange? = null,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val minBpm = max(30f, (points.minOf { it.bpm } - 8).toFloat())
        val maxBpm = max(minBpm + 40f, (points.maxOf { it.bpm } + 10).toFloat())
        fun x(p: HrPoint) = p.minuteOfDay / 1440f * size.width
        fun y(bpm: Int) = size.height - ((bpm - minBpm) / (maxBpm - minBpm)).coerceIn(0f, 1f) * size.height

        val hairline = 1.dp.toPx()
        if (sleepSpan != null) {
            val left = sleepSpan.first / 1440f * size.width
            val right = sleepSpan.last / 1440f * size.width
            drawRect(PulseColors.Sleep.copy(alpha = 0.10f), topLeft = Offset(left, 0f), size = Size(right - left, size.height))
        }
        for (g in 1..3) {
            val gy = size.height * g / 4f
            drawLine(PulseColors.Border, Offset(0f, gy), Offset(size.width, gy), strokeWidth = hairline)
        }
        drawLine(
            PulseColors.TextTertiary, Offset(0f, y(restingHr)), Offset(size.width, y(restingHr)),
            strokeWidth = hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )

        // A gap of more than 20 minutes (band off, or no readings) breaks the line instead of drawing across it.
        val segments = ArrayList<List<HrPoint>>()
        var current = ArrayList<HrPoint>()
        for (p in points) {
            if (current.isNotEmpty() && p.minuteOfDay - current.last().minuteOfDay > 20) {
                segments.add(current)
                current = ArrayList()
            }
            current.add(p)
        }
        if (current.isNotEmpty()) segments.add(current)

        clipRect(right = size.width * reveal.value) {
            for (segment in segments) {
                if (segment.size < 2) {
                    drawCircle(color, 1.5.dp.toPx(), Offset(x(segment[0]), y(segment[0].bpm)))
                    continue
                }
                val line = Path()
                segment.forEachIndexed { i, p ->
                    if (i == 0) {
                        line.moveTo(x(p), y(p.bpm))
                    } else {
                        val before = segment[i - 1]
                        val midX = (x(before) + x(p)) / 2f
                        line.cubicTo(midX, y(before.bpm), midX, y(p.bpm), x(p), y(p.bpm))
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(segment.last()), size.height)
                    lineTo(x(segment.first()), size.height)
                    close()
                }
                drawPath(area, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.26f), Color.Transparent)))
                drawPath(line, color = color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (reveal.value >= 1f) {
            val last = points.last()
            drawCircle(color.copy(alpha = 0.28f), 9.dp.toPx(), Offset(x(last), y(last.bpm)))
            drawCircle(Color.White, 3.5.dp.toPx(), Offset(x(last), y(last.bpm)))
        }
    }
}

/** Time labels to sit under [HeartRateChart]. */
@Composable
fun DayAxisLabels(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (t in listOf("12a", "6a", "12p", "6p", "12a")) Text(t, style = PulseType.Caption.copy(color = PulseColors.TextTertiary))
    }
}

/** A smooth line for a metric across several days. */
@Composable
fun TrendLineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val low = values.min()
        val high = values.max()
        val pad = max((high - low) * 0.2f, 1f)
        val bottom = low - pad
        val top = high + pad
        fun x(i: Int) = i / (values.size - 1f) * size.width
        fun y(v: Float) = size.height - (v - bottom) / (top - bottom) * size.height

        drawLine(PulseColors.Border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) {
                val midX = (x(i - 1) + x(i)) / 2f
                cubicTo(midX, y(values[i - 1]), midX, y(values[i]), x(i), y(values[i]))
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(values.size - 1), size.height)
            lineTo(0f, size.height)
            close()
        }
        clipRect(right = size.width * reveal.value) {
            drawPath(area, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)))
            drawPath(line, color = color, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (reveal.value >= 1f) {
            val i = values.size - 1
            drawCircle(color.copy(alpha = 0.28f), 8.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(Color.White, 3.dp.toPx(), Offset(x(i), y(values[i])))
        }
    }
}

/**
 * Bars that grow up from the bottom. Each bar is a list of stacked segments, one colour per segment.
 * The last bar is full strength; the others are dimmed so "today" stands out.
 */
@Composable
fun BarChart(
    bars: List<List<Float>>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
) {
    val growth = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (bars.isEmpty()) return@Canvas
            val gap = if (bars.size > 14) 2.dp.toPx() else 8.dp.toPx()
            val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
            val maxTotal = max(bars.maxOf { it.sum() } * 1.1f, 0.0001f)
            val radius = CornerRadius(min(barWidth / 2f, 6.dp.toPx()))

            drawLine(PulseColors.Border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            bars.forEachIndexed { index, segments ->
                val strength = if (index == bars.lastIndex || bars.size == 1) 1f else 0.45f
                var top = size.height
                segments.forEachIndexed { s, value ->
                    val height = value / maxTotal * size.height * growth.value
                    if (height > 0.5f) {
                        drawRoundRect(
                            color = colors[s % colors.size].copy(alpha = strength),
                            topLeft = Offset(index * (barWidth + gap), top - height),
                            size = Size(barWidth, height),
                            cornerRadius = radius,
                        )
                        top -= height
                    }
                }
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            if (labels.size <= 7) {
                Row(Modifier.fillMaxWidth()) {
                    labels.forEach {
                        Text(
                            it, Modifier.weight(1f), style = PulseType.Caption.copy(color = PulseColors.TextTertiary),
                            textAlign = TextAlign.Center, maxLines = 1,
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (l in listOf(labels.first(), labels[labels.size / 2], labels.last())) {
                        Text(l, style = PulseType.Caption.copy(color = PulseColors.TextTertiary), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One slim bar split into awake / light / deep, with a legend. */
@Composable
fun SleepStagesBar(stages: SleepStages, modifier: Modifier = Modifier) {
    val items = listOf(
        Triple("Awake", stages.awakeMin, PulseColors.StageAwake),
        Triple("Light", stages.lightMin, PulseColors.StageLight),
        Triple("Deep", stages.deepMin, PulseColors.StageDeep),
    )
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.forEach { (_, minutes, color) ->
                Box(
                    Modifier.weight(max(minutes, 1).toFloat()).fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp)).background(color),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            items.forEach { (name, minutes, color) ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                        Label(name, Modifier.padding(start = 6.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(formatDuration(minutes), style = PulseType.Title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                }
            }
        }
    }
}

/** One heart-rate zone: label, thin bar and minutes. */
@Composable
fun ZoneRow(zone: Int, minutes: Int, maxMinutes: Int, modifier: Modifier = Modifier) {
    val fraction = if (maxMinutes <= 0 || minutes <= 0) 0f else max(minutes / maxMinutes.toFloat(), 0.03f)
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Zone $zone", style = PulseType.Caption, modifier = Modifier.width(54.dp))
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.Track)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(PulseColors.Zones[zone - 1]))
        }
        Text(
            "${minutes}m", style = PulseType.Caption, textAlign = TextAlign.End,
            modifier = Modifier.width(52.dp),
        )
    }
}
