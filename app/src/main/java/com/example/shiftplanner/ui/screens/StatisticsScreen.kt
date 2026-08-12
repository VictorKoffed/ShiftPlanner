package com.example.shiftplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.data.db.OvertimeEntry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Helper to identify if a note represents a shift exchange
private fun isExchangeNote(note: String): Boolean {
    val lower = note.lowercase()
    return lower.contains("byt") || lower.contains("utbytt") || note.contains("BY:")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: ScheduleViewModel, navController: NavController) {
    val allOvertime by viewModel.allOvertime.collectAsState(initial = emptyList())
    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val mainUser = colleagues.find { it.isMainUser }

    val locale = Locale("sv", "SE")
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEEE", locale)

    // UI state for filters and search
    var selectedTypeFilter by remember { mutableStateOf("ALLA") }
    var selectedYearFilter by remember { mutableStateOf("ALLA") }
    var searchQuery by remember { mutableStateOf("") }

    var entryToDelete by remember { mutableStateOf<OvertimeEntry?>(null) }

    // Dynamically populate available years based on database entries
    val availableYears = remember(allOvertime) {
        allOvertime.mapNotNull { entry ->
            try {
                LocalDate.parse(entry.dateString).year.toString()
            } catch (e: Exception) {
                null
            }
        }.distinct().sortedDescending()
    }

    // Reactively compute the filtered list of entries based on current search and filters
    val filteredEntries by remember(allOvertime, selectedTypeFilter, selectedYearFilter, searchQuery, mainUser, colleagues) {
        derivedStateOf {
            val mainRow = mainUser?.rowNumber ?: 1

            // Filter out duplicate exchange entries so only the main user's side of the trade is shown
            val cleanedEntries = allOvertime.filter { entry ->
                if (entry.exchangeGroupId != null) {
                    val isMyRow = entry.rowNumber == mainRow || entry.rowNumber >= 1000
                    // Only show the entry where the main user traded away or acquired a shift
                    isMyRow && (entry.shiftCode.uppercase() == "ABSENT" || entry.note.contains("Du har bytt till dig"))
                } else {
                    true
                }
            }

            cleanedEntries.filter { entry ->
                val date = try { LocalDate.parse(entry.dateString) } catch (e: Exception) { null }
                val yearMatches = selectedYearFilter == "ALLA" || (date != null && date.year.toString() == selectedYearFilter)

                val isExchange = entry.exchangeGroupId != null || isExchangeNote(entry.note)
                val isAbsence = entry.shiftCode == "ABSENT" && !isExchange
                val isMainUserRow = entry.rowNumber == mainRow || entry.rowNumber >= 1000
                val isCover = !isAbsence && !isExchange && !isMainUserRow
                val isCustom = !isAbsence && !isExchange && isMainUserRow

                val typeMatches = when (selectedTypeFilter) {
                    "ALLA" -> true
                    "EXCHANGE" -> isExchange
                    "COVER" -> isCover
                    "CUSTOM" -> isCustom
                    "ABSENT" -> isAbsence
                    else -> true
                }

                val targetColleague = colleagues.find { it.rowNumber == entry.rowNumber }
                val colleagueName = targetColleague?.name ?: ""
                val query = searchQuery.trim().lowercase()

                val searchMatches = query.isBlank() ||
                        entry.note.lowercase().contains(query) ||
                        entry.shiftCode.lowercase().contains(query) ||
                        colleagueName.lowercase().contains(query) ||
                        entry.dateString.contains(query)

                yearMatches && typeMatches && searchMatches
            }
        }
    }

    // Group the filtered entries by month and year for a sectioned UI layout
    val groupedEntries by remember(filteredEntries) {
        derivedStateOf {
            filteredEntries.groupBy { entry ->
                try {
                    YearMonth.from(LocalDate.parse(entry.dateString))
                } catch (e: Exception) {
                    YearMonth.now()
                }
            }.toSortedMap(reverseOrder())
        }
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- TOP SUMMARY CARD ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Total översikt", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))

                        val totalExtras = allOvertime.count { it.shiftCode != "ABSENT" && it.exchangeGroupId == null && !isExchangeNote(it.note) }
                        val totalAbsences = allOvertime.count { it.shiftCode == "ABSENT" && it.exchangeGroupId == null && !isExchangeNote(it.note) }
                        val totalExchanges = allOvertime.count { (it.exchangeGroupId != null || isExchangeNote(it.note)) && (it.rowNumber == (mainUser?.rowNumber ?: 1) || it.rowNumber >= 1000) && it.shiftCode == "ABSENT" }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Totalt inhopp / extra pass:", fontSize = 14.sp)
                            Text("$totalExtras st", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Totalt frånvarodagar:", fontSize = 14.sp)
                            Text("$totalAbsences st", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Totalt antal genomförda byten:", fontSize = 14.sp)
                            Text("$totalExchanges st", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            // --- FILTERS & SEARCH ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Sök på namn, pass, anteckning...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Sökikon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Filtrera efter typ:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "ALLA" to "Alla",
                            "EXCHANGE" to "Byten",
                            "COVER" to "Inhopp",
                            "CUSTOM" to "Eget pass",
                            "ABSENT" to "Frånvaro"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = selectedTypeFilter == key,
                                onClick = { selectedTypeFilter = key },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (availableYears.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Filtrera efter år:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedYearFilter == "ALLA",
                                onClick = { selectedYearFilter = "ALLA" },
                                label = { Text("Alla år", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            availableYears.forEach { year ->
                                FilterChip(
                                    selected = selectedYearFilter == year,
                                    onClick = { selectedYearFilter = year },
                                    label = { Text(year, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(color = Color.LightGray)
            }

            // --- RESULT LIST ---
            if (groupedEntries.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Inga poster matchar din sökning.", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                groupedEntries.forEach { (yearMonth, entries) ->
                    val monthTitle = yearMonth.format(monthFormatter).replaceFirstChar { it.uppercase() }

                    item {
                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(entries.size) { index ->
                        val entry = entries[index]
                        val date = try { LocalDate.parse(entry.dateString) } catch (e: Exception) { null }
                        val dayOfWeek = date?.format(dayOfWeekFormatter)?.replaceFirstChar { it.uppercase() } ?: ""
                        val targetColleague = colleagues.find { it.rowNumber == entry.rowNumber }

                        val isExchange = entry.exchangeGroupId != null || isExchangeNote(entry.note)
                        val isAbsence = entry.shiftCode == "ABSENT" && !isExchange
                        val mainRow = mainUser?.rowNumber ?: 1
                        val isMainUserRow = entry.rowNumber == mainRow || entry.rowNumber >= 1000

                        val typeBadgeText = when {
                            isExchange -> "BYTE"
                            isAbsence -> "FRÅNVARO"
                            !isMainUserRow -> "INHOPP"
                            else -> "EGET PASS"
                        }

                        val badgeTextColor = when {
                            isExchange -> Color(0xFF0288D1)
                            isAbsence -> Color.Red
                            else -> MaterialTheme.colorScheme.primary
                        }

                        val badgeBgColor = when {
                            isExchange -> Color(0xFFE1F5FE)
                            isAbsence -> Color.Red.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        }

                        val detailText = when {
                            isExchange -> entry.note.removePrefix("BY: ")
                            isAbsence -> entry.note
                            !isMainUserRow -> "Inhopp för ${targetColleague?.name ?: "kollega"} (Tur: ${entry.shiftCode})"
                            else -> "Pass / Tid: ${entry.shiftCode}"
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAbsence) Color.Red.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = entry.dateString, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                        Text(text = "($dayOfWeek)", fontSize = 12.sp, color = Color.Gray)

                                        Surface(
                                            color = badgeBgColor,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = typeBadgeText,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = badgeTextColor
                                            )
                                        }
                                    }

                                    Text(text = detailText, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)

                                    if (entry.note.isNotBlank() && !isAbsence && !isExchange) {
                                        Text(text = "Anteckning: \"${entry.note}\"", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                TextButton(onClick = { entryToDelete = entry }) {
                                    Text("Ta bort", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (entryToDelete != null) {
        val entry = entryToDelete!!
        val isExchange = entry.exchangeGroupId != null
        val isAbsence = entry.shiftCode == "ABSENT" && !isExchange
        val mainRow = mainUser?.rowNumber ?: 1
        val isMainUserRow = entry.rowNumber == mainRow || entry.rowNumber >= 1000

        val confirmMessage = if (isAbsence) {
            "Vill du verkligen ta bort frånvaron (${entry.note}) den ${entry.dateString}?"
        } else if (isExchange) {
            "Detta är ett passbyte. Om du tar bort detta kommer båda de inblandade dagarna att rensas. Vill du fortsätta?"
        } else if (!isMainUserRow) {
            val targetColleague = colleagues.find { it.rowNumber == entry.rowNumber }
            "Vill du verkligen ta bort inhoppet för ${targetColleague?.name ?: "kollegan"} (Tur: ${entry.shiftCode}) den ${entry.dateString}?"
        } else {
            "Vill du verkligen ta bort det egna passet (${entry.shiftCode}) den ${entry.dateString}?"
        }

        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(if (isExchange) "Bekräfta borttagning av passbyte" else "Bekräfta borttagning") },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (entry.exchangeGroupId != null) {
                            viewModel.deleteOvertimeGroup(entry.exchangeGroupId)
                        } else {
                            viewModel.deleteOvertime(entry)
                        }
                        entryToDelete = null
                    }
                ) {
                    Text("Ta bort", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Avbryt")
                }
            }
        )
    }
}