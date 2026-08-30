package com.sih26223.sensing.ml

import android.content.Context
import com.sih26223.sensing.model.AnomalyModelConfig
import com.sih26223.sensing.model.DeviceCapabilityReport
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.model.VibrationAnomalyResult
import com.sih26223.sensing.model.VibrationFeatureVector
import com.sih26223.sensing.sensing.BatteryAwareSamplingController
import com.sih26223.sensing.sensing.SensorSampleBuffer

/**
 * End-to-end Edge AI pipeline orchestrator:
 *
 * ```
 * |a| ring buffer → Preprocess → Window → Features → TFLite → AnomalyResult
 * ```
 */
class VibrationAnomalyPipeline(
    context: Context,
    report: DeviceCapabilityReport,
    batteryController: BatteryAwareSamplingController,
    forceModelSize: com.sih26223.sensing.model.AnomalyModelSize? = null
) : AutoCloseable {

    val config: AnomalyModelConfig = AnomalyModelSelector.select(
        report = report,
        batteryMode = batteryController.currentBatteryMode(),
        forceSize = forceModelSize
    )

    private val preprocessor = VibrationPreprocessor(
        sampleRateHz = config.sampleRateHz,
        windowSamples = config.windowSamples,
        hopSamples = config.hopSamples
    )

    private val detector = TfliteVibrationAnomalyDetector(context, config)
    private val accelQuality = report.sensors[SensorKind.ACCELEROMETER]?.qualityScore ?: 0.5f

    /** Minimum samples before first inference. */
    val warmupSamples: Int = config.windowSamples

    /**
     * Process latest samples from ring buffer.
     * Call every feature window tick from [MobileSensingLayer].
     */
    fun process(
        sampleBuffer: SensorSampleBuffer,
        timestampMs: Long = System.currentTimeMillis()
    ): VibrationAnomalyResult? {
        val accelMag = sampleBuffer.peekMagnitudes(SensorKind.ACCELEROMETER, config.windowSamples * 2)
        if (accelMag.size < warmupSamples) return null

        val gyroMag = if (config.inputFeatureDim >= 32) {
            sampleBuffer.peekMagnitudes(SensorKind.GYROSCOPE, config.windowSamples)
        } else null

        val windows = preprocessor.window(accelMag)
        val latestWindow = windows.lastOrNull()?.data ?: return null

        val featureValues = VibrationFeatureExtractor.extract(
            accelWindow = latestWindow,
            sampleRateHz = config.sampleRateHz,
            gyroWindow = gyroMag?.takeIf { it.isNotEmpty() },
            sensorQuality = accelQuality,
            featureCount = config.inputFeatureDim
        )

        val featureVector = VibrationFeatureVector(
            timestampMs = timestampMs,
            values = featureValues,
            featureNames = VibrationFeatureExtractor.ALL_FEATURE_NAMES.take(config.inputFeatureDim),
            rawWindow = if (config.useRawWindow) latestWindow else null,
            spectrogramPatch = if (config.useSpectrogram) {
                VibrationFeatureExtractor.spectrogramPatch(latestWindow, config.sampleRateHz)
            } else null
        )

        return detector.infer(featureVector)
    }

    /** Re-select model when battery mode changes. Returns new config. */
    fun refreshModelSelection(
        context: Context,
        report: DeviceCapabilityReport,
        batteryController: BatteryAwareSamplingController
    ): AnomalyModelConfig {
        close()
        return VibrationAnomalyPipeline(context, report, batteryController).config
    }

    override fun close() {
        detector.close()
    }
}
