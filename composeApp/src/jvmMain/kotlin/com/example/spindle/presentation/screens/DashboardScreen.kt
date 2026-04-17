package com.example.spindle.presentation.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.example.spindle.presentation.components.*
import com.example.spindle.presentation.theme.*
import com.example.spindle.presentation.viewmodels.*
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DashboardScreen(
    spindleRpm: Double              = 0.0,
    radialLoadN: Double             = 0.0,
    deflectionMm: Double            = 0.0,
    bearingLifeHours: Double        = 0.0,
    runStatus: RunStatus            = RunStatus.IDLE,
    runProgress: Float              = 0f,
    pofPct: Double?                 = null,
    reliabilityPct: Double?         = null,
    deflectionP5: Double?           = null,
    deflectionP95: Double?          = null,
    recentRuns: List<RunHistoryEntry> = emptyList(),
    projectName: String             = "No project loaded",
    hasLoadedConfig: Boolean        = false,
    errorMessage: String?           = null,
    onStartSimulation: () -> Unit   = {},
    onDismissError: () -> Unit      = {},
    modifier: Modifier              = Modifier
) {
    val scrollState = rememberScrollState()

    // Stagger indices
    val a0 = rememberStaggerAlpha(0, 50)
    val a1 = rememberStaggerAlpha(1, 50)
    val a2 = rememberStaggerAlpha(2, 50)

    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

            // ── Header ────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a0 },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Column {
                    Text("Mission Control",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary)
                    Text(projectName, style = MaterialTheme.typography.bodyMedium, color = TealBright)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Animated status indicator while running
                    if (runStatus == RunStatus.RUNNING) {
                        val shimmer by rememberInfiniteTransition(label = "shimmer")
                            .animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "sh")
                        StatusBadge("●  COMPUTING", AmberWarning.copy(alpha = 0.5f + shimmer * 0.5f))
                    }
                    Button(
                        onClick  = onStartSimulation,
                        enabled  = runStatus != RunStatus.RUNNING && runStatus != RunStatus.SUBMITTING,
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = TealBright,
                            contentColor           = NavyDeepest,
                            disabledContainerColor = TealSubtle,
                            disabledContentColor   = TextDisabled
                        )
                    ) {
                        Text(
                            when (runStatus) {
                                RunStatus.SUBMITTING -> "⟳  Submitting..."
                                RunStatus.RUNNING    -> "⟳  Running..."
                                else -> if (hasLoadedConfig) "▶  Re-run" else "▶  Configure & Run"
                            },
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Error banner with AnimatedVisibility
            AnimatedVisibility(
                visible = errorMessage != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth(), rimColor = RedDanger.copy(alpha = 0.5f)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("⚠  ${errorMessage ?: ""}",
                                style = MaterialTheme.typography.bodySmall, color = RedDanger,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismissError) {
                                Text("Dismiss", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Main two-column body ───────────────────────────────
            Row(
                Modifier.fillMaxWidth().heightIn(min = 480.dp).graphicsLayer { alpha = a1 },
                Arrangement.spacedBy(20.dp)
            ) {
                // CAD Viewer — left 60%
                CadViewerPanel(
                    isRunning  = runStatus == RunStatus.RUNNING,
                    hasResults = deflectionMm > 0,
                    modifier   = Modifier.weight(0.6f).fillMaxHeight()
                )

                // Right panel — 40%
                Column(Modifier.weight(0.4f).fillMaxHeight(), Arrangement.spacedBy(16.dp)) {

                    // KPI cards
                    SectionHeader("Live Telemetry")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveDataCard("Spindle RPM",
                            if (spindleRpm > 0) "%,.0f".format(spindleRpm) else "—", "RPM",
                            modifier = Modifier.weight(1f))
                        LiveDataCard("Radial Load",
                            if (radialLoadN > 0) "%,.0f".format(radialLoadN) else "—", "N",
                            modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiveDataCard("Tool Tip Defl.",
                            if (deflectionMm > 0) "%.4f".format(deflectionMm) else "—", "mm",
                            status   = when {
                                deflectionMm <= 0       -> DataCardStatus.OFFLINE
                                deflectionMm > 0.05     -> DataCardStatus.WARNING
                                else                    -> DataCardStatus.NOMINAL
                            },
                            modifier = Modifier.weight(1f))
                        LiveDataCard("Bearing L10",
                            if (bearingLifeHours > 0) "%,.0f".format(bearingLifeHours) else "—", "hours",
                            status   = when {
                                bearingLifeHours <= 0        -> DataCardStatus.OFFLINE
                                bearingLifeHours < 20_000    -> DataCardStatus.WARNING
                                else                         -> DataCardStatus.NOMINAL
                            },
                            modifier = Modifier.weight(1f))
                    }

                    // Simulation Status
                    SectionHeader("Simulation Status")
                    SimulationStatusCard(runStatus, runProgress, pofPct, reliabilityPct, deflectionP5, deflectionP95)

                    // Run History
                    SectionHeader("Run History")
                    RunHistoryCard(recentRuns)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Bottom specs strip ────────────────────────────────
            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a2 },
                Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "CNC Lathe Spindle" to "Platform",
                    "40–100 mm OD"      to "Geometry",
                    "≤ 15,000 RPM"      to "Max Speed",
                    "< 0.05 mm"         to "Deflection Limit",
                    "> 350 Hz"          to "Min Nat. Freq.",
                    "> 20,000 h"        to "Bearing L10 Target"
                ).forEach { (value, label) ->
                    Box(
                        Modifier.weight(1f)
                            .background(GlassSurface, RoundedCornerShape(8.dp))
                            .border(1.dp, GlassRim, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                            Text(value, style = MaterialTheme.typography.labelMedium,
                                color = TealBright, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  CadViewerPanel  — Improved stepped hollow shaft visualisation
// ══════════════════════════════════════════════════════════════════

@Composable
private fun CadViewerPanel(
    isRunning: Boolean  = false,
    hasResults: Boolean = false,
    modifier: Modifier  = Modifier
) {
    var selectedMode by remember { mutableStateOf("Wireframe") }
    val modes = listOf("Wireframe", "FEA Stress", "Deflection", "Modal")

    // Slow rotation for idle; faster when running
    val rotSpeed = if (isRunning) 4_000 else 14_000
    val rotation by rememberInfiniteTransition(label = "cadRot").animateFloat(
        0f, 360f, infiniteRepeatable(tween(rotSpeed, easing = LinearEasing)), label = "rot"
    )
    // Scan-line effect when running
    val scanY by if (isRunning) {
        rememberInfiniteTransition(label = "scan").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "scanY"
        )
    } else remember { mutableStateOf(0f) }

    GlassCard(modifier, glowColor = TealGlow, rimColor = GlassRimBright) {
        // Title bar
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("3D CAD Viewer", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("Hollow Stepped Shaft — 4 Segments · 3 Bearings",
                    style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isRunning) StatusBadge("SOLVING", AmberWarning)
                if (hasResults) StatusBadge("RESULTS READY", GreenSafe)
                StatusBadge("PyVista", SlateLight)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Viewport
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(NavyDeepest)
                .drawBehind {
                    drawPerspectiveGrid(this)
                    if (isRunning) drawScanLine(this, scanY)
                }
        ) {
            // Stepped hollow shaft drawing
            SteppedShaftCanvas(
                rotation    = rotation,
                renderMode  = selectedMode,
                hasResults  = hasResults,
                modifier    = Modifier.align(Alignment.Center).fillMaxWidth().fillMaxHeight()
            )

            // Axis labels
            Text("Z →  (Tool Tip)", style = MaterialTheme.typography.labelSmall, color = TextDisabled,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            Text("↑ R", style = MaterialTheme.typography.labelSmall, color = TextDisabled,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp))

            if (!hasResults && !isRunning) {
                Text("Run a simulation to load FEA results",
                    style = MaterialTheme.typography.labelSmall, color = TextDisabled,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mode toolbar
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEach { m ->
                val active = selectedMode == m
                val enabled = m == "Wireframe" || hasResults
                OutlinedButton(
                    onClick  = { if (enabled) selectedMode = m },
                    enabled  = enabled,
                    modifier = Modifier.height(30.dp),
                    shape    = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    border   = BorderStroke(1.dp, if (active) TealBright else GlassRim),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor         = if (active) TealBright else TextSecondary,
                        disabledContentColor = TextDisabled
                    )
                ) { Text(m, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ── Stepped shaft canvas — proper engineering drawing ─────────────

@Composable
private fun SteppedShaftCanvas(
    rotation: Float,
    renderMode: String,
    hasResults: Boolean,
    modifier: Modifier
) {
    // FIX #9: Proper stepped hollow shaft with 4 segments + 3 bearing positions
    // Segment layout (approx screen coords): TaperNose | FrontJournal | Body | RearJournal
    val segmentDefs = listOf(
        SegDef(lengthFrac = 0.16f, outerFrac = 0.55f, innerFrac = 0.00f, name = "Tool Taper"),
        SegDef(lengthFrac = 0.25f, outerFrac = 0.75f, innerFrac = 0.40f, name = "Front Journal"),
        SegDef(lengthFrac = 0.37f, outerFrac = 1.00f, innerFrac = 0.58f, name = "Body"),
        SegDef(lengthFrac = 0.22f, outerFrac = 0.72f, innerFrac = 0.40f, name = "Rear Journal"),
    )
    // Bearing Z positions as fractions of total length
    val bearingZFracs = listOf(0.22f, 0.38f, 0.78f)
    val bearingColors = listOf(TealBright, TealBright, BluePrimary)   // front pair = locating; rear = free

    Canvas(modifier = modifier) {
        val padH  = size.height * 0.12f
        val padW  = size.width  * 0.06f
        val midY  = size.height / 2f
        val maxR  = (size.height / 2f - padH)
        val shaftW = size.width - padW * 2f
        var xCursor = padW

        // Colour map for render modes
        val baseColor = when (renderMode) {
            "FEA Stress"  -> if (hasResults) Color(0xFFFF6B35) else TealBright.copy(alpha = 0.4f)
            "Deflection"  -> if (hasResults) Color(0xFF5A8FFF) else TealBright.copy(alpha = 0.4f)
            "Modal"       -> if (hasResults) Color(0xFF9B59FF) else TealBright.copy(alpha = 0.4f)
            else          -> TealBright.copy(alpha = 0.7f)
        }

        segmentDefs.forEachIndexed { i, seg ->
            val segW   = seg.lengthFrac * shaftW
            val outerR = seg.outerFrac * maxR
            val innerR = seg.innerFrac * maxR

            // Fill (gradient from top face to bottom — isometric feel)
            val segColor = when {
                renderMode == "FEA Stress" && hasResults -> {
                    // Stress heatmap: higher near front bearing
                    val stress = 1f - (i * 0.2f)
                    Color(1f, stress * 0.4f, 0f, 0.6f + stress * 0.3f)
                }
                renderMode == "Deflection" && hasResults -> {
                    val defl = 1f - (i * 0.22f)
                    Color(defl * 0.3f, defl * 0.5f, 1f, 0.5f + defl * 0.3f)
                }
                else -> baseColor
            }

            // Outer rect fill
            drawRect(
                brush   = Brush.verticalGradient(
                    listOf(segColor.copy(alpha = 0.85f), segColor.copy(alpha = 0.45f)),
                    midY - outerR,
                    midY + outerR
                ),
                topLeft = Offset(xCursor, midY - outerR),
                size    = androidx.compose.ui.geometry.Size(segW, outerR * 2)
            )

            // Hollow bore (darker cutout)
            if (innerR > 2f) {
                drawRect(
                    color   = NavyDeepest.copy(alpha = 0.85f),
                    topLeft = Offset(xCursor, midY - innerR),
                    size    = androidx.compose.ui.geometry.Size(segW, innerR * 2)
                )
                // Inner bore edge line
                drawRect(
                    color   = GlassRim.copy(alpha = 0.5f),
                    topLeft = Offset(xCursor, midY - innerR),
                    size    = androidx.compose.ui.geometry.Size(segW, innerR * 2),
                    style   = Stroke(width = 0.8f)
                )
            }

            // Outer edge outline
            drawRect(
                color   = segColor.copy(alpha = 0.9f),
                topLeft = Offset(xCursor, midY - outerR),
                size    = androidx.compose.ui.geometry.Size(segW, outerR * 2),
                style   = Stroke(width = 1.5f)
            )

            // Segment boundary line
            if (i > 0) {
                drawLine(
                    color       = GlassRimBright.copy(alpha = 0.7f),
                    start       = Offset(xCursor, midY - outerR),
                    end         = Offset(xCursor, midY + outerR),
                    strokeWidth = 1.5f
                )
            }

            xCursor += segW
        }

        // Final right boundary
        val lastSeg = segmentDefs.last()
        val lastR   = lastSeg.outerFrac * maxR
        drawLine(baseColor.copy(alpha = 0.7f),
            Offset(xCursor, midY - lastR), Offset(xCursor, midY + lastR), 1.5f)

        // Centre-line (dashed)
        val dashLen = 12f; val gapLen = 6f; var x = padW
        while (x < size.width - padW) {
            drawLine(GlassRim.copy(alpha = 0.35f), Offset(x, midY), Offset(x + dashLen, midY), 1f)
            x += dashLen + gapLen
        }

        // Bearing rings at their Z positions
        bearingZFracs.forEachIndexed { bi, zFrac ->
            val bx  = padW + zFrac * shaftW
            // Find segment outer radius at this Z
            val outerR = interpolateOuterR(zFrac, segmentDefs, maxR)
            val bc  = bearingColors.getOrElse(bi) { TealBright }

            // Outer ring
            drawOval(
                color   = bc.copy(alpha = 0.25f),
                topLeft = Offset(bx - 12f, midY - outerR - 10f),
                size    = androidx.compose.ui.geometry.Size(24f, (outerR + 10f) * 2),
                style   = Stroke(width = 2.5f)
            )
            // Inner ring
            drawOval(
                color   = bc.copy(alpha = 0.6f),
                topLeft = Offset(bx - 8f, midY - outerR),
                size    = androidx.compose.ui.geometry.Size(16f, outerR * 2),
                style   = Stroke(width = 1.5f)
            )
            // Preload arrow above
            if (bi < 2) {   // Front pair get preload arrows
                drawLine(AmberWarning.copy(alpha = 0.7f),
                    Offset(bx, midY - outerR - 16f), Offset(bx, midY - outerR - 4f), 2f)
                drawLine(AmberWarning.copy(alpha = 0.7f),
                    Offset(bx - 4f, midY - outerR - 12f), Offset(bx, midY - outerR - 16f), 2f)
                drawLine(AmberWarning.copy(alpha = 0.7f),
                    Offset(bx + 4f, midY - outerR - 12f), Offset(bx, midY - outerR - 16f), 2f)
            }
        }

        // Tool tip marker
        drawCircle(GreenSafe, 5f, Offset(padW, midY))
        drawLine(GreenSafe.copy(alpha = 0.5f), Offset(padW, midY - maxR), Offset(padW, midY + maxR), 1f)

        // Deflection curve overlay (when results available)
        if (hasResults && renderMode == "Deflection") {
            val path = Path()
            val pts = (0..40).map { i ->
                val t  = i / 40f
                val dx = padW + t * shaftW
                val dy = midY - (1f - t * t) * maxR * 0.12f * sin(t * PI.toFloat())
                Offset(dx, dy)
            }
            path.moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { path.lineTo(it.x, it.y) }
            drawPath(path, BluePrimary.copy(alpha = 0.8f), style = Stroke(width = 2f, cap = StrokeCap.Round))
        }

        // Animated rotation arc at tool tip
        drawArc(
            color      = TealBright.copy(alpha = 0.5f),
            startAngle = rotation,
            sweepAngle = 110f,
            useCenter  = false,
            topLeft    = Offset(padW - 18f, midY - 18f),
            size       = androidx.compose.ui.geometry.Size(36f, 36f),
            style      = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

private data class SegDef(
    val lengthFrac: Float,
    val outerFrac: Float,
    val innerFrac: Float,
    val name: String
)

private fun interpolateOuterR(zFrac: Float, segs: List<SegDef>, maxR: Float): Float {
    var cumLen = 0f
    for (seg in segs) {
        if (zFrac <= cumLen + seg.lengthFrac) return seg.outerFrac * maxR
        cumLen += seg.lengthFrac
    }
    return segs.last().outerFrac * maxR
}

private fun drawPerspectiveGrid(scope: DrawScope) = with(scope) {
    val lc = GlassRim.copy(alpha = 0.15f)
    val cx = size.width / 2f; val vy = size.height * 0.25f
    var y = 32f
    while (y < size.height) {
        val s = y / size.height
        drawLine(lc, Offset(cx - s * size.width * 0.55f, y), Offset(cx + s * size.width * 0.55f, y), 0.7f)
        y += 32f
    }
    for (i in 0..16) {
        drawLine(lc, Offset(cx, vy), Offset(size.width * i / 16f, size.height), 0.5f)
    }
}

private fun drawScanLine(scope: DrawScope, scanY: Float) = with(scope) {
    val y = scanY * size.height
    drawRect(
        brush   = Brush.verticalGradient(
            listOf(Color.Transparent, TealBright.copy(alpha = 0.12f), Color.Transparent),
            y - 20f, y + 20f
        ),
        topLeft = Offset(0f, y - 20f),
        size    = androidx.compose.ui.geometry.Size(size.width, 40f)
    )
}

// ── Simulation Status Card ─────────────────────────────────────────

@Composable
private fun SimulationStatusCard(
    status: RunStatus,
    progress: Float,
    pofPct: Double?,
    reliabilityPct: Double?,
    deflP5: Double?,
    deflP95: Double?
) {
    GlassCard(
        modifier  = Modifier.fillMaxWidth(),
        rimColor  = when (status) {
            RunStatus.RUNNING  -> AmberWarning.copy(alpha = 0.4f)
            RunStatus.COMPLETE -> GreenSafe.copy(alpha = 0.4f)
            RunStatus.FAILED   -> RedDanger.copy(alpha = 0.4f)
            else               -> GlassRim
        }
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Run Status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            val (lbl, clr) = when (status) {
                RunStatus.IDLE       -> "READY"      to TextSecondary
                RunStatus.SUBMITTING -> "SUBMITTING" to BluePrimary
                RunStatus.RUNNING    -> "RUNNING"    to AmberWarning
                RunStatus.COMPLETE   -> "COMPLETE"   to GreenSafe
                RunStatus.FAILED     -> "FAILED"     to RedDanger
            }
            StatusBadge(lbl, clr)
        }

        // Progress bar with smooth animation (already animated in GlassProgressBar)
        AnimatedVisibility(
            visible = status == RunStatus.RUNNING || status == RunStatus.SUBMITTING,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                GlassProgressBar(progress, Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text("Monte Carlo sampling…",
                        style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                }
            }
        }

        // Results reveal with AnimatedVisibility
        AnimatedVisibility(
            visible = status == RunStatus.COMPLETE && pofPct != null,
            enter   = fadeIn(tween(600)) + expandVertically(tween(600))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GlassRim, thickness = 1.dp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("P(Failure)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        val pof = pofPct ?: 0.0
                        AnimatedCounter(
                            targetValue = pof,
                            format      = "%.2f %%",
                            style       = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color       = if (pof < 5) GreenSafe else if (pof < 15) AmberWarning else RedDanger
                        )
                    }
                    Column {
                        Text("Reliability", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        AnimatedCounter(
                            targetValue = reliabilityPct ?: 0.0,
                            format      = "%.2f %%",
                            style       = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color       = GreenSafe
                        )
                    }
                    if (deflP5 != null && deflP95 != null) {
                        Column {
                            Text("95% CI Deflection", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("[%.4f, %.4f] mm".format(deflP5, deflP95),
                                style = MaterialTheme.typography.labelMedium, color = TealBright)
                        }
                    }
                }
            }
        }
    }
}

// ── Run History Card ──────────────────────────────────────────────

@Composable
private fun RunHistoryCard(runs: List<RunHistoryEntry>) {
    GlassCard(Modifier.fillMaxWidth()) {
        if (runs.isEmpty()) {
            Text("No runs yet — configure and submit a simulation",
                style = MaterialTheme.typography.bodySmall, color = TextDisabled)
        } else {
            runs.take(6).forEachIndexed { i, entry ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 5.dp), 1.dp, GlassRim)
                // Stagger each history row
                val rowAlpha = rememberStaggerAlpha(i + 10, 40)
                val col = when (entry.status) {
                    RunStatus.COMPLETE -> GreenSafe
                    RunStatus.FAILED   -> RedDanger
                    RunStatus.RUNNING  -> AmberWarning
                    else               -> TealBright
                }
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = rowAlpha },
                    Arrangement.SpaceBetween, Alignment.CenterVertically
                ) {
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

// ══════════════════════════════════════════════════════════════════
//  @Preview
// ══════════════════════════════════════════════════════════════════

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun DashboardIdlePreview() {
    SpindleTheme {
        DashboardScreen(
            projectName     = "CNC-5000 Rev C",
            hasLoadedConfig = false,
            runStatus       = RunStatus.IDLE
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun DashboardRunningPreview() {
    SpindleTheme {
        DashboardScreen(
            projectName      = "CNC-5000 Rev C",
            hasLoadedConfig  = true,
            runStatus        = RunStatus.RUNNING,
            runProgress      = 0.62f,
            spindleRpm       = 12_500.0,
            radialLoadN      = 1_840.0
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun DashboardCompletePreview() {
    SpindleTheme {
        DashboardScreen(
            projectName      = "CNC-5000 Rev C",
            hasLoadedConfig  = true,
            runStatus        = RunStatus.COMPLETE,
            runProgress      = 1f,
            spindleRpm       = 12_500.0,
            radialLoadN      = 1_840.0,
            deflectionMm     = 0.0324,
            bearingLifeHours = 24_500.0,
            pofPct           = 3.42,
            reliabilityPct   = 96.58,
            deflectionP5     = 0.0281,
            deflectionP95    = 0.0401,
            recentRuns       = listOf(
                RunHistoryEntry("r1", "MC-A1B2", "just now",   RunStatus.COMPLETE),
                RunHistoryEntry("r2", "MC-C3D4", "5 min ago",  RunStatus.COMPLETE),
                RunHistoryEntry("r3", "MC-E5F6", "22 min ago", RunStatus.FAILED),
            )
        )
    }
}
