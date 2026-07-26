package com.rotacerta.entregador.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RotaCertaColorScheme = darkColorScheme(
    background = Bg,
    surface = Surface,
    primary = Accent,
    onPrimary = AccentInk,
    onBackground = TextMain,
    onSurface = TextMain,
    secondary = RouteColor,
    error = Danger
)

@Composable
fun RotaCertaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RotaCertaColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
