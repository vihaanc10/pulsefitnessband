package app.pulse.spike.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The real starting point: no data has come from the band yet. */
class EmptyHealthRepository : HealthRepository {
    override val hasData = false
    override val device = DeviceStatus("G69_9307", connected = false, batteryPercent = 0, lastSync = "Never synced")
    override val user = UserProfile("Vihaan", heightCm = 175, weightKg = 70)

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
    override val user = UserProfile("Vihaan", heightCm = 175, weightKg = 70)

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

        return DayData(
            date = date, recovery = recovery, restingHr = restingHr, hrvMs = hrv, respiratoryRate = respiratory,
            sleepPerformance = performance, sleepMinutes = sleepMinutes, sleepGoalMinutes = goal,
            sleepEfficiency = efficiency, sleepConsistency = consistency, stages = stages,
            strain = strain, calories = calories, steps = steps,
            avgHr = bpms.average().roundToInt(), maxHr = bpms.max(), currentHr = bpms.last(),
            heartRate = series, zoneMinutes = zones.toList(),
        )
    }

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
