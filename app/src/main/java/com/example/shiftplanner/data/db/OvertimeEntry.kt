package com.example.shiftplanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Represents a dynamic schedule modifier such as overtime, absence, or a shift exchange.
// These entries act as overrides on top of the regular base schedule for specific dates.
@Entity(
    tableName = "overtime_entries",
    indices = [Index(value = ["dateString"])] // Speeds up database queries filtering by date
)
data class OvertimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dateString: String,
    val rowNumber: Int,
    val shiftCode: String,
    val note: String,
    val exchangeGroupId: String? = null // Used to link two distinct entries together in a shift exchange
)