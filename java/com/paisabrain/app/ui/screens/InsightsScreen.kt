package com.paisabrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paisabrain.app.R
import com.paisabrain.app.ui.components.PersonalityCard
import com.paisabrain.app.viewmodel.MoneyViewModel

@Composable
fun InsightsScreen(viewModel: MoneyViewModel = viewModel()) {
    val personality by viewModel.moneyPersonality.collectAsState(initial = null)
    val weeklyRoast by viewModel.currentRoast.collectAsState(initial = "")
    val ghostSubs by viewModel.ghostSubscriptions.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Text(
                text = "💡 " + stringResource(R.string.nav_insights),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Money Personality
        item {
            personality?.let {
                PersonalityCard(
                    personalityName = it.name,
                    emoji = it.emoji,
                    description = it.description
                )
            }
        }

        // Weekly Roast
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.insights_weekly_roast),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = weeklyRoast.ifBlank { "Your first roast arrives Monday! 🔥" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Ghost Subscriptions
        if (ghostSubs.isNotEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.insights_ghost_subscriptions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ghostSubs.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("👻 ${sub.merchant}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "₹${String.format("%.0f", sub.amount)}/mo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val totalWaste = ghostSubs.sumOf { it.amount }
                        Text(
                            "Total potential waste: ₹${String.format("%.0f", totalWaste)}/month",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Daily Tip
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.insights_daily_tip),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val tip = viewModel.getDailyTip()
                    Text("${tip.emoji} ${tip.titleKey}", fontWeight = FontWeight.SemiBold)
                    Text(tip.contentKey, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // Daily Challenge
        item {
            val challenge = viewModel.getDailyChallenge()
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.insights_daily_challenge),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${challenge.emoji} ${challenge.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(challenge.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Potential saving: ${challenge.potentialSaving}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
