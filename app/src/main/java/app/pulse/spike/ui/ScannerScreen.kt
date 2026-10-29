package app.pulse.spike.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.SpikeViewModel
import app.pulse.spike.ble.ScanItem
import app.pulse.spike.shareLogFile

private fun neededPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun ScannerScreen(vm: SpikeViewModel) {
    val context = LocalContext.current
    val devices by vm.scanner.devices.collectAsStateWithLifecycle()
    val scanning by vm.scanner.scanning.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val savedLogs = remember { vm.log.savedFiles() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        message = if (results.values.all { it }) {
            vm.scanner.start()
        } else {
            "Pulse needs the permission to find nearby devices. You can allow it in Android Settings > Apps > Pulse Phase 0."
        }
    }

    fun startScan() {
        val missing = neededPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) message = vm.scanner.start() else permissionLauncher.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) { onDispose { vm.scanner.stop() } }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Text("Pulse Phase 0", style = MaterialTheme.typography.headlineMedium)
                    Text("Step 1: find your band. Tap a device to connect to it.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (scanning) {
                            Button(onClick = { vm.scanner.stop() }) { Text("Stop scanning") }
                        } else {
                            Button(onClick = { startScan() }) { Text("Scan for devices") }
                        }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        if (scanning) "Scanning... ${devices.size} found (strongest signal first)" else "${devices.size} found",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            items(devices, key = { it.address }) { device ->
                DeviceCard(device) { vm.connect(device) }
            }
            if (savedLogs.isNotEmpty()) {
                item {
                    Text(
                        "Saved logs",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(savedLogs, key = { it.name }) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(file.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { shareLogFile(context, file) }) { Text("Share") }
                    }
                }
            }
            item { Text("", modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun DeviceCard(device: ScanItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name ?: "(no name)", style = MaterialTheme.typography.titleMedium)
            Text("${device.address}   signal ${device.rssi} dBm", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (device.serviceUuids.isEmpty()) {
                Text("No service UUIDs advertised", fontSize = 12.sp)
            } else {
                Text("Services:", fontSize = 12.sp)
                device.serviceUuids.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}
