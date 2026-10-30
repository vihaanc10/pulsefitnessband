package app.pulse.spike.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pulse.spike.MainActivity
import app.pulse.spike.R
import app.pulse.spike.data.PulseDb
import app.pulse.spike.data.RealHealthRepository
import app.pulse.spike.data.formatClock
import app.pulse.spike.data.formatDuration
import app.pulse.spike.data.recoveryLabel
import app.pulse.spike.data.suggestedBedtimeMinute
import java.time.LocalDate
import java.time.LocalDateTime

/** Sends Pulse's notifications: morning recovery, low band battery and a bedtime reminder. */
class Notifier(private val context: Context, private val db: PulseDb) {

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Runs after every sync. Each notification goes out at most once per day (battery: once per 12 hours). */
    fun check() {
        if (!canNotify()) return
        val repo = RealHealthRepository(db)
        if (!repo.hasData) return
        val now = LocalDateTime.now()
        val today = LocalDate.now().toString()

        if (enabled("recovery") && now.hour in 6..13 && db.getMeta("notified_morning") != today) {
            val day = repo.day(LocalDate.now())
            if (day.hasSleep) {
                val text = if (day.hasRecovery) {
                    "Recovery ${day.recovery}% (${recoveryLabel(day.recovery)}). You slept ${formatDuration(day.sleepMinutes)}."
                } else {
                    "You slept ${formatDuration(day.sleepMinutes)}. Recovery unlocks after 5 nights (${minOf(day.nightsOfData, 5)} of 5 so far)."
                }
                send(1, "Good morning", text)
                db.setMeta("notified_morning", today)
            }
        }

        val battery = db.getMeta("battery")?.toIntOrNull()
        if (enabled("battery") && battery != null && battery <= 20) {
            val last = db.getMeta("notified_battery")?.toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - last > 12 * 3_600_000L) {
                send(2, "Band battery low", "Your band is at $battery%. Charge it soon so tonight's sleep is recorded.")
                db.setMeta("notified_battery", System.currentTimeMillis().toString())
            }
        }

        if (enabled("bedtime") && now.hour in 18..23 && db.getMeta("notified_bedtime") != today) {
            val bed = suggestedBedtimeMinute(repo.day(LocalDate.now()))
            val minuteNow = now.hour * 60 + now.minute
            if (minuteNow in (bed - 45)..bed) {
                send(3, "Wind down soon", "Be in bed by ${formatClock(bed)} for the sleep you need tonight.")
                db.setMeta("notified_bedtime", today)
            }
        }
    }

    /** For the "Send a test notification" button. Returns false if notifications are blocked. */
    fun sendTest(): Boolean {
        if (!canNotify()) return false
        send(9, "Pulse", "Notifications are working.")
        return true
    }

    private fun enabled(key: String): Boolean = db.getMeta("notify_$key")?.let { it == "true" } ?: (key != "sync")

    private fun send(id: Int, title: String, text: String) {
        val channel = NotificationChannel(CHANNEL, "Pulse alerts", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // permission was taken away while we were running
        }
    }

    private companion object {
        const val CHANNEL = "pulse_alerts"
    }
}
