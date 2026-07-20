package com.mtd.common_ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mtd.common_ui.R

/**
 * TASK-08 — single, hoisted source of truth for the app's font families.
 *
 * Previously every `Text` built its family inline as `FontFamily(Font(R.font.x, …))` **inside the
 * composition**, so each one re-allocated a `FontFamily` on every recomposition (~185 sites). These
 * top-level `val`s are allocated once per process; call sites just reference the matching one.
 *
 * Each `val` is a **verbatim hoist** of the exact inline expression it replaces — same font resource AND
 * same declared `FontWeight` argument. This matters: for a single-font family the declared weight is what
 * Compose compares against the requested weight for faux-bold synthesis, so e.g.
 * `Font(iransansmobile_fa_bold, FontWeight.Bold)` (real bold, no synthesis) and
 * `Font(iransansmobile_fa_bold)` (declared Normal → faux-bold when Bold is requested) are NOT
 * interchangeable. Keeping each combination as its own `val` guarantees byte-identical rendering; the only
 * collapse applied is `Font(x)` ≡ `Font(x, FontWeight.Normal)` (that IS the parameter default).
 *
 * The multi-weight [IranSans] / [Inter] / [Vazirmatn] families at the bottom are for `Typography` (Type.kt),
 * where Compose resolves the file from the requested weight.
 */

// ── IranSans — regular file ─────────────────────────────────────────────────────────────────────────
val IranSansRegular = FontFamily(Font(R.font.iransansmobile_fa_regular))
val IranSansRegularMedium = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Medium))
val IranSansRegularLight = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Light))
val IranSansRegularBold = FontFamily(Font(R.font.iransansmobile_fa_regular, FontWeight.Bold))

// ── IranSans — bold file ────────────────────────────────────────────────────────────────────────────
val IranSansBold = FontFamily(Font(R.font.iransansmobile_fa_bold))
val IranSansBoldBold = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Bold))
val IranSansBoldMedium = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.Medium))
val IranSansBoldExtraBold = FontFamily(Font(R.font.iransansmobile_fa_bold, FontWeight.ExtraBold))

// ── IranSans — light file ───────────────────────────────────────────────────────────────────────────
val IranSansLight = FontFamily(Font(R.font.iransansmobile_fa_light))
val IranSansLightLight = FontFamily(Font(R.font.iransansmobile_fa_light, FontWeight.Light))
val IranSansLightMedium = FontFamily(Font(R.font.iransansmobile_fa_light, FontWeight.Medium))

// ── Inter — regular file ────────────────────────────────────────────────────────────────────────────
val InterRegular = FontFamily(Font(R.font.inter_regular))
val InterRegularMedium = FontFamily(Font(R.font.inter_regular, FontWeight.Medium))
val InterRegularBold = FontFamily(Font(R.font.inter_regular, FontWeight.Bold))

// ── Inter — medium file ─────────────────────────────────────────────────────────────────────────────
val InterMedium = FontFamily(Font(R.font.inter_medium))
val InterMediumMedium = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))

// ── Inter — bold file ───────────────────────────────────────────────────────────────────────────────
val InterBold = FontFamily(Font(R.font.inter_bold))
val InterBoldBold = FontFamily(Font(R.font.inter_bold, FontWeight.Bold))

// ── Vazirmatn — medium file ─────────────────────────────────────────────────────────────────────────
val VazirmatnMedium = FontFamily(Font(R.font.vazirmatn_medium))
val VazirmatnMediumMedium = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Medium))
val VazirmatnMediumLight = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Light))
val VazirmatnMediumBold = FontFamily(Font(R.font.vazirmatn_medium, FontWeight.Bold))

// ── Vazirmatn — bold file ───────────────────────────────────────────────────────────────────────────
val VazirmatnBold = FontFamily(Font(R.font.vazirmatn_bold))
val VazirmatnBoldBold = FontFamily(Font(R.font.vazirmatn_bold, FontWeight.Bold))

// ── Multi-weight families (for Typography — resolves the file by requested weight) ───────────────────
val IranSans = FontFamily(
    Font(R.font.iransansmobile_fa_light, FontWeight.Light),
    Font(R.font.iransansmobile_fa_regular, FontWeight.Normal),
    Font(R.font.iransansmobile_fa_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)
