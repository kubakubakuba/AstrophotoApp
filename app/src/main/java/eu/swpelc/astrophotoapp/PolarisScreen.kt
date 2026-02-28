package eu.swpelc.astrophotoapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    val polarisRaDeg = 37.946 + (0.3337 * yearsSince2000)
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(timeStr, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("LST  ${lstToHms(lstDeg)}", style = MaterialTheme.typography.titleMedium)
        Text(
            "HA  %02d:%02d:%02d".format(haH, haM, haS),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Polaris  %d:%02d:%02d".format(clockH, clockM, clockS),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        val clockAccent  = MaterialTheme.colorScheme.primary
        val clockOutline = MaterialTheme.colorScheme.outline
        val clockFg      = MaterialTheme.colorScheme.onBackground
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
                    color = if (isMajor) clockOutline else clockOutline.copy(alpha = 0.4f),
                    start = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                    end   = Offset(center.x + radius   * cosA, center.y + radius   * sinA),
                    strokeWidth = if (isMajor) 3f else 1.5f
                )
            }
            drawCircle(clockOutline, radius, center = center, style = Stroke(2f))
            drawCircle(clockAccent, 4.dp.toPx(), center = center)
            val polAngDeg = 90.0 - clockHours * 30.0
            val polAngRad = Math.toRadians(polAngDeg)
            val cosP =  cos(polAngRad).toFloat()
            val sinP = -sin(polAngRad).toFloat()
            val polarisCenter = center + Offset(radius * cosP, radius * sinP)
            drawLine(clockFg.copy(alpha = 0.3f), center, polarisCenter)
            drawCircle(clockFg, 10.dp.toPx(), center = polarisCenter)
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
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("No location — tap GPS icon", style = MaterialTheme.typography.bodyMedium)
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
