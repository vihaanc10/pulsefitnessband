package app.pulse.spike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pulse.spike.data.UserProfile
import app.pulse.spike.ui.components.PulseButton
import app.pulse.spike.ui.components.PulseLockup
import app.pulse.spike.ui.components.SegmentedControl
import app.pulse.spike.ui.theme.PulseColors
import app.pulse.spike.ui.theme.PulseType

/**
 * Shown the first time the app opens. No account and no internet: the answers stay on the phone and
 * are used for calories and heart-rate zones.
 */
@Composable
fun OnboardingScreen(ui: PulseUiState) {
    var metric by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var cm by remember { mutableStateOf("") }
    var feet by remember { mutableStateOf("") }
    var inches by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var male by remember { mutableStateOf(true) }

    val heightCm: Double? = if (metric) {
        cm.toDoubleOrNull()
    } else {
        val f = feet.toIntOrNull()
        val i = inches.toIntOrNull() ?: if (inches.isBlank()) 0 else null
        if (f != null && i != null) (f * 12 + i) * 2.54 else null
    }
    val weightKg: Double? = weight.toDoubleOrNull()?.let { if (metric) it else it / 2.20462 }
    val ageYears: Int? = age.toIntOrNull()
    val valid = name.isNotBlank() &&
        heightCm != null && heightCm in 100.0..250.0 &&
        weightKg != null && weightKg in 30.0..250.0 &&
        ageYears != null && ageYears in 10..100

    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        Modifier.fillMaxSize().background(PulseColors.Background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        PulseLockup(Modifier.height(96.dp))
        Spacer(Modifier.height(32.dp))
        Text("Welcome to Pulse", style = PulseType.Headline)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tell us a little about you. It helps Pulse work out your calories and heart-rate zones. " +
                "There is no account to create, and everything stays on this phone.",
            style = PulseType.Body,
        )
        Spacer(Modifier.height(28.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SegmentedControl(listOf("Metric (cm, kg)", "Imperial (ft, lb)"), if (metric) 0 else 1, { metric = it == 0 })
            if (metric) {
                OutlinedTextField(
                    cm, { cm = it }, label = { Text("Height (cm)") }, singleLine = true,
                    keyboardOptions = number, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(feet, { feet = it }, label = { Text("Feet") }, singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f))
                    OutlinedTextField(inches, { inches = it }, label = { Text("Inches") }, singleLine = true, keyboardOptions = number, modifier = Modifier.weight(1f))
                }
            }
            OutlinedTextField(
                weight, { weight = it }, label = { Text(if (metric) "Weight (kg)" else "Weight (lb)") },
                singleLine = true, keyboardOptions = decimal, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(age, { age = it }, label = { Text("Age") }, singleLine = true, keyboardOptions = number, modifier = Modifier.fillMaxWidth())
            SegmentedControl(listOf("Male", "Female"), if (male) 0 else 1, { male = it == 0 })
            Text("Sex is only used in the calorie estimate.", style = PulseType.Caption)
        }

        Spacer(Modifier.height(32.dp))
        if (valid) {
            PulseButton(
                "Get started",
                onClick = {
                    ui.completeOnboarding?.invoke(
                        UserProfile(name.trim(), heightCm ?: 175.0, weightKg ?: 70.0, ageYears ?: 25, male),
                        metric,
                    )
                },
            )
        } else {
            Text("Fill in every box to continue.", style = PulseType.Caption)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Pulse is for wellness, not medical advice. Heart-rate and sleep readings from a consumer band are estimates.",
            style = PulseType.Caption,
        )
        Spacer(Modifier.height(36.dp))
    }
}
