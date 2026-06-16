package es.schsebastian.foodrats.feature.achievements.data.firebase

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the `accounts/{uid}/achievements/{id}` document shape (spec §6.2): exactly one field,
 * `unlockedAtEpochMs`. The raw achievement id is the Firestore doc id, NOT a body field — so it must
 * not appear in the serialized JSON. Defaulting tolerates a legacy doc that omits the field.
 */
class AchievementUnlockDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun round_trips_unlocked_at() {
        val dto = AchievementUnlockDto(unlockedAtEpochMs = 1_700_000_000_000L)
        val encoded = json.encodeToString(AchievementUnlockDto.serializer(), dto)
        assertEquals(dto, json.decodeFromString(AchievementUnlockDto.serializer(), encoded))
    }

    @Test
    fun serializes_only_the_unlocked_at_field() {
        val encoded = json.encodeToString(AchievementUnlockDto.serializer(), AchievementUnlockDto(42L))
        assertEquals("""{"unlockedAtEpochMs":42}""", encoded)
    }

    @Test
    fun missing_field_defaults_to_zero() {
        val decoded = json.decodeFromString(AchievementUnlockDto.serializer(), "{}")
        assertEquals(0L, decoded.unlockedAtEpochMs)
    }

    @Test
    fun unknown_fields_are_tolerated() {
        val decoded = json.decodeFromString(
            AchievementUnlockDto.serializer(),
            """{"unlockedAtEpochMs":7,"futureField":"ignored"}""",
        )
        assertEquals(7L, decoded.unlockedAtEpochMs)
    }
}
