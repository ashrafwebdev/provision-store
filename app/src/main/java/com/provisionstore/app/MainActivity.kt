package com.provisionstore.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }

    fun checkForUpdates(silent: Boolean) {
        busy = true
        scope.launch {
            val update = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdate() }
            busy = false
            if (update != null) {
                pending = update
            } else if (!silent) {
                status = "No update available."
            }
        }
    }

    // Look for an update each time the app opens. Stay quiet if there is none.
    LaunchedEffect(Unit) { checkForUpdates(silent = true) }

    pending?.let { update ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Update available") },
            text = { Text("Version ${update.versionCode} is ready. Download and install now?") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    busy = true
                    status = "Downloading update..."
                    scope.launch {
                        try {
                            val apk = withContext(Dispatchers.IO) { UpdateChecker.download(context, update) }
                            UpdateChecker.install(context, apk)
                            status = ""
                        } catch (e: Exception) {
                            status = e.message ?: "Update failed."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Later") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Provision Store", style = MaterialTheme.typography.headlineMedium)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        Button(enabled = !busy, onClick = { checkForUpdates(silent = false) }) {
            Text("Check for updates")
        }
        Spacer(Modifier.height(16.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
