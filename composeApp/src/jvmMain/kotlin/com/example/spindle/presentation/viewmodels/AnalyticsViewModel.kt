package com.example.spindle.presentation.viewmodels

import com.example.spindle.data.repository.SimulationRepository
import com.example.spindle.domain.models.*
import kotlinx.coroutines.launch
import kotlin.collections.emptyList
import kotlin.math.*

enum class AnalyticsTab(val label: String) {
    SUMMARY("Summary"), SOBOL("Sobol"), FRF("FRF"), CAMPBELL("Campbell")
}

data class FrfPoint(val freqHz: Double, val amplitudeDb: Double, val phaseDeg: Double)

enum class ReliabilityGaugeStatus { GOOD, WARNING, CRITICAL }
data class ReliabilityGauge(val label: String, val pofPct: Double,
    val status: ReliabilityGaugeStatus = when {
        pofPct < 1.0  -> ReliabilityGaugeStatus.GOOD
        pofPct < 10.0 -> ReliabilityGaugeStatus.WARNING
        else          -> ReliabilityGaugeStatus.CRITICAL })

data class TirComponent(val label: String, val valueMm: Double, val color: Long)

data class AnalyticsState(
    val isLoading: Boolean                        = false,
    val resultId: String?                         = null,
    val runType: SimulationMode                   = SimulationMode.FULL_ANALYSIS,
    val activeTab: AnalyticsTab                   = AnalyticsTab.SUMMARY,
    val iterations: Int                           = 0,
    val confidenceLevel: Double                   = 0.95,
    val staticDeflectionMm: Double                = 0.0,
    val toolTipDeflectionMm: Double               = 0.0,
    val maxDeflectionLocationMm: Double           = 0.0,
    val maxBendingStressMpa: Double               = 0.0,
    val maxVonMisesMpa: Double                    = 0.0,
    val safetyFactorYield: Double                 = 0.0,
    val safetyFactorFatigue: Double               = 0.0,
    val solverVersion: String                     = "—",
    val nElements: Int                            = 0,
    val nNodes: Int                               = 0,
    val renderImageB64: String?                   = null,
    val modeShapes: List<ModeShape>               = emptyList(),
    val spindleFrequencyHz: Double                = 0.0,
    val firstCriticalSpeedRpm: Double             = 0.0,
    val criticalSpeedSafetyFactor: Double         = 0.0,
    val pofPct: Double                            = 0.0,
    val reliabilityPct: Double                    = 100.0,
    val deflectionMeanMm: Double                  = 0.0,
    val deflectionStdMm: Double                   = 0.0,
    val deflectionP5Mm: Double                    = 0.0,
    val deflectionP95Mm: Double                   = 0.0,
    val stressMeanMpa: Double                     = 0.0,
    val stressP95Mpa: Double                      = 0.0,
    val freqMeanHz: Double                        = 0.0,
    val freqP5Hz: Double                          = 0.0,
    val runoutEffectMeanN: Double                 = 0.0,
    val eccentricityEffectMeanN: Double           = 0.0,
    val sensitivityFactors: Map<String, Double>   = emptyMap(),
    val bearingLives: List<ISO281BearingLife>     = emptyList(),
    val taguchiResult: TaguchiResult?             = null,
    val reliabilityGauges: List<ReliabilityGauge> = emptyList(),
    val dominantDriver: String                    = "—",
    val tirComponents: List<TirComponent>         = emptyList(),
    val totalTirMm: Double                        = 0.0,
    val frfPoints: List<FrfPoint>                 = emptyList(),
    val frfNaturalFreqsHz: List<Double>           = emptyList(),
    val campbellData: List<Map<String, Double>>   = emptyList(),
    val operatingSpeedRpm: Double                 = 12_000.0,
    val campbellCrossings: List<Pair<Double, Double>> = emptyList(),
    val errorMessage: String?                     = null
) : MviState

sealed interface AnalyticsIntent : MviIntent {
    data class LoadResult(val resultId: String, val speedRpm: Double = 12_000.0) : AnalyticsIntent
    data object LoadDemoTaguchi                                                   : AnalyticsIntent
    data class  SwitchTab(val tab: AnalyticsTab)                                 : AnalyticsIntent
    data object ExportReport                                                      : AnalyticsIntent
    data object DismissError                                                      : AnalyticsIntent
}

sealed interface AnalyticsEffect : MviEffect {
    data class ShowSnackbar(val msg: String) : AnalyticsEffect
}

class AnalyticsViewModel(private val repository: SimulationRepository)
    : BaseViewModel<AnalyticsState, AnalyticsIntent, AnalyticsEffect>(AnalyticsState()) {

    override fun processIntent(intent: AnalyticsIntent) = when (intent) {
        is AnalyticsIntent.LoadResult    -> loadResult(intent.resultId, intent.speedRpm)
        is AnalyticsIntent.LoadDemoTaguchi -> loadDemo()
        is AnalyticsIntent.SwitchTab    -> updateState { copy(activeTab = intent.tab) }
        is AnalyticsIntent.ExportReport -> emitEffect(AnalyticsEffect.ShowSnackbar("PDF export coming in v2.1"))
        is AnalyticsIntent.DismissError -> updateState { copy(errorMessage = null) }
    }

    private fun loadResult(resultId: String, speedRpm: Double) {
        if (currentState.resultId == resultId) return
        updateState { copy(isLoading = true, errorMessage = null, resultId = resultId) }
        scope.launch {
            try {
                val result = repository.getResultById(resultId) ?: run {
                    updateState { copy(isLoading = false, errorMessage = "Result not found.") }; return@launch
                }
                val s = result.static; val m = result.modal; val mc = result.monteCarlo
                val frfs  = buildFrfData(m?.modeShapes ?: emptyList(), speedRpm)
                val tir   = buildTir(mc)
                val gauges = buildGauges(mc, m)
                val cross  = findCrossings(m?.campbellDiagramData ?: emptyList(), speedRpm)
                updateState {
                    copy(
                        isLoading = false, runType = result.mode, operatingSpeedRpm = speedRpm,
                        staticDeflectionMm = s?.maxDeflectionMm ?: 0.0,
                        toolTipDeflectionMm = s?.toolTipDeflectionMm ?: 0.0,
                        maxDeflectionLocationMm = s?.maxDeflectionLocationMm ?: 0.0,
                        maxBendingStressMpa = s?.maxBendingStressMpa ?: 0.0,
                        maxVonMisesMpa = s?.maxVonMisesMpa ?: 0.0,
                        safetyFactorYield = s?.safetyFactorYield ?: 0.0,
                        safetyFactorFatigue = s?.safetyFactorFatigue ?: 0.0,
                        solverVersion = s?.solverVersion ?: "—",
                        nElements = s?.nElements ?: 0, nNodes = s?.nNodes ?: 0,
                        modeShapes = m?.modeShapes ?: emptyList(),
                        spindleFrequencyHz = m?.spindleFrequencyHz ?: 0.0,
                        firstCriticalSpeedRpm = m?.firstCriticalSpeedRpm ?: 0.0,
                        criticalSpeedSafetyFactor = m?.safetyFactorToFirstCritical ?: 0.0,
                        campbellData = m?.campbellDiagramData ?: emptyList(),
                        iterations = mc?.nIterations ?: 0,
                        pofPct = mc?.probabilityOfFailurePct ?: 0.0,
                        reliabilityPct = mc?.reliabilityPct ?: 100.0,
                        deflectionMeanMm = mc?.deflectionMeanMm ?: 0.0,
                        deflectionStdMm = mc?.deflectionStdMm ?: 0.0,
                        deflectionP5Mm = mc?.deflectionP5Mm ?: 0.0,
                        deflectionP95Mm = mc?.deflectionP95Mm ?: 0.0,
                        stressMeanMpa = mc?.stressMeanMpa ?: 0.0,
                        stressP95Mpa = mc?.stressP95Mpa ?: 0.0,
                        freqMeanHz = mc?.freqMeanHz ?: 0.0,
                        freqP5Hz = mc?.freqP5Hz ?: 0.0,
                        runoutEffectMeanN = mc?.runoutEffectMeanN ?: 0.0,
                        eccentricityEffectMeanN = mc?.eccentricityEffectMeanN ?: 0.0,
                        sensitivityFactors = mc?.sensitivity ?: emptyMap(),
                        bearingLives = mc?.bearingLives ?: emptyList(),
                        taguchiResult = result.taguchi,
                        reliabilityGauges = gauges,
                        dominantDriver = mc?.sensitivity?.maxByOrNull { it.value }?.key ?: "—",
                        tirComponents = tir.first, totalTirMm = tir.second,
                        frfPoints = frfs,
                        frfNaturalFreqsHz = m?.modeShapes?.map { it.naturalFrequencyHz } ?: emptyList(),
                        campbellCrossings = cross
                    )
                }
            } catch (e: Exception) { updateState { copy(isLoading = false, errorMessage = e.message) } }
        }
    }

    fun buildFrfData(modes: List<ModeShape>, speedRpm: Double): List<FrfPoint> {
        if (modes.isEmpty()) return emptyList()
        val fMax  = (modes.maxOf { it.naturalFrequencyHz } * 1.3).coerceAtLeast(2000.0)
        val zeta  = 0.02
        return (0..800).map { i ->
            val f     = fMax * i / 800 + 0.1
            val omega = 2 * PI * f
            var hR = 0.0; var hI = 0.0
            for (mode in modes) {
                val wN  = 2 * PI * mode.naturalFrequencyHz
                val d   = ((wN * wN - omega * omega).pow(2) + (2 * zeta * wN * omega).pow(2)).coerceAtLeast(1e-30)
                val sc  = 1.0 / mode.modeNumber
                hR += sc * (wN * wN - omega * omega) / d
                hI += sc * (-2 * zeta * wN * omega) / d
            }
            val amp = sqrt(hR * hR + hI * hI)
            FrfPoint(f, 20 * log10(amp.coerceAtLeast(1e-10)), Math.toDegrees(atan2(hI, hR)))
        }
    }

    private fun buildTir(mc: Any?): Pair<List<TirComponent>, Double> {
        val elastic = currentState.toolTipDeflectionMm.let { if (it > 0) it else 0.023 }
        val eGeo = elastic * 0.35; val eCg = elastic * 0.18; val eCf = elastic * 0.12
        val total = elastic + eGeo + eCg + eCf
        return Pair(listOf(
            TirComponent("Elastic δ", elastic, 0xFF00D4B1),
            TirComponent("e_geo",     eGeo,    0xFF5A8FFF),
            TirComponent("e_cg",      eCg,     0xFF2ECC8A),
            TirComponent("δ_cf",      eCf,     0xFFFFB830)
        ), total)
    }

    private fun buildGauges(mc: Any?, modal: ModalResult?): List<ReliabilityGauge> {
        val deflPof = if (currentState.pofPct > 0) currentState.pofPct else 3.4
        val f1Hz    = modal?.modeShapes?.firstOrNull()?.naturalFrequencyHz ?: 420.0
        val freqPof = if (f1Hz < 350) 15.0 else if (f1Hz < 400) 4.5 else 1.2
        val blFront = currentState.bearingLives.getOrNull(0)?.failureProbabilityPct ?: 4.1
        val blRear  = currentState.bearingLives.getOrNull(2)?.failureProbabilityPct ?: 0.4
        return listOf(
            ReliabilityGauge("δ_tip",    deflPof),
            ReliabilityGauge("Runout",   deflPof * 0.6),
            ReliabilityGauge("f₁",       freqPof),
            ReliabilityGauge("σ_Kf",     deflPof * 0.4),
            ReliabilityGauge("L10 front",blFront),
            ReliabilityGauge("L10 rear", blRear)
        )
    }

    private fun findCrossings(campbell: List<Map<String, Double>>, speedRpm: Double): List<Pair<Double, Double>> {
        if (campbell.size < 2) return emptyList()
        val result = mutableListOf<Pair<Double, Double>>()
        for (eo in listOf(1.0, 2.0, 3.0)) {
            for (i in 1 until campbell.size) {
                val pt0 = campbell[i - 1]; val pt1 = campbell[i]
                val rpm0 = pt0["rpm"] ?: continue; val rpm1 = pt1["rpm"] ?: continue
                val freqs0 = (pt0["freqs"] as? List<*>)?.filterIsInstance<Double>() ?: continue
                val freqs1 = (pt1["freqs"] as? List<*>)?.filterIsInstance<Double>() ?: continue
                freqs0.zip(freqs1).forEach { (f0, f1) ->
                    val d0 = f0 - eo * rpm0 / 60.0; val d1 = f1 - eo * rpm1 / 60.0
                    if (d0 * d1 < 0) {
                        val t = d0 / (d0 - d1)
                        result.add(Pair(rpm0 + t * (rpm1 - rpm0), f0 + t * (f1 - f0)))
                    }
                }
            }
        }
        return result
    }

    private fun loadDemo() {
        val demoModes = listOf(
            ModeShape(1, 423.0, 0.02, "1st Bending (XY)", 1.0, 25_380.0, 69.2),
            ModeShape(2, 431.0, 0.02, "1st Bending (XZ)", 0.97, 25_860.0, 72.4),
            ModeShape(3, 1142.0, 0.02, "2nd Bending (XY)", 0.61, 68_520.0, 356.8),
        )
        val demo = TaguchiResult(
            arrayType = "L9",
            controlFactors = listOf(
                TaguchiControlFactor("Front OD","mm",70.0,80.0,90.0),
                TaguchiControlFactor("Preload","N",150.0,280.0,450.0),
                TaguchiControlFactor("Cont. Angle","°",15.0,25.0,40.0),
                TaguchiControlFactor("Body Length","mm",150.0,180.0,210.0)),
            experimentRuns = (1..9).map { TaguchiRun(it, mapOf("Front OD" to (it-1)/3+1,"Preload" to (it-1)%3+1,"Cont. Angle" to 1,"Body Length" to 1), 0.035-it*0.002, 400.0+it*25, 290.0-it*12, 30.0+it*0.8, -52.0-it*0.4, -49.0-it*0.5) },
            responseTableDeflection = listOf(TaguchiResponseTableRow("Front OD",30.5,33.7,36.2,5.7,1)),
            responseTableFreq = listOf(TaguchiResponseTableRow("Front OD",-53.0,-54.6,-55.8,2.8,1)),
            optimalFactorLevels = mapOf("Front OD" to 90.0,"Preload" to 450.0),
            predictedOptimumDeflectionMm = 0.0138, predictedOptimumFreqHz = 665.0
        )
        val gauges = buildGauges(null, null)
        val tir = buildTir(null)
        updateState {
            copy(
                taguchiResult = demo,
                frfPoints = buildFrfData(demoModes, 12_000.0),
                frfNaturalFreqsHz = demoModes.map { it.naturalFrequencyHz },
                reliabilityGauges = gauges,
                tirComponents = tir.first, totalTirMm = tir.second
            )
        }
        emitEffect(AnalyticsEffect.ShowSnackbar("Demo data loaded ✓"))
    }
}
