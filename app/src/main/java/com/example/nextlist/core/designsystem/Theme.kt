package com.example.nextlist.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NextListLightColors = lightColorScheme(
    primary = Color(0xFF426B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9E0),
    onPrimaryContainer = Color(0xFF173A2C),
    secondary = Color(0xFF8A674A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2DFCF),
    onSecondaryContainer = Color(0xFF402B1B),
    tertiary = Color(0xFFE89A5B),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF20231F),
    surface = Color.White,
    onSurface = Color(0xFF20231F),
    surfaceVariant = Color(0xFFF0F2EE),
    onSurfaceVariant = Color(0xFF666B65),
    outline = Color(0xFFD9DDD8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

@Composable
fun NextListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NextListLightColors,
        typography = Typography(),
        content = content,
    )
}
