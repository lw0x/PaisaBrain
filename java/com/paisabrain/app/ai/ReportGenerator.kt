package com.paisabrain.app.ai

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Generates comprehensive monthly financial reports in structured formats
 * ready for PDF rendering or CSV export.
 *
 * Produces data models with sections, rows, charts data, and insights
 * that can be directly fed into a PDF renderer or exported as CSV.
 * All merchant/service references are genericized (e.g., "Food Delivery #1").
 */
class ReportGenerator {

    // ─────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Complete monthly financial report.
     *
     * @property id Unique report identifier.
     * @property monthYear The month this report covers (e.g., "2024-03").
     * @property generatedDate When the report was generated.
     * @property summary High-level financial summary.
     * @property sections Detailed report sections.
     * @property insights Top observations and actionable insights.
     * @property scores Financial health and privacy scores.
     * @property comparisonWithPreviousMonth Month-over-month changes.
     */
    data class MonthlyReport(
        val id: String,
        val monthYear: String,
        val generatedDate: LocalDate = LocalDate.now(),
        val summary: FinancialSummary,
        val sections: List<ReportSection>,
        val insights: List<ReportInsight>,
        val scores: ReportScores,
        val comparisonWithPreviousMonth: MonthComparison?
    )

    /**
     * High-level financial summary for the month.
     *
     * @property totalIncome Total income received.
     * @property totalExpenses Total amount spent.
     * @property netSavings Income minus expenses.
     * @property savingsRate Savings as percentage of income.
     * @property totalTransactions Number of transactions.
     * @property averageDailySpend Average daily spending.
     * @property highestSpendDay Day with maximum spending.
     * @property lowestSpendDay Day with minimum spending.
     */
    data class FinancialSummary(
        val totalIncome: Double,
        val totalExpenses: Double,
        val netSavings: Double,
        val savingsRate: Double, // percentage
        val totalTransactions: Int,
        val averageDailySpend: Double,
        val highestSpendDay: DaySpend?,
        val lowestSpendDay: DaySpend?
    )

    /**
     * A day's spending total.
     */
    data class DaySpend(
        val date: LocalDate,
        val amount: Double
    )

    /**
     * A section within the report (e.g., "Category Breakdown", "Top Merchants").
     *
     * @property id Section identifier.
     * @property title Section title for display.
     * @property subtitle Optional subtitle or description.
     * @property sectionType Type of content in this section.
     * @property rows Data rows within the section.
     * @property chartData Optional chart data for visualization.
     * @property footer Optional footer text or note.
     */
    data class ReportSection(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val sectionType: SectionType,
        val rows: List<ReportRow>,
        val chartData: ChartData? = null,
        val footer: String? = null
    )

    /**
     * A single data row within a section.
     *
     * @property label Row label (e.g., category name, merchant name).
     * @property value Primary value (formatted string).
     * @property numericValue Raw numeric value for calculations.
     * @property percentage Optional percentage value.
     * @property secondaryValue Optional secondary value (e.g., transaction count).
     * @property trend Optional trend indicator (UP, DOWN, FLAT).
     * @property highlighted Whether this row should be visually highlighted.
     */
    data class ReportRow(
        val label: String,
        val value: String,
        val numericValue: Double = 0.0,
        val percentage: Double? = null,
        val secondaryValue: String? = null,
        val trend: TrendDirection? = null,
        val highlighted: Boolean = false
    )

    /**
     * Chart data for visual rendering.
     *
     * @property chartType Type of chart to render.
     * @property labels Category/axis labels.
     * @property values Corresponding numeric values.
     * @property colors Optional color assignments.
     */
    data class ChartData(
        val chartType: ChartType,
        val labels: List<String>,
        val values: List<Double>,
        val colors: List<String>? = null
    )

    /**
     * An insight or observation from the month's data.
     *
     * @property title Short insight headline.
     * @property description Detailed explanation.
     * @property type Insight sentiment (positive, warning, neutral).
     * @property relatedCategory Optional related spending category.
     */
    data class ReportInsight(
        val title: String,
        val description: String,
        val type: InsightType,
        val relatedCategory: String? = null
    )

    /**
     * Financial health and related scores.
     *
     * @property financialHealthScore Overall financial health (0-100).
     * @property budgetAdherenceScore How well budgets were followed (0-100).
     * @property savingsScore Savings behavior score (0-100).
     * @property investmentScore Investment consistency score (0-100).
     * @property privacyScore Data privacy health (0-100).
     * @property overallGrade Letter grade (A+, A, B+, B, C, D, F).
     */
    data class ReportScores(
        val financialHealthScore: Int,
        val budgetAdherenceScore: Int,
        val savingsScore: Int,
        val investmentScore: Int,
        val privacyScore: Int,
        val overallGrade: String
    )

    /**
     * Month-over-month comparison data.
     *
     * @property previousMonthYear Previous month identifier.
     * @property incomeChange Change in income (positive = increase).
     * @property expenseChange Change in expenses.
     * @property savingsChange Change in savings.
     * @property savingsRateChange Change in savings rate (percentage points).
     * @property categoryChanges Per-category spending changes.
     */
    data class MonthComparison(
        val previousMonthYear: String,
        val incomeChange: Double,
        val incomeChangePercent: Double,
        val expenseChange: Double,
        val expenseChangePercent: Double,
        val savingsChange: Double,
        val savingsRateChange: Double, // percentage points
        val categoryChanges: List<CategoryChange>
    )

    /**
     * Change in a single category from previous month.
     */
    data class CategoryChange(
        val category: String,
        val previousAmount: Double,
        val currentAmount: Double,
        val changeAmount: Double,
        val changePercent: Double,
        val trend: TrendDirection
    )

    /**
     * Net worth snapshot for the report.
     */
    data class NetWorthSnapshot(
        val totalAssets: Double,
        val totalLiabilities: Double,
        val netWorth: Double,
        val changeFromLastMonth: Double
    )

    /**
     * Debt payoff progress.
     */
    data class DebtProgress(
        val totalDebt: Double,
        val paidThisMonth: Double,
        val remainingDebt: Double,
        val estimatedPayoffDate: LocalDate?,
        val debtFreeProgress: Double // percentage
    )

    /**
     * Transaction record for CSV export.
     */
    data class TransactionExportRow(
        val date: LocalDate,
        val description: String,
        val category: String,
        val amount: Double,
        val type: String, // "Credit" or "Debit"
        val balance: Double?,
        val assignedTo: String? // Family member name if assigned
    )

    // ─────────────────────────────────────────────────────────────────────
    // Enums
    // ─────────────────────────────────────────────────────────────────────

    enum class SectionType {
        SUMMARY,
        CATEGORY_BREAKDOWN,
        TOP_MERCHANTS,
        BUDGET_ADHERENCE,
        NET_WORTH,
        INVESTMENTS,
        DEBT_PAYOFF,
        COMPARISON,
        INSIGHTS,
        PRIVACY,
        SCORES
    }

    enum class ChartType {
        PIE,
        BAR,
        LINE,
        HORIZONTAL_BAR,
        DONUT
    }

    enum class TrendDirection {
        UP,
        DOWN,
        FLAT
    }

    enum class InsightType {
        POSITIVE,
        WARNING,
        NEUTRAL,
        ALERT
    }

    // ─────────────────────────────────────────────────────────────────────
    // Report Generation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates a complete monthly report from provided data.
     *
     * @param monthYear Month to generate report for (format: "2024-03").
     * @param income Total income for the month.
     * @param expenses Total expenses for the month.
     * @param transactionCount Number of transactions.
     * @param categoryBreakdown Map of category → amount spent.
     * @param topMerchants Top merchants/services by spend (genericized names).
     * @param budgetAllocations Map of category → allocated budget.
     * @param netWorth Current net worth snapshot.
     * @param investmentGrowth Investment value change this month.
     * @param debtProgress Debt repayment progress.
     * @param previousMonthSummary Previous month data for comparison (nullable).
     * @param privacyScore Current privacy health score.
     * @param dailySpending Map of day → total spent.
     * @return Complete MonthlyReport ready for rendering.
     */
    fun generateMonthlyReport(
        monthYear: String,
        income: Double,
        expenses: Double,
        transactionCount: Int,
        categoryBreakdown: Map<String, Double>,
        topMerchants: List<Pair<String, Double>>,
        budgetAllocations: Map<String, Double>,
        netWorth: NetWorthSnapshot,
        investmentGrowth: Double,
        debtProgress: DebtProgress?,
        previousMonthSummary: FinancialSummary? = null,
        privacyScore: Int = 100,
        dailySpending: Map<LocalDate, Double> = emptyMap()
    ): MonthlyReport {
        val savings = income - expenses
        val savingsRate = if (income > 0) (savings / income) * 100 else 0.0
        val daysInMonth = YearMonth.parse(monthYear).lengthOfMonth()
        val avgDaily = expenses / daysInMonth

        val highestDay = dailySpending.maxByOrNull { it.value }?.let {
            DaySpend(it.key, it.value)
        }
        val lowestDay = dailySpending.filter { it.value > 0 }.minByOrNull { it.value }?.let {
            DaySpend(it.key, it.value)
        }

        val summary = FinancialSummary(
            totalIncome = income,
            totalExpenses = expenses,
            netSavings = savings,
            savingsRate = savingsRate,
            totalTransactions = transactionCount,
            averageDailySpend = avgDaily,
            highestSpendDay = highestDay,
            lowestSpendDay = lowestDay
        )

        val sections = buildSections(
            summary = summary,
            categoryBreakdown = categoryBreakdown,
            topMerchants = topMerchants,
            budgetAllocations = budgetAllocations,
            netWorth = netWorth,
            investmentGrowth = investmentGrowth,
            debtProgress = debtProgress,
            privacyScore = privacyScore
        )

        val insights = generateInsights(
            summary = summary,
            categoryBreakdown = categoryBreakdown,
            budgetAllocations = budgetAllocations,
            previousMonth = previousMonthSummary
        )

        val scores = calculateScores(
            summary = summary,
            budgetAllocations = budgetAllocations,
            categoryBreakdown = categoryBreakdown,
            investmentGrowth = investmentGrowth,
            privacyScore = privacyScore
        )

        val comparison = previousMonthSummary?.let {
            buildComparison(monthYear, summary, it, categoryBreakdown)
        }

        return MonthlyReport(
            id = "report_$monthYear",
            monthYear = monthYear,
            summary = summary,
            sections = sections,
            insights = insights,
            scores = scores,
            comparisonWithPreviousMonth = comparison
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Section Builders
    // ─────────────────────────────────────────────────────────────────────

    private fun buildSections(
        summary: FinancialSummary,
        categoryBreakdown: Map<String, Double>,
        topMerchants: List<Pair<String, Double>>,
        budgetAllocations: Map<String, Double>,
        netWorth: NetWorthSnapshot,
        investmentGrowth: Double,
        debtProgress: DebtProgress?,
        privacyScore: Int
    ): List<ReportSection> {
        val sections = mutableListOf<ReportSection>()

        // 1. Summary Section
        sections.add(buildSummarySection(summary))

        // 2. Category Breakdown
        sections.add(buildCategorySection(categoryBreakdown, summary.totalExpenses))

        // 3. Top Merchants (genericized)
        sections.add(buildTopMerchantsSection(topMerchants))

        // 4. Budget Adherence
        sections.add(buildBudgetAdherenceSection(categoryBreakdown, budgetAllocations))

        // 5. Net Worth
        sections.add(buildNetWorthSection(netWorth))

        // 6. Investments
        sections.add(buildInvestmentSection(investmentGrowth))

        // 7. Debt Payoff
        if (debtProgress != null) {
            sections.add(buildDebtSection(debtProgress))
        }

        // 8. Privacy Score
        sections.add(buildPrivacySection(privacyScore))

        return sections
    }

    private fun buildSummarySection(summary: FinancialSummary): ReportSection {
        return ReportSection(
            id = "summary",
            title = "Monthly Summary",
            subtitle = "Your financial overview at a glance",
            sectionType = SectionType.SUMMARY,
            rows = listOf(
                ReportRow("Total Income", "₹${formatAmount(summary.totalIncome)}", summary.totalIncome),
                ReportRow("Total Expenses", "₹${formatAmount(summary.totalExpenses)}", summary.totalExpenses),
                ReportRow(
                    "Net Savings", "₹${formatAmount(summary.netSavings)}", summary.netSavings,
                    highlighted = summary.netSavings > 0
                ),
                ReportRow("Savings Rate", "${String.format("%.1f", summary.savingsRate)}%", summary.savingsRate),
                ReportRow("Transactions", "${summary.totalTransactions}", summary.totalTransactions.toDouble()),
                ReportRow("Avg. Daily Spend", "₹${formatAmount(summary.averageDailySpend)}", summary.averageDailySpend)
            )
        )
    }

    private fun buildCategorySection(
        categoryBreakdown: Map<String, Double>,
        totalExpenses: Double
    ): ReportSection {
        val sortedCategories = categoryBreakdown.entries.sortedByDescending { it.value }
        val rows = sortedCategories.map { (category, amount) ->
            val percentage = if (totalExpenses > 0) (amount / totalExpenses) * 100 else 0.0
            ReportRow(
                label = category,
                value = "₹${formatAmount(amount)}",
                numericValue = amount,
                percentage = percentage,
                secondaryValue = "${String.format("%.1f", percentage)}%"
            )
        }

        val chartData = ChartData(
            chartType = ChartType.PIE,
            labels = sortedCategories.map { it.key },
            values = sortedCategories.map { it.value },
            colors = generateCategoryColors(sortedCategories.size)
        )

        return ReportSection(
            id = "categories",
            title = "Category Breakdown",
            subtitle = "Where your money went",
            sectionType = SectionType.CATEGORY_BREAKDOWN,
            rows = rows,
            chartData = chartData
        )
    }

    private fun buildTopMerchantsSection(
        topMerchants: List<Pair<String, Double>>
    ): ReportSection {
        val rows = topMerchants.take(5).mapIndexed { index, (merchant, amount) ->
            ReportRow(
                label = "#${index + 1} $merchant",
                value = "₹${formatAmount(amount)}",
                numericValue = amount
            )
        }

        return ReportSection(
            id = "top_merchants",
            title = "Top 5 Spending Destinations",
            subtitle = "Your most frequented services",
            sectionType = SectionType.TOP_MERCHANTS,
            rows = rows
        )
    }

    private fun buildBudgetAdherenceSection(
        categoryBreakdown: Map<String, Double>,
        budgetAllocations: Map<String, Double>
    ): ReportSection {
        val rows = budgetAllocations.map { (category, budget) ->
            val actual = categoryBreakdown[category] ?: 0.0
            val adherence = if (budget > 0) (actual / budget) * 100 else 0.0
            val trend = when {
                adherence <= 80 -> TrendDirection.DOWN // Under budget (good)
                adherence <= 100 -> TrendDirection.FLAT // On budget
                else -> TrendDirection.UP // Over budget (bad)
            }

            ReportRow(
                label = category,
                value = "₹${formatAmount(actual)} / ₹${formatAmount(budget)}",
                numericValue = adherence,
                percentage = adherence,
                secondaryValue = "${String.format("%.0f", adherence)}% used",
                trend = trend,
                highlighted = adherence > 100
            )
        }.sortedByDescending { it.numericValue }

        return ReportSection(
            id = "budget_adherence",
            title = "Budget Adherence",
            subtitle = "How well you stuck to your budgets",
            sectionType = SectionType.BUDGET_ADHERENCE,
            rows = rows
        )
    }

    private fun buildNetWorthSection(netWorth: NetWorthSnapshot): ReportSection {
        val changeLabel = if (netWorth.changeFromLastMonth >= 0) "+" else ""
        return ReportSection(
            id = "net_worth",
            title = "Net Worth",
            subtitle = "Your overall financial position",
            sectionType = SectionType.NET_WORTH,
            rows = listOf(
                ReportRow("Total Assets", "₹${formatAmount(netWorth.totalAssets)}", netWorth.totalAssets),
                ReportRow("Total Liabilities", "₹${formatAmount(netWorth.totalLiabilities)}", netWorth.totalLiabilities),
                ReportRow(
                    "Net Worth", "₹${formatAmount(netWorth.netWorth)}", netWorth.netWorth,
                    highlighted = true
                ),
                ReportRow(
                    "Change This Month",
                    "${changeLabel}₹${formatAmount(netWorth.changeFromLastMonth)}",
                    netWorth.changeFromLastMonth,
                    trend = if (netWorth.changeFromLastMonth >= 0) TrendDirection.UP else TrendDirection.DOWN
                )
            )
        )
    }

    private fun buildInvestmentSection(investmentGrowth: Double): ReportSection {
        val growthLabel = if (investmentGrowth >= 0) "+" else ""
        return ReportSection(
            id = "investments",
            title = "Investment Growth",
            subtitle = "Portfolio performance this month",
            sectionType = SectionType.INVESTMENTS,
            rows = listOf(
                ReportRow(
                    "Portfolio Growth",
                    "${growthLabel}₹${formatAmount(investmentGrowth)}",
                    investmentGrowth,
                    trend = if (investmentGrowth >= 0) TrendDirection.UP else TrendDirection.DOWN
                )
            )
        )
    }

    private fun buildDebtSection(debtProgress: DebtProgress): ReportSection {
        val rows = mutableListOf(
            ReportRow("Total Debt", "₹${formatAmount(debtProgress.totalDebt)}", debtProgress.totalDebt),
            ReportRow("Paid This Month", "₹${formatAmount(debtProgress.paidThisMonth)}", debtProgress.paidThisMonth),
            ReportRow("Remaining", "₹${formatAmount(debtProgress.remainingDebt)}", debtProgress.remainingDebt),
            ReportRow(
                "Progress",
                "${String.format("%.1f", debtProgress.debtFreeProgress)}% debt-free",
                debtProgress.debtFreeProgress,
                percentage = debtProgress.debtFreeProgress
            )
        )

        debtProgress.estimatedPayoffDate?.let { date ->
            rows.add(ReportRow(
                "Estimated Payoff",
                date.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                0.0
            ))
        }

        return ReportSection(
            id = "debt",
            title = "Debt Payoff Progress",
            subtitle = "Your journey to becoming debt-free",
            sectionType = SectionType.DEBT_PAYOFF,
            rows = rows
        )
    }

    private fun buildPrivacySection(privacyScore: Int): ReportSection {
        val grade = when {
            privacyScore >= 90 -> "Excellent"
            privacyScore >= 75 -> "Good"
            privacyScore >= 50 -> "Fair"
            else -> "Needs Attention"
        }

        return ReportSection(
            id = "privacy",
            title = "Privacy Score",
            subtitle = "Your data privacy health",
            sectionType = SectionType.PRIVACY,
            rows = listOf(
                ReportRow(
                    "Privacy Score", "$privacyScore/100", privacyScore.toDouble(),
                    percentage = privacyScore.toDouble()
                ),
                ReportRow("Grade", grade, 0.0)
            ),
            footer = "All data processed locally on your device. No data shared with third parties."
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Insights Generation
    // ─────────────────────────────────────────────────────────────────────

    private fun generateInsights(
        summary: FinancialSummary,
        categoryBreakdown: Map<String, Double>,
        budgetAllocations: Map<String, Double>,
        previousMonth: FinancialSummary?
    ): List<ReportInsight> {
        val insights = mutableListOf<ReportInsight>()

        // Insight 1: Savings rate assessment
        insights.add(
            when {
                summary.savingsRate >= 30 -> ReportInsight(
                    title = "Excellent Savings Rate!",
                    description = "You saved ${String.format("%.1f", summary.savingsRate)}% of your income. " +
                            "This is well above the recommended 20% threshold.",
                    type = InsightType.POSITIVE
                )
                summary.savingsRate >= 20 -> ReportInsight(
                    title = "Good Savings Habits",
                    description = "You saved ${String.format("%.1f", summary.savingsRate)}% of your income. " +
                            "You're meeting the recommended savings threshold.",
                    type = InsightType.POSITIVE
                )
                summary.savingsRate >= 0 -> ReportInsight(
                    title = "Room for More Savings",
                    description = "Your savings rate of ${String.format("%.1f", summary.savingsRate)}% " +
                            "is below the recommended 20%. Look for categories to trim.",
                    type = InsightType.WARNING
                )
                else -> ReportInsight(
                    title = "Spending Exceeds Income",
                    description = "You spent more than you earned this month. " +
                            "This is unsustainable — prioritize reducing expenses.",
                    type = InsightType.ALERT
                )
            }
        )

        // Insight 2: Biggest spending category
        val topCategory = categoryBreakdown.maxByOrNull { it.value }
        if (topCategory != null) {
            val percentage = (topCategory.value / summary.totalExpenses) * 100
            insights.add(ReportInsight(
                title = "Top Spending: ${topCategory.key}",
                description = "${topCategory.key} accounted for ${String.format("%.1f", percentage)}% " +
                        "of your total expenses (₹${formatAmount(topCategory.value)}).",
                type = if (percentage > 40) InsightType.WARNING else InsightType.NEUTRAL,
                relatedCategory = topCategory.key
            ))
        }

        // Insight 3: Budget violations
        val overBudgetCategories = budgetAllocations.filter { (category, budget) ->
            val actual = categoryBreakdown[category] ?: 0.0
            actual > budget && budget > 0
        }
        if (overBudgetCategories.isNotEmpty()) {
            insights.add(ReportInsight(
                title = "${overBudgetCategories.size} Budget(s) Exceeded",
                description = "You went over budget in: ${overBudgetCategories.keys.joinToString(", ")}. " +
                        "Consider adjusting budgets or curbing spending in these areas.",
                type = InsightType.WARNING
            ))
        }

        // Insight 4: Month-over-month comparison
        if (previousMonth != null) {
            val expenseChange = summary.totalExpenses - previousMonth.totalExpenses
            val changePercent = if (previousMonth.totalExpenses > 0) {
                (expenseChange / previousMonth.totalExpenses) * 100
            } else 0.0

            if (expenseChange < 0) {
                insights.add(ReportInsight(
                    title = "Spending Decreased!",
                    description = "You spent ${String.format("%.1f", Math.abs(changePercent))}% " +
                            "less than last month. Great discipline!",
                    type = InsightType.POSITIVE
                ))
            } else if (changePercent > 20) {
                insights.add(ReportInsight(
                    title = "Spending Spike",
                    description = "Expenses increased by ${String.format("%.1f", changePercent)}% " +
                            "compared to last month. Review if this was planned.",
                    type = InsightType.WARNING
                ))
            }
        }

        return insights.take(3) // Top 3 insights only
    }

    // ─────────────────────────────────────────────────────────────────────
    // Scores Calculation
    // ─────────────────────────────────────────────────────────────────────

    private fun calculateScores(
        summary: FinancialSummary,
        budgetAllocations: Map<String, Double>,
        categoryBreakdown: Map<String, Double>,
        investmentGrowth: Double,
        privacyScore: Int
    ): ReportScores {
        // Financial Health Score (0-100)
        val healthScore = calculateHealthScore(summary)

        // Budget Adherence Score (0-100)
        val budgetScore = calculateBudgetAdherenceScore(categoryBreakdown, budgetAllocations)

        // Savings Score (0-100)
        val savingsScore = when {
            summary.savingsRate >= 30 -> 100
            summary.savingsRate >= 20 -> 80
            summary.savingsRate >= 10 -> 60
            summary.savingsRate >= 0 -> 40
            else -> 20
        }

        // Investment Score (simple: positive growth = good)
        val investmentScore = when {
            investmentGrowth > 0 -> 80
            investmentGrowth == 0.0 -> 50
            else -> 30
        }

        val overall = (healthScore + budgetScore + savingsScore + investmentScore) / 4
        val grade = when {
            overall >= 90 -> "A+"
            overall >= 80 -> "A"
            overall >= 70 -> "B+"
            overall >= 60 -> "B"
            overall >= 50 -> "C"
            overall >= 40 -> "D"
            else -> "F"
        }

        return ReportScores(
            financialHealthScore = healthScore,
            budgetAdherenceScore = budgetScore,
            savingsScore = savingsScore,
            investmentScore = investmentScore,
            privacyScore = privacyScore,
            overallGrade = grade
        )
    }

    private fun calculateHealthScore(summary: FinancialSummary): Int {
        var score = 50 // Base

        // Savings rate contribution (max +30)
        score += (summary.savingsRate * 0.6).toInt().coerceIn(0, 30)

        // Income > expenses (+20)
        if (summary.netSavings > 0) score += 20

        return score.coerceIn(0, 100)
    }

    private fun calculateBudgetAdherenceScore(
        actual: Map<String, Double>,
        budgets: Map<String, Double>
    ): Int {
        if (budgets.isEmpty()) return 50

        val adherences = budgets.map { (category, budget) ->
            val spent = actual[category] ?: 0.0
            if (budget > 0) {
                (1 - ((spent - budget) / budget).coerceAtLeast(0.0)).coerceIn(0.0, 1.0)
            } else 1.0
        }

        return (adherences.average() * 100).toInt().coerceIn(0, 100)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Month-over-Month Comparison
    // ─────────────────────────────────────────────────────────────────────

    private fun buildComparison(
        currentMonthYear: String,
        current: FinancialSummary,
        previous: FinancialSummary,
        currentCategories: Map<String, Double>
    ): MonthComparison {
        val previousYearMonth = YearMonth.parse(currentMonthYear).minusMonths(1)

        val incomeChange = current.totalIncome - previous.totalIncome
        val expenseChange = current.totalExpenses - previous.totalExpenses
        val savingsChange = current.netSavings - previous.netSavings

        return MonthComparison(
            previousMonthYear = previousYearMonth.toString(),
            incomeChange = incomeChange,
            incomeChangePercent = if (previous.totalIncome > 0) {
                (incomeChange / previous.totalIncome) * 100
            } else 0.0,
            expenseChange = expenseChange,
            expenseChangePercent = if (previous.totalExpenses > 0) {
                (expenseChange / previous.totalExpenses) * 100
            } else 0.0,
            savingsChange = savingsChange,
            savingsRateChange = current.savingsRate - previous.savingsRate,
            categoryChanges = emptyList() // Would need previous category data
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // CSV Export
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates CSV content from transaction data for spreadsheet export.
     *
     * @param transactions List of transactions to export.
     * @param includeHeader Whether to include column header row.
     * @return CSV-formatted string ready for file writing.
     */
    fun generateCsvExport(
        transactions: List<TransactionExportRow>,
        includeHeader: Boolean = true
    ): String {
        val sb = StringBuilder()

        if (includeHeader) {
            sb.appendLine("Date,Description,Category,Amount,Type,Balance,Assigned To")
        }

        transactions.forEach { txn ->
            val date = txn.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val description = escapeCsvField(txn.description)
            val category = escapeCsvField(txn.category)
            val amount = String.format("%.2f", txn.amount)
            val balance = txn.balance?.let { String.format("%.2f", it) } ?: ""
            val assignedTo = txn.assignedTo?.let { escapeCsvField(it) } ?: ""

            sb.appendLine("$date,$description,$category,$amount,${txn.type},$balance,$assignedTo")
        }

        return sb.toString()
    }

    /**
     * Generates a summary CSV with monthly aggregates.
     *
     * @param report The monthly report to summarize.
     * @return CSV string with section summaries.
     */
    fun generateSummaryCsvExport(report: MonthlyReport): String {
        val sb = StringBuilder()
        sb.appendLine("Section,Item,Value,Percentage")

        report.sections.forEach { section ->
            section.rows.forEach { row ->
                val percentage = row.percentage?.let { String.format("%.1f", it) } ?: ""
                sb.appendLine("${escapeCsvField(section.title)},${escapeCsvField(row.label)},${row.numericValue},$percentage")
            }
        }

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────

    private fun formatAmount(amount: Double): String {
        val absAmount = Math.abs(amount)
        return when {
            absAmount >= 10_00_000 -> String.format("%.2f L", absAmount / 100_000)
            absAmount >= 1000 -> String.format("%,.0f", absAmount)
            else -> String.format("%.2f", absAmount)
        }
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }

    private fun generateCategoryColors(count: Int): List<String> {
        val palette = listOf(
            "#4CAF50", "#2196F3", "#FF9800", "#E91E63", "#9C27B0",
            "#00BCD4", "#FF5722", "#607D8B", "#795548", "#CDDC39",
            "#3F51B5", "#009688", "#FFC107", "#673AB7", "#8BC34A"
        )
        return (0 until count).map { palette[it % palette.size] }
    }
}
