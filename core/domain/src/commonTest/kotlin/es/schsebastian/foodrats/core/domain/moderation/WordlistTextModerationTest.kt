package es.schsebastian.foodrats.core.domain.moderation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Locks the on-device text filter's behavioral contract (UGC compliance §2): clean prose passes,
 * objectionable en + es content is flagged, common evasions (leetspeak / repeats / diacritics) are
 * caught, safe substrings do NOT false-positive (Scunthorpe), language scoping is respected, and the
 * most-severe category wins on ties.
 */
class WordlistTextModerationTest {

    private val moderation = WordlistTextModeration()

    private fun assertClean(text: String, tag: String = "en") =
        assertEquals(
            TextModerationVerdict.Clean,
            moderation.evaluate(text, tag),
            "expected clean for \"$text\" ($tag)",
        )

    private fun assertHit(text: String, tag: String, category: ModerationCategory) {
        val v = assertIs<TextModerationVerdict.Objectionable>(
            moderation.evaluate(text, tag),
            "expected objectionable for \"$text\" ($tag)",
        )
        assertEquals(category, v.category, "wrong category for \"$text\" ($tag): matched=${v.terms}")
    }

    @Test
    fun clean_text_passes() {
        assertClean("delicious homemade lasagna")
        assertClean("clase de cocina con mis amigos", tag = "es")
        assertClean("") // blank
        assertClean("   ") // whitespace only
    }

    @Test
    fun safe_substrings_do_not_false_positive_scunthorpe() {
        // "assassin" contains "ass-"; "class"/"clase" contain "-ass-"; "cocktail" contains "-c*ck-".
        // Whole-word matching means none of these trip the filter.
        assertClean("assassin's creed themed pasta night")
        assertClean("our cooking class made cocktail-glazed cake")
        assertClean("clase de cóctel", tag = "es")
    }

    @Test
    fun objectionable_english_is_flagged() {
        assertHit("what the fuck is this", tag = "en", category = ModerationCategory.PROFANITY)
        assertHit("you are an idiot", tag = "en", category = ModerationCategory.HARASSMENT)
    }

    @Test
    fun cross_language_slur_is_flagged_in_any_language_as_hate() {
        assertHit("that is so nazi", tag = "en", category = ModerationCategory.HATE)
        assertHit("eso es muy nazi", tag = "es", category = ModerationCategory.HATE)
    }

    @Test
    fun objectionable_spanish_is_flagged_under_es() {
        assertHit("esto es una mierda", tag = "es", category = ModerationCategory.PROFANITY)
        assertHit("eres un idiota", tag = "es", category = ModerationCategory.HARASSMENT)
    }

    @Test
    fun all_languages_are_screened_regardless_of_active_tag() {
        // The default screen checks EVERY supported language: Spanish "mierda" is flagged even under
        // "en", so abuse is caught when the writer's language differs from the device/UI language.
        assertHit("esto es una mierda", tag = "en", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun evasion_leetspeak_repeats_and_diacritics_are_caught() {
        assertHit("what the sh1t", tag = "en", category = ModerationCategory.PROFANITY) // 1 -> i
        assertHit("you a\$\$hole", tag = "en", category = ModerationCategory.PROFANITY) // $ -> s
        assertHit("shiiiit happens", tag = "en", category = ModerationCategory.PROFANITY) // repeats
        assertHit("eres un gilipóllas", tag = "es", category = ModerationCategory.PROFANITY) // ó -> o
    }

    @Test
    fun severity_ordering_returns_most_severe_category() {
        // "idiot" is HARASSMENT, "nazi" is HATE; HATE outranks HARASSMENT.
        assertHit("you idiot nazi", tag = "en", category = ModerationCategory.HATE)
    }
}
