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
import com.paisabrain.app.engine.InsightEngine
import com.paisabrain.app.ui.MainActivity
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs every Monday at 9 AM to generate
 * a witty roast based on the previous week's spending and show it
 * as a notification.
 *
 * The roast is fun, non-judgmental, and uses templates from
 * roast_templates.json with actual spending data filled in.
 *
 * Scheduling:
 * - Periodic: every 7 days
 * - Initial delay calculated to align with next Monday 9 AM
 * - Flex window: 2 hours (can fire between 9-11 AM)
 *
 * Notification:
 * - Channel: "weekly_roast" (importance: default)
 * - Expandable big text style
 * - Taps open the Money Brain tab
 */
class WeeklyRoastWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weekly_roast_worker"
        const val CHANNEL_ID = "weekly_roast"
        const val NOTIFICATION_ID = 1001

        /**
         * Schedules the weekly roast worker to run every Monday at 9 AM.
         */
        fun schedule(context: Context) {
            val initialDelay = calculateInitialDelay()

            val workRequest = PeriodicWorkRequestBuilder<WeeklyRoastWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
                flexTimeInterval = 2,
                flexTimeIntervalUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("roast")
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        /**
         * Cancels the weekly roast worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Calculates delay until next Monday 9 AM.
         */
        private fun calculateInitialDelay(): Long {
            val now = LocalDateTime.now()
            val targetTime = LocalTime.of(9, 0)

            var nextMonday = now.toLocalDate()
            while (nextMonday.dayOfWeek != DayOfWeek.MONDAY || (nextMonday == now.toLocalDate() && now.toLocalTime()
                    .isAfter(targetTime))
            ) {
                nextMonday = nextMonday.plusDays(1)
            }

            val targetDateTime = LocalDateTime.of(nextMonday, targetTime)
            return Duration.between(now, targetDateTime).toMillis().coerceAtLeast(0)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Create notification channel
            createNotificationChannel()

            // Get last week's transactions
            val lastWeekTransactions = getLastWeekTransactions()

            if (lastWeekTransactions.isEmpty()) {
                // No transactions last week, skip roast
                return Result.success()
            }

            // Generate roast
            val insightEngine = InsightEngine(applicationContext)
            val roast = insightEngine.generateRoast(lastWeekTransactions)

            if (roast.isBlank()) {
                return Result.success()
            }

            // Show notification
            showRoastNotification(roast)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    /**
     * Retrieves all transactions from the past 7 days.
     */
    private suspend fun getLastWeekTransactions(): List<com.paisabrain.app.data.db.entity.TransactionEntity> {
        val database = PaisaBrainDatabase.getInstance(applicationContext)
        val dao = database.transactionDao()

        val weekAgo = LocalDate.now().minusDays(7)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        return dao.getTransactionsBetween(weekAgo, now)
    }

    /**
     * Creates the notification channel for weekly roasts.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_roast),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_roast_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows the roast as an expandable notification.
     */
    private fun showRoastNotification(roast: String) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return // Permission not granted, skip notification
            }
        }

        // Intent to open app on tap
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("tab", "money")
            putExtra("show_roast", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_roast)
            .setContentTitle(applicationContext.getString(R.string.roast_notification_title))
            .setContentText(roast.take(100)) // Short preview
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(roast)
                    .setBigContentTitle(
                        applicationContext.getString(R.string.roast_notification_title)
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID, notification)
    }
}
