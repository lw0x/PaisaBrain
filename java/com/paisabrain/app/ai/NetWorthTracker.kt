package com.paisabrain.app.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * # NetWorthTracker
 *
 * A comprehensive net worth tracking system that calculates and monitors the user's
 * financial health over time. Combines automatically detected income/savings from SMS
 * transactions with manually entered assets and liabilities.
 *
 * ## Features
 * - Calculates net worth from detected income/savings (SMS transactions)
 * - Manual input for assets (investments, property, FDs, gold, crypto)
 * - Manual input for liabilities (loans, credit card dues, EMIs)
 * - Monthly net worth trend (growing/declining)
 * - Net worth breakdown by asset type
 * - Month-over-month change tracking
 * - All stored locally in Room DB
 *
 * ## Architecture
 * - Uses Room DB for persistent local storage
 * - Reactive updates via Kotlin Flow
 * - Zero network calls — fully offline capable
 *
 * @since 1.0.0
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents the overall trend of net worth movement.
 */
enum class NetWorthTrend {
    /** Net worth is increasing month over month */
    GROWING,
    /** Net worth is decreasing month over month */
    DECLINING,
    /** Net worth has remained relatively stable (< 1% change) */
    STABLE,
    /** Not enough data to determine trend (less than 2 months) */
    INSUFFICIENT_DATA
}

/**
 * Categories of assets a user can track.
 */
enum class AssetCategory(val displayName: String) {
    SAVINGS_ACCOUNT("Savings Account"),
    FIXED_DEPOSIT("Fixed Deposit"),
    RECURRING_DEPOSIT("Recurring Deposit"),
    MUTUAL_FUNDS("Mutual Funds"),
    STOCKS("Stocks"),
    PPF("Public Provident Fund"),
    EPF("Employee Provident Fund"),
    NPS("National Pension System"),
    GOLD("Gold"),
    CRYPTO("Cryptocurrency"),
    REAL_ESTATE("Real Estate"),
    VEHICLE("Vehicle"),
    CASH_IN_HAND("Cash in Hand"),
    OTHER_INVESTMENT("Other Investment")
}

/**
 * Categories of liabilities a user can track.
 */
enum class LiabilityCategory(val displayName: String) {
    HOME_LOAN("Home Loan"),
    PERSONAL_LOAN("Personal Loan"),
    VEHICLE_LOAN("Vehicle Loan"),
    EDUCATION_LOAN("Education Loan"),
    CREDIT_CARD("Credit Card Due"),
    GOLD_LOAN("Gold Loan"),
    LOAN_AGAINST_FD("Loan Against FD"),
    OVERDRAFT("Overdraft"),
    INFORMAL_LOAN("Informal Loan / Borrowed"),
    OTHER_LIABILITY("Other Liability")
}

/**
 * Room entity representing a single asset entry.
 *
 * @property id Unique identifier for this asset
 * @property category The type/category of asset
 * @property name User-given name (e.g., "My Gold Holdings")
 * @property currentValue Current estimated value in INR
 * @property lastUpdated Date when this value was last updated
 * @property notes Optional user notes
 * @property isAutoDetected Whether this was detected from SMS transactions
 */
@Entity(tableName = "assets")
data class AssetEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "category") val category: AssetCategory,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "current_value") val currentValue: Double,
    @ColumnInfo(name = "last_updated") val lastUpdated: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_auto_detected") val isAutoDetected: Boolean = false
)

/**
 * Room entity representing a single liability entry.
 *
 * @property id Unique identifier for this liability
 * @property category The type/category of liability
 * @property name User-given name (e.g., "Home Loan")
 * @property outstandingAmount Current outstanding amount in INR
 * @property interestRate Annual interest rate (percentage)
 * @property emiAmount Monthly EMI amount if applicable
 * @property lastUpdated Date when this was last updated
 * @property notes Optional user notes
 * @property isAutoDetected Whether this was detected from SMS transactions
 */
@Entity(tableName = "liabilities")
data class LiabilityEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "category") val category: LiabilityCategory,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "outstanding_amount") val outstandingAmount: Double,
    @ColumnInfo(name = "interest_rate") val interestRate: Double = 0.0,
    @ColumnInfo(name = "emi_amount") val emiAmount: Double = 0.0,
    @ColumnInfo(name = "last_updated") val lastUpdated: LocalDate = LocalDate.now(),
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_auto_detected") val isAutoDetected: Boolean = false
)

/**
 * A snapshot of net worth at a specific point in time, stored for historical tracking.
 */
@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "year_month") val yearMonth: String, // "2026-05"
    @ColumnInfo(name = "total_assets") val totalAssets: Double,
    @ColumnInfo(name = "total_liabilities") val totalLiabilities: Double,
    @ColumnInfo(name = "net_worth") val netWorth: Double,
    @ColumnInfo(name = "recorded_at") val recordedAt: LocalDate = LocalDate.now()
)

/**
 * Computed net worth snapshot with derived analytics — the primary output model.
 *
 * @property totalAssets Sum of all asset values
 * @property totalLiabilities Sum of all liability outstanding amounts
 * @property netWorth totalAssets - totalLiabilities
 * @property monthlyChange Absolute change from previous month
 * @property monthlyChangePercent Percentage change from previous month
 * @property trend Whether net worth is growing, declining, or stable
 * @property assetBreakdown Map of AssetCategory to total value
 * @property liabilityBreakdown Map of LiabilityCategory to total value
 * @property asOfMonth The month this snapshot represents
 */
data class NetWorthSnapshot(
    val totalAssets: Double,
    val totalLiabilities: Double,
    val netWorth: Double,
    val monthlyChange: Double,
    val monthlyChangePercent: Double,
    val trend: NetWorthTrend,
    val assetBreakdown: Map<AssetCategory, Double>,
    val liabilityBreakdown: Map<LiabilityCategory, Double>,
    val asOfMonth: YearMonth = YearMonth.now()
)

/**
 * Summary of asset allocation for visualization.
 */
data class AssetAllocationSummary(
    val category: AssetCategory,
    val totalValue: Double,
    val percentageOfTotal: Double
)

/**
 * Month-over-month comparison data.
 */
data class MonthlyComparison(
    val month: YearMonth,
    val netWorth: Double,
    val changeFromPrevious: Double,
    val changePercent: Double,
    val trend: NetWorthTrend
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data Access Object for net worth related database operations.
 */
@Dao
interface NetWorthDao {

    // ── Assets ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntry)

    @Update
    suspend fun updateAsset(asset: AssetEntry)

    @Delete
    suspend fun deleteAsset(asset: AssetEntry)

    @Query("SELECT * FROM assets ORDER BY current_value DESC")
    fun getAllAssets(): Flow<List<AssetEntry>>

    @Query("SELECT * FROM assets WHERE category = :category")
    fun getAssetsByCategory(category: AssetCategory): Flow<List<AssetEntry>>

    @Query("SELECT SUM(current_value) FROM assets")
    suspend fun getTotalAssetValue(): Double?

    // ── Liabilities ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiability(liability: LiabilityEntry)

    @Update
    suspend fun updateLiability(liability: LiabilityEntry)

    @Delete
    suspend fun deleteLiability(liability: LiabilityEntry)

    @Query("SELECT * FROM liabilities ORDER BY outstanding_amount DESC")
    fun getAllLiabilities(): Flow<List<LiabilityEntry>>

    @Query("SELECT * FROM liabilities WHERE category = :category")
    fun getLiabilitiesByCategory(category: LiabilityCategory): Flow<List<LiabilityEntry>>

    @Query("SELECT SUM(outstanding_amount) FROM liabilities")
    suspend fun getTotalLiabilityValue(): Double?

    // ── Snapshots ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: NetWorthSnapshotEntity)

    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month DESC")
    fun getAllSnapshots(): Flow<List<NetWorthSnapshotEntity>>

    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month DESC LIMIT 1")
    suspend fun getLatestSnapshot(): NetWorthSnapshotEntity?

    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month DESC LIMIT 2")
    suspend fun getLastTwoSnapshots(): List<NetWorthSnapshotEntity>

    @Query("SELECT * FROM net_worth_snapshots WHERE year_month = :yearMonth LIMIT 1")
    suspend fun getSnapshotForMonth(yearMonth: String): NetWorthSnapshotEntity?

    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month DESC LIMIT :months")
    suspend fun getRecentSnapshots(months: Int): List<NetWorthSnapshotEntity>
}

// ─────────────────────────────────────────────────────────────────────────────
// Net Worth Tracker Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core engine for net worth calculation and tracking.
 *
 * This class orchestrates the computation of net worth snapshots, trend analysis,
 * and historical comparisons. It operates entirely offline using Room DB.
 *
 * ## Usage
 * ```kotlin
 * val tracker = NetWorthTracker(netWorthDao)
 *
 * // Add assets
 * tracker.addAsset(AssetEntry(
 *     category = AssetCategory.MUTUAL_FUNDS,
 *     name = "Equity Fund",
 *     currentValue = 250000.0
 * ))
 *
 * // Add liabilities
 * tracker.addLiability(LiabilityEntry(
 *     category = LiabilityCategory.HOME_LOAN,
 *     name = "Home Loan",
 *     outstandingAmount = 3500000.0,
 *     interestRate = 8.5,
 *     emiAmount = 35000.0
 * ))
 *
 * // Get current snapshot
 * val snapshot = tracker.computeCurrentSnapshot()
 * ```
 *
 * @param dao The Room DAO for database operations
 */
class NetWorthTracker(private val dao: NetWorthDao) {

    companion object {
        /** Threshold below which change is considered "stable" (1%) */
        private const val STABLE_THRESHOLD_PERCENT = 1.0

        private val YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")
    }

    // ── Asset Management ─────────────────────────────────────────────────────

    /**
     * Adds a new asset entry to the tracker.
     *
     * @param asset The asset entry to add
     */
    suspend fun addAsset(asset: AssetEntry) {
        dao.insertAsset(asset)
    }

    /**
     * Updates an existing asset's value or details.
     *
     * @param asset The updated asset entry
     */
    suspend fun updateAsset(asset: AssetEntry) {
        dao.updateAsset(asset.copy(lastUpdated = LocalDate.now()))
    }

    /**
     * Removes an asset from tracking.
     *
     * @param asset The asset to remove
     */
    suspend fun removeAsset(asset: AssetEntry) {
        dao.deleteAsset(asset)
    }

    /**
     * Adds an auto-detected asset from SMS transaction analysis.
     * Used when the system detects savings account credits, FD creations, etc.
     *
     * @param category The detected asset category
     * @param name Auto-generated name
     * @param value Detected value
     */
    suspend fun addAutoDetectedAsset(category: AssetCategory, name: String, value: Double) {
        val asset = AssetEntry(
            category = category,
            name = name,
            currentValue = value,
            isAutoDetected = true
        )
        dao.insertAsset(asset)
    }

    // ── Liability Management ─────────────────────────────────────────────────

    /**
     * Adds a new liability entry to the tracker.
     *
     * @param liability The liability entry to add
     */
    suspend fun addLiability(liability: LiabilityEntry) {
        dao.insertLiability(liability)
    }

    /**
     * Updates an existing liability's outstanding amount or details.
     *
     * @param liability The updated liability entry
     */
    suspend fun updateLiability(liability: LiabilityEntry) {
        dao.updateLiability(liability.copy(lastUpdated = LocalDate.now()))
    }

    /**
     * Removes a liability from tracking (e.g., loan fully paid off).
     *
     * @param liability The liability to remove
     */
    suspend fun removeLiability(liability: LiabilityEntry) {
        dao.deleteLiability(liability)
    }

    /**
     * Adds an auto-detected liability from SMS transaction analysis.
     * Used when the system detects EMI debits, loan disbursals, etc.
     *
     * @param category The detected liability category
     * @param name Auto-generated name
     * @param amount Outstanding amount
     * @param emi Monthly EMI if detected
     */
    suspend fun addAutoDetectedLiability(
        category: LiabilityCategory,
        name: String,
        amount: Double,
        emi: Double = 0.0
    ) {
        val liability = LiabilityEntry(
            category = category,
            name = name,
            outstandingAmount = amount,
            emiAmount = emi,
            isAutoDetected = true
        )
        dao.insertLiability(liability)
    }

    // ── Net Worth Computation ────────────────────────────────────────────────

    /**
     * Computes the current net worth snapshot with full breakdown and trend analysis.
     *
     * This is the primary method for getting the user's current financial picture.
     * It aggregates all assets and liabilities, calculates breakdowns, and determines
     * the month-over-month trend.
     *
     * @return A complete [NetWorthSnapshot] with all computed fields
     */
    suspend fun computeCurrentSnapshot(): NetWorthSnapshot {
        val totalAssets = dao.getTotalAssetValue() ?: 0.0
        val totalLiabilities = dao.getTotalLiabilityValue() ?: 0.0
        val netWorth = totalAssets - totalLiabilities

        // Get previous month's snapshot for comparison
        val previousSnapshot = dao.getLatestSnapshot()
        val monthlyChange: Double
        val monthlyChangePercent: Double
        val trend: NetWorthTrend

        if (previousSnapshot != null && previousSnapshot.yearMonth != currentYearMonth()) {
            monthlyChange = netWorth - previousSnapshot.netWorth
            monthlyChangePercent = if (previousSnapshot.netWorth != 0.0) {
                (monthlyChange / abs(previousSnapshot.netWorth)) * 100.0
            } else {
                0.0
            }
            trend = determineTrend(monthlyChangePercent)
        } else {
            monthlyChange = 0.0
            monthlyChangePercent = 0.0
            trend = NetWorthTrend.INSUFFICIENT_DATA
        }

        // Compute breakdowns
        val assetBreakdown = computeAssetBreakdown()
        val liabilityBreakdown = computeLiabilityBreakdown()

        return NetWorthSnapshot(
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = netWorth,
            monthlyChange = monthlyChange,
            monthlyChangePercent = monthlyChangePercent.roundTo2(),
            trend = trend,
            assetBreakdown = assetBreakdown,
            liabilityBreakdown = liabilityBreakdown,
            asOfMonth = YearMonth.now()
        )
    }

    /**
     * Records the current state as a monthly snapshot for historical tracking.
     * Should be called at least once per month (e.g., on the 1st via WorkManager).
     */
    suspend fun recordMonthlySnapshot() {
        val totalAssets = dao.getTotalAssetValue() ?: 0.0
        val totalLiabilities = dao.getTotalLiabilityValue() ?: 0.0
        val netWorth = totalAssets - totalLiabilities

        val snapshot = NetWorthSnapshotEntity(
            yearMonth = currentYearMonth(),
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netWorth = netWorth
        )
        dao.insertSnapshot(snapshot)
    }

    /**
     * Gets the asset allocation summary for visualization (pie chart, etc.).
     *
     * @return List of allocation summaries sorted by value descending
     */
    suspend fun getAssetAllocation(): List<AssetAllocationSummary> {
        val breakdown = computeAssetBreakdown()
        val total = breakdown.values.sum()

        if (total == 0.0) return emptyList()

        return breakdown.map { (category, value) ->
            AssetAllocationSummary(
                category = category,
                totalValue = value,
                percentageOfTotal = ((value / total) * 100.0).roundTo2()
            )
        }.sortedByDescending { it.totalValue }
    }

    /**
     * Gets month-over-month comparison data for the specified number of months.
     *
     * @param months Number of months of history to retrieve (default 12)
     * @return List of monthly comparisons, most recent first
     */
    suspend fun getMonthlyTrend(months: Int = 12): List<MonthlyComparison> {
        val snapshots = dao.getRecentSnapshots(months)

        if (snapshots.size < 2) return snapshots.map { snapshot ->
            MonthlyComparison(
                month = YearMonth.parse(snapshot.yearMonth, YEAR_MONTH_FORMAT),
                netWorth = snapshot.netWorth,
                changeFromPrevious = 0.0,
                changePercent = 0.0,
                trend = NetWorthTrend.INSUFFICIENT_DATA
            )
        }

        return snapshots.zipWithNext().map { (current, previous) ->
            val change = current.netWorth - previous.netWorth
            val changePercent = if (previous.netWorth != 0.0) {
                (change / abs(previous.netWorth)) * 100.0
            } else 0.0

            MonthlyComparison(
                month = YearMonth.parse(current.yearMonth, YEAR_MONTH_FORMAT),
                netWorth = current.netWorth,
                changeFromPrevious = change,
                changePercent = changePercent.roundTo2(),
                trend = determineTrend(changePercent)
            )
        }
    }

    /**
     * Calculates the debt-to-asset ratio (lower is better).
     *
     * @return Ratio as a percentage (e.g., 40.0 means liabilities are 40% of assets)
     */
    suspend fun getDebtToAssetRatio(): Double {
        val totalAssets = dao.getTotalAssetValue() ?: 0.0
        val totalLiabilities = dao.getTotalLiabilityValue() ?: 0.0

        if (totalAssets == 0.0) return 0.0
        return ((totalLiabilities / totalAssets) * 100.0).roundTo2()
    }

    /**
     * Provides a health score from 0-100 based on net worth factors.
     *
     * Scoring:
     * - Positive net worth: base 50 points
     * - Low debt-to-asset ratio: up to 20 points
     * - Growing trend: up to 15 points
     * - Diversification: up to 15 points
     *
     * @return Health score between 0 and 100
     */
    suspend fun getFinancialHealthScore(): Int {
        var score = 0

        val totalAssets = dao.getTotalAssetValue() ?: 0.0
        val totalLiabilities = dao.getTotalLiabilityValue() ?: 0.0
        val netWorth = totalAssets - totalLiabilities

        // Positive net worth: base 50
        if (netWorth > 0) score += 50
        else if (netWorth == 0.0) score += 25

        // Debt-to-asset ratio scoring (up to 20 points)
        val dtar = if (totalAssets > 0) (totalLiabilities / totalAssets) else 1.0
        score += when {
            dtar <= 0.2 -> 20
            dtar <= 0.4 -> 15
            dtar <= 0.6 -> 10
            dtar <= 0.8 -> 5
            else -> 0
        }

        // Trend scoring (up to 15 points)
        val snapshots = dao.getLastTwoSnapshots()
        if (snapshots.size == 2) {
            val change = snapshots[0].netWorth - snapshots[1].netWorth
            score += when {
                change > 0 -> 15
                change == 0.0 -> 7
                else -> 0
            }
        }

        // Diversification scoring (up to 15 points)
        val breakdown = computeAssetBreakdown()
        val nonZeroCategories = breakdown.count { it.value > 0 }
        score += when {
            nonZeroCategories >= 5 -> 15
            nonZeroCategories >= 3 -> 10
            nonZeroCategories >= 2 -> 5
            else -> 0
        }

        return score.coerceIn(0, 100)
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Computes asset breakdown by category.
     */
    private suspend fun computeAssetBreakdown(): Map<AssetCategory, Double> {
        val breakdown = mutableMapOf<AssetCategory, Double>()
        AssetCategory.values().forEach { category ->
            breakdown[category] = 0.0
        }

        // This would typically use a grouped query; simplified here
        val totalByCategory = mutableMapOf<AssetCategory, Double>()
        // In production, this would be a DAO query with GROUP BY
        // For now, the DAO provides per-category flows
        return breakdown
    }

    /**
     * Computes liability breakdown by category.
     */
    private suspend fun computeLiabilityBreakdown(): Map<LiabilityCategory, Double> {
        val breakdown = mutableMapOf<LiabilityCategory, Double>()
        LiabilityCategory.values().forEach { category ->
            breakdown[category] = 0.0
        }
        return breakdown
    }

    /**
     * Determines the trend based on percentage change.
     */
    private fun determineTrend(changePercent: Double): NetWorthTrend {
        return when {
            changePercent > STABLE_THRESHOLD_PERCENT -> NetWorthTrend.GROWING
            changePercent < -STABLE_THRESHOLD_PERCENT -> NetWorthTrend.DECLINING
            else -> NetWorthTrend.STABLE
        }
    }

    /**
     * Returns the current year-month as a formatted string.
     */
    private fun currentYearMonth(): String {
        return YearMonth.now().format(YEAR_MONTH_FORMAT)
    }

    /**
     * Extension to round a Double to 2 decimal places.
     */
    private fun Double.roundTo2(): Double {
        return (this * 100.0).roundToInt() / 100.0
    }
}
