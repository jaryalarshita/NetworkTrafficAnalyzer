package com.arshita.networktrafficanalyzer.viewmodel

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arshita.networktrafficanalyzer.data.AppDatabase
import com.arshita.networktrafficanalyzer.data.NetworkFlow
import com.arshita.networktrafficanalyzer.data.SecurityAlert
import com.arshita.networktrafficanalyzer.data.TrafficDao
import com.arshita.networktrafficanalyzer.engine.ThreatEngine
import com.arshita.networktrafficanalyzer.ui.AppTrafficItem
import com.arshita.networktrafficanalyzer.ui.DataConsumer
import com.arshita.networktrafficanalyzer.ui.ObservationItem
import com.arshita.networktrafficanalyzer.ui.StatItem
import com.arshita.networktrafficanalyzer.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ────────────────────────────────────────────────────────────────────────
 *  HOW A VIEWMODEL WORKS (beginner-friendly explanation)
 * ────────────────────────────────────────────────────────────────────────
 *
 *  1. A ViewModel SURVIVES screen rotation. When you rotate the phone,
 *     the Activity is destroyed and recreated, but this ViewModel stays
 *     alive. That means your data isn't lost.
 *
 *  2. We collect Flow objects from the Room DAO. Whenever a new row is
 *     inserted into the database, Room automatically sends the updated
 *     list through the Flow, which triggers recomposition in your Compose
 *     UI — no manual "refresh" needed.
 *
 *  3. We expose StateFlow objects that the Compose screens observe.
 *     Think of a StateFlow as a "live variable": whenever its value
 *     changes, every Composable that reads it redraws automatically.
 *
 *  4. The ThreatEngine runs entirely in-memory. Each time the flow list
 *     changes, we convert the flows → AppTrafficRecords, call
 *     engine.analyze(), and push the results into StateFlows.
 * ────────────────────────────────────────────────────────────────────────
 */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // ── Dependencies ────────────────────────────────────────────────────
    private val dao: TrafficDao = AppDatabase.getInstance(application).trafficDao()
    private val engine = ThreatEngine()

    // ── UI State holders ────────────────────────────────────────────────

    /** Privacy score (0–100). */
    private val _riskScore = MutableStateFlow(100)
    val riskScore: StateFlow<Int> = _riskScore.asStateFlow()

    /** Human-readable risk label. */
    private val _riskLabel = MutableStateFlow("Low Risk")
    val riskLabel: StateFlow<String> = _riskLabel.asStateFlow()

    /** Stat cards shown at the top of the Dashboard. */
    private val _stats = MutableStateFlow(defaultStats())
    val stats: StateFlow<List<StatItem>> = _stats.asStateFlow()

    /** Top 5 apps by bytes sent (for Dashboard app list). */
    private val _topApps = MutableStateFlow<List<AppTrafficItem>>(emptyList())
    val topApps: StateFlow<List<AppTrafficItem>> = _topApps.asStateFlow()

    /** Top data consumers for the Privacy Report progress bars. */
    private val _dataConsumers = MutableStateFlow<List<DataConsumer>>(emptyList())
    val dataConsumers: StateFlow<List<DataConsumer>> = _dataConsumers.asStateFlow()

    /** Threat observations for the Privacy Report. */
    private val _observations = MutableStateFlow<List<ObservationItem>>(emptyList())
    val observations: StateFlow<List<ObservationItem>> = _observations.asStateFlow()

    /** Security alerts from Room. */
    private val _alerts = MutableStateFlow<List<SecurityAlert>>(emptyList())
    val alerts: StateFlow<List<SecurityAlert>> = _alerts.asStateFlow()

    /** Total captured flow count. */
    private val _flowCount = MutableStateFlow(0)
    val flowCount: StateFlow<Int> = _flowCount.asStateFlow()

    // ── Initialization ──────────────────────────────────────────────────

    init {
        // Observe top apps from Room
        viewModelScope.launch {
            dao.getTopAppsByBytesSent().collect { flows ->
                processTopApps(flows)
            }
        }

        // Observe all flows for ThreatEngine analysis
        viewModelScope.launch {
            dao.getAllFlows().collect { flows ->
                runThreatAnalysis(flows)
            }
        }

        // Observe alerts
        viewModelScope.launch {
            dao.getAllAlerts().collect { alertList ->
                _alerts.value = alertList
            }
        }

        // Observe flow count
        viewModelScope.launch {
            dao.getFlowCount().collect { count ->
                _flowCount.value = count
            }
        }
    }

    // ── Data processing ─────────────────────────────────────────────────

    private fun processTopApps(flows: List<NetworkFlow>) {
        val appColors = listOf(AccentBlue, AccentRed, AccentGreen, AccentPurple, AccentOrange)

        _topApps.value = flows.mapIndexed { index, flow ->
            val color = appColors[index % appColors.size]
            AppTrafficItem(
                name           = flow.appName,
                iconLetter     = flow.appName.first().uppercase(),
                iconColor      = color,
                download       = formatBytes(flow.bytesReceived),
                upload         = formatBytes(flow.bytesSent),
                totalToday     = formatBytes(flow.bytesSent + flow.bytesReceived),
                connectionType = flow.protocol
            )
        }

        // Also build consumers for the Privacy Report
        val maxBytes = flows.maxOfOrNull { it.bytesSent + it.bytesReceived }?.toFloat() ?: 1f
        _dataConsumers.value = flows.mapIndexed { index, flow ->
            val color = appColors[index % appColors.size]
            val totalMb = (flow.bytesSent + flow.bytesReceived) / (1024f * 1024f)
            DataConsumer(
                appName    = flow.appName,
                iconLetter = flow.appName.first().uppercase(),
                iconColor  = color,
                dataMb     = totalMb,
                maxMb      = maxBytes / (1024f * 1024f)
            )
        }

        // Update stats
        val totalUp   = flows.sumOf { it.bytesSent }
        val totalDown = flows.sumOf { it.bytesReceived }
        _stats.value = listOf(
            StatItem("Upload",     formatBytes(totalUp),   "total", AccentGreen),
            StatItem("Download",   formatBytes(totalDown), "total", AccentBlue),
            StatItem("Active Apps", "${flows.size}",       "apps",  AccentOrange)
        )
    }

    /**
     * Converts Room flows → ThreatEngine input, runs analysis,
     * and pushes the results into StateFlows.
     */
    private fun runThreatAnalysis(flows: List<NetworkFlow>) {
        // Group flows by packageName into AppTrafficRecords
        val grouped = flows.groupBy { it.packageName }

        val records = grouped.map { (pkg, appFlows) ->
            val name = appFlows.firstOrNull()?.appName ?: pkg
            ThreatEngine.AppTrafficRecord(
                appName                   = name,
                packageName               = pkg,
                sentDataWhileScreenLocked = false,     // will be wired later
                largestBurstBytes         = appFlows.maxOfOrNull { it.bytesSent } ?: 0L,
                dnsQueriesLastHour        = appFlows.count { it.protocol == "DNS" },
                directIpConnections       = appFlows.count {
                    it.protocol == "TCP" && it.destinationIp.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                },
                contactedDomains          = appFlows.map { it.destinationIp }.distinct()
            )
        }

        val report = engine.analyze(records)

        _riskScore.value = report.riskScore
        _riskLabel.value = report.riskLabel

        // Convert engine observations → UI ObservationItems
        _observations.value = report.observations.map { obs ->
            val (icon, color) = when (obs.severity) {
                ThreatEngine.Severity.CRITICAL -> Icons.Default.Warning to AccentRed
                ThreatEngine.Severity.HIGH     -> Icons.Default.Warning to WarningYellow
                ThreatEngine.Severity.MEDIUM   -> Icons.Default.Warning to AccentOrange
                ThreatEngine.Severity.LOW      -> Icons.Outlined.Info   to AccentBlue
            }
            ObservationItem(
                icon        = icon,
                iconColor   = color,
                title       = obs.rule,
                description = obs.detail
            )
        }

        // Persist new alerts to Room
        viewModelScope.launch {
            val newAlerts = report.observations.map { obs ->
                SecurityAlert(
                    appName     = obs.detail.substringBefore(" "),
                    severity    = obs.severity.name,
                    description = obs.detail
                )
            }
            if (newAlerts.isNotEmpty()) {
                dao.insertAlerts(newAlerts)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024         -> String.format("%.1f KB", bytes / 1_024.0)
        else                   -> "$bytes B"
    }

    private fun defaultStats() = listOf(
        StatItem("Upload",      "0 B",  "total", AccentGreen),
        StatItem("Download",    "0 B",  "total", AccentBlue),
        StatItem("Active Apps", "0",    "apps",  AccentOrange)
    )
}
