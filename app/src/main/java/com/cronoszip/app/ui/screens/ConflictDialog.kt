package com.cronoszip.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cronoszip.app.data.ConflictPolicy
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.components.SecondaryButton
import com.cronoszip.app.ui.components.SolidDialogCard
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning

/** Asked when a copy or move would land on items that already exist in the destination. */
@Composable
fun ConflictDialog(
    names: List<String>,
    onChoose: (ConflictPolicy) -> Unit,
    onCancel: () -> Unit,
) {
    val shown = names.take(3).joinToString(", ") +
        if (names.size > 3) " and ${names.size - 3} more" else ""
    Dialog(onDismissRequest = onCancel) {
        SolidDialogCard(glow = Warning) {
            Text("Already exists", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "These already exist in the destination: $shown",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "Merge folders",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(ConflictPolicy.Merge) },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Replace existing",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(ConflictPolicy.Replace) },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Skip these",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(ConflictPolicy.Skip) },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Keep both (rename)",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoose(ConflictPolicy.KeepBoth) },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Cancel",
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel,
            )
        }
    }
}
