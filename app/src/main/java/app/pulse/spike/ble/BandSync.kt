package app.pulse.spike.ble

import android.content.Context
import app.pulse.spike.data.PulseDb
import app.pulse.spike.data.StepRecord
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The "open the app and it syncs" part. Connects to the saved band, sets its clock, then reads the
 * battery, the heart-rate log and the steps for today and yesterday, and saves everything in [db].
 * Takes roughly 20-30 seconds. Safe to call again: a second call while one is running does nothing.
 */
class BandSync(
    private val context: Context,
    private val db: PulseDb,
    private val log: MessageLog,
    private val onStatus: (syncing: Boolean, message: String) -> Unit,
    private val onDataChanged: () -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var pendingHrDay: LocalDate = LocalDate.now()
    @Volatile private var hrGot = false
    @Volatile private var hrNoData = false
    @Volatile private var stepsDone = false
    @Volatile private var batteryDone = false

    private val listener = object : QcListener {
        override fun onBattery(percent: Int, charging: Boolean) {
            db.setMeta("battery", percent.toString())
            batteryDone = true
        }

        override fun onHeartRateLog(readings: IntArray) {
            if (readings.any { it in 30..220 }) {
                db.saveHeartRate(pendingHrDay, readings)
                hrGot = true
            } else {
                hrNoData = true
            }
        }

        override fun onHeartRateNoData() {
            hrNoData = true
        }

        override fun onSteps(records: List<StepRecord>) {
            db.saveSteps(records)
            stepsDone = true
        }

        override fun onStepsNoData() {
            stepsDone = true
        }
    }

    suspend fun run(address: String, name: String?) {
        if (running) return
        running = true
        var connection: BandConnection? = null
        try {
            onStatus(true, "Connecting to your band...")
            log.startSession(address, name)
            val conn = BandConnection(context, log)
            connection = conn
            conn.listener = listener
            conn.connect(address)
            if (!waitFor(20_000) { conn.state.value == BandConnection.State.Ready }) {
                onStatus(false, "Couldn't reach the band. Keep it close to the phone and try again.")
                return
            }

            onStatus(true, "Syncing: setting the clock...")
            conn.setBandClock()
            delay(1200)

            onStatus(true, "Syncing: battery...")
            batteryDone = false
            conn.requestBattery()
            waitFor(3_000) { batteryDone }

            for (daysAgo in 0..1) {
                onStatus(true, if (daysAgo == 0) "Syncing: today's heart rate..." else "Syncing: yesterday's heart rate...")
                pendingHrDay = LocalDate.now().minusDays(daysAgo.toLong())
                // Way B first: the band's clock holds local time and has no time zone.
                val got = readHeartRate(conn, daysAgo, clockAsUtc = true)
                val offset = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds
                if (!got && offset != 0) readHeartRate(conn, daysAgo, clockAsUtc = false)
            }

            for (daysAgo in 0..1) {
                onStatus(true, if (daysAgo == 0) "Syncing: today's steps..." else "Syncing: yesterday's steps...")
                stepsDone = false
                conn.requestSteps(daysAgo)
                waitFor(4_000) { stepsDone }
            }

            db.setMeta("last_sync", System.currentTimeMillis().toString())
            onDataChanged()
            onStatus(false, "Synced")
        } catch (e: Exception) {
            log.info("error", "Sync failed: ${e.message}")
            onStatus(false, "Sync failed. Try again.")
        } finally {
            connection?.listener = null
            connection?.disconnect()
            running = false
        }
    }

    private suspend fun readHeartRate(conn: BandConnection, daysAgo: Int, clockAsUtc: Boolean): Boolean {
        hrGot = false
        hrNoData = false
        conn.requestHeartRateLog(daysAgo, clockAsUtc)
        waitFor(5_000) { hrGot || hrNoData }
        return hrGot
    }

    private suspend fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        var waited = 0L
        while (waited < timeoutMs) {
            if (condition()) return true
            delay(100)
            waited += 100
        }
        return condition()
    }
}
