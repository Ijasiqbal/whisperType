package com.whispertype.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = com.whispertype.app.R.array.com_google_android_gms_fonts_certs
)

// ── Display / Headline serif ───────────────────────────────────
private val dmSerifDisplay = GoogleFont("DM Serif Display")

val DMSerifDisplay = FontFamily(
    Font(googleFont = dmSerifDisplay, fontProvider = fontProvider, weight = FontWeight.Normal),
)

// ── Body / Label sans-serif ────────────────────────────────────
private val plusJakartaSans = GoogleFont("Plus Jakarta Sans")

val PlusJakartaSans = FontFamily(
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = plusJakartaSans, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

val VozcribeTypography = Typography(
    // Display — hero numbers, big statements (DM Serif Display)
    displayLarge = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.25).sp
    ),

    // Headline — screen titles (DM Serif Display)
    headlineLarge = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DMSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),

    // Title — card headers, section labels (Plus Jakarta Sans)
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),

    // Body — primary reading text (Plus Jakarta Sans)
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),

    // Label — buttons, badges, captions (Plus Jakarta Sans)
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
)
