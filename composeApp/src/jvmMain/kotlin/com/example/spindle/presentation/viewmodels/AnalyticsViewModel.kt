package com.example.spindle.presentation.viewmodels

import com.example.spindle.data.repository.SimulationRepository
import com.example.spindle.domain.models.*
import kotlinx.coroutines.launch

data class AnalyticsState(
    val isLoading: Boolean                    = false,
    val resultId: String?                     = null,
    val runType: SimulationMode               = SimulationMode.FULL_ANALYSIS,
    val iterations: Int                       = 0,
    val confidenceLevel: Double               = 0.95,

    // Static
    val staticDeflectionMm: Double            = 0.0,
    val toolTipDeflectionMm: Double           = 0.0,
    val maxDeflectionLocationMm: Double       = 0.0,
    val maxBendingStressMpa: Double           = 0.0,
    val maxVonMisesMpa: Double                = 0.0,
    val safetyFactorYield: Double             = 0.0,
    val safetyFactorFatigue: Double           = 0.0,
    val solverVersion: String                 = "—",
    val nElements: Int                        = 0,
    val nNodes: Int                           = 0,
    val renderImageB64: String?               = null,

    // Modal
    val modeShapes: List<ModeShape>                   = emptyList(),
    val spindleFrequencyHz: Double                    = 0.0,
    val firstCriticalSpeedRpm: Double                 = 0.0,
    val criticalSpeedSafetyFactor: Double             = 0.0,

    // Monte Carlo
    val pofPct: Double                        = 0.0,
    val reliabilityPct: Double                = 100.0,
    val deflectionMeanMm: Double              = 0.0,
    val deflectionStdMm: Double               = 0.0,
    val deflectionP5Mm: Double                = 0.0,
    val deflectionP95Mm: Double               = 0.0,
    val stressMeanMpa: Double                 = 0.0,
    val stressP95Mpa: Double                  = 0.0,
    val freqMeanHz: Double                    = 0.0,
    val freqP5Hz: Double                      = 0.0,
    val runoutEffectMeanN: Double             = 0.0,
    val eccentricityEffectMeanN: Double       = 0.0,
    val sensitivityFactors: Map<String, Double> = emptyMap(),

    // Bearing life
    val bearingLives: List<ISO281BearingLife> = emptyList(),

    // Taguchi
    val taguchiResult: TaguchiResult?         = null,

    val errorMessage: String?                 = null
) : MviState

sealed interface AnalyticsIntent : MviIntent {
    data class LoadResult(val resultId: String) : AnalyticsIntent
    data object LoadDemoTaguchi                  : AnalyticsIntent
    data object ExportReport                     : AnalyticsIntent
    data object DismissError                     : AnalyticsIntent
}

sealed interface AnalyticsEffect : MviEffect {
    data class ShowSnackbar(val msg: String) : AnalyticsEffect
}

class AnalyticsViewModel(
    private val repository: SimulationRepository
) : BaseViewModel<AnalyticsState, AnalyticsIntent, AnalyticsEffect>(AnalyticsState()) {

    override fun processIntent(intent: AnalyticsIntent) = when (intent) {
        is AnalyticsIntent.LoadResult     -> loadResult(intent.resultId)
        is AnalyticsIntent.LoadDemoTaguchi -> loadDemo()
        is AnalyticsIntent.ExportReport   -> emitEffect(AnalyticsEffect.ShowSnackbar("PDF export coming in v2.1"))
        is AnalyticsIntent.DismissError   -> updateState { copy(errorMessage = null) }
    }

    private fun loadResult(resultId: String) {
        if (currentState.resultId == resultId) return
        updateState { copy(isLoading = true, errorMessage = null, resultId = resultId) }
        scope.launch {
            try {
                val result = repository.getResultById(resultId)
                if (result == null) {
                    updateState { copy(isLoading = false, errorMessage = "Result not found or still running.") }
                    return@launch
                }
                val s  = result.static
                val m  = result.modal
                val mc = result.monteCarlo
                updateState {
                    copy(
                        isLoading                 = false,
                        runType                   = result.mode,
                        staticDeflectionMm        = s?.maxDeflectionMm ?: 0.0,
                        toolTipDeflectionMm       = s?.toolTipDeflectionMm ?: 0.0,
                        maxDeflectionLocationMm   = s?.maxDeflectionLocationMm ?: 0.0,
                        maxBendingStressMpa       = s?.maxBendingStressMpa ?: 0.0,
                        maxVonMisesMpa            = s?.maxVonMisesMpa ?: 0.0,
                        safetyFactorYield         = s?.safetyFactorYield ?: 0.0,
                        safetyFactorFatigue       = s?.safetyFactorFatigue ?: 0.0,
                        solverVersion             = s?.solverVersion ?: "—",
                        nElements                 = s?.nElements ?: 0,
                        nNodes                    = s?.nNodes ?: 0,
                        renderImageB64            = s?.renderImageB64,
                        modeShapes                = m?.modeShapes ?: emptyList(),
                        spindleFrequencyHz        = m?.spindleFrequencyHz ?: 0.0,
                        firstCriticalSpeedRpm     = m?.firstCriticalSpeedRpm ?: 0.0,
                        criticalSpeedSafetyFactor = m?.safetyFactorToFirstCritical ?: 0.0,
                        iterations                = mc?.nIterations ?: 0,
                        pofPct                    = mc?.probabilityOfFailurePct ?: 0.0,
                        reliabilityPct            = mc?.reliabilityPct ?: 100.0,
                        deflectionMeanMm          = mc?.deflectionMeanMm ?: 0.0,
                        deflectionStdMm           = mc?.deflectionStdMm ?: 0.0,
                        deflectionP5Mm            = mc?.deflectionP5Mm ?: 0.0,
                        deflectionP95Mm           = mc?.deflectionP95Mm ?: 0.0,
                        stressMeanMpa             = mc?.stressMeanMpa ?: 0.0,
                        stressP95Mpa              = mc?.stressP95Mpa ?: 0.0,
                        freqMeanHz                = mc?.freqMeanHz ?: 0.0,
                        freqP5Hz                  = mc?.freqP5Hz ?: 0.0,
                        runoutEffectMeanN         = mc?.runoutEffectMeanN ?: 0.0,
                        eccentricityEffectMeanN   = mc?.eccentricityEffectMeanN ?: 0.0,
                        sensitivityFactors        = mc?.sensitivity ?: emptyMap(),
                        bearingLives              = mc?.bearingLives ?: emptyList(),
                        taguchiResult             = result.taguchi
                    )
                }
            } catch (e: Exception) {
                updateState { copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun loadDemo() {
        // Demo Taguchi L9 data
        val factors = listOf(
            TaguchiControlFactor("Front OD", "mm", 70.0, 80.0, 90.0),
            TaguchiControlFactor("Preload", "N", 150.0, 280.0, 450.0),
            TaguchiControlFactor("Contact Angle", "°", 15.0, 25.0, 40.0),
            TaguchiControlFactor("Body Length", "mm", 150.0, 180.0, 210.0),
        )
        val runs = listOf(
            TaguchiRun(1, mapOf("Front OD" to 1,"Preload" to 1,"Contact Angle" to 1,"Body Length" to 1), 0.0312, 420.0, 285.0, 30.1, -52.5, -49.1),
            TaguchiRun(2, mapOf("Front OD" to 1,"Preload" to 2,"Contact Angle" to 2,"Body Length" to 2), 0.0298, 445.0, 271.0, 30.5, -53.0, -48.7),
            TaguchiRun(3, mapOf("Front OD" to 1,"Preload" to 3,"Contact Angle" to 3,"Body Length" to 3), 0.0285, 468.0, 258.0, 30.9, -53.4, -48.2),
            TaguchiRun(4, mapOf("Front OD" to 2,"Preload" to 1,"Contact Angle" to 2,"Body Length" to 3), 0.0218, 512.0, 224.0, 33.2, -54.2, -47.0),
            TaguchiRun(5, mapOf("Front OD" to 2,"Preload" to 2,"Contact Angle" to 3,"Body Length" to 1), 0.0205, 538.0, 212.0, 33.8, -54.6, -46.5),
            TaguchiRun(6, mapOf("Front OD" to 2,"Preload" to 3,"Contact Angle" to 1,"Body Length" to 2), 0.0198, 562.0, 205.0, 34.1, -55.0, -46.2),
            TaguchiRun(7, mapOf("Front OD" to 3,"Preload" to 1,"Contact Angle" to 3,"Body Length" to 2), 0.0162, 590.0, 181.0, 35.8, -55.4, -45.2),
            TaguchiRun(8, mapOf("Front OD" to 3,"Preload" to 2,"Contact Angle" to 1,"Body Length" to 3), 0.0155, 618.0, 174.0, 36.2, -55.8, -44.8),
            TaguchiRun(9, mapOf("Front OD" to 3,"Preload" to 3,"Contact Angle" to 2,"Body Length" to 1), 0.0148, 642.0, 168.0, 36.6, -56.1, -44.5),
        )
        val demo = TaguchiResult(
            arrayType = "L9", controlFactors = factors, experimentRuns = runs,
            responseTableDeflection = listOf(
                TaguchiResponseTableRow("Front OD",       30.5, 33.7, 36.2, 5.7, 1),
                TaguchiResponseTableRow("Preload",         33.2, 33.5, 33.9, 0.7, 4),
                TaguchiResponseTableRow("Contact Angle",   33.5, 33.4, 33.5, 0.1, 4),
                TaguchiResponseTableRow("Body Length",     33.5, 33.5, 33.4, 0.1, 4),
            ),
            responseTableFreq = listOf(
                TaguchiResponseTableRow("Front OD",       -53.0, -54.6, -55.8, 2.8, 1),
                TaguchiResponseTableRow("Preload",         -54.0, -54.5, -54.8, 0.8, 2),
                TaguchiResponseTableRow("Contact Angle",   -54.4, -54.4, -54.6, 0.2, 3),
                TaguchiResponseTableRow("Body Length",     -54.1, -54.3, -54.9, 0.8, 2),
            ),
            optimalFactorLevels = mapOf("Front OD" to 90.0, "Preload" to 450.0,
                "Contact Angle" to 25.0, "Body Length" to 150.0),
            predictedOptimumDeflectionMm = 0.0138,
            predictedOptimumFreqHz = 665.0
        )
        updateState { copy(taguchiResult = demo) }
        emitEffect(AnalyticsEffect.ShowSnackbar("Demo Taguchi L9 dataset loaded"))
    }
}
