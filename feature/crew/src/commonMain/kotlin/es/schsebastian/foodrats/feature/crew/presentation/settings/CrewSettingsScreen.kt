package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.domain.share.ShareController
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrSwitch
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.domain.model.Crew
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.components.FrCrewMemberRow
import es.schsebastian.foodrats.feature.crew.presentation.settings.components.DeleteCrewConfirmDialog
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewSettingsScreen(
    crewId: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onSwitch: () -> Unit = {},
    onDeleted: () -> Unit = {},
    /**
     * Builds the canonical shareable invite URL from a crew code. Supplied by the NavGraph (the URL
     * contract owner in `:shared`) so this feature stays free of a dependency on `:shared`. Defaults
     * to the bare code so previews/tests that don't wire it still render.
     */
    inviteUrlFor: (String) -> String = { it },
    vm: CrewSettingsViewModel = koinViewModel(parameters = { parametersOf((CrewId.of(crewId) as es.schsebastian.foodrats.core.domain.result.Result.Ok).value) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val share = koinInject<ShareController>()
    var memberPendingRemoval by remember { mutableStateOf<AccountId?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMemberFallback = resolve(CrewStringKey.MemberDeleted)
    // Name to show in the success snackbar; set by the MemberRemoved effect, cleared once shown.
    var memberRemovedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CrewSettingsEffect.NavigateToCrewPicker -> onSwitch()
                CrewSettingsEffect.Left -> onLeft()
                CrewSettingsEffect.Deleted -> onDeleted()
                is CrewSettingsEffect.MemberRemoved ->
                    memberRemovedName = eff.displayName ?: deletedMemberFallback
            }
        }
    }

    memberRemovedName?.let { name ->
        val message = resolve(CrewStringKey.SettingsMemberRemoved, name)
        LaunchedEffect(name) {
            snackbarHostState.showSnackbar(message)
            memberRemovedName = null
        }
    }

    FrScreenScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SectionEyebrow(resolve(CrewStringKey.SettingsTitle))
                        FrText(
                            text = state.crew?.name.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(CrewStringKey.SettingsBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) {
        val crew = state.crew
        val error = state.error
        when {
            crew == null && error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            ) { FrErrorBanner(text = resolve(error.toStringKey())) }

            crew == null -> CrewSettingsSkeleton()

            else -> LazyColumn(
                modifier = Modifier.fillMaxHeight().frContentWidth().padding(horizontal = Spacing.lg),
                contentPadding = PaddingValues(vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                // Signature: the crew hero "develops in" with a focal reveal; every section
                // below it rises into place in sequence (small stagger so the column lands fast).
                item {
                    val inviteUrl = inviteUrlFor(crew.code.value)
                    val shareMessage = resolve(CrewStringKey.InviteShareMessage, crew.name, inviteUrl)
                    CrewHeroCard(
                        crew = crew,
                        onCopy = { clipboardManager.setText(AnnotatedString(crew.code.value)) },
                        onShareLink = {
                            vm.onIntent(CrewSettingsIntent.ShareLinkTapped)
                            share.shareText(shareMessage)
                        },
                        onShowQr = { showQr = true },
                        modifier = Modifier.frRevealScale(),
                    )
                }

                if (state.isOwner) {
                    item {
                        FrCard(modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 40)) {
                            FrTextField(
                                value = state.editingCrewName,
                                onValueChange = { vm.onIntent(CrewSettingsIntent.CrewNameChanged(it)) },
                                label = resolve(CrewStringKey.SettingsCrewNameLabel),
                                enabled = !state.isSavingCrewName,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FrButton(
                                label = resolve(CrewStringKey.SettingsSave),
                                onClick = { vm.onIntent(CrewSettingsIntent.SaveCrewName) },
                                enabled = state.editingCrewName.isNotBlank() &&
                                    state.editingCrewName != crew.name &&
                                    !state.isSavingCrewName,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                            )
                        }
                    }

                    item {
                        BlindVotingCard(
                            enabled = crew.blindVoting,
                            saving = state.isSavingBlindVoting,
                            onToggle = { vm.onIntent(CrewSettingsIntent.ToggleBlindVoting(it)) },
                            modifier = Modifier.frRiseIn(delayMillis = 80),
                        )
                    }
                }

                item {
                    MembersCard(
                        crew = crew,
                        isOwner = state.isOwner,
                        myAccountId = state.myAccountId,
                        identities = state.identities,
                        removingMemberIds = state.removingMemberIds,
                        onRemove = { memberPendingRemoval = it },
                        modifier = Modifier.frRiseIn(delayMillis = 120),
                    )
                }

                item {
                    FrButton(
                        label = resolve(CrewStringKey.SettingsSwitchCrew),
                        onClick = { vm.onIntent(CrewSettingsIntent.SwitchCrew) },
                        variant = FrButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 160),
                    )
                }

                item {
                    DangerZoneCard(
                        isOwner = state.isOwner,
                        leaveEnabled = !state.isLeaving,
                        deleteEnabled = !state.isDeleting,
                        onLeave = { vm.onIntent(CrewSettingsIntent.Leave) },
                        onDelete = { vm.onIntent(CrewSettingsIntent.RequestDelete) },
                        modifier = Modifier.frRiseIn(delayMillis = 200),
                    )
                }

                state.error?.let { err ->
                    item { FrErrorBanner(text = resolve(err.toStringKey())) }
                }
            }
        }
    }

    if (showQr) {
        state.crew?.let { c ->
            InviteQrDialog(
                crewName = c.name,
                inviteUrl = inviteUrlFor(c.code.value),
                onDismiss = { showQr = false },
            )
        }
    }

    if (state.showDeleteConfirm) {
        DeleteCrewConfirmDialog(
            crewName = state.crew?.name.orEmpty(),
            onConfirm = { vm.onIntent(CrewSettingsIntent.ConfirmDelete) },
            onDismiss = { vm.onIntent(CrewSettingsIntent.CancelDelete) },
        )
    }

    memberPendingRemoval?.let { pendingId ->
        // displayName can be empty (non-null) for email-signup members, so the old
        // `?:` (null-only) left the dialog showing a blank name. Fall back to the
        // @handle, and only to "deleted user" when there's truly no identity.
        val identity = state.identities[pendingId]
        val memberName = identity?.displayName?.takeIf { it.isNotBlank() }
            ?: identity?.handle?.takeIf { it.isNotBlank() }
            ?: resolve(CrewStringKey.MemberDeleted)
        FrConfirmDialog(
            title = resolve(CrewStringKey.SettingsRemoveMemberConfirmTitle, memberName),
            message = resolve(CrewStringKey.SettingsRemoveMemberConfirmBody, memberName),
            confirmLabel = resolve(CrewStringKey.SettingsRemoveMemberCta),
            dismissLabel = resolve(CrewStringKey.SettingsCancel),
            onConfirm = {
                vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(pendingId))
                memberPendingRemoval = null
            },
            onDismiss = { memberPendingRemoval = null },
            destructive = true,
        )
    }
}

/**
 * Decorative initial-load placeholder mimicking the loaded silhouette: one hero-card block atop a
 * short stack of member rows (avatar circle + flexible name bar). Shown while the crew snapshot is
 * still resolving. Capped to the same content width as the loaded state so width doesn't jump.
 */
@Composable
private fun CrewSettingsSkeleton() {
    Column(
        modifier = Modifier.fillMaxHeight().frContentWidth().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    FrShimmerBox(
                        modifier = Modifier.size(Sizes.avatarMd),
                        shape = CircleShape,
                    )
                    FrShimmerBox(
                        modifier = Modifier.weight(1f).height(Spacing.md),
                        shape = RoundedCornerShape(Radius.sm),
                    )
                }
            }
        }
    }
}

/**
 * Centered crew identity: name, member count, the tap-to-copy invite-code chip, and the invite-link
 * actions (Share link via the system share sheet + Show QR). The full shareable deep link / QR is
 * built upstream from the code (see [CrewSettingsScreen.inviteUrlFor]).
 */
@Composable
private fun CrewHeroCard(
    crew: Crew,
    onCopy: () -> Unit,
    onShareLink: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrText(text = crew.name, style = MaterialTheme.typography.headlineSmall)
            FrText(
                text = resolve(CrewStringKey.SettingsMembersCount, crew.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Tap-to-copy invite code chip — mono + letter-spaced.
            Surface(
                onClick = onCopy,
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = Spacing.md, horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    FrText(
                        text = crew.code.value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        ),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrButton(
                    label = resolve(CrewStringKey.SettingsShareLink),
                    onClick = onShareLink,
                    modifier = Modifier.weight(1f),
                )
                FrButton(
                    label = resolve(CrewStringKey.SettingsShowQr),
                    onClick = onShowQr,
                    variant = FrButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Modal showing the crew's invite link as a scannable QR code plus a caption. */
@Composable
private fun InviteQrDialog(
    crewName: String,
    inviteUrl: String,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        FrCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FrText(
                    text = resolve(CrewStringKey.SettingsQrCaption, crewName),
                    style = MaterialTheme.typography.titleMedium,
                )
                es.schsebastian.foodrats.core.designsystem.atoms.FrQrCode(content = inviteUrl)
                FrButton(
                    label = resolve(CrewStringKey.SettingsQrClose),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Owner-only blind-voting policy toggle: label + explanation on the left, [FrSwitch] on the right. */
@Composable
private fun BlindVotingCard(
    enabled: Boolean,
    saving: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionEyebrow(
            text = resolve(CrewStringKey.SettingsBlindVotingSection),
            color = MaterialTheme.colorScheme.primary,
        )
        FrCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FrText(
                        text = resolve(CrewStringKey.SettingsBlindVotingLabel),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FrText(
                        text = resolve(CrewStringKey.SettingsBlindVotingDescription),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FrSwitch(
                    checked = enabled,
                    onCheckedChange = { onToggle(it) },
                    enabled = !saving,
                    contentDescription = resolve(CrewStringKey.SettingsBlindVotingLabel),
                )
            }
        }
    }
}

/** Members list rendered as token-divided rows inside a single card. */
@Composable
private fun MembersCard(
    crew: Crew,
    isOwner: Boolean,
    myAccountId: AccountId?,
    identities: Map<AccountId, es.schsebastian.foodrats.core.domain.account.Account?>,
    removingMemberIds: Set<AccountId>,
    onRemove: (AccountId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionEyebrow(
                text = resolve(CrewStringKey.SettingsMembersSection),
                color = MaterialTheme.colorScheme.primary,
            )
            if (isOwner) {
                FrText(
                    text = resolve(CrewStringKey.SettingsOwnerBadge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FrCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            crew.members.forEachIndexed { index, m ->
                val isMemberOwner = m.accountId == crew.ownerId
                val canRemove = isOwner && m.accountId != myAccountId
                val isRemoving = m.accountId in removingMemberIds
                FrCrewMemberRow(
                    account = identities[m.accountId],
                    subtitle = resolve(
                        if (isMemberOwner) CrewStringKey.SettingsRoleOwner else CrewStringKey.SettingsRoleMember,
                    ),
                    trailing = if (canRemove) {
                        {
                            if (isRemoving) {
                                FrProgressIndicator(
                                    modifier = Modifier.size(Sizes.iconMd),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                FrIconButton(
                                    icon = FrIcons.Close,
                                    onClick = { onRemove(m.accountId) },
                                    contentDescription = resolve(CrewStringKey.SettingsRemoveMemberCta),
                                )
                            }
                        }
                    } else null,
                )
                if (index < crew.members.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Leave / Delete as full-width destructive rows inside one card. */
@Composable
private fun DangerZoneCard(
    isOwner: Boolean,
    leaveEnabled: Boolean,
    deleteEnabled: Boolean,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalFrSemanticColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Danger-tinted eyebrow so the destructive zone reads as set-apart, not just another section.
        SectionEyebrow(
            text = resolve(CrewStringKey.SettingsDangerSection),
            color = semantic.danger,
        )
        FrCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = semantic.danger.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(Radius.lg),
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            DangerActionRow(
                icon = FrIcons.Logout,
                label = resolve(CrewStringKey.SettingsLeaveCta),
                enabled = leaveEnabled,
                onClick = onLeave,
            )
            if (isOwner) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DangerActionRow(
                    icon = FrIcons.Delete,
                    label = resolve(CrewStringKey.SettingsDeleteCta),
                    enabled = deleteEnabled,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun DangerActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrIcon(image = icon, tint = semantic.danger, modifier = Modifier.size(Sizes.iconMd))
        FrText(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = semantic.danger,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    FrText(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier.semantics { heading() },   // WCAG 2.4.10 heading navigation
    )
}
