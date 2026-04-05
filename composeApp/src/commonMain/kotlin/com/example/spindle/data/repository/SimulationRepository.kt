package com.example.spindle.data.repository

import com.example.spindle.data.network.SpindleApiClient
import com.example.spindle.data.network.dto.ResultResponseDto
import com.example.spindle.domain.models.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

// ── State emitted during a simulation run ────────────────────────
sealed interface SimulationState {
    data object Idle                                      : SimulationState
    data class Submitted(val resultId: String)            : SimulationState
    data class Running(val resultId: String, val progressPct: Int) : SimulationState
    data class Success(val result: SimulationResult)      : SimulationState
    data class Error(val message: String)                 : SimulationState
}

data class EngineHealth(
    val isOnline: Boolean,
    val isAnsysAvailable: Boolean,
    val isPyvistaAvailable: Boolean,
    val isMlModelLoaded: Boolean,
    val version: String
)

// ── Interface ─────────────────────────────────────────────────────
interface SimulationRepository {
    fun runSimulation(config: SpindleConfig): Flow<SimulationState>
    suspend fun getResultById(resultId: String): SimulationResult?
    fun streamOptimisation(config: SpindleConfig): Flow<OptimisationSnapshot>
    suspend fun checkEngineHealth(): EngineHealth
}

// ── Optimisation snapshot (from SSE) ─────────────────────────────
data class OptimisationSnapshot(
    val generation: Int,
    val bestFitnessScore: Double,
    val meanFitnessScore: Double,
    val bestDeflectionMm: Double,
    val bestFreqHz: Double,
    val bestWeightKg: Double,
    val paretoPoints: List<ParetoPoint>,
    val constraintViolations: Int
)

data class ParetoPoint(val deflection: Double, val weight: Double, val freq: Double)

// ── Implementation ────────────────────────────────────────────────
class SimulationRepositoryImpl(private val api: SpindleApiClient) : SimulationRepository {

    private val pollIntervalMs = 2_000L
    private val maxPollAttempts = 150

    override fun runSimulation(config: SpindleConfig): Flow<SimulationState> = flow {
        emit(SimulationState.Idle)
        val response = try {
            api.submitSimulation(config)
        } catch (e: Exception) {
            emit(SimulationState.Error("Engine unreachable: ${e.message}"))
            return@flow
        }
        val resultId = response.resultId
        emit(SimulationState.Submitted(resultId))

        var attempt = 0
        while (attempt < maxPollAttempts) {
            delay(pollIntervalMs)
            attempt++
            val progress = minOf(95, attempt * 100 / maxPollAttempts)
            emit(SimulationState.Running(resultId, progress))

            val dto = try { api.fetchResult(resultId) } catch (e: Exception) {
                emit(SimulationState.Error("Poll failed: ${e.message}")); return@flow
            }
            if (dto == null) continue

            when (dto.status) {
                "COMPLETED" -> { emit(SimulationState.Success(dto.toDomain())); return@flow }
                "FAILED"    -> { emit(SimulationState.Error(dto.errorMessage ?: "Failed")); return@flow }
            }
        }
        emit(SimulationState.Error("Timed out after ${maxPollAttempts * pollIntervalMs / 1000}s"))
    }

    override suspend fun getResultById(resultId: String): SimulationResult? {
        return try {
            val dto = api.fetchResult(resultId) ?: return null
            if (dto.status == "COMPLETED") dto.toDomain() else null
        } catch (_: Exception) { null }
    }

    override fun streamOptimisation(config: SpindleConfig): Flow<OptimisationSnapshot> =
        api.streamOptimisation(config.configId).map { ev ->
            OptimisationSnapshot(
                generation           = ev.generation,
                bestFitnessScore     = ev.bestFitness,
                meanFitnessScore     = ev.meanFitness,
                bestDeflectionMm     = ev.bestDeflectionMm,
                bestFreqHz           = ev.bestFreqHz,
                bestWeightKg         = ev.bestWeightKg,
                paretoPoints         = ev.paretoPoints.map {
                    ParetoPoint(
                        deflection = it["deflection"] ?: 0.0,
                        weight     = it["weight_kg"]  ?: 0.0,
                        freq       = it["freq_hz"]    ?: 0.0
                    )
                },
                constraintViolations = ev.constraintViolations
            )
        }

    override suspend fun checkEngineHealth(): EngineHealth {
        val dto = api.checkHealth()
        return EngineHealth(
            isOnline              = dto.status != "unreachable",
            isAnsysAvailable      = dto.ansysAvailable,
            isPyvistaAvailable    = dto.pyvistaAvailable,
            isMlModelLoaded       = dto.mlModelLoaded,
            version               = dto.version
        )
    }

    private fun ResultResponseDto.toDomain() = SimulationResult(
        resultId      = resultId,
        configId      = configId,
        status        = ResultStatus.COMPLETED,
        mode          = try { SimulationMode.valueOf(runType) } catch (_: Exception) { SimulationMode.FULL_ANALYSIS },
        timestampMs   = timestampMs,
        static        = static,
        modal         = modal,
        monteCarlo    = monteCarlo?.let {
            MonteCarloResultV2(
                nIterations                 = it.iterations,
                probabilityOfFailurePct     = it.probabilityOfFailurePct,
                reliabilityPct              = it.reliabilityPct,
                deflectionMeanMm            = it.deflectionMeanMm,
                deflectionStdMm             = it.deflectionStdMm,
                deflectionP5Mm              = it.deflectionP5Mm,
                deflectionP95Mm             = it.deflectionP95Mm,
                stressMeanMpa               = it.stressMeanMpa,
                stressStdMpa                = it.stressStdMpa,
                stressP95Mpa                = it.stressP95Mpa,
                freqMeanHz                  = it.freqMeanHz,
                freqStdHz                   = it.freqStdHz,
                freqP5Hz                    = it.freqP5Hz,
                bearingLives                = it.bearingLives,
                sensitivity                 = it.sensitivity,
                runoutEffectMeanN           = it.runoutEffectMeanN,
                eccentricityEffectMeanN     = it.eccentricityEffectMeanN,
                misalignmentMomentMeanNmm   = it.misalignmentMomentMeanNmm
            )
        },
        taguchi       = taguchi,
        errorMessage  = errorMessage
    )
}
