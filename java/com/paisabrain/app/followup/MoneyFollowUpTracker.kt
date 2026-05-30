package com.paisabrain.app.followup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Type of social debt — whether you owe someone or they owe you.
 */
enum class DebtType {
    /** You owe this amount to someone */
    I_OWE,

    /** Someone owes this amount to you */
    THEY_OWE
}

/**
 * Represents a social debt entry in the ledger.
 *
 * @property id Unique identifier
 * @property personName Name of the person involved
 * @property amount The amount in local currency (₹)
 * @property type Whether you owe or they owe
 * @property reason Description of what the debt is for
 * @property createdDate When this debt was recorded (epoch millis)
 * @property dueDate Optional deadline for settlement (epoch millis)
 * @property isSettled Whether this debt has been resolved
 * @property settledDate When the debt was settled (epoch millis), null if pending
 * @property linkedTransactionId Optional link to a detected SMS transaction
 */
data class SocialDebt(
    val id: String = UUID.randomUUID().toString(),
    val personName: String,
    val amount: Double,
    val type: DebtType,
    val reason: String,
    val createdDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val isSettled: Boolean = false,
    val settledDate: Long? = null,
    val linkedTransactionId: String? = null
)

/**
 * User's response to an incoming money prompt.
 */
enum class IncomingMoneyAction {
    /** No response needed — debt settled silently */
    SETTLED_NO_RESPONSE,

    /** Should thank the sender */
    SHOULD_THANK,

    /** Need to return the same amount later */
    NEED_TO_RETURN_SAME,

    /** Need to return a partial/different amount */
    PARTIAL_RETURN,

    /** Ignore this transaction */
    IGNORE
}

/**
 * Prompt generated when incoming money is detected.
 *
 * @property id Unique identifier
 * @property amount The received amount
 * @property senderName Person/entity who sent money (from SMS parsing)
 * @property receivedAt Timestamp of the transaction
 * @property matchedDebt If auto-matched to an existing debt, reference it
 * @property userAction User's chosen action, null if not yet responded
 * @property partialReturnAmount If PARTIAL_RETURN chosen, the amount to return
 */
data class IncomingMoneyPrompt(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val senderName: String?,
    val receivedAt: Long,
    val matchedDebt: SocialDebt? = null,
    val userAction: IncomingMoneyAction? = null,
    val partialReturnAmount: Double? = null
)

/**
 * Monthly summary of social debt activity.
 *
 * @property month The month this summary covers
 * @property settledCount Number of transactions settled
 * @property pendingIOweCount Number of pending debts you owe
 * @property pendingIOweAmount Total amount you still owe
 * @property pendingTheyOweCount Number of pending debts owed to you
 * @property pendingTheyOweAmount Total amount owed to you
 */
data class MonthlyDebtSummary(
    val month: YearMonth,
    val settledCount: Int,
    val pendingIOweCount: Int,
    val pendingIOweAmount: Double,
    val pendingTheyOweCount: Int,
    val pendingTheyOweAmount: Double
)

/**
 * Configuration for the money follow-up tracker.
 *
 * @property agingAlertDaysIOwe Days before alerting about debts you owe
 * @property agingAlertDaysTheyOwe Days before alerting about debts owed to you
 * @property autoMatchTolerancePercent Percentage tolerance for auto-matching amounts
 * @property autoMatchWindowDays Days to look back for matching debts
 */
data class MoneyTrackerConfig(
    val agingAlertDaysIOwe: Int = 30,
    val agingAlertDaysTheyOwe: Int = 45,
    val autoMatchTolerancePercent: Double = 5.0,
    val autoMatchWindowDays: Int = 90
)

/**
 * Tracks money-related social obligations and maintains a social debt ledger.
 *
 * This tracker:
 * - Detects incoming money (from SMS transaction credits) and prompts for action
 * - Maintains a "Social Debt Ledger" of amounts owed in both directions
 * - Auto-matches incoming payments to existing debt records
 * - Provides aging alerts for long-outstanding debts
 * - Generates polite reminder messages for debts owed to you
 * - Produces monthly settlement summaries
 *
 * **All processing is entirely on-device. No internet connection required.**
 *
 * Usage:
 * ```kotlin
 * val tracker = MoneyFollowUpTracker(config)
 * tracker.addDebt(SocialDebt(personName = "Friend", amount = 500.0, type = DebtType.THEY_OWE, reason = "Dinner split"))
 * tracker.processIncomingMoney(500.0, "Friend", System.currentTimeMillis())
 * ```
 *
 * @property config Configuration parameters
 */
class MoneyFollowUpTracker(
    private val config: MoneyTrackerConfig = MoneyTrackerConfig()
) {
    private val _debts = MutableStateFlow<List<SocialDebt>>(emptyList())

    /** Observable list of all social debts (pending and settled). */
    val debts: StateFlow<List<SocialDebt>> = _debts.asStateFlow()

    private val _pendingPrompts = MutableStateFlow<List<IncomingMoneyPrompt>>(emptyList())

    /** Observable list of pending incoming money prompts awaiting user action. */
    val pendingPrompts: StateFlow<List<IncomingMoneyPrompt>> = _pendingPrompts.asStateFlow()

    private val _monthlySummary = MutableStateFlow<MonthlyDebtSummary?>(null)

    /** Current month's debt summary. */
    val monthlySummary: StateFlow<MonthlyDebtSummary?> = _monthlySummary.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Adds a new social debt to the ledger.
     *
     * @param debt The debt entry to add
     */
    fun addDebt(debt: SocialDebt) {
        _debts.value = _debts.value + debt
        recalculateSummary()
    }

    /**
     * Processes an incoming money transaction detected from SMS.
     *
     * This method:
     * 1. Checks if the amount matches any existing "THEY_OWE" debt
     * 2. Creates a prompt for the user to categorize the transaction
     * 3. If a match is found, pre-fills the prompt with the match suggestion
     *
     * @param amount The received amount
     * @param senderName The sender's name (parsed from SMS), may be null
     * @param receivedAt Timestamp of the transaction
     * @return The generated prompt for user action
     */
    fun processIncomingMoney(amount: Double, senderName: String?, receivedAt: Long): IncomingMoneyPrompt {
        val matchedDebt = findMatchingDebt(amount, senderName)

        val prompt = IncomingMoneyPrompt(
            amount = amount,
            senderName = senderName,
            receivedAt = receivedAt,
            matchedDebt = matchedDebt
        )

        _pendingPrompts.value = _pendingPrompts.value + prompt
        return prompt
    }

    /**
     * Records the user's action on an incoming money prompt.
     *
     * @param promptId The ID of the prompt
     * @param action The user's chosen action
     * @param partialAmount If action is PARTIAL_RETURN, the amount to return
     */
    fun respondToPrompt(promptId: String, action: IncomingMoneyAction, partialAmount: Double? = null) {
        val prompt = _pendingPrompts.value.find { it.id == promptId } ?: return

        // Update the prompt with user action
        _pendingPrompts.value = _pendingPrompts.value.map {
            if (it.id == promptId) it.copy(userAction = action, partialReturnAmount = partialAmount)
            else it
        }

        when (action) {
            IncomingMoneyAction.SETTLED_NO_RESPONSE -> {
                // If there's a matched debt, mark it settled
                prompt.matchedDebt?.let { markDebtSettled(it.id, prompt.id) }
            }
            IncomingMoneyAction.SHOULD_THANK -> {
                // No ledger change — just a social reminder
            }
            IncomingMoneyAction.NEED_TO_RETURN_SAME -> {
                // Create a new I_OWE debt for the same amount
                addDebt(
                    SocialDebt(
                        personName = prompt.senderName ?: "Unknown",
                        amount = prompt.amount,
                        type = DebtType.I_OWE,
                        reason = "Return money received on ${formatDate(prompt.receivedAt)}",
                        linkedTransactionId = prompt.id
                    )
                )
            }
            IncomingMoneyAction.PARTIAL_RETURN -> {
                // Create a new I_OWE debt for the partial amount
                val returnAmount = partialAmount ?: prompt.amount
                addDebt(
                    SocialDebt(
                        personName = prompt.senderName ?: "Unknown",
                        amount = returnAmount,
                        type = DebtType.I_OWE,
                        reason = "Partial return of ₹${prompt.amount} received on ${formatDate(prompt.receivedAt)}",
                        linkedTransactionId = prompt.id
                    )
                )
            }
            IncomingMoneyAction.IGNORE -> {
                // No action
            }
        }

        // Remove from pending prompts
        _pendingPrompts.value = _pendingPrompts.value.filter { it.id != promptId }
        recalculateSummary()
    }

    /**
     * Attempts to auto-match an incoming amount to an existing debt.
     *
     * Matching criteria:
     * - Amount within configured tolerance (default ±5%)
     * - Same person name (fuzzy match)
     * - Debt created within the configured window
     *
     * @param amount The received amount
     * @param senderName The sender's name
     * @return Matched debt, or null if no match found
     */
    private fun findMatchingDebt(amount: Double, senderName: String?): SocialDebt? {
        if (senderName == null) return null

        val now = System.currentTimeMillis()
        val windowMs = config.autoMatchWindowDays.toLong() * 24 * 60 * 60 * 1000
        val toleranceFactor = config.autoMatchTolerancePercent / 100.0

        return _debts.value.find { debt ->
            !debt.isSettled &&
                    debt.type == DebtType.THEY_OWE &&
                    debt.createdDate > (now - windowMs) &&
                    isAmountMatch(debt.amount, amount, toleranceFactor) &&
                    isNameMatch(debt.personName, senderName)
        }
    }

    /**
     * Checks if two amounts are within tolerance of each other.
     */
    private fun isAmountMatch(expected: Double, actual: Double, toleranceFactor: Double): Boolean {
        val lowerBound = expected * (1.0 - toleranceFactor)
        val upperBound = expected * (1.0 + toleranceFactor)
        return actual in lowerBound..upperBound
    }

    /**
     * Fuzzy name matching — case-insensitive, handles partial matches.
     */
    private fun isNameMatch(name1: String, name2: String): Boolean {
        val n1 = name1.lowercase().trim()
        val n2 = name2.lowercase().trim()
        return n1 == n2 || n1.contains(n2) || n2.contains(n1)
    }

    /**
     * Marks a debt as settled.
     *
     * @param debtId The debt to mark as settled
     * @param transactionId Optional linked transaction that settled this debt
     */
    fun markDebtSettled(debtId: String, transactionId: String? = null) {
        _debts.value = _debts.value.map { debt ->
            if (debt.id == debtId) {
                debt.copy(
                    isSettled = true,
                    settledDate = System.currentTimeMillis(),
                    linkedTransactionId = transactionId ?: debt.linkedTransactionId
                )
            } else debt
        }
        recalculateSummary()
    }

    /**
     * Gets all pending debts you owe to others.
     *
     * @return List of unsettled debts where you are the debtor
     */
    fun getPendingIOwe(): List<SocialDebt> {
        return _debts.value.filter { !it.isSettled && it.type == DebtType.I_OWE }
    }

    /**
     * Gets all pending debts others owe to you.
     *
     * @return List of unsettled debts where others are the debtor
     */
    fun getPendingTheyOwe(): List<SocialDebt> {
        return _debts.value.filter { !it.isSettled && it.type == DebtType.THEY_OWE }
    }

    /**
     * Gets debts that have aged beyond the configured alert threshold.
     *
     * @return Pair of (aged debts I owe, aged debts they owe)
     */
    fun getAgingDebts(): Pair<List<SocialDebt>, List<SocialDebt>> {
        val now = System.currentTimeMillis()

        val agedIOwe = getPendingIOwe().filter { debt ->
            val daysSinceCreated = ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            )
            daysSinceCreated >= config.agingAlertDaysIOwe
        }

        val agedTheyOwe = getPendingTheyOwe().filter { debt ->
            val daysSinceCreated = ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                LocalDate.now()
            )
            daysSinceCreated >= config.agingAlertDaysTheyOwe
        }

        return Pair(agedIOwe, agedTheyOwe)
    }

    /**
     * Generates a polite reminder message for a debt owed to you.
     *
     * This creates a copyable text that the user can manually send. The app
     * never sends messages automatically.
     *
     * @param debt The debt to generate a reminder for
     * @return A polite, ready-to-copy reminder message
     */
    fun generatePoliteReminder(debt: SocialDebt): String {
        require(debt.type == DebtType.THEY_OWE) { "Can only generate reminders for debts owed to you" }

        val daysSince = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
            LocalDate.now()
        )

        val timeDescription = when {
            daysSince <= 7 -> "the other day"
            daysSince <= 14 -> "a couple of weeks ago"
            daysSince <= 30 -> "last month"
            daysSince <= 60 -> "a while back"
            else -> "some time ago"
        }

        val amount = formatAmount(debt.amount)

        return when {
            daysSince <= 14 -> "Hey! Just a friendly reminder about the $amount from $timeDescription. No rush! 😊"
            daysSince <= 30 -> "Hi! Hope you're doing well. Just a gentle nudge about the $amount from $timeDescription. Whenever convenient! 🙂"
            daysSince <= 60 -> "Hey! Small reminder about the $amount from $timeDescription. Totally understand if it slipped your mind! Take your time 😊"
            else -> "Hi! Hope all is well. Just checking in — there's a pending $amount from $timeDescription. No pressure at all, just didn't want it to be forgotten! 🤝"
        }
    }

    /**
     * Generates an aging alert message for a debt you owe.
     *
     * @param debt The aged debt
     * @return A gentle self-reminder message
     */
    fun generateAgingAlertIOwe(debt: SocialDebt): String {
        val daysSince = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
            LocalDate.now()
        )
        val amount = formatAmount(debt.amount)

        return "$amount owed to ${debt.personName} for ${daysSince}+ days. " +
                "Gentle reminder to self: settle it? ${debt.reason}"
    }

    /**
     * Generates the prompt text for an incoming money detection.
     *
     * @param prompt The incoming money prompt
     * @return Formatted prompt text for the user
     */
    fun formatIncomingMoneyPrompt(prompt: IncomingMoneyPrompt): String {
        val amount = formatAmount(prompt.amount)
        val sender = prompt.senderName ?: "someone"

        return buildString {
            append("You received $amount")
            if (prompt.senderName != null) {
                append(" from $sender")
            }
            append(". Action needed?")

            if (prompt.matchedDebt != null) {
                val debtAmount = formatAmount(prompt.matchedDebt.amount)
                val daysAgo = ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(prompt.matchedDebt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                    LocalDate.now()
                )
                append("\n\n💡 Auto-match: $debtAmount owed by ${prompt.matchedDebt.personName}")
                append(" ($daysAgo days ago). Is this the settlement?")
            }
        }
    }

    /**
     * Detects when outgoing money goes to the same person multiple times,
     * potentially indicating a settled debt pattern.
     *
     * @param personName The person receiving money
     * @param amount The amount sent
     * @return Suggestion text if a pattern is detected, null otherwise
     */
    fun detectRepeatedOutgoing(personName: String, amount: Double): String? {
        val matchingDebts = _debts.value.filter { debt ->
            debt.type == DebtType.I_OWE &&
                    isNameMatch(debt.personName, personName) &&
                    debt.isSettled
        }

        return if (matchingDebts.size >= 2) {
            val totalSettled = matchingDebts.sumOf { it.amount }
            "You've sent money to $personName ${matchingDebts.size} times " +
                    "(total: ${formatAmount(totalSettled)} settled). Regular transfers detected."
        } else null
    }

    /**
     * Recalculates the monthly debt summary.
     */
    private fun recalculateSummary() {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val settledThisMonth = _debts.value.count { it.isSettled && (it.settledDate ?: 0) >= monthStart }
        val pendingIOwe = getPendingIOwe()
        val pendingTheyOwe = getPendingTheyOwe()

        _monthlySummary.value = MonthlyDebtSummary(
            month = currentMonth,
            settledCount = settledThisMonth,
            pendingIOweCount = pendingIOwe.size,
            pendingIOweAmount = pendingIOwe.sumOf { it.amount },
            pendingTheyOweCount = pendingTheyOwe.size,
            pendingTheyOweAmount = pendingTheyOwe.sumOf { it.amount }
        )
    }

    /**
     * Generates the monthly summary display text.
     *
     * @return Formatted summary string
     */
    fun getMonthlySummaryText(): String {
        val summary = _monthlySummary.value ?: run {
            recalculateSummary()
            _monthlySummary.value
        } ?: return "No debt activity this month"

        return buildString {
            append("📊 Monthly Summary (${summary.month.month.name.lowercase().replaceFirstChar { it.uppercase() }})")
            append("\n\nSettled: ${summary.settledCount} transactions")
            append("\nPending:")
            append("\n  • You owe: ${summary.pendingIOweCount} (${formatAmount(summary.pendingIOweAmount)})")
            append("\n  • Owed to you: ${summary.pendingTheyOweCount} (${formatAmount(summary.pendingTheyOweAmount)})")
        }
    }

    /**
     * Gets the complete Social Debt Ledger formatted for display.
     *
     * @return Formatted ledger with separate sections for "I owe" and "They owe me"
     */
    fun getLedgerDisplayText(): String {
        val iOwe = getPendingIOwe()
        val theyOwe = getPendingTheyOwe()

        return buildString {
            append("💰 Social Debt Ledger")

            append("\n\n── I Owe ──")
            if (iOwe.isEmpty()) {
                append("\n  All clear! 🎉")
            } else {
                iOwe.forEach { debt ->
                    val days = ChronoUnit.DAYS.between(
                        Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                        LocalDate.now()
                    )
                    append("\n  • ${formatAmount(debt.amount)} → ${debt.personName}")
                    append(" (${debt.reason}, ${days}d ago)")
                }
                append("\n  Total: ${formatAmount(iOwe.sumOf { it.amount })}")
            }

            append("\n\n── They Owe Me ──")
            if (theyOwe.isEmpty()) {
                append("\n  All clear! 🎉")
            } else {
                theyOwe.forEach { debt ->
                    val days = ChronoUnit.DAYS.between(
                        Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                        LocalDate.now()
                    )
                    append("\n  • ${formatAmount(debt.amount)} ← ${debt.personName}")
                    append(" (${debt.reason}, ${days}d ago)")
                }
                append("\n  Total: ${formatAmount(theyOwe.sumOf { it.amount })}")
            }
        }
    }

    /**
     * Formats an amount with the rupee symbol.
     */
    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            "₹${amount.toLong()}"
        } else {
            "₹${"%.2f".format(amount)}"
        }
    }

    /**
     * Formats a timestamp to a human-readable date.
     */
    private fun formatDate(timestamp: Long): String {
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    }

    /**
     * Cleans up resources.
     */
    fun destroy() {
        scope.cancel()
    }
}
