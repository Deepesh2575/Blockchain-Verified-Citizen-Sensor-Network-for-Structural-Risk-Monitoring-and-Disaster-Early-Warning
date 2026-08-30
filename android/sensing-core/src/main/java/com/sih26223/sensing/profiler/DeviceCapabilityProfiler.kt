package com.sih26223.sensing.profiler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.sih26223.sensing.model.GnssCapability
import com.sih26223.sensing.model.SensorAvailability
import com.sih26223.sensing.util.DeviceIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * One-shot GNSS quality probe: accuracy, dual-frequency hint, quality score.
 */
class GnssCapabilityProbe(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun probe(hasLocationPermission: Boolean): GnssCapability = withContext(Dispatchers.IO) {
        if (!hasLocationPermission) {
            return@withContext GnssCapability(
                availability = SensorAvailability.PERMISSION_DENIED,
                qualityScore = 0f
            )
        }

        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = withTimeoutOrNull(8_000L) {
            try {
                Tasks.await(
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                    7,
                    TimeUnit.SECONDS
                )
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }

        if (location == null) {
            return@withContext GnssCapability(
                availability = SensorAvailability.HARDWARE_DISABLED,
                qualityScore = 0f
            )
        }

        val accuracy = location.accuracy.coerceAtLeast(1f)
        val dualFreq = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            location.extras?.getInt("satellites", 0)?.let { it >= 8 } == true

        val quality = when {
            accuracy <= 5f -> 1.0f
            accuracy <= 10f -> 0.85f
            accuracy <= 20f -> 0.65f
            accuracy <= 50f -> 0.40f
            else -> 0.20f
        }

        GnssCapability(
            availability = SensorAvailability.AVAILABLE,
            avgAccuracyMeters = accuracy,
            dualFrequencyCapable = dualFreq,
            qualityScore = quality
        )
    }
}

/**
 * Orchestrates full device profiling and produces a signed-ready [DeviceCapabilityReport].
 */
class DeviceCapabilityProfiler(
    private val context: Context,
    private val sensorProbe: SensorCapabilityProbe = SensorCapabilityProbe(context),
    private val gnssProbe: GnssCapabilityProbe = GnssCapabilityProbe(context),
    private val deviceIdProvider: DeviceIdProvider = DeviceIdProvider(context)
) {
    suspend fun profile(hasLocationPermission: Boolean): com.sih26223.sensing.model.DeviceCapabilityReport {
        val ramMb = (context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
            .let { am ->
                val info = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                (info.totalMem / (1024 * 1024)).toInt()
            }

        val sensors = sensorProbe.probeAll().toMutableMap()
        val gnss = gnssProbe.probe(hasLocationPermission)
        sensors[com.sih26223.sensing.model.SensorKind.GPS] = com.sih26223.sensing.model.SensorProbeResult(
            kind = com.sih26223.sensing.model.SensorKind.GPS,
            availability = gnss.availability,
            qualityScore = gnss.qualityScore
        )

        val tier = DeviceTierClassifier.classify(ramMb, sensors, gnss)
        val score = DeviceTierClassifier.computeCapabilityScore(tier, sensors, gnss)
        val profile = SamplingProfileFactory.forTier(tier, ramMb)
        val integrity = IntegrityChecker.probe()

        return com.sih26223.sensing.model.DeviceCapabilityReport(
            deviceId = deviceIdProvider.getOrCreateDeviceId(),
            profiledAtEpochMs = System.currentTimeMillis(),
            ramClassMb = ramMb,
            tier = tier,
            capabilityScore = score,
            sensors = sensors,
            gnss = gnss,
            integrityFlags = integrity,
            recommendedSamplingProfile = profile.name
        )
    }
}
