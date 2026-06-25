package es.schsebastian.foodrats.core.domain.moderation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Locks the on-device text filter's behavioral contract (UGC compliance §2): clean prose passes,
 * objectionable en + es content is flagged, common evasions (leetspeak / repeats / diacritics /
 * separators / fullwidth / homoglyphs / zero-width chars / NFD combining marks) are caught, safe
 * substrings do NOT false-positive (Scunthorpe), language scoping is respected, and the most-severe
 * category wins on ties.
 */
class WordlistTextModerationTest {

    private val moderation = WordlistTextModeration()

    private fun assertClean(
        text: String,
        tag: String = "en",
        m: WordlistTextModeration = moderation,
    ) = assertEquals(
        TextModerationVerdict.Clean,
        m.evaluate(text, tag),
        "expected clean for \"$text\" ($tag)",
    )

    private fun assertHit(
        text: String,
        tag: String,
        category: ModerationCategory,
        m: WordlistTextModeration = moderation,
    ) {
        val v = assertIs<TextModerationVerdict.Objectionable>(
            m.evaluate(text, tag),
            "expected objectionable for \"$text\" ($tag)",
        )
        assertEquals(category, v.category, "wrong category for \"$text\" ($tag): matched=${v.terms}")
    }

    // ── Baseline: clean text passes ───────────────────────────────────────────────────────────────

    @Test
    fun clean_text_passes() {
        assertClean("delicious homemade lasagna")
        assertClean("clase de cocina con mis amigos", tag = "es")
        assertClean("") // blank
        assertClean("   ") // whitespace only
    }

    // ── Scunthorpe / false-positive guard ────────────────────────────────────────────────────────

    @Test
    fun safe_substrings_do_not_false_positive_scunthorpe() {
        // "assassin" contains "ass-"; "class"/"clase" contain "-ass-"; "cocktail" contains "-c*ck-".
        // Whole-word matching means none of these trip the filter.
        assertClean("assassin's creed themed pasta night")
        assertClean("our cooking class made cocktail-glazed cake")
        assertClean("clase de cóctel", tag = "es")
    }

    @Test
    fun explicit_false_positive_guard_common_words() {
        // Words whose substrings or single letters might accidentally merge into a slur — must stay Clean.
        assertClean("class")
        assertClean("assistant")
        assertClean("grape")
        assertClean("Scunthorpe")
        assertClean("therapist")
        assertClean("analysis")
        assertClean("I am a great assistant in my class")
        assertClean("The grape harvest was exceptional this year")
        assertClean("Scunthorpe is a town in Lincolnshire England")
    }

    @Test
    fun normal_spanish_sentence_passes() {
        assertClean("hoy cocinamos un arroz con pollo delicioso", tag = "es")
        assertClean("la receta de mi abuela es increible", tag = "es")
    }

    // ── C1: food-app false-positive guard — ice-cream cone ───────────────────────────────────────

    @Test
    fun ice_cream_cone_is_not_flagged() {
        // C1: "cono" (ice-cream cone) and its plural must NOT be flagged.
        // "coño" normalized to "cono" via ñ→n is intentionally removed from the wordlist to prevent
        // false-positives on this common food item. Accept the false negative on the vulgar term.
        assertClean("un cono de helado", tag = "es")
        assertClean("dos conos de waffle", tag = "es")
    }

    // ── Standard evasion (existing, now with broader coverage) ───────────────────────────────────

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

    // ── H1: zero-width / format-character bypass ─────────────────────────────────────────────────

    @Test
    fun zero_width_character_bypass_is_caught() {
        // H1: inserting invisible zero-width characters between letters is the most common real evasion.
        // U+200B (ZWSP) inserted between u and c: "fu​ck" must still be caught as PROFANITY.
        assertHit("fu​ck", tag = "en", category = ModerationCategory.PROFANITY)
        // U+00AD (soft hyphen) inserted in "mierda": "mier­da"
        assertHit("mier­da", tag = "es", category = ModerationCategory.PROFANITY)
    }

    // ── H2: NFD (decomposed) diacritics ──────────────────────────────────────────────────────────

    @Test
    fun nfd_decomposed_combining_accents_are_caught() {
        // H2: NFD-decomposed form — base letter + combining accent (U+0301 COMBINING ACUTE ACCENT).
        // "miérda" = m-i-e-(combining acute)-r-d-a; the combining mark is dropped, yielding "mierda".
        // This is a REAL NFD test (base char + U+0301), not the precomposed é (U+00E9).
        assertHit("esto es miérda total", tag = "es", category = ModerationCategory.PROFANITY)
        // Also verify that a decomposed "é" (e + U+0301) in "gilipóllas" still resolves correctly.
        assertHit("eres un gilipóllas", tag = "es", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun precomposed_accented_form_of_spanish_terms_is_caught() {
        // Precomposed é (U+00E9): "miérda" — handled by the stripDiacritic precomposed map.
        // This test exercises the NFC/precomposed path (distinct from the NFD combining-mark path above).
        assertHit("esto es miérda total", tag = "es", category = ModerationCategory.PROFANITY)
        // "gilipollas" evasion with ó accent
        assertHit("eres un gilipóllas", tag = "es", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun latin_extended_a_diacritics_are_stripped() {
        // Latin Extended-A: š (U+0161 → s) tested in a separator-evasion context.
        // "š h i i i t" — š normalizes to "s"; single-char run merge yields "shit" (after run-collapse).
        assertHit("š h i i i t", tag = "en", category = ModerationCategory.PROFANITY)
    }

    // ── Separator-evasion defeat (merge single-char runs) ────────────────────────────────────────

    @Test
    fun separator_evasion_space_separated_letters_caught() {
        // "m i e r d a" — each letter is a separate token; merge-run collapses them to "mierda".
        assertHit("m i e r d a", tag = "es", category = ModerationCategory.PROFANITY)
        // "p u t a" → "puta"
        assertHit("p u t a", tag = "es", category = ModerationCategory.SEXUAL)
    }

    @Test
    fun separator_evasion_dot_separated_letters_caught() {
        // "f.u.c.k" — dots are token delimiters; merge-run collapses single-char tokens to "fuck".
        assertHit("f.u.c.k this meal", tag = "en", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun separator_evasion_underscore_separated_letters_caught() {
        // "s_h_i_t" → ["s","h","i","t"] → merged "shit"
        assertHit("s_h_i_t", tag = "en", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun separator_evasion_mixed_punctuation_caught() {
        // "f-u-c-k" → dashes split each letter → merged "fuck"
        assertHit("f-u-c-k", tag = "en", category = ModerationCategory.PROFANITY)
    }

    // ── Fullwidth fold ────────────────────────────────────────────────────────────────────────────

    @Test
    fun fullwidth_latin_characters_are_caught() {
        // "ｆｕｃｋ" — fullwidth f,u,c,k (U+FF46,U+FF55,U+FF43,U+FF4B) → "fuck"
        assertHit("ｆｕｃｋ this", tag = "en", category = ModerationCategory.PROFANITY)
        // "ｓｈｉｔ" → "shit"
        assertHit("ｓｈｉｔ", tag = "en", category = ModerationCategory.PROFANITY)
    }

    // ── Homoglyph fold ────────────────────────────────────────────────────────────────────────────

    @Test
    fun cyrillic_homoglyph_evasion_is_caught() {
        // Cyrillic "а" (U+0430) looks like Latin "a"; "е" (U+0435) like "e"; "о" (U+043E) like "o".
        // "fuсk" with Cyrillic с (U+0441) instead of Latin c → folds to "fuck".
        assertHit("fuсk this", tag = "en", category = ModerationCategory.PROFANITY)
    }

    // ── It3 fixes ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun dumbass_resolves_to_profanity_not_harassment() {
        // M-A fix: "dumbass" was listed twice — PROFANITY (line 40) and HARASSMENT (line 53).
        // The duplicate HARASSMENT entry is dead (later map key wins in Kotlin) and misleads readers.
        // After removing the HARASSMENT entry, "dumbass" must still be caught as PROFANITY.
        assertHit("you are such a dumbass", tag = "en", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun emoji_glued_to_term_is_caught() {
        // LOW fix: emoji is not a-z, so the new tokenizer treats it as a delimiter.
        // "🍕puta" → tokenize splits on 🍕 → token "puta" → SEXUAL hit.
        assertHit("🍕puta", tag = "es", category = ModerationCategory.SEXUAL)
        // "fuck🔥" → token "fuck" before the emoji → PROFANITY hit.
        assertHit("fuck🔥", tag = "en", category = ModerationCategory.PROFANITY)
    }

    @Test
    fun copy_on_write_cache_is_stable_across_repeated_calls() {
        // H-A sanity: multiple evaluate calls on the same instance must return consistent results.
        // A corrupt mutable-map cache would throw or return wrong results on concurrent structural
        // modification; a copy-on-write cache at worst re-computes once per race (harmless).
        // This test exercises the memoization path by calling evaluate several times with the same
        // and different language tags on the same WordlistTextModeration instance.
        val fresh = WordlistTextModeration()
        repeat(5) {
            assertHit("fuck", tag = "en", category = ModerationCategory.PROFANITY, m = fresh)
            assertHit("mierda", tag = "es", category = ModerationCategory.PROFANITY, m = fresh)
            assertClean("delicious pasta", tag = "en", m = fresh)
        }
    }
}
