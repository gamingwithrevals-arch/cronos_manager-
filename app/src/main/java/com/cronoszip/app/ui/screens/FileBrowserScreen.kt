package com.cronoszip.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cronoszip.app.data.BrowserMode
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.ui.components.BackCircle
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.util.formatBytes
import com.cronoszip.app.util.formatTime
import com.cronoszip.app.util.hasStorageAccess
import com.cronoszip.app.util.requestAllFilesAccess
import com.cronoszip.app.viewmodel.CronosViewModel

private const val ARCHIVE_FOOTER =
    "Showing folders and archives: ZIP, 7Z, TAR, TAR.GZ, TGZ, RAR, split .001+ sets and self-extracting ZIPs."
private const val FOLDER_FOOTER =
    "The archive shrinks in place; its output can live anywhere on the same volume."

/** Picks the folder for an extraction, or the archive to open. Uses the same liquid-glass look as the file manager. */
@Composable
fun FileBrowserScreen(
    viewModel: CronosViewModel,
    mode: BrowserMode,
    onBack: () -> Unit,
    onFolderChosen: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    val browser by viewModel.browser.collectAsState()

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = hasStorageAccess(context)
        viewModel.refreshBrowserAfterPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasStorageAccess(context)
                granted = now
                if (now) viewModel.refreshBrowserAfterPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(mode) {
        viewModel.enterBrowser(mode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        BrowserHeader(mode = mode, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        if (!granted) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PermissionCard(
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            requestAllFilesAccess(context)
                        } else {
                            val permissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                )
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            legacyPermission.launch(permissions)
                        }
                    },
                )
            }
            return@Column
        }

        PathPanel(path = browser.current)
        Spacer(Modifier.height(10.dp))

        RootChips(
            roots = browser.roots,
            current = browser.current,
            onPick = { viewModel.navigateBrowser(it) },
        )
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                browser.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = CronosCyan,
                        strokeWidth = 3.dp,
                        trackColor = Color.White.copy(alpha = 0.08f),
                    )
                }
                browser.error != null -> ErrorPanel(
                    message = browser.error.orEmpty(),
                    onRetry = { viewModel.navigateBrowser(browser.current) },
                )
                else -> FolderList(
                    mode = mode,
                    parent = browser.parent,
                    entries = browser.items,
                    onNavigate = { viewModel.navigateBrowser(it) },
                    onOpen = { item ->
                        if (item.directory) {
                            viewModel.navigateBrowser(item.path)
                        } else if (item.archive) {
                            viewModel.checkArchive(item.path)
                        }
                    },
                )
            }
        }

        if (mode == BrowserMode.Folder) {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = "Use this folder",
                icon = Icons.Rounded.Upload,
                enabled = browser.current.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { onFolderChosen(browser.current) },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BrowserHeader(mode: BrowserMode, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackCircle(onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "CRONOS MANAGER",
                style = MaterialTheme.typography.labelSmall,
                color = CronosCyan,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = if (mode == BrowserMode.Folder) "Choose destination" else "Choose archive",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Text(
                text = if (mode == BrowserMode.Folder) {
                    "Files are extracted into this folder"
                } else {
                    "Any part of a split archive works"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun PathPanel(path: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(20.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = CronosCyan,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = path.ifBlank { "Storage" },
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RootChips(
    roots: List<com.cronoszip.app.data.StorageRoot>,
    current: String,
    onPick: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(roots, key = { it.path }) { root ->
            GlassChip(
                text = root.label,
                selected = current == root.path,
                onClick = { onPick(root.path) },
            )
        }
    }
}

@Composable
private fun FolderList(
    mode: BrowserMode,
    parent: String?,
    entries: List<FileItem>,
    onNavigate: (String) -> Unit,
    onOpen: (FileItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (parent != null) {
            item(key = "up") {
                UpRow(onClick = { onNavigate(parent) })
            }
        }
        items(entries, key = { it.path }) { item ->
            FileRow(item = item, onClick = { onOpen(item) })
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                EmptyFolder()
            }
        }
        item(key = "footer") {
            Text(
                text = if (mode == BrowserMode.Archive) ARCHIVE_FOOTER else FOLDER_FOOTER,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(22.dp)
            .padding(18.dp),
    ) {
        Text(
            text = "Cannot read this folder",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            text = "Try again",
            modifier = Modifier.fillMaxWidth(),
            onClick = onRetry,
        )
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(28.dp)
            .padding(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CronosCyan.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Storage access is needed",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Chronos Manager needs access to read files and open archives. " +
                "Nothing is read or changed until you pick an archive and confirm its plan.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            text = "Allow access",
            icon = Icons.Rounded.Storage,
            modifier = Modifier.fillMaxWidth(),
            onClick = onAllow,
        )
    }
}

@Composable
private fun UpRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(18.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Upload,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Up one folder",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun FileRow(item: FileItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(20.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (item.directory) CronosCyan.copy(alpha = 0.16f) else CronosBlue.copy(alpha = 0.16f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (item.directory) Icons.Rounded.Folder else Icons.Rounded.Archive,
                contentDescription = null,
                tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.directory) {
                    "Folder"
                } else {
                    listOf(
                        formatBytes(item.size),
                        formatTime(item.modified).takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyFolder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(20.dp)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Download,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "No archives or folders here.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
