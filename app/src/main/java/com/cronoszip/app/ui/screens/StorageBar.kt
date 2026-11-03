package com.cronoszip.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.SafStorage
import com.cronoszip.app.ui.components.GlassChip
import com.cronoszip.app.ui.theme.TextSecondary

/**
 * Remembers the folder each mounted folder was opened from, so "Up one folder" works inside a
 * mount. Going up never overwrites a parent, so the trail stays correct.
 */
object SafNav {
    private val parents = HashMap<String, String>()

    /** The part of a document URI that identifies its tree. Two paths in the same tree share it. */
    private fun treeKey(path: String): String = path.substringBefore("/document/")

    /** Called whenever a pane moves from [from] to [to]. */
    fun moved(from: String, to: String) {
        if (from == to || !SafStorage.isSaf(from) || !SafStorage.isSaf(to)) return
        if (treeKey(from) != treeKey(to)) return
        if (parents[from] == to) return
        parents[to] = from
    }

    /** The folder above a mounted folder, or null at the top of the mount. */
    fun parentOf(path: String): String? = parents[path]
}

/**
 * The name of the app that provides a mounted storage, such as "Dolphin Emulator".
 * Falls back to the storage's own name when the provider app can't be found.
 */
fun storageAppLabel(context: Context, treeUri: Uri): String {
    val pm = context.packageManager
    val authority = treeUri.authority
    if (authority != null) {
        val label = runCatching {
            val providerPackage = pm.resolveContentProvider(authority, 0)?.packageName
                ?: return@runCatching null
            pm.getApplicationLabel(pm.getApplicationInfo(providerPackage, 0)).toString()
        }.getOrNull()
        if (!label.isNullOrBlank()) return label
    }
    return SafStorage.displayName(context, treeUri) ?: "Storage"
}

/** One chip in a bar: its text, whether it is the active one, and what a tap does. */
data class BarChip(val text: String, val selected: Boolean, val onClick: () -> Unit)

/** A small label above a row of chips. */
@Composable
fun BarLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/** A horizontal row of chips that scrolls when there are too many. */
@Composable
fun BarChipRow(chips: List<BarChip>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { chip ->
            GlassChip(text = chip.text, selected = chip.selected, onClick = chip.onClick)
        }
    }
}
