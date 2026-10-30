package com.provisionstore.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.provisionstore.app.BuildConfig
import com.provisionstore.app.UpdateChecker
import com.provisionstore.app.UpdateInfo
import com.provisionstore.app.data.AppDatabase
import com.provisionstore.app.data.CartLine
import com.provisionstore.app.data.Item
import com.provisionstore.app.data.PaymentMode
import com.provisionstore.app.data.ShopRepository
import com.provisionstore.app.util.DAY_MS
import com.provisionstore.app.util.startOfToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ShopRepository(AppDatabase.get(app))
    private val prefs = app.getSharedPreferences("shop", Context.MODE_PRIVATE)

    val items = repo.items
    val customers = repo.customers

    /** Off hides the Items and Stock screens, for a shop that only keeps a day book. */
    val trackItems = MutableStateFlow(prefs.getBoolean(KEY_TRACK_ITEMS, true))

    /** Start of the day shown on the Day screen. */
    val selectedDay = MutableStateFlow(startOfToday())
    val daySales = selectedDay.flatMapLatest { repo.salesBetween(it, it + DAY_MS) }
    val dayLines = selectedDay.flatMapLatest { repo.linesBetween(it, it + DAY_MS) }
    val dayPayments = selectedDay.flatMapLatest { repo.paymentsBetween(it, it + DAY_MS) }
    val dayPurchases = selectedDay.flatMapLatest { repo.purchasesBetween(it, it + DAY_MS) }
    val dayExpenses = selectedDay.flatMapLatest { repo.expensesBetween(it, it + DAY_MS) }

    val pendingUpdate = MutableStateFlow<UpdateInfo?>(null)
    val updateStatus = MutableStateFlow("")

    /** True while the app is asking GitHub for the latest release. */
    val checking = MutableStateFlow(false)

    /** null when idle, -1 while downloading with unknown size, otherwise 0..1. */
    val downloadProgress = MutableStateFlow<Float?>(null)

    fun ledgerFor(customerId: Long) = repo.ledgerFor(customerId)

    fun setTrackItems(on: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_ITEMS, on).apply()
        trackItems.value = on
    }

    /** Moves the Day screen by [days]. Stops at today; no entries are added to future days. */
    fun shiftDay(days: Int) {
        if (days > 0 && selectedDay.value >= startOfToday()) return
        selectedDay.value += days * DAY_MS
    }

    /** Right now on today, or the same time of day on an earlier day. */
    private fun entryTime(): Long = selectedDay.value + (System.currentTimeMillis() - startOfToday())

    fun saveItem(item: Item) = viewModelScope.launch { repo.saveItem(item) }

    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }

    fun addStock(itemId: Long, qty: Double) = viewModelScope.launch { repo.addStock(itemId, qty) }

    fun addCustomer(name: String, phone: String) =
        viewModelScope.launch { repo.addCustomer(name, phone) }

    fun recordPayment(customerId: Long, amount: Long) =
        viewModelScope.launch { repo.recordPayment(customerId, amount) }

    fun checkout(cart: List<CartLine>, mode: PaymentMode, customerId: Long?, onSaved: (Long) -> Unit) =
        viewModelScope.launch { onSaved(repo.checkout(cart, mode, customerId)) }

    fun addQuickSale(amount: Long, mode: PaymentMode, customerId: Long?) =
        viewModelScope.launch { repo.addQuickSale(amount, mode, customerId, entryTime()) }

    fun deleteQuickSale(saleId: Long) = viewModelScope.launch { repo.deleteQuickSale(saleId) }

    fun addPurchase(amount: Long, supplier: String, note: String) =
        viewModelScope.launch { repo.addPurchase(amount, supplier, note, entryTime()) }

    fun deletePurchase(id: Long) = viewModelScope.launch { repo.deletePurchase(id) }

    fun addExpense(category: String, amount: Long, note: String) =
        viewModelScope.launch { repo.addExpense(category, amount, note, entryTime()) }

    fun deleteExpense(id: Long) = viewModelScope.launch { repo.deleteExpense(id) }

    suspend fun billText(saleId: Long): String = repo.billText(saleId)

    suspend fun statementText(customerId: Long): String = repo.statementText(customerId)

    fun checkForUpdates(silent: Boolean) {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            if (!silent) updateStatus.value = "Checking for updates..."
            try {
                val update = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdate() }
                if (update != null) {
                    pendingUpdate.value = update
                    updateStatus.value = ""
                } else if (!silent) {
                    updateStatus.value = "You have the latest version (${BuildConfig.VERSION_NAME})."
                }
            } catch (e: Exception) {
                if (!silent) updateStatus.value = "Could not check for updates: ${e.message ?: "no connection"}"
            } finally {
                checking.value = false
            }
        }
    }

    fun dismissUpdate() {
        pendingUpdate.value = null
    }

    fun installUpdate(update: UpdateInfo) {
        pendingUpdate.value = null
        viewModelScope.launch {
            downloadProgress.value = 0f
            updateStatus.value = "Downloading version ${update.versionCode}..."
            try {
                val apk = withContext(Dispatchers.IO) {
                    UpdateChecker.download(getApplication(), update) { fraction ->
                        downloadProgress.value = fraction
                    }
                }
                updateStatus.value = "Download complete. Opening the installer..."
                UpdateChecker.install(getApplication(), apk)
                updateStatus.value = ""
            } catch (e: Exception) {
                updateStatus.value = e.message ?: "Update failed."
            } finally {
                downloadProgress.value = null
            }
        }
    }

    private companion object {
        const val KEY_TRACK_ITEMS = "track_items"
    }
}
