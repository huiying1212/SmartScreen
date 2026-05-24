package com.datacollector.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datacollector.android.ui.theme.Lavender
import com.datacollector.android.ui.theme.Mint
import com.datacollector.android.ui.theme.NavPill
import com.datacollector.android.ui.theme.PaperWhite
import com.datacollector.android.ui.theme.Peach
import com.datacollector.android.ui.theme.ScoreAlert
import com.datacollector.android.ui.theme.ScoreCalm
import com.datacollector.android.ui.theme.ScoreSteady

// ── Layout primitives ────────────────────────────────────────────────────

/**
 * Big, page-level title block. Uses the heavy display scale so each screen
 * has the same "magazine cover" feel as the reference designs.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Standard white card with generous corner radius. The cream background
 * provides enough contrast; we keep elevation at 0 for a flat look.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/**
 * Big colored hero card — the visual centerpiece of the Home screen.
 * Title left, optional body, decorative slot on the right, and a circular
 * arrow CTA in the bottom-left corner.
 */
@Composable
fun HeroCard(
    title: String,
    body: String? = null,
    background: Color = Lavender,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    decoration: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = contentColor,
                )
                if (!body.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (onClick != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NavPill)
                            .clickable(onClick = onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = PaperWhite,
                        )
                    }
                }
            }
            if (decoration != null) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) { decoration() }
            }
        }
    }
}

/**
 * Colored stat block used in pairs/rows. Mimics the "Lessons / Hours" cards
 * in the reference education app — soft pastel background, bold number,
 * label tucked into the top corner.
 */
@Composable
fun StatBlock(
    label: String,
    value: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Inline pill chip — used as both stat indicator and mood/tag selector.
 * Tap-friendly with selected state for the "What's your mood" style.
 */
@Composable
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val base = Modifier
        .clip(RoundedCornerShape(50))
        .background(bg)
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(
        modifier = modifier
            .then(withClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/**
 * Compact stat chip (label + value side-by-side). Kept for screens that
 * relied on the older API.
 */
@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Data viz ─────────────────────────────────────────────────────────────

@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    sizeDp: Int = 144,
    strokeWidthDp: Int = 14,
) {
    val clamped = score.coerceIn(0, 100)
    val color = when {
        clamped < 33 -> ScoreCalm
        clamped < 66 -> ScoreSteady
        else -> ScoreAlert
    }
    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(sizeDp.dp)) {
            val stroke = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)
            drawArc(
                color = Color(0x14000000),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color, color.copy(alpha = 0.6f), color)),
                startAngle = -90f,
                sweepAngle = clamped * 3.6f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clamped.toString(),
                fontSize = (sizeDp / 3).sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "今日分数",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MiniLineChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    heightDp: Int = 80,
) {
    if (values.isEmpty()) {
        EmptyState(title = "暂无数据", body = "等下一次评分采集吧。")
        return
    }
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val w = size.width
        val h = size.height
        val maxV = values.max().coerceAtLeast(1).toFloat()
        val stepX = if (values.size > 1) w / (values.size - 1) else 0f

        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            drawLine(
                color = grid,
                start = Offset(0f, h * f),
                end = Offset(w, h * f),
                strokeWidth = 1f,
            )
        }
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = h - (v / maxV) * h
            val cur = Offset(x, y)
            prev?.let {
                drawLine(
                    color = color,
                    start = it,
                    end = cur,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }
            prev = cur
        }
    }
}

