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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cronoszip.app.ui.screens.liquidGlass
import com.cronoszip.app.ui.theme.CronosBlue

/**
 * Dialog and action-menu surface. It uses the same liquid-glass panel as the rest of the app.
 * A dark base sits under the glass so text stays readable over the screen behind it, and a soft
 * accent rim frames it. Pure Material You shows a solid container instead.
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
            .background(Color(0xE6071324))
            .liquidGlass(28.dp)
            .border(1.dp, glow.copy(alpha = 0.45f), shape)
            .padding(20.dp),
        content = content,
    )
}
