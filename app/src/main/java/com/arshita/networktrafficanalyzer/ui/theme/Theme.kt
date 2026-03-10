package com.arshita.networktrafficanalyzer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AppColorScheme = darkColorScheme(
    primary        = AccentBlue,
    secondary      = AccentGreen,
    tertiary       = AccentOrange,
    background     = DarkBackground,
    surface        = DarkSurface,
    surfaceVariant = DarkCard,
    error          = AccentRed,
    onPrimary      = Color.White,
    onSecondary    = Color.White,
    onTertiary     = Color.White,
    onBackground   = TextPrimary,
    onSurface      = TextPrimary,
    onError        = Color.White,
    outline        = DividerColor
)

@Composable
fun NetworkTrafficAnalyzerTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}