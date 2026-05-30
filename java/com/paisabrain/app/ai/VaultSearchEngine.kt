package com.paisabrain.app.ai

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * VaultSearchEngine - Semantic-like search for vault entries.
 *
 * Implements intelligent search for the Paisa Brain vault using:
 * - TF-IDF (Term Frequency-Inverse Document Frequency) scoring
 * - Synonym expansion for natural language understanding
 * - Fuzzy matching with Levenshtein distance
 * - Field-weighted scoring (title > tags > content)
 * - Query intent classification
 *
 * The engine maps common Indian English synonyms and colloquialisms
 * to enable natural queries like "wifi password" matching entries
 * tagged with "network", "router", "broadband", etc.
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
class VaultSearchEngine {

    // ============================================================
    // Data Models
    // ============================================================

    /**
     * A single entry stored in the vault.
     */
    data class VaultEntry(
        val id: String,
        val title: String,
        val content: String,
        val tags: List<String> = emptyList(),
        val category: VaultCategory = VaultCategory.GENERAL,
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
        val isFavorite: Boolean = false,
        val metadata: Map<String, String> = emptyMap()
    )

    enum class VaultCategory {
        PASSWORD, DOCUMENT, FINANCE, MEDICAL, TRAVEL,
        INSURANCE, EDUCATION, WORK, PERSONAL, GENERAL
    }

    /**
     * A search result with relevance scoring and match highlights.
     */
    data class SearchResult(
        val entry: VaultEntry,
        val relevanceScore: Double, // 0.0 - 1.0
        val matchedFields: List<MatchedField>,
        val matchReason: String, // Human-readable explanation of why this matched
        val highlightedTitle: String, // Title with matched terms wrapped in **
        val highlightedSnippet: String // Content snippet with highlights
    )

    data class MatchedField(
        val fieldName: String, // "title", "content", "tags", "category"
        val matchType: MatchType,
        val matchedTerms: List<String>
    )

    enum class MatchType {
        EXACT,           // Direct keyword match
        SYNONYM,         // Matched via synonym expansion
        FUZZY,           // Close enough (Levenshtein distance ≤ 2)
        CATEGORY_MATCH,  // Matched entry category
        SEMANTIC         // TF-IDF based relevance
    }

    // ============================================================
    // Synonym Dictionary (Indian English context)
    // ============================================================

    /**
     * Comprehensive synonym mappings for common vault searches.
     * Maps query terms to related terms that should match vault entries.
     * Includes Indian English colloquialisms and common misspellings.
     */
    private val synonymDictionary: Map<String, Set<String>> = mapOf(
        // Technology & Internet
        "wifi" to setOf("network", "password", "router", "broadband", "internet", "ssid", "wpa", "hotspot", "fiber_provider", "telecom_provider", "broadband_provider", "lan"),
        "password" to setOf("pwd", "pass", "passcode", "pin", "credentials", "login", "secret", "key", "otp"),
        "internet" to setOf("wifi", "broadband", "telecom", "mobile_provider", "landline_provider", "fiber", "network", "data", "hotspot"),
        "email" to setOf("webmail", "mail_provider", "mail", "email_service", "inbox", "smtp", "imap"),
        "phone" to setOf("mobile", "cell", "number", "sim", "imei", "telecom", "mobile_provider", "carrier", "phone_provider"),
        "computer" to setOf("laptop", "pc", "desktop", "mac", "windows", "system"),
        "app" to setOf("application", "software", "download", "install", "playstore", "appstore"),

        // Medical & Health
        "doctor" to setOf("clinic", "hospital", "medical", "prescription", "dr", "physician", "specialist", "consultation", "appointment", "opd"),
        "medicine" to setOf("prescription", "tablet", "drug", "dosage", "pharmacy", "medical", "chemist", "dawai"),
        "hospital" to setOf("clinic", "medical", "doctor", "nursing home", "health", "emergency", "opd", "ipd", "ward"),
        "health" to setOf("medical", "doctor", "hospital", "insurance", "fitness", "checkup", "test", "report", "lab"),
        "blood" to setOf("group", "type", "test", "report", "donor", "pressure", "sugar", "hemoglobin"),
        "insurance" to setOf("policy", "premium", "cover", "health", "life", "term", "claim", "nominee", "lic", "mediclaim"),
        "vaccination" to setOf("vaccine", "dose", "cowin", "covid", "immunization", "certificate"),

        // Finance & Banking
        "bank" to setOf("account", "ifsc", "branch", "savings", "current", "major_bank", "private_bank", "national_bank", "banking_institution", "bank_provider", "passbook", "balance"),
        "account" to setOf("bank", "number", "savings", "current", "demat", "trading", "balance", "statement"),
        "card" to setOf("credit", "debit", "atm", "cvv", "expiry", "pin", "visa", "mastercard", "rupay"),
        "upi" to setOf("upi_app", "payment_app", "digital_wallet", "upi_platform", "payment", "vpa", "id"),
        "tax" to setOf("pan", "income", "gst", "itr", "tds", "filing", "return", "assessment", "deduction", "80c"),
        "pan" to setOf("card", "number", "tax", "identity", "permanent", "income"),
        "aadhaar" to setOf("aadhar", "uid", "identity", "biometric", "enrollment", "card", "number", "otp"),
        "investment" to setOf("mutual fund", "sip", "stock", "share", "fd", "rd", "ppf", "nps", "demat", "portfolio"),
        "loan" to setOf("emi", "interest", "principal", "tenure", "home", "car", "personal", "education", "repayment"),
        "emi" to setOf("loan", "installment", "monthly", "payment", "due", "principal", "interest"),

        // Documents & ID
        "passport" to setOf("travel", "visa", "document", "identity", "number", "expiry", "renewal"),
        "license" to setOf("driving", "dl", "rto", "vehicle", "learner", "permanent", "renewal"),
        "voter" to setOf("id", "card", "election", "epic", "identity"),
        "ration" to setOf("card", "apl", "bpl", "family", "members"),

        // Travel
        "flight" to setOf("airline", "booking", "pnr", "ticket", "airport", "boarding", "domestic_airline", "carrier", "flight_provider", "airline_service"),
        "train" to setOf("railway", "irctc", "pnr", "ticket", "booking", "seat", "coach", "tatkal", "waiting"),
        "hotel" to setOf("booking", "stay", "resort", "room", "checkin", "checkout", "hotel_app", "travel_booking", "stay_platform"),
        "cab" to setOf("ride_app", "cab_service", "taxi", "ride", "booking", "fare"),
        "visa" to setOf("passport", "travel", "embassy", "consulate", "application", "stamp"),

        // Education
        "school" to setOf("student", "admission", "fee", "class", "roll", "certificate", "marksheet"),
        "college" to setOf("university", "degree", "admission", "fee", "enrollment", "certificate", "marksheet"),
        "exam" to setOf("test", "result", "marksheet", "admit", "card", "hall", "ticket", "score"),
        "certificate" to setOf("degree", "diploma", "marksheet", "document", "proof", "attestation"),

        // Home & Utilities
        "electricity" to setOf("bill", "meter", "unit", "connection", "discom", "power", "light"),
        "water" to setOf("bill", "connection", "meter", "supply", "tanker"),
        "gas" to setOf("cylinder", "lpg", "booking", "connection", "indane", "bharat", "hp"),
        "rent" to setOf("house", "flat", "apartment", "landlord", "agreement", "lease", "deposit", "pg"),
        "society" to setOf("maintenance", "flat", "apartment", "building", "resident", "association"),

        // Work
        "office" to setOf("work", "company", "employee", "id", "badge", "vpn", "login"),
        "salary" to setOf("pay", "slip", "ctc", "income", "compensation", "appraisal"),
        "vpn" to setOf("office", "remote", "work", "login", "connection", "network"),

        // Shopping & Subscriptions
        "subscription" to setOf("video_streaming", "music_streaming", "membership", "ott_platform", "premium_content", "premium", "membership", "plan"),
        "online_shopping" to setOf("ecommerce", "order", "delivery", "shopping", "smart_speaker", "ereader"),
        "streaming_service" to setOf("streaming", "subscription", "password", "profile", "plan"),

        // Common Indian terms
        "aadhar" to setOf("aadhaar", "uid", "identity", "biometric"),
        "pancard" to setOf("pan", "card", "tax", "income"),
        "challan" to setOf("fine", "traffic", "penalty", "payment", "echallan"),
        "fastag" to setOf("toll", "highway", "recharge", "vehicle", "nhai")
    )

    /**
     * Category keywords for query intent classification.
     */
    private val categoryKeywords = mapOf(
        VaultCategory.PASSWORD to setOf("password", "login", "credentials", "pin", "passcode", "secret", "key"),
        VaultCategory.DOCUMENT to setOf("document", "id", "proof", "certificate", "card", "number", "copy"),
        VaultCategory.FINANCE to setOf("bank", "account", "card", "upi", "tax", "loan", "investment", "emi", "pan"),
        VaultCategory.MEDICAL to setOf("doctor", "medical", "health", "hospital", "prescription", "medicine", "blood", "report"),
        VaultCategory.TRAVEL to setOf("flight", "train", "hotel", "passport", "visa", "booking", "ticket", "pnr"),
        VaultCategory.INSURANCE to setOf("insurance", "policy", "premium", "claim", "nominee", "cover"),
        VaultCategory.EDUCATION to setOf("school", "college", "exam", "certificate", "degree", "marksheet", "student"),
        VaultCategory.WORK to setOf("office", "work", "company", "employee", "salary", "vpn"),
        VaultCategory.PERSONAL to setOf("personal", "family", "home", "address", "birthday", "anniversary")
    )

    // ============================================================
    // TF-IDF Index
    // ============================================================

    /**
     * In-memory TF-IDF index for the vault.
     * Rebuilt whenever the vault changes (entries added/modified/deleted).
     */
    private data class TfIdfIndex(
        val documentFrequency: Map<String, Int>,   // term -> number of documents containing it
        val termFrequencies: Map<String, Map<String, Double>>, // docId -> (term -> tf)
        val totalDocuments: Int,
        val documentLengths: Map<String, Int>      // docId -> total terms
    )

    private var index: TfIdfIndex? = null
    private var indexedEntries: List<VaultEntry> = emptyList()

    // Field weights for scoring
    private object FieldWeights {
        const val TITLE = 3.0
        const val TAGS = 2.5
        const val CATEGORY = 1.5
        const val CONTENT = 1.0
        const val METADATA = 0.8
    }

    // ============================================================
    // Index Building
    // ============================================================

    /**
     * Builds or rebuilds the TF-IDF index from vault entries.
     * Call this when entries change (add/update/delete).
     *
     * @param entries All vault entries to index
     */
    fun buildIndex(entries: List<VaultEntry>) {
        indexedEntries = entries

        val documentFrequency = mutableMapOf<String, Int>()
        val termFrequencies = mutableMapOf<String, Map<String, Double>>()
        val documentLengths = mutableMapOf<String, Int>()

        for (entry in entries) {
            val terms = extractTerms(entry)
            val termCounts = terms.groupBy { it }.mapValues { it.value.size }
            val totalTerms = terms.size

            documentLengths[entry.id] = totalTerms

            // TF: term frequency (normalized by document length)
            val tf = termCounts.mapValues { (_, count) ->
                count.toDouble() / totalTerms.coerceAtLeast(1)
            }
            termFrequencies[entry.id] = tf

            // DF: document frequency
            termCounts.keys.forEach { term ->
                documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
            }
        }

        index = TfIdfIndex(
            documentFrequency = documentFrequency,
            termFrequencies = termFrequencies,
            totalDocuments = entries.size,
            documentLengths = documentLengths
        )
    }

    /**
     * Extracts and normalizes all searchable terms from a vault entry.
     * Includes title (repeated for weight), tags, content, and metadata values.
     */
    private fun extractTerms(entry: VaultEntry): List<String> {
        val terms = mutableListOf<String>()

        // Title terms (added multiple times for higher weight in raw TF)
        val titleTerms = tokenize(entry.title)
        repeat(3) { terms.addAll(titleTerms) }

        // Tag terms (added twice)
        entry.tags.forEach { tag ->
            val tagTerms = tokenize(tag)
            repeat(2) { terms.addAll(tagTerms) }
        }

        // Category name
        terms.addAll(tokenize(entry.category.name))

        // Content terms
        terms.addAll(tokenize(entry.content))

        // Metadata values
        entry.metadata.values.forEach { value ->
            terms.addAll(tokenize(value))
        }

        return terms
    }

    /**
     * Tokenizes text into normalized search terms.
     * - Lowercases
     * - Removes punctuation
     * - Splits on whitespace
     * - Removes stopwords
     * - Handles common Indian English abbreviations
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9@.\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
            .map { normalizeToken(it) }
    }

    /**
     * Normalizes a token (handles abbreviations, common variants).
     */
    private fun normalizeToken(token: String): String {
        return when (token) {
            "dr", "dr." -> "doctor"
            "pwd" -> "password"
            "no", "num" -> "number"
            "govt" -> "government"
            "pvt" -> "private"
            "ltd" -> "limited"
            "acct", "acc" -> "account"
            "amt" -> "amount"
            "txn" -> "transaction"
            "mob" -> "mobile"
            "addr" -> "address"
            "dob" -> "dateofbirth"
            "yr", "yrs" -> "year"
            "mo", "mos" -> "month"
            "aadhar" -> "aadhaar"
            "dl" -> "license"
            else -> token
        }
    }

    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "but",
        "in", "for", "to", "of", "with", "it", "this", "that", "from",
        "by", "as", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "shall", "can", "my", "your",
        "his", "her", "its", "our", "their", "me", "him", "them", "us",
        "ka", "ki", "ke", "hai", "ho", "tha", "wala", "vale" // Hindi stopwords
    )

    // ============================================================
    // Search Engine
    // ============================================================

    /**
     * Searches the vault using natural language queries.
     *
     * Algorithm:
     * 1. Tokenize and expand query with synonyms
     * 2. Classify query intent (category detection)
     * 3. Calculate TF-IDF scores for each entry
     * 4. Apply field weighting (title matches > content matches)
     * 5. Apply fuzzy matching for close matches
     * 6. Boost results by category match and recency
     * 7. Return ranked results with explanations
     *
     * @param query Natural language search query
     * @param maxResults Maximum results to return (default: 10)
     * @param categoryFilter Optional category filter
     * @return List of search results sorted by relevance
     */
    fun search(
        query: String,
        maxResults: Int = 10,
        categoryFilter: VaultCategory? = null
    ): List<SearchResult> {
        if (query.isBlank() || indexedEntries.isEmpty()) return emptyList()

        // Ensure index is built
        if (index == null) buildIndex(indexedEntries)

        // Step 1: Tokenize query
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        // Step 2: Expand with synonyms
        val expandedTerms = expandWithSynonyms(queryTerms)

        // Step 3: Detect query intent (category)
        val detectedCategory = detectQueryCategory(queryTerms)

        // Step 4: Score each entry
        val scoredEntries = indexedEntries
            .filter { categoryFilter == null || it.category == categoryFilter }
            .map { entry -> scoreEntry(entry, queryTerms, expandedTerms, detectedCategory) }
            .filter { it.second > 0.01 } // Minimum relevance threshold
            .sortedByDescending { it.second }
            .take(maxResults)

        // Step 5: Build search results with highlights
        return scoredEntries.map { (entry, score, matchedFields) ->
            buildSearchResult(entry, score, matchedFields, queryTerms, expandedTerms)
        }
    }

    /**
     * Expands query terms with synonyms from the dictionary.
     * Returns original terms + synonym expansions with reduced weight.
     */
    private fun expandWithSynonyms(queryTerms: List<String>): Map<String, Double> {
        val expanded = mutableMapOf<String, Double>()

        // Original terms get full weight
        queryTerms.forEach { term ->
            expanded[term] = 1.0
        }

        // Add synonyms with reduced weight
        for (term in queryTerms) {
            val synonyms = synonymDictionary[term]
            if (synonyms != null) {
                synonyms.forEach { synonym ->
                    val synTokens = tokenize(synonym)
                    synTokens.forEach { synToken ->
                        if (synToken !in expanded || expanded[synToken]!! < 0.7) {
                            expanded[synToken] = 0.7 // Synonyms get 70% weight
                        }
                    }
                }
            }

            // Also check if this term appears as a synonym value
            synonymDictionary.forEach { (key, values) ->
                if (term in values) {
                    val keyTokens = tokenize(key)
                    keyTokens.forEach { keyToken ->
                        if (keyToken !in expanded || expanded[keyToken]!! < 0.6) {
                            expanded[keyToken] = 0.6 // Reverse synonym gets 60% weight
                        }
                    }
                }
            }
        }

        return expanded
    }

    /**
     * Detects the likely category the user is searching for.
     */
    private fun detectQueryCategory(queryTerms: List<String>): VaultCategory? {
        val categoryScores = categoryKeywords.mapValues { (_, keywords) ->
            queryTerms.count { term -> term in keywords || keywords.any { it.contains(term) } }
        }

        val bestMatch = categoryScores.maxByOrNull { it.value }
        return if (bestMatch != null && bestMatch.value >= 1) bestMatch.key else null
    }

    /**
     * Scores a single vault entry against the search query.
     * Combines TF-IDF, field weighting, fuzzy matching, and boosts.
     */
    private fun scoreEntry(
        entry: VaultEntry,
        queryTerms: List<String>,
        expandedTerms: Map<String, Double>,
        detectedCategory: VaultCategory?
    ): Triple<VaultEntry, Double, List<MatchedField>> {
        var totalScore = 0.0
        val matchedFields = mutableListOf<MatchedField>()
        val currentIndex = index ?: return Triple(entry, 0.0, emptyList())

        // === TF-IDF Score ===
        val tfidfScore = calculateTfIdfScore(entry.id, expandedTerms, currentIndex)
        totalScore += tfidfScore

        // === Field-Weighted Exact Matching ===
        val titleScore = scoreField(entry.title, queryTerms, expandedTerms)
        if (titleScore > 0) {
            totalScore += titleScore * FieldWeights.TITLE
            matchedFields.add(MatchedField("title", MatchType.EXACT, queryTerms.filter {
                entry.title.lowercase().contains(it)
            }))
        }

        val tagScore = entry.tags.maxOfOrNull { tag ->
            scoreField(tag, queryTerms, expandedTerms)
        } ?: 0.0
        if (tagScore > 0) {
            totalScore += tagScore * FieldWeights.TAGS
            matchedFields.add(MatchedField("tags", MatchType.EXACT, queryTerms.filter { term ->
                entry.tags.any { it.lowercase().contains(term) }
            }))
        }

        val contentScore = scoreField(entry.content, queryTerms, expandedTerms)
        if (contentScore > 0) {
            totalScore += contentScore * FieldWeights.CONTENT
            matchedFields.add(MatchedField("content", MatchType.SEMANTIC, queryTerms.filter {
                entry.content.lowercase().contains(it)
            }))
        }

        // === Synonym Matches ===
        val synonymMatches = findSynonymMatches(entry, expandedTerms)
        if (synonymMatches.isNotEmpty()) {
            totalScore += 0.3 * synonymMatches.size
            matchedFields.add(MatchedField("synonym", MatchType.SYNONYM, synonymMatches))
        }

        // === Fuzzy Matching ===
        val fuzzyMatches = findFuzzyMatches(entry, queryTerms)
        if (fuzzyMatches.isNotEmpty()) {
            totalScore += 0.2 * fuzzyMatches.size
            matchedFields.add(MatchedField("fuzzy", MatchType.FUZZY, fuzzyMatches))
        }

        // === Category Boost ===
        if (detectedCategory != null && entry.category == detectedCategory) {
            totalScore *= 1.3 // 30% boost for category match
            matchedFields.add(MatchedField("category", MatchType.CATEGORY_MATCH, listOf(detectedCategory.name)))
        }

        // === Recency Boost (newer entries slightly preferred) ===
        val ageInDays = (System.currentTimeMillis() - entry.updatedAt) / (1000 * 60 * 60 * 24)
        val recencyBoost = when {
            ageInDays < 7 -> 1.1
            ageInDays < 30 -> 1.05
            ageInDays < 90 -> 1.0
            else -> 0.95
        }
        totalScore *= recencyBoost

        // === Favorite Boost ===
        if (entry.isFavorite) {
            totalScore *= 1.1
        }

        // Normalize to 0-1 range
        val normalizedScore = (totalScore / 10.0).coerceIn(0.0, 1.0)

        return Triple(entry, normalizedScore, matchedFields)
    }

    /**
     * Calculates TF-IDF score for a document against expanded query terms.
     */
    private fun calculateTfIdfScore(
        docId: String,
        queryTerms: Map<String, Double>,
        tfIdfIndex: TfIdfIndex
    ): Double {
        val docTf = tfIdfIndex.termFrequencies[docId] ?: return 0.0
        var score = 0.0

        for ((term, queryWeight) in queryTerms) {
            val tf = docTf[term] ?: continue
            val df = tfIdfIndex.documentFrequency[term] ?: continue
            val idf = ln((tfIdfIndex.totalDocuments.toDouble() + 1) / (df + 1)) + 1

            score += tf * idf * queryWeight
        }

        return score
    }

    /**
     * Scores a text field against query terms.
     * Returns a 0-1 score based on term coverage.
     */
    private fun scoreField(
        fieldText: String,
        queryTerms: List<String>,
        expandedTerms: Map<String, Double>
    ): Double {
        val fieldTokens = tokenize(fieldText).toSet()
        if (fieldTokens.isEmpty()) return 0.0

        var matchScore = 0.0
        var totalWeight = 0.0

        for ((term, weight) in expandedTerms) {
            totalWeight += weight
            if (term in fieldTokens) {
                matchScore += weight
            } else if (fieldTokens.any { it.contains(term) || term.contains(it) }) {
                matchScore += weight * 0.5 // Partial match
            }
        }

        return if (totalWeight > 0) matchScore / totalWeight else 0.0
    }

    /**
     * Finds terms in the entry that match via synonym expansion.
     */
    private fun findSynonymMatches(
        entry: VaultEntry,
        expandedTerms: Map<String, Double>
    ): List<String> {
        val entryText = "${entry.title} ${entry.tags.joinToString(" ")} ${entry.content}".lowercase()
        val entryTokens = tokenize(entryText).toSet()

        return expandedTerms.filter { (term, weight) ->
            weight < 1.0 && term in entryTokens // Only synonym terms (weight < 1.0)
        }.keys.toList()
    }

    /**
     * Finds fuzzy matches using Levenshtein distance.
     */
    private fun findFuzzyMatches(entry: VaultEntry, queryTerms: List<String>): List<String> {
        val entryTokens = tokenize("${entry.title} ${entry.tags.joinToString(" ")}").toSet()
        val matches = mutableListOf<String>()

        for (queryTerm in queryTerms) {
            if (queryTerm.length < 4) continue // Don't fuzzy-match short terms

            for (entryToken in entryTokens) {
                if (entryToken.length < 4) continue
                val distance = levenshteinDistance(queryTerm, entryToken)
                if (distance in 1..2) { // Allow 1-2 character differences
                    matches.add(entryToken)
                    break
                }
            }
        }

        return matches
    }

    /**
     * Levenshtein distance calculation for fuzzy matching.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Quick checks
        if (m == 0) return n
        if (n == 0) return m
        if (s1 == s2) return 0

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[m][n]
    }

    // ============================================================
    // Result Building
    // ============================================================

    private fun buildSearchResult(
        entry: VaultEntry,
        score: Double,
        matchedFields: List<MatchedField>,
        queryTerms: List<String>,
        expandedTerms: Map<String, Double>
    ): SearchResult {
        // Highlight title
        val highlightedTitle = highlightText(entry.title, queryTerms + expandedTerms.keys.toList())

        // Generate content snippet with highlight
        val highlightedSnippet = generateSnippet(entry.content, queryTerms + expandedTerms.keys.toList())

        // Generate human-readable match reason
        val matchReason = generateMatchReason(matchedFields)

        return SearchResult(
            entry = entry,
            relevanceScore = score,
            matchedFields = matchedFields,
            matchReason = matchReason,
            highlightedTitle = highlightedTitle,
            highlightedSnippet = highlightedSnippet
        )
    }

    /**
     * Wraps matched terms in ** for display highlighting.
     */
    private fun highlightText(text: String, terms: List<String>): String {
        var highlighted = text
        val sortedTerms = terms.sortedByDescending { it.length } // Longer terms first

        for (term in sortedTerms.distinct()) {
            val regex = Regex("(?i)\\b${Regex.escape(term)}\\b")
            highlighted = highlighted.replace(regex) { "**${it.value}**" }
        }

        return highlighted
    }

    /**
     * Generates a relevant snippet from content with context around matched terms.
     */
    private fun generateSnippet(content: String, terms: List<String>, maxLength: Int = 150): String {
        if (content.length <= maxLength) return highlightText(content, terms)

        // Find first matching term position
        val lowerContent = content.lowercase()
        var bestPos = -1

        for (term in terms) {
            val pos = lowerContent.indexOf(term)
            if (pos >= 0 && (bestPos < 0 || pos < bestPos)) {
                bestPos = pos
            }
        }

        val startPos = if (bestPos >= 0) {
            (bestPos - 30).coerceAtLeast(0)
        } else 0

        val endPos = (startPos + maxLength).coerceAtMost(content.length)
        val snippet = buildString {
            if (startPos > 0) append("...")
            append(content.substring(startPos, endPos))
            if (endPos < content.length) append("...")
        }

        return highlightText(snippet, terms)
    }

    /**
     * Generates a human-readable explanation of why this result matched.
     */
    private fun generateMatchReason(matchedFields: List<MatchedField>): String {
        if (matchedFields.isEmpty()) return "General relevance"

        val reasons = matchedFields.map { field ->
            when (field.matchType) {
                MatchType.EXACT -> "Matched \"${field.matchedTerms.firstOrNull()}\" in ${field.fieldName}"
                MatchType.SYNONYM -> "Related to: ${field.matchedTerms.take(2).joinToString(", ")}"
                MatchType.FUZZY -> "Similar to: ${field.matchedTerms.firstOrNull()}"
                MatchType.CATEGORY_MATCH -> "Category: ${field.matchedTerms.firstOrNull()}"
                MatchType.SEMANTIC -> "Relevant content match"
            }
        }

        return reasons.take(2).joinToString(" • ")
    }

    // ============================================================
    // Convenience Methods
    // ============================================================

    /**
     * Quick search by category — returns all entries in a category,
     * optionally filtered by a keyword.
     */
    fun searchByCategory(
        category: VaultCategory,
        keyword: String? = null
    ): List<SearchResult> {
        val filteredEntries = indexedEntries.filter { it.category == category }

        return if (keyword != null && keyword.isNotBlank()) {
            search(keyword, categoryFilter = category)
        } else {
            filteredEntries.map { entry ->
                SearchResult(
                    entry = entry,
                    relevanceScore = 1.0,
                    matchedFields = listOf(MatchedField("category", MatchType.CATEGORY_MATCH, listOf(category.name))),
                    matchReason = "Category: ${category.name}",
                    highlightedTitle = entry.title,
                    highlightedSnippet = entry.content.take(100)
                )
            }.sortedByDescending { it.entry.updatedAt }
        }
    }

    /**
     * Suggests vault entries based on context (time, recent activity, etc.).
     * For example, suggests travel documents before a trip, or medical records
     * near a doctor appointment.
     */
    fun getSuggestions(context: SearchContext): List<SearchResult> {
        val suggestions = mutableListOf<SearchResult>()

        // Time-based suggestions
        when (context.trigger) {
            SuggestionTrigger.TRAVEL_UPCOMING -> {
                suggestions.addAll(search("passport visa ticket booking pnr", maxResults = 5))
            }
            SuggestionTrigger.MEDICAL_APPOINTMENT -> {
                suggestions.addAll(search("doctor prescription medical report insurance", maxResults = 5))
            }
            SuggestionTrigger.BILL_PAYMENT -> {
                suggestions.addAll(search("bill account number ifsc upi", maxResults = 5))
            }
            SuggestionTrigger.TAX_SEASON -> {
                suggestions.addAll(search("pan tax investment 80c insurance receipt", maxResults = 5))
            }
            SuggestionTrigger.GENERAL -> {
                // Return recently accessed or favorites
                suggestions.addAll(
                    indexedEntries
                        .filter { it.isFavorite }
                        .sortedByDescending { it.updatedAt }
                        .take(5)
                        .map { entry ->
                            SearchResult(
                                entry = entry,
                                relevanceScore = 0.8,
                                matchedFields = emptyList(),
                                matchReason = "Favorite",
                                highlightedTitle = entry.title,
                                highlightedSnippet = entry.content.take(100)
                            )
                        }
                )
            }
        }

        return suggestions
    }

    data class SearchContext(
        val trigger: SuggestionTrigger,
        val relatedKeywords: List<String> = emptyList()
    )

    enum class SuggestionTrigger {
        TRAVEL_UPCOMING,
        MEDICAL_APPOINTMENT,
        BILL_PAYMENT,
        TAX_SEASON,
        GENERAL
    }

    /**
     * Returns search statistics for debugging/analytics.
     */
    fun getIndexStats(): IndexStats {
        val currentIndex = index ?: return IndexStats(0, 0, 0)
        return IndexStats(
            totalDocuments = currentIndex.totalDocuments,
            uniqueTerms = currentIndex.documentFrequency.size,
            averageDocumentLength = currentIndex.documentLengths.values.average().toInt(),
            topTerms = currentIndex.documentFrequency.entries
                .sortedByDescending { it.value }
                .take(20)
                .map { it.key to it.value }
        )
    }

    data class IndexStats(
        val totalDocuments: Int,
        val uniqueTerms: Int,
        val averageDocumentLength: Int,
        val topTerms: List<Pair<String, Int>> = emptyList()
    )

    /**
     * Adds a new entry to the index without full rebuild.
     * Call this for incremental updates (single entry added).
     */
    fun addToIndex(entry: VaultEntry) {
        indexedEntries = indexedEntries + entry
        buildIndex(indexedEntries) // For simplicity, rebuild. Optimize later if needed.
    }

    /**
     * Removes an entry from the index.
     */
    fun removeFromIndex(entryId: String) {
        indexedEntries = indexedEntries.filter { it.id != entryId }
        buildIndex(indexedEntries)
    }

    /**
     * Updates an entry in the index.
     */
    fun updateInIndex(updatedEntry: VaultEntry) {
        indexedEntries = indexedEntries.map {
            if (it.id == updatedEntry.id) updatedEntry else it
        }
        buildIndex(indexedEntries)
    }
}
