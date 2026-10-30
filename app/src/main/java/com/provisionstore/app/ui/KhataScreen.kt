package com.provisionstore.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.provisionstore.app.data.CustomerWithBalance
import com.provisionstore.app.util.dateTime
import com.provisionstore.app.util.money
import com.provisionstore.app.util.parseMoney
import kotlinx.coroutines.launch

@Composable
fun KhataScreen(vm: ShopViewModel) {
    val customerList by vm.customers.collectAsState(initial = emptyList())
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    val selected = customerList.firstOrNull { it.id == selectedId }

    if (selected != null) {
        CustomerDetail(vm = vm, customer = selected, onBack = { selectedId = null })
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Khata (credit)", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add customer")
            }
            Spacer(Modifier.height(8.dp))
            if (customerList.isEmpty()) {
                Text("No customers yet.")
            }
            LazyColumn(Modifier.weight(1f)) {
                items(customerList, key = { it.id }) { c ->
                    Card(
                        onClick = { selectedId = c.id },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                if (c.phone.isNotBlank()) Text(c.phone)
                            }
                            Text(balanceText(c.balance))
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        CustomerDialog(
            onDismiss = { adding = false },
            onSave = { name, phone ->
                vm.addCustomer(name, phone)
                adding = false
            },
        )
    }
}

private fun balanceText(balance: Long): String = when {
    balance > 0 -> "Owes ${balance.money()}"
    balance < 0 -> "Advance ${(-balance).money()}"
    else -> "Settled"
}

@Composable
private fun CustomerDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), phone.trim()) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomerDetail(vm: ShopViewModel, customer: CustomerWithBalance, onBack: () -> Unit) {
    val ledger by remember(customer.id) { vm.ledgerFor(customer.id) }
        .collectAsState(initial = emptyList())
    var paying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text("Back")
        }
        Text(customer.name, style = MaterialTheme.typography.headlineSmall)
        if (customer.phone.isNotBlank()) Text(customer.phone)
        Spacer(Modifier.height(12.dp))
        Text(
            balanceText(customer.balance),
            style = MaterialTheme.typography.titleLarge,
            color = if (customer.balance > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { paying = true }) {
                Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Record payment")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val text = vm.statementText(customer.id)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share statement"))
                }
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Share statement")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("History", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(ledger, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.note)
                        Text(dateTime(entry.createdAt), style = MaterialTheme.typography.bodySmall)
                    }
                    val payment = entry.amount < 0
                    Text(
                        if (payment) "- ${(-entry.amount).money()}" else "+ ${entry.amount.money()}",
                        color = if (payment) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }

    if (paying) {
        InputDialog(
            title = "Payment from ${customer.name}",
            label = "Amount (₹)",
            initial = "",
            confirmText = "Save",
            number = true,
            onConfirm = { text ->
                parseMoney(text)?.takeIf { it > 0 }?.let { vm.recordPayment(customer.id, it) }
                paying = false
            },
            onDismiss = { paying = false },
        )
    }
}
