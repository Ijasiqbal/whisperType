package com.whispertype.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VozcribeColorScheme = lightColorScheme(
    primary = Rust,
    onPrimary = Color.White,
    primaryContainer = IndigoTint,
    onPrimaryContainer = Slate800,
    secondary = Emerald,
    onSecondary = Color.White,
    secondaryContainer = GreenTint,
    onSecondaryContainer = SuccessDark,
    tertiary = RustLight,
    onTertiary = Color.White,
    error = Error,
    onError = Color.White,
    errorContainer = RedTint,
    onErrorContainer = ErrorDark,
    background = Cream,
    onBackground = Slate800,
    surface = WarmWhite,
    onSurface = Slate800,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    outlineVariant = Slate200,
)

@Composable
fun VozcribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VozcribeColorScheme,
        typography = VozcribeTypography,
        content = content
    )
}
