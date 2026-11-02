package com.cronoszip.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
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
import com.cronoszip.app.data.FileDetails
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.FileOperations
import com.cronoszip.app.data.FileSearch
import com.cronoszip.app.data.FileTools
import com.cronoszip.app.data.FileTransfer
import com.cronoszip.app.data.ListOptions
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Remembers which finished extraction the Files screen already jumped to, so it jumps once per result. */
private object ExtractionHint {
    var applied: String? = null
}

/** Items picked in select mode, plus the items waiting to be pasted. */
private class SelectionState {
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<String>()
    var batch by mutableStateOf<BatchClip?>(null)
    var progress by mutableStateOf<TransferProgress?>(null)
}

/** Items waiting to be pasted: copied (cut = false) or moved (cut = true). */
private data class BatchClip(val paths: List<String>, val cut: Boolean)

/** Which kinds of entries a quick jump shows. */
private enum class ListFilter { None, Apk, Archive }

/** One of the buttons in the quick jump row. */
private data class QuickJump(val label: String, val path: String, val filter: ListFilter)

/** The quick jump row: Internal, Images, Movies, Music, APP and Compress. */
private fun quickJumps(): List<QuickJump> {
    val internal = Environment.getExternalStorageDirectory().absolutePath
    val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    return listOf(
        QuickJump("Internal", internal, ListFilter.None),
        QuickJump("Images", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath, ListFilter.None),
        QuickJump("Movies", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath, ListFilter.None),
        QuickJump("Music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath, ListFilter.None),
        QuickJump("APP", download, ListFilter.Apk),
        QuickJump("Compress", download, ListFilter.Archive),
    )
}

/** Whether an entry should show under the current quick jump filter. Folders always show. */
private fun passesFilter(item: FileItem, filter: ListFilter): Boolean = when (filter) {
    ListFilter.None -> true
    ListFilter.Apk -> item.directory || item.name.endsWith(".apk", ignoreCase = true)
    ListFilter.Archive -> item.directory || isArchive(item.name)
}

/** The internal storage root. Navigation never goes above this folder. */
private fun internalRoot(): String = Environment.getExternalStorageDirectory().absolutePath

/** True when [path] is the internal root or inside it. */
private fun insideRoot(path: String): Boolean {
    val root = internalRoot()
    return path == root || path.startsWith("$root/")
}

private fun toggleSelect(sel: SelectionState, path: String) {
    if (path in sel.selected) sel.selected.remove(path) else sel.selected.add(path)
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

/** A soft, rounded surface used for every panel. The AMOLED theme makes it pure black. */
private fun Modifier.glassSurface(radius: Dp = 22.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(if (AppPrefs.amoled) Color.Black else GlassWhite.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(radius))
    /** How long the second back press has to come before the exit warning clears itself. */
private const val EXIT_WINDOW_MS = 2000L

/** Finds the Activity behind a context, so the back handler can close the app. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** The folder above [path]. Null at the internal root, so navigation never leaves internal storage. */
private fun upPathOf(path: String): String? {
    if (!insideRoot(path) || path == internalRoot()) return null
    return File(path).parent?.takeIf { insideRoot(it) }
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
    val rootPath = internalRoot()

    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    var split by remember { mutableStateOf(false) }
    var activePane by remember { mutableStateOf(0) }
    var pathA by remember { mutableStateOf(rootPath) }
    var pathB by remember { mutableStateOf(rootPath) }
    var filterA by remember { mutableStateOf(ListFilter.None) }
    var filterB by remember { mutableStateOf(ListFilter.None) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var exitArmed by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var zipName by remember { mutableStateOf("Archive.zip") }
    var compressing by remember { mutableStateOf(false) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var conflictNames by remember { mutableStateOf<List<String>?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.Name) }
    var descending by remember { mutableStateOf(false) }
    val opts = ListOptions(showHidden = showHidden, sort = sortMode, descending = descending)
    val batch by BatchExtractor.state.collectAsState()
    val extraction by viewModel.extraction.collectAsState()
    val sel = remember { SelectionState() }
    val pathSuggestions by produceState<List<String>>(initialValue = emptyList(), query) {
        val typed = query.trim()
        if (typed.startsWith("/")) {
            delay(120)
            value = withContext(Dispatchers.IO) { pathSuggestionsFor(typed) }
        } else {
            value = emptyList()
        }
    }

    val activePath = if (split && activePane == 1) pathB else pathA
    val activeFilter = if (split && activePane == 1) filterB else filterA
    val upActive = upPathOf(activePath)

    // Moves the active pane to a folder. Used by the back button and by browsing inside a folder.
    fun setActivePath(path: String) {
        if (split && activePane == 1) pathB = path else pathA = path
    }

    // Quick jump: moves the active pane to a folder and applies its filter. Clears the search.
    fun jumpTo(path: String, filter: ListFilter) {
        if (split && activePane == 1) {
            pathB = path
            filterB = filter
        } else {
            pathA = path
            filterA = filter
        }
        query = ""
    }

    // Takes the selected items as a pending copy or move. The paste bar then asks where to put them.
    fun takeSelection(cut: Boolean) {
        if (sel.selected.isEmpty()) {
            message = "Tap items to select them first."
            return
        }
        val picked = sel.selected.toList()
        sel.batch = BatchClip(picked, cut)
        sel.selecting = false
        sel.selected.clear()
        val verb = if (cut) "Cut" else "Copied"
        message = "$verb ${picked.size} item(s). Tap Paste here when you reach the folder."
    }

    // Runs the copy or move into the active folder with the chosen conflict policy.
    fun runPaste(pending: BatchClip, policy: ConflictPolicy) {
        val here = activePath
        scope.launch {
            val kind = if (pending.cut) TransferKind.Move else TransferKind.Copy
            sel.progress = TransferProgress(if (pending.cut) "Moving" else "Copying", "", 0L, 0L, 0, pending.paths.size)
            val result = withContext(Dispatchers.IO) {
                FileTransfer.run(kind, pending.paths, File(here), policy) { p ->
                    scope.launch { sel.progress = p }
                }
            }
            sel.progress = null
            result
                .onSuccess { count ->
                    if (pending.cut) {
                        sel.batch = null
                    }
                    message = "Pasted $count item(s)."
                }
                .onFailure { error ->
                    message = if (error is InterruptedException) "Cancelled." else (error.message ?: "Paste failed.")
                }
            refresh++
        }
    }

    // Checks for name clashes first. If there are any, the conflict dialog asks what to do.
    fun requestPaste() {
        val pending = sel.batch ?: return
        if (!insideRoot(activePath)) {
            message = "Paste works inside internal storage only."
            return
        }
        val names = FileTransfer.conflicts(pending.paths, File(activePath))
        if (names.isNotEmpty()) {
            conflictNames = names
        } else {
            runPaste(pending, ConflictPolicy.KeepBoth)
        }
    }

    LaunchedEffect(AppPrefs.amoled) { refresh++ }

    // Changing folder ends the current selection, so picks never point at the wrong folder.
    LaunchedEffect(pathA, pathB) {
        sel.selected.clear()
    }

    // When a batch finishes, refresh the lists so new files appear.
    LaunchedEffect(batch.finished) {
        if (batch.finished) refresh++
    }

    // After an extraction finishes, open the folder the files were extracted into.
    LaunchedEffect(extraction.success, extraction.output) {
        val out = extraction.output
        if (extraction.success && out.isNotBlank() && out != ExtractionHint.applied) {
            ExtractionHint.applied = out
            pathA = out
            filterA = ListFilter.None
            activePane = 0
            sel.selected.clear()
            message = "Showing the extracted files."
        }
    }

    // Live search for normal words. A path that starts with / shows folder suggestions instead.
    LaunchedEffect(query) {
        val typed = query.trim()
        if (typed.isBlank() || typed.startsWith("/")) {
            results = emptyList()
            searching = false
        } else {
            searching = true
            delay(250)
            results = withContext(Dispatchers.IO) { FileSearch.find(rootPath, typed, 200) }
            searching = false
        }
    }

    // The second back press within the window exits. The warning clears itself afterwards.
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_WINDOW_MS)
            exitArmed = false
            message = null
        }
    }

    // One back handler decides what Back does, in this order:
    // leave select mode, cancel a pending paste, clear the search, go up a folder, then exit on the second press.
    BackHandler(enabled = true) {
        when {
            sel.selecting -> {
                sel.selecting = false
                sel.selected.clear()
            }
            sel.batch != null -> {
                sel.batch = null
                message = "Cancelled."
            }
            query.isNotBlank() -> {
                query = ""
            }
            upActive != null -> {
                setActivePath(upActive)
            }
            exitArmed -> {
                context.findActivity()?.finish()
            }
            else -> {
                exitArmed = true
                message = "Press back again to exit."
            }
        }
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
            onMore = { showOptions = true },
        )
        Spacer(Modifier.height(12.dp))

        ModeSwitch(split = split, onChange = { split = it; activePane = 0 })
        Spacer(Modifier.height(12.dp))

        QuickJumpRow(
            activePath = activePath,
            activeFilter = activeFilter,
            onJump = { jump -> jumpTo(jump.path, jump.filter) },
        )
        Spacer(Modifier.height(10.dp))

        SearchBar(
            query = query,
            onQuery = { query = it },
            onClear = { query = "" },
        )
        Spacer(Modifier.height(10.dp))

        if (query.isNotBlank()) {
            SearchPanel(
                query = query,
                results = results,
                searching = searching,
                suggestions = pathSuggestions,
                onPickPath = { path -> jumpTo(path, ListFilter.None) },
                onPickItem = { item ->
                    if (item.directory) {
                        jumpTo(item.path, ListFilter.None)
                    } else {
                        jumpTo(File(item.path).parent.orEmpty(), ListFilter.None)
                        message = "Found ${item.name}."
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
             PathCard(path = activePath)
            Spacer(Modifier.height(10.dp))

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
                            filter = filterA,
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
                            filter = filterB,
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
                        filter = filterA,
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

            sel.batch?.let { pending ->
                Spacer(Modifier.height(10.dp))
                PasteBar(
                    count = pending.paths.size,
                    cut = pending.cut,
                    destination = activePath,
                    onPaste = { requestPaste() },
                    onCancel = {
                        sel.batch = null
                        message = "Cancelled."
                    },
                )
            }

            if (sel.selecting) {
                Spacer(Modifier.height(10.dp))
                SelectionDock(
                    count = sel.selected.size,
                    archiveCount = sel.selected.count { isLocalArchive(it) },
                    onSelectAll = {
                        listAllVisible(activePath, activeFilter, opts).forEach { p ->
                            if (p !in sel.selected) sel.selected.add(p)
                        }
                        message = "Selected ${sel.selected.size} item(s)."
                    },
                    onCopy = { takeSelection(cut = false) },
                    onMove = { takeSelection(cut = true) },
                    onDelete = {
                        if (sel.selected.isEmpty()) {
                            message = "Tap items to select them first."
                        } else {
                            confirmBatchDelete = true
                        }
                    },
                    onExtract = {
                        val archives = sel.selected.filter { isLocalArchive(it) }
                        when {
                            sel.selected.isEmpty() -> {
                                message = "Tap items to select them first."
                            }
                            archives.isEmpty() -> {
                                message = "Select archive files (zip, 7z, tar, rar) to extract."
                            }
                            batch.running || viewModel.extraction.value.running -> {
                                message = "Another extraction is running. Wait for it to finish."
                            }
                            else -> {
                                val s = viewModel.settings
                                BatchExtractor.start(archives, s.keepArchiveByDefault, s.keepFreeBytes, s.verifyFirstByDefault)
                                sel.selecting = false
                                sel.selected.clear()
                                showBatch = true
                            }
                        }
                    },
                    onZip = {
                        if (sel.selected.isEmpty()) {
                            message = "Tap items to select them first."
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
        }
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
            onDismiss = { showOptions = false },
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
            count = sel.selected.size,
            busy = compressing,
            name = zipName,
            onName = { zipName = it },
            onCancel = { showCompress = false },
            onCompress = { store ->
                val inputs = sel.selected.toList()
                val typed = zipName.trim()
                val clean = if (typed.endsWith(".zip", ignoreCase = true)) typed else "$typed.zip"
                if (typed.isBlank() || typed.contains('/') || typed.contains('\\')) {
                    message = "Use a plain name for the zip file."
                } else if (inputs.isEmpty()) {
                    message = "Nothing to zip. Select files first."
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

    conflictNames?.let { names ->
        ConflictDialog(
            names = names,
            onChoose = { policy ->
                val pending = sel.batch
                conflictNames = null
                if (pending != null) runPaste(pending, policy)
            },
            onCancel = { conflictNames = null },
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
                                    onFailure = { error ->
                                        if (error is InterruptedException) "Delete cancelled." else (error.message ?: "Delete failed.")
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
/** Folders that match what the user typed after a slash. Only folders inside internal storage are offered. */
private fun pathSuggestionsFor(typed: String): List<String> {
    val asFile = File(typed)
    val parent: File
    val prefix: String
    if (typed.endsWith("/")) {
        parent = asFile
        prefix = ""
    } else {
        parent = asFile.parentFile ?: return emptyList()
        prefix = asFile.name
    }
    if (!parent.isDirectory || !insideRoot(parent.absolutePath)) return emptyList()
    return parent.listFiles()
        .orEmpty()
        .filter { it.isDirectory && !it.name.startsWith(".") && it.name.startsWith(prefix, ignoreCase = true) }
        .filter { insideRoot(it.absolutePath) }
        .sortedBy { it.name.lowercase() }
        .take(8)
        .map { it.absolutePath }
}

/** Every visible item in the active folder, used by Select all. */
private fun listAllVisible(path: String, filter: ListFilter, opts: ListOptions): List<String> {
    if (!insideRoot(path)) return emptyList()
    return FileListing.listLocal(path, opts).orEmpty()
        .filter { passesFilter(it, filter) }
        .map { it.path }
}

/** What tapping a file does: archives go to the extraction screen, everything else opens in another app. */
private fun openEntry(
    context: Context,
    item: FileItem,
    onOpenArchive: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (!item.directory && isArchive(item.name)) {
        onOpenArchive(item.path)
        return
    }
    FileOpener.open(context, item.path)
        .onFailure { onMessage(it.message ?: "This file could not be opened.") }
}

/** The search panel. Shows folder jumps when the text starts with a slash, otherwise live file matches. */
@Composable
private fun SearchPanel(
    query: String,
    results: List<FileItem>,
    searching: Boolean,
    suggestions: List<String>,
    onPickPath: (String) -> Unit,
    onPickItem: (FileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val typed = query.trim()
    Column(
        modifier = modifier
            .glassSurface(20.dp)
            .padding(12.dp),
    ) {
        if (typed.startsWith("/")) {
            Text(
                text = "Jump to folder",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            val exact = File(typed).takeIf { it.isDirectory && insideRoot(it.absolutePath) }
            if (exact != null) {
                SearchRow(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Go to ${exact.name}",
                    subtitle = exact.absolutePath,
                    onClick = { onPickPath(exact.absolutePath) },
                )
            }
            suggestions.forEach { path ->
                SearchRow(
                    icon = Icons.Rounded.Folder,
                    title = File(path).name,
                    subtitle = path,
                    onClick = { onPickPath(path) },
                )
            }
            if (exact == null && suggestions.isEmpty()) {
                Text(
                    text = "No folder matches this path yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        } else {
            Text(
                text = when {
                    searching -> "Searching…"
                    results.isEmpty() -> "No matches for \"$typed\""
                    else -> "${results.size} match(es) in internal storage"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results, key = { it.path }) { item ->
                    SearchRow(
                        icon = if (item.directory) Icons.Rounded.Folder else Icons.Rounded.Description,
                        title = item.name,
                        subtitle = File(item.path).parent.orEmpty(),
                        onClick = { onPickItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
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
    }
}
/** One browsing pane: the folder list, the new folder and new file buttons, and the item menus. */
@Composable
private fun FolderView(
    path: String,
    filter: ListFilter,
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
    val shown = entries.filter { passesFilter(it, filter) }

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
                    itemsIndexed(shown, key = { _, item -> item.path }) { index, item ->
                        EntryRow(
                            item = item,
                            selecting = sel.selecting,
                            checked = item.path in sel.selected,
                            compact = compact,
                            divider = index < shown.lastIndex,
                            onTap = {
                                when {
                                    sel.selecting -> toggleSelect(sel, item.path)
                                    item.directory -> onPath(item.path)
                                    else -> openEntry(context, item, onOpenArchive) { status = it }
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
                        status = "Copied ${item.name}. Tap Paste here in the folder you want."
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sel.batch = BatchClip(listOf(item.path), cut = true)
                        status = "Cut ${item.name}. Tap Paste here in the folder you want."
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
            onResult = {
                status = it
                onChanged()
            },
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
                                    FileOperations.createFolder(File(here), name).map { it.name }
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
                                    FileOperations.rename(File(target.path), name).map { it.name }
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
                                sel.progress = TransferProgress("Deleting", target.name, 0L, 0L, 0, 1)
                                val result = withContext(Dispatchers.IO) {
                                    FileTransfer.run(TransferKind.Delete, listOf(target.path), null) { p ->
                                        scope.launch { sel.progress = p }
                                    }
                                }
                                sel.progress = null
                                result
                                    .onSuccess { status = "Deleted ${target.name}" }
                                    .onFailure { error ->
                                        status = if (error is InterruptedException) "Delete cancelled." else error.message
                                    }
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
private fun TopBar(title: String, onMore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun QuickJumpRow(
    activePath: String,
    activeFilter: ListFilter,
    onJump: (QuickJump) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(quickJumps()) { jump ->
            GlassChip(
                text = jump.label,
                selected = activePath == jump.path && activeFilter == jump.filter,
                onClick = { onJump(jump) },
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClear: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search files, or type / to jump to a path", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = CronosCyan) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextSecondary)
                }
            }
        },
    )
}

@Composable
private fun PathCard(path: String) {
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
                text = path,
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

@Composable
private fun SelectionDock(
    count: Int,
    archiveCount: Int,
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
            item { GlassChip(text = "Zip", selected = count > 0, onClick = onZip) }
            item { GlassChip(text = "Extract", selected = archiveCount > 0, onClick = onExtract) }
            item { GlassChip(text = "Delete", onClick = onDelete) }
        }
    }
}

/** The bar that appears when items are waiting to be copied or moved. Paste and Cancel are always here. */
@Composable
private fun PasteBar(
    count: Int,
    cut: Boolean,
    destination: String,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(20.dp)
            .padding(14.dp),
    ) {
        Text(
            text = "${if (cut) "Move" else "Copy"} $count item(s) here?",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = destination,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            PrimaryButton(
                text = if (cut) "Move here" else "Paste here",
                modifier = Modifier.weight(1f),
                onClick = onPaste,
            )
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
private fun InfoDialog(item: FileItem, onDismiss: () -> Unit) {
    val details by produceState<FileDetails?>(initialValue = null, item.path) {
        value = withContext(Dispatchers.IO) { FileTools.details(item.path) }
    }
    val d = details
    val sizeText = when {
        d != null -> formatBytes(d.size)
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
                                FileTools.createFile(File(dir), typed).fold(
                                    onSuccess = { "Created ${it.name}." },
                                    onFailure = { it.message ?: "The file could not be created." },
                                )
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
