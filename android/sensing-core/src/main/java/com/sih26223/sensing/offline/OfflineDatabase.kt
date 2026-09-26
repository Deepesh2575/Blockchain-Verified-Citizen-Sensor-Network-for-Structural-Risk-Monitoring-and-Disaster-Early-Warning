package com.sih26223.sensing.offline

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineEventEntity::class], version = 1, exportSchema = false)
abstract class OfflineDatabase : RoomDatabase() {
    
    abstract fun offlineEventDao(): OfflineEventDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineDatabase? = null

        fun getDatabase(context: Context): OfflineDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineDatabase::class.java,
                    "sih26223_offline_queue.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
