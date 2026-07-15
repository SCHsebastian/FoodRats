package es.schsebastian.foodrats.feature.stats.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Compact `dd/MM` in the device zone, shared by the passport and pokédex cells. Numeric (no CLDR month
 * names, which aren't uniformly available in commonMain) so it renders identically on both platforms,
 * and the surrounding "Collected/Caught %1$s" wrapper is the localized part. Dropped the 4-digit year
 * (was ISO `yyyy-MM-dd`) so the caption fits one line in the narrow 4-up grid cell instead of
 * truncating ("Conseguid…").
 */
internal fun formatCollectionDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    @Suppress("DEPRECATION")
    val month = date.monthNumber.toString().padStart(2, '0')
    @Suppress("DEPRECATION")
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$day/$month"
}

/**
 * Two-line caption slot height (micro line-height × 2), shared by the pokédex and passport grid cells
 * so every cell in the collection grid stays aligned regardless of caught/locked caption length.
 * Memoized against the current density instead of recomputed on every recomposition.
 */
@Composable
internal fun rememberCaptionSlotHeight(): Dp {
    val density = LocalDensity.current
    val microFontSize = StructuralType.micro.fontSize
    return remember(density, microFontSize) { with(density) { (microFontSize * 1.3f * 2).toDp() } }
}

/**
 * Two-line name slot height (body line-height × 2), shared by the collection grid cells so 1-line and
 * 2-line names never change tile height. Memoized against the current density instead of recomputed on
 * every recomposition.
 */
@Composable
internal fun rememberNameSlotHeight(): Dp {
    val density = LocalDensity.current
    val bodyFontSize = StructuralType.body.fontSize
    return remember(density, bodyFontSize) { with(density) { (bodyFontSize * 1.5f * 2).toDp() } }
}

/**
 * Caption-text color shared by the pokédex and passport grid cells — a foreground tint dimmed a touch
 * more in light theme so the caption reads as secondary against the warm-white floor without going
 * invisible.
 */
@Composable
internal fun captionTextColor(): Color =
    StructuralColors.foreground.copy(alpha = if (StructuralColors.isLight) 0.65f else 0.5f)
