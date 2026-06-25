package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Curated accent variants for [FoodRatsTheme]. Each entry carries the four brand-primary color
 * overrides ([primary], [onPrimary], [primaryContainer], [onPrimaryContainer]) for both light and
 * dark themes — only the primary-family slots shift; Iron & Ember surfaces stay intact.
 *
 * **No domain types here.** The mapping from domain [AccentPalette] to [FrAccent] lives in
 * presentation (`:shared`'s `FoodRatsApp.kt`), keeping `:core:designsystem` vendor-free and
 * domain-free.
 *
 * Color notes:
 * - Ember = the brand default (unchanged from [FoodRatsLightColors] / [FoodRatsDarkColors]).
 * - All on-color tokens satisfy WCAG AA (≥ 4.5:1) against their container pair.
 */
enum class FrAccent(
    // Light-theme primary family
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    // Dark-theme primary family
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    /** Representative swatch colour for display in pickers — the light-mode primary. */
    val swatch: Color,
) {
    /** Brand default — deep olive. Matches the base [FoodRatsLightColors]/[FoodRatsDarkColors]. */
    Ember(
        lightPrimary           = Color(0xFF3B5220),
        lightOnPrimary         = Color(0xFFFFFFFF),
        lightPrimaryContainer  = Color(0xFF9CB47A),
        lightOnPrimaryContainer = Color(0xFF1A2509),
        darkPrimary            = Color(0xFFA8BC85),
        darkOnPrimary          = Color(0xFF1E3009),
        darkPrimaryContainer   = Color(0xFF3A5021),
        darkOnPrimaryContainer = Color(0xFFD9E6C3),
        swatch                 = Color(0xFF3B5220),
    ),
    /** Moss — warmer medium olive, slightly brighter than Ember. */
    Moss(
        lightPrimary           = Color(0xFF486B2F),
        lightOnPrimary         = Color(0xFFFFFFFF),
        lightPrimaryContainer  = Color(0xFFB0C98A),
        lightOnPrimaryContainer = Color(0xFF1A2D0A),
        darkPrimary            = Color(0xFFB5C98F),
        darkOnPrimary          = Color(0xFF1E3409),
        darkPrimaryContainer   = Color(0xFF3C5A20),
        darkOnPrimaryContainer = Color(0xFFD8EAC4),
        swatch                 = Color(0xFF486B2F),
    ),
    /** Rust — terracotta drawn from the Iron & Ember tertiary. */
    Rust(
        lightPrimary           = Color(0xFF7A3826),
        lightOnPrimary         = Color(0xFFFFFFFF),
        lightPrimaryContainer  = Color(0xFFD9A99A),
        lightOnPrimaryContainer = Color(0xFF3D0E00),
        darkPrimary            = Color(0xFFC58D80),
        darkOnPrimary          = Color(0xFF200700),
        darkPrimaryContainer   = Color(0xFF5C1F0F),
        darkOnPrimaryContainer = Color(0xFFF2C8B5),
        swatch                 = Color(0xFF7A3826),
    ),
    /** Steel — cool blue-grey for a more understated look. */
    Steel(
        lightPrimary           = Color(0xFF2E5271),
        lightOnPrimary         = Color(0xFFFFFFFF),
        lightPrimaryContainer  = Color(0xFF9EC0D8),
        lightOnPrimaryContainer = Color(0xFF071E30),
        darkPrimary            = Color(0xFF9EC0D8),
        darkOnPrimary          = Color(0xFF071E30),
        darkPrimaryContainer   = Color(0xFF1C3D55),
        darkOnPrimaryContainer = Color(0xFFCEE4F4),
        swatch                 = Color(0xFF2E5271),
    ),
    /** Berry — muted plum; warm without breaking the Iron & Ember palette family. */
    Berry(
        lightPrimary           = Color(0xFF6B2C5B),
        lightOnPrimary         = Color(0xFFFFFFFF),
        lightPrimaryContainer  = Color(0xFFCE9DC0),
        lightOnPrimaryContainer = Color(0xFF2E0024),
        darkPrimary            = Color(0xFFCE9DC0),
        darkOnPrimary          = Color(0xFF2E0024),
        darkPrimaryContainer   = Color(0xFF521644),
        darkOnPrimaryContainer = Color(0xFFF0CCEC),
        swatch                 = Color(0xFF6B2C5B),
    ),
}

/**
 * Returns a copy of this [ColorScheme] with the primary-family slots replaced by those from
 * [accent]. All other roles are unchanged so the Iron & Ember surface/secondary/tertiary language
 * stays intact across every accent choice.
 */
internal fun ColorScheme.applyAccent(accent: FrAccent, darkTheme: Boolean): ColorScheme =
    if (darkTheme) {
        copy(
            primary              = accent.darkPrimary,
            onPrimary            = accent.darkOnPrimary,
            primaryContainer     = accent.darkPrimaryContainer,
            onPrimaryContainer   = accent.darkOnPrimaryContainer,
        )
    } else {
        copy(
            primary              = accent.lightPrimary,
            onPrimary            = accent.lightOnPrimary,
            primaryContainer     = accent.lightPrimaryContainer,
            onPrimaryContainer   = accent.lightOnPrimaryContainer,
        )
    }
