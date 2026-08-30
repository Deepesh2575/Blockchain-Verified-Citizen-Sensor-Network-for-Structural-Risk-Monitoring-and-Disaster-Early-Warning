package com.sih26223.sensing.sensing

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sih26223.sensing.model.SensorAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val speedMps: Float,
    val timestampMs: Long,
    val isMock: Boolean = false
)

/**
 * Fused location provider wrapper with mock-location detection.
 */
class GnssAcquisitionManager(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val _latestFix = MutableStateFlow<GnssFix?>(null)
    val latestFix: StateFlow<GnssFix?> = _latestFix

    @Volatile
    var availability: SensorAvailability = SensorAvailability.MISSING
        private set

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long) {
        stop()
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val mock = loc.isFromMockProvider
                availability = if (mock) {
                    SensorAvailability.HARDWARE_DISABLED
                } else {
                    SensorAvailability.AVAILABLE
                }
                _latestFix.value = GnssFix(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyM = loc.accuracy,
                    speedMps = loc.speed.coerceAtLeast(0f),
                    timestampMs = loc.time,
                    isMock = mock
                )
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, null)
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
