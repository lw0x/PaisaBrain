package com.paisabrain.app.parser

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SmsParser - Main parser engine for extracting financial transaction data from SMS messages.
 *
 * Supports 50+ Indian banks, all major UPI apps, and various transaction modes
 * (debit card, credit card, UPI, NEFT, IMPS, RTGS, ATM, EMI, auto-debit).
 *
 * NOTE: Internal technical file. Bank names and sender patterns are system
 * constants required for SMS detection — never displayed to the user.
 *
 * Usage:
 * ```
 * val parser = SmsParser()
 * val result = parser.parse(smsBody, senderId, timestamp)
 * if (result != null) {
 *     // Transaction parsed successfully
 *     println("Amount: ${result.amount}, Type: ${result.type}")
 * }
 * ```
 */
class SmsParser {

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA MODELS
    // ═══════════════════════════════════════════════════════════════════════════

    enum class TransactionType {
        CREDIT, DEBIT
    }

    data class ParsedTransaction(
        val amount: Double,
        val type: TransactionType,
        val merchantName: String?,
        val bankName: String?,
        val accountLastFour: String?,
        val balanceAfterTransaction: Double?,
        val transactionMode: SmsBankPatterns.TransactionMode,
        val referenceNumber: String?,
        val timestamp: Long,
        val rawSmsBody: String,
        val senderId: String?,
        val confidence: Float // 0.0 to 1.0
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse an SMS message and extract transaction details.
     *
     * @param body The SMS body text
     * @param senderId The SMS sender ID (e.g., "AD-HDFCBK", "VM-SBIBNK")
     * @param timestamp When the SMS was received (epoch millis)
     * @return ParsedTransaction if a valid financial transaction is found, null otherwise
     */
    fun parse(body: String, senderId: String? = null, timestamp: Long = System.currentTimeMillis()): ParsedTransaction? {
        if (body.isBlank()) return null

        // Quick pre-filter: skip obviously non-financial messages
        if (!SmsBankPatterns.isLikelyFinancialSms(body, senderId)) return null

        // Try bank-specific patterns first (highest confidence)
        val bankResult = tryBankSpecificParse(body, senderId, timestamp)
        if (bankResult != null) return bankResult

        // Try UPI app patterns
        val upiResult = tryUpiAppParse(body, senderId, timestamp)
        if (upiResult != null) return upiResult

        // Fall back to generic patterns
        return tryGenericParse(body, senderId, timestamp)
    }

    /**
     * Check if an SMS is a financial/transactional message.
     * Lighter than full parse - use for filtering.
     */
    fun isFinancialSms(body: String, senderId: String? = null): Boolean {
        return SmsBankPatterns.isLikelyFinancialSms(body, senderId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BANK-SPECIFIC PARSING
    // ═══════════════════════════════════════════════════════════════════════════

    private fun tryBankSpecificParse(
        body: String,
        senderId: String?,
        timestamp: Long
    ): ParsedTransaction? {
        // Find matching bank by sender ID
        val matchedBank = findMatchingBank(senderId, body)
            ?: return null

        // Try debit patterns
        for (pattern in matchedBank.debitPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amount = extractAmountFromMatch(match) ?: continue
                val account = extractAccountFromMatch(match)
                val merchant = extractMerchantFromMatch(match)
                val balance = extractBalance(body, matchedBank.balancePattern)
                val mode = detectTransactionMode(body)
                val refNumber = extractReferenceNumber(body)

                return ParsedTransaction(
                    amount = amount,
                    type = TransactionType.DEBIT,
                    merchantName = cleanMerchantName(merchant),
                    bankName = matchedBank.bankName,
                    accountLastFour = account,
                    balanceAfterTransaction = balance,
                    transactionMode = mode,
                    referenceNumber = refNumber,
                    timestamp = timestamp,
                    rawSmsBody = body,
                    senderId = senderId,
                    confidence = 0.95f
                )
            }
        }

        // Try credit patterns
        for (pattern in matchedBank.creditPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amount = extractAmountFromMatch(match) ?: continue
                val account = extractAccountFromMatch(match)
                val merchant = extractMerchantFromMatch(match)
                val balance = extractBalance(body, matchedBank.balancePattern)
                val mode = detectTransactionMode(body)
                val refNumber = extractReferenceNumber(body)

                return ParsedTransaction(
                    amount = amount,
                    type = TransactionType.CREDIT,
                    merchantName = cleanMerchantName(merchant),
                    bankName = matchedBank.bankName,
                    accountLastFour = account,
                    balanceAfterTransaction = balance,
                    transactionMode = mode,
                    referenceNumber = refNumber,
                    timestamp = timestamp,
                    rawSmsBody = body,
                    senderId = senderId,
                    confidence = 0.95f
                )
            }
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPI APP PARSING
    // ═══════════════════════════════════════════════════════════════════════════

    private fun tryUpiAppParse(
        body: String,
        senderId: String?,
        timestamp: Long
    ): ParsedTransaction? {
        val matchedApp = findMatchingUpiApp(senderId, body)
            ?: return null

        // Try debit patterns
        for (pattern in matchedApp.debitPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amount = extractAmountFromMatch(match) ?: continue
                val merchant = extractMerchantFromMatch(match)
                val account = extractAccountFromBody(body)
                val balance = extractBalanceFromBody(body)
                val refNumber = extractReferenceNumber(body)

                return ParsedTransaction(
                    amount = amount,
                    type = TransactionType.DEBIT,
                    merchantName = cleanMerchantName(merchant),
                    bankName = matchedApp.appName,
                    accountLastFour = account,
                    balanceAfterTransaction = balance,
                    transactionMode = SmsBankPatterns.TransactionMode.UPI,
                    referenceNumber = refNumber,
                    timestamp = timestamp,
                    rawSmsBody = body,
                    senderId = senderId,
                    confidence = 0.90f
                )
            }
        }

        // Try credit patterns
        for (pattern in matchedApp.creditPatterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amount = extractAmountFromMatch(match) ?: continue
                val merchant = extractMerchantFromMatch(match)
                val account = extractAccountFromBody(body)
                val balance = extractBalanceFromBody(body)
                val refNumber = extractReferenceNumber(body)

                return ParsedTransaction(
                    amount = amount,
                    type = TransactionType.CREDIT,
                    merchantName = cleanMerchantName(merchant),
                    bankName = matchedApp.appName,
                    accountLastFour = account,
                    balanceAfterTransaction = balance,
                    transactionMode = SmsBankPatterns.TransactionMode.UPI,
                    referenceNumber = refNumber,
                    timestamp = timestamp,
                    rawSmsBody = body,
                    senderId = senderId,
                    confidence = 0.90f
                )
            }
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GENERIC PARSING (FALLBACK)
    // ═══════════════════════════════════════════════════════════════════════════

    private fun tryGenericParse(
        body: String,
        senderId: String?,
        timestamp: Long
    ): ParsedTransaction? {
        val bodyLower = body.lowercase()

        // Determine transaction type from keywords
        val isDebit = SmsBankPatterns.DEBIT_KEYWORDS.any { bodyLower.contains(it) }
        val isCredit = SmsBankPatterns.CREDIT_KEYWORDS.any { bodyLower.contains(it) }

        // If both or neither, try pattern-based detection
        val transactionType = when {
            isDebit && !isCredit -> TransactionType.DEBIT
            isCredit && !isDebit -> TransactionType.CREDIT
            isDebit && isCredit -> determineTypeFromContext(body)
            else -> return null // Can't determine type
        } ?: return null

        // Try generic patterns based on type
        val patterns = when (transactionType) {
            TransactionType.DEBIT -> SmsBankPatterns.GenericPatterns.debitPatterns
            TransactionType.CREDIT -> SmsBankPatterns.GenericPatterns.creditPatterns
        }

        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                val amount = extractAmountFromGenericMatch(match) ?: continue
                val account = extractAccountFromBody(body)
                val merchant = extractMerchantFromGenericMatch(match) ?: extractMerchantFromBody(body)
                val balance = extractBalanceFromBody(body)
                val bankName = detectBankName(body, senderId)
                val mode = detectTransactionMode(body)
                val refNumber = extractReferenceNumber(body)

                return ParsedTransaction(
                    amount = amount,
                    type = transactionType,
                    merchantName = cleanMerchantName(merchant),
                    bankName = bankName,
                    accountLastFour = account,
                    balanceAfterTransaction = balance,
                    transactionMode = mode,
                    referenceNumber = refNumber,
                    timestamp = timestamp,
                    rawSmsBody = body,
                    senderId = senderId,
                    confidence = 0.75f
                )
            }
        }

        // Last resort: try to extract just the amount
        val amountMatch = Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(body)

        if (amountMatch != null) {
            val amount = parseAmount(amountMatch.groupValues[1]) ?: return null
            if (amount <= 0) return null

            val account = extractAccountFromBody(body)
            val merchant = extractMerchantFromBody(body)
            val balance = extractBalanceFromBody(body)
            val bankName = detectBankName(body, senderId)
            val mode = detectTransactionMode(body)

            return ParsedTransaction(
                amount = amount,
                type = transactionType,
                merchantName = cleanMerchantName(merchant),
                bankName = bankName,
                accountLastFour = account,
                balanceAfterTransaction = balance,
                transactionMode = mode,
                referenceNumber = null,
                timestamp = timestamp,
                rawSmsBody = body,
                senderId = senderId,
                confidence = 0.55f
            )
        }

        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - BANK/APP MATCHING
    // ═══════════════════════════════════════════════════════════════════════════

    private fun findMatchingBank(
        senderId: String?,
        body: String
    ): SmsBankPatterns.BankPattern? {
        // Match by sender ID first
        if (senderId != null) {
            val cleanSender = cleanSenderId(senderId)
            for (bank in SmsBankPatterns.bankPatterns) {
                if (bank.senderIds.any { cleanSender.contains(it, ignoreCase = true) }) {
                    return bank
                }
            }
        }

        // Match by bank name in body
        val bodyUpper = body.uppercase()
        for (bank in SmsBankPatterns.bankPatterns) {
            val bankNameClean = bank.bankName.uppercase()
            if (bodyUpper.contains(bankNameClean) ||
                bank.senderIds.any { bodyUpper.contains(it) }
            ) {
                return bank
            }
        }

        return null
    }

    private fun findMatchingUpiApp(
        senderId: String?,
        body: String
    ): SmsBankPatterns.UpiAppPattern? {
        if (senderId != null) {
            val cleanSender = cleanSenderId(senderId)
            for (app in SmsBankPatterns.upiAppPatterns) {
                if (app.senderIds.any { cleanSender.contains(it, ignoreCase = true) }) {
                    return app
                }
            }
        }

        // Match by app name in body
        val bodyUpper = body.uppercase()
        for (app in SmsBankPatterns.upiAppPatterns) {
            if (bodyUpper.contains(app.appName.uppercase()) ||
                app.senderIds.any { bodyUpper.contains(it) }
            ) {
                return app
            }
        }

        return null
    }

    private fun cleanSenderId(senderId: String): String {
        // Remove common prefixes like "AD-", "VM-", "BZ-", country codes
        return senderId
            .replace(Regex("""^(?:[A-Z]{2}-|[+]?\d{1,3}\s*)"""), "")
            .trim()
            .uppercase()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - EXTRACTION FROM NAMED GROUPS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractAmountFromMatch(match: MatchResult): Double? {
        // Try named group first
        val amountStr = try {
            match.groups["amount"]?.value
        } catch (e: Exception) {
            null
        }

        if (amountStr != null) {
            return parseAmount(amountStr)
        }

        // Fall back to first capturing group that looks like an amount
        for (i in 1..match.groupValues.lastIndex) {
            val groupVal = match.groupValues[i]
            if (groupVal.isNotEmpty() && groupVal.matches(Regex("""[0-9,]+(?:\.[0-9]{1,2})?"""))) {
                val amount = parseAmount(groupVal)
                if (amount != null && amount > 0) return amount
            }
        }

        return null
    }

    private fun extractAccountFromMatch(match: MatchResult): String? {
        return try {
            match.groups["account"]?.value
        } catch (e: Exception) {
            null
        }
    }

    private fun extractMerchantFromMatch(match: MatchResult): String? {
        return try {
            match.groups["merchant"]?.value
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAmountFromGenericMatch(match: MatchResult): Double? {
        for (i in 1..match.groupValues.lastIndex) {
            val groupVal = match.groupValues[i]
            if (groupVal.isNotEmpty() && groupVal.matches(Regex("""[0-9,]+(?:\.[0-9]{1,2})?"""))) {
                val amount = parseAmount(groupVal)
                if (amount != null && amount > 0) return amount
            }
        }
        return null
    }

    private fun extractMerchantFromGenericMatch(match: MatchResult): String? {
        // Look for a group that doesn't look like amount or account number
        for (i in 1..match.groupValues.lastIndex) {
            val groupVal = match.groupValues[i]
            if (groupVal.isNotEmpty() &&
                !groupVal.matches(Regex("""[0-9,]+(?:\.[0-9]{1,2})?""")) &&
                !groupVal.matches(Regex("""\d{4}"""))
            ) {
                return groupVal
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - EXTRACTION FROM FULL BODY
    // ═══════════════════════════════════════════════════════════════════════════

    private fun extractAccountFromBody(body: String): String? {
        for (pattern in SmsBankPatterns.GenericPatterns.accountPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractMerchantFromBody(body: String): String? {
        for (pattern in SmsBankPatterns.GenericPatterns.merchantPatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    private fun extractBalance(body: String, bankBalancePattern: Regex?): Double? {
        // Try bank-specific balance pattern first
        if (bankBalancePattern != null) {
            val match = bankBalancePattern.find(body)
            if (match != null) {
                for (i in 1..match.groupValues.lastIndex) {
                    val groupVal = match.groupValues[i]
                    if (groupVal.isNotEmpty()) {
                        return parseAmount(groupVal)
                    }
                }
            }
        }

        return extractBalanceFromBody(body)
    }

    private fun extractBalanceFromBody(body: String): Double? {
        for (pattern in SmsBankPatterns.GenericPatterns.balancePatterns) {
            val match = pattern.find(body)
            if (match != null && match.groupValues.size > 1) {
                val balStr = match.groupValues[1]
                if (balStr.isNotEmpty()) {
                    return parseAmount(balStr)
                }
            }
        }
        return null
    }

    private fun extractReferenceNumber(body: String): String? {
        // UPI Ref number
        val upiRef = Regex("""(?i)(?:UPI\s*(?:Ref|ref|ID|Txn\s*ID))[\s:.]*(\d{9,18})""").find(body)
        if (upiRef != null) return upiRef.groupValues[1]

        // NEFT/IMPS/RTGS reference
        val transferRef = Regex("""(?i)(?:NEFT|IMPS|RTGS)\s*(?:Ref|ref|ID)[\s:.]*([A-Z0-9]+)""").find(body)
        if (transferRef != null) return transferRef.groupValues[1]

        // Generic reference
        val genericRef = Regex("""(?i)(?:Ref|Reference|Txn)\s*(?:No|Number|ID|#)?[\s:.]*([A-Z0-9]{6,20})""").find(body)
        if (genericRef != null) return genericRef.groupValues[1]

        return null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun detectTransactionMode(body: String): SmsBankPatterns.TransactionMode {
        for ((mode, pattern) in SmsBankPatterns.transactionModePatterns) {
            if (pattern.containsMatchIn(body)) {
                return mode
            }
        }
        return SmsBankPatterns.TransactionMode.UNKNOWN
    }

    private fun detectBankName(body: String, senderId: String?): String? {
        // Try sender ID matching
        if (senderId != null) {
            val cleanSender = cleanSenderId(senderId)
            for (bank in SmsBankPatterns.bankPatterns) {
                if (bank.senderIds.any { cleanSender.contains(it, ignoreCase = true) }) {
                    return bank.bankName
                }
            }
            for (app in SmsBankPatterns.upiAppPatterns) {
                if (app.senderIds.any { cleanSender.contains(it, ignoreCase = true) }) {
                    return app.appName
                }
            }
        }

        // Try body content matching
        val bankNamePatterns = mapOf(
            "SBI" to Regex("""(?i)\b(?:SBI|State\s*Bank)\b"""),
            "HDFC Bank" to Regex("""(?i)\b(?:HDFC)\b"""),
            "ICICI Bank" to Regex("""(?i)\b(?:ICICI)\b"""),
            "Axis Bank" to Regex("""(?i)\b(?:Axis\s*Bank)\b"""),
            "Kotak Bank" to Regex("""(?i)\b(?:Kotak)\b"""),
            "PNB" to Regex("""(?i)\b(?:PNB|Punjab\s*National)\b"""),
            "Bank of Baroda" to Regex("""(?i)\b(?:BOB|Bank\s*of\s*Baroda)\b"""),
            "IDFC First Bank" to Regex("""(?i)\b(?:IDFC)\b"""),
            "Yes Bank" to Regex("""(?i)\b(?:Yes\s*Bank)\b"""),
            "IndusInd Bank" to Regex("""(?i)\b(?:IndusInd)\b"""),
            "Federal Bank" to Regex("""(?i)\b(?:Federal\s*Bank)\b"""),
            "RBL Bank" to Regex("""(?i)\b(?:RBL)\b"""),
            "Union Bank" to Regex("""(?i)\b(?:Union\s*Bank)\b"""),
            "Canara Bank" to Regex("""(?i)\b(?:Canara)\b"""),
            "Bank of India" to Regex("""(?i)\b(?:Bank\s*of\s*India|BOI)\b"""),
            "IDBI Bank" to Regex("""(?i)\b(?:IDBI)\b"""),
            "Bandhan Bank" to Regex("""(?i)\b(?:Bandhan)\b"""),
            "AU Small Finance Bank" to Regex("""(?i)\b(?:AU\s*(?:Small\s*Finance\s*)?Bank)\b"""),
            "Paytm Payments Bank" to Regex("""(?i)\b(?:Paytm\s*(?:Payments\s*)?Bank)\b""")
        )

        for ((name, pattern) in bankNamePatterns) {
            if (pattern.containsMatchIn(body)) {
                return name
            }
        }

        return null
    }

    private fun determineTypeFromContext(body: String): TransactionType? {
        val bodyLower = body.lowercase()

        // Check which keyword appears first - often the primary action
        var firstDebitIndex = Int.MAX_VALUE
        var firstCreditIndex = Int.MAX_VALUE

        for (keyword in SmsBankPatterns.DEBIT_KEYWORDS) {
            val idx = bodyLower.indexOf(keyword)
            if (idx in 0 until firstDebitIndex) {
                firstDebitIndex = idx
            }
        }

        for (keyword in SmsBankPatterns.CREDIT_KEYWORDS) {
            val idx = bodyLower.indexOf(keyword)
            if (idx in 0 until firstCreditIndex) {
                firstCreditIndex = idx
            }
        }

        return when {
            firstDebitIndex < firstCreditIndex -> TransactionType.DEBIT
            firstCreditIndex < firstDebitIndex -> TransactionType.CREDIT
            else -> null
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS - PARSING & CLEANING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse amount string to Double, handling Indian number formats.
     * Supports: "1,234.56", "1,23,456.78", "12345", "1234.5"
     */
    private fun parseAmount(amountStr: String): Double? {
        return try {
            val cleaned = amountStr.replace(",", "").trim()
            if (cleaned.isEmpty()) return null
            val amount = cleaned.toDouble()
            if (amount > 0 && amount < 100_000_000) amount else null // Sanity check: max 10 Cr
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Clean and normalize merchant name.
     * Removes trailing noise like dates, reference numbers, extra spaces.
     */
    private fun cleanMerchantName(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null

        var cleaned = merchant.trim()

        // Remove trailing date patterns
        cleaned = cleaned.replace(Regex("""\s*\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s*$"""), "")
        // Remove trailing reference numbers
        cleaned = cleaned.replace(Regex("""\s*(?:Ref|ref|ID)[\s:]*[A-Z0-9]+\s*$"""), "")
        // Remove trailing UPI-related text
        cleaned = cleaned.replace(Regex("""\s*(?:UPI|VPA|Txn)[\s:]*.*$""", RegexOption.IGNORE_CASE), "")
        // Remove trailing punctuation and whitespace
        cleaned = cleaned.replace(Regex("""[\s.,;:!?]+$"""), "")
        // Remove leading/trailing quotes
        cleaned = cleaned.replace(Regex("""^["']+|["']+$"""), "")
        // Remove "via *" suffix
        cleaned = cleaned.replace(Regex("""\s*via\s+.*$""", RegexOption.IGNORE_CASE), "")
        // Collapse multiple spaces
        cleaned = cleaned.replace(Regex("""\s+"""), " ")
        // Remove very long merchants (likely noise)
        if (cleaned.length > 60) {
            cleaned = cleaned.substring(0, 60).replace(Regex("""\s+\S*$"""), "")
        }

        return cleaned.trim().ifBlank { null }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BATCH PARSING UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Parse multiple SMS messages in batch.
     *
     * @param messages List of (body, senderId, timestamp) triples
     * @return List of successfully parsed transactions
     */
    fun parseBatch(messages: List<Triple<String, String?, Long>>): List<ParsedTransaction> {
        return messages.mapNotNull { (body, senderId, timestamp) ->
            try {
                parse(body, senderId, timestamp)
            } catch (e: Exception) {
                null // Skip messages that throw errors
            }
        }
    }

    /**
     * Parse messages with metadata about failure reasons (useful for debugging).
     */
    data class ParseAttempt(
        val body: String,
        val result: ParsedTransaction?,
        val failureReason: String?
    )

    fun parseWithDiagnostics(body: String, senderId: String? = null, timestamp: Long = System.currentTimeMillis()): ParseAttempt {
        if (body.isBlank()) {
            return ParseAttempt(body, null, "Empty SMS body")
        }

        if (!SmsBankPatterns.isLikelyFinancialSms(body, senderId)) {
            return ParseAttempt(body, null, "Not a financial SMS (failed pre-filter)")
        }

        val result = parse(body, senderId, timestamp)
        return if (result != null) {
            ParseAttempt(body, result, null)
        } else {
            ParseAttempt(body, null, "No patterns matched for transaction extraction")
        }
    }

    companion object {
        /** Singleton instance for convenience */
        val instance: SmsParser by lazy { SmsParser() }

        /** Date formatter for Indian date strings in SMS */
        private val dateFormats = listOf(
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yy", Locale.US),
            SimpleDateFormat("dd/MM/yy", Locale.US),
            SimpleDateFormat("dd-MMM-yyyy", Locale.US),
            SimpleDateFormat("dd-MMM-yy", Locale.US)
        )

        /**
         * Try to parse an Indian-format date string from SMS body.
         */
        fun parseDateFromSms(body: String): Date? {
            val datePattern = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}[/-][A-Za-z]{3}[/-]\d{2,4})\b""")
            val match = datePattern.find(body) ?: return null
            val dateStr = match.groupValues[1]

            for (format in dateFormats) {
                try {
                    format.isLenient = false
                    return format.parse(dateStr)
                } catch (e: Exception) {
                    continue
                }
            }
            return null
        }
    }
}
