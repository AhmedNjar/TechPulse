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
                Brush.linearGradient(listOf(GlassSurface.copy(0.92f), GlassSurface2.copy(0.85f))),
                shape
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    0f to rimColor.copy(0.8f), 0.4f to rimColor.copy(0.4f), 1f to rimColor.copy(0.1f)
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
            .background(color.copy(0.18f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(0.45f), RoundedCornerShape(6.dp))
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

// ── GlassProgressBar ──────────────────────────────────────────────

@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier    = Modifier,
    trackColor: Color     = SlateMid,
    fillColor: Color      = TealBright
) {
    Box(modifier = modifier.height(5.dp).background(trackColor, RoundedCornerShape(50))) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(TealDeep, fillColor)), RoundedCornerShape(50))
        )
    }
}

// ── LiveDataCard ──────────────────────────────────────────────────

enum class DataCardStatus { NOMINAL, WARNING, CRITICAL, OFFLINE }

@Composable
fun LiveDataCard(
    label: String,
    value: String,
    unit: String,
    status: DataCardStatus = DataCardStatus.NOMINAL,
    modifier: Modifier     = Modifier
) {
    val statusColor = when (status) {
        DataCardStatus.NOMINAL  -> GreenSafe
        DataCardStatus.WARNING  -> AmberWarning
        DataCardStatus.CRITICAL -> RedDanger
        DataCardStatus.OFFLINE  -> TextDisabled
    }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        initialValue   = 0.5f,
        targetValue    = 1f,
        animationSpec  = if (status == DataCardStatus.CRITICAL)
            infiniteRepeatable(tween(800), RepeatMode.Reverse)
        else infiniteRepeatable(tween(Int.MAX_VALUE)),
        label          = "pa"
    )

    GlassCard(
        modifier  = modifier.width(160.dp),
        glowColor = statusColor.copy(0.12f),
        rimColor  = if (status == DataCardStatus.NOMINAL) GlassRim else statusColor.copy(0.5f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(statusColor.copy(pulse), RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = statusColor)
        Text(unit,  style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}
