package com.paisabrain.app.ai

import java.time.LocalDate
import java.time.LocalDateTime
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Multi-person family budget management system.
 *
 * Allows users to create family profiles, assign transactions to members,
 * manage shared and personal budgets, track per-person spending, calculate
 * fair share distribution, and sync data locally via encrypted JSON export/import.
 * All data is stored locally — no cloud services, no brand references.
 */
class FamilyBudgetMode {

    // ─────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Represents a family member in the budget system.
     *
     * @property id Unique identifier.
     * @property name Display name.
     * @property role Family role (self, partner, child, parent, other).
     * @property linkedAccountIds Account/card identifiers linked to auto-assign transactions.
     * @property personalBudget Monthly personal spending allowance.
     * @property avatarColorHex Hex color for UI avatar circle.
     * @property addedDate When this member was added.
     */
    data class FamilyMember(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val role: FamilyRole,
        val linkedAccountIds: List<String> = emptyList(),
        val personalBudget: Double = 0.0,
        val avatarColorHex: String = "#4CAF50",
        val addedDate: LocalDate = LocalDate.now()
    )

    /**
     * Family budget configuration including shared and personal allocations.
     *
     * @property id Unique budget identifier.
     * @property monthYear Month and year this budget applies to (format: "2024-03").
     * @property totalFamilyIncome Combined household income.
     * @property sharedBudgets Budgets for shared expense categories.
     * @property personalAllowances Per-member personal spending limits.
     * @property savingsGoalAmount Family savings target for the month.
     * @property actualSavings Actual savings achieved.
     */
    data class FamilyBudget(
        val id: String = UUID.randomUUID().toString(),
        val monthYear: String,
        val totalFamilyIncome: Double,
        val sharedBudgets: List<SharedBudgetCategory>,
        val personalAllowances: List<PersonalAllowance>,
        val savingsGoalAmount: Double = 0.0,
        val actualSavings: Double = 0.0
    )

    /**
     * A shared expense category budget (rent, groceries, utilities, etc.).
     *
     * @property category Expense category name.
     * @property allocatedAmount Monthly budget for this shared category.
     * @property spentAmount Amount spent so far this month.
     * @property contributorShares How the cost is split among members (member ID → percentage).
     */
    data class SharedBudgetCategory(
        val category: String,
        val allocatedAmount: Double,
        val spentAmount: Double = 0.0,
        val contributorShares: Map<String, Double> = emptyMap() // memberId → percentage (0-100)
    )

    /**
     * A shared expense transaction.
     *
     * @property id Unique expense identifier.
     * @property amount Transaction amount.
     * @property category Expense category.
     * @property description Brief description.
     * @property paidByMemberId Who actually paid.
     * @property splitAmongMemberIds Members this expense is shared between.
     * @property date Date of expense.
     * @property isSettled Whether balances have been settled.
     */
    data class SharedExpense(
        val id: String = UUID.randomUUID().toString(),
        val amount: Double,
        val category: String,
        val description: String,
        val paidByMemberId: String,
        val splitAmongMemberIds: List<String>,
        val date: LocalDate = LocalDate.now(),
        val isSettled: Boolean = false
    )

    /**
     * Personal spending allowance for a family member.
     *
     * @property memberId Linked family member.
     * @property monthlyLimit Monthly personal spending limit.
     * @property spentAmount Amount spent from personal budget this month.
     * @property categories Personal spending categories (entertainment, clothing, etc.).
     */
    data class PersonalAllowance(
        val memberId: String,
        val monthlyLimit: Double,
        val spentAmount: Double = 0.0,
        val categories: List<String> = listOf("Entertainment", "Clothing", "Personal Care", "Hobbies")
    )

    /**
     * Family savings goal.
     *
     * @property id Goal identifier.
     * @property title Goal name (e.g., "Vacation Fund").
     * @property targetAmount Target savings amount.
     * @property currentAmount Amount saved so far.
     * @property targetDate Goal deadline.
     * @property contributorIds Members contributing to this goal.
     * @property contributions Per-member contributions.
     */
    data class FamilyGoal(
        val id: String = UUID.randomUUID().toString(),
        val title: String,
        val targetAmount: Double,
        val currentAmount: Double = 0.0,
        val targetDate: LocalDate,
        val contributorIds: List<String>,
        val contributions: Map<String, Double> = emptyMap() // memberId → amount contributed
    )

    /**
     * Spending summary for a single family member.
     *
     * @property memberId Member identifier.
     * @property memberName Display name.
     * @property personalSpending Total personal spending this month.
     * @property sharedContribution Their share of shared expenses.
     * @property totalSpending Combined personal + shared spending.
     * @property budgetUtilization Percentage of personal budget used.
     * @property topCategories Top spending categories.
     */
    data class MemberSpendingSummary(
        val memberId: String,
        val memberName: String,
        val personalSpending: Double,
        val sharedContribution: Double,
        val totalSpending: Double,
        val budgetUtilization: Double, // percentage
        val topCategories: List<Pair<String, Double>> // category → amount
    )

    /**
     * Fair share calculation result.
     *
     * @property memberBalances Who owes whom and how much.
     * @property settlementSuggestions Simplified settlements to minimize transactions.
     */
    data class FairShareResult(
        val memberBalances: Map<String, Double>, // memberId → net balance (positive = owed, negative = owes)
        val settlementSuggestions: List<SettlementSuggestion>
    )

    /**
     * A suggested settlement between two members.
     */
    data class SettlementSuggestion(
        val fromMemberId: String,
        val fromMemberName: String,
        val toMemberId: String,
        val toMemberName: String,
        val amount: Double
    )

    /**
     * Combined family net worth view.
     */
    data class FamilyNetWorth(
        val totalAssets: Double,
        val totalLiabilities: Double,
        val netWorth: Double,
        val perMemberBreakdown: Map<String, Double> // memberId → individual net worth contribution
    )

    /**
     * Encrypted sync package for local data transfer.
     */
    data class SyncPackage(
        val encryptedData: String,
        val iv: String,
        val salt: String,
        val timestamp: LocalDateTime,
        val version: Int = 1,
        val checksum: String
    )

    /**
     * Family roles.
     */
    enum class FamilyRole(val displayName: String) {
        SELF("Self"),
        PARTNER("Partner"),
        CHILD("Child"),
        PARENT("Parent"),
        SIBLING("Sibling"),
        OTHER("Other Member")
    }

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private val familyMembers = mutableListOf<FamilyMember>()
    private val sharedExpenses = mutableListOf<SharedExpense>()
    private val familyGoals = mutableListOf<FamilyGoal>()
    private var currentBudget: FamilyBudget? = null
    private val transactionAssignments = mutableMapOf<String, String>() // transactionId → memberId

    // ─────────────────────────────────────────────────────────────────────
    // Member Management
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a family member to the system.
     *
     * @param member The family member to add.
     * @return The added member with generated ID.
     */
    fun addFamilyMember(member: FamilyMember): FamilyMember {
        familyMembers.add(member)
        return member
    }

    /**
     * Removes a family member.
     *
     * @param memberId The member to remove.
     * @return True if removed, false if not found.
     */
    fun removeFamilyMember(memberId: String): Boolean =
        familyMembers.removeAll { it.id == memberId }

    /**
     * Gets all family members.
     */
    fun getFamilyMembers(): List<FamilyMember> = familyMembers.toList()

    /**
     * Updates a family member's profile.
     */
    fun updateFamilyMember(updatedMember: FamilyMember): Boolean {
        val index = familyMembers.indexOfFirst { it.id == updatedMember.id }
        return if (index >= 0) {
            familyMembers[index] = updatedMember
            true
        } else false
    }

    // ─────────────────────────────────────────────────────────────────────
    // Transaction Assignment
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Auto-assigns a transaction to a family member based on linked accounts.
     *
     * @param transactionId The transaction to assign.
     * @param accountId The account/card the transaction was made from.
     * @return The assigned member ID, or null if no match found.
     */
    fun autoAssignTransaction(transactionId: String, accountId: String): String? {
        val member = familyMembers.find { accountId in it.linkedAccountIds }
        if (member != null) {
            transactionAssignments[transactionId] = member.id
        }
        return member?.id
    }

    /**
     * Manually assigns a transaction to a family member.
     *
     * @param transactionId The transaction to assign.
     * @param memberId The member to assign it to.
     */
    fun manualAssignTransaction(transactionId: String, memberId: String) {
        transactionAssignments[transactionId] = memberId
    }

    /**
     * Gets the assigned member for a transaction.
     */
    fun getTransactionAssignment(transactionId: String): String? =
        transactionAssignments[transactionId]

    // ─────────────────────────────────────────────────────────────────────
    // Shared Expenses
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Records a shared expense.
     *
     * @param expense The shared expense to record.
     */
    fun addSharedExpense(expense: SharedExpense) {
        sharedExpenses.add(expense)
    }

    /**
     * Calculates each member's share for a shared expense (equal split).
     *
     * @param expense The expense to split.
     * @return Map of memberId to their share amount.
     */
    fun calculateEqualSplit(expense: SharedExpense): Map<String, Double> {
        val sharePerPerson = expense.amount / expense.splitAmongMemberIds.size
        return expense.splitAmongMemberIds.associateWith { sharePerPerson }
    }

    /**
     * Calculates custom split based on defined percentages.
     *
     * @param expense The expense to split.
     * @param percentages Map of memberId to their percentage share.
     * @return Map of memberId to their share amount.
     */
    fun calculateCustomSplit(
        expense: SharedExpense,
        percentages: Map<String, Double>
    ): Map<String, Double> {
        return percentages.mapValues { (_, percentage) ->
            expense.amount * (percentage / 100.0)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fair Share Calculator
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Calculates who owes whom based on all shared expenses.
     * Positive balance = owed money. Negative balance = owes money.
     *
     * @return Fair share calculation with settlement suggestions.
     */
    fun calculateFairShare(): FairShareResult {
        val balances = mutableMapOf<String, Double>()

        // Initialize all members with zero balance
        familyMembers.forEach { balances[it.id] = 0.0 }

        // Process each shared expense
        sharedExpenses.filter { !it.isSettled }.forEach { expense ->
            val sharePerPerson = expense.amount / expense.splitAmongMemberIds.size

            // The person who paid is owed by others
            balances[expense.paidByMemberId] =
                (balances[expense.paidByMemberId] ?: 0.0) + expense.amount

            // Each member who shares the expense owes their portion
            expense.splitAmongMemberIds.forEach { memberId ->
                balances[memberId] = (balances[memberId] ?: 0.0) - sharePerPerson
            }
        }

        // Generate settlement suggestions (simplified)
        val settlements = generateSettlements(balances)

        return FairShareResult(
            memberBalances = balances,
            settlementSuggestions = settlements
        )
    }

    private fun generateSettlements(balances: Map<String, Double>): List<SettlementSuggestion> {
        val settlements = mutableListOf<SettlementSuggestion>()
        val debtors = balances.filter { it.value < -0.01 }.toMutableMap() // those who owe
        val creditors = balances.filter { it.value > 0.01 }.toMutableMap() // those owed

        val sortedDebtors = debtors.entries.sortedBy { it.value }.toMutableList()
        val sortedCreditors = creditors.entries.sortedByDescending { it.value }.toMutableList()

        var i = 0
        var j = 0
        while (i < sortedDebtors.size && j < sortedCreditors.size) {
            val debtor = sortedDebtors[i]
            val creditor = sortedCreditors[j]

            val amount = minOf(-debtor.value, creditor.value)
            if (amount > 0.01) {
                val fromMember = familyMembers.find { it.id == debtor.key }
                val toMember = familyMembers.find { it.id == creditor.key }

                if (fromMember != null && toMember != null) {
                    settlements.add(
                        SettlementSuggestion(
                            fromMemberId = debtor.key,
                            fromMemberName = fromMember.name,
                            toMemberId = creditor.key,
                            toMemberName = toMember.name,
                            amount = amount
                        )
                    )
                }

                sortedDebtors[i] = sortedDebtors[i].let {
                    object : MutableMap.MutableEntry<String, Double> {
                        override val key = it.key
                        override var value = it.value + amount
                        override fun setValue(newValue: Double): Double {
                            val old = value; value = newValue; return old
                        }
                    }
                }
                sortedCreditors[j] = sortedCreditors[j].let {
                    object : MutableMap.MutableEntry<String, Double> {
                        override val key = it.key
                        override var value = it.value - amount
                        override fun setValue(newValue: Double): Double {
                            val old = value; value = newValue; return old
                        }
                    }
                }
            }

            if (sortedDebtors[i].value >= -0.01) i++
            if (sortedCreditors[j].value <= 0.01) j++
        }

        return settlements
    }

    // ─────────────────────────────────────────────────────────────────────
    // Per-Person Summary
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates a spending summary for each family member.
     *
     * @param personalSpendingByMember Map of memberId → total personal spending.
     * @param categoryBreakdown Map of memberId → (category → amount).
     * @return List of spending summaries for all members.
     */
    fun generateMemberSummaries(
        personalSpendingByMember: Map<String, Double>,
        categoryBreakdown: Map<String, Map<String, Double>>
    ): List<MemberSpendingSummary> {
        return familyMembers.map { member ->
            val personalSpending = personalSpendingByMember[member.id] ?: 0.0
            val sharedContribution = calculateMemberSharedContribution(member.id)
            val totalSpending = personalSpending + sharedContribution
            val budgetUtil = if (member.personalBudget > 0) {
                (personalSpending / member.personalBudget) * 100
            } else 0.0

            val categories = categoryBreakdown[member.id]?.entries
                ?.sortedByDescending { it.value }
                ?.take(5)
                ?.map { Pair(it.key, it.value) }
                ?: emptyList()

            MemberSpendingSummary(
                memberId = member.id,
                memberName = member.name,
                personalSpending = personalSpending,
                sharedContribution = sharedContribution,
                totalSpending = totalSpending,
                budgetUtilization = budgetUtil,
                topCategories = categories
            )
        }
    }

    private fun calculateMemberSharedContribution(memberId: String): Double {
        return sharedExpenses
            .filter { memberId in it.splitAmongMemberIds && !it.isSettled }
            .sumOf { expense ->
                expense.amount / expense.splitAmongMemberIds.size
            }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Family Goals
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a family savings goal.
     *
     * @param goal The goal to create.
     */
    fun createFamilyGoal(goal: FamilyGoal) {
        familyGoals.add(goal)
    }

    /**
     * Adds a contribution to a family goal.
     *
     * @param goalId The goal to contribute to.
     * @param memberId The contributing member.
     * @param amount Contribution amount.
     * @return Updated goal, or null if not found.
     */
    fun contributeToGoal(goalId: String, memberId: String, amount: Double): FamilyGoal? {
        val index = familyGoals.indexOfFirst { it.id == goalId }
        if (index < 0) return null

        val goal = familyGoals[index]
        val updatedContributions = goal.contributions.toMutableMap()
        updatedContributions[memberId] = (updatedContributions[memberId] ?: 0.0) + amount

        val updatedGoal = goal.copy(
            currentAmount = goal.currentAmount + amount,
            contributions = updatedContributions
        )
        familyGoals[index] = updatedGoal
        return updatedGoal
    }

    /**
     * Gets progress towards all family goals.
     *
     * @return List of goals with completion percentages.
     */
    fun getGoalProgress(): List<Pair<FamilyGoal, Double>> {
        return familyGoals.map { goal ->
            val progress = if (goal.targetAmount > 0) {
                (goal.currentAmount / goal.targetAmount * 100).coerceAtMost(100.0)
            } else 0.0
            Pair(goal, progress)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Combined Net Worth
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Calculates combined family net worth.
     *
     * @param assetsByMember Map of memberId → total assets.
     * @param liabilitiesByMember Map of memberId → total liabilities.
     * @return Combined family net worth view.
     */
    fun calculateFamilyNetWorth(
        assetsByMember: Map<String, Double>,
        liabilitiesByMember: Map<String, Double>
    ): FamilyNetWorth {
        val totalAssets = assetsByMember.values.sum()
        val totalLiabilities = liabilitiesByMember.values.sum()

        val perMemberNetWorth = familyMembers.associate { member ->
            val assets = assetsByMember[member.id] ?: 0.0
            val liabilities = liabilitiesByMember[member.id] ?: 0.0
            member.id to (assets - liabilities)
        }

        return FamilyNetWorth(
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = totalAssets - totalLiabilities,
            perMemberBreakdown = perMemberNetWorth
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Local Sync (Encrypted JSON Export/Import)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Exports all family budget data as an encrypted JSON package for local sync.
     * Uses AES-256 encryption with a user-provided passphrase.
     *
     * @param passphrase User-provided passphrase for encryption.
     * @return Encrypted sync package ready for transfer.
     */
    fun exportForLocalSync(passphrase: String): SyncPackage {
        val jsonData = buildSyncJson()

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encryptedBytes = cipher.doFinal(jsonData.toByteArray(Charsets.UTF_8))

        val checksum = jsonData.hashCode().toString(16)

        return SyncPackage(
            encryptedData = Base64.getEncoder().encodeToString(encryptedBytes),
            iv = Base64.getEncoder().encodeToString(iv),
            salt = Base64.getEncoder().encodeToString(salt),
            timestamp = LocalDateTime.now(),
            checksum = checksum
        )
    }

    /**
     * Imports family budget data from an encrypted sync package.
     *
     * @param syncPackage The encrypted package to import.
     * @param passphrase Passphrase to decrypt the data.
     * @return True if import was successful, false if decryption/parsing failed.
     */
    fun importFromLocalSync(syncPackage: SyncPackage, passphrase: String): Boolean {
        return try {
            val salt = Base64.getDecoder().decode(syncPackage.salt)
            val iv = Base64.getDecoder().decode(syncPackage.iv)
            val encryptedBytes = Base64.getDecoder().decode(syncPackage.encryptedData)

            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, 65536, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = SecretKeySpec(factory.generateSecret(keySpec).encoded, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val jsonData = String(decryptedBytes, Charsets.UTF_8)
            parseSyncJson(jsonData)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildSyncJson(): String {
        // Builds a JSON representation of all family data
        // In production, use kotlinx.serialization or Gson
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"members\":[")
        sb.append(familyMembers.joinToString(",") { member ->
            """{"id":"${member.id}","name":"${member.name}","role":"${member.role}","budget":${member.personalBudget}}"""
        })
        sb.append("],")
        sb.append("\"expenses\":[")
        sb.append(sharedExpenses.joinToString(",") { expense ->
            """{"id":"${expense.id}","amount":${expense.amount},"category":"${expense.category}","paidBy":"${expense.paidByMemberId}","date":"${expense.date}"}"""
        })
        sb.append("],")
        sb.append("\"goals\":[")
        sb.append(familyGoals.joinToString(",") { goal ->
            """{"id":"${goal.id}","title":"${goal.title}","target":${goal.targetAmount},"current":${goal.currentAmount}}"""
        })
        sb.append("]}")
        return sb.toString()
    }

    private fun parseSyncJson(json: String) {
        // In production, use kotlinx.serialization or Gson for proper parsing
        // This is a placeholder for the parsing logic
        // Parse members, expenses, goals from JSON and merge/replace local data
    }

    // ─────────────────────────────────────────────────────────────────────
    // Budget Management
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates or updates the monthly family budget.
     *
     * @param budget The family budget configuration.
     */
    fun setFamilyBudget(budget: FamilyBudget) {
        currentBudget = budget
    }

    /**
     * Gets the current family budget.
     */
    fun getCurrentBudget(): FamilyBudget? = currentBudget

    /**
     * Identifies shared expense categories.
     */
    fun getSharedCategories(): List<String> = listOf(
        "Rent/EMI",
        "Groceries",
        "Utilities (Electricity)",
        "Utilities (Water)",
        "Internet/Phone",
        "Household Supplies",
        "Children's Education",
        "Medical/Health",
        "Transportation",
        "Domestic Help",
        "Insurance Premiums",
        "Dining Out (Family)"
    )

    /**
     * Identifies personal expense categories.
     */
    fun getPersonalCategories(): List<String> = listOf(
        "Entertainment",
        "Clothing & Fashion",
        "Personal Care",
        "Hobbies",
        "Snacks & Beverages",
        "Subscriptions (Personal)",
        "Gifts",
        "Sports & Fitness"
    )
}
