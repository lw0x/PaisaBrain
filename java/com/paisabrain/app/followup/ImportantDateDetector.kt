package com.paisabrain.app.followup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/**
 * Types of important dates that can be detected from text.
 */
enum class EventType {
    /** Person's birthday */
    BIRTHDAY,

    /** Wedding or relationship anniversary */
    ANNIVERSARY,

    /** Doctor, meeting, or other scheduled appointment */
    APPOINTMENT,

    /** Insurance, subscription, license, or document renewal */
    RENEWAL,

    /** Follow-up action with a future date */
    FOLLOW_UP,

    /** Academic or professional examination */
    EXAM,

    /** Work or social meeting */
    MEETING,

    /** Any other date-bearing event */
    OTHER
}

/**
 * Represents an important date detected from vault entries or notes.
 *
 * @property id Unique identifier
 * @property eventType Category of the event
 * @property title Human-readable title for the event
 * @property date The event date/time (epoch millis)
 * @property reminderDates List of timestamps when reminders should fire
 * @property linkedVaultEntryId ID of the vault entry that contained this date
 * @property budgetSuggestion Suggested budget based on history, null if not applicable
 * @property isRecurringYearly Whether this event recurs every year
 * @property notes Additional context or excerpt from the source text
 */
data class ImportantDate(
    val id: String = UUID.randomUUID().toString(),
    val eventType: EventType,
    val title: String,
    val date: Long,
    val reminderDates: List<Long>,
    val linkedVaultEntryId: String? = null,
    val budgetSuggestion: Double? = null,
    val isRecurringYearly: Boolean = false,
    val notes: String? = null
)

/**
 * Result of scanning text for dates — contains the detected date and context.
 *
 * @property rawMatch The exact text that matched
 * @property resolvedDate The parsed date
 * @property eventType Detected event category
 * @property personName Extracted person name, if mentioned
 * @property context Surrounding text for display
 * @property confidence Detection confidence 0.0–1.0
 */
data class DateDetectionResult(
    val rawMatch: String,
    val resolvedDate: LocalDate,
    val eventType: EventType,
    val personName: String?,
    val context: String,
    val confidence: Float
)

/**
 * Historical gift/spending record for budget suggestions.
 *
 * @property personName The person the gift was for
 * @property eventType What occasion the gift was for
 * @property amount Amount spent
 * @property date When the gift was given
 * @property description What was purchased
 */
data class GiftHistoryRecord(
    val personName: String,
    val eventType: EventType,
    val amount: Double,
    val date: Long,
    val description: String?
)

/**
 * Configuration for the important date detector.
 *
 * @property birthdayPreAlertDays Days before birthday for first reminder
 * @property birthdayDayBeforeAlert Whether to also alert 1 day before
 * @property appointmentPreAlertDays Days before appointment for reminder
 * @property renewalPreAlertDays Days before renewal for first reminder
 * @property renewalWeekBeforeAlert Whether to also alert 7 days before renewal
 * @property defaultPreAlertDays Default advance reminder for other events
 */
data class DateDetectorConfig(
    val birthdayPreAlertDays: Int = 7,
    val birthdayDayBeforeAlert: Boolean = true,
    val appointmentPreAlertDays: Int = 1,
    val renewalPreAlertDays: Int = 30,
    val renewalWeekBeforeAlert: Boolean = true,
    val defaultPreAlertDays: Int = 1
)

/**
 * Auto-detects important dates from vault entries, notes, and transcriptions.
 *
 * This detector scans text content (from OCR, voice transcriptions, and manual notes)
 * for date-related patterns and creates reminder entries with appropriate pre-event
 * alerts. It supports:
 *
 * - Explicit dates: "birthday on 15th March", "renewal date: 10/08/2026"
 * - Relative dates: "follow up in 2 weeks", "call back next Monday"
 * - Contextual dates: "meeting on Thursday", "exam on 15th"
 * - Vague dates: "insurance expires November"
 *
 * For recurring events (birthdays, anniversaries), it offers auto-annual reminders.
 * For events with spending history, it suggests budgets based on past behavior.
 *
 * **All processing is entirely on-device. No internet connection required.**
 *
 * Usage:
 * ```kotlin
 * val detector = ImportantDateDetector(config)
 * val results = detector.scanText("Mom's birthday on 15th March", vaultEntryId = "entry123")
 * results.forEach { detector.createReminder(it) }
 * ```
 *
 * @property config Configuration for alert timing
 */
class ImportantDateDetector(
    private val config: DateDetectorConfig = DateDetectorConfig()
) {
    private val _detectedDates = MutableStateFlow<List<ImportantDate>>(emptyList())

    /** All detected important dates with their reminders. */
    val detectedDates: StateFlow<List<ImportantDate>> = _detectedDates.asStateFlow()

    /** Historical gift records for budget suggestions. */
    private val giftHistory = mutableListOf<GiftHistoryRecord>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ─────────────────────────────────────────────────────────────
    // Date Pattern Definitions
    // ─────────────────────────────────────────────────────────────

    private val monthNames = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december"
    )
    private val monthAbbreviations = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    )
    private val dayOfWeekNames = listOf(
        "monday", "tuesday", "wednesday", "thursday",
        "friday", "saturday", "sunday"
    )

    /** Pattern: "birthday on 15th March" or "birthday 22 December" */
    private val birthdayPattern = Regex(
        """(\w+(?:'s)?)\s*birthday\s*(?:on\s*)?(\d{1,2})(?:st|nd|rd|th)?\s*(${monthNames.joinToString("|")}|${monthAbbreviations.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "anniversary 22 December" or "anniversary on 15th March" */
    private val anniversaryPattern = Regex(
        """anniversary\s*(?:on\s*)?(\d{1,2})(?:st|nd|rd|th)?\s*(${monthNames.joinToString("|")}|${monthAbbreviations.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "appointment on 3rd June" or "appointment 15/06/2026" */
    private val appointmentPattern = Regex(
        """(?:appointment|doctor|dentist|checkup|check-up)\s*(?:on\s*)?(\d{1,2})(?:st|nd|rd|th)?\s*(${monthNames.joinToString("|")}|${monthAbbreviations.joinToString("|")}|\d{1,2}[/\-]\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "renewal date: 10/08/2026" or "expires on 15th November" */
    private val renewalPattern = Regex(
        """(?:renewal|renew|expires?|expiry|valid\s*(?:till|until|upto))\s*(?:date)?:?\s*(?:on\s*)?(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "expires November" or "renewal in November" */
    private val renewalMonthPattern = Regex(
        """(?:renewal|renew|expires?|expiry|insurance)\s*(?:in|on)?\s*(${monthNames.joinToString("|")}|${monthAbbreviations.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "follow up in 2 weeks" or "follow up in 3 days" */
    private val followUpRelativePattern = Regex(
        """(?:follow\s*up|followup|call\s*back|remind\s*me)\s*(?:in|after)\s*(\d+)\s*(day|days|week|weeks|month|months)""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "call back next Monday" or "meeting next Thursday" */
    private val nextDayPattern = Regex(
        """(?:call\s*back|meeting|appointment|follow\s*up)\s*(?:on\s*)?next\s*(${dayOfWeekNames.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "meeting on Thursday" (this week) */
    private val thisDayPattern = Regex(
        """(?:meeting|call|appointment)\s*(?:on\s*)?(${dayOfWeekNames.joinToString("|")})""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: "exam on 15th" (day of current/next month) */
    private val dayOnlyPattern = Regex(
        """(?:exam|test|interview|meeting|appointment|deadline)\s*(?:on|is\s*on)?\s*(?:the\s*)?(\d{1,2})(?:st|nd|rd|th)""",
        RegexOption.IGNORE_CASE
    )

    /** Pattern: explicit date "dd/mm/yyyy" or "dd-mm-yyyy" */
    private val explicitDatePattern = Regex(
        """(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})"""
    )

    /**
     * Scans text content for date patterns and returns detection results.
     *
     * @param text The text to scan (from OCR, transcription, or manual note)
     * @param vaultEntryId Optional ID of the vault entry this text came from
     * @return List of detected date results
     */
    fun scanText(text: String, vaultEntryId: String? = null): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        results.addAll(detectBirthdays(text))
        results.addAll(detectAnniversaries(text))
        results.addAll(detectAppointments(text))
        results.addAll(detectRenewals(text))
        results.addAll(detectFollowUps(text))
        results.addAll(detectDayReferences(text))
        results.addAll(detectExams(text))

        // Deduplicate by resolved date + event type
        return results.distinctBy { "${it.resolvedDate}_${it.eventType}" }
    }

    /**
     * Creates an ImportantDate reminder from a detection result.
     *
     * @param result The date detection result
     * @param vaultEntryId Optional vault entry ID to link
     * @return The created ImportantDate with computed reminder schedule
     */
    fun createReminder(result: DateDetectionResult, vaultEntryId: String? = null): ImportantDate {
        val eventDate = result.resolvedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reminderDates = computeReminderDates(result.resolvedDate, result.eventType)
        val isRecurring = result.eventType == EventType.BIRTHDAY || result.eventType == EventType.ANNIVERSARY
        val budget = suggestBudget(result.personName, result.eventType)

        val title = buildTitle(result)

        val importantDate = ImportantDate(
            eventType = result.eventType,
            title = title,
            date = eventDate,
            reminderDates = reminderDates,
            linkedVaultEntryId = vaultEntryId,
            budgetSuggestion = budget,
            isRecurringYearly = isRecurring,
            notes = "Detected from: \"${result.context}\""
        )

        _detectedDates.value = _detectedDates.value + importantDate
        return importantDate
    }

    /**
     * Scans text and automatically creates reminders for all detected dates.
     *
     * @param text Text content to scan
     * @param vaultEntryId Source vault entry ID
     * @return List of created ImportantDate reminders
     */
    fun scanAndCreateReminders(text: String, vaultEntryId: String? = null): List<ImportantDate> {
        val results = scanText(text, vaultEntryId)
        return results.map { createReminder(it, vaultEntryId) }
    }

    /**
     * Adds a gift history record for budget suggestion improvement.
     *
     * @param record The gift/spending record to add
     */
    fun addGiftHistory(record: GiftHistoryRecord) {
        giftHistory.add(record)
    }

    /**
     * Gets a budget suggestion message for an upcoming event.
     *
     * @param personName The person the event is about
     * @param eventType The type of event
     * @return Suggestion message, or null if no history
     */
    fun getBudgetSuggestionText(personName: String?, eventType: EventType): String? {
        if (personName == null) return null

        val relevantHistory = giftHistory.filter {
            it.personName.equals(personName, ignoreCase = true) && it.eventType == eventType
        }

        if (relevantHistory.isEmpty()) return null

        val lastGift = relevantHistory.maxByOrNull { it.date } ?: return null
        val avgAmount = relevantHistory.map { it.amount }.average()

        return buildString {
            append("${eventType.name.lowercase().replaceFirstChar { it.uppercase() }} detected for $personName. ")
            append("Last time you spent ₹${lastGift.amount.toLong()}")
            lastGift.description?.let { append(" on $it") }
            append(". ")
            if (relevantHistory.size > 1) {
                append("Average: ₹${avgAmount.toLong()}. ")
            }
            append("Budget this time?")
        }
    }

    /**
     * Checks if a detected date is already tracked to avoid duplicates.
     *
     * @param date The date to check
     * @param eventType The event type
     * @return true if already tracked
     */
    fun isAlreadyTracked(date: LocalDate, eventType: EventType): Boolean {
        val dateMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return _detectedDates.value.any { it.date == dateMs && it.eventType == eventType }
    }

    /**
     * Gets upcoming reminders that should fire within the specified window.
     *
     * @param withinHours Look ahead this many hours
     * @return List of ImportantDates with reminders due soon
     */
    fun getUpcomingReminders(withinHours: Int = 24): List<ImportantDate> {
        val now = System.currentTimeMillis()
        val windowEnd = now + (withinHours.toLong() * 60 * 60 * 1000)

        return _detectedDates.value.filter { importantDate ->
            importantDate.reminderDates.any { reminderTime ->
                reminderTime in now..windowEnd
            }
        }
    }

    /**
     * Generates a recurring yearly reminder for the next occurrence.
     *
     * @param importantDate The recurring date to advance
     * @return New ImportantDate for next year's occurrence
     */
    fun createNextYearlyOccurrence(importantDate: ImportantDate): ImportantDate? {
        if (!importantDate.isRecurringYearly) return null

        val currentDate = Instant.ofEpochMilli(importantDate.date)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val nextYear = currentDate.plusYears(1)
        val nextDateMs = nextYear.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val reminderDates = computeReminderDates(nextYear, importantDate.eventType)

        val newDate = importantDate.copy(
            id = UUID.randomUUID().toString(),
            date = nextDateMs,
            reminderDates = reminderDates
        )

        _detectedDates.value = _detectedDates.value + newDate
        return newDate
    }

    // ─────────────────────────────────────────────────────────────
    // Detection Methods
    // ─────────────────────────────────────────────────────────────

    private fun detectBirthdays(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        birthdayPattern.findAll(text).forEach { match ->
            val personName = match.groupValues[1].removeSuffix("'s").trim()
            val day = match.groupValues[2].toIntOrNull() ?: return@forEach
            val monthStr = match.groupValues[3].lowercase()
            val month = resolveMonth(monthStr) ?: return@forEach
            val year = resolveYear(month, day)
            val date = try { LocalDate.of(year, month, day) } catch (e: Exception) { return@forEach }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.BIRTHDAY,
                personName = personName,
                context = extractContext(text, match.range),
                confidence = 0.95f
            ))
        }

        return results
    }

    private fun detectAnniversaries(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        anniversaryPattern.findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val monthStr = match.groupValues[2].lowercase()
            val month = resolveMonth(monthStr) ?: return@forEach
            val year = resolveYear(month, day)
            val date = try { LocalDate.of(year, month, day) } catch (e: Exception) { return@forEach }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.ANNIVERSARY,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.9f
            ))
        }

        return results
    }

    private fun detectAppointments(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        appointmentPattern.findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val monthStr = match.groupValues[2].lowercase()
            val month = resolveMonth(monthStr) ?: return@forEach
            val year = resolveYear(month, day)
            val date = try { LocalDate.of(year, month, day) } catch (e: Exception) { return@forEach }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.APPOINTMENT,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.85f
            ))
        }

        return results
    }

    private fun detectRenewals(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        // Explicit date format: dd/mm/yyyy
        renewalPattern.findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = match.groupValues[2].toIntOrNull() ?: return@forEach
            val yearStr = match.groupValues[3]
            val year = resolveExplicitYear(yearStr) ?: return@forEach
            val date = try { LocalDate.of(year, month, day) } catch (e: Exception) { return@forEach }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.RENEWAL,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.9f
            ))
        }

        // Month-only: "expires November"
        renewalMonthPattern.findAll(text).forEach { match ->
            val monthStr = match.groupValues[1].lowercase()
            val month = resolveMonth(monthStr) ?: return@forEach
            val year = resolveYear(month, 1)
            val date = try { LocalDate.of(year, month, 1) } catch (e: Exception) { return@forEach }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.RENEWAL,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.7f
            ))
        }

        return results
    }

    private fun detectFollowUps(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        // Relative: "follow up in 2 weeks"
        followUpRelativePattern.findAll(text).forEach { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return@forEach
            val unit = match.groupValues[2].lowercase()
            val today = LocalDate.now()

            val date = when {
                unit.startsWith("day") -> today.plusDays(count.toLong())
                unit.startsWith("week") -> today.plusWeeks(count.toLong())
                unit.startsWith("month") -> today.plusMonths(count.toLong())
                else -> return@forEach
            }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.FOLLOW_UP,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.85f
            ))
        }

        // "next Monday"
        nextDayPattern.findAll(text).forEach { match ->
            val dayName = match.groupValues[1].lowercase()
            val targetDay = resolveDayOfWeek(dayName) ?: return@forEach
            val today = LocalDate.now()
            var date = today.with(java.time.temporal.TemporalAdjusters.next(targetDay))

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.FOLLOW_UP,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.8f
            ))
        }

        return results
    }

    private fun detectDayReferences(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        // "meeting on Thursday" (this week or next)
        thisDayPattern.findAll(text).forEach { match ->
            val dayName = match.groupValues[1].lowercase()
            val targetDay = resolveDayOfWeek(dayName) ?: return@forEach
            val today = LocalDate.now()

            val date = if (today.dayOfWeek.value < targetDay.value) {
                today.with(java.time.temporal.TemporalAdjusters.nextOrSame(targetDay))
            } else {
                today.with(java.time.temporal.TemporalAdjusters.next(targetDay))
            }

            val eventType = when {
                match.value.lowercase().contains("meeting") -> EventType.MEETING
                match.value.lowercase().contains("appointment") -> EventType.APPOINTMENT
                else -> EventType.FOLLOW_UP
            }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = eventType,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.75f
            ))
        }

        return results
    }

    private fun detectExams(text: String): List<DateDetectionResult> {
        val results = mutableListOf<DateDetectionResult>()

        dayOnlyPattern.findAll(text).forEach { match ->
            if (!match.value.lowercase().contains("exam") &&
                !match.value.lowercase().contains("test") &&
                !match.value.lowercase().contains("interview")) return@forEach

            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val today = LocalDate.now()
            val date = if (day >= today.dayOfMonth) {
                LocalDate.of(today.year, today.month, day)
            } else {
                LocalDate.of(today.year, today.month.plus(1), day)
            }

            results.add(DateDetectionResult(
                rawMatch = match.value,
                resolvedDate = date,
                eventType = EventType.EXAM,
                personName = null,
                context = extractContext(text, match.range),
                confidence = 0.8f
            ))
        }

        return results
    }

    // ─────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Computes reminder dates based on event type and configured alert windows.
     *
     * - Birthdays: 7 days + 1 day before
     * - Appointments: 1 day before
     * - Renewals: 30 days + 7 days before
     * - Others: 1 day before
     */
    private fun computeReminderDates(eventDate: LocalDate, eventType: EventType): List<Long> {
        val reminders = mutableListOf<Long>()
        val zone = ZoneId.systemDefault()

        when (eventType) {
            EventType.BIRTHDAY -> {
                reminders.add(
                    eventDate.minusDays(config.birthdayPreAlertDays.toLong())
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
                if (config.birthdayDayBeforeAlert) {
                    reminders.add(
                        eventDate.minusDays(1)
                            .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    )
                }
            }
            EventType.ANNIVERSARY -> {
                reminders.add(
                    eventDate.minusDays(config.birthdayPreAlertDays.toLong())
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
                reminders.add(
                    eventDate.minusDays(1)
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
            }
            EventType.APPOINTMENT, EventType.MEETING -> {
                reminders.add(
                    eventDate.minusDays(config.appointmentPreAlertDays.toLong())
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
            }
            EventType.RENEWAL -> {
                reminders.add(
                    eventDate.minusDays(config.renewalPreAlertDays.toLong())
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
                if (config.renewalWeekBeforeAlert) {
                    reminders.add(
                        eventDate.minusDays(7)
                            .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                    )
                }
            }
            EventType.FOLLOW_UP, EventType.EXAM, EventType.OTHER -> {
                reminders.add(
                    eventDate.minusDays(config.defaultPreAlertDays.toLong())
                        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                )
            }
        }

        // Also add the event date itself at morning
        reminders.add(
            eventDate.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        )

        // Filter out past dates
        val now = System.currentTimeMillis()
        return reminders.filter { it > now }.sorted()
    }

    /**
     * Resolves a month name/abbreviation to a month number (1-12).
     */
    private fun resolveMonth(monthStr: String): Int? {
        val idx = monthNames.indexOf(monthStr.lowercase())
        if (idx >= 0) return idx + 1

        val abbrIdx = monthAbbreviations.indexOf(monthStr.lowercase())
        if (abbrIdx >= 0) return abbrIdx + 1

        return null
    }

    /**
     * Resolves the year for a given month/day, choosing the next future occurrence.
     */
    private fun resolveYear(month: Int, day: Int): Int {
        val today = LocalDate.now()
        val thisYear = LocalDate.of(today.year, month, day.coerceIn(1, 28))
        return if (thisYear.isBefore(today)) {
            today.year + 1
        } else {
            today.year
        }
    }

    /**
     * Resolves an explicit year string (2-digit or 4-digit) to a full year.
     */
    private fun resolveExplicitYear(yearStr: String): Int? {
        val year = yearStr.toIntOrNull() ?: return null
        return when {
            year in 2020..2099 -> year
            year in 20..99 -> 2000 + year
            year in 0..19 -> 2000 + year
            else -> null
        }
    }

    /**
     * Resolves a day-of-week name to a DayOfWeek enum.
     */
    private fun resolveDayOfWeek(name: String): DayOfWeek? {
        return when (name.lowercase()) {
            "monday" -> DayOfWeek.MONDAY
            "tuesday" -> DayOfWeek.TUESDAY
            "wednesday" -> DayOfWeek.WEDNESDAY
            "thursday" -> DayOfWeek.THURSDAY
            "friday" -> DayOfWeek.FRIDAY
            "saturday" -> DayOfWeek.SATURDAY
            "sunday" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    /**
     * Extracts surrounding context text for a match.
     */
    private fun extractContext(text: String, matchRange: IntRange): String {
        val contextStart = (matchRange.first - 30).coerceAtLeast(0)
        val contextEnd = (matchRange.last + 30).coerceAtMost(text.length)
        val context = text.substring(contextStart, contextEnd).trim()
        return if (contextStart > 0) "...$context" else context
    }

    /**
     * Builds a human-readable title for a detected date.
     */
    private fun buildTitle(result: DateDetectionResult): String {
        return when (result.eventType) {
            EventType.BIRTHDAY -> "${result.personName ?: "Someone"}'s Birthday"
            EventType.ANNIVERSARY -> "Anniversary"
            EventType.APPOINTMENT -> "Appointment on ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
            EventType.RENEWAL -> "Renewal due ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
            EventType.FOLLOW_UP -> "Follow up on ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
            EventType.EXAM -> "Exam on ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
            EventType.MEETING -> "Meeting on ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
            EventType.OTHER -> "Event on ${result.resolvedDate.format(DateTimeFormatter.ofPattern("d MMM"))}"
        }
    }

    /**
     * Suggests a budget for an event based on gift history.
     *
     * @return Suggested amount, or null if no history
     */
    private fun suggestBudget(personName: String?, eventType: EventType): Double? {
        if (personName == null) return null
        if (eventType != EventType.BIRTHDAY && eventType != EventType.ANNIVERSARY) return null

        val relevant = giftHistory.filter {
            it.personName.equals(personName, ignoreCase = true) && it.eventType == eventType
        }

        return if (relevant.isNotEmpty()) {
            relevant.map { it.amount }.average()
        } else null
    }

    /**
     * Generates the link-back display text showing the source of a reminder.
     *
     * @param importantDate The important date with a linked vault entry
     * @param vaultEntryDate The date of the original vault entry
     * @param excerpt A short excerpt from the original text
     * @return Formatted link-back text
     */
    fun formatLinkBackText(importantDate: ImportantDate, vaultEntryDate: LocalDate, excerpt: String): String {
        val dateStr = vaultEntryDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        val shortExcerpt = if (excerpt.length > 50) excerpt.take(50) + "..." else excerpt
        return "This reminder was created from your note on $dateStr: \"$shortExcerpt\""
    }

    /**
     * Generates a message asking about recurring detection.
     *
     * @param importantDate The detected recurring date
     * @return Message asking if it should auto-remind annually
     */
    fun generateRecurringPrompt(importantDate: ImportantDate): String? {
        if (!importantDate.isRecurringYearly) return null

        return when (importantDate.eventType) {
            EventType.BIRTHDAY -> "Same birthday every year — auto-remind annually? 🎂"
            EventType.ANNIVERSARY -> "Anniversary repeats yearly — set up annual reminders? 💍"
            else -> null
        }
    }

    /**
     * Cleans up resources.
     */
    fun destroy() {
        scope.cancel()
    }
}
