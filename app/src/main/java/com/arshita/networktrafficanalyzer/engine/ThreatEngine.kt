package com.arshita.networktrafficanalyzer.engine

/**
 * ThreatEngine — a fully offline, rule-based privacy-risk analyzer.
 *
 * Takes aggregated network data and evaluates it against five rules
 * to produce a Privacy Score (0–100, where 100 = excellent, 0 = critical).
 *
 * No data ever leaves the device.
 */
class ThreatEngine {

    // ─── Data models ────────────────────────────────────────────────────

    /** Input: aggregated traffic record for a single app. */
    data class AppTrafficRecord(
        val appName: String,
        val packageName: String,
        /** True if the app transmitted data while the screen was locked. */
        val sentDataWhileScreenLocked: Boolean,
        /** Largest single burst of outbound data, in bytes. */
        val largestBurstBytes: Long,
        /** Number of DNS queries the app made in the last hour. */
        val dnsQueriesLastHour: Int,
        /** Outbound TCP connections that had no preceding DNS lookup. */
        val directIpConnections: Int,
        /** Domains the app contacted during the analysis window. */
        val contactedDomains: List<String>
    )

    /** A single observation when a rule fires. */
    data class Observation(
        val rule: String,
        val severity: Severity,
        val pointsDeducted: Int,
        val detail: String
    )

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    /** Final output of the engine. */
    data class ThreatReport(
        /** 0 (critical risk) → 100 (excellent). */
        val riskScore: Int,
        /** Human-readable label derived from the score. */
        val riskLabel: String,
        /** Every rule that triggered, with details. */
        val observations: List<Observation>
    )

    // ─── Known tracker domains (hardcoded, no network needed) ───────────

    private val knownTrackers = setOf(
        "analytics.fb-cdn.com",
        "google-analytics.com",
        "graph.facebook.com",
        "analytics.tiktok.com",
        "app-measurement.com",
        "doubleclick.net",
        "ads.google.com",
        "crashlytics.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "mixpanel.com",
        "amplitude.com",
        "segment.io",
        "scorecardresearch.com"
    )

    // ─── Thresholds ─────────────────────────────────────────────────────

    companion object {
        /** Points deducted per rule. */
        private const val PENALTY_BACKGROUND_DATA  = 20
        private const val PENALTY_LARGE_BURST      = 15
        private const val PENALTY_EXCESSIVE_DNS     = 20
        private const val PENALTY_NO_DNS_DIRECT_IP  = 15
        private const val PENALTY_KNOWN_TRACKER     = 10

        /** Rule-specific thresholds. */
        private const val BURST_THRESHOLD_BYTES    = 5L * 1024 * 1024  // 5 MB
        private const val DNS_QUERY_THRESHOLD      = 200
    }

    // ─── Public API ─────────────────────────────────────────────────────

    /**
     * Evaluate a list of per-app traffic records and return a single
     * [ThreatReport] covering the whole device.
     */
    fun analyze(records: List<AppTrafficRecord>): ThreatReport {
        val observations = mutableListOf<Observation>()

        for (record in records) {
            // ── Rule 1: Background Data ──────────────────────────────
            if (record.sentDataWhileScreenLocked) {
                observations += Observation(
                    rule           = "Background Data",
                    severity       = Severity.HIGH,
                    pointsDeducted = PENALTY_BACKGROUND_DATA,
                    detail         = "${record.appName} sent data while the screen " +
                                     "was locked — possible covert exfiltration."
                )
            }

            // ── Rule 2: Large Data Burst ─────────────────────────────
            if (record.largestBurstBytes > BURST_THRESHOLD_BYTES) {
                val burstMb = String.format("%.1f", record.largestBurstBytes / (1024.0 * 1024.0))
                observations += Observation(
                    rule           = "Large Data Burst",
                    severity       = Severity.MEDIUM,
                    pointsDeducted = PENALTY_LARGE_BURST,
                    detail         = "${record.appName} sent ${burstMb} MB in a single " +
                                     "burst (threshold: 5 MB)."
                )
            }

            // ── Rule 3: Excessive DNS Queries ────────────────────────
            if (record.dnsQueriesLastHour > DNS_QUERY_THRESHOLD) {
                observations += Observation(
                    rule           = "Excessive DNS",
                    severity       = Severity.HIGH,
                    pointsDeducted = PENALTY_EXCESSIVE_DNS,
                    detail         = "${record.appName} made ${record.dnsQueriesLastHour} " +
                                     "DNS queries in the last hour (threshold: $DNS_QUERY_THRESHOLD). " +
                                     "This may indicate DNS tunneling or tracking."
                )
            }

            // ── Rule 4: No DNS / Direct IP ───────────────────────────
            if (record.directIpConnections > 0) {
                observations += Observation(
                    rule           = "No DNS / Direct IP",
                    severity       = Severity.HIGH,
                    pointsDeducted = PENALTY_NO_DNS_DIRECT_IP,
                    detail         = "${record.appName} made ${record.directIpConnections} " +
                                     "outbound TCP connection(s) with no preceding DNS query — " +
                                     "possible C2 communication or hardcoded server."
                )
            }

            // ── Rule 5: Known Tracker ────────────────────────────────
            val matchedTrackers = record.contactedDomains.filter { domain ->
                knownTrackers.any { tracker ->
                    domain.equals(tracker, ignoreCase = true) ||
                    domain.endsWith(".$tracker", ignoreCase = true)
                }
            }
            if (matchedTrackers.isNotEmpty()) {
                observations += Observation(
                    rule           = "Known Tracker",
                    severity       = Severity.MEDIUM,
                    pointsDeducted = PENALTY_KNOWN_TRACKER,
                    detail         = "${record.appName} contacted known tracker(s): " +
                                     matchedTrackers.joinToString(", ") + "."
                )
            }
        }

        // ── Calculate final score ────────────────────────────────────────
        val totalDeductions = observations.sumOf { it.pointsDeducted }
        val riskScore       = (100 - totalDeductions).coerceIn(0, 100)
        val riskLabel       = scoreToLabel(riskScore)

        return ThreatReport(
            riskScore    = riskScore,
            riskLabel    = riskLabel,
            observations = observations
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun scoreToLabel(score: Int): String = when {
        score >= 80 -> "Low Risk"
        score >= 60 -> "Moderate Risk"
        score >= 40 -> "High Risk"
        score >= 20 -> "Very High Risk"
        else        -> "Critical Risk"
    }
}
