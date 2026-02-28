package eu.swpelc.astrophotoapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun MoonDetailsScreen(state: AstroState) {
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
fun MoonPhaseCanvas(illuminationPct: Int, moonPhase: String, modifier: Modifier = Modifier) {
    val isWaxing = moonPhase in setOf("Waxing Crescent", "First Quarter", "Waxing Gibbous", "New Moon")
    val nightMode = LocalNightMode.current
    val bgColor  = if (nightMode) Color.Black else Color(0xFF1A1A2E)
    val litColor = if (nightMode) Color(0xFF990000) else Color(0xFFDDDDCC)
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(bgColor, r)
        val k = illuminationPct / 100f
        if (k < 0.01f) return@Canvas
        if (k > 0.99f) { drawCircle(litColor, r); return@Canvas }
        val b = r * abs(1.0 - 2.0 * k).toFloat()
        val path = Path()
        val mainRect = Rect(cx - r, cy - r, cx + r, cy + r)
        val ellRect  = Rect(cx - b, cy - r, cx + b, cy + r)
        val sweep1 = if (isWaxing) 180f else -180f
        val crescent = k <= 0.5f
        val sweep2 = if (isWaxing == crescent) -180f else 180f
        path.arcTo(mainRect, -90f, sweep1, forceMoveTo = true)
        path.arcTo(ellRect, 90f, sweep2, forceMoveTo = false)
        path.close()
        drawPath(path, litColor)
    }
}
