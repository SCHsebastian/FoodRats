package es.schsebastian.foodrats.feature.crew.data.firebase

import es.schsebastian.foodrats.core.domain.crew.CrewRosterPort
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [CrewRosterPort] over the same crew-doc listener [CrewDataSource.observeCrew] already backs —
 * mirrors the [es.schsebastian.foodrats.core.domain.crew.CrewOwnerPort] adapter bound in
 * `crewModule`: no parallel Firestore listener. Maps `memberIds` to [AccountId], silently
 * dropping any malformed id rather than failing the whole roster; emits `emptyList()` when the
 * crew doc is null/unreadable (the roster is advisory — @-mention autocomplete degrades to no
 * suggestions, it never blocks the composer).
 *
 * A named class (rather than the anonymous-object-in-Koin-module style used for
 * `CrewOwnerPort`/`CrewBlindVotingPort`) so the memberIds → AccountId mapping is directly
 * unit-testable against [FakeCrewDataSource] in `commonTest`.
 */
class CrewRosterAdapter(private val dataSource: CrewDataSource) : CrewRosterPort {
    override fun observeMembers(crewId: CrewId): Flow<List<AccountId>> =
        dataSource.observeCrew(crewId).map { dto ->
            dto?.memberIds.orEmpty().mapNotNull { (AccountId.of(it) as? Result.Ok)?.value }
        }
}
