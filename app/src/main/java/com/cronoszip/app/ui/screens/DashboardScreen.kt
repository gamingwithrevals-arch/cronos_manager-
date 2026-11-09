package com.cronoszip.app.ui.screens

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SectionLabel
import com.cronoszip.app.ui.components.VolumeSpace
import com.cronoszip.app.ui.components.volumeSpace
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.util.formatBytes
import com.cronoszip.app.util.requestAllFilesAccess
import java.io.File
import java.nio.file.Files
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Stops the scan from running forever on very large storage. */
private const val DASH_MAX_VISITED = 300_000

/** The file kinds counted by extension. Each one opens the matching Files category. */
private enum class DashKind(
    val title: String,
    val extensions: Set<String>,
    val color: Color,
    val icon: ImageVector,
    val browseCategory: String,
) {
    Images(
        "Images",
        setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"),
        CronosBlue,
        Icons.Rounded.Image,
        "Images",
    ),
    Videos(
        "Videos",
        setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v"),
        Color(0xFFFF4D8D),
        Icons.Rounded.PlayArrow,
        "Movies",
    ),
    Audio(
        "Audio",
        setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus"),
        Color(0xFFFF9F43),
        Icons.Rounded.MusicNote,
        "Music",
    ),
    Apks(
        "APKs",
        setOf("apk"),
        Color(0xFF4ADE80),
        Icons.Rounded.Apps,
        "Apk",
    ),
}

private class DashKindStat {
    var count = 0
    var bytes = 0L
}

private class DashFolder(
    val name: String,
    val path: String,
    val items: Int,
    val modified: Long,
)

private class DashScan(
    val kinds: Map<DashKind, DashKindStat>,
    val folders: List<DashFolder>,
) {
    fun folder(name: String): DashFolder? = folders.firstOrNull { it.name == name }
}

private class DashTile(
    val title: String,
    val color: Color,
    val icon: ImageVector,
    val count: Int?,
    val bytes: Long,
    val onClick: () -> Unit,
)

/**
 * The Home tab: storage at a glance, quick categories, storage locations and recent folders.
 * Every tile opens the Files screen at the matching category or folder.
 */
@Composable
fun DashboardScreen(onOpenBrowse: (category: String?, folder: String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootFile = dashStorageRoot()
    val rootPath = rootFile.absolutePath
    var granted by remember { mutableStateOf(dashHasAllFilesAccess()) }

    // Re-checks permission when the user comes back from the system settings page.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = dashHasAllFilesAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scan by produceState<DashScan?>(initialValue = null, key1 = granted) {
        value = if (granted) dashScanStorage(rootFile) else null
    }
    val scanning = granted && scan == null
    val space = remember(rootPath) { volumeSpace(rootPath) }

    val kindTiles = DashKind.entries.map { kind ->
        val stat = scan?.kinds?.get(kind)
        DashTile(
            title = kind.title,
            color = kind.color,
            icon = kind.icon,
            count = stat?.count,
            bytes = stat?.bytes ?: 0L,
            onClick = { onOpenBrowse(kind.browseCategory, null) },
        )
    }
    val folderTiles = listOf(
        DashTile(
            title = "Documents",
            color = Color(0xFFFFC857),
            icon = Icons.Rounded.Description,
            count = scan?.folder("Documents")?.items,
            bytes = 0L,
            onClick = { onOpenBrowse(null, File(rootPath, "Documents").absolutePath) },
        ),
        DashTile(
            title = "Downloads",
            color = CronosCyan,
            icon = Icons.Rounded.Download,
            count = scan?.folder("Download")?.items,
            bytes = 0L,
            onClick = { onOpenBrowse(null, File(rootPath, "Download").absolutePath) },
        ),
    )
    val tiles = kindTiles + folderTiles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DashHeader(onSearch = { onOpenBrowse(null, null) })

        if (!granted) {
            DashPermissionCard(onAllow = { requestAllFilesAccess(context) })
        }

        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenBrowse(null, rootPath) },
            contentPadding = PaddingValues(18.dp),
        ) {
            DashStorageSummary(space = space)
        }

        SectionLabel("Categories")
        tiles.chunked(3).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { tile ->
                    DashTileCard(
                        tile = tile,
                        subtitle = dashTileSubtitle(tile, scanning),
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            SectionLabel("Storage locations")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashLocationChip(
                    icon = Icons.Rounded.Storage,
                    title = "Internal",
                    subtitle = space?.let { "${formatBytes(it.used)} / ${formatBytes(it.total)}" }
                        ?: "Unavailable",
                    color = CronosCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenBrowse(null, rootPath) },
                )
                DashLocationChip(
                    icon = Icons.Rounded.FolderOpen,
                    title = "External",
                    subtitle = "Open in Browse",
                    color = CronosBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenBrowse(null, null) },
                )
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
        ) {
            SectionLabel("Recent folders")
            Spacer(Modifier.height(6.dp))
            val folders = scan?.folders.orEmpty()
            if (folders.isEmpty()) {
                Text(
                    text = if (scanning) "Looking through your folders…" else "No common folders found yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                )
            }
            folders.sortedByDescending { it.modified }.forEachIndexed { index, folder ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f)),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBrowse(null, folder.path) }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = CronosBlue,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${dashTimeAgo(folder.modified)} · ${folder.items} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashHeader(onSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(CronosBlue, CronosCyan))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chronos Manager",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Manage • Extract • Organize",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search files",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DashStorageSummary(space: VolumeSpace?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DashUsageRing(fraction = space?.usedFraction ?: 0f)
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Internal Storage",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (space == null) {
                    "Storage info unavailable"
                } else {
                    "${formatBytes(space.used)} / ${formatBytes(space.total)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            DashUsageBar(fraction = space?.usedFraction ?: 0f)
            Spacer(Modifier.height(10.dp))
            DashLegendRow(
                color = CronosBlue,
                label = "Used",
                value = space?.let { formatBytes(it.used) } ?: "—",
            )
            DashLegendRow(
                color = Color.White.copy(alpha = 0.35f),
                label = "Free",
                value = space?.let { formatBytes(it.free) } ?: "—",
            )
        }
    }
}

@Composable
private fun DashUsageRing(fraction: Float) {
    val percent = (fraction * 100).roundToInt()
    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 12.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(CronosBlue, CronosCyan, CronosBlue)),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Used",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun DashUsageBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(CronosBlue, CronosCyan))),
        )
    }
}

@Composable
private fun DashLegendRow(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
        )
    }
}

@Composable
private fun DashTileCard(tile: DashTile, subtitle: String, modifier: Modifier) {
    GlassCard(
        modifier = modifier.clickable(onClick = tile.onClick),
        shape = RoundedCornerShape(22.dp),
        glow = tile.color,
        contentPadding = PaddingValues(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tile.color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = tile.color,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun DashLocationChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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
private fun DashPermissionCard(onAllow: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glow = CronosCyan,
        contentPadding = PaddingValues(16.dp),
    ) {
        Text(
            text = "Allow file access",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Chronos Manager needs all-files access to count and open your storage.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Allow access",
            modifier = Modifier.fillMaxWidth(),
            onClick = onAllow,
        )
    }
}

private fun dashTileSubtitle(tile: DashTile, scanning: Boolean): String = when {
    tile.count == null -> if (scanning) "Counting…" else "No access yet"
    tile.bytes > 0L -> "${tile.count} files · ${formatBytes(tile.bytes)}"
    else -> "${tile.count} items"
}

private fun dashTimeAgo(modified: Long): String {
    if (modified <= 0L) return "unknown"
    val minutes = (System.currentTimeMillis() - modified) / 60_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 60L * 24L -> "${minutes / 60L} h ago"
        else -> "${minutes / (60L * 24L)} d ago"
    }
}

private fun dashStorageRoot(): File = Environment.getExternalStorageDirectory()

private fun dashHasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

/**
 * Counts file kinds across the storage root, and the size of the common folders.
 * Runs on a background thread and stops when the dashboard leaves the screen.
 */
private suspend fun dashScanStorage(root: File): DashScan = withContext(Dispatchers.IO) {
    val kinds = DashKind.entries.associateWith { DashKindStat() }
    var visited = 0
    val queue = ArrayDeque<File>()
    queue.addLast(root)

    while (queue.isNotEmpty() && visited < DASH_MAX_VISITED) {
        currentCoroutineContext().ensureActive()
        val dir = queue.removeFirst()
        val children = dir.listFiles() ?: continue
        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                val skip = dir.name == "Android" && child.name in setOf("data", "obb")
                if (!skip) queue.addLast(child)
            } else {
                visited++
                val kind = dashKindOf(child.name) ?: continue
                val stat = kinds.getValue(kind)
                stat.count++
                stat.bytes += child.length()
            }
        }
    }

    val folders = listOf("Download", "DCIM", "Documents", "Pictures").mapNotNull { name ->
        val dir = File(root, name)
        if (!dir.isDirectory) {
            null
        } else {
            DashFolder(
                name = name,
                path = dir.absolutePath,
                items = dir.listFiles()?.count { !it.name.startsWith(".") } ?: 0,
                modified = dir.lastModified(),
            )
        }
    }
    DashScan(kinds = kinds, folders = folders)
}

private fun dashKindOf(name: String): DashKind? {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return DashKind.entries.firstOrNull { ext in it.extensions }
}
