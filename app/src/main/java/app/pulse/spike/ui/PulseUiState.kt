package app.pulse.spike.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pulse.spike.ui.components.PulseTab
import java.time.LocalDate

/**
 * Everything the screens remember (selected tab, chosen day, switches, ticked goals).
 * Held by the ViewModel so it survives rotating the phone and switching tabs.
 */
class PulseUiState {
    var tab by mutableStateOf(PulseTab.Home)
    var labOpen by mutableStateOf(false)

    /** False = real (still empty) data. True = made-up preview numbers for reviewing the design. */
    var useSample by mutableStateOf(false)

    var epochDay by mutableStateOf(LocalDate.now().toEpochDay())
    var healthRange by mutableStateOf(7)
    var metricUnits by mutableStateOf(true)
    var syncing by mutableStateOf(false)
    var lastSync by mutableStateOf<String?>(null)
    var coachQuestion by mutableStateOf<Int?>(null)

    val goalDone = mutableStateMapOf<String, Boolean>()
    val notifications = mutableStateMapOf(
        "recovery" to true,
        "bedtime" to true,
        "battery" to true,
        "sync" to false,
    )
}
