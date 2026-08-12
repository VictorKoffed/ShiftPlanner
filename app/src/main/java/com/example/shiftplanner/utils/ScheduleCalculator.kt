package com.example.shiftplanner.utils

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Helper object for calculating the current position in the rolling 6-week schedule cycle.
object ScheduleCalculator {

    // Anchor date: Week 1 of the schedule cycle started on Monday, July 20, 2026.
    private val weekOneStartDate = LocalDate.of(2026, 7, 20)

    // Returns the schedule week index (1-6) for today's date.
    fun getCurrentWeekIndex(): Int {
        return getWeekIndexForDate(LocalDate.now())
    }

    // Calculates the schedule week index (1-6) for any given date
    // by comparing it to the anchor date and using modulo arithmetic.
    fun getWeekIndexForDate(date: LocalDate): Int {
        val daysBetween = ChronoUnit.DAYS.between(weekOneStartDate, date)

        // Handle dates before the anchor date
        if (daysBetween < 0) {
            val modulo = (daysBetween % 6).toInt()
            return (6 + (modulo % 6)) % 6 + 1
        }

        // Handle dates on or after the anchor date
        val weeksBetween = daysBetween / 7
        return ((weeksBetween % 6).toInt()) + 1
    }
}