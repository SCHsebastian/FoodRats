package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.components.FrCrewMemberRow
import es.schsebastian.foodrats.feature.crew.presentation.components.MyAvatarPicker
import es.schsebastian.foodrats.feature.crew.presentation.components.resizeAvatarForUpload
import es.schsebastian.foodrats.feature.crew.presentation.settings.components.DeleteCrewConfirmDialog
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.domain.models.MimeType
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.io.readByteArray
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CrewSettingsScreen(
    crewId: String,
    onLeft: () -> Unit,
    onSwitch: () -> Unit = {},
    onDeleted: () -> Unit = {},
    onSignedOut: () -> Unit = {},
    vm: CrewSettingsViewModel = koinViewModel(parameters = { parametersOf((CrewId.of(crewId) as es.schsebastian.foodrats.core.domain.result.Result.Ok).value) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val picker = rememberImagePickerKMP()

    LaunchedEffect(picker.result) {
        when (val result = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = result.first ?: return@LaunchedEffect
                val bytes = photo.asSource().readByteArray().resizeAvatarForUpload()
                vm.onIntent(CrewSettingsIntent.UpdateMyAvatar(bytes))
                picker.reset()
            }
            is ImagePickerResult.Error -> {
                println("[CrewSettingsScreen] avatar picker error: ${result.exception.message}")
                picker.reset()
            }
            is ImagePickerResult.Dismissed,
            is ImagePickerResult.Loading,
            is ImagePickerResult.Idle -> Unit
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            FrLog.d(FrLog.Tags.SignOut) { "screen: effect=$eff" }
            when (eff) {
                CrewSettingsEffect.NavigateToCrewPicker -> onSwitch()
                CrewSettingsEffect.Left -> onLeft()
                CrewSettingsEffect.Deleted -> onDeleted()
                CrewSettingsEffect.SignedOut -> {
                    FrLog.d(FrLog.Tags.SignOut) { "screen: invoking onSignedOut() (RootNav handles nav)" }
                    onSignedOut()
                }
            }
        }
    }

    FrScreenScaffold {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.lg),
        ) {
            // Title
            item {
                FrText(
                    text = resolve(CrewStringKey.SettingsTitle),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(Spacing.lg))
            }

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

                // Invite code + copy
                item {
                    FrText(text = resolve(CrewStringKey.SettingsInviteCode))
                    Spacer(Modifier.height(Spacing.xs))
                    FrText(
                        text = crew.code.value,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsShare),
                        onClick = { clipboardManager.setText(AnnotatedString(crew.code.value)) },
                        variant = FrButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    FrCrewMemberRow(displayName = m.displayName, avatarUrl = m.avatarUrl)
                }

                item { Spacer(Modifier.height(Spacing.lg)) }

                // Section: Your profile
                item {
                    FrText(
                        text = resolve(CrewStringKey.SettingsMyProfileSection),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    MyAvatarPicker(
                        initials = state.editingMyDisplayName.ifBlank { "?" }.take(2),
                        avatarUrl = state.myAvatarUrl,
                        onPickClick = {
                            picker.launchGallery(
                                allowMultiple = false,
                                mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG),
                            )
                        },
                        busy = state.isUpdatingAvatar,
                        changeLabel = resolve(CrewStringKey.SettingsChangeAvatarCta),
                        uploadingLabel = resolve(CrewStringKey.SettingsAvatarUploading),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    FrTextField(
                        value = state.editingMyDisplayName,
                        onValueChange = { vm.onIntent(CrewSettingsIntent.MyDisplayNameChanged(it)) },
                        label = resolve(CrewStringKey.SettingsMyDisplayNameLabel),
                        enabled = !state.isSavingMyDisplayName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsSave),
                        onClick = { vm.onIntent(CrewSettingsIntent.SaveMyDisplayName) },
                        enabled = state.editingMyDisplayName.isNotBlank() && !state.isSavingMyDisplayName,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.lg))
                }

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
                    Spacer(Modifier.height(Spacing.sm))
                    FrButton(
                        label = resolve(CrewStringKey.SettingsSignOutCta),
                        onClick = { vm.onIntent(CrewSettingsIntent.SignOut) },
                        variant = FrButtonVariant.Secondary,
                        enabled = !state.isSigningOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.signOutError != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        FrErrorBanner(text = resolve(CrewStringKey.SettingsSignOutFailed))
                    }
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
}
