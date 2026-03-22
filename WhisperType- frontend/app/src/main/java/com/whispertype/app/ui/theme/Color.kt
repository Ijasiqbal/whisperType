package com.whispertype.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

// ── Primary brand ────────────────────────────────────────────────
val Rust = Color(0xFFC45D3E)
val RustLight = Color(0xFFD4845A)

// ── Secondary ────────────────────────────────────────────────────
val Emerald = Color(0xFF10B981)

// ── Neutrals (Slate palette) ─────────────────────────────────────
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// ── Status ───────────────────────────────────────────────────────
val Success = Color(0xFF22C55E)
val SuccessDark = Color(0xFF16A34A)
val Warning = Color(0xFFF59E0B)
val WarningYellow = Color(0xFFEAB308)
val Error = Color(0xFFEF4444)
val ErrorDark = Color(0xFFDC2626)
val WarningOrange = Color(0xFFF97316)

// ── Backgrounds ──────────────────────────────────────────────────
val IndigoTint = Color(0xFFEEF2FF)
val IndigoLight = Color(0xFFEDE9FE)
val GreenTint = Color(0xFFF0FDF4)
val RedTint = Color(0xFFFEE2E2)
val RedLightTint = Color(0xFFFEF2F2)
val WarningTint = Color(0xFFFEF3C7)

// ── Shared gradients ─────────────────────────────────────────────
val RustGradient = Brush.linearGradient(listOf(Rust, RustLight))
val RustGradientHorizontal = Brush.horizontalGradient(listOf(Rust, RustLight))
val ScreenBackground = Brush.radialGradient(
    colors = listOf(IndigoTint, Slate50),
    center = Offset(0.5f, 0f),
    radius = 1500f
)
