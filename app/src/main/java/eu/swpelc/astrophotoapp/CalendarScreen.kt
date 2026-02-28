package eu.swpelc.astrophotoapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
fun UnifiedCalendarScreen(viewModel: AstroViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Sun", "Moon", "Phase", "Twilight")
    val nightMode  = LocalNightMode.current
    val year       by viewModel.calendarYear.collectAsState()
    val month      by viewModel.calendarMonth.collectAsState()
    val calData    by viewModel.calendarData.collectAsState()
    val isLoading  by viewModel.calendarLoading.collectAsState()

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    val tempCal = remember(year, month) { Calendar.getInstance().apply { set(year, month - 1, 1) } }
    val daysInMonth    = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOffset = (tempCal.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.calendarPrevMonth() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
            }
            Text(
                "${monthNames[month - 1]} $year",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.calendarNextMonth() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
            }
        }
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 11.sp) }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            dayNames.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (isLoading && calData.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            ) {
                items(firstDayOffset) { Box(modifier = Modifier.aspectRatio(0.65f)) }
                items(daysInMonth) { dayIdx ->
                    CalendarDayCell(
                        day = dayIdx + 1,
                        data = calData[dayIdx + 1],
                        tab = selectedTab,
                        nightMode = nightMode
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(day: Int, data: CalendarDayData?, tab: Int, nightMode: Boolean) {
    val borderColor = if (nightMode) Color(0xFF550000) else MaterialTheme.colorScheme.outline
    OutlinedCard(
        modifier = Modifier.padding(1.dp).aspectRatio(0.65f),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.5f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp, vertical = 2.dp)
        ) {
            Text(day.toString(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            when (tab) {
                0 -> {
                    Text(data?.sunrise ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                    Text(data?.sunset  ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                }
                1 -> {
                    Text(data?.moonrise ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                    Text(data?.moonset  ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                }
                2 -> {
                    Text(
                        "${data?.moonIllumination ?: 0}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                3 -> {
                    Text(data?.civilDawn ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                    Text(data?.civilDusk ?: "--:--", fontSize = 13.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

/** Returns 0–100 moon illumination % for the given epoch millisecond. */
fun moonIlluminationPct(epochMs: Long): Int {
    // Reference new moon: Jan 29, 2025 ~12:35 UTC
    val refNewMoonMs = 1738154100000L
    val synodicMs    = 2_551_442_976.0
    var age = (epochMs - refNewMoonMs) % synodicMs.toLong()
    if (age < 0) age += synodicMs.toLong()
    val illumination = (1 - cos(age / synodicMs * 2 * PI)) / 2 * 100
    return illumination.roundToInt().coerceIn(0, 100)
}
