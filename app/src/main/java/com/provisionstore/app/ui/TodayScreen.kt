package com.provisionstore.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.provisionstore.app.BuildConfig
import com.provisionstore.app.data.Backup
import com.provisionstore.app.data.PaymentMode
import com.provisionstore.app.util.DAY_MS
import com.provisionstore.app.util.dayLabel
import com.provisionstore.app.util.money
import com.provisionstore.app.util.qty
import com.provisionstore.app.util.startOfToday
import com.provisionstore.app.util.timeOnly

/** One line in the day's entries list. */
private class DayRow(
    val time: Long,
    val title: String,
    val detail: String,
    val amount: Long,
    val onDelete: () -> Unit,
)

@Composable
fun TodayScreen(vm: ShopViewModel) {
    val day by vm.selectedDay.collectAsState()
    val sales by vm.daySales.collectAsState(initial = emptyList())
    val lines by vm.dayLines.collectAsState(initial = emptyList())
    val payments by vm.dayPayments.collectAsState(initial = emptyList())
    val purchases by vm.dayPurchases.collectAsState(initial = emptyList())
    val expenses by vm.dayExpenses.collectAsState(initial = emptyList())
    val customers by vm.customers.collectAsState(initial = emptyList())
    val trackItems by vm.trackItems.collectAsState()
    val updateStatus by vm.updateStatus.collectAsState()
    val checking by vm.checking.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val context = LocalContext.current

    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var addingSale by remember { mutableStateOf(false) }
    var addingPurchase by remember { mutableStateOf(false) }
    var addingExpense by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DayRow?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreFrom = uri
    }

    val isToday = day >= startOfToday()
    // Sales made from the Sell screen have item lines. The rest are day-book sales.
    val itemSaleIds = lines.map { it.saleId }.toSet()
    val quickSales = sales.filter { it.id !in itemSaleIds }
    val customerNames = customers.associate { it.id to it.name }

    val salesTotal = sales.sumOf { it.total }
    fun modeTotal(mode: PaymentMode): Long = sales.filter { it.paymentMode == mode.name }.sumOf { it.total }
    val received = -payments.sumOf { it.amount }
    val purchaseTotal = purchases.sumOf { it.amount }
    val expenseTotal = expenses.sumOf { it.amount }
    val leftover = salesTotal - purchaseTotal - expenseTotal
    val owedToShop = customers.filter { it.balance > 0 }.sumOf { it.balance }

    val rows = (
        quickSales.map { s ->
            DayRow(
                time = s.createdAt,
                title = "Sale (${s.paymentMode.lowercase()})" +
                    (s.customerId?.let { customerNames[it] }?.let { " - $it" } ?: ""),
                detail = timeOnly(s.createdAt),
                amount = s.total,
                onDelete = { vm.deleteQuickSale(s.id) },
            )
        } +
            purchases.map { p ->
                DayRow(
                    time = p.createdAt,
                    title = "Purchase" + if (p.supplier.isNotBlank()) " - ${p.supplier}" else "",
                    detail = listOf(timeOnly(p.createdAt), p.note).filter { it.isNotBlank() }.joinToString("  "),
                    amount = p.amount,
                    onDelete = { vm.deletePurchase(p.id) },
                )
            } +
            expenses.map { e ->
                DayRow(
                    time = e.createdAt,
                    title = "Expense - ${e.category}",
                    detail = listOf(timeOnly(e.createdAt), e.note).filter { it.isNotBlank() }.joinToString("  "),
                    amount = e.amount,
                    onDelete = { vm.deleteExpense(e.id) },
                )
            }
        ).sortedByDescending { it.time }

    val topItems = lines.groupBy { it.itemName }
        .map { (name, rows) -> Triple(name, rows.sumOf { it.qty }, rows.sumOf { it.lineTotal }) }
        .sortedByDescending { it.third }
        .take(5)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftDay(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayLabel(day), style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = { vm.shiftDay(1) }, enabled = !isToday) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        }

        StatLine("Sales (${sales.size} bills)", salesTotal.money(), big = true)
        StatLine("   Cash", modeTotal(PaymentMode.CASH).money())
        StatLine("   UPI", modeTotal(PaymentMode.UPI).money())
        StatLine("   On credit (khata)", modeTotal(PaymentMode.CREDIT).money())
        StatLine("Payments received from khata", received.money())
        StatLine("Purchases", purchaseTotal.money())
        StatLine("Expenses", expenseTotal.money())
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        StatLine("Left after purchases and expenses", leftover.money(), big = true)
        StatLine("Customers owe you (total)", owedToShop.money())

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addingSale = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Sale")
            }
            Button(onClick = { addingPurchase = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Purchase")
            }
            Button(onClick = { addingExpense = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Expense")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Entries", style = MaterialTheme.typography.titleMedium)
        if (rows.isEmpty()) {
            Text("Nothing entered for this day yet.", modifier = Modifier.padding(top = 4.dp))
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.title)
                    Text(row.detail, style = MaterialTheme.typography.bodySmall)
                }
                Text(row.amount.money())
                IconButton(onClick = { confirmDelete = row }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                }
            }
        }

        if (topItems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Top items (from Sell)", style = MaterialTheme.typography.titleMedium)
            topItems.forEach { (name, qty, amount) ->
                StatLine("$name (${qty.qty()})", amount.money())
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Track items and stock", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn off to keep only a day book: sales, purchases, expenses and khata.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = trackItems, onCheckedChange = { vm.setTrackItems(it) })
        }

        Spacer(Modifier.height(16.dp))
        Text("Data and updates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.checkForUpdates(silent = false) },
            enabled = !checking && progress == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(if (checking) "Checking..." else "Check for updates")
        }
        if (checking) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }

        progress?.let { fraction ->
            val label = if (fraction < 0f) {
                "Downloading update..."
            } else {
                "Downloading update... ${(fraction * 100).toInt()}%"
            }
            Text(label, modifier = Modifier.padding(top = 8.dp))
            if (fraction < 0f) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }

        if (updateStatus.isNotBlank()) {
            Text(updateStatus, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Backup.exportIntent(context))
                } catch (e: Exception) {
                    message = e.message ?: "Backup failed."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Back up data")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { restorePicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Restore from backup")
        }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (addingSale) {
        QuickSaleDialog(
            customers = customers,
            onDismiss = { addingSale = false },
            onSave = { amount, mode, customerId ->
                vm.addQuickSale(amount, mode, customerId)
                addingSale = false
            },
        )
    }
    if (addingPurchase) {
        PurchaseDialog(
            onDismiss = { addingPurchase = false },
            onSave = { amount, supplier, note ->
                vm.addPurchase(amount, supplier, note)
                addingPurchase = false
            },
        )
    }
    if (addingExpense) {
        ExpenseDialog(
            onDismiss = { addingExpense = false },
            onSave = { category, amount, note ->
                vm.addExpense(category, amount, note)
                addingExpense = false
            },
        )
    }

    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this entry?") },
            text = { Text("${row.title}  ${row.amount.money()}") },
            confirmButton = {
                TextButton(onClick = {
                    row.onDelete()
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    restoreFrom?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreFrom = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces ALL current data on this phone with the backup. It cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    restoreFrom = null
                    try {
                        Backup.restore(context, uri)
                        Backup.restartApp(context)
                    } catch (e: Exception) {
                        message = e.message ?: "Restore failed."
                    }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreFrom = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String, big: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
    }
}
