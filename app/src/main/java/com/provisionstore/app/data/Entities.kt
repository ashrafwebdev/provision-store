package com.provisionstore.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.roundToLong

// Money is stored in paise (1 rupee = 100) to avoid rounding errors.
// Quantities are plain numbers (kg, litre, pc...), stored in those units.

enum class PaymentMode { CASH, UPI, CREDIT }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val sellPrice: Long,
    val costPrice: Long,
    val stock: Double,
    val reorderLevel: Double,
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
)

/** A sale. Sales made from the Sell screen have lines; quick day-book sales have none. */
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val total: Long,
    val paymentMode: String,
    val customerId: Long?,
)

@Entity(
    tableName = "sale_lines",
    foreignKeys = [
        ForeignKey(
            entity = Sale::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("saleId")],
)
data class SaleLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val itemId: Long,
    val itemName: String,
    val qty: Double,
    val unitPrice: Long,
    val lineTotal: Long,
)

/** amount > 0: credit given (customer owes more). amount < 0: payment received. */
@Entity(tableName = "ledger", indices = [Index("customerId")])
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val createdAt: Long,
    val amount: Long,
    val note: String,
)

/** Goods bought from a supplier, entered as a total. Not tied to items. */
@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val amount: Long,
    val supplier: String,
    val note: String,
)

/** Money spent running the shop: rent, electricity, salary and so on. */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val category: String,
    val amount: Long,
    val note: String,
)

/** One line in the cart on the Sell screen. Not stored; a saved bill is made from these. */
data class CartLine(val item: Item, val qty: Double) {
    val lineTotal: Long get() = (item.sellPrice * qty).roundToLong()
}

/** A customer with their current balance (sum of ledger entries). */
data class CustomerWithBalance(
    val id: Long,
    val name: String,
    val phone: String,
    val balance: Long,
)
