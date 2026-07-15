package es.schsebastian.foodrats.feature.feed.domain.mention

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MentionParserTest {

    private fun accountId(raw: String): AccountId = (AccountId.of(raw) as Result.Ok).value

    private fun candidate(id: String, handle: String, displayName: String = handle) =
        MentionCandidate(accountId(id), handle, displayName)

    // --- parseMentions -----------------------------------------------------------------------

    @Test fun parses_a_single_known_handle() {
        val candidates = listOf(candidate("u1", "sebas"))
        assertEquals(listOf(accountId("u1")), MentionParser.parseMentions("hi @sebas!", candidates))
    }

    @Test fun matches_case_insensitively() {
        val candidates = listOf(candidate("u1", "Sebas"))
        assertEquals(listOf(accountId("u1")), MentionParser.parseMentions("hi @SEBAS", candidates))
        assertEquals(listOf(accountId("u1")), MentionParser.parseMentions("hi @sebas", candidates))
    }

    @Test fun resolves_multiple_mid_text_tokens_in_order() {
        val candidates = listOf(candidate("u1", "sebas"), candidate("u2", "lia"))
        assertEquals(
            listOf(accountId("u1"), accountId("u2")),
            MentionParser.parseMentions("hello @sebas, how are you @lia?", candidates),
        )
    }

    @Test fun unknown_token_is_ignored() {
        val candidates = listOf(candidate("u1", "sebas"))
        assertEquals(emptyList(), MentionParser.parseMentions("hi @ghost", candidates))
    }

    @Test fun empty_roster_yields_no_mentions() {
        assertEquals(emptyList(), MentionParser.parseMentions("hi @sebas", emptyList()))
    }

    @Test fun dedupes_repeated_mentions_preserving_first_seen_order() {
        val candidates = listOf(candidate("u1", "sebas"), candidate("u2", "lia"))
        assertEquals(
            listOf(accountId("u1"), accountId("u2")),
            MentionParser.parseMentions("@sebas @lia thanks @sebas again", candidates),
        )
    }

    @Test fun caps_at_ten_extras_silently_dropped() {
        val candidates = (1..12).map { candidate("u$it", "h$it") }
        val text = (1..12).joinToString(" ") { "@h$it" }
        val result = MentionParser.parseMentions(text, candidates)
        assertEquals(MENTION_PARSE_CAP, result.size)
        assertEquals((1..10).map { accountId("u$it") }, result)
    }

    // --- trailingFragment ----------------------------------------------------------------------

    @Test fun no_at_sign_yields_null_fragment() {
        assertNull(MentionParser.trailingFragment("no mentions here"))
    }

    @Test fun bare_trailing_at_yields_empty_fragment() {
        assertEquals("", MentionParser.trailingFragment("hi @"))
    }

    @Test fun in_progress_fragment_at_end_of_text() {
        assertEquals("se", MentionParser.trailingFragment("hi @se"))
    }

    @Test fun trailing_space_after_at_token_closes_the_mention() {
        assertNull(MentionParser.trailingFragment("hi @se "))
    }

    @Test fun trailing_newline_after_at_token_closes_the_mention() {
        assertNull(MentionParser.trailingFragment("hi @se\nmore text"))
    }

    @Test fun only_the_last_at_sign_is_considered() {
        assertEquals("b", MentionParser.trailingFragment("hi @foo and @b"))
    }

    // --- suggestions -----------------------------------------------------------------------------

    @Test fun suggestions_filter_by_handle_prefix_case_insensitively() {
        val candidates = listOf(candidate("u1", "sebas", "Sebas"), candidate("u2", "lia", "Lia"))
        assertEquals(listOf(candidates[0]), MentionParser.suggestions("SE", candidates))
    }

    @Test fun suggestions_fall_back_to_display_name_prefix() {
        val candidates = listOf(candidate("u1", "xyz123", "Lia Cardona"))
        assertEquals(listOf(candidates[0]), MentionParser.suggestions("lia", candidates))
    }

    @Test fun empty_fragment_matches_every_candidate() {
        val candidates = listOf(candidate("u1", "sebas"), candidate("u2", "lia"))
        assertEquals(candidates, MentionParser.suggestions("", candidates))
    }

    @Test fun suggestions_are_capped_at_the_limit() {
        val candidates = (1..8).map { candidate("u$it", "h$it") }
        assertEquals(5, MentionParser.suggestions("h", candidates, limit = 5).size)
    }

    @Test fun no_matching_candidates_yields_empty_suggestions() {
        val candidates = listOf(candidate("u1", "sebas"))
        assertEquals(emptyList(), MentionParser.suggestions("zzz", candidates))
    }

    // --- applySuggestion -----------------------------------------------------------------------

    @Test fun apply_suggestion_replaces_the_trailing_fragment_with_the_full_handle_and_a_space() {
        assertEquals("hi @sebas ", MentionParser.applySuggestion("hi @se", "sebas"))
    }

    @Test fun apply_suggestion_on_bare_at_inserts_the_handle() {
        assertEquals("@sebas ", MentionParser.applySuggestion("@", "sebas"))
    }

    @Test fun apply_suggestion_only_touches_the_last_at_occurrence() {
        assertEquals("ping @foo and @sebas ", MentionParser.applySuggestion("ping @foo and @se", "sebas"))
    }

    @Test fun apply_suggestion_is_a_no_op_without_an_at_sign() {
        assertEquals("no mention", MentionParser.applySuggestion("no mention", "sebas"))
    }
}
