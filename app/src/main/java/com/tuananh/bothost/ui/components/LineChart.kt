package com.tuananh.bothost.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    title: String,
    values: List<Float>,
    maxValue: Float,
    suffix: String,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val label = values.lastOrNull()?.let { "%.0f%s".format(it, suffix) } ?: "--"
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("$title · $label", style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
                repeat(4) { i ->
                    val y = size.height * i / 3f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                if (values.size >= 2) {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.size - 1).toFloat()
                        val ratio = (value / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val y = size.height - size.height * ratio
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
