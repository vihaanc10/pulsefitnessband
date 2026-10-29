package app.pulse.spike.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pulse.spike.SpikeViewModel
import app.pulse.spike.data.EmptyHealthRepository
import app.pulse.spike.data.SampleHealthRepository
import app.pulse.spike.ui.components.BottomNavigation
import app.pulse.spike.ui.components.PulseTab
import app.pulse.spike.ui.screens.CoachScreen
import app.pulse.spike.ui.screens.HealthScreen
import app.pulse.spike.ui.screens.HomeScreen
import app.pulse.spike.ui.screens.ProfileScreen
import app.pulse.spike.ui.theme.PulseColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The whole app: four tabs with a bottom bar, plus the Bluetooth lab (the Phase 0 tools),
 * which opens from the Profile tab.
 */
@Composable
fun PulseApp(vm: SpikeViewModel) {
    val ui = vm.ui
    val connection by vm.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val emptyRepo = remember { EmptyHealthRepository() }
    val sampleRepo = remember { SampleHealthRepository() }
    val repo = if (ui.useSample) sampleRepo else emptyRepo

    BackHandler(enabled = ui.labOpen && connection == null) { ui.labOpen = false }
    BackHandler(enabled = !ui.labOpen && ui.tab != PulseTab.Home) { ui.tab = PulseTab.Home }

    if (ui.labOpen) {
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
                        PulseTab.Health -> HealthScreen(ui, repo)
                        PulseTab.Coach -> CoachScreen(ui, repo)
                        PulseTab.Profile -> ProfileScreen(ui, repo, onSync = {
                            if (!ui.syncing) {
                                scope.launch {
                                    ui.syncing = true
                                    delay(1500)
                                    ui.syncing = false
                                    ui.lastSync = "Just now"
                                }
                            }
                        })
                    }
                }
            }
            BottomNavigation(selected = ui.tab, onSelect = { ui.tab = it })
        }
    }
}
