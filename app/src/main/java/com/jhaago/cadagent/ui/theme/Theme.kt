package com.jhaago.cadagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315A6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EAF3),
    onPrimaryContainer = Color(0xFF102F3A),
    secondary = Color(0xFF50606A),
    secondaryContainer = Color(0xFFDDE5E9),
    tertiary = Color(0xFF6A5B2E),
    tertiaryContainer = Color(0xFFF1E4B6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CDDC),
    onPrimary = Color(0xFF123642),
    primaryContainer = Color(0xFF244D5B),
    secondary = Color(0xFFBFC9CE),
    tertiary = Color(0xFFD5C68D),
)

@Composable
fun CadAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
