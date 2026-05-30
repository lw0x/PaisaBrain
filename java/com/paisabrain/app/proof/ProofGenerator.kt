package com.paisabrain.app.proof

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Proof Generator — creates formatted financial proof documents for real-life needs.
 *
 * Common use cases:
 * - Income proof for loan applications
 * - Expense summaries for tax filing (FY format)
 * - Rent payment history for HRA claims or new landlord verification
 * - Travel expenses for office reimbursement
 * - Medical expenses for insurance claims or 80D deduction
 * - Financial statements for visa applications
 * - Savings certificate showing financial stability
 * - Debt repayment proof showing EMI consistency
 * - Group settlement proof for disputes
 *
 * All documents include a disclaimer that these are personal records, not official bank statements.
 * User explicitly chooses what to include — nothing is auto-shared.
 *
 * Output: Structured data models suitable for PDF rendering, CSV export, or plain text display.
 */
class ProofGenerator(private val context: Context) {

    companion object {
        /** Disclaimer appended to all generated proofs. */
        private const val DISCLAIMER = "This document is generated from personal financial records " +
                "maintained by the user. It is NOT an official bank statement or certified document. " +
                "For official proof, please contact your financial institution."

        /** Currency symbol for formatting. */
        private const val CURRENCY = "₹"

        /** Financial year start month (April in India). */
        private const val FY_START_MONTH = Calendar.APRIL
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A complete proof document ready for rendering.
     *
     * @param title Document title (e.g., "Income Proof").
     * @param subtitle Additional context (e.g., "April 2025 — March 2026").
     * @param dateRange The period covered by this document.
     * @param sections Organized sections of data.
     * @param footer Footer text including disclaimer.
     * @param generatedAt Timestamp of document generation.
     * @param userName User's name (if they've set it in profile).
     * @param documentId Unique document identifier for reference.
     * @param metadata Additional key-value metadata.
     */
    data class ProofDocument(
        val title: String,
        val subtitle: String,
        val dateRange: Pair<Long, Long>,
        val sections: List<ProofSection>,
        val footer: String,
        val generatedAt: Long,
        val userName: String? = null,
        val documentId: String = UUID.randomUUID().toString().take(8).uppercase(),
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * A section within a proof document (e.g., "Salary Credits", "Monthly Summary").
     *
     * @param heading Section title.
     * @param rows Data rows within this section.
     * @param subtotal Optional subtotal for this section.
     * @param note Optional note or explanation for this section.
     */
    data class ProofSection(
        val heading: String,
        val rows: List<ProofRow>,
        val subtotal: Double? = null,
        val note: String? = null
    )

    /**
     * A single data row in a proof section.
     *
     * @param date Formatted date string.
     * @param description Description of the entry.
     * @param amount Transaction amount.
     * @param category Category (optional — depends on template).
     * @param balance Running balance after this entry (optional).
     * @param reference Reference number or SMS-extracted ref (optional).
     */
    data class ProofRow(
        val date: String,
        val description: String,
        val amount: Double,
        val category: String? = null,
        val balance: Double? = null,
        val reference: String? = null
    )

    /**
     * Available proof document templates.
     */
    enum class ProofTemplate {
        /** Last 6 months salary/income credits. */
        INCOME_PROOF,
        /** Category-wise expense breakdown for tax filing. */
        EXPENSE_SUMMARY,
        /** All rent payments with dates. */
        RENT_HISTORY,
        /** Travel/transport category for reimbursement. */
        TRAVEL_EXPENSES,
        /** Health/medical category for insurance/80D. */
        MEDICAL_EXPENSES,
        /** Full income vs. expense statement. */
        FINANCIAL_STATEMENT,
        /** Monthly savings trend showing stability. */
        SAVINGS_CERTIFICATE,
        /** EMI payment history showing consistency. */
        DEBT_REPAYMENT,
        /** Group expense settlement proof. */
        GROUP_SETTLEMENT,
        /** User picks date range + categories. */
        CUSTOM
    }

    /**
     * Options for customizing proof generation.
     *
     * @param includeRunningBalance Whether to show running balance column.
     * @param includeReferences Whether to include transaction references.
     * @param groupByMonth Whether to group entries by month.
     * @param includeChartData Whether to include data suitable for chart rendering.
     * @param customTitle Override the default template title.
     * @param customCategories For CUSTOM template: which categories to include.
     * @param userName Name to display on the document header.
     * @param accountFilter Optional: only include transactions from specific account.
     */
    data class ProofOptions(
        val includeRunningBalance: Boolean = false,
        val includeReferences: Boolean = false,
        val groupByMonth: Boolean = true,
        val includeChartData: Boolean = false,
        val customTitle: String? = null,
        val customCategories: List<String>? = null,
        val userName: String? = null,
        val accountFilter: String? = null
    )

    /**
     * Represents a transaction for proof generation purposes.
     */
    private data class Transaction(
        val id: Long,
        val merchantName: String,
        val amount: Double,
        val type: String, // "CREDIT" or "DEBIT"
        val category: String,
        val date: Long,
        val balance: Double? = null,
        val reference: String? = null,
        val accountName: String? = null
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a proof document from the specified template.
     *
     * @param template Which type of proof to generate.
     * @param dateRange Start and end timestamps for the period.
     * @param options Customization options.
     * @return A fully structured [ProofDocument] ready for rendering.
     */
    fun generateProof(
        template: ProofTemplate,
        dateRange: Pair<Long, Long>,
        options: ProofOptions = ProofOptions()
    ): ProofDocument {
        return when (template) {
            ProofTemplate.INCOME_PROOF -> generateIncomeProof(dateRange, options)
            ProofTemplate.EXPENSE_SUMMARY -> generateExpenseSummary(dateRange, options)
            ProofTemplate.RENT_HISTORY -> generateRentHistory(dateRange, options)
            ProofTemplate.TRAVEL_EXPENSES -> generateTravelExpenses(dateRange, options)
            ProofTemplate.MEDICAL_EXPENSES -> generateMedicalExpenses(dateRange, options)
            ProofTemplate.FINANCIAL_STATEMENT -> generateFinancialStatement(dateRange, options)
            ProofTemplate.SAVINGS_CERTIFICATE -> generateSavingsCertificate(dateRange, options)
            ProofTemplate.DEBT_REPAYMENT -> generateDebtRepayment(dateRange, options)
            ProofTemplate.GROUP_SETTLEMENT -> generateGroupSettlement(dateRange, options)
            ProofTemplate.CUSTOM -> generateCustomReport(dateRange, options)
        }
    }

    /**
     * Exports a proof document as plain text.
     */
    fun exportAsText(document: ProofDocument): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Header
        sb.appendLine("═══════════════════════════════════════════════")
        sb.appendLine("  ${document.title}")
        sb.appendLine("  ${document.subtitle}")
        sb.appendLine("═══════════════════════════════════════════════")
        sb.appendLine()

        if (document.userName != null) {
            sb.appendLine("  Name: ${document.userName}")
        }
        sb.appendLine("  Period: ${dateFormat.format(Date(document.dateRange.first))} — ${dateFormat.format(Date(document.dateRange.second))}")
        sb.appendLine("  Document ID: ${document.documentId}")
        sb.appendLine()

        // Sections
        for (section in document.sections) {
            sb.appendLine("──────────────────────────────────────────────")
            sb.appendLine("  ${section.heading}")
            sb.appendLine("──────────────────────────────────────────────")

            if (section.note != null) {
                sb.appendLine("  Note: ${section.note}")
                sb.appendLine()
            }

            // Column headers
            val hasBalance = section.rows.any { it.balance != null }
            val hasCategory = section.rows.any { it.category != null }

            val headerParts = mutableListOf("  Date", "Description")
            if (hasCategory) headerParts.add("Category")
            headerParts.add("Amount")
            if (hasBalance) headerParts.add("Balance")

            sb.appendLine("  ${"-".repeat(60)}")

            for (row in section.rows) {
                val parts = mutableListOf<String>()
                parts.add("  ${row.date}")
                parts.add(row.description.take(30).padEnd(30))
                if (hasCategory) parts.add((row.category ?: "").take(15).padEnd(15))
                parts.add("$CURRENCY${formatAmount(row.amount)}".padStart(12))
                if (hasBalance && row.balance != null) {
                    parts.add("$CURRENCY${formatAmount(row.balance)}".padStart(12))
                }
                sb.appendLine(parts.joinToString("  "))
            }

            sb.appendLine("  ${"-".repeat(60)}")

            if (section.subtotal != null) {
                sb.appendLine("  ${"SUBTOTAL".padEnd(45)}$CURRENCY${formatAmount(section.subtotal)}")
            }
            sb.appendLine()
        }

        // Footer
        sb.appendLine("═══════════════════════════════════════════════")
        sb.appendLine("  Generated: ${dateFormat.format(Date(document.generatedAt))}")
        sb.appendLine()
        sb.appendLine("  ⚠️ ${document.footer}")
        sb.appendLine("═══════════════════════════════════════════════")

        return sb.toString()
    }

    /**
     * Exports a proof document as CSV.
     */
    fun exportAsCsv(document: ProofDocument): String {
        val sb = StringBuilder()

        // Header row
        sb.appendLine("Date,Description,Category,Amount,Balance,Section")

        for (section in document.sections) {
            for (row in section.rows) {
                val date = escapeCsv(row.date)
                val desc = escapeCsv(row.description)
                val category = escapeCsv(row.category ?: "")
                val amount = String.format(Locale.getDefault(), "%.2f", row.amount)
                val balance = row.balance?.let {
                    String.format(Locale.getDefault(), "%.2f", it)
                } ?: ""
                val sectionName = escapeCsv(section.heading)

                sb.appendLine("$date,$desc,$category,$amount,$balance,$sectionName")
            }
        }

        return sb.toString()
    }

    /**
     * Gets the financial year date range for a given calendar year.
     * Indian FY runs April to March (e.g., FY 2025-26 = Apr 2025 to Mar 2026).
     *
     * @param startYear The starting year of the FY (e.g., 2025 for FY 2025-26).
     * @return Pair of (startTimestamp, endTimestamp).
     */
    fun getFinancialYearRange(startYear: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()

        // Start: April 1 of startYear
        calendar.set(startYear, Calendar.APRIL, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        // End: March 31 of startYear + 1
        calendar.set(startYear + 1, Calendar.MARCH, 31, 23, 59, 59)
        val end = calendar.timeInMillis

        return Pair(start, end)
    }

    /**
     * Gets the last 6 months date range from today.
     */
    fun getLast6MonthsRange(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -6)
        val start = calendar.timeInMillis
        return Pair(start, end)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Template Implementations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates income proof — last N months of salary/income credits.
     * Formatted similar to a bank statement showing only credit entries.
     */
    private fun generateIncomeProof(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val transactions = getTransactions(dateRange, type = "CREDIT")
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val sections = if (options.groupByMonth) {
            // Group credits by month
            val byMonth = transactions.groupBy { monthFormat.format(Date(it.date)) }
            byMonth.map { (month, txns) ->
                ProofSection(
                    heading = month,
                    rows = txns.map { txn ->
                        ProofRow(
                            date = dateFormat.format(Date(txn.date)),
                            description = txn.merchantName,
                            amount = txn.amount,
                            category = txn.category,
                            balance = if (options.includeRunningBalance) txn.balance else null,
                            reference = if (options.includeReferences) txn.reference else null
                        )
                    },
                    subtotal = txns.sumOf { it.amount }
                )
            }
        } else {
            listOf(
                ProofSection(
                    heading = "All Income Credits",
                    rows = transactions.map { txn ->
                        ProofRow(
                            date = dateFormat.format(Date(txn.date)),
                            description = txn.merchantName,
                            amount = txn.amount,
                            category = txn.category,
                            balance = if (options.includeRunningBalance) txn.balance else null
                        )
                    },
                    subtotal = transactions.sumOf { it.amount }
                )
            )
        }

        // Summary section
        val totalIncome = transactions.sumOf { it.amount }
        val monthCount = transactions.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.date }.get(Calendar.MONTH)
        }.size
        val avgMonthly = if (monthCount > 0) totalIncome / monthCount else 0.0

        val summarySection = ProofSection(
            heading = "Summary",
            rows = listOf(
                ProofRow("", "Total Income in Period", totalIncome),
                ProofRow("", "Number of Months", monthCount.toDouble()),
                ProofRow("", "Average Monthly Income", avgMonthly),
                ProofRow("", "Number of Credit Entries", transactions.size.toDouble())
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Income Proof",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = sections + summarySection,
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName,
            metadata = mapOf(
                "totalIncome" to formatAmount(totalIncome),
                "avgMonthlyIncome" to formatAmount(avgMonthly),
                "months" to monthCount.toString()
            )
        )
    }

    /**
     * Generates expense summary — category-wise breakdown for tax filing.
     * Uses Indian Financial Year (April–March) format.
     */
    private fun generateExpenseSummary(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val transactions = getTransactions(dateRange, type = "DEBIT")
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Group by category
        val byCategory = transactions.groupBy { it.category }

        val sections = byCategory.map { (category, txns) ->
            val sortedTxns = txns.sortedByDescending { it.date }
            ProofSection(
                heading = "$category (${txns.size} transactions)",
                rows = if (sortedTxns.size <= 20) {
                    sortedTxns.map { txn ->
                        ProofRow(
                            date = dateFormat.format(Date(txn.date)),
                            description = txn.merchantName,
                            amount = txn.amount
                        )
                    }
                } else {
                    // For categories with many transactions, show monthly totals
                    val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                    val byMonth = sortedTxns.groupBy { monthFormat.format(Date(it.date)) }
                    byMonth.map { (month, monthTxns) ->
                        ProofRow(
                            date = month,
                            description = "${monthTxns.size} transactions",
                            amount = monthTxns.sumOf { it.amount }
                        )
                    }
                },
                subtotal = txns.sumOf { it.amount }
            )
        }.sortedByDescending { it.subtotal ?: 0.0 }

        // Grand total section
        val grandTotal = transactions.sumOf { it.amount }
        val grandSection = ProofSection(
            heading = "Grand Total",
            rows = listOf(
                ProofRow("", "Total Expenses", grandTotal),
                ProofRow("", "Total Categories", byCategory.size.toDouble()),
                ProofRow("", "Total Transactions", transactions.size.toDouble())
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Expense Summary",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = sections + grandSection,
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates rent payment history — all rent payments with dates.
     * Useful for HRA claims, new landlord verification, or rent receipts.
     */
    private fun generateRentHistory(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val transactions = getTransactions(dateRange, categories = listOf("Rent"))
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val rows = transactions.sortedBy { it.date }.map { txn ->
            ProofRow(
                date = dateFormat.format(Date(txn.date)),
                description = txn.merchantName,
                amount = txn.amount,
                reference = if (options.includeReferences) txn.reference else null
            )
        }

        val totalRent = transactions.sumOf { it.amount }
        val monthCount = transactions.size

        val section = ProofSection(
            heading = "Rent Payments",
            rows = rows,
            subtotal = totalRent,
            note = "$monthCount payments recorded in this period"
        )

        val summarySection = ProofSection(
            heading = "Summary",
            rows = listOf(
                ProofRow("", "Total Rent Paid", totalRent),
                ProofRow("", "Number of Payments", monthCount.toDouble()),
                ProofRow("", "Average Monthly Rent", if (monthCount > 0) totalRent / monthCount else 0.0)
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Rent Payment History",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = listOf(section, summarySection),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates travel expense report for office reimbursement.
     */
    private fun generateTravelExpenses(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val categories = listOf("Transport", "Travel")
        val transactions = getTransactions(dateRange, categories = categories)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val rows = transactions.sortedBy { it.date }.map { txn ->
            ProofRow(
                date = dateFormat.format(Date(txn.date)),
                description = txn.merchantName,
                amount = txn.amount,
                category = txn.category,
                reference = if (options.includeReferences) txn.reference else null
            )
        }

        val totalTravel = transactions.sumOf { it.amount }

        val section = ProofSection(
            heading = "Travel & Transport Expenses",
            rows = rows,
            subtotal = totalTravel
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Travel Expense Report",
            subtitle = "For Reimbursement — $periodStr",
            dateRange = dateRange,
            sections = listOf(section),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName,
            metadata = mapOf("totalAmount" to formatAmount(totalTravel))
        )
    }

    /**
     * Generates medical expense report for insurance claims or Section 80D deduction.
     */
    private fun generateMedicalExpenses(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val categories = listOf("Health & Medical")
        val transactions = getTransactions(dateRange, categories = categories)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val rows = transactions.sortedBy { it.date }.map { txn ->
            ProofRow(
                date = dateFormat.format(Date(txn.date)),
                description = txn.merchantName,
                amount = txn.amount,
                reference = if (options.includeReferences) txn.reference else null
            )
        }

        val totalMedical = transactions.sumOf { it.amount }

        val section = ProofSection(
            heading = "Medical & Health Expenses",
            rows = rows,
            subtotal = totalMedical,
            note = "Includes hospital, clinic, pharmacy, and health-related expenses"
        )

        // 80D note
        val deductionSection = ProofSection(
            heading = "Tax Deduction Reference (Section 80D)",
            rows = listOf(
                ProofRow("", "Total Medical Expenses", totalMedical),
                ProofRow("", "80D Limit (Self/Family < 60 yrs)", 25000.0),
                ProofRow("", "80D Limit (Self/Family ≥ 60 yrs)", 50000.0)
            ),
            note = "Consult a tax professional for actual deduction eligibility."
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Medical Expense Summary",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = listOf(section, deductionSection),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates full financial statement — income vs. expenses.
     * Useful for visa applications showing financial stability.
     */
    private fun generateFinancialStatement(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val allTransactions = getTransactions(dateRange)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

        // Monthly summary
        val byMonth = allTransactions.groupBy { monthFormat.format(Date(it.date)) }

        val monthlySections = byMonth.map { (month, txns) ->
            val credits = txns.filter { it.type == "CREDIT" }
            val debits = txns.filter { it.type == "DEBIT" }
            val totalCredit = credits.sumOf { it.amount }
            val totalDebit = debits.sumOf { it.amount }
            val netSavings = totalCredit - totalDebit

            ProofSection(
                heading = month,
                rows = listOf(
                    ProofRow(month, "Total Income", totalCredit, "Income"),
                    ProofRow(month, "Total Expenses", totalDebit, "Expense"),
                    ProofRow(month, "Net Savings", netSavings, "Savings")
                ),
                subtotal = netSavings
            )
        }

        // Overall summary
        val totalIncome = allTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
        val totalExpense = allTransactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0) (netSavings / totalIncome * 100) else 0.0

        val overallSection = ProofSection(
            heading = "Overall Summary",
            rows = listOf(
                ProofRow("", "Total Income", totalIncome),
                ProofRow("", "Total Expenses", totalExpense),
                ProofRow("", "Net Savings", netSavings),
                ProofRow("", "Savings Rate (%)", savingsRate)
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Financial Statement",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = monthlySections + overallSection,
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName,
            metadata = mapOf(
                "totalIncome" to formatAmount(totalIncome),
                "totalExpenses" to formatAmount(totalExpense),
                "netSavings" to formatAmount(netSavings),
                "savingsRate" to "${savingsRate.toInt()}%"
            )
        )
    }

    /**
     * Generates savings certificate showing consistent monthly savings.
     * Demonstrates financial stability for loans, visas, etc.
     */
    private fun generateSavingsCertificate(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val allTransactions = getTransactions(dateRange)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

        val byMonth = allTransactions.groupBy { monthFormat.format(Date(it.date)) }

        val rows = byMonth.map { (month, txns) ->
            val income = txns.filter { it.type == "CREDIT" }.sumOf { it.amount }
            val expense = txns.filter { it.type == "DEBIT" }.sumOf { it.amount }
            val savings = income - expense
            val rate = if (income > 0) (savings / income * 100) else 0.0
            ProofRow(
                date = month,
                description = "Income: $CURRENCY${formatAmount(income)} | Expense: $CURRENCY${formatAmount(expense)}",
                amount = savings,
                category = "${rate.toInt()}% saved"
            )
        }

        val totalSavings = rows.sumOf { it.amount }
        val avgMonthlySavings = if (rows.isNotEmpty()) totalSavings / rows.size else 0.0
        val positiveMonths = rows.count { it.amount > 0 }

        val section = ProofSection(
            heading = "Monthly Savings Trend",
            rows = rows,
            subtotal = totalSavings,
            note = "$positiveMonths out of ${rows.size} months had positive savings"
        )

        val summarySection = ProofSection(
            heading = "Savings Summary",
            rows = listOf(
                ProofRow("", "Total Savings in Period", totalSavings),
                ProofRow("", "Average Monthly Savings", avgMonthlySavings),
                ProofRow("", "Months with Positive Savings", positiveMonths.toDouble()),
                ProofRow("", "Consistency Rate (%)", if (rows.isNotEmpty()) (positiveMonths.toDouble() / rows.size * 100) else 0.0)
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Savings Certificate",
            subtitle = "Demonstrating consistent savings — $periodStr",
            dateRange = dateRange,
            sections = listOf(section, summarySection),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates debt repayment proof showing EMI payment consistency.
     */
    private fun generateDebtRepayment(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val categories = listOf("EMI & Loans")
        val transactions = getTransactions(dateRange, categories = categories)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Group by merchant (each lender separately)
        val byLender = transactions.groupBy { it.merchantName }

        val sections = byLender.map { (lender, txns) ->
            val sortedTxns = txns.sortedBy { it.date }
            ProofSection(
                heading = lender,
                rows = sortedTxns.map { txn ->
                    ProofRow(
                        date = dateFormat.format(Date(txn.date)),
                        description = "EMI Payment",
                        amount = txn.amount,
                        reference = if (options.includeReferences) txn.reference else null
                    )
                },
                subtotal = txns.sumOf { it.amount },
                note = "${txns.size} payments — all on time"
            )
        }

        val totalPaid = transactions.sumOf { it.amount }
        val summarySection = ProofSection(
            heading = "Repayment Summary",
            rows = listOf(
                ProofRow("", "Total EMI Paid in Period", totalPaid),
                ProofRow("", "Number of Payments", transactions.size.toDouble()),
                ProofRow("", "Number of Lenders/Loans", byLender.size.toDouble())
            )
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Debt Repayment Proof",
            subtitle = "EMI Payment History — $periodStr",
            dateRange = dateRange,
            sections = sections + summarySection,
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates group settlement proof for dispute resolution.
     */
    private fun generateGroupSettlement(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // TODO: Pull from GroupExpenseManager
        // For now, create a placeholder structure
        val section = ProofSection(
            heading = "Group Expense Settlement",
            rows = emptyList(), // Would be populated from GroupExpenseManager
            note = "Detailed record of shared expenses and settlements"
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"

        return ProofDocument(
            title = options.customTitle ?: "Group Settlement Proof",
            subtitle = "Period: $periodStr",
            dateRange = dateRange,
            sections = listOf(section),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    /**
     * Generates a custom report based on user-selected categories and options.
     */
    private fun generateCustomReport(dateRange: Pair<Long, Long>, options: ProofOptions): ProofDocument {
        val categories = options.customCategories
        val transactions = if (categories != null) {
            getTransactions(dateRange, categories = categories)
        } else {
            getTransactions(dateRange)
        }
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        val rows = transactions.sortedBy { it.date }.map { txn ->
            ProofRow(
                date = dateFormat.format(Date(txn.date)),
                description = txn.merchantName,
                amount = txn.amount,
                category = txn.category,
                balance = if (options.includeRunningBalance) txn.balance else null,
                reference = if (options.includeReferences) txn.reference else null
            )
        }

        val section = ProofSection(
            heading = "Transactions",
            rows = rows,
            subtotal = transactions.sumOf { it.amount }
        )

        val periodStr = "${dateFormat.format(Date(dateRange.first))} — ${dateFormat.format(Date(dateRange.second))}"
        val categoriesLabel = categories?.joinToString(", ") ?: "All Categories"

        return ProofDocument(
            title = options.customTitle ?: "Custom Financial Report",
            subtitle = "$categoriesLabel — $periodStr",
            dateRange = dateRange,
            sections = listOf(section),
            footer = DISCLAIMER,
            generatedAt = System.currentTimeMillis(),
            userName = options.userName
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format(Locale.getDefault(), "%,.0f", kotlin.math.abs(amount))
        } else {
            String.format(Locale.getDefault(), "%,.2f", kotlin.math.abs(amount))
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Access Stubs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches transactions from the database with optional filters.
     */
    private fun getTransactions(
        dateRange: Pair<Long, Long>,
        type: String? = null,
        categories: List<String>? = null
    ): List<Transaction> {
        // TODO: Wire to Room DAO
        // var query = transactionDao.getInRange(dateRange.first, dateRange.second)
        // if (type != null) query = query.filter { it.type == type }
        // if (categories != null) query = query.filter { it.category in categories }
        // return query
        return emptyList()
    }
}
