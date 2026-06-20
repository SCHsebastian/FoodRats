package es.schsebastian.foodrats.core.domain.moderation

/**
 * Pure, on-device [TextModerationPort] over a curated en/es wordlist ([Wordlists]).
 *
 * Algorithm (deterministic, allocation-light, no regex — so no catastrophic backtracking):
 * 1. **Normalize** the input: lowercase, strip diacritics (`á→a`, `ñ→n`, `ü→u`), fold common leetspeak
 *    digits/symbols to letters (`1→i 3→e 0→o 4→a @→a $→s 5→s 7→t`), then collapse runs of 3+ of the
 *    same letter to one (`fuuuuck→fuck`, `shiiiit→shit`) while leaving doubles like `ss` intact.
 * 2. **Tokenize** on non-letter boundaries into whole words.
 * 3. **Whole-word match** each token against single-word terms, and a sliding window against multi-word
 *    terms. Word-boundary matching is what avoids the "Scunthorpe problem" — `assassin`, `class`,
 *    `cocktail`, `clase` all pass because a term is never a substring of a larger token.
 * 4. Ties resolve by category severity (`HATE > SEXUAL > HARASSMENT > VIOLENCE > PROFANITY`) so the most
 *    severe hit wins.
 *
 * The wordlist is conservative (precision over recall): this is an advisory-grade FIRST-PASS deterrent,
 * not a guarantee. The report/block paths are the safety net.
 *
 * @param termsForLanguage resolves the active term set for a BCP-47 language tag. Defaults to the bundled
 *   [Wordlists]; injectable so tests can pin a known set. Terms are normalized at evaluation so the list
 *   may be authored with natural spelling/diacritics.
 */
class WordlistTextModeration(
    private val termsForLanguage: (String) -> Map<String, ModerationCategory> = Wordlists::forLanguage,
) : TextModerationPort {

    override fun evaluate(text: String, languageTag: String): TextModerationVerdict {
        if (text.isBlank()) return TextModerationVerdict.Clean

        val tokens = tokenize(normalize(text))
        if (tokens.isEmpty()) return TextModerationVerdict.Clean

        // Normalize the term set so the list can be authored with natural spelling/diacritics.
        val terms = termsForLanguage(languageTag)
            .mapKeys { (term, _) -> normalize(term).trim() }
            .filterKeys { it.isNotEmpty() }

        val singleWord = terms.filterKeys { !it.contains(' ') }
        val multiWord = terms.filterKeys { it.contains(' ') }
            .mapKeys { (term, _) -> term.split(' ').filter { it.isNotEmpty() } }

        val hits = mutableListOf<Pair<String, ModerationCategory>>()

        // Single-word: whole-token equality (never substring → no Scunthorpe).
        for (token in tokens) {
            singleWord[token]?.let { hits += token to it }
        }

        // Multi-word: contiguous token-sequence match.
        for ((phraseTokens, category) in multiWord) {
            if (containsSequence(tokens, phraseTokens)) {
                hits += phraseTokens.joinToString(" ") to category
            }
        }

        if (hits.isEmpty()) return TextModerationVerdict.Clean

        val severe = hits.minByOrNull { SEVERITY.indexOf(it.second) }!!.second
        return TextModerationVerdict.Objectionable(
            category = severe,
            terms = hits.map { it.first }.distinct(),
        )
    }

    private fun normalize(input: String): String {
        // First map each char (lowercase → strip diacritic → fold leetspeak).
        val mapped = buildString(input.length) {
            for (raw in input.lowercase()) append(foldLeet(stripDiacritic(raw)))
        }
        // Then collapse runs: a run of length >= 3 of the same char folds to one
        // ("shiiiit" -> "shit", "fuuuuck" -> "fuck"); doubles survive so a legit "ss" in "ass" stays.
        val sb = StringBuilder(mapped.length)
        var i = 0
        while (i < mapped.length) {
            val c = mapped[i]
            var run = 1
            while (i + run < mapped.length && mapped[i + run] == c) run++
            val keep = if (run >= 3) 1 else run
            repeat(keep) { sb.append(c) }
            i += run
        }
        return sb.toString()
    }

    /** Maps a single accented Latin letter to its base form; ñ→n. Leaves everything else untouched. */
    private fun stripDiacritic(c: Char): Char = when (c) {
        'á', 'à', 'ä', 'â', 'ã', 'å' -> 'a'
        'é', 'è', 'ë', 'ê' -> 'e'
        'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
        'ú', 'ù', 'ü', 'û' -> 'u'
        'ñ' -> 'n'
        'ç' -> 'c'
        else -> c
    }

    /** Folds common leetspeak digit/symbol substitutions to their letter. */
    private fun foldLeet(c: Char): Char = when (c) {
        '1', '!' -> 'i'
        '3' -> 'e'
        '0' -> 'o'
        '4', '@' -> 'a'
        '$', '5' -> 's'
        '7' -> 't'
        else -> c
    }

    /** Splits on any non-letter (digits already folded to letters survive; leftover digits split). */
    private fun tokenize(normalized: String): List<String> =
        normalized.split(*NON_LETTER).filter { it.isNotEmpty() }

    private fun containsSequence(tokens: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty() || phrase.size > tokens.size) return false
        outer@ for (start in 0..tokens.size - phrase.size) {
            for (i in phrase.indices) {
                if (tokens[start + i] != phrase[i]) continue@outer
            }
            return true
        }
        return false
    }

    private companion object {
        /** Most-severe first; index drives the tie-break. */
        val SEVERITY = listOf(
            ModerationCategory.HATE,
            ModerationCategory.SEXUAL,
            ModerationCategory.HARASSMENT,
            ModerationCategory.VIOLENCE,
            ModerationCategory.PROFANITY,
        )

        /** Token delimiters. Everything that is not a-z (post-normalization) splits words. */
        val NON_LETTER: CharArray = (
            " \t\n\r.,;:!?\"'`~^()[]{}<>/\\|#%&*-+=_…—–·•".toCharArray() +
                ('0'..'9').toList().toCharArray()
        )
    }
}
