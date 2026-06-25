package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import kotlin.time.Instant

/**
 * A pending request to join a crew, awaiting the owner's decision. Filed by a non-member who has
 * the invite code (stored at `crews/{crewId}/joinRequests/{accountId}`). The owner sees these in
 * Crew Settings and either Approves — which adds the [accountId] to the crew and deletes the
 * request — or Declines, which just deletes the request. There is no instant self-join; owner
 * approval is the sole membership-grant path.
 *
 * Identity (display name + avatar) is NOT modelled here — it is resolved live via `AccountReadPort`
 * at presentation, exactly like [Member].
 */
data class JoinRequest(
    val accountId: AccountId,
    val requestedAt: Instant,
)
