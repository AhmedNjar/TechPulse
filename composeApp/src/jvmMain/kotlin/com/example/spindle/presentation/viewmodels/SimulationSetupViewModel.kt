package com.example.spindle.presentation.viewmodels

import com.example.spindle.domain.models.*
import kotlinx.coroutines.launch

// ── State ─────────────────────────────────────────────────────────

data class SimulationSetupState(
    val projectName: String              = "New CNC Lathe Spindle",
    val geometry: ShaftGeometry          = ShaftGeometry.defaultCncLathe(),
    val bearingSystem: BearingSystem     = BearingSystem.defaultCncLathe(),
    val operatingSpeedRpm: Double        = 12_000.0,
    val radialLoadN: Double              = 1_500.0,
    val axialLoadN: Double               = 500.0,
    val operatingTempC: Double           = 25.0,
    val selectedMaterial: MaterialSpec   = MaterialSpec.STEEL_42CrMo4,
    val stochastic: StochasticVariables  = StochasticVariables(),
    val simulationMode: SimulationMode   = SimulationMode.FULL_ANALYSIS,
    val nModalModes: Int                 = 6,
    val enableFEA: Boolean               = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSubmitting: Boolean            = false
) : MviState

// ── Intents / Effects ─────────────────────────────────────────────

sealed interface SetupIntent : MviIntent {
    data class UpdateProjectName(val v: String)              : SetupIntent
    data class UpdateGeometry(val v: ShaftGeometry)          : SetupIntent
    data class UpdateBearingSystem(val v: BearingSystem)     : SetupIntent
    data class UpdateRpm(val v: Float)                       : SetupIntent
    data class UpdateRadialLoad(val v: Float)                : SetupIntent
    data class UpdateAxialLoad(val v: Float)                 : SetupIntent
    data class UpdateTempC(val v: Float)                     : SetupIntent
    data class UpdateMaterial(val v: MaterialSpec)           : SetupIntent
    data class UpdateStochastic(val v: StochasticVariables)  : SetupIntent
    data class UpdateSimMode(val v: SimulationMode)          : SetupIntent
    data class UpdateNModalModes(val v: Int)                 : SetupIntent
    data class UpdateEnableFEA(val v: Boolean)               : SetupIntent
    data object Submit                                        : SetupIntent
    data object ResetForm                                     : SetupIntent
}

sealed interface SetupEffect : MviEffect {
    data class ConfigReady(val config: SpindleConfig)      : SetupEffect
    data class ValidationFailed(val summary: String)       : SetupEffect
    data object FormReset                                   : SetupEffect
}

// ── ViewModel ─────────────────────────────────────────────────────

class SimulationSetupViewModel :
    BaseViewModel<SimulationSetupState, SetupIntent, SetupEffect>(SimulationSetupState()) {

    override fun processIntent(intent: SetupIntent) = when (intent) {
        is SetupIntent.UpdateProjectName  -> updateState { copy(projectName = intent.v) }
        is SetupIntent.UpdateGeometry     -> updateState { copy(geometry = intent.v) }
        is SetupIntent.UpdateBearingSystem-> updateState { copy(bearingSystem = intent.v) }
        is SetupIntent.UpdateRpm          -> updateState { copy(operatingSpeedRpm = intent.v.toDouble()) }
        is SetupIntent.UpdateRadialLoad   -> updateState { copy(radialLoadN = intent.v.toDouble()) }
        is SetupIntent.UpdateAxialLoad    -> updateState { copy(axialLoadN = intent.v.toDouble()) }
        is SetupIntent.UpdateTempC        -> updateState { copy(operatingTempC = intent.v.toDouble()) }
        is SetupIntent.UpdateMaterial     -> updateState { copy(selectedMaterial = intent.v) }
        is SetupIntent.UpdateStochastic   -> updateState { copy(stochastic = intent.v) }
        is SetupIntent.UpdateSimMode      -> updateState { copy(simulationMode = intent.v) }
        is SetupIntent.UpdateNModalModes  -> updateState { copy(nModalModes = intent.v) }
        is SetupIntent.UpdateEnableFEA    -> updateState { copy(enableFEA = intent.v) }
        is SetupIntent.Submit             -> submit()
        is SetupIntent.ResetForm          -> { updateState { SimulationSetupState() }; emitEffect(SetupEffect.FormReset) }
    }

    private fun submit() {
        if (currentState.isSubmitting) return
        updateState { copy(isSubmitting = true, fieldErrors = emptyMap()) }
        scope.launch {
            val errors = validate(currentState)
            if (errors.isNotEmpty()) {
                updateState { copy(isSubmitting = false, fieldErrors = errors) }
                emitEffect(SetupEffect.ValidationFailed("${errors.size} error(s): ${errors.values.first()}"))
                return@launch
            }
            val config = SpindleConfig(
                configId           = java.util.UUID.randomUUID().toString(),
                projectName        = currentState.projectName.trim(),
                geometry           = currentState.geometry,
                bearingSystem      = currentState.bearingSystem,
                operatingSpeedRpm  = currentState.operatingSpeedRpm,
                axialLoadN         = currentState.axialLoadN,
                radialLoadN        = currentState.radialLoadN,
                operatingTempC     = currentState.operatingTempC,
                material           = currentState.selectedMaterial,
                stochastic         = currentState.stochastic,
                simulationMode     = currentState.simulationMode,
                nModalModes        = currentState.nModalModes,
                enableFEA          = currentState.enableFEA
            )
            updateState { copy(isSubmitting = false) }
            emitEffect(SetupEffect.ConfigReady(config))
        }
    }

    private fun validate(s: SimulationSetupState): Map<String, String> = buildMap {
        if (s.projectName.isBlank()) put("projectName", "Project name required")
        if (s.geometry.segments.isEmpty()) put("geometry", "At least one segment required")
        s.geometry.segments.forEach { seg ->
            if (seg.innerDiameterMm >= seg.outerDiameterMm)
                put("seg_${seg.segmentId}", "Segment ${seg.segmentId + 1}: ID must be < OD")
        }
        s.bearingSystem.bearings.forEach { b ->
            if (s.operatingSpeedRpm > b.limitingSpeedRpm)
                put("brg_${b.bearingId}", "${b.designation}: RPM exceeds limit ${b.limitingSpeedRpm}")
            if (b.zPositionMm > s.geometry.totalLengthMm)
                put("brg_z_${b.bearingId}", "${b.designation}: Z exceeds shaft length")
        }
    }
}
