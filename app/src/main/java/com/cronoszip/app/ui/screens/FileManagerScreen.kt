package com.cronoszip.app.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cronoszip.app.data.DocumentsProviderApps
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.FileOperations
import com.cronoszip.app.data.MixedFileOps
import com.cronoszip.app.data.PinnedLocation
import com.cronoszip.app.data.PinnedStorage
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.components.StorageSpaceBar
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.FileOpener
import com.cronoszip.app.util.formatBytes
import com.cronoszip.app.util.formatTime
import com.cronoszip.app.util.hasStorageAccess
import com.cronoszip.app.util.isArchive
import com.cronoszip.app.util.requestAllFilesAccess
import com.cronoszip.app.viewmodel.CronosViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Items picked in select mode, shared by both panes. */
private class SelectionState {
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<String>()
    var batch by mutableStateOf<BatchClip?>(null)
}

/** Items waiting to be pasted: copied (cut = false) or moved (cut = true). */
private data class BatchClip(val paths: List<String>, val cut: Boolean)

private fun toggleSelect(sel: SelectionState, path: String) {
    if (path in sel.selected) sel.selected.remove(path) else sel.selected.add(path)
}

private fun toggleSelecting(sel: SelectionState) {
    sel.selecting = !sel.selecting
    if (!sel.selecting) sel.selected.clear()
}

/** Puts the selected items on the batch (copy or move) and leaves select mode. Returns the count. */
private fun startBatch(sel: SelectionState, cut: Boolean): Int {
    val paths = sel.selected.toList()
    sel.batch = BatchClip(paths, cut)
    sel.selecting = false
    sel.selected.clear()
    return paths.size
}

@Composable
fun FileManagerScreen(
    viewModel: CronosViewModel,
    onBack: (() -> Unit)?,
    onOpenArchive: (String) -> Unit,
    onExtract: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    val pinned = viewModel.pinned
    val roots = remember(context, pinned) { managerRoots(context, pinned) }
    var split by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var pathA by remember { mutableStateOf("") }
    var pathB by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    val sel = remember { SelectionState() }

    LaunchedEffect(roots) {
        if (pathA.isBlank() || roots.none { it.second == pathA }) {
            pathA = roots.firstOrNull()?.second.orEmpty()
        }
        if (pathB.isBlank() || roots.none { it.second == pathB }) {
            pathB = roots.getOrNull(1)?.second ?: roots.firstOrNull()?.second.orEmpty()
        }
    }

    // Changing folder ends the current selection, so picks never point at the wrong folder.
    LaunchedEffect(pathA, pathB) {
        sel.selected.clear()
    }

    // The phone back button goes up one folder in the left pane. In select mode it leaves select mode first.
    val upA = upPathOf(pathA)
    BackHandler(enabled = granted && upA != null) {
        if (upA != null) pathA = upA
    }
    BackHandler(enabled = sel.selecting) {
        sel.selecting = false
        sel.selected.clear()
    }

    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = hasStorageAccess(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasStorageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader(
            title = "Chronos Manager",
            subtitle = "Files · storage · archives",
            onBack = onBack,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(GlassWhite.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { GlassChip(text = "List", selected = !split, onClick = { split = false }) }
            item { GlassChip(text = "Split view", selected = split, onClick = { split = true }) }
            item { GlassChip(text = "Select", selected = sel.selecting, onClick = { toggleSelecting(sel) }) }
            item { GlassChip(text = "Storage", onClick = { showStorage = true }) }
            item { GlassChip(text = "Extract archive", onClick = onExtract) }
        }
        Spacer(Modifier.height(10.dp))

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }

        if (sel.selecting) {
            SelectionBar(
                count = sel.selected.size,
                onCopy = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        val n = startBatch(sel, cut = false)
                        message = "Copied $n item(s). Open a folder and tap Paste here."
                    }
                },
                onMove = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        val n = startBatch(sel, cut = true)
                        message = "Cut $n item(s). Open a folder and tap Move here."
                    }
                },
                onDelete = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        confirmBatchDelete = true
                    }
                },
                onClear = {
                    sel.selecting = false
                    sel.selected.clear()
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))

        if (!granted) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                GlassCard(glow = CronosCyan) {
                    Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(CronosCyan.copy(alpha = 0.14f)).border(1.dp, CronosCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Storage access is needed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("Chronos Manager needs access to read files and open archives across storage.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(text = "Allow access", icon = Icons.Rounded.Storage, modifier = Modifier.fillMaxWidth(), onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) requestAllFilesAccess(context)
                        else {
                            val perms = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            legacyPermission.launch(perms)
                        }
                    })
                }
            }
        } else if (split) {
            SplitPanes(
                pathA = pathA,
                pathB = pathB,
                onPathA = { pathA = it },
                onPathB = { pathB = it },
                roots = roots,
                sel = sel,
                refreshToken = refresh,
                onChanged = { refresh++ },
                onOpenArchive = onOpenArchive,
            )
        } else {
            SinglePane(
                path = pathA,
                onPath = { pathA = it },
                roots = roots,
                sel = sel,
                refreshToken = refresh,
                onChanged = { refresh++ },
                onOpenArchive = onOpenArchive,
            )
        }
    }

    if (showStorage) {
        AddStorageDialog(
            pinned = pinned,
            onAddTree = { label, uri, pkg -> viewModel.addPinnedTree(label, uri, pkg) },
            onAddPath = { label, path -> viewModel.addPinnedPath(label, path) },
            onRemove = { viewModel.removePinned(it) },
            onDismiss = { showStorage = false },
        )
    }

    if (confirmBatchDelete) {
        Dialog(onDismissRequest = { confirmBatchDelete = false }) {
            SolidDialogCard(glow = Warning) {
                Text("Delete ${sel.selected.size} item(s)?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "They will be deleted permanently. Folders are deleted with everything inside them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { confirmBatchDelete = false },
                    )
                    PrimaryButton(
                        text = "Delete",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val paths = sel.selected.toList()
                            confirmBatchDelete = false
                            sel.selecting = false
                            sel.selected.clear()
                            scope.launch {
                                val results = withContext(Dispatchers.IO) {
                                    paths.map { p ->
                                        if (SafStorage.isSaf(p)) SafStorage.delete(context, Uri.parse(p))
                                        else FileOperations.delete(File(p))
                                    }
                                }
                                val failed = results.count { it.isFailure }
                                message = if (failed == 0) {
                                    "Deleted ${paths.size} item(s)."
                                } else {
                                    "Deleted ${paths.size - failed}. $failed failed."
                                }
                                refresh++
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { GlassChip(text = "Copy $count", selected = true, onClick = onCopy) }
        item { GlassChip(text = "Move $count", onClick = onMove) }
        item { GlassChip(text = "Delete $count", onClick = onDelete) }
        item { GlassChip(text = "Clear", onClick = onClear) }
    }
}

/** The folder above [path], or null when there is none (the top of the storage). */
private fun upPathOf(path: String): String? {
    if (path.isBlank()) return null
    val parent = if (SafStorage.isSaf(path)) {
        SafStorage.parentTreeUri(Uri.parse(path))?.toString()
    } else {
        File(path).parent
    }
    return parent?.takeIf { it != path }
}

/**
 * What tapping an entry does. Folders navigate, archives go to the extraction flow, APKs and
 * other files open in the matching Android app, and SAF documents open through the provider.
 */
private fun openEntry(
    context: Context,
    current: String,
    item: FileItem,
    navigate: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (item.directory) {
        val id = item.documentId
        if (id != null && SafStorage.isSaf(current)) {
            val child = SafStorage.childTreeUri(Uri.parse(current), id)
            if (child != null) navigate(child.toString()) else onMessage("Cannot open this folder.")
        } else {
            navigate(item.path)
        }
        return
    }
    if (isArchive(item.name)) {
        if (SafStorage.isSaf(item.path)) {
            val documentUri = Uri.parse(item.path)
            val direct = PinnedStorage.resolveDocumentPath(documentUri)
            if (direct != null) {
                onOpenArchive(direct)
            } else {
                val cached = SafStorage.copyToCache(context, documentUri, item.name)
                if (cached != null) {
                    onMessage("Copied ${item.name} to app cache for extraction.")
                    onOpenArchive(cached.absolutePath)
                } else {
                    onMessage("Could not copy the archive out of the document provider.")
                }
            }
        } else {
            onOpenArchive(item.path)
        }
        return
    }
    if (SafStorage.isSaf(item.path)) {
        runCatching { context.startActivity(SafStorage.viewIntent(context, Uri.parse(item.path), null)) }
            .onFailure { onMessage(it.message ?: "No app can open this file.") }
        return
    }
    FileOpener.open(context, item.path)
        .onFailure { onMessage(it.message ?: "This file could not be opened.") }
}

@Composable
private fun SinglePane(
    path: String,
    onPath: (String) -> Unit,
    roots: List<Pair<String, String>>,
    sel: SelectionState,
    refreshToken: Int,
    onChanged: () -> Unit,
    onOpenArchive: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    val upPath = upPathOf(path)

    LaunchedEffect(path, refreshToken) {
        if (path.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val items = FileListing.list(context, path)
        loading = false
        if (items == null) {
            error = "Cannot read this folder."
            entries = emptyList()
        } else {
            entries = items
        }
    }

    fun pasteHere() {
        val batch = sel.batch ?: return
        val here = path
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                batch.paths.map { MixedFileOps.paste(context, here, it, batch.cut) }
            }
            val ok = results.count { it.isSuccess }
            val failed = results.firstOrNull { it.isFailure }?.exceptionOrNull()?.message
            if (batch.cut && failed == null) sel.batch = null
            status = if (failed == null) "Pasted $ok item(s)." else "Pasted $ok. Failed: $failed"
            onChanged()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(
                text = "New folder",
                onClick = {
                    inputName = ""
                    showNewFolder = true
                },
            )
            val batch = sel.batch
            GlassChip(
                text = if (batch?.cut == true) "Move here" else "Paste here",
                selected = batch != null,
                onClick = if (batch != null) {
                    { pasteHere() }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roots) { root ->
                GlassChip(text = root.first, selected = path == root.second, onClick = { onPath(root.second) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = SafStorage.locationLabel(context, path), style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StorageSpaceBar(path = path, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CronosCyan) }
                error != null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { GlassCard(glow = Warning) { Text("Cannot read folder", style = MaterialTheme.typography.titleMedium, color = TextPrimary); Spacer(Modifier.height(6.dp)); Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary); Spacer(Modifier.height(12.dp)); SecondaryButton("Try again", modifier = Modifier.fillMaxWidth(), onClick = { onChanged() }) } }
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (upPath != null) {
                        item { Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPath(upPath) }.background(GlassWhite.copy(alpha = 0.035f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Upload, contentDescription = null, tint = TextSecondary); Spacer(Modifier.width(10.dp)); Text("Up one folder", color = TextSecondary) } }
                    }
                    items(entries) { item ->
                        val checked = item.path in sel.selected
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (sel.selecting) {
                                    toggleSelect(sel, item.path)
                                } else {
                                    openEntry(context, path, item, navigate = onPath, onOpenArchive = onOpenArchive, onMessage = { status = it })
                                }
                            },
                            contentPadding = PaddingValues(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sel.selecting) {
                                    Icon(
                                        imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (checked) CronosCyan else TextSecondary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(
                                            if (item.directory) {
                                                Brush.linearGradient(
                                                    listOf(CronosCyan.copy(alpha = 0.24f), CronosBlue.copy(alpha = 0.08f)),
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(CronosBlue.copy(alpha = 0.3f), CronosCyan.copy(alpha = 0.1f)),
                                                )
                                            },
                                        )
                                        .border(
                                            1.dp,
                                            if (item.directory) CronosCyan.copy(alpha = 0.25f) else CronosBlue.copy(alpha = 0.3f),
                                            RoundedCornerShape(13.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(if (item.directory) Icons.Rounded.Folder else if (isArchive(item.name)) Icons.Rounded.Archive else Icons.Rounded.Description, contentDescription = null, tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF))
                                }
                                Spacer(Modifier.width(13.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(if (item.directory) "Folder" else "${formatBytes(item.size)} · ${formatTime(item.modified)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                if (!sel.selecting && (item.directory || isArchive(item.name))) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.55f),
                                    )
                                }
                                if (!sel.selecting) {
                                    IconButton(onClick = { actionItem = item }) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "File actions",
                                            tint = TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    actionItem?.let { item ->
        Dialog(onDismissRequest = { actionItem = null }) {
            SolidDialogCard(glow = CronosCyan) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (item.directory) "Folder" else "File",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    text = "Select",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.selecting = true
                        if (item.path !in sel.selected) sel.selected.add(item.path)
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Copy",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.path), cut = false)
                        status = "Copied ${item.name}. Open a folder and tap Paste here."
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Cut / move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.path), cut = true)
                        status = "Cut ${item.name}. Open a folder and tap Move here."
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Rename",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        inputName = item.name
                        renameItem = item
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Delete",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        deleteItem = item
                        actionItem = null
                    },
                )
            }
        }
    }

    if (showNewFolder) {
        Dialog(onDismissRequest = { showNewFolder = false }) {
            SolidDialogCard(glow = CronosCyan) {
                Text("New folder", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Folder name", color = TextSecondary) },
                    singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { showNewFolder = false },
                    )
                    PrimaryButton(
                        text = "Create",
                        modifier = Modifier.weight(1f),
                        enabled = inputName.isNotBlank(),
                        onClick = {
                            val name = inputName.trim()
                            val here = path
                            showNewFolder = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (SafStorage.isSaf(here)) {
                                        SafStorage.createFolder(context, Uri.parse(here), name)
                                            .map { SafStorage.documentName(context, it) ?: name }
                                    } else {
                                        FileOperations.createFolder(File(here), name).map { it.name }
                                    }
                                }
                                result
                                    .onSuccess { status = "Created $it" }
                                    .onFailure { status = it.message }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }

    renameItem?.let { item ->
        Dialog(onDismissRequest = { renameItem = null }) {
            SolidDialogCard(glow = CronosCyan) {
                Text("Rename", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("New name", color = TextSecondary) },
                    singleLine = true,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { renameItem = null },
                    )
                    PrimaryButton(
                        text = "Rename",
                        modifier = Modifier.weight(1f),
                        enabled = inputName.isNotBlank(),
                        onClick = {
                            val name = inputName.trim()
                            val target = item
                            renameItem = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (SafStorage.isSaf(target.path)) {
                                        SafStorage.rename(context, Uri.parse(target.path), name)
                                            .map { SafStorage.documentName(context, it) ?: name }
                                    } else {
                                        FileOperations.rename(File(target.path), name).map { it.name }
                                    }
                                }
                                result
                                    .onSuccess { status = "Renamed to $it" }
                                    .onFailure { status = it.message }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }

    deleteItem?.let { item ->
        Dialog(onDismissRequest = { deleteItem = null }) {
            SolidDialogCard(glow = Warning) {
                Text("Delete?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (item.directory) {
                        "The folder and everything inside it will be deleted permanently."
                    } else {
                        "The file will be deleted permanently."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { deleteItem = null },
                    )
                    PrimaryButton(
                        text = "Delete",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = item
                            deleteItem = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    if (SafStorage.isSaf(target.path)) {
                                        SafStorage.delete(context, Uri.parse(target.path))
                                    } else {
                                        FileOperations.delete(File(target.path))
                                    }
                                }
                                result
                                    .onSuccess { status = "Deleted ${target.name}" }
                                    .onFailure { status = it.message }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitPanes(
    pathA: String,
    pathB: String,
    onPathA: (String) -> Unit,
    onPathB: (String) -> Unit,
    roots: List<Pair<String, String>>,
    sel: SelectionState,
    refreshToken: Int,
    onChanged: () -> Unit,
    onOpenArchive: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pane(
            modifier = Modifier.weight(1f),
            path = pathA,
            onPath = onPathA,
            roots = roots,
            sel = sel,
            refreshToken = refreshToken,
            onChanged = onChanged,
            onOpenArchive = onOpenArchive,
        )
        Pane(
            modifier = Modifier.weight(1f),
            path = pathB,
            onPath = onPathB,
            roots = roots,
            sel = sel,
            refreshToken = refreshToken,
            onChanged = onChanged,
            onOpenArchive = onOpenArchive,
        )
    }
}

@Composable
private fun Pane(
    modifier: Modifier = Modifier,
    path: String,
    onPath: (String) -> Unit,
    roots: List<Pair<String, String>>,
    sel: SelectionState,
    refreshToken: Int,
    onChanged: () -> Unit,
    onOpenArchive: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<FileItem?>(null) }
    val upPath = upPathOf(path)

    LaunchedEffect(path, refreshToken) {
        if (path.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val items = FileListing.list(context, path)
        loading = false
        if (items == null) {
            error = "Cannot read"
            entries = emptyList()
        } else {
            entries = items
        }
    }

    fun pasteHere() {
        val batch = sel.batch ?: return
        val here = path
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                batch.paths.map { MixedFileOps.paste(context, here, it, batch.cut) }
            }
            val ok = results.count { it.isSuccess }
            val failed = results.firstOrNull { it.isFailure }?.exceptionOrNull()?.message
            if (batch.cut && failed == null) sel.batch = null
            status = if (failed == null) "Pasted $ok item(s)." else "Pasted $ok. Failed: $failed"
            onChanged()
        }
    }

    GlassCard(modifier = modifier.fillMaxHeight(), contentPadding = PaddingValues(8.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                items(roots) { root ->
                    GlassChip(text = root.first, selected = path == root.second, onClick = { onPath(root.second) })
                }
                item {
                    val batch = sel.batch
                    GlassChip(
                        text = if (batch?.cut == true) "Move here" else "Paste here",
                        selected = batch != null,
                        onClick = if (batch != null) {
                            { pasteHere() }
                        } else {
                            null
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = SafStorage.locationLabel(context, path),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CronosCyan,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp) }
                    error != null -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = Warning)
                        Spacer(Modifier.height(10.dp))
                        SecondaryButton("Try again", onClick = { onChanged() })
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (upPath != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GlassWhite.copy(alpha = 0.03f))
                                        .clickable { onPath(upPath) }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Upload, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text("Up one folder", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        items(entries) { item ->
                            val checked = item.path in sel.selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (checked) CronosCyan.copy(alpha = 0.12f) else GlassWhite.copy(alpha = 0.035f),
                                    )
                                    .clickable {
                                        if (sel.selecting) {
                                            toggleSelect(sel, item.path)
                                        } else {
                                            openEntry(context, path, item, navigate = onPath, onOpenArchive = onOpenArchive, onMessage = { status = it })
                                        }
                                    }
                                    .padding(start = 8.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (sel.selecting) {
                                    Icon(
                                        imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (checked) CronosCyan else TextSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Icon(
                                    imageVector = if (item.directory) Icons.Rounded.Folder else if (isArchive(item.name)) Icons.Rounded.Archive else Icons.Rounded.Description,
                                    contentDescription = null,
                                    tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (item.directory) "Folder" else formatBytes(item.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                    )
                                }
                                if (!sel.selecting) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable { action = item },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "File actions",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    action?.let { item ->
        Dialog(onDismissRequest = { action = null }) {
            SolidDialogCard(glow = CronosCyan) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Select",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.selecting = true
                        if (item.path !in sel.selected) sel.selected.add(item.path)
                        action = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Copy",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.path), cut = false)
                        action = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Cut / move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.path), cut = true)
                        action = null
                    },
                )
            }
        }
    }
}

@Composable
private fun AddStorageDialog(
    pinned: List<PinnedLocation>,
    onAddTree: (String, Uri, String?) -> String?,
    onAddPath: (String, String) -> Unit,
    onRemove: (PinnedLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val providers = remember(context) { DocumentsProviderApps.list(context) }
    var pendingLabel by remember { mutableStateOf("Storage") }
    var pendingPackage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            error = onAddTree(pendingLabel, uri, pendingPackage)
        }
    }

    fun pick(label: String, packageName: String?, authority: String?) {
        pendingLabel = label
        pendingPackage = packageName
        error = null
        treeLauncher.launch(authority?.let { DocumentsProviderApps.initialTreeUri(context, it) })
    }

    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Add app storage", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick an app that provides storage, or choose any folder. Android asks you to confirm " +
                    "the folder once and Chronos Manager keeps that access.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            if (pinned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                pinned.forEach { pin ->
                    val shownPath = if (pin.isRoot) pin.path else pin.treeUri?.let { SafStorage.displayName(context, it) ?: it.toString() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pin.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(shownPath ?: pin.treeUri?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemove(pin) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${pin.label}", tint = Warning, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Warning)
            }

            Spacer(Modifier.height(14.dp))
            Text("Storage apps", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            if (providers.isEmpty()) {
                Text("No storage apps found. Use Choose a folder instead.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            providers.take(8).forEach { provider ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { pick(provider.label, provider.packageName, provider.authority) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(provider.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(provider.packageName, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { pick("Storage", null, null) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose a folder…", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    onAddPath("App storage", context.filesDir.absolutePath)
                }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Add Chronos Manager app storage", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }

            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                text = "Done",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

fun managerRoots(
    context: android.content.Context,
    pinned: List<PinnedLocation> = emptyList(),
): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    pinned.forEach { pin ->
        if (pin.isRoot) {
            val path = pin.path
            if (!path.isNullOrBlank() && File(path).canRead()) list.add(pin.label to path)
        } else {
            pin.treeUri?.let { list.add(pin.label to it.toString()) }
        }
    }
    val external = android.os.Environment.getExternalStorageDirectory()
    external?.let { list.add("Internal" to it.absolutePath) }
    val download = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
    if (download != null && download.absolutePath != external?.absolutePath) list.add("Downloads" to download.absolutePath)
    val documents = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
    if (documents != null) list.add("Documents" to documents.absolutePath)
    val movies = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
    if (movies != null) list.add("Movies" to movies.absolutePath)
    val music = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
    if (music != null) list.add("Music" to music.absolutePath)
    val pictures = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
    if (pictures != null) list.add("Pictures" to pictures.absolutePath)
    context.getExternalFilesDir(null)?.let { list.add("App storage (external)" to it.absolutePath) }
    context.filesDir?.let { list.add("App storage (internal)" to it.absolutePath) }
    context.cacheDir?.let { list.add("App cache" to it.absolutePath) }
    return list.distinctBy { it.second }
}
