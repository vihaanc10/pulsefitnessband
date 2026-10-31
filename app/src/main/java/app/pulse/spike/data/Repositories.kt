package app.pulse.spike.data

import java.time.LocalDate
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The real starting point: no data has come from the band yet. */
class EmptyHealthRepository : HealthRepository {
    override val hasData = false
    override val device = DeviceStatus("G69_9307", connected = false, batteryPercent = 0, lastSync = "Never synced")
    override val user = UserProfile("Vihaan", heightCm = 180.3, weightKg = 72.5, ageYears = 25, male = true)

    override fun day(date: LocalDate) = DayData(
        date = date, recovery = 0, restingHr = 0, hrvMs = null, respiratoryRate = null,
        sleepPerformance = 0, sleepMinutes = 0, sleepGoalMinutes = 480, sleepEfficiency = 0, sleepConsistency = 0,
        stages = SleepStages(0, 0, 0), strain = 0.0, calories = 0, steps = 0, avgHr = 0, maxHr = 0, currentHr = 0,
        heartRate = emptyList(), zoneMinutes = List(5) { 0 },
    )
}

/** Made-up but realistic numbers, so the design can be reviewed. Not the user's real health data. */
class SampleHealthRepository : HealthRepository {
    override val hasData = true
    override val device = DeviceStatus("G69_9307", connected = true, batteryPercent = 65, lastSync = "2 min ago")
    override val user = UserProfile("Vihaan", heightCm = 180.3, weightKg = 72.5, ageYears = 25, male = true)

    override fun day(date: LocalDate): DayData {
        val rnd = Random(date.toEpochDay())
        val isToday = date == LocalDate.now()

        val recovery = if (isToday) 82 else 28 + rnd.nextInt(68)
        val restingHr = 60 - recovery / 12 + rnd.nextInt(-1, 2)
        val hrv = if (isToday) 64 else 36 + recovery / 3 + rnd.nextInt(-4, 5)
        val respiratory = (140 + rnd.nextInt(-8, 9)) / 10.0

        val goal = 480
        val sleepMinutes = if (isToday) 432 else 340 + rnd.nextInt(170)
        val efficiency = 86 + rnd.nextInt(11)
        val consistency = 62 + rnd.nextInt(33)
        val deep = sleepMinutes * (17 + rnd.nextInt(8)) / 100
        val stages = SleepStages(
            awakeMin = sleepMinutes * (4 + rnd.nextInt(5)) / 100,
            lightMin = sleepMinutes - deep,
            deepMin = deep,
        )
        val performance = (0.5 * min(1.0, sleepMinutes / goal.toDouble()) * 100 +
            0.25 * consistency + 0.25 * efficiency).roundToInt()

        val strain = Math.round((if (isToday) 12.4 else 4.0 + rnd.nextDouble() * 13.5) * 10) / 10.0
        val calories = 1850 + (strain * 55).toInt() + rnd.nextInt(120)
        val steps = 3500 + (strain * 520).toInt() + rnd.nextInt(1500)

        val upTo = if (isToday) LocalTime.now().let { it.hour * 60 + it.minute } else 1439
        val series = heartRateSeries(rnd, restingHr, strain, upTo)
        val bpms = series.map { it.bpm }

        val zones = IntArray(5)
        for (bpm in bpms) {
            val fraction = bpm / 190.0
            if (fraction >= 0.5) zones[min(4, ((fraction - 0.5) / 0.1).toInt())] += 5
        }

        val stepWeights = List(24) { if (it in 7..21) 1 + rnd.nextInt(9) else 0 }
        val stepsByHour = stepWeights.map { it * steps / stepWeights.sum() }
        val startEnergy = 0.5 * recovery + 0.5 * performance
        val energySeries = Analysis.energyCurve(series, 7 * 60, startEnergy)

        return DayData(
            date = date, recovery = recovery, restingHr = restingHr, hrvMs = hrv, respiratoryRate = respiratory,
            sleepPerformance = performance, sleepMinutes = sleepMinutes, sleepGoalMinutes = goal,
            sleepEfficiency = efficiency, sleepConsistency = consistency, stages = stages,
            strain = strain, calories = calories, steps = steps,
            avgHr = bpms.average().roundToInt(), maxHr = bpms.max(), currentHr = bpms.last(),
            heartRate = series, zoneMinutes = zones.toList(),
            sleepSpan = 0..(7 * 60),
            energy = energySeries.lastOrNull()?.roundToInt() ?: startEnergy.roundToInt(),
            energySeries = energySeries, hasEnergy = energySeries.isNotEmpty(), stepsByHour = stepsByHour,
        )
    }

    private fun sampleWorkout(id: Long, kind: WorkoutKind, hoursAgo: Int, minutes: Int, km: Double, avg: Int, max: Int, strain: Double, kcal: Int) =
        WorkoutSummary(
            id = id, kind = kind, startMs = System.currentTimeMillis() - hoursAgo * 3_600_000L,
            endMs = System.currentTimeMillis() - hoursAgo * 3_600_000L + minutes * 60_000L, durationMin = minutes,
            distanceM = km * 1000, avgHr = avg, maxHr = max, zoneMinutes = listOf(6, 14, 22, 12, 4), strain = strain,
            calories = kcal, hrSource = "live", hrLoad = 0.0, extraLoad = 0.0, intensity = 2,
        )

    override fun workoutsOn(date: LocalDate): List<WorkoutSummary> =
        if (date == LocalDate.now()) {
            listOf(sampleWorkout(2, WorkoutKind.Basketball, 3, 62, 0.0, 142, 176, 9.6, 640), sampleWorkout(1, WorkoutKind.Biking, 9, 45, 18.4, 131, 158, 7.1, 410))
        } else {
            emptyList()
        }

    override fun recentWorkouts(limit: Int): List<WorkoutSummary> = workoutsOn(LocalDate.now()).take(limit)

    override fun route(workoutId: Long): List<Pair<Double, Double>> =
        if (workoutId == 1L) (0..60).map { i -> val a = i / 60.0 * 6.0; (a * 0.01 + 0.004 * kotlin.math.sin(a * 2)) to (0.012 * kotlin.math.cos(a) + a * 0.003) } else emptyList()

    private fun heartRateSeries(rnd: Random, restingHr: Int, strain: Double, upTo: Int): List<HrPoint> {
        val points = ArrayList<HrPoint>()
        var minute = 0
        while (minute <= upTo) {
            val noise = rnd.nextInt(-3, 4)
            val base = when {
                minute < 390 -> restingHr + 3 + (4 * sin(minute / 37.0)).toInt() // asleep
                minute in 1050..1100 && strain > 8 -> 118 + (strain * 3).toInt() + (6 * sin(minute / 5.0)).toInt() // workout
                else -> 66 + (10 * sin(minute / 90.0)).toInt() // awake
            }
            points.add(HrPoint(minute, (base + noise).coerceIn(40, 190)))
            minute += 5
        }
        return points
    }
}

/** Real data: whatever the band has delivered and the sync has saved in [PulseDb]. */
class RealHealthRepository(private val db: PulseDb) : HealthRepository {
    override val hasData: Boolean get() = db.hasAnyData()

    override val device: DeviceStatus
        get() {
            val battery = db.getMeta("battery")?.toIntOrNull()
            return DeviceStatus(
                name = db.getMeta("band_name") ?: "Your band",
                connected = battery != null,
                batteryPercent = battery ?: 0,
                lastSync = lastSyncText(db.getMeta("last_sync")?.toLongOrNull()),
            )
        }

    override val user: UserProfile get() = db.loadProfile()

    private val nightCache = HashMap<LocalDate, SleepNight?>()

    override fun day(date: LocalDate): DayData {
        val profile = user
        val maxHr = profile.maxHr
        val points = db.heartRate(date)
        val bpms = points.map { it.bpm }
        val (steps, _, _) = db.stepTotals(date)
        val elapsedMinutes = if (date == LocalDate.now()) LocalTime.now().let { it.hour * 60 + it.minute } else 1440
        val calories = Analysis.caloriesEstimate(bpms, elapsedMinutes, profile)

        val zones = IntArray(5)
        for (bpm in bpms) {
            val fraction = bpm / maxHr.toDouble()
            if (fraction >= 0.5) zones[min(4, ((fraction - 0.5) / 0.1).toInt())] += 5
        }

        val goal = 480
        val night = nightFor(date)
        val previous = (1..30).mapNotNull { nightFor(date.minusDays(it.toLong())) }
        val efficiency = if (night == null) 0 else night.asleepMin * 100 / night.inBedMin
        val performance = if (night == null) 0 else {
            (0.6 * min(1.0, night.asleepMin / goal.toDouble()) * 100 + 0.4 * efficiency).roundToInt()
        }
        val consistency = if (night == null || previous.size < 2) 80 else {
            val deviationMin = abs(night.firstSlot - previous.map { it.firstSlot }.average()) * 5
            (100 - deviationMin / 1.8).coerceIn(0.0, 100.0).roundToInt()
        }

        // Recovery needs a personal baseline: 5 nights in total (PRD section 5).
        val recovery = if (night != null && previous.size >= 4) recoveryFor(date, night, previous, performance, maxHr) else null
        // Lifting adds muscular load that heart rate can't see.
        val liftingLoad = workoutsOn(date).filter { it.kind == WorkoutKind.Lifting }.sumOf { it.extraLoad }
        val strain = if (bpms.isEmpty() && liftingLoad == 0.0) 0.0 else Analysis.strainFromHeartRate(bpms, maxHr, liftingLoad)

        // Energy starts from how rested you are at waking up.
        val wakeMinute = if (night != null) (18 * 60 + (night.lastSlot + 1) * 5 - 1440).coerceIn(0, 1439) else 7 * 60
        val startEnergy = when {
            recovery != null -> 0.5 * recovery + 0.5 * performance
            night != null -> performance.toDouble()
            else -> 70.0
        }
        val energySeries = Analysis.energyCurve(points, wakeMinute, startEnergy, maxHr)
        val hours = IntArray(24)
        for ((block, n) in db.stepBlocks(date)) if (block in 0..95) hours[block / 4] += n

        return DayData(
            date = date,
            recovery = recovery ?: 0,
            restingHr = night?.restingHr ?: Analysis.lowestAverage(bpms),
            hrvMs = null, respiratoryRate = null,
            sleepPerformance = performance,
            sleepMinutes = night?.asleepMin ?: 0,
            sleepGoalMinutes = goal,
            sleepEfficiency = efficiency,
            sleepConsistency = consistency,
            stages = SleepStages(night?.awakeMin ?: 0, night?.lightMin ?: 0, night?.deepMin ?: 0),
            strain = strain, calories = calories, steps = steps,
            avgHr = if (bpms.isEmpty()) 0 else bpms.average().roundToInt(),
            maxHr = bpms.maxOrNull() ?: 0, currentHr = bpms.lastOrNull() ?: 0,
            heartRate = points, zoneMinutes = zones.toList(),
            hasSleep = night != null, hasRecovery = recovery != null, hasStrain = bpms.isNotEmpty() || liftingLoad > 0.0,
            nightsOfData = previous.size + if (night != null) 1 else 0,
            sleepWindow = night?.let {
                "${formatClock(18 * 60 + it.firstSlot * 5)} to ${formatClock(18 * 60 + (it.lastSlot + 1) * 5)}"
            },
            sleepSpan = night?.let {
                val from = (18 * 60 + it.firstSlot * 5 - 1440).coerceAtLeast(0)
                val to = (18 * 60 + (it.lastSlot + 1) * 5 - 1440).coerceAtMost(1440)
                if (to > from) from..to else null
            },
            energy = energySeries.lastOrNull()?.roundToInt() ?: startEnergy.roundToInt(),
            energySeries = energySeries,
            hasEnergy = energySeries.isNotEmpty(),
            stepsByHour = hours.toList(),
            sleepFirstSlot = night?.firstSlot,
            sleepLastSlot = night?.lastSlot,
            sleepEdited = db.getMeta("sleep_override_$date") != null,
        )
    }

    override fun workoutsOn(date: LocalDate): List<WorkoutSummary> {
        val zone = java.time.ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val profile = user
        return db.workoutsBetween(from, to).map { WorkoutStats.summarize(db, it, profile) }
    }

    override fun recentWorkouts(limit: Int): List<WorkoutSummary> {
        val profile = user
        return db.recentWorkouts(limit).map { WorkoutStats.summarize(db, it, profile) }
    }

    override fun route(workoutId: Long): List<Pair<Double, Double>> = db.routeOf(workoutId)

    /** The night that ended on the morning of [date], or null if the readings don't show one. */
    private fun nightFor(date: LocalDate): SleepNight? {
        if (nightCache.containsKey(date)) return nightCache[date]
        val night = computeNight(date)
        nightCache[date] = night
        return night
    }

    private fun computeNight(date: LocalDate): SleepNight? {
        val hr = IntArray(Analysis.WINDOW_SLOTS)
        for (p in db.heartRate(date.minusDays(1))) {
            val slot = p.minuteOfDay / 5 - 216 // evening of the day before, from 18:00
            if (slot in 0..71) hr[slot] = p.bpm
        }
        for (p in db.heartRate(date)) {
            val slot = 72 + p.minuteOfDay / 5 // morning, up to 12:00
            if (slot in 72 until Analysis.WINDOW_SLOTS) hr[slot] = p.bpm
        }
        val steps = DoubleArray(Analysis.WINDOW_SLOTS)
        for ((block, n) in db.stepBlocks(date.minusDays(1))) {
            if (block in 72..95) for (k in 0..2) steps[(block - 72) * 3 + k] = n / 3.0
        }
        for ((block, n) in db.stepBlocks(date)) {
            if (block in 0..59) for (k in 0..2) steps[72 + block * 3 + k] = n / 3.0
        }
        val manual = db.getMeta("sleep_override_$date")?.split(",")?.mapNotNull { it.toIntOrNull() }
        if (manual != null && manual.size == 2) {
            Analysis.nightFromWindow(hr, steps, manual[0], manual[1])?.let { return it }
        }
        return Analysis.detectSleep(hr, steps)
    }

    /**
     * PRD section 5, with the inputs we don't have (SpO2 and the "how do I feel" slider) left out
     * and the weights shared out again: resting heart rate vs your baseline 50%, sleep 37.5%, yesterday's strain 12.5%.
     */
    private fun recoveryFor(date: LocalDate, night: SleepNight, previous: List<SleepNight>, performance: Int, maxHr: Int): Int {
        val baseline = previous.map { it.restingHr }.average()
        val restingScore = (70 + (baseline - night.restingHr) * 10).coerceIn(0.0, 100.0)
        val yesterdayStrain = Analysis.strainFromHeartRate(db.heartRate(date.minusDays(1)).map { it.bpm }, maxHr)
        val strainScore = (100 - yesterdayStrain / 21 * 60).coerceIn(0.0, 100.0)
        return (0.5 * restingScore + 0.375 * performance + 0.125 * strainScore).roundToInt()
    }

    private fun lastSyncText(millis: Long?): String {
        if (millis == null) return "Never synced"
        val minutes = Duration.ofMillis(System.currentTimeMillis() - millis).toMinutes()
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> "Over a day ago"
        }
    }
}
