package app.pulse.spike.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.data.WorkoutKind
import app.pulse.spike.data.WorkoutSummary
import app.pulse.spike.data.WorkoutTracker
import app.pulse.spike.data.formatStrain
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.PulseButton
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.components.PulseTab
import app.pulse.spike.ui.components.SegmentedControl
import app.pulse.spike.ui.components.TitleRow
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

@Composable
fun WorkoutsScreen(ui: PulseUiState, repo: HealthRepository) {
    val context = LocalContext.current
    var pendingKind by remember { mutableStateOf<WorkoutKind?>(null) }
    var askedKind by remember { mutableStateOf<WorkoutKind?>(null) }
    var intensity by remember { mutableIntStateOf(2) }

    // GPS workouts need the phone's location. Start the workout whatever the answer is; without it, there is no distance.
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedKind?.let { ui.startWorkout?.invoke(it, intensity) }
        askedKind = null
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        TitleRow("Workouts", repo.user.name.take(1).uppercase(), { ui.tab = PulseTab.Profile })
        Spacer(Modifier.height(24.dp))

        val active = ui.activeWorkout
        if (active != null) {
            ActiveWorkoutPanel(ui, active.kind, active.startMs, ui.metricUnits)
        } else {
            Text("What are you about to do? Pulse tracks it and adds it to your strain.", style = PulseType.Body)
            Spacer(Modifier.height(20.dp))
            WorkoutKind.entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { kind ->
                        PulseCard(Modifier.weight(1f).clickable { pendingKind = kind; intensity = 2 }) {
                            Text(kind.label, style = PulseType.Title)
                            Spacer(Modifier.height(4.dp))
                            Text(if (kind.usesGps) "Phone GPS" else "Heart rate", style = PulseType.Caption)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            val recent = remember(repo) { repo.recentWorkouts(8) }
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Label("Recent workouts")
                Spacer(Modifier.height(14.dp))
                recent.forEach { w ->
                    WorkoutCard(w, repo.route(w.id), ui.metricUnits)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    pendingKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { pendingKind = null },
            title = { Text("Start ${kind.label}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (kind.usesGps) {
                            "Uses your phone's GPS for distance and route, because this band has no GPS. Keep the phone with you."
                        } else {
                            "Heart rate comes from your band."
                        },
                    )
                    if (kind.note.isNotBlank()) Text(kind.note, style = PulseType.Caption)
                    if (kind == WorkoutKind.Lifting) {
                        Text("How hard will it be?", style = PulseType.Caption)
                        SegmentedControl(listOf("Light", "Moderate", "Hard"), intensity - 1, { intensity = it + 1 })
                    }
                    Text("Keep your band close to the phone so heart rate can be recorded.", style = PulseType.Caption)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingKind = null
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (kind.usesGps && !granted) {
                        askedKind = kind
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        ui.startWorkout?.invoke(kind, intensity)
                    }
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { pendingKind = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ActiveWorkoutPanel(ui: PulseUiState, kind: WorkoutKind, startMs: Long, metric: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }
    val tracker by WorkoutTracker.state.collectAsStateWithLifecycle()
    val seconds = max(0L, (now - startMs) / 1000).toInt()
    val clock = if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%d:%02d".format(seconds / 60, seconds % 60)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("${kind.label} in progress", color = PulseColors.Green)
        Spacer(Modifier.height(20.dp))
        Text(clock, style = PulseType.Display)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat("Heart rate", if (tracker.liveHr > 0) "${tracker.liveHr} bpm" else "--")
            if (kind.usesGps) MiniStat("Distance", distanceText(tracker.distanceM, metric))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                kind.usesGps && !tracker.gpsFix -> "Looking for a GPS signal..."
                tracker.liveHr == 0 && seconds > 45 ->
                    "Live heart rate isn't coming through. Pulse will use the band's heart-rate log when you finish."
                else -> "Pulse is recording. Keep the band on and the phone close."
            },
            style = PulseType.Caption, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        PulseButton("End workout", onClick = { ui.stopWorkout?.invoke() })
        Spacer(Modifier.height(12.dp))
        PulseButton("Discard", onClick = { ui.discardWorkout?.invoke() }, filled = false)
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = PulseType.Value)
    }
}

private fun distanceText(meters: Double, metric: Boolean): String =
    if (metric) String.format(Locale.US, "%.2f km", meters / 1000.0) else String.format(Locale.US, "%.2f mi", meters / 1609.344)

private fun whenText(startMs: Long): String =
    Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.getDefault()))

/** One line for the Home screen. */
@Composable
fun WorkoutLine(w: WorkoutSummary, metric: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(w.kind.label, style = PulseType.Title)
            Text("strain ${formatStrain(w.strain)}", style = PulseType.Caption)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            buildString {
                append("${w.durationMin} min")
                if (w.kind.usesGps && w.distanceM > 0) append("  ·  ${distanceText(w.distanceM, metric)}")
                if (w.avgHr > 0) append("  ·  avg ${w.avgHr} bpm")
                append("  ·  ${w.calories} kcal")
            },
            style = PulseType.Caption,
        )
    }
}

@Composable
fun WorkoutCard(w: WorkoutSummary, route: List<Pair<Double, Double>>, metric: Boolean) {
    PulseCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(w.kind.label, style = PulseType.Title)
                Spacer(Modifier.height(4.dp))
                Text(whenText(w.startMs), style = PulseType.Caption)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatStrain(w.strain), style = PulseType.Value)
                Label("strain")
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("Time", "${w.durationMin} min")
            MiniStat("Avg HR", if (w.avgHr > 0) "${w.avgHr}" else "--")
            MiniStat("Max HR", if (w.maxHr > 0) "${w.maxHr}" else "--")
            MiniStat("Calories", "${w.calories}")
        }
        if (w.kind.usesGps && w.distanceM > 0) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MiniStat("Distance", distanceText(w.distanceM, metric))
                MiniStat(paceLabel(w), paceText(w, metric))
            }
        }
        if (route.size >= 2) {
            Spacer(Modifier.height(18.dp))
            RouteSketch(route, Modifier.fillMaxWidth().height(130.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                w.hrSource == "none" -> "Heart rate for this workout will appear after the next sync."
                w.extraLoad > 0 -> "Includes muscular load for lifting. Heart rate: ${w.hrSource}."
                else -> "Heart rate: ${w.hrSource}."
            },
            style = PulseType.Caption,
        )
    }
}

private fun paceLabel(w: WorkoutSummary) = if (w.kind == WorkoutKind.Biking) "Speed" else "Pace"

private fun paceText(w: WorkoutSummary, metric: Boolean): String {
    val km = w.distanceM / 1000.0
    if (km <= 0.01) return "--"
    val distance = if (metric) km else w.distanceM / 1609.344
    if (w.kind == WorkoutKind.Biking) {
        return String.format(Locale.US, "%.1f %s", distance / (w.durationMin / 60.0), if (metric) "km/h" else "mph")
    }
    val secondsPer = w.durationMin * 60.0 / distance
    return "%d:%02d %s".format((secondsPer / 60).toInt(), (secondsPer % 60).toInt(), if (metric) "/km" else "/mi")
}

/** A simple outline of the route (no map needed). */
@Composable
private fun RouteSketch(route: List<Pair<Double, Double>>, modifier: Modifier) {
    Canvas(modifier) {
        val lats = route.map { it.first }
        val lons = route.map { it.second }
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLon = lons.min()
        val maxLon = lons.max()
        val midLat = (minLat + maxLat) / 2
        val spanX = max((maxLon - minLon) * cos(midLat * PI / 180), 1e-6)
        val spanY = max(maxLat - minLat, 1e-6)
        val scale = min(size.width / spanX, size.height / spanY).toFloat() * 0.9f
        val offsetX = (size.width - (spanX * scale).toFloat()) / 2f
        val offsetY = (size.height - (spanY * scale).toFloat()) / 2f
        fun point(lat: Double, lon: Double) = Offset(
            offsetX + ((lon - minLon) * cos(midLat * PI / 180) * scale).toFloat(),
            offsetY + ((maxLat - lat) * scale).toFloat(),
        )
        val path = Path()
        route.forEachIndexed { i, (lat, lon) ->
            val p = point(lat, lon)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, PulseColors.Green, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val start = point(route.first().first, route.first().second)
        val end = point(route.last().first, route.last().second)
        drawCircle(PulseColors.TextSecondary, 4.dp.toPx(), start)
        drawCircle(PulseColors.TextPrimary, 5.dp.toPx(), end)
    }
}
