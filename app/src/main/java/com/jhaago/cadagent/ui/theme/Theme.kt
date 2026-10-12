package com.jhaago.cadagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    primary = Color(0xFF52C2E7), onPrimary = Color(0xFF062936),
    primaryContainer = Color(0xFF183B4C), onPrimaryContainer = Color(0xFFC2EDFA),
    secondary = Color(0xFF9FB9CB), onSecondary = Color(0xFF132D3D),
    secondaryContainer = Color(0xFF253746), onSecondaryContainer = Color(0xFFD9E6EF),
    tertiary = Color(0xFF64DBC0), onTertiary = Color(0xFF06392F),
    tertiaryContainer = Color(0xFF163A33), onTertiaryContainer = Color(0xFFACF3E0),
    background = Color(0xFF10151C), onBackground = Color(0xFFEAF0F6),
    surface = Color(0xFF171F2A), onSurface = Color(0xFFEAF0F6),
    surfaceVariant = Color(0xFF25313F), onSurfaceVariant = Color(0xFFAEBED0),
    surfaceContainerLowest = Color(0xFF10151C), surfaceContainerLow = Color(0xFF151D27),
    surfaceContainer = Color(0xFF1A2430), surfaceContainerHigh = Color(0xFF222F3D),
    surfaceContainerHighest = Color(0xFF2B3949),
    outline = Color(0xFF7E93A9), outlineVariant = Color(0xFF2C3949),
    error = Color(0xFFFFB4B4), onError = Color(0xFF640E19),
    errorContainer = Color(0xFF43232D), onErrorContainer = Color(0xFFFFDADB),
)

private val CadTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun CadAgentTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CadTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content,
    )
}
