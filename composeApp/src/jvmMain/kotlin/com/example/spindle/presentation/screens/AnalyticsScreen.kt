package com.example.spindle.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spindle.domain.models.ISO281BearingLife
import com.example.spindle.domain.models.ModeShape
import com.example.spindle.domain.models.SimulationMode
import com.example.spindle.domain.models.TaguchiControlFactor
import com.example.spindle.domain.models.TaguchiResponseTableRow
import com.example.spindle.domain.models.TaguchiResult
import com.example.spindle.domain.models.TaguchiRun
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.components.ScreenFadeIn
import com.example.spindle.presentation.components.StatusBadge
import com.example.spindle.presentation.components.rememberStaggerAlpha
import com.example.spindle.presentation.theme.AmberWarning
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.GreenSafe
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.SpindleTheme
import com.example.spindle.presentation.theme.TealBright
import com.example.spindle.presentation.theme.TealDeep
import com.example.spindle.presentation.theme.TextDisabled
import com.example.spindle.presentation.theme.TextPrimary
import com.example.spindle.presentation.theme.TextSecondary
import com.example.spindle.presentation.viewmodels.AnalyticsIntent
import com.example.spindle.presentation.viewmodels.AnalyticsState
import com.example.spindle.presentation.viewmodels.AnalyticsTab
import kotlin.collections.component1
import kotlin.collections.component2


// ══════════════════════════════════════════════════════════════════
//  AnalyticsScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun AnalyticsScreen(
    state: AnalyticsState               = AnalyticsState(),
    onIntent: (AnalyticsIntent) -> Unit = {},
    modifier: Modifier                  = Modifier
) {
    val scrollState = rememberScrollState()
    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

            // ── Header ─────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Analytics", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Text(if (state.resultId != null) "Result: ${state.resultId!!.takeLast(8).uppercase()}"
                    else "Run a simulation to see results",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.taguchiResult == null) {
                        OutlinedButton(onClick = { onIntent(AnalyticsIntent.LoadDemoTaguchi) },
                            shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, TealBright.copy(0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                        ) { Text("Load Demo", style = MaterialTheme.typography.labelMedium) }
                    }
                    OutlinedButton(onClick = { onIntent(AnalyticsIntent.ExportReport) },
                        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, GlassRim),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("⬇ Export PDF", style = MaterialTheme.typography.labelMedium) }
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

            Spacer(Modifier.height(16.dp))

            // ── Tab Bar ────────────────────────────────────────────────
            AnalyticsTabBar(activeTab = state.activeTab,
                onTabSelect = { onIntent(AnalyticsIntent.SwitchTab(it)) })

            Spacer(Modifier.height(16.dp))

            // ── Tab routing ────────────────────────────────────────────
            when (state.activeTab) {
                AnalyticsTab.FRF -> {
                    FrfChart(state.frfPoints, state.frfNaturalFreqsHz)
                    return@Column
                }
                AnalyticsTab.CAMPBELL -> {
                    CampbellChart(state.campbellData, state.operatingSpeedRpm, state.campbellCrossings)
                    return@Column
                }
                AnalyticsTab.SOBOL -> {
                    GlassCard(Modifier.fillMaxWidth(), rimColor = TealBright.copy(alpha = 0.3f)) {
                        Text("Sobol S1 Indices (from last run)", style = MaterialTheme.typography.titleSmall, color = TealBright)
                        Spacer(Modifier.height(8.dp))
                        if (state.sensitivityFactors.isEmpty()) {
                            Text("Run a full analysis or open the Sobol screen.",
                                style = MaterialTheme.typography.bodySmall, color = TextDisabled)
                        } else {
                            val maxS = state.sensitivityFactors.values.maxOrNull()?.coerceAtLeast(0.01) ?: 0.01
                            state.sensitivityFactors.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(k, Modifier.width(140.dp), style = MaterialTheme.typography.labelMedium, color = TextPrimary, maxLines = 1)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.height(10.dp).weight((v/maxS).toFloat().coerceAtLeast(0.01f))
                                        .background(Brush.horizontalGradient(listOf(TealDeep, TealBright)), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${"%.3f".format(v)}", style = MaterialTheme.typography.labelSmall, color = TealBright)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ReliabilityGaugesCard(state.reliabilityGauges, state.dominantDriver)
                    Spacer(Modifier.height(12.dp))
                    TirBreakdownCard(state.tirComponents, state.totalTirMm)
                    return@Column
                }
                AnalyticsTab.SUMMARY -> { /* fall through to summary content below */ }
            }

            Spacer(Modifier.height(4.dp))

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
                val taguchiAlpha = rememberStaggerAlpha(5, 60)
                GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = taguchiAlpha }) {
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
        } // end Column
    } // end ScreenFadeIn
}


// ══════════════════════════════════════════════════════════════════
//  @Preview — AnalyticsScreen
// ══════════════════════════════════════════════════════════════════

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun AnalyticsEmptyPreview() {
    SpindleTheme {
        AnalyticsScreen(state = AnalyticsState())
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun AnalyticsWithStaticAndMCPreview() {
    SpindleTheme {
        AnalyticsScreen(
            state = AnalyticsState(
                resultId               = "a1b2c3d4-e5f6",
                runType                = SimulationMode.FULL_ANALYSIS,
                iterations             = 10_000,
                toolTipDeflectionMm    = 0.0324,
                staticDeflectionMm     = 0.0380,
                maxDeflectionLocationMm = 82.0,
                maxBendingStressMpa    = 187.4,
                maxVonMisesMpa         = 231.8,
                safetyFactorYield      = 2.93,
                safetyFactorFatigue    = 1.71,
                solverVersion          = "Dry-Run v2.0 (Euler-Bernoulli)",
                nElements              = 48,
                nNodes                 = 49,
                modeShapes             = listOf(
                    ModeShape(1, 423.0, 0.02, "1st Bending (XY-plane)",  1.0, 25_380.0, 69.2),
                    ModeShape(2, 431.0, 0.02, "1st Bending (XZ-plane)",  0.97,25_860.0, 72.4),
                    ModeShape(3, 1142.0,0.02, "2nd Bending (XY-plane)",  0.61,68_520.0,356.8),
                    ModeShape(4, 1189.0,0.02, "2nd Bending (XZ-plane)",  0.58,71_340.0,375.6),
                ),
                spindleFrequencyHz          = 200.0,
                firstCriticalSpeedRpm       = 25_380.0,
                criticalSpeedSafetyFactor   = 2.11,
                pofPct             = 3.42,
                reliabilityPct     = 96.58,
                deflectionMeanMm   = 0.0318,
                deflectionStdMm    = 0.0041,
                deflectionP5Mm     = 0.0253,
                deflectionP95Mm    = 0.0398,
                stressMeanMpa      = 228.4,
                stressP95Mpa       = 267.1,
                freqMeanHz         = 421.3,
                freqP5Hz           = 389.6,
                runoutEffectMeanN  = 48.3,
                eccentricityEffectMeanN = 22.1,
                sensitivityFactors = mapOf(
                    "Radial Load"      to 0.41,
                    "Bearing Preload"  to 0.28,
                    "OD Tolerance"     to 0.17,
                    "Eccentricity"     to 0.09,
                    "Temperature"      to 0.05
                ),
                bearingLives       = listOf(
                    ISO281BearingLife(0,"7010 CE/P4A",25.0,820.0,92.0,31_400.0,4_200.0,24_100.0,41_800.0,4.1),
                    ISO281BearingLife(1,"7010 CE/P4A",25.0,780.0,88.0,33_200.0,4_500.0,25_900.0,43_600.0,3.2),
                    ISO281BearingLife(2,"NN3010",     48.0,420.0,51.0,58_900.0,7_100.0,46_200.0,74_300.0,0.4),
                )
            )
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun AnalyticsWithTaguchiPreview() {
    SpindleTheme {
        val demo = TaguchiResult(
            arrayType = "L9",
            controlFactors = listOf(
                TaguchiControlFactor("Front OD",     "mm",  70.0, 80.0, 90.0),
                TaguchiControlFactor("Preload",      "N",   150.0,280.0,450.0),
                TaguchiControlFactor("Cont. Angle",  "°",   15.0, 25.0, 40.0),
                TaguchiControlFactor("Body Length",  "mm",  150.0,180.0,210.0),
            ),
            experimentRuns = listOf(
                TaguchiRun(1, mapOf("Front OD" to 1,"Preload" to 1,"Cont. Angle" to 1,"Body Length" to 1), 0.0312, 420.0, 285.0, 30.1, -52.5, -49.1),
                TaguchiRun(2, mapOf("Front OD" to 1,"Preload" to 2,"Cont. Angle" to 2,"Body Length" to 2), 0.0298, 445.0, 271.0, 30.5, -53.0, -48.7),
                TaguchiRun(3, mapOf("Front OD" to 1,"Preload" to 3,"Cont. Angle" to 3,"Body Length" to 3), 0.0285, 468.0, 258.0, 30.9, -53.4, -48.2),
                TaguchiRun(4, mapOf("Front OD" to 2,"Preload" to 1,"Cont. Angle" to 2,"Body Length" to 3), 0.0218, 512.0, 224.0, 33.2, -54.2, -47.0),
                TaguchiRun(5, mapOf("Front OD" to 2,"Preload" to 2,"Cont. Angle" to 3,"Body Length" to 1), 0.0205, 538.0, 212.0, 33.8, -54.6, -46.5),
                TaguchiRun(6, mapOf("Front OD" to 2,"Preload" to 3,"Cont. Angle" to 1,"Body Length" to 2), 0.0198, 562.0, 205.0, 34.1, -55.0, -46.2),
                TaguchiRun(7, mapOf("Front OD" to 3,"Preload" to 1,"Cont. Angle" to 3,"Body Length" to 2), 0.0162, 590.0, 181.0, 35.8, -55.4, -45.2),
                TaguchiRun(8, mapOf("Front OD" to 3,"Preload" to 2,"Cont. Angle" to 1,"Body Length" to 3), 0.0155, 618.0, 174.0, 36.2, -55.8, -44.8),
                TaguchiRun(9, mapOf("Front OD" to 3,"Preload" to 3,"Cont. Angle" to 2,"Body Length" to 1), 0.0148, 642.0, 168.0, 36.6, -56.1, -44.5),
            ),
            responseTableDeflection = listOf(
                TaguchiResponseTableRow("Front OD",     30.5, 33.7, 36.2, 5.7, 1),
                TaguchiResponseTableRow("Preload",      33.2, 33.5, 33.9, 0.7, 3),
                TaguchiResponseTableRow("Cont. Angle",  33.5, 33.4, 33.5, 0.1, 4),
                TaguchiResponseTableRow("Body Length",  33.5, 33.5, 33.4, 0.1, 4),
            ),
            responseTableFreq = listOf(
                TaguchiResponseTableRow("Front OD",     -53.0,-54.6,-55.8, 2.8, 1),
                TaguchiResponseTableRow("Preload",      -54.0,-54.5,-54.8, 0.8, 2),
                TaguchiResponseTableRow("Cont. Angle",  -54.4,-54.4,-54.6, 0.2, 3),
                TaguchiResponseTableRow("Body Length",  -54.1,-54.3,-54.9, 0.8, 2),
            ),
            optimalFactorLevels          = mapOf("Front OD" to 90.0,"Preload" to 450.0,"Cont. Angle" to 25.0,"Body Length" to 150.0),
            predictedOptimumDeflectionMm = 0.0138,
            predictedOptimumFreqHz       = 665.0
        )
        AnalyticsScreen(
            state = AnalyticsState(
                taguchiResult  = demo,
                pofPct         = 3.42,
                reliabilityPct = 96.58,
                deflectionMeanMm = 0.0318
            )
        )
    }
}
