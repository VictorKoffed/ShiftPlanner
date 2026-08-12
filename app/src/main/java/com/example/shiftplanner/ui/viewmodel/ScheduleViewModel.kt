package com.example.shiftplanner

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shiftplanner.data.db.AppDatabase
import com.example.shiftplanner.data.db.Colleague
import com.example.shiftplanner.data.db.OvertimeEntry
import com.example.shiftplanner.data.db.ScheduleEntry
import com.example.shiftplanner.data.db.ScheduleRepository
import com.example.shiftplanner.data.db.ShiftInfo
import com.example.shiftplanner.model.ShiftType
import com.example.shiftplanner.utils.ScheduleCalculator
import com.example.shiftplanner.utils.ShiftLogicHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

// --- HOME SCREEN STATE ---
data class HomeUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isEditingOvertime: Boolean = false,
    val selectedColleagueRow: Int = -1,
    val noteText: String = "",
    val customShiftCode: String = "",
    val errorMessage: String? = null,
    val entryMode: String = "",
    val entryToDelete: OvertimeEntry? = null,
    val exchangeTargetRow: Int = -1,
    val exchangeTargetName: String = "",
    val exchangeTargetDate: LocalDate? = null
)

// --- MONTH SCREEN STATE ---
data class MonthUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isEditingOvertime: Boolean = false,
    val showMonthPickerDialog: Boolean = false,
    val pickerYear: Int = LocalDate.now().year,
    val selectedColleagueRow: Int = -1,
    val noteText: String = "",
    val customShiftCode: String = "",
    val errorMessage: String? = null,
    val entryMode: String = "",
    val entryToDelete: OvertimeEntry? = null,
    val exchangeTargetRow: Int = -1,
    val exchangeTargetName: String = "",
    val exchangeTargetDate: LocalDate? = null
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScheduleRepository
    val allColleagues: Flow<List<Colleague>>
    val allScheduleEntries: Flow<List<ScheduleEntry>>
    val allOvertime: Flow<List<OvertimeEntry>>

    // UI State for HomeScreen
    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    // UI State for MonthScreen
    private val _monthState = MutableStateFlow(MonthUiState())
    val monthState: StateFlow<MonthUiState> = _monthState.asStateFlow()

    companion object {
        private const val TAG = "ShiftPlanner_ViewModel"
    }

    init {
        Log.d(TAG, "Initializing ScheduleViewModel...")
        val dao = AppDatabase.getDatabase(application).scheduleDao()
        repository = ScheduleRepository(dao)

        allColleagues = repository.allColleagues
        allScheduleEntries = repository.allScheduleEntries
        allOvertime = repository.allOvertime

        seedInitialDataIfNeeded()
    }

    private fun triggerWidgetUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.shiftplanner.widget.WidgetUpdater.updateWidgetState(getApplication())
        }
    }

    // --- HOME SCREEN ACTIONS & STATE MANAGERS ---

    fun updateSelectedDate(date: LocalDate) {
        _homeState.value = _homeState.value.copy(selectedDate = date, isEditingOvertime = false)
    }

    fun setEditingOvertime(isEditing: Boolean) {
        _homeState.value = _homeState.value.copy(
            isEditingOvertime = isEditing,
            selectedColleagueRow = -1,
            entryMode = "",
            noteText = "",
            customShiftCode = "",
            errorMessage = null,
            exchangeTargetRow = -1,
            exchangeTargetName = "",
            exchangeTargetDate = null
        )
    }

    fun setEntryMode(mode: String, mainUserRow: Int) {
        val defaultNote = when (mode) {
            "ABSENCE" -> "Sjuk"
            "COVER", "EXCHANGE" -> "Inhopp"
            "CUSTOM" -> "Övertid"
            else -> ""
        }
        _homeState.value = _homeState.value.copy(
            entryMode = mode,
            selectedColleagueRow = if (mode == "ABSENCE" || mode == "CUSTOM") mainUserRow else -1,
            noteText = defaultNote,
            customShiftCode = "",
            exchangeTargetRow = -1,
            exchangeTargetName = "",
            exchangeTargetDate = null
        )
    }

    fun updateNoteText(text: String) {
        _homeState.value = _homeState.value.copy(noteText = text)
    }

    fun updateCustomShiftCode(code: String) {
        _homeState.value = _homeState.value.copy(customShiftCode = code)
    }

    fun setSelectedColleagueRow(row: Int) {
        _homeState.value = _homeState.value.copy(selectedColleagueRow = row)
    }

    fun setExchangeTarget(row: Int, name: String) {
        _homeState.value = _homeState.value.copy(exchangeTargetRow = row, exchangeTargetName = name)
    }

    fun clearExchangeTarget() {
        _homeState.value = _homeState.value.copy(exchangeTargetRow = -1, exchangeTargetName = "", exchangeTargetDate = null)
    }

    fun setExchangeTargetDate(date: LocalDate?) {
        _homeState.value = _homeState.value.copy(exchangeTargetDate = date)
    }

    fun setEntryToDelete(entry: OvertimeEntry?) {
        _homeState.value = _homeState.value.copy(entryToDelete = entry)
    }

    // --- MONTH SCREEN ACTIONS & STATE MANAGERS ---

    fun updateMonthSelectedDate(date: LocalDate) {
        _monthState.value = _monthState.value.copy(selectedDate = date, isEditingOvertime = false)
    }

    fun setMonthEditingOvertime(isEditing: Boolean) {
        _monthState.value = _monthState.value.copy(
            isEditingOvertime = isEditing,
            selectedColleagueRow = -1,
            entryMode = "",
            noteText = "",
            customShiftCode = "",
            errorMessage = null,
            exchangeTargetRow = -1,
            exchangeTargetName = "",
            exchangeTargetDate = null
        )
    }

    fun setShowMonthPickerDialog(show: Boolean, year: Int = LocalDate.now().year) {
        _monthState.value = _monthState.value.copy(showMonthPickerDialog = show, pickerYear = year)
    }

    fun updatePickerYear(year: Int) {
        _monthState.value = _monthState.value.copy(pickerYear = year)
    }

    fun setMonthEntryMode(mode: String, mainUserRow: Int) {
        val defaultNote = when (mode) {
            "ABSENCE" -> "Sjuk"
            "COVER", "EXCHANGE" -> "Inhopp"
            "CUSTOM" -> "Övertid"
            else -> ""
        }
        _monthState.value = _monthState.value.copy(
            entryMode = mode,
            selectedColleagueRow = if (mode == "ABSENCE" || mode == "CUSTOM") mainUserRow else -1,
            noteText = defaultNote,
            customShiftCode = "",
            exchangeTargetRow = -1,
            exchangeTargetName = "",
            exchangeTargetDate = null
        )
    }

    fun updateMonthNoteText(text: String) {
        _monthState.value = _monthState.value.copy(noteText = text)
    }

    fun updateMonthCustomShiftCode(code: String) {
        _monthState.value = _monthState.value.copy(customShiftCode = code)
    }

    fun setMonthSelectedColleagueRow(row: Int) {
        _monthState.value = _monthState.value.copy(selectedColleagueRow = row)
    }

    fun setMonthExchangeTarget(row: Int, name: String) {
        _monthState.value = _monthState.value.copy(exchangeTargetRow = row, exchangeTargetName = name)
    }

    fun clearMonthExchangeTarget() {
        _monthState.value = _monthState.value.copy(exchangeTargetRow = -1, exchangeTargetName = "", exchangeTargetDate = null)
    }

    fun setMonthExchangeTargetDate(date: LocalDate?) {
        _monthState.value = _monthState.value.copy(exchangeTargetDate = date)
    }

    fun setMonthEntryToDelete(entry: OvertimeEntry?) {
        _monthState.value = _monthState.value.copy(entryToDelete = entry)
    }

    // --- DATABASE & LOGIC METHODS ---

    fun saveOvertime(entry: OvertimeEntry, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Saving overtime/exchange for date: ${entry.dateString}, code: ${entry.shiftCode}")
                if (entry.note.isBlank() && entry.shiftCode != "ABSENT") {
                    Log.w(TAG, "Save failed: Missing note.")
                    onError("Ange en anteckning för övertiden/inhoppet.")
                    return@launch
                }
                repository.insertOvertime(entry)
                triggerWidgetUpdate()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save overtime: ${e.message}", e)
                onError("Kunde inte spara övertid: ${e.localizedMessage ?: "Okänt fel"}")
            }
        }
    }

    fun deleteOvertime(entry: OvertimeEntry, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting overtime for date: ${entry.dateString}")
                repository.deleteOvertime(entry)
                triggerWidgetUpdate()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete overtime: ${e.message}", e)
                onError("Kunde inte ta bort övertid: ${e.localizedMessage ?: "Okänt fel"}")
            }
        }
    }

    fun deleteOvertimeGroup(groupId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting exchange group with ID: $groupId")
                repository.deleteOvertimeByGroupId(groupId)
                triggerWidgetUpdate()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete exchange group: ${e.message}", e)
                onError("Kunde inte ta bort passbytet: ${e.localizedMessage ?: "Okänt fel"}")
            }
        }
    }

    suspend fun getTomorrowShiftDetails(): Pair<String, String>? {
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowStr = tomorrow.toString()

        val mainUser = repository.getMainUser() ?: return null
        val rowNumber = mainUser.rowNumber

        val overtimeEntry = repository.getOvertimeForDateAndRow(tomorrowStr, rowNumber)
        val rawCode = if (overtimeEntry != null) {
            overtimeEntry.shiftCode
        } else {
            val weekIndex = ScheduleCalculator.getWeekIndexForDate(tomorrow)
            val dayIndex = tomorrow.dayOfWeek.value - 1
            AppDatabase.getDatabase(getApplication()).scheduleDao().getShiftForEntry(rowNumber, weekIndex, dayIndex)
        }

        if (rawCode.isNullOrBlank() || rawCode == "–" || rawCode == "ABSENT") {
            return null
        }

        val match = ShiftType.values().find { it.code.equals(rawCode, ignoreCase = true) }
        return if (match != null) {
            Pair(match.title, match.time)
        } else {
            Pair("Pass $rawCode", "")
        }
    }

    fun getDayScheduleState(date: LocalDate): Flow<DayScheduleState> {
        val dateStr = date.toString()
        val weekIndex = ScheduleCalculator.getWeekIndexForDate(date)
        val dayIndex = date.dayOfWeek.value - 1

        return combine(allColleagues, allScheduleEntries, allOvertime) { colleagues, entries, overtime ->
            val mainUser = colleagues.find { it.isMainUser }
            val mainUserRow = mainUser?.rowNumber ?: 1

            val myRegularShift = entries.find { it.rowNumber == mainUserRow && it.weekIndex == weekIndex && it.dayIndex == dayIndex }?.shiftCode?.uppercase()

            val dayOtEntries = overtime.filter { it.dateString == dateStr }
            val exchangeByMe = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
            val exchangeCoveredByMe = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
            val coverOtEntry = dayOtEntries.find { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }
            val customOtEntries = dayOtEntries.filter {
                ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) &&
                        it.shiftCode.uppercase() !in listOf("ABSENT", "SJU", "SEM") &&
                        it.exchangeGroupId == null
            }
            val userAbsenceEntry = dayOtEntries.find {
                ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) &&
                        (it.shiftCode.uppercase() == "ABSENT" || it.note.lowercase().contains("sjuk") || it.note.lowercase().contains("semester")) &&
                        it.exchangeGroupId == null
            }

            val baseResolvedCode = ShiftLogicHelper.resolveUserShiftCode(
                rowNumber = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow,
                dateStr = dateStr,
                weekIndex = weekIndex,
                dayIndex = dayIndex,
                allEntries = entries,
                allOvertime = overtime
            )

            val finalMyCode = if (exchangeByMe != null) {
                "-"
            } else if (exchangeCoveredByMe != null) {
                exchangeCoveredByMe.shiftCode.uppercase()
            } else {
                baseResolvedCode
            }

            val activeColleagues = colleagues.mapNotNull { colleague ->
                if (colleague.rowNumber == mainUserRow) return@mapNotNull null
                val code = ShiftLogicHelper.resolveUserShiftCode(colleague.rowNumber, dateStr, weekIndex, dayIndex, entries, overtime)
                val isOt = dayOtEntries.any { it.rowNumber == colleague.rowNumber && it.shiftCode != "ABSENT" }

                if (code.isNotBlank() && code != "-" && code != "BY" && code != "SJU" && code != "SEM") {
                    WorkingColleague(colleague.name, code.uppercase(), isOt, colleague.rowNumber)
                } else null
            }

            // Get the name of the colleague being covered, if applicable
            val coverColleague = colleagues.find { it.rowNumber == coverOtEntry?.rowNumber }
            val coverNoteText = if (coverColleague != null) {
                "Inhopp för ${coverColleague.name}"
            } else {
                exchangeCoveredByMe?.note ?: coverOtEntry?.let { "Inhopp" }
            }

            DayScheduleState(
                date = date,
                mainUserShiftCode = finalMyCode,
                isMainUserFree = exchangeByMe != null || finalMyCode.isBlank() || finalMyCode == "-",
                hasActiveAbsence = finalMyCode == "SJU" || finalMyCode == "SEM" || userAbsenceEntry != null,
                mainUserRegularShift = myRegularShift,
                activeExchangeNote = exchangeByMe?.note,
                coverOrExchangeInNote = coverNoteText,
                customOvertimeNotes = customOtEntries.map { it.note.ifBlank { "Övertid" } },
                workingColleagues = activeColleagues,
                absenceNote = userAbsenceEntry?.note
            )
        }
    }

    // Seeds the database with mock data if it's currently empty.
    private fun seedInitialDataIfNeeded() {
        viewModelScope.launch {
            val existingMainUser = repository.getMainUser()
            if (existingMainUser == null) {
                Log.d(TAG, "No user found in database. Seeding realistic shift schedule...")

                // Add mock colleagues
                repository.insertColleague(Colleague(name = "Demo User", isMainUser = true, rowNumber = 1))
                repository.insertColleague(Colleague(name = "Anna (Kollega 1)", isMainUser = false, rowNumber = 2))
                repository.insertColleague(Colleague(name = "Johan (Kollega 2)", isMainUser = false, rowNumber = 3))
                repository.insertColleague(Colleague(name = "Sara (Kollega 3)", isMainUser = false, rowNumber = 4))
                repository.insertColleague(Colleague(name = "Mikael (Kollega 4)", isMainUser = false, rowNumber = 5))
                repository.insertColleague(Colleague(name = "Emma (Kollega 5)", isMainUser = false, rowNumber = 6))
                repository.insertColleague(Colleague(name = "David (Kollega 6)", isMainUser = false, rowNumber = 7))
                repository.insertColleague(Colleague(name = "Linda (Kollega 7)", isMainUser = false, rowNumber = 8))

                val initialEntries = mutableListOf<ScheduleEntry>()
                fun addShift(row: Int, week: Int, day: Int, code: String) {
                    initialEntries.add(ScheduleEntry(rowNumber = row, weekIndex = week, dayIndex = day, shiftCode = code))
                }

                // -------------------------------------------------------------
                // Mock  (Week 1 to 6, Day 0=Mon till 6=Sun)
                // -------------------------------------------------------------
                for (w in 1..6) {
                    // --- RAD 1: Demo User  ---
                    when (w) {
                        1 -> {
                            addShift(1, w, 0, "F"); addShift(1, w, 1, "F")
                            addShift(1, w, 3, "E"); addShift(1, w, 4, "E")
                        }
                        2 -> {
                            addShift(1, w, 0, "N"); addShift(1, w, 1, "N")
                            addShift(1, w, 4, "F"); addShift(1, w, 5, "J")
                        }
                        3 -> {
                            addShift(1, w, 1, "D"); addShift(1, w, 2, "D")
                            addShift(1, w, 4, "E")
                        }
                        4 -> {
                            addShift(1, w, 0, "F"); addShift(1, w, 1, "F"); addShift(1, w, 2, "F")
                            addShift(1, w, 5, "E"); addShift(1, w, 6, "E")
                        }
                        5 -> {
                            addShift(1, w, 1, "N"); addShift(1, w, 2, "N"); addShift(1, w, 3, "N")
                        }
                        6 -> {
                            addShift(1, w, 0, "D"); addShift(1, w, 1, "D")
                            addShift(1, w, 3, "F"); addShift(1, w, 4, "F")
                        }
                    }

                    // --- Line 2: Anna ---
                    addShift(2, w, 0, "D")
                    addShift(2, w, 1, "D")
                    addShift(2, w, 3, "F")
                    if (w % 2 == 0) addShift(2, w, 5, "F")

                    // --- Line 3: Johan (Eftermiddag och natt) ---
                    addShift(3, w, 1, "E")
                    addShift(3, w, 2, "E")
                    addShift(3, w, 4, "N")
                    addShift(3, w, 6, "E")

                    // --- Line 4: Sara ---
                    addShift(4, w, 0, "N")
                    addShift(4, w, 5, "N")
                    addShift(4, w, 6, "N")
                    if (w == 2 || w == 5) addShift(4, w, 3, "J")

                    // --- Line 5: Mikael ---
                    addShift(5, w, 2, "D")
                    addShift(5, w, 3, "D")
                    addShift(5, w, 5, "F")

                    // --- Line 6: Emma  ---
                    addShift(6, w, 1, "E")
                    addShift(6, w, 3, "E")
                    addShift(6, w, 5, "E")

                    // --- Line 7: David ---
                    addShift(7, w, 0, "D")
                    addShift(7, w, 4, "F")
                    if (w % 2 != 0) addShift(7, w, 6, "J")

                    // --- Line 8: Linda ---
                    addShift(8, w, 2, "N")
                    addShift(8, w, 3, "N")
                    addShift(8, w, 5, "D")
                }

                repository.insertScheduleEntries(initialEntries)
                Log.d(TAG, "Mock schedule data seeded successfully.")
            }
        }
    }

    fun addColleague(name: String, rowNumber: Int = 0) {
        viewModelScope.launch {
            Log.d(TAG, "Adding colleague: $name")
            repository.insertColleague(Colleague(name = name, isMainUser = false, rowNumber = rowNumber))
            triggerWidgetUpdate()
        }
    }

    fun updateColleague(colleague: Colleague) {
        viewModelScope.launch {
            Log.d(TAG, "Updating colleague: ${colleague.name}")
            repository.updateColleague(colleague)
            triggerWidgetUpdate()
        }
    }

    fun deleteColleague(colleague: Colleague) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting colleague: ${colleague.name}")
            repository.deleteColleague(colleague)
            triggerWidgetUpdate()
        }
    }

    fun saveSchedule(entries: List<ScheduleEntry>) {
        viewModelScope.launch {
            Log.d(TAG, "Saving new schedule entries, total: ${entries.size}")
            repository.insertScheduleEntries(entries)
            triggerWidgetUpdate()
        }
    }

    fun getShiftsForDay(week: Int, day: Int): Flow<List<ShiftInfo>> {
        return repository.getShiftsForDay(week, day)
    }
}

// ==========================================
// DATA CLASSES FOR UI STATE
// ==========================================

data class DayScheduleState(
    val date: LocalDate,
    val mainUserShiftCode: String,
    val isMainUserFree: Boolean,
    val hasActiveAbsence: Boolean,
    val mainUserRegularShift: String?,
    val activeExchangeNote: String?,
    val coverOrExchangeInNote: String?,
    val customOvertimeNotes: List<String>,
    val workingColleagues: List<WorkingColleague>,
    val absenceNote: String?
)

data class WorkingColleague(
    val name: String,
    val shiftCode: String,
    val isOvertime: Boolean,
    val rowNumber: Int
)