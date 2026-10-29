package app.pulse.spike.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.SpikeViewModel
import app.pulse.spike.ble.BandConnection
import app.pulse.spike.ble.BandConnection.CharInfo
import app.pulse.spike.ble.LogEntry
import app.pulse.spike.ble.friendlyName
import app.pulse.spike.ble.parseHex
import app.pulse.spike.shareLogFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceScreen(vm: SpikeViewModel, conn: BandConnection) {
    val context = LocalContext.current
    val device by vm.device.collectAsStateWithLifecycle()
    val state by conn.state.collectAsStateWithLifecycle()
    val services by conn.services.collectAsStateWithLifecycle()
    val subscribed by conn.subscribed.collectAsStateWithLifecycle()
    val entries by vm.log.entries.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var writeTarget by remember { mutableStateOf<CharInfo?>(null) }

    BackHandler { vm.disconnect() }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device?.name ?: "(no name)", style = MaterialTheme.typography.titleLarge)
                Text(device?.address ?: "", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(
                    when (state) {
                        BandConnection.State.Connecting -> "Connecting..."
                        BandConnection.State.DiscoveringServices -> "Connected. Reading the list of services..."
                        BandConnection.State.Ready -> "Connected. ${services.size} services found."
                        BandConnection.State.Disconnected -> "Disconnected. Your log is still saved."
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.disconnect() }) { Text("Back") }
                    Button(
                        onClick = { vm.log.currentFile?.let { shareLogFile(context, it) } },
                        enabled = entries.isNotEmpty(),
                    ) { Text("Export CSV") }
                }
                if (state == BandConnection.State.Ready) {
                    OutlinedButton(onClick = { conn.subscribeToAll() }) { Text("Subscribe to all notifications") }
                    Button(onClick = { conn.requestBattery() }) { Text("Test: ask the band for its battery") }
                    OutlinedButton(onClick = { conn.requestHeartRateLog() }) { Text("Test: read today's heart-rate log") }
                    OutlinedButton(onClick = { conn.requestSteps() }) { Text("Test: read today's steps") }
                    OutlinedButton(onClick = { conn.requestHeartRateLog(1) }) { Text("Test: read yesterday's heart-rate log") }
                    OutlinedButton(onClick = { conn.requestSteps(1) }) { Text("Test: read yesterday's steps") }
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Services") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Log (${entries.size})") })
            }

            if (tab == 0) {
                ServicesTab(conn, services, subscribed, onWrite = { writeTarget = it })
            } else {
                LogTab(entries)
            }
        }
    }

    writeTarget?.let { target ->
        WriteDialog(
            target = target,
            onDismiss = { writeTarget = null },
            onSend = { bytes, noResponse ->
                conn.write(target, bytes, noResponse)
                writeTarget = null
            },
        )
    }
}

@Composable
private fun ServicesTab(
    conn: BandConnection,
    services: List<BandConnection.ServiceInfo>,
    subscribed: Set<String>,
    onWrite: (CharInfo) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (services.isEmpty()) {
            item { Text("Nothing yet. Services appear here once the phone has connected to the band.") }
        }
        items(services, key = { it.uuid.toString() }) { service ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Service", style = MaterialTheme.typography.labelMedium)
                    Text(service.uuid.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    service.uuid.friendlyName()?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    service.characteristics.forEach { c ->
                        HorizontalDivider()
                        Text(c.uuid.toString(), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        c.uuid.friendlyName()?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        Text(c.propertyNames(), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (c.canRead) TextButton(onClick = { conn.read(c) }) { Text("Read") }
                            if (c.canWrite || c.canWriteNoResponse) TextButton(onClick = { onWrite(c) }) { Text("Write") }
                            if (c.canNotify || c.canIndicate) {
                                val on = c.key in subscribed
                                TextButton(onClick = { conn.setSubscribed(c, !on) }) {
                                    Text(if (on) "Unsubscribe" else "Subscribe")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTab(entries: List<LogEntry>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    // Newest first, and only the latest 300 on screen. The CSV file has everything.
    val shown = remember(entries) { entries.takeLast(300).reversed() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entries.size > shown.size) {
            item { Text("Showing the latest ${shown.size}. The exported CSV has all ${entries.size}.", fontSize = 12.sp) }
        }
        items(shown.size) { i ->
            val e = shown[i]
            val short = e.characteristic.take(8)
            Text(
                buildString {
                    append(timeFormat.format(Date(e.timeMillis)))
                    append("  ${e.direction} ${e.kind}")
                    if (short.isNotEmpty()) append("  $short")
                    if (e.hex.isNotEmpty()) append("\n${e.hex}")
                    if (e.note.isNotEmpty()) append("\n${e.note}")
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = when (e.direction) {
                    "TX" -> MaterialTheme.colorScheme.primary
                    "RX" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun WriteDialog(target: CharInfo, onDismiss: () -> Unit, onSend: (ByteArray, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var noResponse by remember { mutableStateOf(!target.canWrite) }
    val bytes = parseHex(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write to band") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(target.uuid.toString(), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Bytes in hex, e.g. 01 02 FF") },
                    isError = text.isNotEmpty() && bytes == null,
                    supportingText = {
                        if (text.isNotEmpty() && bytes == null) Text("Use pairs of 0-9 and A-F")
                    },
                )
                if (target.canWrite && target.canWriteNoResponse) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noResponse, onCheckedChange = { noResponse = it })
                        Text("Write without response")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { bytes?.let { onSend(it, noResponse) } }, enabled = bytes != null) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
