package com.arshita.networktrafficanalyzer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [NetworkFlow] and [SecurityAlert] tables.
 * Read queries return [Flow] so Compose UI recomposes automatically
 * whenever the underlying data changes.
 */
@Dao
interface TrafficDao {

    // ─── NetworkFlow operations ─────────────────────────────────────────

    /** Insert a single flow. Replaces on conflict (e.g. same primary key). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlow(flow: NetworkFlow)

    /** Bulk-insert multiple flows at once. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlows(flows: List<NetworkFlow>)

    /** Get the top 5 apps ranked by total bytes sent (descending). */
    @Query("""
        SELECT appName, packageName, destinationIp, protocol,
               SUM(bytesSent)     AS bytesSent,
               SUM(bytesReceived) AS bytesReceived,
               MAX(timestamp)     AS timestamp,
               0 AS id
        FROM network_flows
        GROUP BY packageName
        ORDER BY bytesSent DESC
        LIMIT 5
    """)
    fun getTopAppsByBytesSent(): Flow<List<NetworkFlow>>

    /** Get all flows ordered newest-first (useful for debugging / detail views). */
    @Query("SELECT * FROM network_flows ORDER BY timestamp DESC")
    fun getAllFlows(): Flow<List<NetworkFlow>>

    /** Total number of stored flows. */
    @Query("SELECT COUNT(*) FROM network_flows")
    fun getFlowCount(): Flow<Int>

    // ─── SecurityAlert operations ───────────────────────────────────────

    /** Insert a single alert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: SecurityAlert)

    /** Bulk-insert multiple alerts at once. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<SecurityAlert>)

    /** Get all alerts, newest first. The UI observes this reactively. */
    @Query("SELECT * FROM security_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<SecurityAlert>>

    /** Get alerts for a specific app. */
    @Query("SELECT * FROM security_alerts WHERE appName = :appName ORDER BY timestamp DESC")
    fun getAlertsForApp(appName: String): Flow<List<SecurityAlert>>
}
