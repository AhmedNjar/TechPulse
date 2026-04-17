package com.example.spindle.presentation.viewmodels

import com.example.spindle.domain.models.*
import kotlinx.coroutines.launch

enum class SolverEngine(val label: String, val apiKey: String) {
    INTERNAL("Internal (SpindleFEA)", "internal"),
    ANSYS   ("Ansys",                 "ansys"),
    CALCULIX("CalculiX (Experimental)","calculix")
}
enum class SolverStatus { UNKNOWN, READY, MISSING }
enum class BearingTypeExtended(val label: String, val supportsPreloadGrade: Boolean) {
    CRB ("CRB — Cylindrical Roller", false),
    ACBB("ACBB — Angular Contact",   true)
}
enum class PreloadGrade(val label: String, val typicalForceN: Double) {
    GA("GA — Light",    80.0), GB("GB — Medium", 200.0),
    GC("GC — Heavy",  350.0), GD("GD — X-Heavy",550.0)
}

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
    val solverEngine: SolverEngine       = SolverEngine.INTERNAL,
    val solverExecPath: String           = "",
    val solverStatus: SolverStatus       = SolverStatus.UNKNOWN,
    val bearingTypeExtended: List<BearingTypeExtended> = List(3) { BearingTypeExtended.ACBB },
    val bearingPreloadGrades: List<PreloadGrade>       = List(3) { PreloadGrade.GB },
    val filletRadiiMm: List<Double>      = List(4) { 2.0 },
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSubmitting: Boolean            = false
) : MviState

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
    data class UpdateSolverEngine(val v: SolverEngine)       : SetupIntent
    data class UpdateSolverExecPath(val v: String)           : SetupIntent
    data object CheckSolverStatus                             : SetupIntent
    data class UpdateBearingTypeExtended(val idx: Int, val t: BearingTypeExtended) : SetupIntent
    data class UpdatePreloadGrade(val idx: Int, val g: PreloadGrade)               : SetupIntent
    data class UpdateFilletRadius(val segIdx: Int, val r: Double)                  : SetupIntent
    data object Submit                                        : SetupIntent
    data object ResetForm                                     : SetupIntent
}

sealed interface SetupEffect : MviEffect {
    data class ConfigReady(val config: SpindleConfig)   : SetupEffect
    data class ValidationFailed(val summary: String)    : SetupEffect
    data object FormReset                                : SetupEffect
}

class SimulationSetupViewModel :
    BaseViewModel<SimulationSetupState, SetupIntent, SetupEffect>(SimulationSetupState()) {

    override fun processIntent(intent: SetupIntent) = when (intent) {
        is SetupIntent.UpdateProjectName    -> updateState { copy(projectName = intent.v) }
        is SetupIntent.UpdateGeometry       -> updateState { copy(geometry = intent.v) }
        is SetupIntent.UpdateBearingSystem  -> updateState { copy(bearingSystem = intent.v) }
        is SetupIntent.UpdateRpm            -> updateState { copy(operatingSpeedRpm = intent.v.toDouble()) }
        is SetupIntent.UpdateRadialLoad     -> updateState { copy(radialLoadN = intent.v.toDouble()) }
        is SetupIntent.UpdateAxialLoad      -> updateState { copy(axialLoadN = intent.v.toDouble()) }
        is SetupIntent.UpdateTempC          -> updateState { copy(operatingTempC = intent.v.toDouble()) }
        is SetupIntent.UpdateMaterial       -> updateState { copy(selectedMaterial = intent.v) }
        is SetupIntent.UpdateStochastic     -> updateState { copy(stochastic = intent.v) }
        is SetupIntent.UpdateSimMode        -> updateState { copy(simulationMode = intent.v) }
        is SetupIntent.UpdateNModalModes    -> updateState { copy(nModalModes = intent.v) }
        is SetupIntent.UpdateEnableFEA      -> updateState { copy(enableFEA = intent.v) }
        is SetupIntent.UpdateSolverEngine   -> onSolverChanged(intent.v)
        is SetupIntent.UpdateSolverExecPath -> updateState { copy(solverExecPath = intent.v, solverStatus = SolverStatus.UNKNOWN) }
        is SetupIntent.CheckSolverStatus    -> checkSolverStatus()
        is SetupIntent.UpdateBearingTypeExtended -> {
            val list = currentState.bearingTypeExtended.toMutableList()
            if (intent.idx < list.size) list[intent.idx] = intent.t
            updateState { copy(bearingTypeExtended = list) }
        }
        is SetupIntent.UpdatePreloadGrade -> {
            val list = currentState.bearingPreloadGrades.toMutableList()
            if (intent.idx < list.size) list[intent.idx] = intent.g
            updateState { copy(bearingPreloadGrades = list) }
        }
        is SetupIntent.UpdateFilletRadius -> {
            val list = currentState.filletRadiiMm.toMutableList()
            while (list.size <= intent.segIdx) list.add(2.0)
            list[intent.segIdx] = intent.r.coerceAtLeast(0.0)
            updateState { copy(filletRadiiMm = list) }
        }
        is SetupIntent.Submit    -> submit()
        is SetupIntent.ResetForm -> { updateState { SimulationSetupState() }; emitEffect(SetupEffect.FormReset) }
    }

    private fun onSolverChanged(engine: SolverEngine) {
        val defaultPath = when (engine) {
            SolverEngine.ANSYS    -> if (System.getProperty("os.name").contains("Windows", true))
                "C:\\Program Files\\ANSYS Inc\\v242\\ANSYS\\bin\\winx64\\ANSYS242.exe"
                else "/usr/ansys_inc/v242/ansys/bin/ansys242"
            SolverEngine.CALCULIX -> "/usr/bin/ccx"
            SolverEngine.INTERNAL -> ""
        }
        updateState {
            copy(solverEngine = engine,
                solverExecPath = if (solverExecPath.isEmpty()) defaultPath else solverExecPath,
                solverStatus = if (engine == SolverEngine.INTERNAL) SolverStatus.READY else SolverStatus.UNKNOWN)
        }
        if (engine != SolverEngine.INTERNAL) checkSolverStatus()
    }

    private fun checkSolverStatus() {
        val path = currentState.solverExecPath
        if (path.isEmpty()) { updateState { copy(solverStatus = SolverStatus.MISSING) }; return }
        scope.launch {
            val exists = try { java.io.File(path).exists() } catch (_: Exception) { false }
            updateState { copy(solverStatus = if (exists) SolverStatus.READY else SolverStatus.MISSING) }
        }
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
                configId = java.util.UUID.randomUUID().toString(),
                projectName = currentState.projectName.trim(),
                geometry = currentState.geometry,
                bearingSystem = currentState.bearingSystem,
                operatingSpeedRpm = currentState.operatingSpeedRpm,
                axialLoadN = currentState.axialLoadN,
                radialLoadN = currentState.radialLoadN,
                operatingTempC = currentState.operatingTempC,
                material = currentState.selectedMaterial,
                stochastic = currentState.stochastic,
                simulationMode = currentState.simulationMode,
                nModalModes = currentState.nModalModes,
                enableFEA = currentState.enableFEA
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
                put("seg_${seg.segmentId}", "Segment ${seg.segmentId+1}: ID must be < OD")
        }
        s.bearingSystem.bearings.forEach { b ->
            if (s.operatingSpeedRpm > b.limitingSpeedRpm)
                put("brg_${b.bearingId}", "${b.designation}: RPM exceeds limit ${b.limitingSpeedRpm}")
        }
        if (s.solverEngine != SolverEngine.INTERNAL && s.solverStatus == SolverStatus.MISSING)
            put("solver", "${s.solverEngine.label} executable not found")
    }
}
