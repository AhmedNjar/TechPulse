package com.example.spindle.domain.usecases

import com.example.spindle.data.repository.OptimisationSnapshot
import com.example.spindle.data.repository.SimulationRepository
import com.example.spindle.data.repository.SimulationState
import com.example.spindle.domain.models.SimulationMode
import com.example.spindle.domain.models.SpindleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach

class RunSimulationUseCase(private val repository: SimulationRepository) {
    operator fun invoke(config: SpindleConfig): Flow<SimulationState> {
        val err = validate(config)
        if (err != null) return flowOf(SimulationState.Error("Validation: $err"))
        return repository.runSimulation(config)
    }

    private fun validate(c: SpindleConfig): String? {
        if (c.projectName.isBlank()) return "Project name is required"
        if (c.geometry.segments.isEmpty()) return "At least one shaft segment required"
        if (c.operatingSpeedRpm <= 0) return "Speed must be > 0"
        c.geometry.segments.forEach { seg ->
            if (seg.innerDiameterMm >= seg.outerDiameterMm)
                return "Segment ${seg.segmentId + 1}: ID must be < OD"
        }
        c.bearingSystem.bearings.forEach { b ->
            if (c.operatingSpeedRpm > b.limitingSpeedRpm)
                return "Bearing ${b.designation}: RPM exceeds limit ${b.limitingSpeedRpm}"
        }
        return null
    }
}

class StartOptimisationUseCase(private val repository: SimulationRepository) {
    operator fun invoke(config: SpindleConfig): Flow<OptimisationSnapshot> =
        repository.streamOptimisation(config.copy(simulationMode = SimulationMode.OPTIMIZATION))
            .filter { it.bestFitnessScore.isFinite() }
}
