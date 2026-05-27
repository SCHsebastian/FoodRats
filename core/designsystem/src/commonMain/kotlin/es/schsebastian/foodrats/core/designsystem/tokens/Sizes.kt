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

    // Raised ember capture button in the floating bottom-bar capsule; pokes above the
    // capsule's top edge by Spacing.lg. Larger than touchTarget so it reads as the primary action.
    val captureButton   = 56.dp
    val mealCardImage   = 220.dp

    // Square thumbnail on the feed list row (FrFeedMealRow); pairs with the
    // bottom-end FrScoreBadge overlay.
    val feedRowThumbnail = 76.dp
    val bottomBarHeight = 80.dp
    val touchTarget     = 48.dp

    // Width-locked circle so single-digit (1..9) and double-digit (10) badges
    // share the same footprint. Pairs with FrTextStyles.statNumberSmall.
    val scoreBadge      = 32.dp

    // Star-shaped score badge overlaid on the feed thumbnail; a touch larger than
    // scoreBadge so the centered numeral stays legible inside the star's silhouette.
    val scoreStar       = 40.dp

    // Per-segment cell for the FrScorePicker (visual). The tap target stays
    // 48dp via Modifier.minimumInteractiveComponentSize.
    val scoreCell       = 40.dp

    // Star glyph size inside the FrStarRatingPicker; tap target is touchTarget.
    val starIcon        = 32.dp
}
