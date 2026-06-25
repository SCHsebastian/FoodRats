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
 *
 * **Expansion policy:** add a term only when (a) it is unambiguously objectionable in context, (b) the
 * normalized form does not collide with any common benign word, and (c) it is genuinely evasion-prone
 * (i.e. the separator-evasion / leet-fold improvements alone are not enough). Do not add short
 * ambiguous roots — prefer exact inflected forms.
 */
internal object Wordlists {

    /** English terms. */
    val EN: Map<String, ModerationCategory> = mapOf(
        // PROFANITY
        "fuck" to ModerationCategory.PROFANITY,
        "fucking" to ModerationCategory.PROFANITY,
        "fucked" to ModerationCategory.PROFANITY,
        "fucker" to ModerationCategory.PROFANITY,
        "motherfucker" to ModerationCategory.PROFANITY,
        "shit" to ModerationCategory.PROFANITY,
        "bullshit" to ModerationCategory.PROFANITY,
        "shitty" to ModerationCategory.PROFANITY,
        "asshole" to ModerationCategory.PROFANITY,
        "bitch" to ModerationCategory.PROFANITY,
        "bitches" to ModerationCategory.PROFANITY,
        "bastard" to ModerationCategory.PROFANITY,
        "dickhead" to ModerationCategory.PROFANITY,
        "cock" to ModerationCategory.PROFANITY,
        "prick" to ModerationCategory.PROFANITY,
        "wanker" to ModerationCategory.PROFANITY,
        "jackass" to ModerationCategory.PROFANITY,
        "dumbass" to ModerationCategory.PROFANITY,
        // SEXUAL
        "cunt" to ModerationCategory.SEXUAL,
        "whore" to ModerationCategory.SEXUAL,
        "slut" to ModerationCategory.SEXUAL,
        "dick" to ModerationCategory.SEXUAL,
        "pussy" to ModerationCategory.SEXUAL,
        // HARASSMENT
        "loser" to ModerationCategory.HARASSMENT,
        "moron" to ModerationCategory.HARASSMENT,
        "idiot" to ModerationCategory.HARASSMENT,
        "scum" to ModerationCategory.HARASSMENT,
        "worthless" to ModerationCategory.HARASSMENT,
        "dipshit" to ModerationCategory.HARASSMENT,
        "numskull" to ModerationCategory.HARASSMENT,
        // HATE
        "nigger" to ModerationCategory.HATE,
        "nigga" to ModerationCategory.HATE,
        "spic" to ModerationCategory.HATE,
        "chink" to ModerationCategory.HATE,
        "kike" to ModerationCategory.HATE,
        "wetback" to ModerationCategory.HATE,
        // VIOLENCE
        "kill yourself" to ModerationCategory.VIOLENCE,
        "kys" to ModerationCategory.VIOLENCE,
        "go kill yourself" to ModerationCategory.VIOLENCE,
    )

    /** Spanish terms. */
    val ES: Map<String, ModerationCategory> = mapOf(
        // PROFANITY
        "mierda" to ModerationCategory.PROFANITY,
        "joder" to ModerationCategory.PROFANITY,
        "cabron" to ModerationCategory.PROFANITY,
        "cabrona" to ModerationCategory.PROFANITY,
        "gilipollas" to ModerationCategory.PROFANITY,
        "pendejo" to ModerationCategory.PROFANITY,
        "pendeja" to ModerationCategory.PROFANITY,
        "mamon" to ModerationCategory.PROFANITY,
        "mamona" to ModerationCategory.PROFANITY,
        "hostia" to ModerationCategory.PROFANITY,
        "putada" to ModerationCategory.PROFANITY,
        "chingado" to ModerationCategory.PROFANITY,
        "chingada" to ModerationCategory.PROFANITY,
        "verga" to ModerationCategory.PROFANITY,
        // SEXUAL
        "puta" to ModerationCategory.SEXUAL,
        "puto" to ModerationCategory.SEXUAL,
        "zorra" to ModerationCategory.SEXUAL,
        "zorras" to ModerationCategory.SEXUAL,
        "fulana" to ModerationCategory.SEXUAL,
        // HARASSMENT
        "imbecil" to ModerationCategory.HARASSMENT,
        "idiota" to ModerationCategory.HARASSMENT,
        "inutil" to ModerationCategory.HARASSMENT,
        "estupido" to ModerationCategory.HARASSMENT,
        "estupida" to ModerationCategory.HARASSMENT,
        "tarado" to ModerationCategory.HARASSMENT,
        "tarada" to ModerationCategory.HARASSMENT,
        "pelotudo" to ModerationCategory.HARASSMENT,
        "pelotuda" to ModerationCategory.HARASSMENT,
        "baboso" to ModerationCategory.HARASSMENT,
        // VIOLENCE
        "matate" to ModerationCategory.VIOLENCE,
        "mataos" to ModerationCategory.VIOLENCE,
    )

    /**
     * Cross-language slurs / hate terms that read identically (or near-identically) in en + es and are
     * therefore always screened regardless of the active language. Kept intentionally short; these are
     * the highest-severity ([ModerationCategory.HATE]) hits.
     */
    val NEUTRAL: Map<String, ModerationCategory> = mapOf(
        "nazi" to ModerationCategory.HATE,
        "nazis" to ModerationCategory.HATE,
        "faggot" to ModerationCategory.HATE,
        "fag" to ModerationCategory.HATE,
        "retard" to ModerationCategory.HATE,
        "retarded" to ModerationCategory.HATE,
        "maricon" to ModerationCategory.HATE,
        "maricona" to ModerationCategory.HATE,
    )

    /**
     * Every supported language's terms ∪ the always-on neutral set. This is the DEFAULT screen: the
     * on-device filter checks ALL supported languages regardless of the active UI/device language, so
     * objectionable content is caught even when the writer's language differs from the device/UI language
     * (e.g. a Spanish comment on a System/English-locale device).
     */
    val ALL: Map<String, ModerationCategory> = EN + ES + NEUTRAL

    /** The active term set for [tag]: language-specific terms ∪ the always-on neutral set. */
    fun forLanguage(tag: String): Map<String, ModerationCategory> =
        when (tag.lowercase().take(2)) {
            "es" -> ES + NEUTRAL
            else -> EN + NEUTRAL // default English
        }
}
