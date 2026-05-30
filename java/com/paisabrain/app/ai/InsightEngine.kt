package com.paisabrain.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * InsightEngine - The brain behind Paisa Brain's smart financial insights.
 *
 * Generates contextual, witty, and actionable insights from transaction data.
 * Uses pattern detection, statistical analysis, and Indian financial benchmarks
 * to deliver personalized money intelligence.
 *
 * Features:
 * - Daily/weekly/monthly spending summaries with commentary
 * - Spending pattern detection (weekend binger, late-night orderer, etc.)
 * - Budget predictions and month-end forecasting
 * - Subscription/ghost charge detection
 * - Category comparison vs Indian benchmarks (RBI data)
 * - Merchant-specific relatable insights
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
class InsightEngine {

    // ============================================================
    // Data Models
    // ============================================================

    data class Transaction(
        val id: String,
        val amount: Double,
        val merchant: String,
        val category: SpendingCategory,
        val timestamp: LocalDateTime,
        val isCredit: Boolean = false,
        val description: String = "",
        val paymentMode: PaymentMode = PaymentMode.UPI
    )

    enum class SpendingCategory {
        FOOD_DELIVERY, GROCERIES, TRANSPORTATION, ENTERTAINMENT,
        SHOPPING, HEALTH, EDUCATION, BILLS_UTILITIES,
        SUBSCRIPTIONS, TRANSFERS, INVESTMENTS, RENT,
        FUEL, PERSONAL_CARE, TRAVEL, MISCELLANEOUS
    }

    enum class PaymentMode {
        UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING, CASH, WALLET
    }

    data class Insight(
        val id: String = generateInsightId(),
        val type: InsightType,
        val title: String,
        val description: String,
        val emoji: String,
        val severity: InsightSeverity = InsightSeverity.INFO,
        val actionable: Boolean = false,
        val actionText: String? = null,
        val relatedAmount: Double? = null,
        val generatedAt: LocalDateTime = LocalDateTime.now()
    )

    enum class InsightType {
        DAILY_SUMMARY, WEEKLY_SUMMARY, MONTHLY_SUMMARY,
        PATTERN_DETECTED, BUDGET_PREDICTION, SUBSCRIPTION_ALERT,
        GHOST_CHARGE, BENCHMARK_COMPARISON, MERCHANT_INSIGHT,
        SPENDING_SPIKE, SAVINGS_OPPORTUNITY, STREAK
    }

    enum class InsightSeverity {
        INFO, POSITIVE, WARNING, CRITICAL, FUN
    }

    data class SpendingPattern(
        val patternType: PatternType,
        val confidence: Double, // 0.0 to 1.0
        val description: String,
        val evidence: List<String>
    )

    enum class PatternType {
        WEEKEND_BINGER, LATE_NIGHT_ORDERER, SALARY_DAY_SPLURGER,
        MONDAY_BLUES_SPENDER, IMPULSE_BUYER, CONSISTENT_SPENDER,
        PAYDAY_BINGER, EMI_HEAVY
    }

    data class SubscriptionDetection(
        val merchant: String,
        val amount: Double,
        val frequency: RecurrenceFrequency,
        val lastCharged: LocalDate,
        val nextExpected: LocalDate,
        val confidence: Double,
        val isGhostCharge: Boolean = false
    )

    enum class RecurrenceFrequency {
        WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
    }

    // ============================================================
    // Indian Financial Benchmarks (RBI/NSSO Data 2023-24)
    // Average monthly spending for urban Indian household (₹)
    // ============================================================

    private object IndianBenchmarks {
        // Average monthly spending by category (urban India, ₹)
        val categoryBenchmarks = mapOf(
            SpendingCategory.FOOD_DELIVERY to 4500.0,
            SpendingCategory.GROCERIES to 8000.0,
            SpendingCategory.TRANSPORTATION to 3500.0,
            SpendingCategory.ENTERTAINMENT to 2500.0,
            SpendingCategory.SHOPPING to 5000.0,
            SpendingCategory.HEALTH to 2000.0,
            SpendingCategory.EDUCATION to 3000.0,
            SpendingCategory.BILLS_UTILITIES to 4000.0,
            SpendingCategory.SUBSCRIPTIONS to 1500.0,
            SpendingCategory.RENT to 15000.0,
            SpendingCategory.FUEL to 4000.0,
            SpendingCategory.PERSONAL_CARE to 2000.0,
            SpendingCategory.TRAVEL to 3000.0,
            SpendingCategory.MISCELLANEOUS to 2000.0
        )

        // Average savings rate for salaried Indians (%)
        const val AVG_SAVINGS_RATE = 0.28

        // Average food delivery orders per month
        const val AVG_FOOD_DELIVERY_ORDERS = 12

        // Average monthly income (urban, salaried)
        const val AVG_MONTHLY_INCOME = 55000.0
    }

    // ============================================================
    // Merchant Equivalence Map (for relatable comparisons)
    // ============================================================

    private object MerchantEquivalents {
        data class FunEquivalent(
            val unitName: String,
            val unitCost: Double,
            val emoji: String
        )

        val equivalents = mapOf(
            "food_delivery" to listOf(
                FunEquivalent("plates of dal chawal your mom makes for free", 0.0, "🍛"),
                FunEquivalent("plates of home-cooked thali", 80.0, "🍽️"),
                FunEquivalent("months of tiffin service", 3000.0, "📦")
            ),
            "food_delivery_alt" to listOf(
                FunEquivalent("Maggi packets", 14.0, "🍜"),
                FunEquivalent("homemade biryani portions", 120.0, "🍚"),
                FunEquivalent("roadside momos plates", 60.0, "🥟")
            ),
            "cab_service" to listOf(
                FunEquivalent("auto rides", 50.0, "🛺"),
                FunEquivalent("monthly bus passes", 1500.0, "🚌"),
                FunEquivalent("liters of petrol (if you had a bike)", 100.0, "⛽")
            ),
            "cab_service_alt" to listOf(
                FunEquivalent("metro rides", 30.0, "🚇"),
                FunEquivalent("shared auto rides", 20.0, "🛺"),
                FunEquivalent("monthly cycle maintenance costs", 500.0, "🚲")
            ),
            "online_shopping" to listOf(
                FunEquivalent("chai from tapri", 10.0, "☕"),
                FunEquivalent("books from Sunday book market", 50.0, "📚"),
                FunEquivalent("streaming subscriptions", 199.0, "📺")
            ),
            "online_shopping_alt" to listOf(
                FunEquivalent("vadapav", 20.0, "🥙"),
                FunEquivalent("local market bargain finds", 200.0, "🛍️"),
                FunEquivalent("FD monthly interests (at 7%)", 833.0, "🏦")
            ),
            "streaming_service" to listOf(
                FunEquivalent("cups of cutting chai", 10.0, "☕"),
                FunEquivalent("video platform premium months (with free music)", 129.0, "▶️")
            ),
            "music_service" to listOf(
                FunEquivalent("free video platform (free with ads)", 0.0, "🎵"),
                FunEquivalent("FM radio months (also free!)", 0.0, "📻")
            ),
            "premium_cafe" to listOf(
                FunEquivalent("cutting chai", 10.0, "☕"),
                FunEquivalent("filter coffee from local coffee shop", 40.0, "☕"),
                FunEquivalent("instant coffee sachets", 5.0, "☕")
            ),
            "fashion_shopping" to listOf(
                FunEquivalent("street market t-shirts", 200.0, "👕"),
                FunEquivalent("pairs of chappal from local shop", 150.0, "👟"),
                FunEquivalent("months of gym membership", 1000.0, "💪")
            ),
            "online_grocery" to listOf(
                FunEquivalent("trips to local sabzi mandi", 300.0, "🥬"),
                FunEquivalent("kg of rice from ration shop", 20.0, "🍚")
            ),
            "quick_delivery" to listOf(
                FunEquivalent("walks to the corner kirana store", 0.0, "🚶"),
                FunEquivalent("local delivery boy tips", 20.0, "🛵")
            ),
            "quick_delivery_alt" to listOf(
                FunEquivalent("kirana store visits (also 10-min if nearby)", 0.0, "🏪"),
                FunEquivalent("samosa from the shop downstairs", 15.0, "🔺")
            )
        )
    }

    // ============================================================
    // Witty Commentary Templates
    // ============================================================

    private val dailySummaryComments = mapOf(
        "low" to listOf(
            "Wallet barely noticed today. Good job, minimalist! 🧘",
            "Your bank account sends its thanks. Quiet day! 💚",
            "If every day was like today, you'd retire at 40 💰",
            "The UPI apps are wondering if you're okay 😂",
            "Your money stayed where it belongs today — with you! 🏦"
        ),
        "medium" to listOf(
            "Normal day on Planet Spending. Nothing to see here 🌍",
            "Average spending. You're the median of India right now 📊",
            "Balanced day — your CA would approve 👍",
            "Neither feast nor famine today. Steady! ⚖️",
            "Right in the middle lane. Cruise control spending 🚗"
        ),
        "high" to listOf(
            "Your wallet called. It wants a vacation 💸",
            "Heavy day! Hope it was worth it (no judgment) 🫣",
            "Spend-o-meter is in the red zone today 🔴",
            "Your money had places to go today, apparently 🏃",
            "Payday energy without the payday? 🤔"
        ),
        "extreme" to listOf(
            "Your money just filed a missing person report 🚨",
            "This isn't spending, this is a financial event 💥",
            "Alert: Wallet critically injured. Send help 🏥",
            "Did you buy the whole store or just most of it? 🏬",
            "RBI wants to know your location 📍"
        )
    )

    private val weeklySummaryComments = mapOf(
        "under_budget" to listOf(
            "Under budget this week! Your future self just high-fived you 🙌",
            "Budget game strong 💪 Keep this up and compounding will be your bestie",
            "You beat your own target. Promotion-worthy financial behavior 📈",
            "This week's spending: boss-level restraint 🎯"
        ),
        "on_track" to listOf(
            "Right on track this week. Consistency is underrated 🎯",
            "Steady as she goes, captain. Good week financially ⛵",
            "You're that person who actually sticks to budgets. Rare species! 🦄"
        ),
        "over_budget" to listOf(
            "Slightly over this week. Time to channel your inner Scrooge? 🎩",
            "Budget leaked a bit. Nothing catastrophic — yet 💧",
            "A little over, but tomorrow's another chance to be boring with money 📉"
        ),
        "way_over" to listOf(
            "This week's budget: concept. Your spending: freestyle 🎨",
            "Budget left the chat approximately Monday 💬",
            "The budget was more of a suggestion, apparently 📋"
        )
    )

    // ============================================================
    // Core Insight Generation
    // ============================================================

    /**
     * Generates all applicable insights for the given transaction set.
     * Call this daily or on-demand to refresh the user's insight feed.
     *
     * @param transactions All available transactions (sorted by timestamp desc)
     * @param monthlyIncome User's declared or detected monthly income
     * @param currentBudget Optional monthly budget target
     * @return List of insights sorted by relevance/severity
     */
    fun generateInsights(
        transactions: List<Transaction>,
        monthlyIncome: Double,
        currentBudget: Double? = null
    ): List<Insight> {
        if (transactions.isEmpty()) return emptyList()

        val insights = mutableListOf<Insight>()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Daily summary
        val todayTxns = transactions.filter { it.timestamp.toLocalDate() == today && !it.isCredit }
        if (todayTxns.isNotEmpty()) {
            insights.add(generateDailySummary(todayTxns, monthlyIncome))
        }

        // Weekly summary (if it's Sunday or Monday)
        if (today.dayOfWeek == DayOfWeek.SUNDAY || today.dayOfWeek == DayOfWeek.MONDAY) {
            val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val weekTxns = transactions.filter {
                val txDate = it.timestamp.toLocalDate()
                !it.isCredit && txDate >= weekStart && txDate <= today
            }
            if (weekTxns.isNotEmpty()) {
                insights.add(generateWeeklySummary(weekTxns, currentBudget, monthlyIncome))
            }
        }

        // Monthly insights (first 3 days of month)
        if (today.dayOfMonth <= 3) {
            val lastMonth = today.minusMonths(1)
            val lastMonthTxns = transactions.filter {
                val txDate = it.timestamp.toLocalDate()
                !it.isCredit && txDate.month == lastMonth.month && txDate.year == lastMonth.year
            }
            if (lastMonthTxns.isNotEmpty()) {
                insights.add(generateMonthlySummary(lastMonthTxns, monthlyIncome))
            }
        }

        // Spending pattern detection
        val last30Days = transactions.filter {
            !it.isCredit && it.timestamp.isAfter(now.minusDays(30))
        }
        insights.addAll(detectSpendingPatterns(last30Days))

        // Budget prediction
        val thisMonthTxns = transactions.filter {
            val txDate = it.timestamp.toLocalDate()
            !it.isCredit && txDate.month == today.month && txDate.year == today.year
        }
        if (thisMonthTxns.size >= 5) {
            val prediction = generateBudgetPrediction(thisMonthTxns, monthlyIncome, currentBudget)
            if (prediction != null) insights.add(prediction)
        }

        // Subscription detection
        val last90Days = transactions.filter {
            !it.isCredit && it.timestamp.isAfter(now.minusDays(90))
        }
        insights.addAll(detectSubscriptions(last90Days))

        // Category benchmarks
        if (last30Days.size >= 10) {
            insights.addAll(generateBenchmarkComparisons(last30Days))
        }

        // Merchant-specific insights
        insights.addAll(generateMerchantInsights(thisMonthTxns))

        // Spending spike detection
        val spikeInsight = detectSpendingSpike(transactions, today)
        if (spikeInsight != null) insights.add(spikeInsight)

        return insights.sortedByDescending { it.severity.ordinal }
    }

    // ============================================================
    // Daily Summary
    // ============================================================

    private fun generateDailySummary(
        todayTxns: List<Transaction>,
        monthlyIncome: Double
    ): Insight {
        val totalSpent = todayTxns.sumOf { it.amount }
        val txCount = todayTxns.size
        val dailyBudget = monthlyIncome / 30.0
        val percentOfDaily = (totalSpent / dailyBudget * 100).roundToInt()

        val tier = when {
            totalSpent < dailyBudget * 0.5 -> "low"
            totalSpent < dailyBudget * 1.0 -> "medium"
            totalSpent < dailyBudget * 2.0 -> "high"
            else -> "extreme"
        }

        val comment = dailySummaryComments[tier]?.random() ?: "Interesting day financially."
        val topCategory = todayTxns.groupBy { it.category }
            .maxByOrNull { it.value.sumOf { txn -> txn.amount } }?.key

        val title = "Today's Damage: ₹${formatAmount(totalSpent)}"
        val description = buildString {
            append("$txCount transaction${if (txCount > 1) "s" else ""} today")
            append(" • ${percentOfDaily}% of your daily budget")
            if (topCategory != null) {
                append(" • Mostly on ${formatCategory(topCategory)}")
            }
            append("\n\n$comment")
        }

        val severity = when (tier) {
            "low" -> InsightSeverity.POSITIVE
            "medium" -> InsightSeverity.INFO
            "high" -> InsightSeverity.WARNING
            else -> InsightSeverity.CRITICAL
        }

        return Insight(
            type = InsightType.DAILY_SUMMARY,
            title = title,
            description = description,
            emoji = when (tier) {
                "low" -> "💚"
                "medium" -> "💛"
                "high" -> "🟠"
                else -> "🔴"
            },
            severity = severity,
            relatedAmount = totalSpent
        )
    }

    // ============================================================
    // Weekly Summary
    // ============================================================

    private fun generateWeeklySummary(
        weekTxns: List<Transaction>,
        budget: Double?,
        monthlyIncome: Double
    ): Insight {
        val totalSpent = weekTxns.sumOf { it.amount }
        val weeklyBudget = budget?.div(4.33) ?: (monthlyIncome * 0.6 / 4.33)
        val ratio = totalSpent / weeklyBudget

        val tier = when {
            ratio < 0.85 -> "under_budget"
            ratio < 1.1 -> "on_track"
            ratio < 1.4 -> "over_budget"
            else -> "way_over"
        }

        val comment = weeklySummaryComments[tier]?.random() ?: ""
        val topMerchant = weekTxns.groupBy { it.merchant.lowercase() }
            .maxByOrNull { it.value.sumOf { txn -> txn.amount } }

        val title = "Week in Review: ₹${formatAmount(totalSpent)}"
        val description = buildString {
            append("${weekTxns.size} transactions this week\n")
            if (topMerchant != null) {
                val merchantTotal = topMerchant.value.sumOf { it.amount }
                append("Top merchant: ${topMerchant.key.capitalize()} (₹${formatAmount(merchantTotal)})\n")
            }
            append("\n$comment")
        }

        return Insight(
            type = InsightType.WEEKLY_SUMMARY,
            title = title,
            description = description,
            emoji = when (tier) {
                "under_budget" -> "🏆"
                "on_track" -> "✅"
                "over_budget" -> "⚠️"
                else -> "🚨"
            },
            severity = when (tier) {
                "under_budget" -> InsightSeverity.POSITIVE
                "on_track" -> InsightSeverity.INFO
                "over_budget" -> InsightSeverity.WARNING
                else -> InsightSeverity.CRITICAL
            },
            relatedAmount = totalSpent
        )
    }

    // ============================================================
    // Monthly Summary
    // ============================================================

    private fun generateMonthlySummary(
        monthTxns: List<Transaction>,
        monthlyIncome: Double
    ): Insight {
        val totalSpent = monthTxns.sumOf { it.amount }
        val savingsRate = ((monthlyIncome - totalSpent) / monthlyIncome * 100).roundToInt()
        val categoryBreakdown = monthTxns.groupBy { it.category }
            .mapValues { it.value.sumOf { txn -> txn.amount } }
            .entries.sortedByDescending { it.value }
            .take(3)

        val title = "Last Month: ₹${formatAmount(totalSpent)} spent"
        val description = buildString {
            append("Savings rate: ${savingsRate}%")
            if (savingsRate > 30) append(" 🎉") else if (savingsRate < 10) append(" 😬")
            append("\n\nTop categories:\n")
            categoryBreakdown.forEach { (cat, amount) ->
                val percent = (amount / totalSpent * 100).roundToInt()
                append("  • ${formatCategory(cat)}: ₹${formatAmount(amount)} ($percent%)\n")
            }
            if (savingsRate >= IndianBenchmarks.AVG_SAVINGS_RATE * 100) {
                append("\n✨ You saved more than the average Indian! National avg: ${(IndianBenchmarks.AVG_SAVINGS_RATE * 100).roundToInt()}%")
            } else {
                append("\n💡 India avg savings rate: ${(IndianBenchmarks.AVG_SAVINGS_RATE * 100).roundToInt()}%. You can catch up!")
            }
        }

        return Insight(
            type = InsightType.MONTHLY_SUMMARY,
            title = title,
            description = description,
            emoji = if (savingsRate > 25) "📊" else "📉",
            severity = if (savingsRate > 20) InsightSeverity.POSITIVE else InsightSeverity.WARNING,
            relatedAmount = totalSpent
        )
    }

    // ============================================================
    // Spending Pattern Detection
    // ============================================================

    /**
     * Detects behavioral spending patterns from 30-day transaction history.
     */
    fun detectSpendingPatterns(last30Days: List<Transaction>): List<Insight> {
        val insights = mutableListOf<Insight>()
        val patterns = mutableListOf<SpendingPattern>()

        // 1. Weekend Binger Detection
        val weekendTxns = last30Days.filter {
            val dow = it.timestamp.dayOfWeek
            dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
        }
        val weekdayTxns = last30Days.filter {
            val dow = it.timestamp.dayOfWeek
            dow != DayOfWeek.FRIDAY && dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
        }
        val weekendSpend = weekendTxns.sumOf { it.amount }
        val totalSpend = last30Days.sumOf { it.amount }
        val weekendRatio = if (totalSpend > 0) weekendSpend / totalSpend else 0.0

        if (weekendRatio > 0.55) { // Weekends are 3/7 days = 43%, so 55%+ is notable
            val confidence = ((weekendRatio - 0.43) / 0.57).coerceIn(0.0, 1.0)
            patterns.add(
                SpendingPattern(
                    patternType = PatternType.WEEKEND_BINGER,
                    confidence = confidence,
                    description = "You spend ${(weekendRatio * 100).roundToInt()}% of your money on weekends",
                    evidence = listOf(
                        "Weekend spending: ₹${formatAmount(weekendSpend)}",
                        "Weekday spending: ₹${formatAmount(totalSpend - weekendSpend)}"
                    )
                )
            )
            insights.add(
                Insight(
                    type = InsightType.PATTERN_DETECTED,
                    title = "Weekend Warrior Mode: ON 🎉",
                    description = "You spend ${(weekendRatio * 100).roundToInt()}% of your money Fri-Sun. " +
                            "TGIF hits different when your wallet is involved! " +
                            "Weekend avg: ₹${formatAmount(weekendSpend / 12)} vs Weekday avg: ₹${formatAmount((totalSpend - weekendSpend) / 18)}",
                    emoji = "🎉",
                    severity = InsightSeverity.FUN
                )
            )
        }

        // 2. Late Night Orderer Detection
        val lateNightTxns = last30Days.filter {
            it.timestamp.hour >= 22 || it.timestamp.hour < 4
        }
        val lateNightFoodTxns = lateNightTxns.filter {
            it.category == SpendingCategory.FOOD_DELIVERY
        }
        val allFoodTxns = last30Days.filter { it.category == SpendingCategory.FOOD_DELIVERY }
        val lateNightFoodRatio = if (allFoodTxns.isNotEmpty()) {
            lateNightFoodTxns.sumOf { it.amount } / allFoodTxns.sumOf { it.amount }
        } else 0.0

        if (lateNightFoodRatio > 0.35) {
            insights.add(
                Insight(
                    type = InsightType.PATTERN_DETECTED,
                    title = "Midnight Munchies Detected 🌙",
                    description = "${(lateNightFoodRatio * 100).roundToInt()}% of your food orders are after 10 PM. " +
                            "Your delivery guy probably knows your building better than your neighbors. " +
                            "Late-night total: ₹${formatAmount(lateNightFoodTxns.sumOf { it.amount })}",
                    emoji = "🌙",
                    severity = InsightSeverity.FUN
                )
            )
        }

        // 3. Salary Day Splurger Detection
        val salaryDayTxns = detectSalaryDaySplurge(last30Days)
        if (salaryDayTxns != null) {
            insights.add(salaryDayTxns)
        }

        // 4. Monday Blues Spender
        val mondayTxns = last30Days.filter { it.timestamp.dayOfWeek == DayOfWeek.MONDAY }
        val mondayFoodEntertainment = mondayTxns.filter {
            it.category == SpendingCategory.FOOD_DELIVERY || it.category == SpendingCategory.ENTERTAINMENT
        }
        val avgDailyFoodEnt = last30Days.filter {
            it.category == SpendingCategory.FOOD_DELIVERY || it.category == SpendingCategory.ENTERTAINMENT
        }.sumOf { it.amount } / 30.0
        val mondayAvgFoodEnt = if (mondayFoodEntertainment.isNotEmpty()) {
            mondayFoodEntertainment.sumOf { it.amount } / 4.0 // ~4 Mondays per month
        } else 0.0

        if (mondayAvgFoodEnt > avgDailyFoodEnt * 1.5) {
            insights.add(
                Insight(
                    type = InsightType.PATTERN_DETECTED,
                    title = "Monday Blues = Comfort Spending 😩",
                    description = "You spend ${((mondayAvgFoodEnt / avgDailyFoodEnt - 1) * 100).roundToInt()}% more on " +
                            "food/entertainment on Mondays. Treating the Monday blues with retail therapy?",
                    emoji = "😩",
                    severity = InsightSeverity.FUN
                )
            )
        }

        return insights
    }

    private fun detectSalaryDaySplurge(transactions: List<Transaction>): Insight? {
        // Look for credit transactions that could be salary (large, regular)
        // Then check if spending spikes in the 3 days after
        val creditTxns = transactions.filter { it.isCredit && it.amount > 20000 }
        if (creditTxns.isEmpty()) return null

        val salaryDate = creditTxns.maxByOrNull { it.amount }?.timestamp?.toLocalDate() ?: return null
        val postSalaryTxns = transactions.filter {
            !it.isCredit &&
                    it.timestamp.toLocalDate().isAfter(salaryDate) &&
                    it.timestamp.toLocalDate().isBefore(salaryDate.plusDays(4))
        }
        val postSalarySpend = postSalaryTxns.sumOf { it.amount }
        val avgDailySpend = transactions.filter { !it.isCredit }.sumOf { it.amount } / 30.0
        val postSalaryDailyAvg = postSalarySpend / 3.0

        if (postSalaryDailyAvg > avgDailySpend * 2.0) {
            return Insight(
                type = InsightType.PATTERN_DETECTED,
                title = "Salary Day Splurge Alert 💰➡️💸",
                description = "You spent ₹${formatAmount(postSalarySpend)} in 3 days after salary. " +
                        "That's ${(postSalaryDailyAvg / avgDailySpend).roundToInt()}x your daily average! " +
                        "The 'treat yourself' phase is real.",
                emoji = "💸",
                severity = InsightSeverity.WARNING,
                relatedAmount = postSalarySpend
            )
        }
        return null
    }

    // ============================================================
    // Budget Prediction
    // ============================================================

    /**
     * Predicts month-end spending based on current trajectory.
     */
    private fun generateBudgetPrediction(
        thisMonthTxns: List<Transaction>,
        monthlyIncome: Double,
        budget: Double?
    ): Insight? {
        val today = LocalDate.now()
        val daysElapsed = today.dayOfMonth
        val daysInMonth = YearMonth.from(today).lengthOfMonth()
        val daysRemaining = daysInMonth - daysElapsed

        if (daysElapsed < 5) return null // Too early to predict

        val spentSoFar = thisMonthTxns.sumOf { it.amount }
        val dailyRate = spentSoFar / daysElapsed
        val projectedTotal = spentSoFar + (dailyRate * daysRemaining)
        val targetBudget = budget ?: (monthlyIncome * 0.7) // Default: spend max 70%

        val projectedSavings = monthlyIncome - projectedTotal
        val projectedSavingsRate = (projectedSavings / monthlyIncome * 100).roundToInt()

        return if (projectedTotal > targetBudget) {
            val shortfall = projectedTotal - targetBudget
            val dailyCutNeeded = shortfall / daysRemaining

            Insight(
                type = InsightType.BUDGET_PREDICTION,
                title = "⚠️ Budget Alert: ₹${formatAmount(shortfall)} over by month end",
                description = "At this rate, you'll spend ₹${formatAmount(projectedTotal)} this month.\n" +
                        "That's ₹${formatAmount(shortfall)} over your ${if (budget != null) "budget" else "ideal limit"}.\n\n" +
                        "💡 Cut ₹${formatAmount(dailyCutNeeded)}/day for the next $daysRemaining days to stay on track.\n" +
                        "Projected savings: ${projectedSavingsRate}%",
                emoji = "⚠️",
                severity = InsightSeverity.WARNING,
                actionable = true,
                actionText = "Set daily limit: ₹${formatAmount(dailyCutNeeded)}",
                relatedAmount = shortfall
            )
        } else {
            val surplus = targetBudget - projectedTotal
            Insight(
                type = InsightType.BUDGET_PREDICTION,
                title = "On Track! ₹${formatAmount(surplus)} cushion remaining 🎯",
                description = "Projected month-end spend: ₹${formatAmount(projectedTotal)}\n" +
                        "You'll likely save ${projectedSavingsRate}% this month. " +
                        "Keep it up! 💪",
                emoji = "🎯",
                severity = InsightSeverity.POSITIVE,
                relatedAmount = surplus
            )
        }
    }

    // ============================================================
    // Subscription & Ghost Charge Detection
    // ============================================================

    /**
     * Detects recurring subscriptions and potential ghost charges.
     * A ghost charge is a subscription you might have forgotten about.
     */
    fun detectSubscriptions(transactions: List<Transaction>): List<Insight> {
        val insights = mutableListOf<Insight>()

        // Group by merchant + approximate amount (within 10% tolerance)
        val merchantGroups = transactions
            .filter { !it.isCredit }
            .groupBy { it.merchant.lowercase().trim() }

        val detectedSubs = mutableListOf<SubscriptionDetection>()

        for ((merchant, txns) in merchantGroups) {
            if (txns.size < 2) continue

            // Check for recurring same-amount charges
            val amountGroups = groupByApproximateAmount(txns)

            for ((_, sameTxns) in amountGroups) {
                if (sameTxns.size < 2) continue

                val sortedDates = sameTxns.map { it.timestamp.toLocalDate() }.sorted()
                val intervals = sortedDates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }

                if (intervals.isEmpty()) continue

                val avgInterval = intervals.average()
                val intervalVariance = intervals.map { (it - avgInterval) * (it - avgInterval) }.average()

                // Low variance = consistent interval = likely subscription
                if (intervalVariance < 25.0) { // ~5 days variance allowed
                    val frequency = when {
                        avgInterval in 5.0..9.0 -> RecurrenceFrequency.WEEKLY
                        avgInterval in 12.0..18.0 -> RecurrenceFrequency.BIWEEKLY
                        avgInterval in 25.0..35.0 -> RecurrenceFrequency.MONTHLY
                        avgInterval in 85.0..100.0 -> RecurrenceFrequency.QUARTERLY
                        else -> continue
                    }

                    val amount = sameTxns.map { it.amount }.average()
                    val lastDate = sortedDates.last()
                    val nextExpected = lastDate.plusDays(avgInterval.toLong())
                    val daysSinceLast = ChronoUnit.DAYS.between(lastDate, LocalDate.now())

                    // Ghost charge: subscription detected but user hasn't interacted with service
                    // Heuristic: if it's a lesser-known service or very old subscription
                    val isGhost = daysSinceLast > avgInterval * 1.5 ||
                            (frequency == RecurrenceFrequency.MONTHLY && sameTxns.size >= 3)

                    detectedSubs.add(
                        SubscriptionDetection(
                            merchant = merchant,
                            amount = amount,
                            frequency = frequency,
                            lastCharged = lastDate,
                            nextExpected = nextExpected,
                            confidence = (1.0 - intervalVariance / 25.0).coerceIn(0.5, 1.0),
                            isGhostCharge = isGhost
                        )
                    )
                }
            }
        }

        // Generate insights for detected subscriptions
        if (detectedSubs.isNotEmpty()) {
            val totalMonthly = detectedSubs
                .filter { it.frequency == RecurrenceFrequency.MONTHLY }
                .sumOf { it.amount }

            insights.add(
                Insight(
                    type = InsightType.SUBSCRIPTION_ALERT,
                    title = "${detectedSubs.size} Subscriptions Detected (₹${formatAmount(totalMonthly)}/mo)",
                    description = buildString {
                        append("Recurring charges found:\n")
                        detectedSubs.take(5).forEach { sub ->
                            append("  • ${sub.merchant.capitalize()}: ₹${formatAmount(sub.amount)}/${sub.frequency.name.lowercase()}\n")
                        }
                        if (detectedSubs.size > 5) {
                            append("  ...and ${detectedSubs.size - 5} more\n")
                        }
                        append("\n💡 That's ₹${formatAmount(totalMonthly * 12)}/year on subscriptions alone!")
                    },
                    emoji = "🔄",
                    severity = InsightSeverity.INFO,
                    actionable = true,
                    actionText = "Review subscriptions",
                    relatedAmount = totalMonthly
                )
            )

            // Ghost charges
            val ghosts = detectedSubs.filter { it.isGhostCharge }
            if (ghosts.isNotEmpty()) {
                insights.add(
                    Insight(
                        type = InsightType.GHOST_CHARGE,
                        title = "👻 ${ghosts.size} Possible Ghost Charges",
                        description = "These subscriptions might be draining money silently:\n" +
                                ghosts.joinToString("\n") { "  • ${it.merchant.capitalize()}: ₹${formatAmount(it.amount)}/mo" } +
                                "\n\n🤔 Do you still use these? Could save ₹${formatAmount(ghosts.sumOf { it.amount })}/month!",
                        emoji = "👻",
                        severity = InsightSeverity.WARNING,
                        actionable = true,
                        actionText = "Check ghost charges",
                        relatedAmount = ghosts.sumOf { it.amount }
                    )
                )
            }
        }

        return insights
    }

    private fun groupByApproximateAmount(
        transactions: List<Transaction>,
        tolerance: Double = 0.1
    ): Map<Double, List<Transaction>> {
        val groups = mutableMapOf<Double, MutableList<Transaction>>()

        for (txn in transactions) {
            val matchingKey = groups.keys.firstOrNull { key ->
                abs(txn.amount - key) / key < tolerance
            }
            if (matchingKey != null) {
                groups[matchingKey]!!.add(txn)
            } else {
                groups[txn.amount] = mutableListOf(txn)
            }
        }

        return groups
    }

    // ============================================================
    // Benchmark Comparisons
    // ============================================================

    /**
     * Compares user's spending against Indian averages (RBI/NSSO data).
     */
    private fun generateBenchmarkComparisons(last30Days: List<Transaction>): List<Insight> {
        val insights = mutableListOf<Insight>()

        val categorySpend = last30Days.groupBy { it.category }
            .mapValues { it.value.sumOf { txn -> txn.amount } }

        for ((category, amount) in categorySpend) {
            val benchmark = IndianBenchmarks.categoryBenchmarks[category] ?: continue
            val ratio = amount / benchmark

            // Only generate insight if significantly different (>50% above or below)
            when {
                ratio > 2.0 -> {
                    insights.add(
                        Insight(
                            type = InsightType.BENCHMARK_COMPARISON,
                            title = "${formatCategory(category)}: ${ratio.roundToInt()}x Indian Average! 📊",
                            description = "You spent ₹${formatAmount(amount)} on ${formatCategory(category).lowercase()} this month.\n" +
                                    "Indian urban average: ₹${formatAmount(benchmark)}\n" +
                                    "You're spending ${ratio.roundToInt()}x the national average in this category.\n\n" +
                                    "Not necessarily bad — but worth a look! 🔍",
                            emoji = "📊",
                            severity = InsightSeverity.WARNING,
                            relatedAmount = amount - benchmark
                        )
                    )
                }

                ratio < 0.4 -> {
                    insights.add(
                        Insight(
                            type = InsightType.BENCHMARK_COMPARISON,
                            title = "${formatCategory(category)}: Below Average 💚",
                            description = "You spent only ₹${formatAmount(amount)} on ${formatCategory(category).lowercase()}.\n" +
                                    "Indian urban average: ₹${formatAmount(benchmark)}\n" +
                                    "You're saving ₹${formatAmount(benchmark - amount)} vs the average Indian here! 🎉",
                            emoji = "💚",
                            severity = InsightSeverity.POSITIVE,
                            relatedAmount = benchmark - amount
                        )
                    )
                }
            }
        }

        return insights.take(3) // Don't overwhelm with benchmarks
    }

    // ============================================================
    // Merchant-Specific Insights
    // ============================================================

    /**
     * Generates fun, relatable merchant-specific insights.
     * "You spent ₹X on food delivery = Y plates of dal chawal your mom makes"
     */
    private fun generateMerchantInsights(thisMonthTxns: List<Transaction>): List<Insight> {
        val insights = mutableListOf<Insight>()

        val merchantSpend = thisMonthTxns
            .filter { !it.isCredit }
            .groupBy { it.merchant.lowercase().trim() }
            .mapValues { entry ->
                entry.value.sumOf { it.amount } to entry.value.size
            }

        for ((merchant, pair) in merchantSpend) {
            val (amount, count) = pair
            val equivalents = MerchantEquivalents.equivalents[merchant] ?: continue

            if (amount < 200) continue // Not worth mentioning

            val equiv = equivalents.random()
            val equivalentCount = if (equiv.unitCost > 0) {
                (amount / equiv.unitCost).roundToInt()
            } else {
                count // Use transaction count for "free" equivalents
            }

            val description = if (equiv.unitCost > 0) {
                "You spent ₹${formatAmount(amount)} on ${merchant.capitalize()} this month ($count orders).\n" +
                        "That's ${equivalentCount} ${equiv.unitName} ${equiv.emoji}\n\n" +
                        "Just saying... no judgment here! 🫠"
            } else {
                "You spent ₹${formatAmount(amount)} on ${merchant.capitalize()} this month ($count orders).\n" +
                        "That's $count ${equiv.unitName} ${equiv.emoji}\n\n" +
                        "Your mom called. She wants to cook for you. 📞"
            }

            insights.add(
                Insight(
                    type = InsightType.MERCHANT_INSIGHT,
                    title = "${merchant.capitalize()} Bill: ₹${formatAmount(amount)} ${equiv.emoji}",
                    description = description,
                    emoji = equiv.emoji,
                    severity = InsightSeverity.FUN,
                    relatedAmount = amount
                )
            )
        }

        return insights.take(3)
    }

    // ============================================================
    // Spending Spike Detection
    // ============================================================

    private fun detectSpendingSpike(transactions: List<Transaction>, today: LocalDate): Insight? {
        val last7Days = transactions.filter {
            !it.isCredit && it.timestamp.toLocalDate().isAfter(today.minusDays(7))
        }
        val prev7Days = transactions.filter {
            !it.isCredit &&
                    it.timestamp.toLocalDate().isAfter(today.minusDays(14)) &&
                    it.timestamp.toLocalDate().isBefore(today.minusDays(6))
        }

        if (prev7Days.isEmpty()) return null

        val recentTotal = last7Days.sumOf { it.amount }
        val prevTotal = prev7Days.sumOf { it.amount }
        val spikeRatio = if (prevTotal > 0) recentTotal / prevTotal else 1.0

        if (spikeRatio > 1.8) {
            val increase = recentTotal - prevTotal
            return Insight(
                type = InsightType.SPENDING_SPIKE,
                title = "📈 Spending Spike: ${((spikeRatio - 1) * 100).roundToInt()}% up this week",
                description = "You spent ₹${formatAmount(increase)} more this week compared to last week.\n" +
                        "This week: ₹${formatAmount(recentTotal)} vs Last week: ₹${formatAmount(prevTotal)}\n\n" +
                        "Any special occasion, or did the spending gremlins visit? 🧐",
                emoji = "📈",
                severity = InsightSeverity.WARNING,
                relatedAmount = increase
            )
        }

        return null
    }

    // ============================================================
    // Utility Functions
    // ============================================================

    private fun formatAmount(amount: Double): String {
        return when {
            amount >= 100000 -> "${(amount / 100000).let { "%.1f".format(it) }}L"
            amount >= 1000 -> "${(amount / 1000).let { "%.1f".format(it) }}K"
            else -> "%.0f".format(amount)
        }
    }

    private fun formatCategory(category: SpendingCategory): String {
        return category.name.split("_").joinToString(" ") { it.lowercase().capitalize() }
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    companion object {
        private var insightCounter = 0L

        private fun generateInsightId(): String {
            return "insight_${System.currentTimeMillis()}_${++insightCounter}"
        }
    }
}
