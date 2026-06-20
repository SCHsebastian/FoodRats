package es.schsebastian.foodrats.feature.moderation.presentation.blocked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrEmptyState
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey
import es.schsebastian.foodrats.feature.moderation.presentation.components.FrBlockedUserRow
import es.schsebastian.foodrats.feature.moderation.presentation.toStringKey
import org.koin.compose.viewmodel.koinViewModel

/**
 * The blocked-users management surface (UGC compliance §5). Lists every account the signed-in user has
 * blocked and lets them unblock. Reached from Profile. The list is driven entirely by
 * [BlockedUsersViewModel] state (`BlockedAccountsPort.observeBlocked` ⨯ `AccountReadPort.observeMany`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    vm: BlockedUsersViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box {
    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { es.schsebastian.foodrats.core.designsystem.atoms.FrText(text = resolve(ModerationStringKey.BlockedTitle)) },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(ModerationStringKey.BlockedBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { FrProgressIndicator() }

            state.blockedIds.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FrEmptyState(
                    icon = FrIcons.Person,
                    headline = resolve(ModerationStringKey.BlockedEmptyHeadline),
                    subtext = resolve(ModerationStringKey.BlockedEmptySubtext),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().frContentWidth().padding(horizontal = Spacing.lg),
                contentPadding = PaddingValues(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                state.error?.let { err ->
                    item { FrErrorBanner(text = resolve(err.toStringKey())) }
                }
                item {
                    FrCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) {
                        state.blockedIds.forEachIndexed { index, id ->
                            FrBlockedUserRow(
                                account = state.identities[id],
                                unblocking = id in state.unblockingIds,
                                onUnblock = { vm.onIntent(BlockedUsersIntent.Unblock(id)) },
                            )
                            if (index < state.blockedIds.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.unblockSuccess) {
        UnblockSuccessToast(
            message = resolve(ModerationStringKey.UnblockSuccess),
            onDismiss = { vm.onIntent(BlockedUsersIntent.DismissUnblockSuccess) },
        )
    }
    } // end Box
}

/**
 * Auto-dismissing overlay toast that confirms a successful unblock (UGC compliance §5).
 * Mirrors the `ShareOutcomeToast` pattern in `:feature:feed` — no cross-module import needed.
 */
@Composable
private fun UnblockSuccessToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2500)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.BottomCenter) {
        FrCard {
            FrText(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
