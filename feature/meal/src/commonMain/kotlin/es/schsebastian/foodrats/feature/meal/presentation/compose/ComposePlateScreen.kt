package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.atoms.FrTextField
import es.schsebastian.foodrats.core.designsystem.molecules.FrComposerHeroCard
import es.schsebastian.foodrats.core.designsystem.molecules.FrConfirmDialog
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Motion
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.meal.DailyEmote
import es.schsebastian.foodrats.core.domain.meal.Description
import es.schsebastian.foodrats.core.domain.meal.MealDay
import es.schsebastian.foodrats.core.domain.time.SystemClock
import es.schsebastian.foodrats.core.i18n.CommonStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.meal.domain.error.MealError
import es.schsebastian.foodrats.feature.meal.i18n.MealStringKey
import es.schsebastian.foodrats.feature.meal.presentation.components.DailyEmoteBadge
import es.schsebastian.foodrats.feature.meal.presentation.components.LocationPickerRow
import es.schsebastian.foodrats.feature.meal.presentation.components.SlotPicker
import es.schsebastian.foodrats.feature.meal.presentation.components.decodeImageBitmap
import es.schsebastian.foodrats.feature.meal.presentation.toStringKey
import kotlinx.datetime.TimeZone
import org.koin.compose.viewmodel.koinViewModel

/**
 * Compose-plate screen: pick a slot, name the dish, add a description and an
 * optional location pin. The "Continue" button opens a confirmation dialog
 * ([FrConfirmDialog]); on confirm the upload is enqueued through
 * `BackgroundMealUploadCoordinator` (fire-and-forget) and the user is sent
 * back to the feed — they never see a "review" screen.
 */
@Composable
fun ComposePlateScreen(onPublishStarted: () -> Unit, vm: ComposePlateViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { MealDay.today(SystemClock(), TimeZone.currentSystemDefault()) }
    val emote = remember(today) { DailyEmote.forDay(today) }
    val coordinatesLabel = state.coordinates?.let { coords ->
        resolve(MealStringKey.ComposeCoordinatesFormat, coords.latitude, coords.longitude)
    }

    LaunchedEffect(Unit) {
        vm.effects.collect {
            if (it is ComposePlateEffect.UploadEnqueued) onPublishStarted()
        }
    }

    FrScreenScaffold {
        FrFormLayout {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                AnimatedFormItem(delay = 0) {
                    DailyEmoteBadge(emote = emote, modifier = Modifier.padding(bottom = Spacing.sm))
                }

                AnimatedFormItem(delay = Motion.quick) {
                    FrComposerHeroCard(
                        contentKey = state.photoBytes?.size,
                        modifier = Modifier.padding(bottom = Spacing.md),
                    ) {
                        val bytes = state.photoBytes
                        if (bytes != null) {
                            val img = remember(bytes) { decodeImageBitmap(bytes) }
                            if (img != null) {
                                val pulse = remember { Animatable(0.96f) }
                                LaunchedEffect(bytes) { pulse.animateTo(1f, tween(Motion.medium)) }
                                Image(
                                    bitmap = img,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(pulse.value)
                                        .clip(RoundedCornerShape(Radius.md)),
                                )
                            }
                        }
                    }
                }

                AnimatedFormItem(delay = Motion.short) {
                    SlotPicker(
                        selected = state.selectedSlot,
                        taken = state.takenSlots,
                        onSelect = { slot -> vm.onIntent(ComposePlateIntent.SelectSlot(slot)) },
                    )
                }

                AnimatedFormItem(delay = Motion.short + Motion.quick) {
                    FrLabeledTextField(
                        label = resolve(MealStringKey.ComposeTitle),
                        value = state.dish,
                        onValueChange = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
                        isError = state.error is MealError.Validation &&
                            state.error !is MealError.Validation.DescriptionTooLong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AnimatedFormItem(delay = Motion.medium) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FrTextField(
                            value = state.descriptionInput,
                            onValueChange = { vm.onIntent(ComposePlateIntent.DescriptionChanged(it)) },
                            label = resolve(MealStringKey.ComposeDescriptionPlaceholder),
                            isError = state.descriptionTooLong,
                            singleLine = false,
                            modifier = Modifier.padding(top = Spacing.md).fillMaxWidth(),
                        )
                        FrText(
                            text = resolve(
                                MealStringKey.ComposeDescriptionCounter,
                                state.descriptionInput.trim().length,
                                Description.MAX_LEN,
                            ),
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }

                AnimatedFormItem(delay = Motion.medium + Motion.quick) {
                    LocationPickerRow(
                        idleLabel = resolve(MealStringKey.ComposeAddLocation),
                        locatingLabel = resolve(MealStringKey.ComposeLocating),
                        clearLabel = resolve(MealStringKey.ComposeClearLocation),
                        coordinatesLabel = coordinatesLabel,
                        locating = state.locating,
                        onRequest = { vm.onIntent(ComposePlateIntent.RequestLocation) },
                        onClear = { vm.onIntent(ComposePlateIntent.ClearLocation) },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }

                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                ) {
                    state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                }

                AnimatedContinueButton(
                    enabled = state.canContinue,
                    onClick = { vm.onIntent(ComposePlateIntent.RequestConfirm) },
                )
            }
        }
    }

    if (state.showConfirm) {
        FrConfirmDialog(
            title = resolve(MealStringKey.PublishConfirmTitle),
            message = resolve(MealStringKey.PublishConfirmMessage),
            confirmLabel = resolve(MealStringKey.PublishConfirmCta),
            dismissLabel = resolve(CommonStringKey.Cancel),
            onConfirm = { vm.onIntent(ComposePlateIntent.ConfirmPublish) },
            onDismiss = { vm.onIntent(ComposePlateIntent.DismissConfirm) },
        )
    }
}

/**
 * Lightweight staggered entry: each form item fades + slides in with its own
 * [delay] (ms) so the screen feels assembled rather than dumped.
 */
@Composable
private fun AnimatedFormItem(
    delay: Int,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = Motion.medium,
                delayMillis = delay,
                easing = Motion.Decelerated,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = Motion.medium,
                delayMillis = delay,
                easing = Motion.Decelerated,
            ),
            initialOffsetY = { it / 4 },
        ),
        exit = fadeOut(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * Primary CTA that subtly breathes once enabled, so the user sees the form
 * cross the validity threshold.
 */
@Composable
private fun AnimatedContinueButton(enabled: Boolean, onClick: () -> Unit) {
    val targetScale = if (enabled) 1f else 0.97f
    val baseScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 220),
        label = "continueScale",
    )
    val breath by if (enabled) {
        val transition = rememberInfiniteTransition(label = "continueBreath")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breath",
        )
    } else remember { mutableStateOf(1f) }
    FrButton(
        label = resolve(CommonStringKey.Continue),
        onClick = onClick,
        variant = FrButtonVariant.Primary,
        enabled = enabled,
        modifier = Modifier
            .padding(top = Spacing.md, bottom = Spacing.lg)
            .fillMaxWidth()
            .scale(baseScale * breath),
    )
}
