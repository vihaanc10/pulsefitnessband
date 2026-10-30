package app.pulse.spike.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The activities you can start from the Workouts tab. [met] is a rough effort rating used only if no heart rate is available. */
enum class WorkoutKind(val label: String, val usesGps: Boolean, val met: Double, val note: String) {
    Running("Running", true, 9.8, ""),
    Jogging("Jogging", true, 7.0, ""),
    Walking("Walking", true, 3.5, ""),
    Biking("Biking", true, 7.5, ""),
    Basketball("Basketball", false, 6.5, ""),
    Swimming("Swimming", false, 6.0, "Optical heart rate can be unreliable in water. Check your band's water rating before you swim."),
    Lifting("Lifting", false, 4.0, "Heart rate under-counts lifting, so Pulse adds muscular load based on how hard you say it was."),
}

data class Workout(
    val id: Long,
    val kind: WorkoutKind,
    val startMs: Long,
    val endMs: Long, // 0 while the workout is still running
    val distanceM: Double,
    val intensity: Int, // 1 light, 2 moderate, 3 hard (used for lifting)
)

/** The workout that is running right now. */
data class ActiveWorkout(val id: Long, val kind: WorkoutKind, val startMs: Long, val intensity: Int)

data class WorkoutSummary(
    val id: Long,
    val kind: WorkoutKind,
    val startMs: Long,
    val endMs: Long,
    val durationMin: Int,
    val distanceM: Double,
    val avgHr: Int,
    val maxHr: Int,
    val zoneMinutes: List<Int>,
    val strain: Double,
    val calories: Int,
    /** "live" (heart rate streamed from the band), "band log" (its 5-minute log) or "none" (not synced yet). */
    val hrSource: String,
    val hrLoad: Double,
    val extraLoad: Double,
    val intensity: Int,
)

object WorkoutStats {

    fun summarize(db: PulseDb, w: Workout, user: UserProfile): WorkoutSummary {
        val maxHr = user.maxHr
        val durationMin = max(1L, (w.endMs - w.startMs) / 60_000L).toInt()
        val zones = DoubleArray(5)
        val bpms = ArrayList<Int>()
        var hrLoad = 0.0
        var kcal = 0.0
        var coveredMin = 0.0

        fun add(bpm: Int, minutes: Double) {
            val fraction = bpm / maxHr.toDouble()
            if (fraction >= 0.5) zones[min(4, ((fraction - 0.5) / 0.1).toInt())] += minutes
            hrLoad += minutes * Analysis.zoneWeight(fraction)
            kcal += minutes * Analysis.kcalPerMinute(bpm, user)
            coveredMin += minutes
            bpms.add(bpm)
        }

        var source = "none"
        val live = db.liveHr(w.id)
        if (live.size >= 5) {
            source = "live"
            for (i in live.indices) {
                val next = if (i + 1 < live.size) live[i + 1].first else w.endMs
                add(live[i].second, min(30_000L, max(0L, next - live[i].first)) / 60_000.0)
            }
        } else {
            // Fall back to the band's own heart-rate log: one reading per 5-minute slot.
            val cache = HashMap<LocalDate, Map<Int, Int>>()
            var t = w.startMs - w.startMs % 300_000L
            while (t <= w.endMs) {
                val local = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
                val slots = cache.getOrPut(local.toLocalDate()) { db.heartRate(local.toLocalDate()).associate { it.minuteOfDay / 5 to it.bpm } }
                slots[(local.hour * 60 + local.minute) / 5]?.let { add(it, 5.0) }
                t += 300_000L
            }
            if (bpms.isNotEmpty()) source = "band log"
        }

        // Lifting: heart rate under-counts it, so the effort you rate sets a minimum load.
        var extra = 0.0
        if (w.kind == WorkoutKind.Lifting) {
            val k = when (w.intensity) {
                1 -> 0.7
                3 -> 1.4
                else -> 1.0
            }
            extra = max(0.0, durationMin * 2.0 * k - hrLoad)
        }

        val calories = when {
            source == "none" -> (w.kind.met * user.weightKg * durationMin / 60.0).roundToInt()
            coveredMin < durationMin -> (kcal * min(4.0, durationMin / max(1.0, coveredMin))).roundToInt()
            else -> kcal.roundToInt()
        }
        return WorkoutSummary(
            id = w.id, kind = w.kind, startMs = w.startMs, endMs = w.endMs, durationMin = durationMin,
            distanceM = w.distanceM,
            avgHr = if (bpms.isEmpty()) 0 else bpms.average().roundToInt(),
            maxHr = bpms.maxOrNull() ?: 0,
            zoneMinutes = zones.map { it.roundToInt() },
            strain = Analysis.strainFromLoad(hrLoad + extra),
            calories = calories, hrSource = source, hrLoad = hrLoad, extraLoad = extra, intensity = w.intensity,
        )
    }
}

/** What the GPS and live heart rate are doing during a workout, shared between the service and the screen. */
object WorkoutTracker {
    data class State(val distanceM: Double = 0.0, val liveHr: Int = 0, val speedMps: Double = 0.0, val gpsFix: Boolean = false)

    val state = kotlinx.coroutines.flow.MutableStateFlow(State())

    fun reset() {
        state.value = State()
    }
}

/** True while a workout owns the band connection, so background syncs keep out of the way. */
object BandLock {
    @Volatile
    var workoutActive = false
}
