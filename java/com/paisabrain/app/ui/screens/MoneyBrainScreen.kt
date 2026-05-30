package com.paisabrain.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paisabrain.app.R
import com.paisabrain.app.ui.components.SpendingChart
import com.paisabrain.app.ui.components.TransactionCard
import com.paisabrain.app.ui.theme.PaisaSuccess
import com.paisabrain.app.ui.theme.PaisaError
import com.paisabrain.app.ui.theme.PaisaWarning
import com.paisabrain.app.viewmodel.MoneyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyBrainScreen(viewModel: MoneyViewModel = viewModel()) {
    val transactions by viewModel.recentTransactions.collectAsState(initial = emptyList())
    val todaySpent by viewModel.todaySpent.collectAsState(initial = 0.0)
    val monthSpent by viewModel.monthSpent.collectAsState(initial = 0.0)
    val monthBudget by viewModel.monthBudget.collectAsState(initial = 30000.0)

    val remaining = monthBudget - monthSpent
    val progress = if (monthBudget > 0) (monthSpent / monthBudget).toFloat().coerceIn(0f, 1f) else 0f
    val daysLeft = viewModel.daysLeftInMonth()
    val safePerDay = if (daysLeft > 0) remaining / daysLeft else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "💰 " + stringResource(R.string.nav_money),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Today's Spending Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.money_today_spent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${String.format("%,.0f", todaySpent)}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (todaySpent > safePerDay) PaisaError else PaisaSuccess
                    )
                }
            }
        }

        // Monthly Budget Progress
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.money_month_budget),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "₹${String.format("%,.0f", monthSpent)} / ₹${String.format("%,.0f", monthBudget)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        color = when {
                            progress > 0.9f -> PaisaError
                            progress > 0.7f -> PaisaWarning
                            else -> PaisaSuccess
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.money_remaining),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "₹${String.format("%,.0f", remaining)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (remaining > 0) PaisaSuccess else PaisaError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                stringResource(R.string.money_safe_to_spend),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "₹${String.format("%,.0f", safePerDay)}/day",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Spending Chart
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Last 7 Days",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SpendingChart(
                        data = viewModel.getLast7DaysSpending(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }
        }

        // Recent Transactions
        item {
            Text(
                stringResource(R.string.money_recent_transactions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = stringResource(R.string.money_no_transactions),
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(transactions.take(20)) { transaction ->
                TransactionCard(transaction = transaction)
            }
        }
    }
}
