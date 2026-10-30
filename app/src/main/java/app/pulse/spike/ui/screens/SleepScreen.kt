package app.pulse.spike.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pulse.spike.data.DayData
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.data.formatDuration
import app.pulse.spike.data.sleepAdvice
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.BarChart
import app.pulse.spike.ui.components.DateSelector
import app.pulse.spike.ui.components.EmptyState
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.components.SleepRing
import app.pulse.spike.ui.components.SleepStagesBar
import app.pulse.spike.ui.components.Stat
import app.pulse.spike.ui.components.StatStrip
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import java.time.LocalDate
import java.util.Locale

@Composable
fun SleepScreen(ui: PulseUiState, repo: HealthRepository) {
    val today = LocalDate.now()
    val date = LocalDate.ofEpochDay(ui.epochDay)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Sleep", style = PulseType.Headline)
        Spacer(Modifier.height(20.dp))
        DateSelector(
            date = date,
            canGoNext = date < today,
            onPrev = { ui.epochDay -= 1 },
            onNext = { if (date < today) ui.epochDay += 1 },
        )
        Spacer(Modifier.height(32.dp))
        if (!repo.hasData) {
            EmptyState(
                title = "No sleep data yet",
                message = "Wear your band to sleep. Pulse finds your night from your heart rate and movement.",
                onPreview = { ui.useSample = true },
                onOpenLab = { ui.labOpen = true },
                status = ui.syncStatus,
                onSync = if (ui.bandSaved && !ui.useSample) ui.syncAction else null,
                onAddDevice = if (!ui.bandSaved && !ui.useSample) ({ ui.addDeviceOpen = true }) else null,
            )
        } else {
            val day = remember(date, repo) { repo.day(date) }
            SleepContent(day, repo, date, isToday = date == today)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SleepContent(day: DayData, repo: HealthRepository, date: LocalDate, isToday: Boolean) {
    if (day.hasSleep) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SleepRing(day.sleepPerformance, Modifier.size(290.dp), subtitle = formatDuration(day.sleepMinutes))
        }
        if (day.sleepWindow != null) {
            Spacer(Modifier.height(16.dp))
            Text(day.sleepWindow, style = PulseType.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(32.dp))
        StatStrip(
            listOf(
                Stat("Slept", formatDuration(day.sleepMinutes)),
                Stat("Goal", formatDuration(day.sleepGoalMinutes)),
                Stat("Efficiency", "${day.sleepEfficiency}", "%"),
            ),
        )
        Spacer(Modifier.height(14.dp))
        PulseCard {
            Label("Sleep stages")
            Spacer(Modifier.height(18.dp))
            SleepStagesBar(day.stages)
            if (day.sleepWindow != null) {
                Spacer(Modifier.height(16.dp))
                Text("Estimated from your heart rate and movement, not measured directly.", style = PulseType.Caption)
            }
        }
    } else {
        PulseCard {
            Text(
                "No night found for this day. Wear the band to sleep, then open Pulse in the morning. " +
                    "It needs about 3 hours of heart-rate readings to spot a night.",
                style = PulseType.Body,
            )
        }
    }

    val week = remember(date, repo) { repo.days(date, 7) }
    if (week.any { it.sleepMinutes > 0 }) {
        Spacer(Modifier.height(14.dp))
        PulseCard {
            Label("Last 7 nights")
            Spacer(Modifier.height(18.dp))
            BarChart(
                bars = week.map { listOf(it.stages.deepMin / 60f, it.stages.lightMin / 60f) },
                colors = listOf(PulseColors.StageDeep, PulseColors.StageLight),
                modifier = Modifier.fillMaxWidth().height(140.dp),
                labels = week.map { it.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) },
            )
        }
    }

    val counted = week.filter { it.sleepMinutes > 0 }
    if (counted.size >= 2) {
        val debt = counted.sumOf { maxOf(0, it.sleepGoalMinutes - it.sleepMinutes) }
        Spacer(Modifier.height(14.dp))
        StatStrip(
            listOf(
                Stat("Sleep debt", if (debt == 0) "None" else formatDuration(debt)),
                Stat("Nights", "${counted.size}", "of 7"),
            ),
        )
    }

    if (isToday && day.hasSleep) {
        Spacer(Modifier.height(14.dp))
        PulseCard {
            Label("Tonight")
            Spacer(Modifier.height(14.dp))
            sleepAdvice(day).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp).size(4.dp).clip(CircleShape).background(PulseColors.TextSecondary))
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = PulseType.Body)
                }
            }
        }
    }
}
