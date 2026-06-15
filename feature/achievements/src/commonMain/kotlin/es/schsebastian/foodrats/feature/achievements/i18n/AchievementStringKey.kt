package es.schsebastian.foodrats.feature.achievements.i18n

import es.schsebastian.foodrats.core.i18n.StringKey
import foodrats.feature.achievements.generated.resources.Res
import foodrats.feature.achievements.generated.resources.achievements_best_cook_desc
import foodrats.feature.achievements.generated.resources.achievements_best_cook_title
import foodrats.feature.achievements.generated.resources.achievements_celebration_ack
import foodrats.feature.achievements.generated.resources.achievements_celebration_title
import foodrats.feature.achievements.generated.resources.achievements_detail_close_cta
import foodrats.feature.achievements.generated.resources.achievements_detail_locked_label
import foodrats.feature.achievements.generated.resources.achievements_earned_on_format
import foodrats.feature.achievements.generated.resources.achievements_earned_section_title
import foodrats.feature.achievements.generated.resources.achievements_empty_subtext
import foodrats.feature.achievements.generated.resources.achievements_early_bird_10_desc
import foodrats.feature.achievements.generated.resources.achievements_early_bird_10_title
import foodrats.feature.achievements.generated.resources.achievements_error_no_active_crew
import foodrats.feature.achievements.generated.resources.achievements_error_not_signed_in
import foodrats.feature.achievements.generated.resources.achievements_error_unauthorized
import foodrats.feature.achievements.generated.resources.achievements_error_unavailable
import foodrats.feature.achievements.generated.resources.achievements_first_plate_desc
import foodrats.feature.achievements.generated.resources.achievements_first_plate_title
import foodrats.feature.achievements.generated.resources.achievements_ingredients_100_desc
import foodrats.feature.achievements.generated.resources.achievements_ingredients_100_title
import foodrats.feature.achievements.generated.resources.achievements_ingredients_25_desc
import foodrats.feature.achievements.generated.resources.achievements_ingredients_25_title
import foodrats.feature.achievements.generated.resources.achievements_ingredients_50_desc
import foodrats.feature.achievements.generated.resources.achievements_ingredients_50_title
import foodrats.feature.achievements.generated.resources.achievements_locked_section_title
import foodrats.feature.achievements.generated.resources.achievements_meals_100_desc
import foodrats.feature.achievements.generated.resources.achievements_meals_100_title
import foodrats.feature.achievements.generated.resources.achievements_meals_10_desc
import foodrats.feature.achievements.generated.resources.achievements_meals_10_title
import foodrats.feature.achievements.generated.resources.achievements_meals_50_desc
import foodrats.feature.achievements.generated.resources.achievements_meals_50_title
import foodrats.feature.achievements.generated.resources.achievements_night_owl_10_desc
import foodrats.feature.achievements.generated.resources.achievements_night_owl_10_title
import foodrats.feature.achievements.generated.resources.achievements_progress_format
import foodrats.feature.achievements.generated.resources.achievements_screen_title
import foodrats.feature.achievements.generated.resources.achievements_streak_crew_30_desc
import foodrats.feature.achievements.generated.resources.achievements_streak_crew_30_title
import foodrats.feature.achievements.generated.resources.achievements_streak_crew_7_desc
import foodrats.feature.achievements.generated.resources.achievements_streak_crew_7_title
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_100_desc
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_100_title
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_30_desc
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_30_title
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_7_desc
import foodrats.feature.achievements.generated.resources.achievements_streak_personal_7_title
import foodrats.feature.achievements.generated.resources.achievements_unlocked_toast
import org.jetbrains.compose.resources.StringResource

/**
 * i18n keys for the achievements feature, implementing the sealed [StringKey] interface
 * (the `StatsStringKey` shape). One `…Title` + one `…Desc` per catalog row, plus screen chrome
 * and error rows. (spec §10)
 */
enum class AchievementStringKey(override val resourceId: StringResource) : StringKey {
    // Screen chrome
    ScreenTitle(Res.string.achievements_screen_title),
    EarnedSectionTitle(Res.string.achievements_earned_section_title),
    LockedSectionTitle(Res.string.achievements_locked_section_title),
    ProgressFormat(Res.string.achievements_progress_format),
    EarnedOnFormat(Res.string.achievements_earned_on_format),
    UnlockedToast(Res.string.achievements_unlocked_toast),
    CelebrationTitle(Res.string.achievements_celebration_title),
    CelebrationAck(Res.string.achievements_celebration_ack),
    DetailLockedLabel(Res.string.achievements_detail_locked_label),
    DetailCloseCta(Res.string.achievements_detail_close_cta),
    EmptySubtext(Res.string.achievements_empty_subtext),

    // first_plate
    FirstPlateTitle(Res.string.achievements_first_plate_title),
    FirstPlateDesc(Res.string.achievements_first_plate_desc),

    // meals_10 / 50 / 100
    Meals10Title(Res.string.achievements_meals_10_title),
    Meals10Desc(Res.string.achievements_meals_10_desc),
    Meals50Title(Res.string.achievements_meals_50_title),
    Meals50Desc(Res.string.achievements_meals_50_desc),
    Meals100Title(Res.string.achievements_meals_100_title),
    Meals100Desc(Res.string.achievements_meals_100_desc),

    // ingredients_25 / 50 / 100
    Ingredients25Title(Res.string.achievements_ingredients_25_title),
    Ingredients25Desc(Res.string.achievements_ingredients_25_desc),
    Ingredients50Title(Res.string.achievements_ingredients_50_title),
    Ingredients50Desc(Res.string.achievements_ingredients_50_desc),
    Ingredients100Title(Res.string.achievements_ingredients_100_title),
    Ingredients100Desc(Res.string.achievements_ingredients_100_desc),

    // streak_personal_7 / 30 / 100
    StreakPersonal7Title(Res.string.achievements_streak_personal_7_title),
    StreakPersonal7Desc(Res.string.achievements_streak_personal_7_desc),
    StreakPersonal30Title(Res.string.achievements_streak_personal_30_title),
    StreakPersonal30Desc(Res.string.achievements_streak_personal_30_desc),
    StreakPersonal100Title(Res.string.achievements_streak_personal_100_title),
    StreakPersonal100Desc(Res.string.achievements_streak_personal_100_desc),

    // streak_crew_7 / 30
    StreakCrew7Title(Res.string.achievements_streak_crew_7_title),
    StreakCrew7Desc(Res.string.achievements_streak_crew_7_desc),
    StreakCrew30Title(Res.string.achievements_streak_crew_30_title),
    StreakCrew30Desc(Res.string.achievements_streak_crew_30_desc),

    // best_cook
    BestCookTitle(Res.string.achievements_best_cook_title),
    BestCookDesc(Res.string.achievements_best_cook_desc),

    // early_bird_10 / night_owl_10
    EarlyBird10Title(Res.string.achievements_early_bird_10_title),
    EarlyBird10Desc(Res.string.achievements_early_bird_10_desc),
    NightOwl10Title(Res.string.achievements_night_owl_10_title),
    NightOwl10Desc(Res.string.achievements_night_owl_10_desc),

    // Errors
    ErrorNotSignedIn(Res.string.achievements_error_not_signed_in),
    ErrorNoActiveCrew(Res.string.achievements_error_no_active_crew),
    ErrorUnauthorized(Res.string.achievements_error_unauthorized),
    ErrorUnavailable(Res.string.achievements_error_unavailable),
}
