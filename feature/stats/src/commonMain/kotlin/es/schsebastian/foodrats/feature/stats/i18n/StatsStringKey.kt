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
import foodrats.feature.stats.generated.resources.stats_open_meal_cta
import foodrats.feature.stats.generated.resources.stats_stats_loading
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
import foodrats.feature.stats.generated.resources.stats_calendar_back
import foodrats.feature.stats.generated.resources.stats_calendar_day_a11y_format
import foodrats.feature.stats.generated.resources.stats_calendar_day_date_format
import foodrats.feature.stats.generated.resources.stats_calendar_day_meal_count_badge
import foodrats.feature.stats.generated.resources.stats_calendar_day_no_meals
import foodrats.feature.stats.generated.resources.stats_calendar_day_today_format
import foodrats.feature.stats.generated.resources.stats_calendar_empty_month
import foodrats.feature.stats.generated.resources.stats_calendar_grid_loading
import foodrats.feature.stats.generated.resources.stats_calendar_month_april
import foodrats.feature.stats.generated.resources.stats_calendar_month_august
import foodrats.feature.stats.generated.resources.stats_calendar_month_december
import foodrats.feature.stats.generated.resources.stats_calendar_month_february
import foodrats.feature.stats.generated.resources.stats_calendar_month_january
import foodrats.feature.stats.generated.resources.stats_calendar_month_july
import foodrats.feature.stats.generated.resources.stats_calendar_month_june
import foodrats.feature.stats.generated.resources.stats_calendar_month_march
import foodrats.feature.stats.generated.resources.stats_calendar_month_may
import foodrats.feature.stats.generated.resources.stats_calendar_month_november
import foodrats.feature.stats.generated.resources.stats_calendar_month_october
import foodrats.feature.stats.generated.resources.stats_calendar_month_september
import foodrats.feature.stats.generated.resources.stats_calendar_month_title_format
import foodrats.feature.stats.generated.resources.stats_calendar_next_month
import foodrats.feature.stats.generated.resources.stats_calendar_prev_month
import foodrats.feature.stats.generated.resources.stats_calendar_score_format
import foodrats.feature.stats.generated.resources.stats_calendar_title
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_friday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_friday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_monday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_monday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_saturday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_saturday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_sunday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_sunday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_thursday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_thursday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_tuesday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_tuesday_full
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_wednesday
import foodrats.feature.stats.generated.resources.stats_calendar_weekday_wednesday_full
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

    /** onClickLabel + Role.Button for an award plate tile — opens MealDetail for that meal. */
    OpenMealCta(Res.string.stats_open_meal_cta),
    /** Live-region announcement for the stats-screen loading skeletons (one representative shimmer). */
    StatsLoading(Res.string.stats_stats_loading),


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

    CalendarTitle(Res.string.stats_calendar_title),
    CalendarBackCta(Res.string.stats_calendar_back),
    /** "%1$s %2$d" — localized month name + year. */
    CalendarMonthTitleFormat(Res.string.stats_calendar_month_title_format),
    CalendarPrevMonthCta(Res.string.stats_calendar_prev_month),
    CalendarNextMonthCta(Res.string.stats_calendar_next_month),
    CalendarEmptyMonth(Res.string.stats_calendar_empty_month),
    /** "%1$s ★" — one-decimal average score on a calendar meal row. */
    CalendarScoreFormat(Res.string.stats_calendar_score_format),

    CalendarMonthJanuary(Res.string.stats_calendar_month_january),
    CalendarMonthFebruary(Res.string.stats_calendar_month_february),
    CalendarMonthMarch(Res.string.stats_calendar_month_march),
    CalendarMonthApril(Res.string.stats_calendar_month_april),
    CalendarMonthMay(Res.string.stats_calendar_month_may),
    CalendarMonthJune(Res.string.stats_calendar_month_june),
    CalendarMonthJuly(Res.string.stats_calendar_month_july),
    CalendarMonthAugust(Res.string.stats_calendar_month_august),
    CalendarMonthSeptember(Res.string.stats_calendar_month_september),
    CalendarMonthOctober(Res.string.stats_calendar_month_october),
    CalendarMonthNovember(Res.string.stats_calendar_month_november),
    CalendarMonthDecember(Res.string.stats_calendar_month_december),

    CalendarWeekdayMonday(Res.string.stats_calendar_weekday_monday),
    CalendarWeekdayTuesday(Res.string.stats_calendar_weekday_tuesday),
    CalendarWeekdayWednesday(Res.string.stats_calendar_weekday_wednesday),
    CalendarWeekdayThursday(Res.string.stats_calendar_weekday_thursday),
    CalendarWeekdayFriday(Res.string.stats_calendar_weekday_friday),
    CalendarWeekdaySaturday(Res.string.stats_calendar_weekday_saturday),
    CalendarWeekdaySunday(Res.string.stats_calendar_weekday_sunday),

    /** Full localized weekday names — a11y-only, read by TalkBack instead of the single-letter initial. */
    CalendarWeekdayMondayFull(Res.string.stats_calendar_weekday_monday_full),
    CalendarWeekdayTuesdayFull(Res.string.stats_calendar_weekday_tuesday_full),
    CalendarWeekdayWednesdayFull(Res.string.stats_calendar_weekday_wednesday_full),
    CalendarWeekdayThursdayFull(Res.string.stats_calendar_weekday_thursday_full),
    CalendarWeekdayFridayFull(Res.string.stats_calendar_weekday_friday_full),
    CalendarWeekdaySaturdayFull(Res.string.stats_calendar_weekday_saturday_full),
    CalendarWeekdaySundayFull(Res.string.stats_calendar_weekday_sunday_full),

    /** "%1$s %2$d, %3$d" — localized month name + day-of-month + year, for a day cell's a11y label. */
    CalendarDayDateFormat(Res.string.stats_calendar_day_date_format),
    /** "%1$s, %2$s" — date text + meal-count text, combined into a day cell's a11y label. */
    CalendarDayA11yFormat(Res.string.stats_calendar_day_a11y_format),
    /** "%1$s, today" — wraps [CalendarDayA11yFormat] when the cell is the current day. */
    CalendarDayTodayFormat(Res.string.stats_calendar_day_today_format),
    /** A day cell with no meals — a11y-only, spoken instead of the meal-count plural. */
    CalendarDayNoMeals(Res.string.stats_calendar_day_no_meals),
    /** "%1$d" — plain count shown on the multi-meal badge chip (no glyph). */
    CalendarDayMealCountBadge(Res.string.stats_calendar_day_meal_count_badge),
    /** Loading announcement carried by one representative shimmer cell in [MonthGridSkeleton]. */
    CalendarGridLoading(Res.string.stats_calendar_grid_loading),

    Retry(Res.string.stats_retry),

    ErrorNoActiveCrew(Res.string.stats_error_no_active_crew),
    ErrorNotSignedIn(Res.string.stats_error_not_signed_in),
    ErrorUnauthorized(Res.string.stats_error_unauthorized),
    ErrorCrewNotFound(Res.string.stats_error_crew_not_found),
    ErrorUnavailable(Res.string.stats_error_unavailable),
}
