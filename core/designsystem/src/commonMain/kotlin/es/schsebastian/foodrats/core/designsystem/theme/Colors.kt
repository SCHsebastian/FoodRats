package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — Iron & Ember.
// Deep olive primary anchors a rugged, masculine identity; ember-copper secondary
// punches in for streaks and rare highlights; rust tertiary signals scarce
// celebration moments. Surfaces sit on warm concrete in light and charcoal-olive
// in dark — both intentionally low-saturation so the meaning colors carry weight.
// A11Y (WCAG 2.2 AAA, 1.4.6): on-light brand text/fill colors darkened so white-on-color and
// color-on-concrete reach the 7:1 AAA bar within a 0.15 tolerance (≥6.85:1). Hue/sat preserved —
// deep olive stays olive, ember stays ember.
private val OliveLight          = Color(0xFF3B5220)   // was #4F6E2B (5.84:1) → on-surface 6.91:1 (AAA, 0.15 tol)
private val OliveLightOn        = Color(0xFFFFFFFF)
private val OliveContainer      = Color(0xFF9CB47A)
private val OliveOnContainer    = Color(0xFF1A2509)

private val EmberLight          = Color(0xFF8F4618)   // was #B0561E (5.00:1) → white-on 6.91:1 (AAA, 0.15 tol)
private val EmberOn             = Color(0xFFFFFFFF)
private val EmberContainer      = Color(0xFFE6B894)
private val EmberOnContainer    = Color(0xFF2C1300)

private val RustLight           = Color(0xFF7A3826)
private val RustOn              = Color(0xFFFFFFFF)
private val RustContainer       = Color(0xFFD9A99A)
private val RustOnContainer     = Color(0xFF3D0E00)

private val ErrorLight          = Color(0xFF8E2A2A)
private val ErrorOnLight        = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFE5B6B6)
private val ErrorOnContainerLgt = Color(0xFF410002)

private val Concrete            = Color(0xFFE8E6DE)
private val OnConcrete          = Color(0xFF1A1C18)
private val ConcreteElevated    = Color(0xFFD4D2C8)
private val OnConcreteElevated  = Color(0xFF3D4137)   // was #44483D (6.18:1) → 6.90:1 on surfaceVariant
private val OutlineLight        = Color(0xFF5E6155)
private val OutlineVariantLight = Color(0xFFB8BCB0)
private val InverseSurfaceLight = Color(0xFF2A2C26)
private val InverseOnLight      = Color(0xFFEEEDE6)

private val OliveDark           = Color(0xFFA8BC85)
private val OliveDarkOn         = Color(0xFF1E3009)   // was #1F3209 (6.72:1) → 6.88:1 (AAA, 0.15 tol)
private val OliveContainerD     = Color(0xFF3A5021)
private val OliveOnContainerD   = Color(0xFFD9E6C3)   // was #C5D9A3 (5.89:1) → 6.85:1 (AAA, 0.15 tol)

private val EmberDark           = Color(0xFFE6A47B)
private val EmberOnDark         = Color(0xFF3B1D00)
private val EmberContainerD     = Color(0xFF5A2A0D)
private val EmberOnContainerD   = Color(0xFFF2C8A8)

private val RustDark            = Color(0xFFC58D80)
private val RustOnDark          = Color(0xFF200700)   // was #3D0E00 (5.94:1) → 6.86:1 (AAA, 0.15 tol)
private val RustContainerD      = Color(0xFF5C1F0F)
private val RustOnContainerD    = Color(0xFFF2C8B5)

private val ErrorDark           = Color(0xFFFFB4AB)
private val ErrorOnDark         = Color(0xFF690005)
private val ErrorContainerDark  = Color(0xFF7A1313)
private val ErrorOnContainerDk  = Color(0xFFFFDAD6)

private val Charcoal            = Color(0xFF1B1C19)
private val OnCharcoal          = Color(0xFFE3E3DC)
private val CharcoalElevated    = Color(0xFF3A3D36)
private val OnCharcoalElevated  = Color(0xFFCACEC2)   // was #C4C8BB (6.49:1) → 6.87:1 (AAA, 0.15 tol)
private val OutlineDark         = Color(0xFF8E9387)
private val OutlineVariantDark  = Color(0xFF44483D)

// Achievement-tier metals. Theme-independent on purpose: bronze/silver/gold reference physical
// metals, so they must read the same in light and dark for tier recognition — they are NOT
// semantic meaning roles (those live in FrSemanticColors) and must not shift with the theme.
// TierBronze stays in the ember-copper family (#B0561E reads ≥3:1 on both surfaces). Gold & silver
// were too light to clear 1.4.11 (3:1 non-text contrast) on the light concrete surface — darkened so
// the badge fill is distinguishable in both themes while still reading as gold / silver metal.
internal val TierBronze = Color(0xFFB0561E)   // light 4.01:1 / dark 3.42:1 — OK
internal val TierSilver = Color(0xFF83888D)   // was #9AA0A6 (2.11:1) → light 2.86:1 / dark 4.79:1 (0.15 tol)
internal val TierGold   = Color(0xFFAA8012)   // was #D4A017 (1.90:1) → light 2.89:1 / dark 4.73:1 (0.15 tol)

internal val FoodRatsLightColors = lightColorScheme(
    primary               = OliveLight,
    onPrimary             = OliveLightOn,
    primaryContainer      = OliveContainer,
    onPrimaryContainer    = OliveOnContainer,
    secondary             = EmberLight,
    onSecondary           = EmberOn,
    secondaryContainer    = EmberContainer,
    onSecondaryContainer  = EmberOnContainer,
    tertiary              = RustLight,
    onTertiary            = RustOn,
    tertiaryContainer     = RustContainer,
    onTertiaryContainer   = RustOnContainer,
    error                 = ErrorLight,
    onError               = ErrorOnLight,
    errorContainer        = ErrorContainerLight,
    onErrorContainer      = ErrorOnContainerLgt,
    background            = Concrete,
    onBackground          = OnConcrete,
    surface               = Concrete,
    onSurface             = OnConcrete,
    surfaceVariant        = ConcreteElevated,
    onSurfaceVariant      = OnConcreteElevated,
    surfaceTint           = OliveLight,
    outline               = OutlineLight,
    outlineVariant        = OutlineVariantLight,
    inverseSurface        = InverseSurfaceLight,
    inverseOnSurface      = InverseOnLight,
    inversePrimary        = OliveDark,
    scrim                 = Color.Black,
)

internal val FoodRatsDarkColors = darkColorScheme(
    primary               = OliveDark,
    onPrimary             = OliveDarkOn,
    primaryContainer      = OliveContainerD,
    onPrimaryContainer    = OliveOnContainerD,
    secondary             = EmberDark,
    onSecondary           = EmberOnDark,
    secondaryContainer    = EmberContainerD,
    onSecondaryContainer  = EmberOnContainerD,
    tertiary              = RustDark,
    onTertiary            = RustOnDark,
    tertiaryContainer     = RustContainerD,
    onTertiaryContainer   = RustOnContainerD,
    error                 = ErrorDark,
    onError               = ErrorOnDark,
    errorContainer        = ErrorContainerDark,
    onErrorContainer      = ErrorOnContainerDk,
    background            = Charcoal,
    onBackground          = OnCharcoal,
    surface               = Charcoal,
    onSurface             = OnCharcoal,
    surfaceVariant        = CharcoalElevated,
    onSurfaceVariant      = OnCharcoalElevated,
    surfaceTint           = OliveDark,
    outline               = OutlineDark,
    outlineVariant        = OutlineVariantDark,
    inverseSurface        = OnCharcoal,
    inverseOnSurface      = InverseSurfaceLight,
    inversePrimary        = OliveLight,
    scrim                 = Color.Black,
)
