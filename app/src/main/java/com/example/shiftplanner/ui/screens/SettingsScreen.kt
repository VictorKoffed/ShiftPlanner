package com.example.shiftplanner.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.alarm.AlarmHelper
import com.example.shiftplanner.alarm.AlarmReceiver
import com.example.shiftplanner.ui.navigation.Screen

@Composable
fun SettingsScreen(viewModel: ScheduleViewModel, navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    // State for reminder toggle and time
    var remindersEnabled by remember {
        mutableStateOf(prefs.getBoolean("reminders_enabled", false))
    }
    var reminderTime by remember {
        mutableStateOf(prefs.getString("reminder_time", "20:00") ?: "20:00")
    }

    // Permission launcher for Android 13+ (POST_NOTIFICATIONS)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            AlarmHelper.scheduleNextAlarm(context)
            Toast.makeText(context, "Kvällspåminnelse aktiverad", Toast.LENGTH_SHORT).show()
        } else {
            remindersEnabled = false
            prefs.edit().putBoolean("reminders_enabled", false).apply()
            AlarmHelper.cancelAlarm(context)
            Toast.makeText(context, "Behöver notisbehörighet för att skicka påminnelser", Toast.LENGTH_LONG).show()
        }
    }

    // Helper to display a standard time picker dialog
    fun showTimePicker() {
        val parts = reminderTime.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, hour: Int, minute: Int ->
                val formattedTime = String.format("%02d:%02d", hour, minute)
                reminderTime = formattedTime
                prefs.edit().putString("reminder_time", formattedTime).apply()

                if (remindersEnabled) {
                    AlarmHelper.scheduleNextAlarm(context)
                }

                Toast.makeText(context, "Påminnelsetid sparad till $formattedTime", Toast.LENGTH_SHORT).show()
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Inställningar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Hantera grundschema, kollegor, påminnelser och widget.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(4.dp))

        // SECTION: BASE SCHEDULE & COLLEAGUES
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Grundschema & Personal", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

                Button(
                    onClick = { navController.navigate(Screen.Schedule.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✏️ Mata in / Ändra pass i grundschemat")
                }

                Button(
                    onClick = { navController.navigate(Screen.Colleagues.route) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("👥 Hantera kollegor / radbyte")
                }
            }
        }

        // SECTION: EVENING REMINDERS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kvällspåminnelse", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Få en påminnelse om morgondagens pass.", fontSize = 13.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { checked ->
                            remindersEnabled = checked
                            prefs.edit().putBoolean("reminders_enabled", checked).apply()

                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                                        AlarmHelper.scheduleNextAlarm(context)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    AlarmHelper.scheduleNextAlarm(context)
                                }
                            } else {
                                AlarmHelper.cancelAlarm(context)
                                Toast.makeText(context, "Kvällspåminnelse avstängd", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (remindersEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Påminnelsetid:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable { showTimePicker() }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⏰ Vald tid:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = reminderTime,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text("Klicka på rutan för att ställa in exakt klockslag.", fontSize = 11.sp, color = Color.Gray)

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, AlarmReceiver::class.java)
                                context.sendBroadcast(intent)
                                Toast.makeText(context, "Skickar testnotis nu...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Testa notis nu (direkt)")
                        }
                    }
                }
            }
        }

        // SECTION: WIDGET
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hemskärmswidget", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Se morgondagens pass direkt på telefonens startskärm.", fontSize = 13.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "Håll fingret på telefonens hemskärm och välj 'Widgets' för att lägga till ShiftPlanner.", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Visa hur man lägger till widget", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}