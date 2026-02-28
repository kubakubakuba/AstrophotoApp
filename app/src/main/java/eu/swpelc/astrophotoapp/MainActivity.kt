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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import eu.swpelc.astrophotoapp.ui.theme.AstrophotoAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val vm: AstroViewModel = viewModel()
            val nightMode by vm.nightMode.collectAsState()
            CompositionLocalProvider(LocalNightMode provides nightMode) {
                if (nightMode) {
                    MaterialTheme(colorScheme = nightColorScheme) {
                        MainScreen(viewModel = vm)
                    }
                } else {
                    AstrophotoAppTheme(darkTheme = true) {
                        MainScreen(viewModel = vm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AstroViewModel = viewModel()) {
    val pagerState = rememberPagerState(initialPage = viewModel.lastPage, pageCount = { 5 })
    LaunchedEffect(pagerState.currentPage) { viewModel.lastPage = pagerState.currentPage }
    val scope = rememberCoroutineScope()
    var showLocationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locationState by viewModel.location.collectAsState()
    val astroState by viewModel.astroState.collectAsState()
    val sunspots by viewModel.sunspots.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()
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
        if (!viewModel.isLocationManuallySet()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fetchLocation(context, viewModel)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }

    if (showLocationDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var latText  by remember { mutableStateOf(locationState?.lat?.toString() ?: "") }
        var lonText  by remember { mutableStateOf(locationState?.lon?.toString() ?: "") }
        var nameText by remember { mutableStateOf(locationState?.name ?: "") }
        val searchResults by viewModel.locationSearchResults.collectAsState()
        AlertDialog(
            onDismissRequest = { showLocationDialog = false; viewModel.clearLocationSearch() },
            title = { Text("Set Location") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ── Search bar ──────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search by name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.searchLocation(searchQuery) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    // ── Search results ──────────────────────────────
                    if (searchResults.isNotEmpty()) {
                        searchResults.forEach { result ->
                            TextButton(
                                onClick = {
                                    nameText    = result.shortName
                                    latText     = "%.6f".format(java.util.Locale.US, result.lat)
                                    lonText     = "%.6f".format(java.util.Locale.US, result.lon)
                                    searchQuery = ""
                                    viewModel.clearLocationSearch()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    result.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                    // ── Manual / auto-filled fields ─────────────────
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
                    val lat = latText.trim().replace(',', '.').toDoubleOrNull()
                    val lon = lonText.trim().replace(',', '.').toDoubleOrNull()
                    if (lat != null && lon != null) {
                        viewModel.updateLocation(lat, lon, nameText.ifBlank { "Custom" })
                        showLocationDialog = false
                        viewModel.clearLocationSearch()
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    viewModel.clearLocationSearch()
                }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(currentTimeStr, style = MaterialTheme.typography.titleMedium)
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleNightMode() }) {
                            Icon(
                                if (nightMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Night mode"
                            )
                        }
                        IconButton(onClick = { showLocationDialog = true }) {
                            Icon(Icons.Default.EditLocation, contentDescription = "Manual")
                        }
                        IconButton(onClick = { fetchLocation(context, viewModel) }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "GPS")
                        }
                    }
                )
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    val icons = listOf(
                        Icons.Default.WbSunny,
                        Icons.Default.Brightness3,
                        Icons.Default.CalendarToday,
                        Icons.Default.Waves,
                        Icons.Default.CenterFocusStrong
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
                2 -> UnifiedCalendarScreen(viewModel)
                3 -> AuroraScreen(viewModel)
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
                viewModel.updateLocationFromGps(it.latitude, it.longitude, "Device Location")
            }
        }
}
