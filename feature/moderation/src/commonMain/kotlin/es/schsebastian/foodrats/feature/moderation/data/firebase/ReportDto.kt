package es.schsebastian.foodrats.feature.moderation.data.firebase

import kotlinx.serialization.Serializable

/**
 * Firestore document at `reports/{reporterUid|targetKey}` (UGC compliance §4). The collection is a
 * server-only-readable moderation queue; clients can only create. The deterministic id (one report per
 * reporter per target) powers the [es.schsebastian.foodrats.core.domain.moderation.ReportError.Submit.AlreadyReported]
 * idempotency and the distinct-reporter count the `onReportCreated` function uses to auto-hide content.
 *
 * [targetType] is the discriminator (`"meal"` / `"comment"` / `"account"`). [targetKey] is the stable,
 * collision-free key the security rules pin against the doc id (`reportId == auth.uid + "|" + targetKey`)
 * and the `onReportCreated` distinct-reporter count queries (`where targetKey == …`) — without it the
 * create rule denies and auto-hide never fires. [status] is server-controlled and MUST be created as
 * `"open"`; the moderation function flips it via the Admin SDK.
 *
 * The `*Id` fields are nullable because which apply depends on the target type; the security rules
 * whitelist exactly these keys AND pin each present id into [targetKey] (so the trigger's delete-by-field
 * provably targets the reported content). GitLive `.set(dto)` serializes with `encodeDefaults = true`, so
 * the unused ids ride as explicit `null` — the rule whitelist tolerates them.
 */
@Serializable
data class ReportDto(
    val reporterId: String = "",
    val targetType: String = "",
    val crewId: String? = null,
    val mealId: String? = null,
    val commentId: String? = null,
    val accountId: String? = null,
    val targetKey: String = "",
    val reason: String = "",
    val status: String = "open",
    val createdAtEpochMs: Long = 0L,
)
