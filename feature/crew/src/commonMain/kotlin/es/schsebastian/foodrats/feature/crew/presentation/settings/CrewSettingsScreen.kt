package es.schsebastian.foodrats.feature.crew.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrQrCode
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.molecules.FrSettingsPicker
import es.schsebastian.foodrats.core.designsystem.structural.FrButtonTone
import es.schsebastian.foodrats.core.designsystem.structural.FrEyebrow
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassCircleButton
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassDialog
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassTile
import es.schsebastian.foodrats.core.designsystem.structural.FrGlassToggle
import es.schsebastian.foodrats.core.designsystem.structural.FrMediaFloor
import es.schsebastian.foodrats.core.designsystem.structural.FrMicroRow
import es.schsebastian.foodrats.core.designsystem.structural.FrScrimStyle
import es.schsebastian.foodrats.core.designsystem.structural.FrStructuralRow
import es.schsebastian.foodrats.core.designsystem.structural.FrTileDepth
import es.schsebastian.foodrats.core.designsystem.structural.StructuralBlur
import es.schsebastian.foodrats.core.designsystem.structural.StructuralColors
import es.schsebastian.foodrats.core.designsystem.structural.StructuralType
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.crew.CrewScoreStyle
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.share.ShareController
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.components.FrCrewMemberRow
import es.schsebastian.foodrats.feature.crew.presentation.settings.components.DeleteCrewConfirmDialog
import es.schsebastian.foodrats.feature.crew.presentation.settings.components.LeaveCrewConfirmDialog
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import io.github.ismoy.imagepickerkmp.domain.extensions.asSource
import io.github.ismoy.imagepickerkmp.domain.models.MimeType
import io.github.ismoy.imagepickerkmp.features.imagepicker.model.ImagePickerResult
import io.github.ismoy.imagepickerkmp.features.imagepicker.ui.rememberImagePickerKMP
import kotlinx.coroutines.delay
import kotlinx.io.readByteArray
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Structural crew settings: an edge-to-edge [FrMediaFloor] (the crew banner, or a mackerel mood) with
 * floating glass chrome (back + crew name) and a zero-chrome content plane of frosted strata — the
 * crew-identity hero, owner-only edit tiles (name, blind-voting, tagline, welcome, weekly challenge,
 * score style, banner), the member list, switch-crew, and a crimson danger zone. ALL ViewModel wiring
 * is preserved verbatim; only the visual layer is structural. Dialogs / the score-style sheet stay
 * matte (their own "small screens" to port).
 */
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
    val deletedMemberFallback = resolve(CrewStringKey.MemberDeleted)
    // Name to show in the success toast; set by the MemberRemoved effect, cleared once shown.
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

    val crew = state.crew

    // When a banner is set the floor is that photo (dark in both themes) → floating chrome/eyebrows over
    // it use white onMedia. With no banner the floor is the adaptive atmospheric one (light in light
    // mode) → floating content uses the theme-aware foreground + the olive eyebrow accent.
    val onMediaFloor = state.bannerImageUrl != null
    val planeFg = if (onMediaFloor) StructuralColors.onMedia else StructuralColors.foreground
    val planeEyebrow = if (onMediaFloor) StructuralColors.onMedia.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Z0 — the crew banner blurred (or a mackerel mood) as the floor.
        val bannerUrl = state.bannerImageUrl
        if (bannerUrl != null) {
            FrMediaFloor(
                painter = coil3.compose.rememberAsyncImagePainter(bannerUrl),
                blur = StructuralBlur.Heavy,
                dim = 0.5f,
                scrim = FrScrimStyle.Even,
            )
        } else {
            // No banner: an atmospheric floor that goes LIGHT in light mode (so the section eyebrows +
            // tiles read as a proper light screen). A set banner is a real photo → stays dark (above).
            FrMediaFloor(brush = StructuralColors.fieldFloor, blur = StructuralBlur.Soft)
        }

        when {
            crew == null && state.error != null -> Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                FrText(
                    text = resolve(state.error!!.toStringKey()),
                    style = StructuralType.body,
                    color = LocalFrSemanticColors.current.danger,
                )
            }
            crew == null -> CrewSettingsSkeleton()
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = 72.dp,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    // (a) Identity hero + invite + share + QR.
                    item {
                        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.frRevealScale()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                FrText(text = crew.name, style = StructuralType.titleXl, color = StructuralColors.foreground)
                                FrMicroRow(items = listOf(resolve(CrewStringKey.SettingsMembersCount, crew.size).uppercase()))
                                // Invite code — tap to copy.
                                FrGlassButton(
                                    label = crew.code.value,
                                    onClick = { clipboardManager.setText(AnnotatedString(crew.code.value)) },
                                    tone = FrButtonTone.Glass,
                                    fillWidth = true,
                                )
                                val inviteUrl = inviteUrlFor(crew.code.value)
                                val shareMessage = resolve(CrewStringKey.InviteShareMessage, crew.name, inviteUrl)
                                // Stacked full-width, not a 50/50 Row: neither "Share invite link" nor
                                // "Compartir invitación"/"Mostrar código QR" fits a half-width pill on one
                                // line, so the side-by-side layout wrapped to 2–3 lines. Full-width pills
                                // hold their label on one line in every language.
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    FrGlassButton(
                                        label = resolve(CrewStringKey.SettingsShareLink),
                                        onClick = {
                                            vm.onIntent(CrewSettingsIntent.ShareLinkTapped)
                                            share.shareText(shareMessage)
                                        },
                                        tone = FrButtonTone.Primary,
                                        leadingIcon = FrIcons.Share,
                                        fillWidth = true,
                                    )
                                    FrGlassButton(
                                        label = resolve(CrewStringKey.SettingsShowQr),
                                        onClick = { showQr = true },
                                        tone = FrButtonTone.Ghost,
                                        fillWidth = true,
                                    )
                                }
                            }
                        }
                    }

                    if (state.isOwner) {
                        // (b) Crew-name edit.
                        item {
                            SaveableFieldTile(
                                eyebrow = null,
                                label = resolve(CrewStringKey.SettingsCrewNameLabel),
                                value = state.editingCrewName,
                                onValueChange = { vm.onIntent(CrewSettingsIntent.CrewNameChanged(it)) },
                                singleLine = true,
                                saving = state.isSavingCrewName,
                                saveEnabled = state.editingCrewName.isNotBlank() &&
                                    state.editingCrewName != crew.name && !state.isSavingCrewName,
                                onSave = { vm.onIntent(CrewSettingsIntent.SaveCrewName) },
                                modifier = Modifier.frRiseIn(delayMillis = 40),
                            )
                        }

                        // (c) Blind voting.
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.frRiseIn(delayMillis = 80)) {
                                FrEyebrow(text = resolve(CrewStringKey.SettingsBlindVotingSection).uppercase(), color = planeEyebrow)
                                FrGlassTile {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                            FrText(
                                                text = resolve(CrewStringKey.SettingsBlindVotingLabel),
                                                style = StructuralType.titleMd,
                                                color = StructuralColors.foreground,
                                            )
                                            FrText(
                                                text = resolve(CrewStringKey.SettingsBlindVotingDescription),
                                                style = StructuralType.body,
                                                color = StructuralColors.foreground.copy(alpha = 0.7f),
                                            )
                                        }
                                        FrGlassToggle(
                                            checked = crew.blindVoting,
                                            onCheckedChange = { vm.onIntent(CrewSettingsIntent.ToggleBlindVoting(it)) },
                                            contentDescription = resolve(CrewStringKey.SettingsBlindVotingLabel),
                                            enabled = !state.isSavingBlindVoting,
                                        )
                                    }
                                }
                            }
                        }

                        // (d) Tagline (house rules).
                        item {
                            SaveableFieldTile(
                                eyebrow = resolve(CrewStringKey.SettingsTaglineSection).uppercase(),
                                label = resolve(CrewStringKey.SettingsTaglineLabel),
                                value = state.editingTagline,
                                onValueChange = { vm.onIntent(CrewSettingsIntent.TaglineChanged(it)) },
                                singleLine = false,
                                saving = state.isSavingTagline,
                                saveEnabled = !state.isSavingTagline && state.editingTagline.trim() != crew.tagline?.value.orEmpty(),
                                onSave = { vm.onIntent(CrewSettingsIntent.SaveTagline) },
                                eyebrowColor = planeEyebrow,
                                modifier = Modifier.frRiseIn(delayMillis = 100),
                                placeholder = resolve(CrewStringKey.SettingsTaglinePlaceholder),
                            )
                        }

                        // (e) Welcome message.
                        item {
                            SaveableFieldTile(
                                eyebrow = resolve(CrewStringKey.SettingsWelcomeMessageSection).uppercase(),
                                label = resolve(CrewStringKey.SettingsWelcomeMessageLabel),
                                value = state.editingWelcomeMessage,
                                onValueChange = { vm.onIntent(CrewSettingsIntent.WelcomeMessageChanged(it)) },
                                singleLine = false,
                                saving = state.isSavingWelcomeMessage,
                                saveEnabled = !state.isSavingWelcomeMessage && state.editingWelcomeMessage.trim() != crew.welcomeMessage?.value.orEmpty(),
                                onSave = { vm.onIntent(CrewSettingsIntent.SaveWelcomeMessage) },
                                eyebrowColor = planeEyebrow,
                                modifier = Modifier.frRiseIn(delayMillis = 120),
                                placeholder = resolve(CrewStringKey.SettingsWelcomeMessagePlaceholder),
                            )
                        }

                        // (f) Weekly challenge.
                        item {
                            SaveableFieldTile(
                                eyebrow = resolve(CrewStringKey.SettingsWeeklyChallengeSection).uppercase(),
                                label = resolve(CrewStringKey.SettingsWeeklyChallengeLabel),
                                value = state.editingWeeklyChallenge,
                                onValueChange = { vm.onIntent(CrewSettingsIntent.WeeklyChallengeChanged(it)) },
                                singleLine = false,
                                saving = state.isSavingWeeklyChallenge,
                                saveEnabled = !state.isSavingWeeklyChallenge && state.editingWeeklyChallenge.trim() != crew.weeklyChallenge?.value.orEmpty(),
                                onSave = { vm.onIntent(CrewSettingsIntent.SaveWeeklyChallenge) },
                                eyebrowColor = planeEyebrow,
                                modifier = Modifier.frRiseIn(delayMillis = 140),
                                placeholder = resolve(CrewStringKey.SettingsWeeklyChallengePlaceholder),
                            )
                        }

                        // (g) Score style.
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.frRiseIn(delayMillis = 160)) {
                                FrEyebrow(text = resolve(CrewStringKey.SettingsScoreStyleSection).uppercase(), color = planeEyebrow)
                                val styleLabel = when (crew.scoreStyle) {
                                    CrewScoreStyle.Stars -> resolve(CrewStringKey.SettingsScoreStyleStars)
                                    CrewScoreStyle.Emoji -> resolve(CrewStringKey.SettingsScoreStyleEmoji)
                                    CrewScoreStyle.Numeric -> resolve(CrewStringKey.SettingsScoreStyleNumeric)
                                }
                                FrGlassTile(
                                    depth = FrTileDepth.Deep,
                                    onClick = if (state.isSavingScoreStyle) null else ({ vm.onIntent(CrewSettingsIntent.OpenScoreStylePicker) }),
                                ) {
                                    TileRow(
                                        icon = FrIcons.Star,
                                        title = resolve(CrewStringKey.SettingsScoreStyleLabel),
                                        subtitle = styleLabel,
                                        trailing = {
                                            if (state.isSavingScoreStyle) {
                                                FrProgressIndicator(modifier = Modifier.size(Sizes.iconMd), strokeWidth = 2.dp)
                                            } else {
                                                Chevron()
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // (h) Banner.
                        item {
                            BannerSection(
                                hasBanner = crew.bannerPath != null,
                                imageUrl = state.bannerImageUrl,
                                focalY = crew.bannerFocalY,
                                saving = state.isSavingBanner,
                                onPicked = { vm.onIntent(CrewSettingsIntent.BannerPicked(it)) },
                                onRemove = { vm.onIntent(CrewSettingsIntent.RemoveBanner) },
                                onReposition = { vm.onIntent(CrewSettingsIntent.RepositionBanner(it)) },
                                modifier = Modifier.frRiseIn(delayMillis = 180),
                            )
                        }
                    }

                    // (i) Members.
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.frRiseIn(delayMillis = 140)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FrEyebrow(text = resolve(CrewStringKey.SettingsMembersSection).uppercase(), color = planeEyebrow)
                                if (state.isOwner) {
                                    FrText(
                                        text = resolve(CrewStringKey.SettingsOwnerBadge),
                                        style = StructuralType.micro,
                                        color = planeFg.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            FrGlassTile {
                                crew.members.forEachIndexed { index, m ->
                                    if (index > 0) {
                                        HorizontalDivider(color = StructuralColors.dividerSoft, modifier = Modifier.padding(vertical = Spacing.xxs))
                                    }
                                    val isMemberOwner = m.accountId == crew.ownerId
                                    val canRemove = state.isOwner && m.accountId != state.myAccountId
                                    val isRemoving = m.accountId in state.removingMemberIds
                                    FrCrewMemberRow(
                                        account = state.identities[m.accountId],
                                        subtitle = resolve(
                                            if (isMemberOwner) CrewStringKey.SettingsRoleOwner else CrewStringKey.SettingsRoleMember,
                                        ),
                                        trailing = if (canRemove) {
                                            {
                                                if (isRemoving) {
                                                    FrProgressIndicator(modifier = Modifier.size(Sizes.iconMd), strokeWidth = 2.dp)
                                                } else {
                                                    FrIconButton(
                                                        icon = FrIcons.Close,
                                                        onClick = { memberPendingRemoval = m.accountId },
                                                        contentDescription = resolve(CrewStringKey.SettingsRemoveMemberCta),
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // (j) Switch crew.
                    item {
                        FrGlassButton(
                            label = resolve(CrewStringKey.SettingsSwitchCrew),
                            onClick = { vm.onIntent(CrewSettingsIntent.SwitchCrew) },
                            tone = FrButtonTone.Glass,
                            fillWidth = true,
                            modifier = Modifier.frRiseIn(delayMillis = 180),
                        )
                    }

                    // (k) Danger zone.
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.frRiseIn(delayMillis = 220)) {
                            FrEyebrow(
                                text = resolve(CrewStringKey.SettingsDangerSection).uppercase(),
                                color = LocalFrSemanticColors.current.danger,
                            )
                            FrGlassTile(depth = FrTileDepth.Deep) {
                                DangerRow(
                                    icon = FrIcons.Logout,
                                    label = resolve(CrewStringKey.SettingsLeaveCta),
                                    enabled = !state.isLeaving,
                                    showTopHairline = false,
                                    onClick = { vm.onIntent(CrewSettingsIntent.RequestLeave) },
                                )
                                if (state.isOwner) {
                                    DangerRow(
                                        icon = FrIcons.Delete,
                                        label = resolve(CrewStringKey.SettingsDeleteCta),
                                        enabled = !state.isDeleting,
                                        showTopHairline = true,
                                        onClick = { vm.onIntent(CrewSettingsIntent.RequestDelete) },
                                    )
                                }
                            }
                        }
                    }

                    // (l) Trailing error banner.
                    state.error?.let { err ->
                        item {
                            DangerBanner(text = resolve(err.toStringKey()))
                        }
                    }
                }
            }
        }

        // Top scrim band BEHIND the floating chrome: occludes section eyebrows/tiles that scroll up
        // under the (otherwise fully transparent) bar mid-list — content fades into the band instead of
        // blending with the bar's text. Decorative only (no semantics, no touch handling — sits behind the
        // Row so the back button + label stay tappable/visible above it). Theme/banner-aware to match the
        // screen: a DARK ink gradient over a banner photo (keeps the white onMedia bar text readable), a
        // LIGHT warm-floor gradient otherwise (so it blends into the light atmospheric floor). Opaque-ish
        // at the very top, fading to fully transparent at the bar's bottom edge.
        if (crew != null) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bandHeight = statusBarTop + 64.dp
            val bandTopColor =
                if (onMediaFloor) Color.Black.copy(alpha = 0.55f) else StructuralColors.tileSolid.copy(alpha = 0.95f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bandHeight)
                    .background(
                        Brush.verticalGradient(
                            0f to bandTopColor,
                            0.6f to bandTopColor.copy(alpha = bandTopColor.alpha * 0.5f),
                            1f to bandTopColor.copy(alpha = 0f),
                        ),
                    ),
            )
        }

        // Floating chrome — back (left) + centered crew label.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FrGlassCircleButton(
                icon = FrIcons.Back,
                onClick = onBack,
                contentDescription = resolve(CrewStringKey.SettingsBackCta),
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FrEyebrow(text = resolve(CrewStringKey.SettingsTitle).uppercase(), color = planeEyebrow)
                FrText(
                    text = crew?.name.orEmpty(),
                    style = StructuralType.titleMd,
                    color = planeFg,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Spacer matching the back button's footprint so the label stays centered.
            Spacer(Modifier.size(44.dp))
        }

        // Member-removed toast.
        memberRemovedName?.let { name ->
            StructuralToast(
                message = resolve(CrewStringKey.SettingsMemberRemoved, name),
                onDismiss = { memberRemovedName = null },
            )
        }
    }

    // QR invite dialog (kept matte — its own small screen to port).
    if (showQr) {
        crew?.let { c ->
            InviteQrDialog(
                crewName = c.name,
                inviteUrl = inviteUrlFor(c.code.value),
                onDismiss = { showQr = false },
            )
        }
    }

    // Leave-crew confirm.
    if (state.showLeaveConfirm) {
        LeaveCrewConfirmDialog(
            crewName = crew?.name.orEmpty(),
            onConfirm = { vm.onIntent(CrewSettingsIntent.ConfirmLeave) },
            onDismiss = { vm.onIntent(CrewSettingsIntent.CancelLeave) },
        )
    }

    // Delete-crew confirm.
    if (state.showDeleteConfirm) {
        DeleteCrewConfirmDialog(
            crewName = crew?.name.orEmpty(),
            onConfirm = { vm.onIntent(CrewSettingsIntent.ConfirmDelete) },
            onDismiss = { vm.onIntent(CrewSettingsIntent.CancelDelete) },
        )
    }

    // Score-style picker (bottom sheet, kept matte).
    if (state.showScoreStylePicker) {
        crew?.let { c ->
            FrSettingsPicker(
                title = resolve(CrewStringKey.SettingsScoreStyleLabel),
                options = listOf(
                    "stars" to resolve(CrewStringKey.SettingsScoreStyleStars),
                    "emoji" to resolve(CrewStringKey.SettingsScoreStyleEmoji),
                    "numeric" to resolve(CrewStringKey.SettingsScoreStyleNumeric),
                ),
                selectedId = when (c.scoreStyle) {
                    CrewScoreStyle.Stars -> "stars"
                    CrewScoreStyle.Emoji -> "emoji"
                    CrewScoreStyle.Numeric -> "numeric"
                },
                onDismiss = { vm.onIntent(CrewSettingsIntent.DismissScoreStylePicker) },
                onSelect = { id ->
                    val style = when (id) {
                        "emoji" -> CrewScoreStyle.Emoji
                        "numeric" -> CrewScoreStyle.Numeric
                        else -> CrewScoreStyle.Stars
                    }
                    vm.onIntent(CrewSettingsIntent.SetScoreStyle(style))
                },
            )
        }
    }

    // Remove-banner confirm.
    if (state.showRemoveBannerConfirm) {
        FrConfirmDialog(
            title = resolve(CrewStringKey.SettingsBannerRemoveTitle),
            message = resolve(CrewStringKey.SettingsBannerRemoveBody),
            confirmLabel = resolve(CrewStringKey.SettingsBannerRemove),
            dismissLabel = resolve(CrewStringKey.SettingsCancel),
            onConfirm = { vm.onIntent(CrewSettingsIntent.ConfirmRemoveBanner) },
            onDismiss = { vm.onIntent(CrewSettingsIntent.CancelRemoveBanner) },
            destructive = true,
        )
    }

    // Remove-member confirm.
    memberPendingRemoval?.let { pendingId ->
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

// ---- Structural building blocks -------------------------------------------------------------------

/** A glass tile holding a labelled underline field + a Save button gated by [saveEnabled]. */
@Composable
private fun SaveableFieldTile(
    eyebrow: String?,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
    saving: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    eyebrowColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier) {
        if (eyebrow != null) FrEyebrow(text = eyebrow, color = eyebrowColor)
        FrGlassTile {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                es.schsebastian.foodrats.core.designsystem.structural.FrUnderlineField(
                    value = value,
                    onValueChange = onValueChange,
                    label = label.uppercase(),
                    placeholder = placeholder,
                    singleLine = singleLine,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                FrGlassButton(
                    label = resolve(CrewStringKey.SettingsSave),
                    onClick = onSave,
                    tone = FrButtonTone.Primary,
                    enabled = saveEnabled,
                    compact = true,
                )
            }
        }
    }
}

/** Icon-badge + title + subtitle + trailing row, the structural list-row shape. */
@Composable
private fun TileRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(Radius.sm)).background(scheme.primary.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            FrIcon(image = icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(Sizes.iconMd))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            FrText(text = title, style = StructuralType.titleMd, color = StructuralColors.foreground)
            if (subtitle != null) {
                FrText(text = subtitle, style = StructuralType.body, color = StructuralColors.foreground.copy(alpha = 0.7f))
            }
        }
        trailing()
    }
}

@Composable
private fun DangerRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    showTopHairline: Boolean,
    onClick: () -> Unit,
) {
    val danger = LocalFrSemanticColors.current.danger
    FrStructuralRow(
        onClick = if (enabled) onClick else null,
        showTopHairline = showTopHairline,
        leading = { FrIcon(image = icon, contentDescription = null, tint = danger, modifier = Modifier.size(Sizes.iconMd)) },
        trailing = { Chevron() },
    ) {
        FrText(text = label, style = StructuralType.titleMd, color = danger)
    }
}

@Composable
private fun Chevron() {
    FrIcon(
        image = FrIcons.ChevronRight,
        contentDescription = null,
        tint = StructuralColors.foreground.copy(alpha = 0.4f),
        modifier = Modifier.size(Sizes.iconMd),
    )
}

@Composable
private fun BannerSection(
    hasBanner: Boolean,
    imageUrl: String?,
    focalY: Float,
    saving: Boolean,
    onPicked: (ByteArray) -> Unit,
    onRemove: () -> Unit,
    onReposition: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberImagePickerKMP()
    LaunchedEffect(picker.result) {
        when (val r = picker.result) {
            is ImagePickerResult.Success -> {
                val photo = r.first ?: return@LaunchedEffect
                val bytes = photo.asSource().readByteArray()
                onPicked(bytes)
                picker.reset()
            }
            is ImagePickerResult.Error,
            is ImagePickerResult.Dismissed,
            is ImagePickerResult.Loading,
            is ImagePickerResult.Idle,
            -> Unit
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = modifier) {
        // With a banner the floor is that dark photo → white eyebrow/hint; without one the floor is the
        // light atmospheric floor → the olive eyebrow accent.
        FrEyebrow(
            text = resolve(CrewStringKey.SettingsBannerSection).uppercase(),
            color = if (hasBanner) StructuralColors.onMedia.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary,
        )
        if (hasBanner && imageUrl != null) {
            BannerRepositionPreview(imageUrl = imageUrl, focalY = focalY, onReposition = onReposition)
            FrText(
                text = resolve(CrewStringKey.SettingsBannerRepositionHint),
                style = StructuralType.micro,
                color = StructuralColors.onMedia.copy(alpha = 0.6f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FrGlassButton(
                label = resolve(CrewStringKey.SettingsBannerChange),
                onClick = { picker.launchGallery(allowMultiple = false, mimeTypes = listOf(MimeType.IMAGE_JPEG, MimeType.IMAGE_PNG)) },
                tone = FrButtonTone.Glass,
                leadingIcon = FrIcons.GalleryImport,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
            if (hasBanner) {
                FrGlassButton(
                    label = resolve(CrewStringKey.SettingsBannerRemove),
                    onClick = onRemove,
                    tone = FrButtonTone.Ghost,
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val BannerCropHeight = 180.dp

@Composable
private fun BannerRepositionPreview(
    imageUrl: String,
    focalY: Float,
    onReposition: (Float) -> Unit,
) {
    val ctx = LocalPlatformContext.current
    val heightPx = with(LocalDensity.current) { BannerCropHeight.toPx() }
    // Re-seed the draft whenever the persisted focal changes (e.g. after the write lands).
    var draftFocalY by remember(focalY) { mutableStateOf(focalY) }
    val cd = resolve(CrewStringKey.SettingsBannerPreviewCd)
    AsyncImage(
        model = ImageRequest.Builder(ctx).data(imageUrl).build(),
        contentDescription = cd,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(horizontalBias = 0f, verticalBias = draftFocalY * 2f - 1f),
        modifier = Modifier
            .fillMaxWidth()
            .height(BannerCropHeight)
            .clip(RoundedCornerShape(Radius.lg))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        // Drag down → reveal more of the top → focal toward 0.
                        draftFocalY = (draftFocalY - dragAmount / heightPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = { onReposition(draftFocalY) },
                )
            },
    )
}

/** Crimson banner for the trailing publish/save error. */
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
        FrIcon(image = FrIcons.Warning, contentDescription = null, tint = semantic.onDanger)
        FrText(text = text, color = semantic.onDanger, style = StructuralType.body)
    }
}

/** A brief, auto-dismissing bottom glass toast (replaces the matte snackbar). */
@Composable
private fun StructuralToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(2500)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(Spacing.lg),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FrGlassTile(depth = FrTileDepth.Near) {
            FrText(text = message, style = StructuralType.body, color = StructuralColors.foreground)
        }
    }
}

@Composable
private fun CrewSettingsSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Spacing.lg).padding(top = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        FrGlassTile(depth = FrTileDepth.Near, modifier = Modifier.fillMaxWidth().height(180.dp)) {}
        repeat(3) {
            FrGlassTile(depth = FrTileDepth.Deep, modifier = Modifier.fillMaxWidth().height(88.dp)) {}
        }
    }
}

/**
 * The structural QR invite dialog — a frosted [FrGlassDialog] card floating over the scrim. The
 * caption + close pill read on dark glass; the [FrQrCode] sits on a white rounded plate so it stays
 * dark-on-light and remains scannable against the dark stratum.
 */
@Composable
private fun InviteQrDialog(
    crewName: String,
    inviteUrl: String,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        FrGlassDialog {
            FrText(
                text = resolve(CrewStringKey.SettingsQrCaption, crewName),
                style = StructuralType.titleMd,
                color = StructuralColors.foreground,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(Color.White)
                    .padding(Spacing.md),
            ) {
                FrQrCode(
                    content = inviteUrl,
                    size = 200.dp,
                    foreground = MaterialTheme.colorScheme.scrim,
                    background = Color.White,
                )
            }
            FrGlassButton(
                label = resolve(CrewStringKey.SettingsQrClose),
                onClick = onDismiss,
                tone = FrButtonTone.Glass,
                fillWidth = true,
            )
        }
    }
}
