package eu.swpelc.astrophotoapp

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

data class CalendarDayData(
    val sunrise:          String = "--:--",
    val sunset:           String = "--:--",
    val civilDawn:        String = "--:--",
    val civilDusk:        String = "--:--",
    val moonrise:         String = "--:--",
    val moonset:          String = "--:--",
    val moonIllumination: Int    = 0
)

data class LocationResult(
    val displayName: String,
    val shortName:   String,
    val lat: Double,
    val lon: Double
)
