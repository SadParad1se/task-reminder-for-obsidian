package com.sadparad1se.task_reminder

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4F2FF),
    onPrimaryContainer = Color(0xFF001F2E),
    secondary = Color(0xFF8A16C8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2DAFF),
    onSecondaryContainer = Color(0xFF2E004A),
    tertiary = Color(0xFFB000E8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6FA),
    onTertiaryContainer = Color(0xFF3A0049),
    background = Color(0xFFF9F7FF),
    onBackground = Color(0xFF17121F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF17121F),
    surfaceVariant = Color(0xFFEDE5F4),
    onSurfaceVariant = Color(0xFF4C4456),
    outline = Color(0xFF7E7488),
    outlineVariant = Color(0xFFCFC3D8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF22C4FF),
    onPrimary = Color(0xFF001F2E),
    primaryContainer = Color(0xFF00364B),
    onPrimaryContainer = Color(0xFFC7EEFF),
    secondary = Color(0xFFE34CFF),
    onSecondary = Color(0xFF3B004D),
    secondaryContainer = Color(0xFF5B0078),
    onSecondaryContainer = Color(0xFFF9D7FF),
    tertiary = Color(0xFF9B68FF),
    onTertiary = Color(0xFF21005D),
    tertiaryContainer = Color(0xFF3B1A7A),
    onTertiaryContainer = Color(0xFFE9DDFF),
    background = Color(0xFF050509),
    onBackground = Color(0xFFEDE7F7),
    surface = Color(0xFF0D0B14),
    onSurface = Color(0xFFEDE7F7),
    surfaceVariant = Color(0xFF1A1328),
    onSurfaceVariant = Color(0xFFD5C7E4),
    outline = Color(0xFF8D7EA0),
    outlineVariant = Color(0xFF3F334D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/** Applies the app's Material color scheme to Compose content. */
@Composable
fun TaskReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
        content = content
    )
}
