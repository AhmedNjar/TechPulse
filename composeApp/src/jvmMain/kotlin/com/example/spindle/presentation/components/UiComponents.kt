package com.example.spindle.presentation.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spindle.presentation.theme.*

// ── GlassCard ─────────────────────────────────────────────────────

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color   = TealGlow,
    rimColor: Color    = GlassRim,
    cornerRadius: Dp   = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawCircle(glowColor, size.minDimension * 0.6f, Offset(0f, 0f), alpha = 0.15f)
            }
            .background(
                Brush.linearGradient(listOf(GlassSurface.copy(alpha = 0.92f), GlassSurface2.copy(alpha = 0.85f))),
                shape
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    0f   to rimColor.copy(alpha = 0.8f),
                    0.4f to rimColor.copy(alpha = 0.4f),
                    1f   to rimColor.copy(alpha = 0.1f)
                ),
                shape
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ── StatusBadge ───────────────────────────────────────────────────

@Composable
fun StatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// ── SectionHeader ─────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = GlassRim)
    }
}

// ── GlassProgressBar ─────────────────────────────────────────────
// FIX #3: animateFloatAsState ensures smooth animated fill

@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color  = SlateMid,
    fillColor: Color   = TealBright
) {
    // Animate the fill width smoothly whenever progress changes
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label         = "progressAnim"
    )
    Box(modifier = modifier.height(5.dp).background(trackColor, RoundedCornerShape(50))) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(
                    Brush.horizontalGradient(listOf(TealDeep, fillColor)),
                    RoundedCornerShape(50)
                )
        )
    }
}

// ── LiveDataCard ─────────────────────────────────────────────────
// FIX #1: Removed .width(160.dp) — callers control sizing via modifier
// FIX #4: CRITICAL status uses pulse; all others use steady alpha = 1f

enum class DataCardStatus { NOMINAL, WARNING, CRITICAL, OFFLINE }

@Composable
fun LiveDataCard(
    label: String,
    value: String,
    unit: String,
    status: DataCardStatus = DataCardStatus.NOMINAL,
    modifier: Modifier     = Modifier           // ← Callers control size (weight/fillMaxWidth)
) {
    val statusColor = when (status) {
        DataCardStatus.NOMINAL  -> GreenSafe
        DataCardStatus.WARNING  -> AmberWarning
        DataCardStatus.CRITICAL -> RedDanger
        DataCardStatus.OFFLINE  -> TextDisabled
    }

    // FIX #4: Only animate for CRITICAL — all other states are stable
    val dotAlpha by if (status == DataCardStatus.CRITICAL) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue  = 0.4f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label         = "dotPulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    GlassCard(
        modifier  = modifier,                           // ← Use caller's modifier directly
        glowColor = statusColor.copy(alpha = 0.12f),
        rimColor  = if (status == DataCardStatus.NOMINAL) GlassRim else statusColor.copy(alpha = 0.5f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(statusColor.copy(alpha = dotAlpha), RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            style      = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color      = statusColor,
            maxLines   = 1,
            softWrap   = false
        )
        Text(unit, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

// ── AnimatedCounter ────────────────────────────────────────────────
// Counts up to a target number with an eased animation.
// Use for KPI values like deflection, RPM, POF % when results arrive.

@Composable
fun AnimatedCounter(
    targetValue: Double,
    format: String         = "%.2f",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displaySmall,
    color: Color           = TealBright
) {
    val animatedValue by animateFloatAsState(
        targetValue   = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label         = "counter"
    )
    Text(
        text  = format.format(animatedValue),
        style = style,
        color = color
    )
}

// ── ScreenFadeIn ──────────────────────────────────────────────────
// Wrap screen root with this to get a clean fade-in entrance effect

@Composable
fun ScreenFadeIn(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label         = "screenFade"
    )
    LaunchedEffect(Unit) { visible = true }
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }, content = content)
}

// ── StaggeredColumn ───────────────────────────────────────────────
// Renders children with a staggered slide-up + fade-in entrance

@Composable
fun StaggeredColumn(
    modifier: Modifier          = Modifier,
    itemDelayMs: Int            = 60,
    content: @Composable ColumnScope.() -> Unit
) {
    // Each direct child gets its own staggered delay via LaunchedEffect in the child.
    // This wrapper just provides the column layout; children call rememberStaggerAlpha().
    Column(modifier = modifier, content = content)
}

/**
 * Returns an animated alpha for a staggered entrance.
 * Call inside each card/section: val alpha = rememberStaggerAlpha(index = 0)
 */
@Composable
fun rememberStaggerAlpha(index: Int, delayMs: Int = 60): Float {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label         = "stagger_$index"
    )
    val offsetY by animateFloatAsState(
        targetValue   = if (visible) 0f else 20f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label         = "staggerY_$index"
    )
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * delayMs).toLong())
        visible = true
    }
    return alpha
}

// ══════════════════════════════════════════════════════════════════
//  @Preview functions — visible in the IntelliJ Compose preview panel
// ══════════════════════════════════════════════════════════════════

@Preview
@Composable
private fun GlassCardPreview() {
    SpindleTheme {
        GlassCard(modifier = Modifier.width(300.dp)) {
            Text("Glass Card Preview", color = TextPrimary)
            Text("Sub-text content", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Preview
@Composable
private fun StatusBadgePreview() {
    SpindleTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            StatusBadge("ONLINE",  GreenSafe)
            StatusBadge("RUNNING", AmberWarning)
            StatusBadge("FAILED",  RedDanger)
            StatusBadge("OFFLINE", TextDisabled)
        }
    }
}

@Preview
@Composable
private fun LiveDataCardNominalPreview() {
    SpindleTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
            LiveDataCard("Spindle RPM", "12,500", "RPM", DataCardStatus.NOMINAL, Modifier.width(160.dp))
            LiveDataCard("Deflection",  "0.0312", "mm",  DataCardStatus.WARNING,  Modifier.width(160.dp))
            LiveDataCard("Bearing Life","4,200",  "h L10",DataCardStatus.NOMINAL, Modifier.width(160.dp))
            LiveDataCard("Sensor",      "—",      "N/A",  DataCardStatus.OFFLINE,  Modifier.width(160.dp))
        }
    }
}

@Preview
@Composable
private fun GlassProgressBarPreview() {
    SpindleTheme {
        Column(Modifier.padding(24.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassProgressBar(0.25f, Modifier.fillMaxWidth())
            GlassProgressBar(0.65f, Modifier.fillMaxWidth(), fillColor = AmberWarning)
            GlassProgressBar(1.0f,  Modifier.fillMaxWidth(), fillColor = GreenSafe)
        }
    }
}
