package com.example.spindle.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val NavyDeepest   = Color(0xFF060A14)
val NavyDeep      = Color(0xFF0A0E1A)
val NavyMid       = Color(0xFF0E1422)
val GlassSurface  = Color(0xFF141B2D)
val GlassSurface2 = Color(0xFF1A2235)
val GlassRim      = Color(0xFF243050)
val GlassRimBright= Color(0xFF2E3E64)
val SlateDeep     = Color(0xFF1E2840)
val SlateMid      = Color(0xFF2A3554)
val SlateLight    = Color(0xFF3B4A6B)
val TealBright    = Color(0xFF00D4B1)
val TealMid       = Color(0xFF00B899)
val TealDeep      = Color(0xFF008C74)
val TealGlow      = Color(0x2000D4B1)
val TealSubtle    = Color(0xFF0D5E52)
val GreenSafe     = Color(0xFF2ECC8A)
val AmberWarning  = Color(0xFFFFB830)
val RedDanger     = Color(0xFFFF4D6D)
val BluePrimary   = Color(0xFF5A8FFF)
val TextPrimary   = Color(0xFFEAEEF8)
val TextSecondary = Color(0xFF8896B3)
val TextDisabled  = Color(0xFF4A5472)
val TextAccent    = TealBright

val SpindleDarkColorScheme = darkColorScheme(
    primary             = TealBright,
    onPrimary           = NavyDeepest,
    primaryContainer    = TealSubtle,
    onPrimaryContainer  = TealBright,
    secondary           = BluePrimary,
    onSecondary         = NavyDeepest,
    background          = NavyDeep,
    onBackground        = TextPrimary,
    surface             = GlassSurface,
    onSurface           = TextPrimary,
    surfaceVariant      = GlassSurface2,
    onSurfaceVariant    = TextSecondary,
    error               = RedDanger,
    onError             = NavyDeepest,
    outline             = GlassRim,
    outlineVariant      = SlateMid,
)
