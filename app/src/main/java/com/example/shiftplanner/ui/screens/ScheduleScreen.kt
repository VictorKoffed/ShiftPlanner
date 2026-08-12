package com.example.shiftplanner.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.data.db.ScheduleEntry
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel, navController: NavController) {
    var selectedRow by remember { mutableIntStateOf(1) }
    var selectedWeek by remember { mutableIntStateOf(1) }

    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val allEntries by viewModel.allScheduleEntries.collectAsState(initial = emptyList())

    var mondayShift by remember { mutableStateOf("") }
    var tuesdayShift by remember { mutableStateOf("") }
    var wednesdayShift by remember { mutableStateOf("") }
    var thursdayShift by remember { mutableStateOf("") }
    var fridayShift by remember { mutableStateOf("") }
    var saturdayShift by remember { mutableStateOf("") }
    var sundayShift by remember { mutableStateOf("") }

    // States for dropdown menus
    var isRowDropdownExpanded by remember { mutableStateOf(false) }
    var isWeekDropdownExpanded by remember { mutableStateOf(false) }

    // State for the save confirmation dialog
    var showSaveConfirmationDialog by remember { mutableStateOf(false) }
    var savedSummaryText by remember { mutableStateOf("") }

    // Automatically load existing shifts when row or week changes
    LaunchedEffect(selectedRow, selectedWeek, allEntries) {
        val entriesForThisWeek = allEntries.filter { it.rowNumber == selectedRow && it.weekIndex == selectedWeek }
        mondayShift = entriesForThisWeek.find { it.dayIndex == 0 }?.shiftCode?.uppercase() ?: ""
        tuesdayShift = entriesForThisWeek.find { it.dayIndex == 1 }?.shiftCode?.uppercase() ?: ""
        wednesdayShift = entriesForThisWeek.find { it.dayIndex == 2 }?.shiftCode?.uppercase() ?: ""
        thursdayShift = entriesForThisWeek.find { it.dayIndex == 3 }?.shiftCode?.uppercase() ?: ""
        fridayShift = entriesForThisWeek.find { it.dayIndex == 4 }?.shiftCode?.uppercase() ?: ""
        saturdayShift = entriesForThisWeek.find { it.dayIndex == 5 }?.shiftCode?.uppercase() ?: ""
        sundayShift = entriesForThisWeek.find { it.dayIndex == 6 }?.shiftCode?.uppercase() ?: ""
    }

    val currentPersonName = colleagues.find { it.rowNumber == selectedRow }?.name ?: "Person"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Mata in / Ändra pass", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Välj schemarad och vecka via dropdown. Passen sparas med bekräftelsekvittens.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- SELECT SCHEDULE ROW VIA DROPDOWN ---
        Text("Schemarad:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = isRowDropdownExpanded,
            onExpandedChange = { isRowDropdownExpanded = !isRowDropdownExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            val selectedColleagueName = colleagues.find { it.rowNumber == selectedRow }?.name ?: "Rad $selectedRow"
            OutlinedTextField(
                value = "Rad $selectedRow: $selectedColleagueName",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRowDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )

            ExposedDropdownMenu(
                expanded = isRowDropdownExpanded,
                onDismissRequest = { isRowDropdownExpanded = false }
            ) {
                for (r in 1..10) {
                    val name = colleagues.find { it.rowNumber == r }?.name ?: "Rad $r"
                    DropdownMenuItem(
                        text = { Text("Rad $r: $name", fontWeight = if (selectedRow == r) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedRow = r
                            isRowDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SELECT WEEK VIA DROPDOWN ---
        Text("Schemavecka:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = isWeekDropdownExpanded,
            onExpandedChange = { isWeekDropdownExpanded = !isWeekDropdownExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = "Schemavecka $selectedWeek (v.$selectedWeek)",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isWeekDropdownExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
            )

            ExposedDropdownMenu(
                expanded = isWeekDropdownExpanded,
                onDismissRequest = { isWeekDropdownExpanded = false }
            ) {
                for (w in 1..6) {
                    DropdownMenuItem(
                        text = { Text("Schemavecka $w", fontWeight = if (selectedWeek == w) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            selectedWeek = w
                            isWeekDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Redigerar: $currentPersonName (Schemarad $selectedRow, Vecka $selectedWeek)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // --- DAYS AND INPUT FIELDS ---
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { ShiftInputField("Måndag", mondayShift) { mondayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Tisdag", tuesdayShift) { tuesdayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Onsdag", wednesdayShift) { wednesdayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Torsdag", thursdayShift) { thursdayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Fredag", fridayShift) { fridayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Lördag", saturdayShift) { saturdayShift = it.uppercase(Locale.ROOT) } }
            item { ShiftInputField("Söndag", sundayShift) { sundayShift = it.uppercase(Locale.ROOT) } }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SAVE BUTTON ---
        Button(
            onClick = {
                val entries = listOf(
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 0, shiftCode = mondayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 1, shiftCode = tuesdayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 2, shiftCode = wednesdayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 3, shiftCode = thursdayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 4, shiftCode = fridayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 5, shiftCode = saturdayShift),
                    ScheduleEntry(rowNumber = selectedRow, weekIndex = selectedWeek, dayIndex = 6, shiftCode = sundayShift)
                )

                viewModel.saveSchedule(entries)

                val daysList = listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön")
                val codesList = listOf(mondayShift, tuesdayShift, wednesdayShift, thursdayShift, fridayShift, saturdayShift, sundayShift)

                savedSummaryText = daysList.zip(codesList).joinToString("\n") { (day, code) ->
                    "• $day: ${if (code.isBlank()) "Ledig" else code}"
                }

                showSaveConfirmationDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
        ) {
            Text("Spara Vecka $selectedWeek (Rad $selectedRow)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    // --- SAVE CONFIRMATION DIALOG ---
    if (showSaveConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmationDialog = false },
            title = { Text("Pass sparade! ✅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Följande schema har sparats för $currentPersonName (Schemarad $selectedRow, Vecka $selectedWeek):", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = savedSummaryText,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSaveConfirmationDialog = false
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ShiftInputField(day: String, value: String, onValueChange: (String) -> Unit) {
    val fieldBackgroundColor = scheduleGetShiftColor(value, isToday = false)

    Card(
        colors = CardDefaults.cardColors(containerColor = fieldBackgroundColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = day,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    color = Color.Black
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(it.uppercase(Locale.ROOT)) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 50.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black),
                    placeholder = { Text("Pass") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("F", "E", "N", "D", "J", "").forEach { code ->
                    val isSelected = value == code
                    val buttonColor = getButtonColor(code)

                    Button(
                        onClick = { onValueChange(code) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        Text(if (code.isEmpty()) "Ledig" else code, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun getButtonColor(code: String): Color {
    return when (code) {
        "F" -> Color(0xFFD4EDDA)     // Green
        "E" -> Color(0xFFCCE5FF)     // Blue
        "N" -> Color(0xFFE2D9F3)     // Purple
        "D" -> Color(0xFFFFF3CD)     // Yellow
        "J" -> Color(0xFFFFD6D6)     // Red / Pink
        else -> Color(0xFFE2E3E5)    // Neutral gray for "Off"
    }
}

private fun scheduleGetShiftColor(shiftCode: String, isToday: Boolean): Color {
    return when (shiftCode.uppercase()) {
        "F" -> Color(0xFFD4EDDA)
        "E" -> Color(0xFFCCE5FF)
        "N" -> Color(0xFFE2D9F3)
        "D" -> Color(0xFFFFF3CD)
        "J" -> Color(0xFFFFD6D6)
        "ABS", "VAB" -> Color(0xFFFFD6D6)
        "SEM" -> Color(0xFFFFF3CD)
        else -> Color(0xFFF8F9FA)
    }
}