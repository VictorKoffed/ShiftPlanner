package com.example.shiftplanner.utils

import androidx.compose.ui.graphics.Color

// Helper object for mapping shift codes to UI presentation elements like text descriptions and colors.
object ShiftUiHelper {

    // Returns a compact time string for a given shift code, used in smaller UI elements.
    fun getShortShiftTime(shiftCode: String): String {
        return when (shiftCode.uppercase()) {
            "F" -> "06.00 - 14.30"
            "E" -> "14.00 - 22.30"
            "N" -> "22.00 - 06.30"
            "D" -> "07.00 - 17.00"
            "J" -> "08.00 - 20.00 (Jour)"
            else -> "Tider saknas"
        }
    }

    // Maps a shift code to a specific background color for the UI.
    fun getShiftColor(shiftCode: String, isCustom: Boolean = false, hasPass: Boolean = true): Color {
        if (!hasPass || shiftCode.isEmpty() || shiftCode == "-") return Color.White

        // Custom shifts get a default warning/custom color if they don't match standard codes
        if (isCustom && shiftCode.uppercase() !in listOf("F", "E", "N", "D", "J", "SJU", "SEM")) {
            return Color(0xFFFFE0B2) // Light Orange
        }

        return when (shiftCode.uppercase()) {
            "F" -> Color(0xFFD4EDDA) // Ljusgrön
            "E" -> Color(0xFFCCE5FF) // Ljusblå
            "N" -> Color(0xFFE2D9F3) // Lila
            "D" -> Color(0xFFFFF3CD) // Gul
            "J" -> Color(0xFFFFD6D6) // Rosa/Röd
            "ABS", "VAB", "SJU" -> Color(0xFFFFD6D6)
            "SEM" -> Color(0xFFFFF3CD)
            else -> Color.White
        }
    }

    // Returns a detailed, multiline description of the shift.
    // Includes an example of conditional logic (e.g., custom times for the main user on week 5).
    fun getShiftDescription(shiftCode: String, isMainUser: Boolean = false, weekIndex: Int = -1): String {
        return when (shiftCode.uppercase()) {
            "F" -> {
                // Example of a custom schedule override for a specific week
                if (isMainUser && weekIndex == 5) "05.30 - 14.30\n(OBS: Ändrad starttid v. 5)"
                else "06.00 - 14.30\n(Förmiddag - 8h effektiv tid)"
            }
            "E" -> "14.00 - 22.30\n(Eftermiddag - 8h effektiv tid)"
            "N" -> "22.00 - 06.30\n(Nattpass - Inkl. natt-OB)"
            "D" -> "07.00 - 17.00\n(Långdag - Totalt 10 timmar)"
            "J" -> "08.00 - 20.00\n(Beredskapsjour i hemmet/arbetsplats)"
            else -> "Eget inlagt pass / tider"
        }
    }
}