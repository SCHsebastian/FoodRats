package es.schsebastian.foodrats.app.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import es.schsebastian.foodrats.app.i18n.SharedStringKey
import es.schsebastian.foodrats.core.designsystem.atoms.FrIconButton
import es.schsebastian.foodrats.core.designsystem.atoms.FrIcons
import es.schsebastian.foodrats.core.designsystem.atoms.FrText
import es.schsebastian.foodrats.core.designsystem.layout.frContentWidth
import es.schsebastian.foodrats.core.designsystem.templates.FrScreenScaffold
import es.schsebastian.foodrats.core.designsystem.tokens.Spacing
import es.schsebastian.foodrats.core.i18n.resolve

/**
 * Which embedded legal document to render. A presentation enum (lives in `shared`, never leaks into a
 * feature) so the NavGraph can pick the doc by [Route] without a shared model crossing module lines.
 *
 * Each value owns its [title] plus an ordered list of [sections] (heading + body), all as
 * [SharedStringKey] entries — the full en/es text lives in the shared module's `composeResources`, so
 * there are no hardcoded strings. Adding a section = a new pair here + the two string keys.
 */
enum class LegalDoc(
    val title: SharedStringKey,
    val sections: List<LegalSection>,
) {
    EULA(
        title = SharedStringKey.LegalEulaTitle,
        sections = listOf(
            LegalSection(null, SharedStringKey.LegalEulaBodyIntro),
            LegalSection(SharedStringKey.LegalEulaHeadingScope, SharedStringKey.LegalEulaBodyScope),
            LegalSection(SharedStringKey.LegalEulaHeadingConsentData, SharedStringKey.LegalEulaBodyConsentData),
            LegalSection(SharedStringKey.LegalEulaHeadingAcceptableUse, SharedStringKey.LegalEulaBodyAcceptableUse),
            LegalSection(SharedStringKey.LegalEulaHeadingTermination, SharedStringKey.LegalEulaBodyTermination),
            LegalSection(SharedStringKey.LegalEulaHeadingWarranty, SharedStringKey.LegalEulaBodyWarranty),
            LegalSection(SharedStringKey.LegalEulaHeadingLiability, SharedStringKey.LegalEulaBodyLiability),
            LegalSection(SharedStringKey.LegalEulaHeadingContact, SharedStringKey.LegalEulaBodyContact),
        ),
    ),
    COMMUNITY_GUIDELINES(
        title = SharedStringKey.LegalCommunityTitle,
        sections = listOf(
            LegalSection(null, SharedStringKey.LegalCommunityBodyIntro),
            LegalSection(SharedStringKey.LegalCommunityHeadingRespect, SharedStringKey.LegalCommunityBodyRespect),
            LegalSection(SharedStringKey.LegalCommunityHeadingProhibited, SharedStringKey.LegalCommunityBodyProhibited),
            LegalSection(SharedStringKey.LegalCommunityHeadingReporting, SharedStringKey.LegalCommunityBodyReporting),
            LegalSection(SharedStringKey.LegalCommunityHeadingBlocking, SharedStringKey.LegalCommunityBodyBlocking),
            LegalSection(SharedStringKey.LegalCommunityHeadingEnforcement, SharedStringKey.LegalCommunityBodyEnforcement),
            LegalSection(SharedStringKey.LegalCommunityHeadingContact, SharedStringKey.LegalCommunityBodyContact),
        ),
    ),
}

/** One section of a legal document: an optional [heading] (the intro section has none) + a [body]. */
data class LegalSection(val heading: SharedStringKey?, val body: SharedStringKey)

/**
 * Reusable scrollable screen rendering a titled, sectioned legal document (the EULA or the Community
 * Guidelines) from i18n strings. Plain prose typography — no state, no ViewModel — mirroring
 * `ConsentScreen`. Reachable pre-auth from the SignIn links and from Profile (UGC compliance §6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocScreen(
    doc: LegalDoc,
    onBack: () -> Unit,
) {
    FrScreenScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    FrText(
                        text = resolve(doc.title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    FrIconButton(
                        icon = FrIcons.Back,
                        onClick = onBack,
                        contentDescription = resolve(SharedStringKey.LegalBackCta),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .frContentWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FrText(
                text = resolve(doc.title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            doc.sections.forEach { section ->
                section.heading?.let { headingKey ->
                    FrText(
                        text = resolve(headingKey),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                FrText(
                    text = resolve(section.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
