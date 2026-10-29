package app.pulse.spike

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.pulse.spike.ble.BandConnection
import app.pulse.spike.ble.BleScanner
import app.pulse.spike.ble.MessageLog
import app.pulse.spike.ble.ScanItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Holds the scanner, the log and the current connection so they survive screen rotation. */
class SpikeViewModel(app: Application) : AndroidViewModel(app) {
    val log = MessageLog(File(app.filesDir, "logs"))
    val scanner = BleScanner(app)

    private val _connection = MutableStateFlow<BandConnection?>(null)
    val connection: StateFlow<BandConnection?> = _connection

    private val _device = MutableStateFlow<ScanItem?>(null)
    val device: StateFlow<ScanItem?> = _device

    fun connect(item: ScanItem) {
        scanner.stop()
        log.startSession(item.address, item.name)
        val conn = BandConnection(getApplication(), log)
        _device.value = item
        _connection.value = conn
        conn.connect(item.address)
    }

    /** Leaves the device screen. The CSV file stays on the phone. */
    fun disconnect() {
        _connection.value?.disconnect()
        _connection.value = null
        _device.value = null
    }

    override fun onCleared() {
        _connection.value?.disconnect()
        scanner.stop()
    }
}
