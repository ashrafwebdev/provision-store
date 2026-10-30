package com.provisionstore.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import com.provisionstore.app.util.inputUnit
import com.provisionstore.app.util.money
import com.provisionstore.app.util.parseEntry
import com.provisionstore.app.util.quickAmounts
import com.provisionstore.app.util.toEntry
import kotlin.math.abs
import kotlin.math.roundToLong

/** A dialog with one text field. [number] switches the keyboard to numbers. */
@Composable
fun InputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    number: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = if (number) {
                    KeyboardOptions(keyboardType = KeyboardType.Decimal)
                } else {
                    KeyboardOptions.Default
                },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Asks for a quantity of an item. Kg and litre items take grams and millilitres, with quick
 * buttons for common amounts. The price updates as the quantity is typed.
 * [initial] is a stored quantity, or null for an empty box.
 */
@Composable
fun QuantityDialog(
    title: String,
    unit: String,
    pricePerUnit: Long,
    priceLabel: String = "Price",
    initial: Double?,
    confirmText: String,
    onConfirm: (Double) -> Unit,
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toEntry(unit) ?: "") }
    val quantity = parseEntry(text, unit)?.takeIf { it > 0 }
    val quick = quickAmounts(unit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (quick.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quick.forEach { (label, amount) ->
                            FilterChip(
                                selected = quantity != null && abs(quantity - amount) < 0.0001,
                                onClick = { text = amount.toEntry(unit) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Quantity (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (quantity != null) {
                    Text(
                        "$priceLabel: ${(pricePerUnit * quantity).roundToLong().money()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = quantity != null, onClick = { quantity?.let(onConfirm) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
