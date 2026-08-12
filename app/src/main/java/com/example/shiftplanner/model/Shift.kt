package com.example.shiftplanner.model

// Defines the standard shift types used throughout the application,
// linking their internal abbreviation codes to user-friendly titles and time ranges.
enum class ShiftType(
    val code: String,
    val title: String,
    val time: String
) {
    MORNING("F", "Förmiddagspass", "06.00 - 14.30"),
    EVENING("E", "Eftermiddagspass", "14.00 - 22.30"),
    NIGHT("N", "Nattpass", "22.00 - 06.30"),
    DAY("D", "Långdag", "07.00 - 17.00"),
    JOUR("J", "Jourpass", "08.00 - 20.00"),
    OFF("–", "Ledig", "Inga inplanerade tider")
}