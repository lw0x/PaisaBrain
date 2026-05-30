package com.paisabrain.app.followup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Tone of a nudge notification — always respectful, never guilt-inducing.
 */
enum class NudgeTone {
    /** Soft, non-judgmental reminder — "Just a gentle reminder..." */
    GENTLE,

    /** Factual, helpful information — "Tomorrow is..." */
    INFORMATIVE,

    /** Positive, celebratory tone — "Happy birthday to..." */
    CELEBRATORY
}

/**
 * Represents a scheduled or delivered nudge event.
 *
 * @property id Unique identifier for this nudge
 * @property followUpItemId The follow-up item this nudge relates to
 * @property scheduledTime When this nudge should be delivered (epoch millis)
 * @property nudgeText The notification text to display
 * @property tone The emotional tone of the nudge
 * @property delivered Whether the nudge has been shown to the user
 * @property deliveredAt When it was actually delivered (may differ from scheduled)
 * @property dismissed Whether the user dismissed without action
 * @property snoozed Whether the user snoozed this nudge
 * @property snoozedUntil If snoozed, when it should reappear
 */
data class NudgeEvent(
    val id: String = UUID.randomUUID().toString(),
    val followUpItemId: String,
    val scheduledTime: Long,
    val nudgeText: String,
    val tone: NudgeTone,
    val delivered: Boolean = false,
    val deliveredAt: Long? = null,
    val dismissed: Boolean = false,
    val snoozed: Boolean = false,
    val snoozedUntil: Long? = null
)

/**
 * Batched notification combining multiple nudges into a single notification.
 *
 * @property id Unique identifier
 * @property nudgeIds IDs of the individual nudges in this batch
 * @property summaryText Combined notification text
 * @property itemCount Number of items in this batch
 * @property highestPriority The highest priority among batched items
 * @property scheduledTime When to deliver this batch
 */
data class BatchedNudge(
    val id: String = UUID.randomUUID().toString(),
    val nudgeIds: List<String>,
    val summaryText: String,
    val itemCount: Int,
    val highestPriority: FollowUpPriority,
    val scheduledTime: Long
)

/**
 * Configuration for DND (Do Not Disturb) and notification limits.
 *
 * @property dndStartHour Start of DND window (no notifications unless URGENT)
 * @property dndEndHour End of DND window
 * @property maxNudgesPerDay Maximum notifications per day across all types
 * @property batchThreshold If this many nudges are pending simultaneously, batch them
 * @property respectSystemDnd Whether to also respect Android system DND mode
 */
data class NudgeConfig(
    val dndStartHour: Int = 22,
    val dndEndHour: Int = 8,
    val maxNudgesPerDay: Int = 3,
    val batchThreshold: Int = 3,
    val respectSystemDnd: Boolean = true
)

/**
 * Nudge schedule definition for a specific follow-up type.
 *
 * @property type The follow-up type this schedule applies to
 * @property nudgeDelays List of delays (in hours) from creation/last nudge
 * @property maxNudges Maximum nudges for this type before stopping
 * @property stopMessage Message shown when max nudges reached
 */
data class NudgeSchedule(
    val type: FollowUpType,
    val nudgeDelays: List<Long>,
    val maxNudges: Int,
    val stopMessage: String?
)

/**
 * Tracks daily nudge delivery counts for rate limiting.
 *
 * @property date The date these counts are for
 * @property delivered Number of nudges delivered today
 * @property dismissed Number dismissed without action
 * @property actedUpon Number that led to user action
 */
data class DailyNudgeStats(
    val date: LocalDate,
    val delivered: Int,
    val dismissed: Int,
    val actedUpon: Int
)

/**
 * Non-intrusive, respectful reminder notification engine.
 *
 * **Philosophy:**
 * - NEVER spam the user
 * - NEVER guilt-trip or shame
 * - Always gentle, always dismissible
 * - Respects DND hours and daily limits
 * - Smart batching to minimize interruptions
 *
 * This engine manages the scheduling, text generation, batching, and
 * delivery logic for all follow-up reminders. It ensures the user is
 * nudged just enough to be helpful without becoming annoying.
 *
 * **Nudge schedule by type:**
 * - Missed call: 4h after, next morning, then stop
 * - Money received: same day evening, then stop
 * - Social debt (you owe): 7d, 14d, 30d, then monthly
 * - Social debt (they owe): 14d, 30d, then stop
 * - Important date: 7d before, 1d before
 * - Follow-up note: on specified date/time
 *
 * **All processing is entirely on-device.**
 *
 * Usage:
 * ```kotlin
 * val engine = GentleNudgeEngine(config)
 * engine.scheduleNudge(followUpItem)
 * val pending = engine.getPendingNudges()
 * ```
 *
 * @property config Notification and DND configuration
 */
class GentleNudgeEngine(
    private val config: NudgeConfig = NudgeConfig()
) {
    private val _scheduledNudges = MutableStateFlow<List<NudgeEvent>>(emptyList())

    /** All scheduled nudges (pending and delivered). */
    val scheduledNudges: StateFlow<List<NudgeEvent>> = _scheduledNudges.asStateFlow()

    private val _mutedItems = MutableStateFlow<Set<String>>(emptySet())

    /** Item IDs that the user has muted (no more nudges). */
    val mutedItems: StateFlow<Set<String>> = _mutedItems.asStateFlow()

    private val _dailyStats = MutableStateFlow(DailyNudgeStats(LocalDate.now(), 0, 0, 0))

    /** Today's nudge delivery statistics. */
    val dailyStats: StateFlow<DailyNudgeStats> = _dailyStats.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Nudge schedules per follow-up type. Delays are in hours from item creation. */
    private val schedules: Map<FollowUpType, NudgeSchedule> = mapOf(
        FollowUpType.CALL to NudgeSchedule(
            type = FollowUpType.CALL,
            nudgeDelays = listOf(4L, 17L), // 4h after, then next morning (~17h)
            maxNudges = 2,
            stopMessage = null
        ),
        FollowUpType.MONEY to NudgeSchedule(
            type = FollowUpType.MONEY,
            nudgeDelays = listOf(8L), // Same day evening (~8h after)
            maxNudges = 1,
            stopMessage = null
        ),
        FollowUpType.SOCIAL_DEBT to NudgeSchedule(
            type = FollowUpType.SOCIAL_DEBT,
            nudgeDelays = listOf(168L, 336L, 720L, 1440L), // 7d, 14d, 30d, 60d (in hours)
            maxNudges = 4,
            stopMessage = null
        ),
        FollowUpType.REMINDER to NudgeSchedule(
            type = FollowUpType.REMINDER,
            nudgeDelays = listOf(0L), // On the specified time
            maxNudges = 1,
            stopMessage = null
        ),
        FollowUpType.BILL to NudgeSchedule(
            type = FollowUpType.BILL,
            nudgeDelays = listOf(72L, 24L), // 3 days before, 1 day before
            maxNudges = 2,
            stopMessage = null
        ),
        FollowUpType.DATE to NudgeSchedule(
            type = FollowUpType.DATE,
            nudgeDelays = listOf(168L, 24L), // 7 days before, 1 day before
            maxNudges = 2,
            stopMessage = null
        )
    )

    /**
     * Schedules nudges for a follow-up item based on its type.
     *
     * Automatically determines the nudge schedule, generates appropriate text,
     * and queues the nudges for future delivery.
     *
     * @param item The follow-up item to schedule nudges for
     * @return List of scheduled NudgeEvents
     */
    fun scheduleNudge(item: FollowUpItem): List<NudgeEvent> {
        // Don't schedule if muted
        if (item.id in _mutedItems.value) return emptyList()

        val schedule = schedules[item.type] ?: return emptyList()
        val existingForItem = _scheduledNudges.value.count { it.followUpItemId == item.id }

        if (existingForItem >= schedule.maxNudges) return emptyList()

        val nudges = mutableListOf<NudgeEvent>()
        val baseTime = item.dueBy ?: item.createdAt

        for (i in existingForItem until schedule.nudgeDelays.size.coerceAtMost(schedule.maxNudges)) {
            val delayHours = schedule.nudgeDelays[i]
            val scheduledTime = if (item.dueBy != null && item.type == FollowUpType.DATE) {
                // For dates, nudge BEFORE the event
                item.dueBy - (delayHours * 60 * 60 * 1000)
            } else {
                // For other items, nudge AFTER creation
                item.createdAt + (delayHours * 60 * 60 * 1000)
            }

            // Adjust for DND hours
            val adjustedTime = adjustForDnd(scheduledTime, item.urgency == FollowUpPriority.URGENT)

            val nudgeText = generateNudgeText(item, i)
            val tone = determineTone(item)

            val nudge = NudgeEvent(
                followUpItemId = item.id,
                scheduledTime = adjustedTime,
                nudgeText = nudgeText,
                tone = tone
            )

            nudges.add(nudge)
        }

        _scheduledNudges.value = _scheduledNudges.value + nudges
        return nudges
    }

    /**
     * Gets all nudges that are due for delivery right now.
     *
     * Respects:
     * - DND hours (unless URGENT)
     * - Daily limit (max 3 per day)
     * - Muted items
     * - Snooze preferences
     *
     * @return List of nudges ready to be delivered
     */
    fun getPendingNudges(): List<NudgeEvent> {
        val now = System.currentTimeMillis()
        val todayStats = _dailyStats.value

        // Check daily limit
        if (todayStats.date == LocalDate.now() && todayStats.delivered >= config.maxNudgesPerDay) {
            return emptyList()
        }

        return _scheduledNudges.value.filter { nudge ->
            !nudge.delivered &&
                    !nudge.dismissed &&
                    nudge.followUpItemId !in _mutedItems.value &&
                    nudge.scheduledTime <= now &&
                    (nudge.snoozedUntil == null || nudge.snoozedUntil <= now)
        }
    }

    /**
     * Attempts to batch multiple pending nudges into a single notification.
     *
     * If [config.batchThreshold] or more nudges are pending, combines them
     * into one notification instead of sending separately.
     *
     * @return BatchedNudge if batching is appropriate, null if individual delivery preferred
     */
    fun tryBatchPendingNudges(): BatchedNudge? {
        val pending = getPendingNudges()

        if (pending.size < config.batchThreshold) return null

        val summaryText = buildBatchSummary(pending)
        val highestPriority = pending
            .mapNotNull { nudge ->
                // We'd need to look up the follow-up item for its priority
                // For simplicity, derive from nudge tone
                when (nudge.tone) {
                    NudgeTone.GENTLE -> FollowUpPriority.NORMAL
                    NudgeTone.INFORMATIVE -> FollowUpPriority.HIGH
                    NudgeTone.CELEBRATORY -> FollowUpPriority.LOW
                }
            }
            .minByOrNull { it.ordinal } ?: FollowUpPriority.NORMAL

        return BatchedNudge(
            nudgeIds = pending.map { it.id },
            summaryText = summaryText,
            itemCount = pending.size,
            highestPriority = highestPriority,
            scheduledTime = System.currentTimeMillis()
        )
    }

    /**
     * Marks a nudge as delivered.
     *
     * @param nudgeId The nudge that was shown to the user
     */
    fun markDelivered(nudgeId: String) {
        _scheduledNudges.value = _scheduledNudges.value.map { nudge ->
            if (nudge.id == nudgeId) {
                nudge.copy(delivered = true, deliveredAt = System.currentTimeMillis())
            } else nudge
        }

        // Update daily stats
        val today = LocalDate.now()
        val current = _dailyStats.value
        _dailyStats.value = if (current.date == today) {
            current.copy(delivered = current.delivered + 1)
        } else {
            DailyNudgeStats(today, 1, 0, 0)
        }
    }

    /**
     * Marks a nudge as dismissed by the user.
     *
     * @param nudgeId The nudge that was dismissed
     */
    fun markDismissed(nudgeId: String) {
        _scheduledNudges.value = _scheduledNudges.value.map { nudge ->
            if (nudge.id == nudgeId) {
                nudge.copy(dismissed = true)
            } else nudge
        }

        val today = LocalDate.now()
        val current = _dailyStats.value
        if (current.date == today) {
            _dailyStats.value = current.copy(dismissed = current.dismissed + 1)
        }
    }

    /**
     * Snoozes a nudge for the specified duration.
     *
     * @param nudgeId The nudge to snooze
     * @param duration How long to snooze
     */
    fun snoozeNudge(nudgeId: String, duration: SnoozeDuration) {
        val snoozeUntil = calculateSnoozeTime(duration)

        _scheduledNudges.value = _scheduledNudges.value.map { nudge ->
            if (nudge.id == nudgeId) {
                nudge.copy(snoozed = true, snoozedUntil = snoozeUntil)
            } else nudge
        }
    }

    /**
     * Mutes all future nudges for a specific follow-up item.
     *
     * @param itemId The follow-up item ID to mute
     */
    fun muteItem(itemId: String) {
        _mutedItems.value = _mutedItems.value + itemId

        // Cancel all pending nudges for this item
        _scheduledNudges.value = _scheduledNudges.value.map { nudge ->
            if (nudge.followUpItemId == itemId && !nudge.delivered) {
                nudge.copy(dismissed = true)
            } else nudge
        }
    }

    /**
     * Unmutes a previously muted item.
     *
     * @param itemId The follow-up item ID to unmute
     */
    fun unmuteItem(itemId: String) {
        _mutedItems.value = _mutedItems.value - itemId
    }

    /**
     * Records that the user acted on a nudge (for statistics).
     *
     * @param nudgeId The nudge that prompted action
     */
    fun markActedUpon(nudgeId: String) {
        val today = LocalDate.now()
        val current = _dailyStats.value
        if (current.date == today) {
            _dailyStats.value = current.copy(actedUpon = current.actedUpon + 1)
        }
    }

    /**
     * Checks if we're currently in DND (Do Not Disturb) hours.
     *
     * @return true if notifications should be suppressed
     */
    fun isInDndWindow(): Boolean {
        val now = LocalTime.now()
        val dndStart = LocalTime.of(config.dndStartHour, 0)
        val dndEnd = LocalTime.of(config.dndEndHour, 0)

        return if (dndStart > dndEnd) {
            // Spans midnight: e.g., 22:00 to 08:00
            now >= dndStart || now < dndEnd
        } else {
            now in dndStart..dndEnd
        }
    }

    /**
     * Gets the remaining nudge quota for today.
     *
     * @return Number of nudges still allowed today
     */
    fun getRemainingQuotaToday(): Int {
        val current = _dailyStats.value
        return if (current.date == LocalDate.now()) {
            (config.maxNudgesPerDay - current.delivered).coerceAtLeast(0)
        } else {
            config.maxNudgesPerDay
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Text Generation — Warm, Non-Judgmental, Helpful
    // ─────────────────────────────────────────────────────────────

    /**
     * Generates nudge text for a follow-up item based on its type and nudge number.
     *
     * All generated text is:
     * - Warm and friendly
     * - Non-judgmental (no guilt-tripping)
     * - Dismissible (no pressure)
     * - Concise
     *
     * @param item The follow-up item
     * @param nudgeIndex Which nudge this is (0-based)
     * @return Human-friendly reminder text
     */
    private fun generateNudgeText(item: FollowUpItem, nudgeIndex: Int): String {
        return when (item.type) {
            FollowUpType.CALL -> generateCallNudge(item, nudgeIndex)
            FollowUpType.MONEY -> generateMoneyNudge(item, nudgeIndex)
            FollowUpType.SOCIAL_DEBT -> generateDebtNudge(item, nudgeIndex)
            FollowUpType.BILL -> generateBillNudge(item, nudgeIndex)
            FollowUpType.DATE -> generateDateNudge(item, nudgeIndex)
            FollowUpType.REMINDER -> generateReminderNudge(item, nudgeIndex)
        }
    }

    private fun generateCallNudge(item: FollowUpItem, index: Int): String {
        val person = item.relatedPersonName ?: "someone"
        return when (index) {
            0 -> "Just a gentle reminder — you missed a call from $person earlier today. No pressure! 📞"
            1 -> "Good morning! Quick note: missed call from $person yesterday. Might want to check in when you get a chance 🌅"
            else -> "Missed call from $person still pending. No worries if it's not important! 📞"
        }
    }

    private fun generateMoneyNudge(item: FollowUpItem, index: Int): String {
        return "Small note: ${item.title}. Thought you might want to acknowledge it! No rush 💰"
    }

    private fun generateDebtNudge(item: FollowUpItem, index: Int): String {
        val isIOwe = item.title.lowercase().contains("settle")
        return if (isIOwe) {
            when (index) {
                0 -> "Small thing: ${item.description}. Whenever you're ready! 🤝"
                1 -> "Friendly self-reminder: ${item.description}. No judgment, just keeping track! 📝"
                2 -> "Monthly note: ${item.description}. Settle when convenient! 😊"
                else -> "Periodic reminder: ${item.description}. Take your time! 🙂"
            }
        } else {
            when (index) {
                0 -> "Just noting: ${item.description}. Want to send a gentle reminder? 💬"
                1 -> "Still pending: ${item.description}. Up to you if you'd like to follow up! 🤷"
                else -> "${item.description} — archived. No more reminders about this one."
            }
        }
    }

    private fun generateBillNudge(item: FollowUpItem, index: Int): String {
        return when (index) {
            0 -> "Heads up: ${item.title} is coming up in a few days. Just so you can plan! 📋"
            1 -> "Reminder: ${item.title} is due tomorrow. You've got this! ⏰"
            else -> "${item.title} is due soon. Don't forget! 📅"
        }
    }

    private fun generateDateNudge(item: FollowUpItem, index: Int): String {
        return when (index) {
            0 -> "Upcoming: ${item.title} is in a week! Time to plan? 🗓️"
            1 -> "Tomorrow is ${item.title}! Hope you're ready 🎉"
            else -> "${item.title} is coming up! 📅"
        }
    }

    private fun generateReminderNudge(item: FollowUpItem, index: Int): String {
        return "📌 ${item.title}: ${item.description}"
    }

    /**
     * Determines the appropriate tone for a nudge.
     */
    private fun determineTone(item: FollowUpItem): NudgeTone {
        return when {
            item.type == FollowUpType.DATE &&
                    item.title.lowercase().contains("birthday") -> NudgeTone.CELEBRATORY
            item.type == FollowUpType.DATE &&
                    item.title.lowercase().contains("anniversary") -> NudgeTone.CELEBRATORY
            item.urgency == FollowUpPriority.URGENT -> NudgeTone.INFORMATIVE
            item.type == FollowUpType.BILL -> NudgeTone.INFORMATIVE
            else -> NudgeTone.GENTLE
        }
    }

    /**
     * Adjusts a scheduled time to respect DND hours.
     *
     * If the time falls within DND and the item isn't urgent, pushes it
     * to the next morning after DND ends.
     *
     * @param scheduledTime Original scheduled time (epoch millis)
     * @param isUrgent Whether this is an urgent item (bypasses DND)
     * @return Adjusted time respecting DND, or original if urgent
     */
    private fun adjustForDnd(scheduledTime: Long, isUrgent: Boolean): Long {
        if (isUrgent) return scheduledTime

        val scheduledDateTime = Instant.ofEpochMilli(scheduledTime)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val time = scheduledDateTime.toLocalTime()
        val dndStart = LocalTime.of(config.dndStartHour, 0)
        val dndEnd = LocalTime.of(config.dndEndHour, 0)

        val isDuringDnd = if (dndStart > dndEnd) {
            time >= dndStart || time < dndEnd
        } else {
            time in dndStart..dndEnd
        }

        if (!isDuringDnd) return scheduledTime

        // Push to next morning after DND ends
        val nextMorning = if (time >= dndStart) {
            scheduledDateTime.plusDays(1)
                .withHour(config.dndEndHour)
                .withMinute(0)
                .withSecond(0)
        } else {
            scheduledDateTime
                .withHour(config.dndEndHour)
                .withMinute(0)
                .withSecond(0)
        }

        return nextMorning.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Builds a combined summary text for batched nudges.
     */
    private fun buildBatchSummary(nudges: List<NudgeEvent>): String {
        val count = nudges.size
        val types = nudges.map { it.tone }.distinct()

        return buildString {
            append("You have $count pending items:")
            nudges.take(3).forEachIndexed { index, nudge ->
                append("\n${index + 1}. ${nudge.nudgeText.take(50)}")
                if (nudge.nudgeText.length > 50) append("...")
            }
            if (count > 3) {
                append("\n...and ${count - 3} more")
            }
        }
    }

    /**
     * Calculates snooze-until timestamp.
     */
    private fun calculateSnoozeTime(duration: SnoozeDuration): Long {
        val now = LocalDateTime.now()
        val snoozeDateTime = when (duration) {
            SnoozeDuration.ONE_HOUR -> now.plusHours(1)
            SnoozeDuration.FOUR_HOURS -> now.plusHours(4)
            SnoozeDuration.TOMORROW_MORNING -> {
                now.plusDays(1).withHour(9).withMinute(0).withSecond(0)
            }
            SnoozeDuration.NEXT_WEEK -> {
                now.plusWeeks(1).withHour(9).withMinute(0).withSecond(0)
            }
        }
        return snoozeDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Gets the nudge schedule for "they owe" type debts (more conservative).
     *
     * We stop nagging about others' debts sooner — it's their responsibility.
     * Only 2 nudges: at 14 days and 30 days, then we stop completely.
     *
     * @param item The social debt follow-up item
     * @return Custom schedule for "they owe" items
     */
    fun getTheyOweSchedule(item: FollowUpItem): NudgeSchedule {
        return NudgeSchedule(
            type = FollowUpType.SOCIAL_DEBT,
            nudgeDelays = listOf(336L, 720L), // 14 days, 30 days (in hours)
            maxNudges = 2,
            stopMessage = "No more reminders about this. It's up to them! 🙂"
        )
    }

    /**
     * Resets daily stats if the day has changed.
     */
    fun resetDailyStatsIfNeeded() {
        val today = LocalDate.now()
        if (_dailyStats.value.date != today) {
            _dailyStats.value = DailyNudgeStats(today, 0, 0, 0)
        }
    }

    /**
     * Gets a summary of nudge effectiveness.
     *
     * @return Human-readable effectiveness summary
     */
    fun getEffectivenessSummary(): String {
        val stats = _dailyStats.value
        val total = _scheduledNudges.value.count { it.delivered }
        val acted = _scheduledNudges.value.count { it.delivered && !it.dismissed }

        return buildString {
            append("📊 Nudge Summary")
            append("\nToday: ${stats.delivered} delivered, ${stats.actedUpon} acted upon")
            if (total > 0) {
                val effectiveness = (acted.toFloat() / total * 100).toInt()
                append("\nOverall: $effectiveness% of nudges lead to action")
            }
            append("\nRemaining today: ${getRemainingQuotaToday()}")
        }
    }

    /**
     * Cleans up resources.
     */
    fun destroy() {
        scope.cancel()
    }
}
