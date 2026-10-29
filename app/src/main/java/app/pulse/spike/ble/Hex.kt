package app.pulse.spike.ble

/** Bytes -> "0A 1B FF" */
fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

/** "0A 1B ff", "0x0A,0x1B" or "0a1bff" -> bytes. Returns null if it isn't valid hex. */
fun parseHex(text: String): ByteArray? {
    val clean = text.replace("0x", "", ignoreCase = true)
        .filter { it != ' ' && it != ',' && it != ':' && it != '\n' }
    if (clean.isEmpty() || clean.length % 2 != 0) return null
    if (!clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * A 16-byte packet in the format the public Colmi-ring docs describe: byte 0 = command,
 * bytes 1-14 = data (zero padded), byte 15 = sum of the first 15 bytes. Unverified on our band.
 */
fun qcPacket(command: Int, data: ByteArray = ByteArray(0)): ByteArray {
    val packet = ByteArray(16)
    packet[0] = command.toByte()
    data.copyInto(packet, 1)
    var sum = 0
    for (i in 0 until 15) sum += packet[i].toInt() and 0xFF
    packet[15] = (sum and 0xFF).toByte()
    return packet
}
