package com.provisionstore.app.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.provisionstore.app.data.Item
import com.provisionstore.app.util.parseQty
import com.provisionstore.app.util.qty

@Composable
fun StockScreen(vm: ShopViewModel) {
    val itemList by vm.items.collectAsState(initial = emptyList())
    var addingTo by remember { mutableStateOf<Item?>(null) }

    val lowCount = itemList.count { it.stock <= it.reorderLevel }
    // Low-stock items first.
    val sorted = itemList.sortedBy { it.stock > it.reorderLevel }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Stock", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (lowCount == 0) "All items are above their reorder level" else "$lowCount item(s) low on stock",
            color = if (lowCount == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(sorted, key = { it.id }) { item ->
                val low = item.stock <= item.reorderLevel
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Stock ${item.stock.qty()} ${item.unit}",
                                color = if (low) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        OutlinedButton(onClick = { addingTo = item }) { Text("Add stock") }
                    }
                }
            }
        }
    }

    addingTo?.let { item ->
        InputDialog(
            title = "Add stock: ${item.name}",
            label = "Quantity received (${item.unit})",
            initial = "",
            confirmText = "Add",
            number = true,
            onConfirm = { text ->
                parseQty(text)?.takeIf { it > 0 }?.let { vm.addStock(item.id, it) }
                addingTo = null
            },
            onDismiss = { addingTo = null },
        )
    }
}
