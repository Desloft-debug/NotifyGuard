package com.guard.notifyguard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Своя палитра, без Material You: приложение должно выглядеть одинаково
// на любой прошивке, а не подстраиваться под обои.

private val Ink = Color(0xFF12141A)
private val Slate = Color(0xFF3E4553)
private val Accent = Color(0xFF4C6FFF)
private val AccentDark = Color(0xFF93AAFF)
private val Mint = Color(0xFF2FA37A)
private val Coral = Color(0xFFE05656)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9FF),
    onPrimaryContainer = Color(0xFF10225C),
    secondary = Mint,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FB),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Slate,
    outline = Color(0xFFC3C8D4),
    error = Coral,
    onError = Color.White
)

private val Dark = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0B1638),
    primaryContainer = Color(0xFF243057),
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = Color(0xFF6FD3AC),
    onSecondary = Color(0xFF06281C),
    background = Color(0xFF101319),
    onBackground = Color(0xFFE7E9EF),
    surface = Color(0xFF171B23),
    onSurface = Color(0xFFE7E9EF),
    surfaceVariant = Color(0xFF232833),
    onSurfaceVariant = Color(0xFFB4BBC9),
    outline = Color(0xFF3B424F),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3A0A0A)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun GuardTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
