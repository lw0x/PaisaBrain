package com.paisabrain.app.gamification

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Comprehensive achievement/badge system with 30+ unlockable achievements
 * across multiple financial health categories.
 *
 * Achievements reward positive financial behaviors and encourage engagement
 * with all aspects of the app. Each achievement has a rarity tier that
 * reflects its difficulty, and unlocking achievements grants XP.
 *
 * Categories: Savings, Tracking, Privacy, Vault, Budget, Debt, Streaks, Social
 */
class AchievementSystem(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "achievements_prefs"
        private const val KEY_UNLOCKED = "unlocked_achievements"
        private const val KEY_PROGRESS = "achievement_progress"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Rarity tiers indicating difficulty of achievement.
     *
     * @property displayName Human-readable name
     * @property color Hex color for UI rendering
     * @property xpReward XP granted when unlocked
     */
    enum class Rarity(val displayName: String, val color: String, val xpReward: Int) {
        COMMON("Common", "#9E9E9E", 50),
        RARE("Rare", "#2196F3", 100),
        EPIC("Epic", "#9C27B0", 250),
        LEGENDARY("Legendary", "#FFD700", 500)
    }

    /**
     * Categories to organize achievements.
     */
    enum class Category(val displayName: String, val emoji: String) {
        SAVINGS("Savings", "💰"),
        TRACKING("Tracking", "📊"),
        PRIVACY("Privacy", "🔒"),
        VAULT("Vault", "📸"),
        BUDGET("Budget", "📋"),
        DEBT("Debt", "⚔️"),
        STREAKS("Streaks", "🔥"),
        SOCIAL("Social", "👥")
    }

    /**
     * Represents a single achievement that can be unlocked.
     *
     * @property id Unique identifier
     * @property name Display name of the achievement
     * @property description How to unlock this achievement
     * @property emoji Visual emoji for the badge
     * @property rarity Difficulty/rarity tier
     * @property category Which category this belongs to
     * @property conditionKey Internal key used to evaluate unlock condition
     * @property requiredValue Threshold value needed to unlock
     * @property isUnlocked Whether the user has earned this
     * @property unlockedDate When it was unlocked (ISO datetime string)
     * @property progressCurrent Current progress toward the goal
     */
    data class Achievement(
        val id: String,
        val name: String,
        val description: String,
        val emoji: String,
        val rarity: Rarity,
        val category: Category,
        val conditionKey: String,
        val requiredValue: Int = 1,
        val isUnlocked: Boolean = false,
        val unlockedDate: String? = null,
        val progressCurrent: Int = 0
    ) {
        /** Progress as a percentage (0-100) */
        val progressPercent: Int
            get() = if (isUnlocked) 100
            else ((progressCurrent.toFloat() / requiredValue) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Result from checking achievements after an action.
     *
     * @property newlyUnlocked List of achievements just unlocked
     * @property totalXpEarned Total XP from newly unlocked achievements
     */
    data class AchievementCheckResult(
        val newlyUnlocked: List<Achievement>,
        val totalXpEarned: Int
    )

    // ─────────────────────────────────────────────────────────────
    // Achievement Definitions (30+)
    // ─────────────────────────────────────────────────────────────

    /**
     * Complete catalog of all achievements available in the app.
     */
    val allAchievements: List<Achievement> by lazy {
        listOf(
            // ── TRACKING ──────────────────────────────────────────
            Achievement(
                id = "first_step",
                name = "First Step",
                description = "Log your first transaction",
                emoji = "👣",
                rarity = Rarity.COMMON,
                category = Category.TRACKING,
                conditionKey = "transactions_logged",
                requiredValue = 1
            ),
            Achievement(
                id = "data_collector",
                name = "Data Collector",
                description = "Log 100 transactions",
                emoji = "📝",
                rarity = Rarity.COMMON,
                category = Category.TRACKING,
                conditionKey = "transactions_logged",
                requiredValue = 100
            ),
            Achievement(
                id = "financial_historian",
                name = "Financial Historian",
                description = "Log 1,000 transactions",
                emoji = "📚",
                rarity = Rarity.RARE,
                category = Category.TRACKING,
                conditionKey = "transactions_logged",
                requiredValue = 1000
            ),
            Achievement(
                id = "ghost_hunter",
                name = "Ghost Hunter",
                description = "Find 3 or more unused subscriptions",
                emoji = "👻",
                rarity = Rarity.RARE,
                category = Category.TRACKING,
                conditionKey = "unused_subscriptions_found",
                requiredValue = 3
            ),
            Achievement(
                id = "category_master",
                name = "Category Master",
                description = "Categorize 500 transactions correctly",
                emoji = "🏷️",
                rarity = Rarity.RARE,
                category = Category.TRACKING,
                conditionKey = "transactions_categorized",
                requiredValue = 500
            ),

            // ── BUDGET ────────────────────────────────────────────
            Achievement(
                id = "budget_boss",
                name = "Budget Boss",
                description = "Stay under budget for 3 months straight",
                emoji = "👔",
                rarity = Rarity.EPIC,
                category = Category.BUDGET,
                conditionKey = "months_under_budget_consecutive",
                requiredValue = 3
            ),
            Achievement(
                id = "budget_beginner",
                name = "Budget Beginner",
                description = "Create your first monthly budget",
                emoji = "📋",
                rarity = Rarity.COMMON,
                category = Category.BUDGET,
                conditionKey = "budgets_created",
                requiredValue = 1
            ),
            Achievement(
                id = "minimalist_month",
                name = "Minimalist Month",
                description = "Achieve your lowest spending month ever",
                emoji = "🍃",
                rarity = Rarity.EPIC,
                category = Category.BUDGET,
                conditionKey = "lowest_spending_month_achieved",
                requiredValue = 1
            ),
            Achievement(
                id = "zero_waste",
                name = "Zero Waste",
                description = "Spend exactly your budget — not a rupee over or under 5%",
                emoji = "🎯",
                rarity = Rarity.LEGENDARY,
                category = Category.BUDGET,
                conditionKey = "perfect_budget_month",
                requiredValue = 1
            ),
            Achievement(
                id = "budget_architect",
                name = "Budget Architect",
                description = "Set up budgets for 10 different categories",
                emoji = "🏗️",
                rarity = Rarity.RARE,
                category = Category.BUDGET,
                conditionKey = "budget_categories_set",
                requiredValue = 10
            ),

            // ── SAVINGS ───────────────────────────────────────────
            Achievement(
                id = "penny_pincher",
                name = "Penny Pincher",
                description = "Save your first ₹100",
                emoji = "🪙",
                rarity = Rarity.COMMON,
                category = Category.SAVINGS,
                conditionKey = "total_saved_rupees",
                requiredValue = 100
            ),
            Achievement(
                id = "rainy_day_fund",
                name = "Rainy Day Fund",
                description = "Build an emergency fund of ₹10,000",
                emoji = "☔",
                rarity = Rarity.RARE,
                category = Category.SAVINGS,
                conditionKey = "emergency_fund_amount",
                requiredValue = 10000
            ),
            Achievement(
                id = "net_worth_climber",
                name = "Net Worth Climber",
                description = "Increase your net worth for 3 months straight",
                emoji = "📈",
                rarity = Rarity.EPIC,
                category = Category.SAVINGS,
                conditionKey = "net_worth_growth_months_consecutive",
                requiredValue = 3
            ),
            Achievement(
                id = "lakh_club",
                name = "Lakh Club",
                description = "Reach ₹1,00,000 in total savings",
                emoji = "💎",
                rarity = Rarity.LEGENDARY,
                category = Category.SAVINGS,
                conditionKey = "total_saved_rupees",
                requiredValue = 100000
            ),
            Achievement(
                id = "savings_automator",
                name = "Savings Automator",
                description = "Set up automatic savings for 3 goals",
                emoji = "🤖",
                rarity = Rarity.RARE,
                category = Category.SAVINGS,
                conditionKey = "auto_savings_goals_set",
                requiredValue = 3
            ),

            // ── PRIVACY ───────────────────────────────────────────
            Achievement(
                id = "privacy_warrior",
                name = "Privacy Warrior",
                description = "Achieve a privacy score above 90",
                emoji = "🛡️",
                rarity = Rarity.EPIC,
                category = Category.PRIVACY,
                conditionKey = "privacy_score",
                requiredValue = 90
            ),
            Achievement(
                id = "lockdown_pro",
                name = "Lockdown Pro",
                description = "Enable all available privacy features",
                emoji = "🔐",
                rarity = Rarity.RARE,
                category = Category.PRIVACY,
                conditionKey = "privacy_features_enabled_all",
                requiredValue = 1
            ),
            Achievement(
                id = "data_sovereign",
                name = "Data Sovereign",
                description = "Keep all data on-device for 6 months",
                emoji = "🏰",
                rarity = Rarity.LEGENDARY,
                category = Category.PRIVACY,
                conditionKey = "months_fully_offline",
                requiredValue = 6
            ),
            Achievement(
                id = "audit_complete",
                name = "Audit Complete",
                description = "Complete your first privacy audit",
                emoji = "🔍",
                rarity = Rarity.COMMON,
                category = Category.PRIVACY,
                conditionKey = "privacy_audits_completed",
                requiredValue = 1
            ),

            // ── VAULT ─────────────────────────────────────────────
            Achievement(
                id = "memory_keeper",
                name = "Memory Keeper",
                description = "Store 100 entries in your vault",
                emoji = "📸",
                rarity = Rarity.RARE,
                category = Category.VAULT,
                conditionKey = "vault_entries_count",
                requiredValue = 100
            ),
            Achievement(
                id = "first_memory",
                name = "First Memory",
                description = "Capture your first vault entry",
                emoji = "🌅",
                rarity = Rarity.COMMON,
                category = Category.VAULT,
                conditionKey = "vault_entries_count",
                requiredValue = 1
            ),
            Achievement(
                id = "time_capsule",
                name = "Time Capsule",
                description = "Have vault entries spanning 1 full year",
                emoji = "⏳",
                rarity = Rarity.EPIC,
                category = Category.VAULT,
                conditionKey = "vault_span_days",
                requiredValue = 365
            ),
            Achievement(
                id = "digital_museum",
                name = "Digital Museum",
                description = "Store 500 memories in your vault",
                emoji = "🏛️",
                rarity = Rarity.LEGENDARY,
                category = Category.VAULT,
                conditionKey = "vault_entries_count",
                requiredValue = 500
            ),

            // ── DEBT ──────────────────────────────────────────────
            Achievement(
                id = "debt_slayer",
                name = "Debt Slayer",
                description = "Pay off your first debt completely",
                emoji = "⚔️",
                rarity = Rarity.RARE,
                category = Category.DEBT,
                conditionKey = "debts_paid_off",
                requiredValue = 1
            ),
            Achievement(
                id = "debt_free",
                name = "Debt Free",
                description = "Pay off ALL debts — complete freedom",
                emoji = "🕊️",
                rarity = Rarity.LEGENDARY,
                category = Category.DEBT,
                conditionKey = "all_debts_cleared",
                requiredValue = 1
            ),
            Achievement(
                id = "early_bird",
                name = "Early Bird",
                description = "Pay all bills before their due date for one full month",
                emoji = "🐦",
                rarity = Rarity.RARE,
                category = Category.DEBT,
                conditionKey = "months_all_bills_early",
                requiredValue = 1
            ),
            Achievement(
                id = "interest_crusher",
                name = "Interest Crusher",
                description = "Save ₹5,000 in interest by paying debts early",
                emoji = "💥",
                rarity = Rarity.EPIC,
                category = Category.DEBT,
                conditionKey = "interest_saved_rupees",
                requiredValue = 5000
            ),

            // ── STREAKS ───────────────────────────────────────────
            Achievement(
                id = "streak_starter",
                name = "Streak Starter",
                description = "Achieve a 3-day streak of any type",
                emoji = "🔥",
                rarity = Rarity.COMMON,
                category = Category.STREAKS,
                conditionKey = "max_streak_any",
                requiredValue = 3
            ),
            Achievement(
                id = "streak_warrior",
                name = "Streak Warrior",
                description = "Achieve a 7-day streak of any type",
                emoji = "⚡",
                rarity = Rarity.COMMON,
                category = Category.STREAKS,
                conditionKey = "max_streak_any",
                requiredValue = 7
            ),
            Achievement(
                id = "streak_legend",
                name = "Streak Legend",
                description = "Achieve a 30-day streak of any type",
                emoji = "🌟",
                rarity = Rarity.EPIC,
                category = Category.STREAKS,
                conditionKey = "max_streak_any",
                requiredValue = 30
            ),
            Achievement(
                id = "streak_immortal",
                name = "Streak Immortal",
                description = "Achieve a 100-day streak of any type",
                emoji = "👑",
                rarity = Rarity.LEGENDARY,
                category = Category.STREAKS,
                conditionKey = "max_streak_any",
                requiredValue = 100
            ),
            Achievement(
                id = "multi_streaker",
                name = "Multi-Streaker",
                description = "Have 3 different streaks active simultaneously",
                emoji = "🎪",
                rarity = Rarity.EPIC,
                category = Category.STREAKS,
                conditionKey = "simultaneous_active_streaks",
                requiredValue = 3
            ),

            // ── SOCIAL ────────────────────────────────────────────
            Achievement(
                id = "helpful_friend",
                name = "Helpful Friend",
                description = "Share a savings tip with someone",
                emoji = "🤝",
                rarity = Rarity.COMMON,
                category = Category.SOCIAL,
                conditionKey = "tips_shared",
                requiredValue = 1
            ),
            Achievement(
                id = "accountability_partner",
                name = "Accountability Partner",
                description = "Set up a savings buddy challenge",
                emoji = "👫",
                rarity = Rarity.RARE,
                category = Category.SOCIAL,
                conditionKey = "buddy_challenges_started",
                requiredValue = 1
            ),
            Achievement(
                id = "community_champion",
                name = "Community Champion",
                description = "Help 10 people with financial tips",
                emoji = "🏅",
                rarity = Rarity.EPIC,
                category = Category.SOCIAL,
                conditionKey = "tips_shared",
                requiredValue = 10
            ),
            Achievement(
                id = "mentor",
                name = "Mentor",
                description = "Reach level 10 and share your journey",
                emoji = "🎓",
                rarity = Rarity.LEGENDARY,
                category = Category.SOCIAL,
                conditionKey = "mentor_status_achieved",
                requiredValue = 1
            )
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────

    /**
     * Gets all achievements with their current unlock/progress state.
     *
     * @return List of all achievements with up-to-date status
     */
    fun getAll(): List<Achievement> {
        val unlocked = loadUnlockedSet()
        val progress = loadProgress()

        return allAchievements.map { achievement ->
            val isUnlocked = unlocked.containsKey(achievement.id)
            val currentProgress = progress[achievement.conditionKey] ?: 0

            achievement.copy(
                isUnlocked = isUnlocked,
                unlockedDate = unlocked[achievement.id],
                progressCurrent = if (isUnlocked) achievement.requiredValue else currentProgress
            )
        }
    }

    /**
     * Gets achievements filtered by category.
     *
     * @param category The category to filter by
     * @return Filtered list with current state
     */
    fun getByCategory(category: Category): List<Achievement> {
        return getAll().filter { it.category == category }
    }

    /**
     * Gets achievements filtered by rarity.
     *
     * @param rarity The rarity tier to filter by
     * @return Filtered list with current state
     */
    fun getByRarity(rarity: Rarity): List<Achievement> {
        return getAll().filter { it.rarity == rarity }
    }

    /**
     * Gets only unlocked achievements.
     *
     * @return List of earned achievements
     */
    fun getUnlocked(): List<Achievement> {
        return getAll().filter { it.isUnlocked }
    }

    /**
     * Gets only locked (not yet earned) achievements.
     *
     * @return List of achievements still to earn
     */
    fun getLocked(): List<Achievement> {
        return getAll().filter { !it.isUnlocked }
    }

    /**
     * Updates progress for a specific condition key and checks if any
     * achievements should be unlocked.
     *
     * @param conditionKey The metric being updated (e.g., "transactions_logged")
     * @param newValue The new value for this metric
     * @return Result containing any newly unlocked achievements
     */
    fun updateProgress(conditionKey: String, newValue: Int): AchievementCheckResult {
        val progress = loadProgressMutable()
        progress[conditionKey] = newValue
        saveProgress(progress)

        return checkUnlocks(progress)
    }

    /**
     * Increments progress for a specific condition key by a given amount.
     *
     * @param conditionKey The metric being incremented
     * @param incrementBy Amount to add (default 1)
     * @return Result containing any newly unlocked achievements
     */
    fun incrementProgress(conditionKey: String, incrementBy: Int = 1): AchievementCheckResult {
        val progress = loadProgressMutable()
        val current = progress[conditionKey] ?: 0
        progress[conditionKey] = current + incrementBy
        saveProgress(progress)

        return checkUnlocks(progress)
    }

    /**
     * Checks all achievements against current progress and unlocks any
     * that have met their conditions.
     *
     * @return Result with newly unlocked achievements
     */
    fun evaluateAll(): AchievementCheckResult {
        val progress = loadProgress()
        return checkUnlocks(progress)
    }

    /**
     * Manually unlocks an achievement (for special conditions not
     * tracked by simple numeric progress).
     *
     * @param achievementId The ID of the achievement to unlock
     * @return The unlocked achievement, or null if not found
     */
    fun manualUnlock(achievementId: String): Achievement? {
        val achievement = allAchievements.find { it.id == achievementId } ?: return null
        val unlocked = loadUnlockedMutable()

        if (unlocked.containsKey(achievementId)) {
            return achievement.copy(isUnlocked = true, unlockedDate = unlocked[achievementId])
        }

        val now = LocalDateTime.now().format(dateTimeFormatter)
        unlocked[achievementId] = now
        saveUnlocked(unlocked)

        return achievement.copy(isUnlocked = true, unlockedDate = now)
    }

    /**
     * Gets overall achievement completion statistics.
     *
     * @return Map with stats like total, unlocked, percentage, XP earned
     */
    fun getStats(): Map<String, Any> {
        val all = getAll()
        val unlocked = all.filter { it.isUnlocked }

        val categoryBreakdown = Category.values().associate { category ->
            val catAchievements = all.filter { it.category == category }
            val catUnlocked = catAchievements.count { it.isUnlocked }
            category.displayName to "$catUnlocked/${catAchievements.size}"
        }

        return mapOf(
            "total" to all.size,
            "unlocked" to unlocked.size,
            "locked" to (all.size - unlocked.size),
            "percentComplete" to ((unlocked.size.toFloat() / all.size) * 100).toInt(),
            "totalXpEarned" to unlocked.sumOf { it.rarity.xpReward },
            "categoryBreakdown" to categoryBreakdown,
            "rarestUnlocked" to (unlocked.maxByOrNull { it.rarity.ordinal }?.name ?: "None"),
            "nextClosest" to getClosestToUnlock()?.name
        )
    }

    /**
     * Gets the achievement closest to being unlocked (highest progress %).
     *
     * @return The locked achievement with highest progress, or null
     */
    fun getClosestToUnlock(): Achievement? {
        return getLocked()
            .filter { it.progressPercent > 0 }
            .maxByOrNull { it.progressPercent }
    }

    /**
     * Gets recently unlocked achievements (last N).
     *
     * @param count Number of recent achievements to return
     * @return List of recently unlocked achievements, newest first
     */
    fun getRecent(count: Int = 5): List<Achievement> {
        return getUnlocked()
            .sortedByDescending { it.unlockedDate }
            .take(count)
    }

    /**
     * Clears all achievement data. Use with extreme caution.
     */
    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Checks all achievements against progress and unlocks eligible ones.
     */
    private fun checkUnlocks(progress: Map<String, Int>): AchievementCheckResult {
        val unlocked = loadUnlockedMutable()
        val newlyUnlocked = mutableListOf<Achievement>()
        val now = LocalDateTime.now().format(dateTimeFormatter)

        allAchievements.forEach { achievement ->
            if (!unlocked.containsKey(achievement.id)) {
                val currentValue = progress[achievement.conditionKey] ?: 0
                if (currentValue >= achievement.requiredValue) {
                    unlocked[achievement.id] = now
                    newlyUnlocked.add(
                        achievement.copy(isUnlocked = true, unlockedDate = now)
                    )
                }
            }
        }

        if (newlyUnlocked.isNotEmpty()) {
            saveUnlocked(unlocked)
        }

        return AchievementCheckResult(
            newlyUnlocked = newlyUnlocked,
            totalXpEarned = newlyUnlocked.sumOf { it.rarity.xpReward }
        )
    }

    /**
     * Loads the set of unlocked achievement IDs with their unlock dates.
     */
    private fun loadUnlockedSet(): Map<String, String> {
        val json = prefs.getString(KEY_UNLOCKED, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadUnlockedMutable(): MutableMap<String, String> {
        return loadUnlockedSet().toMutableMap()
    }

    private fun saveUnlocked(unlocked: Map<String, String>) {
        prefs.edit().putString(KEY_UNLOCKED, gson.toJson(unlocked)).apply()
    }

    /**
     * Loads progress values for all condition keys.
     */
    private fun loadProgress(): Map<String, Int> {
        val json = prefs.getString(KEY_PROGRESS, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadProgressMutable(): MutableMap<String, Int> {
        return loadProgress().toMutableMap()
    }

    private fun saveProgress(progress: Map<String, Int>) {
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(progress)).apply()
    }
}
