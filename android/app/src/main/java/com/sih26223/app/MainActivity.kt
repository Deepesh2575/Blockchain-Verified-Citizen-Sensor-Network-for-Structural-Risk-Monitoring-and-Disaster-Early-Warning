package com.sih26223.app

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sih26223.sensing.network.GatewaySyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    
    private lateinit var gatewaySyncManager: GatewaySyncManager
    private val deviceId = UUID.randomUUID().toString()
    
    private var isSensing = false
    private val sensorBuffer = FloatArray(150)
    private var bufferIndex = 0
    private var lastUiUpdateTime = 0L

    // UI Elements
    private lateinit var rootLayout: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var liveSensorText: TextView
    private lateinit var logConsole: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Core Library
        gatewaySyncManager = GatewaySyncManager(this)
        
        // Setup Hardware Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupProgrammaticUI()
    }

    private fun setupProgrammaticUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
            setBackgroundColor(Color.parseColor("#121212")) // Dark mode
        }

        val title = TextView(this).apply {
            text = "SIH 2026 - Edge Sensor Node"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        statusText = TextView(this).apply {
            text = "Status: OFFLINE"
            textSize = 18f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        liveSensorText = TextView(this).apply {
            text = "X: 0.0 | Y: 0.0 | Z: 0.0\nMagnitude: 0.0"
            textSize = 16f
            setTextColor(Color.parseColor("#03DAC5")) // Cyan accent
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, 64)
        }

        startButton = Button(this).apply {
            text = "START LIVE SENSING"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener { toggleSensing() }
        }

        val logTitle = TextView(this).apply {
            text = "Live AI Inference Logs:"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 64, 0, 16)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f // takes remaining space
            )
        }

        logConsole = TextView(this).apply {
            text = "System Initialized...\n"
            textSize = 12f
            setTextColor(Color.parseColor("#00FF00")) // Hacker green
            typeface = android.graphics.Typeface.MONOSPACE
        }

        scrollView.addView(logConsole)

        rootLayout.addView(title)
        rootLayout.addView(statusText)
        rootLayout.addView(liveSensorText)
        rootLayout.addView(startButton)
        rootLayout.addView(logTitle)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    private fun toggleSensing() {
        if (isSensing) {
            stopSensing()
        } else {
            startSensing()
        }
    }

    private fun startSensing() {
        if (accelerometer == null) {
            log("ERROR: No accelerometer hardware found on this device!")
            return
        }
        
        isSensing = true
        bufferIndex = 0
        startButton.text = "STOP SENSING"
        startButton.setBackgroundColor(Color.parseColor("#F44336")) // Red
        statusText.text = "Status: LISTENING (50Hz)"
        statusText.setTextColor(Color.GREEN)
        
        // Register sensor at SENSOR_DELAY_GAME (~50Hz)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        log("Started physical hardware accelerometer at 50Hz.")
    }

    private fun stopSensing() {
        isSensing = false
        startButton.text = "START LIVE SENSING"
        startButton.setBackgroundColor(Color.parseColor("#4CAF50")) // Green
        statusText.text = "Status: OFFLINE"
        statusText.setTextColor(Color.GRAY)
        liveSensorText.text = "X: 0.0 | Y: 0.0 | Z: 0.0\nMagnitude: 0.0"
        
        sensorManager.unregisterListener(this)
        log("Stopped physical hardware accelerometer.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isSensing || event == null) return

        // Calculate vector magnitude from X, Y, Z to remove orientation bias
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        
        // Subtract gravity (approx 9.81) for pure kinetic force
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - 9.81f
        
        // Update UI at approx 10Hz to prevent freezing the main thread
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUiUpdateTime > 100) {
            lastUiUpdateTime = currentTime
            runOnUiThread {
                liveSensorText.text = String.format("X: %.2f | Y: %.2f | Z: %.2f\nMag: %.2f G", x, y, z, magnitude)
            }
        }
        
        // Add to our 150-tick rolling buffer
        sensorBuffer[bufferIndex] = magnitude
        bufferIndex++

        // Once we hit 150 readings (approx 3 seconds at 50Hz)
        if (bufferIndex >= 150) {
            val bufferCopy = sensorBuffer.copyOf()
            bufferIndex = 0 // Reset buffer
            
            // Log to UI occasionally to show it's working
            if (Math.random() < 0.1) {
                 log("Buffer filled. Running TFLite inference...")
            }

            // Run AI Inference in a background coroutine
            CoroutineScope(Dispatchers.IO).launch {
                gatewaySyncManager.processSensorStream(deviceId, bufferCopy)
                
                // If it was a massive shake, log it to the UI and flash screen red
                val maxVib = bufferCopy.maxOrNull() ?: 0f
                if (maxVib > 5.0f) {
                    withContext(Dispatchers.Main) {
                        log("⚠️ CRITICAL VIBRATION DETECTED! Syncing to Gateway...")
                        logConsole.setTextColor(Color.RED)
                        triggerRedFlash()
                    }
                }
            }
        }
    }
    
    private fun triggerRedFlash() {
        rootLayout.setBackgroundColor(Color.parseColor("#440000")) // Dark red background
        rootLayout.postDelayed({
            rootLayout.setBackgroundColor(Color.parseColor("#121212")) // Revert to dark mode
        }, 500)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun log(message: String) {
        runOnUiThread {
            val currentText = logConsole.text.toString()
            // Keep log size manageable
            val newText = if (currentText.length > 2000) {
                currentText.substring(currentText.length - 1000)
            } else {
                currentText
            }
            logConsole.text = "$newText\n> $message"
            logConsole.setTextColor(Color.parseColor("#00FF00")) // Reset to green
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isSensing) {
            stopSensing()
        }
    }
}
