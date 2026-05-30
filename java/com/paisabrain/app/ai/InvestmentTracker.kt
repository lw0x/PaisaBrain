package com.paisabrain.app.ai

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * Comprehensive investment tracking and analysis system.
 *
 * Tracks multiple investment categories (mutual funds, fixed deposits,
 * stocks, gold, real estate, crypto, PPF/NPS), provides portfolio overview,
 * returns calculation (including XIRR approximation), asset allocation,
 * and "what if you invested instead" simulations.
 * All references are generic — no fund house names, broker names, or brand references.
 */
class InvestmentTracker {

    // ─────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Represents a single investment holding.
     *
     * @property id Unique investment identifier.
     * @property category Investment category type.
     * @property name User-given name (e.g., "Large Cap Fund", "5-Year FD").
     * @property investedAmount Total amount invested (principal).
     * @property currentValue Current estimated market value.
     * @property startDate When the investment was started.
     * @property maturityDate Maturity date (applicable for FDs, bonds).
     * @property interestRate Expected/guaranteed rate of return (annual %).
     * @property isRecurring Whether this is a recurring investment (SIP).
     * @property recurringAmount Monthly recurring amount (for SIPs).
     * @property recurringDay Day of month for recurring debit.
     * @property notes User notes about this investment.
     * @property detectedFromSms Whether auto-detected from SMS.
     * @property lastUpdated When the value was last updated.
     * @property cashFlows List of cash flows for XIRR calculation.
     */
    data class Investment(
        val id: String,
        val category: InvestmentCategory,
        val name: String,
        val investedAmount: Double,
        val currentValue: Double,
        val startDate: LocalDate,
        val maturityDate: LocalDate? = null,
        val interestRate: Double? = null, // annual percentage
        val isRecurring: Boolean = false,
        val recurringAmount: Double? = null,
        val recurringDay: Int? = null,
        val notes: String = "",
        val detectedFromSms: Boolean = false,
        val lastUpdated: LocalDate = LocalDate.now(),
        val cashFlows: List<CashFlow> = emptyList()
    )

    /**
     * Represents a cash flow event for XIRR calculation.
     *
     * @property date Date of the cash flow.
     * @property amount Negative for investments (outflow), positive for redemptions (inflow).
     */
    data class CashFlow(
        val date: LocalDate,
        val amount: Double // negative = investment, positive = redemption
    )

    /**
     * Portfolio overview aggregating all investments.
     *
     * @property totalInvested Total amount invested across all holdings.
     * @property totalCurrentValue Current portfolio value.
     * @property totalReturns Absolute returns (current - invested).
     * @property overallReturnPercentage Overall return as percentage.
     * @property xirr Estimated annualized return (XIRR).
     * @property investments All tracked investments.
     * @property assetAllocation Allocation across categories.
     * @property monthlyInvestmentContribution Total monthly recurring investments.
     * @property investmentToSpendingRatio Ratio of monthly investment to monthly spending.
     */
    data class Portfolio(
        val totalInvested: Double,
        val totalCurrentValue: Double,
        val totalReturns: Double,
        val overallReturnPercentage: Double,
        val xirr: Double?,
        val investments: List<Investment>,
        val assetAllocation: List<AssetAllocation>,
        val monthlyInvestmentContribution: Double,
        val investmentToSpendingRatio: Double
    )

    /**
     * Return details for a specific investment.
     *
     * @property investmentId Linked investment.
     * @property absoluteReturn Total profit/loss amount.
     * @property percentageReturn Total return as percentage.
     * @property annualizedReturn CAGR or XIRR annualized return.
     * @property holdingPeriodDays Number of days held.
     * @property daysSinceLastInvestment Days since last contribution.
     */
    data class InvestmentReturn(
        val investmentId: String,
        val absoluteReturn: Double,
        val percentageReturn: Double,
        val annualizedReturn: Double?,
        val holdingPeriodDays: Long,
        val daysSinceLastInvestment: Long
    )

    /**
     * Asset allocation breakdown by category.
     *
     * @property category Investment category.
     * @property investedAmount Total invested in this category.
     * @property currentValue Current value in this category.
     * @property percentage Percentage of total portfolio.
     * @property holdingCount Number of holdings in this category.
     */
    data class AssetAllocation(
        val category: InvestmentCategory,
        val investedAmount: Double,
        val currentValue: Double,
        val percentage: Double,
        val holdingCount: Int
    )

    /**
     * SIP (Systematic Investment Plan) tracking details.
     *
     * @property investmentId Linked investment.
     * @property monthlyAmount Monthly SIP amount.
     * @property totalMonthsCompleted Months of SIP completed.
     * @property totalInvested Total invested so far.
     * @property estimatedValue Estimated current value using expected returns.
     * @property estimatedFutureValue Projected value at target duration.
     * @property targetDurationMonths Planned SIP duration in months.
     * @property expectedAnnualReturn Expected annual return rate (%).
     */
    data class SipDetails(
        val investmentId: String,
        val monthlyAmount: Double,
        val totalMonthsCompleted: Int,
        val totalInvested: Double,
        val estimatedValue: Double,
        val estimatedFutureValue: Double,
        val targetDurationMonths: Int,
        val expectedAnnualReturn: Double
    )

    /**
     * Fixed Deposit tracking details.
     *
     * @property investmentId Linked investment.
     * @property principal Deposited amount.
     * @property annualRate Interest rate (% per annum).
     * @property tenureMonths Deposit tenure in months.
     * @property maturityDate When the FD matures.
     * @property maturityAmount Expected amount at maturity.
     * @property interestEarned Total interest earned at maturity.
     * @property isCompounding Whether interest compounds.
     * @property compoundingFrequency Quarterly/Monthly/Annual.
     * @property daysToMaturity Days remaining until maturity.
     */
    data class FixedDepositDetails(
        val investmentId: String,
        val principal: Double,
        val annualRate: Double,
        val tenureMonths: Int,
        val maturityDate: LocalDate,
        val maturityAmount: Double,
        val interestEarned: Double,
        val isCompounding: Boolean = true,
        val compoundingFrequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY,
        val daysToMaturity: Long
    )

    /**
     * "What if you invested?" simulation result.
     *
     * @property categoryName Spending category being simulated.
     * @property monthlySpendOnCategory Average monthly spend on this category.
     * @property simulationYears Years of simulation.
     * @property assumedAnnualReturn Assumed annual return for calculation.
     * @property totalWouldHaveInvested Total that would have been invested.
     * @property estimatedValue Estimated value with compound returns.
     * @property potentialGain Pure gain from compound returns.
     */
    data class InvestmentSimulation(
        val categoryName: String,
        val monthlySpendOnCategory: Double,
        val simulationYears: Int,
        val assumedAnnualReturn: Double,
        val totalWouldHaveInvested: Double,
        val estimatedValue: Double,
        val potentialGain: Double
    )

    /**
     * Monthly investment contribution trend data point.
     */
    data class MonthlyContributionPoint(
        val monthYear: String, // "2024-03"
        val totalContribution: Double,
        val byCategoryBreakdown: Map<InvestmentCategory, Double>
    )

    /**
     * Investment categories.
     */
    enum class InvestmentCategory(val displayName: String) {
        MUTUAL_FUND_EQUITY("Equity Mutual Fund"),
        MUTUAL_FUND_DEBT("Debt Mutual Fund"),
        MUTUAL_FUND_HYBRID("Hybrid Mutual Fund"),
        FIXED_DEPOSIT("Fixed Deposit"),
        RECURRING_DEPOSIT("Recurring Deposit"),
        PPF("Public Provident Fund"),
        NPS("National Pension System"),
        STOCKS("Direct Equity / Stocks"),
        GOLD_PHYSICAL("Gold (Physical)"),
        GOLD_DIGITAL("Gold (Digital/Sovereign)"),
        REAL_ESTATE("Real Estate"),
        CRYPTO("Cryptocurrency"),
        BONDS("Bonds/Debentures"),
        SAVINGS_ACCOUNT("Savings Account Interest"),
        OTHER("Other Investment")
    }

    /**
     * Compounding frequency options.
     */
    enum class CompoundingFrequency(val timesPerYear: Int) {
        MONTHLY(12),
        QUARTERLY(4),
        HALF_YEARLY(2),
        ANNUALLY(1)
    }

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private val investments = mutableListOf<Investment>()
    private val monthlyContributions = mutableListOf<MonthlyContributionPoint>()

    // ─────────────────────────────────────────────────────────────────────
    // Investment Management
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a new investment to the portfolio.
     *
     * @param investment The investment to add.
     */
    fun addInvestment(investment: Investment) {
        investments.add(investment)
    }

    /**
     * Updates an existing investment's current value.
     *
     * @param investmentId The investment to update.
     * @param newCurrentValue Updated market value.
     * @return Updated investment, or null if not found.
     */
    fun updateInvestmentValue(investmentId: String, newCurrentValue: Double): Investment? {
        val index = investments.indexOfFirst { it.id == investmentId }
        if (index < 0) return null

        val updated = investments[index].copy(
            currentValue = newCurrentValue,
            lastUpdated = LocalDate.now()
        )
        investments[index] = updated
        return updated
    }

    /**
     * Records a new cash flow (contribution or withdrawal) for an investment.
     *
     * @param investmentId The investment to record against.
     * @param amount Negative for new investment, positive for withdrawal.
     * @param date Date of the cash flow.
     */
    fun recordCashFlow(investmentId: String, amount: Double, date: LocalDate = LocalDate.now()) {
        val index = investments.indexOfFirst { it.id == investmentId }
        if (index < 0) return

        val investment = investments[index]
        val updatedCashFlows = investment.cashFlows + CashFlow(date, amount)
        val updatedInvested = if (amount < 0) {
            investment.investedAmount + abs(amount)
        } else {
            investment.investedAmount
        }

        investments[index] = investment.copy(
            cashFlows = updatedCashFlows,
            investedAmount = updatedInvested,
            lastUpdated = LocalDate.now()
        )
    }

    /**
     * Removes an investment from tracking.
     *
     * @param investmentId The investment to remove.
     */
    fun removeInvestment(investmentId: String) {
        investments.removeAll { it.id == investmentId }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Portfolio Overview
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generates complete portfolio overview.
     *
     * @param monthlySpending Average monthly spending for ratio calculation.
     * @return Complete portfolio summary.
     */
    fun getPortfolioOverview(monthlySpending: Double = 0.0): Portfolio {
        val totalInvested = investments.sumOf { it.investedAmount }
        val totalCurrentValue = investments.sumOf { it.currentValue }
        val totalReturns = totalCurrentValue - totalInvested
        val returnPercentage = if (totalInvested > 0) {
            (totalReturns / totalInvested) * 100
        } else 0.0

        val allCashFlows = buildPortfolioCashFlows()
        val xirr = if (allCashFlows.size >= 2) {
            calculateXirr(allCashFlows, totalCurrentValue)
        } else null

        val assetAllocation = calculateAssetAllocation(totalCurrentValue)

        val monthlyContribution = investments
            .filter { it.isRecurring }
            .sumOf { it.recurringAmount ?: 0.0 }

        val investmentRatio = if (monthlySpending > 0) {
            monthlyContribution / monthlySpending
        } else 0.0

        return Portfolio(
            totalInvested = totalInvested,
            totalCurrentValue = totalCurrentValue,
            totalReturns = totalReturns,
            overallReturnPercentage = returnPercentage,
            xirr = xirr,
            investments = investments.toList(),
            assetAllocation = assetAllocation,
            monthlyInvestmentContribution = monthlyContribution,
            investmentToSpendingRatio = investmentRatio
        )
    }

    /**
     * Calculates returns for a specific investment.
     *
     * @param investmentId The investment to analyze.
     * @return Return details, or null if not found.
     */
    fun calculateReturns(investmentId: String): InvestmentReturn? {
        val investment = investments.find { it.id == investmentId } ?: return null
        val today = LocalDate.now()

        val absoluteReturn = investment.currentValue - investment.investedAmount
        val percentageReturn = if (investment.investedAmount > 0) {
            (absoluteReturn / investment.investedAmount) * 100
        } else 0.0

        val holdingDays = ChronoUnit.DAYS.between(investment.startDate, today)

        // CAGR for simple case
        val annualizedReturn = if (holdingDays > 365 && investment.investedAmount > 0) {
            val years = holdingDays / 365.0
            ((investment.currentValue / investment.investedAmount).pow(1.0 / years) - 1) * 100
        } else if (investment.cashFlows.size >= 2) {
            calculateXirr(investment.cashFlows, investment.currentValue)?.times(100)
        } else null

        val daysSinceLastInvestment = investment.cashFlows
            .filter { it.amount < 0 }
            .maxOfOrNull { ChronoUnit.DAYS.between(it.date, today) }
            ?: holdingDays

        return InvestmentReturn(
            investmentId = investmentId,
            absoluteReturn = absoluteReturn,
            percentageReturn = percentageReturn,
            annualizedReturn = annualizedReturn,
            holdingPeriodDays = holdingDays,
            daysSinceLastInvestment = daysSinceLastInvestment
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // SIP Tracker
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gets detailed SIP tracking information.
     *
     * @param investmentId The SIP investment to track.
     * @param expectedAnnualReturn Expected annual return % (default 12%).
     * @param targetDurationMonths Total planned SIP duration.
     * @return SIP details with projections, or null if not a SIP/not found.
     */
    fun getSipDetails(
        investmentId: String,
        expectedAnnualReturn: Double = 12.0,
        targetDurationMonths: Int = 120
    ): SipDetails? {
        val investment = investments.find { it.id == investmentId && it.isRecurring }
            ?: return null

        val monthlyAmount = investment.recurringAmount ?: return null
        val monthsCompleted = ChronoUnit.MONTHS.between(
            investment.startDate, LocalDate.now()
        ).toInt().coerceAtLeast(1)
        val totalInvested = monthlyAmount * monthsCompleted

        // Estimate current value using SIP formula
        val monthlyRate = expectedAnnualReturn / 12 / 100
        val estimatedValue = calculateSipFutureValue(monthlyAmount, monthlyRate, monthsCompleted)
        val estimatedFutureValue = calculateSipFutureValue(monthlyAmount, monthlyRate, targetDurationMonths)

        return SipDetails(
            investmentId = investmentId,
            monthlyAmount = monthlyAmount,
            totalMonthsCompleted = monthsCompleted,
            totalInvested = totalInvested,
            estimatedValue = estimatedValue,
            estimatedFutureValue = estimatedFutureValue,
            targetDurationMonths = targetDurationMonths,
            expectedAnnualReturn = expectedAnnualReturn
        )
    }

    /**
     * Calculates SIP future value using compound interest formula.
     * FV = P × [(1+r)^n - 1] / r × (1+r)
     */
    private fun calculateSipFutureValue(
        monthlyAmount: Double,
        monthlyRate: Double,
        months: Int
    ): Double {
        if (monthlyRate <= 0) return monthlyAmount * months
        val factor = (1 + monthlyRate).pow(months.toDouble())
        return monthlyAmount * ((factor - 1) / monthlyRate) * (1 + monthlyRate)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixed Deposit Tracker
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gets detailed fixed deposit information with maturity projections.
     *
     * @param investmentId The FD investment to track.
     * @return FD details with interest calculation, or null if not an FD/not found.
     */
    fun getFixedDepositDetails(investmentId: String): FixedDepositDetails? {
        val investment = investments.find {
            it.id == investmentId && it.category == InvestmentCategory.FIXED_DEPOSIT
        } ?: return null

        val rate = investment.interestRate ?: return null
        val maturityDate = investment.maturityDate ?: return null
        val principal = investment.investedAmount
        val tenureMonths = ChronoUnit.MONTHS.between(investment.startDate, maturityDate).toInt()

        // Compound interest: A = P(1 + r/n)^(nt)
        val compoundingFrequency = CompoundingFrequency.QUARTERLY
        val n = compoundingFrequency.timesPerYear
        val t = tenureMonths / 12.0
        val maturityAmount = principal * (1 + rate / (100.0 * n)).pow(n * t)
        val interestEarned = maturityAmount - principal
        val daysToMaturity = ChronoUnit.DAYS.between(LocalDate.now(), maturityDate)
            .coerceAtLeast(0)

        return FixedDepositDetails(
            investmentId = investmentId,
            principal = principal,
            annualRate = rate,
            tenureMonths = tenureMonths,
            maturityDate = maturityDate,
            maturityAmount = maturityAmount,
            interestEarned = interestEarned,
            isCompounding = true,
            compoundingFrequency = compoundingFrequency,
            daysToMaturity = daysToMaturity
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Asset Allocation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Calculates current asset allocation (for pie chart data).
     *
     * @param totalPortfolioValue Total portfolio value for percentage calculation.
     * @return List of allocations by category.
     */
    fun calculateAssetAllocation(totalPortfolioValue: Double): List<AssetAllocation> {
        return investments
            .groupBy { it.category }
            .map { (category, holdings) ->
                val invested = holdings.sumOf { it.investedAmount }
                val current = holdings.sumOf { it.currentValue }
                val percentage = if (totalPortfolioValue > 0) {
                    (current / totalPortfolioValue) * 100
                } else 0.0

                AssetAllocation(
                    category = category,
                    investedAmount = invested,
                    currentValue = current,
                    percentage = percentage,
                    holdingCount = holdings.size
                )
            }
            .sortedByDescending { it.percentage }
    }

    // ─────────────────────────────────────────────────────────────────────
    // "What If You Invested" Simulator
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Simulates what returns would look like if spending in a category
     * was instead invested via SIP.
     *
     * @param categoryName Spending category name (e.g., "Food Delivery").
     * @param monthlySpend Average monthly spend on that category.
     * @param years Number of years to simulate.
     * @param assumedReturn Assumed annual return (default 12%).
     * @return Simulation result showing potential gains.
     */
    fun simulateInvestmentInstead(
        categoryName: String,
        monthlySpend: Double,
        years: Int = 5,
        assumedReturn: Double = 12.0
    ): InvestmentSimulation {
        val totalMonths = years * 12
        val monthlyRate = assumedReturn / 12 / 100
        val totalInvested = monthlySpend * totalMonths
        val estimatedValue = calculateSipFutureValue(monthlySpend, monthlyRate, totalMonths)
        val gain = estimatedValue - totalInvested

        return InvestmentSimulation(
            categoryName = categoryName,
            monthlySpendOnCategory = monthlySpend,
            simulationYears = years,
            assumedAnnualReturn = assumedReturn,
            totalWouldHaveInvested = totalInvested,
            estimatedValue = estimatedValue,
            potentialGain = gain
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Monthly Contribution Trend
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Records a monthly contribution data point.
     *
     * @param monthYear Format "2024-03".
     * @param contributions Map of category → amount contributed.
     */
    fun recordMonthlyContribution(
        monthYear: String,
        contributions: Map<InvestmentCategory, Double>
    ) {
        val total = contributions.values.sum()
        monthlyContributions.add(
            MonthlyContributionPoint(
                monthYear = monthYear,
                totalContribution = total,
                byCategoryBreakdown = contributions
            )
        )
    }

    /**
     * Gets monthly contribution trend data for charting.
     *
     * @param lastNMonths Number of months to include.
     * @return List of monthly contribution points, oldest first.
     */
    fun getContributionTrend(lastNMonths: Int = 12): List<MonthlyContributionPoint> {
        return monthlyContributions
            .sortedBy { it.monthYear }
            .takeLast(lastNMonths)
    }

    // ─────────────────────────────────────────────────────────────────────
    // XIRR Calculation (Newton-Raphson Approximation)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Calculates XIRR (Extended Internal Rate of Return) using Newton-Raphson method.
     * Used for irregular cash flow investments.
     *
     * @param cashFlows List of dated cash flows (negative = investment, positive = redemption).
     * @param currentValue Current portfolio value (treated as final positive cash flow today).
     * @return Annualized return rate as decimal (0.12 = 12%), or null if calculation fails.
     */
    fun calculateXirr(cashFlows: List<CashFlow>, currentValue: Double): Double? {
        if (cashFlows.isEmpty()) return null

        val allFlows = cashFlows.toMutableList()
        // Add current value as a positive cash flow today
        allFlows.add(CashFlow(LocalDate.now(), currentValue))

        val dates = allFlows.map { it.date }
        val amounts = allFlows.map { it.amount }
        val firstDate = dates.minOrNull() ?: return null

        // Newton-Raphson iteration
        var rate = 0.1 // Initial guess: 10%
        val maxIterations = 100
        val tolerance = 1e-7

        for (iteration in 0 until maxIterations) {
            var npv = 0.0
            var dnpv = 0.0 // derivative

            for (i in amounts.indices) {
                val yearsFraction = ChronoUnit.DAYS.between(firstDate, dates[i]) / 365.0
                val discountFactor = (1 + rate).pow(yearsFraction)

                if (discountFactor == 0.0) continue

                npv += amounts[i] / discountFactor
                dnpv -= yearsFraction * amounts[i] / ((1 + rate) * discountFactor)
            }

            if (abs(dnpv) < 1e-10) break

            val newRate = rate - npv / dnpv

            if (abs(newRate - rate) < tolerance) {
                return newRate
            }

            rate = newRate

            // Guard against divergence
            if (rate < -0.99 || rate > 10.0) return null
        }

        return if (abs(rate) < 10.0) rate else null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────

    private fun buildPortfolioCashFlows(): List<CashFlow> {
        return investments.flatMap { it.cashFlows }.sortedBy { it.date }
    }

    /**
     * Gets all investments filtered by category.
     */
    fun getInvestmentsByCategory(category: InvestmentCategory): List<Investment> =
        investments.filter { it.category == category }

    /**
     * Gets all SIP investments with their monthly amounts.
     */
    fun getAllSips(): List<Investment> =
        investments.filter { it.isRecurring }

    /**
     * Gets investments detected from SMS.
     */
    fun getSmsDetectedInvestments(): List<Investment> =
        investments.filter { it.detectedFromSms }
}
