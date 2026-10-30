package app.pulse.spike.data

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One night of sleep, estimated from heart rate and movement (the band's own sleep data is not
 * decoded yet). Slots are 5 minutes long, counted from 18:00 the evening before.
 */
data class SleepNight(
    val firstSlot: Int,
    val lastSlot: Int,
    val asleepMin: Int,
    val awakeMin: Int,
    val lightMin: Int,
    val deepMin: Int,
    val restingHr: Int,
    val coveragePercent: Int, // share of the night that really had a heart-rate reading
) {
    val inBedMin: Int get() = (lastSlot - firstSlot + 1) * 5
}

/** Our own first-guess formulas. They are meant to be tuned against real nights. */
object Analysis {
    /** 18:00 the evening before up to 15:00 the next day, in 5-minute slots. */
    const val WINDOW_SLOTS = 252

    /**
     * [hr] has one reading per slot (0 = none). [stepsPerSlot] is the steps taken in each slot.
     * Sleep = the longest stretch of low, steady heart rate with no movement, joined across gaps of up to 30 minutes.
     */
    fun detectSleep(hr: IntArray, stepsPerSlot: DoubleArray): SleepNight? {
        val valid = hr.filter { it in 30..220 }
        if (valid.size < 36) return null // under 3 hours of readings in the whole window
        val sorted = valid.sorted()
        val baseline = sorted[(sorted.size * 0.2).toInt()] // roughly the lowest fifth of the readings

        val sleepLike = BooleanArray(hr.size) { i -> hr[i] in 30..220 && hr[i] <= baseline + 10 && stepsPerSlot[i] < 4.0 }

        var bestFirst = -1
        var bestLast = -1
        var bestCount = 0
        var runFirst = -1
        var runLast = -1
        var runCount = 0
        for (i in hr.indices) {
            if (!sleepLike[i]) continue
            if (runFirst >= 0 && i - runLast <= 7) {
                runLast = i
                runCount++
            } else {
                runFirst = i
                runLast = i
                runCount = 1
            }
            if (runCount > bestCount) {
                bestCount = runCount
                bestFirst = runFirst
                bestLast = runLast
            }
        }
        if (bestCount < 36) return null // less than 3 hours of sleep-like readings

        // Trim ragged edges: the night starts and ends where sleep-like readings are the rule, not the exception.
        fun solid(i: Int, direction: Int): Boolean {
            var count = 0
            for (k in 0 until 6) {
                val j = i + direction * k
                if (j in hr.indices && sleepLike[j]) count++
            }
            return count >= 4
        }
        while (bestFirst < bestLast && (!sleepLike[bestFirst] || !solid(bestFirst, 1))) bestFirst++
        while (bestLast > bestFirst && (!sleepLike[bestLast] || !solid(bestLast, -1))) bestLast--
        if ((bestFirst..bestLast).count { sleepLike[it] } < 36) return null

        val sleepHr = ArrayList<Int>()
        for (i in bestFirst..bestLast) if (sleepLike[i]) sleepHr.add(hr[i])
        // Stages are relative to this night: the calmest quarter of the readings counts as deep sleep.
        val sortedSleep = sleepHr.sorted()
        val deepLimit = sortedSleep[(sortedSleep.size * 0.25).toInt()]
        val deep = sleepHr.count { it <= deepLimit }
        val light = sleepHr.size - deep
        val total = bestLast - bestFirst + 1
        val withReading = (bestFirst..bestLast).count { hr[it] in 30..220 }
        return SleepNight(
            firstSlot = bestFirst, lastSlot = bestLast,
            asleepMin = (deep + light) * 5, awakeMin = (total - deep - light) * 5,
            lightMin = light * 5, deepMin = deep * 5,
            restingHr = lowestAverage(sleepHr),
            coveragePercent = withReading * 100 / total,
        )
    }

    /** The lowest average over 6 readings in a row (about 30 minutes), so one odd reading can't skew it. */
    fun lowestAverage(values: List<Int>, window: Int = 6): Int {
        if (values.isEmpty()) return 0
        if (values.size < window) return values.min()
        return (0..values.size - window).minOf { i -> values.subList(i, i + window).average() }.roundToInt()
    }

    /** 0-21, rising fast at first and flattening, from time spent in the heart-rate zones. [extraLoad] adds load that heart rate can't see (lifting). */
    fun strainFromHeartRate(bpms: List<Int>, maxHr: Int = 190, extraLoad: Double = 0.0): Double {
        var load = extraLoad
        for (bpm in bpms) {
            val fraction = bpm / maxHr.toDouble()
            load += 5 * zoneWeight(fraction) // each reading stands for 5 minutes
        }
        return strainFromLoad(load)
    }

    fun strainFromLoad(load: Double): Double = Math.round(21 * (1 - exp(-load / 450.0)) * 10) / 10.0

    /** How hard a heart rate works the body, by share of maximum: 0 below the zones up to 8 in the top zone. */
    fun zoneWeight(fraction: Double): Double = when {
        fraction >= 0.9 -> 8.0
        fraction >= 0.8 -> 5.0
        fraction >= 0.7 -> 3.0
        fraction >= 0.6 -> 2.0
        fraction >= 0.5 -> 1.0
        else -> 0.0
    }

    /**
     * Energy from 0 to 100 across the day, one value per 5 minutes from waking up. It starts at
     * [startEnergy] (how rested you are) and drains with time awake and with effort, so a hard
     * workout after a short night leaves you "gassed". Our own first guess, meant to be tuned.
     */
    fun energyCurve(points: List<HrPoint>, wakeMinute: Int, startEnergy: Double, maxHr: Int = 190): List<Float> {
        val lastMinute = points.maxOfOrNull { it.minuteOfDay } ?: return emptyList()
        val byMinute = points.associateBy { it.minuteOfDay }
        var energy = startEnergy.coerceIn(0.0, 100.0)
        val out = ArrayList<Float>()
        var minute = wakeMinute - wakeMinute % 5
        while (minute <= lastMinute) {
            val bpm = byMinute[minute]?.bpm
            val effort = if (bpm == null) 0.0 else zoneWeight(bpm / maxHr.toDouble())
            energy = (energy - (0.15 + effort * 0.25)).coerceAtLeast(0.0)
            out.add(energy.toFloat())
            minute += 5
        }
        return out
    }

    /** Calories burned per minute at a heart rate (Keytel et al. formula, which gives kilojoules). */
    fun kcalPerMinute(bpm: Int, user: UserProfile): Double {
        val kilojoules = if (user.male) {
            -55.0969 + 0.6309 * bpm + 0.1988 * user.weightKg + 0.2017 * user.ageYears
        } else {
            -20.4022 + 0.4472 * bpm - 0.1263 * user.weightKg + 0.074 * user.ageYears
        }
        return kilojoules / 4.184
    }

    /** Resting burn per minute (Mifflin-St Jeor). */
    fun restingKcalPerMinute(user: UserProfile): Double =
        (10 * user.weightKg + 6.25 * user.heightCm - 5 * user.ageYears + (if (user.male) 5 else -161)) / 1440.0

    /**
     * Total calories so far: resting burn for the minutes that have passed, plus the extra burn
     * whenever heart rate is above half of maximum. An estimate, not a measurement.
     */
    fun caloriesEstimate(bpms: List<Int>, elapsedMinutes: Int, user: UserProfile): Int {
        val resting = restingKcalPerMinute(user)
        var active = 0.0
        for (bpm in bpms) {
            if (bpm < 0.5 * user.maxHr) continue
            active += max(0.0, kcalPerMinute(bpm, user) - resting) * 5
        }
        return (resting * elapsedMinutes + active).roundToInt()
    }
}
