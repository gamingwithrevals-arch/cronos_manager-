package com.cronoszip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.EntryData
import com.cronoszip.app.data.PlanData
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.Success
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.formatBytes

/** A rounded, translucent panel used for every card on the archive screen. */
fun Modifier.archiveSurface(radius: Dp = 22.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(GlassWhite.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(radius))

@Composable
fun ArchiveSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
fun ArchiveStatBox(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .archiveSurface(18.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) CronosCyan else TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArchiveHeroCard(name: String, format: String, entries: Int, packed: Long, unpacked: Long) {
    Column(modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CronosBlue.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Archive, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${format.uppercase()} · $entries entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArchiveStatBox("Packed", formatBytes(packed), Modifier.weight(1f))
            ArchiveStatBox("Unpacked", formatBytes(unpacked), Modifier.weight(1f), highlight = true)
        }
    }
}

@Composable
fun ArchivePlanCard(plan: PlanData?) {
    Column(modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(16.dp)) {
        if (plan == null) {
            Text(
                text = "Checking the space this archive needs…",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Will it fit?",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (plan.fits) "Fits" else "Not enough room",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (plan.fits) Success else Warning,
                )
            }
            Spacer(Modifier.height(12.dp))
            ArchivePlanLine("Free now", formatBytes(plan.free))
            ArchivePlanLine("Required at peak", formatBytes(plan.required))
            ArchivePlanLine("Kept free", formatBytes(plan.keepFree))
            ArchivePlanLine("Free when finished", formatBytes(plan.freeAfter), highlight = plan.fits)
        }
    }
}

@Composable
private fun ArchivePlanLine(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) Success else TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ArchiveDestinationCard(output: String, enabled: Boolean, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Extract into", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = output.ifBlank { "Next to the archive" },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (enabled) {
            GlassChip(text = "Change", onClick = onChange)
        }
    }
}

@Composable
fun ArchiveOptionsCard(
    keepArchive: Boolean,
    keepFreeBytes: Long,
    enabled: Boolean,
    onKeepArchive: (Boolean) -> Unit,
    onKeepFree: (Long) -> Unit,
) {
    val gb = 1024L * 1024L * 1024L
    Column(modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Keep the archive", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(
                    text = "Off: the archive shrinks as files land, which needs the least space.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Switch(checked = keepArchive, enabled = enabled, onCheckedChange = onKeepArchive)
        }
        Spacer(Modifier.height(14.dp))
        Text(text = "Keep free space", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2, 5, 10).forEach { g ->
                GlassChip(
                    text = "$g GB",
                    selected = keepFreeBytes == g * gb,
                    onClick = { if (enabled) onKeepFree(g * gb) },
                )
            }
        }
    }
}

@Composable
fun ArchiveEntryLine(entry: EntryData) {
    val isDir = entry.kind == "dir"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GlassWhite.copy(alpha = 0.035f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            tint = if (isDir) CronosCyan else Color(0xFF9CC7FF),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.path.substringAfterLast('/').ifEmpty { entry.path },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parent = entry.path.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) {
                Text(
                    text = parent,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isDir) "Folder" else formatBytes(entry.size),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
fun ArchiveProgressCard(
    status: String,
    fraction: Float,
    done: Long,
    total: Long,
    speed: Double,
    latest: String?,
    count: Int,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth().archiveSurface(24.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = status,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(safeFraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = CronosCyan,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(safeFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(CronosBlue, CronosCyan))),
            ) {}
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${formatBytes(done)} of ${formatBytes(total)} · ${formatBytes(speed.toLong())}/s",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        if (latest != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Latest: $latest",
                style = MaterialTheme.typography.labelMedium,
                color = Success,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$count extracted so far",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
