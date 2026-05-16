package es.schsebastian.foodrats.feature.crew.domain.model

import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import kotlinx.datetime.Instant

data class Crew(
    val id: CrewId,
    val name: String,
    val code: CrewCode,
    val ownerId: AccountId,
    val createdAt: Instant,
    val members: List<Member>,
) {
    val size: Int get() = members.size
}
