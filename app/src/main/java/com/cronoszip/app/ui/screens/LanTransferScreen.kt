package com.cronoszip.app.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cronoszip.app.data.FileClipboard
import com.cronoszip.app.lan.FtpRemote
import com.cronoszip.app.lan.LanServerService
import com.cronoszip.app.lan.LanShare
import com.cronoszip.app.lan.LanTransfer
import com.cronoszip.app.lan.RemoteEntry
import com.cronoszip.app.lan.remoteParent
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.util.formatBytes
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LanTab { Share, Connect }

@Composable
fun LanTransferScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by LanShare.status.collectAsState()

    var tab by remember { mutableStateOf(LanTab.Share) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf(LanShare.DEFAULT_PORT.toString()) }
    var user by remember { mutableStateOf("cronos") }
    var pass by remember { mutableStateOf("") }

    var remote by remember { mutableStateOf<FtpRemote?>(null) }
    var remotePath by remember { mutableStateOf("/") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Closing the screen disconnects, but sharing keeps running in the service.
    DisposableEffect(Unit) {
        onDispose {
            remote?.let { open -> Thread { open.close() }.start() }
        }
    }

    // Progress comes from background threads, so it is posted back to the main thread.
    fun post(message: String) {
        Handler(Looper.getMainLooper()).post { note = message }
    }

    fun runTask(work: suspend () -> Unit) {
        busy = true
        note = null
        scope.launch {
            try {
                work()
            } catch (e: Exception) {
                note = e.message ?: "Something went wrong."
            } finally {
                busy = false
            }
        }
    }

    suspend fun loadFolder(path: String) {
        val open = remote ?: return
        val list = withContext(Dispatchers.IO) { open.list(path) }
        remotePath = path
        entries = list
        selected = emptySet()
    }

    fun connectNow() = runTask {
        val port = portText.trim().toIntOrNull() ?: throw IOException("The port must be a number.")
        val connected = withContext(Dispatchers.IO) {
            FtpRemote(host.trim(), port, user.trim(), pass).also { it.connect() }
        }
        remote?.let { old -> Thread { old.close() }.start() }
        remote = connected
        val start = withContext(Dispatchers.IO) { connected.currentDirectory() }
        loadFolder(start)
    }

    fun disconnect() {
        remote?.let { old -> Thread { old.close() }.start() }
        remote = null
        entries = emptyList()
        selected = emptySet()
        note = null
    }

    fun toggle(entry: RemoteEntry) {
        selected = if (entry.path in selected) selected - entry.path else selected + entry.path
    }

    fun openEntry(entry: RemoteEntry) {
        if (entry.directory) runTask { loadFolder(entry.path) } else toggle(entry)
    }

    fun copyToPhone(move: Boolean) = runTask {
        val open = remote ?: return@runTask
        val chosen = entries.filter { it.path in selected }
        if (chosen.isEmpty()) {
            note = "Select one or more items first."
            return@runTask
        }
        val count = withContext(Dispatchers.IO) {
            LanTransfer.receive(open, chosen, move) { message -> post(message) }
        }
        post("${if (move) "Moved" else "Copied"} $count item(s) to ${LanTransfer.inbox().path}")
        loadFolder(remotePath)
    }

    fun pasteHere() = runTask {
        val open = remote ?: return@runTask
        val source = FileClipboard.path
        if (source == null) {
            note = "Nothing is copied on this phone yet."
            return@runTask
        }
        val cut = FileClipboard.cut
        val localFile = File(source)
        val folder = remotePath
        withContext(Dispatchers.IO) {
            LanTransfer.send(open, localFile, folder, cut) { message -> post(message) }
        }
        if (cut) FileClipboard.clear()
        post("Sent ${localFile.name} to $folder on the other phone")
        loadFolder(folder)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(
            title = "LAN transfer",
            subtitle = "Copy or move files between two phones on the same Wi-Fi or hotspot",
            onBack = onBack,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (tab == LanTab.Share) {
                PrimaryButton(text = "Share this phone", modifier = Modifier.weight(1f), onClick = { tab = LanTab.Share })
                SecondaryButton(text = "Connect to phone", modifier = Modifier.weight(1f), onClick = { tab = LanTab.Connect })
            } else {
                SecondaryButton(text = "Share this phone", modifier = Modifier.weight(1f), onClick = { tab = LanTab.Share })
                PrimaryButton(text = "Connect to phone", modifier = Modifier.weight(1f), onClick = { tab = LanTab.Connect })
            }
        }

        if (tab == LanTab.Share) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (status.running) "Sharing is on" else "Sharing is off",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                if (status.running) {
                    Text(
                        text = "On the other phone, use one of these addresses:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    status.addresses.forEach { address ->
                        Text(
                            text = "$address  port ${status.port}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                    }
                    if (status.addresses.isEmpty()) {
                        Text(
                            text = "No network address found. Turn on Wi-Fi or your hotspot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Danger,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Username: ${status.username}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text("Password: ${status.password}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton(
                        text = "Stop sharing",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { LanServerService.stop(context) },
                    )
                } else {
                    Text(
                        text = "On Phone A, turn on the hotspot (or join the same Wi-Fi) and tap Start sharing. " +
                            "On Phone B, open Connect to phone and enter the address and login shown here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Start sharing",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { LanServerService.start(context) },
                    )
                }
                status.error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(text = message, style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
            Text(
                text = "The connection is not encrypted. Only share on networks you trust.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        if (tab == LanTab.Connect) {
            val open = remote
            if (open == null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Connect to another phone", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Address") },
                        placeholder = { Text("192.168.43.1") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = if (busy) "Connecting…" else "Connect",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && host.isNotBlank() && pass.isNotEmpty(),
                        onClick = { connectNow() },
                    )
                }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Connected", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(remotePath, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        SecondaryButton(text = "Disconnect", onClick = { disconnect() })
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = "Up",
                            modifier = Modifier.weight(1f),
                            enabled = !busy && remotePath != "/",
                            onClick = { runTask { loadFolder(remoteParent(remotePath)) } },
                        )
                        SecondaryButton(
                            text = "Refresh",
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            onClick = { runTask { loadFolder(remotePath) } },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (entries.isEmpty()) {
                        Text("This folder is empty.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    entries.forEach { entry ->
                        EntryRow(
                            entry = entry,
                            selected = entry.path in selected,
                            onToggle = { toggle(entry) },
                            onOpen = { openEntry(entry) },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            text = "Copy to phone",
                            modifier = Modifier.weight(1f),
                            enabled = !busy && selected.isNotEmpty(),
                            onClick = { copyToPhone(move = false) },
                        )
                        SecondaryButton(
                            text = "Move to phone",
                            modifier = Modifier.weight(1f),
                            enabled = !busy && selected.isNotEmpty(),
                            onClick = { copyToPhone(move = true) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Paste here",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && FileClipboard.path != null,
                        onClick = { pasteHere() },
                    )
                    Text(
                        text = "Paste here sends what you copied or cut in the file manager to this folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        if (busy) {
            Text("Working…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        note?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun EntryRow(
    entry: RemoteEntry,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selected) "✓" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) CronosBlue else TextSecondary,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(end = 10.dp),
        )
        if (entry.directory) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = CronosBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(
                text = if (entry.directory) "Folder" else formatBytes(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
