package com.example.spindle.data.network.dto

import com.example.spindle.domain.models.*
import kotlinx.serialization.Serializable

// POST /api/simulate
@Serializable
data class SimulateRequestDto(
    val configId: String,
    val projectName: String,
    val mode: String,
    val geometry: ShaftGeometry,
    val bearingSystem: BearingSystem,
    val operatingSpeedRpm: Double,
    val axialLoadN: Double,
    val radialLoadN: Double,
    val operatingTempC: Double,
    val material: String,
    val stochastic: StochasticVariables,
    val enableFEA: Boolean,
    val nModalModes: Int
)

@Serializable
data class SimulateResponseDto(
    val resultId: String,
    val status: String,
    val message: String              = "",
    val estimatedDurationSec: Int    = 0
)

// Nested MC DTO
@Serializable
data class MonteCarloDto(
    val iterations: Int,
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

// GET /api/results/{id}
@Serializable
data class ResultResponseDto(
    val resultId: String,
    val configId: String               = "",
    val runType: String                = "FULL_ANALYSIS",
    val status: String,
    val timestampMs: Long              = 0,
    val static: StaticFEAResult?       = null,
    val modal: ModalResult?            = null,
    val monteCarlo: MonteCarloDto?     = null,
    val taguchi: TaguchiResult?        = null,
    val errorMessage: String?          = null
)

// GET /api/optimisation/stream (SSE)
@Serializable
data class OptimisationEventDto(
    val eventType: String,
    val generation: Int,
    val bestFitness: Double,
    val meanFitness: Double,
    val bestDeflectionMm: Double,
    val bestFreqHz: Double,
    val bestWeightKg: Double,
    val paretoPoints: List<Map<String, Double>>,
    val constraintViolations: Int      = 0
)

// GET /api/health
@Serializable
data class HealthDto(
    val status: String,
    val ansysAvailable: Boolean,
    val pyvistaAvailable: Boolean,
    val mlModelLoaded: Boolean,
    val version: String
)
