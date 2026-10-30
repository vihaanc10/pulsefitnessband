package app.pulse.spike.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import app.pulse.spike.data.UserProfile
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.spike.data.HealthRepository
import app.pulse.spike.ui.PulseUiState
import app.pulse.spike.ui.components.Label
import app.pulse.spike.ui.components.PulseButton
import app.pulse.spike.ui.components.PulseCard
import app.pulse.spike.ui.components.PulseLockup
import app.pulse.spike.ui.components.SegmentedControl
import app.pulse.spike.ui.components.Stat
import app.pulse.spike.ui.components.StatStrip
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(ui: PulseUiState, repo: HealthRepository, onSync: () -> Unit) {
    val user = repo.user
    val device = repo.device
    var confirmRemove by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var editingProfile by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Profile", style = PulseType.Headline)
        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(PulseColors.Card).border(1.dp, PulseColors.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(user.name.take(1).uppercase(), style = PulseType.Headline.copy(fontSize = 26.sp))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(user.name, style = PulseType.Headline.copy(fontSize = 24.sp))
                Text("Personal profile", style = PulseType.Caption)
            }
        }
        Spacer(Modifier.height(28.dp))

        // Band
        Label("Fitness band")
        Spacer(Modifier.height(12.dp))
        PulseCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (!ui.useSample && !ui.bandSaved) "No device added" else device.name, style = PulseType.Title)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (device.connected) PulseColors.Green else PulseColors.TextTertiary),
                        )
                        Text(
                            if (device.connected) "Connected" else "Not connected",
                            style = PulseType.Caption, modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (device.connected) Text("${device.batteryPercent}%", style = PulseType.Value)
            }
            if (device.connected) {
                Spacer(Modifier.height(16.dp))
                BatteryBar(device.batteryPercent / 100f)
            }
            Spacer(Modifier.height(16.dp))
            Text("Last sync: ${ui.lastSync ?: device.lastSync}", style = PulseType.Caption)
            Spacer(Modifier.height(18.dp))
            if (!ui.useSample) {
                if (ui.bandSaved) {
                    PulseButton(if (ui.syncing) "Syncing..." else "Sync now", onClick = onSync, filled = !ui.syncing)
                    if (ui.syncStatus.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.syncStatus, style = PulseType.Caption)
                    }
                    Spacer(Modifier.height(10.dp))
                    PulseButton("Remove device", onClick = { confirmRemove = true }, filled = false)
                } else {
                    PulseButton("Add device", onClick = { ui.addDeviceOpen = true })
                }
                Spacer(Modifier.height(10.dp))
            }
            PulseButton("Bluetooth lab (developer)", onClick = { ui.labOpen = true }, filled = false)
            Spacer(Modifier.height(10.dp))
            Text("Developer tools for scanning a band and testing what it sends.", style = PulseType.Caption)
        }
        Spacer(Modifier.height(28.dp))

        // Body
        Label("Body")
        Spacer(Modifier.height(12.dp))
        val heightText = if (ui.metricUnits) {
            "${user.heightCm.roundToInt()} cm"
        } else {
            val inches = (user.heightCm / 2.54).roundToInt()
            "${inches / 12} ft ${inches % 12} in"
        }
        val weightText = if (ui.metricUnits) {
            String.format(Locale.US, "%.1f kg", user.weightKg)
        } else {
            String.format(Locale.US, "%.1f lb", user.weightKg * 2.20462)
        }
        StatStrip(listOf(Stat("Height", heightText), Stat("Weight", weightText), Stat("Age", "${user.ageYears}")))
        Spacer(Modifier.height(14.dp))
        if (!ui.useSample) {
            PulseButton("Edit height, weight and age", onClick = { editingProfile = true }, filled = false)
            Spacer(Modifier.height(8.dp))
            Text(
                "Used for calories and heart-rate zones. Maximum heart rate is 220 minus your age.",
                style = PulseType.Caption,
            )
            Spacer(Modifier.height(20.dp))
        }
        Label("Units")
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            options = listOf("Metric", "Imperial"),
            selectedIndex = if (ui.metricUnits) 0 else 1,
            onSelect = {
                ui.metricUnits = it == 0
                ui.persistUnits?.invoke(it == 0)
            },
        )
        Spacer(Modifier.height(28.dp))

        // Notifications
        Label("Notifications")
        Spacer(Modifier.height(12.dp))
        PulseCard {
            val rows = listOf(
                Triple("recovery", "Morning recovery", "Your score when you wake up"),
                Triple("bedtime", "Bedtime reminder", "When it is time to wind down"),
                Triple("battery", "Low battery", "When the band needs charging"),
                Triple("sync", "Sync problems", "If the band cannot reach the app"),
            )
            rows.forEach { (key, title, subtitle) ->
                SwitchRow(title, subtitle, ui.notifications[key] == true) { ui.notifications[key] = it; ui.persistNotification?.invoke(key, it) }
            }
            Spacer(Modifier.height(14.dp))
            PulseButton(
                "Send a test notification",
                onClick = {
                    testResult = if (ui.testNotification?.invoke() == true) {
                        "Sent. Look in your notification shade."
                    } else {
                        "Notifications are blocked. Allow them in Android Settings > Apps > Pulse > Notifications."
                    }
                },
                filled = false,
            )
            testResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = PulseType.Caption)
            }
        }
        Spacer(Modifier.height(28.dp))

        // Data
        Label("Data")
        Spacer(Modifier.height(12.dp))
        PulseCard {
            SwitchRow(
                "Show sample data",
                "Made-up numbers to preview the design. Your real data stays empty.",
                ui.useSample,
            ) { ui.useSample = it }
        }
        Spacer(Modifier.height(28.dp))

        Label("How Pulse measures this")
        Spacer(Modifier.height(12.dp))
        PulseCard {
            listOf(
                "Steps" to "Counted by the band.",
                "Heart rate" to "The band's optical sensor, one reading about every 5 minutes.",
                "Calories" to "Estimated from your height, weight, age and heart rate: resting burn plus extra when your heart rate is up.",
                "Sleep" to "Estimated from heart rate and movement, not measured directly.",
                "Recovery, strain, energy" to "Pulse's own formulas, tuned over time. Recovery needs 5 nights.",
                "Not measured yet" to "HRV, breathing rate and blood oxygen.",
            ).forEach { (title, text) ->
                Text(title, style = PulseType.Title.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp))
                Spacer(Modifier.height(2.dp))
                Text(text, style = PulseType.Caption)
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(32.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PulseLockup(Modifier.height(72.dp))
            Spacer(Modifier.height(10.dp))
            Text("Version 0.0.1", style = PulseType.Caption)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Wellness only. Readings from a consumer band's optical sensor are for personal trends, not medical advice or diagnosis.",
            style = PulseType.Caption,
        )
        Spacer(Modifier.height(48.dp))
    }

    if (editingProfile) {
        EditProfileDialog(
            user = user,
            metric = ui.metricUnits,
            onSave = { updated ->
                ui.saveProfile?.invoke(updated)
                editingProfile = false
            },
            onDismiss = { editingProfile = false },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${device.name}?") },
            text = { Text("Pulse will stop syncing with this band. Keep the heart-rate and step history already on your phone, or delete it.") },
            confirmButton = {
                Row {
                    TextButton(onClick = { confirmRemove = false; ui.removeDeviceAction?.invoke(true) }) { Text("Delete data too") }
                    TextButton(onClick = { confirmRemove = false; ui.removeDeviceAction?.invoke(false) }) { Text("Keep data") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BatteryBar(fraction: Float) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "battery")
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PulseColors.Track)) {
        Box(
            Modifier.fillMaxWidth(animated).fillMaxHeight()
                .background(if (fraction < 0.2f) PulseColors.Red else PulseColors.Green),
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PulseType.Title.copy(fontWeight = FontWeight.Normal, fontSize = 15.sp))
            Text(subtitle, style = PulseType.Caption)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PulseColors.Background,
                checkedTrackColor = PulseColors.Green,
                checkedBorderColor = PulseColors.Green,
                uncheckedThumbColor = PulseColors.TextSecondary,
                uncheckedTrackColor = PulseColors.Track,
                uncheckedBorderColor = PulseColors.Border,
            ),
        )
    }
}

@Composable
private fun EditProfileDialog(user: UserProfile, metric: Boolean, onSave: (UserProfile) -> Unit, onDismiss: () -> Unit) {
    val totalInches = (user.heightCm / 2.54).roundToInt()
    var name by remember { mutableStateOf(user.name) }
    var cm by remember { mutableStateOf(String.format(Locale.US, "%.0f", user.heightCm)) }
    var feet by remember { mutableStateOf((totalInches / 12).toString()) }
    var inches by remember { mutableStateOf((totalInches % 12).toString()) }
    var weight by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", if (metric) user.weightKg else user.weightKg * 2.20462))
    }
    var age by remember { mutableStateOf(user.ageYears.toString()) }
    var male by remember { mutableStateOf(user.male) }

    val heightCm: Double? = if (metric) {
        cm.toDoubleOrNull()
    } else {
        val f = feet.toIntOrNull()
        val i = inches.toIntOrNull()
        if (f != null && i != null) (f * 12 + i) * 2.54 else null
    }
    val weightKg: Double? = weight.toDoubleOrNull()?.let { if (metric) it else it / 2.20462 }
    val ageYears: Int? = age.toIntOrNull()
    val valid = name.isNotBlank() &&
        heightCm != null && heightCm in 100.0..250.0 &&
        weightKg != null && weightKg in 30.0..250.0 &&
        ageYears != null && ageYears in 10..100

    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                if (metric) {
                    OutlinedTextField(cm, { cm = it }, label = { Text("Height (cm)") }, singleLine = true, keyboardOptions = number)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(feet, { feet = it }, label = { Text("Feet") }, singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f))
                        OutlinedTextField(inches, { inches = it }, label = { Text("Inches") }, singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f))
                    }
                }
                OutlinedTextField(
                    weight, { weight = it }, label = { Text(if (metric) "Weight (kg)" else "Weight (lb)") },
                    singleLine = true, keyboardOptions = decimal,
                )
                OutlinedTextField(age, { age = it }, label = { Text("Age") }, singleLine = true, keyboardOptions = number)
                SegmentedControl(options = listOf("Male", "Female"), selectedIndex = if (male) 0 else 1, onSelect = { male = it == 0 })
                Text("Sex is only used in the calorie estimate.", style = PulseType.Caption)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(UserProfile(name.trim(), heightCm ?: user.heightCm, weightKg ?: user.weightKg, ageYears ?: user.ageYears, male)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
