package es.schsebastian.foodrats.core.domain.moderation

/**
 * Pure, on-device [TextModerationPort] over a curated en/es wordlist ([Wordlists]).
 *
 * ## Normalization pipeline (applied once per input character, once per term at construction time)
 *
 * 1. **Lowercase** the input.
 * 2. **Drop zero-width / format characters** — U+200B (ZWSP), U+200C (ZWNJ), U+200D (ZWJ),
 *    U+FEFF (BOM), U+00AD (soft hyphen), U+200E/U+200F (LRM/RLM), U+202A–U+202E (bidi embeds).
 *    These are the most common invisible-character bypass vectors (`fu​ck` with ZWSP → `fuck`).
 * 3. **Fullwidth → ASCII** fold (U+FF01–U+FF5E → U+0021–U+007E). Catches `ｆｕｃｋ`.
 * 4. **Homoglyph fold** — curated Cyrillic/Greek look-alikes of Latin letters (e.g. Cyrillic `а→a`,
 *    `е→e`, `о→o`; Greek `α→a`, `ο→o`, `ε→e`). Conservative set to keep false-positives out.
 * 5. **Diacritic → base form** — two mechanisms:
 *    - Explicit precomposed map covering full Latin-1 + Latin-Extended-A (`á→a`, `ñ→n`, `ü→u`, …).
 *    - Drop combining diacritical marks (U+0300–U+036F) so decomposed NFD input (base + combining)
 *      also folds to the base letter. No java.text.Normalizer needed.
 * 6. **Leet fold** — digit/symbol substitutions to letters (`1→i 3→e 0→o 4→a @→a $→s 5→s 7→t`).
 * 7. **Collapse runs** of ≥ 3 identical characters to one (`fuuuuck→fuck`, `shiiiit→shit`). Doubles
 *    survive so `asshole` stays `asshole`.
 *
 * ## Tokenization + separator-evasion defeat
 *
 * 8. **Tokenize** on non-letter boundaries → list of tokens.
 * 9. **Merge single-char runs**: consecutive single-character tokens are combined into a candidate word
 *    and matched as a whole word. E.g. `["m","i","e","r","d","a"]` → `"mierda"`. This defeats
 *    separator-injected evasion (`m i e r d a`, `f.u.c.k`, `s_h_i_t`).
 *
 *    **False-positive safety**: only *single-char* tokens are merged. A normal word like `"class"` or
 *    `"assistant"` tokenizes as a single multi-char token and is never merged or substring-matched.
 *    "grape", "Scunthorpe", "therapist", "analysis" likewise remain a single multi-char token each and
 *    are never joined into anything that hits the wordlist.
 *
 * ## Matching
 *
 * 10. **Whole-word match** each token (and each merged candidate) against single-word terms — exact
 *     equality, never a substring check. Avoids the Scunthorpe problem.
 * 11. **Multi-word (phrase) match** via a contiguous token-sequence check on the original token list.
 * 12. **Severity** — most-severe category wins (`HATE > SEXUAL > HARASSMENT > VIOLENCE > PROFANITY`).
 *
 * The wordlist is conservative (precision over recall): this is an advisory-grade FIRST-PASS deterrent,
 * not a guarantee. The report (§4) and block (§5) paths are the safety net for anything that slips
 * through.
 *
 * @param termsForLanguage resolves the term set for a BCP-47 language tag. Defaults to [Wordlists.ALL]
 *   — i.e. screens EVERY supported language regardless of the active UI/device language. Terms are
 *   normalized once at construction time so the list may be authored with natural spelling/diacritics.
 */
class WordlistTextModeration(
    private val termsForLanguage: (String) -> Map<String, ModerationCategory> = { Wordlists.ALL },
) : TextModerationPort {

    /**
     * Normalized term maps memoized by language tag. The inner [TermMaps] struct holds the
     * single-word and multi-word partitions so [evaluate] performs zero per-invocation normalization
     * of the wordlist.
     *
     * Thread-safety via copy-on-write: [termCache] is a plain `val`-reference to an immutable
     * `Map`. [termsFor] replaces the whole reference atomically (`termCache = termCache + …`) rather
     * than mutating in place. A race at most causes two concurrent callers to both build the same
     * [TermMaps] and then one assignment wins — both values are identical so the lost update is
     * harmless and produces no corruption. No locks needed on JVM or Kotlin/Native.
     */
    private var termCache: Map<String, TermMaps> = emptyMap()

    private fun termsFor(languageTag: String): TermMaps {
        termCache[languageTag]?.let { return it }
        val raw = termsForLanguage(languageTag)
        val normalized = raw
            .mapKeys { (term, _) -> normalize(term).trim() }
            .filterKeys { it.isNotEmpty() }
        val single = normalized.filterKeys { !it.contains(' ') }
        val multi = normalized
            .filterKeys { it.contains(' ') }
            .mapKeys { (term, _) -> term.split(' ').filter { it.isNotEmpty() } }
        val maps = TermMaps(single, multi)
        termCache = termCache + (languageTag to maps)   // replace immutable ref; never mutate in place
        return maps
    }

    private data class TermMaps(
        val singleWord: Map<String, ModerationCategory>,
        val multiWord: Map<List<String>, ModerationCategory>,
    )

    override fun evaluate(text: String, languageTag: String): TextModerationVerdict {
        if (text.isBlank()) return TextModerationVerdict.Clean

        val tokens = tokenize(normalize(text))
        if (tokens.isEmpty()) return TextModerationVerdict.Clean

        val (singleWord, multiWord) = termsFor(languageTag)

        val hits = mutableListOf<Pair<String, ModerationCategory>>()

        // Single-word: whole-token equality (never substring → no Scunthorpe).
        for (token in tokens) {
            singleWord[token]?.let { hits += token to it }
        }

        // Separator-evasion defeat: merge runs of consecutive single-char tokens into candidate words
        // and test those candidates as whole words. Only single-char runs are merged — a multi-char
        // token like "class" is never part of a merge, so there are no false positives.
        for (candidate in mergedSingleCharRuns(tokens)) {
            singleWord[candidate]?.let { hits += candidate to it }
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

    /**
     * Full normalization pipeline: lowercase → drop zero-width/format chars →
     * fullwidth-fold → homoglyph-fold → diacritic-strip (precomposed map + combining-mark drop) →
     * leet-fold → collapse runs of ≥ 3 identical chars.
     */
    private fun normalize(input: String): String {
        val mapped = buildString(input.length) {
            for (raw in input.lowercase()) {
                // H1: drop invisible zero-width and format characters — most common bypass vector.
                if (isZeroWidthOrFormat(raw)) continue
                val ascii = foldFullwidth(raw)
                val noHomoglyph = foldHomoglyph(ascii)
                val noDiacritic = stripDiacritic(noHomoglyph)
                // H2: drop combining diacritical marks left over from NFD-decomposed input.
                if (isCombiningDiacritic(noDiacritic)) continue
                append(foldLeet(noDiacritic))
            }
        }
        // Collapse runs of length ≥ 3 to one ("shiiiit" → "shit"); keep doubles ("ss" stays "ss").
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

    /**
     * Returns true for zero-width and format characters that are invisible in rendered text and used
     * to evade keyword matching (`fu​ck` with U+200B zero-width space → `fuck` after dropping).
     *
     * Dropped set:
     * - U+200B ZERO WIDTH SPACE
     * - U+200C ZERO WIDTH NON-JOINER
     * - U+200D ZERO WIDTH JOINER
     * - U+FEFF ZERO WIDTH NO-BREAK SPACE / BOM
     * - U+00AD SOFT HYPHEN (invisible in most renderers)
     * - U+200E LEFT-TO-RIGHT MARK / U+200F RIGHT-TO-LEFT MARK
     * - U+202A–U+202E BIDI EMBEDDING / OVERRIDE controls
     */
    private fun isZeroWidthOrFormat(c: Char): Boolean {
        val code = c.code
        return code == 0x200B || code == 0x200C || code == 0x200D ||
            code == 0xFEFF || code == 0x00AD ||
            code == 0x200E || code == 0x200F ||
            code in 0x202A..0x202E
    }

    /**
     * Returns true for Unicode combining diacritical marks (U+0300–U+036F). Dropping these after
     * [stripDiacritic] handles NFD-decomposed input where an accented letter arrives as a base
     * character followed by a combining accent (e.g. `e` + U+0301 → `e` after the combining mark
     * is dropped). Precomposed forms (U+00E9 `é`) are already handled by [stripDiacritic].
     */
    private fun isCombiningDiacritic(c: Char): Boolean {
        val code = c.code
        return code in 0x0300..0x036F
    }

    /**
     * Folds a Unicode fullwidth Latin character (U+FF01–U+FF5E) to its ASCII equivalent
     * (U+0021–U+007E). Catches evasion like `ｆｕｃｋ`.
     */
    private fun foldFullwidth(c: Char): Char {
        val code = c.code
        return if (code in 0xFF01..0xFF5E) (code - 0xFEE0).toChar() else c
    }

    /**
     * Curated homoglyph fold for the most common Cyrillic and Greek look-alikes of Latin letters.
     * Conservative — only visually near-identical pairs to keep false-positives out.
     */
    private fun foldHomoglyph(c: Char): Char = when (c) {
        // Cyrillic look-alikes (post-lowercase)
        'а' -> 'a'  // U+0430 CYRILLIC SMALL LETTER A
        'е' -> 'e'  // U+0435 CYRILLIC SMALL LETTER IE
        'о' -> 'o'  // U+043E CYRILLIC SMALL LETTER O
        'р' -> 'p'  // U+0440 CYRILLIC SMALL LETTER ER
        'с' -> 'c'  // U+0441 CYRILLIC SMALL LETTER ES
        'х' -> 'x'  // U+0445 CYRILLIC SMALL LETTER HA
        'і' -> 'i'  // U+0456 CYRILLIC SMALL LETTER BYELORUSSIAN-UKRAINIAN I
        'ѕ' -> 's'  // U+0455 CYRILLIC SMALL LETTER DZE
        'ј' -> 'j'  // U+0458 CYRILLIC SMALL LETTER JE
        'υ' -> 'u'  // U+03C5 GREEK SMALL LETTER UPSILON (looks like u in some fonts)
        // Greek look-alikes
        'α' -> 'a'  // U+03B1 GREEK SMALL LETTER ALPHA
        'ο' -> 'o'  // U+03BF GREEK SMALL LETTER OMICRON
        'ε' -> 'e'  // U+03B5 GREEK SMALL LETTER EPSILON
        'ν' -> 'v'  // U+03BD GREEK SMALL LETTER NU
        'κ' -> 'k'  // U+03BA GREEK SMALL LETTER KAPPA
        'β' -> 'b'  // U+03B2 GREEK SMALL LETTER BETA
        'τ' -> 't'  // U+03C4 GREEK SMALL LETTER TAU
        'η' -> 'n'  // U+03B7 GREEK SMALL LETTER ETA
        'μ' -> 'm'  // U+03BC GREEK SMALL LETTER MU
        else -> c
    }

    /**
     * Maps a precomposed accented Latin letter to its ASCII base form. Covers Latin-1 Supplement
     * (U+00C0–U+00FF) and Latin Extended-A (U+0100–U+017F) — every accented letter in those ranges.
     * Applied after lowercase so only lowercase forms need to be listed.
     *
     * Decomposed (NFD) input is handled separately by [isCombiningDiacritic] dropping the combining
     * mark after this function returns the unchanged base character.
     */
    private fun stripDiacritic(c: Char): Char = when (c) {
        // a-variants
        'á', 'à', 'ä', 'â', 'ã', 'å', 'ā', 'ă', 'ą', 'æ' -> 'a'
        // c-variants
        'ç', 'ć', 'ĉ', 'ċ', 'č' -> 'c'
        // d-variants
        'ď', 'đ' -> 'd'
        // e-variants
        'é', 'è', 'ë', 'ê', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> 'e'
        // g-variants
        'ĝ', 'ğ', 'ġ', 'ģ' -> 'g'
        // h-variants
        'ĥ', 'ħ' -> 'h'
        // i-variants
        'í', 'ì', 'ï', 'î', 'ĩ', 'ī', 'ĭ', 'į', 'ı', 'ĳ' -> 'i'
        // j-variants
        'ĵ' -> 'j'
        // k-variants
        'ķ' -> 'k'
        // l-variants
        'ĺ', 'ļ', 'ľ', 'ŀ', 'ł' -> 'l'
        // n-variants
        'ñ', 'ń', 'ņ', 'ň', 'ŋ' -> 'n'
        // o-variants
        'ó', 'ò', 'ö', 'ô', 'õ', 'ø', 'ō', 'ŏ', 'ő', 'œ' -> 'o'
        // r-variants
        'ŕ', 'ŗ', 'ř' -> 'r'
        // s-variants
        'ś', 'ŝ', 'ş', 'š', 'ß' -> 's'
        // t-variants
        'ţ', 'ť', 'ŧ' -> 't'
        // u-variants
        'ú', 'ù', 'ü', 'û', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> 'u'
        // w-variants
        'ŵ' -> 'w'
        // y-variants
        'ý', 'ÿ', 'ŷ' -> 'y'
        // z-variants
        'ź', 'ż', 'ž' -> 'z'
        else -> c
    }

    /** Folds common leetspeak digit/symbol substitutions to their letter equivalent. */
    private fun foldLeet(c: Char): Char = when (c) {
        '1', '!' -> 'i'
        '3' -> 'e'
        '0' -> 'o'
        '4', '@' -> 'a'
        '$', '5' -> 's'
        '7' -> 't'
        else -> c
    }

    /**
     * Splits on any character that is not a lowercase ASCII letter (`a`–`z`) after normalization.
     *
     * Post-normalization the string contains only ASCII a-z, residual digits (2/6/8/9 — the
     * non-leet ones), spaces, and any other character not covered by the fold pipeline (e.g. emoji,
     * symbols). Treating every non-[a-z] char as a delimiter means emoji glued to a term (e.g.
     * `"🍕puta"`) is correctly split into `["puta"]` and caught. Pure ASCII range check —
     * no `Char.isLetter()` and no JVM/Native behavioural differences.
     */
    private fun tokenize(normalized: String): List<String> {
        val tokens = mutableListOf<String>()
        val buf = StringBuilder()
        for (c in normalized) {
            if (c in 'a'..'z') {
                buf.append(c)
            } else {
                if (buf.isNotEmpty()) {
                    tokens += buf.toString()
                    buf.clear()
                }
            }
        }
        if (buf.isNotEmpty()) tokens += buf.toString()
        return tokens
    }

    /**
     * Produces candidate words by merging consecutive runs of single-character tokens. Only
     * single-char tokens participate — a multi-char token like `"class"` is a hard boundary that
     * resets the accumulator, so normal prose is completely unaffected.
     *
     * E.g. `["m","i","e","r","d","a"]` → `["mierda"]`
     *      `["f","u","c","k"]` → `["fuck"]`
     *      `["s","h","i","i","i","t"]` → `["shit"]` (run of 3 i's collapses to one)
     *      `["hello","w","o","r","l","d"]` → `["world"]` (resets at "hello")
     *      `["class"]` → `[]` (multi-char; no candidate emitted)
     *
     * **Tradeoff note:** a run of ≥ 2 consecutive single-char tokens is merged and tested as a
     * candidate word. This catches `"m i e r d a"` and `"f.u.c.k"` (separator evasion). The
     * accepted tradeoff: a contrived spaced input like `"a s s"` would merge to `"ass"` and match
     * if "ass" were on the wordlist. This is an accepted precision/recall tradeoff — safe for
     * normal prose because any multi-char token (e.g. `"the"`, `"un"`) resets the accumulator and
     * prevents an accidental merge across word boundaries.
     *
     * A run of length 1 is intentionally NOT emitted — a lone single-char token is already checked
     * as its own token in the normal matching pass and would create spurious single-letter hits.
     *
     * After merging, a second run-collapse is applied (≥ 3 identical adjacent chars → 1) so that
     * separator-injected repeats like `i_i_i` within the merged word also fold correctly.
     */
    private fun mergedSingleCharRuns(tokens: List<String>): List<String> {
        val candidates = mutableListOf<String>()
        val run = StringBuilder()
        for (token in tokens) {
            if (token.length == 1) {
                run.append(token)
            } else {
                // multi-char token: flush any accumulated run (≥ 2 chars to matter)
                if (run.length >= 2) candidates += collapseRuns(run.toString())
                run.clear()
            }
        }
        // flush trailing run
        if (run.length >= 2) candidates += collapseRuns(run.toString())
        return candidates
    }

    /**
     * Collapses runs of ≥ 3 identical characters to one (`"shiiit"` → `"shit"`). Doubles survive.
     * Used as a post-merge step so separator-injected letter repeats are normalized.
     */
    private fun collapseRuns(s: String): String {
        if (s.length < 3) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            var run = 1
            while (i + run < s.length && s[i + run] == c) run++
            val keep = if (run >= 3) 1 else run
            repeat(keep) { sb.append(c) }
            i += run
        }
        return sb.toString()
    }

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
    }
}
