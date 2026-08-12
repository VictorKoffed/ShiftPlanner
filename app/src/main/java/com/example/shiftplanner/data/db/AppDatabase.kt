package com.example.shiftplanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// The main Room database class for the application.
// Contains tables for Colleagues, Base Schedule Entries, and Overtime/Modifiers.
@Database(
    entities = [Colleague::class, ScheduleEntry::class, OvertimeEntry::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Provides a singleton instance of the database to prevent multiple instances
        // from being opened at the same time.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schema_database"
                )
                    // Fallback to destructive migration handles schema changes smoothly during development.
                    // In a live production app with real user data, proper Migration objects would be provided here.
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}