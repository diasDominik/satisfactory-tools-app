package io.github.diasdominik.sft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FicsitOrange = Color(0xFFDF691A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF08A3E),
    onPrimary = Color(0xFF2B1300),
    primaryContainer = Color(0xFF7A3500),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF5BC0DE),
    onSecondary = Color(0xFF00222C),
    tertiary = Color(0xFF5CB85C),
    background = Color(0xFF1E2023),
    surface = Color(0xFF1E2023),
    surfaceVariant = Color(0xFF2B3E50),
    surfaceContainer = Color(0xFF26292D),
    surfaceContainerHigh = Color(0xFF2E3237),
    surfaceContainerHighest = Color(0xFF363B41),
    surfaceContainerLow = Color(0xFF222428),
)

private val LightColors = lightColorScheme(
    primary = FicsitOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF00677F),
    tertiary = Color(0xFF2E7D32),
)

@Composable
fun SatisfactoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

object StatusColors {
    val success = Color(0xFF5CB85C)
    val warning = Color(0xFFF0AD4E)
    val danger = Color(0xFFD9534F)
    val info = Color(0xFF5BC0DE)
}
