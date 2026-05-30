package com.paisabrain.app.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * # DebtPayoffPlanner
 *
 * A comprehensive debt payoff calculator that implements both the Snowball and Avalanche
 * strategies to help users become debt-free. Generates detailed payment schedules,
 * calculates interest savings, and provides motivational milestone tracking.
 *
 * ## Strategies
 * - **Snowball** (smallest balance first): Psychologically motivating — quick wins build momentum
 * - **Avalanche** (highest interest first): Mathematically optimal — minimizes total interest paid
 *
 * ## Features
 * - Monthly payment schedule generation
 * - Total interest calculation for each strategy
 * - Debt-free date projection
 * - Money saved vs. minimum payments only
 * - "Extra payment" simulator
 * - Progress tracking over time
 * - Motivational milestones
 *
 * ## No Brand Names
 * All debts use generic names: "Loan 1", "Card 1", etc.
 *
 * @since 1.0.0
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Debt payoff strategy options.
 */
enum class PayoffStrategy(val displayName: String, val description: String) {
    SNOWBALL(
        "Snowball",
        "Pay smallest balance first. Quick wins build motivation."
    ),
    AVALANCHE(
        "Avalanche",
        "Pay highest interest rate first. Saves the most money."
    )
}

/**
 * Represents a single debt the user is tracking.
 *
 * @property id Unique identifier
 * @property name User-given name (e.g., "Personal Loan", "Card 1")
 * @property originalBalance The original loan/debt amount
 * @property currentBalance Current outstanding balance
 * @property interestRate Annual interest rate (percentage, e.g., 15.0 for 15%)
 * @property minimumPayment Minimum monthly payment required
 * @property category Type of debt
 * @property startDate When this debt was taken
 * @property isActive Whether this debt is still active
 */
@Entity(tableName = "debts")
data class DebtEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "original_balance") val originalBalance: Double,
    @ColumnInfo(name = "current_balance") val currentBalance: Double,
    @ColumnInfo(name = "interest_rate") val interestRate: Double,
    @ColumnInfo(name = "minimum_payment") val minimumPayment: Double,
    @ColumnInfo(name = "category") val category: DebtCategory = DebtCategory.OTHER,
    @ColumnInfo(name = "start_date") val startDate: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "is_active") val isActive: Boolean = true
)

/**
 * Categories of debt.
 */
enum class DebtCategory(val displayName: String) {
    HOME_LOAN("Home Loan"),
    PERSONAL_LOAN("Personal Loan"),
    VEHICLE_LOAN("Vehicle Loan"),
    EDUCATION_LOAN("Education Loan"),
    CREDIT_CARD("Credit Card"),
    GOLD_LOAN("Gold Loan"),
    BUSINESS_LOAN("Business Loan"),
    OTHER("Other Loan")
}

/**
 * A single month's payment detail in the payoff schedule.
 *
 * @property month The month this payment is for
 * @property debtName Which debt this payment is applied to
 * @property payment Total payment amount for this debt this month
 * @property principal Portion going to principal
 * @property interest Portion going to interest
 * @property remainingBalance Balance remaining after this payment
 * @property isExtraPayment Whether this includes extra payment beyond minimum
 */
data class MonthlyPaymentDetail(
    val month: YearMonth,
    val debtName: String,
    val payment: Double,
    val principal: Double,
    val interest: Double,
    val remainingBalance: Double,
    val isExtraPayment: Boolean = false
)

/**
 * Complete payoff plan for a single strategy.
 *
 * @property strategy Which strategy this plan uses
 * @property schedule Full monthly payment schedule
 * @property totalInterestPaid Total interest paid over the life of all debts
 * @property debtFreeDate Projected date when all debts are paid off
 * @property totalMonths Total months to become debt-free
 * @property totalPaid Total amount paid (principal + interest)
 * @property interestSavedVsMinimum Interest saved compared to paying only minimums
 * @property monthsSavedVsMinimum Months saved compared to paying only minimums
 * @property debtPayoffOrder Order in which debts are targeted
 */
data class PayoffPlan(
    val strategy: PayoffStrategy,
    val schedule: List<MonthlyPaymentDetail>,
    val totalInterestPaid: Double,
    val debtFreeDate: YearMonth,
    val totalMonths: Int,
    val totalPaid: Double,
    val interestSavedVsMinimum: Double,
    val monthsSavedVsMinimum: Int,
    val debtPayoffOrder: List<String>
)

/**
 * Comparison between Snowball and Avalanche strategies.
 *
 * @property snowballPlan The Snowball strategy plan
 * @property avalanchePlan The Avalanche strategy plan
 * @property interestDifference How much more interest Snowball costs vs Avalanche
 * @property timeDifferenceMonths How many months difference between strategies
 * @property recommendedStrategy Which strategy is recommended (Avalanche saves money)
 * @property recommendation Human-readable recommendation
 */
data class StrategyComparison(
    val snowballPlan: PayoffPlan,
    val avalanchePlan: PayoffPlan,
    val interestDifference: Double,
    val timeDifferenceMonths: Int,
    val recommendedStrategy: PayoffStrategy,
    val recommendation: String
)

/**
 * Result of simulating extra payments.
 *
 * @property extraMonthlyAmount Extra amount being paid per month
 * @property originalDebtFreeDate Without extra payments
 * @property newDebtFreeDate With extra payments
 * @property monthsSaved Months of debt eliminated
 * @property interestSaved Total interest saved
 * @property totalSaved Total money saved (interest)
 */
data class ExtraPaymentSimulation(
    val extraMonthlyAmount: Double,
    val originalDebtFreeDate: YearMonth,
    val newDebtFreeDate: YearMonth,
    val monthsSaved: Int,
    val interestSaved: Double,
    val totalSaved: Double
)

/**
 * A motivational milestone in the debt payoff journey.
 *
 * @property title Milestone title
 * @property description Encouraging description
 * @property targetDate Estimated date of achievement
 * @property isAchieved Whether this milestone has been reached
 * @property achievedDate Actual date achieved (if applicable)
 */
data class DebtMilestone(
    val title: String,
    val description: String,
    val targetDate: YearMonth?,
    val isAchieved: Boolean = false,
    val achievedDate: LocalDate? = null
)

/**
 * Progress snapshot for tracking debt payoff over time.
 */
@Entity(tableName = "debt_progress")
data class DebtProgressEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "recorded_date") val recordedDate: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "total_debt_remaining") val totalDebtRemaining: Double,
    @ColumnInfo(name = "total_paid_this_month") val totalPaidThisMonth: Double,
    @ColumnInfo(name = "debts_remaining_count") val debtsRemainingCount: Int,
    @ColumnInfo(name = "debts_paid_off_count") val debtsPaidOffCount: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data Access Object for debt payoff operations.
 */
@Dao
interface DebtPayoffDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntry)

    @Update
    suspend fun updateDebt(debt: DebtEntry)

    @Delete
    suspend fun deleteDebt(debt: DebtEntry)

    @Query("SELECT * FROM debts WHERE is_active = 1 ORDER BY current_balance ASC")
    fun getActiveDebts(): Flow<List<DebtEntry>>

    @Query("SELECT * FROM debts WHERE is_active = 1 ORDER BY current_balance ASC")
    suspend fun getActiveDebtsSnapshot(): List<DebtEntry>

    @Query("SELECT * FROM debts ORDER BY start_date ASC")
    fun getAllDebts(): Flow<List<DebtEntry>>

    @Query("SELECT SUM(current_balance) FROM debts WHERE is_active = 1")
    suspend fun getTotalDebtBalance(): Double?

    @Query("SELECT SUM(minimum_payment) FROM debts WHERE is_active = 1")
    suspend fun getTotalMinimumPayments(): Double?

    @Query("SELECT COUNT(*) FROM debts WHERE is_active = 0")
    suspend fun getPaidOffCount(): Int

    // ── Progress Tracking ────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: DebtProgressEntry)

    @Query("SELECT * FROM debt_progress ORDER BY recorded_date DESC LIMIT :months")
    suspend fun getRecentProgress(months: Int = 12): List<DebtProgressEntry>

    @Query("SELECT * FROM debt_progress ORDER BY recorded_date DESC LIMIT 1")
    suspend fun getLatestProgress(): DebtProgressEntry?
}

// ─────────────────────────────────────────────────────────────────────────────
// Debt Payoff Planner Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core engine for debt payoff planning and simulation.
 *
 * Implements both Snowball and Avalanche strategies, generates payment schedules,
 * and provides motivational tracking.
 *
 * ## Usage
 * ```kotlin
 * val planner = DebtPayoffPlanner(debtPayoffDao)
 *
 * // Add debts
 * planner.addDebt(DebtEntry(
 *     name = "Personal Loan",
 *     originalBalance = 300000.0,
 *     currentBalance = 250000.0,
 *     interestRate = 12.0,
 *     minimumPayment = 8500.0,
 *     category = DebtCategory.PERSONAL_LOAN
 * ))
 *
 * // Compare strategies
 * val comparison = planner.compareStrategies(extraMonthlyBudget = 5000.0)
 *
 * // Simulate extra payments
 * val simulation = planner.simulateExtraPayment(
 *     extraAmount = 3000.0,
 *     strategy = PayoffStrategy.AVALANCHE
 * )
 * ```
 *
 * @param dao The Room DAO for database operations
 */
class DebtPayoffPlanner(private val dao: DebtPayoffDao) {

    companion object {
        /** Maximum months to simulate (30 years) to prevent infinite loops */
        private const val MAX_SIMULATION_MONTHS = 360

        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")
    }

    // ── Debt Management ──────────────────────────────────────────────────────

    /**
     * Adds a new debt to track.
     *
     * @param debt The debt entry to add
     */
    suspend fun addDebt(debt: DebtEntry) {
        dao.insertDebt(debt)
    }

    /**
     * Updates a debt (e.g., when balance changes after a payment).
     *
     * @param debt The updated debt entry
     */
    suspend fun updateDebt(debt: DebtEntry) {
        dao.updateDebt(debt)
    }

    /**
     * Marks a debt as paid off.
     *
     * @param debt The debt that has been paid off
     */
    suspend fun markDebtPaidOff(debt: DebtEntry) {
        dao.updateDebt(debt.copy(isActive = false, currentBalance = 0.0))
    }

    /**
     * Removes a debt from tracking entirely.
     *
     * @param debt The debt to remove
     */
    suspend fun removeDebt(debt: DebtEntry) {
        dao.deleteDebt(debt)
    }

    // ── Strategy Calculation ─────────────────────────────────────────────────

    /**
     * Generates a complete payoff plan using the specified strategy.
     *
     * @param strategy The payoff strategy to use
     * @param extraMonthlyBudget Additional monthly amount beyond minimums to throw at debt
     * @return Complete [PayoffPlan] with schedule and projections
     */
    suspend fun generatePayoffPlan(
        strategy: PayoffStrategy,
        extraMonthlyBudget: Double = 0.0
    ): PayoffPlan {
        val debts = dao.getActiveDebtsSnapshot().toMutableList()

        if (debts.isEmpty()) {
            return PayoffPlan(
                strategy = strategy,
                schedule = emptyList(),
                totalInterestPaid = 0.0,
                debtFreeDate = YearMonth.now(),
                totalMonths = 0,
                totalPaid = 0.0,
                interestSavedVsMinimum = 0.0,
                monthsSavedVsMinimum = 0,
                debtPayoffOrder = emptyList()
            )
        }

        // Sort debts based on strategy
        val sortedDebts = when (strategy) {
            PayoffStrategy.SNOWBALL -> debts.sortedBy { it.currentBalance }
            PayoffStrategy.AVALANCHE -> debts.sortedByDescending { it.interestRate }
        }

        // Simulate payoff
        val result = simulatePayoff(sortedDebts, extraMonthlyBudget)

        // Calculate minimum-only baseline for comparison
        val minimumOnlyResult = simulatePayoff(sortedDebts, 0.0)

        return PayoffPlan(
            strategy = strategy,
            schedule = result.schedule,
            totalInterestPaid = result.totalInterest.roundTo2(),
            debtFreeDate = result.debtFreeMonth,
            totalMonths = result.totalMonths,
            totalPaid = result.totalPaid.roundTo2(),
            interestSavedVsMinimum = (minimumOnlyResult.totalInterest - result.totalInterest).roundTo2(),
            monthsSavedVsMinimum = minimumOnlyResult.totalMonths - result.totalMonths,
            debtPayoffOrder = result.payoffOrder
        )
    }

    /**
     * Compares Snowball and Avalanche strategies side by side.
     *
     * @param extraMonthlyBudget Additional monthly budget for debt payoff
     * @return [StrategyComparison] with both plans and recommendation
     */
    suspend fun compareStrategies(extraMonthlyBudget: Double = 0.0): StrategyComparison {
        val snowballPlan = generatePayoffPlan(PayoffStrategy.SNOWBALL, extraMonthlyBudget)
        val avalanchePlan = generatePayoffPlan(PayoffStrategy.AVALANCHE, extraMonthlyBudget)

        val interestDiff = snowballPlan.totalInterestPaid - avalanchePlan.totalInterestPaid
        val timeDiff = snowballPlan.totalMonths - avalanchePlan.totalMonths

        val recommendation = buildRecommendation(interestDiff, timeDiff)

        return StrategyComparison(
            snowballPlan = snowballPlan,
            avalanchePlan = avalanchePlan,
            interestDifference = interestDiff.roundTo2(),
            timeDifferenceMonths = timeDiff,
            recommendedStrategy = if (interestDiff > 1000) PayoffStrategy.AVALANCHE else PayoffStrategy.SNOWBALL,
            recommendation = recommendation
        )
    }

    /**
     * Simulates the effect of paying extra money toward debt each month.
     *
     * @param extraAmount Extra monthly payment amount to simulate
     * @param strategy Strategy to use for the simulation
     * @return [ExtraPaymentSimulation] showing time and money saved
     */
    suspend fun simulateExtraPayment(
        extraAmount: Double,
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE
    ): ExtraPaymentSimulation {
        val withoutExtra = generatePayoffPlan(strategy, 0.0)
        val withExtra = generatePayoffPlan(strategy, extraAmount)

        return ExtraPaymentSimulation(
            extraMonthlyAmount = extraAmount,
            originalDebtFreeDate = withoutExtra.debtFreeDate,
            newDebtFreeDate = withExtra.debtFreeDate,
            monthsSaved = withoutExtra.totalMonths - withExtra.totalMonths,
            interestSaved = (withoutExtra.totalInterestPaid - withExtra.totalInterestPaid).roundTo2(),
            totalSaved = (withoutExtra.totalPaid - withExtra.totalPaid).roundTo2()
        )
    }

    /**
     * Simulates multiple extra payment amounts for comparison.
     *
     * @param amounts List of extra monthly amounts to simulate
     * @param strategy Strategy to use
     * @return List of simulations for each amount
     */
    suspend fun simulateMultipleExtraPayments(
        amounts: List<Double> = listOf(1000.0, 2000.0, 3000.0, 5000.0, 10000.0),
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE
    ): List<ExtraPaymentSimulation> {
        return amounts.map { amount ->
            simulateExtraPayment(amount, strategy)
        }
    }

    // ── Milestones & Progress ────────────────────────────────────────────────

    /**
     * Generates motivational milestones for the debt payoff journey.
     *
     * @param strategy Strategy being followed
     * @param extraBudget Extra monthly budget
     * @return List of milestones (achieved and future)
     */
    suspend fun getMilestones(
        strategy: PayoffStrategy = PayoffStrategy.AVALANCHE,
        extraBudget: Double = 0.0
    ): List<DebtMilestone> {
        val debts = dao.getActiveDebtsSnapshot()
        val paidOffCount = dao.getPaidOffCount()
        val totalDebt = dao.getTotalDebtBalance() ?: 0.0
        val milestones = mutableListOf<DebtMilestone>()

        // First debt paid off
        milestones.add(
            DebtMilestone(
                title = "🎉 First Debt Conquered!",
                description = "You paid off your first debt completely!",
                targetDate = null,
                isAchieved = paidOffCount >= 1
            )
        )

        // 25% milestone
        val totalOriginal = debts.sumOf { it.originalBalance }
        val percentPaid = if (totalOriginal > 0) {
            ((totalOriginal - totalDebt) / totalOriginal) * 100
        } else 0.0

        milestones.add(
            DebtMilestone(
                title = "📊 Quarter Way There!",
                description = "25% of your total debt is paid off!",
                targetDate = null,
                isAchieved = percentPaid >= 25.0
            )
        )

        // 50% milestone
        milestones.add(
            DebtMilestone(
                title = "🏆 Halfway Hero!",
                description = "50% of your debt is gone! You're crushing it!",
                targetDate = null,
                isAchieved = percentPaid >= 50.0
            )
        )

        // 75% milestone
        milestones.add(
            DebtMilestone(
                title = "🚀 The Home Stretch!",
                description = "75% done! The finish line is in sight!",
                targetDate = null,
                isAchieved = percentPaid >= 75.0
            )
        )

        // Debt free
        milestones.add(
            DebtMilestone(
                title = "🎊 DEBT FREE!",
                description = "You did it! Every single debt is paid off!",
                targetDate = null,
                isAchieved = totalDebt == 0.0 && paidOffCount > 0
            )
        )

        return milestones
    }

    /**
     * Records current progress for historical tracking.
     */
    suspend fun recordProgress(totalPaidThisMonth: Double) {
        val totalDebt = dao.getTotalDebtBalance() ?: 0.0
        val activeCount = dao.getActiveDebtsSnapshot().size
        val paidOffCount = dao.getPaidOffCount()

        val progress = DebtProgressEntry(
            totalDebtRemaining = totalDebt,
            totalPaidThisMonth = totalPaidThisMonth,
            debtsRemainingCount = activeCount,
            debtsPaidOffCount = paidOffCount
        )
        dao.insertProgress(progress)
    }

    /**
     * Gets a summary of debt payoff progress.
     *
     * @return Pair of (total debt remaining, percentage paid off)
     */
    suspend fun getProgressSummary(): Pair<Double, Double> {
        val debts = dao.getActiveDebtsSnapshot()
        val totalRemaining = dao.getTotalDebtBalance() ?: 0.0
        val totalOriginal = debts.sumOf { it.originalBalance }

        val percentPaid = if (totalOriginal > 0) {
            ((totalOriginal - totalRemaining) / totalOriginal) * 100.0
        } else 0.0

        return Pair(totalRemaining, percentPaid.roundTo2())
    }

    // ── Private Simulation Engine ────────────────────────────────────────────

    /**
     * Internal simulation result container.
     */
    private data class SimulationResult(
        val schedule: List<MonthlyPaymentDetail>,
        val totalInterest: Double,
        val totalPaid: Double,
        val totalMonths: Int,
        val debtFreeMonth: YearMonth,
        val payoffOrder: List<String>
    )

    /**
     * Core simulation engine that calculates month-by-month payoff.
     *
     * @param sortedDebts Debts sorted according to chosen strategy
     * @param extraBudget Additional monthly amount beyond minimums
     * @return Simulation results
     */
    private fun simulatePayoff(
        sortedDebts: List<DebtEntry>,
        extraBudget: Double
    ): SimulationResult {
        // Working copies of balances
        val balances = sortedDebts.associate { it.id to it.currentBalance }.toMutableMap()
        val schedule = mutableListOf<MonthlyPaymentDetail>()
        val payoffOrder = mutableListOf<String>()
        var totalInterest = 0.0
        var totalPaid = 0.0
        var month = 0
        var currentMonth = YearMonth.now()

        while (balances.values.any { it > 0 } && month < MAX_SIMULATION_MONTHS) {
            month++
            currentMonth = currentMonth.plusMonths(1)
            var extraAvailable = extraBudget

            // Pay minimum on all debts first
            for (debt in sortedDebts) {
                val balance = balances[debt.id] ?: 0.0
                if (balance <= 0) continue

                // Calculate monthly interest
                val monthlyRate = debt.interestRate / 100.0 / 12.0
                val monthInterest = balance * monthlyRate
                totalInterest += monthInterest

                // Apply minimum payment
                val payment = minOf(debt.minimumPayment, balance + monthInterest)
                val principal = payment - monthInterest
                val newBalance = (balance - principal).coerceAtLeast(0.0)
                balances[debt.id] = newBalance
                totalPaid += payment

                schedule.add(
                    MonthlyPaymentDetail(
                        month = currentMonth,
                        debtName = debt.name,
                        payment = payment.roundTo2(),
                        principal = principal.roundTo2(),
                        interest = monthInterest.roundTo2(),
                        remainingBalance = newBalance.roundTo2(),
                        isExtraPayment = false
                    )
                )

                if (newBalance == 0.0 && debt.name !in payoffOrder) {
                    payoffOrder.add(debt.name)
                }
            }

            // Apply extra payment to target debt (first in sorted order with balance > 0)
            if (extraAvailable > 0) {
                val targetDebt = sortedDebts.firstOrNull { (balances[it.id] ?: 0.0) > 0 }
                if (targetDebt != null) {
                    val currentBalance = balances[targetDebt.id] ?: 0.0
                    val extraPayment = minOf(extraAvailable, currentBalance)
                    balances[targetDebt.id] = (currentBalance - extraPayment).coerceAtLeast(0.0)
                    totalPaid += extraPayment

                    if (extraPayment > 0) {
                        schedule.add(
                            MonthlyPaymentDetail(
                                month = currentMonth,
                                debtName = targetDebt.name,
                                payment = extraPayment.roundTo2(),
                                principal = extraPayment.roundTo2(),
                                interest = 0.0,
                                remainingBalance = (balances[targetDebt.id] ?: 0.0).roundTo2(),
                                isExtraPayment = true
                            )
                        )
                    }

                    if ((balances[targetDebt.id] ?: 0.0) == 0.0 && targetDebt.name !in payoffOrder) {
                        payoffOrder.add(targetDebt.name)

                        // Freed-up minimum now cascades to next debt (snowball effect)
                        // This is handled in subsequent iterations
                    }
                }
            }
        }

        return SimulationResult(
            schedule = schedule,
            totalInterest = totalInterest,
            totalPaid = totalPaid,
            totalMonths = month,
            debtFreeMonth = currentMonth,
            payoffOrder = payoffOrder
        )
    }

    /**
     * Builds a human-readable recommendation string.
     */
    private fun buildRecommendation(interestDiff: Double, timeDiff: Int): String {
        return when {
            interestDiff < 500 -> {
                "Both strategies are nearly identical for your debts. " +
                        "Go with Snowball for quick motivational wins!"
            }
            interestDiff < 5000 -> {
                "Avalanche saves you ₹${formatCurrency(interestDiff)} in interest. " +
                        "If you need motivation, Snowball's quick wins might be worth the small extra cost."
            }
            else -> {
                "Avalanche saves you ₹${formatCurrency(interestDiff)} in interest " +
                        "and gets you debt-free $timeDiff months sooner. " +
                        "Strongly recommended for maximum savings!"
            }
        }
    }

    /**
     * Formats currency for display.
     */
    private fun formatCurrency(amount: Double): String {
        return when {
            amount >= 10000000 -> "${(amount / 10000000).roundTo2()} Cr"
            amount >= 100000 -> "${(amount / 100000).roundTo2()} L"
            amount >= 1000 -> "${(amount / 1000).roundTo2()}K"
            else -> amount.roundToInt().toString()
        }
    }

    /**
     * Extension to round a Double to 2 decimal places.
     */
    private fun Double.roundTo2(): Double = (this * 100.0).roundToInt() / 100.0
}
