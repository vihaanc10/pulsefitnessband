package app.pulse.spike.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pulse.spike.MainActivity
import app.pulse.spike.R
import app.pulse.spike.ble.BandConnection
import app.pulse.spike.ble.MessageLog
import app.pulse.spike.ble.QcListener
import app.pulse.spike.data.PulseDb
import app.pulse.spike.data.WorkoutKind
import app.pulse.spike.data.WorkoutTracker
import java.io.File

/**
 * Keeps a workout going while the phone is in a pocket: tracks the phone's GPS (the band has none)
 * for running, jogging, walking and biking, and asks the band for live heart rate.
 * Everything it records goes into the database, tagged with the workout's id.
 */
class WorkoutService : Service() {
    private lateinit var db: PulseDb
    private val handler = Handler(Looper.getMainLooper())
    private var workoutId = 0L
    private var kind: WorkoutKind = WorkoutKind.Running
    private var startMs = 0L
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var distanceM = 0.0
    private var conn: BandConnection? = null
    private var liveStarted = false
    private var lastHrAt = 0L
    private var lastStartAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        db = PulseDb(this)
        workoutId = intent.getLongExtra(EXTRA_ID, 0L)
        kind = runCatching { WorkoutKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }.getOrDefault(WorkoutKind.Running)
        startMs = intent.getLongExtra(EXTRA_START, System.currentTimeMillis())

        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (kind.usesGps && hasLocation) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            shutDown()
            return START_NOT_STICKY
        }

        if (kind.usesGps && hasLocation) startGps()
        startBand()
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    // ---- GPS (phone) ----

    @Suppress("MissingPermission")
    private fun startGps() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = manager
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 3f, locationListener, Looper.getMainLooper())
        } catch (e: Exception) {
            // GPS is switched off or not allowed; the workout still runs without distance.
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.hasAccuracy() && location.accuracy > 25f) return // too imprecise to trust
            val previous = lastLocation
            if (previous != null) {
                val step = previous.distanceTo(location).toDouble()
                if (step < 2.0) return // standing still
                if (step > 100.0) { // a jump, not real movement
                    lastLocation = location
                    return
                }
                distanceM += step
            }
            lastLocation = location
            db.addRoutePoint(workoutId, System.currentTimeMillis(), location.latitude, location.longitude)
            WorkoutTracker.state.value = WorkoutTracker.state.value.copy(
                distanceM = distanceM, speedMps = location.speed.toDouble(), gpsFix = true,
            )
        }

        // Needed because older Android versions require these methods to exist.
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Required on Android 10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    // ---- live heart rate (band) ----

    private fun startBand() {
        val address = db.getMeta("band_address") ?: return
        val connection = BandConnection(this, MessageLog(File(filesDir, "logs")))
        conn = connection
        connection.listener = object : QcListener {
            override fun onLiveHeartRate(bpm: Int) {
                lastHrAt = System.currentTimeMillis()
                db.addLiveHr(workoutId, lastHrAt, bpm)
                WorkoutTracker.state.value = WorkoutTracker.state.value.copy(liveHr = bpm)
            }
        }
        connection.connect(address)
    }

    /** Every few seconds: keep the heart-rate stream alive (or restart it) and refresh the notification. */
    private val tick = object : Runnable {
        override fun run() {
            val c = conn
            if (c != null && c.state.value == BandConnection.State.Ready) {
                val now = System.currentTimeMillis()
                if (!liveStarted || (now - lastHrAt > 20_000L && now - lastStartAt > 20_000L)) {
                    c.startLiveHeartRate()
                    liveStarted = true
                    lastStartAt = now
                } else {
                    c.continueLiveHeartRate()
                    c.keepAliveLiveHeartRate()
                }
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification())
            handler.postDelayed(this, 6000L)
        }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val seconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
        val state = WorkoutTracker.state.value
        val text = buildString {
            append("%d:%02d".format(seconds / 60, seconds % 60))
            if (kind.usesGps) append("  ·  %.2f km".format(state.distanceM / 1000))
            if (state.liveHr > 0) append("  ·  ${state.liveHr} bpm")
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentTitle("${kind.label} in progress")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun shutDown() {
        handler.removeCallbacks(tick)
        runCatching { locationManager?.removeUpdates(locationListener) }
        conn?.let {
            it.stopLiveHeartRate()
            handler.postDelayed({ it.disconnect() }, 800L)
        }
        conn = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "app.pulse.spike.STOP_WORKOUT"
        const val EXTRA_ID = "id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_START = "start"
        private const val CHANNEL = "pulse_workout"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, id: Long, kind: WorkoutKind, startMs: Long) {
            val intent = Intent(context, WorkoutService::class.java)
                .putExtra(EXTRA_ID, id).putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_START, startMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WorkoutService::class.java).setAction(ACTION_STOP))
        }
    }
}
