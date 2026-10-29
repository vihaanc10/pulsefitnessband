package app.pulse.spike.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One line of the log.
 * direction: TX = phone -> band, RX = band -> phone, INFO = a note from the app itself.
 * kind: write, write-no-response, read, notify, descriptor, connection, service, characteristic, ...
 */
data class LogEntry(
    val timeMillis: Long,
    val direction: String,
    val kind: String,
    val characteristic: String,
    val hex: String,
    val note: String,
) {
    fun isoTime(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(timeMillis))

    fun toCsvLine(): String =
        listOf(isoTime(), timeMillis.toString(), direction, kind, characteristic, hex, note)
            .joinToString(",") { csvEscape(it) }
}

private fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/**
 * Keeps every message in memory (so the screen can show it) AND appends it to a CSV file
 * on the phone right away (so nothing is lost if the app is closed).
 */
class MessageLog(private val dir: File) {
    private val lock = Any()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    @Volatile
    var currentFile: File? = null
        private set

    /** Starts a fresh CSV file for a new connection. */
    fun startSession(address: String, name: String?): File {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "log_${stamp}_${address.replace(":", "")}.csv")
        file.writeText(HEADER + "\n")
        synchronized(lock) {
            currentFile = file
            _entries.value = emptyList()
        }
        info("session", "device=${name ?: "(no name)"} address=$address")
        return file
    }

    fun add(direction: String, kind: String, characteristic: String, bytes: ByteArray?, note: String = "") {
        val entry = LogEntry(
            System.currentTimeMillis(), direction, kind, characteristic, bytes?.toHex() ?: "", note,
        )
        synchronized(lock) {
            _entries.value = _entries.value + entry
            currentFile?.appendText(entry.toCsvLine() + "\n")
        }
    }

    fun info(kind: String, note: String, characteristic: String = "") =
        add("INFO", kind, characteristic, null, note)

    /** Earlier log files, newest first. */
    fun savedFiles(): List<File> =
        dir.listFiles { f -> f.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private companion object {
        const val HEADER = "time_iso,epoch_ms,direction,kind,characteristic,hex,note"
    }
}
