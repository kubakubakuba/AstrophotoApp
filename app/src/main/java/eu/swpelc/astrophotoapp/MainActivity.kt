@file:OptIn(kotlin.time.ExperimentalTime::class)

package eu.swpelc.astrophotoapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.jamesyox.kastro.sol.SolarEventSequence
import dev.jamesyox.kastro.sol.SolarEventType
import dev.jamesyox.kastro.luna.LunarEventSequence
import dev.jamesyox.kastro.sol.SolarEvent
import dev.jamesyox.kastro.luna.LunarEvent
import eu.swpelc.astrophotoapp.ui.theme.AstrophotoAppTheme
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlinx.datetime.*
import kotlin.time.Instant
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import kotlin.time.Duration.Companion.days

// --- Data Models ---
data class AppLocation(val lat: Double, val lon: Double, val name: String)
data class SunspotRegion(val lat: Double, val lon: Double, val area: Int)

data class AstroState(
    val sunrise: String = "--:--",
    val sunset: String = "--:--",
    val solarNoon: String = "--:--",
    // Photography
    val goldenHourDawnStart: String = "--:--",
    val goldenHourDawnEnd: String = "--:--",
    val goldenHourDuskStart: String = "--:--",
    val goldenHourDuskEnd: String = "--:--",
    val blueHourDawnStart: String = "--:--",
    val blueHourDawnEnd: String = "--:--",
    val blueHourDuskStart: String = "--:--",
    val blueHourDuskEnd: String = "--:--",
    // Twilight (dawn)
    val civilDawn: String = "--:--",
    val nauticalDawn: String = "--:--",
    val astronomicalDawn: String = "--:--",
    // Twilight (dusk)
    val civilDusk: String = "--:--",
    val nauticalDusk: String = "--:--",
    val astronomicalDusk: String = "--:--",
    // Moon
    val moonrise: String = "--:--",
    val moonset: String = "--:--",
    val moonPhase: String = "--",
    val illumination: String = "0%",
    // Day/Night length
    val dayLength: String = "--",
    val nightLength: String = "--"
)

// --- ViewModel ---
class AstroViewModel : ViewModel() {
    private val _location = MutableStateFlow<AppLocation?>(AppLocation(50.0755, 14.4378, "Prague, CZ"))
    val location = _location.asStateFlow()

    private val _astroState = MutableStateFlow(AstroState())
    val astroState = _astroState.asStateFlow()

    private val _sunspots = MutableStateFlow<List<SunspotRegion>>(emptyList())
    val sunspots = _sunspots.asStateFlow()

    private var astroJob: Job? = null

    init {
        calculateAstroData()
        fetchSunspots()
    }

    fun updateLocation(lat: Double, lon: Double, name: String) {
        _location.value = AppLocation(lat, lon, name)
        calculateAstroData()
    }

    fun calculateAstroData(date: LocalDate = Calendar.getInstance().let { cal ->
        LocalDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }) {
        // Capture location NOW, before the coroutine starts, so the job always
        // computes for the location that triggered it — no race with the next call.
        val loc = _location.value ?: return
        astroJob?.cancel()
        astroJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startOfDayMs = Calendar.getInstance().apply {
                    set(date.year, date.monthNumber - 1, date.dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val startOfDay: Instant = Instant.fromEpochMilliseconds(startOfDayMs)

                // Get ALL solar events for the day
                val solarEvents = SolarEventSequence(
                    start = startOfDay,
                    latitude = loc.lat,
                    longitude = loc.lon,
                    requestedSolarEvents = SolarEventType.all,
                    limit = 1.days
                ).toList()

                ensureActive() // allow cancellation after first heavy sequence

                // Get lunar horizon events for the day
                val lunarEvents = LunarEventSequence(
                    start = startOfDay,
                    latitude = loc.lat,
                    longitude = loc.lon,
                    limit = 1.days
                ).toList()

                ensureActive()

                // Find phases in a ±15-day window using Calendar arithmetic (no Instant subtraction)
                val minus15Ms = startOfDayMs - 15L * 86400_000L
                val phaseWindow = LunarEventSequence(
                    start = Instant.fromEpochMilliseconds(minus15Ms),
                    latitude = loc.lat,
                    longitude = loc.lon,
                    limit = 30.days
                ).filterIsInstance<LunarEvent.PhaseEvent>().toList()

                ensureActive()

                // Determine phase label from the most recent primary phase before today
                val prevPhase = phaseWindow.lastOrNull { it.time <= startOfDay }
                val nextPhase = phaseWindow.firstOrNull { it.time > startOfDay }
                val moonPhaseLabel = when (prevPhase) {
                    is LunarEvent.PhaseEvent.NewMoon -> "Waxing Crescent"
                    is LunarEvent.PhaseEvent.FirstQuarter -> "Waxing Gibbous"
                    is LunarEvent.PhaseEvent.FullMoon -> "Waning Gibbous"
                    is LunarEvent.PhaseEvent.LastQuarter -> "Waning Crescent"
                    else -> when (nextPhase) {
                        is LunarEvent.PhaseEvent.NewMoon -> "Waning Crescent"
                        is LunarEvent.PhaseEvent.FirstQuarter -> "Waxing Crescent"
                        is LunarEvent.PhaseEvent.FullMoon -> "Waxing Gibbous"
                        is LunarEvent.PhaseEvent.LastQuarter -> "Waning Gibbous"
                        else -> "Unknown"
                    }
                }
                // Override with exact name if a phase event falls today
                val todayPhase = lunarEvents.filterIsInstance<LunarEvent.PhaseEvent>().firstOrNull()
                val moonPhase = when (todayPhase) {
                    is LunarEvent.PhaseEvent.NewMoon -> "New Moon"
                    is LunarEvent.PhaseEvent.FirstQuarter -> "First Quarter"
                    is LunarEvent.PhaseEvent.FullMoon -> "Full Moon"
                    is LunarEvent.PhaseEvent.LastQuarter -> "Last Quarter"
                    else -> moonPhaseLabel
                }

                // Illumination via cosine of days-since-new-moon — pure Long arithmetic
                val newMoonRef = phaseWindow.filterIsInstance<LunarEvent.PhaseEvent.NewMoon>()
                    .lastOrNull { it.time <= startOfDay }
                val illumination = if (newMoonRef != null) {
                    val daysFromNew = (startOfDayMs - newMoonRef.time.toEpochMilliseconds())
                        .toDouble() / 86400_000.0
                    val angle = (daysFromNew / 29.53) * 2 * Math.PI
                    ((1 - Math.cos(angle)) / 2 * 100).toInt().coerceIn(0, 100)
                } else 0

                fun fmt(t: kotlin.time.Instant?) =
                    t?.let { formatter.format(Date(it.toEpochMilliseconds())) } ?: "--:--"

                // Day / night length: direct epoch-ms subtraction, completely timezone-independent
                val sunriseEpoch = solarEvents.filterIsInstance<SolarEvent.Sunrise>().firstOrNull()?.time?.toEpochMilliseconds()
                val sunsetEpoch  = solarEvents.filterIsInstance<SolarEvent.Sunset>().firstOrNull()?.time?.toEpochMilliseconds()
                val dayMins = if (sunriseEpoch != null && sunsetEpoch != null && sunsetEpoch > sunriseEpoch)
                    ((sunsetEpoch - sunriseEpoch) / 60_000L).toInt() else null
                val dayLength   = dayMins?.let { "${it / 60}h ${it % 60}m" } ?: "--"
                val nightLength = dayMins?.let { "${(1440 - it) / 60}h ${(1440 - it) % 60}m" } ?: "--"

                // Only write state if this job hasn't been superseded
                ensureActive()
                _astroState.value = AstroState(
                    sunrise = fmt(solarEvents.filterIsInstance<SolarEvent.Sunrise>().firstOrNull()?.time),
                    sunset = fmt(solarEvents.filterIsInstance<SolarEvent.Sunset>().firstOrNull()?.time),
                    solarNoon = fmt(solarEvents.filterIsInstance<SolarEvent.Noon>().firstOrNull()?.time),
                    goldenHourDawnStart = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDawn>().firstOrNull()?.time),
                    goldenHourDawnEnd = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDawnEnd>().firstOrNull()?.time),
                    goldenHourDuskStart = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDusk>().firstOrNull()?.time),
                    goldenHourDuskEnd = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDuskEnd>().firstOrNull()?.time),
                    blueHourDawnStart = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDawn>().firstOrNull()?.time),
                    blueHourDawnEnd = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDawnEnd>().firstOrNull()?.time),
                    blueHourDuskStart = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDusk>().firstOrNull()?.time),
                    blueHourDuskEnd = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDuskEnd>().firstOrNull()?.time),
                    civilDawn = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDawn>().firstOrNull()?.time),
                    nauticalDawn = fmt(solarEvents.filterIsInstance<SolarEvent.NauticalDawn>().firstOrNull()?.time),
                    astronomicalDawn = fmt(solarEvents.filterIsInstance<SolarEvent.AstronomicalDawn>().firstOrNull()?.time),
                    civilDusk = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDusk>().firstOrNull()?.time),
                    nauticalDusk = fmt(solarEvents.filterIsInstance<SolarEvent.NauticalDusk>().firstOrNull()?.time),
                    astronomicalDusk = fmt(solarEvents.filterIsInstance<SolarEvent.AstronomicalDusk>().firstOrNull()?.time),
                    moonrise = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonrise>().firstOrNull()?.time),
                    moonset = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonset>().firstOrNull()?.time),
                    moonPhase = moonPhase,
                    illumination = "$illumination%",
                    dayLength = dayLength,
                    nightLength = nightLength
                )
            } catch (e: Exception) {
                // Rethrow CancellationException so the coroutine machinery knows it was cancelled
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Other exceptions: leave existing state untouched
            }
        }
    }

    fun fetchSunspots() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://services.swpc.noaa.gov/json/solar_regions.json")
                val json = url.readText()
                val array = JSONArray(json)
                val entries = (0 until array.length()).map { array.getJSONObject(it) }
                val latestDate = entries
                    .mapNotNull { it.optString("observed_date").takeIf { d -> d.isNotEmpty() } }
                    .maxOrNull() ?: ""
                _sunspots.value = entries
                    .filter { it.optString("observed_date") == latestDate }
                    .map { SunspotRegion(it.getDouble("latitude"), it.getDouble("longitude"), it.getInt("area")) }
            } catch (_: Exception) { }
        }
    }

}

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstrophotoAppTheme(darkTheme = true) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AstroViewModel = viewModel()) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locationState by viewModel.location.collectAsState()
    val astroState by viewModel.astroState.collectAsState()
    val sunspots by viewModel.sunspots.collectAsState()
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    var currentTimeStr by remember { mutableStateOf(timeFormat.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeStr = timeFormat.format(Date())
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocation(context, viewModel)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation(context, viewModel)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    if (showLocationDialog) {
        var latText by remember { mutableStateOf(locationState?.lat?.toString() ?: "") }
        var lonText by remember { mutableStateOf(locationState?.lon?.toString() ?: "") }
        var nameText by remember { mutableStateOf(locationState?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Set Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameText, onValueChange = { nameText = it },
                        label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = latText, onValueChange = { latText = it },
                        label = { Text("Latitude") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = lonText, onValueChange = { lonText = it },
                        label = { Text("Longitude") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        viewModel.updateLocation(lat, lon, nameText.ifBlank { "Custom" })
                        showLocationDialog = false
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(currentTimeStr, style = MaterialTheme.typography.titleMedium)
                    },
                    actions = {
                        IconButton(onClick = { showLocationDialog = true }) {
                            Icon(Icons.Default.EditLocation, contentDescription = "Manual")
                        }
                        IconButton(onClick = { fetchLocation(context, viewModel) }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "GPS")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            listOf("Configuration", "Location", "Change Date", "Share", "Contact Us").forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = { showMenu = false })
                            }
                        }
                    }
                )
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    val icons = listOf(
                        Icons.Default.WbSunny, Icons.Default.Brightness3, Icons.Default.CalendarToday,
                        Icons.Default.Waves, Icons.Default.CenterFocusStrong
                    )
                    icons.forEachIndexed { index, icon ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = { Icon(icon, null) }
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding)) { page ->
            when (page) {
                0 -> SunDetailsScreen(astroState, sunspots)
                1 -> MoonDetailsScreen(astroState)
                2 -> UnifiedCalendarScreen()
                3 -> AuroraScreen()
                4 -> PolarisClockScreen(viewModel)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(context: Context, viewModel: AstroViewModel) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            location?.let {
                viewModel.updateLocation(it.latitude, it.longitude, "Device Location")
            }
        }
}

// --- Screens ---

@Composable
fun SunDetailsScreen(state: AstroState, sunspots: List<SunspotRegion>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoColumn("Sunrise", state.sunrise, "")
            InfoColumn("Solar Noon", state.solarNoon, "")
            InfoColumn("Sunset", state.sunset, "")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoColumn("Day length", state.dayLength, "")
            InfoColumn("Night length", state.nightLength, "")
        }
        Spacer(modifier = Modifier.height(16.dp))
        SunDiskCanvas(sunspots = sunspots, modifier = Modifier.size(200.dp))
        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader("Dawn")
        TwilightRow("Astronomical", state.astronomicalDawn, state.nauticalDawn)
        TwilightRow("Nautical", state.nauticalDawn, state.civilDawn)
        TwilightRow("Civil", state.civilDawn, state.sunrise)
        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("Dusk")
        TwilightRow("Civil", state.sunset, state.civilDusk)
        TwilightRow("Nautical", state.civilDusk, state.nauticalDusk)
        TwilightRow("Astronomical", state.nauticalDusk, state.astronomicalDusk)
        Spacer(modifier = Modifier.height(8.dp))
        SectionHeader("Photography Windows")
        TwilightRow("Golden Hour (dawn)", state.goldenHourDawnStart, state.goldenHourDawnEnd)
        TwilightRow("Blue Hour (dawn)", state.blueHourDawnStart, state.blueHourDawnEnd)
        TwilightRow("Blue Hour (dusk)", state.blueHourDuskStart, state.blueHourDuskEnd)
        TwilightRow("Golden Hour (dusk)", state.goldenHourDuskStart, state.goldenHourDuskEnd)
    }
}

@Composable
fun MoonDetailsScreen(state: AstroState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InfoColumn("Moonrise", state.moonrise, "")
            InfoColumn("Phase", state.moonPhase, "")
            InfoColumn("Moonset", state.moonset, "")
        }
        Spacer(modifier = Modifier.height(32.dp))
        MoonPhaseCanvas(
            illuminationPct = state.illumination.removeSuffix("%").toIntOrNull() ?: 0,
            moonPhase = state.moonPhase,
            modifier = Modifier.size(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Illumination: ${state.illumination}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun UnifiedCalendarScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Rise/Set", "Twilight", "Moonrise", "Moon Phase")
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) }) }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(31) { index ->
                Card(modifier = Modifier.padding(2.dp).aspectRatio(0.8f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("--:--", fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun AuroraScreen() {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader("Kp Index")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current: 3.0", style = MaterialTheme.typography.headlineMedium)
                Text("Low Activity", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(modifier = Modifier.fillMaxWidth().height(200.dp), shape = MaterialTheme.shapes.medium, color = Color.DarkGray) {
            Box(contentAlignment = Alignment.Center) { Text("Aurora Map Placeholder") }
        }
    }
}

// --- Sidereal / Polaris helpers ---

fun calculateLST(lonDeg: Double, timeMs: Long): Double {
    val jd = timeMs / 86400000.0 + 2440587.5
    val t = (jd - 2451545.0) / 36525.0
    val gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
               t * t * 0.000387933 - t * t * t / 38710000.0
    return ((gmst + lonDeg) % 360.0 + 360.0) % 360.0
}

fun lstToHms(lstDeg: Double): String {
    val hours = lstDeg / 15.0
    val h = hours.toInt()
    val m = ((hours - h) * 60).toInt()
    val s = ((hours - h) * 3600 - m * 60).toInt()
    return "%02d:%02d:%02d".format(h, m, s)
}

fun decimalToDMS(decimal: Double, isLat: Boolean): String {
    val dir = if (isLat) (if (decimal >= 0) "N" else "S") else (if (decimal >= 0) "E" else "W")
    val absVal = abs(decimal)
    val deg = absVal.toInt()
    val minFull = (absVal - deg) * 60
    val min = minFull.toInt()
    val sec = (minFull - min) * 60
    return "%d° %02d' %05.2f\" %s".format(deg, min, sec, dir)
}

@Composable
fun PolarisClockScreen(viewModel: AstroViewModel) {
    val locationState by viewModel.location.collectAsState()
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val loc = locationState
    val lstDeg = if (loc != null) calculateLST(loc.lon, currentTimeMs) else 0.0
    // Polaris RA precesses rapidly — use JNow approximation (linear drift from J2000).
    // J2000 epoch = 2000-01-01T11:58:55 UTC ≈ 946728000000 ms
    val yearsSince2000 = (currentTimeMs - 946728000000L) / 31557600000.0
    val polarisRaDeg = 37.946 + (0.3337 * yearsSince2000)  // ~0.33°/yr RA precession for Polaris
    val hourAngleDeg = ((lstDeg - polarisRaDeg) % 360.0 + 360.0) % 360.0
    val haHours = hourAngleDeg / 15.0
    val haH = haHours.toInt()
    val haM = ((haHours - haH) * 60).toInt()
    val haS = ((haHours - haH) * 3600 - haM * 60).toInt()
    val clockRaw = (12.0 - (haHours / 2.0) + 6.0) % 12.0
    val clockHours = if (clockRaw < 0.0) clockRaw + 12.0 else clockRaw
    val clockH = clockHours.toInt()
    val clockMinFrac = (clockHours % 1.0) * 60.0
    val clockM = clockMinFrac.toInt()
    val clockS = ((clockMinFrac % 1.0) * 60).toInt()

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = timeFormatter.format(Date(currentTimeMs))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(timeStr, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("LST  ${lstToHms(lstDeg)}", style = MaterialTheme.typography.titleMedium)
        Text(
            "HA  %02d:%02d:%02d".format(haH, haM, haS),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = "Polaris  %d:%02d:%02d".format(clockH, clockM, clockS),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(modifier = Modifier.size(280.dp)) {
                val radius = size.minDimension / 2.2f
                for (h in 0 until 12) {
                    val angDeg = 90.0 - h * 30.0
                    val angRad = Math.toRadians(angDeg)
                    val cosA =  cos(angRad).toFloat()
                    val sinA = -sin(angRad).toFloat()
                    val isMajor = h % 3 == 0
                    val tickInner = if (isMajor) radius * 0.82f else radius * 0.90f
                    drawLine(
                        color = if (isMajor) Color.Gray else Color.DarkGray,
                        start = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                        end   = Offset(center.x + radius   * cosA, center.y + radius   * sinA),
                        strokeWidth = if (isMajor) 3f else 1.5f
                    )
                }
                drawCircle(Color.Gray, radius, center = center, style = Stroke(2f))
                drawCircle(Color.Yellow, 4.dp.toPx(), center = center)
                // Dot position: clockHours is a CW 12h clock value (1 = 1 o'clock, etc.)
                val polAngDeg = 90.0 - clockHours * 30.0   // standard math angle, CW on screen
                val polAngRad = Math.toRadians(polAngDeg)
                val cosP =  cos(polAngRad).toFloat()
                val sinP = -sin(polAngRad).toFloat()        // flip y for screen coords
                val polarisCenter = center + Offset(radius * cosP, radius * sinP)
                drawLine(Color.White.copy(alpha = 0.3f), center, polarisCenter)
                drawCircle(Color.White, 10.dp.toPx(), center = polarisCenter)
        }
        Spacer(Modifier.height(20.dp))
        if (loc != null) {
            Text(loc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(decimalToDMS(loc.lat, isLat = true),  style = MaterialTheme.typography.bodyMedium)
            Text(decimalToDMS(loc.lon, isLat = false), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${"%+.6f".format(loc.lat)}°,  ${"%+.6f".format(loc.lon)}°",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        } else {
            Text("No location — tap GPS icon", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
fun SunDiskCanvas(sunspots: List<SunspotRegion>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://sdo.gsfc.nasa.gov/assets/img/latest/latest_1024_HMII.jpg")
                .crossfade(true)
                .build(),
            contentDescription = "Solar disk",
            modifier = Modifier.requiredSize(230.dp),
            contentScale = ContentScale.Crop,
            loading = {
                Canvas(modifier = Modifier.requiredSize(230.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color(0xFFFFCC44),
                            0.6f to Color(0xFFE8890C),
                            1f to Color(0xFFA05000),
                            center = center, radius = r
                        ),
                        radius = r
                    )
                }
            },
            error = {
                Canvas(modifier = Modifier.requiredSize(230.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color(0xFFFFCC44),
                            0.6f to Color(0xFFE8890C),
                            1f to Color(0xFFA05000),
                            center = center, radius = r
                        ),
                        radius = r
                    )
                    for (spot in sunspots) {
                        if (abs(spot.lon) > 90.0) continue
                        val phi = Math.toRadians(spot.lat)
                        val lam = Math.toRadians(spot.lon)
                        val sx = (center.x + r * cos(phi) * sin(lam)).toFloat()
                        val sy = (center.y - r * sin(phi)).toFloat()
                        val spotR = (r * 0.025f + r * 0.055f * (spot.area.coerceAtMost(500) / 500f))
                            .coerceIn(r * 0.02f, r * 0.10f)
                        drawCircle(Color(0xFF3A1200).copy(alpha = 0.8f), spotR, Offset(sx, sy))
                    }
                }
            }
        )
    }
}

@Composable
fun MoonPhaseCanvas(illuminationPct: Int, moonPhase: String, modifier: Modifier = Modifier) {
    // Waxing = lit on right, waning = lit on left
    val isWaxing = moonPhase in setOf("Waxing Crescent", "First Quarter", "Waxing Gibbous", "New Moon")
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Dark background
        drawCircle(Color(0xFF1A1A2E), r)
        val k = illuminationPct / 100f
        if (k < 0.01f) return@Canvas  // New moon - fully dark
        if (k > 0.99f) { drawCircle(Color(0xFFDDDDCC), r); return@Canvas }  // Full moon
        // Terminator ellipse semi-minor axis
        val b = r * abs(1.0 - 2.0 * k).toFloat()
        val path = Path()
        val mainRect = Rect(cx - r, cy - r, cx + r, cy + r)
        val ellRect = Rect(cx - b, cy - r, cx + b, cy + r)
        // sweep1: which semicircle is lit (right=CW=+180 for waxing, left=CCW=-180 for waning)
        val sweep1 = if (isWaxing) 180f else -180f
        // sweep2: terminator direction (determines crescent vs gibbous)
        val crescent = k <= 0.5f
        val sweep2 = if (isWaxing == crescent) -180f else 180f
        path.arcTo(mainRect, -90f, sweep1, forceMoveTo = true)
        path.arcTo(ellRect, 90f, sweep2, forceMoveTo = false)
        path.close()
        drawPath(path, Color(0xFFDDDDCC))
    }
}

@Composable
fun InfoColumn(label: String, value: String, subValue: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        if (subValue.isNotEmpty()) Text(subValue, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun TwilightRow(label: String, start: String, end: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(if (end.isEmpty()) start else "$start - $end")
    }
}
