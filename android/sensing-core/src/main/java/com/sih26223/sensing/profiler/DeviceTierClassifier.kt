package com.sih26223.sensing.profiler

import com.sih26223.sensing.model.DeviceTier
import com.sih26223.sensing.model.GnssCapability
import com.sih26223.sensing.model.IntegrityFlags
import com.sih26223.sensing.model.SamplingProfile
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.model.SensorProbeResult

/**
 * Maps device RAM + sensor suite + quality scores to a tier and sampling profile.
 */
object DeviceTierClassifier {

    fun classify(
        ramMb: Int,
        sensors: Map<SensorKind, SensorProbeResult>,
        gnss: GnssCapability
    ): DeviceTier {
        val baro = sensors[SensorKind.BAROMETER]?.isUsable == true
        val imu = sensors[SensorKind.ACCELEROMETER]?.isUsable == true &&
            sensors[SensorKind.GYROSCOPE]?.isUsable == true
        val gnssOk = gnss.isUsable && gnss.avgAccuracyMeters <= 25f

        return when {
            baro && gnssOk && ramMb >= 4096 -> DeviceTier.T1_PREMIUM
            imu && gnss.isUsable && ramMb >= 2048 -> DeviceTier.T2_STANDARD
            else -> DeviceTier.T3_BASIC
        }
    }

    fun computeCapabilityScore(
        tier: DeviceTier,
        sensors: Map<SensorKind, SensorProbeResult>,
        gnss: GnssCapability
    ): Float {
        val weights = mapOf(
            SensorKind.ACCELEROMETER to 0.25f,
            SensorKind.GYROSCOPE to 0.20f,
            SensorKind.BAROMETER to 0.20f,
            SensorKind.MAGNETOMETER to 0.10f,
            SensorKind.GPS to 0.25f
        )
        var score = 0f
        for ((kind, weight) in weights) {
            val component = when (kind) {
                SensorKind.GPS -> gnss.qualityScore
                else -> sensors[kind]?.qualityScore ?: 0f
            }
            score += weight * component
        }
        // Tier floor prevents over-scoring weak devices with one good sensor.
        val tierFloor = when (tier) {
            DeviceTier.T1_PREMIUM -> 0.75f
            DeviceTier.T2_STANDARD -> 0.50f
            DeviceTier.T3_BASIC -> 0.25f
        }
        return minOf(1f, maxOf(tierFloor * 0.5f, score))
    }
}

object SamplingProfileFactory {

    fun forTier(tier: DeviceTier, ramMb: Int): SamplingProfile = when (tier) {
        DeviceTier.T1_PREMIUM -> SamplingProfile(
            name = "premium",
            accelHz = if (ramMb >= 6144) 100f else 50f,
            gyroHz = if (ramMb >= 6144) 100f else 50f,
            baroHz = 5f,
            magHz = 10f,
            gpsIntervalMs = 5_000L,
            featureWindowMs = 2_000L,
            batchUploadIntervalMs = 60_000L,
            enableFftFeatures = ramMb >= 4096,
            maxBufferedEvents = 2_000
        )
        DeviceTier.T2_STANDARD -> SamplingProfile(
            name = "standard",
            accelHz = 50f,
            gyroHz = 25f,
            baroHz = 1f,
            magHz = 5f,
            gpsIntervalMs = 10_000L,
            featureWindowMs = 3_000L,
            batchUploadIntervalMs = 120_000L,
            enableFftFeatures = false,
            maxBufferedEvents = 1_000
        )
        DeviceTier.T3_BASIC -> SamplingProfile(
            name = "basic",
            accelHz = 25f,
            gyroHz = 0f,
            baroHz = 0f,
            magHz = 0f,
            gpsIntervalMs = 30_000L,
            featureWindowMs = 5_000L,
            batchUploadIntervalMs = 300_000L,
            enableFftFeatures = false,
            maxBufferedEvents = 500
        )
    }
}

object IntegrityChecker {

    fun probe(): IntegrityFlags {
        val rooted = checkRootIndicators()
        return IntegrityFlags(
            rooted = rooted,
            mockLocationEnabled = false, // resolved at runtime when location client active
            developerOptionsEnabled = false
        )
    }

    @Suppress("SwallowedException")
    private fun checkRootIndicators(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su"
        )
        return paths.any { path ->
            try {
                java.io.File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
    }
}
