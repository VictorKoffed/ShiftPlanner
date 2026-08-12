package com.example.shiftplanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Represents a colleague or the main user in the system.
@Entity(tableName = "colleagues")
data class Colleague(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isMainUser: Boolean = false, // Flags the device owner for specific highlight UI logic
    val rowNumber: Int = 0 // Which row in the rolling schedule this person occupies
)

// Represents a static shift in the rolling 6-week base schedule.
// Links a specific schedule row to a specific week and day in the cycle.
@Entity(tableName = "schedule_entries")
data class ScheduleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rowNumber: Int,   // Links to Colleague.rowNumber (e.g., 1-10)
    val weekIndex: Int,   // Week in the rolling cycle (1-6)
    val dayIndex: Int,    // Day of the week (0 = Monday, 6 = Sunday)
    val shiftCode: String // Shift code (e.g., "A", "C", "B", or empty for time off)
)