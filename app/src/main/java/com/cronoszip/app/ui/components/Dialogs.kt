package com.cronoszip.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.InkBlue
import com.cronoszip.app.ui.theme.NightBlue

/**
 * An opaque card for dialogs. GlassCard is translucent on purpose, so inside a dialog the
 * screen behind it shows through. Use this for every dialog surface.
 */
@Composable
fun SolidDialogCard(
    modifier: Modifier = Modifier,
    glow: Color = CronosBlue,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(NightBlue, InkBlue)))
            .border(1.dp, glow.copy(alpha = 0.5f), shape)
            .padding(20.dp),
        content = content,
    )
}
