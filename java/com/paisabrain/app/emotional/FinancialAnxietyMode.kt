package com.paisabrain.app.emotional

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Financial Anxiety Support System.
 *
 * Detects stress-spending patterns and provides calming interventions
 * to help users regain perspective during emotionally-driven spending episodes.
 *
 * Detection heuristics:
 * - Sudden spike in spending relative to daily average
 * - Spending at unusual hours (midnight–5 AM)
 * - Multiple small purchases in rapid succession (3+ within 30 minutes)
 *
 * Interventions:
 * - Real financial picture display (balance, income, runway, safety buffer)
 * - Breathing exercise integration (4-7-8 technique)
 * - Affirmation messages grounded in actual financial data
 * - Perspective Mode: contextualizes spending relative to income
 * - Cool-down suggestions with timer
 * - Weekly emotional spending report
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a spending transaction used for pattern analysis.
 *
 * @property id Unique identifier for the transaction.
 * @property amount Transaction amount in INR.
 * @property category Spending category (e.g., "food", "shopping", "entertainment").
 * @property timestamp When the transaction occurred.
 * @property merchant Merchant or payee description.
 * @property isRecurring Whether this is a scheduled recurring payment.
 */
data class SpendingTransaction(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val category: String,
    val timestamp: LocalDateTime,
    val merchant: String = "",
    val isRecurring: Boolean = false
)

/**
 * Describes the user's real financial picture for calming display.
 *
 * @property currentBalance Current account balance in INR.
 * @property upcomingIncome Next expected income amount in INR.
 * @property upcomingIncomeDate When the next income is expected.
 * @property daysOfRunway How many days current balance can sustain at average daily spend.
 * @property safetyBuffer Amount in savings/emergency fund in INR.
 * @property monthlyIncome Total monthly income in INR.
 */
data class FinancialSnapshot(
    val currentBalance: Double,
    val upcomingIncome: Double,
    val upcomingIncomeDate: LocalDate,
    val daysOfRunway: Int,
    val safetyBuffer: Double,
    val monthlyIncome: Double
)

/**
 * A detected stress-spending pattern episode.
 *
 * @property id Unique identifier for this episode.
 * @property detectedAt When the pattern was detected.
 * @property triggerType What triggered the detection.
 * @property involvedTransactions Transaction IDs that form the pattern.
 * @property severityScore Score from 0.0 (mild) to 1.0 (severe).
 * @property interventionShown Whether an intervention was presented.
 */
data class StressEpisode(
    val id: String = UUID.randomUUID().toString(),
    val detectedAt: LocalDateTime = LocalDateTime.now(),
    val triggerType: StressTriggerType,
    val involvedTransactions: List<String>,
    val severityScore: Double,
    val interventionShown: Boolean = false
)

/**
 * Types of stress-spending triggers detected by the system.
 */
enum class StressTriggerType {
    /** Spending significantly above daily average. */
    SPENDING_SPIKE,

    /** Transactions occurring between midnight and 5 AM. */
    UNUSUAL_HOURS,

    /** 3+ small purchases within a 30-minute window. */
    RAPID_SUCCESSION,

    /** Combination of multiple trigger types simultaneously. */
    COMPOUND
}

/**
 * Breathing exercise phase for the 4-7-8 technique.
 *
 * @property phase Name of the current phase.
 * @property durationSeconds Duration for this phase in seconds.
 * @property instruction User-facing instruction text.
 */
data class BreathingPhase(
    val phase: String,
    val durationSeconds: Int,
    val instruction: String
)

/**
 * A perspective comparison to contextualize spending.
 *
 * @property amount The spending amount being contextualized.
 * @property percentOfIncome Percentage of monthly income.
 * @property equivalentDays How many days of average spending this represents.
 * @property message User-facing perspective message.
 */
data class PerspectiveView(
    val amount: Double,
    val percentOfIncome: Double,
    val equivalentDays: Double,
    val message: String
)

/**
 * Cool-down suggestion presented during stress episodes.
 *
 * @property suggestion The suggestion text.
 * @property cooldownMinutes Recommended minutes to wait before returning.
 * @property icon Emoji or icon identifier.
 */
data class CoolDownSuggestion(
    val suggestion: String,
    val cooldownMinutes: Int,
    val icon: String
)

/**
 * Weekly emotional spending report entry.
 *
 * @property weekStart Start date of the reporting week.
 * @property weekEnd End date of the reporting week.
 * @property emotionalTransactions Transactions flagged as likely emotional spending.
 * @property totalEmotionalAmount Total amount spent emotionally.
 * @property patterns Detected patterns description.
 * @property summaryMessage User-facing summary message.
 */
data class EmotionalSpendingReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val emotionalTransactions: List<EmotionalTransaction>,
    val totalEmotionalAmount: Double,
    val patterns: List<String>,
    val summaryMessage: String
)

/**
 * A transaction flagged as likely emotional spending.
 *
 * @property transaction The original transaction.
 * @property reason Why it was flagged as emotional.
 * @property confidence Confidence score 0.0–1.0.
 */
data class EmotionalTransaction(
    val transaction: SpendingTransaction,
    val reason: String,
    val confidence: Double
)

/**
 * Affirmation message based on real financial data.
 *
 * @property message The affirmation text.
 * @property dataPoint The financial data backing the affirmation.
 * @property emoji Accompanying emoji.
 */
data class FinancialAffirmation(
    val message: String,
    val dataPoint: String,
    val emoji: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Core Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Financial Anxiety Mode — the core engine for detecting stress-spending
 * and delivering calming interventions.
 *
 * Usage:
 * ```kotlin
 * val anxietyMode = FinancialAnxietyMode(
 *     dailyAverageSpend = 1500.0,
 *     monthlyIncome = 60000.0
 * )
 *
 * // Feed transactions as they occur
 * anxietyMode.onNewTransaction(transaction)
 *
 * // Check if intervention should be shown
 * if (anxietyMode.shouldShowIntervention()) {
 *     val snapshot = anxietyMode.generateCalmingSnapshot(financialData)
 *     val breathing = anxietyMode.getBreathingExercise()
 *     // Display calming UI
 * }
 * ```
 *
 * @property dailyAverageSpend The user's average daily spending in INR.
 * @property monthlyIncome The user's total monthly income in INR.
 * @property spikeThresholdMultiplier How many times above average triggers a spike (default 2.5x).
 * @property rapidSuccessionWindow Minutes within which multiple purchases trigger detection.
 * @property rapidSuccessionCount Minimum purchases within window to trigger detection.
 * @property unusualHoursStart Start of "unusual hours" (default midnight).
 * @property unusualHoursEnd End of "unusual hours" (default 5 AM).
 */
class FinancialAnxietyMode(
    private val dailyAverageSpend: Double,
    private val monthlyIncome: Double,
    private val spikeThresholdMultiplier: Double = 2.5,
    private val rapidSuccessionWindow: Long = 30L, // minutes
    private val rapidSuccessionCount: Int = 3,
    private val unusualHoursStart: LocalTime = LocalTime.MIDNIGHT,
    private val unusualHoursEnd: LocalTime = LocalTime.of(5, 0)
) {
    private val todayTransactions: MutableList<SpendingTransaction> = mutableListOf()
    private val weekTransactions: MutableList<SpendingTransaction> = mutableListOf()
    private val stressEpisodes: MutableList<StressEpisode> = mutableListOf()
    private var lastInterventionTime: LocalDateTime? = null

    companion object {
        /** Minimum minutes between showing interventions to avoid fatigue. */
        private const val INTERVENTION_COOLDOWN_MINUTES = 60L

        /** Number of 4-7-8 breathing cycles recommended. */
        private const val BREATHING_CYCLES = 4
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction Processing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Process a new transaction and check for stress-spending patterns.
     *
     * Call this method every time a new spending transaction is detected.
     * It automatically maintains the rolling window of recent transactions
     * and checks all stress-trigger heuristics.
     *
     * @param transaction The new spending transaction.
     * @return A [StressEpisode] if a pattern was detected, null otherwise.
     */
    fun onNewTransaction(transaction: SpendingTransaction): StressEpisode? {
        if (transaction.isRecurring) return null

        todayTransactions.add(transaction)
        weekTransactions.add(transaction)
        pruneOldTransactions()

        val triggers = mutableListOf<StressTriggerType>()

        if (detectSpendingSpike()) triggers.add(StressTriggerType.SPENDING_SPIKE)
        if (detectUnusualHours(transaction)) triggers.add(StressTriggerType.UNUSUAL_HOURS)
        if (detectRapidSuccession()) triggers.add(StressTriggerType.RAPID_SUCCESSION)

        if (triggers.isEmpty()) return null

        val triggerType = if (triggers.size > 1) StressTriggerType.COMPOUND else triggers.first()
        val severity = calculateSeverity(triggers, transaction)

        val episode = StressEpisode(
            triggerType = triggerType,
            involvedTransactions = getRecentTransactionIds(),
            severityScore = severity
        )
        stressEpisodes.add(episode)

        return episode
    }

    /**
     * Determines whether a calming intervention should be displayed.
     *
     * Accounts for cooldown period to prevent intervention fatigue.
     *
     * @return True if an intervention should be shown now.
     */
    fun shouldShowIntervention(): Boolean {
        val lastEpisode = stressEpisodes.lastOrNull() ?: return false

        if (lastEpisode.interventionShown) return false

        lastInterventionTime?.let { lastTime ->
            val minutesSince = ChronoUnit.MINUTES.between(lastTime, LocalDateTime.now())
            if (minutesSince < INTERVENTION_COOLDOWN_MINUTES) return false
        }

        return lastEpisode.severityScore >= 0.4
    }

    /**
     * Mark the latest episode as having shown an intervention.
     */
    fun markInterventionShown() {
        stressEpisodes.lastOrNull()?.let { episode ->
            val index = stressEpisodes.indexOf(episode)
            stressEpisodes[index] = episode.copy(interventionShown = true)
        }
        lastInterventionTime = LocalDateTime.now()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Calming Snapshot
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a calming financial snapshot message.
     *
     * Displays: "Hey, I noticed you're spending more than usual.
     * Here's your REAL financial picture:" followed by actual balance,
     * upcoming income, days of runway, and safety buffer.
     *
     * @param snapshot The user's current financial snapshot.
     * @return A formatted calming message string.
     */
    fun generateCalmingMessage(snapshot: FinancialSnapshot): String {
        val daysUntilIncome = ChronoUnit.DAYS.between(LocalDate.now(), snapshot.upcomingIncomeDate)

        return buildString {
            appendLine("Hey, I noticed you're spending more than usual.")
            appendLine("Here's your REAL financial picture:")
            appendLine()
            appendLine("💰 Current Balance: ₹${formatAmount(snapshot.currentBalance)}")
            appendLine("📅 Next Income: ₹${formatAmount(snapshot.upcomingIncome)} in $daysUntilIncome days")
            appendLine("🛤️ Runway: ${snapshot.daysOfRunway} days at your usual pace")
            appendLine("🛡️ Safety Buffer: ₹${formatAmount(snapshot.safetyBuffer)}")
            appendLine()
            appendLine("You're okay. Take a breath.")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Breathing Exercise (4-7-8 Technique)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the breathing exercise sequence using the 4-7-8 technique.
     *
     * The 4-7-8 technique:
     * - Inhale for 4 seconds
     * - Hold for 7 seconds
     * - Exhale for 8 seconds
     * - Repeat 4 cycles
     *
     * @return A list of [BreathingPhase] objects representing the full exercise.
     */
    fun getBreathingExercise(): List<BreathingPhase> {
        val phases = mutableListOf<BreathingPhase>()

        repeat(BREATHING_CYCLES) { cycle ->
            phases.add(
                BreathingPhase(
                    phase = "Inhale",
                    durationSeconds = 4,
                    instruction = "Breathe in slowly through your nose… (${cycle + 1}/$BREATHING_CYCLES)"
                )
            )
            phases.add(
                BreathingPhase(
                    phase = "Hold",
                    durationSeconds = 7,
                    instruction = "Hold your breath gently… (${cycle + 1}/$BREATHING_CYCLES)"
                )
            )
            phases.add(
                BreathingPhase(
                    phase = "Exhale",
                    durationSeconds = 8,
                    instruction = "Exhale slowly through your mouth… (${cycle + 1}/$BREATHING_CYCLES)"
                )
            )
        }

        return phases
    }

    /**
     * Total duration of the full breathing exercise in seconds.
     */
    fun getBreathingExerciseDurationSeconds(): Int = BREATHING_CYCLES * (4 + 7 + 8)

    // ─────────────────────────────────────────────────────────────────────────
    // Affirmation Messages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates affirmation messages grounded in actual financial data.
     *
     * Instead of generic motivational quotes, these affirmations reference
     * the user's real numbers to provide comfort based on facts.
     *
     * @param snapshot The user's current financial snapshot.
     * @return A list of data-backed affirmation messages.
     */
    fun generateAffirmations(snapshot: FinancialSnapshot): List<FinancialAffirmation> {
        val affirmations = mutableListOf<FinancialAffirmation>()

        // Savings-based affirmation
        if (snapshot.safetyBuffer > 0) {
            val monthsOfSecurity = (snapshot.safetyBuffer / (dailyAverageSpend * 30)).toInt()
            affirmations.add(
                FinancialAffirmation(
                    message = "You have ₹${formatAmount(snapshot.safetyBuffer)} saved. " +
                            "That's $monthsOfSecurity months of security. " +
                            "You're doing better than you think.",
                    dataPoint = "Safety buffer: ₹${formatAmount(snapshot.safetyBuffer)}",
                    emoji = "🛡️"
                )
            )
        }

        // Runway-based affirmation
        if (snapshot.daysOfRunway > 7) {
            affirmations.add(
                FinancialAffirmation(
                    message = "Even without any new income, you have ${snapshot.daysOfRunway} days " +
                            "of runway. You're not in danger.",
                    dataPoint = "Days of runway: ${snapshot.daysOfRunway}",
                    emoji = "🛤️"
                )
            )
        }

        // Income proximity affirmation
        val daysUntilIncome = ChronoUnit.DAYS.between(LocalDate.now(), snapshot.upcomingIncomeDate)
        if (daysUntilIncome <= 7) {
            affirmations.add(
                FinancialAffirmation(
                    message = "Your next income of ₹${formatAmount(snapshot.upcomingIncome)} " +
                            "arrives in just $daysUntilIncome days. You'll be fine.",
                    dataPoint = "Income in $daysUntilIncome days",
                    emoji = "📅"
                )
            )
        }

        // Balance-based affirmation
        if (snapshot.currentBalance > dailyAverageSpend * 3) {
            affirmations.add(
                FinancialAffirmation(
                    message = "Your balance of ₹${formatAmount(snapshot.currentBalance)} " +
                            "covers more than ${(snapshot.currentBalance / dailyAverageSpend).toInt()} " +
                            "days of normal spending. One purchase doesn't change that.",
                    dataPoint = "Balance: ₹${formatAmount(snapshot.currentBalance)}",
                    emoji = "💰"
                )
            )
        }

        // General reassurance if everything looks stable
        if (affirmations.isEmpty()) {
            affirmations.add(
                FinancialAffirmation(
                    message = "You're tracking your finances. That alone puts you ahead. " +
                            "Awareness is the first step to control.",
                    dataPoint = "Active tracking",
                    emoji = "✨"
                )
            )
        }

        return affirmations
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Perspective Mode
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a perspective view for a given spending amount.
     *
     * Shows spending in context: what percentage of income it represents,
     * and provides a reassuring or cautioning message accordingly.
     *
     * Example output: "This ₹3000 is 2% of your monthly income.
     * One bad day doesn't define your finances."
     *
     * @param amount The spending amount to contextualize.
     * @return A [PerspectiveView] with contextual information.
     */
    fun generatePerspective(amount: Double): PerspectiveView {
        val percentOfIncome = (amount / monthlyIncome) * 100.0
        val equivalentDays = amount / dailyAverageSpend

        val message = when {
            percentOfIncome < 1.0 -> "This ₹${formatAmount(amount)} is less than 1% of your " +
                    "monthly income. Barely a blip. You're fine."

            percentOfIncome < 5.0 -> "This ₹${formatAmount(amount)} is ${String.format("%.1f", percentOfIncome)}% " +
                    "of your monthly income. One bad day doesn't define your finances."

            percentOfIncome < 10.0 -> "This ₹${formatAmount(amount)} is ${String.format("%.1f", percentOfIncome)}% " +
                    "of your monthly income. Noticeable, but recoverable. " +
                    "You have the rest of the month."

            percentOfIncome < 25.0 -> "This ₹${formatAmount(amount)} is ${String.format("%.1f", percentOfIncome)}% " +
                    "of your monthly income. That's significant. " +
                    "Maybe pause and check if this aligns with your goals."

            else -> "This ₹${formatAmount(amount)} is ${String.format("%.1f", percentOfIncome)}% " +
                    "of your monthly income. This is a big spend. " +
                    "Take a moment — is this a need or an impulse?"
        }

        return PerspectiveView(
            amount = amount,
            percentOfIncome = percentOfIncome,
            equivalentDays = equivalentDays,
            message = message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cool-Down Suggestions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns cool-down suggestions for the user during a stress episode.
     *
     * Provides actionable, non-judgmental suggestions to break the
     * emotional spending cycle.
     *
     * @param severity The severity score (0.0–1.0) of the detected episode.
     * @return A list of [CoolDownSuggestion] objects.
     */
    fun getCoolDownSuggestions(severity: Double = 0.5): List<CoolDownSuggestion> {
        val suggestions = mutableListOf<CoolDownSuggestion>()

        suggestions.add(
            CoolDownSuggestion(
                suggestion = "Take a walk. Even 5 minutes outside resets your brain.",
                cooldownMinutes = 10,
                icon = "🚶"
            )
        )

        suggestions.add(
            CoolDownSuggestion(
                suggestion = "Close shopping apps. Out of sight, out of mind.",
                cooldownMinutes = 60,
                icon = "📱"
            )
        )

        suggestions.add(
            CoolDownSuggestion(
                suggestion = "Come back in 1 hour. If you still want it then, it's probably real.",
                cooldownMinutes = 60,
                icon = "⏰"
            )
        )

        if (severity > 0.6) {
            suggestions.add(
                CoolDownSuggestion(
                    suggestion = "Drink a glass of water. Physical needs can masquerade as wants.",
                    cooldownMinutes = 5,
                    icon = "💧"
                )
            )

            suggestions.add(
                CoolDownSuggestion(
                    suggestion = "Call or text a friend. Connection beats consumption.",
                    cooldownMinutes = 15,
                    icon = "📞"
                )
            )
        }

        if (severity > 0.8) {
            suggestions.add(
                CoolDownSuggestion(
                    suggestion = "Put your phone in another room for 30 minutes. You've got this.",
                    cooldownMinutes = 30,
                    icon = "🔇"
                )
            )
        }

        return suggestions
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Weekly Emotional Spending Report
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates the weekly emotional spending report.
     *
     * Analyzes the past week's transactions and identifies those that were
     * likely driven by emotion rather than need, based on:
     * - Late-night timing (midnight–5 AM)
     * - High variance from category average
     * - Rapid succession pattern
     * - Category patterns (e.g., impulsive categories like shopping, food delivery)
     *
     * @param allWeekTransactions All transactions from the past week.
     * @param categoryAverages Map of category to average weekly spend.
     * @return An [EmotionalSpendingReport] for the week.
     */
    fun generateWeeklyReport(
        allWeekTransactions: List<SpendingTransaction>,
        categoryAverages: Map<String, Double> = emptyMap()
    ): EmotionalSpendingReport {
        val weekStart = LocalDate.now().minusDays(7)
        val weekEnd = LocalDate.now()

        val emotionalTransactions = mutableListOf<EmotionalTransaction>()
        val patterns = mutableListOf<String>()

        // Detect late-night spending
        val lateNightTransactions = allWeekTransactions.filter { txn ->
            val time = txn.timestamp.toLocalTime()
            time.isAfter(unusualHoursStart) && time.isBefore(unusualHoursEnd)
        }
        lateNightTransactions.forEach { txn ->
            emotionalTransactions.add(
                EmotionalTransaction(
                    transaction = txn,
                    reason = "Late night spending (${txn.timestamp.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))})",
                    confidence = 0.7
                )
            )
        }
        if (lateNightTransactions.isNotEmpty()) {
            patterns.add("${lateNightTransactions.size} late-night transactions detected")
        }

        // Detect high-variance spending
        allWeekTransactions.forEach { txn ->
            val categoryAvg = categoryAverages[txn.category]
            if (categoryAvg != null && txn.amount > categoryAvg * 2.0) {
                val alreadyFlagged = emotionalTransactions.any { it.transaction.id == txn.id }
                if (!alreadyFlagged) {
                    emotionalTransactions.add(
                        EmotionalTransaction(
                            transaction = txn,
                            reason = "Unusually high for ${txn.category} category " +
                                    "(₹${formatAmount(txn.amount)} vs avg ₹${formatAmount(categoryAvg)})",
                            confidence = 0.6
                        )
                    )
                }
            }
        }

        // Detect rapid succession bursts
        val sortedByTime = allWeekTransactions.sortedBy { it.timestamp }
        var burstCount = 0
        for (i in 2 until sortedByTime.size) {
            val windowStart = sortedByTime[i - 2].timestamp
            val windowEnd = sortedByTime[i].timestamp
            val minutesBetween = ChronoUnit.MINUTES.between(windowStart, windowEnd)
            if (minutesBetween <= rapidSuccessionWindow) {
                burstCount++
            }
        }
        if (burstCount > 0) {
            patterns.add("$burstCount rapid-succession spending bursts")
        }

        val totalEmotional = emotionalTransactions.sumOf { it.transaction.amount }

        val summaryMessage = when {
            emotionalTransactions.isEmpty() ->
                "Great week! No emotional spending patterns detected. You stayed intentional. 🎯"

            emotionalTransactions.size <= 2 ->
                "${emotionalTransactions.size} transaction this week was likely emotional spending. " +
                        "Total: ₹${formatAmount(totalEmotional)}. Awareness is power."

            else ->
                "${emotionalTransactions.size} transactions this week were likely emotional spending " +
                        "(late night + high variance). Total: ₹${formatAmount(totalEmotional)}. " +
                        "No judgment — now you know your triggers."
        }

        return EmotionalSpendingReport(
            weekStart = weekStart,
            weekEnd = weekEnd,
            emotionalTransactions = emotionalTransactions,
            totalEmotionalAmount = totalEmotional,
            patterns = patterns,
            summaryMessage = summaryMessage
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detects if today's spending represents a significant spike.
     */
    private fun detectSpendingSpike(): Boolean {
        val todayTotal = todayTransactions.sumOf { it.amount }
        return todayTotal > dailyAverageSpend * spikeThresholdMultiplier
    }

    /**
     * Detects if a transaction occurred during unusual hours.
     */
    private fun detectUnusualHours(transaction: SpendingTransaction): Boolean {
        val time = transaction.timestamp.toLocalTime()
        return if (unusualHoursStart.isBefore(unusualHoursEnd)) {
            time.isAfter(unusualHoursStart) && time.isBefore(unusualHoursEnd)
        } else {
            // Handles wrap-around (e.g., 11 PM to 5 AM)
            time.isAfter(unusualHoursStart) || time.isBefore(unusualHoursEnd)
        }
    }

    /**
     * Detects rapid-succession purchases within the configured window.
     */
    private fun detectRapidSuccession(): Boolean {
        if (todayTransactions.size < rapidSuccessionCount) return false

        val recent = todayTransactions.sortedByDescending { it.timestamp }
            .take(rapidSuccessionCount)

        if (recent.size < rapidSuccessionCount) return false

        val oldest = recent.last().timestamp
        val newest = recent.first().timestamp
        val minutesBetween = ChronoUnit.MINUTES.between(oldest, newest)

        return minutesBetween <= rapidSuccessionWindow
    }

    /**
     * Calculates severity score based on triggers and transaction context.
     */
    private fun calculateSeverity(
        triggers: List<StressTriggerType>,
        latestTransaction: SpendingTransaction
    ): Double {
        var score = 0.0

        // Base score from trigger count
        score += triggers.size * 0.25

        // Additional weight for compound triggers
        if (triggers.size > 1) score += 0.1

        // Spending amount relative to daily average
        val amountRatio = latestTransaction.amount / dailyAverageSpend
        score += (amountRatio * 0.1).coerceAtMost(0.3)

        // Time-of-day penalty (worse during unusual hours)
        if (StressTriggerType.UNUSUAL_HOURS in triggers) {
            score += 0.1
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Gets IDs of the most recent transactions involved in the pattern.
     */
    private fun getRecentTransactionIds(): List<String> {
        return todayTransactions.sortedByDescending { it.timestamp }
            .take(5)
            .map { it.id }
    }

    /**
     * Removes transactions older than today from the daily list
     * and older than 7 days from the weekly list.
     */
    private fun pruneOldTransactions() {
        val today = LocalDate.now()
        todayTransactions.removeAll { it.timestamp.toLocalDate() != today }

        val weekAgo = LocalDate.now().minusDays(7)
        weekTransactions.removeAll { it.timestamp.toLocalDate().isBefore(weekAgo) }
    }

    /**
     * Formats an amount for display (e.g., 15000.0 → "15,000").
     */
    private fun formatAmount(amount: Double): String {
        // Indian number formatting
        val wholePart = amount.toLong()
        val str = wholePart.toString()
        if (str.length <= 3) return str

        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)
        val formatted = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$formatted,$lastThree"
    }
}
