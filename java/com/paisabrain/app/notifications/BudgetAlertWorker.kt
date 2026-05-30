package com.paisabrain.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paisabrain.app.R
import com.paisabrain.app.data.db.PaisaBrainDatabase
import com.paisabrain.app.data.preferences.UserPreferences
import com.paisabrain.app.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs daily to check if the user's spending
 * pace will exceed their monthly budget.
 *
 * Logic:
 * 1. Get total spend so far this month
 * 2. Calculate daily average spend rate
 * 3. Project total spend by month end
 * 4. If projected > budget, alert user with how much over they'll be
 *
 * Alert thresholds:
 * - 80% of budget reached → gentle nudge
 * - 100% projected overshoot → warning
 * - Already over budget → urgent alert
 *
 * Scheduling:
 * - Daily at 8 PM (end of typical spending day)
 * - Flex window: 1 hour
 *
 * Smart features:
 * - Won't alert in the first 5 days of the month (too early to predict)
 * - Won't send duplicate alerts within 24 hours
 * - Adapts message tone based on severity
 */
class BudgetAlertWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "budget_alert_worker"
        const val CHANNEL_ID = "budget_alerts"
        const val NOTIFICATION_ID_BASE = 2000
        private const val PREF_LAST_ALERT_TIME = "last_budget_alert_time"
        private const val MIN_ALERT_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val MIN_DAYS_FOR_PREDICTION = 5

        // Alert thresholds
        private const val THRESHOLD_NUDGE = 0.80    // 80% of budget
        private const val THRESHOLD_WARNING = 1.00  // 100% projected
        private const val THRESHOLD_URGENT = 1.20   // 120% projected

        /**
         * Schedules the daily budget alert worker to run at 8 PM.
         */
        fun schedule(context: Context) {
            val initialDelay = calculateInitialDelay()

            val workRequest = PeriodicWorkRequestBuilder<BudgetAlertWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 1,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("budget_alert")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Cancels the budget alert worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Calculates delay until next 8 PM.
         */
        private fun calculateInitialDelay(): Long {
            val now = LocalDateTime.now()
            val targetTime = LocalTime.of(20, 0) // 8 PM

            val targetDate = if (now.toLocalTime().isBefore(targetTime)) {
                now.toLocalDate()
            } else {
                now.toLocalDate().plusDays(1)
            }

            val targetDateTime = LocalDateTime.of(targetDate, targetTime)
            return Duration.between(now, targetDateTime).toMillis().coerceAtLeast(0)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Skip if too early in the month
            val today = LocalDate.now()
            if (today.dayOfMonth < MIN_DAYS_FOR_PREDICTION) {
                return Result.success()
            }

            // Skip if recently alerted
            if (wasRecentlyAlerted()) {
                return Result.success()
            }

            // Get user's monthly budget
            val prefs = UserPreferences(applicationContext)
            val monthlyBudget = prefs.getMonthlyBudget()
            if (monthlyBudget <= 0) {
                // No budget set, skip
                return Result.success()
            }

            // Calculate current spending pace
            val spendingAnalysis = analyzeSpendingPace(monthlyBudget)

            // Determine if alert is needed
            when {
                spendingAnalysis.alreadyOverBudget -> {
                    showAlert(AlertLevel.URGENT, spendingAnalysis, monthlyBudget)
                }
                spendingAnalysis.projectedRatio >= THRESHOLD_WARNING -> {
                    showAlert(AlertLevel.WARNING, spendingAnalysis, monthlyBudget)
                }
                spendingAnalysis.currentRatio >= THRESHOLD_NUDGE -> {
                    showAlert(AlertLevel.NUDGE, spendingAnalysis, monthlyBudget)
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Analyzes current spending pace relative to budget.
     */
    private suspend fun analyzeSpendingPace(monthlyBudget: Double): SpendingAnalysis {
        val database = PaisaBrainDatabase.getInstance(applicationContext)
        val dao = database.transactionDao()

        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today)
        val startOfMonth = yearMonth.atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        // Get total spend this month
        val totalSpent = dao.getTotalSpendBetween(startOfMonth, now) ?: 0.0

        // Calculate projections
        val dayOfMonth = today.dayOfMonth
        val daysInMonth = yearMonth.lengthOfMonth()
        val daysRemaining = daysInMonth - dayOfMonth

        val dailyAverage = if (dayOfMonth > 0) totalSpent / dayOfMonth else 0.0
        val projectedTotal = totalSpent + (dailyAverage * daysRemaining)

        val currentRatio = totalSpent / monthlyBudget
        val projectedRatio = projectedTotal / monthlyBudget

        // Get top spending category this month
        val transactions = dao.getTransactionsBetween(startOfMonth, now)
        val topCategory = transactions
            .groupBy { it.category }
            .maxByOrNull { (_, txns) -> txns.sumOf { it.amount } }
            ?.key ?: "general"

        val topCategoryAmount = transactions
            .filter { it.category == topCategory }
            .sumOf { it.amount }

        return SpendingAnalysis(
            totalSpent = totalSpent,
            projectedTotal = projectedTotal,
            dailyAverage = dailyAverage,
            currentRatio = currentRatio,
            projectedRatio = projectedRatio,
            daysRemaining = daysRemaining,
            alreadyOverBudget = totalSpent > monthlyBudget,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount,
            amountOverBudget = if (totalSpent > monthlyBudget) totalSpent - monthlyBudget else 0.0,
            projectedOverage = if (projectedTotal > monthlyBudget) projectedTotal - monthlyBudget else 0.0
        )
    }

    /**
     * Shows a budget alert notification with appropriate severity.
     */
    private fun showAlert(
        level: AlertLevel,
        analysis: SpendingAnalysis,
        budget: Double
    ) {
        createNotificationChannel()

        // Check notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val (title, message) = buildAlertContent(level, analysis, budget)

        // Intent to open app
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("tab", "money")
            putExtra("show_budget", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (level) {
            AlertLevel.URGENT -> NotificationCompat.PRIORITY_HIGH
            AlertLevel.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            AlertLevel.NUDGE -> NotificationCompat.PRIORITY_LOW
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_budget)
            .setContentTitle(title)
            .setContentText(message.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID_BASE + level.ordinal, notification)

        // Record alert time
        recordAlertTime()
    }

    /**
     * Builds notification title and message based on alert level.
     */
    private fun buildAlertContent(
        level: AlertLevel,
        analysis: SpendingAnalysis,
        budget: Double
    ): Pair<String, String> {
        val ctx = applicationContext
        val formatAmount = { amount: Double ->
            "₹${String.format("%,.0f", amount)}"
        }

        return when (level) {
            AlertLevel.URGENT -> {
                val title = ctx.getString(R.string.budget_alert_urgent_title)
                val message = ctx.getString(
                    R.string.budget_alert_urgent_message,
                    formatAmount(analysis.totalSpent),
                    formatAmount(budget),
                    formatAmount(analysis.amountOverBudget),
                    analysis.daysRemaining
                )
                title to message
            }
            AlertLevel.WARNING -> {
                val title = ctx.getString(R.string.budget_alert_warning_title)
                val message = ctx.getString(
                    R.string.budget_alert_warning_message,
                    formatAmount(analysis.totalSpent),
                    formatAmount(budget),
                    formatAmount(analysis.projectedTotal),
                    formatAmount(analysis.projectedOverage),
                    analysis.topCategory
                )
                title to message
            }
            AlertLevel.NUDGE -> {
                val title = ctx.getString(R.string.budget_alert_nudge_title)
                val message = ctx.getString(
                    R.string.budget_alert_nudge_message,
                    ((analysis.currentRatio * 100).toInt()).toString(),
                    formatAmount(analysis.dailyAverage),
                    analysis.daysRemaining
                )
                title to message
            }
        }
    }

    /**
     * Creates the budget alerts notification channel.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_budget),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_budget_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks if an alert was sent within the last 24 hours.
     */
    private fun wasRecentlyAlerted(): Boolean {
        val prefs = applicationContext.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
        val lastAlertTime = prefs.getLong(PREF_LAST_ALERT_TIME, 0)
        return (System.currentTimeMillis() - lastAlertTime) < MIN_ALERT_INTERVAL_MS
    }

    /**
     * Records the current time as last alert time.
     */
    private fun recordAlertTime() {
        applicationContext.getSharedPreferences("budget_alerts", Context.MODE_PRIVATE)
            .edit()
            .putLong(PREF_LAST_ALERT_TIME, System.currentTimeMillis())
            .apply()
    }
}

/**
 * Alert severity levels.
 */
enum class AlertLevel {
    NUDGE,      // 80% budget used
    WARNING,    // Projected to exceed budget
    URGENT      // Already over budget
}

/**
 * Analysis of current month's spending pace.
 */
data class SpendingAnalysis(
    val totalSpent: Double,
    val projectedTotal: Double,
    val dailyAverage: Double,
    val currentRatio: Double,
    val projectedRatio: Double,
    val daysRemaining: Int,
    val alreadyOverBudget: Boolean,
    val topCategory: String,
    val topCategoryAmount: Double,
    val amountOverBudget: Double,
    val projectedOverage: Double
)
