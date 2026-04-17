package com.example.spindle.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spindle.data.repository.ParetoPoint
import com.example.spindle.domain.models.SpindleConfig
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.components.GlassProgressBar
import com.example.spindle.presentation.components.ScreenFadeIn
import com.example.spindle.presentation.components.StatusBadge
import com.example.spindle.presentation.components.rememberStaggerAlpha
import com.example.spindle.presentation.theme.AmberWarning
import com.example.spindle.presentation.theme.BluePrimary
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.GlassSurface2
import com.example.spindle.presentation.theme.GreenSafe
import com.example.spindle.presentation.theme.NavyDeepest
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.SpindleTheme
import com.example.spindle.presentation.theme.TealBright
import com.example.spindle.presentation.theme.TealSubtle
import com.example.spindle.presentation.theme.TextDisabled
import com.example.spindle.presentation.theme.TextPrimary
import com.example.spindle.presentation.theme.TextSecondary
import com.example.spindle.presentation.viewmodels.OptPhase
import com.example.spindle.presentation.viewmodels.OptimisationIntent
import com.example.spindle.presentation.viewmodels.OptimisationState


// ══════════════════════════════════════════════════════════════════
//  OptimizationScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun OptimizationScreen(
    state: OptimisationState               = OptimisationState(),
    lastConfig: SpindleConfig?             = null,
    onIntent: (OptimisationIntent) -> Unit = {},
    modifier: Modifier                     = Modifier
) {
    val scrollState = rememberScrollState()
    val a0 = rememberStaggerAlpha(0, 60)
    val a1 = rememberStaggerAlpha(1, 60)
    val a2 = rememberStaggerAlpha(2, 60)

    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a0 },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                //style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
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
            GlassCard(
                modifier  = Modifier.fillMaxWidth().graphicsLayer { alpha = a1 },
                rimColor  = AmberWarning.copy(alpha = 0.5f)
            ) {
                Text("⚠ No simulation has been run yet. Complete a Simulation Setup first.",
                    style = MaterialTheme.typography.bodySmall, color = AmberWarning)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Progress strip
        GlassCard(
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = a2 },
            rimColor = if (state.phase == OptPhase.EVOLVING) TealBright.copy(alpha = 0.5f) else GlassRim
        ) {
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
    } // end Column
} // end ScreenFadeIn


// ══════════════════════════════════════════════════════════════════
//  @Preview — OptimizationScreen
// ══════════════════════════════════════════════════════════════════

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun OptimisationIdleNoConfigPreview() {
    SpindleTheme {
        OptimizationScreen(
            state      = OptimisationState(phase = OptPhase.IDLE),
            lastConfig = null
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun OptimisationEvolvingPreview() {
    SpindleTheme {
        OptimizationScreen(
            state = OptimisationState(
                phase               = OptPhase.EVOLVING,
                currentGeneration   = 28,
                maxGenerations      = 50,
                populationSize      = 120,
                constraintViolations = 3,
                improvementPct      = 18.4,
                bestFitnessHistory  = (1..28).map { 0.055 - it * 0.0015 },
                meanFitnessHistory  = (1..28).map { 0.070 - it * 0.0013 },
                bestDeflHistory     = (1..28).map { 0.049 - it * 0.001 },
                bestFreqHistory     = (1..28).map { 290.0 + it * 4.5 },
                currentParetoFront  = listOf(
                    ParetoPoint(0.031, 4.2, 412.0),
                    ParetoPoint(0.038, 3.8, 438.0),
                    ParetoPoint(0.044, 3.4, 471.0),
                    ParetoPoint(0.051, 3.1, 508.0),
                )
            ),
            lastConfig = SpindleConfig.default("CNC-5000 Rev C")
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun OptimisationConvergedPreview() {
    SpindleTheme {
        OptimizationScreen(
            state = OptimisationState(
                phase                = OptPhase.CONVERGED,
                currentGeneration    = 50,
                maxGenerations       = 50,
                hasConverged         = true,
                convergenceGeneration = 43,
                constraintViolations = 0,
                improvementPct       = 24.7,
                bestDeflHistory      = (1..50).map { 0.049 - it * 0.0007 },
                bestFreqHistory      = (1..50).map { 285.0 + it * 3.5 },
                currentParetoFront   = listOf(
                    ParetoPoint(0.028, 4.5, 401.0),
                    ParetoPoint(0.033, 4.0, 432.0),
                    ParetoPoint(0.039, 3.6, 468.0),
                    ParetoPoint(0.046, 3.2, 511.0),
                    ParetoPoint(0.052, 2.9, 549.0),
                )
            ),
            lastConfig = SpindleConfig.default("CNC-5000 Rev C")
        )
    }
}
