package es.schsebastian.foodrats.core.domain.moderation

/**
 * The curated en/es term sets driving [WordlistTextModeration]. Kept `internal` so the actual terms do
 * not leak into public API or generated docs.
 *
 * This is a deliberately MODEST, CONSERVATIVE list — a real but advisory-grade first-pass deterrent
 * that favors precision (few false positives) over recall (catching everything). It is NOT a guarantee:
 * the report (§4) and block (§5) paths are the safety net for anything that slips through. Terms are
 * stored in their already-normalized form (lowercase, no diacritics) so they compare directly against
 * the tokenizer output in [WordlistTextModeration]. Multi-word terms use a single space.
 */
internal object Wordlists {

    /** English terms. */
    val EN: Map<String, ModerationCategory> = mapOf(
        // PROFANITY
        "fuck" to ModerationCategory.PROFANITY,
        "fucking" to ModerationCategory.PROFANITY,
        "shit" to ModerationCategory.PROFANITY,
        "bullshit" to ModerationCategory.PROFANITY,
        "asshole" to ModerationCategory.PROFANITY,
        "bitch" to ModerationCategory.PROFANITY,
        "bastard" to ModerationCategory.PROFANITY,
        "dickhead" to ModerationCategory.PROFANITY,
        // SEXUAL
        "cunt" to ModerationCategory.SEXUAL,
        "whore" to ModerationCategory.SEXUAL,
        "slut" to ModerationCategory.SEXUAL,
        // HARASSMENT
        "loser" to ModerationCategory.HARASSMENT,
        "moron" to ModerationCategory.HARASSMENT,
        "idiot" to ModerationCategory.HARASSMENT,
        "scum" to ModerationCategory.HARASSMENT,
        "worthless" to ModerationCategory.HARASSMENT,
        // VIOLENCE
        "kill yourself" to ModerationCategory.VIOLENCE,
        "kys" to ModerationCategory.VIOLENCE,
    )

    /** Spanish terms. */
    val ES: Map<String, ModerationCategory> = mapOf(
        // PROFANITY
        "mierda" to ModerationCategory.PROFANITY,
        "joder" to ModerationCategory.PROFANITY,
        "cabron" to ModerationCategory.PROFANITY,
        "gilipollas" to ModerationCategory.PROFANITY,
        "pendejo" to ModerationCategory.PROFANITY,
        "coño" to ModerationCategory.PROFANITY,
        "mamon" to ModerationCategory.PROFANITY,
        // SEXUAL
        "puta" to ModerationCategory.SEXUAL,
        "puto" to ModerationCategory.SEXUAL,
        "zorra" to ModerationCategory.SEXUAL,
        // HARASSMENT
        "imbecil" to ModerationCategory.HARASSMENT,
        "idiota" to ModerationCategory.HARASSMENT,
        "inutil" to ModerationCategory.HARASSMENT,
        "estupido" to ModerationCategory.HARASSMENT,
        // VIOLENCE
        "matate" to ModerationCategory.VIOLENCE,
    )

    /**
     * Cross-language slurs / hate terms that read identically (or near-identically) in en + es and are
     * therefore always screened regardless of the active language. Kept intentionally short; these are
     * the highest-severity ([ModerationCategory.HATE]) hits.
     */
    val NEUTRAL: Map<String, ModerationCategory> = mapOf(
        "nazi" to ModerationCategory.HATE,
        "faggot" to ModerationCategory.HATE,
        "retard" to ModerationCategory.HATE,
        "maricon" to ModerationCategory.HATE,
    )

    /** The active term set for [tag]: language-specific terms ∪ the always-on neutral set. */
    fun forLanguage(tag: String): Map<String, ModerationCategory> =
        when (tag.lowercase().take(2)) {
            "es" -> ES + NEUTRAL
            else -> EN + NEUTRAL // default English
        }
}
