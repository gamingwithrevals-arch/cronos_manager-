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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalUriHandler
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
import com.cronoszip.app.data.AppPrefs
import com.cronoszip.app.data.BatchExtractor
import com.cronoszip.app.data.BatchItem
import com.cronoszip.app.data.BatchState
import com.cronoszip.app.data.BatchStatus
import com.cronoszip.app.data.Compressor
import com.cronoszip.app.data.ConflictPolicy
import com.cronoszip.app.data.DocumentsProviderApps
import com.cronoszip.app.data.FileDetails
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.FileOperations
import com.cronoszip.app.data.FileSearch
import com.cronoszip.app.data.FileTools
import com.cronoszip.app.data.FileTransfer
import com.cronoszip.app.data.ListOptions
import com.cronoszip.app.data.PinnedLocation
import com.cronoszip.app.data.PinnedStorage
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.data.SortMode
import com.cronoszip.app.data.TransferKind
import com.cronoszip.app.data.TransferProgress
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

/** Remembers which finished extraction the Files screen already jumped to, so it jumps once per result. */
private object ExtractionHint {
    var applied: String? = null
}

/** Items picked in select mode, shared by both panes. */
private class SelectionState {
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<String>()
    var batch by mutableStateOf<BatchClip?>(null)
    var progress by mutableStateOf<TransferProgress?>(null)
    var tick by mutableStateOf(0)
}

/** Items waiting to be pasted: copied (cut = false) or moved (cut = true). */
private data class BatchClip(val paths: List<String>, val cut: Boolean)

private fun toggleSelect(sel: SelectionState, path: String) {
    if (path in sel.selected) sel.selected.remove(path) else sel.selected.add(path)
}

private fun isLocalArchive(path: String): Boolean =
    !SafStorage.isSaf(path) && isArchive(File(path).name)

private fun batchDone(b: BatchState): Int =
    b.items.count { it.status != BatchStatus.Waiting && it.status != BatchStatus.Working }

/** True when [path] is one of the storage roots or inside one of them. */
private fun underAnyRoot(path: String, roots: List<Pair<String, String>>): Boolean =
    roots.any { path == it.second || path.startsWith(it.second + "/") }

/** The message shown when a folder can't be listed. Explains Android's restriction on app data folders. */
private fun unreadableMessage(path: String): String =
    if (path.contains("/Android/data") || path.contains("/Android/obb")) {
        "Android does not let Chronos Manager read other apps' private folders " +
            "(Android/data and Android/obb) on this phone."
    } else {
        "This folder can't be read."
    }

/** A soft, rounded surface used for every panel. The AMOLED theme makes it pure black. */
private fun Modifier.glassSurface(radius: Dp = 22.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(if (AppPrefs.amoled) Color.Black else GlassWhite.copy(alpha = 0.05f))
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
    val extraction by viewModel.extraction.collectAsState()
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
    var showSearch by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
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

    LaunchedEffect(AppPrefs.amoled) { refresh++ }

    LaunchedEffect(roots) {
        if (pathA.isBlank() || !underAnyRoot(pathA, roots)) {
            pathA = roots.firstOrNull()?.second.orEmpty()
        }
        if (pathB.isBlank() || !underAnyRoot(pathB, roots)) {
            pathB = roots.getOrNull(1)?.second ?: roots.firstOrNull()?.second.orEmpty()
        }
    }

    // After an extraction finishes, open the folder the files were extracted into.
    LaunchedEffect(extraction.success, extraction.output) {
        val out = extraction.output
        if (extraction.success && out.isNotBlank() && out != ExtractionHint.applied) {
            ExtractionHint.applied = out
            pathA = out
            activePane = 0
            sel.selected.clear()
            message = "Showing the extracted files."
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

        LocationStrip(
            roots = roots,
            activePath = activePath,
            onPick = { setActivePath(it) },
            onAdd = { showStorage = true },
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(text = "Search", onClick = { showSearch = true })
            GlassChip(text = "Go to path", onClick = { showGoTo = true })
        }
        Spacer(Modifier.height(12.dp))

        if (granted) {
            PathCard(path = activePath)
            Spacer(Modifier.height(10.dp))
        }

        if (batch.items.isNotEmpty()) {
            BatchPill(batch = batch, onClick = { showBatch = true })
            Spacer(Modifier.height(10.dp))
        }

        sel.progress?.let { p ->
            TransferBar(p = p, onCancel = { FileTransfer.cancel() })
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
                onSelectAll = {
                    val visible = listAllVisible(roots, pathA, pathB, split, activePane, opts)
                    visible.forEach { if (it !in sel.selected) sel.selected.add(it) }
                    message = "Selected ${sel.selected.size} item(s)."
                },
                onCopy = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        sel.batch = BatchClip(sel.selected.toList(), cut = false)
                        sel.selecting = false
                        sel.selected.clear()
                        message = "Copied ${sel.batch?.paths?.size ?: 0} item(s). Open a folder and tap Paste here."
                    }
                },
                onMove = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        sel.batch = BatchClip(sel.selected.toList(), cut = true)
                        sel.selecting = false
                        sel.selected.clear()
                        message = "Cut ${sel.batch?.paths?.size ?: 0} item(s). Open a folder and tap Move here."
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
            amoled = AppPrefs.amoled,
            onAmoled = { AppPrefs.setAmoled(context, it) },
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

    if (showSearch) {
        SearchDialog(
            root = activePath,
            onPick = { item ->
                showSearch = false
                setActivePath(if (item.directory) item.path else File(item.path).parent.orEmpty())
            },
            onDismiss = { showSearch = false },
        )
    }

    if (showGoTo) {
        GoToDialog(
            current = activePath,
            onGo = { typed ->
                val dir = File(typed.trim())
                if (dir.isDirectory) {
                    setActivePath(dir.absolutePath)
                    showGoTo = false
                } else {
                    message = "That folder does not exist."
                }
            },
            onDismiss = { showGoTo = false },
        )
    }

    if (showCompress) {
        CompressDialog(
            count = sel.selected.count { !SafStorage.isSaf(it) },
            busy = compressing,
            name = zipName,
            onName = { zipName = it },
            onCancel = { showCompress = false },
            onCompress = { store ->
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
                            Compressor.compress(output, inputs, store = store)
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
                                sel.progress = TransferProgress("Deleting", "", 0L, 0L, 0, paths.size)
                                val result = withContext(Dispatchers.IO) {
                                    FileTransfer.run(TransferKind.Delete, paths, null) { p ->
                                        scope.launch { sel.progress = p }
                                    }
                                }
                                sel.progress = null
                                message = result.fold(
                                    onSuccess = { "Deleted $it item(s)." },
                                    onFailure = {
                                        if (it is InterruptedException) "Delete cancelled." else (it.message ?: "Delete failed.")
                                    },
                                )
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
                text = "CRONOS MANAGER",
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
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(roots) { root ->
            GlassChip(
                text = root.first,
                selected = activePath == root.second,
                onClick = { onPick(root.second) },
            )
        }
        item {
            GlassChip(text = "+ Add", onClick = onAdd)
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

/** The progress box shown while files are copied, moved or deleted. It can be cancelled. */
@Composable
private fun TransferBar(p: TransferProgress, onCancel: () -> Unit) {
    val fraction = when {
        p.totalBytes > 0L -> (p.doneBytes.toFloat() / p.totalBytes.toFloat()).coerceIn(0f, 1f)
        p.totalItems > 0 -> (p.doneItems.toFloat() / p.totalItems.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(20.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${p.title}… ${p.doneItems} of ${p.totalItems}",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = CronosCyan,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = CronosCyan,
            trackColor = Color.White.copy(alpha = 0.08f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (p.totalBytes > 0L) {
                "${formatBytes(p.doneBytes)} of ${formatBytes(p.totalBytes)} · ${p.current}"
            } else {
                p.current
            },
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = "Cancel", modifier = Modifier.fillMaxWidth(), onClick = onCancel)
    }
}

/** Every item in the active folder, used by Select all. Phone storage only. */
private fun listAllVisible(
    roots: List<Pair<String, String>>,
    pathA: String,
    pathB: String,
    split: Boolean,
    activePane: Int,
    opts: ListOptions,
): List<String> {
    val path = if (split && activePane == 1) pathB else pathA
    if (path.isBlank() || SafStorage.isSaf(path)) return emptyList()
    return FileListing.listLocal(path, opts).orEmpty().map { it.path }
}

@Composable
private fun SelectionDock(
    count: Int,
    archiveCount: Int,
    zipCount: Int,
    onSelectAll: () -> Unit,
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
            item { GlassChip(text = "Select all", onClick = onSelectAll) }
            item { GlassChip(text = "Copy", selected = count > 0, onClick = onCopy) }
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
    amoled: Boolean,
    onAmoled: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Options", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(14.dp))

            SectionTitle("Show")
            SwitchRow(label = "Hidden files", checked = showHidden, onChange = onHidden)
            SwitchRow(label = "AMOLED dark theme", checked = amoled, onChange = onAmoled)

            Spacer(Modifier.height(14.dp))
            SectionTitle("Sort by")
            SortMode.entries.forEach { mode ->
                ChoiceRow(label = mode.label, selected = mode == sortMode, onClick = { onSort(mode) })
            }
            SwitchRow(label = "Descending", checked = descending, onChange = onDescending)

            Spacer(Modifier.height(14.dp))
            SectionTitle("About")
            Text(
                text = "Chronos Manager brings HatchZip functionality to Android. It is a fork of Chronos Zip, " +
                    "which is a fork of HatchZip, expanded into a full-fledged file manager.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "By AizenEMU",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = "Support on YouTube",
                modifier = Modifier.fillMaxWidth(),
                onClick = { uriHandler.openUri("https://youtube.com/@aizenemu") },
            )

            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Done", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
@Composable
private fun SearchDialog(
    root: String,
    onPick: (FileItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Search", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Searches this folder and all subfolders.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name contains…", color = TextSecondary) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (searching) "Searching…" else "Search",
                modifier = Modifier.fillMaxWidth(),
                enabled = !searching && query.isNotBlank(),
                onClick = {
                    searching = true
                    scope.launch {
                        results = withContext(Dispatchers.IO) { FileSearch.find(root, query) }
                        searching = false
                        searched = true
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (searched && results.isEmpty()) {
                    Text("No matches.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                results.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (item.directory) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null,
                            tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = File(item.path).parent.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "Close", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun GoToDialog(
    current: String,
    onGo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Go to path", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Type a full folder path, for example /storage/emulated/0/Download",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Folder path", color = TextSecondary) },
                singleLine = true,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(
                    text = "Go",
                    modifier = Modifier.weight(1f),
                    enabled = typed.isNotBlank(),
                    onClick = { onGo(typed) },
                )
            }
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
    var conflictNames by remember { mutableStateOf<List<String>?>(null) }
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

    // Runs the paste with the chosen conflict policy. Move clears the batch once it succeeds.
    fun runPaste(batch: BatchClip, policy: ConflictPolicy) {
        val here = path
        scope.launch {
            val kind = if (batch.cut) TransferKind.Move else TransferKind.Copy
            sel.progress = TransferProgress(if (batch.cut) "Moving" else "Copying", "", 0L, 0L, 0, batch.paths.size)
            val result = withContext(Dispatchers.IO) {
                FileTransfer.run(kind, batch.paths, File(here), policy) { p ->
                    scope.launch { sel.progress = p }
                }
            }
            sel.progress = null
            result
                .onSuccess {
                    if (batch.cut) sel.batch = null
                    status = "Pasted $it item(s)."
                }
                .onFailure {
                    status = if (it is InterruptedException) "Cancelled." else (it.message ?: "Paste failed.")
                }
            onChanged()
        }
    }

    // Asks first when something with the same name is already in this folder.
    fun pasteHere() {
        val batch = sel.batch ?: return
        if (SafStorage.isSaf(path)) {
            status = "Paste works in phone storage folders only."
            return
        }
        val names = FileTransfer.conflicts(batch.paths, File(path))
        if (names.isNotEmpty()) {
            conflictNames = names
        } else {
            runPaste(batch, ConflictPolicy.KeepBoth)
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
                        sel.batch = BatchClip(listOf(item.path), cut = true)
                        status = "Cut ${item.name}. Open a folder and tap Paste."
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
        infoItem?.let { item ->
        InfoDialog(item = item, onDismiss = { infoItem = null })
    }

    if (showNewFile) {
        NewFileDialog(
            dir = path,
            onResult = { status = it; onChanged() },
            onDismiss = { showNewFile = false },
        )
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
                                if (SafStorage.isSaf(target.path)) {
                                    val result = withContext(Dispatchers.IO) {
                                        SafStorage.delete(context, Uri.parse(target.path))
                                    }
                                    result
                                        .onSuccess { status = "Deleted ${target.name}" }
                                        .onFailure { status = it.message }
                                } else {
                                    sel.progress = TransferProgress("Deleting", target.name, 0L, 0L, 0, 1)
                                    val result = withContext(Dispatchers.IO) {
                                        FileTransfer.run(TransferKind.Delete, listOf(target.path), null) { p ->
                                            scope.launch { sel.progress = p }
                                        }
                                    }
                                    sel.progress = null
                                    result
                                        .onSuccess { status = "Deleted ${target.name}" }
                                        .onFailure {
                                            status = if (it is InterruptedException) "Delete cancelled." else it.message
                                        }
                                }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }

    // Shown when a paste would overwrite or merge with items that already exist.
    conflictNames?.let { names ->
        ConflictDialog(
            names = names,
            onChoose = { policy ->
                val b = sel.batch
                conflictNames = null
                if (b != null) runPaste(b, policy)
            },
            onCancel = { conflictNames = null },
        )
    }
}

@Composable
private fun UpRow(compact: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Upload,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Up one folder",
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
        )
    }
}

@Composable
private fun EntryRow(
    item: FileItem,
    selecting: Boolean,
    checked: Boolean,
    compact: Boolean,
    divider: Boolean,
    onTap: () -> Unit,
    onMore: () -> Unit,
) {
    val iconBox = if (compact) 28.dp else 40.dp
    val iconSize = if (compact) 16.dp else 20.dp
    val tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF)
    val background = if (item.directory) CronosCyan.copy(alpha = 0.14f) else CronosBlue.copy(alpha = 0.16f)
    val icon: ImageVector = when {
        item.directory -> Icons.Rounded.Folder
        isArchive(item.name) -> Icons.Rounded.Archive
        else -> Icons.Rounded.Description
    }
    val subtitle = when {
        item.directory -> "Folder"
        compact -> formatBytes(item.size)
        else -> "${formatBytes(item.size)} · ${formatTime(item.modified)}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(horizontal = if (compact) 2.dp else 4.dp, vertical = if (compact) 8.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) CronosCyan else TextSecondary,
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(iconBox)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
            }
            Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!selecting) {
                if (!compact && item.directory) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (compact) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onMore),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "File actions",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
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
        if (divider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (compact) 36.dp else 56.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f)),
            )
        }
    }
}

@Composable
private fun InfoDialog(item: FileItem, onDismiss: () -> Unit) {
    val details by produceState<FileDetails?>(initialValue = null, item.path) {
        value = if (SafStorage.isSaf(item.path)) {
            null
        } else {
            withContext(Dispatchers.IO) { FileTools.details(item.path) }
        }
    }
    val d = details
    val sizeText = when {
        d != null -> formatBytes(d.size)
        item.directory && SafStorage.isSaf(item.path) -> "Not available for this storage"
        item.directory -> "Calculating…"
        else -> formatBytes(item.size)
    }
    val typeText = when {
        item.directory -> "Folder"
        FileOpener.mimeFor(item.name) == "*/*" -> "File"
        else -> FileOpener.mimeFor(item.name)
    }
    Dialog(onDismissRequest = onDismiss) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Info", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            InfoRow("Type", typeText)
            InfoRow("Size", sizeText)
            if (d != null && d.isDirectory) InfoRow("Items inside", "${d.items} (direct)")
            InfoRow("Modified", formatTime(d?.modified ?: item.modified))
            InfoRow("Hidden", yesNo(d?.hidden ?: item.name.startsWith(".")))
            if (d != null) {
                InfoRow("Can read", yesNo(d.readable))
                InfoRow("Can write", yesNo(d.writable))
            }
            Spacer(Modifier.height(8.dp))
            Text("Location", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = d?.path ?: item.path,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(16.dp))
            SecondaryButton(text = "Close", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"
@Composable
private fun NewFileDialog(
    dir: String,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("New file.txt") }
    var busy by remember { mutableStateOf(false) }
    val extensions = listOf(".txt", ".md", ".json", ".html", ".csv", ".log", ".kt", ".xml")
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        SolidDialogCard(glow = CronosCyan) {
            Text("New file", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The file is created in the folder you are viewing.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("File name", color = TextSecondary) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(extensions) { ext ->
                    GlassChip(
                        text = ext,
                        onClick = { name = name.substringBeforeLast('.', name) + ext },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.weight(1f),
                    onClick = { if (!busy) onDismiss() },
                )
                PrimaryButton(
                    text = if (busy) "Creating…" else "Create",
                    modifier = Modifier.weight(1f),
                    enabled = !busy && name.isNotBlank(),
                    onClick = {
                        busy = true
                        val typed = name.trim()
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (SafStorage.isSaf(dir)) {
                                    "New files can only be created in phone storage folders."
                                } else {
                                    FileTools.createFile(File(dir), typed).fold(
                                        onSuccess = { "Created ${it.name}." },
                                        onFailure = { it.message ?: "The file could not be created." },
                                    )
                                }
                            }
                            busy = false
                            onResult(result)
                            onDismiss()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BatchDialog(
    batch: BatchState,
    onStop: () -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = { if (batch.running) onHide() else onClose() }) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Batch extract", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (batch.running) {
                    "${batchDone(batch)} of ${batch.items.size} done. Stop pauses the current archive and skips the rest."
                } else {
                    "Finished: ${batchDone(batch)} of ${batch.items.size}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                batch.items.forEach { item ->
                    BatchRow(item)
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (batch.running) {
                SecondaryButton(text = "Stop", modifier = Modifier.fillMaxWidth(), onClick = onStop)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Hide", modifier = Modifier.fillMaxWidth(), onClick = onHide)
            } else {
                PrimaryButton(text = "Close", modifier = Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

@Composable
private fun BatchRow(item: BatchItem) {
    val color = when (item.status) {
        BatchStatus.Done -> Success
        BatchStatus.Failed -> Danger
        BatchStatus.Skipped, BatchStatus.Paused -> Warning
        BatchStatus.Working -> CronosCyan
        BatchStatus.Waiting -> TextSecondary
    }
    val label = when (item.status) {
        BatchStatus.Waiting -> "Waiting"
        BatchStatus.Working -> "Extracting"
        BatchStatus.Done -> "Done"
        BatchStatus.Skipped -> "Skipped"
        BatchStatus.Failed -> "Failed"
        BatchStatus.Paused -> "Paused"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassWhite.copy(alpha = 0.035f))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        }
        if (item.message.isNotBlank()) {
            Text(
                text = item.message,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
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
            Text("Storage locations", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Add a folder or a storage app. Android asks you to confirm the folder once.",
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
                Text("No storage apps found.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
