package com.sih26223.app.profiler

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

enum class DeviceTier { LOW, MID, HIGH }

data class DeviceProfile(
    val tier: DeviceTier,
    val recommendedSamplingRateHz: Int,
    val useComplexAiModel: Boolean,
    val totalRamGb: Double,
    val cpuCores: Int
)

class DeviceProfiler(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceProfiler"
    }

    fun profileDevice(): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamBytes = memoryInfo.totalMem
        val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
        
        val cpuCores = Runtime.getRuntime().availableProcessors()
        
        val tier = when {
            totalRamGb < 3.0 || cpuCores <= 4 -> DeviceTier.LOW
            totalRamGb < 6.0 || cpuCores <= 6 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }
        
        val recommendedSamplingRate = when(tier) {
            DeviceTier.LOW -> 50
            DeviceTier.MID -> 100
            DeviceTier.HIGH -> 200
        }
        
        val profile = DeviceProfile(
            tier = tier,
            recommendedSamplingRateHz = recommendedSamplingRate,
            useComplexAiModel = (tier == DeviceTier.HIGH || tier == DeviceTier.MID),
            totalRamGb = totalRamGb,
            cpuCores = cpuCores
        )
        
        Log.i(TAG, "Device Profile Generated: $profile (Model: ${Build.MODEL})")
        return profile
    }
}
