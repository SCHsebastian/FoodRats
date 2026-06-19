package es.schsebastian.foodrats.feature.stats.i18n

import es.schsebastian.foodrats.core.i18n.PluralStringKey
import foodrats.feature.stats.generated.resources.Res
import foodrats.feature.stats.generated.resources.stats_hero_crew_streak
import foodrats.feature.stats.generated.resources.stats_hero_personal_streak
import foodrats.feature.stats.generated.resources.stats_hero_plates_today
import foodrats.feature.stats.generated.resources.stats_most_prolific_metric_format
import foodrats.feature.stats.generated.resources.stats_most_used_ingredient_metric_format
import foodrats.feature.stats.generated.resources.stats_most_voted_plate_voters
import org.jetbrains.compose.resources.PluralStringResource

enum class StatsPluralKey(override val resourceId: PluralStringResource) : PluralStringKey {
    HeroPersonalStreak(Res.plurals.stats_hero_personal_streak),
    HeroCrewStreak(Res.plurals.stats_hero_crew_streak),
    HeroPlatesToday(Res.plurals.stats_hero_plates_today),
    MostVotedPlateVoters(Res.plurals.stats_most_voted_plate_voters),
    MostProlificMetric(Res.plurals.stats_most_prolific_metric_format),
    MostUsedIngredientMetric(Res.plurals.stats_most_used_ingredient_metric_format),
}
