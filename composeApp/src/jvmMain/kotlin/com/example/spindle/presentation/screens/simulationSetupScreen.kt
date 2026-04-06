package com.example.spindle.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spindle.domain.models.ShaftSegment
import com.example.spindle.domain.models.SimulationMode
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.components.ScreenFadeIn
import com.example.spindle.presentation.components.StatusBadge
import com.example.spindle.presentation.components.rememberStaggerAlpha
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.NavyDeepest
import com.example.spindle.presentation.theme.NavyMid
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.SlateMid
import com.example.spindle.presentation.theme.TealBright
import com.example.spindle.presentation.theme.TealSubtle
import com.example.spindle.presentation.theme.TextPrimary
import com.example.spindle.presentation.theme.TextSecondary
import com.example.spindle.presentation.viewmodels.SetupIntent
import com.example.spindle.presentation.viewmodels.SimulationSetupState


// ══════════════════════════════════════════════════════════════════
//  SimulationSetupScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun SimulationSetupScreen(
    state: SimulationSetupState      = SimulationSetupState(),
    onIntent: (SetupIntent) -> Unit  = {},
    modifier: Modifier               = Modifier
) {
    val scrollState = rememberScrollState()
    val a0 = rememberStaggerAlpha(0, 60)
    val a1 = rememberStaggerAlpha(1, 60)
    val a2 = rememberStaggerAlpha(2, 60)
    val a3 = rememberStaggerAlpha(3, 60)

    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

            // Header
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a0 },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Simulation Setup",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        "${state.geometry.segments.size} segments · ${state.bearingSystem.bearings.size} bearings · " +
                                "L = ${"%.0f".format(state.geometry.totalLengthMm)} mm",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onIntent(SetupIntent.ResetForm) },
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, GlassRim),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Reset") }
                    Button(
                        onClick = { onIntent(SetupIntent.Submit) }, enabled = !state.isSubmitting,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealBright,
                            contentColor = NavyDeepest
                        )
                    ) {
                        Text(
                            if (state.isSubmitting) "⟳ Validating…" else "▶ Launch Simulation",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Errors
            if (state.fieldErrors.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth(), rimColor = RedDanger.copy(0.5f)) {
                    state.fieldErrors.values.forEach { err ->
                        Text(
                            "• $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = RedDanger
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Operating conditions
            GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a1 }) {
                Text(
                    "Operating Conditions",
                    style = MaterialTheme.typography.titleSmall,
                    color = TealBright
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.projectName,
                    onValueChange = { onIntent(SetupIntent.UpdateProjectName(it)) },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = setupFieldColors(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(10.dp))
                SetupSlider(
                    "Spindle Speed",
                    state.operatingSpeedRpm.toFloat(),
                    500f..15_000f,
                    "RPM",
                    "%.0f"
                )
                { onIntent(SetupIntent.UpdateRpm(it)) }
                SetupSlider("Radial Load", state.radialLoadN.toFloat(), 0f..15_000f, "N")
                { onIntent(SetupIntent.UpdateRadialLoad(it)) }
                SetupSlider("Axial Load", state.axialLoadN.toFloat(), 0f..5_000f, "N")
                { onIntent(SetupIntent.UpdateAxialLoad(it)) }
            }

            Spacer(Modifier.height(16.dp))

            // Shaft segments (minimal inline editor)
            GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a2 }) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(
                        "Shaft Segments (${state.geometry.segments.size})",
                        style = MaterialTheme.typography.titleSmall, color = TealBright
                    )
                    OutlinedButton(
                        onClick = {
                            val segs = state.geometry.segments.toMutableList()
                            val last = segs.last()
                            segs.add(
                                ShaftSegment(
                                    segs.size, "Segment ${segs.size + 1}",
                                    80.0, last.outerDiameterMm * 0.9, last.innerDiameterMm
                                )
                            )
                            onIntent(SetupIntent.UpdateGeometry(state.geometry.copy(segments = segs)))
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TealBright.copy(0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                    ) { Text("+ Add Segment") }
                }
                Spacer(Modifier.height(8.dp))
                state.geometry.segments.forEachIndexed { idx, seg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${idx + 1}. ${seg.name}", Modifier.weight(1.5f),
                            style = MaterialTheme.typography.labelMedium, color = TextPrimary
                        )
                        Text(
                            "OD ${seg.outerDiameterMm}mm", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TealBright
                        )
                        Text(
                            "ID ${seg.innerDiameterMm}mm", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary
                        )
                        Text(
                            "L ${seg.lengthMm}mm", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary
                        )
                        if (state.geometry.segments.size > 1) {
                            TextButton(onClick = {
                                val newSegs = state.geometry.segments.toMutableList()
                                    .also { it.removeAt(idx) }
                                    .mapIndexed { i, s -> s.copy(segmentId = i) }
                                onIntent(SetupIntent.UpdateGeometry(state.geometry.copy(segments = newSegs)))
                            }) {
                                Text(
                                    "✕",
                                    color = RedDanger,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bearings
            GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a2 }) {
                Text(
                    "Bearing System (${state.bearingSystem.bearings.size} bearings)",
                    style = MaterialTheme.typography.titleSmall, color = TealBright
                )
                Spacer(Modifier.height(8.dp))
                state.bearingSystem.bearings.forEach { b ->
                    val rpmOk = state.operatingSpeedRpm <= b.limitingSpeedRpm
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "${b.bearingId + 1}. ${b.designation}", Modifier.weight(1.5f),
                            style = MaterialTheme.typography.labelMedium, color = TextPrimary
                        )
                        Text(
                            "Z = ${b.zPositionMm}mm", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary
                        )
                        Text(
                            b.role.label, Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TealBright
                        )
                        if (!rpmOk) StatusBadge("OVER LIMIT", RedDanger)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Simulation mode
            GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a3 }) {
                Text(
                    "Analysis Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = TealBright
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SimulationMode.STATIC, SimulationMode.MONTE_CARLO,
                        SimulationMode.FULL_ANALYSIS, SimulationMode.ML_SURROGATE
                    ).forEach { mode ->
                        val sel = state.simulationMode == mode
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (sel) TealSubtle else NavyMid)
                                .border(
                                    1.dp,
                                    if (sel) TealBright else GlassRim,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onIntent(SetupIntent.UpdateSimMode(mode)) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                mode.name.replace("_", " "),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) TealBright else TextSecondary,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        state.enableFEA, { onIntent(SetupIntent.UpdateEnableFEA(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NavyDeepest,
                            checkedTrackColor = TealBright, uncheckedTrackColor = SlateMid
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Enable ANSYS FEA — set ANSYS_DRY_RUN=1 for demo mode",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                }
            }
        } // end ScreenFadeIn
    }
}
