package com.cronoszip.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cronoszip.app.ui.theme.CronosBlue
import com.cronoszip.app.ui.theme.CronosCyan
import com.cronoszip.app.ui.theme.Danger
import com.cronoszip.app.ui.theme.GlassWhite
import com.cronoszip.app.ui.theme.Success
import com.cronoszip.app.ui.theme.TextPrimary
import com.cronoszip.app.ui.theme.TextSecondary
import com.cronoszip.app.ui.theme.Warning

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(CronosBlue, CronosCyan),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = GlassWhite.copy(alpha = 0.28f),
            radius = radius - 1.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(size.width * 0.32f, size.height * 0.24f),
                radius = radius * 0.9f,
            ),
            radius = radius,
            center = center,
        )
        val w = size.width
        val stroke = w * 0.055f
        drawLine(
            color = Color.White,
            start = Offset(w * 0.35f, w * 0.35f),
            end = Offset(w * 0.65f, w * 0.35f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.65f, w * 0.35f),
            end = Offset(w * 0.35f, w * 0.65f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(w * 0.35f, w * 0.65f),
            end = Offset(w * 0.65f, w * 0.65f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = if (animated) CronosCyan else Color.White.copy(alpha = 0.8f),
            radius = w * 0.042f,
            center = Offset(w * 0.73f, w * 0.27f),
        )
    }
}

@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    textSize: TextUnit = 42.sp,
    markSize: Dp = 46.dp,
    showMark: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (showMark) {
            BrandMark(modifier = Modifier.size(markSize))
            Spacer(Modifier.width(11.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Cronos",
                style = TextStyle(
                    color = CronosBlue,
                    fontSize = textSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.4).sp,
                    shadow = Shadow(
                        color = CronosBlue.copy(alpha = 0.45f),
                        blurRadius = 28f,
                    ),
                ),
            )
            Text(
                text = "Zip",
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = textSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1.4).sp,
                    shadow = Shadow(
                        color = Color.White.copy(alpha = 0.18f),
                        blurRadius = 22f,
                    ),
                ),
            )
        }
    }
}

/**
 * The card used everywhere, including inside dialogs and overlays. It is solid dark blue now,
 * so the screen behind it no longer shows through. The blue border glow is kept.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    glow: Color = CronosBlue,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E2340),
                        Color(0xFF0A1A30),
                        Color(0xFF071324),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.White.copy(alpha = 0.06f),
                        glow.copy(alpha = 0.34f),
                    ),
                ),
                shape = shape,
            )
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(CronosBlue, Color(0xFF2478FF), CronosCyan))
                } else {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.05f)),
                    )
                },
            )
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0.08f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) Color.White else TextSecondary,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = if (enabled) Color.White else TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(GlassWhite.copy(alpha = 0.055f))
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)),
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) TextPrimary else TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            color = if (enabled) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(CronosBlue.copy(alpha = 0.5f), CronosCyan.copy(alpha = 0.25f)),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(GlassWhite.copy(alpha = 0.06f), GlassWhite.copy(alpha = 0.025f)),
                    )
                },
            )
            .border(
                1.dp,
                if (selected) CronosCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.11f),
                shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BackCircle(onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun BackCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(GlassWhite.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimary,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(GlassWhite.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = valueColor.copy(alpha = 0.9f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = CronosCyan,
            )
        }
    }
}

@Composable
fun PlanBar(
    free: Long,
    needed: Long,
    keepFree: Long,
    fits: Boolean,
    modifier: Modifier = Modifier,
) {
    val required = (needed + keepFree).coerceAtLeast(1L)
    val scale = maxOf(maxOf(free, required), 1L).toFloat()
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val radius = size.height / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            cornerRadius = CornerRadius(radius),
        )
        val needWidth = size.width * (needed.toFloat() / scale)
        val keepWidth = size.width * (keepFree.toFloat() / scale)
        drawRoundRect(
            brush = if (fits) {
                Brush.horizontalGradient(listOf(CronosBlue, CronosCyan))
            } else {
                Brush.horizontalGradient(listOf(Danger.copy(alpha = 0.85f), Warning))
            },
            size = Size(needWidth.coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(radius),
        )
        if (keepWidth > 0f) {
            drawRoundRect(
                color = Warning.copy(alpha = 0.55f),
                topLeft = Offset(needWidth, 0f),
                size = Size(keepWidth, size.height),
                cornerRadius = CornerRadius(radius),
            )
        }
        val freeX = (size.width * (free.toFloat() / scale)).coerceIn(0f, size.width)
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(freeX, 0f),
            end = Offset(freeX, size.height),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            drawArc(
                color = CronosBlue.copy(alpha = 0.28f),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(CronosBlue, CronosCyan, Color.White, CronosBlue),
                    center = Offset(size.width / 2f, size.height / 2f),
                ),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun CheckingOverlay(
    message: String = "Checking space and safety",
    detail: String = "Nothing has been changed yet.",
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(modifier = Modifier.widthIn(max = 330.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = CronosCyan,
                    strokeWidth = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color = Success,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
