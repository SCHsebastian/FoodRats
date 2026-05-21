package es.schsebastian.foodrats.feature.stats.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.stats.generated.resources.Res
import foodrats.feature.stats.generated.resources.stats_best_cook_metric_format
import foodrats.feature.stats.generated.resources.stats_best_cook_title
import foodrats.feature.stats.generated.resources.stats_best_plate_author_format
import foodrats.feature.stats.generated.resources.stats_best_plate_score_format
import foodrats.feature.stats.generated.resources.stats_best_plate_title
import foodrats.feature.stats.generated.resources.stats_cook_award_needs_more_plural
import foodrats.feature.stats.generated.resources.stats_cook_award_needs_more_singular
import foodrats.feature.stats.generated.resources.stats_cooks_section_title
import foodrats.feature.stats.generated.resources.stats_empty_headline
import foodrats.feature.stats.generated.resources.stats_empty_subtext
import foodrats.feature.stats.generated.resources.stats_error_crew_not_found
import foodrats.feature.stats.generated.resources.stats_error_no_active_crew
import foodrats.feature.stats.generated.resources.stats_error_not_signed_in
import foodrats.feature.stats.generated.resources.stats_error_unauthorized
import foodrats.feature.stats.generated.resources.stats_error_unavailable
import foodrats.feature.stats.generated.resources.stats_hero_crew_streak_plural
import foodrats.feature.stats.generated.resources.stats_hero_crew_streak_singular
import foodrats.feature.stats.generated.resources.stats_hero_i_posted_today
import foodrats.feature.stats.generated.resources.stats_hero_no_streak
import foodrats.feature.stats.generated.resources.stats_hero_personal_streak_plural
import foodrats.feature.stats.generated.resources.stats_hero_personal_streak_singular
import foodrats.feature.stats.generated.resources.stats_hero_plates_today_plural
import foodrats.feature.stats.generated.resources.stats_hero_plates_today_singular
import foodrats.feature.stats.generated.resources.stats_most_criticized_metric_format
import foodrats.feature.stats.generated.resources.stats_most_criticized_title
import foodrats.feature.stats.generated.resources.stats_most_prolific_metric_format
import foodrats.feature.stats.generated.resources.stats_most_prolific_title
import foodrats.feature.stats.generated.resources.stats_most_voted_plate_title
import foodrats.feature.stats.generated.resources.stats_most_voted_plate_voters_plural
import foodrats.feature.stats.generated.resources.stats_most_voted_plate_voters_singular
import foodrats.feature.stats.generated.resources.stats_roast_section_title
import foodrats.feature.stats.generated.resources.stats_summary_avg_per_day_label
import foodrats.feature.stats.generated.resources.stats_summary_total_plates_label
import foodrats.feature.stats.generated.resources.stats_tab_historic
import foodrats.feature.stats.generated.resources.stats_tab_month
import foodrats.feature.stats.generated.resources.stats_tab_week
import foodrats.feature.stats.generated.resources.stats_title
import foodrats.feature.stats.generated.resources.stats_window_empty_historic
import foodrats.feature.stats.generated.resources.stats_window_empty_month
import foodrats.feature.stats.generated.resources.stats_window_empty_week
import org.jetbrains.compose.resources.StringResource

enum class StatsStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.stats_title),

    TabWeek(Res.string.stats_tab_week),
    TabMonth(Res.string.stats_tab_month),
    TabHistoric(Res.string.stats_tab_historic),

    HeroPersonalStreakSingular(Res.string.stats_hero_personal_streak_singular),
    HeroPersonalStreakPlural(Res.string.stats_hero_personal_streak_plural),
    HeroCrewStreakSingular(Res.string.stats_hero_crew_streak_singular),
    HeroCrewStreakPlural(Res.string.stats_hero_crew_streak_plural),
    HeroPlatesTodaySingular(Res.string.stats_hero_plates_today_singular),
    HeroPlatesTodayPlural(Res.string.stats_hero_plates_today_plural),
    HeroIPostedToday(Res.string.stats_hero_i_posted_today),
    HeroNoStreak(Res.string.stats_hero_no_streak),

    WindowEmptyWeek(Res.string.stats_window_empty_week),
    WindowEmptyMonth(Res.string.stats_window_empty_month),
    WindowEmptyHistoric(Res.string.stats_window_empty_historic),

    SummaryTotalPlatesLabel(Res.string.stats_summary_total_plates_label),
    SummaryAvgPerDayLabel(Res.string.stats_summary_avg_per_day_label),

    BestPlateTitle(Res.string.stats_best_plate_title),
    BestPlateScoreFormat(Res.string.stats_best_plate_score_format),
    BestPlateAuthorFormat(Res.string.stats_best_plate_author_format),

    MostVotedPlateTitle(Res.string.stats_most_voted_plate_title),
    MostVotedPlateVotersSingular(Res.string.stats_most_voted_plate_voters_singular),
    MostVotedPlateVotersPlural(Res.string.stats_most_voted_plate_voters_plural),

    CooksSectionTitle(Res.string.stats_cooks_section_title),
    BestCookTitle(Res.string.stats_best_cook_title),
    BestCookMetricFormat(Res.string.stats_best_cook_metric_format),
    MostProlificTitle(Res.string.stats_most_prolific_title),
    MostProlificMetricFormat(Res.string.stats_most_prolific_metric_format),

    RoastSectionTitle(Res.string.stats_roast_section_title),
    MostCriticizedTitle(Res.string.stats_most_criticized_title),
    MostCriticizedMetricFormat(Res.string.stats_most_criticized_metric_format),

    CookAwardNeedsMoreSingular(Res.string.stats_cook_award_needs_more_singular),
    CookAwardNeedsMorePlural(Res.string.stats_cook_award_needs_more_plural),

    EmptyHeadline(Res.string.stats_empty_headline),
    EmptySubtext(Res.string.stats_empty_subtext),

    ErrorNoActiveCrew(Res.string.stats_error_no_active_crew),
    ErrorNotSignedIn(Res.string.stats_error_not_signed_in),
    ErrorUnauthorized(Res.string.stats_error_unauthorized),
    ErrorCrewNotFound(Res.string.stats_error_crew_not_found),
    ErrorUnavailable(Res.string.stats_error_unavailable),
}
