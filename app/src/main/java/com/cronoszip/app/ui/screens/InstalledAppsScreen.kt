package com.cronoszip.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.AppExtraction
import com.cronoszip.app.data.AppPrefs
import com.cronoszip.app.data.InstalledApp
import com.cronoszip.app.data.InstalledApps
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Modifier.appGlassSurface(radius: Dp = 20.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(if (AppPrefs.amoled) Color.Black else GlassWhite.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(radius))

/** Browse installed applications and back up their APK files to Downloads/Chronos Apps. */
@Composable
fun InstalledAppsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val byteProgress = remember { MutableStateFlow(0L to 0L) }
    val progress by byteProgress.collectAsState()
    val copiedBytes = progress.first
    val totalBytes = progress.second

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var sortBySize by remember { mutableStateOf(false) }
    var activePackage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context) {
        loading = true
        apps = withContext(Dispatchers.IO) { InstalledApps.list(context) }
        loading = false
    }

    val visibleApps = remember(apps, showSystemApps, query, sortBySize) {
        val filtered = apps.filter { app ->
            (showSystemApps == app.isSystem) &&
                (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true))
        }
        if (sortBySize) {
            filtered.sortedByDescending { it.totalBytes }
        } else {
            filtered.sortedBy { it.label.lowercase() }
        }
    }

    val userCount = remember(apps) { apps.count { !it.isSystem } }
    val systemCount = remember(apps) { apps.count { it.isSystem } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CRONOS MANAGER · APK BACKUP",
                    style = MaterialTheme.typography.labelSmall,
                    color = CronosCyan,
                    letterSpacing = 1.3.sp,
                )
                Text(
                    text = "Installed Apps",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GlassWhite.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Back up single APKs and split APK bundles to Downloads/Chronos Apps",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(10.dp))

        // Compact Search Bar Pill matching FileManagerScreen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(50))
                .background(GlassWhite.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                maxLines = 1,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = TextPrimary,
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search app name or package...",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { query = "" },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Filter & Sort Chips Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                GlassChip(
                    text = "User apps ($userCount)",
                    selected = !showSystemApps,
                    onClick = { showSystemApps = false },
                )
            }
            item {
                GlassChip(
                    text = "System ($systemCount)",
                    selected = showSystemApps,
                    onClick = { showSystemApps = true },
                )
            }
            item {
                GlassChip(
                    text = if (sortBySize) "Sort: Largest" else "Sort: Name",
                    selected = sortBySize,
                    onClick = { sortBySize = !sortBySize },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        message?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appGlassSurface(12.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = CronosCyan,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (activePackage != null && totalBytes > 0L) {
            val fraction = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appGlassSurface(14.dp)
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Extracting APK… ${formatBytes(copiedBytes)} / ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CronosCyan,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = CronosCyan,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .appGlassSurface(22.dp)
                .padding(10.dp),
        ) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp)
                }
                visibleApps.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (apps.isEmpty()) "No installed apps are visible." else "No apps match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        val iconBitmap = remember(app.packageName) { InstalledApps.icon(context, app.packageName, 112) }
                        val isSaving = activePackage == app.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GlassWhite.copy(alpha = 0.035f))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CronosCyan.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CronosCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (app.isSplit) CronosBlue.copy(alpha = 0.35f)
                                                else CronosCyan.copy(alpha = 0.16f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = if (app.isSplit) "SPLIT" else "APK",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = CronosCyan,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    Text(
                                        text = "${formatBytes(app.totalBytes)} · v${app.versionName.ifBlank { "1.0" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSaving) CronosCyan.copy(alpha = 0.25f)
                                        else CronosBlue.copy(alpha = 0.45f)
                                    )
                                    .border(1.dp, CronosCyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                                    .clickable(enabled = activePackage == null) {
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
                                                    "Saved ${app.label} (${backup.files} file(s)) to ${backup.folder}"
                                                },
                                                onFailure = { error -> error.message ?: "Backup failed." },
                                            )
                                            activePackage = null
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isSaving) "Saving…" else "Backup",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
