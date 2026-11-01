package com.cronoszip.app.ui.components

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning
import com.cronoszip.app.util.formatBytes

/** Free and total space of the storage volume that holds a path. */
data class VolumeSpace(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
    val usedFraction: Float
        get() = if (total <= 0L) 0f else (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

/** Reads the volume's space, or null when the path is not on a readable volume. */
fun volumeSpace(path: String): VolumeSpace? = runCatching {
    val stat = StatFs(path)
    VolumeSpace(total = stat.totalBytes, free = stat.availableBytes)
}.getOrNull()

/** A bar showing used and free space for the volume that holds [path]. Hidden if unreadable. */
@Composable
fun StorageSpaceBar(path: String, modifier: Modifier = Modifier) {
    val space = remember(path) { volumeSpace(path) } ?: return
    val barColor = if (space.usedFraction > 0.9f) Warning else CronosCyan
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Free ${formatBytes(space.free)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
            )
            Text(
                text = "of ${formatBytes(space.total)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(space.usedFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(CronosBlue, barColor))),
            )
        }
    }
}
