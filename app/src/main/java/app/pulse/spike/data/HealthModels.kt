package app.pulse.spike.data

import java.time.LocalDate

/** One heart-rate reading. The band logs roughly one every 5 minutes. */
data class HrPoint(val minuteOfDay: Int, val bpm: Int)

/** The band reports deep and light sleep (PRD section 2). There is no REM field on purpose. */
data class SleepStages(val awakeMin: Int, val lightMin: Int, val deepMin: Int)

data class DayData(
    val date: LocalDate,
    val recovery: Int,
    val restingHr: Int,
    val hrvMs: Int?, // null = the band can't supply it
    val respiratoryRate: Double?, // null = the band can't supply it
    val sleepPerformance: Int,
    val sleepMinutes: Int,
    val sleepGoalMinutes: Int,
    val sleepEfficiency: Int,
    val sleepConsistency: Int,
    val stages: SleepStages,
    val strain: Double,
    val calories: Int,
    val steps: Int,
    val avgHr: Int,
    val maxHr: Int,
    val currentHr: Int,
    val heartRate: List<HrPoint>,
    val zoneMinutes: List<Int>, // 5 zones
    /** Real data can lack a night of sleep, or a recovery baseline. Screens then show only what exists. */
    val hasSleep: Boolean = true,
    val hasRecovery: Boolean = true,
    val hasStrain: Boolean = true,
    /** How many nights of sleep we have found (recovery unlocks at 5). */
    val nightsOfData: Int = 0,
    /** For example "11:20 pm to 6:40 am". Only set when sleep was estimated from the readings. */
    val sleepWindow: String? = null,
)

data class DeviceStatus(
    val name: String,
    val connected: Boolean,
    val batteryPercent: Int,
    val lastSync: String,
)

data class UserProfile(val name: String, val heightCm: Int, val weightKg: Int)

/**
 * Everything the screens know about the user's health comes through this one interface.
 * Today it is backed by sample data; later a repository that reads the band's database plugs in here.
 */
interface HealthRepository {
    /** False until the band has delivered real data. Screens show an empty state instead of numbers. */
    val hasData: Boolean
    val device: DeviceStatus
    val user: UserProfile
    fun day(date: LocalDate): DayData

    fun days(endInclusive: LocalDate, count: Int): List<DayData> =
        (count - 1 downTo 0).map { day(endInclusive.minusDays(it.toLong())) }
}
