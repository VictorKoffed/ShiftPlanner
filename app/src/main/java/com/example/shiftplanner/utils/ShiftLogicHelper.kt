package com.example.shiftplanner.utils

import com.example.shiftplanner.data.db.OvertimeEntry
import com.example.shiftplanner.data.db.ScheduleEntry

// Helper object encapsulating the core business logic for resolving daily shift states.
object ShiftLogicHelper {

    // Determines if a given row belongs to the primary user or is a custom row (>= 1000).
    fun isMainUserRow(rowNum: Int, mainRow: Int): Boolean {
        return rowNum == mainRow || rowNum >= 1000
    }

    // Helper to check if a specific note indicates a shift exchange.
    fun isExchangeNote(note: String): Boolean {
        val lower = note.lowercase()
        return lower.contains("byt") || lower.contains("utbytt") || note.contains("BY:")
    }

    // Resolves the final shift code for a user on a specific date by applying priority rules:
    // 1. Shift exchanges (highest priority)
    // 2. Absences (sick leave, vacation)
    // 3. Overtime or extra shifts
    // 4. Base schedule (lowest priority)
    fun resolveUserShiftCode(
        rowNumber: Int,
        dateStr: String,
        weekIndex: Int,
        dayIndex: Int,
        allEntries: List<ScheduleEntry>,
        allOvertime: List<OvertimeEntry>
    ): String {
        val dayOtEntries = allOvertime.filter { it.dateString == dateStr && it.rowNumber == rowNumber }

        // 1. Check for shift exchanges
        val exchangeEntry = dayOtEntries.find { it.exchangeGroupId != null }
        if (exchangeEntry != null) {
            if (exchangeEntry.shiftCode.uppercase() == "ABSENT") {
                return "BY" // Indicates the shift was traded away
            } else if (exchangeEntry.shiftCode.isNotBlank()) {
                return exchangeEntry.shiftCode.uppercase()
            }
        }

        // 2. Check for absences
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
                else -> "SJU" // Default fallback for absence
            }
        }

        // 3. Check for overtime/extra shifts
        val overtime = dayOtEntries.find { it.shiftCode.uppercase() != "ABSENT" && it.shiftCode.isNotBlank() && it.exchangeGroupId == null }
        if (overtime != null) {
            return overtime.shiftCode.uppercase()
        }

        // 4. Fallback to the regular base schedule
        val regular = allEntries.find { it.rowNumber == rowNumber && it.weekIndex == weekIndex && it.dayIndex == dayIndex }
        return regular?.shiftCode?.uppercase() ?: ""
    }
}