package com.cronoszip.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
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
import com.cronoszip.app.data.FileScan
import com.cronoszip.app.data.FileSort
import com.cronoszip.app.data.FileTools
import com.cronoszip.app.data.FileTransfer
import com.cronoszip.app.data.ListOptions
import com.cronoszip.app.data.SortMode
import com.cronoszip.app.data.TransferKind
import com.cronoszip.app.data.TransferProgress
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

/** Remembers which finished extraction the Files screen already jumped to. */
private object ExtractionHint {
    var applied: String? = null
}

/** The file kinds shown by the quick jump row. Each one is a flat list of files, with no folders. */
private enum class Category(val label: String, val extensions: Set<String>, val emptyText: String) {
    Images("Images", setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"), "No image files found in internal storage."),
    Movies("Movies", setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v"), "No video files found in internal storage."),
    Music("Music", setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus"), "No music files found in internal storage."),
    Apk("APP", setOf("apk"), "No APK files found in internal storage."),
    Compress("Compress", setOf("zip", "7z", "tar", "gz", "tgz", "rar", "xz"), "No compressed files found in internal storage.")
}

/** What one pane shows: a folder, or a category when [category] is set. */
private class PaneState(initialPath: String) {
    var path by mutableStateOf(initialPath)
    var category by mutableStateOf<Category?>(null)
}

/** The rows and status of one pane, loaded from storage. */
private class PaneData {
    var entries by mutableStateOf<List<FileItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/** Items picked in select mode, the copy or move waiting for Paste, and the running transfer. */
private class SelectionState {
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<String>()
    var clipboard by mutableStateOf<Clip?>(null)
    var progress by mutableStateOf<TransferProgress?>(null)
}

/** Items copied (cut = false) or cut (cut = true), waiting for Paste. */
private data class Clip(val paths: List<String>, val cut: Boolean)

/** A copy or move that is waiting on the conflict dialog. */
private data class PendingTransfer(val paths: List<String>, val cut: Boolean, val dest: String, val names: List<String>)

/** Internal storage root. Navigation never goes above this folder. */
private fun internalRoot(): String = Environment.getExternalStorageDirectory().absolutePath

/** True when [path] is the internal root or inside it. */
private fun insideRoot(path: String): Boolean {
    val root = internalRoot()
    return path == root || path.startsWith("$root/")
}

/** The folder above [path], or null at the internal root. */
private fun upPathOf(path: String): String? {
    if (path == internalRoot() || !insideRoot(path)) return null
    return File(path).parent?.takeIf { insideRoot(it) }
}

private fun isLocalArchive(path: String): Boolean = isArchive(File(path).name)

private fun batchDone(b: BatchState): Int =
    b.items.count { it.status != BatchStatus.Waiting && it.status != BatchStatus.Working }

private fun toggleSelect(sel: SelectionState, path: String) {
    if (path in sel.selected) sel.selected.remove(path) else sel.selected.add(path)
}

/** The message shown when a folder can't be listed. */
private fun unreadableMessage(path: String): String =
    if (path.contains("/Android/data") || path.contains("/Android/obb")) {
        "Android does not let Chronos Manager read other apps' private folders " +
            "(Android/data and Android/obb) on this phone."
    } else {
        "This folder can't be read."
    }

/** Sorts a flat list the same way the folder lists are sorted. */
private fun sortItems(items: List<FileItem>, opts: ListOptions): List<FileItem> {
    val visible = items.filter { opts.showHidden || !it.name.startsWith(".") }
    val byName: Comparator<FileItem> = compareBy { it.name.lowercase() }
    val comparator: Comparator<FileItem> = when (opts.sort) {
        SortMode.Name -> byName
        SortMode.Size -> compareBy<FileItem> { it.size }.thenBy { it.name.lowercase() }
        SortMode.Date -> compareBy<FileItem> { it.modified }.thenBy { it.name.lowercase() }
        SortMode.Type -> compareBy<FileItem> { it.name.substringAfterLast('.', "").lowercase() }
            .thenBy { it.name.lowercase() }
    }
    val ordered = if (opts.descending) visible.sortedWith(comparator.reversed()) else visible.sortedWith(comparator)
    return ordered.sortedBy { !it.directory }
}

/** Loads the rows of one pane. A folder lists its contents; a category scans all of internal storage. */
private suspend fun loadPane(pane: PaneState, data: PaneData, opts: ListOptions) {
    data.loading = true
    data.error = null
    val category = pane.category
    if (category == null) {
        val items = withContext(Dispatchers.IO) { FileListing.listLocal(pane.path, opts) }
        if (items == null) {
            data.error = unreadableMessage(pane.path)
            data.entries = emptyList()
        } else {
            data.entries = items
        }
        data.loading = false
    } else {
        data.entries = emptyList()
        FileScan.find(File(internalRoot())) { f -> f.isFile && f.extension.lowercase() in category.extensions }
            .collect { found -> data.entries = FileSort.sort(found, opts.sort, opts.descending) }
        data.loading = false
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

/** Opens an archive in the extraction screen. Anything else opens in the matching app. */
private fun openFile(context: Context, item: FileItem, onArchive: (String) -> Unit, onMessage: (String) -> Unit) {
    if (!item.directory && isArchive(item.name)) {
        onArchive(item.path)
        return
    }
    FileOpener.open(context, item.path).onFailure { onMessage(it.message ?: "This file could not be opened.") }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
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
    val root = internalRoot()
    remember(context) { AppPrefs.load(context) }

    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    var split by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableStateOf(0) }
    val paneA = remember { PaneState(root) }
    val paneB = remember { PaneState(root) }
    val dataA = remember { PaneData() }
    val dataB = remember { PaneData() }
    val sel = remember { SelectionState() }
    val batch by BatchExtractor.state.collectAsState()
    val extraction by viewModel.extraction.collectAsState()
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingTransfer?>(null) }
    var exitArmed by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var zipInputs by remember { mutableStateOf<List<String>>(emptyList()) }
    var zipName by remember { mutableStateOf("Archive.zip") }
    var compressing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.Name) }
    var descending by remember { mutableStateOf(false) }
    val opts = ListOptions(showHidden = showHidden, sort = sortMode, descending = descending)

    val activePane = if (split && activeIndex == 1) paneB else paneA
    val activeData = if (split && activeIndex == 1) dataB else dataA
    val destPane = if (activeIndex == 0) paneB else paneA
    val upActive = if (activePane.category == null) upPathOf(activePane.path) else null

    fun goFolder(pane: PaneState, path: String) {
        pane.category = null
        pane.path = path
        sel.selected.clear()
    }

    fun jump(category: Category?) {
        activePane.category = category
        activePane.path = root
        query = ""
        sel.selected.clear()
    }

    fun setClipboard(paths: List<String>, cut: Boolean) {
        sel.clipboard = Clip(paths, cut)
        sel.selecting = false
        sel.selected.clear()
        message = "${if (cut) "Cut" else "Copied"} ${paths.size} item(s). Open a folder and tap Paste here."
    }

    fun takeSelection(cut: Boolean) {
        val paths = sel.selected.toList()
        if (paths.isEmpty()) {
            message = "Tap items to select them first."
            return
        }
        setClipboard(paths, cut)
    }

    fun runTransfer(paths: List<String>, cut: Boolean, dest: String, policy: ConflictPolicy) {
        scope.launch {
            sel.progress = TransferProgress(if (cut) "Moving" else "Copying", "", 0L, 0L, 0, paths.size)
            val kind = if (cut) TransferKind.Move else TransferKind.Copy
            val result = withContext(Dispatchers.IO) {
                FileTransfer.run(kind, paths, File(dest), policy) { p -> scope.launch { sel.progress = p } }
            }
            sel.progress = null
            result
                .onSuccess { count ->
                    message = "${if (cut) "Moved" else "Copied"} $count item(s)."
                    if (cut && sel.clipboard?.paths == paths) sel.clipboard = null
                }
                .onFailure { error ->
                    message = if (error is InterruptedException) "Cancelled." else (error.message ?: "Transfer failed.")
                }
            refresh++
        }
    }

    fun startTransfer(paths: List<String>, cut: Boolean, dest: String) {
        if (paths.isEmpty()) return
        if (!insideRoot(dest)) {
            message = "Choose a folder inside internal storage."
            return
        }
        if (paths.all { File(it).parent == dest }) {
            message = "Those items are already in this folder."
            return
        }
        val names = FileTransfer.conflicts(paths, File(dest))
        if (names.isNotEmpty()) {
            pending = PendingTransfer(paths, cut, dest, names)
        } else {
            runTransfer(paths, cut, dest, ConflictPolicy.KeepBoth)
        }
    }

    fun pasteClipboard() {
        val clip = sel.clipboard ?: return
        if (activePane.category != null) {
            message = "Open a folder to paste into."
            return
        }
        startTransfer(clip.paths, clip.cut, activePane.path)
    }

    fun transferSelected(cut: Boolean, dest: PaneState) {
        val paths = sel.selected.toList()
        if (paths.isEmpty()) {
            message = "Tap items to select them first."
            return
        }
        if (dest.category != null) {
            message = "Open a folder in the other pane first."
            return
        }
        sel.selecting = false
        sel.selected.clear()
        startTransfer(paths, cut, dest.path)
    }

    fun deletePaths(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            sel.progress = TransferProgress("Deleting", "", 0L, 0L, 0, paths.size)
            val result = withContext(Dispatchers.IO) {
                FileTransfer.run(TransferKind.Delete, paths, null) { p -> scope.launch { sel.progress = p } }
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
    }

    fun openSearchPath(path: String) {
        goFolder(activePane, path)
        query = ""
    }

    fun openSearchItem(item: FileItem) {
        if (item.directory) {
            goFolder(activePane, item.path)
        } else {
            goFolder(activePane, File(item.path).parent.orEmpty())
            message = "Found ${item.name}."
        }
        query = ""
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

    LaunchedEffect(paneA.path, paneA.category, refresh, opts) { loadPane(paneA, dataA, opts) }
    LaunchedEffect(paneB.path, paneB.category, refresh, opts) { loadPane(paneB, dataB, opts) }
    LaunchedEffect(paneA.path, paneA.category, paneB.path, paneB.category) { sel.selected.clear() }
    LaunchedEffect(batch.finished) {
        if (batch.finished) refresh++
    }

    LaunchedEffect(extraction.success, extraction.output) {
        val out = extraction.output
        if (extraction.success && out.isNotBlank() && out != ExtractionHint.applied) {
            ExtractionHint.applied = out
            goFolder(paneA, out)
            activeIndex = 0
            message = "Showing the extracted files."
        }
    }

    LaunchedEffect(query) {
        val typed = query.trim()
        searchResults = emptyList()
        suggestions = emptyList()
        if (typed.isBlank()) {
            searching = false
            return@LaunchedEffect
        }
        delay(200)
        if (typed.startsWith("/")) {
            suggestions = withContext(Dispatchers.IO) { pathSuggestionsFor(typed) }
        } else {
            searching = true
            FileScan.find(File(root)) { f -> f.name.contains(typed, ignoreCase = true) }
                .collect { searchResults = it }
            searching = false
        }
    }

    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(2000)
            exitArmed = false
            message = null
        }
    }

    BackHandler(enabled = true) {
        when {
            sel.selecting -> {
                sel.selecting = false
                sel.selected.clear()
            }
            sel.clipboard != null -> {
                sel.clipboard = null
                message = "Cancelled."
            }
            query.isNotBlank() -> {
                query = ""
            }
            activePane.category != null -> {
                activePane.category = null
            }
            upActive != null -> {
                goFolder(activePane, upActive)
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
        Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        TopBar(title = "Files", onMore = { showOptions = true })
        Spacer(Modifier.height(12.dp))

        ModeSwitch(split = split, onChange = {
            split = it
            activeIndex = 0
        })
        Spacer(Modifier.height(12.dp))

        QuickJumpRow(selected = activePane.category, onJump = { jump(it) })
        Spacer(Modifier.height(10.dp))

        SearchBar(query = query, onQuery = { query = it }, onClear = { query = "" })
        Spacer(Modifier.height(10.dp))

        if (granted) {
            PathCard(
                title = activePane.category?.let { "All ${it.label.lowercase()} · ${activeData.entries.size} file(s)" }
                    ?: activePane.path,
                storagePath = root,
            )
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
                query.isNotBlank() -> SearchPanel(
                    query = query,
                    results = searchResults,
                    searching = searching,
                    suggestions = suggestions,
                    onPickPath = { openSearchPath(it) },
                    onPickItem = { openSearchItem(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                split -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    PaneView(
                        pane = paneA,
                        data = dataA,
                        sel = sel,
                        compact = true,
                        highlighted = activeIndex == 0,
                        onActivate = { activeIndex = 0 },
                        onNavigate = { goFolder(paneA, it) },
                        onOpenArchive = onOpenArchive,
                        onMessage = { message = it },
                        onChanged = { refresh++ },
                        onClipboard = { paths, cut -> setClipboard(paths, cut) },
                        onDelete = { deletePaths(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PaneView(
                        pane = paneB,
                        data = dataB,
                        sel = sel,
                        compact = true,
                        highlighted = activeIndex == 1,
                        onActivate = { activeIndex = 1 },
                        onNavigate = { goFolder(paneB, it) },
                        onOpenArchive = onOpenArchive,
                        onMessage = { message = it },
                        onChanged = { refresh++ },
                        onClipboard = { paths, cut -> setClipboard(paths, cut) },
                        onDelete = { deletePaths(it) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                else -> PaneView(
                    pane = paneA,
                    data = dataA,
                    sel = sel,
                    compact = false,
                    highlighted = false,
                    onActivate = {},
                    onNavigate = { goFolder(paneA, it) },
                    onOpenArchive = onOpenArchive,
                    onMessage = { message = it },
                    onChanged = { refresh++ },
                    onClipboard = { paths, cut -> setClipboard(paths, cut) },
                    onDelete = { deletePaths(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
                sel.progress?.let { p ->
            Spacer(Modifier.height(10.dp))
            TransferBar(p = p, onCancel = { FileTransfer.cancel() })
        }

        sel.clipboard?.let { clip ->
            Spacer(Modifier.height(10.dp))
            PasteBar(
                count = clip.paths.size,
                cut = clip.cut,
                destination = if (activePane.category == null) activePane.path else "Open a folder to paste into",
                canPaste = activePane.category == null,
                onPaste = { pasteClipboard() },
                onCancel = {
                    sel.clipboard = null
                    message = "Cancelled."
                },
            )
        }

        if (sel.selecting) {
            Spacer(Modifier.height(10.dp))
            SelectionDock(
                count = sel.selected.size,
                archiveCount = sel.selected.count { isLocalArchive(it) },
                split = split,
                sideLabel = if (activeIndex == 0) "right" else "left",
                onSelectAll = {
                    activeData.entries.forEach { item ->
                        if (item.path !in sel.selected) sel.selected.add(item.path)
                    }
                    message = "Selected ${sel.selected.size} item(s)."
                },
                onCopy = {
                    if (split) transferSelected(false, destPane) else takeSelection(false)
                },
                onMove = {
                    if (split) transferSelected(true, destPane) else takeSelection(true)
                },
                onZip = {
                    when {
                        sel.selected.isEmpty() -> {
                            message = "Tap items to select them first."
                        }
                        activePane.category != null -> {
                            message = "Open a folder first to save the zip."
                        }
                        else -> {
                            zipInputs = sel.selected.toList()
                            showCompress = true
                        }
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
                onDelete = {
                    if (sel.selected.isEmpty()) {
                        message = "Tap items to select them first."
                    } else {
                        confirmDelete = true
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
            count = zipInputs.size,
            busy = compressing,
            name = zipName,
            onName = { zipName = it },
            onCancel = { showCompress = false },
            onCompress = { store ->
                val typed = zipName.trim()
                val clean = if (typed.endsWith(".zip", ignoreCase = true)) typed else "$typed.zip"
                if (typed.isBlank() || typed.contains('/') || typed.contains('\\')) {
                    message = "Use a plain name for the zip file."
                } else if (zipInputs.isEmpty()) {
                    message = "Nothing to zip. Select files first."
                } else {
                    val output = File(activePane.path, clean).absolutePath
                    val inputs = zipInputs
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

    pending?.let { p ->
        ConflictDialog(
            names = p.names,
            onChoose = { policy ->
                pending = null
                runTransfer(p.paths, p.cut, p.dest, policy)
            },
            onCancel = { pending = null },
        )
    }

    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
            SolidDialogCard(glow = Warning) {
                Text(
                    text = "Delete ${sel.selected.size} item(s)?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
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
                        onClick = { confirmDelete = false },
                    )
                    PrimaryButton(
                        text = "Delete",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val paths = sel.selected.toList()
                            confirmDelete = false
                            sel.selecting = false
                            sel.selected.clear()
                            deletePaths(paths)
                        },
                    )
                }
            }
        }
    }
}
/** One browsing pane: its list of rows, the item menus, and the folder, file, rename and delete dialogs. */
@Composable
private fun PaneView(
    pane: PaneState,
    data: PaneData,
    sel: SelectionState,
    compact: Boolean,
    highlighted: Boolean,
    onActivate: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onMessage: (String) -> Unit,
    onChanged: () -> Unit,
    onClipboard: (List<String>, Boolean) -> Unit,
    onDelete: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val category = pane.category
    val root = internalRoot()
    var actionItem by remember { mutableStateOf<FileItem?>(null) }
    var infoItem by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    val upPath = if (category == null) upPathOf(pane.path) else null

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
        if (category == null) {
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
        } else {
            Text(
                text = if (data.loading) "Scanning internal storage…" else "${category.label}: ${data.entries.size} file(s)",
                style = MaterialTheme.typography.labelMedium,
                color = CronosCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val error = data.error
            when {
                error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                data.loading && data.entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp)
                }
                category != null && data.entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = category.emptyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (upPath != null) {
                        item {
                            UpRow(compact = compact, onClick = { onNavigate(upPath) })
                        }
                    }
                    itemsIndexed(data.entries, key = { _, item -> item.path }) { index, item ->
                        EntryRow(
                            item = item,
                            selecting = sel.selecting,
                            checked = item.path in sel.selected,
                            compact = compact,
                            subtitle = when {
                                category != null -> File(item.path).parent.orEmpty()
                                item.directory -> "Folder"
                                compact -> formatBytes(item.size)
                                else -> "${formatBytes(item.size)} · ${formatTime(item.modified)}"
                            },
                            divider = index < data.entries.lastIndex,
                            onTap = {
                                when {
                                    sel.selecting -> toggleSelect(sel, item.path)
                                    item.directory -> onNavigate(item.path)
                                    else -> openFile(context, item, onOpenArchive, onMessage)
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
                        onClipboard(listOf(item.path), false)
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onClipboard(listOf(item.path), true)
                        actionItem = null
                    },
                )
                if (category == null) {
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
                }
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
            dir = pane.path,
            onResult = {
                onMessage(it)
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
                            val here = pane.path
                            showNewFolder = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    FileOperations.createFolder(File(here), name).map { it.name }
                                }
                                result
                                    .onSuccess { onMessage("Created $it") }
                                    .onFailure { onMessage(it.message ?: "Could not create the folder.") }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }

    renameItem?.let { target ->
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
                            renameItem = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    FileOperations.rename(File(target.path), name).map { it.name }
                                }
                                result
                                    .onSuccess { onMessage("Renamed to $it") }
                                    .onFailure { onMessage(it.message ?: "Could not rename.") }
                                onChanged()
                            }
                        },
                    )
                }
            }
        }
    }

    deleteItem?.let { target ->
        Dialog(onDismissRequest = { deleteItem = null }) {
            SolidDialogCard(glow = Warning) {
                Text("Delete?", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (target.directory) {
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
                            val path = target.path
                            deleteItem = null
                            onDelete(listOf(path))
                        },
                    )
                }
            }
        }
    }
}
/** The search panel: path jumps when the text starts with a slash, otherwise live file matches. */
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
    Column(modifier = modifier.glassSurface(20.dp).padding(12.dp)) {
        if (typed.startsWith("/")) {
            Text(
                text = "Jump to a folder",
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
                    searching && results.isEmpty() -> "Searching internal storage…"
                    searching -> "${results.size} match(es) so far…"
                    results.isEmpty() -> "No matches for \"$typed\""
                    else -> "${results.size} match(es)"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(results, key = { it.path }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPickItem(item) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileThumb(item = item, size = 34.dp)
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

@Composable
private fun QuickJumpRow(selected: Category?, onJump: (Category?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            GlassChip(text = "Internal", selected = selected == null, onClick = { onJump(null) })
        }
        items(Category.entries) { category ->
            GlassChip(
                text = category.label,
                selected = selected == category,
                onClick = { onJump(category) },
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
        placeholder = { Text("Search files, or type / to jump to a folder", color = TextSecondary) },
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
private fun PathCard(title: String, storagePath: String) {
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
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        StorageSpaceBar(path = storagePath)
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

/** The bar that appears when items are waiting for a Paste. Paste and Cancel are always visible. */
@Composable
private fun PasteBar(
    count: Int,
    cut: Boolean,
    destination: String,
    canPaste: Boolean,
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
                enabled = canPaste,
                onClick = onPaste,
            )
        }
    }
}

/** The bar shown in select mode. In split view, Copy and Move send the picks to the other pane. */
@Composable
private fun SelectionDock(
    count: Int,
    archiveCount: Int,
    split: Boolean,
    sideLabel: String,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onZip: () -> Unit,
    onExtract: () -> Unit,
    onDelete: () -> Unit,
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
            if (split) {
                item { GlassChip(text = "Copy to $sideLabel", selected = count > 0, onClick = onCopy) }
                item { GlassChip(text = "Move to $sideLabel", onClick = onMove) }
            } else {
                item { GlassChip(text = "Copy", selected = count > 0, onClick = onCopy) }
                item { GlassChip(text = "Move", onClick = onMove) }
            }
            item { GlassChip(text = "Zip", selected = count > 0, onClick = onZip) }
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
    subtitle: String,
    divider: Boolean,
    onTap: () -> Unit,
    onMore: () -> Unit,
) {
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
            FileThumb(item = item, size = if (compact) 30.dp else 40.dp)
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
