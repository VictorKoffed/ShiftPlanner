package com.example.shiftplanner.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Calculates schedule cycle positions based on a rolling 6-week (42-day) shift rotation.
 */
object ScheduleCalculator {
    // Fixed synchronization anchor: Wednesday, July 29, 2026 corresponds to Week 2, Wednesday.
    private val anchorDate = LocalDate.of(2026, 7, 29)

    // 0-indexed week offset (Week 2 corresponds to index 1).
    private const val ANCHOR_WEEK_INDEX = 1

    // 0-indexed day offset (Monday = 0 ... Wednesday = 2).
    private const val ANCHOR_DAY_INDEX = 2

    /**
     * Determines the schedule week (1-6) and day index (0-6, Monday-Sunday)
     * for any given date within the rotating shift pattern.
     *
     * @param targetDate The date to resolve within the schedule rotation.
     * @return A [Pair] containing the 1-based week number (1..6) and 0-based day index (0..6).
     */
    fun getWeekAndDay(targetDate: LocalDate): Pair<Int, Int> {
        val daysBetween = ChronoUnit.DAYS.between(anchorDate, targetDate)

        // Full rotation consists of 6 weeks (42 days).
        val totalDaysIndex = (ANCHOR_WEEK_INDEX * 7 + ANCHOR_DAY_INDEX + daysBetween) % 42

        // Normalize negative modulo for dates prior to the anchor date.
        val normalizedIndex = if (totalDaysIndex < 0) totalDaysIndex + 42 else totalDaysIndex

        val currentWeek = (normalizedIndex / 7).toInt() + 1
        val currentDayOfWeek = (normalizedIndex % 7).toInt()

        return Pair(currentWeek, currentDayOfWeek)
    }
}
