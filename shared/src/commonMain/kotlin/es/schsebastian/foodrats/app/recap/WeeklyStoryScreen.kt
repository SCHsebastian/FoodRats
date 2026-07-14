package es.schsebastian.foodrats.app.recap

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrStoryScaffold
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.theme.FoodRatsTheme
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.domain.analytics.DigestStorySource
import es.schsebastian.foodrats.core.i18n.ShareCardStringKey
import es.schsebastian.foodrats.core.i18n.resolve
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** How long each scene shows before the timer auto-advances. */
private const val SceneDurationMs = 5_000L
private const val TickMs = 50L

/**
 * The in-app weekly-recap story player (roadmap §2.4). Full-screen Instagram-Stories chrome
 * ([FrStoryScaffold]) over the assembled [RecapScene]s. The auto-advance CLOCK lives here (an
 * effect that animates the active segment's fill and dispatches [WeeklyStoryIntent.Advance] at
 * 100%); the ViewModel owns which scene is current and all analytics. Press-and-hold pauses the
 * clock; tap left/right steps; the close affordance or advancing past the last scene dismisses.
 *
 * [source] records whether the player was reached from the notification tap or an in-app entry —
 * passed to the ViewModel via Koin params for the `digest_story_opened` event.
 */
@Composable
fun WeeklyStoryScreen(
    onDismiss: () -> Unit,
    source: DigestStorySource,
    vm: WeeklyStoryViewModel = koinViewModel(parameters = { parametersOf(source) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(vm) {
        vm.effects.collect { effect ->
            when (effect) {
                WeeklyStoryEffect.Dismiss -> onDismiss()
            }
        }
    }

    // The weekly-recap player is an immersive Instagram-Stories surface — full-bleed dark media in
    // both themes (the scenes are dish/photo floors with white-on-scrim type). Force dark so light
    // mode doesn't flip the story chrome/type to dark-on-dark.
    FoodRatsTheme(darkTheme = true) {
    // On a Light-theme device the OS keeps dark status-bar icons by default, which are nearly
    // invisible on the dark recap background. Force white icons while this screen is in
    // composition and restore on exit.
    StatusBarIconsAppearance(lightIcons = true)
    Box(modifier = Modifier.fillMaxSize()) {
    when {
        state.isLoading -> LoadingOrEmpty(loading = true)
        state.failed || state.recap == null || state.recap!!.isEmpty -> {
            // A failed read or quiet week: show the gentle empty card; tapping anywhere dismisses.
            LoadingOrEmpty(loading = false, onTap = { vm.onIntent(WeeklyStoryIntent.Close) })
        }
        else -> StoryPlayer(state = state, vm = vm)
    }

        // Share-outcome toast (spec §10). Resolved here; auto-clears after a short window.
        state.shareOutcome?.let { outcome ->
            val message = resolve(
                when (outcome) {
                    ShareOutcomeUi.Succeeded   -> ShareCardStringKey.ShareSucceeded
                    ShareOutcomeUi.OpenedSheet -> ShareCardStringKey.ShareOpenedSheet
                    ShareOutcomeUi.Failed      -> ShareCardStringKey.ShareFailed
                },
            )
            ShareOutcomeToast(message = message, onDismiss = { vm.onIntent(WeeklyStoryIntent.DismissShareOutcome) })
        }
    }
    }
}

/**
 * The active story surface: the auto-advance progress clock, the share CTA, and [FrStoryScaffold]
 * itself. Split out of [WeeklyStoryScreen] so the 20Hz `progress` tick recomposes only this scope,
 * not the sibling `when` dispatch or share-outcome toast that also live in the screen's root [Box].
 */
@Composable
private fun StoryPlayer(state: WeeklyStoryState, vm: WeeklyStoryViewModel) {
    // Auto-advance progress for the active segment. Restarts whenever the scene index changes; halts
    // while paused or loading. At 100% it asks the VM to advance (the VM decides last-scene = finish).
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.currentIndex, state.isPaused, state.isLoading, state.recap) {
        progress = 0f
        if (state.isLoading || state.recap == null || state.isPaused) return@LaunchedEffect
        var elapsed = 0L
        while (isActive && elapsed < SceneDurationMs) {
            delay(TickMs)
            elapsed += TickMs
            progress = (elapsed.toFloat() / SceneDurationMs).coerceIn(0f, 1f)
        }
        if (isActive && progress >= 1f) {
            vm.onIntent(WeeklyStoryIntent.Advance)
        }
    }

    val scene = state.currentScene
    // The share CTA lives in the scaffold's overlay action slot — drawn ABOVE the tap zones
    // so a tap on it shares the scene instead of advancing the story. Only shown for the
    // shareable scenes (top-meal / streak / your-week); shows a spinner while rasterizing.
    val shareLabel = resolve(SharedStringKey.RecapShareCta)
    val action: (@Composable () -> Unit)? = if (state.canShareCurrentScene) {
        {
            if (state.isPreparingShare) {
                FrProgressIndicator(color = Color.White)
            } else {
                FrButton(
                    label = shareLabel,
                    onClick = { vm.onIntent(WeeklyStoryIntent.ShareScene) },
                )
            }
        }
    } else {
        null
    }
    FrStoryScaffold(
        segmentCount = state.sceneCount,
        currentIndex = state.currentIndex,
        currentProgress = progress,
        onPrev = { vm.onIntent(WeeklyStoryIntent.Back) },
        onNext = { vm.onIntent(WeeklyStoryIntent.Advance) },
        onClose = { vm.onIntent(WeeklyStoryIntent.Close) },
        onHoldStart = { vm.onIntent(WeeklyStoryIntent.Pause) },
        onHoldEnd = { vm.onIntent(WeeklyStoryIntent.Resume) },
        closeContentDescription = resolve(SharedStringKey.RecapClose),
        progressContentDescription = resolve(SharedStringKey.RecapProgress),
        action = action,
    ) {
        Crossfade(targetState = scene, label = "RecapScene") { current ->
            if (current != null) RecapSceneView(current, Modifier.fillMaxSize())
        }
    }
}

/**
 * Brief, auto-dismissing bottom toast for a share outcome (spec §10), mirroring the feed/stats share
 * toast. No system Toast primitive exists in the design system, so this is a small in-app overlay
 * built from `Fr*` atoms; it clears itself after a short window via [onDismiss].
 */
@Composable
private fun ShareOutcomeToast(message: String, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(2500)
        onDismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        contentAlignment = Alignment.BottomCenter,
    ) {
        FrCard {
            FrText(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LoadingOrEmpty(loading: Boolean, onTap: () -> Unit = {}) {
    // A tap anywhere on the empty/failed card dismisses (no scaffold gestures here).
    val tapModifier = if (loading) Modifier else Modifier.clickable(onClick = onTap)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .then(tapModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            FrProgressIndicator(color = Color.White)
        } else {
            Column(
                modifier = Modifier.padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FrText(
                    text = resolve(SharedStringKey.RecapEmptyTitle),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                FrText(
                    text = resolve(SharedStringKey.RecapEmptySubtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
