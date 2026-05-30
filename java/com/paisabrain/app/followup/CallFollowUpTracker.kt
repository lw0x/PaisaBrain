package com.paisabrain.app.followup

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

// Required permission in AndroidManifest.xml:
// <uses-permission android:name="android.permission.READ_CALL_LOG" />
// <uses-permission android:name="android.permission.READ_CONTACTS" />

/**
 * Urgency level for a missed call based on contextual signals.
 */
enum class UrgencyLevel {
    /** Same person called 2+ times, or late night call — potential emergency */
    URGENT,

    /** Single missed call during work hours — standard follow-up needed */
    NORMAL,

    /** Older missed call or known low-priority pattern */
    LOW
}

/**
 * Identifies recurring caller patterns to provide context for follow-ups.
 *
 * @property description Human-readable description of the pattern
 * @property periodDays Approximate cycle length in days (e.g., 30 for monthly)
 * @property typicalDateRange Day-of-month range when calls typically occur
 * @property confidence Confidence score 0.0–1.0 of pattern detection
 */
data class RecurringPattern(
    val description: String,
    val periodDays: Int,
    val typicalDateRange: IntRange,
    val confidence: Float
)

/**
 * Represents a missed call that has not been returned within the configured window.
 *
 * @property id Unique identifier for this follow-up item
 * @property phoneNumber The caller's phone number
 * @property contactName Resolved contact name, or null if not in phonebook
 * @property missedAt Timestamp (epoch millis) when the call was missed
 * @property urgencyLevel Computed urgency based on call context
 * @property isReturned Whether the user has since called this number back
 * @property returnedAt Timestamp when the call was returned, null if not yet
 * @property reminderCount Number of reminders sent for this missed call
 * @property userAction The action the user took (if any)
 * @property callerPattern Detected recurring pattern for this caller, if any
 */
data class MissedCallFollowUp(
    val id: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val contactName: String?,
    val missedAt: Long,
    val urgencyLevel: UrgencyLevel,
    val isReturned: Boolean = false,
    val returnedAt: Long? = null,
    val reminderCount: Int = 0,
    val userAction: CallFollowUpAction? = null,
    val callerPattern: RecurringPattern? = null
)

/**
 * Actions a user can take on a missed call follow-up.
 */
enum class CallFollowUpAction {
    /** User confirmed they called back */
    CALLED_BACK,

    /** User marked the call as not important */
    NOT_IMPORTANT,

    /** User requested a later reminder */
    REMIND_LATER,

    /** User wants to add this number to contacts */
    ADD_TO_CONTACTS
}

/**
 * Monthly statistics for call return behavior.
 *
 * @property month The month these stats cover
 * @property totalMissedCalls Total missed calls detected
 * @property returnedWithinWindow Calls returned within the configured window
 * @property returnRatePercent Percentage of calls returned promptly
 * @property averageReturnTimeMinutes Average time to return a call in minutes
 */
data class CallReturnStats(
    val month: YearMonth,
    val totalMissedCalls: Int,
    val returnedWithinWindow: Int,
    val returnRatePercent: Float,
    val averageReturnTimeMinutes: Long
)

/**
 * Configuration for the call follow-up tracker.
 *
 * @property returnWindowHours Hours after a missed call before considering it "not returned"
 * @property workHoursStart Start of work hours (inclusive)
 * @property workHoursEnd End of work hours (inclusive)
 * @property lateNightStart Start of late-night window (calls here may be emergencies)
 * @property lateNightEnd End of late-night window
 * @property firstNudgeDelayHours Delay before first reminder
 * @property secondNudgeHour Hour of day for second reminder (next morning)
 * @property maxReminders Maximum reminders before auto-archiving
 */
data class CallTrackerConfig(
    val returnWindowHours: Int = 4,
    val workHoursStart: LocalTime = LocalTime.of(9, 0),
    val workHoursEnd: LocalTime = LocalTime.of(18, 0),
    val lateNightStart: LocalTime = LocalTime.of(23, 0),
    val lateNightEnd: LocalTime = LocalTime.of(5, 0),
    val firstNudgeDelayHours: Int = 4,
    val secondNudgeHour: Int = 9,
    val maxReminders: Int = 2
)

/**
 * Tracks missed calls and generates contextual follow-up reminders.
 *
 * This tracker reads the local Android call log (requires READ_CALL_LOG permission)
 * to detect missed calls that haven't been returned within a configurable window.
 * It provides urgency detection, recurring caller pattern recognition, and a
 * gentle reminder schedule.
 *
 * **All processing is entirely on-device. No internet connection required.**
 *
 * Usage:
 * ```kotlin
 * val tracker = CallFollowUpTracker(context)
 * tracker.scanForMissedCalls()
 * val pending = tracker.pendingFollowUps.value
 * ```
 *
 * @property context Android application context for accessing call log and contacts
 * @property config Configuration parameters for timing and behavior
 */
class CallFollowUpTracker(
    private val context: Context,
    private val config: CallTrackerConfig = CallTrackerConfig()
) {
    private val _pendingFollowUps = MutableStateFlow<List<MissedCallFollowUp>>(emptyList())

    /** Observable list of all pending (unresolved) missed call follow-ups. */
    val pendingFollowUps: StateFlow<List<MissedCallFollowUp>> = _pendingFollowUps.asStateFlow()

    private val _stats = MutableStateFlow<CallReturnStats?>(null)

    /** Current month's call return statistics. */
    val stats: StateFlow<CallReturnStats?> = _stats.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Cache of phone number → contact name mappings */
    private val contactCache = mutableMapOf<String, String?>()

    /** Historical call records for pattern detection */
    private val callHistory = mutableListOf<CallLogEntry>()

    /**
     * Internal representation of a call log entry.
     */
    private data class CallLogEntry(
        val phoneNumber: String,
        val timestamp: Long,
        val type: Int,
        val duration: Long
    )

    /**
     * Scans the device call log for missed calls not returned within the configured window.
     *
     * This reads the call log, identifies missed calls, checks if the user has
     * since made an outgoing call to the same number, and creates follow-up items
     * for any unreturned calls.
     *
     * @return List of newly detected missed call follow-ups
     */
    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    suspend fun scanForMissedCalls(): List<MissedCallFollowUp> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val scanWindowStart = now - (48 * 60 * 60 * 1000L) // Look back 48 hours

        val allCalls = readCallLog(scanWindowStart)
        callHistory.clear()
        callHistory.addAll(allCalls)

        val missedCalls = allCalls.filter { it.type == CallLog.Calls.MISSED_TYPE }
        val outgoingCalls = allCalls.filter { it.type == CallLog.Calls.OUTGOING_TYPE }

        val newFollowUps = mutableListOf<MissedCallFollowUp>()

        // Group missed calls by phone number to detect urgency
        val missedByNumber = missedCalls.groupBy { normalizeNumber(it.phoneNumber) }

        for ((normalizedNumber, calls) in missedByNumber) {
            val latestMissed = calls.maxByOrNull { it.timestamp } ?: continue

            // Check if already returned
            val wasReturned = outgoingCalls.any { outgoing ->
                normalizeNumber(outgoing.phoneNumber) == normalizedNumber &&
                        outgoing.timestamp > latestMissed.timestamp
            }

            if (wasReturned) continue

            // Check if within the return window (still time to call back naturally)
            val timeSinceMissed = now - latestMissed.timestamp
            val returnWindowMs = config.returnWindowHours * 60 * 60 * 1000L
            if (timeSinceMissed < returnWindowMs) continue

            // Check if already tracked
            val existingIds = _pendingFollowUps.value.map { normalizeNumber(it.phoneNumber) }
            if (normalizedNumber in existingIds) continue

            // Determine urgency
            val urgency = determineUrgency(calls, latestMissed.timestamp)

            // Resolve contact name
            val contactName = resolveContactName(latestMissed.phoneNumber)

            // Detect recurring patterns
            val pattern = detectRecurringPattern(normalizedNumber)

            val followUp = MissedCallFollowUp(
                phoneNumber = latestMissed.phoneNumber,
                contactName = contactName,
                missedAt = latestMissed.timestamp,
                urgencyLevel = urgency,
                callerPattern = pattern
            )

            newFollowUps.add(followUp)
        }

        // Update state
        val updatedList = _pendingFollowUps.value + newFollowUps
        _pendingFollowUps.value = updatedList

        // Recalculate stats
        recalculateStats()

        newFollowUps
    }

    /**
     * Determines the urgency level of a missed call based on contextual signals.
     *
     * - URGENT: Same person called 2+ times, or called during late night hours
     * - NORMAL: Single call during work hours
     * - LOW: Everything else
     *
     * @param callsFromNumber All missed calls from this number in the scan window
     * @param latestTimestamp The most recent missed call timestamp
     * @return Computed urgency level
     */
    private fun determineUrgency(callsFromNumber: List<CallLogEntry>, latestTimestamp: Long): UrgencyLevel {
        // Multiple calls from same person = URGENT
        if (callsFromNumber.size >= 2) return UrgencyLevel.URGENT

        val callTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(latestTimestamp),
            ZoneId.systemDefault()
        ).toLocalTime()

        // Late night call = potential emergency → URGENT
        if (isLateNight(callTime)) return UrgencyLevel.URGENT

        // During work hours = NORMAL priority
        if (isWorkHours(callTime)) return UrgencyLevel.NORMAL

        return UrgencyLevel.LOW
    }

    /**
     * Checks if a given time falls within the configured late-night window.
     */
    private fun isLateNight(time: LocalTime): Boolean {
        return if (config.lateNightStart > config.lateNightEnd) {
            // Spans midnight: e.g., 23:00 to 05:00
            time >= config.lateNightStart || time <= config.lateNightEnd
        } else {
            time in config.lateNightStart..config.lateNightEnd
        }
    }

    /**
     * Checks if a given time falls within configured work hours.
     */
    private fun isWorkHours(time: LocalTime): Boolean {
        return time in config.workHoursStart..config.workHoursEnd
    }

    /**
     * Detects recurring calling patterns for a given phone number.
     *
     * Analyzes historical call log data to identify patterns such as:
     * - Monthly callers (e.g., calls every month around 25th-28th)
     * - Weekly callers
     * - Specific day-of-month patterns
     *
     * @param normalizedNumber The phone number to analyze (normalized)
     * @return Detected pattern, or null if no clear pattern found
     */
    private fun detectRecurringPattern(normalizedNumber: String): RecurringPattern? {
        // Look at last 90 days of call history for this number
        val now = System.currentTimeMillis()
        val ninetyDaysAgo = now - (90L * 24 * 60 * 60 * 1000)

        val historicalCalls = callHistory.filter {
            normalizeNumber(it.phoneNumber) == normalizedNumber &&
                    it.timestamp > ninetyDaysAgo
        }

        if (historicalCalls.size < 3) return null

        // Analyze day-of-month distribution
        val daysOfMonth = historicalCalls.map { entry ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.timestamp),
                ZoneId.systemDefault()
            ).dayOfMonth
        }

        // Check if calls cluster in a narrow date range (monthly pattern)
        val minDay = daysOfMonth.min()
        val maxDay = daysOfMonth.max()
        val dateSpread = maxDay - minDay

        if (dateSpread <= 5 && historicalCalls.size >= 3) {
            val confidence = when {
                historicalCalls.size >= 5 -> 0.9f
                historicalCalls.size >= 4 -> 0.75f
                else -> 0.6f
            }

            return RecurringPattern(
                description = "This number calls every month around ${minDay}th–${maxDay}th — likely related to a monthly obligation",
                periodDays = 30,
                typicalDateRange = minDay..maxDay,
                confidence = confidence
            )
        }

        // Check for weekly pattern
        val dayOfWeekCounts = historicalCalls.map { entry ->
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.timestamp),
                ZoneId.systemDefault()
            ).dayOfWeek
        }.groupingBy { it }.eachCount()

        val dominantDay = dayOfWeekCounts.maxByOrNull { it.value }
        if (dominantDay != null && dominantDay.value >= historicalCalls.size * 0.6) {
            return RecurringPattern(
                description = "This number typically calls on ${dominantDay.key.name.lowercase().replaceFirstChar { it.uppercase() }}s",
                periodDays = 7,
                typicalDateRange = 1..7,
                confidence = 0.65f
            )
        }

        return null
    }

    /**
     * Resolves a phone number to a contact name from the device phonebook.
     *
     * Results are cached to avoid repeated lookups.
     *
     * @param phoneNumber The raw phone number to look up
     * @return Contact name if found, null otherwise
     */
    private fun resolveContactName(phoneNumber: String): String? {
        val normalized = normalizeNumber(phoneNumber)

        // Check cache first
        if (contactCache.containsKey(normalized)) {
            return contactCache[normalized]
        }

        val name = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val cursor: Cursor? = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }

        contactCache[normalized] = name
        return name
    }

    /**
     * Reads the Android call log within the specified time window.
     *
     * @param sinceTimestamp Only return entries after this timestamp (epoch millis)
     * @return List of call log entries
     */
    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    private fun readCallLog(sinceTimestamp: Long): List<CallLogEntry> {
        val entries = mutableListOf<CallLogEntry>()

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION
        )

        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(sinceTimestamp.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val numberIndex = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateIndex = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val typeIndex = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val durationIndex = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                entries.add(
                    CallLogEntry(
                        phoneNumber = it.getString(numberIndex) ?: "",
                        timestamp = it.getLong(dateIndex),
                        type = it.getInt(typeIndex),
                        duration = it.getLong(durationIndex)
                    )
                )
            }
        }

        return entries
    }

    /**
     * Records a user action on a missed call follow-up.
     *
     * @param followUpId The ID of the follow-up item
     * @param action The action taken by the user
     */
    fun recordAction(followUpId: String, action: CallFollowUpAction) {
        _pendingFollowUps.value = _pendingFollowUps.value.map { followUp ->
            if (followUp.id == followUpId) {
                when (action) {
                    CallFollowUpAction.CALLED_BACK -> followUp.copy(
                        userAction = action,
                        isReturned = true,
                        returnedAt = System.currentTimeMillis()
                    )
                    CallFollowUpAction.NOT_IMPORTANT,
                    CallFollowUpAction.REMIND_LATER,
                    CallFollowUpAction.ADD_TO_CONTACTS -> followUp.copy(userAction = action)
                }
            } else followUp
        }

        // Remove resolved items (called back or not important)
        if (action == CallFollowUpAction.CALLED_BACK || action == CallFollowUpAction.NOT_IMPORTANT) {
            _pendingFollowUps.value = _pendingFollowUps.value.filter { it.id != followUpId }
        }

        scope.launch { recalculateStats() }
    }

    /**
     * Increments the reminder count for a follow-up item.
     * If the max reminders have been sent, marks as "probably not urgent" and archives.
     *
     * @param followUpId The ID of the follow-up item
     * @return true if another reminder should be sent, false if max reached
     */
    fun incrementReminder(followUpId: String): Boolean {
        var shouldRemind = false

        _pendingFollowUps.value = _pendingFollowUps.value.mapNotNull { followUp ->
            if (followUp.id == followUpId) {
                val newCount = followUp.reminderCount + 1
                if (newCount > config.maxReminders) {
                    // Max reminders reached — auto-archive
                    null
                } else {
                    shouldRemind = true
                    followUp.copy(reminderCount = newCount)
                }
            } else followUp
        }

        return shouldRemind
    }

    /**
     * Gets the next scheduled reminder time for a follow-up item.
     *
     * - First nudge: [config.firstNudgeDelayHours] after the missed call
     * - Second nudge: next morning at [config.secondNudgeHour]
     * - After that: no more reminders (marks as "probably not urgent")
     *
     * @param followUp The follow-up item to schedule
     * @return Timestamp for the next reminder, or null if no more reminders
     */
    fun getNextReminderTime(followUp: MissedCallFollowUp): Long? {
        if (followUp.reminderCount >= config.maxReminders) return null

        return when (followUp.reminderCount) {
            0 -> {
                // First nudge: X hours after missed call
                followUp.missedAt + (config.firstNudgeDelayHours * 60 * 60 * 1000L)
            }
            1 -> {
                // Second nudge: next morning at configured hour
                val missedDate = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(followUp.missedAt),
                    ZoneId.systemDefault()
                )
                val nextMorning = missedDate.plusDays(1)
                    .withHour(config.secondNudgeHour)
                    .withMinute(0)
                    .withSecond(0)
                nextMorning.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            else -> null
        }
    }

    /**
     * Generates a human-readable display string for a missed call follow-up.
     *
     * @param followUp The follow-up item
     * @return Display string like "Missed call from Mom (2 calls, URGENT)"
     */
    fun formatDisplayText(followUp: MissedCallFollowUp): String {
        val caller = followUp.contactName ?: followUp.phoneNumber
        val urgencyTag = when (followUp.urgencyLevel) {
            UrgencyLevel.URGENT -> " ⚠️ URGENT"
            UrgencyLevel.NORMAL -> ""
            UrgencyLevel.LOW -> ""
        }

        val timeAgo = formatTimeAgo(System.currentTimeMillis() - followUp.missedAt)

        return buildString {
            append("Missed call from $caller")
            append(urgencyTag)
            append(" • $timeAgo ago")
            if (followUp.callerPattern != null) {
                append("\n📋 ${followUp.callerPattern.description}")
            }
        }
    }

    /**
     * Calculates monthly call return statistics.
     */
    private fun recalculateStats() {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val allFollowUps = _pendingFollowUps.value
        val returnedThisMonth = allFollowUps.filter {
            it.missedAt >= monthStart && it.isReturned
        }
        val allMissedThisMonth = allFollowUps.filter { it.missedAt >= monthStart }

        val totalMissed = allMissedThisMonth.size
        val returned = returnedThisMonth.size

        val returnRate = if (totalMissed > 0) (returned.toFloat() / totalMissed) * 100f else 0f

        val avgReturnTime = if (returnedThisMonth.isNotEmpty()) {
            returnedThisMonth
                .filter { it.returnedAt != null }
                .map { (it.returnedAt!! - it.missedAt) / (60 * 1000) }
                .average()
                .toLong()
        } else 0L

        _stats.value = CallReturnStats(
            month = currentMonth,
            totalMissedCalls = totalMissed,
            returnedWithinWindow = returned,
            returnRatePercent = returnRate,
            averageReturnTimeMinutes = avgReturnTime
        )
    }

    /**
     * Formats a duration in milliseconds to a human-readable "time ago" string.
     */
    private fun formatTimeAgo(durationMs: Long): String {
        val minutes = durationMs / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
    }

    /**
     * Normalizes a phone number for comparison by removing non-digit characters
     * and country code prefixes.
     */
    private fun normalizeNumber(number: String): String {
        val digitsOnly = number.replace(Regex("[^0-9]"), "")
        // Remove common country code prefix (+91 for India)
        return if (digitsOnly.length > 10 && digitsOnly.startsWith("91")) {
            digitsOnly.substring(2)
        } else if (digitsOnly.length > 10 && digitsOnly.startsWith("0")) {
            digitsOnly.substring(1)
        } else {
            digitsOnly
        }
    }

    /**
     * Returns a formatted statistics string for display.
     * Example: "You returned 78% of missed calls within 4 hours this month"
     */
    fun getStatsDisplayText(): String {
        val currentStats = _stats.value ?: return "No call data available yet"

        return buildString {
            append("You returned ${currentStats.returnRatePercent.toInt()}% of missed calls ")
            append("within ${config.returnWindowHours} hours this month")
            if (currentStats.averageReturnTimeMinutes > 0) {
                append("\nAverage response time: ${currentStats.averageReturnTimeMinutes} minutes")
            }
            append("\nTotal missed: ${currentStats.totalMissedCalls} | Returned: ${currentStats.returnedWithinWindow}")
        }
    }

    /**
     * Cleans up resources when the tracker is no longer needed.
     */
    fun destroy() {
        scope.cancel()
        contactCache.clear()
        callHistory.clear()
    }
}
