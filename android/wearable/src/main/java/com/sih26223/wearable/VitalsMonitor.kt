package com.sih26223.wearable

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Wearable Sensor Fusion (Epic 4)
 * Monitors sudden velocity changes combined with heart rate anomalies 
 * to validate human casualty events.
 */
class VitalsMonitor(private val sensorManager: SensorManager) : SensorEventListener {
    
    private val TAG = "VitalsMonitor"
    
    private var heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var currentHeartRate: Float = 0f
    private var recentHighGEvent: Boolean = false

    fun startMonitoring() {
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        Log.d(TAG, "WearOS Vitals Monitor started.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                currentHeartRate = event.values[0]
                checkCasualtyCondition()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val gForce = Math.sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

                // If G-force exceeds 4G (major fall / impact)
                if (gForce > 4.0f) {
                    recentHighGEvent = true
                    Log.w(TAG, "High-G impact detected on WearOS!")
                }
            }
        }
    }

    private fun checkCasualtyCondition() {
        // If a massive shockwave was detected AND heart rate spikes over 160 or drops to 0
        if (recentHighGEvent && (currentHeartRate > 160f || currentHeartRate < 30f)) {
            Log.e(TAG, "🚨 HIGH PROBABILITY HUMAN CASUALTY EVENT 🚨")
            sendBleIntentToPhone()
            // Reset to avoid spam
            recentHighGEvent = false 
        }
    }

    private fun sendBleIntentToPhone() {
        Log.d(TAG, "Sending BLE Intent to primary smartphone to escalate Risk Score...")
        // Implements Android Wearable DataLayer API or direct BLE broadcast here
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored for prototype
    }
    
    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
    }
}
