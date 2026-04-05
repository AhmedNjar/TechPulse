package com.example.spindle.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class MaterialSpec(
    val description: String,
    val yieldStrengthMpa: Double,
    val youngsModulusGpa: Double,
    val densityKgM3: Double,
    val fatigueStrengthMpa: Double
) {
    STEEL_42CrMo4   ("Alloy Steel 42CrMo4",   680.0,  210.0, 7850.0, 320.0),
    STEEL_42CrMo4_HT("42CrMo4 Heat Treated",  900.0,  210.0, 7850.0, 430.0),
    STEEL_100Cr6    ("Bearing Steel 100Cr6",  1700.0,  210.0, 7830.0, 750.0),
    TITANIUM_Ti6Al4V("Titanium Ti-6Al-4V",     880.0,  114.0, 4430.0, 480.0),
    CARBIDE_TUNGSTEN("Tungsten Carbide",       2200.0,  550.0,15600.0,1000.0)
}

@Serializable
enum class SimulationMode {
    STATIC, MODAL, MONTE_CARLO, FULL_ANALYSIS, ML_SURROGATE, OPTIMIZATION
}

@Serializable
data class StochasticVariables(
    val runout1SigmaMm: Double       = 0.003,
    val eccentricity1SigmaMm: Double = 0.002,
    val bearingPreloadCv: Double     = 0.12,
    val angularMisalignRad: Double   = 2e-4,
    val odToleranceMm: Double        = 0.005,
    val bearingClearanceMm: Double   = 0.003,
    val axialLoadCv: Double          = 0.08,
    val radialLoadCv: Double         = 0.10,
    val tempDeltaC: Double           = 15.0,
    val nIterations: Int             = 10_000,
    val confidenceLevel: Double      = 0.95
)

@Serializable
data class GAConstraints(
    val minNaturalFrequencyHz: Double  = 350.0,
    val maxToolTipDeflectionMm: Double = 0.05,
    val maxWeightKg: Double            = 25.0,
    val maxVonMisesMpa: Double         = 400.0
)

@Serializable
data class SpindleConfig(
    val configId: String,
    val projectName: String,
    val description: String              = "",
    val geometry: ShaftGeometry          = ShaftGeometry.defaultCncLathe(),
    val bearingSystem: BearingSystem     = BearingSystem.defaultCncLathe(),
    val operatingSpeedRpm: Double        = 12_000.0,
    val axialLoadN: Double               = 500.0,
    val radialLoadN: Double              = 1_500.0,
    val operatingTempC: Double           = 25.0,
    val material: MaterialSpec           = MaterialSpec.STEEL_42CrMo4,
    val stochastic: StochasticVariables  = StochasticVariables(),
    val gaConstraints: GAConstraints     = GAConstraints(),
    val simulationMode: SimulationMode   = SimulationMode.FULL_ANALYSIS,
    val nModalModes: Int                 = 6,
    val enableFEA: Boolean               = false,
    val targetDeflectionLimitMm: Double  = 0.05,
    val targetMinFrequencyHz: Double     = 350.0,
    val targetBearingLifeHours: Double   = 20_000.0
) {
    val estimatedWeightKg: Double get() {
        val rho = 7.85e-6
        return geometry.segments.sumOf { it.areaMm2 * it.lengthMm * rho }
    }

    companion object {
        fun default(name: String = "New CNC Lathe Spindle") = SpindleConfig(
            configId    = java.util.UUID.randomUUID().toString(),
            projectName = name,
            description = "CNC lathe spindle — 40–100 mm OD, 15,000 RPM max"
        )
    }
}
