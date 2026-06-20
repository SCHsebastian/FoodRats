package es.schsebastian.foodrats.core.domain.account

/**
 * Typed failures for the per-account block list (UGC compliance — App Store Guideline 1.2 "block
 * abusive users"). Sealed (mirrors the domain-error convention) so call sites exhaust it; nested
 * groups separate the write path (block/unblock) from the read path (observe). No `Unknown` leaf —
 * every realistic failure maps onto `Unavailable` or a domain-specific guard.
 */
sealed interface BlockError {

    /** Failures while adding/removing a block (`block` / `unblock`). */
    sealed interface Write : BlockError {
        /** The owner tried to block themselves. */
        data object SelfBlock : Write

        /** Network / backend unavailable. */
        data object Unavailable : Write
    }

    /** Failures while subscribing to the owner's block list. */
    sealed interface Read : BlockError {
        /** Network / backend unavailable. */
        data object Unavailable : Read
    }
}
