package com.paisabrain.app.gamification

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Manages savings streak tracking across multiple dimensions.
 *
 * Tracks consecutive days of positive financial behavior including
 * staying under budget, no-spend days, daily saving, timely bill payment,
 * and vault memory captures.
 *
 * Features:
 * - Multiple concurrent streak types
 * - One free streak freeze per week (forgives one bad day)
 * - Personal best records
 * - Milestone tracking with celebration triggers
 * - Persistent storage via SharedPreferences
 */
class StreaksManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "streaks_prefs"
        private const val KEY_STREAKS = "all_streaks"
        private const val KEY_WEEKLY_FREEZE_USED = "weekly_freeze_used"
        private const val KEY_FREEZE_WEEK_START = "freeze_week_start"

        /** Milestone day counts that trigger celebrations */
        val MILESTONES = listOf(3, 7, 14, 21, 30, 60, 90, 180, 365)
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Types of streaks that can be tracked.
     */
    enum class StreakType(val displayName: String, val emoji: String, val description: String) {
        /** Consecutive days spending remained under the daily budget */
        UNDER_BUDGET(
            displayName = "Under Budget",
            emoji = "💰",
            description = "Consecutive days you stayed within your daily budget"
        ),

        /** Consecutive days with zero discretionary spending */
        NO_SPEND(
            displayName = "No Spend",
            emoji = "🚫",
            description = "Consecutive days with no discretionary spending"
        ),

        /** Consecutive days where at least some amount was saved */
        DAILY_SAVER(
            displayName = "Daily Saver",
            emoji = "🐷",
            description = "Consecutive days you saved at least something"
        ),

        /** Consecutive bills paid on or before their due date */
        BILL_PAYER(
            displayName = "Bill Payer",
            emoji = "✅",
            description = "Consecutive bills paid on time without missing any"
        ),

        /** Consecutive days a memory/moment was captured in the vault */
        VAULT_CAPTURE(
            displayName = "Vault Capture",
            emoji = "📸",
            description = "Consecutive days you captured a memory in your vault"
        )
    }

    /**
     * Represents the state of a single streak.
     *
     * @property type The category of streak being tracked
     * @property currentCount Current number of consecutive days/events
     * @property bestEver Personal best record for this streak type
     * @property lastUpdated The last date this streak was incremented
     * @property freezesUsed Total number of freezes ever used on this streak
     * @property isActive Whether the streak is currently active (not broken)
     * @property startDate When the current streak began
     * @property milestoneReached The highest milestone reached in the current streak
     */
    data class Streak(
        val type: StreakType,
        val currentCount: Int = 0,
        val bestEver: Int = 0,
        val lastUpdated: String = "",
        val freezesUsed: Int = 0,
        val isActive: Boolean = false,
        val startDate: String = "",
        val milestoneReached: Int = 0
    )

    /**
     * Result of a streak update operation.
     *
     * @property streak The updated streak state
     * @property milestoneHit If a new milestone was just reached, its value; null otherwise
     * @property newBest Whether this update set a new personal best
     * @property frozeToday Whether a freeze was consumed to save the streak
     */
    data class StreakUpdateResult(
        val streak: Streak,
        val milestoneHit: Int? = null,
        val newBest: Boolean = false,
        val frozeToday: Boolean = false
    )

    /**
     * Summary of all streaks for display purposes.
     */
    data class StreakSummary(
        val activeStreaks: List<Streak>,
        val brokenStreaks: List<Streak>,
        val totalActiveDays: Int,
        val longestEverStreak: Int,
        val longestEverType: StreakType?,
        val freezeAvailableThisWeek: Boolean,
        val nextMilestone: Map<StreakType, Int>
    )

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Retrieves the current state of all streaks.
     *
     * @return Map of streak type to its current state
     */
    fun getAllStreaks(): Map<StreakType, Streak> {
        val saved = loadStreaks()
        // Ensure all types exist
        val result = mutableMapOf<StreakType, Streak>()
        StreakType.values().forEach { type ->
            result[type] = saved[type] ?: Streak(type = type)
        }
        return result
    }

    /**
     * Gets a single streak by type.
     *
     * @param type The streak type to retrieve
     * @return The current streak state
     */
    fun getStreak(type: StreakType): Streak {
        return getAllStreaks()[type] ?: Streak(type = type)
    }

    /**
     * Records a successful day for the given streak type.
     * Increments the counter, checks milestones, and updates personal best.
     *
     * @param type The streak type to increment
     * @return Result containing the updated streak and any celebrations triggered
     */
    fun recordSuccess(type: StreakType): StreakUpdateResult {
        val today = LocalDate.now().format(dateFormatter)
        val streaks = loadStreaksMutable()
        val current = streaks[type] ?: Streak(type = type)

        // Prevent double-counting the same day
        if (current.lastUpdated == today && current.isActive) {
            return StreakUpdateResult(streak = current)
        }

        // Check if streak should have been broken (missed yesterday)
        val shouldReset = shouldResetStreak(current, today)

        val updated: Streak
        var frozeToday = false

        if (shouldReset) {
            // Try to use a freeze
            if (canUseFreeze()) {
                useFreeze()
                frozeToday = true
                updated = current.copy(
                    currentCount = current.currentCount + 1,
                    lastUpdated = today,
                    freezesUsed = current.freezesUsed + 1,
                    isActive = true
                )
            } else {
                // Streak broken — start fresh
                updated = Streak(
                    type = type,
                    currentCount = 1,
                    bestEver = current.bestEver,
                    lastUpdated = today,
                    freezesUsed = current.freezesUsed,
                    isActive = true,
                    startDate = today,
                    milestoneReached = 0
                )
            }
        } else {
            // Normal increment
            val newStart = if (!current.isActive || current.startDate.isEmpty()) today else current.startDate
            updated = current.copy(
                currentCount = current.currentCount + 1,
                lastUpdated = today,
                isActive = true,
                startDate = newStart
            )
        }

        // Check personal best
        val newBest = updated.currentCount > updated.bestEver
        val withBest = if (newBest) {
            updated.copy(bestEver = updated.currentCount)
        } else {
            updated
        }

        // Check milestone
        val milestoneHit = checkMilestone(withBest)
        val withMilestone = if (milestoneHit != null) {
            withBest.copy(milestoneReached = milestoneHit)
        } else {
            withBest
        }

        // Save
        streaks[type] = withMilestone
        saveStreaks(streaks)

        return StreakUpdateResult(
            streak = withMilestone,
            milestoneHit = milestoneHit,
            newBest = newBest,
            frozeToday = frozeToday
        )
    }

    /**
     * Explicitly breaks a streak (e.g., user overspent today).
     * Attempts to use a freeze first if available.
     *
     * @param type The streak type that was broken
     * @param forceBreak If true, breaks even if a freeze is available
     * @return The updated streak state
     */
    fun breakStreak(type: StreakType, forceBreak: Boolean = false): Streak {
        val streaks = loadStreaksMutable()
        val current = streaks[type] ?: Streak(type = type)

        if (!current.isActive) return current

        // Try freeze unless forced
        if (!forceBreak && canUseFreeze()) {
            useFreeze()
            val frozen = current.copy(
                freezesUsed = current.freezesUsed + 1
            )
            streaks[type] = frozen
            saveStreaks(streaks)
            return frozen
        }

        // Actually break it
        val broken = current.copy(
            isActive = false,
            currentCount = 0,
            milestoneReached = 0
        )
        streaks[type] = broken
        saveStreaks(streaks)
        return broken
    }

    /**
     * Resets a specific streak to zero (user-initiated fresh start).
     *
     * @param type The streak type to reset
     * @return The reset streak (preserves bestEver)
     */
    fun resetStreak(type: StreakType): Streak {
        val streaks = loadStreaksMutable()
        val current = streaks[type] ?: Streak(type = type)

        val reset = Streak(
            type = type,
            currentCount = 0,
            bestEver = current.bestEver,
            lastUpdated = "",
            freezesUsed = current.freezesUsed,
            isActive = false,
            startDate = "",
            milestoneReached = 0
        )
        streaks[type] = reset
        saveStreaks(streaks)
        return reset
    }

    /**
     * Checks whether a streak freeze is available this week.
     * Each user gets one free freeze per calendar week.
     *
     * @return True if a freeze can be used
     */
    fun canUseFreeze(): Boolean {
        val currentWeekStart = getWeekStart(LocalDate.now())
        val savedWeekStart = prefs.getString(KEY_FREEZE_WEEK_START, "") ?: ""

        return if (savedWeekStart == currentWeekStart) {
            !prefs.getBoolean(KEY_WEEKLY_FREEZE_USED, false)
        } else {
            // New week — freeze is available
            true
        }
    }

    /**
     * Returns a comprehensive summary of all streak states.
     *
     * @return StreakSummary with active/broken streaks, stats, and next milestones
     */
    fun getSummary(): StreakSummary {
        val today = LocalDate.now().format(dateFormatter)
        val allStreaks = getAllStreaks()

        // Check for any that should have been reset due to inactivity
        val validatedStreaks = allStreaks.mapValues { (_, streak) ->
            if (streak.isActive && shouldResetStreak(streak, today)) {
                streak.copy(isActive = false, currentCount = 0, milestoneReached = 0)
            } else {
                streak
            }
        }

        val active = validatedStreaks.values.filter { it.isActive }
        val broken = validatedStreaks.values.filter { !it.isActive }

        val longestEver = validatedStreaks.values.maxByOrNull { it.bestEver }
        val totalActive = active.sumOf { it.currentCount }

        val nextMilestones = mutableMapOf<StreakType, Int>()
        active.forEach { streak ->
            val next = MILESTONES.firstOrNull { it > streak.currentCount }
            if (next != null) {
                nextMilestones[streak.type] = next
            }
        }

        return StreakSummary(
            activeStreaks = active,
            brokenStreaks = broken,
            totalActiveDays = totalActive,
            longestEverStreak = longestEver?.bestEver ?: 0,
            longestEverType = longestEver?.type,
            freezeAvailableThisWeek = canUseFreeze(),
            nextMilestone = nextMilestones
        )
    }

    /**
     * Gets motivational message based on current streak state.
     *
     * @param type The streak type to get a message for
     * @return Motivational or encouraging message string
     */
    fun getMotivationalMessage(type: StreakType): String {
        val streak = getStreak(type)

        return when {
            !streak.isActive -> "Start fresh today! Every journey begins with a single step. 🌱"
            streak.currentCount == 1 -> "Day 1 — the hardest step is done! Keep going! 🎯"
            streak.currentCount < 3 -> "Building momentum! ${streak.currentCount} days and counting. 💪"
            streak.currentCount < 7 -> "Almost a full week! You're on fire! 🔥"
            streak.currentCount < 14 -> "${streak.currentCount} days! This is becoming a habit. 🧠"
            streak.currentCount < 30 -> "Over two weeks! You're in the zone. 🚀"
            streak.currentCount < 60 -> "${streak.currentCount} days! You're unstoppable. ⭐"
            streak.currentCount < 90 -> "Over a month! This is who you are now. 🏆"
            streak.currentCount < 180 -> "${streak.currentCount} days! Legendary discipline. 👑"
            streak.currentCount < 365 -> "Over 3 months! You're rewriting your financial story. 📖"
            else -> "${streak.currentCount} days! A full year of excellence. You're a legend! 🌟"
        }
    }

    /**
     * Gets the days remaining until the next milestone for a streak.
     *
     * @param type The streak type
     * @return Days until next milestone, or null if all milestones achieved
     */
    fun daysUntilNextMilestone(type: StreakType): Int? {
        val streak = getStreak(type)
        val nextMilestone = MILESTONES.firstOrNull { it > streak.currentCount }
        return nextMilestone?.minus(streak.currentCount)
    }

    /**
     * Performs end-of-day validation on all streaks.
     * Should be called once daily (e.g., via WorkManager) to auto-break
     * streaks that were not renewed.
     *
     * @return List of streak types that were broken due to inactivity
     */
    fun performDailyValidation(): List<StreakType> {
        val today = LocalDate.now().format(dateFormatter)
        val streaks = loadStreaksMutable()
        val broken = mutableListOf<StreakType>()

        streaks.forEach { (type, streak) ->
            if (streak.isActive && shouldResetStreak(streak, today)) {
                // Try freeze
                if (canUseFreeze()) {
                    useFreeze()
                    streaks[type] = streak.copy(freezesUsed = streak.freezesUsed + 1)
                } else {
                    streaks[type] = streak.copy(
                        isActive = false,
                        currentCount = 0,
                        milestoneReached = 0
                    )
                    broken.add(type)
                }
            }
        }

        saveStreaks(streaks)
        return broken
    }

    /**
     * Clears all streak data. Use with caution — this is irreversible.
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Determines if a streak should be reset based on the last update date.
     * A streak is considered broken if more than 1 day has passed since last update.
     */
    private fun shouldResetStreak(streak: Streak, today: String): Boolean {
        if (streak.lastUpdated.isEmpty()) return false
        if (!streak.isActive) return false

        return try {
            val lastDate = LocalDate.parse(streak.lastUpdated, dateFormatter)
            val todayDate = LocalDate.parse(today, dateFormatter)
            val daysBetween = ChronoUnit.DAYS.between(lastDate, todayDate)
            daysBetween > 1 // More than 1 day gap means missed a day
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current count has hit a new milestone.
     *
     * @return The milestone value if just reached, null otherwise
     */
    private fun checkMilestone(streak: Streak): Int? {
        val currentMilestone = MILESTONES.lastOrNull { it <= streak.currentCount }
        return if (currentMilestone != null && currentMilestone > streak.milestoneReached) {
            currentMilestone
        } else {
            null
        }
    }

    /**
     * Consumes the weekly freeze.
     */
    private fun useFreeze() {
        val currentWeekStart = getWeekStart(LocalDate.now())
        prefs.edit()
            .putString(KEY_FREEZE_WEEK_START, currentWeekStart)
            .putBoolean(KEY_WEEKLY_FREEZE_USED, true)
            .apply()
    }

    /**
     * Gets the ISO date string for the start of the week containing the given date.
     */
    private fun getWeekStart(date: LocalDate): String {
        val dayOfWeek = date.dayOfWeek.value // 1 (Monday) to 7 (Sunday)
        val weekStart = date.minusDays((dayOfWeek - 1).toLong())
        return weekStart.format(dateFormatter)
    }

    /**
     * Loads all streaks from SharedPreferences.
     */
    private fun loadStreaks(): Map<StreakType, Streak> {
        val json = prefs.getString(KEY_STREAKS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Streak>>() {}.type
            val raw: Map<String, Streak> = gson.fromJson(json, type)
            raw.mapKeys { (key, _) -> StreakType.valueOf(key) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Loads streaks as a mutable map for modification.
     */
    private fun loadStreaksMutable(): MutableMap<StreakType, Streak> {
        return loadStreaks().toMutableMap()
    }

    /**
     * Persists all streaks to SharedPreferences.
     */
    private fun saveStreaks(streaks: Map<StreakType, Streak>) {
        val serializable = streaks.mapKeys { (key, _) -> key.name }
        val json = gson.toJson(serializable)
        prefs.edit().putString(KEY_STREAKS, json).apply()
    }
}
