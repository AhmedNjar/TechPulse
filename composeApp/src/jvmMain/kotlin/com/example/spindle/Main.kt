package com.example.spindle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.*
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.example.spindle.di.appModule
import com.example.spindle.domain.models.*
import com.example.spindle.presentation.navigation.*
import com.example.spindle.presentation.screens.*
import com.example.spindle.presentation.theme.*
import com.example.spindle.presentation.viewmodels.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() {
    startKoin { modules(appModule) }
    application {
        val lifecycle     = remember { LifecycleRegistry() }
        val rootComponent = remember { DefaultRootComponent(DefaultComponentContext(lifecycle)) }
        val windowState   = rememberWindowState(placement = WindowPlacement.Floating,
            size = DpSize(1440.dp, 900.dp), position = WindowPosition.PlatformDefault)
        Window(onCloseRequest = { lifecycle.destroy(); exitApplication() },
            state = windowState, title = "TechPulse Spindle R&D Suite  v2.0", resizable = true) {
            SpindleTheme { AppShell(rootComponent) }
        }
    }
}

@Composable
private fun AppShell(rootComponent: RootComponent) {
    val stack        by rootComponent.stack.subscribeAsState()
    val currentChild  = stack.active.instance
    val snackbar      = remember { SnackbarHostState() }
    
    // FIX: Removed 'remember' wrapper around koinInject()
    val dashVm: DashboardViewModel = koinInject()
    val dashState by dashVm.state.collectAsState()
    val sobolVm: SobolViewModel = koinInject()
    val sobolState by sobolVm.state.collectAsState()

    LaunchedEffect(dashVm) {
        dashVm.effect.onEach { e -> when (e) {
            is DashboardEffect.NavigateTo -> when (e.destination) {
                "analytics" -> rootComponent.onNavigate(NavigationItem.ANALYTICS)
                "setup"     -> rootComponent.onNavigate(NavigationItem.SIMULATION_SETUP)
            }
            is DashboardEffect.SimulationComplete -> snackbar.showSnackbar("Simulation complete ✓", duration = SnackbarDuration.Short)
            is DashboardEffect.ShowSnackbar -> snackbar.showSnackbar(e.message, duration = SnackbarDuration.Short)
        }}.launchIn(this)
    }
    DisposableEffect(dashVm) { onDispose { dashVm.onCleared() } }

    LaunchedEffect(Unit) { sobolVm.startTrainingIfNeeded() }
    LaunchedEffect(sobolVm) {
        sobolVm.effect.onEach { e -> when (e) {
            is SobolEffect.ShowSnackbar  -> snackbar.showSnackbar(e.msg, duration = SnackbarDuration.Short)
            is SobolEffect.TrainingComplete -> snackbar.showSnackbar("Surrogate ready ✓ — Run Sobol enabled", duration = SnackbarDuration.Short)
            else -> {}
        }}.launchIn(this)
    }
    DisposableEffect(sobolVm) { onDispose { sobolVm.onCleared() } }

    val currentNavItem = when (currentChild) {
        is RootComponent.Child.Dashboard       -> NavigationItem.DASHBOARD
        is RootComponent.Child.SimulationSetup -> NavigationItem.SIMULATION_SETUP
        is RootComponent.Child.Analytics       -> NavigationItem.ANALYTICS
        is RootComponent.Child.Sobol           -> NavigationItem.SOBOL
        is RootComponent.Child.Optimisation    -> NavigationItem.OPTIMISATION
        is RootComponent.Child.Settings        -> NavigationItem.SETTINGS
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                if (sobolState.trainingStatus == SobolTrainingStatus.TRAINING) {
                    Card(Modifier.padding(16.dp), colors = CardDefaults.cardColors(containerColor = GlassSurface2)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(sobolState.trainingMessage, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { sobolState.trainingProgressPct / 100f },
                                modifier = Modifier.fillMaxWidth(), color = TealBright, trackColor = SlateMid)
                        }
                    }
                } else { Snackbar(data) }
            }
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            AppSidebar(currentItem = currentNavItem, onItemClick = { rootComponent.onNavigate(it) },
                engineOnline = dashState.engineHealth.isOnline, sobolState = sobolState.trainingStatus)
            Children(stack = rootComponent.stack,
                modifier = Modifier.weight(1f).fillMaxHeight().clip(RectangleShape),
                animation = stackAnimation(slide())) { child ->
                when (child.instance) {
                    is RootComponent.Child.Dashboard       -> DashboardConnected(dashVm, snackbar, rootComponent)
                    is RootComponent.Child.SimulationSetup -> SetupConnected(dashVm, snackbar, rootComponent)
                    is RootComponent.Child.Analytics       -> AnalyticsConnected(dashState.resultId, dashState.lastSubmittedConfig?.operatingSpeedRpm ?: 12_000.0, snackbar)
                    is RootComponent.Child.Sobol           -> SobolConnected(sobolVm, snackbar)
                    is RootComponent.Child.Optimisation    -> OptimisationConnected(dashState.lastSubmittedConfig, snackbar)
                    is RootComponent.Child.Settings        -> SettingsConnected(snackbar)
                }
            }
        }
    }
}

@Composable private fun DashboardConnected(vm: DashboardViewModel, snackbar: SnackbarHostState, root: RootComponent) {
    val state by vm.state.collectAsState()
    DashboardScreen(spindleRpm = state.spindleRpm, radialLoadN = state.radialLoadN,
        deflectionMm = state.deflectionMm, bearingLifeHours = state.bearingLifeHours,
        runStatus = state.runStatus, runProgress = state.runProgress, pofPct = state.pofPct,
        reliabilityPct = state.reliabilityPct, deflectionP5 = state.deflectionP5,
        deflectionP95 = state.deflectionP95, recentRuns = state.recentRuns,
        projectName = state.activeProjectName, hasLoadedConfig = state.lastSubmittedConfig != null,
        errorMessage = state.errorMessage,
        onStartSimulation = {
            val cfg = state.lastSubmittedConfig
            if (cfg != null) vm.processIntent(DashboardIntent.StartSimulation(cfg))
            else root.onNavigate(NavigationItem.SIMULATION_SETUP)
        },
        onDismissError = { vm.processIntent(DashboardIntent.DismissError) }, modifier = Modifier.fillMaxSize())
}

@Composable private fun SetupConnected(dashVm: DashboardViewModel, snackbar: SnackbarHostState, root: RootComponent) {
    val vm: SimulationSetupViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) {
        vm.effect.onEach { e -> when (e) {
            is SetupEffect.ConfigReady -> { dashVm.processIntent(DashboardIntent.StartSimulation(e.config)); root.onNavigate(NavigationItem.DASHBOARD) }
            is SetupEffect.ValidationFailed -> snackbar.showSnackbar(e.summary, duration = SnackbarDuration.Long)
            is SetupEffect.FormReset -> snackbar.showSnackbar("Form reset")
        }}.launchIn(this)
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    SimulationSetupScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable private fun AnalyticsConnected(resultId: String?, speedRpm: Double, snackbar: SnackbarHostState) {
    val vm: AnalyticsViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(resultId) {
        if (resultId != null && resultId != state.resultId)
            vm.processIntent(AnalyticsIntent.LoadResult(resultId, speedRpm))
    }
    LaunchedEffect(vm) { vm.effect.onEach { e -> when (e) { is AnalyticsEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg) }}.launchIn(this) }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    AnalyticsScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable private fun SobolConnected(vm: SobolViewModel, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.effect.onEach { e -> when (e) { is SobolEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg); else -> {} }}.launchIn(this) }
    SobolScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable private fun OptimisationConnected(lastConfig: SpindleConfig?, snackbar: SnackbarHostState) {
    val vm: OptimisationViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.effect.onEach { e -> when (e) {
        is OptimisationEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg)
        is OptimisationEffect.ExportReady  -> snackbar.showSnackbar("CSV: ${e.csvPath}")
    }}.launchIn(this) }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    OptimizationScreen(state = state, lastConfig = lastConfig, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable private fun SettingsConnected(snackbar: SnackbarHostState) {
    val vm: SettingsViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { 
        vm.effect.onEach { e -> when (e) {
            is SettingsEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg)
            is SettingsEffect.SavedOk      -> snackbar.showSnackbar("Settings saved ✓")
        }}.launchIn(this) 
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    SettingsScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}
