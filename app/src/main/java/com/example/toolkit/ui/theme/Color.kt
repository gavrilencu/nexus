package com.example.toolkit.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Enterprise Dark palette v4 — a calm, professional slate-navy surface system
// with a confident blue→indigo brand accent. Designed to read as trustworthy,
// modern productivity software (think 1Password / Linear / Vercel) rather than
// a neon terminal.
//
// IMPORTANT: the val NAMES are intentionally kept stable (some still read
// "…Green") so this retheme cascades through every screen and component without
// touching ~45 call sites. Treat the names as semantic slots, not literal hues:
//   VoidBlack/MatrixBlack/PanelGreen/SurfaceRaised = surfaces (darkest → raised)
//   NeonGreen = primary brand accent · DimGreen = secondary accent
//   SoftGreen = positive/success · AlertAmber/AlertRed = warning/error
//   GhostWhite/MuteGreen/TerminalGray = text (primary → secondary → tertiary)
//   BorderGreen/GlassBorder = hairlines & card outlines

// ── Surfaces (neutral slate-navy, no color tint) ──
val VoidBlack = Color(0xFF0B0F17)        // app background — deep slate-navy
val MatrixBlack = Color(0xFF11151F)      // top bar / drawer / recessed surfaces
val PanelGreen = Color(0xFF151B27)       // card background (paired with GlassBorder)
val SurfaceRaised = Color(0xFF1A2130)    // raised inputs / elevated fills
val BorderGreen = Color(0xFF263143)      // hairlines, dividers, default outlines

// ── Brand accent (blue → indigo) ──
val NeonGreen = Color(0xFF4F7CFF)        // primary accent — professional azure
val DimGreen = Color(0xFF6366F1)         // secondary accent — indigo
val AccentSoft = Color(0xFF4F7CFF).copy(alpha = 0.14f)

// ── Semantic status ──
val SoftGreen = Color(0xFF34D399)        // success / "online" / positive
val AlertAmber = Color(0xFFF5A623)       // warnings
val AlertRed = Color(0xFFEF5C5C)         // errors / destructive

// ── Text (cool neutrals) ──
val GhostWhite = Color(0xFFE8ECF4)       // primary text — near-white, slightly cool
val MuteGreen = Color(0xFF9AA4B8)        // secondary text, captions
val TerminalGray = Color(0xFF6B7488)     // tertiary text / metadata

// Signature accent gradient — used on primary buttons, module icon badges and
// brand wordmarks. A subtle azure→indigo sweep.
val GradientStart = Color(0xFF5B8CFF)    // azure
val GradientEnd = Color(0xFF6366F1)      // indigo
val AccentGradient = Brush.linearGradient(listOf(GradientStart, GradientEnd))

// Card outline token — used with a low alpha at call sites for a subtle hairline.
val GlassBorder = Color(0xFF5B6B85)

// Foreground on the brand accent / gradient (buttons, icon badges, selected tabs).
// White reads best on the blue→indigo accent (the old palette used black on green).
val OnAccent = Color.White

