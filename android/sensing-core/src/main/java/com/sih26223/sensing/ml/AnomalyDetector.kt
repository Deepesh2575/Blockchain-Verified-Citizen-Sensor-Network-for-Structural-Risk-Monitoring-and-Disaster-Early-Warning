package com.sih26223.sensing.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

class AnomalyDetector(context: Context) {
    private var interpreter: Interpreter? = null
    
    // Model parameters (matching train_model.py)
    private val TIME_STEPS = 150
    private val NUM_FEATURES = 3
    
    init {
        try {
            val tfliteModel: MappedByteBuffer = FileUtil.loadMappedFile(context, "anomaly_model.tflite")
            val options = Interpreter.Options()
            // Optimize for battery (use fewer threads)
            options.setNumThreads(1)
            interpreter = Interpreter(tfliteModel, options)
            Log.d("AnomalyDetector", "TFLite Model loaded successfully!")
        } catch (e: Exception) {
            Log.e("AnomalyDetector", "Error reading model", e)
        }
    }

    /**
     * Pass 3 seconds (150 samples) of accelerometer data through the Edge AI model.
     * @param sensorData A flat FloatArray of size 150 * 3 = 450
     * @return Probability (0.0 to 1.0) that the structure is experiencing an anomaly
     */
    fun predictAnomaly(sensorData: FloatArray): Float {
        if (interpreter == null) {
            Log.e("AnomalyDetector", "Interpreter not initialized")
            return 0f
        }

        if (sensorData.size != TIME_STEPS * NUM_FEATURES) {
            Log.e("AnomalyDetector", "Invalid input shape. Expected 450 floats.")
            return 0f
        }

        // Prepare Input Buffer (Float32 = 4 bytes)
        val inputBuffer = ByteBuffer.allocateDirect(TIME_STEPS * NUM_FEATURES * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        for (value in sensorData) {
            inputBuffer.putFloat(value)
        }

        // Prepare Output Buffer (1 Float probability)
        val outputBuffer = ByteBuffer.allocateDirect(4)
        outputBuffer.order(ByteOrder.nativeOrder())

        try {
            // Run Inference (1D-CNN)
            interpreter?.run(inputBuffer, outputBuffer)
            
            outputBuffer.rewind()
            val probability = outputBuffer.float
            
            Log.d("AnomalyDetector", "Prediction Score: $probability")
            return probability
            
        } catch (e: Exception) {
            Log.e("AnomalyDetector", "Inference Failed", e)
            return 0f
        }
    }
    
    fun close() {
        interpreter?.close()
    }
}
