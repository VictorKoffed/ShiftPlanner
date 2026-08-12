package com.example.shiftplanner.widget

import android.content.Context
import android.util.Log
import com.example.shiftplanner.utils.ScheduleCalculator
import com.example.shiftplanner.data.db.AppDatabase
import com.example.shiftplanner.data.db.OvertimeEntry
import com.example.shiftplanner.data.db.ScheduleEntry
import com.example.shiftplanner.model.ShiftType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

// Helper object responsible for aggregating and formatting database records
// into a UI-friendly model for the home screen widget.
object WidgetDataHelper {

    private const val TAG = "ShiftPlanner_Widget"

    // Checks if the row number belongs to the main user or is a custom row (>= 1000)
    private fun isMainUserRow(rowNum: Int, mainRow: Int): Boolean {
        return rowNum == mainRow || rowNum >= 1000
    }

    // Resolves the final shift code for a specific date by evaluating regular schedules,
    // overtime, absences, and shift exchanges in the correct priority order.
    private fun resolveUserShiftCode(
        rowNumber: Int,
        dateStr: String,
        weekIndex: Int,
        dayIndex: Int,
        allEntries: List<ScheduleEntry>,
        allOvertime: List<OvertimeEntry>
    ): String {
        val dayOtEntries = allOvertime.filter { it.dateString == dateStr && it.rowNumber == rowNumber }

        // 1. Check for shift exchanges (highest priority)
        val exchangeEntry = dayOtEntries.find { it.exchangeGroupId != null }
        if (exchangeEntry != null) {
            if (exchangeEntry.shiftCode.uppercase() == "ABSENT") {
                return "BY" // Traded away
            } else if (exchangeEntry.shiftCode.isNotBlank()) {
                return exchangeEntry.shiftCode.uppercase()
            }
        }

        // 2. Check for absences (sick leave, vacation)
        val absenceEntry = dayOtEntries.find {
            (it.shiftCode.uppercase() == "ABSENT" || it.note.isNotBlank()) && it.exchangeGroupId == null
        }
        if (absenceEntry != null) {
            val note = absenceEntry.note.lowercase()
            val code = absenceEntry.shiftCode.uppercase()

            return when {
                code == "SJU" || note.contains("sjuk") -> "SJU"
                code == "SEM" || note.contains("semester") -> "SEM"
                code.isNotBlank() && code != "ABSENT" -> code
                else -> "SJU"
            }
        }

        // 3. Check for regular overtime or extra shifts
        val overtime = dayOtEntries.find { it.shiftCode.uppercase() != "ABSENT" && it.shiftCode.isNotBlank() && it.exchangeGroupId == null }
        if (overtime != null) {
            return overtime.shiftCode.uppercase()
        }

        // 4. Fallback to the regular base schedule
        val regular = allEntries.find { it.rowNumber == rowNumber && it.weekIndex == weekIndex && it.dayIndex == dayIndex }
        return regular?.shiftCode?.uppercase() ?: ""
    }

    // Fetches current schedule data from the database and constructs
    // the complete state required to render the Glance widget.
    suspend fun getWidgetInfo(context: Context): WidgetInfoData {
        Log.d(TAG, "Fetching widget info from database...")
        val db = AppDatabase.getDatabase(context)
        val dao = db.scheduleDao()

        val today = LocalDate.now()

        // Fetch database records (safe to do synchronously here as it's called from a background worker)
        val colleagues = dao.getAllColleaguesSync() ?: emptyList()
        val mainUser = colleagues.find { it.isMainUser }
        val allEntries = dao.getAllScheduleEntriesSync() ?: emptyList()
        val allOvertime = dao.getAllOvertimeSync() ?: emptyList()

        Log.d(TAG, "Main user: ${mainUser?.name} (row ${mainUser?.rowNumber}), Colleagues: ${colleagues.size}, Entries: ${allEntries.size}")

        var shiftTitle = "Ledig"
        var shiftTime = "Inga inplanerade tider"
        var todayHasBlixt = false

        val swedishWeekFields = WeekFields.of(Locale("sv", "SE"))
        val realWeekNumber = today.get(swedishWeekFields.weekOfYear())

        val daysInWeek = mutableListOf<DayWidgetModel>()
        val mondayOfThisWeek = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val dateStringFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        // Build the data model for the 7 days of the current week
        for (i in 0..6) {
            val targetDate = mondayOfThisWeek.plusDays(i.toLong())
            val targetDateStr = targetDate.format(dateStringFormatter)
            val wIndex = ScheduleCalculator.getWeekIndexForDate(targetDate)
            val dIndex = targetDate.dayOfWeek.value - 1

            val isPast = targetDate.isBefore(today)
            if (isPast) {
                daysInWeek.add(
                    DayWidgetModel(
                        dayName = targetDate.format(DateTimeFormatter.ofPattern("EEE", Locale("sv", "SE"))).replaceFirstChar { it.uppercase() }.take(3),
                        dayNumber = targetDate.dayOfMonth.toString(),
                        shiftCode = "",
                        isToday = false,
                        isPast = true,
                        colorType = ShiftColorType.NORMAL,
                        hasBlixt = false
                    )
                )
                continue
            }

            val mainUserRow = mainUser?.rowNumber ?: 1
            val dayOtEntries = allOvertime.filter { it.dateString == targetDateStr }

            val exchangeGoneEntry = dayOtEntries.find { isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
            val exchangeCoverEntry = dayOtEntries.find { isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
            val coverOtEntry = dayOtEntries.find { !isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }

            val targetRow = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow
            val rawResolvedCode = resolveUserShiftCode(targetRow, targetDateStr, wIndex, dIndex, allEntries, allOvertime)

            val resolvedCode = if (exchangeGoneEntry != null) {
                "BY"
            } else if (exchangeCoverEntry != null) {
                exchangeCoverEntry.shiftCode.uppercase()
            } else if (coverOtEntry != null) {
                rawResolvedCode
            } else {
                resolveUserShiftCode(mainUserRow, targetDateStr, wIndex, dIndex, allEntries, allOvertime)
            }

            val cleanCode = if (resolvedCode.uppercase() == "ABSENT" || resolvedCode == "-") "" else resolvedCode
            val dayName = targetDate.format(DateTimeFormatter.ofPattern("EEE", Locale("sv", "SE")))
            val dayNumber = targetDate.dayOfMonth.toString()
            val isToday = targetDate.isEqual(today)

            val hasOtOrCover = dayOtEntries.any { isMainUserRow(it.rowNumber, mainUserRow) } || coverOtEntry != null

            val colorType = when (cleanCode.uppercase()) {
                "F" -> ShiftColorType.GREEN_LIGHT
                "E" -> ShiftColorType.BLUE
                "N" -> ShiftColorType.PURPLE_LIGHT
                "D" -> ShiftColorType.YELLOW
                "J" -> ShiftColorType.RED
                "SJU", "ABS" -> ShiftColorType.RED
                "SEM" -> ShiftColorType.YELLOW
                "BY" -> ShiftColorType.NORMAL
                else -> ShiftColorType.NORMAL
            }

            daysInWeek.add(
                DayWidgetModel(
                    dayName = dayName.replaceFirstChar { it.uppercase() }.take(3),
                    dayNumber = dayNumber,
                    shiftCode = cleanCode,
                    isToday = isToday,
                    isPast = false,
                    colorType = colorType,
                    hasBlixt = hasOtOrCover
                )
            )
        }

        // Determine specific shift details for the current day
        if (mainUser != null) {
            val todayStr = today.format(dateStringFormatter)
            val mainUserRow = mainUser.rowNumber
            val dayOtEntries = allOvertime.filter { it.dateString == todayStr }
            val todayWIndex = ScheduleCalculator.getWeekIndexForDate(today)
            val todayDIndex = today.dayOfWeek.value - 1

            val exchangeGoneEntry = dayOtEntries.find { isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
            val exchangeCoverEntry = dayOtEntries.find { isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
            val coverOtEntry = dayOtEntries.find { !isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }

            todayHasBlixt = dayOtEntries.any { isMainUserRow(it.rowNumber, mainUserRow) } || coverOtEntry != null

            val targetRow = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow
            val rawResolvedCode = resolveUserShiftCode(targetRow, todayStr, todayWIndex, todayDIndex, allEntries, allOvertime)

            val finalTodayCode = if (exchangeGoneEntry != null) {
                "BY"
            } else if (exchangeCoverEntry != null) {
                exchangeCoverEntry.shiftCode.uppercase()
            } else if (coverOtEntry != null) {
                rawResolvedCode
            } else {
                resolveUserShiftCode(mainUserRow, todayStr, todayWIndex, todayDIndex, allEntries, allOvertime)
            }

            if (finalTodayCode.isNotBlank() && finalTodayCode != "-") {
                if (finalTodayCode.uppercase() == "BY") {
                    shiftTitle = "Bortbytt pass (Ledig)"
                    shiftTime = ""
                } else {
                    val match = ShiftType.values().find { it.code.equals(finalTodayCode, ignoreCase = true) }
                    if (match != null) {
                        shiftTitle = "${match.title} (${match.code.uppercase()})"

                        // Use default shift time from shift type definitions
                        shiftTime = match.time
                    } else {
                        shiftTitle = "Pass $finalTodayCode"
                        shiftTime = ""
                    }
                }
            }
        }

        val todayFormatted = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("sv", "SE")))
        Log.d(TAG, "WidgetDataHelper completed. Today's shift title: '$shiftTitle'")

        return WidgetInfoData(
            todayText = todayFormatted.replaceFirstChar { it.uppercase() },
            shiftTitle = shiftTitle,
            shiftTime = shiftTime,
            mainUserName = mainUser?.name ?: "Användare",
            realWeekNumber = realWeekNumber,
            days = daysInWeek,
            todayHasBlixt = todayHasBlixt
        )
    }
}

enum class ShiftColorType {
    NORMAL, GREEN_LIGHT, GREEN_DARK, BLUE, PURPLE_LIGHT, PURPLE_DARK, RED, YELLOW
}

// Data class encapsulating the full state required for the widget UI
data class WidgetInfoData(
    val todayText: String,
    val shiftTitle: String,
    val shiftTime: String,
    val mainUserName: String,
    val realWeekNumber: Int,
    val days: List<DayWidgetModel>,
    val todayHasBlixt: Boolean
)

// Data class representing a single day in the widget's weekly overview
data class DayWidgetModel(
    val dayName: String,
    val dayNumber: String,
    val shiftCode: String,
    val isToday: Boolean,
    val isPast: Boolean,
    val colorType: ShiftColorType,
    val hasBlixt: Boolean
)