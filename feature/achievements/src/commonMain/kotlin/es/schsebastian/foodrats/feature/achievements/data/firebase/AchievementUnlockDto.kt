package es.schsebastian.foodrats.feature.achievements.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore document at `accounts/{uid}/achievements/{achievementId}` (spec §6.2). One doc per
 * **unlocked** achievement; locked achievements have no document (absence = locked). The Firestore
 * document id IS the raw achievement id (`AchievementId.value`), so it is not stored in the body.
 *
 * The default `0L` lets kotlinx-serialization tolerate a malformed/legacy doc that omits the field
 * (it reads back as "unlocked at epoch 0" rather than failing the whole snapshot).
 */
@Serializable
data class AchievementUnlockDto(val unlockedAtEpochMs: Long = 0L)
