package com.provisionstore.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// When the schema changes, bump the version and add a Migration. Do not use
// fallbackToDestructiveMigration: it would wipe the shop's data.
@Database(
    entities = [Item::class, Customer::class, Sale::class, SaleLine::class, LedgerEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        const val FILE_NAME = "provision.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    FILE_NAME,
                ).build().also { instance = it }
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
