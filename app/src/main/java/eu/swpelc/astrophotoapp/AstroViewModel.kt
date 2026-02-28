@file:OptIn(kotlin.time.ExperimentalTime::class)

package eu.swpelc.astrophotoapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jamesyox.kastro.sol.SolarEventSequence
import dev.jamesyox.kastro.sol.SolarEventType
import dev.jamesyox.kastro.luna.LunarEventSequence
import dev.jamesyox.kastro.sol.SolarEvent
import dev.jamesyox.kastro.luna.LunarEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class AstroViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("astro_prefs", Context.MODE_PRIVATE)

    private val _location = MutableStateFlow<AppLocation?>(
        prefs.getString("location_name", null)?.let { name ->
            val lat = prefs.getString("location_lat", null)?.toDoubleOrNull()
            val lon = prefs.getString("location_lon", null)?.toDoubleOrNull()
            if (lat != null && lon != null) AppLocation(lat, lon, name) else null
        } ?: AppLocation(50.0755, 14.4378, "Prague, CZ")
    )
    val location = _location.asStateFlow()

    private val _astroState = MutableStateFlow(AstroState())
    val astroState = _astroState.asStateFlow()

    private val _sunspots = MutableStateFlow<List<SunspotRegion>>(emptyList())
    val sunspots = _sunspots.asStateFlow()

    private val _nightMode = MutableStateFlow(prefs.getBoolean("night_mode", false))
    val nightMode = _nightMode.asStateFlow()

    private val _kpIndex = MutableStateFlow<Float?>(null)
    val kpIndex = _kpIndex.asStateFlow()

    private val _locationSearchResults = MutableStateFlow<List<LocationResult>>(emptyList())
    val locationSearchResults = _locationSearchResults.asStateFlow()

    private val _calendarYear    = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    private val _calendarMonth   = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    val calendarYear  = _calendarYear.asStateFlow()
    val calendarMonth = _calendarMonth.asStateFlow()
    private val _calendarData    = MutableStateFlow<Map<Int, CalendarDayData>>(emptyMap())
    val calendarData  = _calendarData.asStateFlow()
    private val _calendarLoading = MutableStateFlow(false)
    val calendarLoading = _calendarLoading.asStateFlow()
    private var calendarJob: Job? = null

    private var astroJob: Job? = null

    // Persists the current pager page across recompositions (e.g. night mode toggle)
    var lastPage: Int = 0

    init {
        calculateAstroData()
        fetchSunspots()
        computeCalendar()
        fetchKpIndex()
    }

    /** Called when the user sets a location manually (search/dialog). Persists it and prevents GPS override on next startup. */
    fun updateLocation(lat: Double, lon: Double, name: String) {
        _location.value = AppLocation(lat, lon, name)
        prefs.edit()
            .putString("location_lat", lat.toString())
            .putString("location_lon", lon.toString())
            .putString("location_name", name)
            .putBoolean("location_is_manual", true)
            .apply()
        calculateAstroData()
        computeCalendar()
    }

    /** Called when the GPS button is explicitly tapped. Saves GPS location and clears the manual flag so future startups can auto-GPS again. */
    fun updateLocationFromGps(lat: Double, lon: Double, name: String) {
        _location.value = AppLocation(lat, lon, name)
        prefs.edit()
            .putString("location_lat", lat.toString())
            .putString("location_lon", lon.toString())
            .putString("location_name", name)
            .putBoolean("location_is_manual", false)
            .apply()
        calculateAstroData()
        computeCalendar()
    }

    /** Returns true if the user has manually set a location that should not be overridden by auto-GPS on startup. */
    fun isLocationManuallySet(): Boolean = prefs.getBoolean("location_is_manual", false)

    fun calculateAstroData(date: LocalDate = Calendar.getInstance().let { cal ->
        LocalDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }) {
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

                val solarEvents = SolarEventSequence(
                    start = startOfDay,
                    latitude = loc.lat,
                    longitude = loc.lon,
                    requestedSolarEvents = SolarEventType.all,
                    limit = 1.days
                ).toList()

                ensureActive()

                val lunarEvents = LunarEventSequence(
                    start = startOfDay,
                    latitude = loc.lat,
                    longitude = loc.lon,
                    limit = 1.days
                ).toList()

                ensureActive()

                val minus15Ms = startOfDayMs - 15L * 86400_000L
                val phaseWindow = LunarEventSequence(
                    start = Instant.fromEpochMilliseconds(minus15Ms),
                    latitude = loc.lat,
                    longitude = loc.lon,
                    limit = 30.days
                ).filterIsInstance<LunarEvent.PhaseEvent>().toList()

                ensureActive()

                val prevPhase = phaseWindow.lastOrNull { it.time <= startOfDay }
                val nextPhase = phaseWindow.firstOrNull { it.time > startOfDay }
                val moonPhaseLabel = when (prevPhase) {
                    is LunarEvent.PhaseEvent.NewMoon      -> "Waxing Crescent"
                    is LunarEvent.PhaseEvent.FirstQuarter -> "Waxing Gibbous"
                    is LunarEvent.PhaseEvent.FullMoon     -> "Waning Gibbous"
                    is LunarEvent.PhaseEvent.LastQuarter  -> "Waning Crescent"
                    else -> when (nextPhase) {
                        is LunarEvent.PhaseEvent.NewMoon      -> "Waning Crescent"
                        is LunarEvent.PhaseEvent.FirstQuarter -> "Waxing Crescent"
                        is LunarEvent.PhaseEvent.FullMoon     -> "Waxing Gibbous"
                        is LunarEvent.PhaseEvent.LastQuarter  -> "Waning Gibbous"
                        else -> "Unknown"
                    }
                }
                val todayPhase = lunarEvents.filterIsInstance<LunarEvent.PhaseEvent>().firstOrNull()
                val moonPhase = when (todayPhase) {
                    is LunarEvent.PhaseEvent.NewMoon      -> "New Moon"
                    is LunarEvent.PhaseEvent.FirstQuarter -> "First Quarter"
                    is LunarEvent.PhaseEvent.FullMoon     -> "Full Moon"
                    is LunarEvent.PhaseEvent.LastQuarter  -> "Last Quarter"
                    else -> moonPhaseLabel
                }

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

                val sunriseEpoch = solarEvents.filterIsInstance<SolarEvent.Sunrise>().firstOrNull()?.time?.toEpochMilliseconds()
                val sunsetEpoch  = solarEvents.filterIsInstance<SolarEvent.Sunset>().firstOrNull()?.time?.toEpochMilliseconds()
                val dayMins = if (sunriseEpoch != null && sunsetEpoch != null && sunsetEpoch > sunriseEpoch)
                    ((sunsetEpoch - sunriseEpoch) / 60_000L).toInt() else null
                val dayLength   = dayMins?.let { "${it / 60}h ${it % 60}m" } ?: "--"
                val nightLength = dayMins?.let { "${(1440 - it) / 60}h ${(1440 - it) % 60}m" } ?: "--"

                ensureActive()
                _astroState.value = AstroState(
                    sunrise              = fmt(solarEvents.filterIsInstance<SolarEvent.Sunrise>().firstOrNull()?.time),
                    sunset               = fmt(solarEvents.filterIsInstance<SolarEvent.Sunset>().firstOrNull()?.time),
                    solarNoon            = fmt(solarEvents.filterIsInstance<SolarEvent.Noon>().firstOrNull()?.time),
                    goldenHourDawnStart  = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDawn>().firstOrNull()?.time),
                    goldenHourDawnEnd    = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDawnEnd>().firstOrNull()?.time),
                    goldenHourDuskStart  = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDusk>().firstOrNull()?.time),
                    goldenHourDuskEnd    = fmt(solarEvents.filterIsInstance<SolarEvent.GoldenHourDuskEnd>().firstOrNull()?.time),
                    blueHourDawnStart    = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDawn>().firstOrNull()?.time),
                    blueHourDawnEnd      = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDawnEnd>().firstOrNull()?.time),
                    blueHourDuskStart    = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDusk>().firstOrNull()?.time),
                    blueHourDuskEnd      = fmt(solarEvents.filterIsInstance<SolarEvent.BlueHourDuskEnd>().firstOrNull()?.time),
                    civilDawn            = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDawn>().firstOrNull()?.time),
                    nauticalDawn         = fmt(solarEvents.filterIsInstance<SolarEvent.NauticalDawn>().firstOrNull()?.time),
                    astronomicalDawn     = fmt(solarEvents.filterIsInstance<SolarEvent.AstronomicalDawn>().firstOrNull()?.time),
                    civilDusk            = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDusk>().firstOrNull()?.time),
                    nauticalDusk         = fmt(solarEvents.filterIsInstance<SolarEvent.NauticalDusk>().firstOrNull()?.time),
                    astronomicalDusk     = fmt(solarEvents.filterIsInstance<SolarEvent.AstronomicalDusk>().firstOrNull()?.time),
                    moonrise             = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonrise>().firstOrNull()?.time),
                    moonset              = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonset>().firstOrNull()?.time),
                    moonPhase            = moonPhase,
                    illumination         = "$illumination%",
                    dayLength            = dayLength,
                    nightLength          = nightLength
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun computeCalendar(year: Int = _calendarYear.value, month: Int = _calendarMonth.value) {
        val loc = _location.value ?: return
        calendarJob?.cancel()
        _calendarLoading.value = true
        calendarJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val result = mutableMapOf<Int, CalendarDayData>()
                val tempCal = Calendar.getInstance().apply { set(year, month - 1, 1) }
                val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (day in 1..daysInMonth) {
                    ensureActive()
                    val startOfDayMs = Calendar.getInstance().apply {
                        set(year, month - 1, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val startOfDay = Instant.fromEpochMilliseconds(startOfDayMs)
                    val solarEvents = SolarEventSequence(
                        start = startOfDay, latitude = loc.lat, longitude = loc.lon,
                        requestedSolarEvents = SolarEventType.all, limit = 1.days
                    ).toList()
                    val lunarEvents = LunarEventSequence(
                        start = startOfDay, latitude = loc.lat, longitude = loc.lon, limit = 1.days
                    ).toList()
                    fun fmt(t: kotlin.time.Instant?) = t?.let { formatter.format(Date(it.toEpochMilliseconds())) } ?: "--:--"
                    val middayMs = startOfDayMs + 43_200_000L
                    result[day] = CalendarDayData(
                        sunrise          = fmt(solarEvents.filterIsInstance<SolarEvent.Sunrise>().firstOrNull()?.time),
                        sunset           = fmt(solarEvents.filterIsInstance<SolarEvent.Sunset>().firstOrNull()?.time),
                        civilDawn        = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDawn>().firstOrNull()?.time),
                        civilDusk        = fmt(solarEvents.filterIsInstance<SolarEvent.CivilDusk>().firstOrNull()?.time),
                        moonrise         = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonrise>().firstOrNull()?.time),
                        moonset          = fmt(lunarEvents.filterIsInstance<LunarEvent.HorizonEvent.Moonset>().firstOrNull()?.time),
                        moonIllumination = moonIlluminationPct(middayMs)
                    )
                }
                ensureActive()
                _calendarData.value = result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _calendarLoading.value = false
            }
        }
    }

    fun calendarPrevMonth() {
        val y = _calendarYear.value; val m = _calendarMonth.value
        if (m == 1) { _calendarYear.value = y - 1; _calendarMonth.value = 12 } else _calendarMonth.value = m - 1
        _calendarData.value = emptyMap()
        computeCalendar(_calendarYear.value, _calendarMonth.value)
    }

    fun calendarNextMonth() {
        val y = _calendarYear.value; val m = _calendarMonth.value
        if (m == 12) { _calendarYear.value = y + 1; _calendarMonth.value = 1 } else _calendarMonth.value = m + 1
        _calendarData.value = emptyMap()
        computeCalendar(_calendarYear.value, _calendarMonth.value)
    }

    fun fetchKpIndex() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONArray(java.net.URL(
                    "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
                ).readText())
                val lastEntry = json.getJSONArray(json.length() - 1)
                _kpIndex.value = lastEntry.getString(1).toFloatOrNull()
            } catch (_: Exception) { }
        }
    }

    fun searchLocation(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val conn = java.net.URL(
                    "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5"
                ).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "AstrophotoApp/1.0")
                val json = JSONArray(conn.inputStream.bufferedReader().readText())
                _locationSearchResults.value = (0 until json.length()).map { i ->
                    val obj = json.getJSONObject(i)
                    val display = obj.getString("display_name")
                    LocationResult(
                        displayName = display,
                        shortName   = display.split(",").take(2).joinToString(",").trim(),
                        lat = obj.getString("lat").toDouble(),
                        lon = obj.getString("lon").toDouble()
                    )
                }
            } catch (_: Exception) { }
        }
    }

    fun clearLocationSearch() {
        _locationSearchResults.value = emptyList()
    }

    fun toggleNightMode() {
        val new = !_nightMode.value
        _nightMode.value = new
        prefs.edit().putBoolean("night_mode", new).apply()
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
