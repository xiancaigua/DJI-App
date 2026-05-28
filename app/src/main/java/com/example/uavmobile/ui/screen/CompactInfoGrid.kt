package com.example.uavmobile.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class CompactInfoItem(
    val label: String,
    val value: String,
)

@Composable
internal fun CompactInfoGrid(
    items: List<CompactInfoItem>,
    modifier: Modifier = Modifier,
    maxColumns: Int = 4,
    minItemWidth: Dp = 128.dp,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    valueMaxLines: Int = 1,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = compactColumnCount(
            availableWidth = maxWidth,
            maxColumns = maxColumns,
            minItemWidth = minItemWidth,
            horizontalSpacing = horizontalSpacing,
        )
        Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                ) {
                    rowItems.forEach { item ->
                        CompactInfoCell(
                            item = item,
                            valueMaxLines = valueMaxLines,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun compactColumnCount(
    availableWidth: Dp,
    maxColumns: Int,
    minItemWidth: Dp,
    horizontalSpacing: Dp,
): Int {
    val boundedMaxColumns = maxColumns.coerceAtLeast(1)
    for (candidate in boundedMaxColumns downTo 1) {
        val neededWidth = minItemWidth * candidate.toFloat() + horizontalSpacing * (candidate - 1).toFloat()
        if (availableWidth >= neededWidth) {
            return candidate
        }
    }
    return 1
}

@Composable
private fun CompactInfoCell(
    item: CompactInfoItem,
    valueMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = valueMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
