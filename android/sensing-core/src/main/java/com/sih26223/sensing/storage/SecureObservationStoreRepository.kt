package com.sih26223.sensing.storage

import android.content.Context
import androidx.room.Room
import com.sih26223.sensing.model.ObservationEvent
import com.sih26223.sensing.util.HashUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject

/**
 * Encrypted, size-bounded observation queue.
 * Evicts lowest-priority oldest events when [maxBytes] exceeded.
 */
class SecureObservationStore(
    context: Context,
    passphrase: ByteArray,
    private val maxBytes: Long = 5L * 1024 * 1024, // 5 MB default
    private val maxEvents: Int = 2_000
) {
    private val mutex = Mutex()

    private val db: SecureObservationDatabase = Room.databaseBuilder(
        context.applicationContext,
        SecureObservationDatabase::class.java,
        "sensing_observations.db"
    )
        .openHelperFactory(SupportOpenHelperFactory(passphrase))
        .fallbackToDestructiveMigration()
        .build()

    private val dao = db.observationDao()

    suspend fun enqueue(event: ObservationEvent, priority: Int = priorityFor(event)) {
        mutex.withLock {
            val json = eventToJson(event)
            val bytes = json.toByteArray(Charsets.UTF_8)
            val entity = ObservationEntity(
                id = event.id,
                createdAtEpochMs = event.createdAtEpochMs,
                priority = priority,
                eventType = event.eventType,
                confidence = event.confidence,
                payloadJson = json,
                payloadHashHex = event.payloadHashHex,
                sizeBytes = bytes.size
            )
            dao.insert(entity)
            enforceLimits()
        }
    }

    suspend fun pendingBatch(limit: Int = 50): List<ObservationEntity> = dao.pendingBatch(limit)

    suspend fun markAcked(ids: List<String>) {
        dao.markAcked(ids)
    }

    suspend fun storageStats(): StorageStats {
        return StorageStats(
            totalBytes = dao.totalSizeBytes(),
            eventCount = dao.count(),
            maxBytes = maxBytes
        )
    }

    private suspend fun enforceLimits() {
        while (dao.totalSizeBytes() > maxBytes || dao.count() > maxEvents) {
            val toEvict = dao.oldestLowPriorityIds(count = 10)
            if (toEvict.isEmpty()) break
            dao.deleteByIds(toEvict)
        }
    }

    private fun priorityFor(event: ObservationEvent): Int = when (event.eventType) {
        "PRESSURE_DROP", "VIBRATION_ANOMALY", "STRUCTURAL_VIBRATION" -> 1
        else -> 5
    }

    private fun eventToJson(event: ObservationEvent): String {
        val f = event.features
        return JSONObject().apply {
            put("id", event.id)
            put("createdAt", event.createdAtEpochMs)
            put("eventType", event.eventType)
            put("confidence", event.confidence.toDouble())
            put("payloadHash", event.payloadHashHex)
            put("tier", event.tierAtCapture.name)
            put("features", JSONObject().apply {
                put("timestampMs", f.timestampMs)
                put("accelMagMean", f.accelMagnitudeMean.toDouble())
                put("accelMagStd", f.accelMagnitudeStd.toDouble())
                put("accelJerkRms", f.accelJerkRms.toDouble())
                put("gyroMagMean", f.gyroMagnitudeMean.toDouble())
                put("gyroMagStd", f.gyroMagnitudeStd.toDouble())
                put("vibrationLow", f.vibrationEnergyLowBand.toDouble())
                put("vibrationMid", f.vibrationEnergyMidBand.toDouble())
                put("baroHpa", f.baroPressureHpa?.toDouble())
                put("baroDelta", f.baroDeltaHpaPerMin?.toDouble())
                put("magUt", f.magFieldStrengthUt?.toDouble())
                put("lat", f.latitude)
                put("lon", f.longitude)
                put("gpsAcc", f.gpsAccuracyM?.toDouble())
                put("speed", f.speedMps?.toDouble())
            })
        }.toString()
    }

    companion object {
        fun derivePassphrase(context: Context): ByteArray {
            val master = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "sensing_db_passphrase",
                master,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            var pass = prefs.getString("db_pass", null)
            if (pass == null) {
                pass = HashUtils.sha256Hex(java.util.UUID.randomUUID().toString().toByteArray())
                prefs.edit().putString("db_pass", pass).apply()
            }
            return pass.toByteArray(Charsets.UTF_8)
        }
    }
}

data class StorageStats(
    val totalBytes: Long,
    val eventCount: Int,
    val maxBytes: Long
)
