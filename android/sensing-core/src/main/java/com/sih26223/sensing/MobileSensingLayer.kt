package com.sih26223.sensing

import android.content.Context
import android.hardware.SensorManager
import com.sih26223.sensing.model.DeviceCapabilityReport
import com.sih26223.sensing.model.DeviceTier
import com.sih26223.sensing.model.ObservationEvent
import com.sih26223.sensing.model.SamplingProfile
import com.sih26223.sensing.model.SensorKind
import com.sih26223.sensing.profiler.DeviceCapabilityProfiler
import com.sih26223.sensing.profiler.SamplingProfileFactory
import com.sih26223.sensing.sensing.BatteryAwareSamplingController
import com.sih26223.sensing.sensing.GnssAcquisitionManager
import com.sih26223.sensing.sensing.LocalEventDetector
import com.sih26223.sensing.sensing.OrientationIndependentFeatureExtractor
import com.sih26223.sensing.sensing.SensorAcquisitionManager
import com.sih26223.sensing.sensing.SensorSampleBuffer
import com.sih26223.sensing.storage.SecureObservationStore
import com.sih26223.sensing.storage.StorageStats
import com.sih26223.sensing.util.HashUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Top-level facade combining Device Capability Profiler and Mobile Sensing Layer.
 *
 * Usage:
 * ```
 * val sensing = MobileSensingLayer.create(context, hasLocationPermission = true)
 * sensing.profileDevice()
 * sensing.start()
 * // ...
 * sensing.stop()
 * ```
 */
class MobileSensingLayer private constructor(
    private val context: Context,
    private val profiler: DeviceCapabilityProfiler,
    private val batteryController: BatteryAwareSamplingController,
    private val sampleBuffer: SensorSampleBuffer,
    private var sensorManager: SensorAcquisitionManager,
    private val gnssManager: GnssAcquisitionManager,
    private val featureExtractor: OrientationIndependentFeatureExtractor,
    private val eventDetector: LocalEventDetector,
    private val observationStore: SecureObservationStore,
    private val scope: CoroutineScope
) {
    private var capabilityReport: DeviceCapabilityReport? = null
    private var activeProfile: SamplingProfile? = null
    private var sensingJob: Job? = null
    private var profileRefreshJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _lastFeatures = MutableStateFlow<com.sih26223.sensing.model.OrientationInvariantFeatures?>(null)
    val lastFeatures: StateFlow<com.sih26223.sensing.model.OrientationInvariantFeatures?> = _lastFeatures

    suspend fun profileDevice(hasLocationPermission: Boolean = true): DeviceCapabilityReport {
        val report = profiler.profile(hasLocationPermission)
        capabilityReport = report
        activeProfile = SamplingProfileFactory.forTier(report.tier, report.ramClassMb)

        // Rebuild acquisition manager with only usable sensors (graceful missing-sensor handling)
        val usable = report.sensors
            .filterValues { it.isUsable }
            .keys
            .toSet()
        val androidSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.stop()
        sensorManager = SensorAcquisitionManager(androidSensorManager, sampleBuffer, usable)

        return report
    }

    fun start() {
        val report = capabilityReport
            ?: throw IllegalStateException("Call profileDevice() before start()")
        if (_isRunning.value) return

        val baseProfile = activeProfile ?: SamplingProfileFactory.forTier(report.tier, report.ramClassMb)
        val adjusted = batteryController.adjustProfile(baseProfile)
        activeProfile = adjusted

        sensorManager.start(adjusted)
        if (report.gnss.isUsable) {
            gnssManager.start(adjusted.gpsIntervalMs)
        }

        _isRunning.value = true
        sensingJob = scope.launch { sensingLoop(report, adjusted) }
        profileRefreshJob = scope.launch { periodicProfileRefresh() }
    }

    fun stop() {
        sensingJob?.cancel()
        profileRefreshJob?.cancel()
        sensorManager.stop()
        gnssManager.stop()
        _isRunning.value = false
    }

    fun setDisasterMode(enabled: Boolean) {
        batteryController.disasterOverride = enabled
        if (_isRunning.value) {
            // Hot-restart with new profile
            stop()
            start()
        }
    }

    suspend fun storageStats(): StorageStats = observationStore.storageStats()

    private suspend fun sensingLoop(report: DeviceCapabilityReport, profile: SamplingProfile) {
        val qualitySnapshot = report.sensors.mapValues { (_, v) -> v.qualityScore }
        while (scope.isActive) {
            val features = featureExtractor.extract(
                buffer = sampleBuffer,
                gnssFix = gnssManager.latestFix.value,
                tier = report.tier,
                qualitySnapshot = qualitySnapshot,
                enableFft = profile.enableFftFeatures
            )
            _lastFeatures.value = features

            val detection = eventDetector.detect(features)
            if (detection != null) {
                val payloadBytes = featuresToCanonicalBytes(features, detection.type)
                val event = ObservationEvent(
                    id = UUID.randomUUID().toString(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    eventType = detection.type,
                    confidence = detection.confidence,
                    features = features,
                    payloadHashHex = HashUtils.sha256Hex(payloadBytes),
                    tierAtCapture = report.tier
                )
                observationStore.enqueue(event)
            }

            delay(profile.featureWindowMs)
        }
    }

    /** Re-profile every 24 h or on significant config change. */
    private suspend fun periodicProfileRefresh() {
        while (scope.isActive) {
            delay(24 * 60 * 60 * 1000L)
            try {
                profileDevice(hasLocationPermission = true)
            } catch (_: Exception) {
                // Non-fatal; continue with existing profile
            }
        }
    }

    private fun featuresToCanonicalBytes(
        f: com.sih26223.sensing.model.OrientationInvariantFeatures,
        eventType: String
    ): ByteArray {
        // Deterministic serialization for hash anchoring
        val canonical = buildString {
            append(eventType); append('|')
            append(f.timestampMs); append('|')
            append("%.4f".format(f.accelMagnitudeMean)); append('|')
            append("%.4f".format(f.accelJerkRms)); append('|')
            append(f.baroPressureHpa?.let { "%.2f".format(it) } ?: "null"); append('|')
            append(f.latitude ?: "null"); append('|')
            append(f.longitude ?: "null")
        }
        return canonical.toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun create(context: Context): MobileSensingLayer {
            val appContext = context.applicationContext
            val androidSensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val buffer = SensorSampleBuffer(maxPerSensor = 512)

            return MobileSensingLayer(
                context = appContext,
                profiler = DeviceCapabilityProfiler(appContext),
                batteryController = BatteryAwareSamplingController(appContext),
                sampleBuffer = buffer,
                sensorManager = SensorAcquisitionManager(
                    sensorManager = androidSensorManager,
                    buffer = buffer,
                    availableSensors = emptySet()
                ),
                gnssManager = GnssAcquisitionManager(appContext),
                featureExtractor = OrientationIndependentFeatureExtractor(),
                eventDetector = LocalEventDetector(),
                observationStore = SecureObservationStore(
                    context = appContext,
                    passphrase = SecureObservationStore.derivePassphrase(appContext)
                ),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            )
        }
    }
}

/**
 * Factory that rebuilds acquisition manager with discovered sensors after profiling.
 */
suspend fun MobileSensingLayer.createAndProfile(
    context: Context,
    hasLocationPermission: Boolean
): Pair<MobileSensingLayer, DeviceCapabilityReport> {
    val layer = MobileSensingLayer.create(context)
    val report = layer.profileDevice(hasLocationPermission)
    return layer to report
}
