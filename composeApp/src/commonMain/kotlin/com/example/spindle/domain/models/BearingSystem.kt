package com.example.spindle.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class BearingTypeV2(val label: String) {
    ANGULAR_CONTACT_15("Angular Contact 15°"),
    ANGULAR_CONTACT_25("Angular Contact 25°"),
    ANGULAR_CONTACT_40("Angular Contact 40°"),
    DEEP_GROOVE_BALL  ("Deep Groove Ball"),
    CYLINDRICAL_ROLLER("Cylindrical Roller")
}

@Serializable
enum class PreloadClassV2(val label: String, val typicalForceN: Double) {
    LIGHT ("Light  (50–150 N)",  100.0),
    MEDIUM("Medium (150–350 N)", 250.0),
    HEAVY ("Heavy  (350–700 N)", 500.0)
}

@Serializable
enum class MountingArrangementV2(val label: String) {
    BACK_TO_BACK("Back-to-Back (DB)"),
    FACE_TO_FACE("Face-to-Face (DF)"),
    TANDEM      ("Tandem (DT)"),
    SINGLE      ("Single")
}

@Serializable
enum class BearingRoleV2(val label: String) {
    LOCATING    ("Locating (Fixed)"),
    NON_LOCATING("Non-Locating (Free)")
}

@Serializable
data class SpacerConfigV2(
    val lengthMm: Double,
    val odMm: Double,
    val isPrecision: Boolean = true
)

@Serializable
data class BearingMount(
    val bearingId: Int,
    val designation: String                           = "7010 CE/P4A",
    val bearingType: BearingTypeV2                    = BearingTypeV2.ANGULAR_CONTACT_25,
    val zPositionMm: Double,
    val role: BearingRoleV2                           = BearingRoleV2.LOCATING,
    val arrangement: MountingArrangementV2            = MountingArrangementV2.BACK_TO_BACK,
    val preloadClass: PreloadClassV2                  = PreloadClassV2.MEDIUM,
    val preloadForceN: Double                         = 250.0,
    val housingStiffnessNPerMm: Double                = 200.0,
    val contactAngleDeg: Double                       = 25.0,
    val boreDiameterMm: Double                        = 50.0,
    val outerDiameterMm: Double                       = 80.0,
    val widthMm: Double                               = 16.0,
    val dynamicLoadRatingKn: Double                   = 25.0,
    val staticLoadRatingKn: Double                    = 20.0,
    val limitingSpeedRpm: Int                         = 28_000,
    val spacerAfter: SpacerConfigV2?                  = null
) {
    val lifeExponent: Double get() = when (bearingType) {
        BearingTypeV2.CYLINDRICAL_ROLLER -> 10.0 / 3.0
        else -> 3.0
    }

    fun l10LifeHours(equivalentLoadN: Double, speedRpm: Double): Double {
        val c = dynamicLoadRatingKn * 1000.0
        val p = equivalentLoadN.coerceAtLeast(1.0)
        return Math.pow(c / p, lifeExponent) * 1e6 / (60.0 * speedRpm)
    }
}

@Serializable
data class BearingSystem(val bearings: List<BearingMount>) {

    val sortedByPosition: List<BearingMount> get() = bearings.sortedBy { it.zPositionMm }
    val frontBearing: BearingMount get() = sortedByPosition.first()
    val rearBearing: BearingMount  get() = sortedByPosition.last()

    companion object {
        fun defaultCncLathe() = BearingSystem(bearings = listOf(
            BearingMount(0, "7010 CE/P4A", BearingTypeV2.ANGULAR_CONTACT_25,
                zPositionMm = 80.0, role = BearingRoleV2.LOCATING,
                arrangement = MountingArrangementV2.BACK_TO_BACK,
                preloadClass = PreloadClassV2.MEDIUM, preloadForceN = 280.0,
                housingStiffnessNPerMm = 220.0,
                boreDiameterMm = 50.0, outerDiameterMm = 80.0, widthMm = 16.0,
                dynamicLoadRatingKn = 25.0, staticLoadRatingKn = 20.0,
                limitingSpeedRpm = 28_000, spacerAfter = SpacerConfigV2(20.0, 75.0)
            ),
            BearingMount(1, "7010 CE/P4A", BearingTypeV2.ANGULAR_CONTACT_25,
                zPositionMm = 116.0, role = BearingRoleV2.LOCATING,
                arrangement = MountingArrangementV2.BACK_TO_BACK,
                preloadClass = PreloadClassV2.MEDIUM, preloadForceN = 280.0,
                housingStiffnessNPerMm = 220.0,
                boreDiameterMm = 50.0, outerDiameterMm = 80.0, widthMm = 16.0,
                dynamicLoadRatingKn = 25.0, staticLoadRatingKn = 20.0,
                limitingSpeedRpm = 28_000
            ),
            BearingMount(2, "NN3010", BearingTypeV2.CYLINDRICAL_ROLLER,
                zPositionMm = 340.0, role = BearingRoleV2.NON_LOCATING,
                arrangement = MountingArrangementV2.SINGLE,
                preloadClass = PreloadClassV2.LIGHT, preloadForceN = 0.0,
                housingStiffnessNPerMm = 150.0,
                boreDiameterMm = 50.0, outerDiameterMm = 72.0, widthMm = 22.0,
                dynamicLoadRatingKn = 48.0, staticLoadRatingKn = 58.0,
                limitingSpeedRpm = 22_000
            ),
        ))
    }
}
