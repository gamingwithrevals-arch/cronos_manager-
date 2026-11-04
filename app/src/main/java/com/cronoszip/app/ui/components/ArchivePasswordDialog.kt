package com.cronoszip.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary

/** Asks for the password of one archive. [busy] is true while the engine checks it. */
@Composable
fun ArchivePasswordDialog(
    archiveName: String,
    wrong: Boolean,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember(archiveName) { mutableStateOf("") }
    Dialog(onDismissRequest = onCancel) {
        SolidDialogCard(glow = CronosBlue) {
            Text(
                text = "Password protected",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = archiveName,
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (wrong) {
                    "That password did not open this archive. Try again."
                } else {
                    "Enter the password to extract this archive. It stays on this device."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (wrong) Danger else TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                placeholder = { Text("Password", color = TextSecondary) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = "Skip",
                    modifier = Modifier.weight(1f),
                    onClick = onCancel,
                )
                PrimaryButton(
                    text = if (busy) "Checking…" else "Unlock",
                    modifier = Modifier.weight(1f),
                    enabled = password.isNotEmpty() && !busy,
                    onClick = { onUnlock(password) },
                )
            }
        }
    }
}
