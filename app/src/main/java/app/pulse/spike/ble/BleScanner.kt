package app.pulse.spike.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScanItem(
    val name: String?,
    val address: String,
    val rssi: Int, // signal strength in dBm; closer to 0 = nearer
    val serviceUuids: List<String>,
)

/** Looks for nearby Bluetooth LE devices and keeps a list, strongest signal first. */
@SuppressLint("MissingPermission") // the screen asks for permission before calling start()
class BleScanner(context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val found = LinkedHashMap<String, ScanItem>()

    private val _devices = MutableStateFlow<List<ScanItem>>(emptyList())
    val devices: StateFlow<List<ScanItem>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address
            val name = result.scanRecord?.deviceName ?: result.device.name
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            // Some devices send their name in one advert and their services in another: keep what we've seen.
            val old = found[address]
            found[address] = ScanItem(
                name = name ?: old?.name,
                address = address,
                rssi = result.rssi,
                serviceUuids = (uuids + (old?.serviceUuids ?: emptyList())).distinct(),
            )
            _devices.value = found.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    /** Returns null on success, or a short message explaining what went wrong. */
    fun start(): String? {
        if (adapter == null) return "This phone has no Bluetooth."
        if (!adapter.isEnabled) return "Bluetooth is off. Turn it on and try again."
        val scanner = adapter.bluetoothLeScanner ?: return "Could not start the Bluetooth scanner."
        found.clear()
        _devices.value = emptyList()
        scanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        _scanning.value = true
        return null
    }

    fun stop() {
        if (!_scanning.value) return
        adapter?.bluetoothLeScanner?.stopScan(callback)
        _scanning.value = false
    }
}
