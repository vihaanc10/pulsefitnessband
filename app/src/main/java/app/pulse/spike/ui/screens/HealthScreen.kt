package app.pulse.spike.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.pulse.spike.data.DayData
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.data.formatDuration
import app.pulse.spike.data.formatStrain
import app.pulse.spike.data.formatThousands
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.BarChart
import app.pulse.spike.ui.components.DayAxisLabels
import app.pulse.spike.ui.components.EmptyState
import app.pulse.spike.ui.components.HeartRateChart
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.components.SegmentedControl
import app.pulse.spike.ui.components.TrendLineChart
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HealthScreen(ui: PulseUiState, repo: HealthRepository) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Health", style = PulseType.Headline)
        Spacer(Modifier.height(24.dp))
        if (!repo.hasData) {
            EmptyState(
                title = "No health data yet",
                message = "Trends for heart rate, sleep, steps and calories appear here after your band has synced a few days.",
                onPreview = { ui.useSample = true },
                onOpenLab = { ui.labOpen = true },
                status = ui.syncStatus,
                onSync = if (ui.bandSaved && !ui.useSample) ui.syncAction else null,
                onAddDevice = if (!ui.bandSaved && !ui.useSample) ({ ui.addDeviceOpen = true }) else null,
            )
        } else {
            HealthContent(ui, repo)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HealthContent(ui: PulseUiState, repo: HealthRepository) {
    val range = ui.healthRange
    val data = remember(range, repo) { repo.days(LocalDate.now(), range) }
    val today = data.last()
    val labels = data.map { dayLabel(it, range) }

    SegmentedControl(
        options = listOf("7D", "30D"),
        selectedIndex = if (range == 7) 0 else 1,
        onSelect = { ui.healthRange = if (it == 0) 7 else 30 },
        modifier = Modifier.width(150.dp),
    )
    Spacer(Modifier.height(20.dp))

    // Heart rate today
    TrendCard("Heart rate", "${today.currentHr}", "bpm now", "Avg ${today.avgHr} / Max ${today.maxHr}" to PulseColors.TextSecondary) {
        HeartRateChart(today.heartRate, today.restingHr, Modifier.fillMaxWidth().height(130.dp))
        Spacer(Modifier.height(8.dp))
        DayAxisLabels()
    }

    if (today.hasScores) {
        // Resting heart rate
        val resting = data.map { it.restingHr.toFloat() }
        TrendCard(
            "Resting heart rate", "${today.restingHr}", "bpm",
            delta(today.restingHr.toDouble(), resting.average(), higherIsBetter = false, unit = "bpm"),
        ) {
            TrendLineChart(resting, PulseColors.Red, Modifier.fillMaxWidth().height(110.dp))
            ChartAxis(labels)
        }

        // HRV
        val hrv = data.mapNotNull { it.hrvMs?.toFloat() }
        if (hrv.size >= 2) {
            TrendCard(
                "HRV", "${today.hrvMs ?: "--"}", "ms",
                delta((today.hrvMs ?: 0).toDouble(), hrv.average(), higherIsBetter = true, unit = "ms"),
            ) {
                TrendLineChart(hrv, PulseColors.Green, Modifier.fillMaxWidth().height(110.dp))
                ChartAxis(labels)
            }
        } else {
            TrendCard("HRV", "--", "ms", "Not available from this band" to PulseColors.TextSecondary) {}
        }

        // Respiratory rate
        val breathing = data.mapNotNull { it.respiratoryRate?.toFloat() }
        if (breathing.size >= 2) {
            TrendCard(
                "Respiratory rate", String.format(Locale.US, "%.1f", today.respiratoryRate ?: 0.0), "rpm",
                delta(today.respiratoryRate ?: 0.0, breathing.average(), higherIsBetter = false, unit = "rpm"),
            ) {
                TrendLineChart(breathing, PulseColors.Sleep, Modifier.fillMaxWidth().height(110.dp))
                ChartAxis(labels)
            }
        }

        // Sleep trends
        TrendCard(
            "Sleep", formatDuration(today.sleepMinutes), "last night",
            delta(today.sleepMinutes.toDouble(), data.map { it.sleepMinutes }.average(), higherIsBetter = true, unit = "min"),
        ) {
            BarChart(
                bars = data.map { listOf(it.stages.deepMin / 60f, it.stages.lightMin / 60f) },
                colors = listOf(PulseColors.StageDeep, PulseColors.StageLight),
                modifier = Modifier.fillMaxWidth().height(140.dp),
                labels = labels,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Deep", PulseColors.StageDeep)
                Legend("Light", PulseColors.StageLight)
            }
        }

        // Weekly activity (strain)
        TrendCard(
            "Activity", formatStrain(today.strain), "strain",
            delta(today.strain, data.map { it.strain }.average(), higherIsBetter = true, unit = "strain"),
        ) {
            BarChart(data.map { listOf(it.strain.toFloat()) }, listOf(PulseColors.Strain), Modifier.fillMaxWidth().height(120.dp), labels)
        }

    }

    // Calories
    TrendCard(
        "Calories", formatThousands(today.calories), "kcal",
        delta(today.calories.toDouble(), data.map { it.calories }.average(), higherIsBetter = true, unit = "kcal"),
    ) {
        BarChart(data.map { listOf(it.calories.toFloat()) }, listOf(PulseColors.Yellow), Modifier.fillMaxWidth().height(120.dp), labels)
    }

    // Steps
    TrendCard(
        "Steps", formatThousands(today.steps), "steps",
        delta(today.steps.toDouble(), data.map { it.steps }.average(), higherIsBetter = true, unit = "steps"),
        last = true,
    ) {
        BarChart(data.map { listOf(it.steps.toFloat()) }, listOf(PulseColors.Green), Modifier.fillMaxWidth().height(120.dp), labels)
    }
}

@Composable
private fun TrendCard(
    title: String,
    value: String,
    unit: String,
    delta: Pair<String, Color>,
    last: Boolean = false,
    content: @Composable () -> Unit,
) {
    PulseCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Label(title)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = PulseType.Value)
                    Text(unit, style = PulseType.Unit, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                }
            }
            Text(delta.first, style = PulseType.Caption, color = delta.second, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
    if (!last) Spacer(Modifier.height(14.dp))
}

@Composable
private fun ChartAxis(labels: List<String>) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = if (labels.size <= 7) labels else listOf(labels.first(), labels[labels.size / 2], labels.last())
        shown.forEach { Text(it, style = PulseType.Caption.copy(color = PulseColors.TextTertiary), maxLines = 1) }
    }
}

@Composable
private fun Legend(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(name, style = PulseType.Caption, modifier = Modifier.padding(start = 6.dp))
    }
}

private fun dayLabel(day: DayData, range: Int): String =
    if (range <= 7) day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    else "${day.date.dayOfMonth}/${day.date.monthValue}"

private fun delta(current: Double, average: Double, higherIsBetter: Boolean, unit: String): Pair<String, Color> {
    val diff = current - average
    if (abs(diff) < 0.5) return "In line with average" to PulseColors.TextSecondary
    val good = (diff > 0) == higherIsBetter
    val arrow = if (diff > 0) "▲" else "▼"
    return "$arrow ${abs(diff).roundToInt()} $unit vs avg" to (if (good) PulseColors.Green else PulseColors.Yellow)
}
