package com.provisionstore.app.ui

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.provisionstore.app.BuildConfig
import com.provisionstore.app.data.Backup
import com.provisionstore.app.data.PaymentMode
import com.provisionstore.app.util.money
import com.provisionstore.app.util.qty
import com.provisionstore.app.util.todayLabel

@Composable
fun TodayScreen(vm: ShopViewModel) {
    val sales by vm.todaySales.collectAsState(initial = emptyList())
    val lines by vm.todayLines.collectAsState(initial = emptyList())
    val payments by vm.todayPayments.collectAsState(initial = emptyList())
    val updateStatus by vm.updateStatus.collectAsState()
    val context = LocalContext.current
    var restoreFrom by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf("") }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreFrom = uri
    }

    fun sumByMode(mode: PaymentMode): Long = sales.filter { it.paymentMode == mode.name }.sumOf { it.total }

    val total = sales.sumOf { it.total }
    val received = -payments.sumOf { it.amount }
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
        Text("Today", style = MaterialTheme.typography.headlineSmall)
        Text(todayLabel(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        StatLine("Total sales (${sales.size} bills)", total.money(), big = true)
        StatLine("Cash", sumByMode(PaymentMode.CASH).money())
        StatLine("UPI", sumByMode(PaymentMode.UPI).money())
        StatLine("On credit (khata)", sumByMode(PaymentMode.CREDIT).money())
        StatLine("Payments received from khata", received.money())

        Spacer(Modifier.height(16.dp))
        Text("Top items", style = MaterialTheme.typography.titleMedium)
        if (topItems.isEmpty()) {
            Text("No sales yet today.")
        } else {
            topItems.forEach { (name, qty, amount) ->
                StatLine("$name (${qty.qty()})", amount.money())
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Data and updates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.checkForUpdates(silent = false) }, modifier = Modifier.fillMaxWidth()) {
            Text("Check for updates")
        }
        if (updateStatus.isNotBlank()) Text(updateStatus, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Backup.exportIntent(context))
                } catch (e: Exception) {
                    message = e.message ?: "Backup failed."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Back up data") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { restorePicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restore from backup") }
        if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
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
