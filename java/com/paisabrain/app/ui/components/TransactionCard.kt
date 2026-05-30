package com.paisabrain.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paisabrain.app.db.Transaction
import com.paisabrain.app.db.TransactionType
import com.paisabrain.app.ui.theme.PaisaError
import com.paisabrain.app.ui.theme.PaisaSuccess
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionCard(transaction: Transaction) {
    val categoryEmoji = getCategoryEmoji(transaction.category)
    val amountColor = if (transaction.type == TransactionType.CREDIT) PaisaSuccess else PaisaError
    val amountPrefix = if (transaction.type == TransactionType.CREDIT) "+" else "-"
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoryEmoji,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = transaction.merchant.take(25),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${transaction.category} • ${timeFormat.format(Date(transaction.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "$amountPrefix₹${String.format("%,.0f", transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}

private fun getCategoryEmoji(category: String): String {
    return when (category.lowercase()) {
        "food", "food & dining" -> "🍕"
        "transport" -> "🚗"
        "shopping" -> "🛍️"
        "bills", "bills & utilities" -> "📄"
        "entertainment" -> "🎬"
        "health" -> "💊"
        "education" -> "📚"
        "travel" -> "✈️"
        "groceries" -> "🛒"
        "subscriptions" -> "📦"
        "transfers" -> "↔️"
        "atm" -> "🏧"
        "emi" -> "💳"
        "salary" -> "💰"
        "investment" -> "📈"
        else -> "💸"
    }
}
