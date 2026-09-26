package com.sih26223.wearable

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class VitalsService : Service() {

    private val TAG = "VitalsService"
    private lateinit var vitalsMonitor: VitalsMonitor
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        vitalsMonitor = VitalsMonitor(sensorManager) { heartRate ->
            handleCasualtyEvent(heartRate)
        }
        
        vitalsMonitor.startMonitoring()
        Log.d(TAG, "VitalsService created and monitoring started.")
    }

    private fun handleCasualtyEvent(heartRate: Float) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try to get location
                val location: Location? = try {
                    fusedLocationClient.lastLocation.await()
                } catch (e: SecurityException) {
                    null
                }

                val lat = location?.latitude ?: 0.0
                val lng = location?.longitude ?: 0.0
                
                val payload = "HR:${heartRate}|LAT:${lat}|LNG:${lng}"
                
                // Get nodes and send message to phone
                val nodeClient = Wearable.getNodeClient(this@VitalsService)
                val nodes = nodeClient.connectedNodes.await()
                
                val messageClient = Wearable.getMessageClient(this@VitalsService)
                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/casualty_event", payload.toByteArray()).await()
                    Log.d(TAG, "Sent casualty event to node: ${node.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending casualty event: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vitalsMonitor.stopMonitoring()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
