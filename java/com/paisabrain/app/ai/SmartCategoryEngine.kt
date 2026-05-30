package com.paisabrain.app.ai

import android.content.Context
import java.util.*

/**
 * Smart Category Engine — a self-improving categorization system that learns from user corrections.
 *
 * The system builds an ever-growing knowledge base of merchant→category mappings:
 * 1. Initial categorization uses keyword matching and patterns.
 * 2. When users correct a category, the correction is stored permanently.
 * 3. After N corrections for similar merchants, the engine detects PATTERNS
 *    and suggests bulk re-categorization.
 * 4. Time-of-day, amount, and day-of-week patterns further improve guesses.
 *
 * All learning is local — no data leaves the device.
 */
class SmartCategoryEngine(private val context: Context) {

    companion object {
        /** Number of similar corrections needed before suggesting a bulk pattern rule. */
        private const val PATTERN_DETECTION_THRESHOLD = 3

        /** Confidence score for auto-categorized transactions. */
        private const val AUTO_CONFIDENCE = 0.70f

        /** Confidence score for user-corrected transactions. */
        private const val USER_CORRECTED_CONFIDENCE = 1.0f

        /** Maximum number of recent searches/corrections to retain for suggestion engine. */
        private const val MAX_RECENT_CORRECTIONS = 500

        /** Default categories available in the system. */
        val DEFAULT_CATEGORIES = listOf(
            "Food & Drinks",
            "Groceries",
            "Transport",
            "Shopping",
            "Entertainment",
            "Health & Medical",
            "Education",
            "Utilities",
            "Rent",
            "Household",
            "Personal Care",
            "Subscriptions",
            "Insurance",
            "Investments",
            "Transfers",
            "Salary",
            "Freelance Income",
            "Gifts & Donations",
            "Travel",
            "EMI & Loans",
            "Miscellaneous"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A learned rule mapping a merchant pattern to a category.
     *
     * @param id Unique identifier for this rule.
     * @param merchantPattern The merchant name or pattern (e.g., "food_delivery_123", "*mart*").
     * @param assignedCategory The category this merchant belongs to.
     * @param confidence Confidence score (0.0 - 1.0).
     * @param source How this rule was created.
     * @param createdAt Timestamp of rule creation.
     * @param lastUsedAt Timestamp of last time this rule was applied.
     * @param matchCount How many transactions have matched this rule.
     * @param isPatternRule Whether this is a pattern (contains wildcards) vs. exact match.
     */
    data class MerchantRule(
        val id: Long,
        val merchantPattern: String,
        val assignedCategory: String,
        val confidence: Float,
        val source: RuleSource,
        val createdAt: Long,
        val lastUsedAt: Long = createdAt,
        val matchCount: Int = 1,
        val isPatternRule: Boolean = false
    )

    /**
     * How a merchant rule was created.
     */
    enum class RuleSource {
        /** Auto-detected from keyword matching. */
        AUTO,
        /** User manually corrected the category. */
        USER_CORRECTED,
        /** User confirmed a bulk pattern suggestion. */
        PATTERN_CONFIRMED,
        /** Pre-loaded default rules. */
        DEFAULT
    }

    /**
     * Records a user's category correction for a transaction.
     *
     * @param transactionId The transaction that was corrected.
     * @param merchantName The merchant name from the transaction.
     * @param oldCategory What the engine originally assigned.
     * @param newCategory What the user changed it to.
     * @param timestamp When the correction happened.
     */
    data class CategoryCorrection(
        val transactionId: Long,
        val merchantName: String,
        val oldCategory: String,
        val newCategory: String,
        val timestamp: Long
    )

    /**
     * A category suggestion returned by the engine.
     *
     * @param category The suggested category.
     * @param confidence Confidence score (0.0 - 1.0).
     * @param source How the suggestion was determined.
     * @param alternativeCategories Other possible categories ranked by confidence.
     * @param matchedRule The rule that produced this suggestion, if any.
     */
    data class CategorySuggestion(
        val category: String,
        val confidence: Float,
        val source: RuleSource,
        val alternativeCategories: List<Pair<String, Float>> = emptyList(),
        val matchedRule: MerchantRule? = null
    )

    /**
     * A suggestion for bulk re-categorization based on detected patterns.
     *
     * @param pattern The pattern detected (e.g., merchants containing "mart").
     * @param suggestedCategory Category to assign.
     * @param affectedTransactionIds Transactions that would be re-categorized.
     * @param affectedMerchants Merchant names that match the pattern.
     * @param reason Human-readable explanation of why this is suggested.
     * @param correctionCount How many user corrections led to this suggestion.
     */
    data class BulkSuggestion(
        val pattern: String,
        val suggestedCategory: String,
        val affectedTransactionIds: List<Long>,
        val affectedMerchants: List<String>,
        val reason: String,
        val correctionCount: Int
    )

    /**
     * A multi-category split suggestion for a single transaction.
     *
     * @param transactionId The transaction to split.
     * @param totalAmount Total transaction amount.
     * @param suggestedSplits Proposed category→amount splits.
     * @param reason Why a split is suggested.
     */
    data class SplitSuggestion(
        val transactionId: Long,
        val totalAmount: Double,
        val suggestedSplits: Map<String, Double>,
        val reason: String
    )

    /**
     * A user-defined alias for a merchant.
     *
     * @param originalMerchant The merchant name as it appears in SMS.
     * @param userAlias The user's friendly name (e.g., "Restaurant near office").
     * @param category Assigned category.
     */
    data class MerchantAlias(
        val originalMerchant: String,
        val userAlias: String,
        val category: String
    )

    /**
     * Represents a custom category created by the user.
     *
     * @param name Category name.
     * @param emoji Optional emoji for display.
     * @param parentCategory Optional parent for grouping (e.g., "Shared" under "Social").
     * @param createdAt When the user created this category.
     * @param transactionCount Number of transactions in this category.
     */
    data class CustomCategory(
        val name: String,
        val emoji: String? = null,
        val parentCategory: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val transactionCount: Int = 0
    )

    /**
     * Represents a transaction for categorization purposes.
     */
    data class Transaction(
        val id: Long,
        val merchantName: String,
        val amount: Double,
        val timestamp: Long,
        val category: String,
        val rawSms: String? = null,
        val notes: String? = null,
        val tags: List<String> = emptyList()
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Core Categorization
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Categorizes a transaction based on merchant name, amount, and time.
     * Uses the full learning hierarchy:
     * 1. Exact merchant match from user corrections (highest priority)
     * 2. Pattern rules from confirmed bulk suggestions
     * 3. Time/amount/day-of-week contextual patterns
     * 4. Keyword-based default rules
     * 5. Falls back to "Miscellaneous" with low confidence
     *
     * @param merchant Merchant name from the SMS.
     * @param amount Transaction amount.
     * @param time Timestamp of the transaction.
     * @return [CategorySuggestion] with the best guess and alternatives.
     */
    fun categorize(merchant: String, amount: Double, time: Long): CategorySuggestion {
        val normalizedMerchant = normalizeMerchant(merchant)

        // Priority 1: Exact user-corrected match
        val exactRule = findExactRule(normalizedMerchant)
        if (exactRule != null && exactRule.source == RuleSource.USER_CORRECTED) {
            updateRuleUsage(exactRule.id)
            return CategorySuggestion(
                category = exactRule.assignedCategory,
                confidence = USER_CORRECTED_CONFIDENCE,
                source = RuleSource.USER_CORRECTED,
                matchedRule = exactRule,
                alternativeCategories = getAlternatives(normalizedMerchant, exactRule.assignedCategory)
            )
        }

        // Priority 2: Pattern rule match
        val patternRule = findPatternRule(normalizedMerchant)
        if (patternRule != null) {
            updateRuleUsage(patternRule.id)
            return CategorySuggestion(
                category = patternRule.assignedCategory,
                confidence = patternRule.confidence,
                source = patternRule.source,
                matchedRule = patternRule,
                alternativeCategories = getAlternatives(normalizedMerchant, patternRule.assignedCategory)
            )
        }

        // Priority 3: Contextual patterns (time + amount + day)
        val contextCategory = categorizeByContext(normalizedMerchant, amount, time)
        if (contextCategory != null) {
            return CategorySuggestion(
                category = contextCategory.first,
                confidence = contextCategory.second,
                source = RuleSource.AUTO,
                alternativeCategories = getAlternatives(normalizedMerchant, contextCategory.first)
            )
        }

        // Priority 4: Keyword-based default matching
        val keywordCategory = categorizeByKeywords(normalizedMerchant, amount)
        if (keywordCategory != null) {
            return CategorySuggestion(
                category = keywordCategory,
                confidence = AUTO_CONFIDENCE,
                source = RuleSource.DEFAULT,
                alternativeCategories = getAlternatives(normalizedMerchant, keywordCategory)
            )
        }

        // Priority 5: Fallback
        return CategorySuggestion(
            category = "Miscellaneous",
            confidence = 0.30f,
            source = RuleSource.AUTO,
            alternativeCategories = getMostLikelyCategories(amount, time)
        )
    }

    /**
     * Learns from a user's category correction. Stores the correction permanently
     * and updates the merchant→category knowledge base.
     *
     * @param correction The correction details.
     */
    fun learnFromCorrection(correction: CategoryCorrection) {
        // Store the correction record
        saveCorrection(correction)

        // Create or update merchant rule
        val normalizedMerchant = normalizeMerchant(correction.merchantName)
        val existingRule = findExactRule(normalizedMerchant)

        if (existingRule != null) {
            // Update existing rule
            val updatedRule = existingRule.copy(
                assignedCategory = correction.newCategory,
                confidence = USER_CORRECTED_CONFIDENCE,
                source = RuleSource.USER_CORRECTED,
                lastUsedAt = System.currentTimeMillis(),
                matchCount = existingRule.matchCount + 1
            )
            saveRule(updatedRule)
        } else {
            // Create new rule
            val newRule = MerchantRule(
                id = System.nanoTime(),
                merchantPattern = normalizedMerchant,
                assignedCategory = correction.newCategory,
                confidence = USER_CORRECTED_CONFIDENCE,
                source = RuleSource.USER_CORRECTED,
                createdAt = System.currentTimeMillis(),
                matchCount = 1,
                isPatternRule = false
            )
            saveRule(newRule)
        }

        // Check if we should suggest a pattern rule
        checkForPatternOpportunity(correction)
    }

    /**
     * Gets all transactions currently categorized as "Miscellaneous" or "Others"
     * that need manual categorization.
     *
     * @return List of uncategorized transactions, newest first.
     */
    fun getUncategorizedTransactions(): List<Transaction> {
        // TODO: Wire to Room DAO
        // return transactionDao.getByCategories(listOf("Miscellaneous", "Others", "Uncategorized"))
        //     .sortedByDescending { it.timestamp }
        return emptyList()
    }

    /**
     * Suggests bulk re-categorizations based on detected patterns from user corrections.
     *
     * For example, if the user has corrected 3 merchants containing "mart" to "Groceries",
     * this will suggest applying "Groceries" to ALL merchants containing "mart".
     *
     * @return List of bulk suggestions, sorted by impact (most affected transactions first).
     */
    fun suggestBulkRecategorization(): List<BulkSuggestion> {
        val corrections = getRecentCorrections(MAX_RECENT_CORRECTIONS)
        val suggestions = mutableListOf<BulkSuggestion>()

        // Group corrections by new category
        val byCategory = corrections.groupBy { it.newCategory }

        for ((category, categoryCorrectionsList) in byCategory) {
            // Find common substrings among merchant names in this category
            val merchantNames = categoryCorrectionsList.map { normalizeMerchant(it.merchantName) }
            val commonPatterns = findCommonPatterns(merchantNames)

            for (pattern in commonPatterns) {
                // Count how many corrections match this pattern
                val matchingCorrections = categoryCorrectionsList.filter {
                    normalizeMerchant(it.merchantName).contains(pattern, ignoreCase = true)
                }

                if (matchingCorrections.size >= PATTERN_DETECTION_THRESHOLD) {
                    // Find other transactions that match this pattern but aren't in this category
                    val affectedTransactions = findTransactionsMatchingPattern(pattern, category)

                    if (affectedTransactions.isNotEmpty()) {
                        suggestions.add(
                            BulkSuggestion(
                                pattern = pattern,
                                suggestedCategory = category,
                                affectedTransactionIds = affectedTransactions.map { it.id },
                                affectedMerchants = affectedTransactions.map { it.merchantName }.distinct(),
                                reason = "You moved ${matchingCorrections.size} merchants containing " +
                                        "'$pattern' to $category. Apply to ${affectedTransactions.size} similar?",
                                correctionCount = matchingCorrections.size
                            )
                        )
                    }
                }
            }
        }

        return suggestions.sortedByDescending { it.affectedTransactionIds.size }
    }

    /**
     * Applies a bulk re-categorization suggestion.
     *
     * @param suggestion The bulk suggestion to apply.
     * @return Number of transactions updated.
     */
    fun applyBulkSuggestion(suggestion: BulkSuggestion): Int {
        // Create a pattern rule
        val patternRule = MerchantRule(
            id = System.nanoTime(),
            merchantPattern = suggestion.pattern,
            assignedCategory = suggestion.suggestedCategory,
            confidence = 0.90f, // High confidence — user confirmed the pattern
            source = RuleSource.PATTERN_CONFIRMED,
            createdAt = System.currentTimeMillis(),
            matchCount = suggestion.affectedTransactionIds.size,
            isPatternRule = true
        )
        saveRule(patternRule)

        // Update affected transactions
        var count = 0
        for (txnId in suggestion.affectedTransactionIds) {
            if (updateTransactionCategory(txnId, suggestion.suggestedCategory)) {
                count++
            }
        }
        return count
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-Category Split
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Suggests splitting a transaction across multiple categories.
     * Typically useful for supermarket purchases (groceries + household items).
     *
     * @param transactionId Transaction to potentially split.
     * @param merchantName Merchant name.
     * @param amount Total amount.
     * @return [SplitSuggestion] if a split seems appropriate, null otherwise.
     */
    fun suggestSplit(transactionId: Long, merchantName: String, amount: Double): SplitSuggestion? {
        val normalizedMerchant = normalizeMerchant(merchantName)

        // Only suggest splits for certain merchant types and amounts above a threshold
        val splitMerchantPatterns = listOf(
            "supermarket", "hypermarket", "mart", "bazaar", "bazar",
            "store", "mall", "department", "general"
        )

        val isSplitCandidate = splitMerchantPatterns.any {
            normalizedMerchant.contains(it, ignoreCase = true)
        }

        if (!isSplitCandidate || amount < 500) return null

        // Suggest a common split based on historical data for this merchant
        val historicalSplits = getHistoricalSplitsForMerchant(normalizedMerchant)
        if (historicalSplits.isNotEmpty()) {
            return SplitSuggestion(
                transactionId = transactionId,
                totalAmount = amount,
                suggestedSplits = historicalSplits,
                reason = "Based on your previous splits at similar merchants."
            )
        }

        // Default suggestion: 75% groceries, 25% household for supermarkets
        return SplitSuggestion(
            transactionId = transactionId,
            totalAmount = amount,
            suggestedSplits = mapOf(
                "Groceries" to amount * 0.75,
                "Household" to amount * 0.25
            ),
            reason = "Supermarket purchases often include both grocery and household items."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Custom Categories
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new custom category.
     *
     * @param name Category name (e.g., "Parents", "Side Hustle", "Flatmate Shared").
     * @param emoji Optional emoji for display.
     * @param parentCategory Optional parent category for grouping.
     * @return The created custom category.
     */
    fun createCustomCategory(
        name: String,
        emoji: String? = null,
        parentCategory: String? = null
    ): CustomCategory {
        val category = CustomCategory(
            name = name,
            emoji = emoji,
            parentCategory = parentCategory,
            createdAt = System.currentTimeMillis()
        )
        saveCustomCategory(category)
        return category
    }

    /**
     * Gets all categories: default + custom, sorted alphabetically.
     */
    fun getAllCategories(): List<String> {
        val custom = getCustomCategories().map { it.name }
        return (DEFAULT_CATEGORIES + custom).sorted()
    }

    /**
     * Gets user-created custom categories.
     */
    fun getCustomCategories(): List<CustomCategory> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    /**
     * Deletes a custom category. Transactions in it are moved to "Miscellaneous".
     */
    fun deleteCustomCategory(name: String): Boolean {
        // TODO: Wire to Room DAO
        // Move transactions, then delete
        return false
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Merchant Aliases
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets a user-friendly alias for a merchant.
     * Example: "XXXXXXX1234" → "Restaurant near office"
     *
     * @param originalMerchant The merchant name as it appears in SMS.
     * @param alias User's preferred name.
     * @param category Category to assign (optional — keeps existing if null).
     */
    fun setMerchantAlias(originalMerchant: String, alias: String, category: String? = null) {
        val merchantAlias = MerchantAlias(
            originalMerchant = normalizeMerchant(originalMerchant),
            userAlias = alias,
            category = category ?: categorize(originalMerchant, 0.0, System.currentTimeMillis()).category
        )
        saveMerchantAlias(merchantAlias)
    }

    /**
     * Gets the user-defined alias for a merchant, or null if none set.
     */
    fun getMerchantAlias(merchantName: String): MerchantAlias? {
        // TODO: Wire to Room DAO
        return null
    }

    /**
     * Gets all merchant aliases defined by the user.
     */
    fun getAllAliases(): List<MerchantAlias> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rules Export (for Backup)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exports all learned rules as a serializable list (for inclusion in backups).
     */
    fun exportRules(): List<MerchantRule> {
        // TODO: Wire to Room DAO
        // return merchantRuleDao.getAll()
        return emptyList()
    }

    /**
     * Imports rules from a backup, merging with existing rules.
     * User-corrected rules from backup take priority over auto rules.
     *
     * @param rules Rules to import.
     * @return Number of rules imported/updated.
     */
    fun importRules(rules: List<MerchantRule>): Int {
        var imported = 0
        for (rule in rules) {
            val existing = findExactRule(rule.merchantPattern)
            if (existing == null) {
                saveRule(rule)
                imported++
            } else if (rule.source == RuleSource.USER_CORRECTED &&
                existing.source != RuleSource.USER_CORRECTED
            ) {
                // User correction overrides auto rules
                saveRule(rule.copy(id = existing.id))
                imported++
            }
        }
        return imported
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal Categorization Logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Normalizes a merchant name for consistent matching.
     * Removes extra whitespace, converts to lowercase, strips common prefixes/suffixes.
     */
    private fun normalizeMerchant(merchant: String): String {
        return merchant
            .trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(pos|upi|neft|imps|paid to|sent to)\\s*[-/:]?\\s*"), "")
            .replace(Regex("\\s*[-/]\\s*(upi|ref|txn).*$"), "")
            .trim()
    }

    /**
     * Categorizes based on keyword matching against a known dictionary.
     */
    private fun categorizeByKeywords(merchant: String, amount: Double): String? {
        val keywordMap = mapOf(
            "Food & Drinks" to listOf(
                "restaurant", "cafe", "coffee", "pizza", "burger", "biryani",
                "food", "kitchen", "dhaba", "bakery", "juice", "tea", "dine",
                "eat", "meal", "tiffin", "mess", "canteen", "hotel"
            ),
            "Groceries" to listOf(
                "grocery", "grocer", "supermarket", "mart", "bazaar", "bazar",
                "kirana", "provision", "vegetable", "fruit", "milk", "dairy",
                "fresh", "organic", "farm"
            ),
            "Transport" to listOf(
                "cab", "ride", "metro", "railway", "train booking", "fuel", "petrol",
                "diesel", "parking", "toll", "auto", "cab", "taxi", "ride",
                "bus", "transport", "travel"
            ),
            "Shopping" to listOf(
                "fashion", "clothing", "cloth", "apparel", "shoe", "electronics",
                "mobile", "phone", "gadget", "accessories", "jewel", "watch",
                "bag", "cosmetic", "beauty", "shop", "store", "retail"
            ),
            "Entertainment" to listOf(
                "movie", "cinema", "theatre", "theater", "streaming", "music service",
                "game", "gaming", "concert", "show", "ticket", "event", "play",
                "music", "stream", "video", "ott"
            ),
            "Health & Medical" to listOf(
                "hospital", "clinic", "doctor", "medical", "pharmacy", "medicine",
                "health", "lab", "diagnostic", "dental", "eye", "therapy",
                "wellness", "gym", "fitness", "yoga"
            ),
            "Education" to listOf(
                "school", "college", "university", "course", "training",
                "tutorial", "book", "library", "stationery", "tuition",
                "coaching", "academy", "institute", "learn"
            ),
            "Utilities" to listOf(
                "electricity", "electric", "power", "water", "gas", "broadband",
                "internet", "wifi", "dth", "recharge", "postpaid", "prepaid",
                "bill", "utility"
            ),
            "Rent" to listOf(
                "rent", "lease", "landlord", "property", "housing"
            ),
            "Household" to listOf(
                "home", "furniture", "appliance", "repair", "plumber",
                "electrician", "carpenter", "paint", "clean", "laundry",
                "iron", "wash", "maintenance"
            ),
            "Subscriptions" to listOf(
                "subscription", "membership", "annual", "monthly plan",
                "premium", "pro plan", "cloud storage"
            ),
            "Insurance" to listOf(
                "insurance", "lic", "policy", "premium", "cover", "term plan",
                "health plan"
            ),
            "Investments" to listOf(
                "mutual fund", "sip", "stock", "share", "trading",
                "demat", "brokerage", "invest", "fd", "fixed deposit",
                "ppf", "nps", "gold", "bond"
            ),
            "EMI & Loans" to listOf(
                "emi", "loan", "repayment", "installment", "credit card",
                "minimum due", "outstanding", "interest"
            ),
            "Gifts & Donations" to listOf(
                "gift", "donation", "charity", "ngo", "fund", "temple",
                "church", "mosque", "gurudwara", "religious"
            )
        )

        for ((category, keywords) in keywordMap) {
            if (keywords.any { merchant.contains(it, ignoreCase = true) }) {
                return category
            }
        }

        // Amount-based heuristics for uncategorized
        return when {
            amount in 10.0..50.0 -> null // Too generic to guess
            amount > 50000.0 -> null // Large amounts need user input
            else -> null
        }
    }

    /**
     * Uses contextual signals (time of day, day of week, amount range)
     * to improve categorization when merchant name alone is ambiguous.
     *
     * @return Pair of (category, confidence) or null if no contextual match.
     */
    private fun categorizeByContext(merchant: String, amount: Double, time: Long): Pair<String, Float>? {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = time
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        // Morning small amounts (7-10 AM, ₹10-100) are likely food/tea
        if (hour in 7..10 && amount in 10.0..100.0 && merchant.length < 15) {
            return Pair("Food & Drinks", 0.55f)
        }

        // Lunch time amounts (12-2 PM, ₹100-500)
        if (hour in 12..14 && amount in 100.0..500.0) {
            val foodIndicators = listOf("rest", "food", "cafe", "kitchen", "lunch", "meal")
            if (foodIndicators.any { merchant.contains(it, ignoreCase = true) }) {
                return Pair("Food & Drinks", 0.75f)
            }
        }

        // Weekend entertainment spending
        if ((dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) &&
            amount in 200.0..2000.0
        ) {
            val entertainmentIndicators = listOf("movie", "cinema", "mall", "game", "park", "play")
            if (entertainmentIndicators.any { merchant.contains(it, ignoreCase = true) }) {
                return Pair("Entertainment", 0.70f)
            }
        }

        // Month-start large debits (1st-5th) → likely rent or EMI
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        if (dayOfMonth in 1..5 && amount > 5000) {
            // Check if amount matches known rent/EMI patterns from history
            val historyMatch = checkHistoricalRecurrence(merchant, amount, dayOfMonth)
            if (historyMatch != null) {
                return historyMatch
            }
        }

        return null
    }

    /**
     * Checks historical data to see if a transaction matches a recurring pattern.
     */
    private fun checkHistoricalRecurrence(
        merchant: String,
        amount: Double,
        dayOfMonth: Int
    ): Pair<String, Float>? {
        // TODO: Query Room DB for similar transactions (same merchant, similar amount, same time of month)
        // If found 2+ times, it's likely recurring — return its established category
        return null
    }

    /**
     * Finds an exact rule for a normalized merchant name.
     */
    private fun findExactRule(normalizedMerchant: String): MerchantRule? {
        // TODO: Wire to Room DAO
        // return merchantRuleDao.findByExactPattern(normalizedMerchant)
        return null
    }

    /**
     * Finds a pattern rule that matches the merchant name.
     * Pattern rules use substring matching (e.g., pattern "mart" matches "dmart", "bigmart").
     */
    private fun findPatternRule(normalizedMerchant: String): MerchantRule? {
        // TODO: Wire to Room DAO
        // val patternRules = merchantRuleDao.getAllPatternRules()
        // return patternRules.firstOrNull { normalizedMerchant.contains(it.merchantPattern) }
        return null
    }

    /**
     * Gets alternative category suggestions for the alternatives list.
     */
    private fun getAlternatives(merchant: String, primaryCategory: String): List<Pair<String, Float>> {
        val alternatives = mutableListOf<Pair<String, Float>>()

        // Get top 3 most likely categories excluding primary
        val likelyCategories = categorizeByKeywords(merchant, 0.0)
        if (likelyCategories != null && likelyCategories != primaryCategory) {
            alternatives.add(Pair(likelyCategories, AUTO_CONFIDENCE * 0.5f))
        }

        // Add the most common categories the user uses
        val userTopCategories = getUserTopCategories(limit = 3)
        for (cat in userTopCategories) {
            if (cat != primaryCategory && alternatives.none { it.first == cat }) {
                alternatives.add(Pair(cat, 0.30f))
            }
        }

        return alternatives.take(3)
    }

    /**
     * Gets most likely categories based on amount and time (for fallback suggestions).
     */
    private fun getMostLikelyCategories(amount: Double, time: Long): List<Pair<String, Float>> {
        val suggestions = mutableListOf<Pair<String, Float>>()

        // Amount-based guesses
        when {
            amount < 100 -> suggestions.add(Pair("Food & Drinks", 0.40f))
            amount in 100.0..500.0 -> suggestions.add(Pair("Shopping", 0.35f))
            amount in 500.0..5000.0 -> suggestions.add(Pair("Shopping", 0.30f))
            amount > 5000 -> suggestions.add(Pair("EMI & Loans", 0.25f))
        }

        return suggestions.take(3)
    }

    /**
     * Gets the user's most frequently used categories.
     */
    private fun getUserTopCategories(limit: Int): List<String> {
        // TODO: Wire to Room DAO — query transaction table grouped by category, ordered by count
        return listOf("Food & Drinks", "Transport", "Shopping").take(limit)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pattern Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if recent corrections suggest a pattern worth proposing to the user.
     */
    private fun checkForPatternOpportunity(correction: CategoryCorrection) {
        val recentCorrections = getRecentCorrections(50)
        val sameCategory = recentCorrections.filter { it.newCategory == correction.newCategory }

        if (sameCategory.size < PATTERN_DETECTION_THRESHOLD) return

        // Look for common substrings
        val merchantNames = sameCategory.map { normalizeMerchant(it.merchantName) }
        val patterns = findCommonPatterns(merchantNames)

        // Store detected patterns for the next time suggestBulkRecategorization() is called
        for (pattern in patterns) {
            val matchCount = merchantNames.count { it.contains(pattern, ignoreCase = true) }
            if (matchCount >= PATTERN_DETECTION_THRESHOLD) {
                saveDetectedPattern(pattern, correction.newCategory, matchCount)
            }
        }
    }

    /**
     * Finds common substrings among a list of merchant names.
     * Uses a simplified approach: extract words that appear in 3+ names.
     */
    private fun findCommonPatterns(merchantNames: List<String>): List<String> {
        if (merchantNames.size < PATTERN_DETECTION_THRESHOLD) return emptyList()

        // Extract all words from all merchant names
        val wordCounts = mutableMapOf<String, Int>()
        for (name in merchantNames) {
            val words = name.split(Regex("[\\s_\\-./]+"))
            for (word in words) {
                if (word.length >= 3) { // Ignore very short words
                    wordCounts[word] = (wordCounts[word] ?: 0) + 1
                }
            }
        }

        // Return words that appear in at least THRESHOLD merchant names
        return wordCounts.filter { it.value >= PATTERN_DETECTION_THRESHOLD }
            .keys
            .filter { word ->
                // Exclude very generic words
                word !in listOf("the", "and", "pvt", "ltd", "india", "pay", "upi")
            }
            .toList()
    }

    /**
     * Finds transactions matching a pattern that are NOT in the specified category.
     */
    private fun findTransactionsMatchingPattern(pattern: String, excludeCategory: String): List<Transaction> {
        // TODO: Wire to Room DAO
        // SELECT * FROM transactions WHERE merchant LIKE '%pattern%' AND category != excludeCategory
        return emptyList()
    }

    /**
     * Gets historical split ratios for a merchant type.
     */
    private fun getHistoricalSplitsForMerchant(merchant: String): Map<String, Double> {
        // TODO: Query past split transactions for this merchant
        return emptyMap()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence Stubs
    // ─────────────────────────────────────────────────────────────────────────

    private fun saveRule(rule: MerchantRule) {
        // TODO: Wire to Room DAO
    }

    private fun updateRuleUsage(ruleId: Long) {
        // TODO: Wire to Room DAO — update lastUsedAt and increment matchCount
    }

    private fun saveCorrection(correction: CategoryCorrection) {
        // TODO: Wire to Room DAO
    }

    private fun getRecentCorrections(limit: Int): List<CategoryCorrection> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    private fun saveCustomCategory(category: CustomCategory) {
        // TODO: Wire to Room DAO
    }

    private fun saveMerchantAlias(alias: MerchantAlias) {
        // TODO: Wire to Room DAO
    }

    private fun saveDetectedPattern(pattern: String, category: String, matchCount: Int) {
        // TODO: Store for later surfacing in suggestBulkRecategorization()
    }

    private fun updateTransactionCategory(transactionId: Long, newCategory: String): Boolean {
        // TODO: Wire to Room DAO
        return false
    }
}
