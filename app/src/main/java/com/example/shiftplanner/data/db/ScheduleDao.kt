package com.example.shiftplanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Data Access Object (DAO) defining the SQL queries and operations for the local Room database.
@Dao
interface ScheduleDao {

    // --- Colleagues ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertColleague(colleague: Colleague): Long

    @Update
    suspend fun updateColleague(colleague: Colleague): Int

    @Delete
    suspend fun deleteColleague(colleague: Colleague): Int

    @Query("SELECT * FROM colleagues")
    fun getAllColleagues(): Flow<List<Colleague>>

    @Query("SELECT * FROM colleagues WHERE isMainUser = 1 LIMIT 1")
    suspend fun getMainUser(): Colleague?


    // --- Base Schedule Entries ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>): List<Long>

    @Query("""
        SELECT c.name as colleagueName, s.shiftCode 
        FROM schedule_entries s 
        INNER JOIN colleagues c ON s.rowNumber = c.rowNumber 
        WHERE s.weekIndex = :week AND s.dayIndex = :day
    """)
    fun getShiftsForDay(week: Int, day: Int): Flow<List<ShiftInfo>>

    @Query("SELECT * FROM schedule_entries")
    fun getAllScheduleEntries(): Flow<List<ScheduleEntry>>

    @Query("SELECT shiftCode FROM schedule_entries WHERE rowNumber = :row AND weekIndex = :week AND dayIndex = :day LIMIT 1")
    suspend fun getShiftForEntry(row: Int, week: Int, day: Int): String?


    // --- Overtime, Absences & Shift Exchanges ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOvertime(entry: OvertimeEntry): Long

    @Delete
    suspend fun deleteOvertime(entry: OvertimeEntry): Int

    @Query("DELETE FROM overtime_entries WHERE exchangeGroupId = :groupId")
    suspend fun deleteOvertimeByGroupId(groupId: String): Int

    @Query("SELECT * FROM overtime_entries WHERE dateString = :dateStr")
    fun getOvertimeForDate(dateStr: String): Flow<List<OvertimeEntry>>

    @Query("SELECT * FROM overtime_entries WHERE dateString = :dateStr AND rowNumber = :row LIMIT 1")
    suspend fun getOvertimeForDateAndRow(dateStr: String, row: Int): OvertimeEntry?

    @Query("SELECT * FROM overtime_entries")
    fun getAllOvertime(): Flow<List<OvertimeEntry>>


    // --- Synchronous Fetch Methods (Used by Widgets/Alarms) ---

    @Query("SELECT * FROM colleagues")
    suspend fun getAllColleaguesSync(): List<Colleague>

    @Query("SELECT * FROM schedule_entries")
    suspend fun getAllScheduleEntriesSync(): List<ScheduleEntry>

    @Query("SELECT * FROM overtime_entries")
    suspend fun getAllOvertimeSync(): List<OvertimeEntry>
}

// Helper data class for mapping joined queries between entries and colleagues.
data class ShiftInfo(
    val colleagueName: String,
    val shiftCode: String
)