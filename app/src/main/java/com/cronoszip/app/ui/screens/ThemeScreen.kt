package com.cronoszip.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.data.AppPrefs
import com.cronoszip.app.data.ThemeMode
import com.cronoszip.app.ui.components.BackCircle
import com.cronoszip.app.ui.components.PrimaryButton
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.Ink
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary

/** Custom colour choices shown as swatches. */
private val swatchArgb: List<Int> = listOf(
    0xFF2F7CFF.toInt(),
    0xFF35D6FF.toInt(),
    0xFF8B5CF6.toInt(),
    0xFFEC4899.toInt(),
    0xFFF97316.toInt(),
    0xFF22C55E.toInt(),
    0xFFEAB308.toInt(),
    0xFFEF4444.toInt(),
)

/** A row on the Settings screen that opens the theme picker. */
@Composable
fun ThemeEntryRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(GlassWhite.copy(alpha = 0.035f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Palette, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Theme & colours", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Cronos blue, phone colours (Material You) or your own colour",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

/** The theme picker: choose the colour mode, pick a custom colour, and see a preview. */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // AppPrefs is the theme source consumed by CronosZipTheme; ThemePrefs is not wired into it.
    val mode = AppPrefs.themeMode
    val customArgb = AppPrefs.customThemeColor
    val materialAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackCircle(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "CRONOS MANAGER",
                    style = MaterialTheme.typography.labelSmall,
                    color = CronosCyan,
                    letterSpacing = 1.4.sp,
                )
                Text("Theme", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModeOption(
                mode = ThemeMode.Cronos,
                selected = mode == ThemeMode.Cronos,
                enabled = true,
                note = null,
                onClick = { AppPrefs.setTheme(context, ThemeMode.Cronos) },
            )
            ModeOption(
                mode = ThemeMode.Material,
                selected = mode == ThemeMode.Material,
                enabled = materialAvailable,
                note = if (materialAvailable) null else "Needs Android 12 or newer. This phone uses Cronos blue.",
                onClick = { if (materialAvailable) AppPrefs.setTheme(context, ThemeMode.Material) },
            )
            ModeOption(
                mode = ThemeMode.Custom,
                selected = mode == ThemeMode.Custom,
                enabled = true,
                note = null,
                onClick = { AppPrefs.setTheme(context, ThemeMode.Custom) },
            )

            Spacer(Modifier.height(6.dp))
            Text(
                "CUSTOM COLOUR",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 1.2.sp,
            )
            SwatchRow(
                selectedArgb = customArgb,
                onPick = { AppPrefs.setTheme(context, ThemeMode.Custom, it) },
            )

            Spacer(Modifier.height(6.dp))
            ThemePreview()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModeOption(
    mode: ThemeMode,
    selected: Boolean,
    enabled: Boolean,
    note: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(GlassWhite.copy(alpha = if (selected) 0.09f else 0.035f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                mode.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) TextPrimary else TextSecondary,
            )
            Text(
                note ?: mode.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = CronosCyan, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SwatchRow(selectedArgb: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        swatchArgb.forEach { argb ->
            val isSelected = argb == selectedArgb
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(2.dp, if (isSelected) TextPrimary else Color.Transparent, CircleShape)
                    .clickable { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemePreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassWhite.copy(alpha = 0.05f))
            .padding(16.dp),
    ) {
        Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = CronosCyan, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your colours apply to every screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = "Sample button", modifier = Modifier.fillMaxWidth(), onClick = {})
    }
}
