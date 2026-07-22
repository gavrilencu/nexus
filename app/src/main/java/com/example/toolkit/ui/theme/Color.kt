package com.example.toolkit.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Premium-SaaS palette v2 — deep navy-violet base with a glass-card surface
// system and a vivid violet→cyan gradient accent (think Stripe/Revolut),
// replacing the flat slate/indigo v1 theme. Names are kept stable so this
// cascades through every screen without touching call sites.
val VoidBlack = Color(0xFF0A0916)        // app background
val MatrixBlack = Color(0xFF120F24)      // top bar / drawer / recessed surfaces
val PanelGreen = Color(0xFF171330)       // glass card background (paired with GlassBorder)
val BorderGreen = Color(0xFF2C2650)      // hairlines, dividers, default outlines
val NeonGreen = Color(0xFF8B5CF6)        // primary accent (violet) — solid fallback for the gradient
val SoftGreen = Color(0xFF34D399)        // success / "online" / positive status
val DimGreen = Color(0xFFA78BFA)         // secondary accent (light violet)
val MuteGreen = Color(0xFF9C93C2)        // secondary text, captions
val AlertAmber = Color(0xFFF5A623)       // warnings
val AlertRed = Color(0xFFEF4444)         // errors / destructive
val GhostWhite = Color(0xFFF6F4FC)       // primary text on dark surfaces
val TerminalGray = Color(0xFFAAA2C6)     // tertiary text / metadata

// Gradient accent — the signature of the "premium SaaS" look. Used on
// primary buttons, module icon badges, and headline wordmarks.
val GradientStart = Color(0xFF8B5CF6)    // violet-500
val GradientEnd = Color(0xFF06B6D4)      // cyan-500
val AccentGradient = Brush.linearGradient(listOf(GradientStart, GradientEnd))

// Glass-card surface tokens.
val SurfaceRaised = Color(0xFF1D1840)
val GlassBorder = Color(0xFFFFFFFF)      // used with alpha at call sites (e.g. GlassBorder.copy(alpha = 0.10f))
val AccentSoft = Color(0xFF8B5CF6).copy(alpha = 0.16f)
