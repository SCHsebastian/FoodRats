package es.schsebastian.foodrats.feature.crew.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import es.schsebastian.foodrats.feature.crew.data.firebase.CrewDto
import es.schsebastian.foodrats.feature.crew.data.firebase.MemberDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host-test (JVM) coverage of [CrewLocalStore]: seed via [CrewLocalStore.replaceAll], then assert
 * the reactive read projects the rows back into [CrewDto]s (newest-created first), that a full
 * replace drops absent crews, and that the member CSV / blind-voting flag round-trip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrewLocalStoreTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var store: CrewLocalStore

    private val dispatchers = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    @BeforeTest fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
            FoodRatsDatabase.Schema.create(this)
        }
        store = CrewLocalStore(FoodRatsDatabase(driver), dispatchers)
    }

    @AfterTest fun tearDown() {
        driver.close()
    }

    private fun dto(
        id: String,
        name: String = "Crew $id",
        ownerId: String = "owner-$id",
        createdAtEpochMs: Long,
        memberIds: List<String> = listOf(ownerId),
        blindVoting: Boolean = false,
    ) = CrewDto(
        id = id,
        name = name,
        code = "ABCD23",
        ownerId = ownerId,
        createdAtEpochMs = createdAtEpochMs,
        memberIds = memberIds,
        members = memberIds.associateWith { MemberDto(joinedAtEpochMs = createdAtEpochMs) },
        blindVoting = blindVoting,
    )

    @Test fun replace_all_then_observe_orders_newest_created_first() = runTest {
        store.replaceAll(
            listOf(
                dto("c1", createdAtEpochMs = 100L),
                dto("c2", createdAtEpochMs = 300L),
                dto("c3", createdAtEpochMs = 200L),
            ),
        )

        store.observeMyCrews().test {
            assertEquals(listOf("c2", "c3", "c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun replace_all_is_a_full_replace_dropping_absent_crews() = runTest {
        store.replaceAll(listOf(dto("c1", createdAtEpochMs = 1L), dto("c2", createdAtEpochMs = 2L)))
        // The member left c2 → the next snapshot omits it; a full replace must drop it.
        store.replaceAll(listOf(dto("c1", createdAtEpochMs = 1L)))

        store.observeMyCrews().test {
            assertEquals(listOf("c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun member_csv_and_blind_voting_round_trip() = runTest {
        store.replaceAll(
            listOf(
                dto(
                    "c1",
                    createdAtEpochMs = 10L,
                    memberIds = listOf("owner", "bob", "carol"),
                    blindVoting = true,
                ),
            ),
        )

        store.observeMyCrews().test {
            val rebuilt = awaitItem().single()
            assertEquals(setOf("owner", "bob", "carol"), rebuilt.memberIds.toSet())
            // Each member is rebuilt into the members map (so toDomain reconstructs Member rows).
            assertEquals(setOf("owner", "bob", "carol"), rebuilt.members.keys)
            assertEquals(true, rebuilt.blindVoting)
            assertEquals("c1", rebuilt.id)
            assertEquals("Crew c1", rebuilt.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun replace_all_with_empty_clears_the_table() = runTest {
        store.replaceAll(listOf(dto("c1", createdAtEpochMs = 1L)))
        store.replaceAll(emptyList())

        store.observeMyCrews().test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun replace_all_skips_dtos_missing_a_required_column() = runTest {
        // A malformed DTO (null id) can't be stored → it's silently dropped; valid rows still land.
        store.replaceAll(
            listOf(
                dto("c1", createdAtEpochMs = 1L),
                dto("c2", createdAtEpochMs = 2L).copy(id = null),
            ),
        )

        store.observeMyCrews().test {
            assertEquals(listOf("c1"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
