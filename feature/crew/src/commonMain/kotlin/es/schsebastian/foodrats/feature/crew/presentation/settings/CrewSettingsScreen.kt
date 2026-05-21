package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.data.share.ShareController
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.i18n.resolve
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
    vm: CrewSettingsViewModel = koinViewModel(parameters = { parametersOf((CrewId.of(crewId) as es.schsebastian.foodrats.core.domain.result.Result.Ok).value) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val share = koinInject<ShareController>()
    var memberPendingRemoval by remember { mutableStateOf<AccountId?>(null) }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                CrewSettingsEffect.NavigateToCrewPicker -> onSwitch()
                CrewSettingsEffect.Left -> onLeft()
                CrewSettingsEffect.Deleted -> onDeleted()
            }
        }
    }

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(CrewStringKey.SettingsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = FrIcons.Back,
                            contentDescription = resolve(CrewStringKey.SettingsBackCta),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        ) {
            state.crew?.let { crew ->
                // Section: Crew
                item {
                    FrText(
                        text = resolve(CrewStringKey.SettingsCrewSection),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.md))
                }

                // Crew name field + save
                item {
                    FrTextField(
                        value = state.editingCrewName,
                        onValueChange = { vm.onIntent(CrewSettingsIntent.CrewNameChanged(it)) },
                        label = resolve(CrewStringKey.SettingsCrewNameLabel),
                        enabled = state.isOwner && !state.isSavingCrewName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsSave),
                        onClick = { vm.onIntent(CrewSettingsIntent.SaveCrewName) },
                        enabled = state.isOwner &&
                            state.editingCrewName.isNotBlank() &&
                            state.editingCrewName != crew.name &&
                            !state.isSavingCrewName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                }

                // Invite code + share + copy
                item {
                    FrText(text = resolve(CrewStringKey.SettingsInviteCode))
                    Spacer(Modifier.height(Spacing.xs))
                    FrText(
                        text = crew.code.value,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FrButton(
                            label = resolve(CrewStringKey.SettingsShare),
                            onClick = { share.shareText(crew.code.value) },
                            modifier = Modifier.weight(1f),
                        )
                        FrButton(
                            label = resolve(CrewStringKey.SettingsCopyCta),
                            onClick = { clipboardManager.setText(AnnotatedString(crew.code.value)) },
                            variant = FrButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                }

                // Members section
                item {
                    FrText(
                        text = resolve(CrewStringKey.SettingsMembersSection),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                items(crew.members, key = { it.accountId.value }) { m ->
                    val canRemove = state.isOwner && m.accountId != state.myAccountId
                    FrCrewMemberRow(
                        account = state.identities[m.accountId],
                        trailing = if (canRemove) {
                            {
                                IconButton(onClick = { memberPendingRemoval = m.accountId }) {
                                    Icon(
                                        imageVector = FrIcons.Close,
                                        contentDescription = resolve(CrewStringKey.SettingsRemoveMemberCta),
                                    )
                                }
                            }
                        } else null,
                    )
                }

                item { Spacer(Modifier.height(Spacing.lg)) }

                // Section: Actions
                item {
                    FrText(
                        text = resolve(CrewStringKey.SettingsActionsSection),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsSwitchCrew),
                        onClick = { vm.onIntent(CrewSettingsIntent.SwitchCrew) },
                        variant = FrButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsLeaveCta),
                        onClick = { vm.onIntent(CrewSettingsIntent.Leave) },
                        variant = FrButtonVariant.Secondary,
                        enabled = !state.isLeaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }

                // Section: Danger zone (owner only)
                if (state.isOwner) {
                    item {
                        FrText(
                            text = resolve(CrewStringKey.SettingsDangerSection),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        FrButton(
                            label = resolve(CrewStringKey.SettingsDeleteCta),
                            onClick = { vm.onIntent(CrewSettingsIntent.RequestDelete) },
                            enabled = !state.isDeleting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.lg))
                    }
                }
            }

            // Error banner
            state.error?.let { err ->
                item {
                    FrErrorBanner(
                        text = resolve(err.toStringKey()),
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
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
        val memberName = state.identities[pendingId]?.displayName
            ?: resolve(CrewStringKey.MemberDeleted)
        AlertDialog(
            onDismissRequest = { memberPendingRemoval = null },
            title = { Text(resolve(CrewStringKey.SettingsRemoveMemberConfirmTitle, memberName)) },
            text = { Text(resolve(CrewStringKey.SettingsRemoveMemberConfirmBody, memberName)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.onIntent(CrewSettingsIntent.RemoveMemberConfirmed(pendingId))
                    memberPendingRemoval = null
                }) { Text(resolve(CrewStringKey.SettingsRemoveMemberCta)) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingRemoval = null }) {
                    Text(resolve(CrewStringKey.SettingsCancel))
                }
            },
        )
    }
}
