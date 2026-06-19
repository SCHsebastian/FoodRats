package es.schsebastian.foodrats.feature.notifications.presentation.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcon
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.theme.LocalFrSemanticColors
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Sizes
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.notifications.domain.model.NotificationPermission
import es.schsebastian.foodrats.feature.notifications.i18n.NotificationStringKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationPermissionScreen(
    onContinue: () -> Unit,
    vm: NotificationPermissionViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) { NotificationPermissionEffect.Continue -> onContinue() }
        }
    }
    FrScreenScaffold {
        val semantic = LocalFrSemanticColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .frContentWidth(Breakpoints.formMax)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Single-focus prompt: the bell medallion "develops in" as the focal point, then the
            // copy and actions follow with a gentle top-down cascade.
            Box(
                modifier = Modifier
                    .size(Sizes.avatarLg)
                    .clip(CircleShape)
                    .background(semantic.info)
                    .frRevealScale(),
                contentAlignment = Alignment.Center,
            ) {
                FrIcon(
                    image = FrIcons.Notifications,
                    tint = semantic.onInfo,
                    modifier = Modifier.size(Sizes.iconLg),
                )
            }
            FrText(
                text = resolve(NotificationStringKey.PermissionTitle),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.lg).frRiseIn(delayMillis = 80),
            )
            FrText(
                text = resolve(NotificationStringKey.PermissionBody),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.md).frRiseIn(delayMillis = 140),
            )
            if (state.current == NotificationPermission.DeniedForever) {
                FrButton(
                    label = resolve(NotificationStringKey.PermissionSettings),
                    onClick = { vm.onIntent(NotificationPermissionIntent.OpenSettings) },
                    variant = FrButtonVariant.Primary,
                    modifier = Modifier.padding(top = Spacing.lg).frRiseIn(delayMillis = 200),
                )
            } else if (state.isRequesting) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    FrProgressIndicator()
                }
            } else {
                FrButton(
                    label = resolve(NotificationStringKey.PermissionAllow),
                    onClick = { vm.onIntent(NotificationPermissionIntent.Request) },
                    variant = FrButtonVariant.Primary,
                    enabled = !state.isRequesting,
                    modifier = Modifier.padding(top = Spacing.lg).frRiseIn(delayMillis = 200),
                )
            }
            FrButton(
                label = resolve(NotificationStringKey.PermissionSkip),
                onClick = { vm.onIntent(NotificationPermissionIntent.Skip) },
                variant = FrButtonVariant.Ghost,
                modifier = Modifier.padding(top = Spacing.sm).frRiseIn(delayMillis = 260),
            )
            state.error?.let { err ->
                FrErrorBanner(
                    text = resolve(err),
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}
