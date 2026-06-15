package es.schsebastian.foodrats.feature.achievements.data.firebase

import es.schsebastian.foodrats.core.domain.achievement.AchievementProgressError

/**
 * Maps an arbitrary backend [Throwable] to a typed [AchievementProgressError]. We bucket by
 * message-substring because GitLive Firebase does not expose typed `FirebaseException` subtypes
 * uniformly across Android + iOS — the same convention as `CrewErrorMapper`. A permission denial
 * (e.g. rules not yet deployed, or signed-out token revoke) maps to [Unauthorized]; everything else
 * is [Unavailable].
 */
class AchievementErrorMapper {
    fun map(t: Throwable): AchievementProgressError {
        val msg = t.message.orEmpty().lowercase()
        return when {
            "permission" in msg || "permission_denied" in msg || "unauthenticated" in msg ->
                AchievementProgressError.Unauthorized
            else -> AchievementProgressError.Unavailable
        }
    }
}
