package com.provisionstore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

private val tabs = listOf("Sell", "Items", "Stock", "Khata", "Today")

@Composable
fun AppRoot(vm: ShopViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pending by vm.pendingUpdate.collectAsState()
    val progress by vm.downloadProgress.collectAsState()

    // Look for an update each time the app opens. Stay quiet if there is none.
    LaunchedEffect(Unit) { vm.checkForUpdates(silent = true) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(label.first().toString()) },
                        label = { Text(label) },
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
                when (tab) {
                    0 -> SalesScreen(vm)
                    1 -> ItemsScreen(vm)
                    2 -> StockScreen(vm)
                    3 -> KhataScreen(vm)
                    else -> TodayScreen(vm)
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
