package es.schsebastian.foodrats.core.domain.moderation

/**
 * Coarse buckets the wordlist maps a hit to. A value/dimension (NOT an error tree), so it is an `enum`
 * by design — mirrors the existing `MealSlot` / `ReactionKind` value enums. Used for the user-facing
 * reason + an analytics dimension. Ordered most-severe-first; [WordlistTextModeration] reports the most
 * severe category when a single text trips several.
 */
enum class ModerationCategory { HATE, SEXUAL, HARASSMENT, VIOLENCE, PROFANITY }

/**
 * Result of screening a free-text field for objectionable content. Sealed (domain-error shape) so call
 * sites exhaust it. [Clean] carries nothing; [Objectionable] carries the matched [terms] (normalized,
 * for tests/debug only — NEVER surface them to other users) and the most severe [category] (for the
 * user-facing reason + analytics bucketing — never echo the raw term as PII).
 */
sealed interface TextModerationVerdict {
    data object Clean : TextModerationVerdict
    data class Objectionable(
        val category: ModerationCategory,
        val terms: List<String>,
    ) : TextModerationVerdict
}

/**
 * Screens a single user-supplied text field for objectionable content. Synchronous + pure: no IO, no
 * suspension, no flows — moderation is a CPU classification over a string. Lives in `:core:domain` so
 * every layer (comment ViewModel, composer ViewModel, a future server reuse) can call it directly. It
 * is NOT a repository, so it has no `withContext`.
 *
 * Implemented on-device by [WordlistTextModeration]; a future server-side classifier can implement the
 * same port without touching call sites.
 */
fun interface TextModerationPort {
    /**
     * @param text the user-supplied field to screen.
     * @param languageTag BCP-47 active language ("en", "es"); selects which language-specific wordlist
     *   applies (a small always-on cross-language set is checked regardless). Unrecognized tags fall
     *   back to English.
     */
    fun evaluate(text: String, languageTag: String): TextModerationVerdict
}
