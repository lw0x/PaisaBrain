package com.paisabrain.app.emotional

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Milestones Celebrator — recognizes and celebrates financial achievements.
 *
 * Tracks and celebrates:
 * - Net worth milestones (₹10K → ₹1Cr)
 * - Savings milestones (first ₹1,000 → ₹10L+)
 * - Streak milestones (7 → 365 days)
 * - Achievement/badge milestones
 * - Behavior milestones (first month under budget, debt reduced, etc.)
 * - "This Day Last Year" progress comparison
 * - Personal records (longest streak, most saved, lowest spend)
 *
 * Each milestone includes confetti trigger, celebration message,
 * shareable card text, and motivational follow-up.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A celebrated financial milestone.
 *
 * @property id Unique identifier for this milestone achievement.
 * @property type The category of milestone.
 * @property value The numeric value associated (e.g., amount, streak days, badge count).
 * @property achievedDate When the milestone was achieved.
 * @property celebrationMessage The primary celebration text shown to the user.
 * @property shareText Text formatted for sharing on social media or with friends.
 * @property emoji The emoji associated with this milestone.
 * @property confettiType The type of confetti animation to trigger.
 * @property followUpMessage Motivational message shown after the celebration.
 */
data class Milestone(
    val id: String = UUID.randomUUID().toString(),
    val type: MilestoneType,
    val value: Double,
    val achievedDate: LocalDate = LocalDate.now(),
    val celebrationMessage: String,
    val shareText: String,
    val emoji: String,
    val confettiType: ConfettiType = ConfettiType.STANDARD,
    val followUpMessage: String = ""
)

/**
 * Categories of milestones tracked by the system.
 */
enum class MilestoneType {
    /** Net worth reaching a significant threshold. */
    NET_WORTH,

    /** Total savings reaching a significant threshold. */
    SAVINGS,

    /** Consecutive days maintaining a financial habit. */
    STREAK,

    /** Badges/achievements earned. */
    ACHIEVEMENT,

    /** Positive financial behavior changes. */
    BEHAVIOR,

    /** Personal best/record broken. */
    PERSONAL_RECORD
}

/**
 * Types of confetti animations for different milestone tiers.
 */
enum class ConfettiType {
    /** Simple burst for smaller milestones. */
    STANDARD,

    /** Golden confetti for major milestones. */
    GOLDEN,

    /** Full-screen fireworks for life-changing milestones. */
    FIREWORKS,

    /** Rainbow cascade for recovery milestones. */
    RAINBOW,

    /** Subtle sparkle for streak continuations. */
    SPARKLE
}

/**
 * Comparison of today vs exactly one year ago.
 *
 * @property date Today's date.
 * @property currentBalance Today's balance.
 * @property lastYearBalance Balance exactly 365 days ago.
 * @property balanceChange Difference in balance (positive = improvement).
 * @property currentNetWorth Today's net worth.
 * @property lastYearNetWorth Net worth 365 days ago.
 * @property netWorthChange Difference in net worth.
 * @property currentDailySpend Today's average daily spend.
 * @property lastYearDailySpend Last year's average daily spend.
 * @property spendChange Change in daily spend (negative = improvement).
 * @property message User-facing comparison message.
 * @property emoji Emoji representing the overall change.
 */
data class ThisDayLastYear(
    val date: LocalDate = LocalDate.now(),
    val currentBalance: Double,
    val lastYearBalance: Double,
    val balanceChange: Double,
    val currentNetWorth: Double,
    val lastYearNetWorth: Double,
    val netWorthChange: Double,
    val currentDailySpend: Double,
    val lastYearDailySpend: Double,
    val spendChange: Double,
    val message: String,
    val emoji: String
)

/**
 * A personal financial record.
 *
 * @property id Unique identifier.
 * @property recordType What type of record this is.
 * @property value The record value.
 * @property achievedDate When the record was set.
 * @property previousRecord The previous record value (if any).
 * @property message Celebration message for breaking the record.
 */
data class PersonalRecord(
    val id: String = UUID.randomUUID().toString(),
    val recordType: RecordType,
    val value: Double,
    val achievedDate: LocalDate = LocalDate.now(),
    val previousRecord: Double? = null,
    val message: String
)

/**
 * Types of personal records tracked.
 */
enum class RecordType {
    /** Longest consecutive streak in days. */
    LONGEST_STREAK,

    /** Most saved in a single month. */
    MOST_SAVED_MONTH,

    /** Lowest spending month. */
    LOWEST_SPEND_MONTH,

    /** Highest net worth reached. */
    HIGHEST_NET_WORTH,

    /** Most days under budget in a row. */
    MOST_DAYS_UNDER_BUDGET,

    /** Largest single savings deposit. */
    LARGEST_SAVINGS_DEPOSIT
}

/**
 * Historical financial data point for "This Day Last Year" feature.
 *
 * @property date The date of this snapshot.
 * @property balance Account balance on this date.
 * @property netWorth Total net worth on this date.
 * @property dailyAverageSpend Average daily spending around this date.
 */
data class HistoricalSnapshot(
    val date: LocalDate,
    val balance: Double,
    val netWorth: Double,
    val dailyAverageSpend: Double
)

// ─────────────────────────────────────────────────────────────────────────────
// Milestone Thresholds
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Defines the threshold levels for various milestone categories.
 */
object MilestoneThresholds {

    /** Net worth milestone thresholds in INR. */
    val netWorthLevels: List<Double> = listOf(
        10_000.0,       // ₹10K
        50_000.0,       // ₹50K
        1_00_000.0,     // ₹1 Lakh
        5_00_000.0,     // ₹5 Lakh
        10_00_000.0,    // ₹10 Lakh
        25_00_000.0,    // ₹25 Lakh
        50_00_000.0,    // ₹50 Lakh
        1_00_00_000.0   // ₹1 Crore
    )

    /** Savings milestone thresholds in INR. */
    val savingsLevels: List<Double> = listOf(
        1_000.0,        // First ₹1,000
        5_000.0,        // ₹5,000
        10_000.0,       // ₹10K
        25_000.0,       // ₹25K
        50_000.0,       // ₹50K
        1_00_000.0,     // ₹1 Lakh
        2_50_000.0,     // ₹2.5 Lakh
        5_00_000.0,     // ₹5 Lakh
        10_00_000.0     // ₹10 Lakh
    )

    /** Streak milestone thresholds in days. */
    val streakLevels: List<Int> = listOf(7, 14, 30, 60, 90, 180, 365)

    /** Achievement/badge count milestones. */
    val achievementLevels: List<Int> = listOf(1, 5, 10, 25, 50)
}

// ─────────────────────────────────────────────────────────────────────────────
// Core Engine
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Milestones Celebrator — the core engine for detecting, celebrating,
 * and tracking financial milestones.
 *
 * Usage:
 * ```kotlin
 * val celebrator = MilestonesCelebrator()
 *
 * // Check for new milestones
 * val netWorthMilestone = celebrator.checkNetWorthMilestone(
 *     currentNetWorth = 105000.0,
 *     previousNetWorth = 98000.0
 * )
 *
 * // Get "This Day Last Year" comparison
 * val comparison = celebrator.generateThisDayLastYear(today, lastYear)
 *
 * // Check personal records
 * val record = celebrator.checkPersonalRecord(RecordType.MOST_SAVED_MONTH, 25000.0, 22000.0)
 * ```
 */
class MilestonesCelebrator {

    private val achievedMilestones: MutableList<Milestone> = mutableListOf()
    private val personalRecords: MutableMap<RecordType, PersonalRecord> = mutableMapOf()

    // ─────────────────────────────────────────────────────────────────────────
    // Net Worth Milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if a net worth milestone has been crossed.
     *
     * Compares the previous and current net worth to detect when a
     * threshold is crossed for the first time.
     *
     * @param currentNetWorth The user's current total net worth in INR.
     * @param previousNetWorth The user's net worth before the latest update.
     * @return A [Milestone] if a new threshold was crossed, null otherwise.
     */
    fun checkNetWorthMilestone(currentNetWorth: Double, previousNetWorth: Double): Milestone? {
        val crossedLevel = MilestoneThresholds.netWorthLevels.find { level ->
            previousNetWorth < level && currentNetWorth >= level
        } ?: return null

        val milestone = Milestone(
            type = MilestoneType.NET_WORTH,
            value = crossedLevel,
            celebrationMessage = generateNetWorthCelebration(crossedLevel),
            shareText = generateNetWorthShareText(crossedLevel),
            emoji = getNetWorthEmoji(crossedLevel),
            confettiType = getNetWorthConfetti(crossedLevel),
            followUpMessage = generateNetWorthFollowUp(crossedLevel)
        )

        achievedMilestones.add(milestone)
        return milestone
    }

    /**
     * Generates celebration message for a net worth milestone.
     */
    private fun generateNetWorthCelebration(level: Double): String = when {
        level >= 1_00_00_000.0 -> "🏆 INCREDIBLE! Your net worth just crossed ₹1 CRORE! " +
                "You've entered the top tier. This is extraordinary."
        level >= 50_00_000.0 -> "🌟 AMAZING! ₹50 Lakh net worth! " +
                "Half a crore. Let that sink in. You built this."
        level >= 25_00_000.0 -> "💎 ₹25 Lakh net worth! Quarter of a crore! " +
                "Your consistency is paying off in a massive way."
        level >= 10_00_000.0 -> "🎉 ₹10 LAKH NET WORTH! You're a lakhpati times ten! " +
                "This is what discipline looks like."
        level >= 5_00_000.0 -> "🚀 ₹5 Lakh net worth! Half a million rupees! " +
                "You're building real wealth now."
        level >= 1_00_000.0 -> "🎊 ₹1 LAKH NET WORTH! You're officially a lakhpati! " +
                "This is a huge milestone. Be proud."
        level >= 50_000.0 -> "✨ ₹50,000 net worth! Halfway to a lakh! " +
                "You're gaining serious momentum."
        else -> "🌱 ₹10,000 net worth! Your financial garden is growing. " +
                "Every big tree started as a seed."
    }

    /**
     * Generates shareable text for net worth milestones.
     */
    private fun generateNetWorthShareText(level: Double): String {
        val formattedLevel = formatIndianAmount(level)
        return "Just crossed ₹$formattedLevel net worth! 🎉 " +
                "Small steps, big results. The journey continues. 💪"
    }

    /**
     * Returns emoji for net worth level.
     */
    private fun getNetWorthEmoji(level: Double): String = when {
        level >= 1_00_00_000.0 -> "🏆"
        level >= 50_00_000.0 -> "🌟"
        level >= 25_00_000.0 -> "💎"
        level >= 10_00_000.0 -> "🎉"
        level >= 5_00_000.0 -> "🚀"
        level >= 1_00_000.0 -> "🎊"
        level >= 50_000.0 -> "✨"
        else -> "🌱"
    }

    /**
     * Returns confetti type appropriate for the milestone tier.
     */
    private fun getNetWorthConfetti(level: Double): ConfettiType = when {
        level >= 1_00_00_000.0 -> ConfettiType.FIREWORKS
        level >= 10_00_000.0 -> ConfettiType.GOLDEN
        level >= 1_00_000.0 -> ConfettiType.STANDARD
        else -> ConfettiType.SPARKLE
    }

    /**
     * Generates motivational follow-up for net worth milestones.
     */
    private fun generateNetWorthFollowUp(level: Double): String {
        val nextLevel = MilestoneThresholds.netWorthLevels.find { it > level }
        return if (nextLevel != null) {
            "Next target: ₹${formatIndianAmount(nextLevel)}. You know how to get there — keep going!"
        } else {
            "You've reached the highest milestone we track. Time to set your own targets. The sky is the limit!"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Savings Milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if a savings milestone has been crossed.
     *
     * @param currentSavings The user's current total savings in INR.
     * @param previousSavings The savings amount before the latest update.
     * @return A [Milestone] if a new threshold was crossed, null otherwise.
     */
    fun checkSavingsMilestone(currentSavings: Double, previousSavings: Double): Milestone? {
        val crossedLevel = MilestoneThresholds.savingsLevels.find { level ->
            previousSavings < level && currentSavings >= level
        } ?: return null

        val isFirst = crossedLevel == MilestoneThresholds.savingsLevels.first()

        val celebrationMessage = if (isFirst) {
            "🎉 Your first ₹${formatIndianAmount(crossedLevel)} saved! " +
                    "This is where it all begins. You just proved to yourself you CAN save."
        } else {
            "💰 ₹${formatIndianAmount(crossedLevel)} saved! " +
                    "That's real money you built from nothing. Future you is grateful."
        }

        val milestone = Milestone(
            type = MilestoneType.SAVINGS,
            value = crossedLevel,
            celebrationMessage = celebrationMessage,
            shareText = "Savings milestone unlocked: ₹${formatIndianAmount(crossedLevel)}! " +
                    "Building my financial safety net, one step at a time. 🛡️",
            emoji = if (isFirst) "🌱" else "💰",
            confettiType = if (crossedLevel >= 1_00_000.0) ConfettiType.GOLDEN else ConfettiType.STANDARD,
            followUpMessage = generateSavingsFollowUp(crossedLevel)
        )

        achievedMilestones.add(milestone)
        return milestone
    }

    /**
     * Generates follow-up message for savings milestones.
     */
    private fun generateSavingsFollowUp(level: Double): String {
        val nextLevel = MilestoneThresholds.savingsLevels.find { it > level }
        return if (nextLevel != null) {
            val difference = nextLevel - level
            "Only ₹${formatIndianAmount(difference)} to the next milestone. You've got this!"
        } else {
            "You've maxed out our savings milestones! You're a savings champion. 🏆"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Streak Milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if a streak milestone has been reached.
     *
     * @param currentStreakDays The current streak length in days.
     * @return A [Milestone] if a streak threshold was hit, null otherwise.
     */
    fun checkStreakMilestone(currentStreakDays: Int): Milestone? {
        if (currentStreakDays !in MilestoneThresholds.streakLevels) return null

        val celebrationMessage = when (currentStreakDays) {
            7 -> "🔥 7-day streak! One full week of consistency. Habits are forming!"
            14 -> "🔥🔥 14 days! Two weeks strong. This is becoming part of who you are."
            30 -> "⚡ 30-DAY STREAK! A full month! Scientists say it takes 21 days to form a habit. You crushed that."
            60 -> "💪 60 days! Two months of unwavering discipline. You're in the top 5% of users."
            90 -> "🏅 90-DAY STREAK! A full quarter! This isn't a streak anymore — it's a lifestyle."
            180 -> "👑 180 days! Half a year! Your consistency is genuinely impressive."
            365 -> "🏆 365-DAY STREAK! ONE FULL YEAR! This is legendary. You've completely transformed your financial habits."
            else -> "🔥 $currentStreakDays-day streak! Keep going!"
        }

        val shareText = when {
            currentStreakDays >= 365 -> "365-day financial tracking streak! 🏆 One full year of showing up. " +
                    "No shortcuts, just consistency."
            currentStreakDays >= 90 -> "$currentStreakDays-day streak! 🏅 " +
                    "Quarter of a year tracking every rupee. Discipline is freedom."
            else -> "$currentStreakDays-day streak! 🔥 Building habits that build wealth."
        }

        val milestone = Milestone(
            type = MilestoneType.STREAK,
            value = currentStreakDays.toDouble(),
            celebrationMessage = celebrationMessage,
            shareText = shareText,
            emoji = if (currentStreakDays >= 90) "🏅" else "🔥",
            confettiType = when {
                currentStreakDays >= 365 -> ConfettiType.FIREWORKS
                currentStreakDays >= 90 -> ConfettiType.GOLDEN
                currentStreakDays >= 30 -> ConfettiType.STANDARD
                else -> ConfettiType.SPARKLE
            },
            followUpMessage = "Every day you show up, you're investing in your future self. " +
                    "Next milestone: ${getNextStreakTarget(currentStreakDays)} days!"
        )

        achievedMilestones.add(milestone)
        return milestone
    }

    /**
     * Gets the next streak milestone target.
     */
    private fun getNextStreakTarget(current: Int): Int {
        return MilestoneThresholds.streakLevels.find { it > current } ?: (current + 100)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Achievement/Badge Milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if an achievement count milestone has been reached.
     *
     * @param totalBadges Total number of badges/achievements earned.
     * @param totalPossible Total number of possible badges (for "all badges" detection).
     * @return A [Milestone] if a threshold was hit, null otherwise.
     */
    fun checkAchievementMilestone(totalBadges: Int, totalPossible: Int = Int.MAX_VALUE): Milestone? {
        // Check for "all badges" achievement
        if (totalBadges == totalPossible && totalPossible > 0) {
            val milestone = Milestone(
                type = MilestoneType.ACHIEVEMENT,
                value = totalBadges.toDouble(),
                celebrationMessage = "🏆 ALL BADGES UNLOCKED! You've done it all! " +
                        "You're a completionist and a financial master. Incredible.",
                shareText = "Unlocked every single badge! 🏆 " +
                        "100% completion. All financial challenges conquered.",
                emoji = "🏆",
                confettiType = ConfettiType.FIREWORKS,
                followUpMessage = "You've proven mastery over every financial challenge. " +
                        "Now the only competition is yourself."
            )
            achievedMilestones.add(milestone)
            return milestone
        }

        if (totalBadges !in MilestoneThresholds.achievementLevels) return null

        val celebrationMessage = when (totalBadges) {
            1 -> "🏅 First badge earned! Your collection begins. Each badge is proof of a good financial decision."
            5 -> "⭐ 5 badges! You're collecting achievements like a pro. Keep unlocking!"
            10 -> "🌟 10 badges! Double digits! You're mastering multiple financial skills."
            25 -> "💫 25 badges! That's serious dedication across many areas of finance."
            50 -> "✨ 50 badges! Half a century of financial wins. You're an inspiration."
            else -> "🏅 $totalBadges badges earned! Impressive collection!"
        }

        val milestone = Milestone(
            type = MilestoneType.ACHIEVEMENT,
            value = totalBadges.toDouble(),
            celebrationMessage = celebrationMessage,
            shareText = "$totalBadges financial badges unlocked! 🏅 " +
                    "Building good habits one achievement at a time.",
            emoji = if (totalBadges >= 25) "💫" else "🏅",
            confettiType = if (totalBadges >= 25) ConfettiType.GOLDEN else ConfettiType.STANDARD,
            followUpMessage = "Each badge represents a real financial skill you've demonstrated. " +
                    "These aren't just icons — they're proof of growth."
        )

        achievedMilestones.add(milestone)
        return milestone
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Behavior Milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a behavior-based milestone.
     *
     * Use this for custom behavioral achievements like:
     * - "First full month under budget!"
     * - "Debt reduced by 50%!"
     * - "Emergency fund complete!"
     *
     * @param behaviorDescription Description of the behavior achieved.
     * @param celebrationMessage Custom celebration message.
     * @param shareText Custom share text.
     * @return The created [Milestone].
     */
    fun celebrateBehavior(
        behaviorDescription: String,
        celebrationMessage: String? = null,
        shareText: String? = null
    ): Milestone {
        val message = celebrationMessage ?: generateBehaviorCelebration(behaviorDescription)
        val share = shareText ?: "New financial milestone: $behaviorDescription 🎉"

        val milestone = Milestone(
            type = MilestoneType.BEHAVIOR,
            value = 1.0,
            celebrationMessage = message,
            shareText = share,
            emoji = "🎯",
            confettiType = ConfettiType.RAINBOW,
            followUpMessage = "Behavior change is the hardest part of personal finance. " +
                    "You just proved you can do it."
        )

        achievedMilestones.add(milestone)
        return milestone
    }

    /**
     * Checks for common behavior milestones automatically.
     *
     * @param firstMonthUnderBudget Whether this is the first full month under budget.
     * @param debtReductionPercent Percentage of debt reduced (0–100).
     * @param emergencyFundComplete Whether emergency fund target is reached.
     * @param noImpulsePurchasesDays Days without impulse purchases.
     * @return List of behavior milestones achieved (may be empty).
     */
    fun checkBehaviorMilestones(
        firstMonthUnderBudget: Boolean = false,
        debtReductionPercent: Double = 0.0,
        emergencyFundComplete: Boolean = false,
        noImpulsePurchasesDays: Int = 0
    ): List<Milestone> {
        val milestones = mutableListOf<Milestone>()

        if (firstMonthUnderBudget) {
            milestones.add(
                celebrateBehavior(
                    behaviorDescription = "First full month under budget",
                    celebrationMessage = "🎯 FIRST FULL MONTH UNDER BUDGET! " +
                            "You planned, you tracked, you won. This proves budgets work for you."
                )
            )
        }

        if (debtReductionPercent >= 50.0 && debtReductionPercent < 100.0) {
            milestones.add(
                celebrateBehavior(
                    behaviorDescription = "Debt reduced by ${debtReductionPercent.toInt()}%",
                    celebrationMessage = "💪 Debt reduced by ${debtReductionPercent.toInt()}%! " +
                            "You're more than halfway to freedom. The hardest part is behind you."
                )
            )
        }

        if (debtReductionPercent >= 100.0) {
            milestones.add(
                celebrateBehavior(
                    behaviorDescription = "DEBT FREE",
                    celebrationMessage = "🎊🎉🏆 YOU ARE DEBT FREE!! " +
                            "This is life-changing. Every rupee you earn is now YOURS. " +
                            "You did the impossible. Celebrate this forever."
                )
            )
        }

        if (emergencyFundComplete) {
            milestones.add(
                celebrateBehavior(
                    behaviorDescription = "Emergency fund complete",
                    celebrationMessage = "🛡️ EMERGENCY FUND COMPLETE! " +
                            "You now have a financial safety net. Unexpected expenses can't derail you anymore. " +
                            "This is what financial peace feels like."
                )
            )
        }

        if (noImpulsePurchasesDays >= 30) {
            milestones.add(
                celebrateBehavior(
                    behaviorDescription = "$noImpulsePurchasesDays days without impulse purchases",
                    celebrationMessage = "🧘 $noImpulsePurchasesDays days without a single impulse purchase! " +
                            "You've rewired your spending brain. Every purchase is intentional now."
                )
            )
        }

        return milestones
    }

    /**
     * Default behavior celebration generator.
     */
    private fun generateBehaviorCelebration(description: String): String {
        return "🎯 Achievement Unlocked: $description! " +
                "This is a behavior change, and that's the hardest kind of win. Respect."
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "This Day Last Year" Feature
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a "This Day Last Year" comparison.
     *
     * Compares today's financial state with exactly 365 days ago to
     * show progress over a full year.
     *
     * @param today Today's financial snapshot.
     * @param lastYear Financial snapshot from 365 days ago.
     * @return A [ThisDayLastYear] comparison with progress message.
     */
    fun generateThisDayLastYear(today: HistoricalSnapshot, lastYear: HistoricalSnapshot): ThisDayLastYear {
        val balanceChange = today.balance - lastYear.balance
        val netWorthChange = today.netWorth - lastYear.netWorth
        val spendChange = today.dailyAverageSpend - lastYear.dailyAverageSpend

        val improvements = mutableListOf<String>()
        if (balanceChange > 0) improvements.add("balance up ₹${formatIndianAmount(balanceChange)}")
        if (netWorthChange > 0) improvements.add("net worth up ₹${formatIndianAmount(netWorthChange)}")
        if (spendChange < 0) improvements.add("spending down ₹${formatIndianAmount(-spendChange)}/day")

        val declines = mutableListOf<String>()
        if (balanceChange < 0) declines.add("balance")
        if (netWorthChange < 0) declines.add("net worth")
        if (spendChange > 0) declines.add("daily spending increased")

        val (message, emoji) = when {
            improvements.size >= 2 -> Pair(
                "📅 This day last year: You had ₹${formatIndianAmount(lastYear.balance)} in your account " +
                        "and ₹${formatIndianAmount(lastYear.netWorth)} net worth. " +
                        "Today: ₹${formatIndianAmount(today.balance)} balance, " +
                        "₹${formatIndianAmount(today.netWorth)} net worth. " +
                        "That's ${improvements.joinToString(", ")}. A year of growth! 📈",
                "📈"
            )
            improvements.size == 1 -> Pair(
                "📅 This day last year: ${improvements.first()}. " +
                        "Progress isn't always dramatic, but it's real. Keep going.",
                "📊"
            )
            declines.isEmpty() -> Pair(
                "📅 This day last year: Things look about the same. " +
                        "Stability is underrated. You maintained through a full year.",
                "➡️"
            )
            else -> Pair(
                "📅 This day last year: Some numbers were better, some were different. " +
                        "A year is long. What matters is you're still here, still tracking, still growing.",
                "🌱"
            )
        }

        return ThisDayLastYear(
            currentBalance = today.balance,
            lastYearBalance = lastYear.balance,
            balanceChange = balanceChange,
            currentNetWorth = today.netWorth,
            lastYearNetWorth = lastYear.netWorth,
            netWorthChange = netWorthChange,
            currentDailySpend = today.dailyAverageSpend,
            lastYearDailySpend = lastYear.dailyAverageSpend,
            spendChange = spendChange,
            message = message,
            emoji = emoji
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Personal Records
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks if a new personal record has been set.
     *
     * @param recordType The type of record to check.
     * @param newValue The new value to compare against the existing record.
     * @param currentRecord The current record value (null if no record exists yet).
     * @return A [PersonalRecord] if a new record was set, null otherwise.
     */
    fun checkPersonalRecord(
        recordType: RecordType,
        newValue: Double,
        currentRecord: Double? = null
    ): PersonalRecord? {
        val existingRecord = currentRecord ?: personalRecords[recordType]?.value

        val isNewRecord = when {
            existingRecord == null -> true // First entry is always a record
            recordType == RecordType.LOWEST_SPEND_MONTH -> newValue < existingRecord
            else -> newValue > existingRecord
        }

        if (!isNewRecord) return null

        val message = generateRecordMessage(recordType, newValue, existingRecord)

        val record = PersonalRecord(
            recordType = recordType,
            value = newValue,
            previousRecord = existingRecord,
            message = message
        )

        personalRecords[recordType] = record
        return record
    }

    /**
     * Generates a celebration message for breaking a personal record.
     */
    private fun generateRecordMessage(
        recordType: RecordType,
        newValue: Double,
        previousRecord: Double?
    ): String {
        val previousText = if (previousRecord != null) {
            " (Previous: ${formatRecordValue(recordType, previousRecord)})"
        } else ""

        return when (recordType) {
            RecordType.LONGEST_STREAK ->
                "🏆 New record! Longest streak: ${newValue.toInt()} days!$previousText " +
                        "You just outdid yourself!"

            RecordType.MOST_SAVED_MONTH ->
                "🏆 New record! Most saved in a month: ₹${formatIndianAmount(newValue)}!$previousText " +
                        "Your best savings month ever!"

            RecordType.LOWEST_SPEND_MONTH ->
                "🏆 New record! Lowest spending month: ₹${formatIndianAmount(newValue)}!$previousText " +
                        "Maximum efficiency unlocked!"

            RecordType.HIGHEST_NET_WORTH ->
                "🏆 New record! Highest net worth: ₹${formatIndianAmount(newValue)}!$previousText " +
                        "You keep climbing!"

            RecordType.MOST_DAYS_UNDER_BUDGET ->
                "🏆 New record! Most consecutive days under budget: ${newValue.toInt()} days!$previousText " +
                        "Budget mastery!"

            RecordType.LARGEST_SAVINGS_DEPOSIT ->
                "🏆 New record! Largest single deposit: ₹${formatIndianAmount(newValue)}!$previousText " +
                        "Big moves!"
        }
    }

    /**
     * Formats a record value based on its type.
     */
    private fun formatRecordValue(recordType: RecordType, value: Double): String = when (recordType) {
        RecordType.LONGEST_STREAK, RecordType.MOST_DAYS_UNDER_BUDGET -> "${value.toInt()} days"
        else -> "₹${formatIndianAmount(value)}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Query Methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all milestones achieved to date.
     */
    fun getAllMilestones(): List<Milestone> = achievedMilestones.toList()

    /**
     * Returns milestones of a specific type.
     */
    fun getMilestonesByType(type: MilestoneType): List<Milestone> =
        achievedMilestones.filter { it.type == type }

    /**
     * Returns the most recent milestone achieved.
     */
    fun getLatestMilestone(): Milestone? = achievedMilestones.lastOrNull()

    /**
     * Returns all personal records.
     */
    fun getAllPersonalRecords(): Map<RecordType, PersonalRecord> = personalRecords.toMap()

    /**
     * Returns total number of milestones achieved.
     */
    fun getTotalMilestonesAchieved(): Int = achievedMilestones.size

    // ─────────────────────────────────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Formats an amount in Indian numbering system.
     * E.g., 1500000 → "15,00,000"
     */
    private fun formatIndianAmount(amount: Double): String {
        val wholePart = amount.toLong()
        val str = wholePart.toString()
        if (str.length <= 3) return str

        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)
        val formatted = remaining.reversed().chunked(2).joinToString(",").reversed()
        return "$formatted,$lastThree"
    }
}
