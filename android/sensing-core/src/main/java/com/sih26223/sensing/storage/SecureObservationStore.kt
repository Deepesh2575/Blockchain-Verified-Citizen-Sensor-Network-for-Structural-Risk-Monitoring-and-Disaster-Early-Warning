package com.sih26223.sensing.storage

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "observation_queue")
data class ObservationEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val priority: Int,
    val eventType: String,
    val confidence: Float,
    val payloadJson: String,
    val payloadHashHex: String,
    val syncState: String = "pending",
    val sizeBytes: Int
)

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ObservationEntity)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM observation_queue")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT COUNT(*) FROM observation_queue")
    suspend fun count(): Int

    @Query("SELECT * FROM observation_queue WHERE syncState = 'pending' ORDER BY priority ASC, createdAtEpochMs ASC LIMIT :limit")
    suspend fun pendingBatch(limit: Int): List<ObservationEntity>

    @Query("UPDATE observation_queue SET syncState = 'acked' WHERE id IN (:ids)")
    suspend fun markAcked(ids: List<String>)

    @Query("DELETE FROM observation_queue WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM observation_queue ORDER BY priority DESC, createdAtEpochMs ASC LIMIT :count")
    suspend fun oldestLowPriorityIds(count: Int): List<String>
}

@Database(entities = [ObservationEntity::class], version = 1, exportSchema = false)
abstract class SecureObservationDatabase : androidx.room.Database() {
    abstract fun observationDao(): ObservationDao
}
