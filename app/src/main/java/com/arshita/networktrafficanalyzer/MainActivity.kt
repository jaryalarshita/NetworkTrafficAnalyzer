package com.arshita.networktrafficanalyzer

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arshita.networktrafficanalyzer.ui.DashboardScreen
import com.arshita.networktrafficanalyzer.ui.theme.NetworkTrafficAnalyzerTheme
import com.arshita.networktrafficanalyzer.viewmodel.DashboardViewModel
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
                // viewModel() creates OR retrieves the ViewModel.
                // It survives rotation and lives as long as the Activity.
                val viewModel: DashboardViewModel = viewModel()

                DashboardScreen(
                    viewModel   = viewModel,
                    onToggleVpn = { toggleVpn() }
                )
            }
        }
    }

    // ─── VPN control helpers ────────────────────────────────────────────

    private fun toggleVpn() {
        if (TrafficVpnService.isRunning.value) {
            stopVpn()
        } else {
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