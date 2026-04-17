package com.example.spindle.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spindle.presentation.components.GlassCard
import com.example.spindle.presentation.components.ScreenFadeIn
import com.example.spindle.presentation.components.rememberStaggerAlpha
import com.example.spindle.presentation.theme.GlassRim
import com.example.spindle.presentation.theme.GlassSurface2
import com.example.spindle.presentation.theme.GreenSafe
import com.example.spindle.presentation.theme.NavyDeepest
import com.example.spindle.presentation.theme.RedDanger
import com.example.spindle.presentation.theme.SlateMid
import com.example.spindle.presentation.theme.SpindleTheme
import com.example.spindle.presentation.theme.TealBright
import com.example.spindle.presentation.theme.TextDisabled
import com.example.spindle.presentation.theme.TextPrimary
import com.example.spindle.presentation.theme.TextSecondary
import com.example.spindle.presentation.viewmodels.SettingsIntent
import com.example.spindle.presentation.viewmodels.SettingsState

// ══════════════════════════════════════════════════════════════════
//  SettingsScreen
// ══════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    state: SettingsState               = SettingsState(),
    onIntent: (SettingsIntent) -> Unit = {},
    modifier: Modifier                 = Modifier
) {
    val scrollState = rememberScrollState()
    val a0 = rememberStaggerAlpha(0, 60)
    val a1 = rememberStaggerAlpha(1, 60)

    ScreenFadeIn(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {

            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a0 },
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
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

            Row(
                Modifier.fillMaxWidth().graphicsLayer { alpha = a1 },
                Arrangement.spacedBy(16.dp)
            ) {
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
        } // end Column
    } // end ScreenFadeIn
}


// ══════════════════════════════════════════════════════════════════
//  @Preview — SettingsScreen
// ══════════════════════════════════════════════════════════════════

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun SettingsDefaultPreview() {
    SpindleTheme {
        SettingsScreen(state = SettingsState())
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun SettingsSavedPreview() {
    SpindleTheme {
        SettingsScreen(
            state = SettingsState(
                engineUrl          = "http://127.0.0.1:8000",
                ansysExecPath      = "/usr/ansys_inc/v222/ansys/bin/ansys222",
                autoNavigateOnComplete = true,
                lastSavedAt        = "2026-04-06T14:22"
            )
        )
    }
}

@Preview(widthDp = 1440, heightDp = 900)
@Composable
fun SettingsUrlErrorPreview() {
    SpindleTheme {
        SettingsScreen(
            state = SettingsState(
                engineUrl      = "not-a-url",
                engineUrlError = "Must start with http:// or https://"
            )
        )
    }
}
