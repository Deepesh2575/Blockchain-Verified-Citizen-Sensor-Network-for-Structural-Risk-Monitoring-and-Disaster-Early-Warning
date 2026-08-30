package com.sih26223.sensing.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class VibrationFeatureExtractorTest {

    @Test
    fun rms_of_unit_sine_is_consistent() {
        val n = 256
        val signal = FloatArray(n) { i -> sin(2.0 * Math.PI * 5.0 * i / 50.0).toFloat() }
        val features = VibrationFeatureExtractor.extract(
            accelWindow = signal,
            sampleRateHz = 50f,
            featureCount = 12
        )
        assertTrue("RMS should be positive", features[0] > 0.1f)
        assertTrue("Peak should be ≤ 1.2", features[1] <= 1.2f)
    }

    @Test
    fun tiny_model_uses_12_features() {
        val signal = FloatArray(128) { (Math.random() * 0.1).toFloat() }
        val features = VibrationFeatureExtractor.extract(signal, 50f, featureCount = 12)
        assertEquals(12, features.size)
    }

    @Test
    fun preprocessor_produces_overlapping_windows() {
        val pre = VibrationPreprocessor(sampleRateHz = 50f, windowSamples = 128, hopSamples = 64)
        val signal = FloatArray(300) { i -> sin(2.0 * Math.PI * 2.0 * i / 50.0).toFloat() }
        val windows = pre.window(signal)
        assertTrue(windows.size >= 2)
        assertEquals(128, windows.first().data.size)
    }

    @Test
    fun zero_signal_has_low_energy() {
        val signal = FloatArray(128) { 0f }
        val features = VibrationFeatureExtractor.extract(signal, 50f, featureCount = 12)
        assertTrue(features[0] < 0.01f)  // RMS
        assertTrue(features[11] < 0.01f) // Energy
    }
}
