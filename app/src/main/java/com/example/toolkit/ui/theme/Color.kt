package com.example.toolkit.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Hacker / matrix-terminal palette v3 — phosphor green on near-black, with a
// subtle green-tinted glass surface system and a lime→emerald gradient accent
// (think classic CRT terminal). Variable names are kept stable so the retheme
// cascades through every screen and component without touching call sites.
val VoidBlack = Color(0xFF040705)        // app background — near-black w/ faint green
val MatrixBlack = Color(0xFF080D0A)      // top bar / drawer / recessed surfaces
val PanelGreen = Color(0xFF0B140F)       // glass card background (paired with GlassBorder)
val BorderGreen = Color(0xFF1B3A2A)      // hairlines, dividers, default outlines
val NeonGreen = Color(0xFF00FF9C)        // primary accent (phosphor green)
val SoftGreen = Color(0xFF00E676)        // success / "online" / positive status
val DimGreen = Color(0xFF14C76B)         // secondary accent (deeper green)
val MuteGreen = Color(0xFF5FA981)        // secondary text, captions
val AlertAmber = Color(0xFFFFC043)       // warnings
val AlertRed = Color(0xFFFF5C5C)         // errors / destructive
val GhostWhite = Color(0xFFCFFBE6)       // primary text — soft phosphor white-green
val TerminalGray = Color(0xFF7CA893)     // tertiary text / metadata

// Gradient accent — the signature of the terminal look. Used on primary
// buttons, module icon badges, and headline wordmarks.
val GradientStart = Color(0xFF00FF9C)    // phosphor mint
val GradientEnd = Color(0xFF00C853)      // emerald
val AccentGradient = Brush.linearGradient(listOf(GradientStart, GradientEnd))

// Glass-card surface tokens.
val SurfaceRaised = Color(0xFF0E1A13)
val GlassBorder = Color(0xFF00FF9C)      // used with alpha at call sites — glows green
val AccentSoft = Color(0xFF00FF9C).copy(alpha = 0.14f)
