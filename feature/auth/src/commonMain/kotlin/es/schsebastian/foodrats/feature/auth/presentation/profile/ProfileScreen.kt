package es.schsebastian.foodrats.feature.auth.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.layout.frSafeHorizontalPadding
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsPicker
import es.schsebastian.foodrats.core.designsystem.structural.FrAvatarRing
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassAvatar
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassToggle
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralChip
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralRow
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.designsystem.theme.FrAccent
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.domain.preferences.AccentPalette
import es.schsebastian.foodrats.core.domain.preferences.AppLocale
import es.schsebastian.foodrats.core.domain.preferences.MealReminderSchedulePort
import es.schsebastian.foodrats.core.domain.preferences.ThemeMode
import es.schsebastian.foodrats.core.domain.telemetry.FrLog
import es.schsebastian.foodrats.core.i18n.StringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.core.presentation.photopicker.PhotoPickResult
import es.schsebastian.foodrats.core.presentation.photopicker.rememberPhotoPicker
import es.schsebastian.foodrats.feature.auth.domain.error.ProfileError
import es.schsebastian.foodrats.feature.auth.i18n.AuthStringKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalTime
import org.koin.compose.viewmodel.koinViewModel

/**
 * Structural app-settings ("Profile"). A warm Iron & Ember [FrMediaFloor] under a zero-chrome scroll
 * plane: a pushed-screen header (floating back + centred title), then olive-eyebrow sections, each a
 * floating [FrGlassTile] of [FrStructuralRow]s with [FrGlassToggle]s / value+chevron trailing slots.
 * Identity (avatar/name/bio) edits in-place via structural atoms; the danger zone recedes to a deep
 * crimson-tinted tile. ALL ViewModel wiring (every intent, picker, dialog, the delete sub-screen, the
 * avatar image-picker effect) is preserved — only the visual layer changed. The option pickers still
 * use the matte [FrSettingsPicker] bottom sheet (those are their own screens to port).
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenAchievements: () -> Unit = {},
    onOpenEula: () -> Unit = {},
    onOpenGuidelines: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {},
    vm: ProfileViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val picker = rememberPhotoPicker { result ->
        when (result) {
            is PhotoPickResult.Picked -> scope.launch { handlePickedAvatarBytes(vm, result.bytes) }
            is PhotoPickResult.PickedMultiple -> scope.launch {
                // Defensive only — the avatar picker always launches single-pick
                // (`launchGallery()`/`launchCamera()`), so this arm should be unreachable; Wave 3
                // owns any real multi-photo avatar UX. Treat the first photo like a single Picked.
                handlePickedAvatarBytes(vm, result.photos.first().bytes)
            }
            is PhotoPickResult.Failed -> {
                FrLog.w(FrLog.Tags.Auth) { "[ProfileScreen] avatar picker error: ${result.message}" }
                vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.PickFailed))
            }
            PhotoPickResult.Cancelled -> Unit
        }
    }

    if (state.deleteScreenOpen) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Chrome-only settings surface — there is no photo here. The default `dim` (0.38) and the
        // `Even` scrim exist for white-on-photo legibility; over an atmospheric `stageFloor` they only
        // crush the Iron & Ember charcoal to near-black (the "settings is always black" report). Drop
        // both so the charcoal floor actually shows; white `foreground` clears AA over it comfortably.
        // Light mode already dropped the wash (FrMediaFloor.keepDarkWash), so this only affects dark.
        FrMediaFloor(brush = StructuralColors.stageFloor, blur = StructuralBlur.Soft, dim = 0f, scrim = null)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .frSafeHorizontalPadding()
                .frContentWidth(Breakpoints.formMax)
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            ProfileHeader(onBack = onBack)
            GeneralSection(
                state, vm,
                onPickAvatar = { picker.launchGallery() },
            )
            PreferencesSection(state, vm)
            AchievementsSection(onOpenAchievements)
            SafetySection(onOpenBlockedUsers)
            LegalSection(onOpenEula = onOpenEula, onOpenGuidelines = onOpenGuidelines)
            DataExportSection(
                isExportingData = state.isExportingData,
                exportError = state.exportError,
                exportDownloadUrl = state.exportDownloadUrl,
                onExport = { vm.onIntent(ProfileIntent.ExportMyData) },
                onDismissExport = { vm.onIntent(ProfileIntent.DismissExportResult) },
            )
            DangerZoneSection(
                isSigningOut = state.isSigningOut,
                signOutError = state.signOutError,
                onSignOut = { vm.onIntent(ProfileIntent.SignOut) },
                onOpenDeleteAccount = { vm.onIntent(ProfileIntent.OpenDeleteAccount) },
            )
            Spacer(Modifier.navigationBarsPadding().height(Spacing.xl))
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
            onSelect = { id -> vm.onIntent(ProfileIntent.ThemeSelected(ThemeMode.valueOf(id))) },
        )
    }

    if (state.localePickerOpen) {
        FrSettingsPicker(
            title = resolve(AuthStringKey.ProfileLanguagePickerTitle),
            options = localeOptions(),
            selectedId = state.locale.name,
            onDismiss = { vm.onIntent(ProfileIntent.LocalePickerDismiss) },
            onSelect = { id -> vm.onIntent(ProfileIntent.LocaleSelected(AppLocale.valueOf(id))) },
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
            onSelect = { id -> vm.onIntent(ProfileIntent.AccentSelected(AccentPalette.valueOf(id))) },
        )
    }
}

/**
 * Compresses a freshly picked avatar photo off the main thread and dispatches the matching
 * [ProfileIntent] — shared by the single-pick and defensive multi-pick picker branches.
 */
private suspend fun handlePickedAvatarBytes(vm: ProfileViewModel, bytes: ByteArray) {
    // Compression is CPU-bound — hop off the main thread so the UI keeps painting.
    val compression = withContext(Dispatchers.Default) { bytes.compressAvatarForUpload() }
    when (compression) {
        is AvatarCompression.Fit -> vm.onIntent(ProfileIntent.AvatarPicked(compression.bytes))
        AvatarCompression.Unreadable ->
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.Unreadable))
        AvatarCompression.TooLarge ->
            vm.onIntent(ProfileIntent.AvatarPrepareFailed(ProfileError.AvatarPrepare.TooLarge))
    }
}

// ----------------------------------------------------------------------------------------------
// Header + structural section/row scaffolding
// ----------------------------------------------------------------------------------------------

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrGlassCircleButton(
            icon = FrIcons.Back,
            onClick = onBack,
            contentDescription = resolve(AuthStringKey.ProfileBackCta),
        )
        FrText(
            text = resolve(AuthStringKey.ProfileTitle),
            style = StructuralType.titleMd.copy(textAlign = TextAlign.Center),
            color = StructuralColors.foreground,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(44.dp))
    }
}

/** An olive (or danger) eyebrow over a floating glass tile of rows. */
@Composable
private fun StructuralSection(
    eyebrow: String,
    danger: Boolean = false,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val semantic = LocalFrSemanticColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FrEyebrow(text = eyebrow.uppercase(), color = if (danger) semantic.danger else androidx.compose.material3.MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            FrText(text = subtitle, style = StructuralType.micro, color = StructuralColors.foreground.copy(alpha = 0.7f))
        }
        FrGlassTile(depth = if (danger) FrTileDepth.Deep else FrTileDepth.Default) { content() }
    }
}

/** A structural settings row: leading icon · title (+ optional subtitle) · trailing slot or chevron. */
@Composable
private fun SettingsRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    topHairline: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val semantic = LocalFrSemanticColors.current
    val titleColor = if (danger) semantic.danger else StructuralColors.foreground.copy(alpha = if (enabled) 1f else 0.4f)
    val iconColor = if (danger) semantic.danger else StructuralColors.foreground.copy(alpha = 0.7f)
    val chevron: (@Composable () -> Unit)? = if (onClick != null) {
        { FrIcon(image = FrIcons.ChevronRight, tint = StructuralColors.foreground.copy(alpha = 0.4f), modifier = Modifier.size(Sizes.iconSm)) }
    } else {
        null
    }
    FrStructuralRow(
        showTopHairline = topHairline,
        onClick = if (enabled) onClick else null,
        leading = { FrIcon(image = icon, tint = iconColor, modifier = Modifier.size(Sizes.iconMd)) },
        trailing = trailing ?: chevron,
    ) {
        FrText(text = title, style = StructuralType.titleMd, color = titleColor)
        if (subtitle != null) {
            FrText(text = subtitle, style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun RowError(text: String) {
    FrText(
        text = text,
        style = StructuralType.micro,
        color = LocalFrSemanticColors.current.danger,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}

// ----------------------------------------------------------------------------------------------
// Sections
// ----------------------------------------------------------------------------------------------

@Composable
private fun GeneralSection(state: ProfileState, vm: ProfileViewModel, onPickAvatar: () -> Unit) {
    val account = state.account
    val identityLoading = account == null &&
        state.uploadAvatarError == null &&
        state.saveDisplayNameError == null
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfileIdentitySection)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (identityLoading) {
                FrText(text = resolve(AuthStringKey.ProfileIdentityLoading), style = StructuralType.titleLg, color = StructuralColors.foreground.copy(alpha = 0.5f))
            } else {
                // Identity header — avatar + name + badge + change button.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    FrGlassAvatar(
                        initials = account?.displayName?.take(2)?.uppercase().orEmpty().ifBlank { "?" },
                        image = account?.avatarUrl?.let { rememberAsyncImagePainter(it) },
                        ring = FrAvatarRing.Moss,
                        size = 56.dp,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        FrText(text = account?.displayName.orEmpty(), style = StructuralType.titleLg, color = StructuralColors.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        account?.email?.let { FrText(text = it, style = StructuralType.micro, color = StructuralColors.foreground.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        account?.badgeId?.let { FrStructuralChip(label = resolveBadgeLabel(it), compact = true) }
                    }
                    FrGlassCircleButton(
                        icon = FrIcons.Camera,
                        onClick = onPickAvatar,
                        contentDescription = resolve(AuthStringKey.ProfileChangeAvatarCta),
                        size = 40.dp,
                    )
                }
                if (state.isUploadingAvatar || state.isRemovingAvatar) {
                    FrText(
                        text = resolve(if (state.isRemovingAvatar) AuthStringKey.ProfileAvatarRemoving else AuthStringKey.ProfileAvatarUploading),
                        style = StructuralType.micro,
                        color = StructuralColors.foreground.copy(alpha = 0.7f),
                    )
                } else if (account?.avatarUrl != null) {
                    FrGlassButton(
                        label = resolve(AuthStringKey.ProfileRemoveAvatarCta),
                        onClick = { vm.onIntent(ProfileIntent.RemoveAvatarRequested) },
                        tone = FrButtonTone.Ghost,
                        compact = true,
                    )
                }
                state.uploadAvatarError?.let { RowError(resolve(it)) }
                state.removeAvatarError?.let { RowError(resolve(it)) }

                // Display-name editor.
                FrUnderlineField(
                    value = state.editingDisplayName,
                    onValueChange = { vm.onIntent(ProfileIntent.DisplayNameChanged(it)) },
                    label = resolve(AuthStringKey.ProfileDisplayNameLabel),
                    enabled = !state.isSavingDisplayName,
                )
                FrGlassButton(
                    label = resolve(AuthStringKey.ProfileSave),
                    onClick = { vm.onIntent(ProfileIntent.SaveDisplayName) },
                    tone = FrButtonTone.Glass,
                    enabled = state.editingDisplayName.isNotBlank() &&
                        state.editingDisplayName != account?.displayName &&
                        !state.isSavingDisplayName,
                    compact = true,
                )
                state.saveDisplayNameError?.let { RowError(resolve(it)) }

                // Bio editor.
                FrUnderlineField(
                    value = state.editingBio,
                    onValueChange = { vm.onIntent(ProfileIntent.BioChanged(it)) },
                    label = resolve(AuthStringKey.ProfileBioLabel),
                    placeholder = resolve(AuthStringKey.ProfileBioPlaceholder),
                    singleLine = false,
                    enabled = !state.isSavingBio,
                )
                FrGlassButton(
                    label = resolve(AuthStringKey.ProfileBioSave),
                    onClick = { vm.onIntent(ProfileIntent.SaveBio) },
                    tone = FrButtonTone.Glass,
                    enabled = state.editingBio != (account?.bio?.value ?: "") && !state.isSavingBio,
                    compact = true,
                )
                state.saveBioError?.let { RowError(resolve(it)) }
            }
        }
    }
}

@Composable
private fun PreferencesSection(state: ProfileState, vm: ProfileViewModel) {
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfilePreferencesSection)) {
        // Theme
        SettingsRow(
            title = resolve(AuthStringKey.ProfileThemeRow),
            subtitle = resolveThemeLabel(state.themeMode),
            icon = FrIcons.Theme,
            topHairline = false,
            onClick = { vm.onIntent(ProfileIntent.ThemePickerOpen) },
        )
        state.themeError?.let { RowError(resolve(it)) }

        // Language
        SettingsRow(
            title = resolve(AuthStringKey.ProfileLanguageRow),
            subtitle = resolveLocaleLabel(state.locale),
            icon = FrIcons.Language,
            topHairline = true,
            onClick = {
                if (opensSystemSettingsForLanguage) openAppLanguageSettings()
                else vm.onIntent(ProfileIntent.LocalePickerOpen)
            },
        )
        state.localeError?.let { RowError(resolve(it)) }

        // Notifications
        SettingsRow(
            title = resolve(AuthStringKey.ProfileNotificationsRow),
            subtitle = if (state.notificationsEnabled) resolve(AuthStringKey.ProfileNotificationsSubtitleOn) else resolve(AuthStringKey.ProfileNotificationsSubtitleOff),
            icon = FrIcons.Notifications,
            topHairline = true,
            trailing = {
                FrGlassToggle(
                    checked = state.notificationsEnabled,
                    onCheckedChange = { vm.onIntent(ProfileIntent.NotificationsToggled(it)) },
                    contentDescription = resolve(AuthStringKey.ProfileNotificationsRow),
                )
            },
        )
        state.notificationsError?.let { errorKey ->
            RowError(resolve(errorKey))
            if (errorKey == AuthStringKey.ProfileNotificationsPermissionDeniedForever) {
                FrGlassButton(
                    label = resolve(AuthStringKey.ProfileNotificationsOpenSystemSettingsCta),
                    onClick = { vm.onIntent(ProfileIntent.OpenNotificationSystemSettings) },
                    tone = FrButtonTone.Glass,
                    compact = true,
                )
            }
        }

        // Meal reminders
        SettingsRow(
            title = resolve(AuthStringKey.ProfileRemindersRow),
            subtitle = resolve(AuthStringKey.ProfileRemindersSubtitle),
            icon = FrIcons.Notifications,
            topHairline = true,
        )
        if (state.reminderTimes.isEmpty()) {
            FrText(
                text = resolve(AuthStringKey.ProfileRemindersEmpty),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = Sizes.iconMd + Spacing.md),
            )
        } else {
            state.reminderTimes.forEachIndexed { index, time ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Sizes.iconMd + Spacing.md, top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrText(
                        text = formatReminderTime(time),
                        style = StructuralType.titleMd,
                        color = StructuralColors.foreground,
                        modifier = Modifier.weight(1f).padding(vertical = Spacing.xs)
                            .clickable { vm.onIntent(ProfileIntent.ReminderEditOpen(index)) },
                    )
                    FrGlassCircleButton(
                        icon = FrIcons.Delete,
                        onClick = { vm.onIntent(ProfileIntent.ReminderRemove(index)) },
                        contentDescription = resolve(AuthStringKey.ProfileRemindersRemoveCta),
                        size = 32.dp,
                        danger = true,
                    )
                }
            }
        }
        if (state.reminderTimes.size < MealReminderSchedulePort.MAX_REMINDERS) {
            Spacer(Modifier.height(Spacing.xs))
            FrGlassButton(
                label = resolve(AuthStringKey.ProfileRemindersAddCta),
                onClick = { vm.onIntent(ProfileIntent.ReminderAddOpen) },
                tone = FrButtonTone.Glass,
                compact = true,
            )
        }
        state.reminderError?.let { RowError(resolve(it)) }

        // AI suggestions
        SettingsRow(
            title = resolve(AuthStringKey.ProfileAiRow),
            subtitle = if (state.aiEnabled) resolve(AuthStringKey.ProfileAiSubtitleOn) else resolve(AuthStringKey.ProfileAiSubtitleOff),
            icon = FrIcons.Stats,
            topHairline = true,
            trailing = {
                FrGlassToggle(checked = state.aiEnabled, onCheckedChange = { vm.onIntent(ProfileIntent.AiToggled(it)) }, contentDescription = resolve(AuthStringKey.ProfileAiRow))
            },
        )
        state.aiError?.let { RowError(resolve(it)) }

        // Analytics consent
        SettingsRow(
            title = resolve(AuthStringKey.ProfileAnalyticsRow),
            subtitle = if (state.analyticsConsentGranted) resolve(AuthStringKey.ProfileAnalyticsSubtitleOn) else resolve(AuthStringKey.ProfileAnalyticsSubtitleOff),
            icon = FrIcons.Stats,
            topHairline = true,
            trailing = {
                FrGlassToggle(checked = state.analyticsConsentGranted, onCheckedChange = { vm.onIntent(ProfileIntent.AnalyticsConsentToggled(it)) }, contentDescription = resolve(AuthStringKey.ProfileAnalyticsRow))
            },
        )

        // Accent colour
        SettingsRow(
            title = resolve(AuthStringKey.ProfileAccentRow),
            subtitle = resolveAccentLabel(state.accentPalette),
            icon = FrIcons.Theme,
            topHairline = true,
            onClick = { vm.onIntent(ProfileIntent.AccentPickerOpen) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AccentSwatchDot(accent = state.accentPalette.toFrAccent())
                    FrIcon(image = FrIcons.ChevronRight, tint = StructuralColors.foreground.copy(alpha = 0.4f), modifier = Modifier.size(Sizes.iconSm))
                }
            },
        )
        state.accentError?.let { RowError(resolve(it)) }
    }
}

@Composable
private fun AchievementsSection(onOpenAchievements: () -> Unit) {
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfileAchievementsSection)) {
        SettingsRow(
            title = resolve(AuthStringKey.ProfileAchievementsRow),
            subtitle = resolve(AuthStringKey.ProfileAchievementsSubtitle),
            icon = FrIcons.Trophy,
            topHairline = false,
            onClick = onOpenAchievements,
        )
    }
}

@Composable
private fun SafetySection(onOpenBlockedUsers: () -> Unit) {
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfileSafetySection)) {
        SettingsRow(
            title = resolve(AuthStringKey.ProfileBlockedUsersRow),
            subtitle = resolve(AuthStringKey.ProfileBlockedUsersSubtitle),
            icon = FrIcons.Block,
            topHairline = false,
            onClick = onOpenBlockedUsers,
        )
    }
}

@Composable
private fun LegalSection(onOpenEula: () -> Unit, onOpenGuidelines: () -> Unit) {
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfileLegalSection)) {
        SettingsRow(
            title = resolve(AuthStringKey.ProfileLegalEulaRow),
            icon = FrIcons.Public,
            topHairline = false,
            onClick = onOpenEula,
        )
        SettingsRow(
            title = resolve(AuthStringKey.ProfileLegalGuidelinesRow),
            icon = FrIcons.Public,
            topHairline = true,
            onClick = onOpenGuidelines,
        )
    }
}

@Composable
private fun DataExportSection(
    isExportingData: Boolean,
    exportError: StringKey?,
    exportDownloadUrl: String?,
    onExport: () -> Unit,
    onDismissExport: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    StructuralSection(eyebrow = resolve(AuthStringKey.ProfileAccountSection)) {
        SettingsRow(
            title = resolve(AuthStringKey.ExportDataRow),
            subtitle = if (isExportingData) resolve(AuthStringKey.ExportDataInFlight) else resolve(AuthStringKey.ExportDataSubtitle),
            icon = FrIcons.Stats,
            topHairline = false,
            enabled = !isExportingData,
            onClick = onExport,
        )
        exportError?.let { RowError(resolve(it)) }
        exportDownloadUrl?.let { url ->
            Spacer(Modifier.height(Spacing.sm))
            FrText(
                text = resolve(AuthStringKey.ExportDataReadySubtitle),
                style = StructuralType.body,
                color = StructuralColors.foreground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(Spacing.xs))
            FrGlassButton(
                label = resolve(AuthStringKey.ExportDataReadyCta),
                onClick = {
                    uriHandler.openUri(url)
                    onDismissExport()
                },
                tone = FrButtonTone.Glass,
                compact = true,
            )
        }
    }
}

@Composable
private fun DangerZoneSection(
    isSigningOut: Boolean,
    signOutError: StringKey?,
    onSignOut: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
) {
    StructuralSection(
        eyebrow = resolve(AuthStringKey.ProfileDangerZoneSection),
        subtitle = resolve(AuthStringKey.ProfileDangerZoneSubtitle),
        danger = true,
    ) {
        SettingsRow(
            title = resolve(AuthStringKey.ProfileSignOutCta),
            icon = FrIcons.Logout,
            topHairline = false,
            enabled = !isSigningOut,
            danger = true,
            onClick = onSignOut,
        )
        signOutError?.let { RowError(resolve(it)) }
        SettingsRow(
            title = resolve(AuthStringKey.ProfileDeleteAccountRow),
            subtitle = resolve(AuthStringKey.ProfileDeleteAccountSubtitle),
            icon = FrIcons.Delete,
            topHairline = true,
            danger = true,
            onClick = onOpenDeleteAccount,
        )
    }
}

// ── Reminder + option helpers (unchanged) ─────────────────────────────────────────────────────────

private fun formatReminderTime(time: LocalTime): String {
    val hh = time.hour.toString().padStart(2, '0')
    val mm = time.minute.toString().padStart(2, '0')
    return "$hh:$mm"
}

private fun reminderHourOptions(): List<Pair<String, String>> =
    (0..23).map { hour -> hour.toString() to formatReminderTime(LocalTime(hour = hour, minute = 0)) }

@Composable
private fun resolveBadgeLabel(badgeId: String): String = when (badgeId) {
    "first"   -> resolve(AuthStringKey.ProfileBadgeFirst)
    "ten"     -> resolve(AuthStringKey.ProfileBadgeTen)
    "fifty"   -> resolve(AuthStringKey.ProfileBadgeFifty)
    "hundred" -> resolve(AuthStringKey.ProfileBadgeHundred)
    else      -> badgeId
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

@Composable
private fun AccentSwatchDot(accent: FrAccent, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.iconMd)
            .background(color = accent.swatch, shape = CircleShape),
    )
}
