package com.provisionstore.app.data

import androidx.room.withTransaction
import com.provisionstore.app.util.dateTime
import com.provisionstore.app.util.money
import com.provisionstore.app.util.qty
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class ShopRepository(private val db: AppDatabase) {
    private val itemDao = db.itemDao()
    private val customerDao = db.customerDao()
    private val saleDao = db.saleDao()
    private val ledgerDao = db.ledgerDao()
    private val purchaseDao = db.purchaseDao()
    private val expenseDao = db.expenseDao()

    val items: Flow<List<Item>> = itemDao.observeAll()
    val customers: Flow<List<CustomerWithBalance>> = customerDao.observeWithBalance()

    fun salesBetween(from: Long, to: Long): Flow<List<Sale>> = saleDao.observeBetween(from, to)
    fun linesBetween(from: Long, to: Long): Flow<List<SaleLine>> = saleDao.observeLinesBetween(from, to)
    fun paymentsBetween(from: Long, to: Long): Flow<List<LedgerEntry>> =
        ledgerDao.observePaymentsBetween(from, to)

    fun purchasesBetween(from: Long, to: Long): Flow<List<Purchase>> = purchaseDao.observeBetween(from, to)
    fun expensesBetween(from: Long, to: Long): Flow<List<Expense>> = expenseDao.observeBetween(from, to)

    fun ledgerFor(customerId: Long): Flow<List<LedgerEntry>> = ledgerDao.observeFor(customerId)

    suspend fun saveItem(item: Item) {
        if (item.id == 0L) itemDao.insert(item) else itemDao.update(item)
    }

    suspend fun deleteItem(id: Long) = itemDao.delete(id)

    suspend fun addStock(itemId: Long, qty: Double) = itemDao.addStock(itemId, qty)

    suspend fun addCustomer(name: String, phone: String) {
        customerDao.insert(Customer(name = name, phone = phone))
    }

    /** Records a payment from the customer. Stored as a negative ledger amount. */
    suspend fun recordPayment(customerId: Long, amount: Long) {
        ledgerDao.insert(
            LedgerEntry(
                customerId = customerId,
                createdAt = System.currentTimeMillis(),
                amount = -amount,
                note = "Payment received",
            )
        )
    }

    /** Saves the bill, reduces stock, and books the amount to the customer's khata for credit sales. */
    suspend fun checkout(cart: List<CartLine>, mode: PaymentMode, customerId: Long?): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val total = cart.sumOf { it.lineTotal }
            val saleId = saleDao.insertSale(
                Sale(
                    createdAt = now,
                    total = total,
                    paymentMode = mode.name,
                    customerId = customerId,
                )
            )
            saleDao.insertLines(
                cart.map {
                    SaleLine(
                        saleId = saleId,
                        itemId = it.item.id,
                        itemName = it.item.name,
                        qty = it.qty,
                        unitPrice = it.item.sellPrice,
                        lineTotal = it.lineTotal,
                    )
                }
            )
            cart.forEach { itemDao.addStock(it.item.id, -it.qty) }
            if (mode == PaymentMode.CREDIT && customerId != null) {
                ledgerDao.insert(
                    LedgerEntry(
                        customerId = customerId,
                        createdAt = now,
                        amount = total,
                        note = "Bill #$saleId",
                    )
                )
            }
            saleId
        }

    /** A day-book sale: just the total, no items. Credit sales are added to the customer's khata. */
    suspend fun addQuickSale(amount: Long, mode: PaymentMode, customerId: Long?, at: Long) {
        db.withTransaction {
            val saleId = saleDao.insertSale(
                Sale(createdAt = at, total = amount, paymentMode = mode.name, customerId = customerId)
            )
            if (mode == PaymentMode.CREDIT && customerId != null) {
                ledgerDao.insert(
                    LedgerEntry(
                        customerId = customerId,
                        createdAt = at,
                        amount = amount,
                        note = "Credit sale #$saleId",
                    )
                )
            }
        }
    }

    /** Removes a day-book sale and, for credit sales, its khata entry. */
    suspend fun deleteQuickSale(saleId: Long) {
        db.withTransaction {
            ledgerDao.deleteByNote("Credit sale #$saleId")
            saleDao.deleteSale(saleId)
        }
    }

    suspend fun addPurchase(amount: Long, supplier: String, note: String, at: Long) {
        purchaseDao.insert(Purchase(createdAt = at, amount = amount, supplier = supplier, note = note))
    }

    suspend fun deletePurchase(id: Long) = purchaseDao.delete(id)

    suspend fun addExpense(category: String, amount: Long, note: String, at: Long) {
        expenseDao.insert(Expense(createdAt = at, category = category, amount = amount, note = note))
    }

    suspend fun deleteExpense(id: Long) = expenseDao.delete(id)

    suspend fun billText(saleId: Long): String {
        val sale = saleDao.getSale(saleId) ?: return ""
        val lines = saleDao.linesFor(saleId)
        return buildString {
            appendLine("Provision Store")
            appendLine(dateTime(sale.createdAt))
            appendLine("----------------")
            lines.forEach {
                appendLine("${it.itemName}  ${it.qty.qty()} x ${it.unitPrice.money()} = ${it.lineTotal.money()}")
            }
            appendLine("----------------")
            appendLine("Total: ${sale.total.money()}")
            appendLine("Paid by: ${sale.paymentMode}")
        }
    }

    suspend fun statementText(customerId: Long): String {
        val customer = customerDao.get(customerId) ?: return ""
        val entries = ledgerDao.listFor(customerId)
        return buildString {
            appendLine("Account statement - ${customer.name}")
            entries.forEach { e ->
                val kind = if (e.amount < 0) "Paid" else "Credit"
                appendLine("${dateTime(e.createdAt)}  ${e.note}  $kind ${abs(e.amount).money()}")
            }
            appendLine("Balance due: ${ledgerDao.balance(customerId).money()}")
        }
    }
}
