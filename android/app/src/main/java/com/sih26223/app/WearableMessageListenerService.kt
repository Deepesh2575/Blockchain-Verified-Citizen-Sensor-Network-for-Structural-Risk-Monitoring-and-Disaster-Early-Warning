package com.sih26223.app

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.sih26223.sensing.ml.AnomalyDetector

class WearableMessageListenerService : WearableListenerService() {

    private val TAG = "WearableListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/casualty_event") {
            val payload = String(messageEvent.data)
            Log.e(TAG, "🚨 Received Casualty Event from Smartwatch! Payload: $payload")
            
            // Integrate with AnomalyDetector to escalate the risk
            // For example, this could trigger the phone to send an emergency ping with the watch's location
            
            val parts = payload.split("|")
            var heartRate = 0f
            var lat = 0.0
            var lng = 0.0
            
            for (part in parts) {
                if (part.startsWith("HR:")) heartRate = part.removePrefix("HR:").toFloatOrNull() ?: 0f
                if (part.startsWith("LAT:")) lat = part.removePrefix("LAT:").toDoubleOrNull() ?: 0.0
                if (part.startsWith("LNG:")) lng = part.removePrefix("LNG:").toDoubleOrNull() ?: 0.0
            }
            
            Log.d(TAG, "Parsed Watch Data -> HR: $heartRate, Location: ($lat, $lng)")
            
            // Feed a high risk score anomaly to our detector based on the watch's data
            val anomalyDetector = AnomalyDetector(this)
            
            // We simulate feeding data to the ML model that triggers the network layer
            // (AnomalyDetector expects a FloatArray of 450 values)
            val mockData = FloatArray(450) { 10f } // Mock high-G data
            val score = anomalyDetector.predictAnomaly(mockData)
            Log.d(TAG, "Processed casualty risk score: $score")
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
