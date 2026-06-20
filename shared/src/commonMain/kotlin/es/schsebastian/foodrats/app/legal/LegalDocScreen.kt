package es.schsebastian.foodrats.app.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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

/** The i18n email address embedded in both legal-doc contact sections. */
private const val CONTACT_EMAIL = "hello@chsumiapps.com"

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
                // Contact sections: annotate the email so it opens the mail client on tap.
                if (section.body == SharedStringKey.LegalEulaBodyContact ||
                    section.body == SharedStringKey.LegalCommunityBodyContact
                ) {
                    LegalContactBody(text = resolve(section.body))
                } else {
                    FrText(
                        text = resolve(section.body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Renders a legal-doc contact paragraph with the embedded [CONTACT_EMAIL] as a tappable
 * `mailto:` link (Item 7 — UGC compliance §6). Uses [BasicText] with [LinkAnnotation.Url] via
 * [buildAnnotatedString]/[withLink] — the Compose Multiplatform 1.7+ declarative link API.
 * The link is underlined + primary-coloured so it reads as interactive; surrounding prose is
 * [MaterialTheme.colorScheme.onSurfaceVariant] matching the non-contact sections.
 */
@Composable
private fun LegalContactBody(text: String) {
    val emailIndex = text.indexOf(CONTACT_EMAIL)
    val linkColor = MaterialTheme.colorScheme.primary

    val annotated = buildAnnotatedString {
        if (emailIndex < 0) {
            // Fallback: email not found in the resolved string; render as plain text.
            append(text)
            return@buildAnnotatedString
        }
        append(text.substring(0, emailIndex))
        withLink(
            LinkAnnotation.Url(
                url = "mailto:$CONTACT_EMAIL",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) {
            append(CONTACT_EMAIL)
        }
        append(text.substring(emailIndex + CONTACT_EMAIL.length))
    }

    BasicText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.merge(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
