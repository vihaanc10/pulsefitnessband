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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
            )
        } else {
            val day = remember(date, repo) { repo.day(date) }
            HomeContent(day)
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun HomeContent(day: DayData) {
    if (day.hasScores) {
        // Recovery
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RecoveryRing(day.recovery, Modifier.size(290.dp))
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Label(recoveryLabel(day.recovery), color = recoveryColor(day.recovery))
            Spacer(Modifier.height(8.dp))
            Text(
                recoverySummary(day.recovery), style = PulseType.Body, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        StatStrip(
            listOf(
                Stat("Resting HR", "${day.restingHr}", "bpm"),
                Stat("HRV", day.hrvMs?.toString() ?: "--", "ms"),
                Stat("Resp. rate", day.respiratoryRate?.let { String.format(Locale.US, "%.1f", it) } ?: "--", "rpm"),
            ),
        )

        // Sleep
        Spacer(Modifier.height(72.dp))
        Label("Sleep")
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SleepRing(day.sleepPerformance, Modifier.size(250.dp), subtitle = formatDuration(day.sleepMinutes))
        }
        Spacer(Modifier.height(28.dp))
        StatStrip(
            listOf(
                Stat("Slept", formatDuration(day.sleepMinutes)),
                Stat("Goal", formatDuration(day.sleepGoalMinutes)),
                Stat("Efficiency", "${day.sleepEfficiency}", "%"),
            ),
        )
        Spacer(Modifier.height(16.dp))
        PulseCard {
            Label("Sleep stages")
            Spacer(Modifier.height(18.dp))
            SleepStagesBar(day.stages)
        }

        // Strain
        Spacer(Modifier.height(72.dp))
        Label("Strain")
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StrainRing(day.strain, Modifier.size(220.dp))
        }
        Spacer(Modifier.height(14.dp))
        val target = strainTarget(day.recovery)
        Text(
            "Target ${formatStrain(target.start)} to ${formatStrain(target.endInclusive)} for this recovery",
            style = PulseType.Caption, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        StatStrip(
            listOf(
                Stat("Calories", formatThousands(day.calories), "kcal"),
                Stat("Steps", formatThousands(day.steps)),
                Stat("Avg HR", "${day.avgHr}", "bpm"),
            ),
        )

    } else {
        Label("Today so far")
        Spacer(Modifier.height(16.dp))
        StatStrip(
            listOf(
                Stat("Steps", formatThousands(day.steps)),
                Stat("Calories", formatThousands(day.calories)),
                Stat("Avg HR", if (day.avgHr > 0) "${day.avgHr}" else "--", "bpm"),
            ),
        )
        Spacer(Modifier.height(16.dp))
        PulseCard {
            Label("Coming next")
            Spacer(Modifier.height(10.dp))
            Text(
                "Sleep, recovery and strain scores need the band's sleep data. We are still working out how to read it.",
                style = PulseType.Body,
            )
        }
    }

    // Heart rate
    Spacer(Modifier.height(72.dp))
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
private fun BpmValue(bpm: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$bpm", style = PulseType.Value)
        Text("bpm", style = PulseType.Unit, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}
