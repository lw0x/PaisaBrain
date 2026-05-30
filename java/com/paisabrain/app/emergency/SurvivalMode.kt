package com.paisabrain.app.emergency

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Emergency Survival Mode for job loss, income disruption, or financial crisis.
 *
 * Activated with one tap when the user faces a financial emergency. Immediately
 * calculates a survival plan showing runway, essential-only budget, what to cut,
 * and how to extend the safety window.
 *
 * ## Design Philosophy
 * - **Compassionate**: Never judgmental, never scary. Acknowledges the difficulty.
 * - **Actionable**: Every screen gives clear, prioritized steps.
 * - **Empowering**: Shows actual numbers to replace anxiety with information.
 * - **Hopeful**: Always includes recovery perspective and reassurance.
 *
 * ## Key Features
 * - Instant runway calculation (how many months of safety)
 * - Auto-generated essential-only budget
 * - Prioritized cut list (highest savings impact first)
 * - Runway extension projections
 * - Daily survival budget
 * - Recovery tracking when income resumes
 * - Help resources (generic helplines, government schemes)
 *
 * ## Usage
 * ```kotlin
 * val survival = SurvivalMode(context)
 * val plan = survival.activateSurvivalMode(
 *     savings = 150000.0,
 *     essentialExpenses = 35000.0
 * )
 * println("You have ${plan.runway} months of runway")
 * ```
 *
 * @property context Application context for persistence
 */
class SurvivalMode(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "survival_mode_prefs"
        private const val KEY_PLAN = "survival_plan"
        private const val KEY_IS_ACTIVE = "survival_active"
        private const val KEY_ACTIVATED_DATE = "survival_activated_date"
        private const val KEY_DEACTIVATED_DATE = "survival_deactivated_date"
        private const val KEY_RECOVERY_INCOME = "recovery_income"
        private const val KEY_EMERGENCY_FUND_GOAL = "emergency_fund_goal"
    }

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * A single item that can be cut to save money.
     *
     * @property item Name/description of the expense
     * @property monthlyCost How much this costs per month
     * @property priority Priority for cutting (1 = cut first, higher = less urgent)
     * @property howToCancel Instructions for cancelling/pausing this expense
     * @property impactOnRunway How many additional days of runway cutting this provides
     * @property category Expense category this belongs to
     * @property isCancelled Whether user has already cancelled this
     */
    data class CutSuggestion(
        val item: String,
        val monthlyCost: Double,
        val priority: Int,
        val howToCancel: String,
        val impactOnRunway: Double,
        val category: String = "",
        val isCancelled: Boolean = false
    )

    /**
     * Represents a transaction/expense record (simplified for survival mode analysis).
     *
     * @property amount Transaction amount
     * @property category Category of expense
     * @property merchantName Merchant/payee name
     * @property isRecurring Whether this is a recurring charge
     * @property date Transaction date
     */
    data class Transaction(
        val amount: Double,
        val category: String,
        val merchantName: String = "",
        val isRecurring: Boolean = false,
        val date: Long = System.currentTimeMillis()
    )

    /**
     * Complete survival plan.
     *
     * @property runway Number of months the user can survive on current savings
     * @property monthlyEssentials Monthly cost of only essential expenses
     * @property cutList Prioritized list of expenses to cut
     * @property extendedRunway How long the user can survive if they cut suggested items
     * @property dailySurvivalBudget Maximum daily spend in survival mode
     * @property totalSavings Total savings available
     * @property activatedDate When survival mode was activated
     * @property isActive Whether survival mode is currently active
     * @property totalMonthlyCutPossible Total monthly savings from cutting all suggested items
     * @property reassuranceMessage Compassionate message based on the numbers
     */
    data class SurvivalPlan(
        val runway: Double,
        val monthlyEssentials: Double,
        val cutList: List<CutSuggestion>,
        val extendedRunway: Double,
        val dailySurvivalBudget: Double,
        val totalSavings: Double = 0.0,
        val activatedDate: Long = System.currentTimeMillis(),
        val isActive: Boolean = true,
        val totalMonthlyCutPossible: Double = 0.0,
        val reassuranceMessage: String = ""
    )

    /**
     * Essential budget breakdown.
     *
     * @property rent Housing cost (rent/EMI)
     * @property food Groceries and basic food
     * @property utilities Electricity, water, gas, internet (basic)
     * @property medicines Regular medicines/health
     * @property transport Transport to interviews/essential travel
     * @property insurance Must-keep insurance premiums
     * @property total Total monthly essentials
     */
    data class EssentialBudget(
        val rent: Double = 0.0,
        val food: Double = 0.0,
        val utilities: Double = 0.0,
        val medicines: Double = 0.0,
        val transport: Double = 0.0,
        val insurance: Double = 0.0,
        val total: Double = 0.0
    )

    /**
     * A help resource (helpline, government scheme, etc.)
     *
     * @property title Name of the resource
     * @property description What it offers
     * @property contactInfo Phone number or website
     * @property category Type of help (financial counseling, employment, legal, etc.)
     */
    data class HelpResource(
        val title: String,
        val description: String,
        val contactInfo: String,
        val category: String
    )

    /**
     * Recovery progress tracking.
     *
     * @property daysInSurvivalMode How many days spent in survival mode
     * @property amountSpentDuringSurvival Total spent during survival mode
     * @property amountSavedByCutting Money saved by cutting suggested items
     * @property emergencyFundRebuildProgress Percentage of emergency fund rebuilt (0-100)
     * @property newMonthlyIncome New income after recovery (0 if not yet earning)
     * @property message Encouraging recovery message
     */
    data class RecoveryProgress(
        val daysInSurvivalMode: Int,
        val amountSpentDuringSurvival: Double,
        val amountSavedByCutting: Double,
        val emergencyFundRebuildProgress: Int,
        val newMonthlyIncome: Double,
        val message: String
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Functions
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Activates survival mode and generates a complete survival plan.
     *
     * This is the primary entry point when a user faces financial emergency.
     * It immediately provides clarity on their financial runway and actionable steps.
     *
     * @param savings Total available savings (across all accounts)
     * @param essentialExpenses Monthly essential expenses (rent + food + utilities + medicines + transport)
     * @param nonEssentialExpenses Optional: monthly non-essential expenses for cut list
     * @param subscriptions Optional: list of active subscriptions to suggest cutting
     * @return [SurvivalPlan] with complete analysis
     */
    fun activateSurvivalMode(
        savings: Double,
        essentialExpenses: Double,
        nonEssentialExpenses: Double = 0.0,
        subscriptions: List<Transaction> = emptyList()
    ): SurvivalPlan {
        val runway = calculateRunway(savings, essentialExpenses)
        val dailyBudget = if (runway > 0) essentialExpenses / 30.0 else 0.0

        // Generate cut suggestions from subscriptions and non-essentials
        val cutList = getCutSuggestions(subscriptions, essentialExpenses, savings)
        val totalCutSavings = cutList.sumOf { it.monthlyCost }

        // Calculate extended runway if all cuts are made
        val reducedMonthlyBurn = essentialExpenses - totalCutSavings
        val effectiveBurn = maxOf(reducedMonthlyBurn, essentialExpenses * 0.6) // Don't assume cuts below 60% of essentials
        val extendedRunway = if (effectiveBurn > 0) savings / effectiveBurn else 0.0

        // Generate reassurance
        val reassurance = generateReassurance(runway, savings, essentialExpenses)

        val plan = SurvivalPlan(
            runway = runway,
            monthlyEssentials = essentialExpenses,
            cutList = cutList,
            extendedRunway = extendedRunway,
            dailySurvivalBudget = dailyBudget,
            totalSavings = savings,
            activatedDate = System.currentTimeMillis(),
            isActive = true,
            totalMonthlyCutPossible = totalCutSavings,
            reassuranceMessage = reassurance
        )

        // Persist the plan
        savePlan(plan)
        getPrefs().edit()
            .putBoolean(KEY_IS_ACTIVE, true)
            .putLong(KEY_ACTIVATED_DATE, System.currentTimeMillis())
            .apply()

        return plan
    }

    /**
     * Calculates how many months the user can survive on current savings.
     *
     * @param savings Total available savings
     * @param monthlyBurn Monthly expenses (essential only)
     * @return Number of months of runway (e.g., 4.2 means 4 months and ~6 days)
     */
    fun calculateRunway(savings: Double, monthlyBurn: Double): Double {
        if (monthlyBurn <= 0) return Double.MAX_VALUE // No expenses = infinite runway
        if (savings <= 0) return 0.0
        return savings / monthlyBurn
    }

    /**
     * Generates prioritized cut suggestions from transaction history.
     *
     * Prioritizes cuts by:
     * 1. Highest monthly cost (biggest impact)
     * 2. Non-essential categories
     * 3. Recurring/subscription charges
     *
     * @param transactions Recent transactions to analyze for cuttable expenses
     * @param essentialExpenses Monthly essential expenses (for runway impact calculation)
     * @param savings Total savings (for runway impact calculation)
     * @return Prioritized list of [CutSuggestion] items
     */
    fun getCutSuggestions(
        transactions: List<Transaction>,
        essentialExpenses: Double = 0.0,
        savings: Double = 0.0
    ): List<CutSuggestion> {
        val suggestions = mutableListOf<CutSuggestion>()

        // Identify recurring/subscription charges
        val recurringExpenses = transactions
            .filter { it.isRecurring }
            .sortedByDescending { it.amount }

        recurringExpenses.forEachIndexed { index, transaction ->
            val runwayImpact = if (essentialExpenses > 0 && savings > 0) {
                val newBurn = essentialExpenses - transaction.amount
                if (newBurn > 0) (savings / newBurn) - (savings / essentialExpenses) else 0.0
            } else {
                0.0
            }

            suggestions.add(
                CutSuggestion(
                    item = transaction.merchantName.ifEmpty { "${transaction.category} subscription" },
                    monthlyCost = transaction.amount,
                    priority = index + 1,
                    howToCancel = getCancellationGuide(transaction.category),
                    impactOnRunway = runwayImpact * 30, // Convert months to days
                    category = transaction.category
                )
            )
        }

        // Add general cut suggestions for non-essential categories
        val nonEssentialCategories = mapOf(
            "Entertainment & Streaming" to "Pause all streaming subscriptions. Most allow resuming later without losing your account.",
            "Dining Out" to "Switch to home-cooked meals. Saves 60-70% over restaurant food. Meal prep on Sundays.",
            "Shopping (Non-essential)" to "Implement a 30-day rule: want something? Wait 30 days. If you still want it then, reconsider.",
            "Gym/Fitness" to "Pause membership (most gyms allow this). Walk, run, or use free workout videos at home.",
            "Cloud Storage/Apps" to "Downgrade to free tiers. Move files to device storage temporarily.",
            "Salon/Grooming" to "Reduce frequency. Do basics at home. Professional services can wait."
        )

        // Only add categories not already covered by transaction analysis
        val coveredCategories = suggestions.map { it.category.lowercase() }.toSet()
        var priorityCounter = suggestions.size + 1

        nonEssentialCategories.forEach { (category, howTo) ->
            if (category.lowercase() !in coveredCategories) {
                suggestions.add(
                    CutSuggestion(
                        item = category,
                        monthlyCost = 0.0, // Unknown — user should fill
                        priority = priorityCounter++,
                        howToCancel = howTo,
                        impactOnRunway = 0.0,
                        category = category
                    )
                )
            }
        }

        return suggestions.sortedWith(compareBy<CutSuggestion> { it.priority }
            .thenByDescending { it.monthlyCost })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Help Resources
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of help resources for people in financial crisis.
     *
     * Includes: financial counseling, employment services, government schemes,
     * mental health support, and legal aid.
     *
     * @return List of [HelpResource] items
     */
    fun getHelpResources(): List<HelpResource> {
        return listOf(
            // Financial Counseling
            HelpResource(
                title = "Financial Counseling Helpline",
                description = "Free financial advice and debt counseling. They can help negotiate with creditors and create repayment plans.",
                contactInfo = "Dial 14448 for banking-related issues",
                category = "Financial Counseling"
            ),
            HelpResource(
                title = "Debt Restructuring Information",
                description = "If you have loans/EMIs you can't pay, banks offer moratorium and restructuring options. You won't lose your assets immediately — there are legal protections.",
                contactInfo = "Contact your bank's loan department and ask about restructuring",
                category = "Debt Management"
            ),

            // Employment
            HelpResource(
                title = "Government Employment Exchange",
                description = "Register for job opportunities. Also provides skill development programs and training.",
                contactInfo = "Visit your nearest employment exchange or register online at the national career service portal",
                category = "Employment"
            ),
            HelpResource(
                title = "Skill Development Programs",
                description = "Free government-sponsored skill training programs across various industries. Certification provided upon completion.",
                contactInfo = "National Skill Development portal (search for your state's program)",
                category = "Employment"
            ),

            // Government Support
            HelpResource(
                title = "Provident Fund Emergency Withdrawal",
                description = "You may be eligible to withdraw from your provident fund for medical emergencies, loss of employment (after 1 month), or other specified reasons. Partial withdrawal is possible.",
                contactInfo = "PF helpline or visit the PF member portal online",
                category = "Government Benefits"
            ),
            HelpResource(
                title = "Government Insurance Schemes",
                description = "Check eligibility for government health and life insurance schemes that provide affordable coverage during difficult times.",
                contactInfo = "Visit your bank or post office for enrollment details",
                category = "Government Benefits"
            ),

            // Mental Health
            HelpResource(
                title = "Mental Health Support",
                description = "Financial stress is real and valid. Professional counselors can help you manage anxiety during difficult times. Many offer sliding-scale fees or pro-bono sessions.",
                contactInfo = "Mental health helpline: Search for your state's free helpline number",
                category = "Mental Health"
            ),
            HelpResource(
                title = "Crisis Support Line",
                description = "If financial stress is overwhelming, talking to someone helps. Available 24/7, free, and confidential.",
                contactInfo = "National helpline numbers are available via directory services",
                category = "Mental Health"
            ),

            // Legal
            HelpResource(
                title = "Legal Aid for Financial Disputes",
                description = "If creditors are harassing you, threatening you, or using unfair practices, you have legal rights. Free legal aid is available for those who qualify.",
                contactInfo = "National Legal Services Authority helpline: 15100",
                category = "Legal Aid"
            ),
            HelpResource(
                title = "Consumer Rights Protection",
                description = "Wrongful termination, unpaid wages, or unfair employer practices — you have rights. File complaint with labor department.",
                contactInfo = "Labor helpline: 14434 | Consumer helpline: 1915",
                category = "Legal Aid"
            ),

            // Practical
            HelpResource(
                title = "EMI Moratorium Options",
                description = "Many banks allow EMI holiday of 1-3 months during job loss. Interest accrues but payments pause. Ask specifically about 'restructuring under stress'.",
                contactInfo = "Call your bank's loan department directly",
                category = "Debt Management"
            ),
            HelpResource(
                title = "Insurance Premium Grace Period",
                description = "Most insurance policies have a 30-day grace period for premium payment. Don't let policies lapse — contact your insurer about payment difficulties.",
                contactInfo = "Contact your insurance provider's customer service",
                category = "Insurance"
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Extension Strategies
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Calculates how much runway extends with specific cuts.
     *
     * Shows "if you cut items 1-N, your runway becomes X months" projections.
     *
     * @param savings Total savings
     * @param essentialExpenses Monthly essentials
     * @param cuts List of potential cuts
     * @return Map of "cuts applied" description to extended runway months
     */
    fun getExtensionStrategies(
        savings: Double,
        essentialExpenses: Double,
        cuts: List<CutSuggestion>
    ): List<Pair<String, Double>> {
        if (essentialExpenses <= 0 || savings <= 0) return emptyList()

        val strategies = mutableListOf<Pair<String, Double>>()
        var cumulativeSavings = 0.0
        val baseRunway = savings / essentialExpenses

        strategies.add(Pair("Current (no cuts)", baseRunway))

        // Progressive cuts
        val sortedCuts = cuts.filter { it.monthlyCost > 0 }.sortedByDescending { it.monthlyCost }

        sortedCuts.forEachIndexed { index, cut ->
            cumulativeSavings += cut.monthlyCost
            val newBurn = essentialExpenses - cumulativeSavings
            if (newBurn > 0) {
                val newRunway = savings / newBurn
                val description = if (index == 0) {
                    "Cut ${cut.item} (save ${formatCurrency(cut.monthlyCost)}/month)"
                } else {
                    "Cut top ${index + 1} expenses (save ${formatCurrency(cumulativeSavings)}/month)"
                }
                strategies.add(Pair(description, newRunway))
            }
        }

        return strategies
    }

    /**
     * Generates the daily survival budget breakdown.
     *
     * @param monthlyEssentials Monthly essential expenses
     * @return Formatted daily budget breakdown
     */
    fun getDailySurvivalBudget(monthlyEssentials: Double): String {
        val daily = monthlyEssentials / 30.0
        val food = daily * 0.45  // ~45% on food
        val transport = daily * 0.15  // ~15% transport
        val utilities = daily * 0.10  // ~10% utilities
        val buffer = daily * 0.30  // ~30% buffer/other essentials

        return buildString {
            appendLine("📊 Daily Survival Budget: ${formatCurrency(daily)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("  🍽️ Food & Groceries: ~${formatCurrency(food)}")
            appendLine("  🚌 Transport: ~${formatCurrency(transport)}")
            appendLine("  💡 Utilities (daily share): ~${formatCurrency(utilities)}")
            appendLine("  🛡️ Buffer/Other: ~${formatCurrency(buffer)}")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("")
            appendLine("💡 Tips:")
            appendLine("  • Buy groceries weekly (less waste, better budgeting)")
            appendLine("  • Cook at home — saves 60-70% over eating out")
            appendLine("  • Use public transport or pool rides")
            appendLine("  • Track every expense — awareness = control")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Reassurance & Emotional Support
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates compassionate, fact-based reassurance message.
     *
     * Uses ACTUAL numbers to replace anxiety with information. Never minimizes
     * the difficulty but always highlights what's working.
     */
    private fun generateReassurance(
        runway: Double,
        savings: Double,
        essentialExpenses: Double
    ): String {
        return when {
            runway >= 6 -> buildString {
                appendLine("💙 Take a deep breath. Here's the reality:")
                appendLine()
                appendLine("You have ${String.format("%.1f", runway)} months of runway. " +
                        "That's ${(runway * 30).roundToInt()} days of safety, even if ZERO income comes in.")
                appendLine()
                appendLine("This is more time than most people have. You built this safety net " +
                        "through your past discipline. It's doing its job right now — protecting you.")
                appendLine()
                appendLine("Use this time wisely. You don't need to panic-apply to everything. " +
                        "You can be selective. You have time.")
                appendLine()
                appendLine("🌟 This is temporary. You will get through this.")
            }

            runway >= 3 -> buildString {
                appendLine("💙 Here's your situation — clearly, without sugar-coating:")
                appendLine()
                appendLine("You have ${String.format("%.1f", runway)} months of runway. " +
                        "That's real safety — enough time to find new income without desperation.")
                appendLine()
                appendLine("Your savings of ${formatCurrency(savings)} are protecting you right now. " +
                        "With the survival budget below, you can stretch this further.")
                appendLine()
                appendLine("Action items: Start actively looking for income sources this week. " +
                        "You have time, but use it — don't let weeks slip by without progress.")
                appendLine()
                appendLine("🌟 Many people recover from this exact situation. You will too.")
            }

            runway >= 1 -> buildString {
                appendLine("💙 I won't pretend this isn't tight. But let's be clear about what you have:")
                appendLine()
                appendLine("${String.format("%.1f", runway)} months — that's ${(runway * 30).roundToInt()} days. " +
                        "Not ideal, but NOT zero. Every day counts now.")
                appendLine()
                appendLine("Immediate priorities:")
                appendLine("1. Cut every non-essential expense TODAY (see list below)")
                appendLine("2. Pursue any immediate income opportunity (freelance, part-time, gig work)")
                appendLine("3. If you have loans/EMIs, call your bank about moratorium options NOW")
                appendLine("4. Check if you can withdraw from PF (emergency withdrawal rules apply)")
                appendLine()
                appendLine("🌟 Tight doesn't mean impossible. Focus on today's step, not next month's worry.")
            }

            else -> buildString {
                appendLine("💙 This is a difficult moment, but you're taking the right step by looking at the numbers.")
                appendLine()
                appendLine("Your runway is very short. Here's what to do RIGHT NOW:")
                appendLine("1. Call family/close friends who can help bridge this gap — there is no shame in asking.")
                appendLine("2. Contact your bank about any loan moratorium (they'd rather restructure than default)")
                appendLine("3. Look into emergency government assistance programs")
                appendLine("4. Take any available income opportunity — even temporary/part-time")
                appendLine()
                appendLine("Remember: this is a financial situation, not a reflection of your worth. " +
                        "Millions of people have been exactly here and recovered. You will too.")
                appendLine()
                appendLine("🌟 One day at a time. You've survived 100% of your worst days so far.")
            }
        }
    }

    /**
     * Gets daily motivational/support message during survival mode.
     *
     * Changes based on how many days into survival mode the user is.
     *
     * @return Encouraging message for today
     */
    fun getDailyEncouragement(): String? {
        if (!isActive()) return null

        val activatedDate = getPrefs().getLong(KEY_ACTIVATED_DATE, 0L)
        if (activatedDate == 0L) return null

        val daysIn = ((System.currentTimeMillis() - activatedDate) / (24 * 60 * 60 * 1000)).toInt()

        return when {
            daysIn == 0 -> "Day 1. You've taken control by activating survival mode. Knowledge is power — you know your numbers now. 💪"
            daysIn <= 3 -> "Day $daysIn. The first few days are the hardest emotionally. You're doing the right things. Stay the course."
            daysIn <= 7 -> "Day $daysIn — one week in. You're building a new routine. Every day within budget is a win. Celebrate small victories."
            daysIn <= 14 -> "Day $daysIn. Two weeks of discipline! You've proven you can do this. Keep applying, keep networking, keep going."
            daysIn <= 30 -> "Day $daysIn. A month of resilience. This is genuinely impressive. Most people never show this kind of financial discipline."
            daysIn <= 60 -> "Day $daysIn. Still going strong. Remember: many job searches take 2-3 months. You're well within normal timelines."
            else -> "Day $daysIn. You've shown incredible strength. If the job search is longer than expected, consider expanding your options — freelance, consulting, different industry. Change is okay."
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Recovery Tracking
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Deactivates survival mode when the user recovers income.
     *
     * Generates a recovery summary and begins tracking emergency fund rebuilding.
     *
     * @param newMonthlyIncome The user's new monthly income
     * @return Recovery message and plan
     */
    fun deactivateSurvivalMode(newMonthlyIncome: Double): String {
        val activatedDate = getPrefs().getLong(KEY_ACTIVATED_DATE, 0L)
        val daysInSurvival = if (activatedDate > 0) {
            ((System.currentTimeMillis() - activatedDate) / (24 * 60 * 60 * 1000)).toInt()
        } else {
            0
        }

        getPrefs().edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .putLong(KEY_DEACTIVATED_DATE, System.currentTimeMillis())
            .putFloat(KEY_RECOVERY_INCOME, newMonthlyIncome.toFloat())
            .apply()

        return buildString {
            appendLine("🎉 CONGRATULATIONS! You made it through!")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📅 Days in survival mode: $daysInSurvival")
            appendLine("💰 New income: ${formatCurrency(newMonthlyIncome)}/month")
            appendLine()
            appendLine("You showed incredible discipline and resilience. ")
            appendLine("Not everyone can navigate a financial crisis this well.")
            appendLine()
            appendLine("🔄 WHAT'S NEXT — Rebuilding Phase:")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("1. Rebuild your emergency fund (target: 6 months of expenses)")
            appendLine("2. Gradually resume cancelled subscriptions (only ones you actually missed)")
            appendLine("3. Keep the spending awareness you built — it's a superpower")
            appendLine("4. Consider: you survived on the survival budget. " +
                    "The gap between that and your income? That's your true savings potential!")
            appendLine()
            appendLine("💪 You're stronger and wiser now. The habits you built in survival mode ")
            appendLine("will serve you for the rest of your financial life.")
        }
    }

    /**
     * Gets recovery/rebuilding progress after exiting survival mode.
     *
     * @param currentSavings Current savings after recovery
     * @param monthlyExpenses Current monthly expenses
     * @return [RecoveryProgress] with rebuilding status
     */
    fun getRecoveryProgress(currentSavings: Double, monthlyExpenses: Double): RecoveryProgress? {
        val deactivatedDate = getPrefs().getLong(KEY_DEACTIVATED_DATE, 0L)
        if (deactivatedDate == 0L) return null

        val activatedDate = getPrefs().getLong(KEY_ACTIVATED_DATE, 0L)
        val daysInSurvival = if (activatedDate > 0) {
            ((deactivatedDate - activatedDate) / (24 * 60 * 60 * 1000)).toInt()
        } else {
            0
        }

        val targetEmergencyFund = monthlyExpenses * 6 // 6 months target
        val progress = if (targetEmergencyFund > 0) {
            minOf(((currentSavings / targetEmergencyFund) * 100).roundToInt(), 100)
        } else {
            0
        }

        val newIncome = getPrefs().getFloat(KEY_RECOVERY_INCOME, 0f).toDouble()

        val message = when {
            progress >= 100 -> "🏆 Amazing! Your emergency fund is fully rebuilt (6 months of expenses). You're now better prepared than before!"
            progress >= 75 -> "🌟 You're at ${progress}% of your 6-month emergency fund goal. Almost there!"
            progress >= 50 -> "📈 Half way! ${progress}% of your emergency fund goal reached. You're building back strong."
            progress >= 25 -> "💪 ${progress}% rebuilt. Great progress! Every month brings more security."
            else -> "🌱 Rebuilding started (${progress}% of 6-month goal). Be patient — you're moving in the right direction."
        }

        return RecoveryProgress(
            daysInSurvivalMode = daysInSurvival,
            amountSpentDuringSurvival = 0.0, // Would need transaction tracking integration
            amountSavedByCutting = 0.0, // Would need cut tracking
            emergencyFundRebuildProgress = progress,
            newMonthlyIncome = newIncome,
            message = message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // State Management
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Checks if survival mode is currently active.
     *
     * @return true if the user is in survival mode
     */
    fun isActive(): Boolean {
        return getPrefs().getBoolean(KEY_IS_ACTIVE, false)
    }

    /**
     * Gets the current survival plan (if active).
     *
     * @return The active [SurvivalPlan] or null if not in survival mode
     */
    fun getCurrentPlan(): SurvivalPlan? {
        if (!isActive()) return null
        val json = getPrefs().getString(KEY_PLAN, null) ?: return null
        return try {
            gson.fromJson(json, SurvivalPlan::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Updates the survival plan (e.g., when user marks items as cancelled).
     *
     * @param plan Updated plan
     */
    fun updatePlan(plan: SurvivalPlan) {
        savePlan(plan)
    }

    private fun savePlan(plan: SurvivalPlan) {
        val json = gson.toJson(plan)
        getPrefs().edit().putString(KEY_PLAN, json).apply()
    }

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Gets generic cancellation guidance for a category.
     */
    private fun getCancellationGuide(category: String): String {
        return when (category.lowercase()) {
            "streaming", "entertainment" ->
                "Open the service's app/website → Account Settings → Cancel/Pause subscription. Most allow pausing without losing your data."
            "music" ->
                "App → Account → Cancel subscription. Your playlists will remain; you'll just hear ads or lose offline access."
            "food delivery", "dining" ->
                "Delete food delivery apps from your phone. Out of sight = out of mind. Cook at home."
            "fitness", "gym" ->
                "Visit the gym/call them and ask about pausing membership. Most have a 'freeze' option for 1-3 months."
            "cloud", "storage" ->
                "Downgrade to free tier. Move important files to local storage first."
            "shopping" ->
                "Unsubscribe from deal emails. Remove saved cards from shopping sites. Add friction to purchasing."
            else ->
                "Check the service's app or website for cancellation/pause options. Many allow pausing instead of cancelling."
        }
    }

    /**
     * Formats amount as Indian currency.
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
