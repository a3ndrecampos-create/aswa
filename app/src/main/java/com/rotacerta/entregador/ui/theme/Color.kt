package com.rotacerta.entregador.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cores fixas de status
val RouteColor = Color(0xFF0EA5A4)
val Danger = Color(0xFFDC2626)
val Success = Color(0xFF16A34A)

// ---- Tema claro (único tema do app) ----
// Fundo levemente azulado + cartões brancos + azul vivo de destaque: visual limpo, de app
// de mobilidade, com ótimo contraste ao ar livre (importante pra quem entrega na rua).
private val LightBg = Color(0xFFF4F6FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFEEF2F8)
private val LightSurface3 = Color(0xFFE2E8F1)
private val LightLine = Color(0xFFD9E0EA)
private val LightTextMain = Color(0xFF0F172A)
private val LightMuted = Color(0xFF64748B)
private val LightAccent = Color(0xFF2563EB)
private val LightAccentInk = Color(0xFFFFFFFF)

data class RotaPalette(
    val bg: Color, val surface: Color, val surface2: Color, val surface3: Color,
    val line: Color, val textMain: Color, val muted: Color,
    val accent: Color, val accentInk: Color
)

val LightPalette = RotaPalette(
    LightBg, LightSurface, LightSurface2, LightSurface3, LightLine, LightTextMain, LightMuted,
    LightAccent, LightAccentInk
)

val LocalRotaPalette = staticCompositionLocalOf { LightPalette }

val Bg: Color @Composable get() = LocalRotaPalette.current.bg
val Surface: Color @Composable get() = LocalRotaPalette.current.surface
val Surface2: Color @Composable get() = LocalRotaPalette.current.surface2
val Surface3: Color @Composable get() = LocalRotaPalette.current.surface3
val Line: Color @Composable get() = LocalRotaPalette.current.line
val TextMain: Color @Composable get() = LocalRotaPalette.current.textMain
val Muted: Color @Composable get() = LocalRotaPalette.current.muted
val Accent: Color @Composable get() = LocalRotaPalette.current.accent
val AccentInk: Color @Composable get() = LocalRotaPalette.current.accentInk
