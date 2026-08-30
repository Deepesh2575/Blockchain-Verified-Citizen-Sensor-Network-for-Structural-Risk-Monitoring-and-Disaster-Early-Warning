package com.sih26223.sensing.ml

import com.sih26223.sensing.model.AnomalyModelConfig
import com.sih26223.sensing.model.AnomalyModelSize
import com.sih26223.sensing.model.BatteryMode
import com.sih26223.sensing.model.DeviceCapabilityReport
import com.sih26223.sensing.model.DeviceTier

/**
 * Selects Tiny / Small / Medium model from DCP report and runtime constraints.
 */
object AnomalyModelSelector {

    fun select(
        report: DeviceCapabilityReport,
        batteryMode: BatteryMode,
        forceSize: AnomalyModelSize? = null
    ): AnomalyModelConfig {
        forceSize?.let { return configFor(it) }

        val accelQuality = report.sensors[com.sih26223.sensing.model.SensorKind.ACCELEROMETER]
            ?.qualityScore ?: 0f

        return when {
            batteryMode == BatteryMode.CRITICAL -> configFor(AnomalyModelSize.TINY)
            batteryMode == BatteryMode.POWER_SAVE && report.ramClassMb < 4096 ->
                configFor(AnomalyModelSize.TINY)
            report.tier == DeviceTier.T3_BASIC || accelQuality < 0.4f ->
                configFor(AnomalyModelSize.TINY)
            report.tier == DeviceTier.T1_PREMIUM &&
                report.ramClassMb >= 6144 &&
                batteryMode != BatteryMode.POWER_SAVE ->
                configFor(AnomalyModelSize.MEDIUM)
            report.tier == DeviceTier.T2_STANDARD || report.ramClassMb >= 3072 ->
                configFor(AnomalyModelSize.SMALL)
            else -> configFor(AnomalyModelSize.TINY)
        }
    }

    fun configFor(size: AnomalyModelSize): AnomalyModelConfig = when (size) {
        AnomalyModelSize.TINY -> AnomalyModelConfig(
            size = size,
            assetPath = "models/vibration_anomaly_tiny_int8.tflite",
            windowSamples = 128,       // 2.56 s @ 50 Hz
            hopSamples = 64,
            sampleRateHz = 50f,
            inputFeatureDim = 12,
            useRawWindow = false,
            useSpectrogram = false,
            maxInferenceMs = 5L,
            scoreThreshold = 0.65f
        )
        AnomalyModelSize.SMALL -> AnomalyModelConfig(
            size = size,
            assetPath = "models/vibration_anomaly_small_int8.tflite",
            windowSamples = 256,       // 5.12 s @ 50 Hz
            hopSamples = 128,
            sampleRateHz = 50f,
            inputFeatureDim = 32,
            useRawWindow = true,       // 256-point |a| window as 2nd input (multi-input model)
            useSpectrogram = false,
            maxInferenceMs = 15L,
            scoreThreshold = 0.55f
        )
        AnomalyModelSize.MEDIUM -> AnomalyModelConfig(
            size = size,
            assetPath = "models/vibration_anomaly_medium_fp16.tflite",
            windowSamples = 512,       // 10.24 s @ 50 Hz
            hopSamples = 256,
            sampleRateHz = 50f,
            inputFeatureDim = 64,
            useRawWindow = true,
            useSpectrogram = true,     // 32×16 log-mel-ish patch
            maxInferenceMs = 40L,
            scoreThreshold = 0.50f
        )
    }
}
