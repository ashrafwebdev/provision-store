package com.provisionstore.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// When the schema changes, bump the version and add a Migration here. Never use
// fallbackToDestructiveMigration: it would wipe the shop's data.
@Database(
    entities = [
        Item::class,
        Customer::class,
        Sale::class,
        SaleLine::class,
        LedgerEntry::class,
        Purchase::class,
        Expense::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val FILE_NAME = "provision.db"

        /** Adds the purchases and expenses tables. Existing data is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `amount` INTEGER NOT NULL, " +
                        "`supplier` TEXT NOT NULL, `note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, `note` TEXT NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        /** Closes the open database so its file can be replaced during a restore. */
        fun close() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
