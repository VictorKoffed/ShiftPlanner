package com.example.shiftplanner.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.example.shiftplanner.MainActivity
import com.google.gson.Gson

class ScheduleWidget : GlanceAppWidget() {

    override val stateDefinition = WidgetStateDefinition

    companion object {
        private const val TAG = "ShiftPlanner_Widget"
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance started for id: $id")

        provideContent {
            // 1. Read the saved JSON state from DataStore
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val jsonString = prefs[WidgetUpdater.widgetDataKey]

            // 2. Deserialize the state back into the WidgetInfoData model
            val data = if (jsonString != null) {
                Gson().fromJson(jsonString, WidgetInfoData::class.java)
            } else {
                null
            }

            // 3. Render the UI
            if (data != null) {
                WidgetContent(context, data)
            } else {
                // Fallback loading state
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Laddar schema...",
                        style = TextStyle(color = ColorProvider(Color.White))
                    )
                }
            }
        }
    }

    @Composable
    fun WidgetContent(context: Context, data: WidgetInfoData) {
        val todayDayModel = data.days.find { it.isToday }
        val rawShift = todayDayModel?.shiftCode?.ifEmpty { "LEDIG" } ?: "LEDIG"

        // Format raw codes into readable labels for the widget UI
        val (hugeShiftCode, subShiftText) = when (rawShift.uppercase()) {
            "BY" -> "BY" to "(Ledig/Byte)"
            "SJU" -> "SJUK" to "(Frånvaro)"
            "SEM" -> "SEMESTER" to "(Ledig)"
            "LEDIG", "-" -> "LEDIG" to ""
            else -> rawShift to ""
        }

        Log.d(TAG, "WidgetContent composing with hero code: '$hugeShiftCode'")

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp)
                .clickable(actionStartActivity<MainActivity>()), // Open app on tap
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {

            Spacer(modifier = GlanceModifier.height(16.dp))

            // -- HERO SECTION (Large today status) --
            Text(
                text = data.todayText,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = ColorProvider(Color.White.copy(alpha = 0.9f)),
                    fontWeight = FontWeight.Medium
                )
            )

            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(
                    text = hugeShiftCode,
                    style = TextStyle(
                        fontSize = if (hugeShiftCode.length > 3) 56.sp else 84.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White)
                    )
                )
                if (data.todayHasBlixt) {
                    Text(
                        text = "⚡",
                        style = TextStyle(fontSize = 32.sp),
                        modifier = GlanceModifier.padding(start = 8.dp)
                    )
                }
            }

            if (subShiftText.isNotEmpty()) {
                Text(
                    text = subShiftText,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFFFF8A80))
                    )
                )
            }

            if (data.shiftTime.isNotEmpty() && subShiftText.isEmpty()) {
                Text(
                    text = data.shiftTime,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(Color.White.copy(alpha = 0.8f)),
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Pushes the weekly schedule block to the bottom of the widget
            Spacer(modifier = GlanceModifier.defaultWeight())

            // -- WEEKLY SCHEDULE BOTTOM BAR --
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(
                    text = "Vecka ${data.realWeekNumber}",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorProvider(Color.White.copy(alpha = 0.9f))
                    ),
                    modifier = GlanceModifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = GlanceModifier.fillMaxWidth()
                ) {
                    data.days.forEach { day ->
                        // Determine background highlight based on shift type
                        val cellBgColor = when {
                            day.isPast -> Color.White.copy(alpha = 0.05f)
                            day.colorType == ShiftColorType.GREEN_LIGHT || day.colorType == ShiftColorType.GREEN_DARK -> Color(0xFFC3E6CB).copy(alpha = 0.3f)
                            day.colorType == ShiftColorType.BLUE -> Color(0xFFCCE5FF).copy(alpha = 0.3f)
                            day.colorType == ShiftColorType.PURPLE_LIGHT || day.colorType == ShiftColorType.PURPLE_DARK -> Color(0xFFD8B4FE).copy(alpha = 0.3f)
                            day.colorType == ShiftColorType.RED -> Color(0xFFFFD6D6).copy(alpha = 0.3f)
                            day.colorType == ShiftColorType.YELLOW -> Color(0xFFFFF3CD).copy(alpha = 0.3f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }

                        // Text styling based on past/current day status
                        val textColor = when {
                            day.isPast -> Color.White.copy(alpha = 0.4f)
                            day.isToday -> Color(0xFFFF8A80)
                            else -> Color.White
                        }

                        val cellModifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 2.dp)
                            .background(ColorProvider(cellBgColor))
                            .padding(vertical = 6.dp)

                        Column(
                            modifier = cellModifier,
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                        ) {
                            Text(
                                text = day.dayName,
                                style = TextStyle(fontSize = 9.sp, color = ColorProvider(textColor.copy(alpha = 0.8f)))
                            )

                            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                                Text(
                                    text = day.dayNumber,
                                    style = TextStyle(
                                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        color = ColorProvider(textColor)
                                    )
                                )
                                if (day.hasBlixt) {
                                    Text(text = "⚡", style = TextStyle(fontSize = 8.sp))
                                }
                            }

                            Spacer(modifier = GlanceModifier.height(2.dp))

                            Text(
                                text = day.shiftCode.ifEmpty { "-" },
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorProvider(textColor)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}