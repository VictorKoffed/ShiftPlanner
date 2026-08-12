package com.example.shiftplanner.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun MonthViewScreen(viewModel: ScheduleViewModel, navController: NavController) {
    val allEntries by viewModel.allScheduleEntries.collectAsState(initial = emptyList())
    val allOvertime by viewModel.allOvertime.collectAsState(initial = emptyList())
    val colleagues by viewModel.allColleagues.collectAsState(initial = emptyList())
    val monthState by viewModel.monthState.collectAsState()
    val mainUser = colleagues.find { it.isMainUser }
    val mainUserRow = mainUser?.rowNumber ?: 1

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val centerIndex = 100
    val pagerState = rememberPagerState(initialPage = centerIndex) { 200 }
    val exchangePagerState = rememberPagerState(initialPage = centerIndex) { 200 }

    val displayedYearMonth = remember(pagerState.currentPage) {
        YearMonth.now().plusMonths((pagerState.currentPage - centerIndex).toLong())
    }

    val currentYearMonth = YearMonth.now()
    val isCurrentMonthDisplayed = displayedYearMonth == currentYearMonth

    val locale = Locale("sv", "SE")
    val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    val dateStringFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val weekFields = WeekFields.of(locale)
    val monthTitle = displayedYearMonth.format(monthFormatter).replaceFirstChar { it.uppercase() }

    val dayState by remember(monthState.selectedDate) {
        viewModel.getDayScheduleState(monthState.selectedDate)
    }.collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setShowMonthPickerDialog(true, displayedYearMonth.year)
                }
            ) {
                Text(
                    text = "$monthTitle ▾",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!isCurrentMonthDisplayed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(centerIndex)
                            }
                            viewModel.updateMonthSelectedDate(LocalDate.now())
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Idag", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "v.",
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach { dayName ->
                Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val monthForPage = remember(page) {
                YearMonth.now().plusMonths((page - centerIndex).toLong())
            }

            val weeksInMonth by remember(monthForPage, allEntries, allOvertime, mainUser) {
                derivedStateOf {
                    val firstDayOfMonth = monthForPage.atDay(1)
                    val dayOfWeek = firstDayOfMonth.dayOfWeek.value
                    val leadingEmptyDays = dayOfWeek - 1
                    val totalDays = monthForPage.lengthOfMonth()

                    val rawDays = mutableListOf<LocalDate?>()
                    for (i in 0 until leadingEmptyDays) { rawDays.add(null) }
                    for (day in 1..totalDays) { rawDays.add(monthForPage.atDay(day)) }

                    while (rawDays.size % 7 != 0) {
                        rawDays.add(null)
                    }

                    rawDays.chunked(7)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeksInMonth.forEach { weekDays ->
                    val firstValidDate = weekDays.find { it != null }
                    val weekNumber = firstValidDate?.get(weekFields.weekOfWeekBasedYear()) ?: 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.width(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "v.$weekNumber",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }

                        weekDays.forEach { date ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                            ) {
                                if (date != null) {
                                    val dateStr = date.format(dateStringFormatter)
                                    val dayOvertime = allOvertime.filter { it.dateString == dateStr }
                                    val weekIndex = ScheduleCalculator.getWeekIndexForDate(date)
                                    val dayIndex = date.dayOfWeek.value - 1

                                    val exchangeGoneEntry = dayOvertime.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() == "ABSENT" }
                                    val exchangeCoverEntry = dayOvertime.find { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.exchangeGroupId != null && it.shiftCode.uppercase() != "ABSENT" }
                                    val coverOtEntry = dayOvertime.find { !ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) && it.shiftCode.uppercase() != "ABSENT" && it.exchangeGroupId == null }

                                    val targetRow = if (coverOtEntry != null) coverOtEntry.rowNumber else mainUserRow
                                    val rawResolvedCode = ShiftLogicHelper.resolveUserShiftCode(targetRow, dateStr, weekIndex, dayIndex, allEntries, allOvertime)

                                    val shiftCode = if (exchangeGoneEntry != null) {
                                        "BY"
                                    } else if (exchangeCoverEntry != null) {
                                        exchangeCoverEntry.shiftCode.uppercase()
                                    } else if (coverOtEntry != null) {
                                        rawResolvedCode
                                    } else {
                                        ShiftLogicHelper.resolveUserShiftCode(mainUserRow, dateStr, weekIndex, dayIndex, allEntries, allOvertime)
                                    }

                                    val hasOtOrCover = dayOvertime.any { ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) } || coverOtEntry != null

                                    val isToday = date == LocalDate.now()
                                    val isPast = date.isBefore(LocalDate.now())
                                    val isSelected = date == monthState.selectedDate

                                    val customOtEntries = dayOvertime.filter {
                                        ShiftLogicHelper.isMainUserRow(it.rowNumber, mainUserRow) &&
                                                it.shiftCode.uppercase() != "ABSENT" &&
                                                it.shiftCode.uppercase() != "SJU" &&
                                                it.shiftCode.uppercase() != "SEM" &&
                                                it.exchangeGroupId == null
                                    }
                                    val isCustom = customOtEntries.isNotEmpty() && coverOtEntry == null && exchangeGoneEntry == null && exchangeCoverEntry == null

                                    val rawBg = ShiftUiHelper.getShiftColor(shiftCode, isCustom = isCustom, hasPass = shiftCode.isNotEmpty() && shiftCode != "-")
                                    val cardBackgroundColor = if (isPast && !isToday) rawBg.copy(alpha = 0.6f) else rawBg

                                    val baseTextColor = if (shiftCode.isNotEmpty()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                    val finalTextColor = if (isToday) Color.Red else if (isPast && !isSelected) Color.DarkGray else baseTextColor

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .animateContentSize()
                                            .border(
                                                width = if (isSelected) 2.5.dp else if (isToday) 2.5.dp else 0.5.dp,
                                                color = if (isSelected) Color.DarkGray
                                                else if (isToday) Color.Red
                                                else Color.LightGray,
                                                shape = CardDefaults.shape
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.updateMonthSelectedDate(date)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${date.dayOfMonth}",
                                                    fontSize = if (isToday) 12.sp else 10.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isToday) Color.Red else if (isPast && !isSelected) Color.Gray else Color.DarkGray
                                                )
                                                if (hasOtOrCover) {
                                                    Text(text = "⚡", fontSize = 8.sp, color = if (isPast && !isSelected) Color.Gray else Color.Unspecified)
                                                }
                                            }

                                            Text(
                                                text = if (shiftCode == "-") "" else shiftCode,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = finalTextColor,
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

        Spacer(modifier = Modifier.height(16.dp))

        monthState.selectedDate.let { date ->
            val dateStr = date.format(dateStringFormatter)
            val dayOvertime = allOvertime.filter { it.dateString == dateStr }.filter { ot ->
                if (ot.exchangeGroupId != null) {
                    val isMyRow = ShiftLogicHelper.isMainUserRow(ot.rowNumber, mainUserRow)
                    isMyRow && (ot.shiftCode.uppercase() == "ABSENT" || ot.note.contains("Du har bytt till dig"))
                } else {
                    true
                }
            }

            val weekIndex = ScheduleCalculator.getWeekIndexForDate(date)
            val dayIndex = date.dayOfWeek.value - 1

            val shiftsThisDay = allEntries.filter { it.weekIndex == weekIndex && it.dayIndex == dayIndex }
            val myRegularShift = shiftsThisDay.find { it.rowNumber == mainUserRow }

            val isToday = date == LocalDate.now()
            val panelBackgroundColor = if (dayState?.activeExchangeNote != null) Color.White
            else ShiftUiHelper.getShiftColor(dayState?.mainUserShiftCode ?: "", isCustom = dayState?.customOvertimeNotes?.isNotEmpty() == true, hasPass = dayState?.mainUserShiftCode?.let { it.isNotEmpty() && it != "-" } == true)

            val baseDateString = date.format(fullDateFormatter).replaceFirstChar { it.uppercase() }
            val fullDateString = if (isToday) "$baseDateString (Idag)" else baseDateString

            Card(
                colors = CardDefaults.cardColors(containerColor = panelBackgroundColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .border(1.5.dp, Color.LightGray, CardDefaults.shape)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fullDateString,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.8f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Schemav. $weekIndex",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                    AnimatedVisibility(
                        visible = !monthState.isEditingOvertime,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            dayState?.let { state ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "DITT PASS",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.DarkGray
                                    )
                                    if (state.activeExchangeNote != null) {
                                        Text(
                                            text = "Ledig",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(color = Color.Red.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                                            Text(
                                                text = "⚡ ${state.activeExchangeNote}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red
                                            )
                                        }
                                        if (state.mainUserRegularShift != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Ordinarie pass: ${state.mainUserRegularShift}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.DarkGray
                                            )
                                        }
                                    } else if (state.mainUserShiftCode.isNotEmpty() && state.mainUserShiftCode != "-") {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = state.mainUserShiftCode,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 20.sp,
                                                color = Color.Black
                                            )

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (state.coverOrExchangeInNote != null) {
                                                    Surface(color = Color.Red.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                                                        Text(
                                                            text = "⚡ ${state.coverOrExchangeInNote}",
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red
                                                        )
                                                    }
                                                }

                                                if (state.customOvertimeNotes.isNotEmpty() && !state.hasActiveAbsence) {
                                                    state.customOvertimeNotes.forEach { note ->
                                                        Surface(color = Color.Red.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                                                            Text(
                                                                text = "⚡ ${state.mainUserShiftCode} ($note)",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Red
                                                            )
                                                        }
                                                    }
                                                }

                                                if (state.hasActiveAbsence) {
                                                    val absenceLabel = when (state.mainUserShiftCode) {
                                                        "SJU" -> "🏥 Sjukanmäld"
                                                        "SEM" -> "🌴 Semester"
                                                        else -> "🏥 ${state.absenceNote ?: "Frånvaro"}"
                                                    }
                                                    Surface(color = Color.Red.copy(alpha = 0.2f), shape = MaterialTheme.shapes.small) {
                                                        Text(
                                                            text = absenceLabel,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (state.mainUserShiftCode != "SJU" && state.mainUserShiftCode != "SEM") {
                                            Text(
                                                text = ShiftUiHelper.getShiftDescription(state.mainUserShiftCode, isMainUser = true, weekIndex = weekIndex),
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
                                                color = Color.Black
                                            )
                                            if (state.mainUserRegularShift != null && (state.mainUserRegularShift != state.mainUserShiftCode || state.coverOrExchangeInNote != null)) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "Ordinarie pass: ${state.mainUserRegularShift}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.DarkGray
                                                )
                                            }
                                        } else {
                                            if (state.mainUserRegularShift != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Ordinarie pass: ${state.mainUserRegularShift}",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.DarkGray
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Ledig",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.Black
                                        )
                                        if (state.mainUserRegularShift != null && state.mainUserRegularShift != "-") {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Ordinarie pass: ${state.mainUserRegularShift}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "VILKA SOM JOBBAR DENNA DAG",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.DarkGray
                                        )

                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.setMonthEditingOvertime(true)
                                                coroutineScope.launch {
                                                    scrollState.animateScrollTo(scrollState.maxValue)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Hantera pass & frånvaro", fontSize = 11.sp)
                                        }
                                    }

                                    if (state.workingColleagues.isEmpty()) {
                                        Text("Inga andra inlagda på schemat denna dag.", fontSize = 14.sp, color = Color.Black)
                                    } else {
                                        state.workingColleagues.forEach { colleague ->
                                            val shortTime = ShiftUiHelper.getShortShiftTime(colleague.shiftCode)
                                            val otMarker = if (colleague.isOvertime) " ⚡(Övertid)" else ""
                                            Text(
                                                text = "• ${colleague.name}: ${colleague.shiftCode} ($shortTime)$otMarker",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = monthState.isEditingOvertime,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Hantera pass & frånvaro", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)

                            if (myRegularShift != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("📅 Ordinarie pass: ${myRegularShift.shiftCode}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            Text("Fast schemalagt pass", fontSize = 11.sp, color = Color.DarkGray)
                                        }
                                    }
                                }
                            }

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

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(titleDesc, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                                if (otEntry.note.isNotBlank() && otEntry.shiftCode != "ABSENT" && otEntry.exchangeGroupId == null) {
                                                    Text("\"${otEntry.note}\"", fontSize = 11.sp, color = Color.DarkGray)
                                                }
                                            }
                                            TextButton(onClick = { viewModel.setMonthEntryToDelete(otEntry) }) {
                                                Text("Ta bort", color = Color.Red, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = Color.LightGray)
                            }

                            val isFullyLocked = dayState?.activeExchangeNote != null
                            val hasAnyExchangeOnDay = dayOvertime.any { it.exchangeGroupId != null }
                            val hasRegularShiftToday = dayState?.mainUserRegularShift != null && dayState?.mainUserRegularShift != "-"
                            val hasActiveAbsence = dayState?.hasActiveAbsence == true
                            val isCoverOrExchangedIn = dayState?.coverOrExchangeInNote != null

                            if (isFullyLocked) {
                                Text(
                                    text = "Denna dag har ett bortbytt pass och är helt låst. Ta bort bytet för att göra ändringar.",
                                    fontSize = 12.sp,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (!hasActiveAbsence) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (myRegularShift != null && dayOvertime.isEmpty() && !hasAnyExchangeOnDay) {
                                        FilterChip(
                                            selected = monthState.entryMode == "ABSENCE",
                                            onClick = { viewModel.setMonthEntryMode("ABSENCE", mainUserRow) },
                                            label = { Text("Frånvaro") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (!isCoverOrExchangedIn && !hasAnyExchangeOnDay) {
                                        FilterChip(
                                            selected = monthState.entryMode == "COVER",
                                            onClick = { viewModel.setMonthEntryMode("COVER", mainUserRow) },
                                            label = { Text("Inhopp") },
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (hasRegularShiftToday) {
                                            FilterChip(
                                                selected = monthState.entryMode == "EXCHANGE",
                                                onClick = { viewModel.setMonthEntryMode("EXCHANGE", mainUserRow) },
                                                label = { Text("Byta pass") },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    FilterChip(
                                        selected = monthState.entryMode == "CUSTOM",
                                        onClick = { viewModel.setMonthEntryMode("CUSTOM", mainUserRow) },
                                        label = { Text("Eget pass") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Du har anmält frånvaro denna dag. Ta bort frånvaron ovan om du vill kunna lägga till pass.",
                                    fontSize = 11.sp,
                                    color = Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (monthState.entryMode == "ABSENCE") {
                                Text("Markera din frånvaro:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("Sjuk", "Semester").forEach { status ->
                                        Button(
                                            onClick = { viewModel.updateMonthNoteText(status) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (monthState.noteText == status) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(2.dp)
                                        ) {
                                            Text(status, fontSize = 11.sp, color = if (monthState.noteText == status) Color.White else Color.Black)
                                        }
                                    }
                                }
                            } else if (monthState.entryMode == "COVER") {
                                Text("Välj vem du vill hoppa in för:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                val workingColleagues = colleagues.mapNotNull { colleague ->
                                    val regShift = shiftsThisDay.find { it.rowNumber == colleague.rowNumber }
                                    val otShift = dayOvertime.find { it.rowNumber == colleague.rowNumber }
                                    val code = otShift?.shiftCode ?: regShift?.shiftCode
                                    if (!code.isNullOrBlank() && code != "ABSENT") {
                                        Triple(colleague, code.uppercase(), otShift != null)
                                    } else null
                                }
                                val availableColleagues = workingColleagues.filter { it.first.rowNumber != mainUserRow }
                                if (availableColleagues.isEmpty()) {
                                    Text("Inga ordinarie pass inlagda denna dag att ta över.", fontSize = 13.sp, color = Color.Red)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        availableColleagues.forEach { (colleague, code, _) ->
                                            val isSelected = colleague.rowNumber == monthState.selectedColleagueRow
                                            Surface(
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.setMonthSelectedColleagueRow(colleague.rowNumber) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${colleague.name} (Tur: $code)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (monthState.entryMode == "EXCHANGE") {
                                if (monthState.exchangeTargetRow == -1) {
                                    Text("Vem vill du byta pass med?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                    val otherColleagues = colleagues.filter { it.rowNumber != mainUserRow }
                                    if (otherColleagues.isEmpty()) {
                                        Text("Inga kollegor tillgängliga.", fontSize = 13.sp, color = Color.Red)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            otherColleagues.forEach { colleague ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color.Gray),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.setMonthExchangeTarget(colleague.rowNumber, colleague.name) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(colleague.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (monthState.exchangeTargetDate == null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Kollega: ${monthState.exchangeTargetName}", fontSize = 13.sp, color = Color.DarkGray)
                                        TextButton(onClick = { viewModel.clearMonthExchangeTarget() }) {
                                            Text("Ändra", fontSize = 12.sp)
                                        }
                                    }
                                    Text("Välj i kalendern vilken dag hos ${monthState.exchangeTargetName} du vill byta till:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)

                                    val exchangeYM = remember(exchangePagerState.currentPage) {
                                        YearMonth.now().plusMonths((exchangePagerState.currentPage - centerIndex).toLong())
                                    }
                                    val exMonthTitle = exchangeYM.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).replaceFirstChar { it.uppercase() }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(exMonthTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        }

                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Text("v.", modifier = Modifier.width(26.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            listOf("Mån", "Tis", "Ons", "Tor", "Fre", "Lör", "Sön").forEach { dayName ->
                                                Text(text = dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        HorizontalPager(
                                            state = exchangePagerState,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { page ->
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
                                                            Box(modifier = Modifier.weight(1f).heightIn(min = 36.dp)) {
                                                                if (d != null) {
                                                                    val isSourceDate = d == monthState.selectedDate
                                                                    val dStr = d.format(dateStringFormatter)
                                                                    val wIdx = ScheduleCalculator.getWeekIndexForDate(d)
                                                                    val dIdx = d.dayOfWeek.value - 1
                                                                    val cCode = ShiftLogicHelper.resolveUserShiftCode(monthState.exchangeTargetRow, dStr, wIdx, dIdx, allEntries, allOvertime)
                                                                    val hasPass = cCode.isNotEmpty() && cCode != "-"

                                                                    Button(
                                                                        onClick = { viewModel.setMonthExchangeTargetDate(d) },
                                                                        enabled = hasPass,
                                                                        contentPadding = PaddingValues(0.dp),
                                                                        colors = ButtonDefaults.buttonColors(
                                                                            containerColor = if (hasPass) MaterialTheme.colorScheme.primaryContainer else Color.LightGray.copy(alpha = 0.3f)
                                                                        ),
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
                                    val formattedTargetDate = monthState.exchangeTargetDate?.format(fullDateFormatter)?.replaceFirstChar { it.uppercase() }
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("🤝 Bytet som kommer registreras:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("• Denna dag (${date.format(dateStringFormatter)}) övertas av ${monthState.exchangeTargetName}.", fontSize = 11.sp)
                                            Text("• Du tar över ${monthState.exchangeTargetName}s pass den $formattedTargetDate.", fontSize = 11.sp)
                                            TextButton(onClick = { viewModel.setMonthExchangeTargetDate(null) }, contentPadding = PaddingValues(0.dp)) { Text("Ändra dag", fontSize = 11.sp) }
                                        }
                                    }
                                }
                            } else if (monthState.entryMode == "CUSTOM") {
                                Text("Skriv in passkod eller tid (t.ex. A, 16-20, 2h):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                                OutlinedTextField(
                                    value = monthState.customShiftCode,
                                    onValueChange = { viewModel.updateMonthCustomShiftCode(it) },
                                    label = { Text("Pass / Tid") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            HorizontalDivider(color = Color.LightGray)

                            if (monthState.entryMode.isNotBlank() && monthState.entryMode != "EXCHANGE") {
                                OutlinedTextField(
                                    value = monthState.noteText,
                                    onValueChange = { viewModel.updateMonthNoteText(it) },
                                    label = { Text("Anteckning (Ex. Namn / Sjuk)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            val myCodeOnTargetDay = if (monthState.exchangeTargetDate != null) {
                                val tDateStr = monthState.exchangeTargetDate!!.format(dateStringFormatter)
                                val tWIdx = ScheduleCalculator.getWeekIndexForDate(monthState.exchangeTargetDate!!)
                                val tDIdx = monthState.exchangeTargetDate!!.dayOfWeek.value - 1
                                ShiftLogicHelper.resolveUserShiftCode(mainUserRow, tDateStr, tWIdx, tDIdx, allEntries, allOvertime)
                            } else ""

                            val isBusyTargetDay = monthState.exchangeTargetDate != null && myCodeOnTargetDay.isNotBlank() && myCodeOnTargetDay != "-" && myCodeOnTargetDay != "BY"

                            if (monthState.entryMode == "EXCHANGE" && isBusyTargetDay) {
                                Text("⚠️ Du jobbar redan ($myCodeOnTargetDay) den valda dagen och kan inte byta till dig ett till pass.", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (monthState.errorMessage != null) {
                                Text(
                                    text = monthState.errorMessage!!,
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.setMonthEditingOvertime(false) }) {
                                    Text("Avbryt")
                                }

                                val workingColleaguesForSave = colleagues.mapNotNull { colleague ->
                                    val regShift = shiftsThisDay.find { it.rowNumber == colleague.rowNumber }
                                    val otShift = dayOvertime.filter { it.dateString == dateStr }.find { it.rowNumber == colleague.rowNumber }
                                    val code = otShift?.shiftCode ?: regShift?.shiftCode
                                    if (!code.isNullOrBlank() && code != "ABSENT") {
                                        Triple(colleague, code.uppercase(), otShift != null)
                                    } else null
                                }

                                val canSaveCheck = when (monthState.entryMode) {
                                    "ABSENCE" -> monthState.noteText.isNotBlank() && !hasActiveAbsence
                                    "COVER" -> !hasActiveAbsence && monthState.selectedColleagueRow != -1 && workingColleaguesForSave.any { it.first.rowNumber == monthState.selectedColleagueRow }
                                    "EXCHANGE" -> !hasActiveAbsence && monthState.exchangeTargetRow != -1 && monthState.exchangeTargetDate != null && !isBusyTargetDay
                                    "CUSTOM" -> !hasActiveAbsence && monthState.customShiftCode.isNotBlank() && monthState.noteText.isNotBlank()
                                    else -> false
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        if (monthState.entryMode == "EXCHANGE" && monthState.exchangeTargetRow != -1 && monthState.exchangeTargetDate != null) {
                                            val targetDateStr = monthState.exchangeTargetDate!!.format(dateStringFormatter)
                                            val targetFormattedDate = monthState.exchangeTargetDate!!.format(fullDateFormatter)?.replaceFirstChar { it.uppercase() } ?: ""
                                            val myCurrentShiftToday = myRegularShift?.shiftCode ?: "A"
                                            val myCurrentDateFormatted = date.format(fullDateFormatter).replaceFirstChar { it.uppercase() }

                                            val exchangeId = java.util.UUID.randomUUID().toString()

                                            val entryForMeDayA = OvertimeEntry(
                                                dateString = dateStr,
                                                rowNumber = mainUserRow,
                                                shiftCode = "ABSENT",
                                                note = "BY: Du har bytt bort detta pass med ${monthState.exchangeTargetName} (Nytt pass: $targetFormattedDate)",
                                                exchangeGroupId = exchangeId
                                            )
                                            viewModel.saveOvertime(entryForMeDayA)

                                            val entryForColleagueDayA = OvertimeEntry(
                                                dateString = dateStr,
                                                rowNumber = monthState.exchangeTargetRow,
                                                shiftCode = myCurrentShiftToday,
                                                note = "BY: Inhopp för ${mainUser?.name ?: "kollega"} (Byte)",
                                                exchangeGroupId = exchangeId
                                            )
                                            viewModel.saveOvertime(entryForColleagueDayA)

                                            val targetWIdx = ScheduleCalculator.getWeekIndexForDate(monthState.exchangeTargetDate!!)
                                            val targetDIdx = monthState.exchangeTargetDate!!.dayOfWeek.value - 1
                                            val colleagueShiftCodeOnTargetDay = ShiftLogicHelper.resolveUserShiftCode(
                                                rowNumber = monthState.exchangeTargetRow,
                                                dateStr = targetDateStr,
                                                weekIndex = targetWIdx,
                                                dayIndex = targetDIdx,
                                                allEntries = allEntries,
                                                allOvertime = allOvertime
                                            ).ifEmpty { "A" }

                                            val entryForColleagueDayB = OvertimeEntry(
                                                dateString = targetDateStr,
                                                rowNumber = monthState.exchangeTargetRow,
                                                shiftCode = "ABSENT",
                                                note = "BY: Bortbytt till ${mainUser?.name ?: "kollega"}",
                                                exchangeGroupId = exchangeId
                                            )
                                            viewModel.saveOvertime(entryForColleagueDayB)

                                            val entryForMeDayB = OvertimeEntry(
                                                dateString = targetDateStr,
                                                rowNumber = mainUserRow,
                                                shiftCode = colleagueShiftCodeOnTargetDay,
                                                note = "BY: Du har bytt till dig detta pass med ${monthState.exchangeTargetName} ($myCurrentDateFormatted)",
                                                exchangeGroupId = exchangeId
                                            )
                                            viewModel.saveOvertime(entryForMeDayB)

                                        } else {
                                            val targetRow = if (monthState.entryMode == "COVER") monthState.selectedColleagueRow else mainUserRow
                                            val codeToSave = when (monthState.entryMode) {
                                                "COVER" -> workingColleaguesForSave.find { it.first.rowNumber == monthState.selectedColleagueRow }?.second ?: "A"
                                                "ABSENT" -> "ABSENT"
                                                else -> monthState.customShiftCode.uppercase()
                                            }
                                            val finalNote = if (monthState.entryMode == "COVER" && monthState.noteText.isBlank()) "Inhopp" else monthState.noteText

                                            val newEntry = OvertimeEntry(
                                                dateString = dateStr,
                                                rowNumber = targetRow,
                                                shiftCode = codeToSave,
                                                note = finalNote
                                            )
                                            viewModel.saveOvertime(newEntry)
                                        }

                                        viewModel.setMonthEditingOvertime(false)
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
    }

    if (monthState.showMonthPickerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowMonthPickerDialog(false) },
            title = { Text("Välj månad och år") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.updatePickerYear(monthState.pickerYear - 1) }) {
                            Text("◀")
                        }
                        Text(
                            text = "År: ${monthState.pickerYear}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        OutlinedButton(onClick = { viewModel.updatePickerYear(monthState.pickerYear + 1) }) {
                            Text("▶")
                        }
                    }

                    val months = (1..12).map { YearMonth.of(monthState.pickerYear, it) }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 350.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(months) { targetYM ->
                            val isCurrentSelected = targetYM == displayedYearMonth
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setShowMonthPickerDialog(false)
                                    val monthsDifference = (targetYM.year - YearMonth.now().year) * 12 + (targetYM.monthValue - YearMonth.now().monthValue)
                                    val targetPage = centerIndex + monthsDifference
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(targetPage)
                                    }
                                },
                                colors = if (isCurrentSelected) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                contentPadding = PaddingValues(4.dp)
                            ) {
                                Text(
                                    text = targetYM.month.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() },
                                    fontSize = 13.sp,
                                    color = if (isCurrentSelected) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowMonthPickerDialog(false) }) {
                    Text("Stäng")
                }
            }
        )
    }

    monthState.entryToDelete?.let { entry ->
        val targetColleague = colleagues.find { it.rowNumber == entry.rowNumber }
        val isAbsence = entry.shiftCode == "ABSENT" && entry.exchangeGroupId == null
        val isExchange = entry.exchangeGroupId != null

        val confirmMessage = if (isAbsence) {
            "Vill du verkligen ta bort frånvaron (${entry.note}) den ${entry.dateString}?"
        } else if (isExchange) {
            "Detta är ett passbyte. Om du tar bort detta kommer båda de inblandade dagarna att rensas. Vill du fortsätta?"
        } else if (targetColleague != null && targetColleague.rowNumber != mainUser?.rowNumber) {
            "Vill du verkligen ta bort inhoppet för ${targetColleague.name} (Tur: ${entry.shiftCode}) den ${entry.dateString}?"
        } else {
            "Vill du verkligen ta bort det egna passet (${entry.shiftCode}) den ${entry.dateString}?"
        }

        AlertDialog(
            onDismissRequest = { viewModel.setMonthEntryToDelete(null) },
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
                        viewModel.setMonthEntryToDelete(null)
                    }
                ) {
                    Text("Ta bort", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setMonthEntryToDelete(null) }) {
                    Text("Stäng")
                }
            }
        )
    }
}