package com.example.spindle.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.GreenSafe
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.SlateMid
import com.example.spindle.presentation.theme.TealBright
import com.example.spindle.presentation.theme.TextDisabled
import com.example.spindle.presentation.theme.TextPrimary
import com.example.spindle.presentation.theme.TextSecondary


// ── Shared screen primitives ───────────────────────────────────────

@Composable
fun KpiCard(label: String, value: String, good: Boolean = false, bad: Boolean = false, modifier: Modifier) {
    GlassCard(modifier, rimColor = when { good -> GreenSafe.copy(0.4f); bad -> RedDanger.copy(0.4f); else -> GlassRim }) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium,
            color = when { good -> GreenSafe; bad -> RedDanger; else -> TealBright },
            fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SetupSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                unit: String, format: String = "%.1f", enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextSecondary else TextDisabled)
            Text("${format.format(value)} $unit", style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TealBright else TextDisabled, fontWeight = FontWeight.SemiBold)
        }
        Slider(value, onChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = TealBright,
                inactiveTrackColor = SlateMid, disabledThumbColor = TextDisabled,
                disabledActiveTrackColor = TextDisabled))
    }
}

@Composable
fun setupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TealBright, unfocusedBorderColor = GlassRim,
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedLabelColor = TealBright, unfocusedLabelColor = TextSecondary,
    cursorColor = TealBright, errorBorderColor = RedDanger
)
