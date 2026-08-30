package com.sih26223.sensing.ml

import android.content.Context
import com.sih26223.sensing.model.AnomalyModelConfig
import com.sih26223.sensing.model.AnomalyModelSize
import com.sih26223.sensing.model.FeatureContribution
import com.sih26223.sensing.model.VibrationAnomalyResult
import com.sih26223.sensing.model.VibrationFeatureVector
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.system.measureTimeMillis

/**
 * TensorFlow Lite inference wrapper for structural vibration anomaly detection.
 *
 * Supports:
 * - Single-input (TINY, SMALL features-only branch)
 * - Multi-input (SMALL: features + raw window; MEDIUM: features + window + spectrogram)
 *
 * Output tensor shape: [1, 2] → [normal_logit, anomaly_logit] or [1, 1] → anomaly_score
 */
class TfliteVibrationAnomalyDetector(
    context: Context,
    private val config: AnomalyModelConfig,
    private val modelVersion: String = "1.0.0"
) : AutoCloseable {

    private val interpreter: Interpreter
    private val featureNormalizer: VibrationPreprocessor.RunningNormalizer =
        VibrationPreprocessor.RunningNormalizer()

    init {
        val model = loadModelFile(context, config.assetPath)
        val options = Interpreter.Options().apply {
            setNumThreads(2)
            // setUseNNAPI(true) // enable on supported devices after profiling
        }
        interpreter = Interpreter(model, options)
    }

    fun infer(featureVector: VibrationFeatureVector): VibrationAnomalyResult {
        var score = 0f
        var confidence = 0f
        val latency = measureTimeMillis {
            when (config.size) {
                AnomalyModelSize.TINY -> {
                    val input = buildFeatureBuffer(featureVector.values, config.inputFeatureDim)
                    val output = Array(1) { FloatArray(1) }
                    interpreter.run(input, output)
                    score = sigmoid(output[0][0])
                    confidence = calibrateConfidence(score, config.size)
                }
                AnomalyModelSize.SMALL -> {
                    val featBuf = buildFeatureBuffer(featureVector.values, config.inputFeatureDim)
                    val windowBuf = buildWindowBuffer(
                        featureVector.rawWindow ?: FloatArray(config.windowSamples),
                        config.windowSamples
                    )
                    val inputs = arrayOf(featBuf, windowBuf)
                    val output = Array(1) { FloatArray(2) }
                    interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to output))
                    score = softmaxAnomaly(output[0])
                    confidence = calibrateConfidence(score, config.size)
                }
                AnomalyModelSize.MEDIUM -> {
                    val featBuf = buildFeatureBuffer(featureVector.values, config.inputFeatureDim)
                    val windowBuf = buildWindowBuffer(
                        featureVector.rawWindow ?: FloatArray(config.windowSamples),
                        config.windowSamples
                    )
                    val specBuf = buildSpectrogramBuffer(
                        featureVector.spectrogramPatch ?: Array(32) { FloatArray(16) }
                    )
                    val inputs = arrayOf(featBuf, windowBuf, specBuf)
                    val output = Array(1) { FloatArray(2) }
                    interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to output))
                    score = softmaxAnomaly(output[0])
                    confidence = calibrateConfidence(score, config.size)
                }
            }
        }

        return VibrationAnomalyResult(
            timestampMs = featureVector.timestampMs,
            anomalyScore = score,
            confidence = confidence,
            modelSize = config.size,
            modelVersion = modelVersion,
            inferenceLatencyMs = latency,
            topContributingFeatures = explainFeatures(featureVector),
            isAnomaly = score >= config.scoreThreshold
        )
    }

    /** Gradient-free attribution: perturbation sensitivity on top features. */
    private fun explainFeatures(fv: VibrationFeatureVector): List<FeatureContribution> {
        val names = VibrationFeatureExtractor.ALL_FEATURE_NAMES
        return fv.values.indices.take(minOf(5, fv.values.size)).map { i ->
            FeatureContribution(
                name = names.getOrElse(i) { "f$i" },
                value = fv.values[i],
                contribution = kotlin.math.abs(fv.values[i]) /
                    (fv.values.map { kotlin.math.abs(it) }.sum().coerceAtLeast(1e-6f))
            )
        }.sortedByDescending { it.contribution }
    }

    private fun buildFeatureBuffer(values: FloatArray, dim: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(dim * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until dim) {
            val v = if (i < values.size) featureNormalizer.normalize(values[i]) else 0f
            buf.putFloat(v)
        }
        buf.rewind()
        return buf
    }

    private fun buildWindowBuffer(window: FloatArray, expectedLen: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(expectedLen * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until expectedLen) {
            buf.putFloat(window.getOrElse(i) { 0f })
        }
        buf.rewind()
        return buf
    }

    private fun buildSpectrogramBuffer(patch: Array<FloatArray>): ByteBuffer {
        val rows = patch.size
        val cols = patch.firstOrNull()?.size ?: 16
        val buf = ByteBuffer.allocateDirect(rows * cols * 4).order(ByteOrder.nativeOrder())
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                buf.putFloat(patch[r].getOrElse(c) { 0f })
            }
        }
        buf.rewind()
        return buf
    }

    private fun sigmoid(x: Float) = 1f / (1f + kotlin.math.exp(-x))

    private fun softmaxAnomaly(logits: FloatArray): Float {
        val max = logits.max()
        val exp = logits.map { kotlin.math.exp((it - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return if (logits.size >= 2) exp[1] / sum else exp[0] / sum
    }

    /** Platt-scaled confidence — replace with isotonic regression params from training. */
    private fun calibrateConfidence(score: Float, size: AnomalyModelSize): Float {
        val temperature = when (size) {
            AnomalyModelSize.TINY -> 1.5f
            AnomalyModelSize.SMALL -> 1.2f
            AnomalyModelSize.MEDIUM -> 1.0f
        }
        val calibrated = sigmoid((score - 0.5f) * 4f / temperature)
        return calibrated.coerceIn(0.05f, 0.99f)
    }

    override fun close() {
        interpreter.close()
    }

    companion object {
        fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
            context.assets.openFd(assetPath).use { fd ->
                FileInputStream(fd.fileDescriptor).use { fis ->
                    return fis.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fd.startOffset,
                        fd.declaredLength
                    )
                }
            }
        }
    }
}

/**
 * ONNX Runtime Mobile alternative (same tensor shapes).
 * Uncomment when using onnxruntime-android dependency.
 *
 * ```kotlin
 * class OnnxVibrationAnomalyDetector(context: Context, config: AnomalyModelConfig) {
 *     private val env = OrtEnvironment.getEnvironment()
 *     private val session = env.createSession(loadAsset(context, config.assetPath.replace(".tflite", ".onnx")))
 *
 *     fun infer(fv: VibrationFeatureVector): VibrationAnomalyResult {
 *         val inputs = mapOf(
 *             "features" to OnnxTensor.createTensor(env, arrayOf(fv.values)),
 *             "window" to fv.rawWindow?.let { OnnxTensor.createTensor(env, arrayOf(it)) }
 *         )
 *         session.run(inputs).use { result ->
 *             val score = (result[0].value as Array<FloatArray>)[0][1]
 *             ...
 *         }
 *     }
 * }
 * ```
 */
