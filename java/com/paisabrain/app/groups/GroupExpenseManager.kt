package com.paisabrain.app.groups

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Group Expense Manager — split expenses with flatmates, travel buddies, or office colleagues.
 *
 * Key design principle: This works entirely OFFLINE and does NOT require other members to install
 * the app. You track everything yourself — it's your personal record of shared expenses.
 *
 * Features:
 * - Create groups for any shared-expense scenario (flat, trip, office lunch, wedding)
 * - Log who paid what and how to split it (equal / by amount / by percentage / exclude members)
 * - Auto-calculate optimized settlements (minimum number of transactions to settle all debts)
 * - Track settlements and running totals
 * - Export shareable summaries as plain text (copy-paste to any chat app)
 * - Recurring group expenses with reminders
 */
class GroupExpenseManager(private val context: Context) {

    companion object {
        /** Currency symbol for display. */
        private const val CURRENCY = "₹"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A group of people who share expenses.
     *
     * @param id Unique identifier.
     * @param name Group display name (e.g., "Flat 302", "Goa Trip 2026").
     * @param members List of member names (just names — no accounts needed).
     * @param createdAt When the group was created.
     * @param isActive Whether the group is currently active or archived.
     * @param emoji Optional emoji for quick identification.
     * @param description Optional description / notes about the group.
     */
    data class Group(
        val id: Long,
        val name: String,
        val members: List<String>,
        val createdAt: Long,
        val isActive: Boolean = true,
        val emoji: String? = null,
        val description: String? = null
    )

    /**
     * A single expense within a group.
     *
     * @param id Unique identifier.
     * @param groupId Which group this expense belongs to.
     * @param paidBy Name of the person who paid.
     * @param amount Total amount paid.
     * @param description What the expense was for.
     * @param splitType How to divide this expense.
     * @param splitDetails Per-member split amounts (member name → their share).
     * @param date When the expense occurred.
     * @param isSettlement Whether this is a settlement payment (not an expense).
     * @param isRecurring Whether this is a recurring expense.
     * @param recurringDay Day of month for recurring (e.g., 1 for rent on 1st).
     */
    data class GroupExpense(
        val id: Long,
        val groupId: Long,
        val paidBy: String,
        val amount: Double,
        val description: String,
        val splitType: SplitType,
        val splitDetails: Map<String, Double>,
        val date: Long,
        val isSettlement: Boolean = false,
        val isRecurring: Boolean = false,
        val recurringDay: Int? = null
    )

    /**
     * How an expense is split among group members.
     */
    enum class SplitType {
        /** Divide equally among all members. */
        EQUAL,
        /** Each person's share specified as an exact amount. */
        BY_AMOUNT,
        /** Each person's share specified as a percentage. */
        BY_PERCENTAGE,
        /** Exclude some members; split equally among the rest. */
        EXCLUDE_SOME
    }

    /**
     * An optimized debt settlement (minimum transactions to balance all debts).
     *
     * @param from Person who owes money.
     * @param to Person who is owed money.
     * @param amount Amount to transfer.
     */
    data class DebtSimplification(
        val from: String,
        val to: String,
        val amount: Double
    )

    /**
     * Records when a debt between two people is settled.
     *
     * @param id Unique identifier.
     * @param groupId Which group this settlement belongs to.
     * @param from Person who paid.
     * @param to Person who received.
     * @param amount Amount settled.
     * @param date When settled.
     * @param note Optional note (e.g., "UPI transfer", "Cash").
     */
    data class Settlement(
        val id: Long,
        val groupId: Long,
        val from: String,
        val to: String,
        val amount: Double,
        val date: Long,
        val note: String? = null
    )

    /**
     * Running total for a member within a group.
     *
     * @param memberName Member's name.
     * @param totalPaid Total amount this person has paid.
     * @param totalShare Total amount this person owes (their fair share).
     * @param balance Net balance (positive = others owe them, negative = they owe).
     * @param fairShareDeviation How much over/under their fair share they are.
     */
    data class MemberSummary(
        val memberName: String,
        val totalPaid: Double,
        val totalShare: Double,
        val balance: Double,
        val fairShareDeviation: Double
    )

    /**
     * Template for pre-filling common recurring group expenses.
     */
    data class GroupTemplate(
        val name: String,
        val expenses: List<TemplateExpense>,
        val emoji: String
    )

    /**
     * A single expense within a group template.
     */
    data class TemplateExpense(
        val description: String,
        val estimatedAmount: Double,
        val splitType: SplitType = SplitType.EQUAL,
        val recurringDay: Int? = null
    )

    /**
     * Result of adding an expense, including the updated balances.
     */
    data class ExpenseResult(
        val expense: GroupExpense,
        val updatedBalances: Map<String, Double>
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Group Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new expense group.
     *
     * @param name Group name.
     * @param members List of member names (include yourself).
     * @param emoji Optional emoji.
     * @param description Optional description.
     * @return The created [Group].
     */
    fun createGroup(
        name: String,
        members: List<String>,
        emoji: String? = null,
        description: String? = null
    ): Group {
        require(members.size >= 2) { "A group needs at least 2 members." }
        require(name.isNotBlank()) { "Group name cannot be empty." }

        val group = Group(
            id = System.nanoTime(),
            name = name,
            members = members.map { it.trim() }.distinct(),
            createdAt = System.currentTimeMillis(),
            isActive = true,
            emoji = emoji,
            description = description
        )
        saveGroup(group)
        return group
    }

    /**
     * Adds a member to an existing group.
     *
     * @param groupId Group to add member to.
     * @param memberName Name of the new member.
     * @return Updated group, or null if group not found.
     */
    fun addMember(groupId: Long, memberName: String): Group? {
        val group = getGroupById(groupId) ?: return null
        if (group.members.contains(memberName.trim())) return group // Already a member

        val updated = group.copy(members = group.members + memberName.trim())
        saveGroup(updated)
        return updated
    }

    /**
     * Removes a member from a group. Their existing expenses remain in history.
     *
     * @param groupId Group to remove member from.
     * @param memberName Member to remove.
     * @return Updated group, or null if group not found.
     */
    fun removeMember(groupId: Long, memberName: String): Group? {
        val group = getGroupById(groupId) ?: return null
        if (!group.members.contains(memberName)) return group

        val updated = group.copy(members = group.members - memberName)
        saveGroup(updated)
        return updated
    }

    /**
     * Archives a group (marks it inactive but keeps history).
     */
    fun archiveGroup(groupId: Long) {
        val group = getGroupById(groupId) ?: return
        saveGroup(group.copy(isActive = false))
    }

    /**
     * Gets all active groups.
     */
    fun getActiveGroups(): List<Group> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    /**
     * Gets all groups including archived.
     */
    fun getAllGroups(): List<Group> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    /**
     * Gets a group by ID.
     */
    fun getGroupById(groupId: Long): Group? {
        // TODO: Wire to Room DAO
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expense Logging
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Logs an expense to a group.
     *
     * @param groupId Target group.
     * @param paidBy Who paid (must be a group member).
     * @param amount Total amount.
     * @param description What was purchased.
     * @param splitType How to split.
     * @param splitDetails For BY_AMOUNT/BY_PERCENTAGE: map of member→share.
     *                     For EXCLUDE_SOME: map of excluded members → 0.0.
     *                     For EQUAL: can be empty (auto-calculated).
     * @return [ExpenseResult] with the created expense and updated balances.
     */
    fun addExpense(
        groupId: Long,
        paidBy: String,
        amount: Double,
        description: String,
        splitType: SplitType,
        splitDetails: Map<String, Double> = emptyMap()
    ): ExpenseResult? {
        val group = getGroupById(groupId) ?: return null
        require(group.members.contains(paidBy)) { "'$paidBy' is not a member of this group." }
        require(amount > 0) { "Amount must be positive." }

        // Calculate the actual split
        val finalSplit = calculateSplit(group.members, amount, splitType, splitDetails)

        val expense = GroupExpense(
            id = System.nanoTime(),
            groupId = groupId,
            paidBy = paidBy,
            amount = amount,
            description = description,
            splitType = splitType,
            splitDetails = finalSplit,
            date = System.currentTimeMillis()
        )

        saveExpense(expense)

        val updatedBalances = calculateBalances(groupId)
        return ExpenseResult(expense = expense, updatedBalances = updatedBalances)
    }

    /**
     * Records a settlement (one person paying back another).
     *
     * @param groupId Target group.
     * @param from Person who is paying.
     * @param to Person being paid back.
     * @param amount Amount settled.
     * @param note Optional note.
     * @return The settlement expense record.
     */
    fun recordSettlement(
        groupId: Long,
        from: String,
        to: String,
        amount: Double,
        note: String? = null
    ): GroupExpense? {
        val group = getGroupById(groupId) ?: return null
        require(group.members.contains(from)) { "'$from' is not a member of this group." }
        require(group.members.contains(to)) { "'$to' is not a member of this group." }
        require(amount > 0) { "Settlement amount must be positive." }

        // A settlement is recorded as an expense where 'from' paid 'to',
        // and only 'from' owes the full amount to 'to'.
        val expense = GroupExpense(
            id = System.nanoTime(),
            groupId = groupId,
            paidBy = from,
            amount = amount,
            description = "Settlement: $from → $to" + (note?.let { " ($it)" } ?: ""),
            splitType = SplitType.BY_AMOUNT,
            splitDetails = mapOf(to to amount),
            date = System.currentTimeMillis(),
            isSettlement = true
        )

        saveExpense(expense)
        return expense
    }

    /**
     * Deletes an expense from a group.
     *
     * @param expenseId Expense to delete.
     * @return true if deleted successfully.
     */
    fun deleteExpense(expenseId: Long): Boolean {
        // TODO: Wire to Room DAO
        return false
    }

    /**
     * Gets all expenses for a group, ordered by date (newest first).
     */
    fun getGroupExpenses(groupId: Long): List<GroupExpense> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Balance Calculation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the net balance for each member in a group.
     *
     * Positive balance = others owe this person (they've over-paid their share).
     * Negative balance = this person owes others (they've under-paid).
     *
     * @param groupId Group to calculate for.
     * @return Map of member name → net balance.
     */
    fun calculateBalances(groupId: Long): Map<String, Double> {
        val group = getGroupById(groupId) ?: return emptyMap()
        val expenses = getGroupExpenses(groupId)

        // Track what each person has paid and what they owe
        val paid = mutableMapOf<String, Double>()
        val owes = mutableMapOf<String, Double>()

        for (member in group.members) {
            paid[member] = 0.0
            owes[member] = 0.0
        }

        for (expense in expenses) {
            if (expense.isSettlement) {
                // Settlements: payer's balance decreases, receiver's balance decreases
                paid[expense.paidBy] = (paid[expense.paidBy] ?: 0.0) + expense.amount
                for ((member, share) in expense.splitDetails) {
                    owes[member] = (owes[member] ?: 0.0) + share
                }
            } else {
                // Regular expense: payer paid the full amount
                paid[expense.paidBy] = (paid[expense.paidBy] ?: 0.0) + expense.amount

                // Each member owes their split share
                for ((member, share) in expense.splitDetails) {
                    owes[member] = (owes[member] ?: 0.0) + share
                }
            }
        }

        // Net balance = total paid - total owed
        val balances = mutableMapOf<String, Double>()
        for (member in group.members) {
            balances[member] = (paid[member] ?: 0.0) - (owes[member] ?: 0.0)
        }

        return balances
    }

    /**
     * Calculates detailed per-member summaries.
     */
    fun getMemberSummaries(groupId: Long): List<MemberSummary> {
        val group = getGroupById(groupId) ?: return emptyList()
        val expenses = getGroupExpenses(groupId).filter { !it.isSettlement }
        val balances = calculateBalances(groupId)

        val totalGroupSpend = expenses.sumOf { it.amount }
        val fairShare = if (group.members.isNotEmpty()) totalGroupSpend / group.members.size else 0.0

        return group.members.map { member ->
            val totalPaid = expenses.filter { it.paidBy == member }.sumOf { it.amount }
            val totalOwes = expenses.flatMap { expense ->
                expense.splitDetails.entries.filter { it.key == member }
            }.sumOf { it.value }

            MemberSummary(
                memberName = member,
                totalPaid = totalPaid,
                totalShare = totalOwes,
                balance = balances[member] ?: 0.0,
                fairShareDeviation = totalPaid - fairShare
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Debt Simplification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simplifies debts to minimize the number of transactions needed to settle.
     *
     * Example: A owes B ₹600, B owes C ₹400
     * Simplified: A pays B ₹200, A pays C ₹400 (2 transactions instead of 2, but optimized amounts)
     *
     * Uses a greedy algorithm: match largest creditor with largest debtor.
     *
     * @param groupId Group to simplify debts for.
     * @return List of simplified debt transactions.
     */
    fun simplifyDebts(groupId: Long): List<DebtSimplification> {
        val balances = calculateBalances(groupId)
        if (balances.isEmpty()) return emptyList()

        // Separate into creditors (positive balance) and debtors (negative balance)
        val creditors = mutableListOf<Pair<String, Double>>() // people who are owed money
        val debtors = mutableListOf<Pair<String, Double>>()   // people who owe money

        for ((member, balance) in balances) {
            when {
                balance > 0.01 -> creditors.add(Pair(member, balance))
                balance < -0.01 -> debtors.add(Pair(member, -balance)) // Store as positive amount
            }
        }

        // Sort by amount (largest first) for greedy matching
        creditors.sortByDescending { it.second }
        debtors.sortByDescending { it.second }

        val settlements = mutableListOf<DebtSimplification>()

        // Greedy matching: pair largest debtor with largest creditor
        val creditorsQueue = creditors.toMutableList()
        val debtorsQueue = debtors.toMutableList()

        while (creditorsQueue.isNotEmpty() && debtorsQueue.isNotEmpty()) {
            val creditor = creditorsQueue[0]
            val debtor = debtorsQueue[0]

            val settleAmount = minOf(creditor.second, debtor.second)

            if (settleAmount > 0.01) { // Ignore trivial amounts
                settlements.add(
                    DebtSimplification(
                        from = debtor.first,
                        to = creditor.first,
                        amount = roundToTwo(settleAmount)
                    )
                )
            }

            // Update remaining amounts
            val remainingCredit = creditor.second - settleAmount
            val remainingDebt = debtor.second - settleAmount

            creditorsQueue.removeAt(0)
            debtorsQueue.removeAt(0)

            if (remainingCredit > 0.01) {
                creditorsQueue.add(0, Pair(creditor.first, remainingCredit))
            }
            if (remainingDebt > 0.01) {
                debtorsQueue.add(0, Pair(debtor.first, remainingDebt))
            }
        }

        return settlements
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shareable Summary
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a plain-text shareable summary of a group's expenses and balances.
     * Users can copy this and paste into any messaging app.
     *
     * @param groupId Group to summarize.
     * @return Formatted text summary.
     */
    fun generateShareableGroupSummary(groupId: Long): String {
        val group = getGroupById(groupId) ?: return "Group not found."
        val expenses = getGroupExpenses(groupId).filter { !it.isSettlement }
        val balances = calculateBalances(groupId)
        val simplifiedDebts = simplifyDebts(groupId)
        val memberSummaries = getMemberSummaries(groupId)

        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Header
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("${group.emoji ?: "📊"} ${group.name}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine()

        // Total spend
        val totalSpend = expenses.sumOf { it.amount }
        sb.appendLine("💰 Total Group Spend: $CURRENCY${formatAmount(totalSpend)}")
        sb.appendLine("👥 Members: ${group.members.joinToString(", ")}")
        sb.appendLine("📅 Period: ${dateFormat.format(Date(group.createdAt))} — ${dateFormat.format(Date())}")
        sb.appendLine()

        // Per-member totals
        sb.appendLine("── Who Paid What ──")
        for (summary in memberSummaries.sortedByDescending { it.totalPaid }) {
            val deviation = if (summary.fairShareDeviation >= 0) {
                "+$CURRENCY${formatAmount(summary.fairShareDeviation)} over fair share"
            } else {
                "$CURRENCY${formatAmount(-summary.fairShareDeviation)} under fair share"
            }
            sb.appendLine("  ${summary.memberName}: $CURRENCY${formatAmount(summary.totalPaid)} ($deviation)")
        }
        sb.appendLine()

        // Simplified settlements
        if (simplifiedDebts.isNotEmpty()) {
            sb.appendLine("── Who Owes Whom ──")
            for (debt in simplifiedDebts) {
                sb.appendLine("  ${debt.from} → ${debt.to}: $CURRENCY${formatAmount(debt.amount)}")
            }
        } else {
            sb.appendLine("✅ All settled! No pending balances.")
        }
        sb.appendLine()

        // Recent expenses (last 10)
        if (expenses.isNotEmpty()) {
            sb.appendLine("── Recent Expenses ──")
            val recent = expenses.sortedByDescending { it.date }.take(10)
            for (expense in recent) {
                val date = dateFormat.format(Date(expense.date))
                sb.appendLine("  $date | ${expense.paidBy} paid $CURRENCY${formatAmount(expense.amount)} — ${expense.description}")
            }
            if (expenses.size > 10) {
                sb.appendLine("  ... and ${expenses.size - 10} more")
            }
        }

        sb.appendLine()
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Generated on ${dateFormat.format(Date())}")

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Split Calculation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the per-member split for an expense.
     *
     * @param members All group members.
     * @param amount Total amount to split.
     * @param splitType How to split.
     * @param splitDetails User-provided split info (varies by type).
     * @return Map of member name → their share amount.
     */
    private fun calculateSplit(
        members: List<String>,
        amount: Double,
        splitType: SplitType,
        splitDetails: Map<String, Double>
    ): Map<String, Double> {
        return when (splitType) {
            SplitType.EQUAL -> {
                val perPerson = amount / members.size
                members.associateWith { roundToTwo(perPerson) }
            }

            SplitType.BY_AMOUNT -> {
                // Validate that splits sum to total
                val providedTotal = splitDetails.values.sum()
                if (kotlin.math.abs(providedTotal - amount) > 1.0) {
                    // Proportionally adjust to match total
                    val ratio = amount / providedTotal
                    splitDetails.mapValues { roundToTwo(it.value * ratio) }
                } else {
                    splitDetails
                }
            }

            SplitType.BY_PERCENTAGE -> {
                // splitDetails contains member → percentage (should sum to 100)
                val totalPercent = splitDetails.values.sum()
                splitDetails.mapValues { (_, percent) ->
                    roundToTwo(amount * (percent / totalPercent))
                }
            }

            SplitType.EXCLUDE_SOME -> {
                // splitDetails contains excluded members mapped to 0.0
                val excludedMembers = splitDetails.filter { it.value == 0.0 }.keys
                val includedMembers = members.filter { it !in excludedMembers }
                if (includedMembers.isEmpty()) {
                    members.associateWith { roundToTwo(amount / members.size) }
                } else {
                    val perPerson = amount / includedMembers.size
                    members.associateWith { member ->
                        if (member in includedMembers) roundToTwo(perPerson) else 0.0
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Group Templates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets pre-defined group templates for common scenarios.
     */
    fun getGroupTemplates(): List<GroupTemplate> {
        return listOf(
            GroupTemplate(
                name = "Monthly Flat Expenses",
                emoji = "🏠",
                expenses = listOf(
                    TemplateExpense("Rent", 15000.0, SplitType.EQUAL, recurringDay = 1),
                    TemplateExpense("Electricity Bill", 2000.0, SplitType.EQUAL, recurringDay = 5),
                    TemplateExpense("WiFi / Internet", 1000.0, SplitType.EQUAL, recurringDay = 1),
                    TemplateExpense("Cook / Maid", 4000.0, SplitType.EQUAL, recurringDay = 1),
                    TemplateExpense("Water / Tanker", 500.0, SplitType.EQUAL, recurringDay = 15),
                    TemplateExpense("Gas Cylinder", 900.0, SplitType.EQUAL),
                    TemplateExpense("Society Maintenance", 3000.0, SplitType.EQUAL, recurringDay = 1)
                )
            ),
            GroupTemplate(
                name = "Trip Expenses",
                emoji = "✈️",
                expenses = listOf(
                    TemplateExpense("Travel / Tickets", 5000.0, SplitType.EQUAL),
                    TemplateExpense("Accommodation", 8000.0, SplitType.EQUAL),
                    TemplateExpense("Food & Restaurants", 3000.0, SplitType.EQUAL),
                    TemplateExpense("Local Transport", 2000.0, SplitType.EQUAL),
                    TemplateExpense("Activities / Entry Fees", 1500.0, SplitType.EQUAL),
                    TemplateExpense("Shopping / Souvenirs", 1000.0, SplitType.BY_AMOUNT)
                )
            ),
            GroupTemplate(
                name = "Office Lunch Pool",
                emoji = "🍱",
                expenses = listOf(
                    TemplateExpense("Lunch", 200.0, SplitType.EQUAL),
                    TemplateExpense("Snacks / Tea", 50.0, SplitType.EQUAL),
                    TemplateExpense("Birthday Cake / Party", 500.0, SplitType.EQUAL)
                )
            ),
            GroupTemplate(
                name = "Couple / Partner Expenses",
                emoji = "❤️",
                expenses = listOf(
                    TemplateExpense("Dinner / Date", 2000.0, SplitType.EQUAL),
                    TemplateExpense("Groceries", 3000.0, SplitType.EQUAL),
                    TemplateExpense("Gifts", 1000.0, SplitType.BY_AMOUNT),
                    TemplateExpense("Shared Subscription", 500.0, SplitType.EQUAL)
                )
            )
        )
    }

    /**
     * Creates a group pre-filled with expenses from a template.
     *
     * @param templateName Name of the template to use.
     * @param groupName Custom group name.
     * @param members Group members.
     * @return The created group (expenses from template are added as recurring reminders).
     */
    fun createGroupFromTemplate(
        templateName: String,
        groupName: String,
        members: List<String>
    ): Group? {
        val template = getGroupTemplates().find { it.name == templateName } ?: return null

        val group = createGroup(
            name = groupName,
            members = members,
            emoji = template.emoji
        )

        // Set up recurring expense reminders based on template
        for (templateExpense in template.expenses) {
            if (templateExpense.recurringDay != null) {
                createRecurringExpenseReminder(
                    groupId = group.id,
                    description = templateExpense.description,
                    estimatedAmount = templateExpense.estimatedAmount,
                    splitType = templateExpense.splitType,
                    dayOfMonth = templateExpense.recurringDay
                )
            }
        }

        return group
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recurring Expenses
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a recurring expense reminder for a group.
     */
    fun createRecurringExpenseReminder(
        groupId: Long,
        description: String,
        estimatedAmount: Double,
        splitType: SplitType,
        dayOfMonth: Int
    ) {
        // TODO: Store in Room and schedule reminder via WorkManager/AlarmManager
    }

    /**
     * Gets recurring expenses that are due today or overdue.
     */
    fun getDueRecurringExpenses(): List<GroupExpense> {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        // TODO: Query Room for recurring expenses where recurringDay <= today
        // and not yet logged for this month
        return emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fair Share Indicator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets a human-readable fair share message for a specific member.
     *
     * @param groupId Group to check.
     * @param memberName The member to describe.
     * @return A message like "You're ₹2,600 OVER your fair share. Others owe you."
     */
    fun getFairShareMessage(groupId: Long, memberName: String): String {
        val summaries = getMemberSummaries(groupId)
        val summary = summaries.find { it.memberName == memberName }
            ?: return "No data for $memberName."

        val deviation = summary.fairShareDeviation

        return when {
            deviation > 100 -> "You're $CURRENCY${formatAmount(deviation)} OVER your fair share. Others owe you."
            deviation < -100 -> "You're $CURRENCY${formatAmount(-deviation)} UNDER your fair share. You owe others."
            else -> "You're right at your fair share. All good! ✅"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rounds a double to 2 decimal places.
     */
    private fun roundToTwo(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    /**
     * Formats an amount for display (no decimals if whole number).
     */
    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format(Locale.getDefault(), "%,.0f", amount)
        } else {
            String.format(Locale.getDefault(), "%,.2f", amount)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence Stubs (to be wired to Room)
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveGroup(group: Group) {
        // TODO: Wire to Room DAO
        // groupDao.insertOrUpdate(group.toEntity())
    }

    private fun saveExpense(expense: GroupExpense) {
        // TODO: Wire to Room DAO
        // groupExpenseDao.insert(expense.toEntity())
    }
}
