package com.sih26223.sensing.model

/**
 * Edge AI model tier — selected by [DeviceCapabilityReport] + battery mode.
 */
enum class AnomalyModelSize {
    /** ~15 KB INT8 — 12 hand-crafted features, logistic / tiny MLP */
    TINY,
    /** ~180 KB INT8 — 32 features, 1D-CNN on magnitude window */
    SMALL,
    /** ~1.2 MB FP16 — 64 features + 256-sample spectrogram patch, CNN-LSTM */
    MEDIUM
}

data class AnomalyModelConfig(
    val size: AnomalyModelSize,
    val assetPath: String,
    val windowSamples: Int,
    val hopSamples: Int,
    val sampleRateHz: Float,
    val inputFeatureDim: Int,
    val useRawWindow: Boolean,
    val useSpectrogram: Boolean,
    val maxInferenceMs: Long,
    val scoreThreshold: Float
)

data class VibrationFeatureVector(
    val timestampMs: Long,
    val values: FloatArray,
    val featureNames: List<String>,
    val rawWindow: FloatArray? = null,
    val spectrogramPatch: Array<FloatArray>? = null
) {
    override fun equals(other: Any?) = other is VibrationFeatureVector && values.contentEquals(other.values)
    override fun hashCode() = values.contentHashCode()
}

/**
 * Pipeline output consumed by [ObservationEvent] and blockchain anchoring.
 */
data class VibrationAnomalyResult(
    val timestampMs: Long,
    val anomalyScore: Float,       // 0..1 — P(anomaly)
    val confidence: Float,         // 0..1 — model certainty (calibrated)
    val modelSize: AnomalyModelSize,
    val modelVersion: String,
    val inferenceLatencyMs: Long,
    val topContributingFeatures: List<FeatureContribution> = emptyList(),
    val isAnomaly: Boolean = anomalyScore >= 0.5f
)

data class FeatureContribution(
    val name: String,
    val value: Float,
    val contribution: Float  // SHAP-like or gradient-free attribution
)
