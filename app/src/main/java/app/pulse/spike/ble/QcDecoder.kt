package app.pulse.spike.ble

import java.time.Instant
import java.time.ZoneId

/**
 * Turns the band's 16-byte replies into one plain-English line in the log.
 * The layouts come from public Colmi-ring docs and are NOT verified on our band yet, so every
 * summary says "(unverified)". The raw bytes are always logged too.
 */
class QcDecoder(private val log: MessageLog) {

    // ---- heart-rate log (command 0x15) ----
    private var hrExpected = 0
    private var hrReadings = IntArray(0)
    private var hrIndex = 0
    private var hrStart = 0L

    // ---- steps (command 0x43) ----
    private var stepRecords = 0
    private var stepTotal = 0
    private var calorieTotal = 0
    private var distanceTotal = 0
    private var stepDate = ""

    fun onPacket(p: ByteArray) {
        if (p.size != 16) return
        when (p[0].toInt() and 0xFF) {
            0x16 -> heartRateSettings(p)
            0x15 -> heartRate(p)
            0x43 -> steps(p)
        }
    }

    private fun u(p: ByteArray, i: Int) = p[i].toInt() and 0xFF

    private fun heartRate(p: ByteArray) {
        when (val sub = u(p, 1)) {
            255 -> log.info("decoded", "Heart-rate log: the band says it has no data for that day (unverified)")
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
        if (stepRecords == 0 && first == 255) {
            log.info("decoded", "Steps: the band says it has no data for that day (unverified)")
            return
        }
        if (stepRecords == 0 && first == 240) return // header packet
        stepDate = "20%02d-%02d-%02d".format(bcd(u(p, 1)), bcd(u(p, 2)), bcd(u(p, 3)))
        calorieTotal += u(p, 7) or (u(p, 8) shl 8)
        stepTotal += u(p, 9) or (u(p, 10) shl 8)
        distanceTotal += u(p, 11) or (u(p, 12) shl 8)
        stepRecords++
        if (u(p, 5) == u(p, 6) - 1) {
            log.info(
                "decoded",
                "Steps (unverified): day $stepDate, $stepTotal steps, $distanceTotal m, " +
                    "$calorieTotal calories (raw units), from $stepRecords records",
            )
            stepRecords = 0; stepTotal = 0; calorieTotal = 0; distanceTotal = 0
        }
    }
}
