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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrSwitch
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsDivider
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRow
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRowTone
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSection
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSectionTone
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
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
    onOpenAchievements: () -> Unit = {},
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
                FrLog.w(FrLog.Tags.Auth) { "[ProfileScreen] avatar picker error: ${r.exception.message}" }
                picker.reset()
            }
            is ImagePickerResult.Dismissed,
            is ImagePickerResult.Loading,
            is ImagePickerResult.Idle -> Unit
        }
    }

    if (state.deleteScreenOpen) {
        // Locale-correct confirmation phrase: resolve the SAME template the screen displays
        // (en "DELETE %1$s" / es "BORRAR %1$s") so a Spanish user types "BORRAR <name>", not the
        // English "DELETE <name>". Trim because the template leaves a trailing space when no
        // account/name is loaded yet (the field is disabled in that case anyway).
        val displayName = state.account?.displayName?.trim().orEmpty()
        val expectedPhrase = resolve(AuthStringKey.DeleteAccountPhraseTemplate, displayName).trim()
        DeleteAccountScreen(
            state = state,
            onBack = { vm.onIntent(ProfileIntent.CloseDeleteAccount) },
            onConfirmationChanged = { vm.onIntent(ProfileIntent.DeleteConfirmationChanged(it)) },
            onRequestDialog = { vm.onIntent(ProfileIntent.RequestDeleteDialog) },
            onDialogDismiss = { vm.onIntent(ProfileIntent.DeleteDialogDismiss) },
            onDialogConfirm = { phrase -> vm.onIntent(ProfileIntent.DeleteDialogConfirm(phrase)) },
            expectedPhrase = expectedPhrase,
        )
        return
    }

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    FrText(
                        text = resolve(AuthStringKey.ProfileTitle),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(AuthStringKey.ProfileBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { GeneralSection(state, vm, onPickAvatar = {
                picker.launchGallery(
                    allowMultiple = false,
                    mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG),
                )
            }) }
            item { PreferencesSection(state, vm) }
            item { AchievementsSection(onOpenAchievements) }
            item { DataExportSection(state, vm) }
            item { DangerZoneSection(state, vm) }
        }
    }

    if (state.themePickerOpen) {
        FrSettingsPicker(
            title = resolve(AuthStringKey.ProfileThemePickerTitle),
            options = themeOptions(),
            selectedId = state.themeMode.name,
            onDismiss = { vm.onIntent(ProfileIntent.ThemePickerDismiss) },
            onSelect = { id ->
                vm.onIntent(ProfileIntent.ThemeSelected(ThemeMode.valueOf(id)))
            },
        )
    }

    if (state.localePickerOpen) {
        FrSettingsPicker(
            title = resolve(AuthStringKey.ProfileLanguagePickerTitle),
            options = localeOptions(),
            selectedId = state.locale.name,
            onDismiss = { vm.onIntent(ProfileIntent.LocalePickerDismiss) },
            onSelect = { id ->
                vm.onIntent(ProfileIntent.LocaleSelected(AppLocale.valueOf(id)))
            },
        )
    }
}

@Composable
private fun GeneralSection(
    state: ProfileState,
    vm: ProfileViewModel,
    onPickAvatar: () -> Unit,
) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfileIdentitySection)) {
        val initials = state.account?.displayName?.take(2)?.uppercase().orEmpty().ifBlank { "?" }
        FrAvatarPicker(
            initials = initials,
            avatarUrl = state.account?.avatarUrl,
            onPickClick = onPickAvatar,
            busy = state.isUploadingAvatar,
            changeLabel = resolve(AuthStringKey.ProfileChangeAvatarCta),
            uploadingLabel = resolve(AuthStringKey.ProfileAvatarUploading),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
        )
        state.uploadAvatarError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.sm))
        }

        FrSettingsDivider()

        // Display-name editor — laid out as a row containing the label, field and save button.
        // Keeps the existing intent flow.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FrText(
                text = resolve(AuthStringKey.ProfileDisplayNameLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FrTextField(
                value = state.editingDisplayName,
                onValueChange = { vm.onIntent(ProfileIntent.DisplayNameChanged(it)) },
                label = resolve(AuthStringKey.ProfileDisplayNameLabel),
                enabled = !state.isSavingDisplayName,
                modifier = Modifier.fillMaxWidth(),
            )
            FrButton(
                label = resolve(AuthStringKey.ProfileSave),
                onClick = { vm.onIntent(ProfileIntent.SaveDisplayName) },
                enabled = state.editingDisplayName.isNotBlank()
                    && state.editingDisplayName != state.account?.displayName
                    && !state.isSavingDisplayName,
                modifier = Modifier.fillMaxWidth(),
            )
            state.saveDisplayNameError?.let {
                FrErrorBanner(text = resolve(it))
            }
        }

        state.account?.email?.let { email ->
            FrSettingsDivider()
            FrSettingsRow(
                title = resolve(AuthStringKey.ProfileSignedInAsLabel),
                subtitle = email,
                icon = FrIcons.Person,
            )
        }
    }
}

@Composable
private fun PreferencesSection(state: ProfileState, vm: ProfileViewModel) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfilePreferencesSection)) {
        // Theme
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileThemeRow),
            subtitle = resolveThemeLabel(state.themeMode),
            icon = FrIcons.Theme,
            onClick = { vm.onIntent(ProfileIntent.ThemePickerOpen) },
        )
        state.themeError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }

        FrSettingsDivider()

        // Language — iOS deep-links to its own page in the system Settings app,
        // where the per-app language picker is the native UX. Android keeps the
        // in-app radio picker because Compose Resources honors LocalAppLocale.
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileLanguageRow),
            subtitle = resolveLocaleLabel(state.locale),
            icon = FrIcons.Language,
            onClick = {
                if (opensSystemSettingsForLanguage) openAppLanguageSettings()
                else vm.onIntent(ProfileIntent.LocalePickerOpen)
            },
        )
        state.localeError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }

        FrSettingsDivider()

        // Notifications
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileNotificationsRow),
            subtitle = if (state.notificationsEnabled)
                resolve(AuthStringKey.ProfileNotificationsSubtitleOn)
            else
                resolve(AuthStringKey.ProfileNotificationsSubtitleOff),
            icon = FrIcons.Notifications,
            trailing = {
                FrSwitch(
                    checked = state.notificationsEnabled,
                    onCheckedChange = { vm.onIntent(ProfileIntent.NotificationsToggled(it)) },
                )
            },
        )
        state.notificationsError?.let { errorKey ->
            FrErrorBanner(text = resolve(errorKey), modifier = Modifier.padding(horizontal = Spacing.md))
            if (errorKey == AuthStringKey.ProfileNotificationsPermissionDeniedForever) {
                FrButton(
                    label = resolve(AuthStringKey.ProfileNotificationsOpenSystemSettingsCta),
                    onClick = { vm.onIntent(ProfileIntent.OpenNotificationSystemSettings) },
                    variant = FrButtonVariant.Secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        FrSettingsDivider()

        // Analytics consent — withdraw or re-grant at any time (GDPR Art. 7(3)). The switch
        // mirrors ConsentPort.decision; flipping it writes grant()/revoke().
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileAnalyticsRow),
            subtitle = if (state.analyticsConsentGranted)
                resolve(AuthStringKey.ProfileAnalyticsSubtitleOn)
            else
                resolve(AuthStringKey.ProfileAnalyticsSubtitleOff),
            icon = FrIcons.Stats,
            trailing = {
                FrSwitch(
                    checked = state.analyticsConsentGranted,
                    onCheckedChange = { vm.onIntent(ProfileIntent.AnalyticsConsentToggled(it)) },
                )
            },
        )
    }
}

@Composable
private fun AchievementsSection(onOpenAchievements: () -> Unit) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfileAchievementsSection)) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileAchievementsRow),
            subtitle = resolve(AuthStringKey.ProfileAchievementsSubtitle),
            icon = FrIcons.Trophy,
            onClick = onOpenAchievements,
        )
    }
}

@Composable
private fun DataExportSection(state: ProfileState, vm: ProfileViewModel) {
    val uriHandler = LocalUriHandler.current
    FrSettingsSection(title = resolve(AuthStringKey.ProfileAccountSection)) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ExportDataRow),
            subtitle = if (state.isExportingData)
                resolve(AuthStringKey.ExportDataInFlight)
            else
                resolve(AuthStringKey.ExportDataSubtitle),
            icon = FrIcons.Stats,
            enabled = !state.isExportingData,
            onClick = { vm.onIntent(ProfileIntent.ExportMyData) },
        )
        state.exportError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }
        state.exportDownloadUrl?.let { url ->
            FrText(
                text = resolve(AuthStringKey.ExportDataReadySubtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            FrButton(
                label = resolve(AuthStringKey.ExportDataReadyCta),
                onClick = {
                    uriHandler.openUri(url)
                    vm.onIntent(ProfileIntent.DismissExportResult)
                },
                variant = FrButtonVariant.Secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun DangerZoneSection(state: ProfileState, vm: ProfileViewModel) {
    FrSettingsSection(
        title = resolve(AuthStringKey.ProfileDangerZoneSection),
        subtitle = resolve(AuthStringKey.ProfileDangerZoneSubtitle),
        tone = FrSettingsSectionTone.Danger,
    ) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileSignOutCta),
            icon = FrIcons.Logout,
            enabled = !state.isSigningOut,
            onClick = { vm.onIntent(ProfileIntent.SignOut) },
        )
        state.signOutError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }

        FrSettingsDivider()

        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileDeleteAccountRow),
            subtitle = resolve(AuthStringKey.ProfileDeleteAccountSubtitle),
            icon = FrIcons.Delete,
            tone = FrSettingsRowTone.Danger,
            onClick = { vm.onIntent(ProfileIntent.OpenDeleteAccount) },
        )
    }
}

@Composable
private fun resolveThemeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.Light -> resolve(AuthStringKey.ProfileThemeOptionLight)
    ThemeMode.Dark -> resolve(AuthStringKey.ProfileThemeOptionDark)
    ThemeMode.System -> resolve(AuthStringKey.ProfileThemeOptionSystem)
}

@Composable
private fun resolveLocaleLabel(locale: AppLocale): String = when (locale) {
    AppLocale.System -> resolve(AuthStringKey.ProfileLanguageOptionSystem)
    AppLocale.En -> resolve(AuthStringKey.ProfileLanguageOptionEn)
    AppLocale.Es -> resolve(AuthStringKey.ProfileLanguageOptionEs)
}

@Composable
private fun themeOptions(): List<Pair<String, String>> = listOf(
    ThemeMode.System.name to resolve(AuthStringKey.ProfileThemeOptionSystem),
    ThemeMode.Light.name to resolve(AuthStringKey.ProfileThemeOptionLight),
    ThemeMode.Dark.name to resolve(AuthStringKey.ProfileThemeOptionDark),
)

@Composable
private fun localeOptions(): List<Pair<String, String>> = listOf(
    AppLocale.System.name to resolve(AuthStringKey.ProfileLanguageOptionSystem),
    AppLocale.En.name to resolve(AuthStringKey.ProfileLanguageOptionEn),
    AppLocale.Es.name to resolve(AuthStringKey.ProfileLanguageOptionEs),
)
