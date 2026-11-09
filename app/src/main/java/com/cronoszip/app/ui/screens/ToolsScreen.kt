package com.cronoszip.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cronoszip.app.ui.components.GlassCard
import com.cronoszip.app.ui.components.ScreenHeader
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary

/** The Tools tab: archive, app, network, theme and update tools in one place. */
@Composable
fun ToolsScreen(
    onOpenExtract: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenLan: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(
            title = "Tools",
            subtitle = "Extract, share and keep the app current",
        )
        ToolRow(
            icon = Icons.Rounded.Archive,
            title = "Extract archives",
            subtitle = "Open a zip, 7z, tar or rar file",
            color = CronosBlue,
            onClick = onOpenExtract,
        )
        ToolRow(
            icon = Icons.Rounded.Apps,
            title = "Installed apps",
            subtitle = "Browse and back up apps on this phone",
            color = CronosCyan,
            onClick = onOpenApps,
        )
        ToolRow(
            icon = Icons.Rounded.Wifi,
            title = "LAN transfer",
            subtitle = "Share files between two phones on Wi-Fi",
            color = CronosBlue,
            onClick = onOpenLan,
        )
        ToolRow(
            icon = Icons.Rounded.Palette,
            title = "Theme & colours",
            subtitle = "Cronos blue, phone colours or your own",
            color = CronosCyan,
            onClick = onOpenTheme,
        )
        ToolRow(
            icon = Icons.Rounded.Refresh,
            title = "Check for updates",
            subtitle = "See if a newer version is available",
            color = CronosBlue,
            onClick = onOpenUpdate,
        )
    }
}

@Composable
private fun ToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        glow = color,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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
