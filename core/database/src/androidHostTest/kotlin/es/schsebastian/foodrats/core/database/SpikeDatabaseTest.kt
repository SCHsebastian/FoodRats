package es.schsebastian.foodrats.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

/** P3a-T0 feasibility gate: SQLDelight codegen runs and the generated DB works on the host JVM. */
class SpikeDatabaseTest {
    @Test
    fun insert_and_select_roundtrips() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FoodRatsDatabase.Schema.create(driver)
        val db = FoodRatsDatabase(driver)

        db.spikeQueries.insertSpike(1L, "hello")
        val rows = db.spikeQueries.selectAll().executeAsList()

        assertEquals(1, rows.size)
        assertEquals("hello", rows[0].label)
        driver.close()
    }
}
