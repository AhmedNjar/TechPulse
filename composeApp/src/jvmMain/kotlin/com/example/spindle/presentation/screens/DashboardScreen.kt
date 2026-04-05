package com.example.spindle.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.spindle.presentation.components.*
import com.example.spindle.presentation.theme.*
import com.example.spindle.presentation.viewmodels.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DashboardScreen(
    spindleRpm: Double         = 0.0,
    radialLoadN: Double        = 0.0,
    deflectionMm: Double       = 0.0,
    bearingLifeHours: Double   = 0.0,
    runStatus: RunStatus       = RunStatus.IDLE,
    runProgress: Float         = 0f,
    pofPct: Double?            = null,
    reliabilityPct: Double?    = null,
    deflectionP5: Double?      = null,
    deflectionP95: Double?     = null,
    recentRuns: List<RunHistoryEntry> = emptyList(),
    projectName: String        = "No project loaded",
    hasLoadedConfig: Boolean   = false,
    errorMessage: String?      = null,
    onStartSimulation: () -> Unit = {},
    onDismissError: () -> Unit    = {},
    modifier: Modifier            = Modifier
) {
    val scrollState = rememberScrollState()
    Column(modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Mission Control",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary)
                Text(projectName, style = MaterialTheme.typography.bodyMedium, color = TealBright)
            }
            Button(
                onClick  = onStartSimulation,
                enabled  = runStatus != RunStatus.RUNNING && runStatus != RunStatus.SUBMITTING,
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = TealBright, contentColor = NavyDeepest,
                    disabledContainerColor = TealSubtle, disabledContentColor = TextDisabled)
            ) {
                Text(when (runStatus) {
                    RunStatus.SUBMITTING -> "⟳  Submitting..."
                    RunStatus.RUNNING    -> "⟳  Running..."
                    else -> if (hasLoadedConfig) "▶  Re-run" else "▶  Configure & Run"
                }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }

        errorMessage?.let { err ->
            Spacer(Modifier.height(12.dp))
            GlassCard(Modifier.fillMaxWidth(), rimColor = RedDanger.copy(0.5f)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("⚠  $err", style = MaterialTheme.typography.bodySmall, color = RedDanger, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth().heightIn(min = 480.dp), Arrangement.spacedBy(20.dp)) {
            // CAD viewer
            CadViewerPanel(Modifier.weight(0.6f).fillMaxHeight())

            // Right panel
            Column(Modifier.weight(0.4f).fillMaxHeight(), Arrangement.spacedBy(16.dp)) {
                SectionHeader("Live Telemetry")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveDataCard("Spindle RPM", if (spindleRpm > 0) "%,.0f".format(spindleRpm) else "—", "RPM",
                        modifier = Modifier.weight(1f))
                    LiveDataCard("Radial Load", if (radialLoadN > 0) "%,.0f".format(radialLoadN) else "—", "N",
                        modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiveDataCard("Deflection", if (deflectionMm > 0) "%.4f".format(deflectionMm) else "—", "mm",
                        status = if (deflectionMm > 0.05) DataCardStatus.WARNING else DataCardStatus.NOMINAL,
                        modifier = Modifier.weight(1f))
                    LiveDataCard("Bearing Life", if (bearingLifeHours > 0) "%,.0f".format(bearingLifeHours) else "—",
                        "h L10", modifier = Modifier.weight(1f))
                }

                SectionHeader("Simulation Status")
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Run Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        val (lbl, clr) = when (runStatus) {
                            RunStatus.IDLE       -> "READY"      to TextSecondary
                            RunStatus.SUBMITTING -> "SUBMITTING" to BluePrimary
                            RunStatus.RUNNING    -> "RUNNING"    to AmberWarning
                            RunStatus.COMPLETE   -> "COMPLETE"   to GreenSafe
                            RunStatus.FAILED     -> "FAILED"     to RedDanger
                        }
                        StatusBadge(lbl, clr)
                    }
                    if (runStatus == RunStatus.RUNNING || runStatus == RunStatus.SUBMITTING) {
                        Spacer(Modifier.height(10.dp))
                        GlassProgressBar(runProgress, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${(runProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    if (runStatus == RunStatus.COMPLETE && pofPct != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text("P(Failure)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${"%.2f".format(pofPct)} %", style = MaterialTheme.typography.titleSmall,
                                    color = if (pofPct < 5) GreenSafe else RedDanger, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Reliability", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${"%.2f".format(reliabilityPct ?: 0.0)} %", style = MaterialTheme.typography.titleSmall,
                                    color = GreenSafe, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                SectionHeader("Run History")
                GlassCard(Modifier.fillMaxWidth()) {
                    if (recentRuns.isEmpty()) {
                        Text("No runs yet", style = MaterialTheme.typography.bodySmall, color = TextDisabled)
                    } else {
                        recentRuns.take(6).forEachIndexed { i, entry ->
                            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 5.dp), 1.dp, GlassRim)
                            val col = when (entry.status) {
                                RunStatus.COMPLETE -> GreenSafe; RunStatus.FAILED -> RedDanger
                                RunStatus.RUNNING  -> AmberWarning; else -> TealBright
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).background(col, RoundedCornerShape(50)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(entry.label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                                }
                                Text(entry.relativeTime, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadViewerPanel(modifier: Modifier) {
    val rotation by rememberInfiniteTransition(label = "r").animateFloat(
        0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "rot"
    )
    GlassCard(modifier, glowColor = TealGlow, rimColor = GlassRimBright) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("3D CAD Viewer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge("STEP / ANSYS", TealBright)
                StatusBadge("PyVista", SlateLight)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(NavyDeepest)
                .drawBehind { drawCadGrid() },
            contentAlignment = Alignment.Center

        ) {
            SpindleWireframe(rotation)
            Text("Integrate PyVista server-side render here",
                style = MaterialTheme.typography.labelSmall, color = TextDisabled,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Wireframe", "FEA Stress", "FEA Deflection", "Modal").forEach { m ->
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    border = BorderStroke(1.dp, GlassRim),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text(
                        m,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCadGrid() {
    val lc = GlassRim.copy(0.2f); val cx = size.width/2; val vy = size.height*0.3f; var y = 40f
    while (y < size.height) {
        val s = y / size.height
        drawLine(lc, Offset(cx - s*size.width*0.6f, y), Offset(cx + s*size.width*0.6f, y), 0.8f); y += 40f
    }
    for (i in 0..14) drawLine(lc, Offset(cx, vy), Offset(size.width*i/14f, size.height), 0.6f)
}

@Composable
private fun SpindleWireframe(rotationDeg: Float) {
    Canvas(Modifier.size(300.dp, 180.dp)) {
        val cx = size.width/2; val cy = size.height/2
        val a = TealBright.copy(0.7f); val d = TealDeep.copy(0.4f)
        drawRect(d, Offset(cx-140f,cy-50f), androidx.compose.ui.geometry.Size(280f,100f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        drawRect(a, Offset(cx-130f,cy-20f), androidx.compose.ui.geometry.Size(260f,40f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawOval(a, Offset(cx-130f,cy-40f), androidx.compose.ui.geometry.Size(30f,80f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawOval(a, Offset(cx+100f,cy-40f), androidx.compose.ui.geometry.Size(30f,80f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(TealBright, Offset(cx-130f,cy), Offset(cx-175f,cy), 3f)
        drawPath(Path().apply { moveTo(cx-175f,cy-10f); lineTo(cx-195f,cy); lineTo(cx-175f,cy+10f); close() }, a)
        val ang = Math.toRadians((rotationDeg).toDouble())
        drawArc(TealBright.copy(0.5f), rotationDeg, 120f, false,
            Offset(cx-20f,cy-20f), androidx.compose.ui.geometry.Size(40f,40f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2f, cap = StrokeCap.Round))
    }
}

@Preview
@Composable
fun DashboardPreview(){
    SpindleTheme { DashboardScreen() }
}
