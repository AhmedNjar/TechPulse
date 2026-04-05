package com.example.spindle.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class ShaftSegment(
    val segmentId: Int,
    val name: String               = "Segment",
    val lengthMm: Double,
    val outerDiameterMm: Double,
    val innerDiameterMm: Double    = 0.0,
    val surfaceToleranceMm: Double = 0.005,
    val material: MaterialSpec     = MaterialSpec.STEEL_42CrMo4
) {
    val areaMm2: Double get() =
        Math.PI / 4.0 * (outerDiameterMm * outerDiameterMm - innerDiameterMm * innerDiameterMm)

    val secondMomentMm4: Double get() =
        Math.PI / 64.0 * (
            outerDiameterMm * outerDiameterMm * outerDiameterMm * outerDiameterMm -
            innerDiameterMm * innerDiameterMm * innerDiameterMm * innerDiameterMm
        )

    val hollownessRatio: Double get() =
        if (outerDiameterMm == 0.0) 0.0 else innerDiameterMm / outerDiameterMm

    val isHollow: Boolean get() = innerDiameterMm > 0.0
}

@Serializable
data class ShaftGeometry(val segments: List<ShaftSegment>) {

    val totalLengthMm: Double get() = segments.sumOf { it.lengthMm }

    val zBoundariesMm: List<Double> get() {
        val zs = mutableListOf(0.0)
        segments.forEach { zs.add(zs.last() + it.lengthMm) }
        return zs
    }

    companion object {
        fun defaultCncLathe() = ShaftGeometry(segments = listOf(
            ShaftSegment(0, "Tool Taper",    lengthMm = 80.0,  outerDiameterMm = 60.0, innerDiameterMm = 0.0,  surfaceToleranceMm = 0.003),
            ShaftSegment(1, "Front Journal", lengthMm = 120.0, outerDiameterMm = 80.0, innerDiameterMm = 40.0, surfaceToleranceMm = 0.005),
            ShaftSegment(2, "Body",          lengthMm = 180.0, outerDiameterMm = 90.0, innerDiameterMm = 55.0, surfaceToleranceMm = 0.010),
            ShaftSegment(3, "Rear Journal",  lengthMm = 100.0, outerDiameterMm = 70.0, innerDiameterMm = 40.0, surfaceToleranceMm = 0.005),
        ))
    }
}
