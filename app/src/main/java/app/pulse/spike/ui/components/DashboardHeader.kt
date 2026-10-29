package app.pulse.spike.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.data.DeviceStatus
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Top of the Home screen: logo, band status, greeting, profile shortcut and a day selector.
 * [canGoNext] is false on today because there is no tomorrow yet.
 */
@Composable
fun DashboardHeader(
    date: LocalDate,
    greeting: String,
    initial: String,
    device: DeviceStatus,
    isSample: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isToday = date == LocalDate.now()
    val dateText = if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PulseMark(Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("PULSE", style = PulseType.Label.copy(fontSize = 11.sp, letterSpacing = 3.sp), color = PulseColors.TextPrimary)
            if (isSample) {
                Spacer(Modifier.width(10.dp))
                Pill("Sample data", color = PulseColors.Yellow)
            }
            Spacer(Modifier.weight(1f))
            DeviceChip(device)
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, style = PulseType.Headline.copy(fontSize = 26.sp), modifier = Modifier.weight(1f))
            Box(
                Modifier.size(38.dp).clip(CircleShape).border(1.dp, PulseColors.Border, CircleShape)
                    .background(PulseColors.Card).clickable(onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, style = PulseType.Title.copy(fontSize = 14.sp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            ArrowButton("‹", enabled = true, onClick = onPrev)
            Label(dateText, color = PulseColors.TextPrimary)
            ArrowButton("›", enabled = canGoNext, onClick = onNext)
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph, style = PulseType.Headline.copy(fontSize = 24.sp),
            color = if (enabled) PulseColors.TextPrimary else PulseColors.TextTertiary, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceChip(device: DeviceStatus) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, PulseColors.Border, shape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (device.connected) PulseColors.Green else PulseColors.TextTertiary),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            if (device.connected) "${device.batteryPercent}%" else "Not connected",
            style = PulseType.Caption.copy(fontSize = 11.sp), color = PulseColors.TextPrimary,
        )
    }
}
