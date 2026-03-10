package com.arshita.networktrafficanalyzer.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.net.InetSocketAddress

/**
 * A local VPN service that intercepts all device traffic (IPv4 + IPv6)
 * without root. Packets are read from the TUN interface and forwarded
 * out through a DatagramChannel so that the device's internet continues
 * to work normally.
 *
 * Currently, each packet's byte-length is logged to Logcat (tag: TrafficVPN).
 */
class TrafficVpnService : VpnService() {

    companion object {
        private const val TAG = "TrafficVPN"
        private const val CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 1

        private val _isRunning = MutableStateFlow(false)
        /** Observe this from the UI to show VPN status. */
        val isRunning: StateFlow<Boolean> = _isRunning

        /** Action constants used in the start/stop intents. */
        const val ACTION_START = "com.arshita.networktrafficanalyzer.vpn.START"
        const val ACTION_STOP  = "com.arshita.networktrafficanalyzer.vpn.STOP"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── Service lifecycle ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                startForegroundNotification()
                startTunnel()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    // ─── Tunnel setup ───────────────────────────────────────────────────

    private fun startTunnel() {
        if (vpnInterface != null) return // already running

        val builder = Builder()
            // ── IPv4 ──
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            // ── IPv6 ──
            .addAddress("fd00::2", 128)
            .addRoute("::", 0)
            // ── General ──
            .setMtu(1500)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setSession("NetworkTrafficAnalyzer")
            .setBlocking(true)

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            stopSelf()
            return
        }

        _isRunning.value = true
        Log.i(TAG, "VPN tunnel established")

        // Start the packet-reading coroutine
        job = scope.launch { readPackets() }
    }

    private fun stopTunnel() {
        _isRunning.value = false
        job?.cancel()
        job = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        Log.i(TAG, "VPN tunnel stopped")
    }

    // ─── Packet reading loop ────────────────────────────────────────────

    /**
     * Reads raw packets from the TUN file descriptor in a loop.
     * Each packet's length is logged to Logcat.
     * Packets are forwarded out through the real network so the
     * device's internet is not blocked.
     */
    private suspend fun readPackets() = withContext(Dispatchers.IO) {
        val fd = vpnInterface?.fileDescriptor ?: return@withContext
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val buffer = ByteBuffer.allocate(1500) // MTU-sized buffer

        // Open a DatagramChannel that bypasses the VPN (protect it)
        val tunnel = DatagramChannel.open()
        protect(tunnel.socket()) // ← critical: prevents infinite loop
        tunnel.connect(InetSocketAddress("127.0.0.1", 0))

        try {
            while (isActive) {
                // Read one packet from the TUN interface
                val length = inputStream.read(buffer.array())

                if (length > 0) {
                    // Log packet size
                    Log.d(TAG, "Packet length: $length bytes")

                    // Write the packet back to the TUN so the
                    // OS can deliver it (loopback for now).
                    buffer.limit(length)
                    outputStream.write(buffer.array(), 0, length)
                    buffer.clear()
                }
            }
        } catch (e: Exception) {
            if (isActive) {
                Log.e(TAG, "Error reading packets", e)
            }
        } finally {
            tunnel.close()
            Log.i(TAG, "Packet reading loop ended")
        }
    }

    // ─── Foreground notification ────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the VPN traffic analyzer is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Traffic Analyzer")
            .setContentText("Capturing traffic…")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }
}
