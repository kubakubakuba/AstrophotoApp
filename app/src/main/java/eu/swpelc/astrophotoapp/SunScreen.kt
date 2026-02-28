package eu.swpelc.astrophotoapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
fun SunDiskCanvas(sunspots: List<SunspotRegion>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nightMode = LocalNightMode.current
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
            colorFilter = if (nightMode) ColorFilter.tint(Color(0xFFCC2200), BlendMode.Multiply) else null,
            loading = {
                Canvas(modifier = Modifier.requiredSize(230.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = if (nightMode) Brush.radialGradient(
                            0f to Color(0xFFCC2200), 0.6f to Color(0xFF880000), 1f to Color(0xFF330000),
                            center = center, radius = r
                        ) else Brush.radialGradient(
                            0f to Color(0xFF848484), 0.6f to Color(0xFF686868), 1f to Color(0xFF4A4A4A),
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
                        brush = if (nightMode) Brush.radialGradient(
                            0f to Color(0xFFCC2200), 0.6f to Color(0xFF880000), 1f to Color(0xFF330000),
                            center = center, radius = r
                        ) else Brush.radialGradient(
                            0f to Color(0xFF848484), 0.6f to Color(0xFF686868), 1f to Color(0xFF4A4A4A),
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
                        drawCircle(
                            if (nightMode) Color(0xFF220000).copy(alpha = 0.8f) else Color(0xFF3A1200).copy(alpha = 0.8f),
                            spotR, Offset(sx, sy)
                        )
                    }
                }
            }
        )
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
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth(),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun TwilightRow(label: String, start: String, end: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(if (end.isEmpty()) start else "$start - $end")
    }
}
