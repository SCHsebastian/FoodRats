package es.schsebastian.foodrats.feature.crew.presentation.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrShimmerBox
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.motion.frRevealScale
import es.schsebastian.foodrats.core.designsystem.motion.frRiseIn
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import es.schsebastian.foodrats.core.designsystem.tokens.Breakpoints
import es.schsebastian.foodrats.core.designsystem.tokens.Radius
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Accept-an-invite preview (roadmap §3.2). Reached from a `…/invite/{code}` deep link / QR scan via
 * [Route.InvitePreview]. Resolves and previews the crew (name + member count). Accepting FILES A
 * JOIN REQUEST — there is no instant join — so on success the screen shows a "waiting for the owner's
 * approval" confirmation rather than navigating into a crew the user isn't a member of yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteScreen(
    code: String,
    onBack: () -> Unit,
    vm: AcceptInviteViewModel = koinViewModel(parameters = { parametersOf(code) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { FrText(text = resolve(CrewStringKey.InviteEyebrow)) },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(CrewStringKey.InviteBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) {
        val crew = state.crew
        val error = state.error
        when {
            crew == null && state.isResolving -> InvitePreviewSkeleton()

            crew == null -> Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
            }

            state.requestSent -> Column(
                modifier = Modifier.fillMaxHeight()
                    .frContentWidth(Breakpoints.formMax)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            ) {
                FrCard(modifier = Modifier.fillMaxWidth().frRevealScale()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FrText(
                            text = resolve(CrewStringKey.InviteRequestSentTitle),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        FrText(
                            text = resolve(CrewStringKey.InviteRequestSentBody, crew.name),
                            style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                FrButton(
                    label = resolve(CrewStringKey.InviteDoneCta),
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 90),
                )
                // Requester-side cancel: withdraw the pending request and return to the join CTA.
                FrButton(
                    label = resolve(CrewStringKey.InviteCancelRequestCta),
                    onClick = { vm.onIntent(AcceptInviteIntent.Cancel) },
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isCancelling,
                    modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 130),
                )
            }

            else -> Column(
                modifier = Modifier.fillMaxHeight()
                    .frContentWidth(Breakpoints.formMax)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            ) {
                // "You've been invited to join" eyebrow rises in first, framing the card below it.
                FrText(
                    text = resolve(CrewStringKey.InviteSubtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.frRiseIn(),
                )
                // Signature: the crew preview "develops in" as the focal element of the screen.
                FrCard(modifier = Modifier.fillMaxWidth().frRevealScale()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FrText(text = crew.name, style = MaterialTheme.typography.headlineMedium)
                        FrText(
                            text = resolve(CrewStringKey.SettingsMembersCount, crew.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                // Request-to-join / Decline rise in just behind the card so the choice arrives last.
                FrButton(
                    label = resolve(CrewStringKey.InviteJoinCta),
                    onClick = { vm.onIntent(AcceptInviteIntent.Join) },
                    enabled = !state.isJoining,
                    modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 90),
                )
                FrButton(
                    label = resolve(CrewStringKey.InviteDeclineCta),
                    onClick = onBack,
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isJoining,
                    modifier = Modifier.fillMaxWidth().frRiseIn(delayMillis = 130),
                )
            }
        }
    }
}

/**
 * Decorative resolving-state placeholder mimicking the invite-preview card: a card-shaped block atop
 * two short bars standing in for the crew name and member count. Capped to the same form width as the
 * resolved state so the layout doesn't jump when the crew arrives.
 */
@Composable
private fun InvitePreviewSkeleton() {
    Column(
        modifier = Modifier.fillMaxHeight()
            .frContentWidth(Breakpoints.formMax)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            shape = RoundedCornerShape(Radius.lg),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth(fraction = 0.6f).height(Spacing.lg),
            shape = RoundedCornerShape(Radius.sm),
        )
        FrShimmerBox(
            modifier = Modifier.fillMaxWidth(fraction = 0.4f).height(Spacing.md),
            shape = RoundedCornerShape(Radius.sm),
        )
    }
}
