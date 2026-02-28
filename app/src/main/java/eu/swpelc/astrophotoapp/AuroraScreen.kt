package eu.swpelc.astrophotoapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

@Composable
fun AuroraScreen(viewModel: AstroViewModel) {
    val kpIndex   by viewModel.kpIndex.collectAsState()
    val nightMode  = LocalNightMode.current
    val context    = LocalContext.current

    fun kpLabel(kp: Float) = when {
        kp < 3f -> "Quiet"
        kp < 4f -> "Unsettled"
        kp < 5f -> "Active"
        kp < 6f -> "Minor Storm (G1)"
        kp < 7f -> "Moderate Storm (G2)"
        kp < 8f -> "Strong Storm (G3)"
        kp < 9f -> "Severe Storm (G4)"
        else    -> "Extreme Storm (G5)"
    }
    fun kpColor(kp: Float) = when {
        kp < 3f -> if (nightMode) Color(0xFF882200) else Color(0xFF2E7D32)
        kp < 5f -> if (nightMode) Color(0xFFBB6600) else Color(0xFFF57F17)
        else    -> if (nightMode) Color(0xFFCC2200) else Color(0xFFC62828)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Kp Index ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kp Index", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.fetchKpIndex() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val kp = kpIndex
                if (kp != null) {
                    Text(
                        "Current: ${"%.1f".format(kp)}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        kpLabel(kp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kpColor(kp)
                    )
                } else {
                    Text("Fetching Kp data…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Aurora Forecast ───────────────────────────────────────
        Text(
            "Aurora Forecast (NOAA)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data("https://services.swpc.noaa.gov/images/aurora-forecast-northern-hemisphere.jpg")
                .crossfade(true)
                .build(),
            contentDescription = "NOAA Aurora forecast",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Fit,
            colorFilter = if (nightMode) ColorFilter.tint(Color(0xFFCC2200), BlendMode.Multiply) else null,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        )
    }
}
