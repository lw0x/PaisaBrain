package com.paisabrain.app.tools

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Organized document storage and retrieval system.
 *
 * Provides categorized storage for important documents with OCR-based
 * auto-detection, expiry tracking, and smart reminders for renewals.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Document categories for organized storage.
 */
enum class DocumentCategory(val displayName: String, val icon: String) {
    ID_DOCUMENTS("ID Documents", "🪪"),
    INSURANCE("Insurance", "🛡️"),
    MEDICAL("Medical", "🏥"),
    FINANCIAL("Financial", "🏦"),
    EDUCATION("Education", "🎓"),
    PROPERTY("Property", "🏠"),
    VEHICLE("Vehicle", "🚗"),
    WARRANTY("Warranty", "📦"),
    CONTRACTS("Contracts", "📝"),
    RECEIPTS("Receipts", "🧾")
}

/**
 * Represents a stored document with metadata.
 *
 * @property id Unique identifier for the document
 * @property title User-friendly document title
 * @property category Classification category
 * @property photos List of file paths to document photos/scans
 * @property ocrText Extracted text from OCR processing
 * @property expiryDate Optional expiry/renewal date
 * @property tags User-defined searchable tags
 * @property createdAt Timestamp when document was added
 * @property notes Additional user notes
 * @property detectedType Auto-detected document sub-type (e.g., "PAN Card", "Passport")
 * @property isArchived Whether document is archived
 */
data class Document(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: DocumentCategory,
    val photos: List<String> = emptyList(),
    val ocrText: String = "",
    val expiryDate: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val notes: String = "",
    val detectedType: String = "",
    val isArchived: Boolean = false
)

/**
 * Expiry reminder with urgency level.
 */
data class ExpiryReminder(
    val documentId: String,
    val documentTitle: String,
    val category: DocumentCategory,
    val expiryDate: LocalDate,
    val daysRemaining: Long,
    val urgency: ReminderUrgency,
    val message: String
)

/**
 * Urgency levels for expiry reminders.
 */
enum class ReminderUrgency {
    /** More than 60 days remaining */
    LOW,
    /** 30-60 days remaining */
    MEDIUM,
    /** 7-30 days remaining */
    HIGH,
    /** Less than 7 days or already expired */
    CRITICAL
}

/**
 * Search result with relevance scoring.
 */
data class DocumentSearchResult(
    val document: Document,
    val matchScore: Double,
    val matchedFields: List<String>
)

/**
 * Summary statistics for the vault.
 */
data class VaultSummary(
    val totalDocuments: Int,
    val byCategory: Map<DocumentCategory, Int>,
    val expiringWithin30Days: Int,
    val expiringWithin90Days: Int,
    val expiredCount: Int,
    val recentlyAdded: List<Document>
)

// ─────────────────────────────────────────────────────────────────────────────
// Document Vault Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core document vault managing storage, retrieval, and intelligence.
 *
 * Features:
 * - Categorized document storage with photo attachments
 * - OCR-based auto-detection of document types
 * - Expiry tracking with multi-level reminders
 * - Full-text and tag-based search
 * - Quick access by natural language queries
 */
class DocumentVault {

    private val documents = mutableListOf<Document>()

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD Operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds a new document to the vault.
     *
     * If [ocrText] is provided, auto-detection runs to suggest category and type.
     *
     * @param title Document title
     * @param category Document category
     * @param photos File paths to document photos
     * @param ocrText Extracted OCR text (optional)
     * @param expiryDate Document expiry date (optional)
     * @param tags Searchable tags
     * @param notes Additional notes
     * @return The created Document
     */
    fun addDocument(
        title: String,
        category: DocumentCategory,
        photos: List<String> = emptyList(),
        ocrText: String = "",
        expiryDate: LocalDate? = null,
        tags: List<String> = emptyList(),
        notes: String = ""
    ): Document {
        val detectedType = if (ocrText.isNotBlank()) detectDocumentType(ocrText) else ""
        val effectiveCategory = if (ocrText.isNotBlank()) {
            suggestCategory(ocrText) ?: category
        } else {
            category
        }

        val document = Document(
            title = title,
            category = effectiveCategory,
            photos = photos,
            ocrText = ocrText,
            expiryDate = expiryDate,
            tags = tags,
            notes = notes,
            detectedType = detectedType
        )
        documents.add(document)
        return document
    }

    /**
     * Updates an existing document.
     *
     * @param id Document ID to update
     * @param update Lambda to modify the document
     * @return Updated document or null if not found
     */
    fun updateDocument(id: String, update: (Document) -> Document): Document? {
        val index = documents.indexOfFirst { it.id == id }
        if (index == -1) return null

        val updated = update(documents[index])
        documents[index] = updated
        return updated
    }

    /**
     * Archives a document (soft delete).
     */
    fun archiveDocument(id: String): Boolean {
        val index = documents.indexOfFirst { it.id == id }
        if (index == -1) return false
        documents[index] = documents[index].copy(isArchived = true)
        return true
    }

    /**
     * Permanently removes a document.
     */
    fun deleteDocument(id: String): Boolean {
        return documents.removeAll { it.id == id }
    }

    /**
     * Retrieves a document by ID.
     */
    fun getDocument(id: String): Document? {
        return documents.find { it.id == id && !it.isArchived }
    }

    /**
     * Retrieves all documents in a category.
     */
    fun getByCategory(category: DocumentCategory): List<Document> {
        return documents.filter { it.category == category && !it.isArchived }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Retrieves all active (non-archived) documents.
     */
    fun getAllDocuments(): List<Document> {
        return documents.filter { !it.isArchived }.sortedByDescending { it.createdAt }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OCR Auto-Detection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detects document type from OCR-extracted text.
     *
     * Scans for known keywords and patterns to identify the document.
     *
     * @param ocrText The text extracted via OCR
     * @return Detected document type name, or empty string if unknown
     */
    fun detectDocumentType(ocrText: String): String {
        val text = ocrText.uppercase()

        return when {
            // ID Documents
            text.contains("PERMANENT ACCOUNT NUMBER") || text.contains("PAN") &&
                text.contains("INCOME TAX") -> "PAN Card"

            text.contains("AADHAAR") || text.contains("UNIQUE IDENTIFICATION") ||
                text.contains("UID") && text.contains("GOVT") -> "Aadhaar Card"

            text.contains("PASSPORT") && (text.contains("REPUBLIC") ||
                text.contains("NATIONALITY")) -> "Passport"

            text.contains("DRIVING") && text.contains("LICENCE") ||
                text.contains("DRIVING LICENSE") -> "Driving Licence"

            text.contains("VOTER") && text.contains("IDENTITY") ||
                text.contains("ELECTION COMMISSION") -> "Voter ID"

            // Insurance
            text.contains("POLICY NO") || text.contains("POLICY NUMBER") ||
                text.contains("SUM ASSURED") || text.contains("PREMIUM") -> when {
                text.contains("HEALTH") || text.contains("MEDICAL") ||
                    text.contains("HOSPITALIZATION") -> "Health Insurance Policy"
                text.contains("LIFE") || text.contains("DEATH BENEFIT") -> "Life Insurance Policy"
                text.contains("MOTOR") || text.contains("VEHICLE") ||
                    text.contains("COMPREHENSIVE") -> "Vehicle Insurance Policy"
                text.contains("HOME") || text.contains("PROPERTY") -> "Property Insurance Policy"
                else -> "Insurance Policy"
            }

            // Financial
            text.contains("INVOICE") || text.contains("INV NO") ||
                text.contains("TAX INVOICE") -> "Invoice"

            text.contains("FIXED DEPOSIT") || text.contains("FD RECEIPT") -> "Fixed Deposit Receipt"

            text.contains("MUTUAL FUND") || text.contains("NAV") &&
                text.contains("UNITS") -> "Mutual Fund Statement"

            text.contains("FORM 16") || text.contains("FORM-16") -> "Form 16 (Tax)"

            text.contains("ITR") || text.contains("INCOME TAX RETURN") -> "Income Tax Return"

            text.contains("SALARY SLIP") || text.contains("PAY SLIP") ||
                text.contains("PAYSLIP") -> "Salary Slip"

            // Property
            text.contains("SALE DEED") || text.contains("CONVEYANCE") -> "Sale Deed"
            text.contains("REGISTRATION") && text.contains("PROPERTY") -> "Property Registration"

            // Vehicle
            text.contains("REGISTRATION CERTIFICATE") && text.contains("VEHICLE") ||
                text.contains("RC BOOK") -> "Vehicle RC"

            text.contains("POLLUTION") && text.contains("CERTIFICATE") ||
                text.contains("PUC") -> "PUC Certificate"

            // Education
            text.contains("MARK SHEET") || text.contains("MARKS") &&
                text.contains("EXAMINATION") -> "Mark Sheet"

            text.contains("DEGREE") || text.contains("BACHELOR") ||
                text.contains("MASTER") -> "Degree Certificate"

            // Medical
            text.contains("PRESCRIPTION") || text.contains("RX") &&
                text.contains("DR.") -> "Medical Prescription"

            text.contains("LAB REPORT") || text.contains("TEST RESULTS") ||
                text.contains("PATHOLOGY") -> "Lab Report"

            // Contracts
            text.contains("AGREEMENT") && (text.contains("RENT") ||
                text.contains("LEASE")) -> "Rental Agreement"

            text.contains("EMPLOYMENT") && text.contains("AGREEMENT") ||
                text.contains("OFFER LETTER") -> "Employment Document"

            // Receipts & Warranties
            text.contains("WARRANTY") && text.contains("CARD") -> "Warranty Card"
            text.contains("RECEIPT") || text.contains("PAYMENT RECEIVED") -> "Payment Receipt"

            else -> ""
        }
    }

    /**
     * Suggests the most appropriate category based on OCR text.
     *
     * @param ocrText Extracted text from the document
     * @return Suggested category or null if no strong match
     */
    fun suggestCategory(ocrText: String): DocumentCategory? {
        val text = ocrText.uppercase()

        val categoryScores = mutableMapOf<DocumentCategory, Int>()

        // ID Document keywords
        val idKeywords = listOf("PAN", "AADHAAR", "PASSPORT", "DRIVING LICENCE",
            "VOTER ID", "UNIQUE IDENTIFICATION", "IDENTITY")
        categoryScores[DocumentCategory.ID_DOCUMENTS] = idKeywords.count { text.contains(it) }

        // Insurance keywords
        val insuranceKeywords = listOf("POLICY", "PREMIUM", "SUM ASSURED", "INSURED",
            "COVERAGE", "NOMINEE", "CLAIM")
        categoryScores[DocumentCategory.INSURANCE] = insuranceKeywords.count { text.contains(it) }

        // Medical keywords
        val medicalKeywords = listOf("PATIENT", "DOCTOR", "PRESCRIPTION", "DIAGNOSIS",
            "HOSPITAL", "PATHOLOGY", "LAB", "MEDICINE")
        categoryScores[DocumentCategory.MEDICAL] = medicalKeywords.count { text.contains(it) }

        // Financial keywords
        val financialKeywords = listOf("BANK", "ACCOUNT", "TRANSACTION", "INTEREST",
            "DEPOSIT", "MUTUAL FUND", "INVESTMENT", "TAX", "FORM 16")
        categoryScores[DocumentCategory.FINANCIAL] = financialKeywords.count { text.contains(it) }

        // Education keywords
        val educationKeywords = listOf("UNIVERSITY", "COLLEGE", "MARKS", "GRADE",
            "DEGREE", "EXAMINATION", "SEMESTER", "CERTIFICATE")
        categoryScores[DocumentCategory.EDUCATION] = educationKeywords.count { text.contains(it) }

        // Property keywords
        val propertyKeywords = listOf("PROPERTY", "LAND", "SALE DEED", "REGISTRATION",
            "PLOT", "FLAT", "APARTMENT", "POSSESSION")
        categoryScores[DocumentCategory.PROPERTY] = propertyKeywords.count { text.contains(it) }

        // Vehicle keywords
        val vehicleKeywords = listOf("VEHICLE", "MOTOR", "ENGINE", "CHASSIS",
            "REGISTRATION CERTIFICATE", "RC", "PUC", "POLLUTION")
        categoryScores[DocumentCategory.VEHICLE] = vehicleKeywords.count { text.contains(it) }

        // Warranty keywords
        val warrantyKeywords = listOf("WARRANTY", "GUARANTEE", "SERVICE", "REPAIR",
            "MANUFACTURE DATE", "VALID TILL")
        categoryScores[DocumentCategory.WARRANTY] = warrantyKeywords.count { text.contains(it) }

        // Contract keywords
        val contractKeywords = listOf("AGREEMENT", "TERMS", "CONDITIONS", "PARTY",
            "HEREIN", "CLAUSE", "TENURE", "LEASE", "RENT")
        categoryScores[DocumentCategory.CONTRACTS] = contractKeywords.count { text.contains(it) }

        // Receipt keywords
        val receiptKeywords = listOf("RECEIPT", "PAID", "AMOUNT", "INVOICE",
            "GST", "TOTAL", "BILL", "PAYMENT")
        categoryScores[DocumentCategory.RECEIPTS] = receiptKeywords.count { text.contains(it) }

        val bestMatch = categoryScores.maxByOrNull { it.value }
        return if (bestMatch != null && bestMatch.value >= 2) bestMatch.key else null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expiry Tracking & Reminders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets all pending expiry reminders sorted by urgency.
     *
     * Generates context-appropriate reminder messages based on document type.
     *
     * @param today Reference date (defaults to today)
     * @return List of reminders sorted by urgency (most urgent first)
     */
    fun getExpiryReminders(today: LocalDate = LocalDate.now()): List<ExpiryReminder> {
        return documents
            .filter { !it.isArchived && it.expiryDate != null }
            .mapNotNull { doc ->
                val daysRemaining = ChronoUnit.DAYS.between(today, doc.expiryDate!!)

                // Only show reminders for documents expiring within 90 days or already expired
                if (daysRemaining > 90) return@mapNotNull null

                val urgency = when {
                    daysRemaining < 0 -> ReminderUrgency.CRITICAL
                    daysRemaining <= 7 -> ReminderUrgency.CRITICAL
                    daysRemaining <= 30 -> ReminderUrgency.HIGH
                    daysRemaining <= 60 -> ReminderUrgency.MEDIUM
                    else -> ReminderUrgency.LOW
                }

                val message = generateReminderMessage(doc, daysRemaining)

                ExpiryReminder(
                    documentId = doc.id,
                    documentTitle = doc.title,
                    category = doc.category,
                    expiryDate = doc.expiryDate,
                    daysRemaining = daysRemaining,
                    urgency = urgency,
                    message = message
                )
            }
            .sortedWith(compareBy({ it.urgency.ordinal * -1 }, { it.daysRemaining }))
    }

    /**
     * Generates a context-specific reminder message.
     */
    private fun generateReminderMessage(doc: Document, daysRemaining: Long): String {
        val timeText = when {
            daysRemaining < 0 -> "expired ${-daysRemaining} days ago"
            daysRemaining == 0L -> "expires today"
            daysRemaining == 1L -> "expires tomorrow"
            else -> "expires in $daysRemaining days"
        }

        return when (doc.category) {
            DocumentCategory.ID_DOCUMENTS -> when {
                doc.detectedType.contains("Passport", ignoreCase = true) ->
                    "⚠️ Your passport $timeText! Renewal takes 2-4 weeks. Apply soon to avoid travel disruption."
                doc.detectedType.contains("Driving", ignoreCase = true) ->
                    "⚠️ Your driving licence $timeText! Renew to avoid fines."
                else -> "⚠️ Your ${doc.title} $timeText!"
            }

            DocumentCategory.INSURANCE -> when {
                doc.detectedType.contains("Health", ignoreCase = true) ->
                    "🛡️ Health insurance $timeText! Don't let your coverage lapse — medical emergencies don't wait."
                doc.detectedType.contains("Vehicle", ignoreCase = true) ->
                    "🚗 Vehicle insurance renewal $timeText! Driving without insurance is illegal and risky."
                doc.detectedType.contains("Life", ignoreCase = true) ->
                    "🛡️ Life insurance policy $timeText! Ensure your family stays protected."
                else -> "🛡️ Insurance policy $timeText! Renew to maintain coverage."
            }

            DocumentCategory.VEHICLE ->
                "🚗 Vehicle document $timeText! Check if renewal is needed to stay road-legal."

            DocumentCategory.WARRANTY ->
                "📦 Warranty for ${doc.title} $timeText! Get any pending repairs done while still covered."

            DocumentCategory.CONTRACTS ->
                "📝 Contract ${doc.title} $timeText! Review terms for renewal or exit."

            DocumentCategory.MEDICAL ->
                "🏥 Medical document ${doc.title} $timeText! Schedule a follow-up if needed."

            else -> "📋 ${doc.title} $timeText!"
        }
    }

    /**
     * Gets documents expiring within a specific number of days.
     */
    fun getExpiringDocuments(withinDays: Int, today: LocalDate = LocalDate.now()): List<Document> {
        val cutoff = today.plusDays(withinDays.toLong())
        return documents.filter { doc ->
            !doc.isArchived && doc.expiryDate != null &&
                !doc.expiryDate.isBefore(today) &&
                !doc.expiryDate.isAfter(cutoff)
        }.sortedBy { it.expiryDate }
    }

    /**
     * Gets already-expired documents.
     */
    fun getExpiredDocuments(today: LocalDate = LocalDate.now()): List<Document> {
        return documents.filter { doc ->
            !doc.isArchived && doc.expiryDate != null && doc.expiryDate.isBefore(today)
        }.sortedBy { it.expiryDate }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Search & Quick Access
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches documents across all fields.
     *
     * Searches title, tags, OCR text, notes, detected type, and category name.
     * Results are ranked by relevance score.
     *
     * @param query Search query string
     * @return Sorted list of matching documents with relevance scores
     */
    fun search(query: String): List<DocumentSearchResult> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase().trim()
        val queryWords = queryLower.split("\\s+".toRegex())

        return documents
            .filter { !it.isArchived }
            .mapNotNull { doc ->
                var score = 0.0
                val matchedFields = mutableListOf<String>()

                // Title match (highest weight)
                if (doc.title.lowercase().contains(queryLower)) {
                    score += 10.0
                    matchedFields.add("title")
                } else {
                    val titleWordMatches = queryWords.count { doc.title.lowercase().contains(it) }
                    if (titleWordMatches > 0) {
                        score += titleWordMatches * 3.0
                        matchedFields.add("title")
                    }
                }

                // Category match
                if (doc.category.displayName.lowercase().contains(queryLower)) {
                    score += 7.0
                    matchedFields.add("category")
                }

                // Detected type match
                if (doc.detectedType.lowercase().contains(queryLower)) {
                    score += 8.0
                    matchedFields.add("detectedType")
                }

                // Tag match
                val tagMatches = doc.tags.count { it.lowercase().contains(queryLower) }
                if (tagMatches > 0) {
                    score += tagMatches * 5.0
                    matchedFields.add("tags")
                }

                // OCR text match
                if (doc.ocrText.lowercase().contains(queryLower)) {
                    score += 4.0
                    matchedFields.add("ocrText")
                } else {
                    val ocrWordMatches = queryWords.count { doc.ocrText.lowercase().contains(it) }
                    if (ocrWordMatches > 0) {
                        score += ocrWordMatches * 1.5
                        matchedFields.add("ocrText")
                    }
                }

                // Notes match
                if (doc.notes.lowercase().contains(queryLower)) {
                    score += 3.0
                    matchedFields.add("notes")
                }

                if (score > 0) {
                    DocumentSearchResult(doc, score, matchedFields)
                } else null
            }
            .sortedByDescending { it.matchScore }
    }

    /**
     * Quick access using natural language queries.
     *
     * Interprets common requests like:
     * - "Show me my health insurance"
     * - "PAN card"
     * - "Vehicle documents"
     * - "Expiring soon"
     *
     * @param query Natural language query
     * @return Matching documents
     */
    fun quickAccess(query: String): List<Document> {
        val q = query.lowercase().trim()

        // Handle special queries
        return when {
            q.contains("expir") || q.contains("renew") ->
                getExpiringDocuments(90)

            q.contains("expired") ->
                getExpiredDocuments()

            q.contains("recent") || q.contains("latest") || q.contains("new") ->
                documents.filter { !it.isArchived }
                    .sortedByDescending { it.createdAt }
                    .take(10)

            else -> {
                // Map common phrases to categories
                val categoryMatch = mapQueryToCategory(q)
                if (categoryMatch != null) {
                    getByCategory(categoryMatch)
                } else {
                    search(query).map { it.document }
                }
            }
        }
    }

    /**
     * Maps natural language query phrases to document categories.
     */
    private fun mapQueryToCategory(query: String): DocumentCategory? {
        return when {
            query.contains("id") || query.contains("identity") ||
                query.contains("pan") || query.contains("aadhaar") ||
                query.contains("passport") || query.contains("licence") ||
                query.contains("license") -> DocumentCategory.ID_DOCUMENTS

            query.contains("insurance") || query.contains("policy") ->
                DocumentCategory.INSURANCE

            query.contains("medical") || query.contains("health") && !query.contains("insurance") ||
                query.contains("hospital") || query.contains("prescription") ->
                DocumentCategory.MEDICAL

            query.contains("financial") || query.contains("bank") ||
                query.contains("tax") || query.contains("fd") ||
                query.contains("investment") -> DocumentCategory.FINANCIAL

            query.contains("education") || query.contains("degree") ||
                query.contains("certificate") || query.contains("mark") ->
                DocumentCategory.EDUCATION

            query.contains("property") || query.contains("house") ||
                query.contains("flat") || query.contains("land") ->
                DocumentCategory.PROPERTY

            query.contains("vehicle") || query.contains("car") ||
                query.contains("bike") || query.contains("rc") ->
                DocumentCategory.VEHICLE

            query.contains("warranty") || query.contains("guarantee") ->
                DocumentCategory.WARRANTY

            query.contains("contract") || query.contains("agreement") ||
                query.contains("rent") || query.contains("lease") ->
                DocumentCategory.CONTRACTS

            query.contains("receipt") || query.contains("bill") ||
                query.contains("invoice") -> DocumentCategory.RECEIPTS

            else -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vault Summary & Statistics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a comprehensive summary of the document vault.
     */
    fun getVaultSummary(today: LocalDate = LocalDate.now()): VaultSummary {
        val active = documents.filter { !it.isArchived }

        val byCategory = active.groupBy { it.category }
            .mapValues { it.value.size }

        val expiringWithin30 = active.count { doc ->
            doc.expiryDate != null &&
                !doc.expiryDate.isBefore(today) &&
                !doc.expiryDate.isAfter(today.plusDays(30))
        }

        val expiringWithin90 = active.count { doc ->
            doc.expiryDate != null &&
                !doc.expiryDate.isBefore(today) &&
                !doc.expiryDate.isAfter(today.plusDays(90))
        }

        val expired = active.count { doc ->
            doc.expiryDate != null && doc.expiryDate.isBefore(today)
        }

        val recent = active.sortedByDescending { it.createdAt }.take(5)

        return VaultSummary(
            totalDocuments = active.size,
            byCategory = byCategory,
            expiringWithin30Days = expiringWithin30,
            expiringWithin90Days = expiringWithin90,
            expiredCount = expired,
            recentlyAdded = recent
        )
    }

    /**
     * Formats vault summary as a readable string.
     */
    fun formatVaultSummary(today: LocalDate = LocalDate.now()): String {
        val summary = getVaultSummary(today)
        val sb = StringBuilder()

        sb.appendLine("📂 Document Vault Summary")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Total documents: ${summary.totalDocuments}")
        sb.appendLine()

        if (summary.byCategory.isNotEmpty()) {
            sb.appendLine("By Category:")
            summary.byCategory
                .toSortedMap(compareByDescending { summary.byCategory[it] })
                .forEach { (category, count) ->
                    sb.appendLine("  ${category.icon} ${category.displayName}: $count")
                }
            sb.appendLine()
        }

        if (summary.expiredCount > 0) {
            sb.appendLine("⚠️ Expired: ${summary.expiredCount} documents need attention")
        }
        if (summary.expiringWithin30Days > 0) {
            sb.appendLine("🔔 Expiring within 30 days: ${summary.expiringWithin30Days}")
        }
        if (summary.expiringWithin90Days > summary.expiringWithin30Days) {
            sb.appendLine("📅 Expiring within 90 days: ${summary.expiringWithin90Days}")
        }

        return sb.toString().trimEnd()
    }
}
