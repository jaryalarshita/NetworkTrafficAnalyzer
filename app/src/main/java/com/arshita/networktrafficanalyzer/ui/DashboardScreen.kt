package com.arshita.networktrafficanalyzer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arshita.networktrafficanalyzer.ui.theme.*
import com.arshita.networktrafficanalyzer.viewmodel.DashboardViewModel
import com.arshita.networktrafficanalyzer.vpn.TrafficVpnService
import kotlin.math.sin

// ─── Data models ────────────────────────────────────────────────────────────────

data class StatItem(
    val label: String,
    val value: String,
    val unit: String,
    val accentColor: Color
)

data class AppTrafficItem(
    val name: String,
    val iconLetter: String,
    val iconColor: Color,
    val download: String,
    val upload: String,
    val totalToday: String,
    val connectionType: String
)

// ─── Sample data ────────────────────────────────────────────────────────────────

private val sampleStats = listOf(
    StatItem("Upload Speed", "2.4", "MB/s", AccentGreen),
    StatItem("Download Speed", "15.8", "MB/s", AccentBlue),
    StatItem("Active Connections", "47", "apps", AccentOrange)
)

private val sampleApps = listOf(
    AppTrafficItem("Chrome",    "C", AccentBlue,   "124.5 MB", "12.3 MB", "136.8 MB", "HTTPS"),
    AppTrafficItem("YouTube",   "Y", AccentRed,    "892.1 MB", "4.7 MB",  "896.8 MB", "HTTPS"),
    AppTrafficItem("Spotify",   "S", AccentGreen,  "256.3 MB", "1.2 MB",  "257.5 MB", "HTTPS"),
    AppTrafficItem("Instagram", "I", AccentPurple, "187.9 MB", "45.6 MB", "233.5 MB", "HTTPS"),
    AppTrafficItem("WhatsApp",  "W", AccentGreen,  "67.2 MB",  "34.1 MB", "101.3 MB", "E2E Encrypted"),
    AppTrafficItem("Maps",      "M", AccentBlue,   "43.8 MB",  "8.9 MB",  "52.7 MB",  "HTTPS"),
    AppTrafficItem("Gmail",     "G", AccentOrange, "22.1 MB",  "5.4 MB",  "27.5 MB",  "HTTPS")
)

// ─── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel? = null,
    onToggleVpn: () -> Unit = {}
) {
    val vpnRunning by TrafficVpnService.isRunning.collectAsState()

    // Observe ViewModel state, fall back to sample data for previews
    val statsFromVm    = viewModel?.stats?.collectAsState()?.value
    val topAppsFromVm  = viewModel?.topApps?.collectAsState()?.value
    val displayStats   = if (!statsFromVm.isNullOrEmpty()) statsFromVm else sampleStats
    val displayApps    = if (!topAppsFromVm.isNullOrEmpty()) topAppsFromVm else sampleApps
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Network Traffic Analyzer",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        // Online indicator dot
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(StatusOnline, CircleShape)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── VPN control card ──
            item {
                VpnControlCard(
                    isRunning = vpnRunning,
                    onToggle = onToggleVpn
                )
            }

            // ── Stats cards row ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displayStats.forEach { stat ->
                        StatCard(
                            stat = stat,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Graph card ──
            item {
                GraphCard()
            }

            // ── App traffic section header ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Traffic",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${sampleApps.size} apps",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // ── App traffic list ──
            itemsIndexed(displayApps) { _, app ->
                AppTrafficRow(app = app)
            }
        }
    }
}

// ─── Stat card ──────────────────────────────────────────────────────────────────

@Composable
fun StatCard(stat: StatItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Accent dot
            Box(
                Modifier
                    .size(8.dp)
                    .background(stat.accentColor, CircleShape)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stat.value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary
            )
            Text(
                text = stat.unit,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stat.label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

// ─── Graph card ─────────────────────────────────────────────────────────────────

@Composable
fun GraphCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Speed Graph",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = AccentGreen, label = "Upload")
                    LegendDot(color = AccentBlue,  label = "Download")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Canvas placeholder graph
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height
                val gridColor = DividerColor

                // ── Draw horizontal grid lines ──
                for (i in 0..4) {
                    val y = h * i / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end   = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                // ── Draw vertical grid lines ──
                for (i in 0..6) {
                    val x = w * i / 6f
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end   = Offset(x, h),
                        strokeWidth = 1f
                    )
                }

                // ── Helper: generate sine-wave path ──
                fun wavePath(
                    phaseShift: Float,
                    amplitude: Float,
                    verticalOffset: Float
                ): Path {
                    val path = Path()
                    val steps = 200
                    for (i in 0..steps) {
                        val x = w * i / steps.toFloat()
                        val y = verticalOffset + amplitude * sin(
                            (i.toFloat() / steps * 4f * Math.PI + phaseShift).toFloat()
                        )
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }

                // ── Upload line (green) ──
                val uploadPath = wavePath(
                    phaseShift    = 0f,
                    amplitude     = h * 0.2f,
                    verticalOffset = h * 0.55f
                )
                drawPath(
                    path  = uploadPath,
                    color = AccentGreen,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // ── Download line (blue) ──
                val downloadPath = wavePath(
                    phaseShift    = 2f,
                    amplitude     = h * 0.25f,
                    verticalOffset = h * 0.4f
                )
                drawPath(
                    path  = downloadPath,
                    color = AccentBlue,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Time axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("60s", "50s", "40s", "30s", "20s", "10s", "Now").forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

// ─── App traffic expandable row ─────────────────────────────────────────────────

@Composable
fun AppTrafficRow(app: AppTrafficItem, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column {
            // Main row – always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(app.iconColor, app.iconColor.copy(alpha = 0.6f))
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.iconLetter,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Name + download summary
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "↓ ${app.download}  ↑ ${app.upload}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Expand/collapse icon
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                                 else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextSecondary
                )
            }

            // Expanded detail section
            AnimatedVisibility(visible = expanded) {
                Divider(color = DividerColor, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailChip(label = "Total Today", value = app.totalToday)
                    DetailChip(label = "Connection",  value = app.connectionType)
                    DetailChip(label = "Download",    value = app.download)
                }
            }
        }
    }
}

@Composable
fun DetailChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}

// ─── VPN Control Card ───────────────────────────────────────────────────────────

@Composable
fun VpnControlCard(
    isRunning: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (isRunning) AccentGreen else AccentRed,
        animationSpec = tween(400),
        label = "vpn_status_color"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                Modifier
                    .size(10.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(12.dp))

            // Status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isRunning) "VPN Active" else "VPN Inactive",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (isRunning) "Capturing device traffic…"
                           else "Tap Start to begin capture",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Start / Stop button
            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) AccentRed else AccentGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Close
                                 else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "Stop" else "Start Capture",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ─── Preview ────────────────────────────────────────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 851,
    name = "Dashboard Preview"
)
@Composable
fun DashboardPreview() {
    NetworkTrafficAnalyzerTheme {
        DashboardScreen()
    }
}
