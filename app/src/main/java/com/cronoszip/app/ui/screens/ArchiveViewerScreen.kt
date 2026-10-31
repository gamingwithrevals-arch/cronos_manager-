package com.cronoszip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.ArchiveConfig
import com.cronoszip.app.data.ArchiveRepository
import com.cronoszip.app.data.EngineException
import com.cronoszip.app.data.EntryData
import com.cronoszip.app.data.ListData
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.components.StatTile
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shows what is inside an archive without extracting anything. The engine reads only the
 * archive's index, so nothing is written to storage. Extract hands the archive to the
 * existing extraction flow.
 */
@Composable
fun ArchiveViewerScreen(
    path: String,
    onBack: () -> Unit,
    onExtract: () -> Unit,
) {
    val repository = remember { ArchiveRepository() }
    var loading by remember(path) { mutableStateOf(true) }
    var listing by remember(path) { mutableStateOf<ListData?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(path) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.list(ArchiveConfig(archive = path)) }
        }
        result
            .onSuccess { listing = it }
            .onFailure { failure ->
                listing = null
                error = if (failure is EngineException && failure.code == "PASSWORD_NEEDED") {
                    "This archive is password protected. Tap Extract to enter its password."
                } else {
                    failure.message ?: "The archive could not be read."
                }
            }
        loading = false
    }

    val shown = remember(listing, query) {
        val all = listing?.entries.orEmpty()
        if (query.isBlank()) all else all.filter { it.path.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader(
            title = listing?.displayName ?: archiveName(path),
            subtitle = listing?.let { "${it.format.uppercase()} · ${it.entryCount} entries" } ?: "Reading archive",
            onBack = onBack,
        )

        listing?.let { info ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(label = "Packed", value = formatBytes(info.archiveSize), modifier = Modifier.weight(1f))
                StatTile(label = "Unpacked", value = formatBytes(info.totalSize), modifier = Modifier.weight(1f), valueColor = CronosCyan)
            }
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("Filter by name", color = TextSecondary) },
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CronosCyan)
                }
                error != null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    GlassCard(glow = Warning) {
                        Text("Cannot show contents", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (listing?.truncated == true) {
                        item {
                            Text(
                                text = "Showing the first 5000 entries.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Warning,
                            )
                        }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                text = "No entries match.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                    items(shown) { entry -> EntryRow(entry) }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                text = "Extract this archive",
                icon = Icons.Rounded.Upload,
                modifier = Modifier.fillMaxWidth(),
                onClick = onExtract,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: EntryData) {
    val isDir = entry.kind == "dir"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassWhite.copy(alpha = 0.035f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            tint = if (isDir) CronosCyan else Color(0xFF9CC7FF),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.path.substringAfterLast('/').ifEmpty { entry.path },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val parent = entry.path.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                Text(
                    text = parent,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isDir) "Folder" else formatBytes(entry.size),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

private fun archiveName(path: String): String = path.substringAfterLast('/').ifEmpty { path }
