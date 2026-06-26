package es.schsebastian.foodrats.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = FoodRatsDatabase.Schema,
            context = context,
            name = DATABASE_FILE_NAME,
            // Android opens SQLite with foreign-key enforcement OFF per connection, so the
            // mealRating → meal ON DELETE CASCADE would NOT fire (offline-first P4-T1: the
            // CachePruner relies on it). onConfigure runs once per connection, before the schema
            // callbacks, which is the only safe place to set the PRAGMA.
            callback = object : AndroidSqliteDriver.Callback(FoodRatsDatabase.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                    // WAL + synchronous=NORMAL: one fsync/commit instead of the rollback-journal +
                    // synchronous=FULL default's two. WAL is orthogonal to the FK CASCADE above;
                    // synchronous=NORMAL under WAL only risks losing the last txn on hard power-loss
                    // (never corruption) — fine for this Firestore-backed re-syncable cache.
                    // journal_mode must be set OUTSIDE a transaction; onConfigure is that place, and
                    // PRAGMA journal_mode returns a row so it must run as a query.
                    db.query("PRAGMA journal_mode=WAL").use { it.moveToFirst() }
                    db.execSQL("PRAGMA synchronous=NORMAL")
                }
            },
        )
}
