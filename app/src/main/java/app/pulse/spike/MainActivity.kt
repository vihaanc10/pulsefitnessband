package app.pulse.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.ui.DeviceScreen
import app.pulse.spike.ui.ScannerScreen

class MainActivity : ComponentActivity() {
    private val vm: SpikeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                val connection by vm.connection.collectAsStateWithLifecycle()
                val current = connection
                if (current == null) {
                    ScannerScreen(vm)
                } else {
                    DeviceScreen(vm, current)
                }
            }
        }
    }
}
