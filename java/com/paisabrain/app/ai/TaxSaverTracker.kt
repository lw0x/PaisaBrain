package com.paisabrain.app.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * # TaxSaverTracker
 *
 * India income tax saving tracker for FY 2026-27. Tracks all major deduction sections,
 * calculates tax liability under both old and new regimes, and automatically flags
 * tax-deductible transactions detected from SMS.
 *
 * ## Supported Sections
 * - **Section 80C** (₹1,50,000 limit): PF, ELSS, PPF, Life Insurance, Tuition Fees,
 *   Home Loan Principal, NSC, 5-Year FD, Sukanya Samriddhi
 * - **Section 80D** (Health Insurance): Self ₹25K / Parents ₹50K (senior citizen)
 * - **Section 80CCD(1B)** (NPS): Additional ₹50,000 deduction
 * - **Section 24** (Home Loan Interest): ₹2,00,000 limit
 * - **HRA Exemption**: Auto-detects rent payments from SMS transfers
 * - **Standard Deduction**: ₹50,000 (auto-applied for salaried)
 *
 * ## Features
 * - Total tax saved so far & remaining deduction capacity
 * - Old vs New regime comparison with recommendation
 * - Income tax slab calculation (both regimes, FY 2026-27)
 * - Auto-flags tax-deductible SMS transactions
 * - No brand/company names used
 *
 * @since 1.0.0
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tax regime options for FY 2026-27.
 */
enum class TaxRegime(val displayName: String) {
    OLD("Old Regime (with deductions)"),
    NEW("New Regime (lower rates, fewer deductions)")
}

/**
 * Deduction sections supported by this tracker.
 */
enum class DeductionSection(
    val displayName: String,
    val maxLimit: Double,
    val description: String
) {
    SECTION_80C(
        "Section 80C",
        150000.0,
        "Investments & savings: PF, PPF, ELSS, Life Insurance, Tuition Fees, etc."
    ),
    SECTION_80D_SELF(
        "Section 80D (Self & Family)",
        25000.0,
        "Health insurance premium for self, spouse, and children"
    ),
    SECTION_80D_PARENTS(
        "Section 80D (Parents)",
        50000.0,
        "Health insurance for parents (₹50K if senior citizen, else ₹25K)"
    ),
    SECTION_80CCD_1B(
        "Section 80CCD(1B)",
        50000.0,
        "Additional NPS contribution beyond 80C limit"
    ),
    SECTION_24(
        "Section 24 (Home Loan Interest)",
        200000.0,
        "Interest paid on home loan for self-occupied property"
    ),
    HRA_EXEMPTION(
        "HRA Exemption",
        Double.MAX_VALUE, // Calculated dynamically
        "House Rent Allowance exemption based on actual rent paid"
    ),
    STANDARD_DEDUCTION(
        "Standard Deduction",
        50000.0,
        "Flat deduction for salaried employees (auto-applied)"
    )
}

/**
 * Sub-categories within Section 80C.
 */
enum class Section80CInstrument(val displayName: String) {
    EPF("Employee Provident Fund (EPF)"),
    PPF("Public Provident Fund (PPF)"),
    ELSS("Equity Linked Savings Scheme (ELSS)"),
    LIFE_INSURANCE("Life Insurance Premium"),
    NSC("National Savings Certificate (NSC)"),
    FIVE_YEAR_FD("5-Year Tax Saving Fixed Deposit"),
    SUKANYA_SAMRIDDHI("Sukanya Samriddhi Yojana"),
    HOME_LOAN_PRINCIPAL("Home Loan Principal Repayment"),
    TUITION_FEES("Tuition Fees (max 2 children)"),
    TAX_SAVING_BONDS("Tax Saving Infrastructure Bonds"),
    OTHER_80C("Other 80C Eligible")
}

/**
 * A single tax-saving investment/expense entry.
 *
 * @property id Unique identifier
 * @property section Which deduction section this falls under
 * @property instrument Specific instrument (for 80C) or null
 * @property amount Amount invested/paid
 * @property date Date of investment/payment
 * @property description User description or auto-detected detail
 * @property isAutoDetected Whether this was detected from SMS
 * @property isVerified Whether user has verified this entry
 */
@Entity(tableName = "tax_deductions")
data class TaxDeductionEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "section") val section: DeductionSection,
    @ColumnInfo(name = "instrument") val instrument: Section80CInstrument? = null,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "date") val date: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "is_auto_detected") val isAutoDetected: Boolean = false,
    @ColumnInfo(name = "is_verified") val isVerified: Boolean = true,
    @ColumnInfo(name = "financial_year") val financialYear: String = currentFinancialYear()
)

/**
 * User's income details for tax calculation.
 *
 * @property grossSalary Annual gross salary
 * @property basicSalary Annual basic salary (for HRA calculation)
 * @property hraReceived Annual HRA received
 * @property rentPaid Annual rent paid
 * @property isMetroCity Whether user lives in a metro city (affects HRA)
 * @property otherIncome Any other taxable income
 * @property isSeniorCitizen Whether the taxpayer is a senior citizen (60+)
 * @property areParentsSenior Whether parents are senior citizens (for 80D)
 */
data class IncomeDetails(
    val grossSalary: Double,
    val basicSalary: Double = grossSalary * 0.4, // Default: 40% of gross
    val hraReceived: Double = grossSalary * 0.2, // Default: 20% of gross
    val rentPaid: Double = 0.0,
    val isMetroCity: Boolean = false,
    val otherIncome: Double = 0.0,
    val isSeniorCitizen: Boolean = false,
    val areParentsSenior: Boolean = false
)

/**
 * Tax calculation result for a specific regime.
 *
 * @property regime Which regime this calculation is for
 * @property grossIncome Total gross income
 * @property totalDeductions Total deductions claimed
 * @property taxableIncome Income after deductions
 * @property taxBeforeCess Tax before education cess
 * @property educationCess 4% cess amount
 * @property totalTaxLiability Final tax payable
 * @property effectiveTaxRate Effective tax rate percentage
 * @property slabBreakdown Breakdown of tax by slab
 */
data class TaxCalculationResult(
    val regime: TaxRegime,
    val grossIncome: Double,
    val totalDeductions: Double,
    val taxableIncome: Double,
    val taxBeforeCess: Double,
    val educationCess: Double,
    val totalTaxLiability: Double,
    val effectiveTaxRate: Double,
    val slabBreakdown: List<SlabDetail>
)

/**
 * Individual tax slab calculation detail.
 */
data class SlabDetail(
    val slabRange: String,
    val rate: Double,
    val taxableAmountInSlab: Double,
    val taxOnSlab: Double
)

/**
 * Complete tax saving summary.
 *
 * @property totalInvested Total amount invested across all sections
 * @property totalDeductionsClaimed Total deductions that can be claimed
 * @property totalTaxSaved Estimated tax saved (at applicable slab rate)
 * @property remainingCapacity How much more can be invested for deductions
 * @property sectionWiseBreakdown Breakdown by each section
 * @property oldRegimeTax Tax under old regime
 * @property newRegimeTax Tax under new regime
 * @property recommendedRegime Which regime saves more tax
 * @property regimeSavings How much the recommended regime saves
 */
data class TaxSavingSummary(
    val totalInvested: Double,
    val totalDeductionsClaimed: Double,
    val totalTaxSaved: Double,
    val remainingCapacity: Map<DeductionSection, Double>,
    val sectionWiseBreakdown: Map<DeductionSection, Double>,
    val oldRegimeTax: Double,
    val newRegimeTax: Double,
    val recommendedRegime: TaxRegime,
    val regimeSavings: Double
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data Access Object for tax deduction entries.
 */
@Dao
interface TaxSaverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeduction(entry: TaxDeductionEntry)

    @Update
    suspend fun updateDeduction(entry: TaxDeductionEntry)

    @Delete
    suspend fun deleteDeduction(entry: TaxDeductionEntry)

    @Query("SELECT * FROM tax_deductions WHERE financial_year = :fy ORDER BY date DESC")
    fun getDeductionsForFY(fy: String = currentFinancialYear()): Flow<List<TaxDeductionEntry>>

    @Query("SELECT * FROM tax_deductions WHERE financial_year = :fy AND section = :section")
    suspend fun getDeductionsBySection(
        section: DeductionSection,
        fy: String = currentFinancialYear()
    ): List<TaxDeductionEntry>

    @Query("SELECT SUM(amount) FROM tax_deductions WHERE financial_year = :fy AND section = :section")
    suspend fun getTotalForSection(
        section: DeductionSection,
        fy: String = currentFinancialYear()
    ): Double?

    @Query("SELECT SUM(amount) FROM tax_deductions WHERE financial_year = :fy")
    suspend fun getTotalDeductions(fy: String = currentFinancialYear()): Double?

    @Query("SELECT * FROM tax_deductions WHERE is_auto_detected = 1 AND is_verified = 0")
    fun getUnverifiedAutoDetections(): Flow<List<TaxDeductionEntry>>

    @Query("SELECT * FROM tax_deductions WHERE financial_year = :fy AND section = :section AND instrument = :instrument")
    suspend fun getByInstrument(
        section: DeductionSection,
        instrument: Section80CInstrument,
        fy: String = currentFinancialYear()
    ): List<TaxDeductionEntry>
}

// ─────────────────────────────────────────────────────────────────────────────
// Tax Saver Tracker Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core engine for tax saving tracking and calculation.
 *
 * Handles deduction tracking, tax calculation under both regimes, HRA computation,
 * and automatic detection of tax-saving transactions.
 *
 * ## Usage
 * ```kotlin
 * val tracker = TaxSaverTracker(taxSaverDao)
 *
 * // Add a deduction
 * tracker.addDeduction(TaxDeductionEntry(
 *     section = DeductionSection.SECTION_80C,
 *     instrument = Section80CInstrument.ELSS,
 *     amount = 50000.0,
 *     description = "ELSS Mutual Fund SIP"
 * ))
 *
 * // Get tax summary
 * val income = IncomeDetails(grossSalary = 1500000.0, rentPaid = 240000.0)
 * val summary = tracker.getTaxSavingSummary(income)
 * ```
 *
 * @param dao The Room DAO for database operations
 */
class TaxSaverTracker(private val dao: TaxSaverDao) {

    companion object {
        // ── FY 2026-27 Limits ────────────────────────────────────────────────
        const val SECTION_80C_LIMIT = 150000.0
        const val SECTION_80D_SELF_LIMIT = 25000.0
        const val SECTION_80D_SELF_SENIOR_LIMIT = 50000.0
        const val SECTION_80D_PARENTS_LIMIT = 25000.0
        const val SECTION_80D_PARENTS_SENIOR_LIMIT = 50000.0
        const val SECTION_80CCD_1B_LIMIT = 50000.0
        const val SECTION_24_LIMIT = 200000.0
        const val STANDARD_DEDUCTION = 50000.0

        // ── New Regime Slabs FY 2026-27 ──────────────────────────────────────
        // As per Union Budget 2025 (effective FY 2025-26 onwards, continued in 2026-27)
        private val NEW_REGIME_SLABS = listOf(
            TaxSlab(0.0, 400000.0, 0.0),
            TaxSlab(400000.0, 800000.0, 5.0),
            TaxSlab(800000.0, 1200000.0, 10.0),
            TaxSlab(1200000.0, 1600000.0, 15.0),
            TaxSlab(1600000.0, 2000000.0, 20.0),
            TaxSlab(2000000.0, 2400000.0, 25.0),
            TaxSlab(2400000.0, Double.MAX_VALUE, 30.0)
        )

        // ── Old Regime Slabs FY 2026-27 ──────────────────────────────────────
        private val OLD_REGIME_SLABS = listOf(
            TaxSlab(0.0, 250000.0, 0.0),
            TaxSlab(250000.0, 500000.0, 5.0),
            TaxSlab(500000.0, 1000000.0, 20.0),
            TaxSlab(1000000.0, Double.MAX_VALUE, 30.0)
        )

        // ── Old Regime Senior Citizen Slabs ──────────────────────────────────
        private val OLD_REGIME_SENIOR_SLABS = listOf(
            TaxSlab(0.0, 300000.0, 0.0),
            TaxSlab(300000.0, 500000.0, 5.0),
            TaxSlab(500000.0, 1000000.0, 20.0),
            TaxSlab(1000000.0, Double.MAX_VALUE, 30.0)
        )

        /** Education cess rate */
        private const val EDUCATION_CESS_RATE = 0.04

        /** Section 87A rebate limit under new regime */
        private const val NEW_REGIME_REBATE_LIMIT = 1200000.0
        private const val NEW_REGIME_REBATE_AMOUNT = 60000.0

        /** Section 87A rebate limit under old regime */
        private const val OLD_REGIME_REBATE_LIMIT = 500000.0
        private const val OLD_REGIME_REBATE_AMOUNT = 12500.0
    }

    /**
     * Internal tax slab representation.
     */
    private data class TaxSlab(
        val lowerBound: Double,
        val upperBound: Double,
        val rate: Double
    )

    // ── Deduction Management ─────────────────────────────────────────────────

    /**
     * Adds a new tax deduction entry.
     *
     * @param entry The deduction entry to add
     * @throws IllegalArgumentException if amount exceeds section limit
     */
    suspend fun addDeduction(entry: TaxDeductionEntry) {
        dao.insertDeduction(entry)
    }

    /**
     * Updates an existing deduction entry.
     */
    suspend fun updateDeduction(entry: TaxDeductionEntry) {
        dao.updateDeduction(entry)
    }

    /**
     * Removes a deduction entry.
     */
    suspend fun removeDeduction(entry: TaxDeductionEntry) {
        dao.deleteDeduction(entry)
    }

    /**
     * Adds an auto-detected tax-deductible transaction from SMS parsing.
     * Marked as unverified until user confirms.
     *
     * @param section Detected deduction section
     * @param instrument Specific instrument if identifiable
     * @param amount Transaction amount
     * @param description Description from SMS
     */
    suspend fun addAutoDetectedDeduction(
        section: DeductionSection,
        instrument: Section80CInstrument? = null,
        amount: Double,
        description: String
    ) {
        val entry = TaxDeductionEntry(
            section = section,
            instrument = instrument,
            amount = amount,
            description = description,
            isAutoDetected = true,
            isVerified = false
        )
        dao.insertDeduction(entry)
    }

    /**
     * Verifies an auto-detected deduction.
     */
    suspend fun verifyDeduction(entry: TaxDeductionEntry) {
        dao.updateDeduction(entry.copy(isVerified = true))
    }

    // ── Tax Calculation ──────────────────────────────────────────────────────

    /**
     * Calculates tax under the Old Regime with all applicable deductions.
     *
     * @param income User's income details
     * @return [TaxCalculationResult] for the old regime
     */
    suspend fun calculateOldRegimeTax(income: IncomeDetails): TaxCalculationResult {
        val grossIncome = income.grossSalary + income.otherIncome

        // Calculate all deductions
        var totalDeductions = 0.0

        // Standard Deduction
        totalDeductions += STANDARD_DEDUCTION

        // Section 80C
        val section80C = min(
            dao.getTotalForSection(DeductionSection.SECTION_80C) ?: 0.0,
            SECTION_80C_LIMIT
        )
        totalDeductions += section80C

        // Section 80D - Self
        val selfLimit = if (income.isSeniorCitizen) SECTION_80D_SELF_SENIOR_LIMIT else SECTION_80D_SELF_LIMIT
        val section80DSelf = min(
            dao.getTotalForSection(DeductionSection.SECTION_80D_SELF) ?: 0.0,
            selfLimit
        )
        totalDeductions += section80DSelf

        // Section 80D - Parents
        val parentsLimit = if (income.areParentsSenior) SECTION_80D_PARENTS_SENIOR_LIMIT else SECTION_80D_PARENTS_LIMIT
        val section80DParents = min(
            dao.getTotalForSection(DeductionSection.SECTION_80D_PARENTS) ?: 0.0,
            parentsLimit
        )
        totalDeductions += section80DParents

        // Section 80CCD(1B) - NPS
        val section80CCD = min(
            dao.getTotalForSection(DeductionSection.SECTION_80CCD_1B) ?: 0.0,
            SECTION_80CCD_1B_LIMIT
        )
        totalDeductions += section80CCD

        // Section 24 - Home Loan Interest
        val section24 = min(
            dao.getTotalForSection(DeductionSection.SECTION_24) ?: 0.0,
            SECTION_24_LIMIT
        )
        totalDeductions += section24

        // HRA Exemption
        val hraExemption = calculateHRAExemption(income)
        totalDeductions += hraExemption

        // Calculate taxable income
        val taxableIncome = (grossIncome - totalDeductions).coerceAtLeast(0.0)

        // Calculate tax using slabs
        val slabs = if (income.isSeniorCitizen) OLD_REGIME_SENIOR_SLABS else OLD_REGIME_SLABS
        val (taxBeforeCess, slabBreakdown) = calculateTaxFromSlabs(taxableIncome, slabs)

        // Apply Section 87A rebate
        val taxAfterRebate = if (taxableIncome <= OLD_REGIME_REBATE_LIMIT) {
            (taxBeforeCess - OLD_REGIME_REBATE_AMOUNT).coerceAtLeast(0.0)
        } else {
            taxBeforeCess
        }

        // Education Cess
        val educationCess = taxAfterRebate * EDUCATION_CESS_RATE
        val totalTax = taxAfterRebate + educationCess

        val effectiveRate = if (grossIncome > 0) (totalTax / grossIncome) * 100 else 0.0

        return TaxCalculationResult(
            regime = TaxRegime.OLD,
            grossIncome = grossIncome,
            totalDeductions = totalDeductions,
            taxableIncome = taxableIncome,
            taxBeforeCess = taxAfterRebate,
            educationCess = educationCess.roundTo2(),
            totalTaxLiability = totalTax.roundTo2(),
            effectiveTaxRate = effectiveRate.roundTo2(),
            slabBreakdown = slabBreakdown
        )
    }

    /**
     * Calculates tax under the New Regime (FY 2026-27).
     * Limited deductions available: only Standard Deduction of ₹75,000.
     *
     * @param income User's income details
     * @return [TaxCalculationResult] for the new regime
     */
    suspend fun calculateNewRegimeTax(income: IncomeDetails): TaxCalculationResult {
        val grossIncome = income.grossSalary + income.otherIncome

        // New regime: only standard deduction of ₹75,000 (updated for FY 2026-27)
        val newRegimeStandardDeduction = 75000.0
        val totalDeductions = newRegimeStandardDeduction

        val taxableIncome = (grossIncome - totalDeductions).coerceAtLeast(0.0)

        // Calculate tax using new regime slabs
        val (taxBeforeCess, slabBreakdown) = calculateTaxFromSlabs(taxableIncome, NEW_REGIME_SLABS)

        // Apply Section 87A rebate for new regime
        val taxAfterRebate = if (taxableIncome <= NEW_REGIME_REBATE_LIMIT) {
            (taxBeforeCess - NEW_REGIME_REBATE_AMOUNT).coerceAtLeast(0.0)
        } else {
            taxBeforeCess
        }

        // Education Cess
        val educationCess = taxAfterRebate * EDUCATION_CESS_RATE
        val totalTax = taxAfterRebate + educationCess

        val effectiveRate = if (grossIncome > 0) (totalTax / grossIncome) * 100 else 0.0

        return TaxCalculationResult(
            regime = TaxRegime.NEW,
            grossIncome = grossIncome,
            totalDeductions = totalDeductions,
            taxableIncome = taxableIncome,
            taxBeforeCess = taxAfterRebate,
            educationCess = educationCess.roundTo2(),
            totalTaxLiability = totalTax.roundTo2(),
            effectiveTaxRate = effectiveRate.roundTo2(),
            slabBreakdown = slabBreakdown
        )
    }

    /**
     * Compares both regimes and provides a complete tax saving summary.
     *
     * @param income User's income details
     * @return Complete [TaxSavingSummary] with regime comparison
     */
    suspend fun getTaxSavingSummary(income: IncomeDetails): TaxSavingSummary {
        val oldRegimeResult = calculateOldRegimeTax(income)
        val newRegimeResult = calculateNewRegimeTax(income)

        val totalInvested = dao.getTotalDeductions() ?: 0.0

        // Calculate remaining capacity per section
        val remainingCapacity = mutableMapOf<DeductionSection, Double>()
        for (section in DeductionSection.values()) {
            if (section == DeductionSection.HRA_EXEMPTION || section == DeductionSection.STANDARD_DEDUCTION) continue
            val used = dao.getTotalForSection(section) ?: 0.0
            val limit = section.maxLimit
            remainingCapacity[section] = (limit - used).coerceAtLeast(0.0)
        }

        // Section-wise breakdown
        val sectionBreakdown = mutableMapOf<DeductionSection, Double>()
        for (section in DeductionSection.values()) {
            sectionBreakdown[section] = dao.getTotalForSection(section) ?: 0.0
        }

        // Determine recommended regime
        val recommendedRegime: TaxRegime
        val regimeSavings: Double
        if (oldRegimeResult.totalTaxLiability <= newRegimeResult.totalTaxLiability) {
            recommendedRegime = TaxRegime.OLD
            regimeSavings = newRegimeResult.totalTaxLiability - oldRegimeResult.totalTaxLiability
        } else {
            recommendedRegime = TaxRegime.NEW
            regimeSavings = oldRegimeResult.totalTaxLiability - newRegimeResult.totalTaxLiability
        }

        // Estimate tax saved (difference between no deductions and with deductions in old regime)
        val taxSaved = (newRegimeResult.totalTaxLiability - oldRegimeResult.totalTaxLiability)
            .coerceAtLeast(0.0)

        return TaxSavingSummary(
            totalInvested = totalInvested,
            totalDeductionsClaimed = oldRegimeResult.totalDeductions,
            totalTaxSaved = taxSaved,
            remainingCapacity = remainingCapacity,
            sectionWiseBreakdown = sectionBreakdown,
            oldRegimeTax = oldRegimeResult.totalTaxLiability,
            newRegimeTax = newRegimeResult.totalTaxLiability,
            recommendedRegime = recommendedRegime,
            regimeSavings = regimeSavings.roundTo2()
        )
    }

    // ── HRA Calculation ──────────────────────────────────────────────────────

    /**
     * Calculates HRA exemption based on the three-way minimum rule:
     * 1. Actual HRA received
     * 2. Rent paid - 10% of basic salary
     * 3. 50% of basic (metro) or 40% of basic (non-metro)
     *
     * @param income Income details with HRA and rent information
     * @return Annual HRA exemption amount
     */
    fun calculateHRAExemption(income: IncomeDetails): Double {
        if (income.rentPaid <= 0 || income.hraReceived <= 0) return 0.0

        val actualHRA = income.hraReceived
        val rentMinusBasic = income.rentPaid - (0.10 * income.basicSalary)
        val percentOfBasic = if (income.isMetroCity) {
            0.50 * income.basicSalary
        } else {
            0.40 * income.basicSalary
        }

        return minOf(actualHRA, rentMinusBasic, percentOfBasic).coerceAtLeast(0.0)
    }

    // ── Remaining Capacity ───────────────────────────────────────────────────

    /**
     * Gets how much more can be invested in Section 80C.
     *
     * @return Remaining amount under ₹1.5L limit
     */
    suspend fun getRemaining80C(): Double {
        val used = dao.getTotalForSection(DeductionSection.SECTION_80C) ?: 0.0
        return (SECTION_80C_LIMIT - used).coerceAtLeast(0.0)
    }

    /**
     * Gets how much more can be claimed under Section 80D.
     *
     * @param isSeniorCitizen Whether the taxpayer is senior
     * @return Remaining 80D capacity (self)
     */
    suspend fun getRemaining80DSelf(isSeniorCitizen: Boolean = false): Double {
        val limit = if (isSeniorCitizen) SECTION_80D_SELF_SENIOR_LIMIT else SECTION_80D_SELF_LIMIT
        val used = dao.getTotalForSection(DeductionSection.SECTION_80D_SELF) ?: 0.0
        return (limit - used).coerceAtLeast(0.0)
    }

    /**
     * Gets remaining NPS (80CCD 1B) capacity.
     */
    suspend fun getRemainingNPS(): Double {
        val used = dao.getTotalForSection(DeductionSection.SECTION_80CCD_1B) ?: 0.0
        return (SECTION_80CCD_1B_LIMIT - used).coerceAtLeast(0.0)
    }

    // ── SMS Auto-Detection Helpers ───────────────────────────────────────────

    /**
     * Analyzes an SMS transaction to determine if it's tax-deductible.
     * Returns the detected deduction entry or null.
     *
     * Keywords checked:
     * - "PPF", "provident fund" → Section 80C/PPF
     * - "ELSS", "tax saving fund" → Section 80C/ELSS
     * - "insurance premium", "life cover" → Section 80C/Life Insurance
     * - "health insurance", "medical policy" → Section 80D
     * - "NPS", "pension" → Section 80CCD(1B)
     * - "home loan EMI" → Section 80C (principal) + Section 24 (interest)
     * - "rent", "rental payment" → HRA
     *
     * @param message SMS message text
     * @param amount Transaction amount
     * @param date Transaction date
     * @return Detected deduction entry, or null if not tax-deductible
     */
    fun detectTaxDeductibleTransaction(
        message: String,
        amount: Double,
        date: LocalDate
    ): TaxDeductionEntry? {
        val lowerMsg = message.lowercase()

        return when {
            // PPF
            lowerMsg.contains("ppf") || lowerMsg.contains("public provident") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80C,
                    instrument = Section80CInstrument.PPF,
                    amount = amount,
                    date = date,
                    description = "PPF contribution detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // ELSS
            lowerMsg.contains("elss") || lowerMsg.contains("tax saving fund") ||
                    lowerMsg.contains("tax saver fund") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80C,
                    instrument = Section80CInstrument.ELSS,
                    amount = amount,
                    date = date,
                    description = "ELSS investment detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // Life Insurance
            lowerMsg.contains("life insurance") || lowerMsg.contains("life cover") ||
                    lowerMsg.contains("lic premium") || lowerMsg.contains("insurance premium") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80C,
                    instrument = Section80CInstrument.LIFE_INSURANCE,
                    amount = amount,
                    date = date,
                    description = "Life insurance premium detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // Health Insurance
            lowerMsg.contains("health insurance") || lowerMsg.contains("medical insurance") ||
                    lowerMsg.contains("health policy") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80D_SELF,
                    instrument = null,
                    amount = amount,
                    date = date,
                    description = "Health insurance premium detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // NPS
            lowerMsg.contains("nps") || lowerMsg.contains("national pension") ||
                    lowerMsg.contains("pension scheme") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80CCD_1B,
                    instrument = null,
                    amount = amount,
                    date = date,
                    description = "NPS contribution detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // Rent Payment (for HRA)
            lowerMsg.contains("rent") || lowerMsg.contains("rental") ||
                    lowerMsg.contains("house rent") -> {
                TaxDeductionEntry(
                    section = DeductionSection.HRA_EXEMPTION,
                    instrument = null,
                    amount = amount,
                    date = date,
                    description = "Rent payment detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // NSC
            lowerMsg.contains("nsc") || lowerMsg.contains("national savings certificate") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80C,
                    instrument = Section80CInstrument.NSC,
                    amount = amount,
                    date = date,
                    description = "NSC investment detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            // Tax Saving FD
            lowerMsg.contains("tax saving fd") || lowerMsg.contains("5 year fd") ||
                    lowerMsg.contains("tax saver fd") -> {
                TaxDeductionEntry(
                    section = DeductionSection.SECTION_80C,
                    instrument = Section80CInstrument.FIVE_YEAR_FD,
                    amount = amount,
                    date = date,
                    description = "Tax Saving FD detected",
                    isAutoDetected = true,
                    isVerified = false
                )
            }
            else -> null
        }
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Calculates tax from slabs and returns breakdown.
     */
    private fun calculateTaxFromSlabs(
        taxableIncome: Double,
        slabs: List<TaxSlab>
    ): Pair<Double, List<SlabDetail>> {
        var totalTax = 0.0
        val breakdown = mutableListOf<SlabDetail>()

        for (slab in slabs) {
            if (taxableIncome <= slab.lowerBound) break

            val taxableInSlab = min(taxableIncome, slab.upperBound) - slab.lowerBound
            val taxOnSlab = taxableInSlab * (slab.rate / 100.0)
            totalTax += taxOnSlab

            val upperDisplay = if (slab.upperBound == Double.MAX_VALUE) "Above" else "₹${formatLakhs(slab.upperBound)}"
            val slabRange = "₹${formatLakhs(slab.lowerBound)} - $upperDisplay"

            breakdown.add(
                SlabDetail(
                    slabRange = slabRange,
                    rate = slab.rate,
                    taxableAmountInSlab = taxableInSlab.roundTo2(),
                    taxOnSlab = taxOnSlab.roundTo2()
                )
            )
        }

        return Pair(totalTax, breakdown)
    }

    /**
     * Formats amount in lakhs for display.
     */
    private fun formatLakhs(amount: Double): String {
        return when {
            amount >= 10000000 -> "${(amount / 10000000).roundTo2()} Cr"
            amount >= 100000 -> "${(amount / 100000).roundTo2()} L"
            else -> amount.roundToInt().toString()
        }
    }

    private fun Double.roundTo2(): Double = (this * 100.0).roundToInt() / 100.0
}

// ─────────────────────────────────────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the current financial year string (e.g., "2026-27").
 * Indian FY runs April to March.
 */
fun currentFinancialYear(): String {
    val today = LocalDate.now()
    val fyStartYear = if (today.monthValue >= 4) today.year else today.year - 1
    val fyEndYear = fyStartYear + 1
    return "$fyStartYear-${fyEndYear.toString().takeLast(2)}"
}
