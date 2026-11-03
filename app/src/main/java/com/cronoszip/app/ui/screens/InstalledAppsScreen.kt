package com.cronoszip.app.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.AppExtraction
import com.cronoszip.app.data.InstalledApp
import com.cronoszip.app.data.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Browse installed applications and back up their APK files to Downloads/Chronos Apps. */
@Composable
fun InstalledAppsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val byteProgress = remember { MutableStateFlow(0L to 0L) }
    val (copiedBytes, totalBytes) by byteProgress.collectAsState()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var activePackage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        loading = true
        apps = withContext(Dispatchers.IO) { InstalledApps.list(context) }
        loading = false
    }

    val visibleApps = apps.filter { app ->
        (showSystemApps || !app.isSystem) &&
            (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Installed apps", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text(
            "Back up APKs (including split APK bundles) to Downloads/Chronos Apps.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search apps") },
            singleLine = true,
        )
        TextButton(onClick = { showSystemApps = !showSystemApps }) {
            Text(if (showSystemApps) "Hide system apps" else "Show system apps")
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        if (activePackage != null && totalBytes > 0L) {
            LinearProgressIndicator(
                progress = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        when {
            loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            visibleApps.isEmpty() -> Text(
                if (apps.isEmpty()) "No installed apps are visible to this app." else "No apps match your search.",
                modifier = Modifier.padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${app.versionName.ifBlank { "Unknown version" }} · ${if (app.isSplit) "Split APK" else "APK"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                enabled = activePackage == null,
                                onClick = {
                                    activePackage = app.packageName
                                    message = "Backing up ${app.label}…"
                                    byteProgress.value = 0L to app.totalBytes
                                    scope.launch {
                                        val result: Result<AppExtraction> = withContext(Dispatchers.IO) {
                                            InstalledApps.extract(app) { copied, total ->
                                                byteProgress.value = copied to total
                                            }
                                        }
                                        message = result.fold(
                                            onSuccess = { backup ->
                                                "Saved ${app.label} (${backup.files} APK file(s)) to ${backup.folder}."
                                            },
                                            onFailure = { error -> error.message ?: "Backup failed." },
                                        )
                                        activePackage = null
                                    }
                                },
                            ) { Text(if (activePackage == app.packageName) "Saving…" else "Back up") }
                        }
                    }
                }
            }
        }
    }
}
