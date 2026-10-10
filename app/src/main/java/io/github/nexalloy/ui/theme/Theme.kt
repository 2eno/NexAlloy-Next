package io.github.nexalloy.ui.theme

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

private val Light = lightColorScheme(
    primary = Color(0xFF3A5BA9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAE2FF),
    onPrimaryContainer = Color(0xFF001848),
    secondary = Color(0xFF585E71),
    secondaryContainer = Color(0xFFDDE2F9),
    tertiary = Color(0xFF735572),
    tertiaryContainer = Color(0xFFFED7F9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB1C5FF),
    onPrimary = Color(0xFF002B75),
    primaryContainer = Color(0xFF1F428F),
    onPrimaryContainer = Color(0xFFDAE2FF),
    secondary = Color(0xFFC0C6DC),
    secondaryContainer = Color(0xFF404659),
    tertiary = Color(0xFFE1BBDD),
    tertiaryContainer = Color(0xFF5A3E59),
)

/** Material 3 theme, with the wallpaper colors on Android 12 and newer. */
@Composable
fun NexAlloyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)

        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
