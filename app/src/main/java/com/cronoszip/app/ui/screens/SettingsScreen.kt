package com.cronoszip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cronoszip.app.BuildConfig
import com.cronoszip.app.data.CronosSettings
import com.cronoszip.app.data.HistoryEntry
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SectionLabel
import com.cronoszip.app.ui.components.StatusPill
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.Success
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.formatBytes
import com.cronoszip.app.util.formatTime
import com.cronoszip.app.util.hasStorageAccess
import com.cronoszip.app.util.requestAllFilesAccess
import com.cronoszip.app.viewmodel.CronosViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: CronosViewModel,
    onBack: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onOpenUpdate: () -> Unit = {},
    onOpenTheme: () -> Unit = {},
) {
    val settings = viewModel.settings
    val history = viewModel.history
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var storage by remember { mutableStateOf(hasStorageAccess(context)) }
    var confirmClear by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storage = hasStorageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(title = "Settings", onBack = onBack)
        }

        item {
            GlassCard(glow = CronosBlue, contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(GlassWhite.copy(alpha = 0.06f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = CronosCyan,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Extraction engine",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = if (viewModel.engineAvailable) {
                                "Rust core loaded and ready"
                            } else {
                                "Native library not packaged in this build"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    StatusPill(
                        text = if (viewModel.engineAvailable) "Ready" else "Missing",
                        color = if (viewModel.engineAvailable) Success else Danger,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Version ${viewModel.appVersion} (code ${viewModel.appVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = CronosCyan,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All files access",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = if (storage) {
                                "Archive files can be read and shrunk anywhere."
                            } else {
                                "Grant access to open archives outside the app folder."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (!storage) {
                        SecondaryButton(
                            text = "Grant",
                            onClick = { requestAllFilesAccess(context) },
                        )
                    } else {
                        StatusPill(text = "Granted", color = Success)
                    }
                }
            }
        }

        item {
            SectionLabel(text = "File manager")
        }

        item {
            GlassCard(contentPadding = PaddingValues(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenFiles)
                            .background(GlassWhite.copy(alpha = 0.035f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Files", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("One file manager with list and split view, plus app storage shortcuts", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextSecondary)
                    }
                    ThemeEntryRow(onClick = onOpenTheme)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenUpdate)
                            .background(GlassWhite.copy(alpha = 0.035f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Updater", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Check latest release, download SHA-256 verified APK and install in place", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
        }

        item {
            SectionLabel(text = "Space safety")
        }

        item {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = CronosCyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always keep free",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Extraction stops before the device runs out of room.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Text(
                        text = "${settings.keepFreeGb} GB",
                        style = MaterialTheme.typography.titleMedium,
                        color = CronosCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = settings.keepFreeGb.toFloat(),
                    onValueChange = { value ->
                        val gb = value.roundToInt().coerceIn(1, 50)
                        if (gb != settings.keepFreeGb) {
                            viewModel.updateSettings(settings.copy(keepFreeGb = gb))
                        }
                    },
                    valueRange = 1f..50f,
                    steps = 48,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "1 GB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Text(
                        text = "Reserve ${formatBytes(settings.keepFreeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.75f),
                    )
                    Text(
                        text = "50 GB",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        item {
            SectionLabel(text = "Defaults")
        }

        item {
            GlassCard(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 18.dp)) {
                SettingSwitch(
                    title = "Keep archives by default",
                    detail = "Safer, but needs room for both copies. Shrinking in place is the fast path.",
                    checked = settings.keepArchiveByDefault,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(keepArchiveByDefault = it))
                    },
                )
                SettingSwitch(
                    title = "Verify before extracting",
                    detail = "Reads every checksum first. Slower, but damage is found before files move.",
                    checked = settings.verifyFirstByDefault,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(verifyFirstByDefault = it))
                    },
                )
                SettingSwitch(
                    title = "Liquid background motion",
                    detail = "Animated waves, glow and drifting circles.",
                    checked = settings.animations,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(animations = it))
                    },
                    last = true,
                )
            }
        }

        item {
            SectionLabel(text = "Recent archives", trailing = if (history.isEmpty()) null else "${history.size}")
        }

        if (history.isEmpty()) {
            item {
                GlassCard(contentPadding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "Checked archives appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.path }) { entry ->
                HistoryRow(
                    entry = entry,
                    onClick = {
                        viewModel.checkArchive(entry.path)
                    },
                )
            }
            item {
                SecondaryButton(
                    text = "Clear history",
                    icon = Icons.Rounded.DeleteOutline,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { confirmClear = true },
                )
            }
        }

        item {
            SectionLabel(text = "About")
        }

        item {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = CronosCyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CronosZip",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} · Local-only extraction",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Archives never leave the device. The engine verifies checksums, proves " +
                        "free space, and shrinks the archive from the end only after each file is " +
                        "flushed to disk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.82f),
                )
            }
        }
    }

    if (confirmClear) {
        Dialog(onDismissRequest = { confirmClear = false }) {
            GlassCard(glow = Danger) {
                Text(
                    text = "Clear recent archives?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "This only clears the list. Archive files are not touched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f),
                        onClick = { confirmClear = false },
                    )
                    PrimaryButton(
                        text = "Clear",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            confirmClear = false
                            viewModel.clearHistory()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f)),
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassWhite.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CronosBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = CronosCyan,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${entry.status} · ${formatTime(entry.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.format.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (entry.status.startsWith("Extracted")) Success else CronosCyan,
        )
    }
}
