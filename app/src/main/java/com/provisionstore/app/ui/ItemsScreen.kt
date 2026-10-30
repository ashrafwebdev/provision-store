package com.provisionstore.app.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.provisionstore.app.data.Item
import com.provisionstore.app.util.inputUnit
import com.provisionstore.app.util.moneyInput
import com.provisionstore.app.util.parseEntry
import com.provisionstore.app.util.parseMoney
import com.provisionstore.app.util.perUnit
import com.provisionstore.app.util.stockText
import com.provisionstore.app.util.toEntry
import com.provisionstore.app.util.unitChoices

@Composable
fun ItemsScreen(vm: ShopViewModel) {
    val itemList by vm.items.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Item?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Items", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text("Add item")
        }
        Spacer(Modifier.height(8.dp))
        if (itemList.isEmpty()) {
            Text("No items yet. Tap Add item to start.")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(itemList, key = { it.id }) { item ->
                Card(
                    onClick = { editing = item },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("Sell ${item.sellPrice.perUnit(item.unit)}")
                        Text("Cost ${item.costPrice.perUnit(item.unit)}")
                        Text("In stock ${item.stock.stockText(item.unit)}")
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ItemDialog(
            item = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { item ->
                vm.saveItem(item)
                adding = false
                editing = null
            },
            onDelete = { item ->
                vm.deleteItem(item.id)
                editing = null
            },
        )
    }
}

@Composable
private fun ItemDialog(
    item: Item?,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: (Item) -> Unit,
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var unit by remember { mutableStateOf(item?.unit ?: "pc") }
    var sell by remember { mutableStateOf(item?.sellPrice?.moneyInput() ?: "") }
    var cost by remember { mutableStateOf(item?.costPrice?.moneyInput() ?: "") }
    var stock by remember { mutableStateOf(item?.let { it.stock.toEntry(it.unit) } ?: "") }
    var reorder by remember { mutableStateOf(item?.let { it.reorderLevel.toEntry(it.unit) } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    val sellPrice = parseMoney(sell)
    val valid = name.isNotBlank() && unit.isNotBlank() && sellPrice != null && sellPrice > 0
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val choices = (unitChoices() + unit).distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add item" else "Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "For kg and litre items, type grams or ml. Example: 250 means 250 g.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                Text("Unit", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u) },
                        )
                    }
                }
                OutlinedTextField(
                    value = sell,
                    onValueChange = { sell = it },
                    label = { Text("Selling price (₹ per $unit)") },
                    singleLine = true,
                    keyboardOptions = decimal,
                )
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text("Cost price (₹ per $unit)") },
                    singleLine = true,
                    keyboardOptions = decimal,
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Stock on hand (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = decimal,
                )
                OutlinedTextField(
                    value = reorder,
                    onValueChange = { reorder = it },
                    label = { Text("Warn when below (${inputUnit(unit)})") },
                    singleLine = true,
                    keyboardOptions = decimal,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        Item(
                            id = item?.id ?: 0L,
                            name = name.trim(),
                            unit = unit.trim(),
                            sellPrice = sellPrice ?: 0L,
                            costPrice = parseMoney(cost) ?: 0L,
                            stock = parseEntry(stock, unit) ?: 0.0,
                            reorderLevel = parseEntry(reorder, unit) ?: 0.0,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (item != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${item.name}?") },
            text = { Text("The item is removed from the list. Past bills keep their record.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
