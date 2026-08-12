package com.example.shiftplanner.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ScheduleCalculator {
    // Vår kända referenspunkt: Onsdag 29 juli 2026 är vi på vecka 2.
    private val anchorDate = LocalDate.of(2026, 7, 29)

    // Vecka 2 motsvarar index 1 (om vecka 1 är index 0)
    private const val ANCHOR_WEEK_INDEX = 1
    // Onsdag är dag index 2 (Måndag=0, Tisdag=1, Onsdag=2, Torsdag=3, Fredag=4, Lördag=5, Söndag=6)
    private const val ANCHOR_DAY_INDEX = 2

    fun getWeekAndDay(targetDate: LocalDate): Pair<Int, Int> {
        val daysBetween = ChronoUnit.DAYS.between(anchorDate, targetDate)

        // Ett helt schema är 6 veckor * 7 dagar = 42 dagar
        val totalDaysIndex = (ANCHOR_WEEK_INDEX * 7 + ANCHOR_DAY_INDEX + daysBetween) % 42

        // Hantera negativa tal om man tittar bakåt i tiden
        val normalizedIndex = if (totalDaysIndex < 0) totalDaysIndex + 42 else totalDaysIndex

        val currentWeek = (normalizedIndex / 7).toInt() + 1 // Ger vecka 1-6
        val currentDayOfWeek = (normalizedIndex % 7).toInt() // Ger dag 0-6

        return Pair(currentWeek, currentDayOfWeek)
    }
}