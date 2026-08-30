package com.sih26223.sensing.sensing

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.sih26223.sensing.model.BatteryMode
import com.sih26223.sensing.model.SamplingProfile

/**
 * Adjusts sampling rates based on battery level, charging state, and disaster override.
 */
class BatteryAwareSamplingController(private val context: Context) {

    @Volatile
    var disasterOverride: Boolean = false

    fun currentBatteryMode(): BatteryMode {
        if (disasterOverride) return BatteryMode.DISASTER

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryMode.NORMAL

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (100f * level / scale) else 100f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return when {
            charging -> BatteryMode.NORMAL
            pct <= 10f -> BatteryMode.CRITICAL
            pct <= 20f -> BatteryMode.POWER_SAVE
            else -> BatteryMode.NORMAL
        }
    }

    fun adjustProfile(base: SamplingProfile): SamplingProfile {
        return when (currentBatteryMode()) {
            BatteryMode.DISASTER -> base.copy(
                accelHz = minOf(base.accelHz * 1.5f, 100f),
                gyroHz = minOf(base.gyroHz * 1.5f, 100f),
                baroHz = minOf(base.baroHz * 2f, 10f),
                gpsIntervalMs = (base.gpsIntervalMs * 0.5).toLong().coerceAtLeast(3_000L)
            )
            BatteryMode.NORMAL -> base
            BatteryMode.POWER_SAVE -> base.copy(
                accelHz = base.accelHz * 0.5f,
                gyroHz = base.gyroHz * 0.5f,
                baroHz = base.baroHz * 0.5f,
                magHz = base.magHz * 0.5f,
                gpsIntervalMs = base.gpsIntervalMs * 2,
                enableFftFeatures = false
            )
            BatteryMode.CRITICAL -> base.copy(
                accelHz = base.accelHz * 0.25f,
                gyroHz = 0f,
                baroHz = if (base.baroHz > 0) 0.5f else 0f,
                magHz = 0f,
                gpsIntervalMs = base.gpsIntervalMs * 4,
                enableFftFeatures = false,
                featureWindowMs = base.featureWindowMs * 2
            )
        }
    }
}
