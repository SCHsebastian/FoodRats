package es.schsebastian.foodrats.core.domain.meal

import es.schsebastian.foodrats.core.domain.model.AccountId

/**
 * Discriminates how a [Meal] is authored.
 *
 * This is a behaviorally-inert seam: today the only live leaf is [Solo] (one author), and every
 * existing and new meal is [Solo] by default — see [Meal.kind]. Nothing branches on this type yet.
 * The seam exists so the designed-but-deferred multi-author kind can be added later as a NEW leaf
 * (and a new data-class payload) WITHOUT migrating [Solo] or changing any current behavior.
 *
 * House convention: `sealed interface` with `data object`/`data class` leaves (never an enum), so a
 * leaf can carry a payload. [Solo] carries none, so it is a `data object`.
 *
 * ## Forward design (DEFERRED — do NOT build here; gated by spec §13 open decisions)
 *
 * The future multi-author kind (working name `Together`, spec §5) slots in as:
 * ```
 * data class Together(val coAuthorIds: Set<AccountId>) : MealKind
 * //   coAuthorIds is the FULL participant set, INCLUDING the creator (Meal.author).
 * ```
 * It is additive: a new leaf + its field. `Solo` is untouched. The `when (kind)` exhaustiveness
 * check locked by the domain tests will then force every consumer to handle the new arm.
 *
 * See `docs/specs/2026-06-14-meal-post-types-design.md` §4.1 / §4.2 (this seam) and §5 (the
 * deferred design).
 */
sealed interface MealKind {

    /** One author — the only live kind today. The author is [Meal.author] / [MealAuthor]. */
    data object Solo : MealKind

    // Future (DEFERRED — spec §5; gated by §13 open decisions):
    //   data class Together(val coAuthorIds: Set<AccountId>) : MealKind
    //   coAuthorIds is the full participant set INCLUDING the creator.
}

/**
 * The full set of accounts that authored a [Meal] of this [MealKind].
 *
 * For [MealKind.Solo] this is exactly one account — the meal's single [author][Meal.author]
 * ([MealAuthor.accountId]). This is the pure encoding of the §4.2 Solo invariant: a Solo meal has
 * exactly one author, and it is the existing `author`. Pass the meal's author id.
 *
 * Kept as a pure function (no side effects, no I/O) so it is trivially testable and so the future
 * [MealKind.Together] arm can return its `coAuthorIds` here when that leaf is added.
 */
fun MealKind.authorIds(authorId: AccountId): Set<AccountId> = when (this) {
    MealKind.Solo -> setOf(authorId)
    // Future: is MealKind.Together -> coAuthorIds
}
