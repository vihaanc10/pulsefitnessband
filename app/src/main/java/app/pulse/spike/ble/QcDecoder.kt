package app.pulse.spike.ble

import app.pulse.spike.data.StepRecord
import java.time.Instant
import java.time.ZoneId

/** Called when a complete answer from the band has been decoded. */
interface QcListener {
    fun onBattery(percent: Int, charging: Boolean) {}

    /** One reading per 5-minute slot, slot 0 = midnight of the day we asked for. 0 = nothing recorded. */
    fun onHeartRateLog(readings: IntArray) {}
    fun onHeartRateNoData() {}
    fun onSteps(records: List<StepRecord>) {}
    fun onStepsNoData() {}

    /** A heart-rate reading streamed live from the band (workout mode). */
    fun onLiveHeartRate(bpm: Int) {}
}

/**
 * Turns the band's 16-byte replies into plain-English log lines and hands the numbers to [listener].
 * The layouts come from public Colmi-ring docs and are NOT fully verified on our band yet, so every
 * summary says "(unverified)". The raw bytes are always logged too.
 */
class QcDecoder(private val log: MessageLog, private val listener: () -> QcListener?) {

    // ---- heart-rate log (command 0x15) ----
    private var hrExpected = 0
    private var hrReadings = IntArray(0)
    private var hrIndex = 0
    private var hrStart = 0L

    // ---- steps (command 0x43) ----
    private val stepList = ArrayList<StepRecord>()

    fun onPacket(p: ByteArray) {
        if (p.size != 16) return
        when (p[0].toInt() and 0xFF) {
            0x01 -> log.info("decoded", "The band answered the set-clock command (its reply is a feature list we ignore)")
            0x03 -> battery(p)
            0x69 -> if (u(p, 1) == 1 && u(p, 2) == 0 && u(p, 3) in 30..220) listener()?.onLiveHeartRate(u(p, 3))
            0x16 -> heartRateSettings(p)
            0x15 -> heartRate(p)
            0x43 -> steps(p)
        }
    }

    private fun u(p: ByteArray, i: Int) = p[i].toInt() and 0xFF

    private fun battery(p: ByteArray) {
        val percent = u(p, 1)
        val charging = u(p, 2) == 1
        log.info("decoded", "Battery $percent%${if (charging) ", charging" else ""} (unverified)")
        listener()?.onBattery(percent, charging)
    }

    private fun heartRate(p: ByteArray) {
        when (val sub = u(p, 1)) {
            255 -> {
                log.info("decoded", "Heart-rate log: the band says it has no data for that day (unverified)")
                listener()?.onHeartRateNoData()
            }
            0 -> {
                hrExpected = u(p, 2)
                hrReadings = IntArray(hrExpected * 13)
                hrIndex = 0
            }
            else -> {
                if (sub == 1) {
                    hrStart = (u(p, 2) or (u(p, 3) shl 8) or (u(p, 4) shl 16) or (u(p, 5) shl 24)).toLong() and 0xFFFFFFFFL
                    for (i in 6..14) put(u(p, i))
                } else {
                    for (i in 2..14) put(u(p, i))
                }
                if (sub == hrExpected - 1) summariseHeartRate()
            }
        }
    }

    private fun heartRateSettings(p: ByteArray) {
        val state = when (u(p, 2)) {
            1 -> "ON"
            2 -> "OFF"
            else -> "unclear (value ${u(p, 2)})"
        }
        log.info("decoded", "Heart-rate logging is $state, interval ${u(p, 3)} minutes (unverified)")
    }

    private fun put(value: Int) {
        if (hrIndex < hrReadings.size) hrReadings[hrIndex++] = value
    }

    private fun summariseHeartRate() {
        listener()?.onHeartRateLog(hrReadings.copyOf())
        val good = hrReadings.withIndex().filter { it.value in 30..220 }
        if (good.isEmpty()) {
            log.info("decoded", "Heart-rate log: ${hrReadings.size} slots, but no readings yet (unverified)")
            return
        }
        val last = good.last()
        val minutes = last.index * 5 // one slot every 5 minutes from midnight
        val clock = "%02d:%02d".format(minutes / 60, minutes % 60)
        val day = Instant.ofEpochSecond(hrStart).atZone(ZoneId.systemDefault()).toLocalDate()
        log.info(
            "decoded",
            "Heart-rate log (unverified): day $day, ${good.size} readings, " +
                "latest ${last.value} bpm at about $clock, lowest ${good.minOf { it.value }}, " +
                "highest ${good.maxOf { it.value }}, average ${good.map { it.value }.average().toInt()}",
        )
    }

    private fun bcd(b: Int) = (b shr 4) * 10 + (b and 0x0F)

    private fun steps(p: ByteArray) {
        val first = u(p, 1)
        if (stepList.isEmpty() && first == 255) {
            log.info("decoded", "Steps: the band says it has no data for that day (unverified)")
            listener()?.onStepsNoData()
            return
        }
        if (stepList.isEmpty() && first == 240) return // header packet
        val day = "20%02d-%02d-%02d".format(bcd(u(p, 1)), bcd(u(p, 2)), bcd(u(p, 3)))
        stepList.add(
            StepRecord(
                day = day,
                index = u(p, 4), // 15-minute block of the day
                steps = u(p, 9) or (u(p, 10) shl 8),
                calories = u(p, 7) or (u(p, 8) shl 8),
                distanceM = u(p, 11) or (u(p, 12) shl 8),
            ),
        )
        if (u(p, 5) == u(p, 6) - 1) {
            log.info(
                "decoded",
                "Steps (unverified): day $day, ${stepList.sumOf { it.steps }} steps, ${stepList.sumOf { it.distanceM }} m, " +
                    "${stepList.sumOf { it.calories }} calories (raw units), from ${stepList.size} records",
            )
            listener()?.onSteps(stepList.toList())
            stepList.clear()
        }
    }
}
