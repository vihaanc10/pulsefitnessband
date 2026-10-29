package app.pulse.spike.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.data.DayData
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.data.coachAnswers
import app.pulse.spike.data.formatStrain
import app.pulse.spike.data.formatThousands
import app.pulse.spike.data.recoveryAdvice
import app.pulse.spike.data.recoverySummary
import app.pulse.spike.data.sleepAdvice
import app.pulse.spike.data.strainTarget
import app.pulse.spike.data.suggestedBedtime
import app.pulse.spike.data.workoutFor
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.EmptyState
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.Pill
import app.pulse.spike.ui.components.PulseButton
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import app.pulse.spike.ui.theme.recoveryColor
import java.time.LocalDate

@Composable
fun CoachScreen(ui: PulseUiState, repo: HealthRepository) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Coach", style = PulseType.Headline)
            Spacer(Modifier.width(12.dp))
            Pill("Preview")
        }
        Spacer(Modifier.height(24.dp))
        if (!repo.hasData) {
            EmptyState(
                title = "Your coach needs data",
                message = "Recommendations are built from your recovery, sleep and strain once your band has synced.",
                onPreview = { ui.useSample = true },
                onOpenLab = { ui.labOpen = true },
            )
        } else {
            CoachContent(ui, repo.day(LocalDate.now()))
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun CoachContent(ui: PulseUiState, day: DayData) {
    val workout = workoutFor(day.recovery)
    val target = strainTarget(day.recovery)
    val workoutAdded = ui.goalDone["workout_added"] == true

    PulseCard {
        Label("Today's focus", color = recoveryColor(day.recovery))
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                day.recovery >= 67 -> "Push today"
                day.recovery >= 34 -> "Steady day"
                else -> "Recover"
            },
            style = PulseType.Headline,
        )
        Spacer(Modifier.height(8.dp))
        Text(recoverySummary(day.recovery), style = PulseType.Body)
    }
    Spacer(Modifier.height(14.dp))

    PulseCard {
        Label("Suggested workout")
        Spacer(Modifier.height(12.dp))
        Text(workout.title, style = PulseType.Headline.copy(fontSize = 24.sp))
        Spacer(Modifier.height(6.dp))
        Text(workout.detail, style = PulseType.Body)
        Spacer(Modifier.height(14.dp))
        Text(
            "${workout.minutes} min  /  ${workout.zones}  /  strain ${formatStrain(target.start)}-${formatStrain(target.endInclusive)}",
            style = PulseType.Caption,
        )
        Spacer(Modifier.height(18.dp))
        PulseButton(
            text = if (workoutAdded) "Added to today's goals" else "Add to today's goals",
            onClick = { ui.goalDone["workout_added"] = !workoutAdded },
            filled = !workoutAdded,
        )
    }
    Spacer(Modifier.height(14.dp))

    PulseCard {
        Label("Recovery")
        Spacer(Modifier.height(14.dp))
        recoveryAdvice(day).forEach { Bullet(it) }
    }
    Spacer(Modifier.height(14.dp))

    PulseCard {
        Label("Sleep tonight")
        Spacer(Modifier.height(14.dp))
        sleepAdvice(day).forEach { Bullet(it) }
    }
    Spacer(Modifier.height(14.dp))

    PulseCard {
        Label("Daily goals")
        Spacer(Modifier.height(10.dp))
        ProgressGoal("Steps", "${formatThousands(day.steps)} / 10,000", day.steps / 10000f)
        ProgressGoal("Strain", "${formatStrain(day.strain)} / ${formatStrain(target.start)}", (day.strain / target.start).toFloat())
        CheckGoal(ui, "bed", "In bed by ${suggestedBedtime(day)}", "For a 6:30 am wake-up")
        CheckGoal(ui, "water", "Drink 3 litres of water", "Spread through the day")
        CheckGoal(ui, "winddown", "10 minutes screen-free wind-down", "Before you sleep")
        if (workoutAdded) CheckGoal(ui, "workout_done", "Workout: ${workout.title}", "${workout.minutes} min, ${workout.zones}")
    }
    Spacer(Modifier.height(30.dp))

    Label("Ask the coach")
    Spacer(Modifier.height(12.dp))
    val answers = coachAnswers(day)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        answers.forEachIndexed { index, (question, _) ->
            val selected = ui.coachQuestion == index
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.clip(shape)
                    .background(if (selected) PulseColors.CardRaised else Color.Transparent)
                    .border(1.dp, PulseColors.Border, shape)
                    .clickable { ui.coachQuestion = if (selected) null else index }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    question, style = PulseType.Body.copy(fontSize = 13.sp),
                    color = if (selected) PulseColors.TextPrimary else PulseColors.TextSecondary, maxLines = 1,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.animateContentSize()) {
        val open = ui.coachQuestion
        if (open != null && open in answers.indices) {
            PulseCard { Text(answers[open].second, style = PulseType.Body) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Sample answers. A real coach arrives later and only runs if you turn it on.", style = PulseType.Caption)
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 8.dp).size(4.dp).clip(CircleShape).background(PulseColors.TextSecondary))
        Spacer(Modifier.width(12.dp))
        Text(text, style = PulseType.Body)
    }
}

@Composable
private fun ProgressGoal(title: String, detail: String, fraction: Float) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "goal")
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = PulseType.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp))
            Text(detail, style = PulseType.Caption)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.Track)) {
            Box(
                Modifier.fillMaxWidth(animated).fillMaxHeight().background(
                    if (fraction >= 1f) PulseColors.Green else PulseColors.TextPrimary,
                ),
            )
        }
    }
}

@Composable
private fun CheckGoal(ui: PulseUiState, key: String, title: String, subtitle: String) {
    val checked = ui.goalDone[key] == true
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { ui.goalDone[key] = !checked }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .border(1.5.dp, if (checked) PulseColors.Green else PulseColors.TextTertiary, CircleShape)
                .background(if (checked) PulseColors.Green else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = PulseType.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp))
            Text(subtitle, style = PulseType.Caption)
        }
    }
}
