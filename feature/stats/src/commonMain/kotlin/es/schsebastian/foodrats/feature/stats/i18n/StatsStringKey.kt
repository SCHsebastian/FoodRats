package es.schsebastian.foodrats.feature.stats.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.stats.generated.resources.Res
import foodrats.feature.stats.generated.resources.stats_best_cook_metric_format
import foodrats.feature.stats.generated.resources.stats_best_cook_metric_format_glyph_free
import foodrats.feature.stats.generated.resources.stats_best_cook_title
import foodrats.feature.stats.generated.resources.stats_best_plate_author_format
import foodrats.feature.stats.generated.resources.stats_best_plate_title
import foodrats.feature.stats.generated.resources.stats_cooks_section_title
import foodrats.feature.stats.generated.resources.stats_empty_headline
import foodrats.feature.stats.generated.resources.stats_empty_subtext
import foodrats.feature.stats.generated.resources.stats_error_crew_not_found
import foodrats.feature.stats.generated.resources.stats_error_no_active_crew
import foodrats.feature.stats.generated.resources.stats_error_not_signed_in
import foodrats.feature.stats.generated.resources.stats_error_unauthorized
import foodrats.feature.stats.generated.resources.stats_error_unavailable
import foodrats.feature.stats.generated.resources.stats_hero_i_posted_today
import foodrats.feature.stats.generated.resources.stats_hero_no_streak
import foodrats.feature.stats.generated.resources.stats_most_criticized_metric_format
import foodrats.feature.stats.generated.resources.stats_most_criticized_metric_format_glyph_free
import foodrats.feature.stats.generated.resources.stats_most_criticized_title
import foodrats.feature.stats.generated.resources.stats_most_prolific_title
import foodrats.feature.stats.generated.resources.stats_most_used_ingredient_title
import foodrats.feature.stats.generated.resources.stats_top_ingredient_by_member_title
import foodrats.feature.stats.generated.resources.stats_most_voted_plate_title
import foodrats.feature.stats.generated.resources.stats_bingo_category_beverage
import foodrats.feature.stats.generated.resources.stats_bingo_category_dairy
import foodrats.feature.stats.generated.resources.stats_bingo_category_fish
import foodrats.feature.stats.generated.resources.stats_bingo_category_fruit
import foodrats.feature.stats.generated.resources.stats_bingo_category_grain
import foodrats.feature.stats.generated.resources.stats_bingo_category_legume
import foodrats.feature.stats.generated.resources.stats_bingo_category_meat
import foodrats.feature.stats.generated.resources.stats_bingo_category_other
import foodrats.feature.stats.generated.resources.stats_bingo_category_sauce
import foodrats.feature.stats.generated.resources.stats_bingo_category_spice
import foodrats.feature.stats.generated.resources.stats_bingo_category_sweet
import foodrats.feature.stats.generated.resources.stats_bingo_category_vegetable
import foodrats.feature.stats.generated.resources.stats_bingo_collected_on_format
import foodrats.feature.stats.generated.resources.stats_bingo_index_format
import foodrats.feature.stats.generated.resources.stats_bingo_locked_label
import foodrats.feature.stats.generated.resources.stats_bingo_mystery_glyph
import foodrats.feature.stats.generated.resources.stats_bingo_mystery_name
import foodrats.feature.stats.generated.resources.stats_bingo_progress_format
import foodrats.feature.stats.generated.resources.stats_bingo_title
import foodrats.feature.stats.generated.resources.stats_collection_title
import foodrats.feature.stats.generated.resources.stats_passport_collected_on_format
import foodrats.feature.stats.generated.resources.stats_passport_locked_label
import foodrats.feature.stats.generated.resources.stats_passport_progress_format
import foodrats.feature.stats.generated.resources.stats_passport_title
import foodrats.feature.stats.generated.resources.stats_plate_photo_format
import foodrats.feature.stats.generated.resources.stats_retry
import foodrats.feature.stats.generated.resources.stats_roast_section_title
import foodrats.feature.stats.generated.resources.stats_share_award
import foodrats.feature.stats.generated.resources.stats_share_score_format
import foodrats.feature.stats.generated.resources.stats_summary_avg_per_day_label
import foodrats.feature.stats.generated.resources.stats_summary_total_plates_label
import foodrats.feature.stats.generated.resources.stats_your_stats_eyebrow
import foodrats.feature.stats.generated.resources.stats_your_streak_eyebrow
import foodrats.feature.stats.generated.resources.stats_streak_days_unit
import foodrats.feature.stats.generated.resources.stats_awards_eyebrow
import foodrats.feature.stats.generated.resources.stats_tab_historic
import foodrats.feature.stats.generated.resources.stats_tab_month
import foodrats.feature.stats.generated.resources.stats_tab_week
import foodrats.feature.stats.generated.resources.stats_title
import foodrats.feature.stats.generated.resources.stats_weekly_recap_cta
import foodrats.feature.stats.generated.resources.stats_window_empty_historic
import foodrats.feature.stats.generated.resources.stats_window_empty_month
import foodrats.feature.stats.generated.resources.stats_window_empty_week
import org.jetbrains.compose.resources.StringResource

enum class StatsStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.stats_title),

    TabWeek(Res.string.stats_tab_week),
    TabMonth(Res.string.stats_tab_month),
    TabHistoric(Res.string.stats_tab_historic),

    HeroIPostedToday(Res.string.stats_hero_i_posted_today),
    HeroNoStreak(Res.string.stats_hero_no_streak),

    WindowEmptyWeek(Res.string.stats_window_empty_week),
    WindowEmptyMonth(Res.string.stats_window_empty_month),
    WindowEmptyHistoric(Res.string.stats_window_empty_historic),

    SummaryTotalPlatesLabel(Res.string.stats_summary_total_plates_label),
    SummaryAvgPerDayLabel(Res.string.stats_summary_avg_per_day_label),

    YourStatsEyebrow(Res.string.stats_your_stats_eyebrow),
    YourStreakEyebrow(Res.string.stats_your_streak_eyebrow),
    StreakDaysUnit(Res.string.stats_streak_days_unit),
    AwardsEyebrow(Res.string.stats_awards_eyebrow),

    PlatePhotoFormat(Res.string.stats_plate_photo_format),

    BestPlateTitle(Res.string.stats_best_plate_title),
    BestPlateAuthorFormat(Res.string.stats_best_plate_author_format),

    MostVotedPlateTitle(Res.string.stats_most_voted_plate_title),

    CooksSectionTitle(Res.string.stats_cooks_section_title),
    BestCookTitle(Res.string.stats_best_cook_title),
    BestCookMetricFormat(Res.string.stats_best_cook_metric_format),
    /** C8b — glyph-free variant (Emoji/Numeric); %1$s = pre-rendered score, %2$d = plate count. */
    BestCookMetricFormatGlyphFree(Res.string.stats_best_cook_metric_format_glyph_free),
    MostProlificTitle(Res.string.stats_most_prolific_title),

    RoastSectionTitle(Res.string.stats_roast_section_title),
    MostCriticizedTitle(Res.string.stats_most_criticized_title),
    MostCriticizedMetricFormat(Res.string.stats_most_criticized_metric_format),
    /** C8b — glyph-free variant (Emoji/Numeric); %1$s = pre-rendered score, no ★. */
    MostCriticizedMetricFormatGlyphFree(Res.string.stats_most_criticized_metric_format_glyph_free),

    MostUsedIngredientTitle(Res.string.stats_most_used_ingredient_title),
    TopIngredientByMemberTitle(Res.string.stats_top_ingredient_by_member_title),


    CollectionTitle(Res.string.stats_collection_title),

    PassportTitle(Res.string.stats_passport_title),
    PassportProgressFormat(Res.string.stats_passport_progress_format),
    PassportLockedLabel(Res.string.stats_passport_locked_label),
    PassportCollectedOnFormat(Res.string.stats_passport_collected_on_format),

    BingoTitle(Res.string.stats_bingo_title),
    BingoProgressFormat(Res.string.stats_bingo_progress_format),
    BingoLockedLabel(Res.string.stats_bingo_locked_label),
    BingoCollectedOnFormat(Res.string.stats_bingo_collected_on_format),
    BingoMysteryGlyph(Res.string.stats_bingo_mystery_glyph),
    BingoMysteryName(Res.string.stats_bingo_mystery_name),
    BingoIndexFormat(Res.string.stats_bingo_index_format),

    BingoCategoryVegetable(Res.string.stats_bingo_category_vegetable),
    BingoCategoryFruit(Res.string.stats_bingo_category_fruit),
    BingoCategoryMeat(Res.string.stats_bingo_category_meat),
    BingoCategoryFish(Res.string.stats_bingo_category_fish),
    BingoCategoryDairy(Res.string.stats_bingo_category_dairy),
    BingoCategoryGrain(Res.string.stats_bingo_category_grain),
    BingoCategoryLegume(Res.string.stats_bingo_category_legume),
    BingoCategorySauce(Res.string.stats_bingo_category_sauce),
    BingoCategorySpice(Res.string.stats_bingo_category_spice),
    BingoCategorySweet(Res.string.stats_bingo_category_sweet),
    BingoCategoryBeverage(Res.string.stats_bingo_category_beverage),
    BingoCategoryOther(Res.string.stats_bingo_category_other),

    EmptyHeadline(Res.string.stats_empty_headline),
    EmptySubtext(Res.string.stats_empty_subtext),

    WeeklyRecapCta(Res.string.stats_weekly_recap_cta),
    ShareAward(Res.string.stats_share_award),
    ShareScoreFormat(Res.string.stats_share_score_format),

    Retry(Res.string.stats_retry),

    ErrorNoActiveCrew(Res.string.stats_error_no_active_crew),
    ErrorNotSignedIn(Res.string.stats_error_not_signed_in),
    ErrorUnauthorized(Res.string.stats_error_unauthorized),
    ErrorCrewNotFound(Res.string.stats_error_crew_not_found),
    ErrorUnavailable(Res.string.stats_error_unavailable),
}
