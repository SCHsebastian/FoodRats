package es.schsebastian.foodrats.app.recap

import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey

/**
 * The assembled, ready-to-render weekly-recap (roadmap §2.4): an ordered list of non-empty
 * [RecapScene]s the story player flips through. Built PURELY from the existing client read paths
 * (`ObserveStatsUseCase` + `ObserveAchievementsUseCase`) — nothing is recomputed and no new server
 * read is added (the digest function already computes the same window; this is its rich client view).
 *
 * Each [RecapScene] is a small, render-ready value object (no domain ports, no Compose) so a single
 * scene composable can render it both inside the in-app player AND, in Wave 3, off-screen to a
 * shareable bitmap. The cover and "your week" summary always appear; the award/streak/badge/cuisine
 * scenes appear only when their underlying data exists, so an empty crew-week degrades to just the
 * cover + a gentle "quiet week" summary rather than a wall of blanks.
 */
data class WeeklyRecap(
    val scenes: List<RecapScene>,
) {
    val sceneCount: Int get() = scenes.size
    val isEmpty: Boolean get() = scenes.isEmpty()
}

/**
 * A single full-screen recap card. The [kind] is the stable analytics discriminator (snake_case, no
 * PII); the leaf payloads carry only display-ready primitives (already-localized-by-the-caller is
 * NOT done here — display text is resolved in the scene composable via StringKeys, these payloads
 * hold the raw values: dish names, member display names, counts).
 */
sealed interface RecapScene {
    val kind: RecapSceneKind

    /** Opening card — brand motif + the recap's week label. Always present. */
    data class Cover(val weekLabel: String) : RecapScene {
        override val kind = RecapSceneKind.Cover
    }

    /** The week's highest-scoring plate. */
    data class TopMeal(
        val photoUrl: String,
        val dishName: String,
        val authorName: String,
        val score: Double,
        val ratingCount: Int,
    ) : RecapScene {
        override val kind = RecapSceneKind.TopMeal
    }

    /** Highest average-rated cook of the week. */
    data class BestCook(
        val memberName: String,
        val avgScore: Double,
    ) : RecapScene {
        override val kind = RecapSceneKind.BestCook
    }

    /** Member who posted the most plates this week. */
    data class MostProlific(
        val memberName: String,
        val postCount: Int,
    ) : RecapScene {
        override val kind = RecapSceneKind.MostProlific
    }

    /** The signed-in member's personal posting streak. */
    data class Streak(
        val streakDays: Int,
    ) : RecapScene {
        override val kind = RecapSceneKind.Streak
    }

    /**
     * New badges the member unlocked during the recap week (never present when empty). Carries the
     * achievements' i18n title keys — NOT raw strings — so the scene composable resolves them in the
     * active locale, keeping i18n discipline intact through the recap layer.
     */
    data class Badges(
        val titleKeys: List<AchievementStringKey>,
    ) : RecapScene {
        override val kind = RecapSceneKind.Badges
    }

    /** Cuisines collected (passport) so far — celebrates variety (anti-diet). */
    data class Cuisines(
        val collectedCount: Int,
        val totalCount: Int,
    ) : RecapScene {
        override val kind = RecapSceneKind.Cuisines
    }

    /**
     * Closing summary — the signed-in member's OWN collectibles at a glance. Always present. All
     * three facts are personal (streak is the member's; the passport/bingo counts derive over the
     * member's own meals), so this never leaks another member's data.
     */
    data class YourWeek(
        val streakDays: Int,
        val cuisinesCollected: Int,
        val ingredientsCollected: Int,
    ) : RecapScene {
        override val kind = RecapSceneKind.YourWeek
    }
}

/** Stable per-scene discriminator. The [wire] slug is the analytics `scene_kind` value (no PII). */
enum class RecapSceneKind(val wire: String) {
    Cover("cover"),
    TopMeal("top_meal"),
    BestCook("best_cook"),
    MostProlific("most_prolific"),
    Streak("streak"),
    Badges("badges"),
    Cuisines("cuisines"),
    YourWeek("your_week"),
}
