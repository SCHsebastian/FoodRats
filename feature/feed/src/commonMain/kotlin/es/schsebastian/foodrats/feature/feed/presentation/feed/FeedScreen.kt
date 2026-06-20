package es.schsebastian.foodrats.feature.feed.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrUploadProgressBar
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrFeedLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.i18n.resolvePlural
import es.schsebastian.foodrats.feature.feed.domain.error.FeedError
import es.schsebastian.foodrats.feature.feed.i18n.FeedPluralKey
import es.schsebastian.foodrats.feature.feed.i18n.FeedStringKey
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedDayHeader
import es.schsebastian.foodrats.feature.feed.presentation.components.FrFeedMealRow
import es.schsebastian.foodrats.feature.feed.presentation.components.FrSyncStatusBar
import es.schsebastian.foodrats.feature.feed.presentation.components.FrUploadQueueBar
import es.schsebastian.foodrats.feature.feed.presentation.toStringKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import org.koin.compose.viewmodel.koinViewModel

/** Number of meal-row placeholders shown while the first day window loads. */
private const val FEED_SKELETON_ROWS = 5

@Composable
fun FeedScreen(
    onPickCrewClick: () -> Unit,
    onMealClick: (mealId: String, dayIso: String) -> Unit,
    vm: FeedViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FrScreenScaffold(contentWindowInsets = WindowInsets(0)) {
        // FrFeedLayout slots are `dayHeader` and `list` (not header/body per plan).
        FrFeedLayout(
            dayHeader = {
                val date = state.day?.day?.date
                val today = state.today
                val iso = date?.toString().orEmpty()
                val isToday = date != null && date == today
                val isYesterday = date != null && today != null &&
                    date == today.minus(DatePeriod(days = 1))
                val primary = when {
                    isToday -> resolve(FeedStringKey.Title)
                    isYesterday -> resolve(FeedStringKey.Yesterday)
                    else -> iso
                }
                // Secondary date line only when the primary is a relative word.
                val secondary = if (isToday || isYesterday) iso else ""
                Column(modifier = Modifier.fillMaxWidth()) {
                    FrUploadProgressBar(visible = state.isUploadActive)
                    // Offline-first publish queue indicator (roadmap §5.2): pending +
                    // terminal-failed counts with retry/dismiss. Hides itself when empty.
                    FrUploadQueueBar(
                        pending = state.queuedPending,
                        failed = state.queuedFailed,
                        onRetry = { vm.onIntent(FeedIntent.RetryQueuedDrafts) },
                        onDismiss = { vm.onIntent(FeedIntent.DismissQueuedDrafts) },
                    )
                    // Write-outbox sync indicator (P2 §1 T8): rate/comment/reaction/
                    // crew-admin mutations parked while offline. Hides itself when empty.
                    FrSyncStatusBar(
                        pending = state.syncPending,
                        failed = state.syncFailed,
                        onRetry = { vm.onIntent(FeedIntent.RetrySyncOutbox) },
                        onDismiss = { vm.onIntent(FeedIntent.DismissSyncOutbox) },
                    )
                    FrFeedDayHeader(
                        primaryLabel = primary,
                        secondaryLabel = secondary,
                        sortKey = iso,
                        canGoPrev = state.canGoPrev,
                        canGoNext = state.canGoNext,
                        onPrev = { vm.onIntent(FeedIntent.PrevDay) },
                        onNext = { vm.onIntent(FeedIntent.NextDay) },
                    )
                    // Cached-feed freshness (offline-first P4-T2): a subtle "synced X ago" line under
                    // the day header. Hidden until the first sync of the session lands.
                    state.syncedRelative?.let { rel ->
                        FrText(
                            text = resolve(FeedStringKey.SyncedAgo, resolve(rel.key, rel.amount)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .frContentWidth()
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                    }
                }
            },
            list = {
                // Read the state fields once; the body branches off these locals.
                val meals = state.meals
                val error = state.error
                // Pull-to-refresh (offline-first P4-T2): a downward swipe forces a re-pull of the
                // active crew's window via FeedIntent.Refresh; the spinner clears when the fresh
                // sync stamp lands (isRefreshing reset in the VM). Disabled while there's no active
                // crew (nothing to refresh). PullToRefreshBox is the CMP material3 affordance.
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { vm.onIntent(FeedIntent.Refresh) },
                    state = refreshState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    when {
                        error is FeedError.Session.NoActiveCrew -> {
                            Box(
                                modifier = Modifier.frContentWidth().fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FrEmptyState(
                                    icon = FrIcons.Group,
                                    headline = resolve(FeedStringKey.NoActiveCrewHeadline),
                                    subtext = resolve(FeedStringKey.NoActiveCrewSubtext),
                                    cta = {
                                        FrButton(
                                            label = resolve(FeedStringKey.PickCrewCta),
                                            onClick = onPickCrewClick,
                                            variant = FrButtonVariant.Primary,
                                        )
                                    },
                                )
                            }
                        }
                        state.isLoading && meals.isEmpty() -> {
                            FeedLoadingSkeleton()
                        }
                        meals.isEmpty() && error == null -> {
                            // "today" copy only when the cursor is on today (or today is
                            // still resolving); past days get the past-tense variant so we
                            // don't claim "nobody posted today" while viewing last Tuesday.
                            val viewingToday = state.today == null ||
                                state.day?.day?.date == state.today
                            Box(
                                modifier = Modifier.frContentWidth().fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FrEmptyState(
                                    icon = FrIcons.GalleryImport,
                                    headline = resolve(FeedStringKey.EmptyHeadline),
                                    subtext = resolve(
                                        if (viewingToday) FeedStringKey.EmptySubtext
                                        else FeedStringKey.EmptySubtextPast,
                                    ),
                                )
                            }
                        }
                        else -> {
                            val dayIso = state.day?.day?.date?.toString().orEmpty()
                            LazyColumn(
                                modifier = Modifier.frContentWidth().fillMaxSize(),
                                contentPadding = PaddingValues(
                                    horizontal = Spacing.md,
                                    vertical = Spacing.sm,
                                ),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                item(key = "plates-count") {
                                    PlatesCountHeader(
                                        count = meals.size,
                                        modifier = Modifier.frRiseIn(delayMillis = 0),
                                    )
                                }
                                itemsIndexed(meals, key = { _, ui -> ui.mealId }) { index, ui ->
                                    FrFeedMealRow(
                                        ui = ui,
                                        onClick = { onMealClick(ui.mealId, dayIso) },
                                        onReact = { vm.onIntent(FeedIntent.ReactMeal(ui.mealId)) },
                                        // Bespoke fade+rise cascade on first compose. The window is
                                        // kept small (index % 6) so rows scrolled into view later
                                        // still pop promptly instead of waiting on a long stagger.
                                        // animateItem handles reorder/insert placement underneath.
                                        modifier = Modifier
                                            .animateItem()
                                            .frRiseIn(delayMillis = (index % 6) * 40),
                                    )
                                }
                            }
                        }
                    }
                    error?.let { err ->
                        if (err !is FeedError.Session.NoActiveCrew) {
                            FrErrorBanner(text = resolve(err.toStringKey()))
                        }
                    }
                    state.rateError?.let { err ->
                        FrErrorBanner(text = resolve(err.toStringKey()))
                    }
                    state.reactError?.let { err ->
                        FrErrorBanner(text = resolve(err.toStringKey()))
                    }
                }
                }
            },
        )
    }
}

/**
 * The "N plates" list header: a clearer, slightly weightier label than the old muted caption — it
 * names the day's haul as a small headline (onSurface, semibold) with comfortable breathing room
 * below it so the first card doesn't crowd it. The count + pluralization come from
 * [FeedPluralKey.PlatesCount] (i18n owns "plate"/"plates"); it rises first in the entrance cascade.
 */
@Composable
private fun PlatesCountHeader(count: Int, modifier: Modifier = Modifier) {
    FrText(
        text = resolvePlural(FeedPluralKey.PlatesCount, count),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
    )
}

/**
 * Loading placeholder for the feed list: a short stack of shimmer rows that mimic
 * [FrFeedMealRow]'s silhouette (square thumbnail + a title line and a subtitle line),
 * capped to the same content width as the loaded list so the layout doesn't jump when
 * meals arrive. Purely decorative — no text, no content descriptions.
 */
@Composable
private fun FeedLoadingSkeleton() {
    Column(
        modifier = Modifier
            .frContentWidth()
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(FEED_SKELETON_ROWS) {
            FeedSkeletonRow()
        }
    }
}

@Composable
private fun FeedSkeletonRow() {
    FrCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        contentPadding = PaddingValues(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrShimmerBox(
                modifier = Modifier.size(Sizes.feedRowThumbnail),
                shape = RoundedCornerShape(Radius.md),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.6f).height(18.dp),
                    shape = RoundedCornerShape(Radius.sm),
                )
                FrShimmerBox(
                    modifier = Modifier.fillMaxWidth(0.35f).height(14.dp),
                    shape = RoundedCornerShape(Radius.sm),
                )
            }
        }
    }
}
