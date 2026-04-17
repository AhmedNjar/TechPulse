package com.example.spindle.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.GreenSafe
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.TealBright
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
