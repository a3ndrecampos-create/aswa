package com.rotacerta.entregador.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cores fixas (funcionam bem tanto no tema escuro quanto no claro)
val RouteColor = Color(0xFF1E9E8F)
val Danger = Color(0xFFE5484D)
val Success = Color(0xFF2FA86A)

// ---- Tema escuro (valores fixos usados para montar a paleta) ----
private val DarkBg = Color(0xFF0F1115)
private val DarkSurface = Color(0xFF171A21)
private val DarkSurface2 = Color(0xFF1E222B)
private val DarkSurface3 = Color(0xFF262B36)
private val DarkLine = Color(0xFF2C313D)
private val DarkTextMain = Color(0xFFF5F6F8)
private val DarkMuted = Color(0xFF8B93A7)
private val DarkAccent = Color(0xFF8B5CF6)      // roxo vívido — funciona bem sobre fundo escuro
private val DarkAccentInk = Color(0xFFFFFFFF)   // texto branco sobre o roxo

// ---- Tema claro (valores fixos usados para montar a paleta) ----
private val LightBg = Color(0xFFF7F7F9)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurface2 = Color(0xFFF0F1F4)
private val LightSurface3 = Color(0xFFE6E8ED)
private val LightLine = Color(0xFFDDE0E6)
private val LightTextMain = Color(0xFF16181D)
private val LightMuted = Color(0xFF6B7280)
private val LightAccent = Color(0xFF7C3AED)     // roxo mais escuro — mais legível sobre fundo claro
private val LightAccentInk = Color(0xFFFFFFFF)  // texto branco sobre o roxo

data class RotaPalette(
    val bg: Color, val surface: Color, val surface2: Color, val surface3: Color,
    val line: Color, val textMain: Color, val muted: Color,
    val accent: Color, val accentInk: Color
)

val DarkPalette = RotaPalette(
    DarkBg, DarkSurface, DarkSurface2, DarkSurface3, DarkLine, DarkTextMain, DarkMuted,
    DarkAccent, DarkAccentInk
)
val LightPalette = RotaPalette(
    LightBg, LightSurface, LightSurface2, LightSurface3, LightLine, LightTextMain, LightMuted,
    LightAccent, LightAccentInk
)

val LocalRotaPalette = staticCompositionLocalOf { DarkPalette }

// Mesmos nomes de antes (Bg, Surface, TextMain, Muted, Accent...), só que agora
// resolvidos pelo tema atual — os componentes que já usavam essas cores continuam
// funcionando sem mudança nenhuma, só passam a reagir ao tema claro/escuro.
val Bg: Color @Composable get() = LocalRotaPalette.current.bg
val Surface: Color @Composable get() = LocalRotaPalette.current.surface
val Surface2: Color @Composable get() = LocalRotaPalette.current.surface2
val Surface3: Color @Composable get() = LocalRotaPalette.current.surface3
val Line: Color @Composable get() = LocalRotaPalette.current.line
val TextMain: Color @Composable get() = LocalRotaPalette.current.textMain
val Muted: Color @Composable get() = LocalRotaPalette.current.muted
val Accent: Color @Composable get() = LocalRotaPalette.current.accent
val AccentInk: Color @Composable get() = LocalRotaPalette.current.accentInk
