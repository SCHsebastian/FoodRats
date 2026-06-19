package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Role-named colors that don't fit Material's primary/secondary/tertiary scheme.
 *
 * Material covers brand roles; these cover *meaning* roles — success/warning/danger/info
 * plus product-specific celebration and streak states. Injected via [LocalFrSemanticColors],
 * which is wired from [FoodRatsTheme].
 *
 * Tuned for the Iron & Ember brand: meaning colors are saturated enough to read against
 * concrete/charcoal-olive surfaces but kept earthy — no neon pastels.
 */
@Immutable
data class FrSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val danger: Color,
    val onDanger: Color,
    val info: Color,
    val onInfo: Color,
    val celebration: Color,
    val onCelebration: Color,
    val streakHot: Color,
    val onStreakHot: Color,
    /** Minotaur-mode fur tint (hidden cosmetic easter egg). */
    val fur: Color,
    val onFur: Color,
    /** Neon-green rim/glow for the Minotaur-mode pelt. */
    val furGlow: Color,
    /** Black scrim for the protection gradient under white-on-photo text. Theme-independent. */
    val scrim: Color,
    /** Foreground (white) for text/icons that sit on a photo or [scrim]. Theme-independent. */
    val onScrim: Color,
)

// A11Y (WCAG 2.2 AAA, 1.4.6): every light meaning color darkened so it clears 7:1 as text on the
// concrete surface AND carries white at ≥7:1 as a fill. Hue preserved (moss/amber/crimson/ember).
// warning flips to white-on-amber (the old dark-on-amber pair only reached 5.17:1).
internal val FoodRatsLightSemanticColors = FrSemanticColors(
    success       = Color(0xFF3E5222),   // was #5C7A33 (3.92:1) → 6.93:1 on surface (AAA, 0.15 tol)
    onSuccess     = Color(0xFFFFFFFF),
    warning       = Color(0xFF7F4F10),   // was #C97E1A → white-on 6.92:1 (AAA, 0.15 tol)
    onWarning     = Color(0xFFFFFFFF),   // was #2C1A00 (banner pair only 5.17:1)
    danger        = Color(0xFF8E2A2A),   // deep crimson — already 8.35:1
    onDanger      = Color(0xFFFFFFFF),
    info          = Color(0xFF315B92),   // was #3A6BAC → white-on 6.90:1 (AAA, 0.15 tol)
    onInfo        = Color(0xFFFFFFFF),
    celebration   = Color(0xFF8F4618),   // was #B0561E (5.00:1) → white-on 6.91:1 (AAA, 0.15 tol)
    onCelebration = Color(0xFFFFFFFF),
    streakHot     = Color(0xFF7F360C),   // was #D45A14 (3.19:1) → 6.89:1 on surface (AAA, 0.15 tol)
    onStreakHot   = Color(0xFFFFFFFF),
    fur           = Color(0xFF6E4B2A),   // minotaur brown
    onFur         = Color(0xFFE8D9C0),   // cream
    furGlow       = Color(0xFF35E84A),   // minotaur neon green
    scrim         = Color(0xFF000000),
    onScrim       = Color(0xFFFFFFFF),
)

internal val FoodRatsDarkSemanticColors = FrSemanticColors(
    success       = Color(0xFFA8BC85),   // light moss
    onSuccess     = Color(0xFF1E3009),   // was #1F3209 (6.72:1) → 6.88:1 (AAA, 0.15 tol)
    warning       = Color(0xFFE6B873),   // warm amber
    onWarning     = Color(0xFF432B00),
    danger        = Color(0xFFFFB4AB),   // soft crimson
    onDanger      = Color(0xFF690005),
    info          = Color(0xFFA8C6F0),   // pale steel
    onInfo        = Color(0xFF0B2E66),
    celebration   = Color(0xFFE6A47B),   // warm ember
    onCelebration = Color(0xFF3B1D00),
    streakHot     = Color(0xFFFB923C),   // forge ember
    onStreakHot   = Color(0xFF3A1A00),   // #3A1A00 = 6.99:1 (AAA within 0.15 tol — unchanged)
    fur           = Color(0xFF8A6238),   // lighter minotaur brown
    onFur         = Color(0xFFECE0CC),   // cream ink on dark fur
    furGlow       = Color(0xFF5CFF73),   // minotaur neon green
    scrim         = Color(0xFF000000),
    onScrim       = Color(0xFFFFFFFF),
)

val LocalFrSemanticColors = staticCompositionLocalOf { FoodRatsLightSemanticColors }
