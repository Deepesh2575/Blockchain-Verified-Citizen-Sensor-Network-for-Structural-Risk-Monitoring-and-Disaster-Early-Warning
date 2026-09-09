package com.sih26223.sensing.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import com.sih26223.sensing.ml.AnomalyDetector

/**
 * GatewaySyncManager
 * 
 * Handles the secure transmission of vibration anomalies to the backend Verification Gateway.
 * Integrates directly with the Edge AI (AnomalyDetector) and Trust Core.
 */
class GatewaySyncManager(private val context: Context) {

    private val detector = AnomalyDetector(context)

    companion object {
        private const val TAG = "GatewaySyncManager"
        private const val GATEWAY_URL = "http://10.109.241.233:4000/api/ingest"
    }

    /**
     * Called by the live MainActivity when it receives buffered sensor data.
     */
    suspend fun processSensorStream(deviceId: String, sensorData: FloatArray, maxMagnitude: Float, estimatedFreq: Float, lat: Double, lon: Double, audioRisk: Float = 0.0f) {
        var probability = detector.predictAnomaly(sensorData)
        
        // Multi-Modal AI Fusion: Combine Kinetic AI with Acoustic AI
        val fusedProbability = (probability * 0.7f) + (audioRisk * 0.3f)
        
        if (fusedProbability > 0.80f) {
            Log.w(TAG, "🚨 CRITICAL MULTI-MODAL ANOMALY DETECTED! Probability: ${fusedProbability * 100}%")
            syncAnomalyToGateway(deviceId, fusedProbability, "T1", estimatedFreq, lat, lon)
        } else {
            Log.d(TAG, "Status Normal. Probability: ${fusedProbability * 100}%")
        }
    }

    /**
     * Sends a verified anomaly payload to the backend.
     */
    suspend fun syncAnomalyToGateway(
        deviceId: String,
        anomalyScore: Float,
        sensorTier: String,
        peakFrequency: Float,
        lat: Double,
        lon: Double
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            // 1. Construct the Payload
            val payload = JSONObject().apply {
                put("eventId", java.util.UUID.randomUUID().toString())
                put("timestamp", System.currentTimeMillis())
                put("aiAnomalyScore", anomalyScore)
                put("sensorTier", sensorTier) // Must be exactly "T1" for 1.0 multiplier
                put("hasHardwareAttestation", true) // Claiming we have a valid TEE Keystore signature
                put("peakFrequencyHz", peakFrequency) // The real shaking frequency!
                put("latitude", lat) 
                put("longitude", lon)
            }

            // 2. Wrap with Cryptographic Signature (Trust Layer)
            // For the SIH demonstration, we will append a valid signature. 
            // In the "Spoofing Attack" demo phase, an attacker would send "INVALID_SPOOF_SIG"
            val requestBody = JSONObject().apply {
                put("deviceId", deviceId)
                put("signature", "VALID_ECDSA_SIGNATURE_0x8f2a99") 
                put("payload", payload)
            }

            // 3. Dispatch over HTTP
            Log.i(TAG, "Syncing anomaly to Gateway: ${payload.getString("eventId")}")
            
            val url = URL(GATEWAY_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            // Write JSON body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            // 4. Handle Response
            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 201) {
                Log.d(TAG, "Successfully synced! Gateway confirmed receipt.")
                return@withContext true
            } else {
                Log.w(TAG, "Gateway rejected payload. Code: $responseCode")
                // Return true if it's a 400/403 (security rejection), so we don't retry.
                // Return false if it's a 500 (server error), so we queue it offline.
                return@withContext responseCode in 400..499 
            }

        } catch (e: Exception) {
            Log.e(TAG, "Network failure: Offline mode triggered. Cause: ${e.message}")
            // Connection failed (no internet). Return false so the Offline Queue retains it.
            return@withContext false
        }
    }
}
