package com.provisionstore.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY name")
    fun observeAll(): Flow<List<Item>>

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE items SET stock = stock + :delta WHERE id = :id")
    suspend fun addStock(id: Long, delta: Double)
}

@Dao
interface CustomerDao {
    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.phone AS phone,
               COALESCE(SUM(l.amount), 0) AS balance
        FROM customers c
        LEFT JOIN ledger l ON l.customerId = c.id
        GROUP BY c.id
        ORDER BY c.name
        """
    )
    fun observeWithBalance(): Flow<List<CustomerWithBalance>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): Customer?

    @Insert
    suspend fun insert(customer: Customer): Long
}

@Dao
interface SaleDao {
    @Insert
    suspend fun insertSale(sale: Sale): Long

    @Insert
    suspend fun insertLines(lines: List<SaleLine>)

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSale(id: Long): Sale?

    @Query("SELECT * FROM sale_lines WHERE saleId = :saleId")
    suspend fun linesFor(saleId: Long): List<SaleLine>

    @Query("SELECT * FROM sales WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Sale>>

    @Query(
        "SELECT * FROM sale_lines WHERE saleId IN " +
            "(SELECT id FROM sales WHERE createdAt BETWEEN :from AND :to)"
    )
    fun observeLinesBetween(from: Long, to: Long): Flow<List<SaleLine>>
}

@Dao
interface LedgerDao {
    @Insert
    suspend fun insert(entry: LedgerEntry): Long

    @Query("SELECT * FROM ledger WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun observeFor(customerId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger WHERE customerId = :customerId ORDER BY createdAt")
    suspend fun listFor(customerId: Long): List<LedgerEntry>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger WHERE customerId = :customerId")
    suspend fun balance(customerId: Long): Long

    @Query("SELECT * FROM ledger WHERE amount < 0 AND createdAt BETWEEN :from AND :to")
    fun observePaymentsBetween(from: Long, to: Long): Flow<List<LedgerEntry>>
}
