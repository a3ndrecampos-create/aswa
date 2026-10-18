package com.rotacerta.entregador.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Tema único do app: claro. */
@Composable
fun RotaCertaTheme(content: @Composable () -> Unit) {
    val palette = LightPalette
    val colorScheme = lightColorScheme(
        background = palette.bg,
        surface = palette.surface,
        surfaceVariant = palette.surface2,
        primary = palette.accent,
        onPrimary = palette.accentInk,
        onBackground = palette.textMain,
        onSurface = palette.textMain,
        onSurfaceVariant = palette.muted,
        outline = palette.line,
        secondary = RouteColor,
        error = Danger
    )
    CompositionLocalProvider(LocalRotaPalette provides palette) {
        MaterialTheme(colorScheme = colorScheme, typography = MaterialTheme.typography, content = content)
    }
}
