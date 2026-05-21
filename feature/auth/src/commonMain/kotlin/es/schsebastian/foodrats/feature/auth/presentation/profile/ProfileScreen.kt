package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.domain.models.MimeType
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.io.readByteArray
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    vm: ProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberImagePickerKMP()

    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = r.first
                if (photo != null) {
                    val bytes = photo.asSource().readByteArray().resizeAvatarForUpload()
                    vm.onIntent(ProfileIntent.AvatarPicked(bytes))
                }
                picker.reset()
            }
            is ImagePickerResult.Error -> {
                println("[ProfileScreen] avatar picker error: ${r.exception.message}")
                picker.reset()
            }
            is ImagePickerResult.Dismissed,
            is ImagePickerResult.Loading,
            is ImagePickerResult.Idle -> Unit
        }
    }

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(resolve(AuthStringKey.ProfileTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = FrIcons.Back,
                            contentDescription = null,
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
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Identity section header
            item {
                FrText(
                    text = resolve(AuthStringKey.ProfileIdentitySection),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            // Avatar picker + initials
            item {
                val initials = state.account?.displayName?.take(2)?.uppercase().orEmpty().ifBlank { "?" }
                FrAvatarPicker(
                    initials = initials,
                    avatarUrl = state.account?.avatarUrl,
                    onPickClick = {
                        picker.launchGallery(
                            allowMultiple = false,
                            mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG),
                        )
                    },
                    busy = state.isUploadingAvatar,
                    changeLabel = resolve(AuthStringKey.ProfileChangeAvatarCta),
                    uploadingLabel = resolve(AuthStringKey.ProfileAvatarUploading),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.uploadAvatarError?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    FrErrorBanner(text = resolve(it))
                }
            }

            // Display name field + save
            item {
                FrTextField(
                    value = state.editingDisplayName,
                    onValueChange = { vm.onIntent(ProfileIntent.DisplayNameChanged(it)) },
                    label = resolve(AuthStringKey.ProfileDisplayNameLabel),
                    enabled = !state.isSavingDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                FrButton(
                    label = resolve(AuthStringKey.ProfileSave),
                    onClick = { vm.onIntent(ProfileIntent.SaveDisplayName) },
                    enabled = state.editingDisplayName.isNotBlank()
                        && state.editingDisplayName != state.account?.displayName
                        && !state.isSavingDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.saveDisplayNameError?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    FrErrorBanner(text = resolve(it))
                }
            }

            // Signed-in-as (email, read-only)
            state.account?.email?.let { email ->
                item {
                    FrText(
                        text = resolve(AuthStringKey.ProfileSignedInAsLabel),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FrText(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Account section + Sign out
            item {
                Spacer(Modifier.height(Spacing.lg))
                FrText(
                    text = resolve(AuthStringKey.ProfileAccountSection),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.md))
                FrButton(
                    label = resolve(AuthStringKey.ProfileSignOutCta),
                    onClick = { vm.onIntent(ProfileIntent.SignOut) },
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isSigningOut,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.signOutError?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    FrErrorBanner(text = resolve(it))
                }
            }
        }
    }
}
