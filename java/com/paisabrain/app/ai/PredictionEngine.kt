package com.paisabrain.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PredictionEngine - Financial forecasting for Paisa Brain.
 *
 * Predicts month-end balance, spending trajectory, and upcoming bills
 * using statistical methods:
 * - Simple Linear Regression for trend analysis
 * - Exponential Moving Averages (EMA) for smoothed predictions
 * - Seasonal decomposition for recurring patterns
 * - Confidence intervals for prediction reliability
 *
 * No TensorFlow/ML frameworks required — pure statistical inference
 * optimized for mobile performance.
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
class PredictionEngine {

    // ============================================================
    // Data Models
    // ============================================================

    data class Transaction(
        val amount: Double,
        val merchant: String,
        val category: String,
        val timestamp: LocalDateTime,
        val isCredit: Boolean = false,
        val isRecurring: Boolean = false
    )

    /**
     * Month-end financial prediction with confidence intervals.
     */
    data class MonthEndPrediction(
        val predictedTotalSpend: Double,
        val predictedBalance: Double,
        val predictedSavings: Double,
        val predictedSavingsRate: Double, // 0.0 - 1.0
        val confidenceInterval: ConfidenceInterval,
        val spendingTrajectory: SpendingTrajectory,
        val dailyBurnRate: Double,
        val daysUntilBudgetExhausted: Int?, // null if won't be exhausted
        val riskLevel: RiskLevel,
        val dailyPredictions: List<DailyPrediction>,
        val generatedAt: LocalDateTime = LocalDateTime.now()
    )

    data class ConfidenceInterval(
        val lower: Double,
        val upper: Double,
        val confidenceLevel: Double = 0.90 // 90% CI
    )

    enum class SpendingTrajectory {
        ACCELERATING,  // Spending is increasing over time
        STEADY,        // Consistent daily spending
        DECELERATING,  // Spending is decreasing
        ERRATIC        // High variance, unpredictable
    }

    enum class RiskLevel {
        LOW,      // On track for good savings
        MODERATE, // Might slightly exceed budget
        HIGH,     // Likely to exceed budget
        CRITICAL  // Already over or far over trajectory
    }

    data class DailyPrediction(
        val date: LocalDate,
        val predictedSpend: Double,
        val cumulativeSpend: Double,
        val isActual: Boolean = false // true if this is real data, false if predicted
    )

    /**
     * Upcoming bill prediction based on historical patterns.
     */
    data class BillPrediction(
        val merchant: String,
        val predictedAmount: Double,
        val expectedDate: LocalDate,
        val confidence: Double, // 0.0 - 1.0
        val category: String,
        val isRecurring: Boolean,
        val variability: BillVariability,
        val historicalAmounts: List<Double>,
        val trend: BillTrend
    )

    enum class BillVariability {
        FIXED,     // Same amount every time (subscriptions)
        LOW,       // Small variations (utilities)
        MODERATE,  // Some variation (grocery patterns)
        HIGH       // Significant variation (discretionary)
    }

    enum class BillTrend {
        INCREASING,  // Amount growing over time
        STABLE,      // Consistent amounts
        DECREASING,  // Amount shrinking
        SEASONAL     // Varies by season (e.g., electricity in summer)
    }

    /**
     * Spending velocity analysis.
     */
    data class SpendingVelocity(
        val currentDailyRate: Double,    // Current burn rate (EMA-smoothed)
        val averageDailyRate: Double,    // Historical average
        val velocityChange: Double,       // % change in velocity vs last week
        val projectedMonthEnd: Double,   // Where we'll land
        val requiredDailyRate: Double,   // What we should spend to meet budget
        val burnRateRatio: Double        // current / required (>1 = overspending)
    )

    // ============================================================
    // Statistical Utilities
    // ============================================================

    /**
     * Simple Linear Regression (OLS).
     * Returns slope (m) and intercept (b) for y = mx + b.
     */
    private data class LinearRegression(
        val slope: Double,
        val intercept: Double,
        val rSquared: Double, // Goodness of fit (0-1)
        val standardError: Double
    )

    private fun linearRegression(xValues: List<Double>, yValues: List<Double>): LinearRegression {
        require(xValues.size == yValues.size && xValues.size >= 2) {
            "Need at least 2 data points for regression"
        }

        val n = xValues.size
        val sumX = xValues.sum()
        val sumY = yValues.sum()
        val sumXY = xValues.zip(yValues).sumOf { (x, y) -> x * y }
        val sumXX = xValues.sumOf { it * it }
        val sumYY = yValues.sumOf { it * it }

        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0.0) {
            return LinearRegression(0.0, sumY / n, 0.0, 0.0)
        }

        val slope = (n * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / n

        // R-squared
        val yMean = sumY / n
        val ssTotal = yValues.sumOf { (it - yMean).pow(2) }
        val ssResidual = xValues.zip(yValues).sumOf { (x, y) ->
            (y - (slope * x + intercept)).pow(2)
        }
        val rSquared = if (ssTotal > 0) 1.0 - (ssResidual / ssTotal) else 0.0

        // Standard error
        val standardError = if (n > 2) sqrt(ssResidual / (n - 2)) else 0.0

        return LinearRegression(slope, intercept, rSquared.coerceIn(0.0, 1.0), standardError)
    }

    /**
     * Exponential Moving Average (EMA) calculation.
     * More weight on recent values for responsive predictions.
     *
     * @param values Data points in chronological order
     * @param alpha Smoothing factor (0-1). Higher = more weight on recent. Default: 0.3
     */
    private fun exponentialMovingAverage(values: List<Double>, alpha: Double = 0.3): List<Double> {
        if (values.isEmpty()) return emptyList()

        val ema = mutableListOf(values.first())
        for (i in 1 until values.size) {
            ema.add(alpha * values[i] + (1 - alpha) * ema[i - 1])
        }
        return ema
    }

    /**
     * Simple Moving Average over a window.
     */
    private fun simpleMovingAverage(values: List<Double>, window: Int): List<Double> {
        if (values.size < window) return listOf(values.average())
        return values.windowed(window) { it.average() }
    }

    /**
     * Calculates standard deviation.
     */
    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    /**
     * Weighted moving average (more recent data = more weight).
     */
    private fun weightedMovingAverage(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val totalWeight = (1..values.size).sum().toDouble()
        return values.mapIndexed { index, value ->
            value * (index + 1) / totalWeight
        }.sum()
    }

    // ============================================================
    // Month-End Prediction
    // ============================================================

    /**
     * Predicts the month-end financial position based on current spending trajectory.
     *
     * Uses a combination of:
     * 1. Linear regression on daily cumulative spending
     * 2. EMA for recent trend detection
     * 3. Day-of-week seasonality adjustment
     * 4. Historical same-month comparison (if available)
     *
     * @param transactions All transactions (at least current month needed)
     * @param monthlyIncome User's monthly income
     * @param monthlyBudget Optional budget target
     * @param historicalMonthlySpends Previous months' total spending (for seasonality)
     * @return MonthEndPrediction with all forecast details
     */
    fun predictMonthEnd(
        transactions: List<Transaction>,
        monthlyIncome: Double,
        monthlyBudget: Double? = null,
        historicalMonthlySpends: List<Double> = emptyList()
    ): MonthEndPrediction {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val daysInMonth = currentMonth.lengthOfMonth()
        val dayOfMonth = today.dayOfMonth
        val daysRemaining = daysInMonth - dayOfMonth

        // Get current month's debit transactions
        val currentMonthTxns = transactions.filter {
            !it.isCredit &&
                    it.timestamp.toLocalDate().month == today.month &&
                    it.timestamp.toLocalDate().year == today.year
        }.sortedBy { it.timestamp }

        if (currentMonthTxns.isEmpty()) {
            return getDefaultPrediction(monthlyIncome, monthlyBudget, daysInMonth)
        }

        // Calculate daily totals
        val dailyTotals = (1..dayOfMonth).map { day ->
            val date = today.withDayOfMonth(day)
            currentMonthTxns.filter { it.timestamp.toLocalDate() == date }.sumOf { it.amount }
        }

        // Method 1: Linear Regression on cumulative spending
        val cumulativeSpend = dailyTotals.runningReduce { acc, d -> acc + d }
        val xValues = (1..cumulativeSpend.size).map { it.toDouble() }
        val regression = linearRegression(xValues, cumulativeSpend)
        val regressionPrediction = regression.slope * daysInMonth + regression.intercept

        // Method 2: EMA-based daily rate projection
        val recentEma = exponentialMovingAverage(dailyTotals, alpha = 0.4)
        val currentDailyRate = recentEma.lastOrNull() ?: dailyTotals.average()
        val emaPrediction = cumulativeSpend.last() + (currentDailyRate * daysRemaining)

        // Method 3: Day-of-week seasonality adjustment
        val dowAdjustment = calculateDayOfWeekAdjustment(transactions, daysRemaining, today)
        val seasonalPrediction = cumulativeSpend.last() + dowAdjustment

        // Method 4: Historical average (if available)
        val historicalPrediction = if (historicalMonthlySpends.isNotEmpty()) {
            val historicalAvg = historicalMonthlySpends.average()
            val currentRatio = cumulativeSpend.last() / (historicalAvg * dayOfMonth / daysInMonth)
            historicalAvg * currentRatio
        } else null

        // Combine predictions with weights
        val weights = mutableListOf(0.35, 0.30, 0.20) // regression, EMA, seasonal
        val predictions = mutableListOf(regressionPrediction, emaPrediction, seasonalPrediction)

        if (historicalPrediction != null) {
            weights.add(0.15)
            predictions.add(historicalPrediction)
            // Renormalize weights
            val totalWeight = weights.sum()
            for (i in weights.indices) weights[i] /= totalWeight
        }

        val predictedTotal = predictions.zip(weights).sumOf { (pred, weight) -> pred * weight }
            .coerceAtLeast(cumulativeSpend.last()) // Can't predict less than already spent

        // Confidence Interval (based on variance and regression fit)
        val dailyStdDev = standardDeviation(dailyTotals)
        val predictionStdDev = dailyStdDev * sqrt(daysRemaining.toDouble())
        val confidenceInterval = ConfidenceInterval(
            lower = (predictedTotal - 1.645 * predictionStdDev).coerceAtLeast(cumulativeSpend.last()),
            upper = predictedTotal + 1.645 * predictionStdDev,
            confidenceLevel = 0.90
        )

        // Spending trajectory
        val trajectory = determineTrajectory(dailyTotals, regression)

        // Risk level
        val budget = monthlyBudget ?: (monthlyIncome * 0.7)
        val riskLevel = when {
            predictedTotal > budget * 1.3 -> RiskLevel.CRITICAL
            predictedTotal > budget * 1.1 -> RiskLevel.HIGH
            predictedTotal > budget * 0.95 -> RiskLevel.MODERATE
            else -> RiskLevel.LOW
        }

        // Days until budget exhausted
        val remainingBudget = budget - cumulativeSpend.last()
        val daysUntilExhausted = if (currentDailyRate > 0 && remainingBudget > 0) {
            (remainingBudget / currentDailyRate).roundToInt().let {
                if (it < daysRemaining) it else null
            }
        } else if (remainingBudget <= 0) 0 else null

        // Generate daily predictions for chart
        val dailyPredictions = generateDailyPredictions(
            dailyTotals, cumulativeSpend, daysInMonth, today, currentDailyRate, regression
        )

        val predictedSavings = monthlyIncome - predictedTotal
        val predictedSavingsRate = (predictedSavings / monthlyIncome).coerceIn(-0.5, 1.0)

        return MonthEndPrediction(
            predictedTotalSpend = predictedTotal,
            predictedBalance = predictedSavings,
            predictedSavings = predictedSavings.coerceAtLeast(0.0),
            predictedSavingsRate = predictedSavingsRate,
            confidenceInterval = confidenceInterval,
            spendingTrajectory = trajectory,
            dailyBurnRate = currentDailyRate,
            daysUntilBudgetExhausted = daysUntilExhausted,
            riskLevel = riskLevel,
            dailyPredictions = dailyPredictions
        )
    }

    /**
     * Calculates day-of-week spending adjustment for remaining days.
     * Weekends typically have different spending patterns.
     */
    private fun calculateDayOfWeekAdjustment(
        transactions: List<Transaction>,
        daysRemaining: Int,
        today: LocalDate
    ): Double {
        // Calculate average spending by day of week from historical data
        val last60Days = transactions.filter {
            !it.isCredit && it.timestamp.toLocalDate().isAfter(today.minusDays(60))
        }

        if (last60Days.size < 14) {
            return last60Days.sumOf { it.amount } / last60Days.map { it.timestamp.toLocalDate() }.distinct().size.coerceAtLeast(1) * daysRemaining
        }

        val dowAverages = last60Days.groupBy { it.timestamp.dayOfWeek }
            .mapValues { entry ->
                val days = entry.value.map { it.timestamp.toLocalDate() }.distinct().size.coerceAtLeast(1)
                entry.value.sumOf { it.amount } / days
            }

        // Sum expected spending for remaining days based on their day-of-week
        var predictedRemaining = 0.0
        for (i in 1..daysRemaining) {
            val futureDate = today.plusDays(i.toLong())
            val dow = futureDate.dayOfWeek
            predictedRemaining += dowAverages[dow] ?: dowAverages.values.average()
        }

        return predictedRemaining
    }

    private fun determineTrajectory(
        dailyTotals: List<Double>,
        regression: LinearRegression
    ): SpendingTrajectory {
        if (dailyTotals.size < 5) return SpendingTrajectory.STEADY

        val cv = standardDeviation(dailyTotals) / dailyTotals.average()

        return when {
            cv > 1.0 -> SpendingTrajectory.ERRATIC
            regression.slope > dailyTotals.average() * 0.05 -> SpendingTrajectory.ACCELERATING
            regression.slope < -dailyTotals.average() * 0.05 -> SpendingTrajectory.DECELERATING
            else -> SpendingTrajectory.STEADY
        }
    }

    private fun generateDailyPredictions(
        dailyTotals: List<Double>,
        cumulativeSpend: List<Double>,
        daysInMonth: Int,
        today: LocalDate,
        dailyRate: Double,
        regression: LinearRegression
    ): List<DailyPrediction> {
        val predictions = mutableListOf<DailyPrediction>()

        // Actual days
        for (i in dailyTotals.indices) {
            predictions.add(
                DailyPrediction(
                    date = today.withDayOfMonth(i + 1),
                    predictedSpend = dailyTotals[i],
                    cumulativeSpend = cumulativeSpend[i],
                    isActual = true
                )
            )
        }

        // Predicted days
        var cumulative = cumulativeSpend.last()
        for (day in (today.dayOfMonth + 1)..daysInMonth) {
            // Blend regression prediction with EMA
            val regressionDaily = regression.slope
            val blendedDaily = (regressionDaily * 0.4 + dailyRate * 0.6).coerceAtLeast(0.0)
            cumulative += blendedDaily

            predictions.add(
                DailyPrediction(
                    date = today.withDayOfMonth(day),
                    predictedSpend = blendedDaily,
                    cumulativeSpend = cumulative,
                    isActual = false
                )
            )
        }

        return predictions
    }

    private fun getDefaultPrediction(
        monthlyIncome: Double,
        budget: Double?,
        daysInMonth: Int
    ): MonthEndPrediction {
        val defaultBudget = budget ?: (monthlyIncome * 0.7)
        return MonthEndPrediction(
            predictedTotalSpend = defaultBudget,
            predictedBalance = monthlyIncome - defaultBudget,
            predictedSavings = monthlyIncome - defaultBudget,
            predictedSavingsRate = 1.0 - defaultBudget / monthlyIncome,
            confidenceInterval = ConfidenceInterval(defaultBudget * 0.7, defaultBudget * 1.3),
            spendingTrajectory = SpendingTrajectory.STEADY,
            dailyBurnRate = defaultBudget / daysInMonth,
            daysUntilBudgetExhausted = null,
            riskLevel = RiskLevel.LOW,
            dailyPredictions = emptyList()
        )
    }

    // ============================================================
    // Upcoming Bills Prediction
    // ============================================================

    /**
     * Predicts upcoming bills and recurring charges based on historical patterns.
     *
     * Algorithm:
     * 1. Groups transactions by merchant
     * 2. Detects recurring patterns (monthly, weekly, quarterly)
     * 3. Predicts next occurrence date and amount
     * 4. Estimates amount trend (increasing/decreasing/stable)
     *
     * @param transactions Historical transactions (90+ days recommended)
     * @param lookAheadDays How many days ahead to predict (default: 30)
     * @return List of predicted upcoming bills sorted by date
     */
    fun predictUpcomingBills(
        transactions: List<Transaction>,
        lookAheadDays: Int = 30
    ): List<BillPrediction> {
        val today = LocalDate.now()
        val debitTxns = transactions.filter { !it.isCredit }

        // Group by merchant
        val merchantGroups = debitTxns.groupBy { it.merchant.lowercase().trim() }
        val predictions = mutableListOf<BillPrediction>()

        for ((merchant, txns) in merchantGroups) {
            if (txns.size < 2) continue

            val sortedTxns = txns.sortedBy { it.timestamp }
            val amounts = sortedTxns.map { it.amount }
            val dates = sortedTxns.map { it.timestamp.toLocalDate() }

            // Calculate intervals between transactions
            val intervals = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
            if (intervals.isEmpty()) continue

            val avgInterval = intervals.average()
            val intervalStdDev = standardDeviation(intervals.map { it.toDouble() })

            // Determine if this is a recurring bill
            val isRecurring = isLikelyRecurring(intervals, amounts)
            if (!isRecurring) continue

            // Predict next date
            val lastDate = dates.last()
            val predictedInterval = weightedMovingAverage(intervals.map { it.toDouble() })
            val nextDate = lastDate.plusDays(predictedInterval.toLong())

            // Only include if within look-ahead window
            if (nextDate.isAfter(today.plusDays(lookAheadDays.toLong()))) continue
            if (nextDate.isBefore(today)) {
                // Might have already been charged — skip or flag as overdue
                if (ChronoUnit.DAYS.between(nextDate, today) > 5) continue
            }

            // Predict amount
            val predictedAmount = predictBillAmount(amounts)
            val amountVariability = classifyVariability(amounts)
            val trend = classifyTrend(amounts)

            // Confidence based on consistency
            val confidence = calculateBillConfidence(intervals, amounts)

            predictions.add(
                BillPrediction(
                    merchant = merchant,
                    predictedAmount = predictedAmount,
                    expectedDate = if (nextDate.isBefore(today)) today.plusDays(1) else nextDate,
                    confidence = confidence,
                    category = sortedTxns.last().category,
                    isRecurring = true,
                    variability = amountVariability,
                    historicalAmounts = amounts.takeLast(6),
                    trend = trend
                )
            )
        }

        return predictions.sortedBy { it.expectedDate }
    }

    /**
     * Determines if a series of transactions is likely a recurring bill.
     */
    private fun isLikelyRecurring(intervals: List<Long>, amounts: List<Double>): Boolean {
        if (intervals.size < 2) return false

        val avgInterval = intervals.average()
        val intervalCv = standardDeviation(intervals.map { it.toDouble() }) / avgInterval

        // Check if intervals are consistent (CV < 0.3 = consistent)
        val hasConsistentInterval = intervalCv < 0.35

        // Check if amounts are somewhat consistent
        val amountMean = amounts.average()
        val amountCv = standardDeviation(amounts) / amountMean
        val hasConsistentAmount = amountCv < 0.25

        // Check if interval matches known patterns (weekly, biweekly, monthly, quarterly)
        val matchesKnownPattern = avgInterval in 5.0..10.0 ||  // weekly
                avgInterval in 12.0..18.0 || // biweekly
                avgInterval in 25.0..35.0 || // monthly
                avgInterval in 85.0..100.0   // quarterly

        return hasConsistentInterval && (hasConsistentAmount || matchesKnownPattern)
    }

    /**
     * Predicts the next bill amount using weighted recent history.
     */
    private fun predictBillAmount(amounts: List<Double>): Double {
        if (amounts.size <= 2) return amounts.last()

        // Use EMA for prediction (more weight on recent amounts)
        val ema = exponentialMovingAverage(amounts, alpha = 0.4)
        val emaPrediction = ema.last()

        // Also consider linear trend
        val xValues = amounts.indices.map { it.toDouble() }
        val regression = linearRegression(xValues, amounts)
        val regressionPrediction = regression.slope * amounts.size + regression.intercept

        // Blend: if R² is high, trust regression more; otherwise trust EMA
        val regressionWeight = regression.rSquared * 0.4
        val emaWeight = 1.0 - regressionWeight

        return (emaPrediction * emaWeight + regressionPrediction * regressionWeight)
            .coerceAtLeast(0.0)
    }

    private fun classifyVariability(amounts: List<Double>): BillVariability {
        if (amounts.size < 2) return BillVariability.FIXED
        val cv = standardDeviation(amounts) / amounts.average()
        return when {
            cv < 0.02 -> BillVariability.FIXED
            cv < 0.10 -> BillVariability.LOW
            cv < 0.30 -> BillVariability.MODERATE
            else -> BillVariability.HIGH
        }
    }

    private fun classifyTrend(amounts: List<Double>): BillTrend {
        if (amounts.size < 3) return BillTrend.STABLE

        val xValues = amounts.indices.map { it.toDouble() }
        val regression = linearRegression(xValues, amounts)
        val percentChange = regression.slope / amounts.average()

        return when {
            percentChange > 0.03 -> BillTrend.INCREASING
            percentChange < -0.03 -> BillTrend.DECREASING
            else -> BillTrend.STABLE
        }
    }

    private fun calculateBillConfidence(intervals: List<Long>, amounts: List<Double>): Double {
        val intervalConsistency = 1.0 - (standardDeviation(intervals.map { it.toDouble() }) /
                intervals.average()).coerceIn(0.0, 1.0)
        val amountConsistency = 1.0 - (standardDeviation(amounts) /
                amounts.average()).coerceIn(0.0, 1.0)
        val sampleSizeBonus = (intervals.size.toDouble() / 6.0).coerceIn(0.0, 0.2)

        return ((intervalConsistency * 0.5 + amountConsistency * 0.3 + sampleSizeBonus) * 100)
            .roundToInt() / 100.0
    }

    // ============================================================
    // Spending Velocity Analysis
    // ============================================================

    /**
     * Analyzes current spending velocity (burn rate) vs where it should be.
     *
     * @param transactions Current month's transactions
     * @param monthlyBudget Target budget
     * @param monthlyIncome Monthly income
     * @return SpendingVelocity analysis
     */
    fun analyzeSpendingVelocity(
        transactions: List<Transaction>,
        monthlyBudget: Double,
        monthlyIncome: Double
    ): SpendingVelocity {
        val today = LocalDate.now()
        val daysInMonth = YearMonth.from(today).lengthOfMonth()
        val dayOfMonth = today.dayOfMonth
        val daysRemaining = daysInMonth - dayOfMonth

        val currentMonthDebits = transactions.filter {
            !it.isCredit &&
                    it.timestamp.toLocalDate().month == today.month &&
                    it.timestamp.toLocalDate().year == today.year
        }

        // Daily totals for the month so far
        val dailyTotals = (1..dayOfMonth).map { day ->
            val date = today.withDayOfMonth(day)
            currentMonthDebits.filter { it.timestamp.toLocalDate() == date }.sumOf { it.amount }
        }

        if (dailyTotals.isEmpty()) {
            return SpendingVelocity(
                currentDailyRate = 0.0,
                averageDailyRate = monthlyBudget / daysInMonth,
                velocityChange = 0.0,
                projectedMonthEnd = 0.0,
                requiredDailyRate = monthlyBudget / daysInMonth,
                burnRateRatio = 0.0
            )
        }

        // Current daily rate (EMA of last 7 days or available)
        val recentDays = dailyTotals.takeLast(7)
        val ema = exponentialMovingAverage(recentDays, alpha = 0.4)
        val currentRate = ema.lastOrNull() ?: dailyTotals.average()

        // Average daily rate
        val averageRate = dailyTotals.average()

        // Velocity change (compare last 7 days to previous 7 days)
        val velocityChange = if (dailyTotals.size >= 14) {
            val recent7 = dailyTotals.takeLast(7).average()
            val prev7 = dailyTotals.dropLast(7).takeLast(7).average()
            if (prev7 > 0) ((recent7 - prev7) / prev7 * 100) else 0.0
        } else 0.0

        // Projected month-end
        val spentSoFar = dailyTotals.sum()
        val projected = spentSoFar + (currentRate * daysRemaining)

        // Required rate to stay within budget
        val remainingBudget = (monthlyBudget - spentSoFar).coerceAtLeast(0.0)
        val requiredRate = if (daysRemaining > 0) remainingBudget / daysRemaining else 0.0

        // Burn rate ratio
        val burnRatio = if (requiredRate > 0) currentRate / requiredRate else
            if (currentRate > 0) Double.MAX_VALUE else 0.0

        return SpendingVelocity(
            currentDailyRate = currentRate,
            averageDailyRate = averageRate,
            velocityChange = velocityChange,
            projectedMonthEnd = projected,
            requiredDailyRate = requiredRate,
            burnRateRatio = burnRatio
        )
    }

    // ============================================================
    // Category-Level Predictions
    // ============================================================

    /**
     * Predicts spending by category for the remainder of the month.
     */
    fun predictCategorySpending(
        transactions: List<Transaction>,
        historicalTransactions: List<Transaction> = emptyList()
    ): Map<String, CategoryPrediction> {
        val today = LocalDate.now()
        val dayOfMonth = today.dayOfMonth
        val daysInMonth = YearMonth.from(today).lengthOfMonth()
        val daysRemaining = daysInMonth - dayOfMonth

        val currentMonthDebits = transactions.filter {
            !it.isCredit &&
                    it.timestamp.toLocalDate().month == today.month &&
                    it.timestamp.toLocalDate().year == today.year
        }

        val categoryGroups = currentMonthDebits.groupBy { it.category.lowercase() }
        val predictions = mutableMapOf<String, CategoryPrediction>()

        for ((category, txns) in categoryGroups) {
            val spentSoFar = txns.sumOf { it.amount }
            val dailyTotals = (1..dayOfMonth).map { day ->
                val date = today.withDayOfMonth(day)
                txns.filter { it.timestamp.toLocalDate() == date }.sumOf { it.amount }
            }

            val dailyRate = if (dailyTotals.isNotEmpty()) {
                val ema = exponentialMovingAverage(dailyTotals, 0.3)
                ema.lastOrNull() ?: dailyTotals.average()
            } else 0.0

            val predicted = spentSoFar + (dailyRate * daysRemaining)

            // Compare with historical if available
            val historicalMonthly = historicalTransactions.filter {
                !it.isCredit && it.category.lowercase() == category
            }.groupBy { YearMonth.from(it.timestamp.toLocalDate()) }
                .mapValues { it.value.sumOf { txn -> txn.amount } }
                .values.toList()

            val historicalAvg = if (historicalMonthly.isNotEmpty()) historicalMonthly.average() else null

            predictions[category] = CategoryPrediction(
                category = category,
                spentSoFar = spentSoFar,
                predictedTotal = predicted,
                dailyRate = dailyRate,
                historicalAverage = historicalAvg,
                vsHistorical = if (historicalAvg != null && historicalAvg > 0) {
                    ((predicted - historicalAvg) / historicalAvg * 100).roundToInt()
                } else null
            )
        }

        return predictions
    }

    data class CategoryPrediction(
        val category: String,
        val spentSoFar: Double,
        val predictedTotal: Double,
        val dailyRate: Double,
        val historicalAverage: Double?,
        val vsHistorical: Int? // % difference vs historical average
    )

    // ============================================================
    // Salary Day Detection
    // ============================================================

    /**
     * Detects the likely salary day from transaction history.
     * Looks for consistent large credit transactions.
     *
     * @param transactions All transactions including credits
     * @return Detected salary day (1-31) or null
     */
    fun detectSalaryDay(transactions: List<Transaction>): Int? {
        val credits = transactions.filter { it.isCredit && it.amount > 10000 }
        if (credits.size < 2) return null

        // Group credits by day of month
        val dayGroups = credits.groupBy { it.timestamp.dayOfMonth }
        val mostCommonDay = dayGroups.maxByOrNull { it.value.size }

        if (mostCommonDay != null && mostCommonDay.value.size >= 2) {
            // Verify amounts are consistent (likely same source)
            val amounts = mostCommonDay.value.map { it.amount }
            val cv = standardDeviation(amounts) / amounts.average()
            if (cv < 0.3) { // Consistent amount = likely salary
                return mostCommonDay.key
            }
        }

        return null
    }

    /**
     * Predicts next salary date and amount.
     */
    fun predictNextSalary(transactions: List<Transaction>): SalaryPrediction? {
        val salaryDay = detectSalaryDay(transactions) ?: return null

        val salaryTxns = transactions.filter {
            it.isCredit && it.timestamp.dayOfMonth == salaryDay && it.amount > 10000
        }.sortedBy { it.timestamp }

        if (salaryTxns.size < 2) return null

        val amounts = salaryTxns.map { it.amount }
        val predictedAmount = weightedMovingAverage(amounts)

        val lastSalaryDate = salaryTxns.last().timestamp.toLocalDate()
        val today = LocalDate.now()
        val nextSalaryDate = if (today.dayOfMonth >= salaryDay) {
            today.plusMonths(1).withDayOfMonth(salaryDay.coerceAtMost(
                YearMonth.from(today.plusMonths(1)).lengthOfMonth()
            ))
        } else {
            today.withDayOfMonth(salaryDay.coerceAtMost(
                YearMonth.from(today).lengthOfMonth()
            ))
        }

        val daysUntilSalary = ChronoUnit.DAYS.between(today, nextSalaryDate).toInt()

        return SalaryPrediction(
            expectedDate = nextSalaryDate,
            predictedAmount = predictedAmount,
            daysUntilSalary = daysUntilSalary,
            historicalAmounts = amounts.takeLast(6)
        )
    }

    data class SalaryPrediction(
        val expectedDate: LocalDate,
        val predictedAmount: Double,
        val daysUntilSalary: Int,
        val historicalAmounts: List<Double>
    )

    // ============================================================
    // Financial Health Score Prediction
    // ============================================================

    /**
     * Predicts the user's financial health score trend.
     * Score is 0-100 based on savings rate, spending consistency, and bill management.
     */
    fun predictFinancialHealthTrend(
        transactions: List<Transaction>,
        monthlyIncome: Double,
        monthlyBudget: Double? = null
    ): HealthScorePrediction {
        val today = LocalDate.now()

        // Calculate monthly scores for last 3 months
        val monthlyScores = (-2..0).map { monthOffset ->
            val month = YearMonth.from(today.plusMonths(monthOffset.toLong()))
            val monthTxns = transactions.filter {
                val txMonth = YearMonth.from(it.timestamp.toLocalDate())
                txMonth == month && !it.isCredit
            }
            val monthSpend = monthTxns.sumOf { it.amount }
            calculateHealthScore(monthSpend, monthlyIncome, monthlyBudget, monthTxns)
        }

        // Predict next month's score
        val trend = if (monthlyScores.size >= 2) {
            val xValues = monthlyScores.indices.map { it.toDouble() }
            val yValues = monthlyScores.map { it.toDouble() }
            val regression = linearRegression(xValues, yValues)
            regression.slope * monthlyScores.size + regression.intercept
        } else monthlyScores.lastOrNull()?.toDouble() ?: 50.0

        val predictedScore = trend.roundToInt().coerceIn(0, 100)
        val currentScore = monthlyScores.lastOrNull() ?: 50

        return HealthScorePrediction(
            currentScore = currentScore,
            predictedNextMonth = predictedScore,
            trend = when {
                predictedScore > currentScore + 5 -> ScoreTrend.IMPROVING
                predictedScore < currentScore - 5 -> ScoreTrend.DECLINING
                else -> ScoreTrend.STABLE
            },
            monthlyScores = monthlyScores
        )
    }

    private fun calculateHealthScore(
        monthSpend: Double,
        income: Double,
        budget: Double?,
        transactions: List<Transaction>
    ): Int {
        var score = 50 // Base score

        // Savings rate component (0-30 points)
        val savingsRate = (income - monthSpend) / income
        score += (savingsRate * 100).roundToInt().coerceIn(-15, 30)

        // Budget adherence (0-20 points)
        if (budget != null && budget > 0) {
            val budgetRatio = monthSpend / budget
            score += when {
                budgetRatio <= 0.9 -> 20
                budgetRatio <= 1.0 -> 15
                budgetRatio <= 1.1 -> 5
                budgetRatio <= 1.3 -> -5
                else -> -15
            }
        }

        // Spending consistency (0-10 points)
        if (transactions.isNotEmpty()) {
            val dailyAmounts = transactions.groupBy { it.timestamp.toLocalDate() }
                .mapValues { it.value.sumOf { txn -> txn.amount } }
                .values.toList()
            if (dailyAmounts.size >= 5) {
                val cv = standardDeviation(dailyAmounts) / dailyAmounts.average()
                score += when {
                    cv < 0.3 -> 10
                    cv < 0.5 -> 5
                    cv < 1.0 -> 0
                    else -> -5
                }
            }
        }

        return score.coerceIn(0, 100)
    }

    data class HealthScorePrediction(
        val currentScore: Int,
        val predictedNextMonth: Int,
        val trend: ScoreTrend,
        val monthlyScores: List<Int>
    )

    enum class ScoreTrend {
        IMPROVING, STABLE, DECLINING
    }
}
