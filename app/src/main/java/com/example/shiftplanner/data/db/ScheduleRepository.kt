package com.example.shiftplanner.data.db

import kotlinx.coroutines.flow.Flow

// Repository class that abstracts access to the Room database.
// Provides a clean API for the ViewModel to observe and modify data.
class ScheduleRepository(private val scheduleDao: ScheduleDao) {

    val allColleagues: Flow<List<Colleague>> = scheduleDao.getAllColleagues()
    val allScheduleEntries: Flow<List<ScheduleEntry>> = scheduleDao.getAllScheduleEntries()
    val allOvertime: Flow<List<OvertimeEntry>> = scheduleDao.getAllOvertime()

    // Colleague management
    suspend fun insertColleague(colleague: Colleague) = scheduleDao.insertColleague(colleague)
    suspend fun updateColleague(colleague: Colleague) = scheduleDao.updateColleague(colleague)
    suspend fun deleteColleague(colleague: Colleague) = scheduleDao.deleteColleague(colleague)
    suspend fun getMainUser() = scheduleDao.getMainUser()

    // Base schedule management
    suspend fun insertScheduleEntries(entries: List<ScheduleEntry>) = scheduleDao.insertScheduleEntries(entries)
    fun getShiftsForDay(week: Int, day: Int): Flow<List<ShiftInfo>> = scheduleDao.getShiftsForDay(week, day)

    // Overtime, absences, and shift exchange management
    suspend fun insertOvertime(entry: OvertimeEntry) = scheduleDao.insertOvertime(entry)
    suspend fun deleteOvertime(entry: OvertimeEntry) = scheduleDao.deleteOvertime(entry)
    suspend fun deleteOvertimeByGroupId(groupId: String) = scheduleDao.deleteOvertimeByGroupId(groupId)
    suspend fun getOvertimeForDateAndRow(dateStr: String, row: Int) = scheduleDao.getOvertimeForDateAndRow(dateStr, row)
}