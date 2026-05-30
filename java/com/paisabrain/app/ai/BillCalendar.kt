package com.paisabrain.app.ai

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * # BillCalendar
 *
 * An intelligent bill tracking and calendar system that auto-detects recurring bills
 * from transaction history, predicts upcoming due dates, sends smart reminders, and
 * tracks bill inflation over time.
 *
 * ## Features
 * - Scans transaction history to detect recurring bills
 * - Stores bill name, usual amount, usual date, last paid, next expected
 * - Monthly bill calendar view generation
 * - Smart notifications: reminds 2 days before each bill
 * - Total monthly bills & percentage of income tracking
 * - Year-over-year bill inflation detection
 * - Detects bill amount increases ("Your utility bill ↑15% vs 3-month avg")
 * - Categories: utilities, insurance, subscriptions, EMIs, rent, memberships
 *
 * ## No Brand References
 * Uses generic categories like "streaming service", "food delivery", "utility provider"
 *
 * @since 1.0.0
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Categories for recurring bills.
 */
enum class BillCategory(val displayName: String, val icon: String) {
    RENT("Rent", "🏠"),
    ELECTRICITY("Electricity", "⚡"),
    WATER("Water Supply", "💧"),
    GAS("Gas / Cooking Fuel", "🔥"),
    INTERNET("Internet / Broadband", "🌐"),
    MOBILE_RECHARGE("Mobile Recharge", "📱"),
    DTH_CABLE("TV / Cable / DTH", "📺"),
    STREAMING("Streaming Service", "🎬"),
    MUSIC("Music Subscription", "🎵"),
    CLOUD_STORAGE("Cloud Storage", "☁️"),
    FOOD_DELIVERY("Food Delivery Subscription", "🍕"),
    GROCERY_SUBSCRIPTION("Grocery Subscription", "🛒"),
    GYM_FITNESS("Gym / Fitness", "💪"),
    INSURANCE_HEALTH("Health Insurance", "🏥"),
    INSURANCE_LIFE("Life Insurance", "🛡️"),
    INSURANCE_VEHICLE("Vehicle Insurance", "🚗"),
    HOME_LOAN_EMI("Home Loan EMI", "🏡"),
    PERSONAL_LOAN_EMI("Personal Loan EMI", "💳"),
    VEHICLE_LOAN_EMI("Vehicle Loan EMI", "🚘"),
    EDUCATION_LOAN_EMI("Education Loan EMI", "🎓"),
    CREDIT_CARD_BILL("Credit Card Bill", "💳"),
    MUTUAL_FUND_SIP("Mutual Fund SIP", "📈"),
    SCHOOL_FEES("School / Tuition Fees", "📚"),
    SOCIETY_MAINTENANCE("Society Maintenance", "🏢"),
    NEWSPAPER("Newspaper / Magazine", "📰"),
    SOFTWARE_SUBSCRIPTION("Software Subscription", "💻"),
    MEMBERSHIP("Membership / Club", "🎫"),
    OTHER("Other Recurring Bill", "📋")
}

/**
 * Status of a bill relative to its due date.
 */
enum class BillStatus(val displayName: String) {
    UPCOMING("Upcoming"),
    DUE_TODAY("Due Today"),
    OVERDUE("Overdue"),
    PAID("Paid"),
    UNKNOWN("Unknown")
}

/**
 * Room entity representing a detected/tracked recurring bill.
 *
 * @property id Unique identifier
 * @property name Bill/merchant name (generic, no brands)
 * @property category Bill category
 * @property usualAmount Average/typical bill amount
 * @property lastPaidAmount Amount paid last time
 * @property usualDayOfMonth Day of month when bill is typically due (1-31)
 * @property lastPaidDate Date when this bill was last paid
 * @property nextExpectedDate Next expected due date
 * @property isAutoDetected Whether this was auto-detected from SMS
 * @property isActive Whether this bill is still active
 * @property threeMonthAverage Rolling 3-month average amount
 * @property twelveMonthAgo Amount from 12 months ago (for YoY comparison)
 * @property reminderDaysBefore Days before due date to send reminder
 * @property notes User notes
 */
@Entity(tableName = "bills")
data class BillEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: BillCategory,
    @ColumnInfo(name = "usual_amount") val usualAmount: Double,
    @ColumnInfo(name = "last_paid_amount") val lastPaidAmount: Double = 0.0,
    @ColumnInfo(name = "usual_day_of_month") val usualDayOfMonth: Int,
    @ColumnInfo(name = "last_paid_date") val lastPaidDate: LocalDate? = null,
    @ColumnInfo(name = "next_expected_date") val nextExpectedDate: LocalDate,
    @ColumnInfo(name = "is_auto_detected") val isAutoDetected: Boolean = false,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "three_month_average") val threeMonthAverage: Double = 0.0,
    @ColumnInfo(name = "twelve_month_ago") val twelveMonthAgo: Double = 0.0,
    @ColumnInfo(name = "reminder_days_before") val reminderDaysBefore: Int = 2,
    @ColumnInfo(name = "notes") val notes: String? = null
)

/**
 * Historical record of a bill payment for tracking changes over time.
 */
@Entity(tableName = "bill_payments")
data class BillPaymentRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "bill_id") val billId: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "paid_date") val paidDate: LocalDate,
    @ColumnInfo(name = "was_on_time") val wasOnTime: Boolean = true
)

/**
 * Bill increase alert when a bill's amount spikes.
 *
 * @property billName Name of the bill
 * @property previousAverage 3-month rolling average
 * @property currentAmount Latest bill amount
 * @property increasePercent Percentage increase
 * @property increaseAmount Absolute increase
 * @property alertMessage Human-readable alert message
 */
data class BillIncreaseAlert(
    val billName: String,
    val category: BillCategory,
    val previousAverage: Double,
    val currentAmount: Double,
    val increasePercent: Double,
    val increaseAmount: Double,
    val alertMessage: String
)

/**
 * Monthly bill calendar data for UI rendering.
 *
 * @property month The month this calendar is for
 * @property bills All bills due in this month, sorted by date
 * @property totalAmount Total bill amount for the month
 * @property paidCount Number of bills already paid
 * @property pendingCount Number of bills still pending
 * @property overdueCount Number of overdue bills
 */
data class MonthlyBillCalendar(
    val month: YearMonth,
    val bills: List<CalendarBillItem>,
    val totalAmount: Double,
    val paidCount: Int,
    val pendingCount: Int,
    val overdueCount: Int
)

/**
 * A single bill item in the calendar view.
 */
data class CalendarBillItem(
    val billId: String,
    val name: String,
    val category: BillCategory,
    val amount: Double,
    val dueDate: LocalDate,
    val status: BillStatus,
    val daysUntilDue: Long,
    val reminderDate: LocalDate
)

/**
 * Bill summary statistics.
 *
 * @property totalMonthlyBills Total of all monthly recurring bills
 * @property billCount Number of active bills
 * @property percentageOfIncome Bills as percentage of monthly income
 * @property yearOverYearInflation Average bill inflation vs last year
 * @property highestBill The largest monthly bill
 * @property categoryBreakdown Bills grouped by category with totals
 * @property upcomingInNext7Days Bills due in the next 7 days
 */
data class BillSummary(
    val totalMonthlyBills: Double,
    val billCount: Int,
    val percentageOfIncome: Double,
    val yearOverYearInflation: Double,
    val highestBill: BillEntry?,
    val categoryBreakdown: Map<BillCategory, Double>,
    val upcomingInNext7Days: List<BillEntry>
)

/**
 * Notification/reminder model for upcoming bills.
 */
data class BillReminder(
    val billId: String,
    val billName: String,
    val amount: Double,
    val dueDate: LocalDate,
    val daysUntilDue: Long,
    val message: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Room DAO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Data Access Object for bill calendar operations.
 */
@Dao
interface BillCalendarDao {

    // ── Bills ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntry)

    @Update
    suspend fun updateBill(bill: BillEntry)

    @Delete
    suspend fun deleteBill(bill: BillEntry)

    @Query("SELECT * FROM bills WHERE is_active = 1 ORDER BY next_expected_date ASC")
    fun getActiveBills(): Flow<List<BillEntry>>

    @Query("SELECT * FROM bills WHERE is_active = 1 ORDER BY next_expected_date ASC")
    suspend fun getActiveBillsSnapshot(): List<BillEntry>

    @Query("SELECT * FROM bills WHERE is_active = 1 AND category = :category")
    fun getBillsByCategory(category: BillCategory): Flow<List<BillEntry>>

    @Query("SELECT * FROM bills WHERE is_active = 1 AND next_expected_date BETWEEN :startDate AND :endDate ORDER BY next_expected_date ASC")
    suspend fun getBillsDueInRange(startDate: LocalDate, endDate: LocalDate): List<BillEntry>

    @Query("SELECT * FROM bills WHERE is_active = 1 AND next_expected_date <= :date AND last_paid_date < next_expected_date")
    suspend fun getOverdueBills(date: LocalDate = LocalDate.now()): List<BillEntry>

    @Query("SELECT SUM(usual_amount) FROM bills WHERE is_active = 1")
    suspend fun getTotalMonthlyBills(): Double?

    @Query("SELECT COUNT(*) FROM bills WHERE is_active = 1")
    suspend fun getActiveBillCount(): Int

    // ── Payment Records ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentRecord(record: BillPaymentRecord)

    @Query("SELECT * FROM bill_payments WHERE bill_id = :billId ORDER BY paid_date DESC")
    suspend fun getPaymentHistory(billId: String): List<BillPaymentRecord>

    @Query("SELECT * FROM bill_payments WHERE bill_id = :billId ORDER BY paid_date DESC LIMIT 3")
    suspend fun getLastThreePayments(billId: String): List<BillPaymentRecord>

    @Query("SELECT * FROM bill_payments WHERE bill_id = :billId AND paid_date >= :fromDate ORDER BY paid_date DESC")
    suspend fun getPaymentsSince(billId: String, fromDate: LocalDate): List<BillPaymentRecord>

    @Query("SELECT AVG(amount) FROM bill_payments WHERE bill_id = :billId ORDER BY paid_date DESC LIMIT 3")
    suspend fun getThreeMonthAverage(billId: String): Double?
}

// ─────────────────────────────────────────────────────────────────────────────
// Bill Calendar Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core engine for bill detection, tracking, and calendar generation.
 *
 * ## Usage
 * ```kotlin
 * val calendar = BillCalendarEngine(billCalendarDao)
 *
 * // Auto-detect bills from transaction history
 * val detected = calendar.detectRecurringBills(transactions)
 *
 * // Get monthly calendar
 * val monthView = calendar.getMonthlyCalendar(YearMonth.now())
 *
 * // Get upcoming reminders
 * val reminders = calendar.getUpcomingReminders()
 *
 * // Check for bill increases
 * val alerts = calendar.detectBillIncreases()
 * ```
 *
 * @param dao The Room DAO for database operations
 */
class BillCalendarEngine(private val dao: BillCalendarDao) {

    companion object {
        /** Minimum occurrences to classify as a recurring bill */
        private const val MIN_BILL_OCCURRENCES = 3

        /** Maximum variance in day-of-month to consider same bill */
        private const val MAX_DAY_VARIANCE = 4

        /** Percentage increase to trigger an alert */
        private const val INCREASE_ALERT_THRESHOLD_PERCENT = 10.0

        /** Default reminder: 2 days before due date */
        private const val DEFAULT_REMINDER_DAYS = 2
    }

    // ── Bill Detection ───────────────────────────────────────────────────────

    /**
     * Scans transaction history to detect recurring bills automatically.
     *
     * Logic:
     * 1. Groups transactions by merchant/recipient
     * 2. For each group with 3+ transactions:
     *    - Checks if amounts are similar (within 20% variance)
     *    - Checks if timing is monthly (25-35 day gaps)
     * 3. Creates bill entries for detected patterns
     *
     * @param transactions Historical debit transactions (3-6 months)
     * @return List of newly detected bills
     */
    suspend fun detectRecurringBills(
        transactions: List<HistoricalTransaction>
    ): List<BillEntry> {
        val detectedBills = mutableListOf<BillEntry>()

        // Only consider debit transactions
        val debits = transactions.filter { !it.isCredit }

        // Group by merchant
        val grouped = debits.groupBy { it.merchant.lowercase().trim() }

        for ((merchant, txns) in grouped) {
            if (txns.size < MIN_BILL_OCCURRENCES) continue

            val sorted = txns.sortedBy { it.date }

            // Check if timing is monthly
            val gaps = sorted.zipWithNext().map { (a, b) ->
                ChronoUnit.DAYS.between(a.date, b.date)
            }
            val avgGap = gaps.average()
            if (avgGap < 25 || avgGap > 35) continue

            // Check amount consistency
            val amounts = sorted.map { it.amount }
            val avgAmount = amounts.average()
            val maxVariance = amounts.map { abs(it - avgAmount) / avgAmount }.max()
            if (maxVariance > 0.30) continue // Allow up to 30% variance for variable bills

            // Determine usual day of month
            val days = sorted.map { it.date.dayOfMonth }
            val usualDay = days.average().roundToInt()

            // Determine category
            val category = inferBillCategory(merchant, avgAmount)

            // Calculate next expected date
            val lastDate = sorted.last().date
            val nextDate = calculateNextDueDate(lastDate, usualDay)

            // Calculate 3-month average
            val recentAmounts = amounts.takeLast(3)
            val threeMonthAvg = recentAmounts.average()

            // Get 12-month-ago amount for YoY tracking
            val twelveMonthAgo = if (amounts.size >= 12) amounts[amounts.size - 12] else 0.0

            val bill = BillEntry(
                name = merchant.capitalizeWords(),
                category = category,
                usualAmount = avgAmount.roundTo2(),
                lastPaidAmount = amounts.last(),
                usualDayOfMonth = usualDay,
                lastPaidDate = lastDate,
                nextExpectedDate = nextDate,
                isAutoDetected = true,
                threeMonthAverage = threeMonthAvg.roundTo2(),
                twelveMonthAgo = twelveMonthAgo
            )

            detectedBills.add(bill)
            dao.insertBill(bill)

            // Store payment history
            for (txn in sorted) {
                dao.insertPaymentRecord(
                    BillPaymentRecord(
                        billId = bill.id,
                        amount = txn.amount,
                        paidDate = txn.date
                    )
                )
            }
        }

        return detectedBills
    }

    // ── Bill Management ──────────────────────────────────────────────────────

    /**
     * Adds a manually entered bill.
     */
    suspend fun addBill(bill: BillEntry) {
        dao.insertBill(bill)
    }

    /**
     * Updates a bill entry.
     */
    suspend fun updateBill(bill: BillEntry) {
        dao.updateBill(bill)
    }

    /**
     * Marks a bill as paid and records the payment.
     *
     * @param billId The bill that was paid
     * @param amount Amount paid
     * @param paidDate Date of payment
     */
    suspend fun markBillPaid(billId: String, amount: Double, paidDate: LocalDate = LocalDate.now()) {
        val bills = dao.getActiveBillsSnapshot()
        val bill = bills.find { it.id == billId } ?: return

        // Record payment
        dao.insertPaymentRecord(
            BillPaymentRecord(
                billId = billId,
                amount = amount,
                paidDate = paidDate
            )
        )

        // Update bill with new last paid info and calculate next due date
        val nextDate = calculateNextDueDate(paidDate, bill.usualDayOfMonth)
        val newThreeMonthAvg = dao.getThreeMonthAverage(billId) ?: amount

        dao.updateBill(
            bill.copy(
                lastPaidAmount = amount,
                lastPaidDate = paidDate,
                nextExpectedDate = nextDate,
                threeMonthAverage = newThreeMonthAvg
            )
        )
    }

    /**
     * Deactivates a bill (e.g., cancelled subscription).
     */
    suspend fun deactivateBill(bill: BillEntry) {
        dao.updateBill(bill.copy(isActive = false))
    }

    // ── Calendar Generation ──────────────────────────────────────────────────

    /**
     * Generates the monthly bill calendar for a given month.
     *
     * @param month The month to generate calendar for
     * @return [MonthlyBillCalendar] with all bills and statistics
     */
    suspend fun getMonthlyCalendar(month: YearMonth = YearMonth.now()): MonthlyBillCalendar {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        val today = LocalDate.now()

        val bills = dao.getBillsDueInRange(startDate, endDate)

        val calendarItems = bills.map { bill ->
            val dueDate = bill.nextExpectedDate
            val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
            val status = determineBillStatus(bill, today)
            val reminderDate = dueDate.minusDays(bill.reminderDaysBefore.toLong())

            CalendarBillItem(
                billId = bill.id,
                name = bill.name,
                category = bill.category,
                amount = bill.usualAmount,
                dueDate = dueDate,
                status = status,
                daysUntilDue = daysUntilDue,
                reminderDate = reminderDate
            )
        }

        val paidCount = calendarItems.count { it.status == BillStatus.PAID }
        val pendingCount = calendarItems.count { it.status == BillStatus.UPCOMING || it.status == BillStatus.DUE_TODAY }
        val overdueCount = calendarItems.count { it.status == BillStatus.OVERDUE }

        return MonthlyBillCalendar(
            month = month,
            bills = calendarItems.sortedBy { it.dueDate },
            totalAmount = calendarItems.sumOf { it.amount },
            paidCount = paidCount,
            pendingCount = pendingCount,
            overdueCount = overdueCount
        )
    }

    // ── Reminders ────────────────────────────────────────────────────────────

    /**
     * Gets upcoming bill reminders (bills due within reminder window).
     *
     * @return List of bills that should trigger a reminder today
     */
    suspend fun getUpcomingReminders(): List<BillReminder> {
        val today = LocalDate.now()
        val bills = dao.getActiveBillsSnapshot()

        return bills
            .filter { bill ->
                val reminderDate = bill.nextExpectedDate.minusDays(bill.reminderDaysBefore.toLong())
                val daysUntilDue = ChronoUnit.DAYS.between(today, bill.nextExpectedDate)
                // Remind if we're within the reminder window and bill hasn't been paid
                today >= reminderDate && daysUntilDue >= 0 &&
                        (bill.lastPaidDate == null || bill.lastPaidDate!! < bill.nextExpectedDate)
            }
            .map { bill ->
                val daysUntilDue = ChronoUnit.DAYS.between(today, bill.nextExpectedDate)
                BillReminder(
                    billId = bill.id,
                    billName = bill.name,
                    amount = bill.usualAmount,
                    dueDate = bill.nextExpectedDate,
                    daysUntilDue = daysUntilDue,
                    message = buildReminderMessage(bill.name, bill.usualAmount, daysUntilDue)
                )
            }
    }

    // ── Bill Increase Detection ──────────────────────────────────────────────

    /**
     * Detects bills whose latest amount exceeds the 3-month average by the threshold.
     *
     * @return List of bill increase alerts
     */
    suspend fun detectBillIncreases(): List<BillIncreaseAlert> {
        val alerts = mutableListOf<BillIncreaseAlert>()
        val bills = dao.getActiveBillsSnapshot()

        for (bill in bills) {
            if (bill.threeMonthAverage <= 0 || bill.lastPaidAmount <= 0) continue

            val increasePercent = ((bill.lastPaidAmount - bill.threeMonthAverage) / bill.threeMonthAverage) * 100

            if (increasePercent >= INCREASE_ALERT_THRESHOLD_PERCENT) {
                val increaseAmount = bill.lastPaidAmount - bill.threeMonthAverage
                alerts.add(
                    BillIncreaseAlert(
                        billName = bill.name,
                        category = bill.category,
                        previousAverage = bill.threeMonthAverage,
                        currentAmount = bill.lastPaidAmount,
                        increasePercent = increasePercent.roundTo2(),
                        increaseAmount = increaseAmount.roundTo2(),
                        alertMessage = "Your ${bill.name} bill increased ${increasePercent.roundToInt()}% " +
                                "vs your 3-month average (₹${bill.threeMonthAverage.roundToInt()} → " +
                                "₹${bill.lastPaidAmount.roundToInt()})"
                    )
                )
            }
        }

        return alerts.sortedByDescending { it.increasePercent }
    }

    /**
     * Calculates year-over-year bill inflation across all bills.
     *
     * @return Average YoY inflation percentage across all tracked bills
     */
    suspend fun calculateYearOverYearInflation(): Double {
        val bills = dao.getActiveBillsSnapshot()
        val billsWithHistory = bills.filter { it.twelveMonthAgo > 0 }

        if (billsWithHistory.isEmpty()) return 0.0

        val inflations = billsWithHistory.map { bill ->
            ((bill.usualAmount - bill.twelveMonthAgo) / bill.twelveMonthAgo) * 100
        }

        return inflations.average().roundTo2()
    }

    // ── Summary & Statistics ─────────────────────────────────────────────────

    /**
     * Gets comprehensive bill summary.
     *
     * @param monthlyIncome User's monthly income (for percentage calculation)
     * @return [BillSummary] with all statistics
     */
    suspend fun getBillSummary(monthlyIncome: Double): BillSummary {
        val bills = dao.getActiveBillsSnapshot()
        val totalBills = dao.getTotalMonthlyBills() ?: 0.0
        val billCount = dao.getActiveBillCount()

        val percentOfIncome = if (monthlyIncome > 0) {
            (totalBills / monthlyIncome) * 100.0
        } else 0.0

        val yoyInflation = calculateYearOverYearInflation()

        val highestBill = bills.maxByOrNull { it.usualAmount }

        // Category breakdown
        val categoryBreakdown = bills
            .groupBy { it.category }
            .mapValues { (_, billsInCategory) -> billsInCategory.sumOf { it.usualAmount } }

        // Upcoming in next 7 days
        val today = LocalDate.now()
        val next7Days = today.plusDays(7)
        val upcoming = dao.getBillsDueInRange(today, next7Days)

        return BillSummary(
            totalMonthlyBills = totalBills.roundTo2(),
            billCount = billCount,
            percentageOfIncome = percentOfIncome.roundTo2(),
            yearOverYearInflation = yoyInflation,
            highestBill = highestBill,
            categoryBreakdown = categoryBreakdown,
            upcomingInNext7Days = upcoming
        )
    }

    // ── Private Helpers ──────────────────────────────────────────────────────

    /**
     * Determines the current status of a bill.
     */
    private fun determineBillStatus(bill: BillEntry, today: LocalDate): BillStatus {
        val dueDate = bill.nextExpectedDate

        // Check if already paid
        if (bill.lastPaidDate != null && bill.lastPaidDate!! >= dueDate) {
            return BillStatus.PAID
        }

        return when {
            today > dueDate -> BillStatus.OVERDUE
            today == dueDate -> BillStatus.DUE_TODAY
            else -> BillStatus.UPCOMING
        }
    }

    /**
     * Calculates the next due date based on last payment and usual day.
     */
    private fun calculateNextDueDate(lastDate: LocalDate, usualDay: Int): LocalDate {
        val nextMonth = lastDate.plusMonths(1)
        val adjustedDay = usualDay.coerceAtMost(nextMonth.lengthOfMonth())
        return nextMonth.withDayOfMonth(adjustedDay)
    }

    /**
     * Infers bill category from merchant name and amount patterns.
     */
    private fun inferBillCategory(merchant: String, amount: Double): BillCategory {
        val lower = merchant.lowercase()

        return when {
            // Utilities
            lower.contains("electric") || lower.contains("power") ||
                    lower.contains("discom") -> BillCategory.ELECTRICITY
            lower.contains("water") || lower.contains("jal") -> BillCategory.WATER
            lower.contains("gas") || lower.contains("lpg") ||
                    lower.contains("piped gas") -> BillCategory.GAS
            lower.contains("broadband") || lower.contains("internet") ||
                    lower.contains("wifi") || lower.contains("fiber") -> BillCategory.INTERNET
            lower.contains("mobile") || lower.contains("recharge") ||
                    lower.contains("prepaid") || lower.contains("postpaid") -> BillCategory.MOBILE_RECHARGE
            lower.contains("dth") || lower.contains("cable") ||
                    lower.contains("tata play") -> BillCategory.DTH_CABLE

            // Subscriptions
            lower.contains("streaming") || lower.contains("ott") ||
                    lower.contains("video") -> BillCategory.STREAMING
            lower.contains("music") || lower.contains("audio") -> BillCategory.MUSIC
            lower.contains("cloud") || lower.contains("storage") -> BillCategory.CLOUD_STORAGE
            lower.contains("food") || lower.contains("delivery") -> BillCategory.FOOD_DELIVERY
            lower.contains("grocery") || lower.contains("grocer") -> BillCategory.GROCERY_SUBSCRIPTION

            // Insurance
            lower.contains("health") && lower.contains("insur") -> BillCategory.INSURANCE_HEALTH
            lower.contains("life") && lower.contains("insur") -> BillCategory.INSURANCE_LIFE
            lower.contains("motor") || lower.contains("vehicle insur") -> BillCategory.INSURANCE_VEHICLE

            // EMIs (typically larger amounts)
            lower.contains("home loan") || lower.contains("housing") -> BillCategory.HOME_LOAN_EMI
            lower.contains("personal loan") -> BillCategory.PERSONAL_LOAN_EMI
            lower.contains("car loan") || lower.contains("vehicle loan") -> BillCategory.VEHICLE_LOAN_EMI
            lower.contains("education loan") || lower.contains("study loan") -> BillCategory.EDUCATION_LOAN_EMI
            lower.contains("emi") -> {
                // Guess based on amount
                when {
                    amount > 20000 -> BillCategory.HOME_LOAN_EMI
                    amount > 10000 -> BillCategory.PERSONAL_LOAN_EMI
                    else -> BillCategory.PERSONAL_LOAN_EMI
                }
            }

            // Rent
            lower.contains("rent") || lower.contains("landlord") -> BillCategory.RENT

            // Maintenance
            lower.contains("maintenance") || lower.contains("society") -> BillCategory.SOCIETY_MAINTENANCE

            // SIP
            lower.contains("sip") || lower.contains("mutual fund") -> BillCategory.MUTUAL_FUND_SIP

            // Fitness
            lower.contains("gym") || lower.contains("fitness") ||
                    lower.contains("yoga") -> BillCategory.GYM_FITNESS

            // Credit Card
            lower.contains("credit card") || lower.contains("card payment") -> BillCategory.CREDIT_CARD_BILL

            // School
            lower.contains("school") || lower.contains("tuition") ||
                    lower.contains("college") -> BillCategory.SCHOOL_FEES

            // Software
            lower.contains("software") || lower.contains("saas") ||
                    lower.contains("app subscription") -> BillCategory.SOFTWARE_SUBSCRIPTION

            // Default
            else -> BillCategory.OTHER
        }
    }

    /**
     * Builds a human-friendly reminder message.
     */
    private fun buildReminderMessage(name: String, amount: Double, daysUntil: Long): String {
        return when (daysUntil) {
            0L -> "⚡ $name (₹${amount.roundToInt()}) is due TODAY!"
            1L -> "⏰ $name (₹${amount.roundToInt()}) is due TOMORROW"
            else -> "📅 $name (₹${amount.roundToInt()}) is due in $daysUntil days"
        }
    }

    /**
     * Extension to capitalize each word in a string.
     */
    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }

    /**
     * Extension to round to 2 decimal places.
     */
    private fun Double.roundTo2(): Double = (this * 100.0).roundToInt() / 100.0
}
