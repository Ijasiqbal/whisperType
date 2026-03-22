package com.whispertype.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

// ── Primary brand ────────────────────────────────────────────────
val Rust = Color(0xFFC45D3E)
val RustLight = Color(0xFFD4845A)
val RustDark = Color(0xFFAA4E33)
val RustDeep = Color(0xFF8B3A22)

// ── Secondary ────────────────────────────────────────────────────
val Emerald = Color(0xFF10B981)

// ── Neutrals (Warm Stone palette) ──────────────────────────────
val Slate800 = Color(0xFF292524)   // Stone 800 — warm near-black
val Slate700 = Color(0xFF44403C)   // Stone 700
val Slate600 = Color(0xFF57534E)   // Stone 600
val Slate500 = Color(0xFF78716C)   // Stone 500
val Slate400 = Color(0xFFA8A29E)   // Stone 400
val Slate300 = Color(0xFFD6D3D1)   // Stone 300
val Slate200 = Color(0xFFE7E5E4)   // Stone 200
val Slate100 = Color(0xFFF5F5F4)   // Stone 100
val Slate50 = Color(0xFFFAFAF9)    // Stone 50

// ── Status ───────────────────────────────────────────────────────
val Success = Color(0xFF22C55E)
val SuccessDark = Color(0xFF16A34A)
val Warning = Color(0xFFF59E0B)
val WarningYellow = Color(0xFFEAB308)
val Error = Color(0xFFEF4444)
val ErrorDark = Color(0xFFDC2626)
val WarningOrange = Color(0xFFF97316)

// ── Accent ────────────────────────────────────────────────────
val Gold = Color(0xFFFFD700)
val RustAmber = Color(0xFFE09060)

// ── Backgrounds (warm tints) ───────────────────────────────────
val IndigoTint = Color(0xFFFFF7F3)   // Warm peach tint (was cool indigo)
val IndigoLight = Color(0xFFFEF0E9)  // Warm blush
val GreenTint = Color(0xFFF0FDF4)
val RedTint = Color(0xFFFEE2E2)
val RedLightTint = Color(0xFFFEF2F2)
val WarningTint = Color(0xFFFEF3C7)

// ── Cream / warm white ─────────────────────────────────────────
val Cream = Color(0xFFFFFBF8)
val WarmWhite = Color(0xFFFFFEFC)

// ── Shared gradients ───────────────────────────────────────────
val RustGradient = Brush.linearGradient(listOf(Rust, RustLight))
val RustGradientHorizontal = Brush.horizontalGradient(listOf(Rust, RustLight))

val ScreenBackground = Brush.radialGradient(
    colors = listOf(IndigoTint, Cream),
    center = Offset(0.5f, 0f),
    radius = 1500f
)

// ── Login-specific gradients ───────────────────────────────────
val LoginBackground = Brush.verticalGradient(
    colors = listOf(
        Cream,
        Color(0xFFFFF3ED),   // Warm peach mid
        Color(0xFFFEEBE2),   // Deeper peach
        Color(0xFFFCE1D4)    // Rust-kissed bottom
    )
)
