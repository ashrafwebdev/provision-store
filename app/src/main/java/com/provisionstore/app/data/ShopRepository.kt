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

    val items: Flow<List<Item>> = itemDao.observeAll()
    val customers: Flow<List<CustomerWithBalance>> = customerDao.observeWithBalance()

    fun salesBetween(from: Long, to: Long): Flow<List<Sale>> = saleDao.observeBetween(from, to)
    fun linesBetween(from: Long, to: Long): Flow<List<SaleLine>> = saleDao.observeLinesBetween(from, to)
    fun paymentsBetween(from: Long, to: Long): Flow<List<LedgerEntry>> =
        ledgerDao.observePaymentsBetween(from, to)

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
