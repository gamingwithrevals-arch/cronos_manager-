package com.cronoszip.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary

/** The Zip dialog: name the archive, choose store-only or normal compression, then compress. */
@Composable
fun CompressDialog(
    count: Int,
    busy: Boolean,
    name: String,
    onName: (String) -> Unit,
    onCancel: () -> Unit,
    onCompress: (Boolean) -> Unit,
) {
    var store by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!busy) onCancel() }) {
        SolidDialogCard(glow = CronosCyan) {
            Text("Compress to ZIP", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$count item(s) will be zipped into the active folder. Folders include everything inside them.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Archive name", color = TextSecondary) },
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Store only (no compression, fastest)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = store, onCheckedChange = { store = it })
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                PrimaryButton(
                    text = if (busy) "Working…" else "Compress",
                    modifier = Modifier.weight(1f),
                    enabled = !busy && name.isNotBlank() && count > 0,
                    onClick = { onCompress(store) },
                )
            }
        }
    }
}
