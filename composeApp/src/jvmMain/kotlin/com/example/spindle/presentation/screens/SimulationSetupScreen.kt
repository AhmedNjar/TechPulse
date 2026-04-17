package com.example.spindle.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    val a0 = rememberStaggerAlpha(0, 60)
    val a1 = rememberStaggerAlpha(1, 60)
    val a2 = rememberStaggerAlpha(2, 60)
    val a3 = rememberStaggerAlpha(3, 60)

    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

        // Header
        Row(
            Modifier.fillMaxWidth().graphicsLayer { alpha = a0 },
            Arrangement.SpaceBetween, Alignment.CenterVertically) {
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
        GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a1 }) {
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

        // ── Shaft Segments — editable expandable cards ───────────
        GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a2 }) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Shaft Segments (${state.geometry.segments.size})",
                        style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Text(
                        "Total L = ${"%.0f".format(state.geometry.totalLengthMm)} mm  •  " +
                        "Est. mass ≈ ${"%.2f".format(
                            state.geometry.segments.sumOf { it.areaMm2 * it.lengthMm * 7.85e-6 }
                        )} kg",
                        style = MaterialTheme.typography.labelSmall, color = TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = {
                        val segs = state.geometry.segments.toMutableList()
                        val last = segs.last()
                        segs.add(ShaftSegment(
                            segs.size, "Segment ${segs.size + 1}",
                            80.0, last.outerDiameterMm * 0.9, last.innerDiameterMm
                        ))
                        onIntent(SetupIntent.UpdateGeometry(state.geometry.copy(segments = segs)))
                    },
                    enabled = state.geometry.segments.size < 8,
                    shape   = RoundedCornerShape(8.dp),
                    border  = BorderStroke(1.dp, TealBright.copy(alpha = 0.5f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                ) { Text("+ Add Segment", style = MaterialTheme.typography.labelMedium) }
            }

            Spacer(Modifier.height(10.dp))

            // Profile preview bar (proportional widths + hollowness)
            val totalL = state.geometry.totalLengthMm.toFloat().coerceAtLeast(1f)
            Row(
                Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp))
            ) {
                val segColours = listOf(TealBright, BluePrimary, AmberWarning, GreenSafe,
                    Color(0xFF9B59FF), Color(0xFFFF8C42), Color(0xFFFF4D6D), Color(0xFF4ECDC4))
                state.geometry.segments.forEachIndexed { i, seg ->
                    val frac = (seg.lengthMm / totalL).toFloat()
                    val col  = segColours[i % segColours.size]
                    Box(
                        Modifier.weight(frac.coerceAtLeast(0.01f)).fillMaxHeight()
                            .background(col.copy(alpha = 0.25f))
                            .border(1.dp, col.copy(alpha = 0.6f)),
                        Alignment.Center
                    ) {
                        Text(seg.name.take(8),
                            style = MaterialTheme.typography.labelSmall, color = col,
                            maxLines = 1, softWrap = false)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Editable segment cards
            state.geometry.segments.forEachIndexed { idx, seg ->
                var expanded by remember(idx) { mutableStateOf(false) }
                val segCol = listOf(TealBright, BluePrimary, AmberWarning, GreenSafe,
                    Color(0xFF9B59FF), Color(0xFFFF8C42), Color(0xFFFF4D6D),
                    Color(0xFF4ECDC4))[idx % 8]

                val odErr = if (seg.outerDiameterMm < 10 || seg.outerDiameterMm > 150)
                    "OD must be 10–150 mm" else null
                val idErr = if (seg.innerDiameterMm >= seg.outerDiameterMm)
                    "ID must be < OD (${seg.outerDiameterMm} mm)" else null

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDeepest.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (odErr != null || idErr != null) RedDanger.copy(alpha = 0.6f)
                            else segCol.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    // Collapsed header row — click to expand
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(10.dp)
                                .background(segCol, RoundedCornerShape(3.dp)))
                            Text("${idx + 1}. ${seg.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Ø${seg.outerDiameterMm}×${seg.innerDiameterMm} mm",
                                style = MaterialTheme.typography.labelSmall, color = segCol)
                            Text("L ${seg.lengthMm} mm",
                                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            if (seg.isHollow) Text("k=${"%.2f".format(seg.hollownessRatio)}",
                                style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                            if (odErr != null || idErr != null)
                                StatusBadge("ERROR", RedDanger)
                            // Delete button
                            if (state.geometry.segments.size > 1) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val newSegs = state.geometry.segments
                                                .toMutableList().also { it.removeAt(idx) }
                                                .mapIndexed { i, s -> s.copy(segmentId = i) }
                                            onIntent(SetupIntent.UpdateGeometry(
                                                state.geometry.copy(segments = newSegs)))
                                        },
                                    Alignment.Center
                                ) { Text("✕", style = MaterialTheme.typography.labelSmall,
                                    color = RedDanger) }
                            }
                            Text(if (expanded) "▾" else "▸",
                                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    // Expanded edit fields
                    AnimatedVisibility(visible = expanded,
                        enter = fadeIn() + expandVertically(),
                        exit  = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            HorizontalDivider(color = GlassRim, thickness = 1.dp,
                                modifier = Modifier.padding(bottom = 10.dp))

                            // Row 1: Name field
                            OutlinedTextField(
                                value = seg.name,
                                onValueChange = { v ->
                                    val newSegs = state.geometry.segments.toMutableList()
                                    newSegs[idx] = seg.copy(name = v)
                                    onIntent(SetupIntent.UpdateGeometry(
                                        state.geometry.copy(segments = newSegs)))
                                },
                                label     = { Text("Segment Name") },
                                singleLine = true,
                                modifier  = Modifier.fillMaxWidth(),
                                colors    = setupFieldColors(),
                                shape     = RoundedCornerShape(6.dp)
                            )
                            Spacer(Modifier.height(8.dp))

                            // Row 2: Geometry fields
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // OD
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = seg.outerDiameterMm.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                val newSegs = state.geometry.segments.toMutableList()
                                                newSegs[idx] = seg.copy(outerDiameterMm = d)
                                                onIntent(SetupIntent.UpdateGeometry(
                                                    state.geometry.copy(segments = newSegs)))
                                            }
                                        },
                                        label   = { Text("Outer Ø (mm)") },
                                        isError = odErr != null,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors  = setupFieldColors(),
                                        shape   = RoundedCornerShape(6.dp)
                                    )
                                    odErr?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = RedDanger, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) }
                                }
                                // ID
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = seg.innerDiameterMm.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                val newSegs = state.geometry.segments.toMutableList()
                                                newSegs[idx] = seg.copy(innerDiameterMm = d.coerceAtLeast(0.0))
                                                onIntent(SetupIntent.UpdateGeometry(
                                                    state.geometry.copy(segments = newSegs)))
                                            }
                                        },
                                        label   = { Text("Inner Ø (mm) — 0 = solid") },
                                        isError = idErr != null,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors  = setupFieldColors(),
                                        shape   = RoundedCornerShape(6.dp)
                                    )
                                    idErr?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                        color = RedDanger, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) }
                                }
                                // Length
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = seg.lengthMm.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                val newSegs = state.geometry.segments.toMutableList()
                                                newSegs[idx] = seg.copy(lengthMm = d.coerceAtLeast(1.0))
                                                onIntent(SetupIntent.UpdateGeometry(
                                                    state.geometry.copy(segments = newSegs)))
                                            }
                                        },
                                        label      = { Text("Length (mm)") },
                                        singleLine = true,
                                        modifier   = Modifier.fillMaxWidth(),
                                        colors     = setupFieldColors(),
                                        shape      = RoundedCornerShape(6.dp)
                                    )
                                }
                                // Surface tolerance
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = seg.surfaceToleranceMm.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                val newSegs = state.geometry.segments.toMutableList()
                                                newSegs[idx] = seg.copy(surfaceToleranceMm = d.coerceAtLeast(0.0))
                                                onIntent(SetupIntent.UpdateGeometry(
                                                    state.geometry.copy(segments = newSegs)))
                                            }
                                        },
                                        label      = { Text("Surf. Tol. ± (mm)") },
                                        singleLine = true,
                                        modifier   = Modifier.fillMaxWidth(),
                                        colors     = setupFieldColors(),
                                        shape      = RoundedCornerShape(6.dp)
                                    )
                                }
                                // Fillet radius at step boundary
                                Column(Modifier.weight(1f)) {
                                    val filletVal = state.filletRadiiMm.getOrElse(idx) { 2.0 }
                                    OutlinedTextField(
                                        value = filletVal.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                onIntent(SetupIntent.UpdateFilletRadius(idx, d))
                                            }
                                        },
                                        label      = { Text("Fillet r (mm)") },
                                        singleLine = true,
                                        modifier   = Modifier.fillMaxWidth(),
                                        colors     = setupFieldColors(),
                                        shape      = RoundedCornerShape(6.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Computed properties row
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "I = ${"%.1f".format(seg.secondMomentMm4)} mm⁴",
                                    "A = ${"%.1f".format(seg.areaMm2)} mm²",
                                    "k = ${"%.3f".format(seg.hollownessRatio)}",
                                    "m ≈ ${"%.3f".format(seg.areaMm2 * seg.lengthMm * 7.85e-6)} kg"
                                ).forEach { label ->
                                    Box(
                                        Modifier
                                            .background(NavyMid, RoundedCornerShape(4.dp))
                                            .border(1.dp, GlassRim, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) { Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = TealBright) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Bearing System — editable expandable cards ───────────
        GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a2 }) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Bearing System (${state.bearingSystem.bearings.size} bearings)",
                    style = MaterialTheme.typography.titleSmall, color = TealBright)
                OutlinedButton(
                    onClick = {
                        val brgs = state.bearingSystem.bearings.toMutableList()
                        val lastZ = brgs.maxOf { it.zPositionMm }
                        brgs.add(BearingMount(
                            bearingId = brgs.size,
                            zPositionMm = (lastZ + 50.0)
                                .coerceAtMost(state.geometry.totalLengthMm - 10.0)
                        ))
                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(brgs)))
                    },
                    enabled = state.bearingSystem.bearings.size < 8,
                    shape   = RoundedCornerShape(8.dp),
                    border  = BorderStroke(1.dp, TealBright.copy(alpha = 0.5f)),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                ) { Text("+ Add Bearing", style = MaterialTheme.typography.labelMedium) }
            }

            Spacer(Modifier.height(10.dp))

            state.bearingSystem.bearings.sortedBy { it.zPositionMm }
                .forEachIndexed { listIdx, b ->

                // Find original index for update dispatch
                val origIdx = state.bearingSystem.bearings.indexOf(b)
                var expanded by remember(b.bearingId) { mutableStateOf(false) }
                val rpmOver = state.operatingSpeedRpm > b.limitingSpeedRpm
                val zOver   = b.zPositionMm > state.geometry.totalLengthMm
                val hasError = rpmOver || zOver

                val bc = if (b.role == BearingRoleV2.LOCATING) TealBright else BluePrimary

                // Simplified ISO 281 L10 preview
                val loadEst = state.radialLoadN * 0.5 + b.preloadForceN * 0.3
                val l10Preview = if (state.operatingSpeedRpm > 0 && loadEst > 0)
                    Math.pow(b.dynamicLoadRatingKn * 1000.0 / loadEst, b.lifeExponent) *
                        1e6 / (60.0 * state.operatingSpeedRpm)
                else 0.0

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavyDeepest.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            if (hasError) RedDanger.copy(alpha = 0.6f) else bc.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    // Collapsed header — click to expand
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(10.dp).background(bc, RoundedCornerShape(50)))
                            Text("${listIdx + 1}. ${b.designation}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Z = ${b.zPositionMm} mm",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (zOver) RedDanger else TextSecondary)
                            Text(b.role.label,
                                style = MaterialTheme.typography.labelSmall, color = bc)
                            Text(b.arrangement.label,
                                style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                            if (l10Preview > 0) Text(
                                "L₁₀ ≈ ${"%.0f".format(l10Preview)} h",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (l10Preview >= 20_000) GreenSafe else AmberWarning)
                            if (rpmOver) StatusBadge("OVER LIMIT", RedDanger)
                            if (state.bearingSystem.bearings.size > 1) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            val newBrgs = state.bearingSystem.bearings
                                                .toMutableList().also { it.removeAt(origIdx) }
                                                .mapIndexed { i, br -> br.copy(bearingId = i) }
                                            onIntent(SetupIntent.UpdateBearingSystem(
                                                BearingSystem(newBrgs)))
                                        },
                                    Alignment.Center
                                ) { Text("✕", style = MaterialTheme.typography.labelSmall,
                                    color = RedDanger) }
                            }
                            Text(if (expanded) "▾" else "▸",
                                style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    // Expanded edit fields
                    AnimatedVisibility(visible = expanded,
                        enter = fadeIn() + expandVertically(),
                        exit  = fadeOut() + shrinkVertically()) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            HorizontalDivider(color = GlassRim, thickness = 1.dp,
                                modifier = Modifier.padding(bottom = 10.dp))

                            // Row 1: Designation + Z position + Preload + Housing K
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = b.designation,
                                    onValueChange = { v ->
                                        val newBrgs = state.bearingSystem.bearings.toMutableList()
                                        newBrgs[origIdx] = b.copy(designation = v)
                                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                    },
                                    label = { Text("Designation") }, singleLine = true,
                                    modifier = Modifier.weight(1.5f),
                                    colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                )
                                Column(Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = b.zPositionMm.toString(),
                                        onValueChange = { v ->
                                            v.toDoubleOrNull()?.let { d ->
                                                val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                newBrgs[origIdx] = b.copy(zPositionMm = d.coerceAtLeast(0.0))
                                                onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                            }
                                        },
                                        label = { Text("Z Position (mm)") }, singleLine = true,
                                        isError = zOver, modifier = Modifier.fillMaxWidth(),
                                        colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                    )
                                    if (zOver) Text("Exceeds shaft length",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RedDanger, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                                }
                                OutlinedTextField(
                                    value = b.preloadForceN.toString(),
                                    onValueChange = { v ->
                                        v.toDoubleOrNull()?.let { d ->
                                            val newBrgs = state.bearingSystem.bearings.toMutableList()
                                            newBrgs[origIdx] = b.copy(preloadForceN = d.coerceAtLeast(0.0))
                                            onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                        }
                                    },
                                    label = { Text("Preload (N)") }, singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                )
                                OutlinedTextField(
                                    value = b.housingStiffnessNPerMm.toString(),
                                    onValueChange = { v ->
                                        v.toDoubleOrNull()?.let { d ->
                                            val newBrgs = state.bearingSystem.bearings.toMutableList()
                                            newBrgs[origIdx] = b.copy(housingStiffnessNPerMm = d.coerceAtLeast(1.0))
                                            onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                        }
                                    },
                                    label = { Text("Housing K (N/mm)") }, singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Row 2: Bearing type (CRB/ACBB) + Preload Grade + Role + Arrangement
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Bearing Type Extended pill (CRB vs ACBB)
                                Column(Modifier.weight(0.8f)) {
                                    // k_bearing live badge
                                    val kBearing = b.housingStiffnessNPerMm
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("Type", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Box(Modifier.background(TealSubtle, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                            Text("k = ${"%.0f".format(kBearing)} N/μm",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                                color = TealBright)
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val extIdx = minOf(listIdx, state.bearingTypeExtended.size - 1)
                                        val currentExt = state.bearingTypeExtended.getOrElse(extIdx) { BearingTypeExtended.ACBB }
                                        BearingTypeExtended.entries.forEach { bt ->
                                            val sel = currentExt == bt
                                            Box(Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(if (sel) TealSubtle else NavyMid)
                                                .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(6.dp))
                                                .clickable { onIntent(SetupIntent.UpdateBearingTypeExtended(extIdx, bt)) }
                                                .padding(horizontal = 7.dp, vertical = 5.dp)) {
                                                Text(bt.name, style = MaterialTheme.typography.labelSmall,
                                                    color = if (sel) TealBright else TextSecondary)
                                            }
                                        }
                                    }
                                }
                                // Preload Grade — ACBB only
                                val extIdx2 = minOf(listIdx, state.bearingTypeExtended.size - 1)
                                val currentExt2 = state.bearingTypeExtended.getOrElse(extIdx2) { BearingTypeExtended.ACBB }
                                if (currentExt2.supportsPreloadGrade) {
                                    Column(Modifier.weight(0.9f)) {
                                        Text("Preload Grade (ACBB)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            PreloadGrade.entries.forEach { pg ->
                                                val gradeIdx = minOf(listIdx, state.bearingPreloadGrades.size - 1)
                                                val sel = state.bearingPreloadGrades.getOrElse(gradeIdx) { PreloadGrade.GB } == pg
                                                Box(Modifier.clip(RoundedCornerShape(6.dp))
                                                    .background(if (sel) TealSubtle else NavyMid)
                                                    .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        onIntent(SetupIntent.UpdatePreloadGrade(gradeIdx, pg))
                                                        val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                        newBrgs[origIdx] = b.copy(preloadForceN = pg.typicalForceN)
                                                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                                    }
                                                    .padding(horizontal = 7.dp, vertical = 5.dp)) {
                                                    Text(pg.name, style = MaterialTheme.typography.labelSmall,
                                                        color = if (sel) TealBright else TextSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                                // Role selector
                                Column(Modifier.weight(1f)) {
                                    Text("Role", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        BearingRoleV2.entries.forEach { role ->
                                            val sel = b.role == role
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (sel) TealSubtle else NavyMid)
                                                    .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                        newBrgs[origIdx] = b.copy(role = role)
                                                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(role.label, style = MaterialTheme.typography.labelSmall,
                                                    color = if (sel) TealBright else TextSecondary)
                                            }
                                        }
                                    }
                                }
                                // Arrangement selector
                                Column(Modifier.weight(1f)) {
                                    Text("Arrangement", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MountingArrangementV2.entries.forEach { arr ->
                                            val sel = b.arrangement == arr
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (sel) TealSubtle else NavyMid)
                                                    .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                        newBrgs[origIdx] = b.copy(arrangement = arr)
                                                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(arr.label, style = MaterialTheme.typography.labelSmall,
                                                    color = if (sel) TealBright else TextSecondary)
                                            }
                                        }
                                    }
                                }
                                // Preload class
                                Column(Modifier.weight(0.8f)) {
                                    Text("Preload Class", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        PreloadClassV2.entries.forEach { pc ->
                                            val sel = b.preloadClass == pc
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (sel) TealSubtle else NavyMid)
                                                    .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                        newBrgs[origIdx] = b.copy(
                                                            preloadClass  = pc,
                                                            preloadForceN = pc.typicalForceN
                                                        )
                                                        onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                                            ) {
                                                Text(pc.name, style = MaterialTheme.typography.labelSmall,
                                                    color = if (sel) TealBright else TextSecondary)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Row 3: Catalogue data
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(
                                    Triple("Bore Ø (mm)", b.boreDiameterMm.toString()) { v: Double ->
                                        b.copy(boreDiameterMm = v) },
                                    Triple("OD (mm)",     b.outerDiameterMm.toString()) { v: Double ->
                                        b.copy(outerDiameterMm = v) },
                                    Triple("C (kN)",      b.dynamicLoadRatingKn.toString()) { v: Double ->
                                        b.copy(dynamicLoadRatingKn = v) },
                                    Triple("C₀ (kN)",     b.staticLoadRatingKn.toString()) { v: Double ->
                                        b.copy(staticLoadRatingKn = v) },
                                ).forEach { (lbl, value, update) ->
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { raw ->
                                            raw.toDoubleOrNull()?.let { d ->
                                                val newBrgs = state.bearingSystem.bearings.toMutableList()
                                                newBrgs[origIdx] = update(d.coerceAtLeast(0.001))
                                                onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                            }
                                        },
                                        label = { Text(lbl) }, singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                    )
                                }
                                // Speed limit
                                OutlinedTextField(
                                    value = b.limitingSpeedRpm.toString(),
                                    onValueChange = { v ->
                                        v.toIntOrNull()?.let { d ->
                                            val newBrgs = state.bearingSystem.bearings.toMutableList()
                                            newBrgs[origIdx] = b.copy(limitingSpeedRpm = d.coerceAtLeast(100))
                                            onIntent(SetupIntent.UpdateBearingSystem(BearingSystem(newBrgs)))
                                        }
                                    },
                                    label = { Text("n_lim (RPM)") }, singleLine = true,
                                    isError = rpmOver, modifier = Modifier.weight(1f),
                                    colors = setupFieldColors(), shape = RoundedCornerShape(6.dp)
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // L10 life summary row
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (l10Preview > 0) {
                                    Box(Modifier.background(NavyMid, RoundedCornerShape(4.dp))
                                        .border(1.dp, GlassRim, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text("L₁₀ ≈ ${"%.0f".format(l10Preview)} h",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (l10Preview >= 20_000) GreenSafe else AmberWarning)
                                    }
                                }
                                Box(Modifier.background(TealSubtle, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)) {
                                    Text("k = ${"%.0f".format(b.housingStiffnessNPerMm)} N/μm",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                        color = TealBright)
                                }
                                if (rpmOver) {
                                    Box(Modifier.background(RedDanger.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .border(1.dp, RedDanger.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                        Text(
                                            "⚠  ${state.operatingSpeedRpm.toInt()} RPM exceeds limit ${b.limitingSpeedRpm} RPM",
                                            style = MaterialTheme.typography.labelSmall, color = RedDanger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Solver Engine Card ───────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a3 }) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Solver Engine", style = MaterialTheme.typography.titleSmall, color = TealBright)
                    Text("Routes to POST /api/fea/solve", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
                }
                val (sLabel, sColor) = when (state.solverStatus) {
                    SolverStatus.READY   -> "READY"   to GreenSafe
                    SolverStatus.MISSING -> "MISSING" to RedDanger
                    SolverStatus.UNKNOWN -> "—"       to TextDisabled
                }
                StatusBadge(sLabel, sColor)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SolverEngine.entries.forEach { engine ->
                    val sel = state.solverEngine == engine
                    Box(Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (sel) TealSubtle else NavyMid)
                        .border(1.dp, if (sel) TealBright else GlassRim, RoundedCornerShape(8.dp))
                        .clickable { onIntent(SetupIntent.UpdateSolverEngine(engine)) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(engine.label, style = MaterialTheme.typography.labelMedium,
                            color = if (sel) TealBright else TextSecondary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
            if (state.solverEngine != SolverEngine.INTERNAL) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.solverExecPath, { onIntent(SetupIntent.UpdateSolverExecPath(it)) },
                        label = { Text("Path to ${state.solverEngine.label} Executable") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = state.solverStatus == SolverStatus.MISSING,
                        colors = setupFieldColors(), shape = RoundedCornerShape(6.dp))
                    Button(onClick = { onIntent(SetupIntent.CheckSolverStatus) }, modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GlassSurface2, contentColor = TealBright),
                        shape = RoundedCornerShape(8.dp)) { Text("Test") }
                }
                val fmt = when (state.solverEngine) {
                    SolverEngine.ANSYS    -> "Export format: .cdb (ANSYS CDB)"
                    SolverEngine.CALCULIX -> "Export format: .inp (CalculiX, experimental)"
                    else -> ""
                }
                if (fmt.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(fmt, style = MaterialTheme.typography.labelSmall, color = TextDisabled) }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Internal SpindleFEA: BEAM188 + COMBIN14 + MASS21 — no external install required",
                    style = MaterialTheme.typography.labelSmall, color = TextDisabled)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Analysis Mode ────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth().graphicsLayer { alpha = a3 }) {
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
    } // end ScreenFadeIn
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

// ══════════════════════════════════════════════════════════════════
//  @Preview — SimulationSetupScreen
// ══════════════════════════════════════════════════════════════════

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun SimulationSetupPreview() {
    SpindleTheme {
        SimulationSetupScreen(
            state = SimulationSetupState(
                projectName = "CNC-5000 Rev C",
                geometry    = ShaftGeometry.defaultCncLathe(),
                bearingSystem = BearingSystem.defaultCncLathe(),
                operatingSpeedRpm = 12_000.0,
                radialLoadN = 1_500.0,
                simulationMode = SimulationMode.FULL_ANALYSIS
            )
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun SimulationSetupValidationErrorPreview() {
    SpindleTheme {
        SimulationSetupScreen(
            state = SimulationSetupState(
                projectName  = "",
                isSubmitting = false,
                fieldErrors  = mapOf(
                    "projectName" to "Project name is required",
                    "seg_0"       to "Segment 1: ID must be < OD"
                )
            )
        )
    }
}

