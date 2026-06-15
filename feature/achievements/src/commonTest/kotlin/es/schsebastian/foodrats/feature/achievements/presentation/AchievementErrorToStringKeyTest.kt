package es.schsebastian.foodrats.feature.achievements.presentation

import es.schsebastian.foodrats.feature.achievements.domain.error.AchievementError
import es.schsebastian.foodrats.feature.achievements.i18n.AchievementStringKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exhaustiveness lock: one mapping per [AchievementError] leaf (spec §11). */
class AchievementErrorToStringKeyTest {

    @Test
    fun session_notSignedIn_maps() {
        assertEquals(
            AchievementStringKey.ErrorNotSignedIn,
            AchievementError.Session.NotSignedIn.toStringKey(),
        )
    }

    @Test
    fun session_noActiveCrew_maps() {
        assertEquals(
            AchievementStringKey.ErrorNoActiveCrew,
            AchievementError.Session.NoActiveCrew.toStringKey(),
        )
    }

    @Test
    fun read_unauthorized_maps() {
        assertEquals(
            AchievementStringKey.ErrorUnauthorized,
            AchievementError.Read.Unauthorized.toStringKey(),
        )
    }

    @Test
    fun read_unavailable_maps() {
        assertEquals(
            AchievementStringKey.ErrorUnavailable,
            AchievementError.Read.Unavailable.toStringKey(),
        )
    }
}
