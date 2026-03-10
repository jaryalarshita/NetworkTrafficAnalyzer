package com.arshita.networktrafficanalyzer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arshita.networktrafficanalyzer.ui.theme.*
import com.arshita.networktrafficanalyzer.viewmodel.DashboardViewModel

// ─── Data models ────────────────────────────────────────────────────────────────

data class DataConsumer(
    val appName: String,
    val iconLetter: String,
    val iconColor: Color,
    val dataMb: Float,
    val maxMb: Float   // for normalizing the progress bar
)

data class ObservationItem(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val description: String
)

data class RecommendationItem(
    val title: String,
    val description: String
)

// ─── Sample data ────────────────────────────────────────────────────────────────

private const val SAMPLE_SCORE = 62

private val sampleConsumers = listOf(
    DataConsumer("YouTube",   "Y", AccentRed,    896.8f, 900f),
    DataConsumer("Spotify",   "S", AccentGreen,  257.5f, 900f),
    DataConsumer("Instagram", "I", AccentPurple, 233.5f, 900f),
    DataConsumer("Chrome",    "C", AccentBlue,   136.8f, 900f),
    DataConsumer("WhatsApp",  "W", AccentGreen,  101.3f, 900f)
)

private val sampleObservations = listOf(
    ObservationItem(
        icon        = Icons.Default.Warning,
        iconColor   = WarningYellow,
        title       = "Background Data Detected",
        description = "Instagram sent data while the screen was locked — possible covert exfiltration."
    ),
    ObservationItem(
        icon        = Icons.Default.Warning,
        iconColor   = AccentOrange,
        title       = "Known Tracker Contacted",
        description = "Chrome contacted google-analytics.com and doubleclick.net."
    ),
    ObservationItem(
        icon        = Icons.Outlined.Info,
        iconColor   = AccentBlue,
        title       = "Large Burst",
        description = "YouTube sent 12.4 MB in a single burst (threshold: 5 MB)."
    )
)

private val sampleRecommendations = listOf(
    RecommendationItem(
        title       = "Restrict Background Data",
        description = "Go to Settings → Apps → Instagram → Data Usage and disable background data."
    ),
    RecommendationItem(
        title       = "Use a DNS-level Ad Blocker",
        description = "Set Private DNS to a tracker-blocking provider like dns.adguard.com."
    ),
    RecommendationItem(
        title       = "Review App Permissions",
        description = "Remove network access from apps that don't need it via ADB or a firewall app."
    )
)

// ─── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyReportScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel? = null
) {
    // Observe ViewModel, fall back to sample data for previews
    val score        = viewModel?.riskScore?.collectAsState()?.value ?: SAMPLE_SCORE
    val consumers    = viewModel?.dataConsumers?.collectAsState()?.value
    val obs          = viewModel?.observations?.collectAsState()?.value
    val displayConsumers = if (!consumers.isNullOrEmpty()) consumers else sampleConsumers
    val displayObs       = if (!obs.isNullOrEmpty()) obs else sampleObservations
    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Privacy Report",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
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
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Risk Dial ──
            item { RiskDialCard(score = score) }

            // ── Top Data Consumers ──
            item { SectionHeader(title = "Top Data Consumers") }
            itemsIndexed(displayConsumers) { _, consumer ->
                DataConsumerRow(consumer = consumer)
            }

            // ── Observations ──
            item { SectionHeader(title = "Observations") }
            itemsIndexed(displayObs) { _, obsItem ->
                ObservationRow(observation = obsItem)
            }

            // ── Recommendations ──
            item { SectionHeader(title = "Recommendations") }
            itemsIndexed(sampleRecommendations) { index, rec ->
                RecommendationRow(index = index + 1, recommendation = rec)
            }
        }
    }
}

// ─── Section header ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary
    )
}

// ─── Risk Dial (Canvas)  ────────────────────────────────────────────────────────

@Composable
fun RiskDialCard(score: Int, modifier: Modifier = Modifier) {
    // Animate the arc sweep on first composition
    val animatedFraction by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 1200),
        label = "dial_sweep"
    )

    val arcColor = when {
        score >= 80 -> AccentGreen
        score >= 60 -> WarningYellow
        score >= 40 -> AccentOrange
        else        -> AccentRed
    }

    val riskLabel = when {
        score >= 80 -> "Low Risk"
        score >= 60 -> "Moderate Risk"
        score >= 40 -> "High Risk"
        score >= 20 -> "Very High Risk"
        else        -> "Critical Risk"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Canvas dial
            Box(contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier.size(180.dp)
                ) {
                    val strokeWidth = 14.dp.toPx()
                    val padding = strokeWidth / 2
                    val arcSize = Size(
                        size.width - strokeWidth,
                        size.height - strokeWidth
                    )

                    // Background track (full 270° arc)
                    drawArc(
                        color      = DividerColor,
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter  = false,
                        topLeft    = Offset(padding, padding),
                        size       = arcSize,
                        style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Foreground arc (animated)
                    drawArc(
                        color      = arcColor,
                        startAngle = 135f,
                        sweepAngle = 270f * animatedFraction,
                        useCenter  = false,
                        topLeft    = Offset(padding, padding),
                        size       = arcSize,
                        style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Score text in the centre
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "/ 100",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Risk label badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = arcColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = riskLabel,
                    color = arcColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Based on analysis of your recent network activity",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Data consumer row with progress bar ────────────────────────────────────────

@Composable
fun DataConsumerRow(consumer: DataConsumer, modifier: Modifier = Modifier) {
    val fraction = (consumer.dataMb / consumer.maxMb).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(consumer.iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = consumer.iconLetter,
                    color = consumer.iconColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = consumer.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${String.format("%.1f", consumer.dataMb)} MB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = consumer.iconColor,
                    trackColor = DividerColor,
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

// ─── Observation row ────────────────────────────────────────────────────────────

@Composable
fun ObservationRow(observation: ObservationItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = observation.icon,
                contentDescription = null,
                tint = observation.iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = observation.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = observation.iconColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = observation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

// ─── Recommendation row ─────────────────────────────────────────────────────────

@Composable
fun RecommendationRow(
    index: Int,
    recommendation: RecommendationItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Numbered circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(AccentBlue.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = recommendation.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = recommendation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
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
    name = "Privacy Report Preview"
)
@Composable
fun PrivacyReportPreview() {
    NetworkTrafficAnalyzerTheme {
        PrivacyReportScreen()
    }
}
