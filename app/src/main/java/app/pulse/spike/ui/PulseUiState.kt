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
    var syncStatus by mutableStateOf("")
    var lastSync by mutableStateOf<String?>(null)

    /** Goes up by one whenever new band data has been saved, so screens re-read it. */
    var dataVersion by mutableStateOf(0)

    /** True once a band has been picked in the Bluetooth lab, so the app knows which one to sync. */
    var bandSaved by mutableStateOf(false)

    var addDeviceOpen by mutableStateOf(false)

    /** False on a brand-new install until the welcome screen has been filled in. */
    var onboarded by mutableStateOf(true)
    var completeOnboarding: ((app.pulse.spike.data.UserProfile, Boolean) -> Unit)? = null

    /** The logo intro shows once each time the app starts. */
    var introDone by mutableStateOf(false)

    /** Set by the ViewModel: forgets the saved band (true = also delete its data). */
    var removeDeviceAction: ((Boolean) -> Unit)? = null

    /** Set by the ViewModel: remembers a notification switch, and sends a test notification (false = blocked). */
    var persistNotification: ((String, Boolean) -> Unit)? = null
    var testNotification: (() -> Boolean)? = null

    /** The workout running right now, and the actions the Workouts tab calls (set by the ViewModel). */
    var activeWorkout by mutableStateOf<app.pulse.spike.data.ActiveWorkout?>(null)
    var startWorkout: ((app.pulse.spike.data.WorkoutKind, Int) -> Unit)? = null
    var stopWorkout: (() -> Unit)? = null
    var discardWorkout: (() -> Unit)? = null

    /** Set by the ViewModel: saves the edited profile, and remembers metric / imperial. */
    var saveProfile: ((app.pulse.spike.data.UserProfile) -> Unit)? = null
    var persistUnits: ((Boolean) -> Unit)? = null

    /** Set by the ViewModel: save or clear a hand-edited sleep time (date, first slot, last slot). */
    var saveSleepOverride: ((java.time.LocalDate, Int, Int) -> Unit)? = null
    var clearSleepOverride: ((java.time.LocalDate) -> Unit)? = null

    /** Set by the ViewModel: starts a sync. */
    var syncAction: (() -> Unit)? = null
    var coachQuestion by mutableStateOf<Int?>(null)

    val goalDone = mutableStateMapOf<String, Boolean>()
    val notifications = mutableStateMapOf(
        "recovery" to true,
        "bedtime" to true,
        "battery" to true,
        "sync" to false,
    )
}
