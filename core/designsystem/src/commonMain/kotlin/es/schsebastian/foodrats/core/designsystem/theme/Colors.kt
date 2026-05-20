package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand palette — Iron & Ember.
// Deep olive primary anchors a rugged, masculine identity; ember-copper secondary
// punches in for streaks and rare highlights; rust tertiary signals scarce
// celebration moments. Surfaces sit on warm concrete in light and charcoal-olive
// in dark — both intentionally low-saturation so the meaning colors carry weight.
private val OliveLight          = Color(0xFF4F6E2B)
private val OliveLightOn        = Color(0xFFFFFFFF)
private val OliveContainer      = Color(0xFF9CB47A)
private val OliveOnContainer    = Color(0xFF1A2509)

private val EmberLight          = Color(0xFFB0561E)
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
private val OnConcreteElevated  = Color(0xFF44483D)
private val OutlineLight        = Color(0xFF5E6155)
private val OutlineVariantLight = Color(0xFFB8BCB0)
private val InverseSurfaceLight = Color(0xFF2A2C26)
private val InverseOnLight      = Color(0xFFEEEDE6)

private val OliveDark           = Color(0xFFA8BC85)
private val OliveDarkOn         = Color(0xFF1F3209)
private val OliveContainerD     = Color(0xFF3A5021)
private val OliveOnContainerD   = Color(0xFFC5D9A3)

private val EmberDark           = Color(0xFFE6A47B)
private val EmberOnDark         = Color(0xFF3B1D00)
private val EmberContainerD     = Color(0xFF5A2A0D)
private val EmberOnContainerD   = Color(0xFFF2C8A8)

private val RustDark            = Color(0xFFC58D80)
private val RustOnDark          = Color(0xFF3D0E00)
private val RustContainerD      = Color(0xFF5C1F0F)
private val RustOnContainerD    = Color(0xFFF2C8B5)

private val ErrorDark           = Color(0xFFFFB4AB)
private val ErrorOnDark         = Color(0xFF690005)
private val ErrorContainerDark  = Color(0xFF7A1313)
private val ErrorOnContainerDk  = Color(0xFFFFDAD6)

private val Charcoal            = Color(0xFF1B1C19)
private val OnCharcoal          = Color(0xFFE3E3DC)
private val CharcoalElevated    = Color(0xFF3A3D36)
private val OnCharcoalElevated  = Color(0xFFC4C8BB)
private val OutlineDark         = Color(0xFF8E9387)
private val OutlineVariantDark  = Color(0xFF44483D)

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
