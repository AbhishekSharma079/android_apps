package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = FastShareBlueLight,
    onPrimary = Color.White,
    primaryContainer = FastShareBlueDark,
    onPrimaryContainer = Color.White,
    secondary = FastShareGreen,
    onSecondary = Color.White,
    secondaryContainer = FastShareGreenDark,
    onSecondaryContainer = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceElevated,
    onBackground = DarkText,
    onSurface = DarkText,
    outline = Color(0xFF374151)
)

private val LightColorScheme = lightColorScheme(
    primary = FastShareBlue,
    onPrimary = Color.White,
    primaryContainer = FastShareBlueContainer,
    onPrimaryContainer = FastShareBlueDark,
    secondary = FastShareGreen,
    onSecondary = Color.White,
    secondaryContainer = FastShareGreenContainer,
    onSecondaryContainer = FastShareGreenDark,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onBackground = LightText,
    onSurface = LightText,
    outline = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep FastShare distinctive branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
