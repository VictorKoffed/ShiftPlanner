package com.example.shiftplanner.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.shiftplanner.utils.ScheduleCalculator
import com.example.shiftplanner.ScheduleViewModel
import com.example.shiftplanner.data.db.OvertimeEntry
import com.example.shiftplanner.utils.ShiftLogicHelper
import com.example.shiftplanner.utils.ShiftUiHelper
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun HomeScreen(viewModel: ScheduleViewModel, navController: NavController) {
    val context = LocalContext.current
    val homeState by viewModel.homeState.collectAsState()
    val locale = Locale("sv", "SE")

    val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    val shortDateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    val dateStringFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val weekFields = WeekFields.ISO
    val currentScheduleWeek = ScheduleCalculator.getCurrentWeekIndex()
    val currentDate = LocalDate.now()

    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val allEntries by viewModel.allScheduleEntries.collectAsState(initial = emptyList())
    val allOvertime by viewModel.allOvertime.collectAsState(initial = emptyList())
    val mainUser = colleagues.find { it.isMainUser }
    val mainUserRow = mainUser?.rowNumber ?: 1

    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val centerIndex = 100
    val exchangePagerState = rememberPagerState(initialPage = centerIndex) { 200 }

    val startOfThisWeek = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val endOfThisWeek = startOfThisWeek.plusDays(6)
    val thisWeekAllDays = (0..6).map { startOfThisWeek.plusDays(it.toLong()) }

    val dayState by remember(homeState.selectedDate) {
        viewModel.getDayScheduleState(homeState.selectedDate)
    }.collectAsState(initial = null)

    // Calculate the "Next Shift" by looking ahead up to 30 days
    val nextShiftInfo = remember(allEntries, allOvertime, mainUserRow) {
        var found: Triple<LocalDate, String, String>? = null
        val today = LocalDate.now()

        for (i in 1..30) {
            val targetDate = today.plusDays(i.toLong())
            val dStr = targetDate.format(dateStringFormatter)
            val wIndex = ScheduleCalculator.getWeekIndexForDate(targetDate)
            val dIndex = targetDate.dayOfWeek.value - 1

            val dayOtEntries = allOvertime.filter { it.dateString == dStr }
            val exchangeGoneEntry = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
            val exchangeCoverEntry = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
            val coverOtEntry = dayOtEntries.find { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }

            val targetRow = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow
            val rawResolvedCode = ShiftLogicHelper.resolveUserShiftCode(targetRow, dStr, wIndex, dIndex, allEntries, allOvertime)
            val resolvedCode = if (exchangeGoneEntry != null) "BY" else if (exchangeCoverEntry != null) exchangeCoverEntry.shiftCode.uppercase() else if (coverOtEntry != null) rawResolvedCode else ShiftLogicHelper.resolveUserShiftCode(mainUserRow, dStr, wIndex, dIndex, allEntries, allOvertime)

            if (resolvedCode.isNotBlank() && resolvedCode != "-" && resolvedCode != "SJU" && resolvedCode != "SEM" && resolvedCode != "BY") {
                val match = com.example.shiftplanner.model.ShiftType.values().find { it.code.equals(resolvedCode, ignoreCase = true) }
                val timeText = match?.time ?: ""
                found = Triple(targetDate, resolvedCode, timeText)
                break
            }
        }
        found
    }

    val thisWeekCalendarRow by remember {
        derivedStateOf {
            thisWeekAllDays.map { date ->
                val dStr = date.format(dateStringFormatter)
                val dayOfWeekIdx = date.dayOfWeek.value - 1

                val dayOtEntries = allOvertime.filter { it.dateString == dStr }

                val exchangeGoneEntry = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
                val exchangeCoverEntry = dayOtEntries.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
                val coverOtEntry = dayOtEntries.find { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }

                val targetRow = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow
                val rawResolvedCode = ShiftLogicHelper.resolveUserShiftCode(targetRow, dStr, currentScheduleWeek, dayOfWeekIdx, allEntries, allOvertime)

                val resolvedCode = if (exchangeGoneEntry != null) "BY" else if (exchangeCoverEntry != null) exchangeCoverEntry.shiftCode.uppercase() else if (coverOtEntry != null) rawResolvedCode else ShiftLogicHelper.resolveUserShiftCode(mainUserRow, dStr, currentScheduleWeek, dayOfWeekIdx, allEntries, allOvertime)

                Triple(date, date.dayOfMonth.toString(), resolvedCode)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // --- SECTION 1: TODAY'S SHIFT (HERO CARD) ---
            item {
                val dateStr = homeState.selectedDate.format(dateStringFormatter)
                val dayOvertime = allOvertime.filter { it.dateString == dateStr }.filter { ot ->
                    if (ot.exchangeGroupId != null) {
                        val isMyRow = ShiftLogicHelper.isMainUserRow(ot.rowNumber, mainUserRow)
                        isMyRow && (ot.shiftCode.uppercase() == "ABSENT" || ot.note.contains("Du har bytt till dig"))
                    } else {
                        true
                    }
                }

                val weekIndex = ScheduleCalculator.getWeekIndexForDate(homeState.selectedDate)
                val dayIndex = homeState.selectedDate.dayOfWeek.value - 1

                val shiftsThisDay = allEntries.filter { it.weekIndex == weekIndex && it.dayIndex == dayIndex }
                val myRegularShift = shiftsThisDay.find { it.rowNumber == mainUserRow }

                val isToday = homeState.selectedDate == currentDate
                val panelBackgroundColor = if (dayState?.activeExchangeNote != null) Color.White
                else ShiftUiHelper.getShiftColor(dayState?.mainUserShiftCode ?: "", isCustom = dayState?.customOvertimeNotes?.isNotEmpty() == true, hasPass = dayState?.mainUserShiftCode?.let { it.isNotEmpty() && it != "-" } == true)

                val baseDateString = homeState.selectedDate.format(fullDateFormatter).replaceFirstChar { it.uppercase() }
                val fullDateString = if (isToday) "$baseDateString (Idag)" else baseDateString

                Card(
                    colors = CardDefaults.cardColors(containerColor = panelBackgroundColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        .border(1.dp, Color.LightGray, CardDefaults.shape)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = fullDateString, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small) {
                                Text(
                                    text = "v. $weekIndex",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // --- READ-ONLY MODE ---
                        AnimatedVisibility(visible = !homeState.isEditingOvertime) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                dayState?.let { state ->
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("DITT PASS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        if (state.activeExchangeNote != null) {
                                            Text("Ledig", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.Black)
                                            Surface(color = Color.Red.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                Text(
                                                    text = "⚡ ${state.activeExchangeNote}",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Red
                                                )
                                            }
                                            if (state.mainUserRegularShift != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Ordinarie pass: ${state.mainUserRegularShift}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            }
                                        } else if (state.mainUserShiftCode.isNotEmpty() && state.mainUserShiftCode != "-") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(state.mainUserShiftCode, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color.Black)

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (state.coverOrExchangeInNote != null) {
                                                        Surface(color = Color.Red.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                            Text("⚡ ${state.coverOrExchangeInNote}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                                        }
                                                    }

                                                    if (state.customOvertimeNotes.isNotEmpty() && !state.hasActiveAbsence) {
                                                        state.customOvertimeNotes.forEach { note ->
                                                            Surface(color = Color.Red.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                                Text("⚡ ${state.mainUserShiftCode} ($note)", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                                            }
                                                        }
                                                    }

                                                    if (state.hasActiveAbsence) {
                                                        val absenceLabel = when (state.mainUserShiftCode) {
                                                            "SJU" -> "🏥 Sjukanmäld"
                                                            "SEM" -> "🌴 Semester"
                                                            else -> "🏥 ${state.absenceNote ?: "Frånvaro"}"
                                                        }
                                                        Surface(color = Color.Red.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                            Text(absenceLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                                        }
                                                    }
                                                }
                                            }

                                            if (state.mainUserShiftCode != "SJU" && state.mainUserShiftCode != "SEM") {
                                                Text(ShiftUiHelper.getShiftDescription(state.mainUserShiftCode, isMainUser = true, weekIndex = weekIndex), fontSize = 14.sp, lineHeight = 18.sp, color = Color.DarkGray)
                                                if (state.mainUserRegularShift != null && (state.mainUserRegularShift != state.mainUserShiftCode || state.coverOrExchangeInNote != null)) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("Ordinarie pass: ${state.mainUserRegularShift}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                                }
                                            } else if (state.mainUserRegularShift != null) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Ordinarie pass: ${state.mainUserRegularShift}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            }
                                        } else {
                                            Text("Ledig", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.Black)
                                            if (state.mainUserRegularShift != null && state.mainUserRegularShift != "-") {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Ordinarie pass: ${state.mainUserRegularShift}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("VILKA SOM JOBBAR DENNA DAG", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        if (state.workingColleagues.isEmpty()) {
                                            Text("Inga andra inlagda på schemat denna dag.", fontSize = 13.sp, color = Color.DarkGray)
                                        } else {
                                            state.workingColleagues.forEach { colleague ->
                                                val shortTime = ShiftUiHelper.getShortShiftTime(colleague.shiftCode)
                                                val otMarker = if (colleague.isOvertime) " ⚡(Övertid)" else ""
                                                Text("• ${colleague.name}: ${colleague.shiftCode} ($shortTime)$otMarker", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setEditingOvertime(true)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Hantera pass & frånvaro", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // --- EDIT MODE ---
                        AnimatedVisibility(visible = homeState.isEditingOvertime) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HorizontalDivider(color = Color.LightGray)
                                Text("Hantera pass & frånvaro", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)

                                if (dayOvertime.isNotEmpty()) {
                                    Text("Bokade tillägg / frånvaro denna dag:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    dayOvertime.forEach { otEntry ->
                                        val targetColleague = colleagues.find { it.rowNumber == otEntry.rowNumber }
                                        val titleDesc = when {
                                            otEntry.exchangeGroupId != null -> {
                                                val cleanNote = otEntry.note.removePrefix("BY: ")
                                                "⚡ $cleanNote"
                                            }
                                            ShiftLogicHelper.isMainUserRow(otEntry.rowNumber, mainUserRow) && (otEntry.shiftCode.uppercase() == "ABSENT" || otEntry.note.lowercase().contains("sjuk") || otEntry.note.lowercase().contains("semester")) -> "🏥 Frånvaro: ${otEntry.note}"
                                            targetColleague != null && targetColleague.rowNumber != mainUserRow -> "⚡ Inhopp för ${targetColleague.name} (${otEntry.shiftCode})"
                                            else -> "⚡ Extra pass / Övertid: ${otEntry.shiftCode}"
                                        }

                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(titleDesc, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                    if (otEntry.note.isNotBlank() && otEntry.shiftCode != "ABSENT" && otEntry.exchangeGroupId == null) {
                                                        Text("\"${otEntry.note}\"", fontSize = 11.sp, color = Color.DarkGray)
                                                    }
                                                }
                                                TextButton(onClick = { viewModel.setEntryToDelete(otEntry) }) {
                                                    Text("Ta bort", color = Color.Red, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                val isFullyLocked = dayState?.activeExchangeNote != null
                                val hasAnyExchangeOnDay = dayOvertime.any { it.exchangeGroupId != null }
                                val hasRegularShiftToday = dayState?.mainUserRegularShift != null && dayState?.mainUserRegularShift != "-"
                                val isCoverOrExchangedIn = dayState?.coverOrExchangeInNote != null
                                val hasActiveAbsence = dayState?.hasActiveAbsence == true

                                if (isFullyLocked) {
                                    Text("Denna dag har ett bortbytt pass och är helt låst. Ta bort bytet för att göra ändringar.", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                } else if (!hasActiveAbsence) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (myRegularShift != null && dayOvertime.isEmpty() && !hasAnyExchangeOnDay) {
                                            FilterChip(
                                                selected = homeState.entryMode == "ABSENCE",
                                                onClick = { viewModel.setEntryMode("ABSENCE", mainUserRow) },
                                                label = { Text("Frånvaro") }, modifier = Modifier.weight(1f)
                                            )
                                        }

                                        if (!isCoverOrExchangedIn && !hasAnyExchangeOnDay) {
                                            FilterChip(
                                                selected = homeState.entryMode == "COVER",
                                                onClick = { viewModel.setEntryMode("COVER", mainUserRow) },
                                                label = { Text("Inhopp") }, modifier = Modifier.weight(1f)
                                            )

                                            if (hasRegularShiftToday) {
                                                FilterChip(
                                                    selected = homeState.entryMode == "EXCHANGE",
                                                    onClick = { viewModel.setEntryMode("EXCHANGE", mainUserRow) },
                                                    label = { Text("Byta pass") }, modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        FilterChip(
                                            selected = homeState.entryMode == "CUSTOM",
                                            onClick = { viewModel.setEntryMode("CUSTOM", mainUserRow) },
                                            label = { Text("Eget pass") }, modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text("Du har anmält frånvaro denna dag. Ta bort frånvaron ovan om du vill kunna lägga till pass.", fontSize = 11.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                                }

                                if (homeState.entryMode == "ABSENCE") {
                                    Text("Markera din frånvaro:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("Sjuk", "Semester").forEach { status ->
                                            Button(
                                                onClick = { viewModel.updateNoteText(status) },
                                                colors = ButtonDefaults.buttonColors(containerColor = if (homeState.noteText == status) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)
                                            ) {
                                                Text(status, fontSize = 11.sp, color = if (homeState.noteText == status) Color.White else Color.Black)
                                            }
                                        }
                                    }
                                } else if (homeState.entryMode == "COVER") {
                                    Text("Välj vem du vill hoppa in för:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    val workingColleagues = colleagues.mapNotNull { colleague ->
                                        val regShift = shiftsThisDay.find { it.rowNumber == colleague.rowNumber }
                                        val otShift = dayOvertime.find { it.rowNumber == colleague.rowNumber }
                                        val code = otShift?.shiftCode ?: regShift?.shiftCode
                                        if (!code.isNullOrBlank() && code != "ABSENT") Triple(colleague, code.uppercase(), otShift != null) else null
                                    }
                                    val availableColleagues = workingColleagues.filter { it.first.rowNumber != mainUserRow }
                                    if (availableColleagues.isEmpty()) {
                                        Text("Inga ordinarie pass inlagda denna dag att ta över.", fontSize = 13.sp, color = Color.Red)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            availableColleagues.forEach { (colleague, code, _) ->
                                                val isSelected = colleague.rowNumber == homeState.selectedColleagueRow
                                                Surface(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray),
                                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.setSelectedColleagueRow(colleague.rowNumber) }
                                                ) {
                                                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text("${colleague.name} (Tur: $code)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (homeState.entryMode == "EXCHANGE") {
                                    if (homeState.exchangeTargetRow == -1) {
                                        Text("Vem vill du byta pass med?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        val otherColleagues = colleagues.filter { it.rowNumber != mainUserRow }
                                        if (otherColleagues.isEmpty()) {
                                            Text("Inga kollegor tillgängliga.", fontSize = 13.sp, color = Color.Red)
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                otherColleagues.forEach { colleague ->
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.Gray),
                                                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setExchangeTarget(colleague.rowNumber, colleague.name) }
                                                    ) {
                                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Text(colleague.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (homeState.exchangeTargetDate == null) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Kollega: ${homeState.exchangeTargetName}", fontSize = 13.sp, color = Color.DarkGray)
                                            TextButton(onClick = { viewModel.clearExchangeTarget() }) { Text("Ändra", fontSize = 12.sp) }
                                        }

                                        Text("Välj i kalendern vilken dag hos ${homeState.exchangeTargetName} du vill byta till:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                        val exchangeYM = remember(exchangePagerState.currentPage) { YearMonth.now().plusMonths((exchangePagerState.currentPage - centerIndex).toLong()) }
                                        val exMonthTitle = exchangeYM.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.uppercase() }

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                                Text(exMonthTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                            }

                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text("v.", modifier = Modifier.width(26.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach { dayName ->
                                                    Text(text = dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            HorizontalPager(state = exchangePagerState, modifier = Modifier.fillMaxWidth()) { page ->
                                                val mPage = YearMonth.now().plusMonths((page - centerIndex).toLong())
                                                val firstDay = mPage.atDay(1)
                                                val leading = firstDay.dayOfWeek.value - 1
                                                val length = mPage.lengthOfMonth()
                                                val daysList = mutableListOf<LocalDate?>()
                                                for (i in 0 until leading) daysList.add(null)
                                                for (d in 1..length) daysList.add(mPage.atDay(d))
                                                while (daysList.size % 7 != 0) daysList.add(null)

                                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    daysList.chunked(7).forEach { weekChunk ->
                                                        val firstValidDate = weekChunk.find { it != null }
                                                        val weekNumber = firstValidDate?.get(weekFields.weekOfWeekBasedYear()) ?: 0

                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                                                                if (weekNumber > 0) Text(text = "$weekNumber", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                            }

                                                            weekChunk.forEach { d ->
                                                                Box(modifier = Modifier.weight(1f).height(36.dp)) {
                                                                    if (d != null) {
                                                                        val isSourceDate = d == homeState.selectedDate
                                                                        val dStr = d.format(dateStringFormatter)
                                                                        val wIdx = ScheduleCalculator.getWeekIndexForDate(d)
                                                                        val dIdx = d.dayOfWeek.value - 1
                                                                        val cCode = ShiftLogicHelper.resolveUserShiftCode(homeState.exchangeTargetRow, dStr, wIdx, dIdx, allEntries, allOvertime)
                                                                        val hasPass = cCode.isNotEmpty() && cCode != "-"

                                                                        Button(
                                                                            onClick = { viewModel.setExchangeTargetDate(d) },
                                                                            enabled = hasPass,
                                                                            contentPadding = PaddingValues(0.dp),
                                                                            colors = ButtonDefaults.buttonColors(containerColor = if (hasPass) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f)),
                                                                            border = if (isSourceDate) BorderStroke(1.dp, Color.Red.copy(alpha = 0.6f)) else null,
                                                                            modifier = Modifier.fillMaxSize()
                                                                        ) {
                                                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                                                Text(text = "${d.dayOfMonth}", fontSize = 9.sp, color = if (isSourceDate) Color.Red else Color.DarkGray, fontWeight = if (isSourceDate) FontWeight.ExtraBold else FontWeight.Normal)
                                                                                Text(if (hasPass) cCode else "-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
                                    } else {
                                        val formattedTargetDate = homeState.exchangeTargetDate?.format(fullDateFormatter)?.replaceFirstChar { it.uppercase() }
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("🤝 Bytet som kommer registreras:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("• Denna dag (${homeState.selectedDate.format(dateStringFormatter)}) övertas av ${homeState.exchangeTargetName}.", fontSize = 11.sp)
                                                Text("• Du tar över ${homeState.exchangeTargetName}s pass den $formattedTargetDate.", fontSize = 11.sp)
                                                TextButton(onClick = { viewModel.setExchangeTargetDate(null) }, contentPadding = PaddingValues(0.dp)) { Text("Ändra dag", fontSize = 11.sp) }
                                            }
                                        }
                                    }
                                } else if (homeState.entryMode == "CUSTOM") {
                                    Text("Skriv in passkod eller tid (t.ex. A, 16-20, 2h):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    OutlinedTextField(value = homeState.customShiftCode, onValueChange = { viewModel.updateCustomShiftCode(it) }, label = { Text("Pass / Tid") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }

                                if (homeState.entryMode.isNotBlank() && homeState.entryMode != "EXCHANGE") {
                                    OutlinedTextField(value = homeState.noteText, onValueChange = { viewModel.updateNoteText(it) }, label = { Text("Anteckning") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }

                                val myCodeOnTargetDay = if (homeState.exchangeTargetDate != null) {
                                    val tDateStr = homeState.exchangeTargetDate!!.format(dateStringFormatter)
                                    val tWIdx = ScheduleCalculator.getWeekIndexForDate(homeState.exchangeTargetDate!!)
                                    val tDIdx = homeState.exchangeTargetDate!!.dayOfWeek.value - 1
                                    ShiftLogicHelper.resolveUserShiftCode(mainUserRow, tDateStr, tWIdx, tDIdx, allEntries, allOvertime)
                                } else ""

                                val isBusyTargetDay = homeState.exchangeTargetDate != null && myCodeOnTargetDay.isNotBlank() && myCodeOnTargetDay != "-" && myCodeOnTargetDay != "BY"

                                if (homeState.entryMode == "EXCHANGE" && isBusyTargetDay) {
                                    Text("⚠️ Du jobbar redan ($myCodeOnTargetDay) den valda dagen och kan inte byta till dig ett till pass.", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (homeState.errorMessage != null) {
                                    Text(text = homeState.errorMessage!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { viewModel.setEditingOvertime(false) }) { Text("Avbryt") }

                                    val workingColleaguesForSave = colleagues.mapNotNull { colleague ->
                                        val regShift = shiftsThisDay.find { it.rowNumber == colleague.rowNumber }
                                        val otShift = dayOvertime.filter { it.dateString == dateStr }.find { it.rowNumber == colleague.rowNumber }
                                        val code = otShift?.shiftCode ?: regShift?.shiftCode
                                        if (!code.isNullOrBlank() && code != "ABSENT") Triple(colleague, code.uppercase(), otShift != null) else null
                                    }

                                    val canSaveCheck = when (homeState.entryMode) {
                                        "ABSENCE" -> homeState.noteText.isNotBlank() && !hasActiveAbsence
                                        "COVER" -> !hasActiveAbsence && homeState.selectedColleagueRow != -1 && workingColleaguesForSave.any { it.first.rowNumber == homeState.selectedColleagueRow }
                                        "EXCHANGE" -> !hasActiveAbsence && homeState.exchangeTargetRow != -1 && homeState.exchangeTargetDate != null && !isBusyTargetDay
                                        "CUSTOM" -> !hasActiveAbsence && homeState.customShiftCode.isNotBlank() && homeState.noteText.isNotBlank()
                                        else -> false
                                    }

                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                            if (homeState.entryMode == "EXCHANGE" && homeState.exchangeTargetRow != -1 && homeState.exchangeTargetDate != null) {
                                                val targetDateStr = homeState.exchangeTargetDate!!.format(dateStringFormatter)
                                                val targetFormattedDate = homeState.exchangeTargetDate!!.format(fullDateFormatter)?.replaceFirstChar { it.uppercase() } ?: ""
                                                val myCurrentShiftToday = myRegularShift?.shiftCode ?: "A"
                                                val myCurrentDateFormatted = homeState.selectedDate.format(fullDateFormatter).replaceFirstChar { it.uppercase() }

                                                val exchangeId = java.util.UUID.randomUUID().toString()

                                                val entryForMeDayA = OvertimeEntry(dateString = dateStr, rowNumber = mainUserRow, shiftCode = "ABSENT", note = "BY: Du har bytt bort detta pass med ${homeState.exchangeTargetName} (Nytt pass: $targetFormattedDate)", exchangeGroupId = exchangeId)
                                                viewModel.saveOvertime(entryForMeDayA)

                                                val entryForColleagueDayA = OvertimeEntry(dateString = dateStr, rowNumber = homeState.exchangeTargetRow, shiftCode = myCurrentShiftToday, note = "BY: Inhopp för ${mainUser?.name ?: "kollega"} (Byte)", exchangeGroupId = exchangeId)
                                                viewModel.saveOvertime(entryForColleagueDayA)

                                                val targetWIdx = ScheduleCalculator.getWeekIndexForDate(homeState.exchangeTargetDate!!)
                                                val targetDIdx = homeState.exchangeTargetDate!!.dayOfWeek.value - 1
                                                val colleagueShiftCodeOnTargetDay = ShiftLogicHelper.resolveUserShiftCode(homeState.exchangeTargetRow, targetDateStr, targetWIdx, targetDIdx, allEntries, allOvertime).ifEmpty { "A" }

                                                val entryForColleagueDayB = OvertimeEntry(dateString = targetDateStr, rowNumber = homeState.exchangeTargetRow, shiftCode = "ABSENT", note = "BY: Bortbytt till ${mainUser?.name ?: "kollega"}", exchangeGroupId = exchangeId)
                                                viewModel.saveOvertime(entryForColleagueDayB)

                                                val entryForMeDayB = OvertimeEntry(dateString = targetDateStr, rowNumber = mainUserRow, shiftCode = colleagueShiftCodeOnTargetDay, note = "BY: Du har bytt till dig detta pass med ${homeState.exchangeTargetName} ($myCurrentDateFormatted)", exchangeGroupId = exchangeId)
                                                viewModel.saveOvertime(entryForMeDayB)

                                            } else {
                                                val targetRow = if (homeState.entryMode == "COVER") homeState.selectedColleagueRow else mainUserRow
                                                val codeToSave = when (homeState.entryMode) {
                                                    "COVER" -> workingColleaguesForSave.find { it.first.rowNumber == homeState.selectedColleagueRow }?.second ?: "A"
                                                    "ABSENT" -> "ABSENT"
                                                    else -> homeState.customShiftCode.uppercase()
                                                }
                                                val finalNote = if (homeState.entryMode == "COVER" && homeState.noteText.isBlank()) "Inhopp" else homeState.noteText

                                                viewModel.saveOvertime(OvertimeEntry(dateString = dateStr, rowNumber = targetRow, shiftCode = codeToSave, note = finalNote))
                                            }

                                            viewModel.setEditingOvertime(false)
                                        },
                                        enabled = canSaveCheck
                                    ) {
                                        Text("Spara")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Denna vecka (v.${currentDate.get(weekFields.weekOfWeekBasedYear())})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach { day -> Text(text = day, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    thisWeekCalendarRow.forEach { (date, dayNum, code) ->
                                        val isPast = date.isBefore(currentDate)
                                        val isToday = date == currentDate
                                        val isSelected = date == homeState.selectedDate

                                        val dStr = date.format(dateStringFormatter)
                                        val dayOtEntries = allOvertime.filter { it.dateString == dStr }

                                        val coverOtEntry = dayOtEntries.find { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }
                                        val hasOtOrCover = dayOtEntries.any { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) } || coverOtEntry != null

                                        val isCustom = dayOtEntries.any { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUser?.rowNumber ?: 1) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null } && dayOtEntries.none { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUser?.rowNumber ?: 1) && it.shiftCode.uppercase() != "ABSENT" }

                                        val rawBg = ShiftUiHelper.getShiftColor(code, isCustom = isCustom, hasPass = code.isNotEmpty() && code != "-")
                                        val cellBg = if (isPast && !isToday) rawBg.copy(alpha = 0.6f) else rawBg

                                        Surface(
                                            color = cellBg, shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(width = if (isSelected) 2.5.dp else if (isToday) 1.5.dp else 0.5.dp, color = if (isSelected) Color.Black else if (isToday) Color.Red else Color.LightGray),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 2.dp)
                                                .heightIn(min = 50.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.updateSelectedDate(date)
                                                    if (isToday) coroutineScope.launch { listState.animateScrollToItem(0) }
                                                }
                                        ) {
                                            Column(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = dayNum,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isToday) Color.Red else if (isPast && !isSelected) Color.Gray else Color.DarkGray
                                                    )
                                                    if (hasOtOrCover) Text(text = "⚡", fontSize = 8.sp, color = if (isPast && !isSelected) Color.Gray else Color.Unspecified)
                                                }
                                                Text(
                                                    text = if (code == "-") "" else code,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isToday) Color.Red else if (isPast && !isSelected) Color.DarkGray else Color.Black,
                                                    maxLines = 1,
                                                    textAlign = TextAlign.Center
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

            if (nextShiftInfo != null) {
                item {
                    val (nextDate, nextCode, nextTime) = nextShiftInfo!!
                    val daysBetween = ChronoUnit.DAYS.between(currentDate, nextDate)
                    val daysText = when (daysBetween) {
                        1L -> "Imorgon"
                        2L -> "I övermorgon"
                        else -> "Om $daysBetween dagar"
                    }
                    val formattedNextDate = nextDate.format(shortDateFormatter).replaceFirstChar { it.uppercase() }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!nextDate.isBefore(startOfThisWeek) && !nextDate.isAfter(endOfThisWeek)) {
                                    viewModel.updateSelectedDate(nextDate)
                                    coroutineScope.launch { listState.animateScrollToItem(0) }
                                } else {
                                    viewModel.updateMonthSelectedDate(nextDate)
                                    navController.navigate("month_view")
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("📅 Nästa pass: $daysText ($formattedNextDate)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Pass $nextCode ${if (nextTime.isNotEmpty()) "• $nextTime" else ""}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                            Text(
                                text = "Visa ➔",
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    homeState.entryToDelete?.let { entry ->
        val targetColleague = colleagues.find { it.rowNumber == entry.rowNumber }
        val isExchange = entry.exchangeGroupId != null
        val isAbsence = entry.shiftCode == "ABSENT" && !isExchange
        val isMyRow = ShiftLogicHelper.isMainUserRow(entry.rowNumber, mainUserRow)

        val confirmMessage = if (isAbsence) {
            "Vill du verkligen ta bort frånvaron (${entry.note}) den ${entry.dateString}?"
        } else if (isExchange) {
            "Detta är ett passbyte. Om du tar bort detta kommer båda de inblandade dagarna att rensas. Vill du fortsätta?"
        } else if (!isMyRow && targetColleague != null) {
            "Vill du verkligen ta bort inhoppet för ${targetColleague.name} (Tur: ${entry.shiftCode}) den ${entry.dateString}?"
        } else {
            "Vill du verkligen ta bort det egna passet (${entry.shiftCode}) den ${entry.dateString}?"
        }

        AlertDialog(
            onDismissRequest = { viewModel.setEntryToDelete(null) },
            title = { Text(if (isExchange) "Bekräfta borttagning av passbyte" else "Bekräfta borttagning") },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (entry.exchangeGroupId != null) {
                            viewModel.deleteOvertimeGroup(entry.exchangeGroupId)
                        } else {
                            viewModel.deleteOvertime(entry)
                        }
                        viewModel.setEntryToDelete(null)
                    }
                ) { Text("Ta bort", color = Color.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setEntryToDelete(null) }) { Text("Avbryt") }
            }
        )
    }
}