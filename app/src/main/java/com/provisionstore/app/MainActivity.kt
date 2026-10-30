package com.provisionstore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.provisionstore.app.ui.ItemsScreen
import com.provisionstore.app.ui.KhataScreen
import com.provisionstore.app.ui.SalesScreen
import com.provisionstore.app.ui.ShopViewModel
import com.provisionstore.app.ui.StockScreen
import com.provisionstore.app.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

// Icons are from Material Icons (Apache 2.0).
private enum class Screen(val label: String, val icon: ImageVector, val needsItems: Boolean) {
    SELL("Sell", Icons.Filled.ShoppingCart, needsItems = true),
    ITEMS("Items", Icons.Filled.Category, needsItems = true),
    STOCK("Stock", Icons.Filled.Inventory2, needsItems = true),
    KHATA("Khata", Icons.Filled.Book, needsItems = false),
    DAY("Day", Icons.Filled.CalendarToday, needsItems = false),
}

@Composable
fun AppRoot(vm: ShopViewModel = viewModel()) {
    var current by rememberSaveable { mutableStateOf(Screen.DAY) }
    val pending by vm.pendingUpdate.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val trackItems by vm.trackItems.collectAsState()

    // Look for an update each time the app opens. Stay quiet if there is none.
    LaunchedEffect(Unit) { vm.checkForUpdates(silent = true) }

    val visible = Screen.entries.filter { trackItems || !it.needsItems }
    val shown = if (current in visible) current else Screen.DAY

    Scaffold(
        bottomBar = {
            NavigationBar {
                visible.forEach { screen ->
                    NavigationBarItem(
                        selected = shown == screen,
                        onClick = { current = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Download progress stays visible on every tab.
            progress?.let { fraction ->
                if (fraction < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
            Box(Modifier.weight(1f)) {
                when (shown) {
                    Screen.SELL -> SalesScreen(vm)
                    Screen.ITEMS -> ItemsScreen(vm)
                    Screen.STOCK -> StockScreen(vm)
                    Screen.KHATA -> KhataScreen(vm)
                    Screen.DAY -> TodayScreen(vm)
                }
            }
        }
    }

    pending?.let { update ->
        AlertDialog(
            onDismissRequest = { vm.dismissUpdate() },
            title = { Text("Update available") },
            text = { Text("Version ${update.versionCode} is ready. Download and install now?") },
            confirmButton = { TextButton(onClick = { vm.installUpdate(update) }) { Text("Update") } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdate() }) { Text("Later") } },
        )
    }
}
