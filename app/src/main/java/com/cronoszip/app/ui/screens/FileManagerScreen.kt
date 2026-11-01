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

/** Remembers which finished extraction the Files screen already jumped to, so it jumps once per result. */
private object ExtractionHint {
    var applied: String? = null
}

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
