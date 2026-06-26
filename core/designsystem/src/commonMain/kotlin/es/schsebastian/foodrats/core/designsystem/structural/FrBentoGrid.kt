package es.schsebastian.foodrats.core.designsystem.structural

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing

/**
 * One cell in an [FrBentoGrid]. [colSpan] (1..columns) is the cell's width = its **data priority**:
 * the top plate spans wide, the low plate spans narrow — size *is* the ranking. Height is owned by
 * [content] (size it with an aspect ratio or a fixed height per priority).
 */
data class FrBentoItem(
    val colSpan: Int,
    val content: @Composable () -> Unit,
)

/**
 * The asymmetric **bento** grid (`structural.css` `.bento`): an N-column track where each tile's
 * width scales to its priority via [FrBentoItem.colSpan]. Items are packed greedily into rows;
 * a row that doesn't fill the track keeps its tiles at true width (a trailing spacer holds the gap),
 * so a lone `c3` reads as half-width — priority stays honest.
 *
 * This is column-span only (the common case). For a true row-spanning mosaic, compose nested
 * [FrBentoGrid]s or size tile heights directly.
 */
/**
 * Greedy column-span packing: fills each row up to [columns], starting a new row when the next tile
 * would overflow. Pure — shared by [FrBentoGrid] (eager) and [frBentoItems] (lazy) so both lay tiles
 * out identically.
 */
private fun packBentoRows(items: List<FrBentoItem>, columns: Int): List<List<FrBentoItem>> {
    val packed = mutableListOf<MutableList<FrBentoItem>>()
    var current = mutableListOf<FrBentoItem>()
    var used = 0
    for (item in items) {
        val span = item.colSpan.coerceIn(1, columns)
        if (used + span > columns) {
            if (current.isNotEmpty()) packed.add(current)
            current = mutableListOf()
            used = 0
        }
        current.add(item.copy(colSpan = span))
        used += span
    }
    if (current.isNotEmpty()) packed.add(current)
    return packed
}

/** One packed bento row: weighted tiles + a trailing spacer holding any unfilled track. */
@Composable
private fun BentoRow(row: List<FrBentoItem>, columns: Int, horizontalGap: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
    ) {
        var sum = 0
        row.forEach { item ->
            Box(Modifier.weight(item.colSpan.toFloat())) { item.content() }
            sum += item.colSpan
        }
        val remaining = columns - sum
        if (remaining > 0) {
            Spacer(Modifier.weight(remaining.toFloat()))
        }
    }
}

@Composable
fun FrBentoGrid(
    items: List<FrBentoItem>,
    modifier: Modifier = Modifier,
    columns: Int = 6,
    horizontalGap: Dp = Spacing.sm,
    verticalGap: Dp = Spacing.sm,
) {
    val rows = remember(items, columns) { packBentoRows(items, columns) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(verticalGap)) {
        rows.forEach { row -> BentoRow(row = row, columns = columns, horizontalGap = horizontalGap) }
    }
}

/**
 * Lazy equivalent of [FrBentoGrid]: emits the packed bento rows as individual [LazyListScope] items so
 * OFF-SCREEN rows — and crucially their tile content (image decodes especially) — are never composed.
 * Use this when the bento is part of a larger scrolling plane that is itself a `LazyColumn` (a plain
 * [FrBentoGrid] inside a `verticalScroll` composes and decodes every tile of the list at once).
 *
 * Inter-row spacing is owned by the host column's `verticalArrangement` (set it to the bento's vertical
 * gap), which matches [FrBentoGrid]'s [verticalGap][FrBentoGrid] when the column uses
 * `Arrangement.spacedBy`. [keyPrefix] keeps these row keys distinct from the host's other lazy items.
 */
fun LazyListScope.frBentoItems(
    items: List<FrBentoItem>,
    columns: Int = 6,
    horizontalGap: Dp = Spacing.sm,
    keyPrefix: String = "bento",
) {
    packBentoRows(items, columns).forEachIndexed { index, row ->
        item(key = "$keyPrefix-row-$index") {
            BentoRow(row = row, columns = columns, horizontalGap = horizontalGap)
        }
    }
}
