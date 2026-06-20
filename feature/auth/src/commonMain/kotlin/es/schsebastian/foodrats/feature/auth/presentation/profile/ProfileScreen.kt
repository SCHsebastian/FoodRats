package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrSwitch
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrAvatarPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsDivider
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsPicker
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRow
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsRowTone
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSection
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsSectionTone
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.designsystem.theme.FrAccent
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.domain.models.MimeType
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.datetime.LocalTime
import kotlinx.io.readByteArray
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit = {},
    // The embedded EULA / Community Guidelines (Route.Eula / Route.CommunityGuidelines) live in
    // `shared`; navigation is threaded in as callbacks so :feature:auth stays free of :shared's Route.
    onOpenEula: () -> Unit = {},
    onOpenGuidelines: () -> Unit = {},
    // Blocked-users list (Route.BlockedUsers, UGC compliance §5) — threaded in so :feature:auth
    // stays free of :shared's Route, like the legal-doc callbacks.
    onOpenBlockedUsers: () -> Unit = {},
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
            modifier = Modifier.fillMaxSize().frContentWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // Sections rise in on a small top-down stagger (one step ≈ 60ms) so the settings list
            // assembles itself rather than snapping in as one block.
            item {
                GeneralSection(
                    state, vm,
                    onPickAvatar = {
                        picker.launchGallery(
                            allowMultiple = false,
                            mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG),
                        )
                    },
                    modifier = Modifier.frRiseIn(),
                )
            }
            item { PreferencesSection(state, vm, modifier = Modifier.frRiseIn(delayMillis = 60)) }
            item { AchievementsSection(onOpenAchievements, modifier = Modifier.frRiseIn(delayMillis = 120)) }
            item { SafetySection(onOpenBlockedUsers, modifier = Modifier.frRiseIn(delayMillis = 180)) }
            item {
                LegalSection(
                    onOpenEula = onOpenEula,
                    onOpenGuidelines = onOpenGuidelines,
                    modifier = Modifier.frRiseIn(delayMillis = 240),
                )
            }
            item { DataExportSection(state, vm, modifier = Modifier.frRiseIn(delayMillis = 300)) }
            item { DangerZoneSection(state, vm, modifier = Modifier.frRiseIn(delayMillis = 360)) }
        }
    }

    if (state.removeAvatarConfirmOpen) {
        FrConfirmDialog(
            title = resolve(AuthStringKey.ProfileRemoveAvatarConfirmTitle),
            message = resolve(AuthStringKey.ProfileRemoveAvatarConfirmBody),
            confirmLabel = resolve(AuthStringKey.ProfileRemoveAvatarConfirmCta),
            dismissLabel = resolve(AuthStringKey.ProfileRemoveAvatarCancel),
            onConfirm = { vm.onIntent(ProfileIntent.RemoveAvatarConfirmed) },
            onDismiss = { vm.onIntent(ProfileIntent.RemoveAvatarDismissed) },
            destructive = true,
        )
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

    if (state.reminderPickerOpen) {
        val selectedHour = state.reminderEditingIndex
            ?.let { state.reminderTimes.getOrNull(it)?.hour?.toString() }
            .orEmpty()
        FrSettingsPicker(
            title = resolve(AuthStringKey.ProfileRemindersPickerTitle),
            options = reminderHourOptions(),
            selectedId = selectedHour,
            onDismiss = { vm.onIntent(ProfileIntent.ReminderPickerDismiss) },
            onSelect = { id -> vm.onIntent(ProfileIntent.ReminderHourSelected(id.toInt())) },
        )
    }

    if (state.accentPickerOpen) {
        FrSettingsPicker(
            title = resolve(AuthStringKey.ProfileAccentPickerTitle),
            options = accentOptions(),
            selectedId = state.accentPalette.name,
            onDismiss = { vm.onIntent(ProfileIntent.AccentPickerDismiss) },
            onSelect = { id ->
                vm.onIntent(ProfileIntent.AccentSelected(AccentPalette.valueOf(id)))
            },
        )
    }
}

/** 24-hour `HH:mm` label — pure/locale-neutral, used for both the row and the picker options. */
private fun formatReminderTime(time: LocalTime): String {
    val hh = time.hour.toString().padStart(2, '0')
    val mm = time.minute.toString().padStart(2, '0')
    return "$hh:$mm"
}

/** Picker options: every whole hour 00:00..23:00, id = the hour, label = `HH:00`. */
private fun reminderHourOptions(): List<Pair<String, String>> =
    (0..23).map { hour -> hour.toString() to formatReminderTime(LocalTime(hour = hour, minute = 0)) }

@Composable
private fun GeneralSection(
    state: ProfileState,
    vm: ProfileViewModel,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    // The identity block (avatar + display-name) hydrates from the account stream, which starts
    // null. Until it lands — and only while no identity error is surfaced — show a tasteful
    // skeleton in its place. An identity error means there IS something to act on, so we render
    // the real controls (which carry the banner) rather than hiding it behind a shimmer.
    val identityLoading = account == null &&
        state.uploadAvatarError == null &&
        state.saveDisplayNameError == null
    FrSettingsSection(title = resolve(AuthStringKey.ProfileIdentitySection), modifier = modifier) {
        if (identityLoading) {
            IdentitySkeleton()
        } else {
            // Prominent identity header — the display name as a headline with the email beneath it,
            // so "who am I" reads at a glance above the editable avatar/name controls.
            account?.let { acc ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FrText(
                        text = acc.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    acc.email?.let { email ->
                        FrText(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FrSettingsDivider()
            }
            val initials = account?.displayName?.take(2)?.uppercase().orEmpty().ifBlank { "?" }
            FrAvatarPicker(
                initials = initials,
                avatarUrl = account?.avatarUrl,
                onPickClick = onPickAvatar,
                busy = state.isUploadingAvatar || state.isRemovingAvatar,
                changeLabel = resolve(AuthStringKey.ProfileChangeAvatarCta),
                uploadingLabel = if (state.isRemovingAvatar)
                    resolve(AuthStringKey.ProfileAvatarRemoving)
                else
                    resolve(AuthStringKey.ProfileAvatarUploading),
                onRemoveClick = { vm.onIntent(ProfileIntent.RemoveAvatarRequested) },
                removeLabel = resolve(AuthStringKey.ProfileRemoveAvatarCta),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
            )
            state.uploadAvatarError?.let {
                FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
                Spacer(Modifier.height(Spacing.sm))
            }
            state.removeAvatarError?.let {
                FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
                Spacer(Modifier.height(Spacing.sm))
            }

            FrSettingsDivider()

            // Display-name editor — laid out as a row containing the label, field and save button.
            // Keeps the existing intent flow.
            Column(
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
                        && state.editingDisplayName != account?.displayName
                        && !state.isSavingDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.saveDisplayNameError?.let {
                    FrErrorBanner(text = resolve(it))
                }
            }

            FrSettingsDivider()

            // Bio — personal tagline shown under the display name. Blank clears the bio.
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FrText(
                    text = resolve(AuthStringKey.ProfileBioLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FrTextField(
                    value = state.editingBio,
                    onValueChange = { vm.onIntent(ProfileIntent.BioChanged(it)) },
                    label = resolve(AuthStringKey.ProfileBioPlaceholder),
                    enabled = !state.isSavingBio,
                    modifier = Modifier.fillMaxWidth(),
                )
                FrButton(
                    label = resolve(AuthStringKey.ProfileBioSave),
                    onClick = { vm.onIntent(ProfileIntent.SaveBio) },
                    enabled = state.editingBio != (account?.bio?.value ?: "")
                        && !state.isSavingBio,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.saveBioError?.let {
                    FrErrorBanner(text = resolve(it))
                }
            }
        }
    }
}

/**
 * Decorative shimmer placeholder for the identity block (avatar circle + display-name bar)
 * shown while [ProfileState.account] is still null and no identity error is surfaced. Mirrors
 * the silhouette of [FrAvatarPicker] + the display-name editor so the swap-in is seamless.
 */
@Composable
private fun IdentitySkeleton() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        FrShimmerBox(
            modifier = Modifier.size(Sizes.avatarLg),
            shape = CircleShape,
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth(fraction = 0.6f).height(Sizes.iconMd),
            shape = RoundedCornerShape(Radius.sm),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(Sizes.touchTarget),
            shape = RoundedCornerShape(Radius.sm),
        )
    }
}

@Composable
private fun PreferencesSection(state: ProfileState, vm: ProfileViewModel, modifier: Modifier = Modifier) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfilePreferencesSection), modifier = modifier) {
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
                    contentDescription = resolve(AuthStringKey.ProfileNotificationsRow),
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

        // Meal reminders — up to 3 daily nudges at user-chosen hours. Tap a time to change it,
        // the trash to remove it; "Add reminder" appears until the max is reached.
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileRemindersRow),
            subtitle = resolve(AuthStringKey.ProfileRemindersSubtitle),
            icon = FrIcons.Notifications,
        )
        if (state.reminderTimes.isEmpty()) {
            FrText(
                text = resolve(AuthStringKey.ProfileRemindersEmpty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        } else {
            state.reminderTimes.forEachIndexed { index, time ->
                FrSettingsRow(
                    title = formatReminderTime(time),
                    onClick = { vm.onIntent(ProfileIntent.ReminderEditOpen(index)) },
                    trailing = {
                        FrIconButton(
                            icon = FrIcons.Delete,
                            onClick = { vm.onIntent(ProfileIntent.ReminderRemove(index)) },
                            contentDescription = resolve(AuthStringKey.ProfileRemindersRemoveCta),
                        )
                    },
                )
            }
        }
        if (state.reminderTimes.size < MealReminderSchedulePort.MAX_REMINDERS) {
            FrButton(
                label = resolve(AuthStringKey.ProfileRemindersAddCta),
                onClick = { vm.onIntent(ProfileIntent.ReminderAddOpen) },
                variant = FrButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
        state.reminderError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }

        FrSettingsDivider()

        // AI suggestions — on-device plate-photo analysis for ingredient suggestions. The switch
        // mirrors AiPreferencePort.enabled; disabling it prevents any plate photos from being
        // sent to the classifier (all processing stays on-device when enabled).
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileAiRow),
            subtitle = if (state.aiEnabled)
                resolve(AuthStringKey.ProfileAiSubtitleOn)
            else
                resolve(AuthStringKey.ProfileAiSubtitleOff),
            icon = FrIcons.Stats,
            trailing = {
                FrSwitch(
                    checked = state.aiEnabled,
                    onCheckedChange = { vm.onIntent(ProfileIntent.AiToggled(it)) },
                    contentDescription = resolve(AuthStringKey.ProfileAiRow),
                )
            },
        )
        state.aiError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
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
                    contentDescription = resolve(AuthStringKey.ProfileAnalyticsRow),
                )
            },
        )

        FrSettingsDivider()

        // Accent-colour picker — lets the user personalise the app's primary palette from a
        // curated set of Iron & Ember variants. The swatch dot previews the current accent.
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileAccentRow),
            subtitle = resolveAccentLabel(state.accentPalette),
            icon = FrIcons.Theme,
            trailing = {
                AccentSwatchDot(accent = state.accentPalette.toFrAccent())
            },
            onClick = { vm.onIntent(ProfileIntent.AccentPickerOpen) },
        )
        state.accentError?.let {
            FrErrorBanner(text = resolve(it), modifier = Modifier.padding(horizontal = Spacing.md))
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun AchievementsSection(onOpenAchievements: () -> Unit, modifier: Modifier = Modifier) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfileAchievementsSection), modifier = modifier) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileAchievementsRow),
            subtitle = resolve(AuthStringKey.ProfileAchievementsSubtitle),
            icon = FrIcons.Trophy,
            onClick = onOpenAchievements,
        )
    }
}

@Composable
private fun SafetySection(onOpenBlockedUsers: () -> Unit, modifier: Modifier = Modifier) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfileSafetySection), modifier = modifier) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileBlockedUsersRow),
            subtitle = resolve(AuthStringKey.ProfileBlockedUsersSubtitle),
            icon = FrIcons.Block,
            onClick = onOpenBlockedUsers,
        )
    }
}

@Composable
private fun LegalSection(
    onOpenEula: () -> Unit,
    onOpenGuidelines: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FrSettingsSection(title = resolve(AuthStringKey.ProfileLegalSection), modifier = modifier) {
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileLegalEulaRow),
            icon = FrIcons.Public,
            onClick = onOpenEula,
        )
        FrSettingsDivider()
        FrSettingsRow(
            title = resolve(AuthStringKey.ProfileLegalGuidelinesRow),
            icon = FrIcons.Public,
            onClick = onOpenGuidelines,
        )
    }
}

@Composable
private fun DataExportSection(state: ProfileState, vm: ProfileViewModel, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    FrSettingsSection(title = resolve(AuthStringKey.ProfileAccountSection), modifier = modifier) {
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
private fun DangerZoneSection(state: ProfileState, vm: ProfileViewModel, modifier: Modifier = Modifier) {
    FrSettingsSection(
        title = resolve(AuthStringKey.ProfileDangerZoneSection),
        subtitle = resolve(AuthStringKey.ProfileDangerZoneSubtitle),
        tone = FrSettingsSectionTone.Danger,
        modifier = modifier,
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

// ── Accent helpers ────────────────────────────────────────────────────────────────────────────────

/**
 * Presentation-layer mapping: [AccentPalette] → [FrAccent].
 * Defined here (in `:feature:auth`'s presentation layer) because [ProfileScreen] needs it
 * to render the swatch, and `:core:designsystem` must stay domain-free.
 */
private fun AccentPalette.toFrAccent(): FrAccent = when (this) {
    AccentPalette.Ember -> FrAccent.Ember
    AccentPalette.Moss  -> FrAccent.Moss
    AccentPalette.Rust  -> FrAccent.Rust
    AccentPalette.Steel -> FrAccent.Steel
    AccentPalette.Berry -> FrAccent.Berry
}

@Composable
private fun resolveAccentLabel(palette: AccentPalette): String = when (palette) {
    AccentPalette.Ember -> resolve(AuthStringKey.ProfileAccentOptionEmber)
    AccentPalette.Moss  -> resolve(AuthStringKey.ProfileAccentOptionMoss)
    AccentPalette.Rust  -> resolve(AuthStringKey.ProfileAccentOptionRust)
    AccentPalette.Steel -> resolve(AuthStringKey.ProfileAccentOptionSteel)
    AccentPalette.Berry -> resolve(AuthStringKey.ProfileAccentOptionBerry)
}

@Composable
private fun accentOptions(): List<Pair<String, String>> = listOf(
    AccentPalette.Ember.name to resolve(AuthStringKey.ProfileAccentOptionEmber),
    AccentPalette.Moss.name  to resolve(AuthStringKey.ProfileAccentOptionMoss),
    AccentPalette.Rust.name  to resolve(AuthStringKey.ProfileAccentOptionRust),
    AccentPalette.Steel.name to resolve(AuthStringKey.ProfileAccentOptionSteel),
    AccentPalette.Berry.name to resolve(AuthStringKey.ProfileAccentOptionBerry),
)

/**
 * Small filled circle previewing the accent's primary swatch colour. Lives in
 * `:feature:auth`'s presentation, not in `:core:designsystem`, because it takes a
 * domain-adjacent [FrAccent] and lives inside a feature screen, not a design-system atom.
 */
@Composable
private fun AccentSwatchDot(accent: FrAccent, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.iconMd)
            .background(color = accent.swatch, shape = CircleShape),
    )
}
