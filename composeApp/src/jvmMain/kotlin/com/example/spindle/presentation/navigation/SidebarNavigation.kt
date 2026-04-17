package com.example.spindle.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spindle.presentation.components.StatusBadge
import com.example.spindle.presentation.theme.*
import com.example.spindle.presentation.viewmodels.SobolTrainingStatus

@Composable
fun AppSidebar(
    currentItem: NavigationItem,
    onItemClick: (NavigationItem) -> Unit,
    engineOnline: Boolean,
    sobolState: SobolTrainingStatus = SobolTrainingStatus.IDLE,
    modifier: Modifier = Modifier
) {
    Column(modifier.width(220.dp).fillMaxHeight()
        .background(Brush.verticalGradient(listOf(SlateDeep, NavyDeepest)))
        .border(1.dp, Brush.verticalGradient(listOf(GlassRim, GlassRim.copy(alpha = 0.2f))), RoundedCornerShape(0.dp))
        .padding(vertical = 16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp)
                    .background(Brush.linearGradient(listOf(TealDeep, TealBright)), RoundedCornerShape(8.dp))
            ) { Text("S", color = NavyDeepest, fontSize = 18.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Spindle Suite", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("R&D Platform", style = MaterialTheme.typography.labelSmall, color = TealBright)
            }
        }
        Spacer(Modifier.height(20.dp))

        NavigationItem.entries.filter { it != NavigationItem.SETTINGS }.forEach { item ->
            val badge: @Composable (() -> Unit)? = when {
                item == NavigationItem.SOBOL && sobolState == SobolTrainingStatus.TRAINING -> {
                    { SobolTrainingBadge() }
                }
                item == NavigationItem.SOBOL && sobolState == SobolTrainingStatus.DONE -> {
                    { StatusBadge("READY", GreenSafe) }
                }
                else -> null
            }
            SidebarItem(item = item, isActive = item == currentItem, onClick = { onItemClick(item) }, badge = badge)
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassRim, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("Engine", style = MaterialTheme.typography.labelSmall, color = TextDisabled)
            Spacer(Modifier.width(8.dp))
            StatusBadge(if (engineOnline) "ONLINE" else "OFFLINE", if (engineOnline) GreenSafe else RedDanger)
        }
        Spacer(Modifier.height(8.dp))
        SidebarItem(NavigationItem.SETTINGS, currentItem == NavigationItem.SETTINGS, { onItemClick(NavigationItem.SETTINGS) })
    }
}

@Composable
private fun SobolTrainingBadge() {
    val shimmer by rememberInfiniteTransition(label = "sh").animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shim")
    Box(Modifier.background(AmberWarning.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
        .border(1.dp, AmberWarning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("TRAIN", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = AmberWarning.copy(alpha = shimmer))
    }
}

@Composable
private fun SidebarItem(item: NavigationItem, isActive: Boolean, onClick: () -> Unit,
    badge: @Composable (() -> Unit)? = null) {
    val bgColor  by animateColorAsState(if (isActive) TealSubtle else Color.Transparent, tween(200), label = "bg")
    val txtColor by animateColorAsState(if (isActive) TealBright else TextSecondary, tween(200), label = "txt")
    val barColor by animateColorAsState(if (isActive) TealBright else Color.Transparent, tween(200), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp)).background(bgColor)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Box(Modifier.width(3.dp).height(18.dp).background(barColor, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Text(item.icon, fontSize = 15.sp, color = txtColor)
        Spacer(Modifier.width(10.dp))
        Text(item.label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal),
            color = txtColor, maxLines = 1, softWrap = false)
        badge?.invoke()
    }
}

@Preview(widthDp = 220, heightDp = 900)
@Composable
private fun SidebarReadyPreview() {
    SpindleTheme { AppSidebar(NavigationItem.DASHBOARD, {}, true, SobolTrainingStatus.DONE) }
}
@Preview(widthDp = 220, heightDp = 900)
@Composable
fun SidebarTrainingPreview() {
    SpindleTheme { AppSidebar(NavigationItem.SOBOL, {}, false, SobolTrainingStatus.TRAINING) }
}
