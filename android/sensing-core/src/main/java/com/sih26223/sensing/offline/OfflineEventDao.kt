package com.sih26223.sensing.offline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface OfflineEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: OfflineEventEntity)

    @Query("SELECT * FROM offline_events ORDER BY timestampMs ASC")
    suspend fun getAllPendingEvents(): List<OfflineEventEntity>

    @Delete
    suspend fun deleteEvent(event: OfflineEventEntity)
    
    @Query("DELETE FROM offline_events WHERE eventId = :id")
    suspend fun deleteEventById(id: String)
}
