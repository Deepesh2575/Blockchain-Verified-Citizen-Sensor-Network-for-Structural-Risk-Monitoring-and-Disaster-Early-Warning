package com.sih26223.sensing.model

/**
 * Hardware tier derived from RAM, sensor suite, and quality scores.
 * Drives adaptive sampling and feature complexity.
 */
enum class DeviceTier {
    /** Barometer + good GNSS + high sensor quality — full hazard detection */
    T1_PREMIUM,
    /** IMU + GNSS, adequate quality — vibration, movement, geo events */
    T2_STANDARD,
    /** Limited sensors or low quality — corroboration / manual reports only */
    T3_BASIC
}

enum class BatteryMode {
    NORMAL,
    POWER_SAVE,
    CRITICAL,
    /** Overrides power save — active disaster / user-declared emergency */
    DISASTER
}

enum class SensorKind {
    ACCELEROMETER,
    GYROSCOPE,
    BAROMETER,
    MAGNETOMETER,
    GPS
}

enum class SensorAvailability {
    AVAILABLE,
    MISSING,
    PERMISSION_DENIED,
    HARDWARE_DISABLED
}

data class SensorProbeResult(
    val kind: SensorKind,
    val availability: SensorAvailability,
    val maxSampleRateHz: Float = 0f,
    val defaultSampleRateHz: Float = 0f,
    val resolution: Float = 0f,
    val noiseFloor: Float = 0f,
    val qualityScore: Float = 0f,
    val calibrationOffset: FloatArray? = null
) {
    val isUsable: Boolean
        get() = availability == SensorAvailability.AVAILABLE && qualityScore >= 0.3f
}

data class GnssCapability(
    val availability: SensorAvailability,
    val avgAccuracyMeters: Float = Float.MAX_VALUE,
    val dualFrequencyCapable: Boolean = false,
    val qualityScore: Float = 0f
) {
    val isUsable: Boolean
        get() = availability == SensorAvailability.AVAILABLE && qualityScore >= 0.3f
}

data class DeviceCapabilityReport(
    val deviceId: String,
    val profiledAtEpochMs: Long,
    val ramClassMb: Int,
    val tier: DeviceTier,
    val capabilityScore: Float,
    val sensors: Map<SensorKind, SensorProbeResult>,
    val gnss: GnssCapability,
    val integrityFlags: IntegrityFlags,
    val recommendedSamplingProfile: String
)

data class IntegrityFlags(
    val rooted: Boolean = false,
    val mockLocationEnabled: Boolean = false,
    val developerOptionsEnabled: Boolean = false
)

data class SamplingProfile(
    val name: String,
    val accelHz: Float,
    val gyroHz: Float,
    val baroHz: Float,
    val magHz: Float,
    val gpsIntervalMs: Long,
    val featureWindowMs: Long,
    val batchUploadIntervalMs: Long,
    val enableFftFeatures: Boolean,
    val maxBufferedEvents: Int
)

data class OrientationInvariantFeatures(
    val timestampMs: Long,
    val accelMagnitudeMean: Float,
    val accelMagnitudeStd: Float,
    val accelJerkRms: Float,
    val gyroMagnitudeMean: Float,
    val gyroMagnitudeStd: Float,
    val vibrationEnergyLowBand: Float,
    val vibrationEnergyMidBand: Float,
    val baroPressureHpa: Float?,
    val baroDeltaHpaPerMin: Float?,
    val magFieldStrengthUt: Float?,
    val latitude: Double?,
    val longitude: Double?,
    val gpsAccuracyM: Float?,
    val speedMps: Float?,
    val deviceTier: DeviceTier,
    val sensorQualitySnapshot: Map<SensorKind, Float>
)

data class ObservationEvent(
    val id: String,
    val createdAtEpochMs: Long,
    val eventType: String,
    val confidence: Float,
    val features: OrientationInvariantFeatures,
    val payloadHashHex: String,
    val tierAtCapture: DeviceTier
)
