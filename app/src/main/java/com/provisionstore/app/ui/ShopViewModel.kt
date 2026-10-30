package com.provisionstore.app.ui

import android.app.Application
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ShopRepository(AppDatabase.get(app))

    val items = repo.items
    val customers = repo.customers

    // Today's figures cover the day the screen was opened.
    private val dayStart = startOfToday()
    private val dayEnd = dayStart + DAY_MS
    val todaySales = repo.salesBetween(dayStart, dayEnd)
    val todayLines = repo.linesBetween(dayStart, dayEnd)
    val todayPayments = repo.paymentsBetween(dayStart, dayEnd)

    val pendingUpdate = MutableStateFlow<UpdateInfo?>(null)
    val updateStatus = MutableStateFlow("")

    /** True while the app is asking GitHub for the latest release. */
    val checking = MutableStateFlow(false)

    /** null when idle, -1 while downloading with unknown size, otherwise 0..1. */
    val downloadProgress = MutableStateFlow<Float?>(null)

    fun ledgerFor(customerId: Long) = repo.ledgerFor(customerId)

    fun saveItem(item: Item) = viewModelScope.launch { repo.saveItem(item) }

    fun deleteItem(id: Long) = viewModelScope.launch { repo.deleteItem(id) }

    fun addStock(itemId: Long, qty: Double) = viewModelScope.launch { repo.addStock(itemId, qty) }

    fun addCustomer(name: String, phone: String) =
        viewModelScope.launch { repo.addCustomer(name, phone) }

    fun recordPayment(customerId: Long, amount: Long) =
        viewModelScope.launch { repo.recordPayment(customerId, amount) }

    fun checkout(cart: List<CartLine>, mode: PaymentMode, customerId: Long?, onSaved: (Long) -> Unit) =
        viewModelScope.launch { onSaved(repo.checkout(cart, mode, customerId)) }

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
}
