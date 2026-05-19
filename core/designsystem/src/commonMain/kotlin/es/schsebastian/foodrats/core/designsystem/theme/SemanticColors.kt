package es.schsebastian.foodrats.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Role-named colors that don't fit Material's primary/secondary/tertiary scheme.
 *
 * Material covers brand roles; these cover *meaning* roles — success/warning/danger/info
 * plus product-specific celebration and streak states. Inject via [LocalFrSemanticColors],
 * which is wired from [FoodRatsTheme].
 *
 * `success`, `danger`, and `celebration` intentionally alias to scheme roles so they shift
 * with the brand palette; `streakHot`, `warning`, and `info` are standalone.
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
)

internal val FoodRatsLightSemanticColors = FrSemanticColors(
    success       = Color(0xFF3FB950),
    onSuccess     = Color(0xFFFFFFFF),
    warning       = Color(0xFFF59E0B),
    onWarning     = Color(0xFF2C1A00),
    danger        = Color(0xFFBA1A1A),
    onDanger      = Color(0xFFFFFFFF),
    info          = Color(0xFF3B82F6),
    onInfo        = Color(0xFFFFFFFF),
    celebration   = Color(0xFFE84B6A),
    onCelebration = Color(0xFFFFFFFF),
    streakHot     = Color(0xFFF97316),
    onStreakHot   = Color(0xFFFFFFFF),
)

internal val FoodRatsDarkSemanticColors = FrSemanticColors(
    success       = Color(0xFF6EDC78),
    onSuccess     = Color(0xFF003910),
    warning       = Color(0xFFFCD34D),
    onWarning     = Color(0xFF432B00),
    danger        = Color(0xFFFFB4AB),
    onDanger      = Color(0xFF690005),
    info          = Color(0xFF93C5FD),
    onInfo        = Color(0xFF0B2E66),
    celebration   = Color(0xFFFFB1BD),
    onCelebration = Color(0xFF5F1124),
    streakHot     = Color(0xFFFB923C),
    onStreakHot   = Color(0xFF3A1A00),
)

val LocalFrSemanticColors = staticCompositionLocalOf { FoodRatsLightSemanticColors }
