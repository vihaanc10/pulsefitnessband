package app.pulse.spike.data

import java.util.Locale

fun formatDuration(minutes: Int): String = "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

fun formatThousands(n: Int): String = String.format(Locale.US, "%,d", n)

fun formatStrain(v: Double): String = String.format(Locale.US, "%.1f", v)

fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    val hour24 = m / 60
    val suffix = if (hour24 < 12) "am" else "pm"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return "$hour12:${"%02d".format(m % 60)} $suffix"
}

fun recoveryLabel(recovery: Int): String = when {
    recovery >= 67 -> "Primed"
    recovery >= 34 -> "Moderate"
    else -> "Low"
}

fun recoverySummary(recovery: Int): String = when {
    recovery >= 67 -> "Your body is well recovered and ready for a hard day."
    recovery >= 34 -> "Recovery is moderate. Train, but keep the effort controlled."
    else -> "Recovery is low. Favour rest, sleep and easy movement."
}

fun strainTarget(recovery: Int): ClosedFloatingPointRange<Double> = when {
    recovery >= 67 -> 14.0..18.0
    recovery >= 34 -> 10.0..14.0
    else -> 4.0..10.0
}

data class WorkoutSuggestion(val title: String, val detail: String, val minutes: Int, val zones: String)

fun workoutFor(recovery: Int): WorkoutSuggestion = when {
    recovery >= 67 -> WorkoutSuggestion("Tempo run", "Hold a steady, hard effort.", 40, "Zones 3-4")
    recovery >= 34 -> WorkoutSuggestion("Easy ride", "Stay conversational the whole way.", 35, "Zone 2")
    else -> WorkoutSuggestion("Walk and mobility", "Gentle movement to help recovery.", 20, "Zone 1")
}

fun recoveryAdvice(day: DayData): List<String> = buildList {
    add("Overnight resting heart rate was ${day.restingHr} bpm.")
    when {
        day.recovery >= 67 -> add("Take advantage of the green day: this is a good time for your hardest session.")
        day.recovery >= 34 -> add("Keep intensity in check and finish training at least 3 hours before bed.")
        else -> add("Skip intense training today and protect tonight's sleep.")
    }
    add("Drink water through the day and keep caffeine to the morning.")
}

fun sleepNeedMinutes(day: DayData): Int =
    day.sleepGoalMinutes + if (day.strain >= 14) 30 else if (day.strain >= 10) 15 else 0

/** Bedtime for a 6:30 am wake-up, with 15 minutes to fall asleep. */
fun suggestedBedtime(day: DayData): String = formatClock(6 * 60 + 30 - sleepNeedMinutes(day) - 15)

fun sleepAdvice(day: DayData): List<String> = buildList {
    add("You need about ${formatDuration(sleepNeedMinutes(day))} tonight.")
    add("Aim to be in bed by ${suggestedBedtime(day)} for a 6:30 am wake-up.")
    if (day.sleepConsistency < 75) add("Your bed and wake times varied lately. A steadier schedule will lift your sleep score.")
}

fun coachAnswers(day: DayData): List<Pair<String, String>> = listOf(
    "Why is my recovery ${day.recovery}%?" to
        "Mostly your overnight resting heart rate (${day.restingHr} bpm) and sleep (${formatDuration(day.sleepMinutes)}). Each one is compared with your own recent average.",
    "How hard should I train today?" to
        "Aim for a strain between ${formatStrain(strainTarget(day.recovery).start)} and ${formatStrain(strainTarget(day.recovery).endInclusive)}.",
    "How do I sleep better?" to
        "Keep a steady bedtime, finish heavy training earlier and keep the room cool and dark.",
)
