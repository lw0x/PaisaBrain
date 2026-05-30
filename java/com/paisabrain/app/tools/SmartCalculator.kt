package com.paisabrain.app.tools

import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * All-in-one financial calculator suite.
 *
 * Provides EMI, SIP, FD, lumpsum, tip, GST, loan comparison, PPF,
 * gratuity, and bill splitting calculations — all pure math, no internet required.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * EMI calculation result with amortization schedule.
 */
data class EMIResult(
    val loanAmount: Double,
    val annualRate: Double,
    val tenureMonths: Int,
    val monthlyEMI: Double,
    val totalInterest: Double,
    val totalPayment: Double,
    val amortizationSchedule: List<AmortizationEntry>
)

/**
 * Single month entry in amortization schedule.
 */
data class AmortizationEntry(
    val month: Int,
    val emiAmount: Double,
    val principalPaid: Double,
    val interestPaid: Double,
    val outstandingBalance: Double
)

/**
 * SIP (Systematic Investment Plan) calculation result.
 */
data class SIPResult(
    val monthlyAmount: Double,
    val years: Int,
    val expectedReturnRate: Double,
    val totalInvested: Double,
    val futureValue: Double,
    val wealthGained: Double,
    val yearlyBreakdown: List<SIPYearEntry>
)

/**
 * Year-wise SIP growth breakdown.
 */
data class SIPYearEntry(
    val year: Int,
    val investedSoFar: Double,
    val valueSoFar: Double,
    val gainSoFar: Double
)

/**
 * Lumpsum investment calculation result.
 */
data class LumpsumResult(
    val principal: Double,
    val annualRate: Double,
    val years: Int,
    val maturityAmount: Double,
    val totalGain: Double,
    val effectiveReturn: Double,
    val yearlyGrowth: List<YearlyGrowthEntry>
)

/**
 * Yearly growth entry for lumpsum/FD.
 */
data class YearlyGrowthEntry(
    val year: Int,
    val openingBalance: Double,
    val interestEarned: Double,
    val closingBalance: Double
)

/**
 * Fixed Deposit calculation result.
 */
data class FDResult(
    val principal: Double,
    val annualRate: Double,
    val tenureMonths: Int,
    val compoundingFrequency: CompoundingFrequency,
    val maturityAmount: Double,
    val totalInterestEarned: Double,
    val effectiveAnnualRate: Double
)

/**
 * Compounding frequency options for FD.
 */
enum class CompoundingFrequency(val timesPerYear: Int, val displayName: String) {
    MONTHLY(12, "Monthly"),
    QUARTERLY(4, "Quarterly"),
    HALF_YEARLY(2, "Half-Yearly"),
    YEARLY(1, "Yearly")
}

/**
 * Tip calculation result.
 */
data class TipResult(
    val billAmount: Double,
    val tipPercentage: Double,
    val tipAmount: Double,
    val totalWithTip: Double,
    val numberOfPeople: Int,
    val perPersonShare: Double
)

/**
 * GST calculation result.
 */
data class GSTResult(
    val baseAmount: Double,
    val gstRate: Double,
    val cgst: Double,
    val sgst: Double,
    val totalGST: Double,
    val totalAmount: Double,
    val isReverseCalculation: Boolean
)

/**
 * Bill split entry for unequal splits.
 */
data class BillSplitEntry(
    val personName: String,
    val amount: Double,
    val percentage: Double
)

/**
 * Bill split result.
 */
data class BillSplitResult(
    val totalBill: Double,
    val splits: List<BillSplitEntry>,
    val isEqual: Boolean
)

/**
 * Loan comparison result.
 */
data class LoanComparisonResult(
    val loans: List<LoanComparisonEntry>,
    val bestLoan: LoanComparisonEntry,
    val maxSavings: Double,
    val recommendation: String
)

/**
 * Individual loan in comparison.
 */
data class LoanComparisonEntry(
    val label: String,
    val loanAmount: Double,
    val annualRate: Double,
    val tenureMonths: Int,
    val monthlyEMI: Double,
    val totalInterest: Double,
    val totalPayment: Double
)

/**
 * PPF (Public Provident Fund) calculation result.
 */
data class PPFResult(
    val yearlyInvestment: Double,
    val interestRate: Double,
    val tenureYears: Int,
    val maturityAmount: Double,
    val totalInvested: Double,
    val totalInterestEarned: Double,
    val yearlyBreakdown: List<PPFYearEntry>
)

/**
 * Year-wise PPF breakdown.
 */
data class PPFYearEntry(
    val year: Int,
    val openingBalance: Double,
    val deposit: Double,
    val interestEarned: Double,
    val closingBalance: Double
)

/**
 * Gratuity calculation result.
 */
data class GratuityResult(
    val lastDrawnSalary: Double,
    val yearsOfService: Double,
    val gratuityAmount: Double,
    val formula: String,
    val isEligible: Boolean,
    val eligibilityNote: String
)

/**
 * Saved calculation history entry.
 */
data class CalculationHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val summary: String,
    val result: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

// ─────────────────────────────────────────────────────────────────────────────
// Smart Calculator Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core financial calculator with all computation modes.
 *
 * All calculations are pure math — no internet required.
 * Maintains a history of the last 20 calculations for quick reference.
 */
class SmartCalculator {

    private val history = mutableListOf<CalculationHistoryEntry>()
    private val maxHistorySize = 20

    // ─────────────────────────────────────────────────────────────────────────
    // EMI Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates EMI (Equated Monthly Installment) with full amortization schedule.
     *
     * Formula: EMI = P × r × (1+r)^n / ((1+r)^n - 1)
     * where P = principal, r = monthly rate, n = tenure in months
     *
     * @param loanAmount Principal loan amount
     * @param annualRate Annual interest rate in percentage (e.g., 8.5)
     * @param tenureMonths Loan tenure in months
     * @return Complete EMI result with amortization schedule
     */
    fun calculateEMI(loanAmount: Double, annualRate: Double, tenureMonths: Int): EMIResult {
        require(loanAmount > 0) { "Loan amount must be positive" }
        require(annualRate > 0) { "Interest rate must be positive" }
        require(tenureMonths > 0) { "Tenure must be positive" }

        val monthlyRate = annualRate / 12 / 100
        val n = tenureMonths.toDouble()

        val emi = loanAmount * monthlyRate * (1 + monthlyRate).pow(n) /
            ((1 + monthlyRate).pow(n) - 1)

        val totalPayment = emi * tenureMonths
        val totalInterest = totalPayment - loanAmount

        // Build amortization schedule
        val schedule = mutableListOf<AmortizationEntry>()
        var balance = loanAmount

        for (month in 1..tenureMonths) {
            val interestForMonth = balance * monthlyRate
            val principalForMonth = emi - interestForMonth
            balance -= principalForMonth

            schedule.add(
                AmortizationEntry(
                    month = month,
                    emiAmount = roundToTwo(emi),
                    principalPaid = roundToTwo(principalForMonth),
                    interestPaid = roundToTwo(interestForMonth),
                    outstandingBalance = roundToTwo(maxOf(0.0, balance))
                )
            )
        }

        val result = EMIResult(
            loanAmount = loanAmount,
            annualRate = annualRate,
            tenureMonths = tenureMonths,
            monthlyEMI = roundToTwo(emi),
            totalInterest = roundToTwo(totalInterest),
            totalPayment = roundToTwo(totalPayment),
            amortizationSchedule = schedule
        )

        addToHistory("EMI",
            "₹${formatAmount(loanAmount)} @ ${annualRate}% for ${tenureMonths}m",
            "EMI: ₹${formatAmount(emi)}, Total Interest: ₹${formatAmount(totalInterest)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIP Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates SIP (Systematic Investment Plan) future value.
     *
     * Formula: FV = P × [((1+r)^n - 1) / r] × (1+r)
     * where P = monthly amount, r = monthly rate, n = total months
     *
     * @param monthlyAmount Monthly SIP amount
     * @param years Investment duration in years
     * @param expectedReturnRate Expected annual return rate in percentage
     * @return SIP result with year-wise breakdown
     */
    fun calculateSIP(monthlyAmount: Double, years: Int, expectedReturnRate: Double): SIPResult {
        require(monthlyAmount > 0) { "Monthly amount must be positive" }
        require(years > 0) { "Duration must be positive" }
        require(expectedReturnRate > 0) { "Return rate must be positive" }

        val monthlyRate = expectedReturnRate / 12 / 100
        val totalMonths = years * 12
        val totalInvested = monthlyAmount * totalMonths

        val futureValue = monthlyAmount *
            (((1 + monthlyRate).pow(totalMonths) - 1) / monthlyRate) *
            (1 + monthlyRate)

        val wealthGained = futureValue - totalInvested

        // Year-wise breakdown
        val yearlyBreakdown = mutableListOf<SIPYearEntry>()
        for (year in 1..years) {
            val months = year * 12
            val invested = monthlyAmount * months
            val value = monthlyAmount *
                (((1 + monthlyRate).pow(months) - 1) / monthlyRate) *
                (1 + monthlyRate)

            yearlyBreakdown.add(
                SIPYearEntry(
                    year = year,
                    investedSoFar = roundToTwo(invested),
                    valueSoFar = roundToTwo(value),
                    gainSoFar = roundToTwo(value - invested)
                )
            )
        }

        val result = SIPResult(
            monthlyAmount = monthlyAmount,
            years = years,
            expectedReturnRate = expectedReturnRate,
            totalInvested = roundToTwo(totalInvested),
            futureValue = roundToTwo(futureValue),
            wealthGained = roundToTwo(wealthGained),
            yearlyBreakdown = yearlyBreakdown
        )

        addToHistory("SIP",
            "₹${formatAmount(monthlyAmount)}/month for ${years}y @ ${expectedReturnRate}%",
            "Future Value: ₹${formatAmount(futureValue)}, Gained: ₹${formatAmount(wealthGained)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lumpsum Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates one-time (lumpsum) investment projection.
     *
     * @param principal One-time investment amount
     * @param annualRate Expected annual return rate
     * @param years Investment duration
     * @return Lumpsum result with yearly growth
     */
    fun calculateLumpsum(principal: Double, annualRate: Double, years: Int): LumpsumResult {
        require(principal > 0) { "Principal must be positive" }
        require(annualRate > 0) { "Return rate must be positive" }
        require(years > 0) { "Duration must be positive" }

        val maturityAmount = principal * (1 + annualRate / 100).pow(years)
        val totalGain = maturityAmount - principal
        val effectiveReturn = ((maturityAmount / principal) - 1) * 100

        val yearlyGrowth = mutableListOf<YearlyGrowthEntry>()
        var balance = principal

        for (year in 1..years) {
            val interest = balance * annualRate / 100
            val closing = balance + interest
            yearlyGrowth.add(
                YearlyGrowthEntry(
                    year = year,
                    openingBalance = roundToTwo(balance),
                    interestEarned = roundToTwo(interest),
                    closingBalance = roundToTwo(closing)
                )
            )
            balance = closing
        }

        val result = LumpsumResult(
            principal = principal,
            annualRate = annualRate,
            years = years,
            maturityAmount = roundToTwo(maturityAmount),
            totalGain = roundToTwo(totalGain),
            effectiveReturn = roundToTwo(effectiveReturn),
            yearlyGrowth = yearlyGrowth
        )

        addToHistory("Lumpsum",
            "₹${formatAmount(principal)} for ${years}y @ ${annualRate}%",
            "Maturity: ₹${formatAmount(maturityAmount)}, Gain: ₹${formatAmount(totalGain)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FD Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates Fixed Deposit maturity amount.
     *
     * Formula: A = P × (1 + r/n)^(n×t)
     * where n = compounding frequency per year, t = tenure in years
     *
     * @param principal FD amount
     * @param annualRate Annual interest rate
     * @param tenureMonths Tenure in months
     * @param compounding Compounding frequency (default: Quarterly)
     * @return FD calculation result
     */
    fun calculateFD(
        principal: Double,
        annualRate: Double,
        tenureMonths: Int,
        compounding: CompoundingFrequency = CompoundingFrequency.QUARTERLY
    ): FDResult {
        require(principal > 0) { "Principal must be positive" }
        require(annualRate > 0) { "Rate must be positive" }
        require(tenureMonths > 0) { "Tenure must be positive" }

        val n = compounding.timesPerYear
        val t = tenureMonths / 12.0
        val rate = annualRate / 100

        val maturityAmount = principal * (1 + rate / n).pow(n * t)
        val totalInterest = maturityAmount - principal
        val effectiveRate = ((maturityAmount / principal).pow(1.0 / t) - 1) * 100

        val result = FDResult(
            principal = principal,
            annualRate = annualRate,
            tenureMonths = tenureMonths,
            compoundingFrequency = compounding,
            maturityAmount = roundToTwo(maturityAmount),
            totalInterestEarned = roundToTwo(totalInterest),
            effectiveAnnualRate = roundToTwo(effectiveRate)
        )

        addToHistory("FD",
            "₹${formatAmount(principal)} @ ${annualRate}% for ${tenureMonths}m (${compounding.displayName})",
            "Maturity: ₹${formatAmount(maturityAmount)}, Interest: ₹${formatAmount(totalInterest)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tip Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates tip and optional split among people.
     *
     * @param billAmount Total bill amount
     * @param tipPercentage Tip percentage
     * @param numberOfPeople Number of people to split among (default: 1)
     * @return Tip calculation result
     */
    fun calculateTip(billAmount: Double, tipPercentage: Double, numberOfPeople: Int = 1): TipResult {
        require(billAmount > 0) { "Bill amount must be positive" }
        require(tipPercentage >= 0) { "Tip percentage cannot be negative" }
        require(numberOfPeople > 0) { "Number of people must be at least 1" }

        val tipAmount = billAmount * tipPercentage / 100
        val totalWithTip = billAmount + tipAmount
        val perPerson = totalWithTip / numberOfPeople

        val result = TipResult(
            billAmount = roundToTwo(billAmount),
            tipPercentage = tipPercentage,
            tipAmount = roundToTwo(tipAmount),
            totalWithTip = roundToTwo(totalWithTip),
            numberOfPeople = numberOfPeople,
            perPersonShare = roundToTwo(perPerson)
        )

        addToHistory("Tip",
            "₹${formatAmount(billAmount)} + ${tipPercentage}% tip ÷ $numberOfPeople",
            "Per person: ₹${formatAmount(perPerson)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GST Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates GST (forward: base → total with GST).
     *
     * @param baseAmount Amount before GST
     * @param gstRate GST rate (5, 12, 18, or 28)
     * @return GST calculation breakdown (CGST + SGST)
     */
    fun calculateGSTForward(baseAmount: Double, gstRate: Double): GSTResult {
        require(baseAmount > 0) { "Amount must be positive" }
        require(gstRate in listOf(5.0, 12.0, 18.0, 28.0)) {
            "GST rate must be 5, 12, 18, or 28"
        }

        val totalGST = baseAmount * gstRate / 100
        val cgst = totalGST / 2
        val sgst = totalGST / 2
        val totalAmount = baseAmount + totalGST

        val result = GSTResult(
            baseAmount = roundToTwo(baseAmount),
            gstRate = gstRate,
            cgst = roundToTwo(cgst),
            sgst = roundToTwo(sgst),
            totalGST = roundToTwo(totalGST),
            totalAmount = roundToTwo(totalAmount),
            isReverseCalculation = false
        )

        addToHistory("GST (Forward)",
            "₹${formatAmount(baseAmount)} + ${gstRate}% GST",
            "GST: ₹${formatAmount(totalGST)}, Total: ₹${formatAmount(totalAmount)}")

        return result
    }

    /**
     * Calculates GST reverse (total with GST → base amount).
     *
     * @param totalAmount Amount inclusive of GST
     * @param gstRate GST rate (5, 12, 18, or 28)
     * @return GST breakdown showing base amount
     */
    fun calculateGSTReverse(totalAmount: Double, gstRate: Double): GSTResult {
        require(totalAmount > 0) { "Amount must be positive" }
        require(gstRate in listOf(5.0, 12.0, 18.0, 28.0)) {
            "GST rate must be 5, 12, 18, or 28"
        }

        val baseAmount = totalAmount * 100 / (100 + gstRate)
        val totalGST = totalAmount - baseAmount
        val cgst = totalGST / 2
        val sgst = totalGST / 2

        val result = GSTResult(
            baseAmount = roundToTwo(baseAmount),
            gstRate = gstRate,
            cgst = roundToTwo(cgst),
            sgst = roundToTwo(sgst),
            totalGST = roundToTwo(totalGST),
            totalAmount = roundToTwo(totalAmount),
            isReverseCalculation = true
        )

        addToHistory("GST (Reverse)",
            "₹${formatAmount(totalAmount)} inclusive of ${gstRate}% GST",
            "Base: ₹${formatAmount(baseAmount)}, GST: ₹${formatAmount(totalGST)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bill Split Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Splits bill equally among N people.
     *
     * @param totalBill Total bill amount
     * @param people Number of people
     * @param names Optional list of names
     * @return Equal split result
     */
    fun splitBillEqual(totalBill: Double, people: Int, names: List<String> = emptyList()): BillSplitResult {
        require(totalBill > 0) { "Bill must be positive" }
        require(people > 0) { "Need at least 1 person" }

        val perPerson = totalBill / people
        val percentage = 100.0 / people

        val splits = (0 until people).map { i ->
            BillSplitEntry(
                personName = names.getOrElse(i) { "Person ${i + 1}" },
                amount = roundToTwo(perPerson),
                percentage = roundToTwo(percentage)
            )
        }

        addToHistory("Split (Equal)",
            "₹${formatAmount(totalBill)} ÷ $people people",
            "Each pays: ₹${formatAmount(perPerson)}")

        return BillSplitResult(
            totalBill = totalBill,
            splits = splits,
            isEqual = true
        )
    }

    /**
     * Splits bill with custom percentages/amounts per person.
     *
     * @param totalBill Total bill amount
     * @param shares Map of person name to their share percentage (must sum to 100)
     * @return Unequal split result
     */
    fun splitBillUnequal(totalBill: Double, shares: Map<String, Double>): BillSplitResult {
        require(totalBill > 0) { "Bill must be positive" }
        require(shares.isNotEmpty()) { "Need at least 1 share" }

        val totalPercentage = shares.values.sum()
        require(totalPercentage in 99.5..100.5) {
            "Shares must sum to 100%, got ${totalPercentage}%"
        }

        val splits = shares.map { (name, percentage) ->
            BillSplitEntry(
                personName = name,
                amount = roundToTwo(totalBill * percentage / 100),
                percentage = roundToTwo(percentage)
            )
        }

        addToHistory("Split (Unequal)",
            "₹${formatAmount(totalBill)} among ${shares.size} people",
            splits.joinToString(", ") { "${it.personName}: ₹${formatAmount(it.amount)}" })

        return BillSplitResult(
            totalBill = totalBill,
            splits = splits,
            isEqual = false
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Loan Comparison
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Compares multiple loan offers side by side.
     *
     * @param loans List of loan parameters (label, amount, rate, tenure)
     * @return Comparison result with recommendation
     */
    fun compareLoanOffers(
        loans: List<Triple<String, Triple<Double, Double, Int>, Unit>>
    ): LoanComparisonResult {
        return compareLoanOffersInternal(
            loans.map { Triple(it.first, it.second.first, it.second.second to it.second.third) }
        )
    }

    /**
     * Compares 2-3 loan offers side by side.
     *
     * @param offers List of (label, loanAmount, annualRate, tenureMonths)
     * @return Comparison with best option highlighted
     */
    fun compareLoans(offers: List<LoanOffer>): LoanComparisonResult {
        require(offers.size in 2..5) { "Compare between 2 and 5 loan offers" }

        val entries = offers.map { offer ->
            val monthlyRate = offer.annualRate / 12 / 100
            val n = offer.tenureMonths.toDouble()
            val emi = offer.loanAmount * monthlyRate * (1 + monthlyRate).pow(n) /
                ((1 + monthlyRate).pow(n) - 1)
            val totalPayment = emi * offer.tenureMonths
            val totalInterest = totalPayment - offer.loanAmount

            LoanComparisonEntry(
                label = offer.label,
                loanAmount = offer.loanAmount,
                annualRate = offer.annualRate,
                tenureMonths = offer.tenureMonths,
                monthlyEMI = roundToTwo(emi),
                totalInterest = roundToTwo(totalInterest),
                totalPayment = roundToTwo(totalPayment)
            )
        }

        val bestLoan = entries.minByOrNull { it.totalPayment }!!
        val worstLoan = entries.maxByOrNull { it.totalPayment }!!
        val maxSavings = worstLoan.totalPayment - bestLoan.totalPayment

        val recommendation = buildString {
            append("Best option: ${bestLoan.label} ")
            append("(EMI: ₹${formatAmount(bestLoan.monthlyEMI)}, ")
            append("Total cost: ₹${formatAmount(bestLoan.totalPayment)}). ")
            if (maxSavings > 0) {
                append("You save ₹${formatAmount(maxSavings)} compared to the most expensive option.")
            }
        }

        val result = LoanComparisonResult(
            loans = entries,
            bestLoan = bestLoan,
            maxSavings = roundToTwo(maxSavings),
            recommendation = recommendation
        )

        addToHistory("Loan Compare",
            "${offers.size} offers compared",
            "Best: ${bestLoan.label}, Saves ₹${formatAmount(maxSavings)}")

        return result
    }

    private fun compareLoanOffersInternal(
        offers: List<Triple<String, Double, Pair<Double, Int>>>
    ): LoanComparisonResult {
        val loanOffers = offers.map { (label, amount, rateAndTenure) ->
            LoanOffer(label, amount, rateAndTenure.first, rateAndTenure.second)
        }
        return compareLoans(loanOffers)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PPF Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates PPF (Public Provident Fund) maturity.
     *
     * PPF compounds annually, interest calculated on minimum balance
     * between 5th and last day of month.
     *
     * @param yearlyInvestment Annual investment amount (max ₹1.5L)
     * @param interestRate Current PPF rate (default 7.1%)
     * @param tenureYears PPF tenure (default 15 years)
     * @return PPF calculation with year-wise breakdown
     */
    fun calculatePPF(
        yearlyInvestment: Double,
        interestRate: Double = 7.1,
        tenureYears: Int = 15
    ): PPFResult {
        require(yearlyInvestment > 0) { "Investment must be positive" }
        require(yearlyInvestment <= 150000) { "PPF maximum yearly limit is ₹1,50,000" }
        require(tenureYears >= 15) { "PPF minimum tenure is 15 years" }

        val rate = interestRate / 100
        val yearlyBreakdown = mutableListOf<PPFYearEntry>()
        var balance = 0.0

        for (year in 1..tenureYears) {
            val opening = balance
            val deposit = yearlyInvestment
            val interest = (opening + deposit) * rate
            balance = opening + deposit + interest

            yearlyBreakdown.add(
                PPFYearEntry(
                    year = year,
                    openingBalance = roundToTwo(opening),
                    deposit = deposit,
                    interestEarned = roundToTwo(interest),
                    closingBalance = roundToTwo(balance)
                )
            )
        }

        val totalInvested = yearlyInvestment * tenureYears
        val totalInterest = balance - totalInvested

        val result = PPFResult(
            yearlyInvestment = yearlyInvestment,
            interestRate = interestRate,
            tenureYears = tenureYears,
            maturityAmount = roundToTwo(balance),
            totalInvested = totalInvested,
            totalInterestEarned = roundToTwo(totalInterest),
            yearlyBreakdown = yearlyBreakdown
        )

        addToHistory("PPF",
            "₹${formatAmount(yearlyInvestment)}/year for ${tenureYears}y @ ${interestRate}%",
            "Maturity: ₹${formatAmount(balance)}, Interest: ₹${formatAmount(totalInterest)}")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gratuity Calculator
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates gratuity amount.
     *
     * Formula: (Last drawn salary × Years of service × 15) / 26
     * Eligible after 5 years of continuous service.
     *
     * @param lastDrawnSalary Basic salary + DA (last drawn)
     * @param yearsOfService Total years of service
     * @return Gratuity calculation result
     */
    fun calculateGratuity(lastDrawnSalary: Double, yearsOfService: Double): GratuityResult {
        require(lastDrawnSalary > 0) { "Salary must be positive" }
        require(yearsOfService > 0) { "Years of service must be positive" }

        val isEligible = yearsOfService >= 5
        val roundedYears = if (yearsOfService % 1 >= 0.5) {
            kotlin.math.ceil(yearsOfService)
        } else {
            kotlin.math.floor(yearsOfService)
        }

        val gratuityAmount = (lastDrawnSalary * roundedYears * 15) / 26

        val eligibilityNote = if (isEligible) {
            "Eligible for gratuity (≥ 5 years of service)."
        } else {
            "Not yet eligible. Gratuity requires minimum 5 years of continuous service. " +
                "You need ${5 - yearsOfService} more years."
        }

        val result = GratuityResult(
            lastDrawnSalary = lastDrawnSalary,
            yearsOfService = yearsOfService,
            gratuityAmount = roundToTwo(gratuityAmount),
            formula = "(₹${formatAmount(lastDrawnSalary)} × ${roundedYears.toInt()} × 15) ÷ 26",
            isEligible = isEligible,
            eligibilityNote = eligibilityNote
        )

        addToHistory("Gratuity",
            "Salary ₹${formatAmount(lastDrawnSalary)}, ${yearsOfService}y service",
            "Gratuity: ₹${formatAmount(gratuityAmount)} (${if (isEligible) "Eligible" else "Not yet eligible"})")

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // History Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gets calculation history (most recent first).
     *
     * @param limit Maximum entries to return
     * @return List of recent calculations
     */
    fun getHistory(limit: Int = maxHistorySize): List<CalculationHistoryEntry> {
        return history.takeLast(minOf(limit, history.size)).reversed()
    }

    /**
     * Clears calculation history.
     */
    fun clearHistory() {
        history.clear()
    }

    /**
     * Formats a specific history entry for display.
     */
    fun formatHistoryEntry(entry: CalculationHistoryEntry): String {
        return buildString {
            append("${entry.type}: ${entry.summary}")
            appendLine()
            append("  → ${entry.result}")
            appendLine()
            append("  (${entry.timestamp.format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))})")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility Functions
    // ─────────────────────────────────────────────────────────────────────────

    private fun addToHistory(type: String, summary: String, result: String) {
        history.add(CalculationHistoryEntry(type = type, summary = summary, result = result))
        if (history.size > maxHistorySize) {
            history.removeAt(0)
        }
    }

    private fun roundToTwo(value: Double): Double {
        return (value * 100).roundToLong() / 100.0
    }

    private fun formatAmount(amount: Double): String {
        val absAmount = kotlin.math.abs(amount)
        return when {
            absAmount >= 10_000_000 -> String.format("%.2f Cr", amount / 10_000_000)
            absAmount >= 100_000 -> String.format("%.2f L", amount / 100_000)
            absAmount >= 1000 -> {
                val formatted = String.format("%.0f", amount)
                // Indian number formatting
                if (formatted.length > 3) {
                    val lastThree = formatted.takeLast(3)
                    val remaining = formatted.dropLast(3)
                    val withCommas = remaining.reversed().chunked(2).joinToString(",").reversed()
                    "$withCommas,$lastThree"
                } else formatted
            }
            else -> String.format("%.2f", amount)
        }
    }
}

/**
 * Loan offer input for comparison.
 */
data class LoanOffer(
    val label: String,
    val loanAmount: Double,
    val annualRate: Double,
    val tenureMonths: Int
)
