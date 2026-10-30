package app.pulse.spike.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.pulse.spike.ui.components.ProgressRing
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.strainColor
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.data.DayData
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.data.formatDuration
import app.pulse.spike.data.formatStrain
import app.pulse.spike.data.formatThousands
import app.pulse.spike.data.recoveryLabel
import app.pulse.spike.data.recoverySummary
import app.pulse.spike.data.strainTarget
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.DashboardHeader
import app.pulse.spike.ui.components.DayAxisLabels
import app.pulse.spike.ui.components.EmptyState
import app.pulse.spike.ui.components.HeartRateChart
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.components.PulseTab
import app.pulse.spike.ui.components.RecoveryRing
import app.pulse.spike.ui.components.SleepRing
import app.pulse.spike.ui.components.SleepStagesBar
import app.pulse.spike.ui.components.Stat
import app.pulse.spike.ui.components.StatStrip
import app.pulse.spike.ui.components.StrainRing
import app.pulse.spike.ui.components.ZoneRow
import app.pulse.spike.ui.theme.PulseType
import app.pulse.spike.ui.theme.recoveryColor
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun HomeScreen(ui: PulseUiState, repo: HealthRepository) {
    val today = LocalDate.now()
    val date = LocalDate.ofEpochDay(ui.epochDay)
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        DashboardHeader(
            date = date,
            greeting = "$greeting, ${repo.user.name}",
            initial = repo.user.name.take(1).uppercase(),
            device = repo.device,
            isSample = ui.useSample,
            syncing = ui.syncing,
            canGoNext = date < today,
            onPrev = { ui.epochDay -= 1 },
            onNext = { if (date < today) ui.epochDay += 1 },
            onProfile = { ui.tab = PulseTab.Profile },
        )
        Spacer(Modifier.height(40.dp))
        if (!repo.hasData) {
            EmptyState(
                title = "Waiting for your band",
                message = "Your recovery, sleep, strain and heart rate will appear here once the band has synced.",
                onPreview = { ui.useSample = true },
                onOpenLab = { ui.labOpen = true },
                status = ui.syncStatus,
                onSync = if (ui.bandSaved && !ui.useSample) ui.syncAction else null,
                onAddDevice = if (!ui.bandSaved && !ui.useSample) ({ ui.addDeviceOpen = true }) else null,
            )
        } else {
            val day = remember(date, repo) { repo.day(date) }
            HomeContent(day, ui)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HomeContent(day: DayData, ui: PulseUiState) {
    // The three rings, like the WHOOP home screen: sleep, recovery, strain.
    SummaryRings(day, onOpenSleep = { ui.tab = PulseTab.Sleep })
    Spacer(Modifier.height(32.dp))

    PulseCard {
        Label("Today's outlook", color = if (day.hasRecovery) recoveryColor(day.recovery) else PulseColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Text(
            if (day.hasRecovery) {
                val target = strainTarget(day.recovery)
                recoverySummary(day.recovery) + " Aim for a strain of ${formatStrain(target.start)} to ${formatStrain(target.endInclusive)}."
            } else {
                "Keep wearing your band, day and night. Recovery and your daily advice unlock after 5 nights of data."
            },
            style = PulseType.Body,
        )
    }

    // Recovery
    Spacer(Modifier.height(64.dp))
    if (day.hasRecovery) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RecoveryRing(day.recovery, Modifier.size(290.dp))
        }
        Spacer(Modifier.height(24.dp))
        Label(recoveryLabel(day.recovery), color = recoveryColor(day.recovery), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
        StatStrip(
            listOf(
                Stat("Resting HR", "${day.restingHr}", "bpm"),
                Stat("HRV", day.hrvMs?.toString() ?: "--", "ms"),
                Stat("Resp. rate", day.respiratoryRate?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "rpm"),
            ),
        )
    } else {
        Label("Recovery")
        Spacer(Modifier.height(16.dp))
        PulseCard {
            Text("Calibrating", style = PulseType.Headline.copy(fontSize = 24.sp))
            Spacer(Modifier.height(10.dp))
            Text(
                "Recovery is measured against your own normal, so it unlocks after 5 nights. " +
                    "Nights found so far: ${minOf(day.nightsOfData, 5)} of 5.",
                style = PulseType.Body,
            )
            if (day.hasSleep) {
                Spacer(Modifier.height(18.dp))
                Label("Resting heart rate last night")
                Spacer(Modifier.height(8.dp))
                BpmValue(day.restingHr)
            }
        }
    }

    // Strain
    Spacer(Modifier.height(64.dp))
    Label("Strain")
    Spacer(Modifier.height(24.dp))
    if (day.hasStrain) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StrainRing(day.strain, Modifier.size(230.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
    StatStrip(
        listOf(
            Stat("Calories", formatThousands(day.calories)),
            Stat("Avg HR", if (day.avgHr > 0) "${day.avgHr}" else "--", "bpm"),
            Stat("Max HR", if (day.maxHr > 0) "${day.maxHr}" else "--", "bpm"),
        ),
    )

    // Steps
    Spacer(Modifier.height(64.dp))
    Label("Steps")
    Spacer(Modifier.height(16.dp))
    PulseCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatThousands(day.steps), style = PulseType.Display)
            Text("of 10,000", style = PulseType.Unit, modifier = Modifier.padding(start = 10.dp, bottom = 10.dp))
        }
        Spacer(Modifier.height(18.dp))
        GoalBar(day.steps / 10000f)
    }

    // Heart rate
    Spacer(Modifier.height(64.dp))
    Label("Heart rate")
    Spacer(Modifier.height(16.dp))
    PulseCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Label("Latest")
                Spacer(Modifier.height(8.dp))
                BpmValue(day.currentHr)
            }
            Column(horizontalAlignment = Alignment.End) {
                Label("Resting")
                Spacer(Modifier.height(8.dp))
                BpmValue(day.restingHr)
            }
        }
        Spacer(Modifier.height(20.dp))
        HeartRateChart(day.heartRate, day.restingHr, Modifier.fillMaxWidth().height(150.dp))
        Spacer(Modifier.height(8.dp))
        DayAxisLabels()
        Spacer(Modifier.height(26.dp))
        Label("Time in zones")
        Spacer(Modifier.height(8.dp))
        val longest = day.zoneMinutes.maxOrNull() ?: 0
        day.zoneMinutes.forEachIndexed { index, minutes -> ZoneRow(index + 1, minutes, longest) }
    }
}

@Composable
private fun SummaryRings(day: DayData, onOpenSleep: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MiniRing(
            label = "Sleep",
            progress = if (day.hasSleep) day.sleepPerformance / 100f else 0f,
            color = PulseColors.Sleep,
            text = { f -> if (day.hasSleep) "${(f * 100).roundToInt()}%" else "--" },
            modifier = Modifier.clickable(onClick = onOpenSleep),
        )
        MiniRing(
            label = "Recovery",
            progress = if (day.hasRecovery) day.recovery / 100f else 0f,
            color = if (day.hasRecovery) recoveryColor(day.recovery) else PulseColors.Green,
            text = { f -> if (day.hasRecovery) "${(f * 100).roundToInt()}%" else "--" },
        )
        MiniRing(
            label = "Strain",
            progress = if (day.hasStrain) (day.strain / 21.0).toFloat() else 0f,
            color = strainColor(day.strain),
            text = { f -> if (day.hasStrain) formatStrain(f * 21.0) else "--" },
        )
    }
}

@Composable
private fun MiniRing(label: String, progress: Float, color: Color, text: (Float) -> String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ProgressRing(progress = progress, color = color, modifier = Modifier.size(104.dp), strokeWidth = 3.5.dp) { fraction ->
            Text(text(fraction), style = PulseType.Value.copy(fontSize = 25.sp))
        }
        Spacer(Modifier.height(12.dp))
        Label(label)
    }
}

@Composable
private fun GoalBar(fraction: Float) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1000), label = "steps")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.Track)) {
        Box(
            Modifier.fillMaxWidth(animated).fillMaxHeight()
                .background(if (fraction >= 1f) PulseColors.Green else PulseColors.TextPrimary),
        )
    }
}

@Composable
private fun BpmValue(bpm: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$bpm", style = PulseType.Value)
        Text("bpm", style = PulseType.Unit, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}
