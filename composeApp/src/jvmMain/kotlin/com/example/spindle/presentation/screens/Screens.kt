package com.example.spindle.presentation.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spindle.presentation.components.*
import com.example.spindle.presentation.theme.*
import com.example.spindle.presentation.viewmodels.*
import com.example.spindle.domain.models.*

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
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Simulation Setup",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary)
                Text("${state.geometry.segments.size} segments · ${state.bearingSystem.bearings.size} bearings · " +
                    "L = ${"%.0f".format(state.geometry.totalLengthMm)} mm",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onIntent(SetupIntent.ResetForm) },
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, GlassRim),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Reset") }
                Button(onClick = { onIntent(SetupIntent.Submit) }, enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealBright, contentColor = NavyDeepest)
                ) { Text(if (state.isSubmitting) "⟳ Validating…" else "▶ Launch Simulation",
                    fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Errors
        if (state.fieldErrors.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), rimColor = RedDanger.copy(0.5f)) {
                state.fieldErrors.values.forEach { err ->
                    Text("• $err", style = MaterialTheme.typography.bodySmall, color = RedDanger)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Operating conditions
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Operating Conditions", style = MaterialTheme.typography.titleSmall, color = TealBright)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.projectName, onValueChange = { onIntent(SetupIntent.UpdateProjectName(it)) },
                label = { Text("Project Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = setupFieldColors(), shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(10.dp))
            SetupSlider("Spindle Speed", state.operatingSpeedRpm.toFloat(), 500f..15_000f, "RPM", "%.0f")
                { onIntent(SetupIntent.UpdateRpm(it)) }
            SetupSlider("Radial Load",   state.radialLoadN.toFloat(),    0f..15_000f, "N")
                { onIntent(SetupIntent.UpdateRadialLoad(it)) }
            SetupSlider("Axial Load",    state.axialLoadN.toFloat(),     0f..5_000f,  "N")
                { onIntent(SetupIntent.UpdateAxialLoad(it)) }
        }

        Spacer(Modifier.height(16.dp))

        // Shaft segments (minimal inline editor)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Shaft Segments (${state.geometry.segments.size})",
                    style = MaterialTheme.typography.titleSmall, color = TealBright)
                OutlinedButton(onClick = {
                    val segs = state.geometry.segments.toMutableList()
                    val last = segs.last()
                    segs.add(ShaftSegment(segs.size, "Segment ${segs.size+1}",
                        80.0, last.outerDiameterMm * 0.9, last.innerDiameterMm))
                    onIntent(SetupIntent.UpdateGeometry(state.geometry.copy(segments = segs)))
                }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TealBright.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                ) { Text("+ Add Segment") }
            }
            Spacer(Modifier.height(8.dp))
            state.geometry.segments.forEachIndexed { idx, seg ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.spacedBy(8.dp)) {
                    Text("${idx+1}. ${seg.name}", Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Text("OD ${seg.outerDiameterMm}mm", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = TealBright)
                    Text("ID ${seg.innerDiameterMm}mm", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("L ${seg.lengthMm}mm", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    if (state.geometry.segments.size > 1) {
                        TextButton(onClick = {
                            val newSegs = state.geometry.segments.toMutableList().also { it.removeAt(idx) }
                                .mapIndexed { i, s -> s.copy(segmentId = i) }
                            onIntent(SetupIntent.UpdateGeometry(state.geometry.copy(segments = newSegs)))
                        }) { Text("✕", color = RedDanger, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bearings
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Bearing System (${state.bearingSystem.bearings.size} bearings)",
                style = MaterialTheme.typography.titleSmall, color = TealBright)
            Spacer(Modifier.height(8.dp))
            state.bearingSystem.bearings.forEach { b ->
                val rpmOk = state.operatingSpeedRpm <= b.limitingSpeedRpm
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.spacedBy(12.dp)) {
                    Text("${b.bearingId+1}. ${b.designation}", Modifier.weight(1.5f),
                        style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Text("Z = ${b.zPositionMm}mm", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(b.role.label, Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = TealBright)
                    if (!rpmOk) StatusBadge("OVER LIMIT", RedDanger)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Simulation mode
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Analysis Mode", style = MaterialTheme.typography.titleSmall, color = TealBright)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SimulationMode.STATIC, SimulationMode.MONTE_CARLO,
                       SimulationMode.FULL_ANALYSIS, SimulationMode.ML_SURROGATE).forEach { mode ->
                    val sel = state.simulationMode == mode
                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) TealSubtle else NavyMid)
                        .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(8.dp))
                        .clickable { onIntent(SetupIntent.UpdateSimMode(mode)) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text(mode.name.replace("_"," "), style = MaterialTheme.typography.labelMedium,
                        color = if (sel) TealBright else TextSecondary,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(state.enableFEA, { onIntent(SetupIntent.UpdateEnableFEA(it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyDeepest,
                        checkedTrackColor = TealBright, uncheckedTrackColor = SlateMid))
                Spacer(Modifier.width(8.dp))
                Text("Enable ANSYS FEA — set ANSYS_DRY_RUN=1 for demo mode",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Preview
@Composable
private fun SimulationSetupScreenPreview() {
    SpindleTheme {
        SimulationSetupScreen()
    }
}

// ══════════════════════════════════════════════════════════════════
//  AnalyticsScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun AnalyticsScreen(
    state: AnalyticsState           = AnalyticsState(),
    onIntent: (AnalyticsIntent) -> Unit = {},
    modifier: Modifier              = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Analytics", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text(if (state.resultId != null) "Result: ${state.resultId!!.takeLast(8).uppercase()}"
                     else "Run a simulation to see results",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (state.taguchiResult == null) {
                OutlinedButton(onClick = { onIntent(AnalyticsIntent.LoadDemoTaguchi) },
                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TealBright.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                ) { Text("Load Demo Taguchi L9", style = MaterialTheme.typography.labelMedium) }
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TealBright)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            return@Column
        }

        Spacer(Modifier.height(20.dp))

        // Static KPIs
        if (state.staticDeflectionMm > 0 || state.maxVonMisesMpa > 0) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                KpiCard("Tool Tip Deflection", "${"%.4f".format(state.toolTipDeflectionMm)} mm",
                    bad = state.toolTipDeflectionMm > 0.05, modifier = Modifier.weight(1f))
                KpiCard("Max Von Mises", "${"%.1f".format(state.maxVonMisesMpa)} MPa",
                    bad = state.maxVonMisesMpa > 400, modifier = Modifier.weight(1f))
                KpiCard("Safety Factor (yield)", "${"%.2f".format(state.safetyFactorYield)}",
                    good = state.safetyFactorYield >= 2.0, modifier = Modifier.weight(1f))
                KpiCard("Safety Factor (fatigue)", "${"%.2f".format(state.safetyFactorFatigue)}",
                    good = state.safetyFactorFatigue >= 1.5, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Modal
        if (state.modeShapes.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Modal Analysis", style = MaterialTheme.typography.titleSmall, color = TealBright)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("1st Natural Freq.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        val f1 = state.modeShapes.first().naturalFrequencyHz
                        Text("${"%.1f".format(f1)} Hz", style = MaterialTheme.typography.titleSmall,
                            color = if (f1 >= 350) GreenSafe else RedDanger, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("1st Critical Speed", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${"%.0f".format(state.firstCriticalSpeedRpm)} RPM",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.firstCriticalSpeedRpm > 15_000) GreenSafe else AmberWarning,
                            fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Safety Factor", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("${"%.2f".format(state.criticalSpeedSafetyFactor)}×",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.criticalSpeedSafetyFactor >= 1.4) GreenSafe else RedDanger,
                            fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                state.modeShapes.take(4).forEach { mode ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                        Text("Mode ${mode.modeNumber}: ${mode.modeType}",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("${"%.1f".format(mode.naturalFrequencyHz)} Hz",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (mode.naturalFrequencyHz >= 350) TealBright else RedDanger,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Monte Carlo
        if (state.iterations > 0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Monte Carlo (${state.iterations} iter.)", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    StatusBadge(
                        if (state.pofPct < 5) "RELIABLE" else if (state.pofPct < 15) "MARGINAL" else "CRITICAL",
                        if (state.pofPct < 5) GreenSafe else if (state.pofPct < 15) AmberWarning else RedDanger
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(
                        "P(Failure)"     to "${"%.2f".format(state.pofPct)} %",
                        "Reliability"    to "${"%.2f".format(state.reliabilityPct)} %",
                        "Defl. Mean"     to "${"%.4f".format(state.deflectionMeanMm)} mm",
                        "Defl. 95th"     to "${"%.4f".format(state.deflectionP95Mm)} mm",
                        "Freq. 5th"      to "${"%.1f".format(state.freqP5Hz)} Hz",
                    ).forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(value, style = MaterialTheme.typography.titleSmall, color = TealBright, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bearing life
        if (state.bearingLives.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("ISO 281 Bearing Life", style = MaterialTheme.typography.titleSmall, color = TealBright)
                Spacer(Modifier.height(8.dp))
                state.bearingLives.forEachIndexed { i, life ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), 1.dp, GlassRim)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Bearing ${life.bearingId+1}: ${life.designation}",
                            style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Text("L10 = ${"%.0f".format(life.l10LifeMeanHours)} h",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (life.l10LifeMeanHours >= 20_000) GreenSafe else RedDanger,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Taguchi
        state.taguchiResult?.let { tag ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Taguchi Robust Design (${tag.arrayType})", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    StatusBadge("OPTIMAL", GreenSafe)
                }
                Spacer(Modifier.height(8.dp))
                Text("Optimal factor levels:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    tag.optimalFactorLevels.entries.take(4).forEach { (k, v) ->
                        Column {
                            Text(k, style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                            Text("${"%.1f".format(v)}", style = MaterialTheme.typography.labelMedium,
                                color = TealBright, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Predicted Deflection", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("≤ ${"%.4f".format(tag.predictedOptimumDeflectionMm)} mm",
                            style = MaterialTheme.typography.titleSmall, color = GreenSafe, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("Predicted Frequency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("≥ ${"%.1f".format(tag.predictedOptimumFreqHz)} Hz",
                            style = MaterialTheme.typography.titleSmall, color = GreenSafe, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AnalyticsScreenPreview() {
    SpindleTheme {
        AnalyticsScreen()
    }
}

// ══════════════════════════════════════════════════════════════════
//  OptimizationScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun OptimizationScreen(
    state: OptimisationState              = OptimisationState(),
    lastConfig: SpindleConfig? = null,
    onIntent: (OptimisationIntent) -> Unit = {},
    modifier: Modifier                    = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Genetic Algorithm Optimisation",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text("NSGA-II · 3 objectives: deflection · mass · frequency",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.phase == OptPhase.EVOLVING) {
                    OutlinedButton(onClick = { onIntent(OptimisationIntent.StopEvolution) },
                        border = BorderStroke(1.dp, RedDanger), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger)
                    ) { Text("■ Stop") }
                }
                Button(
                    onClick = {
                        lastConfig?.let { onIntent(OptimisationIntent.StartEvolution(it)) }
                    },
                    enabled = state.phase == OptPhase.IDLE && lastConfig != null,
                    shape   = RoundedCornerShape(10.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = TealBright, contentColor = NavyDeepest,
                        disabledContainerColor = TealSubtle, disabledContentColor = TextDisabled)
                ) {
                    Text(when {
                        lastConfig == null            -> "⚠ Run Setup first"
                        state.phase == OptPhase.EVOLVING -> "⟳ Evolving…"
                        state.phase == OptPhase.CONVERGED -> "✓ Converged"
                        else -> "▶ Start Evolution"
                    }, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (lastConfig == null) {
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth(), rimColor = AmberWarning.copy(0.5f)) {
                Text("⚠ No simulation has been run yet. Complete a Simulation Setup first.",
                    style = MaterialTheme.typography.bodySmall, color = AmberWarning)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Progress strip
        GlassCard(Modifier.fillMaxWidth(), rimColor = if (state.phase == OptPhase.EVOLVING) TealBright.copy(0.5f) else GlassRim) {
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(20.dp), Alignment.CenterVertically) {
                Column {
                    Text("Generation", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${state.currentGeneration} / ${state.maxGenerations}",
                        style = MaterialTheme.typography.headlineSmall, color = TealBright, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Best Deflection", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${"%.4f".format(state.bestDeflHistory.lastOrNull() ?: 0.0)} mm",
                        style = MaterialTheme.typography.titleMedium, color = GreenSafe)
                }
                Column {
                    Text("Best Frequency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${"%.1f".format(state.bestFreqHistory.lastOrNull() ?: 0.0)} Hz",
                        style = MaterialTheme.typography.titleMedium,
                        color = if ((state.bestFreqHistory.lastOrNull() ?: 0.0) >= 350) GreenSafe else RedDanger)
                }
                Column {
                    Text("Constraint Violations", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("${state.constraintViolations}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.constraintViolations == 0) GreenSafe else AmberWarning)
                }
                Column(Modifier.weight(1f)) {
                    GlassProgressBar(state.currentGeneration.toFloat() / state.maxGenerations.coerceAtLeast(1),
                        Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("${state.currentGeneration * 100 / state.maxGenerations.coerceAtLeast(1)}%  " +
                        "improvement: ${"%.1f".format(state.improvementPct)}%",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                val (sl, sc) = when (state.phase) {
                    OptPhase.IDLE         -> "IDLE"      to TextDisabled
                    OptPhase.INITIALISING -> "INIT"      to BluePrimary
                    OptPhase.EVOLVING     -> "EVOLVING"  to TealBright
                    OptPhase.CONVERGED    -> "CONVERGED" to GreenSafe
                    OptPhase.FAILED       -> "FAILED"    to RedDanger
                }
                StatusBadge(sl, sc)
            }
        }

        if (state.hasConverged && state.convergenceGeneration != null) {
            Spacer(Modifier.height(8.dp))
            GlassCard(Modifier.fillMaxWidth(), rimColor = GreenSafe.copy(0.4f)) {
                Text("✓ Convergence at generation ${state.convergenceGeneration} — < 0.1% improvement over 10 generations",
                    style = MaterialTheme.typography.bodySmall, color = GreenSafe)
            }
        }

        // Pareto front table
        if (state.currentParetoFront.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Pareto Front (${state.currentParetoFront.size} solutions)",
                        style = MaterialTheme.typography.titleSmall, color = TealBright)
                    OutlinedButton(onClick = { onIntent(OptimisationIntent.ExportParetoFront) },
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TealBright.copy(0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                    ) { Text("Export CSV") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().background(GlassSurface2, RoundedCornerShape(4.dp)).padding(6.dp)) {
                    Text("Deflection (mm)", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TealBright)
                    Text("Weight (kg)",     Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TealBright)
                    Text("Frequency (Hz)",  Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = TealBright)
                }
                state.currentParetoFront.take(8).forEach { pt ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 6.dp)) {
                        Text("${"%.5f".format(pt.deflection)}", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pt.deflection <= 0.05) GreenSafe else RedDanger)
                        Text("${"%.2f".format(pt.weight)}", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        Text("${"%.1f".format(pt.freq)}", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pt.freq >= 350) GreenSafe else AmberWarning)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun OptimizationScreenPreview() {
    SpindleTheme {
        OptimizationScreen()
    }
}

// ══════════════════════════════════════════════════════════════════
//  SettingsScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    state: SettingsState                  = SettingsState(),
    onIntent: (SettingsIntent) -> Unit    = {},
    modifier: Modifier                    = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Settings", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                Text("Engine connection, ANSYS path, and preferences",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onIntent(SettingsIntent.RestoreDefaults) },
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, GlassRim),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) { Text("Restore Defaults") }
                Button(onClick = { onIntent(SettingsIntent.Save) }, enabled = !state.isSaving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealBright, contentColor = NavyDeepest)
                ) { Text(if (state.isSaving) "⟳ Saving…" else "✓ Save", fontWeight = FontWeight.Bold) }
            }
        }

        state.lastSavedAt?.let {
            Spacer(Modifier.height(4.dp))
            Text("Last saved: $it", style = MaterialTheme.typography.labelSmall, color = GreenSafe)
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), Arrangement.spacedBy(16.dp)) {
                // Engine connection
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Python Engine Connection", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            state.engineUrl, { onIntent(SettingsIntent.UpdateEngineUrl(it)) },
                            label = { Text("Engine Base URL") }, singleLine = true, modifier = Modifier.weight(1f),
                            isError = state.engineUrlError != null,
                            colors = setupFieldColors(), shape = RoundedCornerShape(8.dp)
                        )
                        Button(onClick = { onIntent(SettingsIntent.TestEngineConnection) },
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GlassSurface2, contentColor = TealBright),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Test") }
                    }
                    state.engineUrlError?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = RedDanger)
                    }
                }
                // ANSYS path
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("ANSYS MAPDL Path", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        state.ansysExecPath, { onIntent(SettingsIntent.UpdateAnsysPath(it)) },
                        label = { Text("ANSYS Executable Path") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), colors = setupFieldColors(), shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("Set ANSYS_DRY_RUN=1 in the terminal to use analytical fallback without ANSYS.",
                        style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                }
            }
            Column(Modifier.weight(1f), Arrangement.spacedBy(16.dp)) {
                // UI prefs
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("UI Preferences", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(state.autoNavigateOnComplete, { onIntent(SettingsIntent.UpdateAutoNavigate(it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NavyDeepest,
                                checkedTrackColor = TealBright, uncheckedTrackColor = SlateMid))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Auto-navigate to Analytics after simulation",
                                style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("When off, results appear in Dashboard status card",
                                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                // About
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("About", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "App"        to "TechPulse Spindle R&D Suite",
                        "Version"    to "2.0.0",
                        "Package"    to "com.example.spindle",
                        "Kotlin"     to "2.1.0",
                        "Compose"    to "1.7.3",
                        "GA"         to "DEAP NSGA-II",
                        "Physics"    to "ANSYS BEAM188 + Block Lanczos",
                        "Renderer"   to "PyVista (OSMesa)"
                    ).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(v, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SpindleTheme {
        SettingsScreen()
    }
}

// ── Shared screen primitives ───────────────────────────────────────

@Composable
private fun KpiCard(label: String, value: String, good: Boolean = false, bad: Boolean = false, modifier: Modifier) {
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
