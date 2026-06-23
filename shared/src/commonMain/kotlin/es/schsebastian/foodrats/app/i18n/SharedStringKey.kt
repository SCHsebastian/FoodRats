package es.schsebastian.foodrats.app.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.shared.generated.resources.Res
import foodrats.shared.generated.resources.legal_back_cta
import foodrats.shared.generated.resources.legal_eula_gate_title
import foodrats.shared.generated.resources.legal_eula_gate_body
import foodrats.shared.generated.resources.legal_eula_accept_cta
import foodrats.shared.generated.resources.legal_eula_gate_read_eula_cta
import foodrats.shared.generated.resources.legal_eula_gate_read_guidelines_cta
import foodrats.shared.generated.resources.legal_community_body_blocking
import foodrats.shared.generated.resources.legal_community_body_contact
import foodrats.shared.generated.resources.legal_community_body_enforcement
import foodrats.shared.generated.resources.legal_community_body_intro
import foodrats.shared.generated.resources.legal_community_body_prohibited
import foodrats.shared.generated.resources.legal_community_body_reporting
import foodrats.shared.generated.resources.legal_community_body_respect
import foodrats.shared.generated.resources.legal_community_heading_blocking
import foodrats.shared.generated.resources.legal_community_heading_contact
import foodrats.shared.generated.resources.legal_community_heading_enforcement
import foodrats.shared.generated.resources.legal_community_heading_prohibited
import foodrats.shared.generated.resources.legal_community_heading_reporting
import foodrats.shared.generated.resources.legal_community_heading_respect
import foodrats.shared.generated.resources.legal_community_title
import foodrats.shared.generated.resources.legal_eula_body_acceptable_use
import foodrats.shared.generated.resources.legal_eula_body_consent_data
import foodrats.shared.generated.resources.legal_eula_body_contact
import foodrats.shared.generated.resources.legal_eula_body_intro
import foodrats.shared.generated.resources.legal_eula_body_liability
import foodrats.shared.generated.resources.legal_eula_body_scope
import foodrats.shared.generated.resources.legal_eula_body_termination
import foodrats.shared.generated.resources.legal_eula_body_warranty
import foodrats.shared.generated.resources.legal_eula_heading_acceptable_use
import foodrats.shared.generated.resources.legal_eula_heading_consent_data
import foodrats.shared.generated.resources.legal_eula_heading_contact
import foodrats.shared.generated.resources.legal_eula_heading_liability
import foodrats.shared.generated.resources.legal_eula_heading_scope
import foodrats.shared.generated.resources.legal_eula_heading_termination
import foodrats.shared.generated.resources.legal_eula_heading_warranty
import foodrats.shared.generated.resources.legal_eula_title
import foodrats.shared.generated.resources.consent_allow
import foodrats.shared.generated.resources.consent_body
import foodrats.shared.generated.resources.consent_deny
import foodrats.shared.generated.resources.consent_privacy_note
import foodrats.shared.generated.resources.consent_title
import foodrats.shared.generated.resources.nav_capture_cta
import foodrats.shared.generated.resources.offline_banner
import foodrats.shared.generated.resources.nav_profile_cta
import foodrats.shared.generated.resources.nav_settings_cta
import foodrats.shared.generated.resources.nav_tab_feed
import foodrats.shared.generated.resources.nav_tab_passport
import foodrats.shared.generated.resources.nav_tab_stats
import foodrats.shared.generated.resources.recap_badges_title
import foodrats.shared.generated.resources.recap_best_cook_subtitle
import foodrats.shared.generated.resources.recap_best_cook_title
import foodrats.shared.generated.resources.recap_close
import foodrats.shared.generated.resources.recap_cover_subtitle
import foodrats.shared.generated.resources.recap_cover_title
import foodrats.shared.generated.resources.recap_cuisines_ratio
import foodrats.shared.generated.resources.recap_cuisines_subtitle
import foodrats.shared.generated.resources.recap_cuisines_title
import foodrats.shared.generated.resources.recap_rating_unit
import foodrats.shared.generated.resources.recap_empty_subtitle
import foodrats.shared.generated.resources.recap_empty_title
import foodrats.shared.generated.resources.recap_entry_cta
import foodrats.shared.generated.resources.recap_most_prolific_subtitle
import foodrats.shared.generated.resources.recap_most_prolific_title
import foodrats.shared.generated.resources.recap_progress
import foodrats.shared.generated.resources.recap_share_cta
import foodrats.shared.generated.resources.recap_streak_title
import foodrats.shared.generated.resources.recap_top_meal_author
import foodrats.shared.generated.resources.recap_top_meal_score
import foodrats.shared.generated.resources.recap_top_meal_title
import foodrats.shared.generated.resources.recap_your_week_cuisines
import foodrats.shared.generated.resources.recap_your_week_ingredients
import foodrats.shared.generated.resources.recap_your_week_title
import org.jetbrains.compose.resources.StringResource

enum class SharedStringKey(override val resourceId: StringResource) : StringKey {
    NavCaptureCta(Res.string.nav_capture_cta),
    NavSettingsCta(Res.string.nav_settings_cta),
    NavTabFeed(Res.string.nav_tab_feed),
    NavTabPassport(Res.string.nav_tab_passport),
    NavTabStats(Res.string.nav_tab_stats),
    NavProfileCta(Res.string.nav_profile_cta),
    ConsentTitle(Res.string.consent_title),
    ConsentBody(Res.string.consent_body),
    ConsentPrivacyNote(Res.string.consent_privacy_note),
    ConsentAllow(Res.string.consent_allow),
    ConsentDeny(Res.string.consent_deny),

    // Embedded legal docs (UGC compliance §6) — surfaced at the login screen + Profile.
    LegalBackCta(Res.string.legal_back_cta),
    // EULA gate re-acceptance screen (UGC compliance §6 — shown when CURRENT_EULA_VERSION > accepted).
    LegalEulaGateTitle(Res.string.legal_eula_gate_title),
    LegalEulaGateBody(Res.string.legal_eula_gate_body),
    LegalEulaAcceptCta(Res.string.legal_eula_accept_cta),
    // Link buttons to the full legal docs shown on the gate (Apple G1.2 — must be readable at acceptance).
    LegalEulaGateReadEulaCta(Res.string.legal_eula_gate_read_eula_cta),
    LegalEulaGateReadGuidelinesCta(Res.string.legal_eula_gate_read_guidelines_cta),

    // End User License Agreement (adapted from Apple's standard Licensed Application EULA).
    LegalEulaTitle(Res.string.legal_eula_title),
    LegalEulaBodyIntro(Res.string.legal_eula_body_intro),
    LegalEulaHeadingScope(Res.string.legal_eula_heading_scope),
    LegalEulaBodyScope(Res.string.legal_eula_body_scope),
    LegalEulaHeadingConsentData(Res.string.legal_eula_heading_consent_data),
    LegalEulaBodyConsentData(Res.string.legal_eula_body_consent_data),
    LegalEulaHeadingAcceptableUse(Res.string.legal_eula_heading_acceptable_use),
    LegalEulaBodyAcceptableUse(Res.string.legal_eula_body_acceptable_use),
    LegalEulaHeadingTermination(Res.string.legal_eula_heading_termination),
    LegalEulaBodyTermination(Res.string.legal_eula_body_termination),
    LegalEulaHeadingWarranty(Res.string.legal_eula_heading_warranty),
    LegalEulaBodyWarranty(Res.string.legal_eula_body_warranty),
    LegalEulaHeadingLiability(Res.string.legal_eula_heading_liability),
    LegalEulaBodyLiability(Res.string.legal_eula_body_liability),
    LegalEulaHeadingContact(Res.string.legal_eula_heading_contact),
    LegalEulaBodyContact(Res.string.legal_eula_body_contact),

    // Community Guidelines.
    LegalCommunityTitle(Res.string.legal_community_title),
    LegalCommunityBodyIntro(Res.string.legal_community_body_intro),
    LegalCommunityHeadingRespect(Res.string.legal_community_heading_respect),
    LegalCommunityBodyRespect(Res.string.legal_community_body_respect),
    LegalCommunityHeadingProhibited(Res.string.legal_community_heading_prohibited),
    LegalCommunityBodyProhibited(Res.string.legal_community_body_prohibited),
    LegalCommunityHeadingReporting(Res.string.legal_community_heading_reporting),
    LegalCommunityBodyReporting(Res.string.legal_community_body_reporting),
    LegalCommunityHeadingBlocking(Res.string.legal_community_heading_blocking),
    LegalCommunityBodyBlocking(Res.string.legal_community_body_blocking),
    LegalCommunityHeadingEnforcement(Res.string.legal_community_heading_enforcement),
    LegalCommunityBodyEnforcement(Res.string.legal_community_body_enforcement),
    LegalCommunityHeadingContact(Res.string.legal_community_heading_contact),
    LegalCommunityBodyContact(Res.string.legal_community_body_contact),

    // App-wide offline banner (offline-first §P1-T2)
    OfflineBanner(Res.string.offline_banner),

    // Weekly recap story (roadmap §2.4)
    RecapEntryCta(Res.string.recap_entry_cta),
    RecapClose(Res.string.recap_close),
    RecapProgress(Res.string.recap_progress),
    RecapCoverTitle(Res.string.recap_cover_title),
    RecapCoverSubtitle(Res.string.recap_cover_subtitle),
    RecapTopMealTitle(Res.string.recap_top_meal_title),
    RecapTopMealAuthor(Res.string.recap_top_meal_author),
    RecapTopMealScore(Res.string.recap_top_meal_score),
    RecapBestCookTitle(Res.string.recap_best_cook_title),
    RecapBestCookSubtitle(Res.string.recap_best_cook_subtitle),
    RecapMostProlificTitle(Res.string.recap_most_prolific_title),
    RecapMostProlificSubtitle(Res.string.recap_most_prolific_subtitle),
    RecapStreakTitle(Res.string.recap_streak_title),
    RecapBadgesTitle(Res.string.recap_badges_title),
    RecapCuisinesTitle(Res.string.recap_cuisines_title),
    RecapCuisinesSubtitle(Res.string.recap_cuisines_subtitle),
    RecapCuisinesRatio(Res.string.recap_cuisines_ratio),
    RecapRatingUnit(Res.string.recap_rating_unit),
    RecapYourWeekTitle(Res.string.recap_your_week_title),
    RecapYourWeekCuisines(Res.string.recap_your_week_cuisines),
    RecapYourWeekIngredients(Res.string.recap_your_week_ingredients),
    RecapEmptyTitle(Res.string.recap_empty_title),
    RecapEmptySubtitle(Res.string.recap_empty_subtitle),
    RecapShareCta(Res.string.recap_share_cta),
}
