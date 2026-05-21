package es.schsebastian.foodrats.feature.meal.presentation.compose

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.molecules.FrLabeledTextField
import es.schsebastian.foodrats.core.designsystem.templates.FrFormLayout
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
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

@Composable
fun ComposePlateScreen(onComposed: () -> Unit, vm: ComposePlateViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today = remember { MealDay.today(SystemClock(), TimeZone.currentSystemDefault()) }
    val emote = remember(today) { DailyEmote.forDay(today) }
    val coordinatesLabel = state.coordinates?.let { coords ->
        resolve(MealStringKey.ComposeCoordinatesFormat, coords.latitude, coords.longitude)
    }
    LaunchedEffect(Unit) {
        vm.effects.collect { if (it is ComposePlateEffect.NavigateToPublish) onComposed() }
    }
    FrScreenScaffold {
        FrFormLayout {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                StaggeredEntry(delay = 0) {
                    DailyEmoteBadge(emote = emote, modifier = Modifier.padding(bottom = Spacing.sm))
                }
                StaggeredEntry(delay = 80) {
                    state.photoBytes?.let { bytes ->
                        val img = remember(bytes) { decodeImageBitmap(bytes) }
                        if (img != null) {
                            val pulse = remember { androidx.compose.animation.core.Animatable(0.96f) }
                            LaunchedEffect(bytes) { pulse.animateTo(1f, tween(320)) }
                            Image(
                                bitmap = img,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .scale(pulse.value)
                                    .clip(RoundedCornerShape(Spacing.sm))
                                    .padding(bottom = Spacing.md),
                            )
                        }
                    }
                }
                StaggeredEntry(delay = 160) {
                    SlotPicker(
                        selected = state.selectedSlot,
                        taken = state.takenSlots,
                        onSelect = { slot -> vm.onIntent(ComposePlateIntent.SelectSlot(slot)) },
                    )
                }
                StaggeredEntry(delay = 220) {
                    FrLabeledTextField(
                        label = resolve(MealStringKey.ComposeTitle),
                        value = state.dish,
                        onValueChange = { vm.onIntent(ComposePlateIntent.DishChanged(it)) },
                        isError = state.error is MealError.Validation &&
                            state.error !is MealError.Validation.DescriptionTooLong,
                    )
                }
                StaggeredEntry(delay = 280) {
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
                StaggeredEntry(delay = 340) {
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
                    enabled = state.dish.isNotBlank() && !state.descriptionTooLong,
                    onClick = { vm.onIntent(ComposePlateIntent.Continue) },
                )
            }
        }
    }
}

@Composable
private fun StaggeredEntry(delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(durationMillis = 320, delayMillis = delay)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 320, delayMillis = delay),
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
    } else remember { androidx.compose.runtime.mutableStateOf(1f) }
    FrButton(
        label = resolve(CommonStringKey.Continue),
        onClick = onClick,
        variant = FrButtonVariant.Primary,
        enabled = enabled,
        modifier = Modifier
            .padding(top = Spacing.md, bottom = Spacing.lg)
            .scale(baseScale * breath),
    )
}
