package es.schsebastian.foodrats.feature.moderation.presentation.blocked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.moderation.i18n.ModerationStringKey
import es.schsebastian.foodrats.feature.moderation.presentation.components.FrBlockedUserRow
import es.schsebastian.foodrats.feature.moderation.presentation.toStringKey
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * Structural blocked-users management surface (UGC compliance §5). A warm Iron & Ember [FrMediaFloor]
 * under a zero-chrome content plane: a floating glass back button + centred title as pushed-screen
 * chrome, then either a structural empty tile or a single frosted [FrGlassTile] of [FrBlockedUserRow]s
 * separated by soft hairlines. ALL [BlockedUsersViewModel] wiring (state, the Unblock + dismiss
 * intents, the error banner, the success toast) is preserved verbatim; only the visual layer is
 * structural. The list is driven entirely by VM state
 * (`BlockedAccountsPort.observeBlocked` ⨯ `AccountReadPort.observeMany`). Reached from Profile.
 */
@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    vm: BlockedUsersViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — atmospheric warm Iron & Ember floor (no photo on this safety screen).
        FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft, scrim = FrScrimStyle.Even)

        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { FrProgressIndicator() }

            state.blockedIds.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().frSafeHorizontalPadding().padding(horizontal = Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                BlockedEmptyTile()
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().frSafeHorizontalPadding().frContentWidth(Breakpoints.contentMax),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = 104.dp,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                state.error?.let { err ->
                    item { DangerBanner(text = resolve(err.toStringKey())) }
                }
                item {
                    FrGlassTile {
                        state.blockedIds.forEachIndexed { index, id ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = StructuralColors.dividerSoft,
                                    modifier = Modifier.padding(vertical = Spacing.xxs),
                                )
                            }
                            FrBlockedUserRow(
                                account = state.identities[id],
                                unblocking = id in state.unblockingIds,
                                onUnblock = { vm.onIntent(BlockedUsersIntent.Unblock(id)) },
                            )
                        }
                    }
                }
            }
        }

        // Floating chrome — back (left) + centred safety label.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .frSafeHorizontalPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Back,
                onClick = onBack,
                contentDescription = resolve(ModerationStringKey.BlockedBackCta),
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                FrEyebrow(text = resolve(ModerationStringKey.BlockedSectionLabel).uppercase())
                FrText(
                    text = resolve(ModerationStringKey.BlockedTitle),
                    style = StructuralType.titleMd.copy(textAlign = TextAlign.Center),
                    color = StructuralColors.foreground,
                )
            }
            // Spacer matching the back button's footprint so the label stays centered.
            Spacer(Modifier.size(44.dp))
        }

        if (state.unblockSuccess) {
            StructuralToast(
                message = resolve(ModerationStringKey.UnblockSuccess),
                onDismiss = { vm.onIntent(BlockedUsersIntent.DismissUnblockSuccess) },
            )
        }
    }
}

/** Structural empty state — a deep glass tile with a faint icon + headline + subtext. */
@Composable
private fun BlockedEmptyTile() {
    FrGlassTile(
        depth = FrTileDepth.Deep,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.xxl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrIcon(
                image = FrIcons.Person,
                contentDescription = null,
                tint = StructuralColors.foreground.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp),
            )
            FrText(
                text = resolve(ModerationStringKey.BlockedEmptyHeadline),
                style = StructuralType.titleLg.copy(textAlign = TextAlign.Center),
                color = StructuralColors.foreground,
            )
            FrText(
                text = resolve(ModerationStringKey.BlockedEmptySubtext),
                style = StructuralType.body.copy(textAlign = TextAlign.Center),
                color = StructuralColors.foreground.copy(alpha = 0.7f),
            )
        }
    }
}

/** Crimson structural banner for the trailing block-list error. */
@Composable
private fun DangerBanner(text: String) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(semantic.danger)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FrIcon(image = FrIcons.Warning, contentDescription = null, tint = semantic.onDanger, modifier = Modifier.size(Sizes.iconMd))
        FrText(text = text, color = semantic.onDanger, style = StructuralType.body)
    }
}

/**
 * Auto-dismissing bottom glass toast confirming a successful unblock (UGC compliance §5 success
 * feedback). Mirrors the `StructuralToast` in `CrewSettingsScreen` — auto-hides after ~2.5s.
 */
@Composable
private fun StructuralToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(2500)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().frSafeHorizontalPadding().padding(Spacing.lg),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FrGlassTile(depth = FrTileDepth.Near) {
            FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
        }
    }
}
