package com.paisabrain.app.followup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Types of follow-up items that can appear in the unified queue.
 */
enum class FollowUpType {
    /** Missed call that needs to be returned */
    CALL,

    /** Incoming money requiring acknowledgment or action */
    MONEY,

    /** Social debt (owed or owing) needing attention */
    SOCIAL_DEBT,

    /** General reminder from vault notes or user-created */
    REMINDER,

    /** Bill payment due */
    BILL,

    /** Important date approaching (birthday, anniversary, etc.) */
    DATE
}

/**
 * Priority levels for follow-up items in the queue.
 */
enum class FollowUpPriority {
    /** Missed call 2x, overdue bill, emergency items */
    URGENT,

    /** Money received today, appointment tomorrow */
    HIGH,

    /** Missed call 1x, debt aging, approaching dates */
    NORMAL,

    /** General reminders, low-priority items */
    LOW
}

/**
 * Smart grouping categories for the follow-up dashboard.
 */
enum class FollowUpGroup {
    /** People-related: calls + money from same person grouped together */
    PEOPLE,

    /** All financial follow-ups: debts, bills, money acknowledgments */
    MONEY,

    /** Life events: appointments, dates, reminders */
    LIFE
}

/**
 * Snooze duration options for follow-up items.
 */
enum class SnoozeDuration {
    /** Snooze for 1 hour */
    ONE_HOUR,

    /** Snooze for 4 hours */
    FOUR_HOURS,

    /** Snooze until tomorrow morning (9 AM) */
    TOMORROW_MORNING,

    /** Snooze for one week */
    NEXT_WEEK
}

/**
 * Represents a single item in the unified follow-up queue.
 *
 * @property id Unique identifier for this item
 * @property type The category of follow-up
 * @property title Short display title
 * @property description Longer description with context
 * @property urgency Priority level for sorting
 * @property createdAt When this item was created (epoch millis)
 * @property dueBy When this item should be acted upon (epoch millis), null if no deadline
 * @property isCompleted Whether the user has marked this as done
 * @property completedAt When it was completed (epoch millis)
 * @property snoozeUntil If snoozed, when it should reappear (epoch millis)
 * @property completionNote Optional note added when completing
 * @property relatedPersonName Name of the person involved (for grouping)
 * @property sourceId ID of the source item (debt ID, call follow-up ID, etc.)
 */
data class FollowUpItem(
    val id: String = UUID.randomUUID().toString(),
    val type: FollowUpType,
    val title: String,
    val description: String,
    val urgency: FollowUpPriority,
    val createdAt: Long = System.currentTimeMillis(),
    val dueBy: Long? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val snoozeUntil: Long? = null,
    val completionNote: String? = null,
    val relatedPersonName: String? = null,
    val sourceId: String? = null
)

/**
 * Summary of the current follow-up queue state.
 *
 * @property totalPending Total active (non-completed, non-snoozed) items
 * @property urgentCount Number of URGENT priority items
 * @property highCount Number of HIGH priority items
 * @property normalCount Number of NORMAL priority items
 * @property lowCount Number of LOW priority items
 * @property snoozedCount Number of currently snoozed items
 * @property archivedTodayCount Number of items auto-archived today
 */
data class QueueSummary(
    val totalPending: Int,
    val urgentCount: Int,
    val highCount: Int,
    val normalCount: Int,
    val lowCount: Int,
    val snoozedCount: Int,
    val archivedTodayCount: Int
)

/**
 * Configuration for the follow-up queue.
 *
 * @property autoArchiveDays Days after which unactioned items are auto-archived
 * @property maxActiveItems Maximum items shown in the active queue
 * @property defaultMorningHour Hour of "tomorrow morning" for snooze
 */
data class FollowUpQueueConfig(
    val autoArchiveDays: Int = 7,
    val maxActiveItems: Int = 20,
    val defaultMorningHour: Int = 9
)

/**
 * Unified follow-up dashboard that aggregates and prioritizes all pending actions.
 *
 * This queue combines:
 * - Missed call follow-ups
 * - Money acknowledgments and social debts
 * - Vault note reminders
 * - Bill payment dues
 * - Important dates approaching
 *
 * Items are priority-sorted and smartly grouped by person or category.
 * Supports snoozing, completion with notes, and automatic expiry/archival.
 *
 * **All processing is entirely on-device.**
 *
 * Usage:
 * ```kotlin
 * val queue = FollowUpQueue(config)
 * queue.addItem(FollowUpItem(type = FollowUpType.CALL, title = "Call back Mom", ...))
 * val active = queue.activeItems.value
 * val badge = queue.badgeCount.value  // "3 follow-ups pending"
 * ```
 *
 * @property config Queue configuration parameters
 */
class FollowUpQueue(
    private val config: FollowUpQueueConfig = FollowUpQueueConfig()
) {
    private val _allItems = MutableStateFlow<List<FollowUpItem>>(emptyList())

    /** All items in the queue (including archived and completed). */
    val allItems: StateFlow<List<FollowUpItem>> = _allItems.asStateFlow()

    private val _activeItems = MutableStateFlow<List<FollowUpItem>>(emptyList())

    /** Currently active (visible) items, sorted by priority. */
    val activeItems: StateFlow<List<FollowUpItem>> = _activeItems.asStateFlow()

    private val _archivedItems = MutableStateFlow<List<FollowUpItem>>(emptyList())

    /** Items that have been auto-archived or completed. */
    val archivedItems: StateFlow<List<FollowUpItem>> = _archivedItems.asStateFlow()

    private val _badgeCount = MutableStateFlow(0)

    /** Number of pending follow-ups for badge display. */
    val badgeCount: StateFlow<Int> = _badgeCount.asStateFlow()

    private val _summary = MutableStateFlow(QueueSummary(0, 0, 0, 0, 0, 0, 0))

    /** Current queue summary statistics. */
    val summary: StateFlow<QueueSummary> = _summary.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // Start periodic maintenance (auto-archive, un-snooze)
        scope.launch {
            while (isActive) {
                performMaintenance()
                delay(60 * 1000L) // Check every minute
            }
        }
    }

    /**
     * Adds a new item to the follow-up queue.
     *
     * @param item The follow-up item to add
     */
    fun addItem(item: FollowUpItem) {
        _allItems.value = _allItems.value + item
        refreshActiveItems()
    }

    /**
     * Adds multiple items to the queue at once.
     *
     * @param items List of follow-up items to add
     */
    fun addItems(items: List<FollowUpItem>) {
        _allItems.value = _allItems.value + items
        refreshActiveItems()
    }

    /**
     * Marks an item as completed with an optional note.
     *
     * @param itemId The ID of the item to complete
     * @param note Optional completion note
     */
    fun completeItem(itemId: String, note: String? = null) {
        _allItems.value = _allItems.value.map { item ->
            if (item.id == itemId) {
                item.copy(
                    isCompleted = true,
                    completedAt = System.currentTimeMillis(),
                    completionNote = note
                )
            } else item
        }
        refreshActiveItems()
    }

    /**
     * Snoozes an item for the specified duration.
     *
     * @param itemId The ID of the item to snooze
     * @param duration How long to snooze
     */
    fun snoozeItem(itemId: String, duration: SnoozeDuration) {
        val snoozeUntil = calculateSnoozeTime(duration)

        _allItems.value = _allItems.value.map { item ->
            if (item.id == itemId) {
                item.copy(snoozeUntil = snoozeUntil)
            } else item
        }
        refreshActiveItems()
    }

    /**
     * Snoozes an item until a specific timestamp.
     *
     * @param itemId The ID of the item to snooze
     * @param untilTimestamp When the item should reappear (epoch millis)
     */
    fun snoozeItemUntil(itemId: String, untilTimestamp: Long) {
        _allItems.value = _allItems.value.map { item ->
            if (item.id == itemId) {
                item.copy(snoozeUntil = untilTimestamp)
            } else item
        }
        refreshActiveItems()
    }

    /**
     * Removes an item from the queue entirely.
     *
     * @param itemId The ID of the item to remove
     */
    fun removeItem(itemId: String) {
        _allItems.value = _allItems.value.filter { it.id != itemId }
        refreshActiveItems()
    }

    /**
     * Gets items grouped by smart category.
     *
     * - PEOPLE: Groups items related to the same person
     * - MONEY: All financial items
     * - LIFE: All life event items
     *
     * @return Map of group → items in that group
     */
    fun getGroupedItems(): Map<FollowUpGroup, List<FollowUpItem>> {
        val active = _activeItems.value

        return mapOf(
            FollowUpGroup.PEOPLE to active.filter { item ->
                item.type == FollowUpType.CALL ||
                        (item.type == FollowUpType.MONEY && item.relatedPersonName != null) ||
                        (item.type == FollowUpType.SOCIAL_DEBT && item.relatedPersonName != null)
            },
            FollowUpGroup.MONEY to active.filter { item ->
                item.type == FollowUpType.MONEY ||
                        item.type == FollowUpType.SOCIAL_DEBT ||
                        item.type == FollowUpType.BILL
            },
            FollowUpGroup.LIFE to active.filter { item ->
                item.type == FollowUpType.REMINDER ||
                        item.type == FollowUpType.DATE
            }
        )
    }

    /**
     * Gets items grouped by the related person, for the "People" view.
     *
     * Items from the same person (calls + money + debts) are grouped together
     * to give a holistic view of follow-ups for each relationship.
     *
     * @return Map of person name → their related items
     */
    fun getItemsByPerson(): Map<String, List<FollowUpItem>> {
        return _activeItems.value
            .filter { it.relatedPersonName != null }
            .groupBy { it.relatedPersonName!! }
    }

    /**
     * Gets the daily badge text for notification display.
     *
     * @return Formatted badge text like "3 follow-ups pending"
     */
    fun getBadgeText(): String {
        val count = _badgeCount.value
        return when (count) {
            0 -> "All caught up! ✨"
            1 -> "1 follow-up pending"
            else -> "$count follow-ups pending"
        }
    }

    /**
     * Calculates the snooze-until timestamp for a given duration.
     */
    private fun calculateSnoozeTime(duration: SnoozeDuration): Long {
        val now = LocalDateTime.now()

        val snoozeDateTime = when (duration) {
            SnoozeDuration.ONE_HOUR -> now.plusHours(1)
            SnoozeDuration.FOUR_HOURS -> now.plusHours(4)
            SnoozeDuration.TOMORROW_MORNING -> {
                now.plusDays(1)
                    .withHour(config.defaultMorningHour)
                    .withMinute(0)
                    .withSecond(0)
            }
            SnoozeDuration.NEXT_WEEK -> {
                now.plusWeeks(1)
                    .withHour(config.defaultMorningHour)
                    .withMinute(0)
                    .withSecond(0)
            }
        }

        return snoozeDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /**
     * Refreshes the active items list by filtering and sorting all items.
     *
     * Active items are those that:
     * - Are not completed
     * - Are not currently snoozed (snooze time has passed or never snoozed)
     * - Are not auto-archived
     */
    private fun refreshActiveItems() {
        val now = System.currentTimeMillis()

        val active = _allItems.value.filter { item ->
            !item.isCompleted &&
                    (item.snoozeUntil == null || item.snoozeUntil <= now) &&
                    !isAutoArchived(item, now)
        }.sortedWith(
            compareBy<FollowUpItem> { it.urgency.ordinal }
                .thenBy { it.dueBy ?: Long.MAX_VALUE }
                .thenByDescending { it.createdAt }
        ).take(config.maxActiveItems)

        _activeItems.value = active
        _badgeCount.value = active.size

        // Update archived
        val archived = _allItems.value.filter { item ->
            item.isCompleted || isAutoArchived(item, now)
        }
        _archivedItems.value = archived

        // Update summary
        updateSummary(active, now)
    }

    /**
     * Checks if an item should be auto-archived (older than configured days without action).
     */
    private fun isAutoArchived(item: FollowUpItem, now: Long): Boolean {
        val ageMs = now - item.createdAt
        val archiveThresholdMs = config.autoArchiveDays.toLong() * 24 * 60 * 60 * 1000
        return ageMs > archiveThresholdMs && !item.isCompleted
    }

    /**
     * Updates the queue summary statistics.
     */
    private fun updateSummary(activeItems: List<FollowUpItem>, now: Long) {
        val snoozed = _allItems.value.count { item ->
            !item.isCompleted && item.snoozeUntil != null && item.snoozeUntil > now
        }

        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val archivedToday = _allItems.value.count { item ->
            isAutoArchived(item, now) && item.createdAt >= (now - config.autoArchiveDays.toLong() * 24 * 60 * 60 * 1000)
        }

        _summary.value = QueueSummary(
            totalPending = activeItems.size,
            urgentCount = activeItems.count { it.urgency == FollowUpPriority.URGENT },
            highCount = activeItems.count { it.urgency == FollowUpPriority.HIGH },
            normalCount = activeItems.count { it.urgency == FollowUpPriority.NORMAL },
            lowCount = activeItems.count { it.urgency == FollowUpPriority.LOW },
            snoozedCount = snoozed,
            archivedTodayCount = archivedToday
        )
    }

    /**
     * Performs periodic maintenance:
     * - Un-snoozes items whose snooze period has elapsed
     * - Auto-archives old items
     * - Refreshes the active items list
     */
    private fun performMaintenance() {
        val now = System.currentTimeMillis()

        // Un-snooze items whose time has come
        var changed = false
        _allItems.value = _allItems.value.map { item ->
            if (item.snoozeUntil != null && item.snoozeUntil <= now) {
                changed = true
                item.copy(snoozeUntil = null)
            } else item
        }

        if (changed) {
            refreshActiveItems()
        }
    }

    /**
     * Creates a follow-up item from a missed call.
     *
     * @param callFollowUp The missed call follow-up data
     * @return A queue-ready follow-up item
     */
    fun createFromMissedCall(callFollowUp: MissedCallFollowUp): FollowUpItem {
        val caller = callFollowUp.contactName ?: callFollowUp.phoneNumber
        val urgency = when (callFollowUp.urgencyLevel) {
            UrgencyLevel.URGENT -> FollowUpPriority.URGENT
            UrgencyLevel.NORMAL -> FollowUpPriority.NORMAL
            UrgencyLevel.LOW -> FollowUpPriority.LOW
        }

        return FollowUpItem(
            type = FollowUpType.CALL,
            title = "Call back $caller",
            description = buildString {
                append("Missed call at ${formatTime(callFollowUp.missedAt)}")
                if (callFollowUp.urgencyLevel == UrgencyLevel.URGENT) {
                    append(" • Called multiple times")
                }
                callFollowUp.callerPattern?.let {
                    append("\n${it.description}")
                }
            },
            urgency = urgency,
            createdAt = callFollowUp.missedAt,
            relatedPersonName = callFollowUp.contactName,
            sourceId = callFollowUp.id
        )
    }

    /**
     * Creates a follow-up item from a social debt.
     *
     * @param debt The social debt entry
     * @return A queue-ready follow-up item
     */
    fun createFromDebt(debt: SocialDebt): FollowUpItem {
        val daysSince = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(debt.createdDate).atZone(ZoneId.systemDefault()).toLocalDate(),
            LocalDate.now()
        )

        val urgency = when {
            daysSince > 30 && debt.type == DebtType.I_OWE -> FollowUpPriority.HIGH
            daysSince > 14 -> FollowUpPriority.NORMAL
            else -> FollowUpPriority.LOW
        }

        val title = if (debt.type == DebtType.I_OWE) {
            "Settle ₹${debt.amount.toLong()} with ${debt.personName}"
        } else {
            "₹${debt.amount.toLong()} owed by ${debt.personName}"
        }

        return FollowUpItem(
            type = FollowUpType.SOCIAL_DEBT,
            title = title,
            description = "${debt.reason} • ${daysSince} days ago",
            urgency = urgency,
            createdAt = debt.createdDate,
            dueBy = debt.dueDate,
            relatedPersonName = debt.personName,
            sourceId = debt.id
        )
    }

    /**
     * Creates a follow-up item from an important date.
     *
     * @param importantDate The important date entry
     * @return A queue-ready follow-up item
     */
    fun createFromImportantDate(importantDate: ImportantDate): FollowUpItem {
        val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(),
            Instant.ofEpochMilli(importantDate.date).atZone(ZoneId.systemDefault()).toLocalDate()
        )

        val urgency = when {
            daysUntil <= 1 -> FollowUpPriority.HIGH
            daysUntil <= 7 -> FollowUpPriority.NORMAL
            else -> FollowUpPriority.LOW
        }

        return FollowUpItem(
            type = FollowUpType.DATE,
            title = importantDate.title,
            description = when {
                daysUntil == 0L -> "Today!"
                daysUntil == 1L -> "Tomorrow"
                else -> "In $daysUntil days"
            },
            urgency = urgency,
            createdAt = System.currentTimeMillis(),
            dueBy = importantDate.date,
            sourceId = importantDate.id
        )
    }

    /**
     * Formats a timestamp to a time-of-day string.
     */
    private fun formatTime(timestamp: Long): String {
        val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
        return "${time.hour}:${time.minute.toString().padStart(2, '0')}"
    }

    /**
     * Cleans up resources.
     */
    fun destroy() {
        scope.cancel()
    }
}
