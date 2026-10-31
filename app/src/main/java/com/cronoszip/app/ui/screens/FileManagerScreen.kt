package com.cronoszip.app.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
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
import com.cronoszip.app.data.FileClipboard
import com.cronoszip.app.data.FileItem
import com.cronoszip.app.data.FileListing
import com.cronoszip.app.data.FileOperations
import com.cronoszip.app.data.PinnedLocation
import com.cronoszip.app.data.PinnedStorage
import com.cronoszip.app.data.MixedFileOps
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
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

@Composable
fun FileManagerScreen(
    viewModel: CronosViewModel,
    onBack: () -> Unit,
    onOpenArchive: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }
    val pinned = viewModel.pinned
    val roots = remember(context, pinned) { managerRoots(context, pinned) }
    var split by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }

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
            title = "Files",
            subtitle = "Local storage · App storage · One screen",
            onBack = onBack,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassChip(text = "List", selected = !split, onClick = { split = false })
            GlassChip(text = "Split view", selected = split, onClick = { split = true })
            GlassChip(text = "Storage", onClick = { showStorage = true })
        }
        Spacer(Modifier.height(10.dp))

        if (!granted) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                GlassCard(glow = CronosCyan) {
                    Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(CronosCyan.copy(alpha = 0.14f)).border(1.dp, CronosCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Storage access is needed", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text("CronosZip needs access to read files and open archives across storage.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
            SplitPanes(roots = roots, onOpenArchive = onOpenArchive)
        } else {
            SinglePane(roots = roots, onOpenArchive = onOpenArchive)
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
}

@Composable
private fun SinglePane(
    roots: List<Pair<String, String>>,
    onOpenArchive: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(roots.firstOrNull()?.second ?: "") }
    var entries by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var actionItem by remember { mutableStateOf<FileItem?>(null) }
    var renameItem by remember { mutableStateOf<FileItem?>(null) }
    var deleteItem by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }

    fun load(path: String) {
        if (path.isBlank()) return
        current = path
        loading = true
        error = null
        scope.launch {
            val items = FileListing.list(context, path)
            if (current != path) return@launch
            loading = false
            if (items == null) {
                error = "Cannot read this folder."
                entries = emptyList()
            } else {
                entries = items
            }
        }
    }

    fun open(item: FileItem) {
        if (item.directory) {
            if (SafStorage.isSaf(current)) {
                val child = item.documentId?.let { SafStorage.childTreeUri(Uri.parse(current), it) }
                if (child != null) load(child.toString()) else error = "Cannot open this folder."
            } else {
                load(item.path)
            }
        } else if (isArchive(item.name)) {
            if (SafStorage.isSaf(item.path)) {
                val documentUri = Uri.parse(item.path)
                val direct = PinnedStorage.resolveDocumentPath(documentUri)
                if (direct != null) {
                    onOpenArchive(direct)
                } else {
                    val cached = SafStorage.copyToCache(context, documentUri, item.name)
                    if (cached != null) {
                        status = "Copied ${item.name} to app cache for extraction."
                        onOpenArchive(cached.absolutePath)
                    } else {
                        status = "Could not copy the archive out of the document provider."
                    }
                }
            } else {
                onOpenArchive(item.path)
            }
        } else if (SafStorage.isSaf(item.path)) {
            runCatching { context.startActivity(SafStorage.viewIntent(context, Uri.parse(item.path), null)) }
                .onFailure { status = it.message }
        }
    }

    fun pasteHere() {
        val source = FileClipboard.path ?: return
        val cut = FileClipboard.cut
        val here = current
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MixedFileOps.paste(context, here, source, cut)
            }
            result
                .onSuccess { name ->
                    status = "Pasted $name"
                    if (cut) {
                        FileClipboard.path = null
                        FileClipboard.cut = false
                    }
                }
                .onFailure { status = it.message }
            load(here)
        }
    }

    LaunchedEffect(roots) {
        if (current.isBlank() || roots.none { it.second == current }) {
            current = roots.firstOrNull()?.second ?: ""
        }
        if (current.isNotBlank()) load(current)
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
            GlassChip(
                text = if (FileClipboard.cut) "Move here" else "Paste",
                selected = FileClipboard.path != null,
                onClick = if (FileClipboard.path != null) {
                    {
                        pasteHere()
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roots) { root ->
                GlassChip(text = root.first, selected = current == root.second, onClick = { load(root.second) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = SafStorage.locationLabel(context, current), style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        status?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CronosCyan,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CronosCyan) }
                error != null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { GlassCard(glow = Warning) { Text("Cannot read folder", style = MaterialTheme.typography.titleMedium, color = TextPrimary); Spacer(Modifier.height(6.dp)); Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary); Spacer(Modifier.height(12.dp)); SecondaryButton("Try again", modifier = Modifier.fillMaxWidth(), onClick = { load(current) }) } }
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    val safParent = if (SafStorage.isSaf(current)) SafStorage.parentTreeUri(Uri.parse(current)) else null
                    val fileParent = if (!SafStorage.isSaf(current)) File(current).parent else null
                    val parent = safParent?.toString() ?: fileParent
                    if (parent != null && parent != current) {
                        item { Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { load(parent) }.background(GlassWhite.copy(alpha = 0.035f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Upload, contentDescription = null, tint = TextSecondary); Spacer(Modifier.width(10.dp)); Text("Up one folder", color = TextSecondary) } }
                    }
                    items(entries) { item ->
                        GlassCard(modifier = Modifier.fillMaxWidth().clickable { open(item) }, contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    Text(item.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (item.directory) "Folder" else "${formatBytes(item.size)} · ${formatTime(item.modified)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                                if (item.directory || isArchive(item.name)) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = TextSecondary.copy(alpha = 0.55f),
                                    )
                                }
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

    actionItem?.let { item ->
        Dialog(onDismissRequest = { actionItem = null }) {
            GlassCard(glow = CronosCyan) {
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
                    text = "Copy",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        FileClipboard.path = item.path
                        FileClipboard.cut = false
                        status = "Copied ${item.name}. Open another folder and tap Paste."
                        actionItem = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Cut / move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        FileClipboard.path = item.path
                        FileClipboard.cut = true
                        status = "Cut ${item.name}. Open the destination and tap Move here."
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
            GlassCard(glow = CronosCyan) {
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
                            val here = current
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
                                load(here)
                            }
                        },
                    )
                }
            }
        }
    }

    renameItem?.let { item ->
        Dialog(onDismissRequest = { renameItem = null }) {
            GlassCard(glow = CronosCyan) {
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
                            val here = current
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
                                load(here)
                            }
                        },
                    )
                }
            }
        }
    }

    deleteItem?.let { item ->
        Dialog(onDismissRequest = { deleteItem = null }) {
            GlassCard(glow = Warning) {
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
                            val here = current
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
                                load(here)
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
    roots: List<Pair<String, String>>,
    onOpenArchive: (String) -> Unit,
) {
    var refreshToken by remember { mutableStateOf(0) }
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Pane(
            modifier = Modifier.weight(1f),
            roots = roots,
            start = roots.firstOrNull()?.second.orEmpty(),
            refreshToken = refreshToken,
            onChanged = { refreshToken++ },
            onOpenArchive = onOpenArchive,
        )
        Pane(
            modifier = Modifier.weight(1f),
            roots = roots,
            start = roots.getOrNull(1)?.second ?: roots.firstOrNull()?.second.orEmpty(),
            refreshToken = refreshToken,
            onChanged = { refreshToken++ },
            onOpenArchive = onOpenArchive,
        )
    }
}

@Composable
private fun Pane(
    modifier: Modifier = Modifier,
    roots: List<Pair<String, String>>,
    start: String,
    refreshToken: Int,
    onChanged: () -> Unit,
    onOpenArchive: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(start) { mutableStateOf(start) }
    var entries by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<FileItem?>(null) }

    fun load(path: String) {
        if (path.isBlank()) return
        current = path
        loading = true
        error = null
        scope.launch {
            val items = FileListing.list(context, path)
            if (current != path) return@launch
            loading = false
            if (items == null) {
                error = "Cannot read"
                entries = emptyList()
            } else {
                entries = items
            }
        }
    }

    fun open(item: FileItem) {
        if (item.directory) {
            if (SafStorage.isSaf(current)) {
                val child = item.documentId?.let { SafStorage.childTreeUri(Uri.parse(current), it) }
                if (child != null) load(child.toString()) else error = "Cannot open"
            } else {
                load(item.path)
            }
        } else if (isArchive(item.name)) {
            if (SafStorage.isSaf(item.path)) {
                val documentUri = Uri.parse(item.path)
                val direct = PinnedStorage.resolveDocumentPath(documentUri)
                if (direct != null) {
                    onOpenArchive(direct)
                } else {
                    val cached = SafStorage.copyToCache(context, documentUri, item.name)
                    if (cached != null) onOpenArchive(cached.absolutePath) else error = "Cannot copy archive"
                }
            } else {
                onOpenArchive(item.path)
            }
        } else if (SafStorage.isSaf(item.path)) {
            runCatching { context.startActivity(SafStorage.viewIntent(context, Uri.parse(item.path), null)) }
                .onFailure { error = it.message }
        }
    }

    fun pasteHere() {
        val source = FileClipboard.path ?: return
        val cut = FileClipboard.cut
        val here = current
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MixedFileOps.paste(context, here, source, cut)
            }
            result.onSuccess {
                if (cut) {
                    FileClipboard.path = null
                    FileClipboard.cut = false
                }
                onChanged()
            }
            load(here)
            result.onFailure { error = it.message }
        }
    }

    LaunchedEffect(current, refreshToken) { if (current.isNotBlank()) load(current) }

    GlassCard(modifier = modifier.fillMaxHeight(), contentPadding = PaddingValues(10.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                items(roots) { root -> GlassChip(text = root.first.take(9), selected = current == root.second, onClick = { load(root.second) }) }
                item {
                    GlassChip(
                        text = if (FileClipboard.cut) "Move here" else "Paste",
                        selected = FileClipboard.path != null,
                        onClick = if (FileClipboard.path != null) {
                            {
                                pasteHere()
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = SafStorage.locationLabel(context, current), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.weight(1f)) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CronosCyan, strokeWidth = 2.dp) }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = Warning) }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val safParent = if (SafStorage.isSaf(current)) SafStorage.parentTreeUri(Uri.parse(current)) else null
                        val fileParent = if (!SafStorage.isSaf(current)) File(current).parent else null
                        val parent = safParent?.toString() ?: fileParent
                        if (parent != null && parent != current) {
                            item { Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { load(parent) }.background(GlassWhite.copy(alpha = 0.03f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Upload, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Up", color = TextSecondary, style = MaterialTheme.typography.bodySmall) } }
                        }
                        items(entries) { item ->
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open(item) }.background(GlassWhite.copy(alpha = 0.035f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (item.directory) Brush.linearGradient(listOf(CronosCyan.copy(alpha = 0.22f), CronosBlue.copy(alpha = 0.08f))) else Brush.linearGradient(listOf(CronosBlue.copy(alpha = 0.26f), CronosCyan.copy(alpha = 0.1f)))), contentAlignment = Alignment.Center) {
                                    Icon(if (item.directory) Icons.Rounded.Folder else if (isArchive(item.name)) Icons.Rounded.Archive else Icons.Rounded.Description, contentDescription = null, tint = if (item.directory) CronosCyan else Color(0xFF9CC7FF), modifier = Modifier.size(17.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (item.directory) "Folder" else formatBytes(item.size), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                if (item.directory || isArchive(item.name)) Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                                IconButton(onClick = { action = item }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "File actions", tint = TextSecondary, modifier = Modifier.size(16.dp))
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
            GlassCard(glow = CronosCyan) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Copy",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        FileClipboard.path = item.path
                        FileClipboard.cut = false
                        action = null
                    },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = "Cut / move",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        FileClipboard.path = item.path
                        FileClipboard.cut = true
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
        GlassCard(glow = CronosCyan) {
            Text("Add app storage", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Pick an app that provides storage, or choose any folder. Android asks you to confirm " +
                    "the folder once and CronosZip keeps that access.",
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
                Text("Add CronosZip app storage", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
