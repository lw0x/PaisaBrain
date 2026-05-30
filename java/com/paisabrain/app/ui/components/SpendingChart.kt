package com.paisabrain.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.paisabrain.app.ui.theme.PaisaPrimary
import com.paisabrain.app.ui.theme.PaisaSecondary

@Composable
fun SpendingChart(
    data: List<Pair<String, Double>>, // (day label, amount)
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxAmount = data.maxOfOrNull { it.second } ?: 1.0

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val barWidth = size.width / (data.size * 2f)
            val maxHeight = size.height * 0.85f

            data.forEachIndexed { index, (_, amount) ->
                val barHeight = ((amount / maxAmount) * maxHeight).toFloat()
                val x = (index * 2 + 0.5f) * barWidth

                // Bar
                drawRoundRect(
                    color = PaisaPrimary,
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }

        // Day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
