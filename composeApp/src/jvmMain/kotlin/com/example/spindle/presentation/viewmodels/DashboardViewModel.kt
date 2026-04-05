package com.example.spindle.presentation.viewmodels

import com.example.spindle.data.repository.EngineHealth
import com.example.spindle.data.repository.SimulationRepository
import com.example.spindle.data.repository.SimulationState
import com.example.spindle.domain.models.SimulationResult
import com.example.spindle.domain.models.SpindleConfig
import com.example.spindle.domain.usecases.RunSimulationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

// ── State ─────────────────────────────────────────────────────────

enum class RunStatus { IDLE, SUBMITTING, RUNNING, COMPLETE, FAILED }

data class RunHistoryEntry(
    val resultId: String,
    val label: String,
    val relativeTime: String,
    val status: RunStatus
)

data class DashboardState(
    val engineHealth: EngineHealth       = EngineHealth(false, false, false, false, "—"),
    val spindleRpm: Double               = 0.0,
    val radialLoadN: Double              = 0.0,
    val deflectionMm: Double            = 0.0,
    val bearingLifeHours: Double        = 0.0,
    val vonMisesStressMpa: Double       = 0.0,
    val runStatus: RunStatus             = RunStatus.IDLE,
    val runProgress: Float               = 0f,
    val resultId: String?                = null,
    val pofPct: Double?                  = null,
    val reliabilityPct: Double?          = null,
    val deflectionP5: Double?            = null,
    val deflectionP95: Double?           = null,
    val recentRuns: List<RunHistoryEntry> = emptyList(),
    val activeProjectName: String        = "No project loaded",
    val lastSubmittedConfig: SpindleConfig? = null,
    val errorMessage: String?            = null
) : MviState

// ── Intents / Effects ─────────────────────────────────────────────

sealed interface DashboardIntent : MviIntent {
    data class StartSimulation(val config: SpindleConfig) : DashboardIntent
    data object DismissError                              : DashboardIntent
    data object RefreshEngineHealth                       : DashboardIntent
}

sealed interface DashboardEffect : MviEffect {
    data class NavigateTo(val destination: String)  : DashboardEffect
    data class ShowSnackbar(val message: String)    : DashboardEffect
    data object SimulationComplete                  : DashboardEffect
}

// ── ViewModel ─────────────────────────────────────────────────────

class DashboardViewModel(
    private val runSimulationUseCase: RunSimulationUseCase,
    private val repository: SimulationRepository
) : BaseViewModel<DashboardState, DashboardIntent, DashboardEffect>(DashboardState()) {

    init {
        checkHealth()
        scope.launch { while (true) { delay(30_000); checkHealth() } }
    }

    override fun processIntent(intent: DashboardIntent) = when (intent) {
        is DashboardIntent.StartSimulation  -> startSimulation(intent.config)
        is DashboardIntent.DismissError     -> updateState { copy(errorMessage = null) }
        is DashboardIntent.RefreshEngineHealth -> checkHealth()
    }

    private fun startSimulation(config: SpindleConfig) {
        if (currentState.runStatus == RunStatus.RUNNING) return
        scope.launch {
            runSimulationUseCase(config)
                .flowOn(Dispatchers.Default)
                .catch { e -> handleError("Simulation error: ${e.message}") }
                .collect { simState ->
                    when (simState) {
                        is SimulationState.Idle      -> updateState { copy(runStatus = RunStatus.IDLE, runProgress = 0f) }
                        is SimulationState.Submitted -> updateState {
                            copy(runStatus = RunStatus.SUBMITTING, resultId = simState.resultId,
                                runProgress = 0f, activeProjectName = config.projectName,
                                lastSubmittedConfig = config)
                        }
                        is SimulationState.Running   -> updateState {
                            copy(runStatus = RunStatus.RUNNING, runProgress = simState.progressPct / 100f)
                        }
                        is SimulationState.Success   -> applyResult(simState.result, config)
                        is SimulationState.Error     -> handleError(simState.message)
                    }
                }
        }
    }

    private fun applyResult(result: SimulationResult, config: SpindleConfig) {
        val mc    = result.monteCarlo
        val entry = RunHistoryEntry(result.resultId, "Run-${result.resultId.takeLast(4).uppercase()}",
            "just now", RunStatus.COMPLETE)
        updateState {
            copy(
                runStatus           = RunStatus.COMPLETE,
                runProgress         = 1f,
                resultId            = result.resultId,
                deflectionMm        = result.static?.toolTipDeflectionMm ?: 0.0,
                vonMisesStressMpa   = result.static?.maxVonMisesMpa ?: 0.0,
                bearingLifeHours    = mc?.bearingLives?.firstOrNull()?.l10LifeMeanHours ?: 0.0,
                spindleRpm          = config.operatingSpeedRpm,
                radialLoadN         = config.radialLoadN,
                pofPct              = mc?.probabilityOfFailurePct,
                reliabilityPct      = mc?.reliabilityPct,
                deflectionP5        = mc?.deflectionP5Mm,
                deflectionP95       = mc?.deflectionP95Mm,
                recentRuns          = (listOf(entry) + recentRuns).take(8),
                lastSubmittedConfig = config,
                errorMessage        = null
            )
        }
        emitEffect(DashboardEffect.SimulationComplete)
        emitEffect(DashboardEffect.ShowSnackbar("Simulation complete ✓"))
    }

    private fun handleError(msg: String) {
        updateState { copy(runStatus = RunStatus.FAILED, errorMessage = msg) }
        emitEffect(DashboardEffect.ShowSnackbar(msg))
    }

    private fun checkHealth() {
        scope.launch {
            val health = repository.checkEngineHealth()
            updateState { copy(engineHealth = health) }
        }
    }
}
