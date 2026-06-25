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

/** The i18n support web URL embedded in both legal-doc contact sections (UGC compliance §6). */
private const val CONTACT_URL = "https://foodrats-de4ec.web.app"

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
 * Renders a legal-doc contact paragraph with the embedded [CONTACT_EMAIL] (as a `mailto:` link)
 * and the [CONTACT_URL] (as a web link) both tappable (Item 7 — UGC compliance §6). Uses
 * [BasicText] with [LinkAnnotation.Url] via [buildAnnotatedString]/[withLink] — the Compose
 * Multiplatform 1.7+ declarative link API. Each link is underlined + primary-coloured so it reads
 * as interactive; surrounding prose is [MaterialTheme.colorScheme.onSurfaceVariant] matching the
 * non-contact sections. The two tokens are emitted in whatever positional order they appear so
 * the same renderer works for the EULA and Community contact bodies in both locales.
 */
@Composable
private fun LegalContactBody(text: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
        ),
    )

    // Each embedded link token paired with the URL it should open. The email opens via `mailto:`;
    // the support URL is already an absolute https URL. Sorted by position so the prose between
    // tokens is appended in reading order regardless of which comes first.
    data class LinkToken(val display: String, val url: String)
    val tokens = listOf(
        LinkToken(CONTACT_EMAIL, "mailto:$CONTACT_EMAIL"),
        LinkToken(CONTACT_URL, CONTACT_URL),
    )
        .map { it to text.indexOf(it.display) }
        .filter { (_, index) -> index >= 0 }
        .sortedBy { (_, index) -> index }

    val annotated = buildAnnotatedString {
        var cursor = 0
        tokens.forEach { (token, index) ->
            // Guard against overlapping/duplicate matches; skip any token already consumed.
            if (index < cursor) return@forEach
            append(text.substring(cursor, index))
            withLink(LinkAnnotation.Url(url = token.url, styles = linkStyles)) {
                append(token.display)
            }
            cursor = index + token.display.length
        }
        append(text.substring(cursor))
    }

    BasicText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.merge(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
