package com.example.spindle.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class ResultStatus { PENDING, RUNNING, COMPLETED, FAILED }

@Serializable
data class StaticFEAResult(
    val solverVersion: String,
    val elementType: String              = "BEAM188",
    val nElements: Int,
    val nNodes: Int,
    val maxDeflectionMm: Double,
    val maxDeflectionLocationMm: Double,
    val toolTipDeflectionMm: Double,
    val maxBendingStressMpa: Double,
    val maxBendingStressLocationMm: Double,
    val maxVonMisesMpa: Double,
    val safetyFactorYield: Double,
    val safetyFactorFatigue: Double,
    val renderImageB64: String?          = null
)

@Serializable
data class ModeShape(
    val modeNumber: Int,
    val naturalFrequencyHz: Double,
    val dampingRatio: Double             = 0.0,
    val modeType: String,
    val maxDisplacementMm: Double,
    val criticalSpeedRpm: Double,
    val safetyMarginPct: Double
)

@Serializable
data class ModalResult(
    val solver: String                   = "ANSYS Block Lanczos",
    val nModesExtracted: Int,
    val modeShapes: List<ModeShape>,
    val spindleFrequencyHz: Double,
    val firstCriticalSpeedRpm: Double,
    val safetyFactorToFirstCritical: Double,
    val campbellDiagramData: List<Map<String, Double>> = emptyList()
)

@Serializable
data class ISO281BearingLife(
    val bearingId: Int,
    val designation: String,
    val dynamicLoadRatingKn: Double,
    val equivalentDynamicLoadMeanN: Double,
    val equivalentDynamicLoadStdN: Double,
    val l10LifeMeanHours: Double,
    val l10LifeStdHours: Double,
    val l10LifeP5Hours: Double,
    val l10LifeP95Hours: Double,
    val failureProbabilityPct: Double
)

@Serializable
data class MonteCarloResultV2(
    val nIterations: Int,
    val probabilityOfFailurePct: Double,
    val reliabilityPct: Double,
    val deflectionMeanMm: Double,
    val deflectionStdMm: Double,
    val deflectionP5Mm: Double,
    val deflectionP95Mm: Double,
    val stressMeanMpa: Double,
    val stressStdMpa: Double,
    val stressP95Mpa: Double,
    val freqMeanHz: Double,
    val freqStdHz: Double,
    val freqP5Hz: Double,
    val bearingLives: List<ISO281BearingLife>,
    val sensitivity: Map<String, Double>,
    val runoutEffectMeanN: Double,
    val eccentricityEffectMeanN: Double,
    val misalignmentMomentMeanNmm: Double
)

@Serializable
data class TaguchiControlFactor(
    val name: String, val unit: String,
    val level1: Double, val level2: Double, val level3: Double
)

@Serializable
data class TaguchiRun(
    val runNumber: Int,
    val factorLevels: Map<String, Int>,
    val responseDeflectionMm: Double,
    val responseFreqHz: Double,
    val responseStressMpa: Double,
    val snRatioDeflection: Double,
    val snRatioFreq: Double,
    val snRatioStress: Double
)

@Serializable
data class TaguchiResponseTableRow(
    val factorName: String,
    val level1Effect: Double,
    val level2Effect: Double,
    val level3Effect: Double,
    val delta: Double,
    val rank: Int
)

@Serializable
data class TaguchiResult(
    val arrayType: String                     = "L9",
    val controlFactors: List<TaguchiControlFactor>,
    val experimentRuns: List<TaguchiRun>,
    val responseTableDeflection: List<TaguchiResponseTableRow>,
    val responseTableFreq: List<TaguchiResponseTableRow>,
    val optimalFactorLevels: Map<String, Double>,
    val predictedOptimumDeflectionMm: Double,
    val predictedOptimumFreqHz: Double,
    val confidenceIntervalPct: Double         = 95.0
)

@Serializable
data class SimulationResult(
    val resultId: String,
    val configId: String,
    val status: ResultStatus               = ResultStatus.PENDING,
    val mode: SimulationMode,
    val timestampMs: Long,
    val durationSec: Double                = 0.0,
    val static: StaticFEAResult?           = null,
    val modal: ModalResult?                = null,
    val monteCarlo: MonteCarloResultV2?    = null,
    val taguchi: TaguchiResult?            = null,
    val warnings: List<String>             = emptyList(),
    val errorMessage: String?              = null
)
