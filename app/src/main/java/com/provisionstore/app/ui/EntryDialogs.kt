package com.provisionstore.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.provisionstore.app.data.CustomerWithBalance
import com.provisionstore.app.data.PaymentMode
import com.provisionstore.app.util.money
import com.provisionstore.app.util.parseMoney

/** Expense categories shown as buttons when adding an expense. */
val expenseCategories = listOf(
    "Rent",
    "Electricity",
    "Salary",
    "Transport",
    "Shop expenses",
    "Tea and snacks",
    "Other",
)

/** A day-book sale: the total and how it was paid. Credit needs a customer. */
@Composable
fun QuickSaleDialog(
    customers: List<CustomerWithBalance>,
    onDismiss: () -> Unit,
    onSave: (Long, PaymentMode, Long?) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var customerId by remember { mutableStateOf<Long?>(null) }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }
    val canSave = amount != null && (mode != PaymentMode.CREDIT || customerId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total sale (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("How was it paid?", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PaymentMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.name) },
                        )
                    }
                }
                if (mode == PaymentMode.CREDIT) {
                    Text("Customer", style = MaterialTheme.typography.labelLarge)
                    if (customers.isEmpty()) {
                        Text("Add a customer in the Khata tab first.")
                    }
                    Column(
                        Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        customers.forEach { c ->
                            FilterChip(
                                selected = customerId == c.id,
                                onClick = { customerId = c.id },
                                label = { Text("${c.name} (${c.balance.money()})") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    amount?.let { onSave(it, mode, if (mode == PaymentMode.CREDIT) customerId else null) }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A purchase from a supplier, as a total. Supplier and note are optional. */
@Composable
fun PurchaseDialog(
    onDismiss: () -> Unit,
    onSave: (Long, String, String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Total bill (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text("Supplier (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                amount?.let { onSave(it, supplier.trim(), note.trim()) }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** An expense: pick a category, then the amount. Note is optional. */
@Composable
fun ExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit,
) {
    var category by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amount = parseMoney(amountText)?.takeIf { it > 0 }
    val selected = category

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    expenseCategories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null && selected != null, onClick = {
                if (amount != null && selected != null) onSave(selected, amount, note.trim())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
