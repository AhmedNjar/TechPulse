package com.example.spindle.presentation.viewmodels

import com.example.spindle.data.repository.OptimisationSnapshot
import com.example.spindle.data.repository.ParetoPoint
import com.example.spindle.domain.models.SpindleConfig
import com.example.spindle.domain.usecases.StartOptimisationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

enum class OptPhase { IDLE, INITIALISING, EVOLVING, CONVERGED, FAILED }

data class OptimisationState(
    val phase: OptPhase                      = OptPhase.IDLE,
    val currentGeneration: Int               = 0,
    val maxGenerations: Int                  = 50,
    val populationSize: Int                  = 120,
    val bestFitnessHistory: List<Double>     = emptyList(),
    val meanFitnessHistory: List<Double>     = emptyList(),
    val bestDeflHistory: List<Double>        = emptyList(),
    val bestFreqHistory: List<Double>        = emptyList(),
    val bestWeightHistory: List<Double>      = emptyList(),
    val currentParetoFront: List<ParetoPoint> = emptyList(),
    val constraintViolations: Int            = 0,
    val improvementPct: Double               = 0.0,
    val hasConverged: Boolean                = false,
    val convergenceGeneration: Int?          = null,
    val errorMessage: String?                = null
) : MviState

sealed interface OptimisationIntent : MviIntent {
    data class StartEvolution(val config: SpindleConfig) : OptimisationIntent
    data object StopEvolution                             : OptimisationIntent
    data object ResetState                                : OptimisationIntent
    data object ExportParetoFront                         : OptimisationIntent
}

sealed interface OptimisationEffect : MviEffect {
    data class ShowSnackbar(val msg: String)              : OptimisationEffect
    data class ExportReady(val csvPath: String)           : OptimisationEffect
}

class OptimisationViewModel(
    private val startOptimisationUseCase: StartOptimisationUseCase
) : BaseViewModel<OptimisationState, OptimisationIntent, OptimisationEffect>(OptimisationState()) {

    private var job: Job? = null

    override fun processIntent(intent: OptimisationIntent) = when (intent) {
        is OptimisationIntent.StartEvolution  -> startEvolution(intent.config)
        is OptimisationIntent.StopEvolution   -> stop()
        is OptimisationIntent.ResetState      -> { stop(); updateState { OptimisationState() } }
        is OptimisationIntent.ExportParetoFront -> export()
    }

    private fun startEvolution(config: SpindleConfig) {
        if (currentState.phase == OptPhase.EVOLVING) return
        updateState { OptimisationState(phase = OptPhase.INITIALISING) }

        job = scope.launch {
            startOptimisationUseCase(config)
                .catch { e -> updateState { copy(phase = OptPhase.FAILED, errorMessage = e.message) } }
                .onCompletion { cause ->
                    if (cause == null && currentState.phase == OptPhase.EVOLVING)
                        updateState { copy(phase = OptPhase.CONVERGED, hasConverged = true) }
                }
                .collect { snap ->
                    val history   = currentState.bestFitnessHistory
                    val converged = history.size >= 10 &&
                        history.takeLast(10).let { r ->
                            (r.first() - r.last()) / r.first().coerceAtLeast(1e-10) < 0.001
                        }
                    val baseline   = history.firstOrNull() ?: snap.bestFitnessScore
                    val improvement = if (baseline > 0) (baseline - snap.bestFitnessScore) / baseline * 100 else 0.0

                    updateState {
                        copy(
                            phase               = OptPhase.EVOLVING,
                            currentGeneration   = snap.generation,
                            bestFitnessHistory  = bestFitnessHistory + snap.bestFitnessScore,
                            meanFitnessHistory  = meanFitnessHistory + snap.meanFitnessScore,
                            bestDeflHistory     = bestDeflHistory    + snap.bestDeflectionMm,
                            bestFreqHistory     = bestFreqHistory    + snap.bestFreqHz,
                            bestWeightHistory   = bestWeightHistory  + snap.bestWeightKg,
                            currentParetoFront  = snap.paretoPoints,
                            constraintViolations= snap.constraintViolations,
                            improvementPct      = improvement,
                            hasConverged        = converged,
                            convergenceGeneration = if (converged && convergenceGeneration == null)
                                snap.generation else convergenceGeneration
                        )
                    }
                }
        }
    }

    private fun stop() {
        job?.cancel(); job = null
        updateState { copy(phase = OptPhase.IDLE) }
        emitEffect(OptimisationEffect.ShowSnackbar("Evolution stopped"))
    }

    private fun export() {
        if (currentState.currentParetoFront.isEmpty()) {
            emitEffect(OptimisationEffect.ShowSnackbar("No Pareto data to export"))
            return
        }
        emitEffect(OptimisationEffect.ShowSnackbar("CSV export — implement file write in v2.1"))
    }

    override fun onCleared() { job?.cancel(); super.onCleared() }
}
