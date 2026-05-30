package com.paisabrain.app.search

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Powerful transaction search engine with full-text search, combinable filters,
 * sorting, saved searches ("smart folders"), and export capabilities.
 *
 * Searches across: merchant names, categories, raw SMS text, user notes, and tags.
 * Provides aggregate statistics on results and supports CSV/text export.
 *
 * All searching happens locally on the Room database — no network required.
 */
class TransactionSearch(private val context: Context) {

    companion object {
        /** Maximum number of recent searches to retain. */
        private const val MAX_RECENT_SEARCHES = 20

        /** Maximum number of saved smart folders. */
        private const val MAX_SMART_FOLDERS = 50
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Represents a transaction for search purposes.
     */
    data class Transaction(
        val id: Long,
        val merchantName: String,
        val amount: Double,
        val type: TransactionType,
        val category: String,
        val date: Long,
        val rawSms: String? = null,
        val notes: String? = null,
        val tags: List<String> = emptyList(),
        val accountName: String? = null,
        val isRecurring: Boolean = false,
        val balance: Double? = null
    )

    /**
     * Transaction type: money in or money out.
     */
    enum class TransactionType {
        CREDIT,
        DEBIT,
        BOTH // For filtering: show both types
    }

    /**
     * Fields available for sorting search results.
     */
    enum class SortField {
        DATE,
        AMOUNT,
        CATEGORY,
        MERCHANT
    }

    /**
     * Sort direction.
     */
    enum class SortOrder {
        ASCENDING,
        DESCENDING
    }

    /**
     * Export format options.
     */
    enum class ExportFormat {
        CSV,
        FORMATTED_TEXT,
        JSON
    }

    /**
     * A complete search query with all possible filter combinations.
     *
     * @param text Free-text search string (searches merchant, category, SMS, notes, tags).
     * @param dateRange Optional date range filter (start timestamp, end timestamp).
     * @param categories Optional category filter (multi-select: match ANY of these).
     * @param amountRange Optional amount filter (min, max).
     * @param type Filter by credit/debit/both.
     * @param merchantPattern Filter by specific merchant name or pattern.
     * @param isRecurring If true, only show recurring transactions.
     * @param tags Filter by tags (match ANY).
     * @param accountName Filter by specific account.
     * @param sortBy Field to sort results by.
     * @param sortOrder Ascending or descending.
     * @param limit Maximum results to return (0 = unlimited).
     * @param offset Pagination offset.
     */
    data class SearchQuery(
        val text: String? = null,
        val dateRange: Pair<Long, Long>? = null,
        val categories: List<String>? = null,
        val amountRange: Pair<Double, Double>? = null,
        val type: TransactionType? = null,
        val merchantPattern: String? = null,
        val isRecurring: Boolean? = null,
        val tags: List<String>? = null,
        val accountName: String? = null,
        val sortBy: SortField = SortField.DATE,
        val sortOrder: SortOrder = SortOrder.DESCENDING,
        val limit: Int = 0,
        val offset: Int = 0
    )

    /**
     * Results of a search query, including aggregate statistics.
     *
     * @param transactions List of matching transactions.
     * @param totalAmount Sum of all matching transaction amounts (debits as positive).
     * @param count Total number of results.
     * @param avgAmount Average transaction amount.
     * @param dateRange Human-readable date range of results.
     * @param creditTotal Total of credit transactions in results.
     * @param debitTotal Total of debit transactions in results.
     * @param categoryBreakdown Count of results per category.
     * @param hasMore Whether there are more results beyond the returned page.
     */
    data class SearchResult(
        val transactions: List<Transaction>,
        val totalAmount: Double,
        val count: Int,
        val avgAmount: Double,
        val dateRange: String,
        val creditTotal: Double = 0.0,
        val debitTotal: Double = 0.0,
        val categoryBreakdown: Map<String, Int> = emptyMap(),
        val hasMore: Boolean = false
    )

    /**
     * A saved search query ("smart folder") that auto-updates.
     *
     * @param id Unique identifier.
     * @param name User-given name (e.g., "Food over ₹500").
     * @param query The saved search parameters.
     * @param createdAt When this smart folder was created.
     * @param lastUsedAt Last time the user opened this smart folder.
     * @param resultCount Cached count of matching transactions (updated periodically).
     */
    data class SavedSearch(
        val id: Long,
        val name: String,
        val query: SearchQuery,
        val createdAt: Long,
        val lastUsedAt: Long = createdAt,
        val resultCount: Int = 0
    )

    /**
     * A search suggestion for autocomplete.
     *
     * @param text The suggestion text.
     * @param type What kind of suggestion it is.
     * @param resultCount Estimated number of results for this suggestion.
     */
    data class SearchSuggestion(
        val text: String,
        val type: SuggestionType,
        val resultCount: Int = 0
    )

    /**
     * Types of search suggestions.
     */
    enum class SuggestionType {
        RECENT_SEARCH,
        MERCHANT,
        CATEGORY,
        TAG,
        SMART_FOLDER
    }

    /**
     * Highlighted match within a transaction (for UI rendering).
     *
     * @param field Which field the match was found in.
     * @param text The full field text.
     * @param matchStart Start index of the match within the text.
     * @param matchEnd End index of the match.
     */
    data class SearchHighlight(
        val field: String,
        val text: String,
        val matchStart: Int,
        val matchEnd: Int
    )

    /**
     * A search result with highlight information for UI display.
     */
    data class HighlightedTransaction(
        val transaction: Transaction,
        val highlights: List<SearchHighlight>
    )

    /**
     * Predefined date range options for quick filtering.
     */
    enum class DateRangePreset {
        LAST_7_DAYS,
        THIS_MONTH,
        LAST_MONTH,
        LAST_3_MONTHS,
        LAST_6_MONTHS,
        THIS_YEAR,
        LAST_YEAR,
        ALL_TIME,
        CUSTOM
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Search
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes a search query and returns results with aggregate statistics.
     *
     * @param query The search parameters (all filters are optional and combinable).
     * @return [SearchResult] with matching transactions and statistics.
     */
    fun search(query: SearchQuery): SearchResult {
        // Build the database query from the SearchQuery parameters
        var results = getAllTransactions()

        // Apply text search (across multiple fields)
        if (!query.text.isNullOrBlank()) {
            val searchTerms = query.text.lowercase(Locale.getDefault()).split(Regex("\\s+"))
            results = results.filter { txn ->
                searchTerms.all { term ->
                    txn.merchantName.lowercase().contains(term) ||
                            txn.category.lowercase().contains(term) ||
                            (txn.rawSms?.lowercase()?.contains(term) == true) ||
                            (txn.notes?.lowercase()?.contains(term) == true) ||
                            txn.tags.any { it.lowercase().contains(term) }
                }
            }
        }

        // Apply date range filter
        if (query.dateRange != null) {
            results = results.filter { it.date in query.dateRange.first..query.dateRange.second }
        }

        // Apply category filter
        if (!query.categories.isNullOrEmpty()) {
            results = results.filter { it.category in query.categories }
        }

        // Apply amount range filter
        if (query.amountRange != null) {
            results = results.filter {
                it.amount in query.amountRange.first..query.amountRange.second
            }
        }

        // Apply transaction type filter
        if (query.type != null && query.type != TransactionType.BOTH) {
            results = results.filter { it.type == query.type }
        }

        // Apply merchant pattern filter
        if (!query.merchantPattern.isNullOrBlank()) {
            val pattern = query.merchantPattern.lowercase()
            results = results.filter {
                it.merchantName.lowercase().contains(pattern)
            }
        }

        // Apply recurring filter
        if (query.isRecurring == true) {
            results = results.filter { it.isRecurring }
        }

        // Apply tag filter
        if (!query.tags.isNullOrEmpty()) {
            results = results.filter { txn ->
                txn.tags.any { it in query.tags!! }
            }
        }

        // Apply account filter
        if (!query.accountName.isNullOrBlank()) {
            results = results.filter { it.accountName == query.accountName }
        }

        // Sort results
        results = sortResults(results, query.sortBy, query.sortOrder)

        // Calculate aggregates BEFORE pagination
        val totalCount = results.size
        val totalAmount = results.sumOf { it.amount }
        val avgAmount = if (totalCount > 0) totalAmount / totalCount else 0.0
        val creditTotal = results.filter { it.type == TransactionType.CREDIT }.sumOf { it.amount }
        val debitTotal = results.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount }
        val categoryBreakdown = results.groupBy { it.category }.mapValues { it.value.size }

        // Date range of results
        val dateRange = if (results.isNotEmpty()) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val earliest = results.minOf { it.date }
            val latest = results.maxOf { it.date }
            "${dateFormat.format(Date(earliest))} — ${dateFormat.format(Date(latest))}"
        } else {
            "No results"
        }

        // Apply pagination
        val hasMore: Boolean
        if (query.limit > 0) {
            val endIndex = minOf(query.offset + query.limit, results.size)
            hasMore = endIndex < results.size
            results = results.subList(query.offset, endIndex)
        } else {
            hasMore = false
        }

        // Save to recent searches
        if (!query.text.isNullOrBlank()) {
            saveRecentSearch(query.text)
        }

        return SearchResult(
            transactions = results,
            totalAmount = totalAmount,
            count = totalCount,
            avgAmount = avgAmount,
            dateRange = dateRange,
            creditTotal = creditTotal,
            debitTotal = debitTotal,
            categoryBreakdown = categoryBreakdown,
            hasMore = hasMore
        )
    }

    /**
     * Performs search with highlight information for UI display.
     * Returns transactions with exact positions of text matches highlighted.
     *
     * @param query Search query.
     * @return List of transactions with highlight positions.
     */
    fun searchWithHighlights(query: SearchQuery): List<HighlightedTransaction> {
        val result = search(query)
        if (query.text.isNullOrBlank()) {
            return result.transactions.map { HighlightedTransaction(it, emptyList()) }
        }

        val searchTerms = query.text.lowercase(Locale.getDefault()).split(Regex("\\s+"))

        return result.transactions.map { txn ->
            val highlights = mutableListOf<SearchHighlight>()

            for (term in searchTerms) {
                // Check merchant name
                findHighlights(txn.merchantName, term, "merchant")?.let { highlights.addAll(it) }

                // Check category
                findHighlights(txn.category, term, "category")?.let { highlights.addAll(it) }

                // Check notes
                txn.notes?.let { notes ->
                    findHighlights(notes, term, "notes")?.let { highlights.addAll(it) }
                }

                // Check raw SMS
                txn.rawSms?.let { sms ->
                    findHighlights(sms, term, "sms")?.let { highlights.addAll(it) }
                }

                // Check tags
                for (tag in txn.tags) {
                    findHighlights(tag, term, "tag")?.let { highlights.addAll(it) }
                }
            }

            HighlightedTransaction(txn, highlights)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Suggestions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets search suggestions as the user types.
     * Combines recent searches, known merchants, categories, and smart folders.
     *
     * @param partialText What the user has typed so far.
     * @return List of suggestions, ranked by relevance.
     */
    fun getSuggestions(partialText: String): List<SearchSuggestion> {
        if (partialText.isBlank()) {
            // Show recent searches when search box is empty
            return getRecentSearches().map {
                SearchSuggestion(it, SuggestionType.RECENT_SEARCH)
            }
        }

        val suggestions = mutableListOf<SearchSuggestion>()
        val lowerText = partialText.lowercase(Locale.getDefault())

        // Recent searches matching the text
        getRecentSearches()
            .filter { it.lowercase().contains(lowerText) }
            .take(3)
            .forEach { suggestions.add(SearchSuggestion(it, SuggestionType.RECENT_SEARCH)) }

        // Matching merchants
        getKnownMerchants()
            .filter { it.lowercase().contains(lowerText) }
            .take(5)
            .forEach { suggestions.add(SearchSuggestion(it, SuggestionType.MERCHANT)) }

        // Matching categories
        getCategories()
            .filter { it.lowercase().contains(lowerText) }
            .take(3)
            .forEach { suggestions.add(SearchSuggestion(it, SuggestionType.CATEGORY)) }

        // Matching tags
        getKnownTags()
            .filter { it.lowercase().contains(lowerText) }
            .take(3)
            .forEach { suggestions.add(SearchSuggestion(it, SuggestionType.TAG)) }

        // Matching saved searches
        getSavedSearches()
            .filter { it.name.lowercase().contains(lowerText) }
            .take(2)
            .forEach {
                suggestions.add(
                    SearchSuggestion(it.name, SuggestionType.SMART_FOLDER, it.resultCount)
                )
            }

        return suggestions.distinctBy { it.text }.take(10)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Saved Searches (Smart Folders)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves a search query as a "smart folder" that auto-updates.
     *
     * @param name Display name for the saved search.
     * @param query The search parameters to save.
     * @return The created [SavedSearch].
     */
    fun saveSearchAsSmartFolder(name: String, query: SearchQuery): SavedSearch {
        val currentCount = search(query.copy(limit = 0)).count

        val saved = SavedSearch(
            id = System.nanoTime(),
            name = name,
            query = query,
            createdAt = System.currentTimeMillis(),
            resultCount = currentCount
        )
        persistSavedSearch(saved)
        return saved
    }

    /**
     * Gets all saved smart folders.
     */
    fun getSavedSearches(): List<SavedSearch> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    /**
     * Deletes a saved search / smart folder.
     */
    fun deleteSavedSearch(id: Long): Boolean {
        // TODO: Wire to Room DAO
        return false
    }

    /**
     * Refreshes the result count for all saved searches.
     * Call this periodically or after new transactions are added.
     */
    fun refreshSavedSearchCounts() {
        val saved = getSavedSearches()
        for (search in saved) {
            val count = search(search.query.copy(limit = 0)).count
            if (count != search.resultCount) {
                persistSavedSearch(search.copy(resultCount = count))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Export
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Exports search results in the specified format.
     *
     * @param results The search results to export.
     * @param format Output format (CSV, formatted text, or JSON).
     * @return Formatted string of the export data.
     */
    fun exportResults(results: SearchResult, format: ExportFormat): String {
        return when (format) {
            ExportFormat.CSV -> exportAsCsv(results)
            ExportFormat.FORMATTED_TEXT -> exportAsFormattedText(results)
            ExportFormat.JSON -> exportAsJson(results)
        }
    }

    /**
     * Exports results as CSV with headers.
     */
    private fun exportAsCsv(results: SearchResult): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // CSV header
        sb.appendLine("Date,Merchant,Amount,Type,Category,Notes,Tags")

        // Data rows
        for (txn in results.transactions) {
            val date = dateFormat.format(Date(txn.date))
            val merchant = escapeCsv(txn.merchantName)
            val amount = String.format(Locale.getDefault(), "%.2f", txn.amount)
            val type = txn.type.name
            val category = escapeCsv(txn.category)
            val notes = escapeCsv(txn.notes ?: "")
            val tags = escapeCsv(txn.tags.joinToString(";"))

            sb.appendLine("$date,$merchant,$amount,$type,$category,$notes,$tags")
        }

        // Summary footer
        sb.appendLine()
        sb.appendLine("# Summary")
        sb.appendLine("# Total Transactions: ${results.count}")
        sb.appendLine("# Total Amount: ${String.format(Locale.getDefault(), "%.2f", results.totalAmount)}")
        sb.appendLine("# Average Amount: ${String.format(Locale.getDefault(), "%.2f", results.avgAmount)}")
        sb.appendLine("# Date Range: ${results.dateRange}")

        return sb.toString()
    }

    /**
     * Exports results as human-readable formatted text.
     */
    private fun exportAsFormattedText(results: SearchResult): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("  Transaction Report")
        sb.appendLine("  ${results.dateRange}")
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine()
        sb.appendLine("  Total: ₹${formatAmount(results.totalAmount)} (${results.count} transactions)")
        sb.appendLine("  Credits: ₹${formatAmount(results.creditTotal)}")
        sb.appendLine("  Debits: ₹${formatAmount(results.debitTotal)}")
        sb.appendLine("  Average: ₹${formatAmount(results.avgAmount)}")
        sb.appendLine()
        sb.appendLine("───────────────────────────────────────")

        // Group by date
        val byDate = results.transactions.groupBy { dateFormat.format(Date(it.date)) }
        for ((date, transactions) in byDate) {
            sb.appendLine()
            sb.appendLine("  📅 $date")
            sb.appendLine("  ─────────────────")
            for (txn in transactions) {
                val time = timeFormat.format(Date(txn.date))
                val typeSymbol = if (txn.type == TransactionType.CREDIT) "+" else "-"
                val amountStr = "$typeSymbol₹${formatAmount(txn.amount)}"
                sb.appendLine("    $time  $amountStr  ${txn.merchantName}")
                sb.appendLine("           [${txn.category}]${txn.notes?.let { " — $it" } ?: ""}")
            }
            val dayTotal = transactions.sumOf { it.amount }
            sb.appendLine("    Day total: ₹${formatAmount(dayTotal)}")
        }

        sb.appendLine()
        sb.appendLine("───────────────────────────────────────")

        // Category breakdown
        if (results.categoryBreakdown.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("  📊 Category Breakdown")
            sb.appendLine("  ─────────────────")
            val sorted = results.categoryBreakdown.entries.sortedByDescending { it.value }
            for ((category, count) in sorted) {
                sb.appendLine("    $category: $count transactions")
            }
        }

        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════")
        sb.appendLine("  Generated: ${dateFormat.format(Date())}")

        return sb.toString()
    }

    /**
     * Exports results as JSON array.
     */
    private fun exportAsJson(results: SearchResult): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"summary\": {")
        sb.appendLine("    \"count\": ${results.count},")
        sb.appendLine("    \"totalAmount\": ${results.totalAmount},")
        sb.appendLine("    \"avgAmount\": ${results.avgAmount},")
        sb.appendLine("    \"creditTotal\": ${results.creditTotal},")
        sb.appendLine("    \"debitTotal\": ${results.debitTotal},")
        sb.appendLine("    \"dateRange\": \"${results.dateRange}\"")
        sb.appendLine("  },")
        sb.appendLine("  \"transactions\": [")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        results.transactions.forEachIndexed { index, txn ->
            val comma = if (index < results.transactions.size - 1) "," else ""
            sb.appendLine("    {")
            sb.appendLine("      \"id\": ${txn.id},")
            sb.appendLine("      \"date\": \"${dateFormat.format(Date(txn.date))}\",")
            sb.appendLine("      \"merchant\": \"${escapeJson(txn.merchantName)}\",")
            sb.appendLine("      \"amount\": ${txn.amount},")
            sb.appendLine("      \"type\": \"${txn.type.name}\",")
            sb.appendLine("      \"category\": \"${escapeJson(txn.category)}\",")
            sb.appendLine("      \"notes\": ${if (txn.notes != null) "\"${escapeJson(txn.notes)}\"" else "null"},")
            sb.appendLine("      \"tags\": [${txn.tags.joinToString(",") { "\"$it\"" }}],")
            sb.appendLine("      \"isRecurring\": ${txn.isRecurring}")
            sb.appendLine("    }$comma")
        }

        sb.appendLine("  ]")
        sb.appendLine("}")

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Date Range Presets
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a date range preset to actual timestamps.
     *
     * @param preset The preset to convert.
     * @return Pair of (startTimestamp, endTimestamp).
     */
    fun getDateRangeForPreset(preset: DateRangePreset): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        return when (preset) {
            DateRangePreset.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                Pair(calendar.timeInMillis, now)
            }
            DateRangePreset.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            DateRangePreset.LAST_MONTH -> {
                calendar.add(Calendar.MONTH, -1)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                val start = calendar.timeInMillis
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                Pair(start, calendar.timeInMillis)
            }
            DateRangePreset.LAST_3_MONTHS -> {
                calendar.add(Calendar.MONTH, -3)
                Pair(calendar.timeInMillis, now)
            }
            DateRangePreset.LAST_6_MONTHS -> {
                calendar.add(Calendar.MONTH, -6)
                Pair(calendar.timeInMillis, now)
            }
            DateRangePreset.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                Pair(calendar.timeInMillis, now)
            }
            DateRangePreset.LAST_YEAR -> {
                calendar.add(Calendar.YEAR, -1)
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                val start = calendar.timeInMillis
                calendar.set(Calendar.MONTH, Calendar.DECEMBER)
                calendar.set(Calendar.DAY_OF_MONTH, 31)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                Pair(start, calendar.timeInMillis)
            }
            DateRangePreset.ALL_TIME -> Pair(0L, now)
            DateRangePreset.CUSTOM -> Pair(0L, now) // Caller should provide custom range
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quick Filters (Convenience Methods)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Quick search by merchant name.
     */
    fun searchByMerchant(merchantName: String): SearchResult {
        return search(SearchQuery(merchantPattern = merchantName))
    }

    /**
     * Quick search by category within a date range.
     */
    fun searchByCategory(category: String, dateRange: Pair<Long, Long>? = null): SearchResult {
        return search(SearchQuery(categories = listOf(category), dateRange = dateRange))
    }

    /**
     * Gets all transactions above a certain amount.
     */
    fun searchHighValue(minAmount: Double): SearchResult {
        return search(SearchQuery(amountRange = Pair(minAmount, Double.MAX_VALUE)))
    }

    /**
     * Gets all recurring transactions.
     */
    fun getRecurringTransactions(): SearchResult {
        return search(SearchQuery(isRecurring = true))
    }

    /**
     * Gets today's transactions.
     */
    fun getTodayTransactions(): SearchResult {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()
        return search(SearchQuery(dateRange = Pair(startOfDay, now)))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sorts a list of transactions by the specified field and order.
     */
    private fun sortResults(
        transactions: List<Transaction>,
        sortBy: SortField,
        sortOrder: SortOrder
    ): List<Transaction> {
        val sorted = when (sortBy) {
            SortField.DATE -> transactions.sortedBy { it.date }
            SortField.AMOUNT -> transactions.sortedBy { it.amount }
            SortField.CATEGORY -> transactions.sortedBy { it.category }
            SortField.MERCHANT -> transactions.sortedBy { it.merchantName.lowercase() }
        }

        return if (sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    /**
     * Finds highlight positions for a search term within a text field.
     */
    private fun findHighlights(text: String, term: String, fieldName: String): List<SearchHighlight>? {
        val highlights = mutableListOf<SearchHighlight>()
        var startIndex = 0
        val lowerText = text.lowercase(Locale.getDefault())

        while (true) {
            val index = lowerText.indexOf(term, startIndex)
            if (index < 0) break
            highlights.add(
                SearchHighlight(
                    field = fieldName,
                    text = text,
                    matchStart = index,
                    matchEnd = index + term.length
                )
            )
            startIndex = index + 1
        }

        return highlights.ifEmpty { null }
    }

    /**
     * Escapes a string for CSV (wraps in quotes if contains comma/quote/newline).
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Escapes a string for JSON output.
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    /**
     * Formats amount for display.
     */
    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format(Locale.getDefault(), "%,.0f", amount)
        } else {
            String.format(Locale.getDefault(), "%,.2f", amount)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence / Data Access Stubs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets all transactions from the database (for in-memory filtering).
     * In production, push filters to SQL for performance.
     */
    private fun getAllTransactions(): List<Transaction> {
        // TODO: Wire to Room DAO
        // In production, build dynamic SQL query from SearchQuery parameters
        // instead of loading all into memory.
        return emptyList()
    }

    private fun getKnownMerchants(): List<String> {
        // TODO: Wire to Room DAO — SELECT DISTINCT merchantName FROM transactions
        return emptyList()
    }

    private fun getCategories(): List<String> {
        // TODO: Wire to Room DAO — SELECT DISTINCT category FROM transactions
        return emptyList()
    }

    private fun getKnownTags(): List<String> {
        // TODO: Wire to Room DAO
        return emptyList()
    }

    private fun getRecentSearches(): List<String> {
        // TODO: Load from SharedPreferences or Room
        return emptyList()
    }

    private fun saveRecentSearch(text: String) {
        // TODO: Save to SharedPreferences or Room (keep last MAX_RECENT_SEARCHES)
    }

    private fun persistSavedSearch(savedSearch: SavedSearch) {
        // TODO: Wire to Room DAO
    }
}
