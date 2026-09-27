package com.sih26223.sensing.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
@JvmSuppressWildcards
interface OfflineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OfflineEventEntity): Long

    @Query("SELECT * FROM offline_events ORDER BY timestampMs ASC")
    suspend fun getAllPendingEvents(): List<OfflineEventEntity>

    @Delete
    suspend fun deleteEvent(event: OfflineEventEntity): Int
    
    @Query("DELETE FROM offline_events WHERE eventId = :id")
    suspend fun deleteEventById(id: String): Int
}
