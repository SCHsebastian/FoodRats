package es.schsebastian.foodrats.feature.achievements.domain.model

import kotlin.jvm.JvmInline

/**
 * The compile-time catalog key for an [Achievement] (e.g. `AchievementId("first_plate")`).
 *
 * Unlike user-supplied ids (`MealId`/`CrewId`) this is a constant authored in the catalog, so it
 * takes no validating `of(): Result<…>` factory — it is constructed directly. The raw [value] is
 * also the Firestore document id used by the persistence layer (`accounts/{uid}/achievements/{id}`).
 */
@JvmInline
value class AchievementId(val value: String)
