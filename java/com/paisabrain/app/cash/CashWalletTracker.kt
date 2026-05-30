package com.paisabrain.app.cash

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cash Wallet Tracker — tracks physical cash spending that is invisible to SMS-based detection.
 *
 * In India, approximately 50% of daily spending is still cash-based. This module bridges
 * the gap by:
 * 1. Detecting ATM withdrawals from SMS and creating "cash wallets" with that amount.
 * 2. Providing quick-entry templates for common cash purchases (tea, auto, vegetables, etc.).
 * 3. Tracking unaccounted cash and prompting weekly reconciliation.
 * 4. Showing cash vs. digital spending ratio for complete financial picture.
 *
 * All data is stored locally — no network access required.
 */
class CashWalletTracker(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "paisa_brain_cash_tracker_prefs"
        private const val PREF_TEMPLATES_CUSTOMIZED = "cash_templates_customized"

        /** Threshold in days for considering a wallet stale (for reconciliation nudge). */
        private const val RECONCILIATION_NUDGE_DAYS = 7

        /** Minimum unaccounted percentage to trigger a nudge. */
        private const val UNACCOUNTED_NUDGE_THRESHOLD = 0.30 // 30%
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Represents a cash wallet created from an ATM withdrawal or manual entry.
     *
     * @param id Unique identifier.
     * @param withdrawalDate Timestamp of the ATM withdrawal or wallet creation.
     * @param initialAmount Amount withdrawn or added to the wallet.
     * @param remainingAmount Current remaining balance (initialAmount - sum of expenses).
     * @param trackedExpenses List of cash expenses logged against this wallet.
     * @param unaccounted Amount that hasn't been tracked yet.
     * @param source How this wallet was created (ATM_SMS, MANUAL).
     * @param note Optional user note (e.g., "Salary withdrawal").
     * @param isActive Whether this wallet is still in use (vs. archived/reconciled).
     */
    data class CashWallet(
        val id: Long,
        val withdrawalDate: Long,
        val initialAmount: Double,
        val remainingAmount: Double,
        val trackedExpenses: List<CashExpense>,
        val unaccounted: Double,
        val source: WalletSource = WalletSource.MANUAL,
        val note: String? = null,
        val isActive: Boolean = true
    )

    /**
     * A single cash expense logged against a wallet.
     *
     * @param id Unique identifier.
     * @param amount Amount spent.
     * @param category Category of the expense.
     * @param description Brief description or template name.
     * @param timestamp When the expense occurred.
     * @param walletId Which cash wallet this expense belongs to.
     * @param templateId If created from a template, which template was used.
     */
    data class CashExpense(
        val id: Long,
        val amount: Double,
        val category: String,
        val description: String,
        val timestamp: Long,
        val walletId: Long,
        val templateId: String? = null
    )

    /**
     * A quick-entry template for common cash purchases.
     *
     * @param id Template identifier (stable across sessions).
     * @param name Display name of the template.
     * @param defaultAmount Default amount (user can customize).
     * @param emoji Emoji icon for quick visual recognition.
     * @param category Auto-assigned category.
     * @param usageCount How many times this template has been used (for sorting).
     * @param minAmount Suggested minimum for this type of expense.
     * @param maxAmount Suggested maximum for this type of expense.
     * @param isCustom Whether this is a user-created template.
     */
    data class CashTemplate(
        val id: String,
        val name: String,
        val defaultAmount: Double,
        val emoji: String,
        val category: String,
        val usageCount: Int = 0,
        val minAmount: Double = 0.0,
        val maxAmount: Double = 0.0,
        val isCustom: Boolean = false
    )

    /**
     * Source of a cash wallet creation.
     */
    enum class WalletSource {
        /** Created automatically from ATM withdrawal SMS. */
        ATM_SMS,
        /** Created manually by the user. */
        MANUAL
    }

    /**
     * Weekly reconciliation summary for a cash wallet.
     *
     * @param walletId The wallet being reconciled.
     * @param totalWithdrawn Initial wallet amount.
     * @param totalTracked Sum of all tracked expenses.
     * @param unaccounted Difference that hasn't been categorized.
     * @param unaccountedPercentage Percentage of funds unaccounted for.
     * @param daysSinceCreation How old this wallet is.
     * @param suggestedActions Possible actions user can take.
     */
    data class ReconciliationSummary(
        val walletId: Long,
        val totalWithdrawn: Double,
        val totalTracked: Double,
        val unaccounted: Double,
        val unaccountedPercentage: Float,
        val daysSinceCreation: Int,
        val suggestedActions: List<ReconciliationAction>
    )

    /**
     * Actions available during weekly cash reconciliation.
     */
    enum class ReconciliationAction {
        /** Split unaccounted amount across categories. */
        SPLIT_INTO_CATEGORIES,
        /** Mark entire unaccounted amount as miscellaneous. */
        MARK_AS_MISC,
        /** Ignore / close the wallet without reconciling. */
        IGNORE,
        /** Manually enter specific expenses. */
        LOG_EXPENSES
    }

    /**
     * Ratio statistics for cash vs. digital spending.
     *
     * @param digitalSpend Total digital spending in period.
     * @param cashSpend Total cash spending (tracked) in period.
     * @param digitalPercentage Percentage of total that's digital.
     * @param cashPercentage Percentage of total that's cash.
     * @param untrackedCash Unaccounted cash in the period.
     * @param periodLabel Human-readable period description.
     */
    data class SpendingRatio(
        val digitalSpend: Double,
        val cashSpend: Double,
        val digitalPercentage: Float,
        val cashPercentage: Float,
        val untrackedCash: Double,
        val periodLabel: String
    )

    /**
     * Alert/nudge for the user about untracked cash.
     */
    data class CashNudge(
        val walletId: Long,
        val message: String,
        val unaccountedAmount: Double,
        val withdrawnAmount: Double,
        val type: NudgeType
    )

    /**
     * Types of cash tracking nudges.
     */
    enum class NudgeType {
        /** First reminder after withdrawal. */
        INITIAL_REMINDER,
        /** Weekly reconciliation nudge. */
        WEEKLY_RECONCILIATION,
        /** High cash usage pattern detected. */
        HIGH_CASH_PATTERN,
        /** Long time since any cash logging. */
        DORMANT_WALLET
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Default Templates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the default set of cash expense templates common in Indian daily life.
     * User can customize amounts via [updateTemplateAmount].
     */
    fun getDefaultTemplates(): List<CashTemplate> {
        return listOf(
            CashTemplate(
                id = "tea_coffee",
                name = "Tea / Coffee",
                defaultAmount = 20.0,
                emoji = "☕",
                category = "Food & Drinks",
                minAmount = 10.0,
                maxAmount = 50.0
            ),
            CashTemplate(
                id = "auto_rickshaw",
                name = "Auto / Rickshaw",
                defaultAmount = 50.0,
                emoji = "🛺",
                category = "Transport",
                minAmount = 20.0,
                maxAmount = 100.0
            ),
            CashTemplate(
                id = "street_food",
                name = "Street Food",
                defaultAmount = 60.0,
                emoji = "🍲",
                category = "Food & Drinks",
                minAmount = 30.0,
                maxAmount = 100.0
            ),
            CashTemplate(
                id = "parking",
                name = "Parking",
                defaultAmount = 30.0,
                emoji = "🅿️",
                category = "Transport",
                minAmount = 20.0,
                maxAmount = 50.0
            ),
            CashTemplate(
                id = "tip",
                name = "Tip",
                defaultAmount = 50.0,
                emoji = "💰",
                category = "Miscellaneous",
                minAmount = 20.0,
                maxAmount = 100.0
            ),
            CashTemplate(
                id = "vegetables",
                name = "Vegetable Vendor",
                defaultAmount = 200.0,
                emoji = "🥬",
                category = "Groceries",
                minAmount = 100.0,
                maxAmount = 500.0
            ),
            CashTemplate(
                id = "maid_help",
                name = "Maid / Help Salary",
                defaultAmount = 3000.0,
                emoji = "🏠",
                category = "Household",
                minAmount = 1000.0,
                maxAmount = 5000.0
            ),
            CashTemplate(
                id = "society_maintenance",
                name = "Society Maintenance",
                defaultAmount = 2500.0,
                emoji = "🏢",
                category = "Household",
                minAmount = 500.0,
                maxAmount = 10000.0
            ),
            CashTemplate(
                id = "religious_donation",
                name = "Religious Donation",
                defaultAmount = 51.0,
                emoji = "🙏",
                category = "Donations",
                minAmount = 10.0,
                maxAmount = 500.0
            ),
            CashTemplate(
                id = "custom",
                name = "Custom Amount",
                defaultAmount = 0.0,
                emoji = "✏️",
                category = "Miscellaneous",
                minAmount = 1.0,
                maxAmount = 100000.0,
                isCustom = true
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wallet Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new cash wallet from an ATM withdrawal detected via SMS.
     *
     * @param amount Withdrawal amount.
     * @param withdrawalDate Timestamp of the withdrawal.
     * @param note Optional note (e.g., bank/ATM location from SMS).
     * @return The created [CashWallet].
     */
    fun createWalletFromATM(amount: Double, withdrawalDate: Long, note: String? = null): CashWallet {
        val wallet = CashWallet(
            id = System.nanoTime(), // In production, use Room auto-generate
            withdrawalDate = withdrawalDate,
            initialAmount = amount,
            remainingAmount = amount,
            trackedExpenses = emptyList(),
            unaccounted = amount,
            source = WalletSource.ATM_SMS,
            note = note,
            isActive = true
        )
        saveWallet(wallet)
        return wallet
    }

    /**
     * Creates a new cash wallet manually (user decides to track cash they have).
     *
     * @param amount Starting cash amount.
     * @param note Optional description.
     * @return The created [CashWallet].
     */
    fun createWalletManual(amount: Double, note: String? = null): CashWallet {
        val wallet = CashWallet(
            id = System.nanoTime(),
            withdrawalDate = System.currentTimeMillis(),
            initialAmount = amount,
            remainingAmount = amount,
            trackedExpenses = emptyList(),
            unaccounted = amount,
            source = WalletSource.MANUAL,
            note = note,
            isActive = true
        )
        saveWallet(wallet)
        return wallet
    }

    /**
     * Logs a cash expense against the active wallet (or the most recent one).
     *
     * @param amount Amount spent.
     * @param category Category of the expense.
     * @param description Brief description.
     * @param walletId Specific wallet to debit, or null for the most recent active wallet.
     * @param templateId If using a template, its ID.
     * @return The created [CashExpense], or null if no active wallet or insufficient balance.
     */
    fun logCashExpense(
        amount: Double,
        category: String,
        description: String,
        walletId: Long? = null,
        templateId: String? = null
    ): CashExpense? {
        if (amount <= 0) return null

        val targetWallet = if (walletId != null) {
            getWalletById(walletId)
        } else {
            getMostRecentActiveWallet()
        }

        if (targetWallet == null) {
            // No active wallet — create an implicit one
            createWalletManual(amount, "Auto-created for cash expense")
            return logCashExpense(amount, category, description, null, templateId)
        }

        val expense = CashExpense(
            id = System.nanoTime(),
            amount = amount,
            category = category,
            description = description,
            timestamp = System.currentTimeMillis(),
            walletId = targetWallet.id,
            templateId = templateId
        )

        // Update wallet balance
        val updatedWallet = targetWallet.copy(
            remainingAmount = maxOf(0.0, targetWallet.remainingAmount - amount),
            trackedExpenses = targetWallet.trackedExpenses + expense,
            unaccounted = maxOf(0.0, targetWallet.unaccounted - amount)
        )
        saveWallet(updatedWallet)
        saveCashExpense(expense)

        // Increment template usage if applicable
        if (templateId != null) {
            incrementTemplateUsage(templateId)
        }

        return expense
    }

    /**
     * Logs a cash expense using a template (quick entry).
     *
     * @param template The template to use.
     * @param amount Override amount (if null, uses template default).
     * @param walletId Target wallet (if null, uses most recent active).
     * @return The created [CashExpense].
     */
    fun logFromTemplate(
        template: CashTemplate,
        amount: Double? = null,
        walletId: Long? = null
    ): CashExpense? {
        val finalAmount = amount ?: template.defaultAmount
        if (finalAmount <= 0) return null

        return logCashExpense(
            amount = finalAmount,
            category = template.category,
            description = template.name,
            walletId = walletId,
            templateId = template.id
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ATM Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if an SMS body represents an ATM withdrawal and extracts the amount.
     * Call this from the SMS listener when a new financial SMS is received.
     *
     * Patterns detected:
     * - "withdrawn", "ATM withdrawal", "cash withdrawal"
     * - Amount patterns: "Rs.5000", "INR 5,000", "Rs 5000.00"
     *
     * @param smsBody The full SMS text.
     * @param sender SMS sender address.
     * @return Withdrawal amount if detected, null otherwise.
     */
    fun detectATMWithdrawal(smsBody: String, sender: String): Double? {
        val lowerBody = smsBody.lowercase(Locale.getDefault())

        // Check for ATM/cash withdrawal keywords
        val isWithdrawal = listOf(
            "atm", "withdrawn", "cash withdrawal", "cash w/d",
            "atm-wd", "atm wd", "atm/cash", "debited.*atm"
        ).any { keyword ->
            if (keyword.contains(".*")) {
                Regex(keyword).containsMatchIn(lowerBody)
            } else {
                lowerBody.contains(keyword)
            }
        }

        if (!isWithdrawal) return null

        // Extract amount from SMS
        return extractAmount(smsBody)
    }

    /**
     * Extracts a monetary amount from SMS text.
     * Handles formats: Rs.5000, Rs 5,000.00, INR 5000, ₹5,000
     */
    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+\.?\d*)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9,]+\.?\d*)\s*(?:Rs\.?|INR|₹)""", RegexOption.IGNORE_CASE),
            Regex("""(?:amount|amt)[:\s]*(?:Rs\.?|INR|₹)?\s*([0-9,]+\.?\d*)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) return amount
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reconciliation & Nudges
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates reconciliation summaries for wallets that need attention.
     * Called weekly or when the user opens the cash tracking section.
     *
     * @return List of wallets needing reconciliation, sorted by urgency.
     */
    fun getReconciliationSummaries(): List<ReconciliationSummary> {
        val activeWallets = getActiveWallets()
        val summaries = mutableListOf<ReconciliationSummary>()

        for (wallet in activeWallets) {
            val daysSinceCreation = ((System.currentTimeMillis() - wallet.withdrawalDate) /
                    (1000L * 60 * 60 * 24)).toInt()

            if (daysSinceCreation < RECONCILIATION_NUDGE_DAYS) continue

            val unaccountedPercentage = if (wallet.initialAmount > 0) {
                (wallet.unaccounted / wallet.initialAmount).toFloat()
            } else 0f

            if (unaccountedPercentage < UNACCOUNTED_NUDGE_THRESHOLD) continue

            val actions = mutableListOf<ReconciliationAction>()
            actions.add(ReconciliationAction.LOG_EXPENSES)
            actions.add(ReconciliationAction.SPLIT_INTO_CATEGORIES)
            actions.add(ReconciliationAction.MARK_AS_MISC)
            actions.add(ReconciliationAction.IGNORE)

            summaries.add(
                ReconciliationSummary(
                    walletId = wallet.id,
                    totalWithdrawn = wallet.initialAmount,
                    totalTracked = wallet.initialAmount - wallet.unaccounted,
                    unaccounted = wallet.unaccounted,
                    unaccountedPercentage = unaccountedPercentage,
                    daysSinceCreation = daysSinceCreation,
                    suggestedActions = actions
                )
            )
        }

        return summaries.sortedByDescending { it.unaccounted }
    }

    /**
     * Performs reconciliation action on a wallet.
     *
     * @param walletId The wallet to reconcile.
     * @param action The chosen reconciliation action.
     * @param splitCategories If action is SPLIT_INTO_CATEGORIES, the category→amount map.
     */
    fun reconcileWallet(
        walletId: Long,
        action: ReconciliationAction,
        splitCategories: Map<String, Double>? = null
    ) {
        val wallet = getWalletById(walletId) ?: return

        when (action) {
            ReconciliationAction.MARK_AS_MISC -> {
                logCashExpense(
                    amount = wallet.unaccounted,
                    category = "Miscellaneous",
                    description = "Untracked cash (reconciled)",
                    walletId = walletId
                )
            }
            ReconciliationAction.SPLIT_INTO_CATEGORIES -> {
                splitCategories?.forEach { (category, amount) ->
                    logCashExpense(
                        amount = amount,
                        category = category,
                        description = "Reconciled: $category",
                        walletId = walletId
                    )
                }
            }
            ReconciliationAction.IGNORE -> {
                // Archive the wallet without reconciling
                val archived = wallet.copy(isActive = false)
                saveWallet(archived)
            }
            ReconciliationAction.LOG_EXPENSES -> {
                // No-op here — UI will open expense logging screen
            }
        }
    }

    /**
     * Generates nudge messages for untracked cash.
     *
     * @return List of nudges that should be shown to the user.
     */
    fun generateNudges(): List<CashNudge> {
        val nudges = mutableListOf<CashNudge>()
        val activeWallets = getActiveWallets()

        for (wallet in activeWallets) {
            val daysSinceCreation = ((System.currentTimeMillis() - wallet.withdrawalDate) /
                    (1000L * 60 * 60 * 24)).toInt()

            val unaccountedPercentage = if (wallet.initialAmount > 0) {
                wallet.unaccounted / wallet.initialAmount
            } else 0.0

            when {
                // Brand new wallet with no tracking
                daysSinceCreation <= 1 && wallet.trackedExpenses.isEmpty() -> {
                    nudges.add(
                        CashNudge(
                            walletId = wallet.id,
                            message = "You withdrew ₹${formatAmount(wallet.initialAmount)}. " +
                                    "Log your cash expenses to keep track!",
                            unaccountedAmount = wallet.unaccounted,
                            withdrawnAmount = wallet.initialAmount,
                            type = NudgeType.INITIAL_REMINDER
                        )
                    )
                }
                // Weekly reconciliation needed
                daysSinceCreation >= RECONCILIATION_NUDGE_DAYS && unaccountedPercentage > UNACCOUNTED_NUDGE_THRESHOLD -> {
                    nudges.add(
                        CashNudge(
                            walletId = wallet.id,
                            message = "₹${formatAmount(wallet.unaccounted)} of " +
                                    "₹${formatAmount(wallet.initialAmount)} withdrawn hasn't been tracked. " +
                                    "Where did it go?",
                            unaccountedAmount = wallet.unaccounted,
                            withdrawnAmount = wallet.initialAmount,
                            type = NudgeType.WEEKLY_RECONCILIATION
                        )
                    )
                }
                // Dormant wallet (old, some tracking, but abandoned)
                daysSinceCreation >= 14 && wallet.trackedExpenses.isNotEmpty() -> {
                    val lastExpenseTime = wallet.trackedExpenses.maxOfOrNull { it.timestamp } ?: 0L
                    val daysSinceLastExpense = ((System.currentTimeMillis() - lastExpenseTime) /
                            (1000L * 60 * 60 * 24)).toInt()
                    if (daysSinceLastExpense > 7 && wallet.unaccounted > 100) {
                        nudges.add(
                            CashNudge(
                                walletId = wallet.id,
                                message = "₹${formatAmount(wallet.unaccounted)} cash is unaccounted " +
                                        "for this week. Reconcile?",
                                unaccountedAmount = wallet.unaccounted,
                                withdrawnAmount = wallet.initialAmount,
                                type = NudgeType.DORMANT_WALLET
                            )
                        )
                    }
                }
            }
        }

        // Check for high cash usage pattern (across all wallets in current month)
        val monthlyStats = getMonthlyWithdrawalStats()
        if (monthlyStats.first > 0 && monthlyStats.second > UNACCOUNTED_NUDGE_THRESHOLD) {
            nudges.add(
                CashNudge(
                    walletId = 0L,
                    message = "You withdrew ₹${formatAmount(monthlyStats.first.toDouble())} this month " +
                            "but only tracked ${((1.0 - monthlyStats.second) * 100).toInt()}%. " +
                            "Try logging cash purchases for better insights.",
                    unaccountedAmount = monthlyStats.first * monthlyStats.second,
                    withdrawnAmount = monthlyStats.first.toDouble(),
                    type = NudgeType.HIGH_CASH_PATTERN
                )
            )
        }

        return nudges
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Spending Ratio
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the cash vs. digital spending ratio for a given period.
     *
     * @param startDate Start of the period (timestamp).
     * @param endDate End of the period (timestamp).
     * @return [SpendingRatio] with percentage breakdown.
     */
    fun getSpendingRatio(startDate: Long, endDate: Long): SpendingRatio {
        val cashSpend = getCashSpendInPeriod(startDate, endDate)
        val digitalSpend = getDigitalSpendInPeriod(startDate, endDate)
        val untrackedCash = getUntrackedCashInPeriod(startDate, endDate)
        val total = cashSpend + digitalSpend

        val digitalPercentage = if (total > 0) ((digitalSpend / total) * 100).toFloat() else 0f
        val cashPercentage = if (total > 0) ((cashSpend / total) * 100).toFloat() else 0f

        val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val periodLabel = dateFormat.format(Date(startDate))

        return SpendingRatio(
            digitalSpend = digitalSpend,
            cashSpend = cashSpend,
            digitalPercentage = digitalPercentage,
            cashPercentage = cashPercentage,
            untrackedCash = untrackedCash,
            periodLabel = periodLabel
        )
    }

    /**
     * Gets the current month's spending ratio.
     */
    fun getCurrentMonthRatio(): SpendingRatio {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfMonth = calendar.timeInMillis
        val now = System.currentTimeMillis()
        return getSpendingRatio(startOfMonth, now)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Template Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets all templates, sorted by usage frequency (most used first).
     * Includes both default and user-created templates.
     */
    fun getTemplates(): List<CashTemplate> {
        val templates = loadCustomizedTemplates() ?: getDefaultTemplates()
        return templates.sortedByDescending { it.usageCount }
    }

    /**
     * Updates the default amount for a template to match user's local costs.
     *
     * @param templateId Template to update.
     * @param newAmount New default amount.
     */
    fun updateTemplateAmount(templateId: String, newAmount: Double) {
        val templates = (loadCustomizedTemplates() ?: getDefaultTemplates()).toMutableList()
        val index = templates.indexOfFirst { it.id == templateId }
        if (index >= 0) {
            templates[index] = templates[index].copy(defaultAmount = newAmount)
            saveCustomizedTemplates(templates)
        }
    }

    /**
     * Creates a new user-defined cash expense template.
     *
     * @param name Template name.
     * @param defaultAmount Default amount.
     * @param emoji Display emoji.
     * @param category Auto-assigned category.
     * @return The created template.
     */
    fun createCustomTemplate(
        name: String,
        defaultAmount: Double,
        emoji: String,
        category: String
    ): CashTemplate {
        val template = CashTemplate(
            id = "custom_${System.currentTimeMillis()}",
            name = name,
            defaultAmount = defaultAmount,
            emoji = emoji,
            category = category,
            usageCount = 0,
            isCustom = true
        )
        val templates = (loadCustomizedTemplates() ?: getDefaultTemplates()).toMutableList()
        // Insert before the generic "Custom Amount" template
        val customIndex = templates.indexOfFirst { it.id == "custom" }
        if (customIndex >= 0) {
            templates.add(customIndex, template)
        } else {
            templates.add(template)
        }
        saveCustomizedTemplates(templates)
        return template
    }

    /**
     * Deletes a user-created template.
     *
     * @param templateId Template to delete (only custom templates can be deleted).
     * @return true if deleted, false if template not found or is a default template.
     */
    fun deleteCustomTemplate(templateId: String): Boolean {
        val templates = (loadCustomizedTemplates() ?: getDefaultTemplates()).toMutableList()
        val template = templates.find { it.id == templateId }
        if (template == null || !template.isCustom) return false
        templates.remove(template)
        saveCustomizedTemplates(templates)
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wallet Queries
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets all active (non-archived) cash wallets.
     */
    fun getActiveWallets(): List<CashWallet> {
        // TODO: Wire to Room DAO
        // return cashWalletDao.getActiveWallets()
        return emptyList()
    }

    /**
     * Gets a specific wallet by ID.
     */
    fun getWalletById(walletId: Long): CashWallet? {
        // TODO: Wire to Room DAO
        // return cashWalletDao.getById(walletId)
        return null
    }

    /**
     * Gets the most recent active wallet (for quick expense logging).
     */
    fun getMostRecentActiveWallet(): CashWallet? {
        return getActiveWallets().maxByOrNull { it.withdrawalDate }
    }

    /**
     * Gets all cash expenses for a specific wallet.
     */
    fun getExpensesForWallet(walletId: Long): List<CashExpense> {
        // TODO: Wire to Room DAO
        // return cashExpenseDao.getByWalletId(walletId)
        return emptyList()
    }

    /**
     * Gets total cash wallet balance across all active wallets.
     */
    fun getTotalCashBalance(): Double {
        return getActiveWallets().sumOf { it.remainingAmount }
    }

    /**
     * Gets total unaccounted cash across all active wallets.
     */
    fun getTotalUnaccounted(): Double {
        return getActiveWallets().sumOf { it.unaccounted }
    }

    /**
     * Archives a wallet (marks it as inactive).
     */
    fun archiveWallet(walletId: Long) {
        val wallet = getWalletById(walletId) ?: return
        saveWallet(wallet.copy(isActive = false))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format(Locale.getDefault(), "%,.0f", amount)
        } else {
            String.format(Locale.getDefault(), "%,.2f", amount)
        }
    }

    private fun incrementTemplateUsage(templateId: String) {
        val templates = (loadCustomizedTemplates() ?: getDefaultTemplates()).toMutableList()
        val index = templates.indexOfFirst { it.id == templateId }
        if (index >= 0) {
            templates[index] = templates[index].copy(usageCount = templates[index].usageCount + 1)
            saveCustomizedTemplates(templates)
        }
    }

    /**
     * Gets monthly withdrawal total and unaccounted fraction.
     * @return Pair of (total withdrawn this month, fraction unaccounted 0.0-1.0)
     */
    private fun getMonthlyWithdrawalStats(): Pair<Long, Double> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val startOfMonth = calendar.timeInMillis

        val wallets = getActiveWallets().filter { it.withdrawalDate >= startOfMonth }
        val totalWithdrawn = wallets.sumOf { it.initialAmount }.toLong()
        val totalUnaccounted = wallets.sumOf { it.unaccounted }
        val fraction = if (totalWithdrawn > 0) totalUnaccounted / totalWithdrawn.toDouble() else 0.0

        return Pair(totalWithdrawn, fraction)
    }

    private fun getCashSpendInPeriod(startDate: Long, endDate: Long): Double {
        // TODO: Query Room DB for cash expenses in date range
        return 0.0
    }

    private fun getDigitalSpendInPeriod(startDate: Long, endDate: Long): Double {
        // TODO: Query Room DB for digital (SMS-detected) transactions in date range
        return 0.0
    }

    private fun getUntrackedCashInPeriod(startDate: Long, endDate: Long): Double {
        val wallets = getActiveWallets().filter {
            it.withdrawalDate in startDate..endDate
        }
        return wallets.sumOf { it.unaccounted }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence Stubs (to be wired to Room)
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveWallet(wallet: CashWallet) {
        // TODO: Wire to Room DAO
        // cashWalletDao.insertOrUpdate(wallet.toEntity())
    }

    private fun saveCashExpense(expense: CashExpense) {
        // TODO: Wire to Room DAO
        // cashExpenseDao.insert(expense.toEntity())
    }

    private fun loadCustomizedTemplates(): List<CashTemplate>? {
        // TODO: Load from Room or SharedPreferences
        // If user has never customized, return null to use defaults
        val hasCustomized = prefs.getBoolean(PREF_TEMPLATES_CUSTOMIZED, false)
        return if (hasCustomized) {
            // Load from DB
            null // Placeholder
        } else {
            null
        }
    }

    private fun saveCustomizedTemplates(templates: List<CashTemplate>) {
        prefs.edit().putBoolean(PREF_TEMPLATES_CUSTOMIZED, true).apply()
        // TODO: Save to Room DAO
        // cashTemplateDao.replaceAll(templates.map { it.toEntity() })
    }
}
