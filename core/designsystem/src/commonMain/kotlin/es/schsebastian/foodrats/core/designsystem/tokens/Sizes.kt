package es.schsebastian.foodrats.core.designsystem.tokens
import androidx.compose.ui.unit.dp
object Sizes {
    val avatarSm        = 24.dp
    val avatarMd        = 40.dp
    val avatarLg        = 64.dp
    val iconSm          = 16.dp
    val iconMd          = 24.dp
    val iconLg          = 32.dp
    val shutter         = 72.dp
    val streakBadge     = 56.dp
    val mealCardImage   = 220.dp
    val bottomBarHeight = 80.dp
    val touchTarget     = 48.dp

    // Width-locked circle so single-digit (1..9) and double-digit (10) badges
    // share the same footprint. Pairs with FrTextStyles.statNumberSmall.
    val scoreBadge      = 32.dp

    // Per-segment cell for the FrScorePicker (visual). The tap target stays
    // 48dp via Modifier.minimumInteractiveComponentSize.
    val scoreCell       = 40.dp

    // Star glyph size inside the FrStarRatingPicker; tap target is touchTarget.
    val starIcon        = 32.dp
}
