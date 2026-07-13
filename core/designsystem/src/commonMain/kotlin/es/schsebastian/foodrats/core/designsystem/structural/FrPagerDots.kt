package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import es.schsebastian.foodrats.core.designsystem.preview.FrPreview
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme

/**
 * Small page-position indicator for a horizontal pager sitting over a media floor (e.g. the
 * multi-photo meal-detail header). Purely decorative — it carries no semantics of its own; a
 * caller that needs an accessible page announcement puts a `contentDescription` on the pager
 * container instead (mirrors [FrScrim], which is also a pure visual layer with no semantics of
 * its own). Colors reuse [StructuralColors.onMedia] at the same alpha idiom already used for
 * on-media micro content ([FrMicroRow]'s dot separators) — full-alpha white marks the current
 * page, dim white the rest — so it reads correctly over any photo without inventing a new color
 * role. Renders nothing for `pageCount <= 1` (no pager to indicate).
 */
@Composable
fun FrPagerDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pageCount) { index ->
            val active = index == currentPage
            Box(
                modifier = Modifier
                    .testTag("pagerDot")
                    .padding(vertical = 1.dp)
                    .size(if (active) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(StructuralColors.onMedia.copy(alpha = if (active) 0.95f else 0.4f)),
            )
        }
    }
}

@FrPreview
@Composable
private fun FrPagerDotsPreview() {
    FoodRatsTheme(darkTheme = true) {
        Box(Modifier.background(StructuralColors.dishRamen).padding(24.dp)) {
            FrPagerDots(pageCount = 4, currentPage = 1)
        }
    }
}
