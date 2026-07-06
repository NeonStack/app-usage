package com.custom.timelinelog

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Font definitions using Montserrat fonts downloaded to resources
val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_bold, FontWeight.Bold)
)

// UI Color Palette (Slate & Emerald Dark Theme)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Emerald500 = Color(0xFF10B981)
val Emerald900 = Color(0xFF064E3B)
val Emerald50 = Color(0xFFECFDF5)
val Rose500 = Color(0xFFF43F5E)

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Color.White,
    primaryContainer = Emerald900,
    onPrimaryContainer = Emerald50,
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate300
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
fun TimelineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}

// Data models for the processed list items
sealed interface ProcessedTimelineItem {
    val startTime: Long
    val endTime: Long
    val durationMs: Long

    data class Session(
        val packageName: String,
        val appName: String,
        override val startTime: Long,
        override val endTime: Long,
        override val durationMs: Long,
        val icon: ImageBitmap?
    ) : ProcessedTimelineItem

    data class Gap(
        override val startTime: Long,
        override val endTime: Long,
        override val durationMs: Long
    ) : ProcessedTimelineItem
}

// Temporary items used only during core calculation
private data class RawSession(
    val packageName: String,
    val startTime: Long,
    val endTime: Long
)

data class AppMetadata(
    val appName: String,
    val icon: ImageBitmap?
)

sealed interface UiState {
    object Loading : UiState
    object PermissionRequired : UiState
    data class Success(val items: List<ProcessedTimelineItem>) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun loadData(context: Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            val hasPermission = checkPermission(context)
            if (!hasPermission) {
                _uiState.value = UiState.PermissionRequired
                return@launch
            }

            try {
                val data = withContext(Dispatchers.IO) {
                    fetchTimelineData(context)
                }
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to query usage stats")
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must be called before setContent to avoid insets crash on API 29+
        enableEdgeToEdge()

        setContent {
            TimelineTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = uiState) {
                        is UiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Emerald500)
                            }
                        }
                        is UiState.PermissionRequired -> {
                            PermissionRequiredScreen(
                                onGrantClick = {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                    try {
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                    }
                                }
                            )
                        }
                        is UiState.Success -> {
                            // Fix: use this@MainActivity to correctly reference the Activity
                            TimelineDashboard(
                                items = state.items,
                                onRefresh = { viewModel.loadData(this@MainActivity) }
                            )
                        }
                        is UiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Rose500,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = state.message,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = { viewModel.loadData(this@MainActivity) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Only reload if not already showing data, to avoid spamming re-queries
        // when the user navigates back from within the app itself.
        val current = viewModel.uiState.value
        if (current !is UiState.Success) {
            viewModel.loadData(this)
        } else {
            // Always re-check permission in case user revoked it in settings
            viewModel.loadData(this)
        }
    }
}

// Check Usage Access permission
private fun checkPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

// Fetch logs and construct chronological timeline on the background coroutine
private fun fetchTimelineData(context: Context): List<ProcessedTimelineItem> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val endTime = System.currentTimeMillis()
    val startTime = endTime - 7 * 24 * 60 * 60 * 1000L

    // 1. Resolve launchers programmatically to filter them out of the logs
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolveInfos = packageManager.queryIntentActivities(
        launcherIntent,
        PackageManager.MATCH_DEFAULT_ONLY
    )
    val launcherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()

    val systemPackages = setOf(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.gms",
        context.packageName
    )
    val filterPackages = systemPackages + launcherPackages

    // 2. Query event logs
    val events = usageStatsManager.queryEvents(startTime, endTime)
    val rawEvents = mutableListOf<UsageEvents.Event>()

    while (events.hasNextEvent()) {
        val event = UsageEvents.Event()
        events.getNextEvent(event)
        if (event.packageName !in filterPackages) {
            rawEvents.add(event)
        }
    }

    // Sort chronologically
    rawEvents.sortBy { it.timeStamp }

    // 3. Pair activity resumed and paused events
    val rawSessions = mutableListOf<RawSession>()
    var activeSessionPackage: String? = null
    var activeSessionStartTime: Long = 0L

    for (event in rawEvents) {
        val eventType = event.eventType
        val pkg = event.packageName
        val time = event.timeStamp

        when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                // Ghost App Fallback: If A was running, and we see B resume,
                // assume A closed at B's start time instead of letting it run indefinitely.
                if (activeSessionPackage != null) {
                    if (activeSessionPackage != pkg) {
                        val duration = time - activeSessionStartTime
                        if (duration > 1000L) { // Filter out micro sessions (< 1 sec)
                            rawSessions.add(RawSession(activeSessionPackage, activeSessionStartTime, time))
                        }
                        activeSessionPackage = pkg
                        activeSessionStartTime = time
                    }
                } else {
                    activeSessionPackage = pkg
                    activeSessionStartTime = time
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                if (activeSessionPackage == pkg) {
                    val duration = time - activeSessionStartTime
                    if (duration > 1000L) {
                        rawSessions.add(RawSession(pkg, activeSessionStartTime, time))
                    }
                    activeSessionPackage = null
                }
            }
        }
    }

    // Cap the last active session at query time if still running
    if (activeSessionPackage != null) {
        val duration = endTime - activeSessionStartTime
        if (duration > 1000L) {
            rawSessions.add(RawSession(activeSessionPackage, activeSessionStartTime, endTime))
        }
    }

    // 4. Extract and Cache app labels and icons on this background thread
    val timelineItems = mutableListOf<ProcessedTimelineItem>()
    if (rawSessions.isNotEmpty()) {
        val appMetadataCache = mutableMapOf<String, AppMetadata>()
        
        fun getAppMetadata(packageName: String): AppMetadata {
            return appMetadataCache.getOrPut(packageName) {
                var appLabel = packageName
                var iconBitmap: ImageBitmap? = null
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    appLabel = packageManager.getApplicationLabel(appInfo).toString()
                    val drawable = packageManager.getApplicationIcon(appInfo)
                    iconBitmap = drawableToBitmap(drawable).asImageBitmap()
                } catch (e: Exception) {
                    // Fallback to package name and null icon
                }
                AppMetadata(appLabel, iconBitmap)
            }
        }

        // Insert first session
        val first = rawSessions[0]
        val firstMeta = getAppMetadata(first.packageName)
        timelineItems.add(
            ProcessedTimelineItem.Session(
                packageName = first.packageName,
                appName = firstMeta.appName,
                startTime = first.startTime,
                endTime = first.endTime,
                durationMs = first.endTime - first.startTime,
                icon = firstMeta.icon
            )
        )

        // Insert subsequent sessions and calculate idle gaps > 5 mins
        for (i in 1 until rawSessions.size) {
            val prev = rawSessions[i - 1]
            val curr = rawSessions[i]

            val gapMs = curr.startTime - prev.endTime
            if (gapMs > 5 * 60 * 1000L) {
                timelineItems.add(
                    ProcessedTimelineItem.Gap(
                        startTime = prev.endTime,
                        endTime = curr.startTime,
                        durationMs = gapMs
                    )
                )
            }

            val currMeta = getAppMetadata(curr.packageName)
            timelineItems.add(
                ProcessedTimelineItem.Session(
                    packageName = curr.packageName,
                    appName = currMeta.appName,
                    startTime = curr.startTime,
                    endTime = curr.endTime,
                    durationMs = curr.endTime - curr.startTime,
                    icon = currMeta.icon
                )
            )
        }
    }

    return timelineItems
}

// Convert drawable to Bitmap safely, handling adaptive icons, vectors, and recycled BitmapDrawables
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    // Guard: BitmapDrawable.bitmap can be null if the bitmap was recycled
    if (drawable is BitmapDrawable) {
        val bmp = drawable.bitmap
        if (bmp != null && !bmp.isRecycled) return bmp
    }
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    try {
        drawable.draw(canvas)
    } catch (e: Exception) {
        // Swallow any draw exception; the bitmap will remain blank and the
        // letter-fallback in the UI will render instead.
    }
    return bitmap
}

@Composable
fun PermissionRequiredScreen(onGrantClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Emerald900.copy(alpha = 0.4f), CircleShape)
                    .border(2.dp, Emerald500, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Emerald500,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Usage Access Required",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "To track app transitions and calculate idle timeline gaps, this app requires native Android Usage Access. Your logs are kept local to this app.",
                color = Slate400,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Grant Usage Access",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(onRefreshClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "TIMELINE LOG",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 1.5.sp
            )
        },
        actions = {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Timeline",
                    tint = Emerald500
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
    )
}

@Composable
fun DateSelectorRow(
    dates: List<LocalDate>,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit
) {
    val dayOfWeekFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val dayOfMonthFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())
    
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Slate900)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dates) { date ->
            val isSelected = date == selectedDate
            val dayName = if (date == LocalDate.now()) "Today" else date.format(dayOfWeekFormatter)
            val dayNum = date.format(dayOfMonthFormatter)
            
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Emerald500 else Slate800)
                    .clickable { onDateSelect(date) }
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Emerald500 else Slate700,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayName,
                        color = if (isSelected) Color.White else Slate400,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayNum,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineDashboard(
    items: List<ProcessedTimelineItem>,
    onRefresh: () -> Unit
) {
    val dates = remember {
        (0..6).map { LocalDate.now().minusDays(it.toLong()) }.reversed()
    }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    
    val filteredItems = remember(items, selectedDate) {
        items.filter { item ->
            val itemDate = Instant.ofEpochMilli(item.startTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            itemDate == selectedDate
        }
    }

    Scaffold(
        topBar = { MainAppBar(onRefreshClick = onRefresh) },
        containerColor = Slate900
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DateSelectorRow(
                dates = dates,
                selectedDate = selectedDate,
                onDateSelect = { selectedDate = it }
            )
            
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "📭",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Usage Logged",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Either no apps were used, or system logs are still syncing.",
                            color = Slate400,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    items(filteredItems) { item ->
                        when (item) {
                            is ProcessedTimelineItem.Session -> {
                                SessionItemRow(session = item)
                            }
                            is ProcessedTimelineItem.Gap -> {
                                GapItemRow(gap = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItemRow(session: ProcessedTimelineItem.Session) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min)
    ) {
        // Left side: Line and Icon
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxHeight()) {
                drawLine(
                    color = Slate700,
                    start = Offset(x = size.width / 2, y = 0f),
                    end = Offset(x = size.width / 2, y = size.height),
                    strokeWidth = 4f
                )
            }
            
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(36.dp)
                    .background(Slate900, CircleShape)
                    .border(2.dp, Emerald500, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (session.icon != null) {
                    Image(
                        bitmap = session.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = session.appName.take(1).uppercase(),
                        color = Emerald500,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Right side: Content Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = session.appName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.packageName,
                    color = Slate400,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${formatTime(session.startTime)} - ${formatTime(session.endTime)}",
                        color = Slate300,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(session.durationMs),
                        color = Emerald500,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GapItemRow(gap: ProcessedTimelineItem.Gap) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(IntrinsicSize.Min)
    ) {
        // Left side: Dashed Line and Icon
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxHeight()) {
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(
                    color = Rose500.copy(alpha = 0.5f),
                    start = Offset(x = size.width / 2, y = 0f),
                    end = Offset(x = size.width / 2, y = size.height),
                    strokeWidth = 4f,
                    pathEffect = pathEffect
                )
            }
            
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(36.dp)
                    .background(Slate900, CircleShape)
                    .border(2.dp, Rose500, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💤",
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Right side: Gap Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate800.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.3f)),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Screen Off / Idle Time",
                    color = Rose500,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Duration: ${formatGapDuration(gap.durationMs)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatTime(gap.startTime)} - ${formatTime(gap.endTime)}",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Formatting helpers
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> {
            val remainingMins = minutes % 60
            "${hours}h ${remainingMins}m"
        }
        minutes > 0 -> {
            val remainingSecs = seconds % 60
            "${minutes}m ${remainingSecs}s"
        }
        else -> "${seconds}s"
    }
}

private fun formatGapDuration(durationMs: Long): String {
    val minutes = durationMs / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> {
            val remainingHours = hours % 24
            val remainingMins = minutes % 60
            "${days}d ${remainingHours}h ${remainingMins}m"
        }
        hours > 0 -> {
            val remainingMins = minutes % 60
            "${hours}h ${remainingMins}m"
        }
        else -> "${minutes}m"
    }
}
