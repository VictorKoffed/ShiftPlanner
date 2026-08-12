package com.example.shiftplanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.data.db.Colleague

@Composable
fun ColleaguesScreen(viewModel: ScheduleViewModel) {
    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val haptic = LocalHapticFeedback.current

    // UI state for adding/editing colleagues
    var nameInput by remember { mutableStateOf("") }
    var rowInput by remember { mutableStateOf("") }
    var editingColleague by remember { mutableStateOf<Colleague?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // State to track the colleague selected for deletion
    var colleagueToDelete by remember { mutableStateOf<Colleague?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Hantera Kollegor & Schemarader", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Här ser du vem som är kopplad till vilken schemarad. Om en kollega byts ut klickar du bara på pennan för att uppdatera namnet på den raden!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- ADD NEW COLLEAGUE FORM ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Lägg till ny kollega", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Namn (t.ex. Anna)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rowInput,
                    onValueChange = { rowInput = it },
                    label = { Text("Schemarad (1 - 10)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val rowNum = rowInput.toIntOrNull() ?: 0
                            viewModel.addColleague(nameInput, rowNum)
                            nameInput = ""
                            rowInput = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Spara kollega")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Nuvarande personer på schemaraderna:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // --- COLLEAGUES LIST ---
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(colleagues) { colleague ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${colleague.name} ${if (colleague.isMainUser) "(Huvudanvändare ⭐)" else ""}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Schemarad: ${if (colleague.rowNumber > 0) "Rad ${colleague.rowNumber}" else "Ej kopplad"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editingColleague = colleague
                                showDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Redigera")
                            }

                            // Prevent deletion of the main user to ensure the app doesn't break
                            if (!colleague.isMainUser) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    colleagueToDelete = colleague
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Ta bort", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- EDIT COLLEAGUE DIALOG ---
    if (showDialog && editingColleague != null) {
        var editName by remember { mutableStateOf(editingColleague!!.name) }
        var editRow by remember { mutableStateOf(editingColleague!!.rowNumber.toString()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Byt ut / Redigera person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Här ändrar du vem som har raden. Om en ny person tar över raden skriver du bara det nya namnet här nedanför.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Namn") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editRow,
                        onValueChange = { editRow = it },
                        label = { Text("Schemarad (1-10)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val updated = editingColleague!!.copy(
                        name = editName,
                        rowNumber = editRow.toIntOrNull() ?: 0
                    )
                    viewModel.updateColleague(updated)
                    showDialog = false
                }) {
                    Text("Spara ändring")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Avbryt")
                }
            }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (colleagueToDelete != null) {
        val colleague = colleagueToDelete!!
        AlertDialog(
            onDismissRequest = { colleagueToDelete = null },
            title = { Text("Bekräfta borttagning") },
            text = {
                Text("Vill du verkligen ta bort ${colleague.name} (Rad ${colleague.rowNumber})?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteColleague(colleague)
                        colleagueToDelete = null
                    }
                ) {
                    Text("Ta bort", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { colleagueToDelete = null }) {
                    Text("Avbryt")
                }
            }
        )
    }
}