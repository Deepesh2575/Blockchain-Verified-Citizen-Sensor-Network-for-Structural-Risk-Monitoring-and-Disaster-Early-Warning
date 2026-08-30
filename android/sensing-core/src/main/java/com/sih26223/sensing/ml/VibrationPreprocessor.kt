package com.sih26223.sensing.ml

import com.sih26223.sensing.util.MathUtils
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Stage 1–2 of pipeline: gravity removal, filtering, detrend, windowing.
 *
 * Input: raw accelerometer magnitude series (|a| in m/s²) at known sample rate.
 * Output: overlapping fixed-length windows ready for feature extraction.
 */
class VibrationPreprocessor(
    private val sampleRateHz: Float,
    private val windowSamples: Int,
    private val hopSamples: Int
) {
    /** Running estimate of gravity component on |a| (slow EMA). */
    private var gravityBaseline: Float = 9.81f

    /**
     * Remove slow gravity drift from magnitude signal via high-pass (EMA subtraction).
     * Phone orientation changes appear as slow |a| drift; structural vibration is 0.5–40 Hz.
     */
    fun highPassGravityRemoval(magnitudes: FloatArray, alpha: Float = 0.98f): FloatArray {
        if (magnitudes.isEmpty()) return magnitudes
        val out = FloatArray(magnitudes.size)
        var baseline = gravityBaseline
        for (i in magnitudes.indices) {
            baseline = alpha * baseline + (1f - alpha) * magnitudes[i]
            out[i] = magnitudes[i] - baseline
        }
        gravityBaseline = baseline
        return out
    }

    /** Remove linear trend (least-squares line) within window — reduces temperature drift artifacts. */
    fun detrend(window: FloatArray): FloatArray {
        val n = window.size
        if (n < 2) return window
        var sumX = 0f
        var sumY = 0f
        var sumXY = 0f
        var sumX2 = 0f
        for (i in 0 until n) {
            sumX += i
            sumY += window[i]
            sumXY += i * window[i]
            sumX2 += i * i
        }
        val denom = n * sumX2 - sumX * sumX
        if (denom == 0f) return window
        val slope = (n * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / n
        return FloatArray(n) { i -> window[i] - (intercept + slope * i) }
    }

    /** Hann window to reduce spectral leakage before FFT / CNN. */
    fun applyHann(window: FloatArray): FloatArray {
        val n = window.size
        return FloatArray(n) { i ->
            val w = 0.5f * (1f - kotlin.math.cos(2.0 * Math.PI * i / (n - 1).coerceAtLeast(1))).toFloat()
            window[i] * w
        }
    }

    /** Z-score normalize using running Welford stats (online normalization for TFLite input stability). */
    class RunningNormalizer {
        private var count = 0L
        private var mean = 0.0
        private var m2 = 0.0

        fun update(x: Float) {
            count++
            val delta = x - mean
            mean += delta / count
            m2 += delta * (x - mean)
        }

        fun normalize(value: Float): Float {
            if (count < 2) return value
            val variance = m2 / (count - 1)
            val std = kotlin.math.sqrt(variance).toFloat().coerceAtLeast(1e-6f)
            return (value - mean.toFloat()) / std
        }
    }

    /**
     * Slice signal into overlapping windows.
     * @return list of (windowStartIndex, windowArray)
     */
    fun window(magnitudes: FloatArray): List<WindowSlice> {
        if (magnitudes.size < windowSamples) {
            val padded = FloatArray(windowSamples)
            System.arraycopy(magnitudes, 0, padded, 0, magnitudes.size)
            return listOf(WindowSlice(0, applyHann(detrend(padded))))
        }
        val slices = mutableListOf<WindowSlice>()
        var start = 0
        while (start + windowSamples <= magnitudes.size) {
            val raw = magnitudes.copyOfRange(start, start + windowSamples)
            val processed = applyHann(detrend(highPassGravityRemoval(raw)))
            slices.add(WindowSlice(start, processed))
            start += hopSamples
        }
        return slices
    }

    data class WindowSlice(val startIndex: Int, val data: FloatArray) {
        override fun equals(other: Any?) = other is WindowSlice && data.contentEquals(other.data)
        override fun hashCode() = data.contentHashCode()
    }
}

/**
 * Stage 3: compute time-domain, frequency-domain, and statistical features.
 *
 * Full feature set (64) — model selector picks subset per tier.
 */
object VibrationFeatureExtractor {

    val ALL_FEATURE_NAMES: List<String> = listOf(
        // Time domain (12)
        "rms", "peak", "peak_to_peak", "crest_factor", "variance", "std", "skewness", "kurtosis",
        "jerk_rms", "jerk_peak", "zero_crossing_rate", "energy",
        // Frequency domain — band energies (10)
        "band_0_5_1hz", "band_1_2hz", "band_2_5hz", "band_5_10hz", "band_10_20hz",
        "band_20_40hz", "spectral_centroid", "spectral_spread", "spectral_entropy", "dominant_freq",
        // Structural proxies (8)
        "fundamental_freq", "harmonic_ratio_2f", "harmonic_ratio_3f", "modal_energy_ratio",
        "decay_rate", "quality_factor_q", "impulse_factor", "clearance_factor",
        // Gyro coupling (6) — rotation-invariant magnitude
        "gyro_rms", "gyro_peak", "gyro_accel_ratio", "gyro_band_5_20hz",
        "gyro_variance", "gyro_jerk_rms",
        // Context (4)
        "sample_rate", "window_duration_s", "quality_weight", "snr_estimate",
        // Higher-order stats (24) — populated for MEDIUM only
        "p10", "p25", "p50", "p75", "p90", "iqr",
        "autocorr_lag1", "autocorr_lag5", "autocorr_lag10",
        "fft_bin_1", "fft_bin_2", "fft_bin_3", "fft_bin_4", "fft_bin_5",
        "fft_bin_6", "fft_bin_7", "fft_bin_8", "fft_bin_9", "fft_bin_10",
        "wavelet_energy_1", "wavelet_energy_2", "wavelet_energy_3", "wavelet_energy_4"
    )

    fun extract(
        accelWindow: FloatArray,
        sampleRateHz: Float,
        gyroWindow: FloatArray? = null,
        sensorQuality: Float = 1f,
        featureCount: Int = 32
    ): FloatArray {
        val full = FloatArray(ALL_FEATURE_NAMES.size)
        val n = accelWindow.size
        if (n == 0) return FloatArray(featureCount)

        val mean = MathUtils.mean(accelWindow)
        val std = MathUtils.std(accelWindow)
        val rms = kotlin.math.sqrt(accelWindow.map { it * it }.average()).toFloat()
        val peak = accelWindow.maxOrNull() ?: 0f
        val minVal = accelWindow.minOrNull() ?: 0f
        val peakToPeak = peak - minVal
        val crest = if (rms > 1e-6f) peak / rms else 0f
        val variance = std * std
        val energy = accelWindow.sumOf { (it * it).toDouble() }.toFloat()

        full[0] = rms
        full[1] = peak
        full[2] = peakToPeak
        full[3] = crest
        full[4] = variance
        full[5] = std
        full[6] = skewness(accelWindow, mean, std)
        full[7] = kurtosis(accelWindow, mean, std)
        full[8] = jerkRms(accelWindow)
        full[9] = jerkPeak(accelWindow)
        full[10] = zeroCrossingRate(accelWindow)
        full[11] = energy

        val spectrum = magnitudeSpectrum(accelWindow)
        val bands = bandEnergies(spectrum, sampleRateHz, n)
        for (i in 0 until min(5, bands.size)) full[12 + i] = bands[i]
        full[17] = spectralCentroid(spectrum, sampleRateHz, n)
        full[18] = spectralSpread(spectrum, full[17], sampleRateHz, n)
        full[19] = spectralEntropy(spectrum)
        full[20] = dominantFrequency(spectrum, sampleRateHz, n)

        // Structural proxies (simplified estimators)
        full[21] = full[20] // fundamental ≈ dominant
        full[22] = harmonicRatio(spectrum, full[20], sampleRateHz, n, 2)
        full[23] = harmonicRatio(spectrum, full[20], sampleRateHz, n, 3)
        full[24] = if (bands.sum() > 0) bands[2] / bands.sum() else 0f
        full[25] = decayRate(accelWindow)
        full[26] = if (full[25] > 0) full[20] / (2 * full[25]) else 0f
        full[27] = if (rms > 0) peak / rms else 0f
        full[28] = if (peak > 0) peakToPeak / peak else 0f

        if (gyroWindow != null && gyroWindow.isNotEmpty()) {
            val gRms = kotlin.math.sqrt(gyroWindow.map { it * it }.average()).toFloat()
            full[29] = gRms
            full[30] = gyroWindow.maxOrNull() ?: 0f
            full[31] = if (rms > 1e-6f) gRms / rms else 0f
            full[32] = bandEnergies(magnitudeSpectrum(gyroWindow), sampleRateHz, gyroWindow.size)
                .drop(2).take(1).firstOrNull() ?: 0f
            full[33] = MathUtils.std(gyroWindow)
            full[34] = jerkRms(gyroWindow)
        }

        full[35] = sampleRateHz
        full[36] = n / sampleRateHz
        full[37] = sensorQuality
        full[38] = if (std > 0) rms / std else 0f

        // Percentiles for MEDIUM
        if (featureCount >= 64) {
            val sorted = accelWindow.sorted()
            full[39] = percentile(sorted, 0.10f)
            full[40] = percentile(sorted, 0.25f)
            full[41] = percentile(sorted, 0.50f)
            full[42] = percentile(sorted, 0.75f)
            full[43] = percentile(sorted, 0.90f)
            full[44] = full[42] - full[40]
            full[45] = autocorr(accelWindow, 1)
            full[46] = autocorr(accelWindow, 5)
            full[47] = autocorr(accelWindow, 10)
            for (i in 0 until 10) full[48 + i] = spectrum.getOrElse(i + 1) { 0f }
            val wavelet = haarWaveletEnergy(accelWindow)
            for (i in 0 until 4) full[58 + i] = wavelet.getOrElse(i) { 0f }
        }

        return full.copyOf(featureCount)
    }

    /** Build 32×16 log-power spectrogram patch for MEDIUM CNN branch. */
    fun spectrogramPatch(window: FloatArray, sampleRateHz: Float, rows: Int = 32, cols: Int = 16): Array<FloatArray> {
        val spec = magnitudeSpectrum(window)
        val patch = Array(rows) { FloatArray(cols) }
        val step = max(1, spec.size / (rows * cols))
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = (r * cols + c) * step
                val power = if (idx < spec.size) spec[idx] * spec[idx] else 0f
                patch[r][c] = ln(power + 1e-8f)
            }
        }
        return patch
    }

    private fun skewness(v: FloatArray, mean: Float, std: Float): Float {
        if (std < 1e-6f) return 0f
        return v.map { val d = (it - mean) / std; d * d * d }.average().toFloat()
    }

    private fun kurtosis(v: FloatArray, mean: Float, std: Float): Float {
        if (std < 1e-6f) return 0f
        return v.map { val d = (it - mean) / std; d * d * d * d }.average().toFloat() - 3f
    }

    private fun jerkRms(v: FloatArray): Float {
        if (v.size < 2) return 0f
        var sum = 0f
        for (i in 1 until v.size) {
            val j = v[i] - v[i - 1]
            sum += j * j
        }
        return kotlin.math.sqrt(sum / (v.size - 1))
    }

    private fun jerkPeak(v: FloatArray): Float {
        if (v.size < 2) return 0f
        var maxJ = 0f
        for (i in 1 until v.size) {
            maxJ = max(maxJ, kotlin.math.abs(v[i] - v[i - 1]))
        }
        return maxJ
    }

    private fun zeroCrossingRate(v: FloatArray): Float {
        if (v.size < 2) return 0f
        var crosses = 0
        for (i in 1 until v.size) {
            if (v[i] * v[i - 1] < 0) crosses++
        }
        return crosses.toFloat() / (v.size - 1)
    }

    private fun magnitudeSpectrum(v: FloatArray): FloatArray {
        val n = v.size
        val half = n / 2
        val spec = FloatArray(half)
        for (k in 1 until half) {
            var re = 0.0
            var im = 0.0
            for (t in v.indices) {
                val angle = 2.0 * Math.PI * k * t / n
                re += v[t] * kotlin.math.cos(angle)
                im += v[t] * kotlin.math.sin(angle)
            }
            spec[k] = kotlin.math.sqrt(re * re + im * im).toFloat()
        }
        return spec
    }

    private fun bandEnergies(spec: FloatArray, fs: Float, n: Int): FloatArray {
        val bands = floatArrayOf(0.5f, 1f, 2f, 5f, 10f, 20f, 40f)
        val energies = FloatArray(bands.size - 1)
        for (k in 1 until spec.size) {
            val freq = k * fs / n
            for (b in 0 until bands.size - 1) {
                if (freq in bands[b]..bands[b + 1]) {
                    energies[b] += spec[k] * spec[k]
                }
            }
        }
        return energies
    }

    private fun spectralCentroid(spec: FloatArray, fs: Float, n: Int): Float {
        var num = 0f
        var den = 0f
        for (k in spec.indices) {
            val f = k * fs / n
            num += f * spec[k]
            den += spec[k]
        }
        return if (den > 0) num / den else 0f
    }

    private fun spectralSpread(spec: FloatArray, centroid: Float, fs: Float, n: Int): Float {
        var num = 0f
        var den = 0f
        for (k in spec.indices) {
            val f = k * fs / n
            val d = f - centroid
            num += d * d * spec[k]
            den += spec[k]
        }
        return if (den > 0) kotlin.math.sqrt(num / den) else 0f
    }

    private fun spectralEntropy(spec: FloatArray): Float {
        val total = spec.sum().coerceAtLeast(1e-8f)
        var entropy = 0f
        for (s in spec) {
            val p = s / total
            if (p > 0) entropy -= p * ln(p)
        }
        return entropy
    }

    private fun dominantFrequency(spec: FloatArray, fs: Float, n: Int): Float {
        var maxK = 1
        for (k in 2 until spec.size) {
            if (spec[k] > spec[maxK]) maxK = k
        }
        return maxK * fs / n
    }

    private fun harmonicRatio(spec: FloatArray, f0: Float, fs: Float, n: Int, harmonic: Int): Float {
        val target = f0 * harmonic
        var energy = 0f
        var total = spec.sum().coerceAtLeast(1e-8f)
        for (k in spec.indices) {
            val f = k * fs / n
            if (kotlin.math.abs(f - target) < 0.5f) energy += spec[k]
        }
        return energy / total
    }

    private fun decayRate(v: FloatArray): Float {
        val peakIdx = v.indices.maxByOrNull { v[it] } ?: 0
        if (peakIdx >= v.size - 2) return 0f
        val post = v.copyOfRange(peakIdx, v.size)
        val env = post.map { kotlin.math.abs(it) }
        val mid = env.size / 2
        if (env[mid] < 1e-6f) return 0f
        return -ln((env.last() + 1e-6f) / (env[mid] + 1e-6f)) / (env.size - mid).coerceAtLeast(1)
    }

    private fun percentile(sorted: List<Float>, p: Float): Float {
        val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun autocorr(v: FloatArray, lag: Int): Float {
        if (lag >= v.size) return 0f
        val mean = MathUtils.mean(v)
        var num = 0f
        var den = 0f
        for (i in v.indices) {
            den += (v[i] - mean) * (v[i] - mean)
            if (i + lag < v.size) num += (v[i] - mean) * (v[i + lag] - mean)
        }
        return if (den > 0) num / den else 0f
    }

    private fun haarWaveletEnergy(v: FloatArray): FloatArray {
        var current = v.copyOf()
        val energies = FloatArray(4)
        for (level in 0 until 4) {
            if (current.size < 2) break
            val half = current.size / 2
            val approx = FloatArray(half)
            val detail = FloatArray(half)
            for (i in 0 until half) {
                approx[i] = (current[2 * i] + current[2 * i + 1]) / kotlin.math.sqrt(2.0).toFloat()
                detail[i] = (current[2 * i] - current[2 * i + 1]) / kotlin.math.sqrt(2.0).toFloat()
            }
            energies[level] = detail.map { it * it }.average().toFloat()
            current = approx
        }
        return energies
    }
}
