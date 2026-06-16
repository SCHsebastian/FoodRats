package es.schsebastian.foodrats.feature.crew.presentation.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.schsebastian.foodrats.core.designsystem.atoms.FrButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrButtonVariant
import es.schsebastian.foodrats.core.designsystem.atoms.FrCard
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrProgressIndicator
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.molecules.FrErrorBanner
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.i18n.resolve
import es.schsebastian.foodrats.feature.crew.i18n.CrewStringKey
import es.schsebastian.foodrats.feature.crew.presentation.toStringKey
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Accept-an-invite preview (roadmap §3.2). Reached from a `…/invite/{code}` deep link / QR scan via
 * [Route.InvitePreview]. Resolves and previews the crew (name + member count) and joins on accept
 * through the existing join-by-code path; [onJoined] lands the user on the crew's Main feed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteScreen(
    code: String,
    onJoined: (CrewId) -> Unit,
    onBack: () -> Unit,
    vm: AcceptInviteViewModel = koinViewModel(parameters = { parametersOf(code) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.effects.collect { eff ->
            when (eff) {
                is AcceptInviteEffect.Joined -> onJoined(eff.crewId)
            }
        }
    }

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
        when {
            crew == null && state.isResolving -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { FrProgressIndicator() }

            crew == null -> Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
            }

            else -> Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            ) {
                FrText(
                    text = resolve(CrewStringKey.InviteSubtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FrCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        FrText(text = crew.name, style = MaterialTheme.typography.headlineSmall)
                        FrText(
                            text = resolve(CrewStringKey.SettingsMembersCount, crew.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.error?.let { FrErrorBanner(text = resolve(it.toStringKey())) }
                FrButton(
                    label = resolve(CrewStringKey.InviteJoinCta),
                    onClick = { vm.onIntent(AcceptInviteIntent.Join) },
                    enabled = !state.isJoining,
                    modifier = Modifier.fillMaxWidth(),
                )
                FrButton(
                    label = resolve(CrewStringKey.InviteDeclineCta),
                    onClick = onBack,
                    variant = FrButtonVariant.Secondary,
                    enabled = !state.isJoining,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
