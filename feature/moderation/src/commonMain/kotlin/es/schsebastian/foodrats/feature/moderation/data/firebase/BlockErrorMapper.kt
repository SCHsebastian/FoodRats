package es.schsebastian.foodrats.feature.moderation.data.firebase

import es.schsebastian.foodrats.core.domain.account.BlockError

/**
 * Maps an arbitrary backend [Throwable] to a typed write-path [BlockError]. Raw throwables are first
 * classified into a typed [ModerationFault] by [toModerationFault] (the single message-inspection
 * seam); this mapper then translates **by fault type**, never by message, so a Firebase SDK wording
 * change touches only `toModerationFault`.
 *
 * Block has no domain-specific backend leaf beyond `Unavailable`, so a permission denial / network /
 * unknown fault all collapse to [BlockError.Write.Unavailable] — the only retry-able write failure the
 * UI surfaces. [BlockError.Write.SelfBlock] is a pre-flight guard in the repository, never produced here.
 */
class BlockErrorMapper {
    fun map(t: Throwable): BlockError.Write = when (t.toModerationFault()) {
        ModerationFault.PermissionDenied -> BlockError.Write.Unavailable
        ModerationFault.Network -> BlockError.Write.Unavailable
        ModerationFault.AlreadyExists -> BlockError.Write.Unavailable
        ModerationFault.Unavailable -> BlockError.Write.Unavailable
    }
}
