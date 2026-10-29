package app.pulse.spike.ble

import java.util.UUID

private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

/** "0000180f-0000-1000-8000-00805f9b34fb" -> "180F". Custom UUIDs stay in full. */
fun UUID.short(): String {
    val s = toString().lowercase()
    return if (s.startsWith("0000") && s.endsWith(BASE_SUFFIX)) s.substring(4, 8).uppercase() else s
}

private val standardNames = mapOf(
    "1800" to "Generic Access",
    "1801" to "Generic Attribute",
    "180A" to "Device Information",
    "180D" to "Heart Rate",
    "180F" to "Battery",
    "2A00" to "Device Name",
    "2A01" to "Appearance",
    "2A04" to "Preferred Connection Parameters",
    "2A19" to "Battery Level",
    "2A24" to "Model Number",
    "2A25" to "Serial Number",
    "2A26" to "Firmware Revision",
    "2A27" to "Hardware Revision",
    "2A28" to "Software Revision",
    "2A29" to "Manufacturer Name",
    "2A37" to "Heart Rate Measurement",
)

// From memory of the Colmi open-source docs; double-check it against that repo (PRD section 8, step 1).
private const val COLMI_STYLE_SERVICE = "6e40fff0-b5a3-f393-e0a9-e50e24dcca9e"

/** A friendly name if we know one, otherwise null. */
fun UUID.friendlyName(): String? {
    if (toString().lowercase() == COLMI_STYLE_SERVICE) return "Possible Colmi/QC-style service (unconfirmed)"
    return standardNames[short()]
}
