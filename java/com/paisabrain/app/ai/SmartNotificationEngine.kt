package com.paisabrain.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Contextually intelligent notification system that delivers timely,
 * relevant financial alerts to help users stay on track with their budget.
 *
 * Supports morning briefings, bill reminders, overspend alerts, positive
 * reinforcement, ghost charge detection, and witty weekly summaries.
 * All messages use generic terms — no brand or company references.
 */
class SmartNotificationEngine {

    // ─────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Represents a smart notification to be delivered to the user.
     *
     * @property id Unique notification identifier.
     * @property type The category/trigger type of this notification.
     * @property title Short notification title.
     * @property body Full notification message.
     * @property priority Priority level determining display behavior.
     * @property scheduledTime When this notification should be delivered.
     * @property triggerCondition Description of what triggered this notification.
     * @property actionLabel Optional action button text.
     * @property actionDeepLink Optional deep link for the action button.
     * @property isDismissed Whether the user dismissed this notification.
     * @property isSnoozed Whether the user snoozed this notification.
     * @property snoozeUntil If snoozed, when to re-show.
     * @property metadata Additional context data as key-value pairs.
     */
    data class SmartNotification(
        val id: String,
        val type: NotificationType,
        val title: String,
        val body: String,
        val priority: NotificationPriority,
        val scheduledTime: LocalDateTime,
        val triggerCondition: String,
        val actionLabel: String? = null,
        val actionDeepLink: String? = null,
        val isDismissed: Boolean = false,
        val isSnoozed: Boolean = false,
        val snoozeUntil: LocalDateTime? = null,
        val metadata: Map<String, String> = emptyMap()
    )

    /**
     * User's notification preferences.
     *
     * @property dndStartTime Do-Not-Disturb start time.
     * @property dndEndTime Do-Not-Disturb end time.
     * @property enableMorningBriefing Whether to send morning briefings.
     * @property enableWeekendAlerts Whether to send pre-weekend warnings.
     * @property enableBillReminders Whether to send bill due reminders.
     * @property enableOverspendAlerts Whether to send real-time overspend alerts.
     * @property enablePositiveReinforcement Whether to celebrate savings milestones.
     * @property enableWeeklyRoast Whether to send witty weekly spending summaries.
     * @property enableGhostChargeAlerts Whether to alert on unused subscription charges.
     * @property enablePrivacyAlerts Whether to warn about sensor/data access.
     * @property maxDailyNotifications Maximum notifications per day.
     */
    data class NotificationPreferences(
        val dndStartTime: LocalTime = LocalTime.of(22, 0),
        val dndEndTime: LocalTime = LocalTime.of(7, 0),
        val enableMorningBriefing: Boolean = true,
        val enableWeekendAlerts: Boolean = true,
        val enableBillReminders: Boolean = true,
        val enableOverspendAlerts: Boolean = true,
        val enablePositiveReinforcement: Boolean = true,
        val enableWeeklyRoast: Boolean = true,
        val enableGhostChargeAlerts: Boolean = true,
        val enablePrivacyAlerts: Boolean = false,
        val maxDailyNotifications: Int = 5
    )

    /**
     * Financial snapshot used to generate contextual notifications.
     *
     * @property currentBalance Current account balance.
     * @property safeToSpendToday Calculated safe daily spending amount.
     * @property totalSpentToday Amount already spent today.
     * @property totalSpentThisWeek Amount spent this week.
     * @property averageWeekendSpend Historical average weekend spending.
     * @property remainingMonthlyBudget Budget remaining for the rest of the month.
     * @property daysLeftInMonth Days remaining in current month.
     * @property upcomingBills Bills due within the next 7 days.
     * @property detectedSalaryDay Detected regular salary credit day (1-31).
     * @property lastWeekTotalSpend Total spending last week.
     * @property lastWeekBudget Budget that was allocated for last week.
     * @property unusedSubscriptions Subscriptions not used in 30+ days.
     */
    data class FinancialSnapshot(
        val currentBalance: Double,
        val safeToSpendToday: Double,
        val totalSpentToday: Double,
        val totalSpentThisWeek: Double,
        val averageWeekendSpend: Double,
        val remainingMonthlyBudget: Double,
        val daysLeftInMonth: Int,
        val upcomingBills: List<UpcomingBill> = emptyList(),
        val detectedSalaryDay: Int? = null,
        val lastWeekTotalSpend: Double = 0.0,
        val lastWeekBudget: Double = 0.0,
        val unusedSubscriptions: List<UnusedSubscriptionInfo> = emptyList()
    )

    /**
     * An upcoming bill with due date and amount.
     */
    data class UpcomingBill(
        val name: String, // Generic: "Electricity Bill", "Internet Bill"
        val amount: Double,
        val dueDate: LocalDate
    )

    /**
     * A subscription detected as unused.
     */
    data class UnusedSubscriptionInfo(
        val categoryLabel: String,
        val monthlyAmount: Double,
        val daysSinceLastUsed: Long
    )

    /**
     * Types of smart notifications the engine can generate.
     */
    enum class NotificationType {
        MORNING_BRIEFING,
        PRE_WEEKEND_ALERT,
        BILL_DUE_REMINDER,
        OVERSPEND_ALERT,
        SALARY_ANTICIPATION,
        MONTH_END_COUNTDOWN,
        POSITIVE_REINFORCEMENT,
        GHOST_CHARGE_DETECTION,
        WEEKLY_ROAST,
        PRIVACY_ALERT
    }

    /**
     * Notification priority levels.
     */
    enum class NotificationPriority(val displayName: String) {
        LOW("Low — silent delivery"),
        MEDIUM("Medium — standard notification"),
        HIGH("High — heads-up display"),
        URGENT("Urgent — persistent alert")
    }

    // ─────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────

    private var preferences = NotificationPreferences()
    private val pendingNotifications = mutableListOf<SmartNotification>()
    private val deliveredToday = mutableListOf<SmartNotification>()
    private var notificationIdCounter = 0L

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates user notification preferences.
     */
    fun updatePreferences(newPreferences: NotificationPreferences) {
        preferences = newPreferences
    }

    /**
     * Evaluates all notification triggers and generates pending notifications.
     *
     * @param snapshot Current financial state of the user.
     * @param now Current date-time (injectable for testing).
     * @return List of notifications that should be delivered now.
     */
    fun evaluateTriggers(
        snapshot: FinancialSnapshot,
        now: LocalDateTime = LocalDateTime.now()
    ): List<SmartNotification> {
        val notifications = mutableListOf<SmartNotification>()

        if (isInDnd(now.toLocalTime())) return emptyList()

        // Morning Briefing — 9:00 AM daily
        if (preferences.enableMorningBriefing && isTriggerTime(now, 9, 0)) {
            notifications.add(generateMorningBriefing(snapshot, now))
        }

        // Pre-Weekend Alert — Friday 6:00 PM
        if (preferences.enableWeekendAlerts &&
            now.dayOfWeek == DayOfWeek.FRIDAY &&
            isTriggerTime(now, 18, 0)
        ) {
            notifications.add(generatePreWeekendAlert(snapshot, now))
        }

        // Bill Due Reminders — 2 days before due date
        if (preferences.enableBillReminders) {
            snapshot.upcomingBills
                .filter { ChronoUnit.DAYS.between(now.toLocalDate(), it.dueDate) == 2L }
                .forEach { bill ->
                    notifications.add(generateBillReminder(bill, now))
                }
        }

        // Overspend Alert — real-time when daily spend exceeds safe pace
        if (preferences.enableOverspendAlerts &&
            snapshot.totalSpentToday > snapshot.safeToSpendToday
        ) {
            notifications.add(generateOverspendAlert(snapshot, now))
        }

        // Salary Anticipation — day before salary day
        if (snapshot.detectedSalaryDay != null) {
            val tomorrow = now.toLocalDate().plusDays(1)
            if (tomorrow.dayOfMonth == snapshot.detectedSalaryDay) {
                notifications.add(generateSalaryAnticipation(snapshot, now))
            }
        }

        // Month-End Countdown — 5 days before month end
        if (snapshot.daysLeftInMonth == 5) {
            notifications.add(generateMonthEndCountdown(snapshot, now))
        }

        // Positive Reinforcement — Monday for under-budget week
        if (preferences.enablePositiveReinforcement &&
            now.dayOfWeek == DayOfWeek.MONDAY &&
            isTriggerTime(now, 9, 30) &&
            snapshot.lastWeekTotalSpend < snapshot.lastWeekBudget
        ) {
            notifications.add(generatePositiveReinforcement(snapshot, now))
        }

        // Ghost Charge Detection — when unused subscription charges
        if (preferences.enableGhostChargeAlerts && snapshot.unusedSubscriptions.isNotEmpty()) {
            snapshot.unusedSubscriptions.forEach { sub ->
                notifications.add(generateGhostChargeAlert(sub, now))
            }
        }

        // Weekly Roast — Monday 9:00 AM
        if (preferences.enableWeeklyRoast &&
            now.dayOfWeek == DayOfWeek.MONDAY &&
            isTriggerTime(now, 9, 0)
        ) {
            notifications.add(generateWeeklyRoast(snapshot, now))
        }

        // Respect daily notification limit
        val remainingSlots = preferences.maxDailyNotifications - deliveredToday.size
        val toDeliver = notifications.take(remainingSlots.coerceAtLeast(0))

        pendingNotifications.addAll(toDeliver)
        deliveredToday.addAll(toDeliver)

        return toDeliver
    }

    /**
     * Handles user dismissing a notification.
     *
     * @param notificationId The notification to dismiss.
     */
    fun dismissNotification(notificationId: String) {
        pendingNotifications.removeAll { it.id == notificationId }
    }

    /**
     * Handles user snoozing a notification.
     *
     * @param notificationId The notification to snooze.
     * @param snoozeDurationMinutes How long to snooze (default 60 minutes).
     */
    fun snoozeNotification(notificationId: String, snoozeDurationMinutes: Long = 60) {
        val index = pendingNotifications.indexOfFirst { it.id == notificationId }
        if (index >= 0) {
            val notification = pendingNotifications[index]
            pendingNotifications[index] = notification.copy(
                isSnoozed = true,
                snoozeUntil = LocalDateTime.now().plusMinutes(snoozeDurationMinutes)
            )
        }
    }

    /**
     * Gets notifications that were snoozed and are now due to re-display.
     */
    fun getSnoozedNotificationsDue(now: LocalDateTime = LocalDateTime.now()): List<SmartNotification> {
        return pendingNotifications.filter { notification ->
            notification.isSnoozed &&
                notification.snoozeUntil != null &&
                now.isAfter(notification.snoozeUntil)
        }
    }

    /**
     * Resets the daily delivery counter (call at midnight).
     */
    fun resetDailyCounter() {
        deliveredToday.clear()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Notification Generators
    // ─────────────────────────────────────────────────────────────────────

    private fun generateMorningBriefing(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val billsSummary = if (snapshot.upcomingBills.isNotEmpty()) {
            val nextBill = snapshot.upcomingBills.minByOrNull { it.dueDate }!!
            "\n📋 Next bill: ${nextBill.name} — ₹${formatAmount(nextBill.amount)} due ${nextBill.dueDate}"
        } else ""

        val body = """
            |☀️ Good morning! Here's your financial snapshot:
            |💰 Balance: ₹${formatAmount(snapshot.currentBalance)}
            |✅ Safe to spend today: ₹${formatAmount(snapshot.safeToSpendToday)}
            |📅 ${snapshot.daysLeftInMonth} days left this month$billsSummary
        """.trimMargin()

        return createNotification(
            type = NotificationType.MORNING_BRIEFING,
            title = "Morning Financial Briefing",
            body = body,
            priority = NotificationPriority.LOW,
            scheduledTime = now,
            triggerCondition = "Daily at 9:00 AM"
        )
    }

    private fun generatePreWeekendAlert(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val body = """
            |🎉 Weekend ahead! A friendly heads-up:
            |📊 Your average weekend spend: ₹${formatAmount(snapshot.averageWeekendSpend)}
            |💰 Remaining budget: ₹${formatAmount(snapshot.remainingMonthlyBudget)}
            |
            |Tip: Set a weekend limit to avoid Monday regrets!
        """.trimMargin()

        return createNotification(
            type = NotificationType.PRE_WEEKEND_ALERT,
            title = "Weekend Spending Alert",
            body = body,
            priority = NotificationPriority.MEDIUM,
            scheduledTime = now,
            triggerCondition = "Friday at 6:00 PM",
            actionLabel = "Set Weekend Budget"
        )
    }

    private fun generateBillReminder(
        bill: UpcomingBill,
        now: LocalDateTime
    ): SmartNotification {
        val body = """
            |⏰ Bill due in 2 days!
            |📋 ${bill.name}: ₹${formatAmount(bill.amount)}
            |📅 Due: ${bill.dueDate}
            |
            |Make sure you have sufficient balance.
        """.trimMargin()

        return createNotification(
            type = NotificationType.BILL_DUE_REMINDER,
            title = "Bill Due: ${bill.name}",
            body = body,
            priority = NotificationPriority.HIGH,
            scheduledTime = now,
            triggerCondition = "2 days before bill due date",
            actionLabel = "View Bill Details"
        )
    }

    private fun generateOverspendAlert(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val overspentBy = snapshot.totalSpentToday - snapshot.safeToSpendToday
        val body = """
            |⚠️ Daily spending limit exceeded!
            |💸 Spent today: ₹${formatAmount(snapshot.totalSpentToday)}
            |🎯 Safe limit: ₹${formatAmount(snapshot.safeToSpendToday)}
            |📈 Over by: ₹${formatAmount(overspentBy)}
            |
            |Consider pausing non-essential spending for the rest of today.
        """.trimMargin()

        return createNotification(
            type = NotificationType.OVERSPEND_ALERT,
            title = "⚠️ Overspending Alert",
            body = body,
            priority = NotificationPriority.HIGH,
            scheduledTime = now,
            triggerCondition = "Real-time: daily spend exceeds safe pace"
        )
    }

    private fun generateSalaryAnticipation(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val body = """
            |💰 Salary expected tomorrow!
            |📊 Current balance: ₹${formatAmount(snapshot.currentBalance)}
            |
            |Plan your budget allocations:
            |• Bills & essentials first
            |• Savings & investments next
            |• Remaining for discretionary spending
        """.trimMargin()

        return createNotification(
            type = NotificationType.SALARY_ANTICIPATION,
            title = "Salary Day Tomorrow!",
            body = body,
            priority = NotificationPriority.LOW,
            scheduledTime = now,
            triggerCondition = "Day before detected salary date",
            actionLabel = "Plan Budget"
        )
    }

    private fun generateMonthEndCountdown(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val dailyBudgetRemaining = if (snapshot.daysLeftInMonth > 0) {
            snapshot.remainingMonthlyBudget / snapshot.daysLeftInMonth
        } else 0.0

        val body = """
            |📅 5 days left this month!
            |💰 Remaining budget: ₹${formatAmount(snapshot.remainingMonthlyBudget)}
            |📊 Daily allowance: ₹${formatAmount(dailyBudgetRemaining)}/day
            |
            |${if (snapshot.remainingMonthlyBudget > 0) "You're on track! Keep it up." else "Budget stretched thin — prioritize essentials."}
        """.trimMargin()

        return createNotification(
            type = NotificationType.MONTH_END_COUNTDOWN,
            title = "Month-End Budget Check",
            body = body,
            priority = NotificationPriority.MEDIUM,
            scheduledTime = now,
            triggerCondition = "5 days before month end"
        )
    }

    private fun generatePositiveReinforcement(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val saved = snapshot.lastWeekBudget - snapshot.lastWeekTotalSpend
        val body = """
            |🎉 Amazing week! You came in under budget!
            |💪 Budget: ₹${formatAmount(snapshot.lastWeekBudget)}
            |✅ Spent: ₹${formatAmount(snapshot.lastWeekTotalSpend)}
            |🏆 Saved: ₹${formatAmount(saved)}
            |
            |That's ₹${formatAmount(saved * 52)}/year if you keep this up!
        """.trimMargin()

        return createNotification(
            type = NotificationType.POSITIVE_REINFORCEMENT,
            title = "🏆 Under Budget Last Week!",
            body = body,
            priority = NotificationPriority.LOW,
            scheduledTime = now,
            triggerCondition = "Monday after an under-budget week"
        )
    }

    private fun generateGhostChargeAlert(
        sub: UnusedSubscriptionInfo,
        now: LocalDateTime
    ): SmartNotification {
        val body = """
            |👻 Ghost charge detected!
            |💸 ${sub.categoryLabel}: ₹${formatAmount(sub.monthlyAmount)}/month
            |📅 Last used: ${sub.daysSinceLastUsed} days ago
            |💰 Annual waste: ₹${formatAmount(sub.monthlyAmount * 12)}
            |
            |Consider cancelling if you no longer need this service.
        """.trimMargin()

        return createNotification(
            type = NotificationType.GHOST_CHARGE_DETECTION,
            title = "Ghost Subscription: ${sub.categoryLabel}",
            body = body,
            priority = NotificationPriority.MEDIUM,
            scheduledTime = now,
            triggerCondition = "Recurring charge for service unused 30+ days",
            actionLabel = "View Cancellation Guide"
        )
    }

    private fun generateWeeklyRoast(
        snapshot: FinancialSnapshot,
        now: LocalDateTime
    ): SmartNotification {
        val roastMessage = getWittyRoast(snapshot)
        val body = """
            |📊 Your Week in Review:
            |💸 Total spent: ₹${formatAmount(snapshot.lastWeekTotalSpend)}
            |
            |$roastMessage
        """.trimMargin()

        return createNotification(
            type = NotificationType.WEEKLY_ROAST,
            title = "Weekly Spending Roast 🔥",
            body = body,
            priority = NotificationPriority.LOW,
            scheduledTime = now,
            triggerCondition = "Monday 9:00 AM weekly",
            actionLabel = "See Full Breakdown"
        )
    }

    /**
     * Generates a privacy alert notification.
     *
     * @param accessType Type of sensor/data access detected.
     * @param appCategory Generic category of the app accessing data.
     * @param now Current time.
     */
    fun generatePrivacyAlert(
        accessType: String,
        appCategory: String,
        now: LocalDateTime = LocalDateTime.now()
    ): SmartNotification {
        val body = """
            |🔒 Privacy Notice:
            |📱 A $appCategory accessed your $accessType.
            |
            |Review app permissions in your device settings
            |if this seems unexpected.
        """.trimMargin()

        return createNotification(
            type = NotificationType.PRIVACY_ALERT,
            title = "Privacy: $accessType Access",
            body = body,
            priority = NotificationPriority.MEDIUM,
            scheduledTime = now,
            triggerCondition = "Sensor/data access detected (if privacy alerts enabled)"
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun createNotification(
        type: NotificationType,
        title: String,
        body: String,
        priority: NotificationPriority,
        scheduledTime: LocalDateTime,
        triggerCondition: String,
        actionLabel: String? = null,
        actionDeepLink: String? = null
    ): SmartNotification {
        notificationIdCounter++
        return SmartNotification(
            id = "notif_${notificationIdCounter}_${System.currentTimeMillis()}",
            type = type,
            title = title,
            body = body,
            priority = priority,
            scheduledTime = scheduledTime,
            triggerCondition = triggerCondition,
            actionLabel = actionLabel,
            actionDeepLink = actionDeepLink
        )
    }

    private fun isInDnd(currentTime: LocalTime): Boolean {
        return if (preferences.dndStartTime > preferences.dndEndTime) {
            // DND spans midnight (e.g., 22:00 to 07:00)
            currentTime >= preferences.dndStartTime || currentTime <= preferences.dndEndTime
        } else {
            currentTime in preferences.dndStartTime..preferences.dndEndTime
        }
    }

    private fun isTriggerTime(now: LocalDateTime, hour: Int, minute: Int): Boolean {
        // Allow a 15-minute window around the trigger time
        val triggerTime = LocalTime.of(hour, minute)
        val currentTime = now.toLocalTime()
        val diff = Math.abs(
            ChronoUnit.MINUTES.between(
                currentTime.atDate(LocalDate.now()),
                triggerTime.atDate(LocalDate.now())
            )
        )
        return diff <= 15
    }

    private fun getWittyRoast(snapshot: FinancialSnapshot): String {
        val weeklySpend = snapshot.lastWeekTotalSpend
        val weeklyBudget = snapshot.lastWeekBudget

        return when {
            weeklySpend < weeklyBudget * 0.5 ->
                "🧊 Your wallet is in deep freeze mode. Impressive self-control!"
            weeklySpend < weeklyBudget * 0.8 ->
                "👏 Solid week! Your future self just sent a thank-you note."
            weeklySpend < weeklyBudget ->
                "😅 Close call! You finished just under budget. Living on the edge."
            weeklySpend < weeklyBudget * 1.2 ->
                "🙈 Slightly over budget. We'll call it 'investment in happiness'."
            weeklySpend < weeklyBudget * 1.5 ->
                "🔥 Your budget didn't just break — it caught fire. Time for a reset!"
            else ->
                "💀 Your budget called. It wants a divorce. Next week, let's do better!"
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format("%,d", amount.toLong())
        } else {
            String.format("%,.0f", amount)
        }
    }
}
