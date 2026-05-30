package com.paisabrain.app.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * # CashFlowForecast
 *
 * An intelligent cash flow forecasting engine that predicts future account balances
 * by analyzing historical transaction patterns. Detects recurring income and expenses,
 * identifies "danger zones" where balances might dip critically low, and generates
 * day-by-day projections.
 *
 * ## Features
 * - Predicts balance for next 7/14/30/60/90 days
 * - Detects recurring income (salary, freelance, rental income, etc.)
 * - Detects recurring expenses (rent, EMIs, subscriptions, utilities)
 * - Shows "danger zones" — days where balance might dip dangerously low
 * - Plots future cash flow curve
 * - Considers upcoming detected bills
 *
 * ## How It Works
 * 1. Analyzes 3-6 months of SMS transaction history
 * 2. Identifies recurring patterns using frequency & amount matching
 * 3. Projects these patterns forward into the future
 * 4. Applies variance adjustments based on historical fluctuations
 * 5. Flags dates where projected balance falls below the user's safety threshold
 *
 * @since 1.0.0
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single day's forecast in the cash flow projection.
 *
 * @property date The date this forecast is for
 * @property predictedBalance Expected balance at end of day
 * @property predictedIncome Total expected income on this day
 * @property predictedExpenses Total expected expenses on this day
 * @property isPayday Whether a salary/primary income is expected
 * @property isDangerZone Whether balance is predicted to be dangerously low
 * @property confidencePercent Confidence in this prediction (decreases further out)
 */
data class DayForecast(
    val date: LocalDate,
    val predictedBalance: Double,
    val predictedIncome: Double,
    val predictedExpenses: Double,
    val isPayday: Boolean,
    val isDangerZone: Boolean,
    val confidencePercent: Double = 100.0
)

/**
 * A date range representing a danger zone period.
 *
 * @property startDate First day of the danger zone
 * @property endDate Last day of the danger zone
 * @property lowestBalance Lowest predicted balance during this period
 * @property daysUntilStart Days from today until this danger zone begins
 */
data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lowestBalance: Double,
    val daysUntilStart: Long = ChronoUnit.DAYS.between(LocalDate.now(), startDate)
)

/**
 * The complete cash flow projection — the primary output of the forecasting engine.
 *
 * @property dayByDayForecast List of daily forecasts for the projection period
 * @property dangerZones Consecutive date ranges where balance is critically low
 * @property nextIncomeDate Next expected date of primary income (salary)
 * @property predictedMonthEndBalance Predicted balance at end of current month
 * @property lowestPoint The lowest balance predicted in the forecast period
 * @property lowestPointDate The date of the lowest predicted balance
 * @property totalPredictedIncome Total income expected in forecast period
 * @property totalPredictedExpenses Total expenses expected in forecast period
 * @property forecastDays Number of days in this forecast
 */
data class CashFlowProjection(
    val dayByDayForecast: List<DayForecast>,
    val dangerZones: List<DateRange>,
    val nextIncomeDate: LocalDate?,
    val predictedMonthEndBalance: Double,
    val lowestPoint: Double,
    val lowestPointDate: LocalDate?,
    val totalPredictedIncome: Double,
    val totalPredictedExpenses: Double,
    val forecastDays: Int
)

/**
 * Represents a detected recurring transaction pattern.
 *
 * @property id Unique identifier
 * @property name Descriptive name (e.g., "Monthly Salary", "Rent Payment")
 * @property amount Average transaction amount
 * @property amountVariance How much the amount typically varies (std deviation)
 * @property dayOfMonth Usual day of month (1-31), null if weekly
 * @property dayOfWeek Day of week if weekly recurrence
 * @property frequency How often this recurs
 * @property isIncome Whether this is income (true) or expense (false)
 * @property lastOccurrence Last date this transaction occurred
 * @property confidence Confidence score 0.0-1.0 that this is truly recurring
 * @property category Transaction category
 */
@Entity(tableName = "recurring_patterns")
data class RecurringPattern(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "amount_variance") val amountVariance: Double = 0.0,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: DayOfWeek? = null,
    @ColumnInfo(name = "frequency") val frequency: RecurrenceFrequency,
    @ColumnInfo(name = "is_income") val isIncome: Boolean,
    @ColumnInfo(name = "last_occurrence") val lastOccurrence: LocalDate,
    @ColumnInfo(name = "confidence") val confidence: Double = 0.8,
    @ColumnInfo(name = "category") val category: String = "General"
)

/**
 * How often a recurring transaction occurs.
 */
enum class RecurrenceFrequency(val displayName: String, val approximateDays: Int) {
    WEEKLY("Weekly", 7),
    BIWEEKLY("Bi-weekly", 14),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 90),
    SEMI_ANNUAL("Semi-Annual", 180),
    ANNUAL("Annual", 365)
}

/**
 * Represents a historical transaction used for pattern detection.
 */
data class HistoricalTransaction(
    val date: LocalDate,
    val amount: Double,
    val isCredit: Boolean,
    val merchant: String,
    val category: String = "General"
)

/**
 * User-configurable settings for the forecast engine.
 *
 * @property currentBalance Current account balance as starting point
 * @property dangerThreshold Balance below which is considered "dangerous"
 * @property safetyBuffer Additional buffer the user wants to maintain
 * @property includeWeekendAdjustments Whether to shift weekend transactions to weekdays
 */
data class ForecastSettings(
    val currentBalance: Double,
    val dangerThreshold: Double = 5000.0,
    val safetyBuffer: Double = 10000.0,
    val includeWeekendAdjustments: Boolean = true
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data Access Object for recurring pattern storage and retrieval.
 */
@Dao
interface CashFlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: RecurringPattern)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<RecurringPattern>)

    @Update
    suspend fun updatePattern(pattern: RecurringPattern)

    @Delete
    suspend fun deletePattern(pattern: RecurringPattern)

    @Query("SELECT * FROM recurring_patterns WHERE is_income = 1 ORDER BY amount DESC")
    fun getRecurringIncome(): Flow<List<RecurringPattern>>

    @Query("SELECT * FROM recurring_patterns WHERE is_income = 0 ORDER BY amount DESC")
    fun getRecurringExpenses(): Flow<List<RecurringPattern>>

    @Query("SELECT * FROM recurring_patterns ORDER BY confidence DESC")
    fun getAllPatterns(): Flow<List<RecurringPattern>>

    @Query("SELECT * FROM recurring_patterns WHERE confidence >= :minConfidence")
    suspend fun getHighConfidencePatterns(minConfidence: Double = 0.7): List<RecurringPattern>

    @Query("DELETE FROM recurring_patterns")
    suspend fun clearAllPatterns()
}

// ─────────────────────────────────────────────────────────────────────────────
// Cash Flow Forecast Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The core cash flow forecasting engine.
 *
 * Analyzes historical transaction patterns to project future balances and identify
 * potential cash crunches before they happen.
 *
 * ## Usage
 * ```kotlin
 * val engine = CashFlowForecastEngine(cashFlowDao)
 *
 * // Detect patterns from transaction history
 * engine.analyzeTransactionHistory(transactions)
 *
 * // Generate 30-day forecast
 * val projection = engine.generateForecast(
 *     settings = ForecastSettings(currentBalance = 45000.0),
 *     days = 30
 * )
 *
 * // Check danger zones
 * projection.dangerZones.forEach { zone ->
 *     println("⚠️ Low balance expected ${zone.startDate} to ${zone.endDate}")
 * }
 * ```
 *
 * @param dao The Room DAO for persisting detected patterns
 */
class CashFlowForecastEngine(private val dao: CashFlowDao) {

    companion object {
        /** Minimum number of occurrences to consider a pattern recurring */
        private const val MIN_OCCURRENCES = 3

        /** Maximum day variance to group transactions as same recurring item */
        private const val MAX_DAY_VARIANCE = 3

        /** Maximum amount variance (percentage) to group as same transaction */
        private const val MAX_AMOUNT_VARIANCE_PERCENT = 0.15

        /** Confidence decay per week into the future */
        private const val CONFIDENCE_DECAY_PER_WEEK = 0.05

        /** Default danger threshold in INR */
        private const val DEFAULT_DANGER_THRESHOLD = 5000.0
    }

    // ── Pattern Detection ────────────────────────────────────────────────────

    /**
     * Analyzes historical transactions to detect recurring patterns.
     *
     * Groups transactions by merchant/description, then checks for monthly/weekly
     * recurrence. Patterns with sufficient confidence are stored for forecasting.
     *
     * @param transactions List of historical transactions (ideally 3-6 months)
     * @return List of detected recurring patterns
     */
    suspend fun analyzeTransactionHistory(
        transactions: List<HistoricalTransaction>
    ): List<RecurringPattern> {
        val detectedPatterns = mutableListOf<RecurringPattern>()

        // Group transactions by merchant/category
        val grouped = transactions.groupBy { it.merchant.lowercase().trim() }

        for ((merchant, txns) in grouped) {
            if (txns.size < MIN_OCCURRENCES) continue

            // Check for monthly recurrence
            val monthlyPattern = detectMonthlyPattern(merchant, txns)
            if (monthlyPattern != null) {
                detectedPatterns.add(monthlyPattern)
                continue
            }

            // Check for weekly recurrence
            val weeklyPattern = detectWeeklyPattern(merchant, txns)
            if (weeklyPattern != null) {
                detectedPatterns.add(weeklyPattern)
            }
        }

        // Persist detected patterns
        dao.insertPatterns(detectedPatterns)

        return detectedPatterns
    }

    /**
     * Detects if a set of transactions follows a monthly pattern.
     *
     * @param merchant The merchant name
     * @param transactions Transactions for this merchant
     * @return RecurringPattern if monthly recurrence detected, null otherwise
     */
    private fun detectMonthlyPattern(
        merchant: String,
        transactions: List<HistoricalTransaction>
    ): RecurringPattern? {
        val sortedByDate = transactions.sortedBy { it.date }

        // Check if transactions occur roughly monthly
        val dayOfMonthValues = sortedByDate.map { it.date.dayOfMonth }
        val avgDay = dayOfMonthValues.average()
        val dayVariance = dayOfMonthValues.map { abs(it - avgDay) }.average()

        if (dayVariance > MAX_DAY_VARIANCE) return null

        // Check amount consistency
        val amounts = sortedByDate.map { it.amount }
        val avgAmount = amounts.average()
        val amountVariance = amounts.map { abs(it - avgAmount) / avgAmount }.average()

        if (amountVariance > MAX_AMOUNT_VARIANCE_PERCENT) return null

        // Check gaps between transactions are roughly monthly (25-35 days)
        val gaps = sortedByDate.zipWithNext().map { (a, b) ->
            ChronoUnit.DAYS.between(a.date, b.date)
        }
        val avgGap = gaps.average()
        if (avgGap < 25 || avgGap > 35) return null

        // Calculate confidence based on consistency and sample size
        val confidence = calculateConfidence(
            occurrences = transactions.size,
            dayVariance = dayVariance,
            amountVariance = amountVariance
        )

        return RecurringPattern(
            id = "pattern_monthly_${merchant.hashCode()}",
            name = merchant.capitalizeWords(),
            amount = avgAmount,
            amountVariance = amountVariance * avgAmount,
            dayOfMonth = avgDay.roundToInt(),
            frequency = RecurrenceFrequency.MONTHLY,
            isIncome = transactions.first().isCredit,
            lastOccurrence = sortedByDate.last().date,
            confidence = confidence,
            category = transactions.first().category
        )
    }

    /**
     * Detects if a set of transactions follows a weekly pattern.
     */
    private fun detectWeeklyPattern(
        merchant: String,
        transactions: List<HistoricalTransaction>
    ): RecurringPattern? {
        val sortedByDate = transactions.sortedBy { it.date }

        // Check if same day of week
        val daysOfWeek = sortedByDate.map { it.date.dayOfWeek }
        val mostCommonDay = daysOfWeek.groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: return null

        val onSameDay = daysOfWeek.count { it == mostCommonDay }
        if (onSameDay.toDouble() / daysOfWeek.size < 0.7) return null

        // Check gaps are roughly weekly (5-9 days)
        val gaps = sortedByDate.zipWithNext().map { (a, b) ->
            ChronoUnit.DAYS.between(a.date, b.date)
        }
        val avgGap = gaps.average()
        if (avgGap < 5 || avgGap > 9) return null

        val amounts = sortedByDate.map { it.amount }
        val avgAmount = amounts.average()

        return RecurringPattern(
            id = "pattern_weekly_${merchant.hashCode()}",
            name = merchant.capitalizeWords(),
            amount = avgAmount,
            amountVariance = amounts.standardDeviation(),
            dayOfWeek = mostCommonDay,
            frequency = RecurrenceFrequency.WEEKLY,
            isIncome = transactions.first().isCredit,
            lastOccurrence = sortedByDate.last().date,
            confidence = 0.75,
            category = transactions.first().category
        )
    }

    // ── Forecast Generation ──────────────────────────────────────────────────

    /**
     * Generates a cash flow projection for the specified number of days.
     *
     * @param settings User's forecast settings (current balance, thresholds)
     * @param days Number of days to forecast (7, 14, 30, 60, or 90)
     * @return Complete [CashFlowProjection] with day-by-day forecasts and danger zones
     */
    suspend fun generateForecast(
        settings: ForecastSettings,
        days: Int = 30
    ): CashFlowProjection {
        val patterns = dao.getHighConfidencePatterns()
        val today = LocalDate.now()
        val forecasts = mutableListOf<DayForecast>()
        var runningBalance = settings.currentBalance

        // Generate day-by-day forecast
        for (dayOffset in 1..days) {
            val forecastDate = today.plusDays(dayOffset.toLong())
            val predictedIncome = predictIncomeForDate(forecastDate, patterns)
            val predictedExpenses = predictExpensesForDate(forecastDate, patterns)

            runningBalance += predictedIncome - predictedExpenses

            val isPayday = isPayday(forecastDate, patterns)
            val isDangerZone = runningBalance < settings.dangerThreshold

            // Confidence decreases the further out we go
            val weeksOut = dayOffset / 7.0
            val confidence = (100.0 - (weeksOut * CONFIDENCE_DECAY_PER_WEEK * 100))
                .coerceIn(50.0, 100.0)

            forecasts.add(
                DayForecast(
                    date = forecastDate,
                    predictedBalance = runningBalance.roundTo2(),
                    predictedIncome = predictedIncome.roundTo2(),
                    predictedExpenses = predictedExpenses.roundTo2(),
                    isPayday = isPayday,
                    isDangerZone = isDangerZone,
                    confidencePercent = confidence
                )
            )
        }

        // Identify danger zones (consecutive days below threshold)
        val dangerZones = identifyDangerZones(forecasts, settings.dangerThreshold)

        // Find lowest point
        val lowestForecast = forecasts.minByOrNull { it.predictedBalance }

        // Find next income date
        val nextIncomeDate = forecasts.firstOrNull { it.isPayday }?.date

        // Predict month-end balance
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        val monthEndForecast = forecasts.firstOrNull { it.date == monthEnd }

        return CashFlowProjection(
            dayByDayForecast = forecasts,
            dangerZones = dangerZones,
            nextIncomeDate = nextIncomeDate,
            predictedMonthEndBalance = monthEndForecast?.predictedBalance ?: runningBalance,
            lowestPoint = lowestForecast?.predictedBalance ?: runningBalance,
            lowestPointDate = lowestForecast?.date,
            totalPredictedIncome = forecasts.sumOf { it.predictedIncome },
            totalPredictedExpenses = forecasts.sumOf { it.predictedExpenses },
            forecastDays = days
        )
    }

    /**
     * Quick check: will the user run out of money in the next N days?
     *
     * @param settings Forecast settings
     * @param days Days to check ahead
     * @return True if balance is predicted to go below danger threshold
     */
    suspend fun willRunLow(settings: ForecastSettings, days: Int = 30): Boolean {
        val projection = generateForecast(settings, days)
        return projection.dangerZones.isNotEmpty()
    }

    /**
     * Gets the predicted balance for a specific future date.
     *
     * @param targetDate The date to predict balance for
     * @param settings Forecast settings
     * @return Predicted balance, or null if date is too far in the future
     */
    suspend fun predictBalanceOnDate(
        targetDate: LocalDate,
        settings: ForecastSettings
    ): Double? {
        val daysAhead = ChronoUnit.DAYS.between(LocalDate.now(), targetDate).toInt()
        if (daysAhead <= 0 || daysAhead > 90) return null

        val projection = generateForecast(settings, daysAhead)
        return projection.dayByDayForecast.lastOrNull()?.predictedBalance
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Predicts total income expected on a given date based on recurring patterns.
     */
    private fun predictIncomeForDate(
        date: LocalDate,
        patterns: List<RecurringPattern>
    ): Double {
        return patterns
            .filter { it.isIncome && isPatternDueOnDate(it, date) }
            .sumOf { it.amount }
    }

    /**
     * Predicts total expenses expected on a given date based on recurring patterns.
     */
    private fun predictExpensesForDate(
        date: LocalDate,
        patterns: List<RecurringPattern>
    ): Double {
        return patterns
            .filter { !it.isIncome && isPatternDueOnDate(it, date) }
            .sumOf { it.amount }
    }

    /**
     * Determines if a recurring pattern is expected to occur on the given date.
     */
    private fun isPatternDueOnDate(pattern: RecurringPattern, date: LocalDate): Boolean {
        return when (pattern.frequency) {
            RecurrenceFrequency.MONTHLY -> {
                val expectedDay = pattern.dayOfMonth ?: return false
                // Handle months with fewer days
                val adjustedDay = expectedDay.coerceAtMost(date.lengthOfMonth())
                date.dayOfMonth == adjustedDay
            }
            RecurrenceFrequency.WEEKLY -> {
                pattern.dayOfWeek == date.dayOfWeek
            }
            RecurrenceFrequency.BIWEEKLY -> {
                if (pattern.dayOfWeek != date.dayOfWeek) return false
                val weeksSinceLast = ChronoUnit.WEEKS.between(pattern.lastOccurrence, date)
                weeksSinceLast % 2 == 0L
            }
            RecurrenceFrequency.QUARTERLY -> {
                val monthsSinceLast = ChronoUnit.MONTHS.between(
                    pattern.lastOccurrence.withDayOfMonth(1),
                    date.withDayOfMonth(1)
                )
                monthsSinceLast % 3 == 0L && date.dayOfMonth == (pattern.dayOfMonth ?: 1)
            }
            RecurrenceFrequency.SEMI_ANNUAL -> {
                val monthsSinceLast = ChronoUnit.MONTHS.between(
                    pattern.lastOccurrence.withDayOfMonth(1),
                    date.withDayOfMonth(1)
                )
                monthsSinceLast % 6 == 0L && date.dayOfMonth == (pattern.dayOfMonth ?: 1)
            }
            RecurrenceFrequency.ANNUAL -> {
                date.monthValue == pattern.lastOccurrence.monthValue &&
                        date.dayOfMonth == (pattern.dayOfMonth ?: pattern.lastOccurrence.dayOfMonth)
            }
        }
    }

    /**
     * Checks if a date is a payday (primary income expected).
     */
    private fun isPayday(date: LocalDate, patterns: List<RecurringPattern>): Boolean {
        // Consider the largest recurring income as the primary salary
        val primaryIncome = patterns
            .filter { it.isIncome && it.frequency == RecurrenceFrequency.MONTHLY }
            .maxByOrNull { it.amount } ?: return false

        return isPatternDueOnDate(primaryIncome, date)
    }

    /**
     * Identifies consecutive danger zone periods from the forecast.
     */
    private fun identifyDangerZones(
        forecasts: List<DayForecast>,
        threshold: Double
    ): List<DateRange> {
        val dangerZones = mutableListOf<DateRange>()
        var zoneStart: LocalDate? = null
        var lowestInZone = Double.MAX_VALUE

        for (forecast in forecasts) {
            if (forecast.isDangerZone) {
                if (zoneStart == null) {
                    zoneStart = forecast.date
                }
                if (forecast.predictedBalance < lowestInZone) {
                    lowestInZone = forecast.predictedBalance
                }
            } else {
                if (zoneStart != null) {
                    dangerZones.add(
                        DateRange(
                            startDate = zoneStart,
                            endDate = forecast.date.minusDays(1),
                            lowestBalance = lowestInZone
                        )
                    )
                    zoneStart = null
                    lowestInZone = Double.MAX_VALUE
                }
            }
        }

        // Close any open danger zone at end of forecast
        if (zoneStart != null) {
            dangerZones.add(
                DateRange(
                    startDate = zoneStart,
                    endDate = forecasts.last().date,
                    lowestBalance = lowestInZone
                )
            )
        }

        return dangerZones
    }

    /**
     * Calculates confidence score for a detected pattern.
     */
    private fun calculateConfidence(
        occurrences: Int,
        dayVariance: Double,
        amountVariance: Double
    ): Double {
        var confidence = 0.5

        // More occurrences = higher confidence
        confidence += (occurrences.coerceAtMost(12) / 12.0) * 0.3

        // Lower day variance = higher confidence
        confidence += ((MAX_DAY_VARIANCE - dayVariance) / MAX_DAY_VARIANCE) * 0.1

        // Lower amount variance = higher confidence
        confidence += ((MAX_AMOUNT_VARIANCE_PERCENT - amountVariance) / MAX_AMOUNT_VARIANCE_PERCENT) * 0.1

        return confidence.coerceIn(0.0, 1.0)
    }

    // ── Extension Functions ──────────────────────────────────────────────────

    private fun Double.roundTo2(): Double = (this * 100.0).roundToInt() / 100.0

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0
        val mean = average()
        val variance = map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}
