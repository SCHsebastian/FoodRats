package es.schsebastian.foodrats.core.data.outbox

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.schsebastian.foodrats.core.database.FoodRatsDatabase
import es.schsebastian.foodrats.core.domain.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * JVM in-memory SQLDelight harness for the outbox host tests (P3b-T6). The outbox store now wraps
 * [FoodRatsDatabase]; each test seeds a real in-memory `outbox` table over a [JdbcSqliteDriver],
 * mirroring `MealLocalStoreTest`. (`:core:data`'s iOS test target can't link Firebase, so the
 * cross-platform DB behavior is covered by `:core:database:iosSimulatorArm64Test` instead.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class OutboxTestDb {
    val driver: JdbcSqliteDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).apply {
        FoodRatsDatabase.Schema.create(this)
    }
    val database: FoodRatsDatabase = FoodRatsDatabase(driver)

    val dispatchers: DispatcherProvider = object : DispatcherProvider {
        private val d: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val main = d
        override val io = d
        override val default = d
    }

    fun store(): OutboxLocalStore = OutboxLocalStore(database, dispatchers)

    fun close() = driver.close()
}
