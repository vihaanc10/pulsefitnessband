package app.pulse.spike.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.SpikeViewModel
import app.pulse.spike.ble.hasBlePermissions
import app.pulse.spike.ui.components.PulseButton
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType

private fun permissionsToAsk(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/** Scan for nearby bands and pick one. Pulse remembers it and syncs it from then on. */
@Composable
fun AddDeviceScreen(vm: SpikeViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val devices by vm.scanner.devices.collectAsStateWithLifecycle()
    val scanning by vm.scanner.scanning.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        message = if (results.values.all { it }) {
            vm.scanner.start()
        } else {
            "Pulse needs the Nearby devices and Location permissions to find your band. Allow them in Android Settings > Apps > Pulse > Permissions."
        }
    }

    fun startScan() {
        if (hasBlePermissions(context)) message = vm.scanner.start() else launcher.launch(permissionsToAsk())
    }

    LaunchedEffect(Unit) { startScan() }
    DisposableEffect(Unit) { onDispose { vm.scanner.stop() } }

    val named = devices.filter { !it.name.isNullOrBlank() }

    Column(
        Modifier.fillMaxSize().background(PulseColors.Background).statusBarsPadding().navigationBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Add a device", style = PulseType.Headline)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Switch your band on and hold it next to your phone. Tap it when it appears below.",
                    style = PulseType.Body,
                )
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = PulseType.Caption, color = PulseColors.Red)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (scanning) "Searching... ${named.size} found" else "${named.size} found",
                    style = PulseType.Caption,
                )
            }
            items(named, key = { it.address }) { device ->
                PulseCard(Modifier.clickable { vm.addDevice(device); onClose() }) {
                    Text(device.name ?: "", style = PulseType.Title)
                    Spacer(Modifier.height(4.dp))
                    Text("${device.address}   signal ${device.rssi} dBm", style = PulseType.Caption)
                }
            }
            item {
                Text(
                    "Can't see your band? Make sure it's charged, close any other band app, and tap Search again. " +
                        "Pulse reads bands that speak the same Bluetooth language as the LIGE G69. " +
                        "Other bands may need extra work.",
                    style = PulseType.Caption,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scanning) {
                PulseButton("Stop", onClick = { vm.scanner.stop() }, filled = false)
            } else {
                PulseButton("Search again", onClick = { startScan() })
            }
            PulseButton("Cancel", onClick = onClose, filled = false)
        }
    }
}
