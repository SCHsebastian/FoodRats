package es.schsebastian.foodrats.feature.ingredient.domain.usecase

import es.schsebastian.foodrats.core.domain.meal.Ingredient
import kotlin.math.abs

/**
 * Folds a search string into a comparison-ready form so matching ignores everything
 * a user shouldn't have to type exactly:
 *
 * - **case** — `lowercase`d, so "TOMATE" == "tomate";
 * - **accents/diacritics** — `á é í ó ú ü ñ ç …` folded to their base letter, so
 *   "platano" reaches "Plátano" and "jalapeno" reaches "Jalapeño";
 * - **punctuation & symbols** — anything that isn't a letter or digit becomes a
 *   single word break, so "crème-fraîche!" and "creme fraiche" normalize alike;
 * - **whitespace** — runs collapse to one space, with leading/trailing trimmed.
 *
 * Pure `commonMain` (no `java.text.Normalizer`) so it behaves identically on Android
 * and iOS. The fold table covers the Latin diacritics the en/es catalog actually uses.
 */
internal fun normalizeForSearch(input: String): String {
    val sb = StringBuilder(input.length)
    var pendingSpace = false
    for (raw in input) {
        val folded = foldDiacritic(raw.lowercaseChar())
        if (folded.isLetterOrDigit()) {
            if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
            pendingSpace = false
            sb.append(folded)
        } else {
            // Any non-alphanumeric (space, punctuation, symbol) is a word break.
            pendingSpace = true
        }
    }
    return sb.toString()
}

private fun foldDiacritic(c: Char): Char = when (c) {
    'á', 'à', 'ä', 'â', 'ã', 'å', 'ā' -> 'a'
    'é', 'è', 'ë', 'ê', 'ē'           -> 'e'
    'í', 'ì', 'ï', 'î', 'ī'           -> 'i'
    'ó', 'ò', 'ö', 'ô', 'õ', 'ō'      -> 'o'
    'ú', 'ù', 'ü', 'û', 'ū'           -> 'u'
    'ñ'                               -> 'n'
    'ç'                               -> 'c'
    'ý', 'ÿ'                          -> 'y'
    else                             -> c
}

/**
 * A normalization-folded, precomputed search index over a catalog snapshot.
 *
 * Folding every ingredient's name + aliases **once** (when the catalog changes) keeps
 * each keystroke an O(catalog) walk of cheap string comparisons rather than re-folding
 * the whole catalog on every character typed.
 *
 * Matching is typo-tolerant: each query word must hit some token of an ingredient by
 * substring **or** by a short bounded edit distance ("tomate" ~ "tomatto"), so similar
 * words still surface. Results are ranked best-first; the picker re-buckets them by
 * category for display, preserving that order within each group.
 */
class IngredientSearchIndex private constructor(
    private val entries: List<Entry>,
) {
    private class Entry(
        val ingredient: Ingredient,
        val haystack: String,      // normalized name + aliases, space-joined
        val tokens: List<String>,  // distinct normalized words
    )

    /** Every ingredient in catalog order — the result for a blank query. */
    val all: List<Ingredient> get() = entries.map { it.ingredient }

    fun search(rawQuery: String): List<Ingredient> {
        val query = normalizeForSearch(rawQuery)
        if (query.isEmpty()) return all
        val terms = query.split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return all

        return entries
            .mapNotNull { entry ->
                val score = score(entry, query, terms)
                if (score > 0) entry to score else null
            }
            .sortedWith(
                compareByDescending<Pair<Entry, Int>> { it.second }
                    .thenBy { it.first.ingredient.displayName },
            )
            .map { it.first.ingredient }
    }

    /** 0 means "no match" — a single unmatched term disqualifies the ingredient. */
    private fun score(entry: Entry, fullQuery: String, terms: List<String>): Int {
        var score = 0
        // Whole-phrase containment is the strongest signal (handles multi-word names).
        if (entry.haystack.contains(fullQuery)) {
            score += WHOLE_PHRASE
            if (entry.haystack.startsWith(fullQuery)) score += PREFIX_BONUS
        }
        // AND semantics: every typed word must land somewhere, so queries narrow.
        for (term in terms) {
            val best = entry.tokens.maxOfOrNull { tokenScore(it, term) } ?: 0
            if (best == 0) return 0
            score += best
        }
        return score
    }

    private fun tokenScore(token: String, term: String): Int = when {
        token == term            -> EXACT
        token.startsWith(term)   -> PREFIX
        token.contains(term)     -> CONTAINS
        else -> {
            val dist = boundedDistance(token, term, maxFuzzyDistance(term.length))
            if (dist < 0) 0 else (FUZZY_BASE - dist).coerceAtLeast(1)
        }
    }

    companion object {
        private const val WHOLE_PHRASE = 100
        private const val PREFIX_BONUS = 30
        private const val EXACT = 40
        private const val PREFIX = 28
        private const val CONTAINS = 18
        private const val FUZZY_BASE = 10

        /** Longer words tolerate more typos; very short ones tolerate none (too noisy). */
        private fun maxFuzzyDistance(len: Int): Int = when {
            len <= 3 -> 0
            len <= 6 -> 1
            else     -> 2
        }

        fun from(catalog: Collection<Ingredient>): IngredientSearchIndex {
            val entries = catalog.map { ing ->
                val haystack = normalizeForSearch(
                    (listOf(ing.displayName) + ing.aliases).joinToString(" "),
                )
                val tokens = haystack.split(' ').filter { it.isNotEmpty() }.distinct()
                Entry(ing, haystack, tokens)
            }
            return IngredientSearchIndex(entries)
        }
    }
}

/**
 * Levenshtein edit distance, capped at [max]: returns the distance when it is `<= max`,
 * or `-1` as soon as it provably exceeds [max] (length gap or whole-row minimum past the
 * cap). The cap lets us bail early instead of computing the full matrix for far-apart
 * words — the common case while typing.
 */
internal fun boundedDistance(a: String, b: String, max: Int): Int {
    if (max <= 0) return if (a == b) 0 else -1
    val la = a.length
    val lb = b.length
    if (abs(la - lb) > max) return -1
    if (la == 0) return if (lb <= max) lb else -1
    if (lb == 0) return if (la <= max) la else -1

    var prev = IntArray(lb + 1) { it }
    var curr = IntArray(lb + 1)
    for (i in 1..la) {
        curr[0] = i
        var rowMin = curr[0]
        val ca = a[i - 1]
        for (j in 1..lb) {
            val cost = if (ca == b[j - 1]) 0 else 1
            val d = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            curr[j] = d
            if (d < rowMin) rowMin = d
        }
        if (rowMin > max) return -1
        val swap = prev; prev = curr; curr = swap
    }
    val dist = prev[lb]
    return if (dist <= max) dist else -1
}
