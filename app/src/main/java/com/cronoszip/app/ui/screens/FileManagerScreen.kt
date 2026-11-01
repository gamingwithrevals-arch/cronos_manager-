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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cronoszip.app.data.BatchExtractor
import com.cronoszip.app.data.BatchItem
import com.cronoszip.app.data.BatchState
import com.cronoszip.app.data.BatchStatus
import com.cronoszip.app.data.Compressor
import com.cronoszip.app.data.DocumentsProviderApps
import com.cronoszip.app.data.FileClipboard
import com.cronoszip.app.data.FileDetails
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.FileOperations
import com.cronoszip.app.data.FileTools
import com.cronoszip.app.data.ListOptions
import com.cronoszip.app.data.MixedFileOps
import com.cronoszip.app.data.PinnedLocation
import com.cronoszip.app.data.PinnedStorage
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.data.SortMode
import com.cronoszip.app.ui.components.BackCircle
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.components.StorageSpaceBar
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.Success
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

private fun startBatch(sel: SelectionState, cut: Boolean): Int {
    val paths = sel.selected.toList()
    sel.batch = BatchClip(paths, cut)
    sel.selecting = false
    sel.selected.clear()
    return paths.size
}

private fun isLocalArchive(path: String): Boolean =
    !SafStorage.isSaf(path) && isArchive(File(path).name)

private fun batchDone(b: BatchState): Int =
    b.items.count { it.status != BatchStatus.Waiting && it.status != BatchStatus.Working }

/** The message shown when a folder can't be listed. Explains Android's restriction on app data folders. */
private fun unreadableMessage(path: String): String =
    if (path.contains("/Android/data") || path.contains("/Android/obb")) {
        "Android does not let Chronos Manager read other apps' private folders " +
            "(Android/data and Android/obb) on this phone."
    } else {
        "This folder can't be read."
    }

/** A soft, rounded surface used for every panel, so the whole screen looks consistent. */
private fun Modifier.glassSurface(radius: Dp = 22.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(GlassWhite.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(radius))

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
    var activePane by remember { mutableStateOf(0) }
    var pathA by remember { mutableStateOf("") }
    var pathB by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf("Archive.zip") }
    var compressing by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.Name) }
    var descending by remember { mutableStateOf(false) }
    val opts = ListOptions(showHidden = showHidden, sort = sortMode, descending = descending)
    val batch by BatchExtractor.state.collectAsState()
    val sel = remember { SelectionState() }

    val activePath = if (split && activePane == 1) pathB else pathA
    fun setActivePath(path: String) {
        if (split && activePane == 1) pathB = path else pathA = path
    }

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

    // When a batch finishes, refresh the lists so new folders and files appear.
    LaunchedEffect(batch.finished) {
        if (batch.finished) refresh++
    }

    // The phone back button goes up one folder in the active pane. In select mode it leaves select mode first.
    val upActive = upPathOf(activePath)
    BackHandler(enabled = granted && upActive != null) {
        if (upActive != null) setActivePath(upActive)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        TopBar(
            title = "Files",
            onBack = onBack,
            onMore = { showOptions = true },
        )
        Spacer(Modifier.height(14.dp))

        ModeSwitch(split = split, onChange = { split = it; activePane = 0 })
        Spacer(Modifier.height(12.dp))

        LocationStrip(roots = roots, activePath = activePath, onPick = { setActivePath(it) })
        Spacer(Modifier.height(12.dp))

        if (granted) {
            PathCard(path = activePath)
            Spacer(Modifier.height(10.dp))
        }

        if (batch.items.isNotEmpty()) {
            BatchPill(batch = batch, onClick = { showBatch = true })
            Spacer(Modifier.height(10.dp))
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !granted -> PermissionCard(
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            requestAllFilesAccess(context)
                        } else {
                            val perms = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            legacyPermission.launch(perms)
                        }
                    },
                )
                split -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    FolderView(
                        path = pathA,
                        onPath = { pathA = it },
                        sel = sel,
                        opts = opts,
                        refreshToken = refresh,
                        compact = true,
                        highlighted = activePane == 0,
                        onActivate = { activePane = 0 },
                        onChanged = { refresh++ },
                        onOpenArchive = onOpenArchive,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    FolderView(
                        path = pathB,
                        onPath = { pathB = it },
                        sel = sel,
                        opts = opts,
                        refreshToken = refresh,
                        compact = true,
                        highlighted = activePane == 1,
                        onActivate = { activePane = 1 },
                        onChanged = { refresh++ },
                        onOpenArchive = onOpenArchive,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                else -> FolderView(
                    path = pathA,
                    onPath = { pathA = it },
                    sel = sel,
                    opts = opts,
                    refreshToken = refresh,
                    compact = false,
                    highlighted = false,
                    onActivate = {},
                    onChanged = { refresh++ },
                    onOpenArchive = onOpenArchive,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (sel.selecting) {
            Spacer(Modifier.height(10.dp))
            SelectionDock(
                count = sel.selected.size,
                archiveCount = sel.selected.count { isLocalArchive(it) },
                zipCount = sel.selected.count { !SafStorage.isSaf(it) },
                onCopy = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        val n = startBatch(sel, cut = false)
                        message = "Copied $n item(s). Open a folder and tap Paste."
                    }
                },
                onMove = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        val n = startBatch(sel, cut = true)
                        message = "Cut $n item(s). Open a folder and tap Paste."
                    }
                },
                onDelete = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        confirmBatchDelete = true
                    }
                },
                onExtract = {
                    val archives = sel.selected.filter { isLocalArchive(it) }
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else if (archives.isEmpty()) {
                        message = "Select archive files (zip, 7z, tar, rar) to extract."
                    } else if (batch.running || viewModel.extraction.value.running) {
                        message = "Another extraction is running. Wait for it to finish."
                    } else {
                        val s = viewModel.settings
                        BatchExtractor.start(archives, s.keepArchiveByDefault, s.keepFreeBytes, s.verifyFirstByDefault)
                        sel.selecting = false
                        sel.selected.clear()
                        showBatch = true
                    }
                },
                onZip = {
                    val zipCount = sel.selected.count { !SafStorage.isSaf(it) }
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else if (zipCount == 0) {
                        message = "Only files on phone storage can be zipped."
                    } else if (SafStorage.isSaf(activePath)) {
                        message = "Open a folder on phone storage in the active pane to save the zip."
                    } else {
                        showCompress = true
                    }
                },
                onDone = {
                    sel.selecting = false
                    sel.selected.clear()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showOptions) {
        OptionsDialog(
            showHidden = showHidden,
            onHidden = { showHidden = it },
            sortMode = sortMode,
            onSort = { sortMode = it },
            descending = descending,
            onDescending = { descending = it },
            onExtract = {
                showOptions = false
                onExtract()
            },
            onStorage = {
                showOptions = false
                showStorage = true
            },
            onSettings = {
                showOptions = false
                onSettings()
            },
            onDismiss = { showOptions = false },
        )
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

    if (showBatch) {
        BatchDialog(
            batch = batch,
            onStop = { BatchExtractor.stop() },
            onHide = { showBatch = false },
            onClose = {
                showBatch = false
                BatchExtractor.clear()
            },
        )
    }

    if (showCompress) {
        CompressDialog(
            count = sel.selected.count { !SafStorage.isSaf(it) },
            busy = compressing,
            name = zipName,
            onName = { zipName = it },
            onCancel = { showCompress = false },
            onCompress = {
                val inputs = sel.selected.filter { !SafStorage.isSaf(it) }
                val typed = zipName.trim()
                val clean = if (typed.endsWith(".zip", ignoreCase = true)) typed else "$typed.zip"
                if (typed.isBlank() || typed.contains('/') || typed.contains('\\')) {
                    message = "Use a plain name for the zip file."
                } else if (inputs.isEmpty()) {
                    message = "Nothing to zip. Select files on phone storage."
                } else {
                    val output = File(activePath, clean).absolutePath
                    compressing = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            Compressor.compress(output, inputs, store = false)
                        }
                        compressing = false
                        showCompress = false
                        sel.selecting = false
                        sel.selected.clear()
                        message = result.fold(
                            onSuccess = { "Created $clean with ${it.entries} entries (${formatBytes(it.bytes)})." },
                            onFailure = { it.message ?: "Compression failed." },
                        )
                        refresh++
                    }
                }
            },
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
private fun TopBar(title: String, onBack: (() -> Unit)?, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackCircle(onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CHRONOS MANAGER",
                style = MaterialTheme.typography.labelSmall,
                color = CronosCyan,
                letterSpacing = 1.4.sp,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassWhite.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "Options",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ModeSwitch(split: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(50.dp)
            .padding(4.dp),
    ) {
        ModeSegment(label = "Browse", selected = !split, modifier = Modifier.weight(1f)) { onChange(false) }
        ModeSegment(label = "Split", selected = split, modifier = Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) CronosBlue.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun LocationStrip(
    roots: List<Pair<String, String>>,
    activePath: String,
    onPick: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(roots) { root ->
            GlassChip(
                text = root.first,
                selected = activePath == root.second,
                onClick = { onPick(root.second) },
            )
        }
    }
}

@Composable
private fun PathCard(path: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(20.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = SafStorage.locationLabel(context, path),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        StorageSpaceBar(path = path)
    }
}

@Composable
private fun BatchPill(batch: BatchState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (batch.running) "Extracting ${batchDone(batch)} of ${batch.items.size}" else "Batch finished",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "View",
            style = MaterialTheme.typography.labelLarge,
            color = CronosCyan,
        )
    }
}

@Composable
private fun PermissionCard(onAllow: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        GlassCard(glow = CronosCyan) {
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
            Text("Storage access is needed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Chronos Manager needs access to read files and open archives.",
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
}

@Composable
private fun SelectionDock(
    count: Int,
    archiveCount: Int,
    zipCount: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onExtract: () -> Unit,
    onZip: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(26.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count selected",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelLarge,
                color = CronosCyan,
                modifier = Modifier.clickable(onClick = onDone).padding(4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GlassChip(text = "Copy", selected = true, onClick = onCopy) }
            item { GlassChip(text = "Move", onClick = onMove) }
            item { GlassChip(text = "Zip", selected = zipCount > 0, onClick = onZip) }
            item { GlassChip(text = "Extract", selected = archiveCount > 0, onClick = onExtract) }
            item { GlassChip(text = "Delete", onClick = onDelete) }
        }
    }
}

@Composable
private fun OptionsDialog(
    showHidden: Boolean,
    onHidden: (Boolean) -> Unit,
    sortMode: SortMode,
    onSort: (SortMode) -> Unit,
    descending: Boolean,
    onDescending: (Boolean) -> Unit,
    onExtract: () -> Unit,
    onStorage: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Options", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Show")
            SwitchRow(label = "Hidden files", checked = showHidden, onChange = onHidden)

            Spacer(Modifier.height(14.dp))
            SectionTitle("Sort by")
            SortMode.entries.forEach { mode ->
                ChoiceRow(label = mode.label, selected = mode == sortMode, onClick = { onSort(mode) })
            }
            SwitchRow(label = "Descending", checked = descending, onChange = onDescending)

            Spacer(Modifier.height(14.dp))
            SectionTitle("Tools")
            LinkRow(label = "Extract archive", onClick = onExtract)
            LinkRow(label = "Storage locations", onClick = onStorage)
            LinkRow(label = "Settings", onClick = onSettings)

            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Done", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) TextPrimary else TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp),
        )
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
private fun FolderView(
    path: String,
    onPath: (String) -> Unit,
    sel: SelectionState,
    opts: ListOptions,
    refreshToken: Int,
    compact: Boolean,
    highlighted: Boolean,
    onActivate: () -> Unit,
    onChanged: () -> Unit,
    onOpenArchive: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<FileItem?>(null) }
    var infoItem by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    val upPath = upPathOf(path)

    LaunchedEffect(path, refreshToken, opts) {
        if (path.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        val items = FileListing.list(context, path, opts)
        loading = false
        if (items == null) {
            error = unreadableMessage(path)
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

    Column(
        modifier = modifier
            .glassSurface(if (compact) 20.dp else 24.dp)
            .then(
                if (highlighted && compact) {
                    Modifier.border(1.dp, CronosCyan.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = compact) { onActivate() }
            .padding(if (compact) 8.dp else 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(
                text = "+ Folder",
                onClick = {
                    inputName = ""
                    showNewFolder = true
                },
            )
            GlassChip(text = "+ File", onClick = { showNewFile = true })
            val batch = sel.batch
            if (batch != null) {
                GlassChip(text = "Paste", selected = true, onClick = { pasteHere() })
            }
        }

        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp)
                }
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Try again", onClick = { onChanged() })
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (upPath != null) {
                        item {
                            UpRow(compact = compact, onClick = { onPath(upPath) })
                        }
                    }
                    itemsIndexed(entries, key = { _, item -> item.path }) { index, item ->
                        EntryRow(
                            item = item,
                            selecting = sel.selecting,
                            checked = item.path in sel.selected,
                            compact = compact,
                            divider = index < entries.lastIndex,
                            onTap = {
                                if (sel.selecting) {
                                    toggleSelect(sel, item.path)
                                } else {
                                    openEntry(context, path, item, navigate = onPath, onOpenArchive = onOpenArchive, onMessage = { status = it })
                                }
                            },
                            onMore = { actionItem = item },
                        )
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
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                SecondaryButton(
                    text = "Info",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        infoItem = item
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
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
                        status = "Copied ${item.name}. Open a folder and tap Paste."
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.pa
