package com.arshita.networktrafficanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import com.arshita.networktrafficanalyzer.ui.DashboardScreen
import com.arshita.networktrafficanalyzer.ui.theme.NetworkTrafficAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetworkTrafficAnalyzerTheme {
                DashboardScreen(modifier = Modifier)
            }
        }
    }
}