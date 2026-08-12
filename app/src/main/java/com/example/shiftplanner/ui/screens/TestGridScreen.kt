package com.example.shiftplanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shiftplanner.ScheduleViewModel

@Composable
fun TestGridScreen(viewModel: ScheduleViewModel, navController: NavController) {
    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val entries by viewModel.allScheduleEntries.collectAsState(initial = emptyList())

    // Fixed heights to ensure the sticky name column and the scrollable grid align perfectly
    val rowHeight = 44.dp
    val headerHeight = 34.dp
    val subHeaderHeight = 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header and description
        Text(
            text = "Granska hela schemat (6 veckor)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Här får du en fullständig överblick över rullande 6-veckorsschema för alla kollegor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Legend / Color explanation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("F", "E", "N", "D", "J").forEach { code ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(getGridShiftColor(code))
                            .border(0.5.dp, Color.Gray, RoundedCornerShape(3.dp))
                    )
                    Text(text = code, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val days = listOf("M", "T", "O", "T", "F", "L", "S")
        val sortedColleagues = remember(colleagues) { colleagues.sortedBy { it.rowNumber } }

        // --- MAIN GRID LAYOUT ---
        Row(modifier = Modifier.fillMaxWidth()) {

            // 1. FIXED COLUMN (Colleague Names)
            Column(
                modifier = Modifier
                    .width(110.dp) // Adjusted width to save space for the grid
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // "Namn" Header
                Box(
                    modifier = Modifier.height(headerHeight),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = "Namn", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Empty space to align with the "M T O T F L S" row
                Box(modifier = Modifier.height(subHeaderHeight))

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Colleague names list
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortedColleagues.forEachIndexed { index, colleague ->
                        val rowBackgroundColor = if (index % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(rowBackgroundColor)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${colleague.rowNumber}. ${colleague.name}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 2. SCROLLABLE SECTION (Weeks and Shifts)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                // Week headers (Vecka 1, Vecka 2, etc.)
                Row {
                    for (week in 1..6) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .height(headerHeight)
                                .width(36.dp * 7 + 6.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Vecka $week",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Day headers (M, T, O...)
                Row(modifier = Modifier.height(subHeaderHeight), verticalAlignment = Alignment.CenterVertically) {
                    for (week in 1..6) {
                        Row {
                            for (day in days) {
                                Box(
                                    modifier = Modifier.width(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Shift grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sortedColleagues.forEachIndexed { index, colleague ->
                        val rowBackgroundColor = if (index % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .height(rowHeight)
                                .clip(RoundedCornerShape(4.dp))
                                .background(rowBackgroundColor)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (week in 1..6) {
                                Row(modifier = Modifier.padding(end = 8.dp)) {
                                    for (dayIndex in 0..6) {
                                        val shift = entries.find {
                                            it.rowNumber == colleague.rowNumber &&
                                                    it.weekIndex == week &&
                                                    it.dayIndex == dayIndex
                                        }
                                        val shiftCode = shift?.shiftCode?.uppercase() ?: ""
                                        val cellBg = getGridShiftColor(shiftCode)

                                        Box(
                                            modifier = Modifier
                                                .size(width = 36.dp, height = 32.dp)
                                                .padding(1.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(cellBg)
                                                .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = shiftCode,
                                                textAlign = TextAlign.Center,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (shiftCode.isNotEmpty()) Color.Black else Color.Transparent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to resolve specific background colors for the grid view
fun getGridShiftColor(shiftCode: String): Color {
    return when (shiftCode) {
        "F" -> Color(0xFFD4EDDA)     // Light Green
        "E" -> Color(0xFFCCE5FF)     // Light Blue
        "N" -> Color(0xFFE2D9F3)     // Light Purple
        "D" -> Color(0xFFFFF3CD)     // Light Yellow
        "J" -> Color(0xFFFFD6D6)     // Light Red / Pink
        else -> Color(0xFFF8F9FA)    // Neutral fallback for empty/custom shifts
    }
}