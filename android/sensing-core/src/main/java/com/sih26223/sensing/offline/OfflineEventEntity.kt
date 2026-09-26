package com.sih26223.sensing.offline

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_events")
data class OfflineEventEntity(
    @PrimaryKey val eventId: String,
    val payloadJson: String,
    val timestampMs: Long
)
