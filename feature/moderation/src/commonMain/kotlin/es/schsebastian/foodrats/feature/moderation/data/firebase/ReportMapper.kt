package es.schsebastian.foodrats.feature.moderation.data.firebase

import es.schsebastian.foodrats.core.domain.moderation.ReportReason
import es.schsebastian.foodrats.core.domain.moderation.ReportTarget

/** Wire discriminator for the `reason` field; the leaves map 1:1 to the security-rule whitelist (§8.2). */
internal fun ReportReason.toWire(): String = when (this) {
    ReportReason.ChildSafety -> "child_safety"
    ReportReason.Spam -> "spam"
    ReportReason.Harassment -> "harassment"
    ReportReason.Hate -> "hate"
    ReportReason.Sexual -> "sexual"
    ReportReason.Violence -> "violence"
    ReportReason.Other -> "other"
}

/** Wire discriminator for the `targetType` field. */
internal fun ReportTarget.targetType(): String = when (this) {
    is ReportTarget.Meal -> "meal"
    is ReportTarget.Comment -> "comment"
    is ReportTarget.Account -> "account"
}

/**
 * Stable, collision-free key identifying a target across reporters. Combined with the reporter uid it
 * forms the deterministic report-doc id `{reporterUid}|{targetKey}` so a second report of the same
 * target by the same reporter collides (idempotency) while distinct reporters still produce distinct
 * docs (powering the auto-hide distinct-count).
 *
 * The delimiter is `|` (not `_` or `-`): crew ids, account uids, day keys and slots are `[A-Za-z0-9_-]`
 * and a meal id embeds underscores (`crewId_uid_dayKey_slot`), so an underscore- or hyphen-joined key was
 * ambiguous — two distinct (reporter, target) pairs could collide on one doc id, breaking the
 * one-report-per-reporter-per-target invariant the distinct count relies on. None of the components can
 * contain `|`, so the key (and the `reporterUid|targetKey` join) is strictly one-to-one with the target,
 * and the security rule recovers the component ids by string-pinning them back into the key. The rule +
 * the `onReportCreated` trigger + its tests all use this exact scheme.
 */
internal fun ReportTarget.targetKey(): String = when (this) {
    is ReportTarget.Meal -> "meal|${crewId.value}|${mealId.value}"
    is ReportTarget.Comment -> "comment|${crewId.value}|${mealId.value}|${commentId.value}"
    is ReportTarget.Account -> "account|${accountId.value}"
}

/**
 * Builds the wire fields. `targetKey` + `status` are REQUIRED by the create rule (`firestore.rules`
 * reports block): the rule pins `reportId == auth.uid + "|" + targetKey`, requires `status == "open"`,
 * and pins each present id field back into `targetKey` — omitting any of them denies the create.
 * `targetKey` also powers the `onReportCreated` distinct-reporter count + auto-hide. The unused `*Id`
 * fields ride as `null` (GitLive encodes defaults); the rule whitelist permits them.
 */
internal fun ReportTarget.toDto(
    reporterId: String,
    reason: ReportReason,
    nowMs: Long,
): ReportDto = when (this) {
    is ReportTarget.Meal -> ReportDto(
        reporterId = reporterId,
        targetType = targetType(),
        crewId = crewId.value,
        mealId = mealId.value,
        targetKey = targetKey(),
        reason = reason.toWire(),
        status = "open",
        createdAtEpochMs = nowMs,
    )
    is ReportTarget.Comment -> ReportDto(
        reporterId = reporterId,
        targetType = targetType(),
        crewId = crewId.value,
        mealId = mealId.value,
        commentId = commentId.value,
        targetKey = targetKey(),
        reason = reason.toWire(),
        status = "open",
        createdAtEpochMs = nowMs,
    )
    is ReportTarget.Account -> ReportDto(
        reporterId = reporterId,
        targetType = targetType(),
        accountId = accountId.value,
        targetKey = targetKey(),
        reason = reason.toWire(),
        status = "open",
        createdAtEpochMs = nowMs,
    )
}
