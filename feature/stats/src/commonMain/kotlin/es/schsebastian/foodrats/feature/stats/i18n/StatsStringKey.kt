package es.schsebastian.foodrats.feature.stats.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.stats.generated.resources.Res
import foodrats.feature.stats.generated.resources.stats_crew_streak_label
import foodrats.feature.stats.generated.resources.stats_dish_tally_row
import foodrats.feature.stats.generated.resources.stats_empty_headline
import foodrats.feature.stats.generated.resources.stats_empty_subtext
import foodrats.feature.stats.generated.resources.stats_error_crew_not_found
import foodrats.feature.stats.generated.resources.stats_error_no_active_crew
import foodrats.feature.stats.generated.resources.stats_error_not_signed_in
import foodrats.feature.stats.generated.resources.stats_error_unauthorized
import foodrats.feature.stats.generated.resources.stats_error_unavailable
import foodrats.feature.stats.generated.resources.stats_leaderboard_row
import foodrats.feature.stats.generated.resources.stats_leaderboard_section
import foodrats.feature.stats.generated.resources.stats_meals_considered
import foodrats.feature.stats.generated.resources.stats_personal_streak_label
import foodrats.feature.stats.generated.resources.stats_post_count
import foodrats.feature.stats.generated.resources.stats_streak_unit_plural
import foodrats.feature.stats.generated.resources.stats_streak_unit_singular
import foodrats.feature.stats.generated.resources.stats_title
import foodrats.feature.stats.generated.resources.stats_top_dishes_section
import org.jetbrains.compose.resources.StringResource

enum class StatsStringKey(override val resourceId: StringResource) : StringKey {
    Title(Res.string.stats_title),
    CrewStreakLabel(Res.string.stats_crew_streak_label),
    PersonalStreakLabel(Res.string.stats_personal_streak_label),
    StreakUnitSingular(Res.string.stats_streak_unit_singular),
    StreakUnitPlural(Res.string.stats_streak_unit_plural),
    TopDishesSection(Res.string.stats_top_dishes_section),
    LeaderboardSection(Res.string.stats_leaderboard_section),
    MealsConsidered(Res.string.stats_meals_considered),
    DishTallyRow(Res.string.stats_dish_tally_row),
    LeaderboardRow(Res.string.stats_leaderboard_row),
    PostCount(Res.string.stats_post_count),
    EmptyHeadline(Res.string.stats_empty_headline),
    EmptySubtext(Res.string.stats_empty_subtext),
    ErrorNoActiveCrew(Res.string.stats_error_no_active_crew),
    ErrorNotSignedIn(Res.string.stats_error_not_signed_in),
    ErrorUnauthorized(Res.string.stats_error_unauthorized),
    ErrorCrewNotFound(Res.string.stats_error_crew_not_found),
    ErrorUnavailable(Res.string.stats_error_unavailable),
}
