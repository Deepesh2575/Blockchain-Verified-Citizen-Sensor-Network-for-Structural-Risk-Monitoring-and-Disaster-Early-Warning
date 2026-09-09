package com.sih26223.sensing.ml

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

class AnomalyDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    
    // Model parameters (matching train_model.py)
    private val TIME_STEPS = 150
    private val NUM_FEATURES = 3

    // Ultra-Low-Power Sleep Architecture
    private var isAwake = false
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sigMotionSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    
    init {
        try {
            val tfliteModel: MappedByteBuffer = FileUtil.loadMappedFile(context, "anomaly_model.tflite")
            val options = Interpreter.Options()
            // Optimize for battery (use fewer threads)
            options.setNumThreads(1)
            interpreter = Interpreter(tfliteModel, options)
            Log.d("AnomalyDetector", "TFLite Model loaded successfully!")
            
            // Register Hardware-Level Interrupt for Sleep Architecture
            if (sigMotionSensor != null) {
                sensorManager.requestTriggerSensor(object : TriggerEventListener() {
                    override fun onTrigger(event: TriggerEvent?) {
                        Log.d("AnomalyDetector", "Significant Motion Detected! Waking Edge AI.")
                        isAwake = true
                        // In a real app, schedule a timer to put it back to sleep after 60 seconds
                    }
                }, sigMotionSensor)
                Log.d("AnomalyDetector", "Sleep Architecture Active. Waiting for Significant Motion.")
            }
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
        // Ultra-Low-Power Sleep Check
        if (!isAwake) {
            // Prevent battery drain by not running inference during baseline normal states
            return 0f
        }

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

    // --- Phase 2: Federated Edge Learning ---
    
    fun exportLocalGradients(): ByteArray {
        // Mock: Extract updated model weights to send to the central aggregator
        Log.d("AnomalyDetector", "Exporting local model gradients for Federated Learning...")
        return ByteArray(0) 
    }

    fun importGlobalWeights(weights: ByteArray) {
        // Mock: Apply averaged weights from the swarm intelligence aggregator
        Log.d("AnomalyDetector", "Importing global weights from swarm aggregator...")
        // interpreter?.applyWeights(weights) // (Requires specific TFLite C API bindings)
    }
}
