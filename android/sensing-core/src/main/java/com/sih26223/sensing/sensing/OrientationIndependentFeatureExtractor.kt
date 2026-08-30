package com.sih26223.sensing.sensing

import com.sih26223.sensing.model.DeviceTier
import com.sih26223.sensing.model.OrientationInvariantFeatures
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.util.MathUtils
import kotlin.math.abs

/**
 * Computes rotation-invariant features from sensor windows.
 *
 * Orientation independence strategy:
 * - Use vector magnitudes |a|, |ω|, |B| instead of axis components
 * - High-pass gravity removal via successive magnitude differencing (jerk)
 * - Barometer and GPS are inherently orientation-independent
 */
class OrientationIndependentFeatureExtractor {

    private var lastBaroHpa: Float? = null
    private var lastBaroTimestampMs: Long = 0L

    fun extract(
        buffer: SensorSampleBuffer,
        gnssFix: GnssFix?,
        tier: DeviceTier,
        qualitySnapshot: Map<SensorKind, Float>,
        enableFft: Boolean
    ): OrientationInvariantFeatures {
        val accelMag = buffer.peekMagnitudes(SensorKind.ACCELEROMETER, 128)
        val gyroMag = buffer.peekMagnitudes(SensorKind.GYROSCOPE, 128)

        val accelMean = MathUtils.mean(accelMag)
        val accelStd = MathUtils.std(accelMag)
        val gyroMean = MathUtils.mean(gyroMag)
        val gyroStd = MathUtils.std(gyroMag)
        val jerkRms = computeJerkRms(accelMag)

        val (energyLow, energyMid) = if (enableFft && accelMag.size >= 64) {
            bandEnergies(accelMag)
        } else {
            0f to 0f
        }

        val baroSample = buffer.latest(SensorKind.BAROMETER)
        val baroHpa = baroSample?.magnitude
        val baroDelta = computeBaroDelta(baroHpa, baroSample?.timestampNs?.div(1_000_000) ?: 0L)

        val magSample = buffer.latest(SensorKind.MAGNETOMETER)
        val magStrength = magSample?.magnitude

        return OrientationInvariantFeatures(
            timestampMs = System.currentTimeMillis(),
            accelMagnitudeMean = accelMean,
            accelMagnitudeStd = accelStd,
            accelJerkRms = jerkRms,
            gyroMagnitudeMean = gyroMean,
            gyroMagnitudeStd = gyroStd,
            vibrationEnergyLowBand = energyLow,
            vibrationEnergyMidBand = energyMid,
            baroPressureHpa = baroHpa,
            baroDeltaHpaPerMin = baroDelta,
            magFieldStrengthUt = magStrength,
            latitude = gnssFix?.latitude,
            longitude = gnssFix?.longitude,
            gpsAccuracyM = gnssFix?.accuracyM,
            speedMps = gnssFix?.speedMps,
            deviceTier = tier,
            sensorQualitySnapshot = qualitySnapshot
        )
    }

    /** Jerk = d|a|/dt — rotation invariant proxy for impulsive events (quake, impact). */
    private fun computeJerkRms(accelMagnitudes: FloatArray): Float {
        if (accelMagnitudes.size < 3) return 0f
        var sum = 0f
        var count = 0
        for (i in 1 until accelMagnitudes.size) {
            val jerk = accelMagnitudes[i] - accelMagnitudes[i - 1]
            sum += jerk * jerk
            count++
        }
        return if (count > 0) kotlin.math.sqrt(sum / count) else 0f
    }

    private fun computeBaroDelta(currentHpa: Float?, timestampMs: Long): Float? {
        if (currentHpa == null) return null
        val prev = lastBaroHpa ?: run {
            lastBaroHpa = currentHpa
            lastBaroTimestampMs = timestampMs
            return null
        }
        val dtMin = (timestampMs - lastBaroTimestampMs).coerceAtLeast(1L) / 60_000f
        val delta = (currentHpa - prev) / dtMin
        lastBaroHpa = currentHpa
        lastBaroTimestampMs = timestampMs
        return delta
    }

    /**
     * Lightweight DFT energy in 0.5–5 Hz and 5–20 Hz bands for structural vibration.
     * Intentionally simple — avoids heavy FFT on low-end devices when enableFft=false.
     */
    private fun bandEnergies(signal: FloatArray): Pair<Float, Float> {
        val n = signal.size
        var low = 0f
        var mid = 0f
        val sampleRate = 50f // assumed; sufficient for relative energy comparison
        for (k in 1 until n / 2) {
            var re = 0f
            var im = 0f
            for (t in signal.indices) {
                val angle = 2.0 * Math.PI * k * t / n
                re += (signal[t] * kotlin.math.cos(angle)).toFloat()
                im += (signal[t] * kotlin.math.sin(angle)).toFloat()
            }
            val power = re * re + im * im
            val freq = k * sampleRate / n
            when {
                freq in 0.5f..5f -> low += power
                freq in 5f..20f -> mid += power
            }
        }
        return low to mid
    }
}

/**
 * Lightweight on-device event detector using orientation-invariant thresholds.
 * Thresholds scale by device tier and sensor quality.
 */
class LocalEventDetector {

    fun detect(features: OrientationInvariantFeatures): LocalDetection? {
        val quality = features.sensorQualitySnapshot[SensorKind.ACCELEROMETER] ?: 0.5f
        val jerkThreshold = 2.0f / quality.coerceAtLeast(0.3f)
        val baroDropThreshold = -0.5f // hPa/min — rapid pressure drop

        return when {
            features.accelJerkRms > jerkThreshold && features.accelMagnitudeStd > 0.5f ->
                LocalDetection("VIBRATION_ANOMALY", confidence = (features.accelJerkRms / 10f).coerceIn(0f, 1f))

            features.baroDeltaHpaPerMin != null && features.baroDeltaHpaPerMin < baroDropThreshold ->
                LocalDetection("PRESSURE_DROP", confidence = abs(features.baroDeltaHpaPerMin / 2f).coerceIn(0f, 1f))

            features.vibrationEnergyMidBand > 100f ->
                LocalDetection("STRUCTURAL_VIBRATION", confidence = 0.6f)

            else -> null
        }
    }
}

data class LocalDetection(val type: String, val confidence: Float)
