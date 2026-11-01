package com.cronoszip.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.ArchiveConfig
import com.cronoszip.app.data.ArchiveRepository
import com.cronoszip.app.data.EngineException
import com.cronoszip.app.data.ListData
import com.cronoszip.app.ui.components.BackCircle
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.viewmodel.CronosViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Most entries the expanded list draws. Bigger archives show the first ones with a note. */
private const val ARCHIVE_MAX_SHOWN = 300

private fun archiveNameOf(path: String): String = path.substringAfterLast('/').ifEmpty { path }

/**
 * One screen for an archive. It previews the contents and space plan first. Extract starts the
 * extraction on this same screen, the contents show what has been extracted, and each extracted
 * file leaves the list with an animation.
 */
@Composable
fun ArchiveViewerScreen(
    viewModel: CronosViewModel,
    path: String,
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onDone: () -> Unit,
) {
    val repository = remember { ArchiveRepository() }
    val extraction by viewModel.extraction.collectAsState()
    val check = viewModel.currentCheck
    val options = viewModel.options
    var listing by remember(path) { mutableStateOf<ListData?>(null) }
    var loadError by remember(path) { mutableStateOf<String?>(null) }
    var expanded by remember(path) { mutableStateOf(false) }
    var query by remember(path) { mutableStateOf("") }
    var started by remember(path) { mutableStateOf(viewModel.extraction.value.running) }
    val extracted = remember(path) { mutableStateListOf<String>() }
    val entries = listing?.entries.orEmpty()

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.list(ArchiveConfig(archive = path)) }
        }
        result
            .onSuccess { listing = it }
            .onFailure { failure ->
                loadError = if (failure is EngineException && failure.code == "PASSWORD_NEEDED") {
                    "This archive is password protected. Enter the password to continue."
                } else {
                    failure.message ?: "The archive could not be read."
                }
            }
        viewModel.checkArchive(path)
    }

    // Each file the engine reports as extracted leaves the archive list.
    LaunchedEffect(extraction.currentFile) {
        val file = extraction.currentFile
        if (started && file != null && file !in extracted) extracted.add(file)
    }

    // When the whole extraction succeeds, every entry has left the archive.
    LaunchedEffect(extraction.success) {
        if (started && extraction.success) {
            entries.forEach { if (it.path !in extracted) extracted.add(it.path) }
        }
    }

    // Show the contents while extracting so the vanishing rows are visible.
    LaunchedEffect(extraction.running) {
        if (started && extraction.running) expanded = true
    }

    val running = started && extraction.running
    val paused = started && extraction.paused && !extraction.running
    val finished = started && extraction.success && !extraction.running
    val failure = if (started && !extraction.running && !extraction.success) extraction.errorMessage else null
    val canStart = check != null && check.plan.fits && !viewModel.isChecking && !running
    val shown = entries
        .filter { query.isBlank() || it.path.contains(query, ignoreCase = true) }
        .take(ARCHIVE_MAX_SHOWN)

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackCircle(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CHRONOS MANAGER",
                    style = MaterialTheme.typography.labelSmall,
                    color = CronosCyan,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    text = listing?.displayName ?: archiveNameOf(path),
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            ArchiveHeroCard(
                name = listing?.displayName ?: archiveNameOf(path),
                format = listing?.format.orEmpty(),
                entries = listing?.entryCount ?: 0,
                packed = listing?.archiveSize ?: 0L,
                unpacked = listing?.totalSize ?: 0L,
            )
            loadError?.let {
                Spacer(Modifier.height(10.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = Warning)
            }
            if (started) {
                ArchiveSectionLabel("Progress")
                ArchiveProgressCard(
                    status = when {
                        running -> "Extracting…"
                        paused -> "Paused"
                        finished -> "Extraction complete"
                        failure != null -> failure
                        else -> "Ready"
                    },
                    fraction = extraction.fraction,
                    done = extraction.done,
                    total = extraction.total,
                    speed = extraction.speed,
                    latest = extracted.lastOrNull(),
                    count = extracted.size,
                )
            }
            ArchiveSectionLabel("Space plan")
            ArchivePlanCard(plan = check?.plan)
            ArchiveSectionLabel("Destination")
            ArchiveDestinationCard(output = check?.output.orEmpty(), enabled = !running, onChange = onChooseFolder)
            ArchiveSectionLabel("Options")
            ArchiveOptionsCard(
                keepArchive = options.keepArchive,
                keepFreeBytes = options.keepFree,
                enabled = !running,
                onKeepArchive = { viewModel.updateOptions(options.copy(keepArchive = it)) },
                onKeepFree = { viewModel.updateOptions(options.copy(keepFree = it)) },
            )
            ArchiveSectionLabel("Contents")
            Column(modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${entries.size} files and folders",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = CronosCyan,
                    )
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Filter by name", color = TextSecondary) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (entries.size > ARCHIVE_MAX_SHOWN) {
                            Text(
                                text = "Showing the first $ARCHIVE_MAX_SHOWN of ${entries.size} entries.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Warning,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        shown.forEach { entry ->
                            AnimatedVisibility(
                                visible = entry.path !in extracted,
                                exit = fadeOut(tween(450)) + shrinkVertically(tween(450)),
                            ) {
                                Column {
                                    ArchiveEntryLine(entry)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(10.dp))
        when {
            finished -> PrimaryButton(
                text = "Open in Files",
                icon = Icons.Rounded.FolderOpen,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDone,
            )
            running -> SecondaryButton(
                text = "Pause",
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.cancelExtraction() },
            )
            paused -> PrimaryButton(
                text = "Resume",
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    started = true
                    viewModel.resumeExtraction()
                },
            )
            else -> PrimaryButton(
                text = "Extract this archive",
                icon = Icons.Rounded.Upload,
                modifier = Modifier.fillMaxWidth(),
                enabled = canStart,
                onClick = {
                    started = true
                    extracted.clear()
                    viewModel.startExtraction()
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
