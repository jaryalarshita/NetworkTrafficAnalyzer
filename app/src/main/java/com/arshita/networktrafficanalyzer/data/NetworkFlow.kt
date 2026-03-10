package com.arshita.networktrafficanalyzer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single captured network flow (one connection or data transfer).
 * Stored entirely on-device via Room — no cloud sync.
 */
@Entity(tableName = "network_flows")
data class NetworkFlow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Human-readable app name, e.g. "Chrome". */
    val appName: String,

    /** Android package name, e.g. "com.android.chrome". */
    val packageName: String,

    /** The remote IP this flow connected to. */
    val destinationIp: String,

    /** Protocol string: "TCP", "UDP", "DNS", etc. */
    val protocol: String,

    /** Total bytes sent in this flow. */
    val bytesSent: Long,

    /** Total bytes received in this flow. */
    val bytesReceived: Long,

    /** Epoch millis when this flow was captured. */
    val timestamp: Long = System.currentTimeMillis()
)
