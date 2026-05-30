package com.paisabrain.app.gamification

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * XP-based level progression system that rewards positive financial behaviors.
 *
 * Users earn XP through various actions like logging transactions, staying
 * under budget, maintaining streaks, and unlocking achievements. XP accumulates
 * toward levels 1-20, each with a unique title reflecting growing financial mastery.
 *
 * The XP curve is exponential — early levels are quick to achieve for motivation,
 * while later levels require sustained discipline.
 */
class LevelSystem(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "level_system_prefs"
        private const val KEY_TOTAL_XP = "total_xp"
        private const val KEY_XP_HISTORY = "xp_history"
        private const val KEY_DAILY_LOGIN_DATE = "daily_login_date"
        private const val KEY_LEVEL_UP_PENDING = "level_up_pending"

        /** Maximum achievable level */
        const val MAX_LEVEL = 20

        /** Base XP required for level 2 */
        private const val BASE_XP = 100

        /** Exponential growth factor per level */
        private const val GROWTH_FACTOR = 1.45
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Actions that grant XP, with their reward amounts.
     *
     * @property displayName Human-readable action name
     * @property xp XP points awarded for this action
     * @property emoji Visual indicator
     * @property description What the user did to earn this
     * @property dailyLimit Maximum times this can be earned per day (0 = unlimited)
     */
    enum class XpAction(
        val displayName: String,
        val xp: Int,
        val emoji: String,
        val description: String,
        val dailyLimit: Int = 0
    ) {
        /** Awarded once per calendar day for opening the app */
        DAILY_LOGIN(
            displayName = "Daily Login",
            xp = 10,
            emoji = "📅",
            description = "Opened the app today",
            dailyLimit = 1
        ),

        /** Awarded for each day spent under the daily budget */
        UNDER_BUDGET_DAY(
            displayName = "Under Budget Day",
            xp = 20,
            emoji = "💰",
            description = "Stayed within your daily budget",
            dailyLimit = 1
        ),

        /** Awarded for capturing a memory/moment in the vault */
        VAULT_CAPTURE(
            displayName = "Vault Capture",
            xp = 15,
            emoji = "📸",
            description = "Captured a memory in the vault",
            dailyLimit = 3
        ),

        /** Awarded when a bill is paid on or before its due date */
        BILL_PAID_ON_TIME(
            displayName = "Bill Paid On Time",
            xp = 25,
            emoji = "✅",
            description = "Paid a bill before the due date",
            dailyLimit = 5
        ),

        /** Awarded when a streak milestone is reached (3-day) */
        STREAK_MILESTONE_SMALL(
            displayName = "Streak Milestone (3-7 days)",
            xp = 50,
            emoji = "🔥",
            description = "Reached a streak milestone",
            dailyLimit = 0
        ),

        /** Awarded when a streak milestone is reached (14-30 days) */
        STREAK_MILESTONE_MEDIUM(
            displayName = "Streak Milestone (14-30 days)",
            xp = 150,
            emoji = "⚡",
            description = "Reached a significant streak milestone",
            dailyLimit = 0
        ),

        /** Awarded when a streak milestone is reached (60+ days) */
        STREAK_MILESTONE_LARGE(
            displayName = "Streak Milestone (60+ days)",
            xp = 500,
            emoji = "🌟",
            description = "Reached an extraordinary streak milestone",
            dailyLimit = 0
        ),

        /** Awarded when any achievement badge is unlocked */
        ACHIEVEMENT_UNLOCKED(
            displayName = "Achievement Unlocked",
            xp = 100,
            emoji = "🏆",
            description = "Unlocked a new achievement badge",
            dailyLimit = 0
        ),

        /** Awarded for setting up a new budget category */
        BUDGET_SET(
            displayName = "Budget Set",
            xp = 30,
            emoji = "📋",
            description = "Created or updated a budget",
            dailyLimit = 3
        ),

        /** Awarded for completing a full week under budget */
        WEEK_UNDER_BUDGET(
            displayName = "Week Under Budget",
            xp = 100,
            emoji = "🎯",
            description = "Stayed under budget for the entire week",
            dailyLimit = 1
        ),

        /** Awarded for logging a transaction manually */
        TRANSACTION_LOGGED(
            displayName = "Transaction Logged",
            xp = 5,
            emoji = "📝",
            description = "Logged a transaction",
            dailyLimit = 10
        ),

        /** Awarded for completing a financial goal */
        GOAL_COMPLETED(
            displayName = "Goal Completed",
            xp = 200,
            emoji = "🎉",
            description = "Completed a savings goal",
            dailyLimit = 0
        ),

        /** Awarded for reviewing and categorizing expenses */
        EXPENSE_REVIEW(
            displayName = "Expense Review",
            xp = 15,
            emoji = "🔍",
            description = "Reviewed your expenses",
            dailyLimit = 1
        ),

        /** Awarded for completing a no-spend day */
        NO_SPEND_DAY(
            displayName = "No-Spend Day",
            xp = 30,
            emoji = "🚫",
            description = "Completed a no-spend day",
            dailyLimit = 1
        )
    }

    /**
     * Level definition with title and XP thresholds.
     *
     * @property level Numeric level (1-20)
     * @property title Descriptive title for this level
     * @property emoji Level badge emoji
     * @property xpRequired Total cumulative XP needed to reach this level
     * @property perk Description of what unlocks at this level
     */
    data class LevelDefinition(
        val level: Int,
        val title: String,
        val emoji: String,
        val xpRequired: Int,
        val perk: String
    )

    /**
     * Record of a single XP earning event.
     *
     * @property action The action that earned XP
     * @property xpEarned Amount of XP earned
     * @property timestamp When the XP was earned
     * @property description Additional context
     */
    data class XpEvent(
        val action: String,
        val xpEarned: Int,
        val timestamp: String,
        val description: String
    )

    /**
     * Current user level status with all relevant metrics.
     *
     * @property currentLevel The user's current level number
     * @property title The title for the current level
     * @property emoji Level badge
     * @property totalXp Total XP earned lifetime
     * @property xpForCurrentLevel XP threshold for current level
     * @property xpForNextLevel XP threshold for next level
     * @property xpProgress XP earned within current level toward next
     * @property xpNeeded XP still needed to reach next level
     * @property progressPercent Percentage toward next level (0-100)
     * @property isMaxLevel Whether user has reached the highest level
     */
    data class LevelStatus(
        val currentLevel: Int,
        val title: String,
        val emoji: String,
        val totalXp: Int,
        val xpForCurrentLevel: Int,
        val xpForNextLevel: Int,
        val xpProgress: Int,
        val xpNeeded: Int,
        val progressPercent: Int,
        val isMaxLevel: Boolean
    )

    /**
     * Result of an XP-granting action.
     *
     * @property xpEarned XP actually earned (may be 0 if daily limit reached)
     * @property totalXp New total XP
     * @property leveledUp Whether a level-up occurred
     * @property newLevel New level if leveled up
     * @property newTitle New title if leveled up
     * @property limitReached Whether the daily limit for this action was hit
     */
    data class XpResult(
        val xpEarned: Int,
        val totalXp: Int,
        val leveledUp: Boolean = false,
        val newLevel: Int? = null,
        val newTitle: String? = null,
        val limitReached: Boolean = false
    )

    // ─────────────────────────────────────────────────────────────
    // Level Definitions
    // ─────────────────────────────────────────────────────────────

    /**
     * All 20 levels with titles, XP requirements, and perks.
     */
    val levels: List<LevelDefinition> by lazy {
        listOf(
            LevelDefinition(1, "Beginner", "🌱", 0, "Welcome! Start tracking your finances"),
            LevelDefinition(2, "Aware", "👀", 100, "You're becoming aware of your spending"),
            LevelDefinition(3, "Tracker", "📊", 250, "Unlock detailed spending breakdowns"),
            LevelDefinition(4, "Planner", "📅", 500, "Unlock budget planning tools"),
            LevelDefinition(5, "Saver", "🐷", 850, "Unlock savings goal templates"),
            LevelDefinition(6, "Optimizer", "⚙️", 1350, "Unlock spending optimization tips"),
            LevelDefinition(7, "Strategist", "♟️", 2000, "Unlock investment insights"),
            LevelDefinition(8, "Builder", "🏗️", 2850, "Unlock net worth tracking"),
            LevelDefinition(9, "Achiever", "🎯", 3900, "Unlock advanced analytics"),
            LevelDefinition(10, "Expert", "🧠", 5200, "Unlock all privacy features"),
            LevelDefinition(11, "Mentor", "🎓", 6800, "Unlock sharing and social features"),
            LevelDefinition(12, "Investor", "📈", 8800, "Unlock portfolio tracking"),
            LevelDefinition(13, "Architect", "🏛️", 11200, "Unlock custom dashboards"),
            LevelDefinition(14, "Commander", "⭐", 14200, "Unlock automation rules"),
            LevelDefinition(15, "Visionary", "🔮", 17800, "Unlock predictive insights"),
            LevelDefinition(16, "Champion", "🏆", 22200, "Unlock challenge creation"),
            LevelDefinition(17, "Master", "👑", 27500, "Unlock all customization options"),
            LevelDefinition(18, "Guru", "🧘", 33800, "Unlock wisdom sharing features"),
            LevelDefinition(19, "Legend", "🌟", 41200, "Unlock exclusive themes"),
            LevelDefinition(20, "Financial Ninja", "🥷", 50000, "Maximum mastery achieved!")
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Gets the current level status for the user.
     *
     * @return Complete level status with progress information
     */
    fun getStatus(): LevelStatus {
        val totalXp = getTotalXp()
        val currentLevel = calculateLevel(totalXp)
        val levelDef = levels.find { it.level == currentLevel } ?: levels.first()
        val nextLevelDef = levels.find { it.level == currentLevel + 1 }

        val xpForCurrent = levelDef.xpRequired
        val xpForNext = nextLevelDef?.xpRequired ?: levelDef.xpRequired
        val isMax = currentLevel >= MAX_LEVEL

        val xpProgress = totalXp - xpForCurrent
        val xpNeeded = if (isMax) 0 else xpForNext - totalXp
        val levelRange = if (isMax) 1 else xpForNext - xpForCurrent
        val progressPercent = if (isMax) 100
        else ((xpProgress.toFloat() / levelRange) * 100).toInt().coerceIn(0, 99)

        return LevelStatus(
            currentLevel = currentLevel,
            title = levelDef.title,
            emoji = levelDef.emoji,
            totalXp = totalXp,
            xpForCurrentLevel = xpForCurrent,
            xpForNextLevel = xpForNext,
            xpProgress = xpProgress,
            xpNeeded = xpNeeded,
            progressPercent = progressPercent,
            isMaxLevel = isMax
        )
    }

    /**
     * Awards XP for a specific action. Respects daily limits and
     * triggers level-up celebrations when thresholds are crossed.
     *
     * @param action The action to award XP for
     * @param multiplier Optional multiplier (e.g., 2x weekends)
     * @return Result with XP earned and any level changes
     */
    fun awardXp(action: XpAction, multiplier: Float = 1.0f): XpResult {
        // Check daily limit
        if (action.dailyLimit > 0 && getDailyCount(action) >= action.dailyLimit) {
            return XpResult(
                xpEarned = 0,
                totalXp = getTotalXp(),
                limitReached = true
            )
        }

        val levelBefore = calculateLevel(getTotalXp())
        val xpToAward = (action.xp * multiplier).toInt()

        // Add XP
        val newTotal = addXp(xpToAward)

        // Record event
        recordXpEvent(XpEvent(
            action = action.name,
            xpEarned = xpToAward,
            timestamp = LocalDateTime.now().format(dateTimeFormatter),
            description = action.description
        ))

        // Increment daily count
        incrementDailyCount(action)

        // Check level up
        val levelAfter = calculateLevel(newTotal)
        val leveledUp = levelAfter > levelBefore

        if (leveledUp) {
            prefs.edit().putBoolean(KEY_LEVEL_UP_PENDING, true).apply()
        }

        val newLevelDef = if (leveledUp) levels.find { it.level == levelAfter } else null

        return XpResult(
            xpEarned = xpToAward,
            totalXp = newTotal,
            leveledUp = leveledUp,
            newLevel = if (leveledUp) levelAfter else null,
            newTitle = newLevelDef?.title
        )
    }

    /**
     * Awards XP for a streak milestone based on the number of days.
     *
     * @param streakDays The milestone day count reached
     * @return XP result
     */
    fun awardStreakMilestoneXp(streakDays: Int): XpResult {
        val action = when {
            streakDays >= 60 -> XpAction.STREAK_MILESTONE_LARGE
            streakDays >= 14 -> XpAction.STREAK_MILESTONE_MEDIUM
            else -> XpAction.STREAK_MILESTONE_SMALL
        }
        return awardXp(action)
    }

    /**
     * Records the daily login and awards XP if not already claimed today.
     *
     * @return XP result (0 if already logged in today)
     */
    fun recordDailyLogin(): XpResult {
        val today = LocalDate.now().format(dateFormatter)
        val lastLogin = prefs.getString(KEY_DAILY_LOGIN_DATE, "") ?: ""

        if (lastLogin == today) {
            return XpResult(xpEarned = 0, totalXp = getTotalXp(), limitReached = true)
        }

        prefs.edit().putString(KEY_DAILY_LOGIN_DATE, today).apply()
        return awardXp(XpAction.DAILY_LOGIN)
    }

    /**
     * Gets the total XP earned lifetime.
     *
     * @return Total XP points
     */
    fun getTotalXp(): Int {
        return prefs.getInt(KEY_TOTAL_XP, 0)
    }

    /**
     * Gets XP history (recent events).
     *
     * @param limit Maximum number of events to return
     * @return List of recent XP events, newest first
     */
    fun getXpHistory(limit: Int = 20): List<XpEvent> {
        val json = prefs.getString(KEY_XP_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<XpEvent>>() {}.type
            val all: List<XpEvent> = gson.fromJson(json, type)
            all.takeLast(limit).reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets XP earned today.
     *
     * @return Total XP earned in the current calendar day
     */
    fun getXpEarnedToday(): Int {
        val today = LocalDate.now().format(dateFormatter)
        return getXpHistory(100)
            .filter { it.timestamp.startsWith(today) }
            .sumOf { it.xpEarned }
    }

    /**
     * Checks if there is a pending level-up celebration to show.
     *
     * @return True if the user leveled up and hasn't seen the celebration
     */
    fun hasPendingLevelUp(): Boolean {
        return prefs.getBoolean(KEY_LEVEL_UP_PENDING, false)
    }

    /**
     * Acknowledges the level-up celebration (user has seen it).
     */
    fun acknowledgeLevelUp() {
        prefs.edit().putBoolean(KEY_LEVEL_UP_PENDING, false).apply()
    }

    /**
     * Gets the level definition for a specific level number.
     *
     * @param level Level number (1-20)
     * @return Level definition or null if invalid
     */
    fun getLevelDefinition(level: Int): LevelDefinition? {
        return levels.find { it.level == level }
    }

    /**
     * Gets a summary of available XP actions and their rewards.
     *
     * @return List of all XP actions with descriptions
     */
    fun getXpGuide(): List<Map<String, Any>> {
        return XpAction.values().map { action ->
            mapOf(
                "action" to action.displayName,
                "xp" to action.xp,
                "emoji" to action.emoji,
                "description" to action.description,
                "dailyLimit" to action.dailyLimit,
                "remainingToday" to if (action.dailyLimit > 0) {
                    (action.dailyLimit - getDailyCount(action)).coerceAtLeast(0)
                } else {
                    -1 // Unlimited
                }
            )
        }
    }

    /**
     * Calculates how many days at current pace to reach next level.
     *
     * @return Estimated days to next level, or null if at max
     */
    fun estimatedDaysToNextLevel(): Int? {
        val status = getStatus()
        if (status.isMaxLevel) return null

        val todayXp = getXpEarnedToday()
        val avgDailyXp = if (todayXp > 0) todayXp else 50 // Default estimate
        return if (avgDailyXp > 0) {
            (status.xpNeeded / avgDailyXp) + 1
        } else {
            null
        }
    }

    /**
     * Clears all level/XP data. Use with extreme caution.
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Calculates the level for a given total XP amount.
     */
    private fun calculateLevel(totalXp: Int): Int {
        var level = 1
        for (levelDef in levels) {
            if (totalXp >= levelDef.xpRequired) {
                level = levelDef.level
            } else {
                break
            }
        }
        return level
    }

    /**
     * Adds XP to the total and persists.
     */
    private fun addXp(amount: Int): Int {
        val current = getTotalXp()
        val newTotal = current + amount
        prefs.edit().putInt(KEY_TOTAL_XP, newTotal).apply()
        return newTotal
    }

    /**
     * Records an XP event in history (keeps last 200 events).
     */
    private fun recordXpEvent(event: XpEvent) {
        val history = getXpHistoryMutable()
        history.add(event)

        // Trim to last 200 events
        val trimmed = if (history.size > 200) {
            history.takeLast(200).toMutableList()
        } else {
            history
        }

        prefs.edit().putString(KEY_XP_HISTORY, gson.toJson(trimmed)).apply()
    }

    /**
     * Gets how many times an action has been performed today.
     */
    private fun getDailyCount(action: XpAction): Int {
        val today = LocalDate.now().format(dateFormatter)
        val key = "daily_count_${action.name}_$today"
        return prefs.getInt(key, 0)
    }

    /**
     * Increments the daily count for an action.
     */
    private fun incrementDailyCount(action: XpAction) {
        val today = LocalDate.now().format(dateFormatter)
        val key = "daily_count_${action.name}_$today"
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    /**
     * Loads XP history as mutable list.
     */
    private fun getXpHistoryMutable(): MutableList<XpEvent> {
        val json = prefs.getString(KEY_XP_HISTORY, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<XpEvent>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            mutableListOf()
        }
    }
}
