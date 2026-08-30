# Device Capability Profiler & Mobile Sensing Layer

Android library module (`sensing-core`) for SIH26223 — blockchain-verified citizen sensor network.

**Min SDK:** 26 (Android 8.0) · **Target:** low-end (2–3 GB RAM) through high-end devices

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     MobileSensingLayer (facade)                  │
├─────────────────────────────────────────────────────────────────┤
│  DeviceCapabilityProfiler          │  Mobile Sensing Pipeline   │
│  ├─ SensorCapabilityProbe          │  ├─ BatteryAwareSampling   │
│  ├─ GnssCapabilityProbe            │  ├─ SensorAcquisitionMgr   │
│  ├─ SensorQualityScorer            │  ├─ GnssAcquisitionMgr    │
│  ├─ DeviceTierClassifier           │  ├─ OrientationInvariant   │
│  └─ SamplingProfileFactory         │  │   FeatureExtractor      │
│                                    │  ├─ LocalEventDetector     │
│                                    │  └─ SecureObservationStore │
└─────────────────────────────────────────────────────────────────┘
```

---

## Class Structure

| Package | Class | Responsibility |
|---------|-------|----------------|
| `model` | `DeviceCapabilityReport`, `SamplingProfile`, `OrientationInvariantFeatures` | Immutable data contracts |
| `profiler` | `DeviceCapabilityProfiler` | Orchestrates one-shot / periodic profiling |
| `profiler` | `SensorCapabilityProbe` | Hardware probe + 2 s calibration window |
| `profiler` | `SensorQualityScorer` | Noise / rate / resolution → 0..1 score |
| `profiler` | `DeviceTierClassifier` | RAM + sensor suite → T1/T2/T3 |
| `profiler` | `SamplingProfileFactory` | Tier → default Hz, window, buffer limits |
| `sensing` | `BatteryAwareSamplingController` | Battery mode → rate scaling |
| `sensing` | `SensorAcquisitionManager` | Registers only usable sensors |
| `sensing` | `GnssAcquisitionManager` | Fused location + mock detection |
| `sensing` | `OrientationIndependentFeatureExtractor` | \|a\|, \|ω\|, jerk, band energy |
| `sensing` | `LocalEventDetector` | On-device threshold events |
| `storage` | `SecureObservationStore` | SQLCipher Room queue, 5 MB cap |
| root | `MobileSensingLayer` | Public API facade |

---

## Device Tiering

| Tier | RAM | Sensors | Sampling | FFT |
|------|-----|---------|----------|-----|
| **T1_PREMIUM** | ≥ 4 GB | Baro + IMU + GNSS ≤ 25 m | Accel 50–100 Hz, Baro 5 Hz | Yes (≥ 4 GB) |
| **T2_STANDARD** | ≥ 2 GB | IMU + GNSS | Accel 50 Hz, Baro 1 Hz | No |
| **T3_BASIC** | Any | Partial / low quality | Accel 25 Hz, GPS 30 s | No |

---

## Orientation Independence

Axis components (x, y, z) vary with phone rotation. We use:

- **Accelerometer / gyro / magnetometer:** vector magnitude `\|v\| = sqrt(x² + y² + z²)`
- **Jerk:** RMS of successive `\|a\|` differences (rotation-invariant impulsive proxy)
- **Barometer:** absolute hPa + ΔhPa/min
- **GPS:** lat/lon/accuracy/speed (frame-independent)

Optional band-energy features use a lightweight DFT on `\|a\|` windows — disabled on T2/T3 and under power-save.

---

## Battery-Aware Sampling

| Mode | Trigger | Effect |
|------|---------|--------|
| `NORMAL` | Battery > 20% or charging | Base profile |
| `POWER_SAVE` | Battery ≤ 20% | 50% rates, FFT off |
| `CRITICAL` | Battery ≤ 10% | 25% rates, gyro/mag off |
| `DISASTER` | User / SDMA override | 1.5× rates, min GPS 3 s |

---

## Missing Sensor Handling

1. `SensorCapabilityProbe` returns `SensorAvailability.MISSING` — never crashes.
2. `SensorProbeResult.isUsable` requires `qualityScore ≥ 0.3`.
3. `profileDevice()` rebuilds `SensorAcquisitionManager` with only usable sensors.
4. `SamplingProfile` sets Hz = 0 for absent sensors (e.g. T3 has no baro).
5. Feature extractor returns `null` for baro/GPS fields when unavailable.

---

## Secure Storage

- **SQLCipher**-encrypted Room DB (`SecureObservationStore`)
- Passphrase in **EncryptedSharedPreferences**
- **5 MB** default cap + **2 000** event max
- LRU eviction: lowest priority, oldest first
- Priority 1 = hazard events; 5 = routine

---

## Integration Example

```kotlin
class SensingForegroundService : Service() {
    private lateinit var sensing: MobileSensingLayer

    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            val (layer, report) = createAndProfile(
                context = this@SensingForegroundService,
                hasLocationPermission = hasFineLocation()
            )
            sensing = layer
            Log.i("DCP", "Tier=${report.tier} score=${report.capabilityScore}")
            sensing.start()
        }
    }

    fun onDisasterDeclared() = sensing.setDisasterMode(true)

    override fun onDestroy() {
        sensing.stop()
        super.onDestroy()
    }
}
```

---

## File Layout

```
android/sensing-core/
├── build.gradle.kts
└── src/main/java/com/sih26223/sensing/
    ├── MobileSensingLayer.kt
    ├── model/SensingModels.kt
    ├── profiler/
    │   ├── DeviceCapabilityProfiler.kt
    │   ├── DeviceTierClassifier.kt
    │   └── SensorCapabilityProbe.kt
    ├── sensing/
    │   ├── BatteryAwareSamplingController.kt
    │   ├── GnssAcquisitionManager.kt
    │   ├── OrientationIndependentFeatureExtractor.kt
    │   └── SensorAcquisitionManager.kt
    ├── storage/
    │   ├── SecureObservationStore.kt
    │   └── SecureObservationStoreRepository.kt
    └── util/DeviceIdProvider.kt
```
