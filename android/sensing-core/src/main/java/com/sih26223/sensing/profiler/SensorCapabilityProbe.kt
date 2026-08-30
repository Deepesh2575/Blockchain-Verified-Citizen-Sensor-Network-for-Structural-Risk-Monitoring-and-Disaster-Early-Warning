package com.sih26223.sensing.profiler

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.sih26223.sensing.model.SensorAvailability
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.model.SensorProbeResult
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Probes each hardware sensor for availability, max rate, noise floor, and quality score.
 * Runs a short calibration window (~2 s) per sensor.
 */
class SensorCapabilityProbe(
    private val context: Context,
    private val qualityScorer: SensorQualityScorer = SensorQualityScorer()
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    suspend fun probeAll(): Map<SensorKind, SensorProbeResult> {
        return SensorKind.entries.associateWith { kind ->
            when (kind) {
                SensorKind.GPS -> probeGpsPlaceholder()
                else -> probeHardwareSensor(kind)
            }
        }
    }

    private suspend fun probeHardwareSensor(kind: SensorKind): SensorProbeResult {
        val androidType = kind.toAndroidType() ?: return missing(kind)
        val sensor = sensorManager.getDefaultSensor(androidType)
            ?: return missing(kind)

        val maxRate = sensorManager.getSensorList(androidType)
            .mapNotNull { s -> if (s.minDelay > 0) 1_000_000f / s.minDelay else null }
            .maxOrNull()
            ?: (1_000_000f / sensor.minDelay.coerceAtLeast(1))

        val samples = collectSamples(sensor, sampleCount = 128, intervalUs = sensor.minDelay.coerceAtLeast(20_000))
        if (samples.isEmpty()) {
            return SensorProbeResult(
                kind = kind,
                availability = SensorAvailability.HARDWARE_DISABLED,
                maxSampleRateHz = maxRate
            )
        }

        val magnitudes = samples.map { vector -> vectorMagnitude(vector) }
        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        val std = sqrt(variance)
        val quality = qualityScorer.score(kind, std, maxRate, sensor.resolution)

        return SensorProbeResult(
            kind = kind,
            availability = SensorAvailability.AVAILABLE,
            maxSampleRateHz = maxRate,
            defaultSampleRateHz = minOf(maxRate, defaultRateFor(kind)),
            resolution = sensor.resolution,
            noiseFloor = std,
            qualityScore = quality,
            calibrationOffset = floatArrayOf(mean)
        )
    }

    private fun probeGpsPlaceholder(): SensorProbeResult {
        // GNSS probed separately via GnssCapabilityProbe; placeholder for map completeness.
        return SensorProbeResult(
            kind = SensorKind.GPS,
            availability = SensorAvailability.AVAILABLE,
            qualityScore = 0.5f
        )
    }

    private suspend fun collectSamples(
        sensor: Sensor,
        sampleCount: Int,
        intervalUs: Int
    ): List<FloatArray> {
        val buffer = mutableListOf<FloatArray>()
        val latch = java.util.concurrent.CountDownLatch(1)

        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                buffer.add(event.values.copyOf())
                if (buffer.size >= sampleCount) {
                    sensorManager.unregisterListener(this)
                    latch.countDown()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager.registerListener(
            listener, sensor,
            intervalUs,
            intervalUs
        )
        if (!registered) return emptyList()

        val deadline = System.currentTimeMillis() + 3_000L
        while (buffer.size < sampleCount && System.currentTimeMillis() < deadline) {
            delay(50)
        }
        sensorManager.unregisterListener(listener)
        return buffer
    }

    private fun missing(kind: SensorKind) = SensorProbeResult(
        kind = kind,
        availability = SensorAvailability.MISSING,
        qualityScore = 0f
    )

    private fun defaultRateFor(kind: SensorKind): Float = when (kind) {
        SensorKind.ACCELEROMETER -> 50f
        SensorKind.GYROSCOPE -> 50f
        SensorKind.BAROMETER -> 5f
        SensorKind.MAGNETOMETER -> 10f
        SensorKind.GPS -> 0.2f
    }

    private fun vectorMagnitude(v: FloatArray): Float {
        var sum = 0f
        for (x in v) sum += x * x
        return sqrt(sum)
    }
}

private fun SensorKind.toAndroidType(): Int? = when (this) {
    SensorKind.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
    SensorKind.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
    SensorKind.BAROMETER -> Sensor.TYPE_PRESSURE
    SensorKind.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
    SensorKind.GPS -> null
}

/**
 * Scores sensor quality 0..1 based on noise, resolution, and max sample rate.
 */
class SensorQualityScorer {

    fun score(kind: SensorKind, noiseStd: Float, maxRateHz: Float, resolution: Float): Float {
        val noiseScore = noiseScoreFor(kind, noiseStd)
        val rateScore = (maxRateHz / expectedMaxRate(kind)).coerceIn(0f, 1f)
        val resolutionScore = if (resolution > 0f) {
            (resolution / expectedResolution(kind)).coerceIn(0f, 1f)
        } else 0.5f
        return (0.5f * noiseScore + 0.3f * rateScore + 0.2f * resolutionScore).coerceIn(0f, 1f)
    }

    private fun noiseScoreFor(kind: SensorKind, std: Float): Float {
        // Lower noise → higher score. Thresholds tuned per sensor physics.
        val threshold = when (kind) {
            SensorKind.ACCELEROMETER -> 0.15f   // m/s² std while stationary
            SensorKind.GYROSCOPE -> 0.02f        // rad/s
            SensorKind.BAROMETER -> 0.3f         // hPa
            SensorKind.MAGNETOMETER -> 2f        // µT
            SensorKind.GPS -> 10f                // meters
        }
        return (1f - (std / threshold)).coerceIn(0f, 1f)
    }

    private fun expectedMaxRate(kind: SensorKind): Float = when (kind) {
        SensorKind.ACCELEROMETER, SensorKind.GYROSCOPE -> 200f
        SensorKind.BAROMETER -> 10f
        SensorKind.MAGNETOMETER -> 50f
        SensorKind.GPS -> 1f
    }

    private fun expectedResolution(kind: SensorKind): Float = when (kind) {
        SensorKind.ACCELEROMETER -> 0.002f
        SensorKind.GYROSCOPE -> 0.001f
        SensorKind.BAROMETER -> 0.01f
        SensorKind.MAGNETOMETER -> 0.1f
        SensorKind.GPS -> 1f
    }
}
