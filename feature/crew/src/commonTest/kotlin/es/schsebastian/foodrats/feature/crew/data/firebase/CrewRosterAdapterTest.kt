package es.schsebastian.foodrats.feature.crew.data.firebase

import app.cash.turbine.test
import es.schsebastian.foodrats.core.domain.model.AccountId
import es.schsebastian.foodrats.core.domain.model.CrewId
import es.schsebastian.foodrats.core.domain.result.Result
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CrewRosterAdapterTest {

    private val crewId = (CrewId.of("crew-1") as Result.Ok).value

    @Test
    fun maps_crew_doc_memberIds_to_accountIds() = runTest {
        val ds = FakeCrewDataSource()
        ds.observeCrewFlow = flowOf(
            CrewDto(id = "crew-1", memberIds = listOf("acc-1", "acc-2", "acc-3")),
        )
        val port = CrewRosterAdapter(ds)

        port.observeMembers(crewId).test {
            assertEquals(
                listOf(AccountId.of("acc-1"), AccountId.of("acc-2"), AccountId.of("acc-3"))
                    .map { (it as Result.Ok).value },
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun drops_blank_member_ids_instead_of_failing_the_whole_roster() = runTest {
        val ds = FakeCrewDataSource()
        ds.observeCrewFlow = flowOf(
            CrewDto(id = "crew-1", memberIds = listOf("acc-1", "", "  ")),
        )
        val port = CrewRosterAdapter(ds)

        port.observeMembers(crewId).test {
            assertEquals(listOf((AccountId.of("acc-1") as Result.Ok).value), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unknown_or_absent_crew_emits_empty_list() = runTest {
        val ds = FakeCrewDataSource()
        ds.observeCrewFlow = flowOf(null)
        val port = CrewRosterAdapter(ds)

        port.observeMembers(crewId).test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
