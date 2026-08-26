package com.vpntz.app.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vpntz.app.data.local.datastore.DarkMode

private val DarkColorScheme = darkColorScheme(
    primary = VpnTzPrimaryLight,
    onPrimary = Color.Black,
    primaryContainer = VpnTzPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = VpnTzSecondary,
    onSecondary = Color.Black,
    secondaryContainer = VpnTzSecondaryDark,
    onSecondaryContainer = Color.Black,
    tertiary = VpnTzPrimaryLight,
    onTertiary = Color.Black,
    background = BackgroundDark,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val AmoledColorScheme = darkColorScheme(
    primary = VpnTzPrimaryLight,
    onPrimary = Color.Black,
    primaryContainer = VpnTzPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = VpnTzSecondary,
    onSecondary = Color.Black,
    secondaryContainer = VpnTzSecondaryDark,
    onSecondaryContainer = Color.Black,
    tertiary = VpnTzPrimaryLight,
    onTertiary = Color.Black,
    background = BackgroundAmoled,
    onBackground = Color.White,
    surface = SurfaceAmoled,
    onSurface = Color.White,
    surfaceVariant = SurfaceVariantAmoled,
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

private val LightColorScheme = lightColorScheme(
    primary = VpnTzPrimary,
    onPrimary = Color.White,
    primaryContainer = VpnTzPrimaryLight,
    onPrimaryContainer = Color.Black,
    secondary = VpnTzSecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = VpnTzSecondary,
    onSecondaryContainer = Color.Black,
    tertiary = VpnTzPrimaryDark,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = Color.Black,
    surface = SurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun VpnTzTheme(
    darkMode: DarkMode = DarkMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkMode) {
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
        DarkMode.AMOLED -> true
        DarkMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        darkMode == DarkMode.AMOLED -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Control status bar and navigation bar icon colors based on theme
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
