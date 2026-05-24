package com.datacollector.android.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────────────────
// Soft lavender backdrop with vivid pastel blocks. Calm, modern, restful —
// the whole app sits on a single light-purple canvas so cards pop without
// shouting.

val LavenderBackground = Color(0xFFEBE6F5)   // primary page background
val LavenderBackgroundDeep = Color(0xFFDED7EE) // dividers, chips, alt cards
val LavenderTint = Color(0xFFE6E3FF)         // subtle highlights
val Lavender = Color(0xFFBCB6FF)             // primary container / hero
val LavenderDeep = Color(0xFF7A73E0)         // primary
val LavenderInk = Color(0xFF453F8A)          // accent text on light bg

val PaperWhite = Color(0xFFFFFFFF)           // surface (cards)

val Peach = Color(0xFFFFC9A0)
val PeachDeep = Color(0xFFE89762)
val Mint = Color(0xFFB4E8C9)
val MintDeep = Color(0xFF62B889)
val Sun = Color(0xFFFFD93D)

val Ink = Color(0xFF1B1A2E)                  // body text
val InkDark = Ink                            // alias kept for legacy references
val InkMuted = Color(0xFF6E6B85)
val InkSoft = Color(0xFF9D9AB3)

val NavPill = Color(0xFF1B1A2E)              // floating bottom nav

// ── Score signal ──────────────────────────────────────────────────────────

val ScoreCalm = Color(0xFF62B889)
val ScoreSteady = Color(0xFFE8B84B)
val ScoreAlert = Color(0xFFE07A6A)

// ── Legacy aliases (still referenced in a few places) ────────────────────

val Cream = LavenderBackground
val CreamDeep = LavenderBackgroundDeep
val SurfaceLight = PaperWhite
val SurfaceDim = LavenderBackgroundDeep
