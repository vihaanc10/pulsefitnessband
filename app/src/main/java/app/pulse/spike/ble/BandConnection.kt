package app.pulse.spike.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Connects to one device, discovers its services and characteristics, and lets you read, write and
 * subscribe to notifications. Every byte that goes in either direction is written to [log].
 *
 * Android's Bluetooth can only do ONE thing at a time, so requests wait in a queue.
 */
@SuppressLint("MissingPermission") // the scanner screen asks for permission first
@Suppress("DEPRECATION") // the old read/write calls still work on every Android version we target
class BandConnection(private val context: Context, private val log: MessageLog) {

    data class CharInfo(val serviceUuid: UUID, val uuid: UUID, val properties: Int) {
        val key: String get() = "$serviceUuid/$uuid"
        val canRead: Boolean get() = has(BluetoothGattCharacteristic.PROPERTY_READ)
        val canWrite: Boolean get() = has(BluetoothGattCharacteristic.PROPERTY_WRITE)
        val canWriteNoResponse: Boolean get() = has(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)
        val canNotify: Boolean get() = has(BluetoothGattCharacteristic.PROPERTY_NOTIFY)
        val canIndicate: Boolean get() = has(BluetoothGattCharacteristic.PROPERTY_INDICATE)

        private fun has(flag: Int) = (properties and flag) != 0

        fun propertyNames(): String = buildList {
            if (canRead) add("READ")
            if (canWrite) add("WRITE")
            if (canWriteNoResponse) add("WRITE_NO_RESPONSE")
            if (canNotify) add("NOTIFY")
            if (canIndicate) add("INDICATE")
        }.joinToString(" ")
    }

    data class ServiceInfo(val uuid: UUID, val characteristics: List<CharInfo>)

    enum class State { Connecting, DiscoveringServices, Ready, Disconnected }

    private val _state = MutableStateFlow(State.Connecting)
    val state: StateFlow<State> = _state

    private val _services = MutableStateFlow<List<ServiceInfo>>(emptyList())
    val services: StateFlow<List<ServiceInfo>> = _services

    /** Keys of the characteristics we are currently subscribed to. */
    private val _subscribed = MutableStateFlow<Set<String>>(emptySet())
    val subscribed: StateFlow<Set<String>> = _subscribed

    private var gatt: BluetoothGatt? = null
    private val decoder = QcDecoder(log)
    private val handler = Handler(Looper.getMainLooper())

    // ---- one-thing-at-a-time queue -------------------------------------------------------------
    private val lock = Any()
    private val pending = ArrayDeque<() -> Boolean>() // each returns true if the request was started
    private var busy = false
    private var opToken = 0
    private val subscribeTargets = HashMap<String, Boolean>() // key -> true if enabling, false if disabling

    private fun enqueue(op: () -> Boolean) = synchronized(lock) {
        pending.addLast(op)
        if (!busy) startNext()
    }

    // Caller must hold [lock].
    private fun startNext() {
        while (true) {
            val op = pending.removeFirstOrNull()
            if (op == null) {
                busy = false
                return
            }
            busy = true
            val token = ++opToken
            if (op()) {
                // Safety net: if Android never answers, don't freeze the queue forever.
                handler.postDelayed({
                    synchronized(lock) {
                        if (busy && opToken == token) {
                            log.info("timeout", "No answer from the band after 5 seconds; moving on")
                            busy = false
                            startNext()
                        }
                    }
                }, 5000)
                return
            }
        }
    }

    private fun finishOp() = synchronized(lock) {
        busy = false
        startNext()
    }

    // ---- public actions -------------------------------------------------------------------------

    fun connect(address: String) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        log.info("connection", "Connecting to $address")
        gatt = adapter.getRemoteDevice(address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        log.info("connection", "Disconnect requested")
        gatt?.disconnect()
        // If the band never answered, make sure we still let go of the connection.
        handler.postDelayed({ closeGatt() }, 700)
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        synchronized(lock) {
            pending.clear()
            busy = false
        }
        _state.value = State.Disconnected
    }

    fun read(info: CharInfo) = enqueue {
        val ch = find(info) ?: return@enqueue false
        log.add("TX", "read", info.uuid.toString(), null, "read request")
        gatt?.readCharacteristic(ch) == true
    }

    fun write(info: CharInfo, bytes: ByteArray, noResponse: Boolean) = enqueue {
        val ch = find(info) ?: return@enqueue false
        val kind = if (noResponse) "write-no-response" else "write"
        ch.writeType = if (noResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        ch.value = bytes
        log.add("TX", kind, info.uuid.toString(), bytes)
        val started = gatt?.writeCharacteristic(ch) == true
        if (!started) log.info("error", "Android refused to start the write", info.uuid.toString())
        started
    }

    fun setSubscribed(info: CharInfo, enable: Boolean) = enqueue {
        val ch = find(info) ?: return@enqueue false
        val cccd = ch.getDescriptor(CCCD) ?: run {
            log.info("error", "No notification switch (0x2902) on this characteristic", info.uuid.toString())
            return@enqueue false
        }
        gatt?.setCharacteristicNotification(ch, enable)
        val value = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            info.canNotify -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        cccd.value = value
        subscribeTargets[info.key] = enable
        log.add("TX", "descriptor", info.uuid.toString(), value, if (enable) "subscribe" else "unsubscribe")
        val started = gatt?.writeDescriptor(cccd) == true
        if (!started) log.info("error", "Android refused to start the subscribe", info.uuid.toString())
        started
    }

    fun subscribeToAll() {
        _services.value.flatMap { it.characteristics }
            .filter { (it.canNotify || it.canIndicate) && it.key !in _subscribed.value }
            .forEach { setSubscribed(it, true) }
    }

    /**
     * Sends one 16-byte request in the format the public Colmi docs describe, after making sure
     * we are listening on the reply slot. These requests only ask questions; they change nothing.
     */
    private fun sendQc(command: Int, data: ByteArray = ByteArray(0)) {
        val chars = _services.value.flatMap { it.characteristics }
        val send = chars.firstOrNull { it.serviceUuid == QC_SERVICE && it.uuid == QC_WRITE }
        val reply = chars.firstOrNull { it.serviceUuid == QC_SERVICE && it.uuid == QC_NOTIFY }
        if (send == null || reply == null) {
            log.info("error", "This band has no 6e40fff0 service, so this test can't run")
            return
        }
        if (reply.key !in _subscribed.value) setSubscribed(reply, true)
        write(send, qcPacket(command, data), noResponse = !send.canWrite)
    }

    fun requestBattery() = sendQc(0x03)

    /** Read-only: asks whether automatic heart-rate logging is on, and how often. */
    fun requestHeartRateSettings() = sendQc(0x16, byteArrayOf(1))

    /** WRITES a setting on the band: turns automatic heart-rate logging on, one reading every [minutes]. */
    fun enableHeartRateLogging(minutes: Int) = sendQc(0x16, byteArrayOf(2, 1, minutes.toByte()))

    /** Heart-rate log for a day. The request carries that day's midnight (our local time) as a Unix timestamp. */
    fun requestHeartRateLog(daysAgo: Int = 0) {
        val midnight = LocalDate.now().minusDays(daysAgo.toLong()).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        sendQc(0x15, ByteArray(4) { ((midnight shr (8 * it)) and 0xFF).toByte() })
    }

    /** Steps for a day (0 = today, 1 = yesterday). The other four bytes are fixed values from the public docs. */
    fun requestSteps(daysAgo: Int = 0) = sendQc(0x43, byteArrayOf(daysAgo.toByte(), 0x0F, 0, 0x5F, 0x01))

    private fun find(info: CharInfo): BluetoothGattCharacteristic? {
        val ch = gatt?.getService(info.serviceUuid)?.getCharacteristic(info.uuid)
        if (ch == null) log.info("error", "Characteristic not found", info.uuid.toString())
        return ch
    }

    // ---- what Android tells us -------------------------------------------------------------------

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            log.info("connection", "state=${stateName(newState)} status=$status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = State.DiscoveringServices
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post { closeGatt() }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val list = g.services.map { s ->
                ServiceInfo(
                    s.uuid,
                    s.characteristics.map { c -> CharInfo(s.uuid, c.uuid, c.properties) },
                )
            }
            for (s in list) {
                log.info("service", s.uuid.friendlyName() ?: "", s.uuid.toString())
                for (c in s.characteristics) {
                    log.info("characteristic", "${c.propertyNames()} (service ${s.uuid})", c.uuid.toString())
                }
            }
            _services.value = list
            _state.value = State.Ready
        }

        // Android 13+ calls this one...
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            handleRead(c, value, status)
        }

        // ...older Android calls this one.
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            handleRead(c, c.value ?: ByteArray(0), status)
        }

        private fun handleRead(c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            log.add("RX", "read", c.uuid.toString(), value, "status=$status")
            finishOp()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            log.info("write-result", "status=$status", c.uuid.toString())
            finishOp()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            log.add("RX", "notify", c.uuid.toString(), value)
            if (c.uuid == QC_NOTIFY) decoder.onPacket(value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val value = c.value ?: ByteArray(0)
            log.add("RX", "notify", c.uuid.toString(), value)
            if (c.uuid == QC_NOTIFY) decoder.onPacket(value)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            val c = d.characteristic
            val key = "${c.service.uuid}/${c.uuid}"
            val enabling = subscribeTargets.remove(key)
            if (status == BluetoothGatt.GATT_SUCCESS && enabling != null) {
                _subscribed.value = if (enabling) _subscribed.value + key else _subscribed.value - key
            }
            log.info("descriptor-result", "status=$status", c.uuid.toString())
            finishOp()
        }
    }

    private fun stateName(state: Int) = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> state.toString()
    }

    private companion object {
        val QC_SERVICE: UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
        val QC_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val QC_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        /** The standard "turn notifications on/off" switch every BLE characteristic uses. */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
