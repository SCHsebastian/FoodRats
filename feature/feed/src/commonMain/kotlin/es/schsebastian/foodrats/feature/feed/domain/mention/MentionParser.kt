package es.schsebastian.foodrats.feature.feed.domain.mention

import es.schsebastian.foodrats.core.domain.model.AccountId

/** Extras beyond this are silently dropped — mentions are advisory and never block posting (PLAN.md). */
const val MENTION_PARSE_CAP = 10

/** Default number of rows the composer's suggestion list shows. */
const val MENTION_SUGGESTIONS_LIMIT = 5

/**
 * A crew member the @-mention composer can resolve `@token`s against. [handle] is the match key for
 * both parsing (`@handle` in posted text) and suggestion filtering; [displayName] backs the
 * suggestion list's secondary label and its own prefix-match fallback.
 */
data class MentionCandidate(
    val accountId: AccountId,
    val handle: String,
    val displayName: String,
)

/**
 * Matches an `@token` occurrence anywhere in a string — `@` followed by one or more handle
 * characters. Deliberately permissive on the charset (crew handles are `uid.take(8)`, i.e.
 * alphanumeric, but this stays loose so a false-shaped token just fails to resolve against the
 * roster rather than never being recognized as a candidate token at all).
 */
val MENTION_TOKEN_REGEX = Regex("@([A-Za-z0-9_-]+)")

/**
 * Pure @-mention text parsing shared by [es.schsebastian.foodrats.feature.feed.presentation.detail.MealDetailViewModel]
 * (suggestion state + mention resolution on post/edit) and the comment-row highlighter in
 * `MealDetailScreen`. No I/O, no coroutines.
 */
object MentionParser {

    /**
     * Extracts every `@handle` token in [text] that matches a [candidates] handle (case-insensitive),
     * deduped by account id preserving first-seen order, capped at [MENTION_PARSE_CAP]. A token that
     * doesn't match any known handle is silently ignored (not a mention).
     */
    fun parseMentions(text: String, candidates: List<MentionCandidate>): List<AccountId> {
        if (candidates.isEmpty()) return emptyList()
        val byHandle = candidates.associateBy { it.handle.lowercase() }
        val result = LinkedHashSet<AccountId>()
        for (match in MENTION_TOKEN_REGEX.findAll(text)) {
            if (result.size >= MENTION_PARSE_CAP) break
            byHandle[match.groupValues[1].lowercase()]?.let { result += it.accountId }
        }
        return result.toList()
    }

    /**
     * The `@fragment` currently being typed at the END of [text] (fragment excludes the leading `@`;
     * an empty string means "just typed `@`" — show every candidate). Returns `null` when there's no
     * `@` in [text], or the run after the last `@` contains whitespace (the mention was already closed
     * by a space/newline, so the cursor — assumed at the end of [text] — isn't inside it anymore).
     */
    fun trailingFragment(text: String): String? {
        val at = text.lastIndexOf('@')
        if (at == -1) return null
        val rest = text.substring(at + 1)
        if (rest.any { it.isWhitespace() }) return null
        return rest
    }

    /**
     * Up to [limit] [candidates] whose handle OR display name starts with [fragment]
     * (case-insensitive). An empty [fragment] (bare `@`) matches every candidate.
     */
    fun suggestions(
        fragment: String,
        candidates: List<MentionCandidate>,
        limit: Int = MENTION_SUGGESTIONS_LIMIT,
    ): List<MentionCandidate> {
        val needle = fragment.lowercase()
        return candidates
            .filter { it.handle.lowercase().startsWith(needle) || it.displayName.lowercase().startsWith(needle) }
            .take(limit)
    }

    /**
     * Rewrites [text]'s trailing `@fragment` (see [trailingFragment]) to `@handle ` (trailing space so
     * typing can continue past the inserted mention). No-op (returns [text] unchanged) if [text] has
     * no `@` — callers only invoke this when a suggestion was actually offered, i.e. [trailingFragment]
     * was non-null for the same text.
     */
    fun applySuggestion(text: String, handle: String): String {
        val at = text.lastIndexOf('@')
        if (at == -1) return text
        return text.substring(0, at) + "@" + handle + " "
    }
}
