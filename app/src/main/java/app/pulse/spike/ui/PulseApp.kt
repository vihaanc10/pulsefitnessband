package app.pulse.spike.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.SpikeViewModel
import app.pulse.spike.data.RealHealthRepository
import app.pulse.spike.data.SampleHealthRepository
import app.pulse.spike.ui.components.BottomNavigation
import app.pulse.spike.ui.components.PulseTab
import app.pulse.spike.ui.components.PulseLockup
import app.pulse.spike.ui.screens.CoachScreen
import app.pulse.spike.ui.screens.HealthScreen
import app.pulse.spike.ui.screens.HomeScreen
import app.pulse.spike.ui.screens.ProfileScreen
import app.pulse.spike.ui.screens.SleepScreen
import app.pulse.spike.ui.screens.WorkoutsScreen
import app.pulse.spike.ui.theme.PulseColors
import kotlinx.coroutines.delay

/**
 * The whole app: four tabs with a bottom bar, plus the Bluetooth lab (the Phase 0 tools),
 * which opens from the Profile tab. When the app opens it syncs the saved band by itself.
 */
@Composable
fun PulseApp(vm: SpikeViewModel) {
    val ui = vm.ui
    val connection by vm.connection.collectAsStateWithLifecycle()
    val sampleRepo = remember { SampleHealthRepository() }
    // A new repository object each time the sync saves data, so every screen re-reads it.
    val realRepo = remember(ui.dataVersion) { RealHealthRepository(vm.db) }
    val repo = if (ui.useSample) sampleRepo else realRepo

    // Like QWatch Pro: open the app and it fetches the latest from the band.
    LaunchedEffect(Unit) { vm.syncNow() }

    // Android 13+ needs permission for notifications. Ask once, after a band has been added.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ui.bandSaved) {
        if (ui.bandSaved && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            vm.db.getMeta("asked_notifications") == null
        ) {
            vm.db.setMeta("asked_notifications", "1")
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = ui.addDeviceOpen) { ui.addDeviceOpen = false }
    BackHandler(enabled = ui.labOpen && connection == null && !ui.addDeviceOpen) { ui.labOpen = false }
    BackHandler(enabled = !ui.labOpen && !ui.addDeviceOpen && ui.tab != PulseTab.Home) { ui.tab = PulseTab.Home }

    LaunchedEffect(Unit) {
        delay(1300)
        ui.introDone = true
    }

    Box(Modifier.fillMaxSize()) {
        if (ui.addDeviceOpen) {
            AddDeviceScreen(vm, onClose = { ui.addDeviceOpen = false })
        } else if (ui.labOpen) {
            val current = connection
            if (current == null) {
                ScannerScreen(vm, onExit = { ui.labOpen = false })
            } else {
                DeviceScreen(vm, current)
            }
        } else {
            Column(Modifier.fillMaxSize().background(PulseColors.Background)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = ui.tab,
                        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
                        label = "tab",
                    ) { tab ->
                        when (tab) {
                            PulseTab.Home -> HomeScreen(ui, repo)
                            PulseTab.Sleep -> SleepScreen(ui, repo)
                            PulseTab.Workouts -> WorkoutsScreen(ui, repo)
                        PulseTab.Health -> HealthScreen(ui, repo)
                            PulseTab.Coach -> CoachScreen(ui, repo)
                            PulseTab.Profile -> ProfileScreen(ui, repo, onSync = { vm.syncNow() })
                        }
                    }
                }
                BottomNavigation(selected = ui.tab, onSelect = { ui.tab = it })
            }
        }

        // The logo shows for a moment when the app starts, then fades away.
        AnimatedVisibility(visible = !ui.introDone, enter = fadeIn(tween(0)), exit = fadeOut(tween(500))) {
            Box(Modifier.fillMaxSize().background(PulseColors.Background), contentAlignment = Alignment.Center) {
                PulseLockup(Modifier.height(120.dp))
            }
        }
    }
}
