package es.schsebastian.foodrats.app.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.shared.generated.resources.Res
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
import foodrats.shared.generated.resources.recap_cuisines_subtitle
import foodrats.shared.generated.resources.recap_cuisines_title
import foodrats.shared.generated.resources.recap_empty_subtitle
import foodrats.shared.generated.resources.recap_empty_title
import foodrats.shared.generated.resources.recap_entry_cta
import foodrats.shared.generated.resources.recap_most_prolific_subtitle
import foodrats.shared.generated.resources.recap_most_prolific_title
import foodrats.shared.generated.resources.recap_progress
import foodrats.shared.generated.resources.recap_share_cta
import foodrats.shared.generated.resources.recap_streak_subtitle
import foodrats.shared.generated.resources.recap_streak_title
import foodrats.shared.generated.resources.recap_top_meal_author
import foodrats.shared.generated.resources.recap_top_meal_score
import foodrats.shared.generated.resources.recap_top_meal_title
import foodrats.shared.generated.resources.recap_your_week_cuisines
import foodrats.shared.generated.resources.recap_your_week_ingredients
import foodrats.shared.generated.resources.recap_your_week_streak
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
    RecapStreakSubtitle(Res.string.recap_streak_subtitle),
    RecapBadgesTitle(Res.string.recap_badges_title),
    RecapCuisinesTitle(Res.string.recap_cuisines_title),
    RecapCuisinesSubtitle(Res.string.recap_cuisines_subtitle),
    RecapYourWeekTitle(Res.string.recap_your_week_title),
    RecapYourWeekStreak(Res.string.recap_your_week_streak),
    RecapYourWeekCuisines(Res.string.recap_your_week_cuisines),
    RecapYourWeekIngredients(Res.string.recap_your_week_ingredients),
    RecapEmptyTitle(Res.string.recap_empty_title),
    RecapEmptySubtitle(Res.string.recap_empty_subtitle),
    RecapShareCta(Res.string.recap_share_cta),
}
