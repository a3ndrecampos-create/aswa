package com.rotacerta.entregador.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RotaCertaTheme(lightTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (lightTheme) LightPalette else DarkPalette

    val colorScheme = if (lightTheme) {
        lightColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.accent,
            onPrimary = palette.accentInk,
            onBackground = palette.textMain,
            onSurface = palette.textMain,
            secondary = RouteColor,
            error = Danger
        )
    } else {
        darkColorScheme(
            background = palette.bg,
            surface = palette.surface,
            primary = palette.accent,
            onPrimary = palette.accentInk,
            onBackground = palette.textMain,
            onSurface = palette.textMain,
            secondary = RouteColor,
            error = Danger
        )
    }

    CompositionLocalProvider(LocalRotaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
