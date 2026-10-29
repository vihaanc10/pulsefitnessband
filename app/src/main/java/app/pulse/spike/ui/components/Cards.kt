package app.pulse.spike.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType

val CardShape = RoundedCornerShape(22.dp)

/** Small spaced-out capital label. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = PulseColors.TextSecondary) {
    Text(text.uppercase(), modifier = modifier, style = PulseType.Label, color = color, maxLines = 1)
}

/** Dark rounded card with a hairline border. */
@Composable
fun PulseCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PulseColors.Card)
            .border(1.dp, PulseColors.Border, CardShape)
            .padding(20.dp),
        content = content,
    )
}

/** One big number with a small label above it. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    captionColor: Color = PulseColors.TextSecondary,
) {
    PulseCard(modifier) {
        Label(label)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = PulseType.Value)
            if (unit != null) Text(unit, style = PulseType.Unit, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = PulseType.Caption, color = captionColor)
        }
    }
}

data class Stat(val label: String, val value: String, val unit: String? = null)

/** A single card holding 2-4 stats side by side, split by hairlines. */
@Composable
fun StatStrip(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(PulseColors.Card)
            .border(1.dp, PulseColors.Border, CardShape)
            .padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) Box(Modifier.width(1.dp).height(34.dp).background(PulseColors.Border))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(stat.label)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stat.value, style = PulseType.Value.copy(fontSize = 22.sp))
                    if (stat.unit != null) {
                        Text(stat.unit, style = PulseType.Caption, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
                    }
                }
            }
        }
    }
}

/** Two or three choices in one pill, e.g. 7D / 30D. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(PulseColors.Card)
            .border(1.dp, PulseColors.Border, shape)
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                if (selected) PulseColors.CardRaised else Color.Transparent, tween(180), label = "segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = PulseType.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = if (selected) PulseColors.TextPrimary else PulseColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Tiny outlined tag, e.g. "SAMPLE DATA". */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = PulseColors.TextSecondary) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier.clip(shape).border(1.dp, PulseColors.Border, shape).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = PulseType.Label.copy(fontSize = 8.sp), color = color, maxLines = 1)
    }
}

@Composable
fun PulseButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) PulseColors.TextPrimary else Color.Transparent)
            .border(1.dp, if (filled) PulseColors.TextPrimary else PulseColors.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = PulseType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = if (filled) Color.Black else PulseColors.TextPrimary,
        )
    }
}

/** Shown on every data screen until the band has delivered something real. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    onPreview: () -> Unit,
    onOpenLab: () -> Unit,
    modifier: Modifier = Modifier,
    status: String? = null,
    onSync: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(progress = 0f, color = PulseColors.Green, modifier = Modifier.size(200.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("--", style = PulseType.Display, color = PulseColors.TextTertiary)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = PulseType.Headline.copy(fontSize = 24.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, style = PulseType.Body, textAlign = TextAlign.Center)
        if (!status.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(status, style = PulseType.Caption.copy(color = PulseColors.Green), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onSync != null) PulseButton("Sync now", onSync)
            PulseButton("Preview with sample data", onPreview, filled = onSync == null)
            PulseButton("Open Bluetooth lab", onOpenLab, filled = false)
        }
    }
}
