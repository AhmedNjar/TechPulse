package com.example.spindle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.spindle.domain.models.SpindleConfig
import com.example.spindle.presentation.navigation.*
import com.example.spindle.presentation.screens.*
import com.example.spindle.presentation.theme.SpindleTheme
import com.example.spindle.presentation.viewmodels.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() {

    // Boot Koin before any Compose code
    startKoin { modules(appModule) }

    application {
        val lifecycle     = remember { LifecycleRegistry() }
        val rootComponent = remember {
            DefaultRootComponent(DefaultComponentContext(lifecycle))
        }
        val windowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            size      = DpSize(1440.dp, 900.dp),
            position  = WindowPosition.PlatformDefault
        )

        Window(
            onCloseRequest = { lifecycle.destroy(); exitApplication() },
            state          = windowState,
            title          = "TechPulse Spindle R&D Suite  v2.0",
            resizable      = true
        ) {
            SpindleTheme {
                AppShell(rootComponent)
            }
        }
    }
}

// ── AppShell ──────────────────────────────────────────────────────

@Composable
private fun AppShell(rootComponent: RootComponent) {
    val stack        by rootComponent.stack.subscribeAsState()
    val currentChild  = stack.active.instance

    val snackbarHostState = remember { SnackbarHostState() }
    val dashVm: DashboardViewModel = koinInject()
    val dashState by dashVm.state.collectAsState()

    // Collect Dashboard effects
    LaunchedEffect(dashVm) {
        dashVm.effect.onEach { effect ->
            when (effect) {
                is DashboardEffect.NavigateTo -> when (effect.destination) {
                    "analytics" -> rootComponent.onNavigate(NavigationItem.ANALYTICS)
                    "setup"     -> rootComponent.onNavigate(NavigationItem.SIMULATION_SETUP)
                }
                is DashboardEffect.SimulationComplete -> {
                    if (dashState.engineHealth.isOnline) {
                        // Respect user setting: only auto-navigate if configured
                        snackbarHostState.showSnackbar("Simulation complete ✓", duration = SnackbarDuration.Short)
                    }
                }
                is DashboardEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message, duration = SnackbarDuration.Short)
            }
        }.launchIn(this)
    }
    DisposableEffect(dashVm) { onDispose { dashVm.onCleared() } }

    val currentNavItem = when (currentChild) {
        is RootComponent.Child.Dashboard       -> NavigationItem.DASHBOARD
        is RootComponent.Child.SimulationSetup -> NavigationItem.SIMULATION_SETUP
        is RootComponent.Child.Analytics       -> NavigationItem.ANALYTICS
        is RootComponent.Child.Optimisation    -> NavigationItem.OPTIMISATION
        is RootComponent.Child.Settings        -> NavigationItem.SETTINGS
    }

    Scaffold(
        snackbarHost     = { SnackbarHost(snackbarHostState) },
        containerColor   = androidx.compose.ui.graphics.Color.Transparent
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            AppSidebar(
                currentItem  = currentNavItem,
                onItemClick  = { rootComponent.onNavigate(it) },
                engineOnline = dashState.engineHealth.isOnline
            )
            Children(
                stack     = rootComponent.stack,
                modifier  = Modifier.weight(1f).fillMaxHeight(),
                animation = stackAnimation(slide())
            ) { child ->
                when (child.instance) {
                    is RootComponent.Child.Dashboard ->
                        DashboardScreenConnected(dashVm, snackbarHostState, rootComponent)
                    is RootComponent.Child.SimulationSetup ->
                        SetupScreenConnected(dashVm, snackbarHostState, rootComponent)
                    is RootComponent.Child.Analytics ->
                        AnalyticsScreenConnected(dashState.resultId, snackbarHostState)
                    is RootComponent.Child.Optimisation ->
                        OptimisationScreenConnected(dashState.lastSubmittedConfig, snackbarHostState)
                    is RootComponent.Child.Settings ->
                        SettingsScreenConnected(snackbarHostState)
                }
            }
        }
    }
}

// ── Screen connectors ─────────────────────────────────────────────

@Composable
private fun DashboardScreenConnected(
    vm: DashboardViewModel,
    snackbar: SnackbarHostState,
    root: RootComponent
) {
    val state by vm.state.collectAsState()
    DashboardScreen(
        spindleRpm       = state.spindleRpm,
        radialLoadN      = state.radialLoadN,
        deflectionMm     = state.deflectionMm,
        bearingLifeHours = state.bearingLifeHours,
        runStatus        = state.runStatus,
        runProgress      = state.runProgress,
        pofPct           = state.pofPct,
        reliabilityPct   = state.reliabilityPct,
        deflectionP5     = state.deflectionP5,
        deflectionP95    = state.deflectionP95,
        recentRuns       = state.recentRuns,
        projectName      = state.activeProjectName,
        hasLoadedConfig  = state.lastSubmittedConfig != null,
        errorMessage     = state.errorMessage,
        onStartSimulation = {
            val cfg = state.lastSubmittedConfig
            if (cfg != null) vm.processIntent(DashboardIntent.StartSimulation(cfg))
            else root.onNavigate(NavigationItem.SIMULATION_SETUP)
        },
        onDismissError   = { vm.processIntent(DashboardIntent.DismissError) },
        modifier         = Modifier.fillMaxSize()
    )
}

@Composable
private fun SetupScreenConnected(
    dashVm: DashboardViewModel,
    snackbar: SnackbarHostState,
    root: RootComponent
) {
    val vm: SimulationSetupViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) {
        vm.effect.onEach { effect ->
            when (effect) {
                is SetupEffect.ConfigReady -> {
                    dashVm.processIntent(DashboardIntent.StartSimulation(effect.config))
                    root.onNavigate(NavigationItem.DASHBOARD)
                }
                is SetupEffect.ValidationFailed ->
                    snackbar.showSnackbar(effect.summary, duration = SnackbarDuration.Long)
                is SetupEffect.FormReset ->
                    snackbar.showSnackbar("Form reset to defaults")
            }
        }.launchIn(this)
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    SimulationSetupScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun AnalyticsScreenConnected(resultId: String?, snackbar: SnackbarHostState) {
    val vm: AnalyticsViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(resultId) {
        if (resultId != null && resultId != state.resultId)
            vm.processIntent(AnalyticsIntent.LoadResult(resultId))
    }
    LaunchedEffect(vm) {
        vm.effect.onEach { e ->
            when (e) { is AnalyticsEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg) }
        }.launchIn(this)
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    AnalyticsScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun OptimisationScreenConnected(
    lastConfig: SpindleConfig?,
    snackbar: SnackbarHostState
) {
    val vm: OptimisationViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) {
        vm.effect.onEach { e ->
            when (e) {
                is OptimisationEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg)
                is OptimisationEffect.ExportReady  -> snackbar.showSnackbar("CSV: ${e.csvPath}")
            }
        }.launchIn(this)
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    OptimizationScreen(state = state, lastConfig = lastConfig,
        onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun SettingsScreenConnected(snackbar: SnackbarHostState) {
    val vm: SettingsViewModel = koinInject()
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) {
        vm.effect.onEach { e ->
            when (e) {
                is SettingsEffect.ShowSnackbar -> snackbar.showSnackbar(e.msg)
                is SettingsEffect.SavedOk      -> snackbar.showSnackbar("Settings saved ✓")
            }
        }.launchIn(this)
    }
    DisposableEffect(vm) { onDispose { vm.onCleared() } }
    SettingsScreen(state = state, onIntent = { vm.processIntent(it) }, modifier = Modifier.fillMaxSize())
}
