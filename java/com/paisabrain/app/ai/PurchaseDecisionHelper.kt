package com.paisabrain.app.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * "Can I Afford This?" — Instant purchase decision calculator.
 *
 * Provides comprehensive financial analysis of a potential purchase, considering
 * the user's current financial state, budget impact, EMI comparisons, opportunity
 * cost, and behavioral patterns to help make better spending decisions.
 *
 * ## Key Features
 * - Affordability assessment based on actual savings pattern
 * - Real-time budget impact calculation for remainder of month
 * - Cash vs EMI total cost comparison
 * - "Wait calculator" — how long to save without budget impact
 * - Opportunity cost visualization (investment alternative)
 * - Traffic-light verdict system (Go / Think / Stop)
 * - Needs vs Wants rational check
 * - Purchase decision history with regret analysis
 * - Time-of-day and category-based regret prediction
 *
 * ## Tone
 * Helpful and non-judgmental. Never says "you shouldn't buy this" —
 * presents facts and lets the user decide. Celebrates good decisions.
 *
 * ## Usage
 * ```kotlin
 * val helper = PurchaseDecisionHelper(context)
 * val decision = helper.evaluatePurchase(
 *     price = 30000.0,
 *     currentSavings = 85000.0,
 *     monthlyBudgetRemaining = 25000.0,
 *     daysLeftInMonth = 18
 * )
 * println(decision.verdict) // GO_AHEAD, THINK_ABOUT_IT, or NOT_NOW
 * ```
 *
 * @property context Application context for persistence
 */
class PurchaseDecisionHelper(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "purchase_decision_prefs"
        private const val KEY_DECISIONS = "purchase_decisions"
        private const val KEY_MONTHLY_SAVINGS_AVG = "avg_monthly_savings"
        private const val KEY_MONTHLY_INCOME = "monthly_income"

        /** Default assumed annual return for opportunity cost calculation */
        private const val DEFAULT_ANNUAL_RETURN_PERCENT = 12.0

        /** Hours considered "late night" for impulse detection (10 PM - 2 AM) */
        private val IMPULSE_HOURS = listOf(22, 23, 0, 1, 2)

        /** Percentage of monthly savings that makes a purchase "easily affordable" */
        private const val EASILY_AFFORDABLE_THRESHOLD = 0.25 // 25% of monthly savings

        /** Percentage beyond which purchase is "stretching" */
        private const val STRETCHING_THRESHOLD = 0.75 // 75% of monthly savings

        /** Beyond this, user truly cannot afford it right now */
        private const val CANNOT_AFFORD_THRESHOLD = 1.5 // 150% of monthly savings
    }

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────────
    // Enums & Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * How affordable a purchase is relative to the user's financial state.
     */
    enum class AffordabilityLevel {
        /** Well within means — won't meaningfully impact savings or budget */
        EASILY_AFFORDABLE,
        /** Affordable but will be noticeable in the budget */
        POSSIBLE,
        /** Can technically be done but significantly strains finances */
        STRETCHING,
        /** Would deplete savings below safety or exceed available budget */
        CANNOT_AFFORD
    }

    /**
     * Traffic light verdict for purchase decisions.
     */
    enum class Verdict {
        /** Financial situation comfortably supports this purchase */
        GO_AHEAD,
        /** Borderline — user should consider carefully before deciding */
        THINK_ABOUT_IT,
        /** Finances don't support this right now — waiting is recommended */
        NOT_NOW
    }

    /**
     * What the user ultimately decided to do.
     */
    enum class FinalDecision {
        /** User went ahead and bought it */
        BOUGHT,
        /** User is actively saving/waiting to buy later */
        WAITING,
        /** User decided not to buy at all */
        DECIDED_NO,
        /** Decision still pending */
        PENDING
    }

    /**
     * EMI details for installment purchases.
     *
     * @property tenureMonths Number of months for EMI
     * @property annualInterestRate Annual interest rate in percent (e.g., 15.0 for 15%)
     * @property processingFee One-time processing fee (if any)
     */
    data class EmiDetails(
        val tenureMonths: Int,
        val annualInterestRate: Double,
        val processingFee: Double = 0.0
    )

    /**
     * Cash vs EMI comparison breakdown.
     *
     * @property cashPrice One-time cash payment amount
     * @property emiPerMonth Monthly EMI amount
     * @property totalEmiCost Total amount paid over full EMI tenure
     * @property interestCost Total interest paid (extra cost over cash price)
     * @property interestPercentage Interest as percentage of cash price
     * @property processingFee Processing fee charged
     */
    data class CashVsEmiComparison(
        val cashPrice: Double,
        val emiPerMonth: Double,
        val totalEmiCost: Double,
        val interestCost: Double,
        val interestPercentage: Double,
        val processingFee: Double
    )

    /**
     * Complete purchase decision analysis result.
     *
     * @property itemName Name/description of the item (optional, user-provided)
     * @property price Purchase price
     * @property emiOption EMI details if applicable
     * @property affordabilityLevel How affordable this is
     * @property affordabilityMessage Human-readable affordability assessment
     * @property budgetImpact How this affects daily budget for rest of month
     * @property budgetImpactMessage Formatted budget impact explanation
     * @property cashVsEmi Cash vs EMI comparison (null if no EMI considered)
     * @property waitDays Number of days to save up without impacting budget
     * @property waitMessage Formatted wait suggestion
     * @property opportunityCost5yr What this money would grow to in 5 years if invested
     * @property opportunityCostMessage Formatted opportunity cost explanation
     * @property verdict Traffic light recommendation
     * @property verdictMessage Explanation of the verdict
     * @property needsVsWantsNote Gentle reminder about needs vs wants
     * @property decisionDate When this analysis was generated
     * @property finalDecision What user ultimately decided (updated later)
     * @property category Item category (for regret analysis)
     * @property hourOfDecision Hour when analysis was requested (for impulse detection)
     */
    data class PurchaseDecision(
        val itemName: String = "",
        val price: Double,
        val emiOption: EmiDetails? = null,
        val affordabilityLevel: AffordabilityLevel,
        val affordabilityMessage: String,
        val budgetImpact: BudgetImpact,
        val budgetImpactMessage: String,
        val cashVsEmi: CashVsEmiComparison? = null,
        val cashVsEmiMessage: String = "",
        val waitDays: Int,
        val waitMessage: String,
        val opportunityCost5yr: Double,
        val opportunityCostMessage: String,
        val verdict: Verdict,
        val verdictMessage: String,
        val needsVsWantsNote: String,
        val decisionDate: Long = System.currentTimeMillis(),
        val finalDecision: FinalDecision = FinalDecision.PENDING,
        val category: String = "",
        val hourOfDecision: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    )

    /**
     * Budget impact details.
     *
     * @property currentDailyBudget Current daily budget before purchase
     * @property newDailyBudget What daily budget becomes after purchase
     * @property budgetReductionPercent Percentage reduction in daily budget
     * @property daysLeftInMonth Days remaining in the current month
     */
    data class BudgetImpact(
        val currentDailyBudget: Double,
        val newDailyBudget: Double,
        val budgetReductionPercent: Double,
        val daysLeftInMonth: Int
    )

    /**
     * Summary of purchase decisions over a period.
     *
     * @property totalDecisions Total number of decisions logged
     * @property boughtCount Items user bought
     * @property waitedAndBoughtCount Items user waited on then bought
     * @property decidedNoCount Items user decided not to buy
     * @property neverBoughtSavings Money saved from items user decided against
     * @property pendingCount Decisions still pending
     */
    data class DecisionSummary(
        val totalDecisions: Int,
        val boughtCount: Int,
        val waitedAndBoughtCount: Int,
        val decidedNoCount: Int,
        val neverBoughtSavings: Double,
        val pendingCount: Int
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Evaluation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Evaluates a potential purchase and returns comprehensive decision analysis.
     *
     * This is the main entry point. Takes the user's current financial context
     * and the purchase details, returns a complete analysis with verdict.
     *
     * @param price Price of the item in rupees
     * @param emiMonths Number of EMI months (null = cash purchase)
     * @param emiRate Annual interest rate for EMI (null = no EMI)
     * @param currentSavings Total available savings
     * @param monthlyBudgetRemaining Budget remaining for current month
     * @param daysLeftInMonth Days left in the current month
     * @param itemName Optional name/description of the item
     * @param monthlyAvgSavings Average monthly savings (for affordability assessment)
     * @param category Category of purchase (for regret analysis)
     * @return Complete [PurchaseDecision] analysis
     */
    fun evaluatePurchase(
        price: Double,
        emiMonths: Int? = null,
        emiRate: Double? = null,
        currentSavings: Double,
        monthlyBudgetRemaining: Double,
        daysLeftInMonth: Int,
        itemName: String = "",
        monthlyAvgSavings: Double = getStoredMonthlySavings(),
        category: String = ""
    ): PurchaseDecision {
        // 1. Affordability Check
        val affordability = calculateAffordability(price, monthlyAvgSavings, currentSavings)

        // 2. Budget Impact
        val budgetImpact = calculateBudgetImpact(price, monthlyBudgetRemaining, daysLeftInMonth)

        // 3. Cash vs EMI
        val cashVsEmi = if (emiMonths != null && emiRate != null) {
            calculateCashVsEmi(price, emiMonths, emiRate)
        } else {
            null
        }

        // 4. Wait Calculator
        val dailySavingsPossible = if (monthlyAvgSavings > 0) monthlyAvgSavings / 30.0 else 0.0
        val waitDays = if (dailySavingsPossible > 0) {
            ceil(price / dailySavingsPossible).toInt()
        } else {
            Int.MAX_VALUE
        }

        // 5. Opportunity Cost (5 years at assumed return)
        val opportunityCost = calculateOpportunityCost(price, 5, DEFAULT_ANNUAL_RETURN_PERCENT)

        // 6. Verdict
        val verdict = determineVerdict(affordability.first, budgetImpact, currentSavings, price)

        // 7. Needs vs Wants note
        val needsNote = getNeedsVsWantsNote(price, affordability.first)

        // Build messages
        val affordabilityMessage = buildAffordabilityMessage(affordability.first, monthlyAvgSavings, price)
        val budgetImpactMessage = buildBudgetImpactMessage(budgetImpact, price)
        val cashVsEmiMessage = buildCashVsEmiMessage(cashVsEmi, price)
        val waitMessage = buildWaitMessage(waitDays, dailySavingsPossible)
        val opportunityMessage = buildOpportunityCostMessage(price, opportunityCost)
        val verdictMessage = buildVerdictMessage(verdict)

        return PurchaseDecision(
            itemName = itemName,
            price = price,
            emiOption = if (emiMonths != null && emiRate != null) EmiDetails(emiMonths, emiRate) else null,
            affordabilityLevel = affordability.first,
            affordabilityMessage = affordabilityMessage,
            budgetImpact = budgetImpact,
            budgetImpactMessage = budgetImpactMessage,
            cashVsEmi = cashVsEmi,
            cashVsEmiMessage = cashVsEmiMessage,
            waitDays = waitDays,
            waitMessage = waitMessage,
            opportunityCost5yr = opportunityCost,
            opportunityCostMessage = opportunityMessage,
            verdict = verdict,
            verdictMessage = verdictMessage,
            needsVsWantsNote = needsNote,
            category = category,
            hourOfDecision = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Calculation Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Determines affordability level based on price relative to monthly savings.
     */
    private fun calculateAffordability(
        price: Double,
        monthlySavings: Double,
        totalSavings: Double
    ): Pair<AffordabilityLevel, Double> {
        if (monthlySavings <= 0) {
            return if (totalSavings >= price * 3) {
                Pair(AffordabilityLevel.POSSIBLE, 0.0)
            } else {
                Pair(AffordabilityLevel.CANNOT_AFFORD, 0.0)
            }
        }

        val ratio = price / monthlySavings

        val level = when {
            ratio <= EASILY_AFFORDABLE_THRESHOLD -> AffordabilityLevel.EASILY_AFFORDABLE
            ratio <= STRETCHING_THRESHOLD -> AffordabilityLevel.POSSIBLE
            ratio <= CANNOT_AFFORD_THRESHOLD -> AffordabilityLevel.STRETCHING
            else -> AffordabilityLevel.CANNOT_AFFORD
        }

        return Pair(level, ratio)
    }

    /**
     * Calculates how buying this item would affect the daily budget for the rest of the month.
     */
    private fun calculateBudgetImpact(
        price: Double,
        monthlyBudgetRemaining: Double,
        daysLeftInMonth: Int
    ): BudgetImpact {
        val safeDays = maxOf(daysLeftInMonth, 1)
        val currentDaily = monthlyBudgetRemaining / safeDays
        val newRemaining = monthlyBudgetRemaining - price
        val newDaily = maxOf(newRemaining / safeDays, 0.0)
        val reductionPercent = if (currentDaily > 0) {
            ((currentDaily - newDaily) / currentDaily) * 100
        } else {
            100.0
        }

        return BudgetImpact(
            currentDailyBudget = currentDaily,
            newDailyBudget = newDaily,
            budgetReductionPercent = reductionPercent,
            daysLeftInMonth = safeDays
        )
    }

    /**
     * Calculates the total cost comparison between paying cash and EMI.
     *
     * Uses the reducing balance EMI formula:
     * EMI = P × r × (1+r)^n / ((1+r)^n - 1)
     * where P = principal, r = monthly rate, n = tenure in months
     */
    private fun calculateCashVsEmi(
        price: Double,
        tenureMonths: Int,
        annualRate: Double,
        processingFee: Double = 0.0
    ): CashVsEmiComparison {
        val monthlyRate = annualRate / 12.0 / 100.0

        val emi = if (monthlyRate > 0 && tenureMonths > 0) {
            val factor = (1 + monthlyRate).pow(tenureMonths.toDouble())
            price * monthlyRate * factor / (factor - 1)
        } else {
            price / maxOf(tenureMonths, 1).toDouble()
        }

        val totalEmiCost = emi * tenureMonths + processingFee
        val interestCost = totalEmiCost - price
        val interestPercent = if (price > 0) (interestCost / price) * 100 else 0.0

        return CashVsEmiComparison(
            cashPrice = price,
            emiPerMonth = emi,
            totalEmiCost = totalEmiCost,
            interestCost = interestCost,
            interestPercentage = interestPercent,
            processingFee = processingFee
        )
    }

    /**
     * Calculates what the money would grow to if invested instead.
     *
     * Uses compound interest: A = P × (1 + r/12)^(12×t)
     */
    private fun calculateOpportunityCost(
        principal: Double,
        years: Int,
        annualReturnPercent: Double
    ): Double {
        val monthlyRate = annualReturnPercent / 12.0 / 100.0
        val months = years * 12
        return principal * (1 + monthlyRate).pow(months.toDouble())
    }

    /**
     * Determines the overall verdict based on all factors.
     */
    private fun determineVerdict(
        affordability: AffordabilityLevel,
        budgetImpact: BudgetImpact,
        savings: Double,
        price: Double
    ): Verdict {
        // If can't afford, always NOT_NOW
        if (affordability == AffordabilityLevel.CANNOT_AFFORD) {
            return Verdict.NOT_NOW
        }

        // If buying would leave daily budget below ₹200, think about it
        if (budgetImpact.newDailyBudget < 200) {
            return Verdict.NOT_NOW
        }

        // If buying would use more than 50% of total savings, think about it
        if (price > savings * 0.5) {
            return Verdict.THINK_ABOUT_IT
        }

        // If easily affordable and budget impact is less than 30%, go ahead
        if (affordability == AffordabilityLevel.EASILY_AFFORDABLE &&
            budgetImpact.budgetReductionPercent < 30
        ) {
            return Verdict.GO_AHEAD
        }

        // If possible but budget takes a hit, think about it
        if (affordability == AffordabilityLevel.POSSIBLE) {
            return if (budgetImpact.budgetReductionPercent < 40) {
                Verdict.GO_AHEAD
            } else {
                Verdict.THINK_ABOUT_IT
            }
        }

        // Stretching = always think about it
        if (affordability == AffordabilityLevel.STRETCHING) {
            return Verdict.THINK_ABOUT_IT
        }

        return Verdict.THINK_ABOUT_IT
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Message Builders
    // ─────────────────────────────────────────────────────────────────────────────

    private fun buildAffordabilityMessage(
        level: AffordabilityLevel,
        monthlySavings: Double,
        price: Double
    ): String {
        val savingsStr = formatCurrency(monthlySavings)
        val priceStr = formatCurrency(price)

        return when (level) {
            AffordabilityLevel.EASILY_AFFORDABLE ->
                "✅ Based on your average monthly savings of $savingsStr, " +
                        "this $priceStr purchase is easily affordable. It won't significantly " +
                        "impact your savings pattern."

            AffordabilityLevel.POSSIBLE ->
                "🟡 Based on your average monthly savings of $savingsStr, " +
                        "this is tight but possible. You'll notice it in your budget " +
                        "but can manage if other expenses stay normal."

            AffordabilityLevel.STRETCHING ->
                "🟠 Based on your savings pattern of $savingsStr/month, " +
                        "this purchase of $priceStr would be a significant stretch. " +
                        "It might mean cutting back on other things this month."

            AffordabilityLevel.CANNOT_AFFORD ->
                "🔴 At $priceStr, this is beyond comfortable affordability right now " +
                        "(your average savings: $savingsStr/month). Waiting and saving up " +
                        "would be the financially safe approach."
        }
    }

    private fun buildBudgetImpactMessage(impact: BudgetImpact, price: Double): String {
        val currentStr = formatCurrency(impact.currentDailyBudget)
        val newStr = formatCurrency(impact.newDailyBudget)

        return if (impact.newDailyBudget > 0) {
            "📊 If you buy this today, your daily budget drops from $currentStr to " +
                    "$newStr for the remaining ${impact.daysLeftInMonth} days of this month " +
                    "(${impact.budgetReductionPercent.roundToInt()}% reduction)."
        } else {
            "📊 ⚠️ This purchase of ${formatCurrency(price)} exceeds your remaining " +
                    "monthly budget. You'd need to dip into savings or skip other planned expenses."
        }
    }

    private fun buildCashVsEmiMessage(comparison: CashVsEmiComparison?, price: Double): String {
        if (comparison == null) return ""

        return buildString {
            appendLine("💰 Cash vs EMI Comparison:")
            appendLine("   • Cash: ${formatCurrency(price)} (one-time)")
            appendLine("   • EMI: ${formatCurrency(comparison.emiPerMonth)}/month × ${comparison.cashPrice.let { 
                val months = (comparison.totalEmiCost / comparison.emiPerMonth).roundToInt()
                "$months months"
            }} = ${formatCurrency(comparison.totalEmiCost)}")
            appendLine("   • Extra cost on EMI: ${formatCurrency(comparison.interestCost)} " +
                    "(${comparison.interestPercentage.roundToInt()}% more)")
            if (comparison.processingFee > 0) {
                appendLine("   • Processing fee: ${formatCurrency(comparison.processingFee)}")
            }
        }.trimEnd()
    }

    private fun buildWaitMessage(waitDays: Int, dailySavings: Double): String {
        return when {
            waitDays == Int.MAX_VALUE ->
                "⏳ Cannot calculate wait time — no regular savings pattern detected yet."
            waitDays <= 7 ->
                "⏳ If you save ${formatCurrency(dailySavings)}/day (your natural pace), " +
                        "you can buy this in just $waitDays days without any budget impact!"
            waitDays <= 30 ->
                "⏳ Save ${formatCurrency(dailySavings)}/day and you'll have enough in " +
                        "$waitDays days (about ${waitDays / 7} weeks) — no stress, no budget crunch."
            waitDays <= 90 ->
                "⏳ At your current savings pace, you'd need about ${waitDays / 30} months " +
                        "to buy this without impacting your budget. Consider if it's worth the wait."
            else ->
                "⏳ This would take about ${waitDays / 30} months to save for at your current pace. " +
                        "This is a significant purchase relative to your income."
        }
    }

    private fun buildOpportunityCostMessage(price: Double, futureValue: Double): String {
        val priceStr = formatCurrency(price)
        val futureStr = formatCurrency(futureValue)
        val growthStr = formatCurrency(futureValue - price)

        return "📈 Opportunity Cost: ${priceStr} invested at ${DEFAULT_ANNUAL_RETURN_PERCENT.roundToInt()}% " +
                "annual return = ${futureStr} in 5 years. That's ${growthStr} in growth. " +
                "Is this purchase worth more to you than ${futureStr} five years from now?"
    }

    private fun buildVerdictMessage(verdict: Verdict): String {
        return when (verdict) {
            Verdict.GO_AHEAD ->
                "🟢 Go ahead! Your finances comfortably support this purchase. " +
                        "You've earned it — enjoy without guilt!"
            Verdict.THINK_ABOUT_IT ->
                "🟡 Think about it. You can technically afford this, but it'll be " +
                        "noticeable in your budget. Sleep on it — if you still want it " +
                        "tomorrow, it's probably worth it."
            Verdict.NOT_NOW ->
                "🔴 Not the best time. Your budget is tight for this right now. " +
                        "Consider waiting until next month or saving up gradually. " +
                        "There's no shame in waiting — it's actually a power move. 💪"
        }
    }

    private fun getNeedsVsWantsNote(price: Double, level: AffordabilityLevel): String {
        return when (level) {
            AffordabilityLevel.EASILY_AFFORDABLE ->
                "💭 Need or want? At this price point relative to your income, " +
                        "it doesn't matter much — you can afford either."
            AffordabilityLevel.POSSIBLE, AffordabilityLevel.STRETCHING ->
                "💭 Honest check: Is this a NEED (essential for daily life, health, " +
                        "or work) or a WANT (nice to have)? For tight budgets, needs get priority. " +
                        "Wants can wait for a better month. No judgment either way — just clarity."
            AffordabilityLevel.CANNOT_AFFORD ->
                "💭 When finances are tight, prioritize needs (food, health, " +
                        "housing, transport to work). Wants can absolutely wait — and " +
                        "delaying often reduces the desire. You might not even want this next month!"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Regret Predictor
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Predicts likelihood of purchase regret based on historical patterns.
     *
     * Analyzes past purchase decisions and their outcomes to provide
     * a data-driven regret prediction.
     *
     * @param price Price of the potential purchase
     * @param category Category (if available)
     * @return Human-readable regret prediction message, or null if insufficient data
     */
    fun getRegretPrediction(price: Double, category: String = ""): String? {
        val decisions = getDecisionHistory()
        if (decisions.size < 5) return null // Need minimum data for meaningful prediction

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isLateNight = currentHour in IMPULSE_HOURS

        // Check regret rate for similar price range purchases
        val similarPriceDecisions = decisions.filter { decision ->
            val priceDiff = kotlin.math.abs(decision.price - price)
            priceDiff <= price * 0.3 // Within 30% of target price
        }

        val boughtAndRegretted = similarPriceDecisions.count { it.finalDecision == FinalDecision.DECIDED_NO }
        val totalBought = similarPriceDecisions.count { it.finalDecision == FinalDecision.BOUGHT }

        val messages = mutableListOf<String>()

        // Late night impulse warning
        if (isLateNight) {
            val lateDecisions = decisions.filter { it.hourOfDecision in IMPULSE_HOURS }
            val lateRegrets = lateDecisions.count {
                it.finalDecision == FinalDecision.DECIDED_NO || it.finalDecision == FinalDecision.WAITING
            }
            if (lateDecisions.isNotEmpty()) {
                val regretRate = (lateRegrets.toFloat() / lateDecisions.size * 100).roundToInt()
                if (regretRate > 50) {
                    messages.add(
                        "🌙 Late-night shopping alert! Based on your history, " +
                                "${regretRate}% of purchases considered after 10 PM were later regretted. " +
                                "Try deciding again tomorrow morning."
                    )
                }
            }
        }

        // Category-based prediction
        if (category.isNotEmpty()) {
            val categoryDecisions = decisions.filter {
                it.category.equals(category, ignoreCase = true)
            }
            val categoryNotBought = categoryDecisions.count {
                it.finalDecision == FinalDecision.DECIDED_NO || it.finalDecision == FinalDecision.PENDING
            }
            if (categoryDecisions.size >= 3 && categoryNotBought > categoryDecisions.size / 2) {
                val skipRate = (categoryNotBought.toFloat() / categoryDecisions.size * 100).roundToInt()
                messages.add(
                    "📊 Pattern alert: You've looked at $category purchases " +
                            "${categoryDecisions.size} times before and skipped $skipRate% of them. " +
                            "You might not really need this."
                )
            }
        }

        // "Wait and see" success rate
        val waitedDecisions = decisions.filter { it.finalDecision == FinalDecision.DECIDED_NO }
        if (waitedDecisions.size >= 3) {
            val totalSaved = waitedDecisions.sumOf { it.price }
            messages.add(
                "💪 You've said 'no' to ${waitedDecisions.size} purchases " +
                        "and saved ${formatCurrency(totalSaved)} in total. Your future self thanks you!"
            )
        }

        return messages.joinToString("\n\n").ifEmpty { null }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Decision History
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Saves a purchase decision to history.
     *
     * @param decision The decision to save
     */
    fun saveDecision(decision: PurchaseDecision) {
        val decisions = getDecisionHistory().toMutableList()
        decisions.add(decision)
        // Keep last 100 decisions
        val trimmed = if (decisions.size > 100) decisions.takeLast(100) else decisions
        val json = gson.toJson(trimmed)
        getPrefs().edit().putString(KEY_DECISIONS, json).apply()
    }

    /**
     * Retrieves all past purchase decisions.
     *
     * @return List of all stored decisions, most recent first
     */
    fun getDecisionHistory(): List<PurchaseDecision> {
        val json = getPrefs().getString(KEY_DECISIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PurchaseDecision>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Updates the final decision for a past evaluation.
     *
     * @param decisionDate The timestamp of the original decision
     * @param finalDecision What the user ultimately decided
     */
    fun updateFinalDecision(decisionDate: Long, finalDecision: FinalDecision) {
        val decisions = getDecisionHistory().toMutableList()
        val index = decisions.indexOfFirst { it.decisionDate == decisionDate }
        if (index >= 0) {
            decisions[index] = decisions[index].copy(finalDecision = finalDecision)
            val json = gson.toJson(decisions)
            getPrefs().edit().putString(KEY_DECISIONS, json).apply()
        }
    }

    /**
     * Gets a summary of purchase decision patterns for a given month.
     *
     * @param monthsAgo How many months back to analyze (0 = current month)
     * @return [DecisionSummary] with aggregated stats
     */
    fun getDecisionSummary(monthsAgo: Int = 0): DecisionSummary {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -monthsAgo)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        val monthStart = cal.timeInMillis

        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.timeInMillis

        val monthDecisions = getDecisionHistory().filter {
            it.decisionDate in monthStart until monthEnd
        }

        return DecisionSummary(
            totalDecisions = monthDecisions.size,
            boughtCount = monthDecisions.count { it.finalDecision == FinalDecision.BOUGHT },
            waitedAndBoughtCount = monthDecisions.count { it.finalDecision == FinalDecision.WAITING },
            decidedNoCount = monthDecisions.count { it.finalDecision == FinalDecision.DECIDED_NO },
            neverBoughtSavings = monthDecisions
                .filter { it.finalDecision == FinalDecision.DECIDED_NO }
                .sumOf { it.price },
            pendingCount = monthDecisions.count { it.finalDecision == FinalDecision.PENDING }
        )
    }

    /**
     * Gets a motivational summary message about purchase discipline.
     *
     * @return Encouraging message about money saved by waiting/saying no
     */
    fun getMotivationalSummary(): String {
        val decisions = getDecisionHistory()
        val noBuys = decisions.filter { it.finalDecision == FinalDecision.DECIDED_NO }
        val pending = decisions.filter { it.finalDecision == FinalDecision.PENDING }

        if (noBuys.isEmpty() && pending.isEmpty()) {
            return "Start using the purchase evaluator and we'll track how much you save by making thoughtful decisions! 🧠"
        }

        val savedAmount = noBuys.sumOf { it.price }
        val pendingAmount = pending.sumOf { it.price }

        return buildString {
            if (noBuys.isNotEmpty()) {
                appendLine("🎉 You said 'no' to ${noBuys.size} purchases and saved ${formatCurrency(savedAmount)}!")
                appendLine("That money is still yours. Well done! 💪")
            }
            if (pending.isNotEmpty()) {
                appendLine()
                appendLine("🤔 You have ${pending.size} pending decisions worth ${formatCurrency(pendingAmount)}.")
                appendLine("Items you haven't thought about in a while probably aren't worth buying.")
            }
        }.trimEnd()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Configuration
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Stores the user's average monthly savings (can be auto-calculated or manual).
     *
     * @param amount Average monthly savings in rupees
     */
    fun setMonthlySavings(amount: Double) {
        getPrefs().edit().putFloat(KEY_MONTHLY_SAVINGS_AVG, amount.toFloat()).apply()
    }

    /**
     * Gets stored monthly savings figure.
     */
    fun getStoredMonthlySavings(): Double {
        return getPrefs().getFloat(KEY_MONTHLY_SAVINGS_AVG, 0f).toDouble()
    }

    /**
     * Stores the user's monthly income.
     *
     * @param amount Monthly income in rupees
     */
    fun setMonthlyIncome(amount: Double) {
        getPrefs().edit().putFloat(KEY_MONTHLY_INCOME, amount.toFloat()).apply()
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    /**
     * Formats a number as Indian currency string (₹X,XX,XXX).
     */
    private fun formatCurrency(amount: Double): String {
        return if (amount >= 10000000) {
            "₹${String.format("%.1f", amount / 10000000)} Cr"
        } else if (amount >= 100000) {
            "₹${String.format("%.1f", amount / 100000)} L"
        } else {
            "₹${String.format("%,.0f", amount)}"
        }
    }
}
