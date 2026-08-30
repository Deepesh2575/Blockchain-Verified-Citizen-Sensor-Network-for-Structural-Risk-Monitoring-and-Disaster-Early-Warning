package com.sih26223.sensing.profiler

import com.sih26223.sensing.model.DeviceTier
import com.sih26223.sensing.model.GnssCapability
import com.sih26223.sensing.model.SensorAvailability
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.model.SensorProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierClassifierTest {

    private fun usableSensor(kind: SensorKind, quality: Float = 0.8f) = SensorProbeResult(
        kind = kind,
        availability = SensorAvailability.AVAILABLE,
        qualityScore = quality
    )

    private fun missing(kind: SensorKind) = SensorProbeResult(
        kind = kind,
        availability = SensorAvailability.MISSING,
        qualityScore = 0f
    )

    @Test
    fun `T1 when barometer GNSS good and 4GB RAM`() {
        val sensors = mapOf(
            SensorKind.ACCELEROMETER to usableSensor(SensorKind.ACCELEROMETER),
            SensorKind.GYROSCOPE to usableSensor(SensorKind.GYROSCOPE),
            SensorKind.BAROMETER to usableSensor(SensorKind.BAROMETER),
            SensorKind.MAGNETOMETER to usableSensor(SensorKind.MAGNETOMETER),
            SensorKind.GPS to usableSensor(SensorKind.GPS)
        )
        val gnss = GnssCapability(SensorAvailability.AVAILABLE, avgAccuracyMeters = 8f, qualityScore = 0.9f)
        assertEquals(DeviceTier.T1_PREMIUM, DeviceTierClassifier.classify(4096, sensors, gnss))
    }

    @Test
    fun `T2 when IMU and GNSS but no barometer`() {
        val sensors = mapOf(
            SensorKind.ACCELEROMETER to usableSensor(SensorKind.ACCELEROMETER),
            SensorKind.GYROSCOPE to usableSensor(SensorKind.GYROSCOPE),
            SensorKind.BAROMETER to missing(SensorKind.BAROMETER),
            SensorKind.MAGNETOMETER to missing(SensorKind.MAGNETOMETER),
            SensorKind.GPS to usableSensor(SensorKind.GPS)
        )
        val gnss = GnssCapability(SensorAvailability.AVAILABLE, avgAccuracyMeters = 15f, qualityScore = 0.7f)
        assertEquals(DeviceTier.T2_STANDARD, DeviceTierClassifier.classify(3072, sensors, gnss))
    }

    @Test
    fun `T3 when low RAM and missing IMU`() {
        val sensors = SensorKind.entries.associateWith { missing(it) }
        val gnss = GnssCapability(SensorAvailability.PERMISSION_DENIED, qualityScore = 0f)
        assertEquals(DeviceTier.T3_BASIC, DeviceTierClassifier.classify(2048, sensors, gnss))
    }

    @Test
    fun capabilityScore_bounded_0_to_1() {
        val sensors = mapOf(
            SensorKind.ACCELEROMETER to usableSensor(SensorKind.ACCELEROMETER, 1f),
            SensorKind.GYROSCOPE to usableSensor(SensorKind.GYROSCOPE, 1f),
            SensorKind.BAROMETER to usableSensor(SensorKind.BAROMETER, 1f),
            SensorKind.MAGNETOMETER to usableSensor(SensorKind.MAGNETOMETER, 1f),
            SensorKind.GPS to usableSensor(SensorKind.GPS, 1f)
        )
        val gnss = GnssCapability(SensorAvailability.AVAILABLE, avgAccuracyMeters = 5f, qualityScore = 1f)
        val tier = DeviceTier.T1_PREMIUM
        val score = DeviceTierClassifier.computeCapabilityScore(tier, sensors, gnss)
        assertTrue(score in 0f..1f)
    }
}

class SensorQualityScorerTest {

    private val scorer = SensorQualityScorer()

    @Test
    fun low_noise_scores_higher() {
        val good = scorer.score(SensorKind.ACCELEROMETER, noiseStd = 0.05f, maxRateHz = 200f, resolution = 0.002f)
        val bad = scorer.score(SensorKind.ACCELEROMETER, noiseStd = 0.5f, maxRateHz = 50f, resolution = 0.01f)
        assertTrue(good > bad)
    }
}
