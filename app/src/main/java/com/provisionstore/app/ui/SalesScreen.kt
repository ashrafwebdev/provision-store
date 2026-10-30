package com.provisionstore.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.provisionstore.app.data.CartLine
import com.provisionstore.app.data.CustomerWithBalance
import com.provisionstore.app.data.Item
import com.provisionstore.app.data.PaymentMode
import com.provisionstore.app.util.money
import com.provisionstore.app.util.parseQty
import com.provisionstore.app.util.qty
import kotlinx.coroutines.launch

@Composable
fun SalesScreen(vm: ShopViewModel) {
    val itemList by vm.items.collectAsState(initial = emptyList())
    val customerList by vm.customers.collectAsState(initial = emptyList())
    val cart = remember { mutableStateListOf<CartLine>() }
    var search by rememberSaveable { mutableStateOf("") }
    var pickedItem by remember { mutableStateOf<Item?>(null) }
    var editLine by remember { mutableStateOf<CartLine?>(null) }
    var showCheckout by remember { mutableStateOf(false) }
    var savedBillId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val total = cart.sumOf { it.lineTotal }
    val shownItems = itemList.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }

    // Quantity 0 or less removes the line from the cart.
    fun setQty(item: Item, qty: Double) {
        val index = cart.indexOfFirst { it.item.id == item.id }
        if (qty <= 0) {
            if (index >= 0) cart.removeAt(index)
        } else if (index >= 0) {
            cart[index] = CartLine(item, qty)
        } else {
            cart.add(CartLine(item, qty))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Sell", style = MaterialTheme.typography.headlineSmall)

        if (cart.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(cart, key = { it.item.id }) { line ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editLine = line }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.item.name)
                                    Text(
                                        "${line.qty.qty()} ${line.item.unit} x ${line.item.sellPrice.money()}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Text(line.lineTotal.money())
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Total ${total.money()}", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { showCheckout = true }) { Text("Charge") }
                    }
                }
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search item") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        if (itemList.isEmpty()) {
            Text("No items yet. Add them in the Items tab.", modifier = Modifier.padding(top = 12.dp))
        }

        LazyColumn(Modifier.weight(1f)) {
            items(shownItems, key = { it.id }) { item ->
                Card(
                    onClick = { pickedItem = item },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Stock ${item.stock.qty()} ${item.unit}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("${item.sellPrice.money()} / ${item.unit}")
                    }
                }
            }
        }
    }

    pickedItem?.let { item ->
        val alreadyInCart = cart.find { it.item.id == item.id }?.qty ?: 0.0
        InputDialog(
            title = item.name,
            label = "Quantity (${item.unit})",
            initial = "1",
            confirmText = "Add",
            number = true,
            onConfirm = { text ->
                parseQty(text)?.takeIf { it > 0 }?.let { setQty(item, alreadyInCart + it) }
                pickedItem = null
            },
            onDismiss = { pickedItem = null },
        )
    }

    editLine?.let { line ->
        InputDialog(
            title = line.item.name,
            label = "Quantity (${line.item.unit}), 0 removes",
            initial = line.qty.qty(),
            confirmText = "Set",
            number = true,
            onConfirm = { text ->
                parseQty(text)?.let { setQty(line.item, it) }
                editLine = null
            },
            onDismiss = { editLine = null },
        )
    }

    if (showCheckout) {
        CheckoutDialog(
            total = total,
            customers = customerList,
            onDismiss = { showCheckout = false },
            onConfirm = { mode, customerId ->
                showCheckout = false
                vm.checkout(cart.toList(), mode, customerId) { saleId ->
                    cart.clear()
                    savedBillId = saleId
                }
            },
        )
    }

    savedBillId?.let { id ->
        AlertDialog(
            onDismissRequest = { savedBillId = null },
            title = { Text("Bill saved") },
            text = { Text("Bill #$id saved and stock updated.") },
            confirmButton = {
                TextButton(onClick = {
                    savedBillId = null
                    scope.launch {
                        val text = vm.billText(id)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Share bill"))
                    }
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { savedBillId = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun CheckoutDialog(
    total: Long,
    customers: List<CustomerWithBalance>,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMode, Long?) -> Unit,
) {
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var customerId by remember { mutableStateOf<Long?>(null) }
    val canConfirm = mode != PaymentMode.CREDIT || customerId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Total ${total.money()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.name) },
                        )
                    }
                }
                if (mode == PaymentMode.CREDIT) {
                    Text("Choose the customer")
                    if (customers.isEmpty()) {
                        Text("Add a customer in the Khata tab first.")
                    }
                    Column(
                        Modifier
                            .heightIn(max = 220.dp)
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
                enabled = canConfirm,
                onClick = { onConfirm(mode, if (mode == PaymentMode.CREDIT) customerId else null) },
            ) { Text("Save bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
