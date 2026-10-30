package app.pulse.spike

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pulse.spike.ble.BandConnection
import app.pulse.spike.ble.BandSync
import app.pulse.spike.ble.BleScanner
import app.pulse.spike.ble.MessageLog
import app.pulse.spike.ble.ScanItem
import app.pulse.spike.background.BackgroundSync
import app.pulse.spike.background.Notifier
import app.pulse.spike.ble.hasBlePermissions
import app.pulse.spike.data.PulseDb
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.PulseTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Holds the scanner, the log, the database, the sync and the current connection so they survive screen rotation. */
class SpikeViewModel(app: Application) : AndroidViewModel(app) {
    val log = MessageLog(File(app.filesDir, "logs"))
    val scanner = BleScanner(app)
    val ui = PulseUiState()
    val db = PulseDb(app)

    private val bandSync = BandSync(
        context = app,
        db = db,
        log = log,
        onStatus = { syncing, message ->
            ui.syncing = syncing
            ui.syncStatus = message
        },
        onDataChanged = { ui.dataVersion += 1 },
    )

    private val _connection = MutableStateFlow<BandConnection?>(null)
    val connection: StateFlow<BandConnection?> = _connection

    private val _device = MutableStateFlow<ScanItem?>(null)
    val device: StateFlow<ScanItem?> = _device

    init {
        ui.bandSaved = db.getMeta("band_address") != null
        ui.syncAction = { syncNow() }
        ui.removeDeviceAction = { deleteData -> removeDevice(deleteData) }
        for (key in listOf("recovery", "bedtime", "battery", "sync")) {
            db.getMeta("notify_$key")?.let { ui.notifications[key] = it == "true" }
        }
        ui.persistNotification = { key, on -> db.setMeta("notify_$key", on.toString()) }
        ui.testNotification = { Notifier(getApplication(), db).sendTest() }
        if (ui.bandSaved) BackgroundSync.schedule(getApplication())
    }

    /** Connects to the saved band in the background and copies its readings onto the phone. */
    fun syncNow() {
        val address = db.getMeta("band_address") ?: return
        if (_connection.value != null || ui.syncing) return // the lab is using the band, or a sync is running
        if (!hasBlePermissions(getApplication())) {
            ui.syncStatus = "Allow Bluetooth in the Bluetooth lab first (Profile > Bluetooth lab > Scan)."
            return
        }
        viewModelScope.launch { bandSync.run(address, db.getMeta("band_name")) }
    }

    /** Saves a band picked in the Add device screen and starts syncing it. */
    fun addDevice(item: ScanItem) {
        scanner.stop()
        db.setMeta("band_address", item.address)
        db.setMeta("band_name", item.name ?: "Your band")
        ui.bandSaved = true
        ui.syncStatus = ""
        ui.dataVersion += 1
        ui.tab = PulseTab.Home
        BackgroundSync.schedule(getApplication())
        syncNow()
    }

    /** Forgets the saved band, so another one can be added. */
    fun removeDevice(deleteData: Boolean) {
        db.removeDevice(deleteData)
        BackgroundSync.cancel(getApplication())
        ui.bandSaved = false
        ui.syncStatus = ""
        ui.dataVersion += 1
    }

    /** Opens the lab connection. Also remembers this band, so the app can sync it automatically next time. */
    fun connect(item: ScanItem) {
        scanner.stop()
        db.setMeta("band_address", item.address)
        db.setMeta("band_name", item.name ?: "Your band")
        ui.bandSaved = true
        BackgroundSync.schedule(getApplication())
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
