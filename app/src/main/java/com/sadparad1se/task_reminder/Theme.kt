package com.sadparad1se.task_reminder

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3A5A40),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF588157),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    background = androidx.compose.ui.graphics.Color(0xFFF6FBF6),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1F1C),
    surface = androidx.compose.ui.graphics.Color(0xFFF6FBF6),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1F1C)
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA3C9A8),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF12301D),
    secondary = androidx.compose.ui.graphics.Color(0xFF8CBF8A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF102B16),
    background = androidx.compose.ui.graphics.Color(0xFF101410),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE3EAE2),
    surface = androidx.compose.ui.graphics.Color(0xFF101410),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE3EAE2)
)

/** Applies the app's Material color scheme to Compose content. */
@Composable
fun TaskReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
