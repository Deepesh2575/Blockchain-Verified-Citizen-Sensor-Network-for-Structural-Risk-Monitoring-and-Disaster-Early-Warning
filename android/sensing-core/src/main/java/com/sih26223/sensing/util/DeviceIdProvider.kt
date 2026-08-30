package com.sih26223.sensing.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class DeviceIdProvider(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "sensing_device_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val id = "did:sih26223:${UUID.randomUUID()}"
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
    }
}

object MathUtils {
    fun magnitude(v: FloatArray, count: Int = v.size): Float {
        var sum = 0f
        for (i in 0 until count) sum += v[i] * v[i]
        return kotlin.math.sqrt(sum)
    }

    fun mean(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        return values.sum() / values.size
    }

    fun std(values: FloatArray): Float {
        if (values.size < 2) return 0f
        val m = mean(values)
        var sum = 0f
        for (v in values) {
            val d = v - m
            sum += d * d
        }
        return kotlin.math.sqrt(sum / (values.size - 1))
    }
}

object HashUtils {
    fun sha256Hex(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
