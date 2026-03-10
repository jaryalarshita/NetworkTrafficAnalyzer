package com.arshita.networktrafficanalyzer

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.arshita.networktrafficanalyzer.ui.DashboardScreen
import com.arshita.networktrafficanalyzer.ui.theme.NetworkTrafficAnalyzerTheme
import com.arshita.networktrafficanalyzer.vpn.TrafficVpnService

class MainActivity : ComponentActivity() {

    /**
     * Launcher that handles the system VPN-permission dialog result.
     * If the user grants permission, we start the VPN service.
     */
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkTrafficAnalyzerTheme {
                DashboardScreen(
                    onToggleVpn = { toggleVpn() }
                )
            }
        }
    }

    // ─── VPN control helpers ────────────────────────────────────────────

    /**
     * Called when the user taps the Start / Stop button.
     */
    private fun toggleVpn() {
        if (TrafficVpnService.isRunning.value) {
            stopVpn()
        } else {
            // VpnService.prepare() returns null if permission is already granted
            val intent = VpnService.prepare(this)
            if (intent != null) {
                vpnPermissionLauncher.launch(intent)
            } else {
                startVpn()
            }
        }
    }

    private fun startVpn() {
        val intent = Intent(this, TrafficVpnService::class.java).apply {
            action = TrafficVpnService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopVpn() {
        val intent = Intent(this, TrafficVpnService::class.java).apply {
            action = TrafficVpnService.ACTION_STOP
        }
        startService(intent)
    }
}