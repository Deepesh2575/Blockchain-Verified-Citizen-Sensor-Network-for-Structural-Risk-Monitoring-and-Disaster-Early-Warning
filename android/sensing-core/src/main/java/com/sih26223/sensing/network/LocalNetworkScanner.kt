package com.sih26223.sensing.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class LocalNetworkScanner(context: Context) {
    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveredDevices = ConcurrentHashMap<String, String>()
    
    // Common services for Smart TVs, IoT, Routers
    private val serviceTypes = listOf(
        "_googlecast._tcp", // Chromecasts, Android TVs
        "_http._tcp",       // Web interfaces, Routers, IoT hubs
        "_airplay._tcp",    // Apple TVs
        "_ipp._tcp"         // Printers
    )
    
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

    companion object {
        private const val TAG = "LocalNetworkScanner"
    }

    fun startScanning() {
        Log.i(TAG, "Starting continuous local network discovery (mDNS)...")
        serviceTypes.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "Service discovery started for: $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "Service found: ${service.serviceName} (${service.serviceType})")
                    // In a production app, we would resolve the service to get IP/Port.
                    // For footprinting, just logging the name is enough for context.
                    discoveredDevices[service.serviceName] = service.serviceType
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.e(TAG, "Service lost: ${service.serviceName}")
                    discoveredDevices.remove(service.serviceName)
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.i(TAG, "Discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                    nsdManager.stopServiceDiscovery(this)
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Discovery failed: Error code:$errorCode")
                    nsdManager.stopServiceDiscovery(this)
                }
            }
            discoveryListeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start discovery for $serviceType: ${e.message}")
            }
        }
    }

    fun stopScanning() {
        discoveryListeners.forEach { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery: ${e.message}")
            }
        }
        discoveryListeners.clear()
        discoveredDevices.clear()
    }

    fun getDiscoveredDevices(): List<String> {
        return discoveredDevices.keys.toList()
    }
}
