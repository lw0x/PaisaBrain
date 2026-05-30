package com.paisabrain.app.followup

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Relationship categories for person-based spending analysis.
 */
enum class RelationshipCategory {
    /** Parents, siblings, spouse, children */
    FAMILY,

    /** Close friends and social circle */
    FRIEND,

    /** Work colleagues and professional contacts */
    COLLEAGUE,

    /** Self-spending (personal purchases) */
    SELF
}

/**
 * Represents a gift given to or received from a person.
 *
 * @property id Unique identifier
 * @property personName Who the gift was for/from
 * @property occasion What the gift was for (birthday, festival, etc.)
 * @property amount Amount spent on the gift
 * @property date When the gift was given (epoch millis)
 * @property description What was purchased
 * @property isGiven true if you gave the gift, false if you received it
 */
data class GiftRecord(
    val id: String = UUID.randomUUID().toString(),
    val personName: String,
    val occasion: String,
    val amount: Double,
    val date: Long,
    val description: String?,
    val isGiven: Boolean = true
)

/**
 * Represents a shared/split expense with a person.
 *
 * @property id Unique identifier
 * @property personName Who the expense was split with
 * @property totalAmount Total bill amount
 * @property yourShare Your portion of the split
 * @property date When the split occurred (epoch millis)
 * @property description What the expense was for (dinner, trip, etc.)
 * @property isSettled Whether the split has been settled
 */
data class SplitRecord(
    val id: String = UUID.randomUUID().toString(),
    val personName: String,
    val totalAmount: Double,
    val yourShare: Double,
    val date: Long,
    val description: String?,
    val isSettled: Boolean = false
)

/**
 * Comprehensive spending profile for a single person/relationship.
 *
 * @property personName Name of the person
 * @property relationship Category of the relationship
 * @property totalSpentOn Total amount you've spent on/for this person
 * @property totalReceivedFrom Total amount received from this person
 * @property giftHistory All gift records involving this person
 * @property splitHistory All split/shared expense records with this person
 * @property lastInteraction Timestamp of last financial interaction (epoch millis)
 */
data class PersonExpenseProfile(
    val personName: String,
    val relationship: RelationshipCategory,
    val totalSpentOn: Double,
    val totalReceivedFrom: Double,
    val giftHistory: List<GiftRecord>,
    val splitHistory: List<SplitRecord>,
    val lastInteraction: Long
)

/**
 * Monthly spending breakdown by relationship category.
 *
 * @property month The month this breakdown covers
 * @property familySpend Total spent on family members
 * @property friendSpend Total spent on friends
 * @property colleagueSpend Total spent on colleagues
 * @property selfSpend Total spent on self
 * @property totalSocialSpend All social spending (family + friends + colleagues)
 * @property totalIncome Total income for the month (if known, for % calculation)
 */
data class MonthlyRelationshipSpend(
    val month: YearMonth,
    val familySpend: Double,
    val friendSpend: Double,
    val colleagueSpend: Double,
    val selfSpend: Double,
    val totalSocialSpend: Double,
    val totalIncome: Double?
)

/**
 * Annual summary of spending on a specific person.
 *
 * @property personName The person
 * @property year The year
 * @property totalSpent Total amount spent on/with them
 * @property giftCount Number of gifts given
 * @property splitCount Number of split expenses
 * @property topOccasion Most common occasion for spending
 * @property averageGiftAmount Average gift value
 */
data class AnnualPersonSummary(
    val personName: String,
    val year: Int,
    val totalSpent: Double,
    val giftCount: Int,
    val splitCount: Int,
    val topOccasion: String?,
    val averageGiftAmount: Double?
)

/**
 * Insight generated from spending pattern analysis.
 *
 * @property title Short insight headline
 * @property description Detailed insight text
 * @property category Which relationship category this insight is about
 * @property isPositive Whether this is a positive/encouraging insight
 * @property suggestedAction Optional suggested action
 */
data class RelationshipInsight(
    val title: String,
    val description: String,
    val category: RelationshipCategory?,
    val isPositive: Boolean,
    val suggestedAction: String?
)

/**
 * Configuration for the relationship expense tracker.
 *
 * @property nationalAverageSocialSpendPercent National average % of income on social activities
 * @property highSpendThresholdPercent Percentage above which to flag high social spending
 * @property inactivityAlertDays Days of no interaction before noting inactivity
 */
data class RelationshipTrackerConfig(
    val nationalAverageSocialSpendPercent: Double = 8.0,
    val highSpendThresholdPercent: Double = 20.0,
    val inactivityAlertDays: Int = 90
)

/**
 * Tracks spending patterns per person and relationship category.
 *
 * This tracker:
 * - Auto-groups transactions by contact/person name
 * - Shows per-person spending breakdowns
 * - Maintains gift history and split history per person
 * - Generates relationship insights (positive framing, never guilt-inducing)
 * - Provides annual per-person summaries for budgeting
 * - Calculates a "generosity score" with positive reinforcement
 *
 * **All processing is entirely on-device. No internet connection required.**
 *
 * Usage:
 * ```kotlin
 * val tracker = RelationshipExpenseTracker(config)
 * tracker.recordGift(GiftRecord(personName = "Mom", occasion = "Birthday", amount = 2000.0, ...))
 * val profile = tracker.getProfile("Mom")
 * val insights = tracker.generateInsights()
 * ```
 *
 * @property config Tracker configuration
 */
class RelationshipExpenseTracker(
    private val config: RelationshipTrackerConfig = RelationshipTrackerConfig()
) {
    private val _profiles = MutableStateFlow<Map<String, PersonExpenseProfile>>(emptyMap())

    /** All person expense profiles, keyed by normalized name. */
    val profiles: StateFlow<Map<String, PersonExpenseProfile>> = _profiles.asStateFlow()

    private val _monthlySpend = MutableStateFlow<MonthlyRelationshipSpend?>(null)

    /** Current month's spending breakdown by category. */
    val monthlySpend: StateFlow<MonthlyRelationshipSpend?> = _monthlySpend.asStateFlow()

    private val _insights = MutableStateFlow<List<RelationshipInsight>>(emptyList())

    /** Generated relationship spending insights. */
    val insights: StateFlow<List<RelationshipInsight>> = _insights.asStateFlow()

    /** Internal storage of all gift records */
    private val allGifts = mutableListOf<GiftRecord>()

    /** Internal storage of all split records */
    private val allSplits = mutableListOf<SplitRecord>()

    /** Person → relationship category mapping */
    private val relationshipMap = mutableMapOf<String, RelationshipCategory>()

    /** Monthly income for percentage calculations */
    private var monthlyIncome: Double? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Sets the user's monthly income for percentage-based insights.
     *
     * @param income Monthly income amount
     */
    fun setMonthlyIncome(income: Double) {
        monthlyIncome = income
        recalculateMonthlySpend()
    }

    /**
     * Assigns a relationship category to a person.
     *
     * @param personName The person's name
     * @param category Their relationship category
     */
    fun setRelationship(personName: String, category: RelationshipCategory) {
        val normalized = normalizeName(personName)
        relationshipMap[normalized] = category
        rebuildProfile(normalized)
    }

    /**
     * Records a gift given to or received from a person.
     *
     * @param gift The gift record to add
     */
    fun recordGift(gift: GiftRecord) {
        allGifts.add(gift)
        rebuildProfile(normalizeName(gift.personName))
        recalculateMonthlySpend()
    }

    /**
     * Records a split/shared expense with a person.
     *
     * @param split The split record to add
     */
    fun recordSplit(split: SplitRecord) {
        allSplits.add(split)
        rebuildProfile(normalizeName(split.personName))
        recalculateMonthlySpend()
    }

    /**
     * Records a general spending transaction associated with a person.
     *
     * This is used for non-gift, non-split spending (e.g., treating someone to coffee).
     *
     * @param personName Person involved
     * @param amount Amount spent
     * @param description What was purchased
     * @param date When it happened (epoch millis)
     */
    fun recordSpending(personName: String, amount: Double, description: String?, date: Long = System.currentTimeMillis()) {
        // Record as a gift with generic occasion
        val gift = GiftRecord(
            personName = personName,
            occasion = "treat",
            amount = amount,
            date = date,
            description = description,
            isGiven = true
        )
        recordGift(gift)
    }

    /**
     * Records money received from a person (gift or payment).
     *
     * @param personName Person who sent money
     * @param amount Amount received
     * @param description Context
     * @param date When received (epoch millis)
     */
    fun recordReceived(personName: String, amount: Double, description: String?, date: Long = System.currentTimeMillis()) {
        val gift = GiftRecord(
            personName = personName,
            occasion = "received",
            amount = amount,
            date = date,
            description = description,
            isGiven = false
        )
        allGifts.add(gift)
        rebuildProfile(normalizeName(personName))
    }

    /**
     * Gets the complete expense profile for a person.
     *
     * @param personName The person to look up
     * @return Their expense profile, or null if no records exist
     */
    fun getProfile(personName: String): PersonExpenseProfile? {
        return _profiles.value[normalizeName(personName)]
    }

    /**
     * Gets per-person spending for the current month.
     *
     * @param personName The person
     * @return Amount spent on/with them this month
     */
    fun getMonthlySpendOnPerson(personName: String): Double {
        val normalized = normalizeName(personName)
        val monthStart = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val giftsThisMonth = allGifts.filter {
            normalizeName(it.personName) == normalized && it.isGiven && it.date >= monthStart
        }.sumOf { it.amount }

        val splitsThisMonth = allSplits.filter {
            normalizeName(it.personName) == normalized && it.date >= monthStart
        }.sumOf { it.yourShare }

        return giftsThisMonth + splitsThisMonth
    }

    /**
     * Gets per-person spending for the current year.
     *
     * @param personName The person
     * @return Amount spent on/with them this year
     */
    fun getYearlySpendOnPerson(personName: String): Double {
        val normalized = normalizeName(personName)
        val yearStart = LocalDate.of(Year.now().value, 1, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val giftsThisYear = allGifts.filter {
            normalizeName(it.personName) == normalized && it.isGiven && it.date >= yearStart
        }.sumOf { it.amount }

        val splitsThisYear = allSplits.filter {
            normalizeName(it.personName) == normalized && it.date >= yearStart
        }.sumOf { it.yourShare }

        return giftsThisYear + splitsThisYear
    }

    /**
     * Gets the formatted per-person spending display text.
     *
     * @param personName The person
     * @return Formatted text like "Spent on/with Mom: ₹5,000 this month, ₹32,000 this year"
     */
    fun getPersonSpendingText(personName: String): String {
        val monthly = getMonthlySpendOnPerson(personName)
        val yearly = getYearlySpendOnPerson(personName)

        return "Spent on/with $personName: ${formatAmount(monthly)} this month, ${formatAmount(yearly)} this year"
    }

    /**
     * Gets the last gift given to a person with context.
     *
     * @param personName The person
     * @return Formatted text like "Last gift to Mom: ₹1,500 (birthday, May 2025)"
     */
    fun getLastGiftText(personName: String): String? {
        val normalized = normalizeName(personName)
        val lastGift = allGifts
            .filter { normalizeName(it.personName) == normalized && it.isGiven }
            .maxByOrNull { it.date } ?: return null

        val date = Instant.ofEpochMilli(lastGift.date)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val dateStr = date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

        return buildString {
            append("Last gift to $personName: ${formatAmount(lastGift.amount)}")
            append(" (${lastGift.occasion}, $dateStr)")
            lastGift.description?.let { append(" — $it") }
        }
    }

    /**
     * Generates the "Generosity Score" — positive framing of social spending.
     *
     * This is intentionally positive and encouraging. It celebrates generosity
     * rather than guilt-tripping about spending.
     *
     * @return Generosity score text with positive framing
     */
    fun getGenerosityScoreText(): String {
        val yearStart = LocalDate.of(Year.now().value, 1, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val totalTreats = allGifts
            .filter { it.isGiven && it.date >= yearStart }
            .sumOf { it.amount }

        val friendTreats = allGifts
            .filter { gift ->
                gift.isGiven &&
                        gift.date >= yearStart &&
                        relationshipMap[normalizeName(gift.personName)] == RelationshipCategory.FRIEND
            }.sumOf { it.amount }

        return buildString {
            append("💛 Generosity Score")
            append("\n\nYou've treated friends ${formatAmount(friendTreats)} this year.")
            append("\nTotal social giving: ${formatAmount(totalTreats)}")
            append("\n\nThat's generosity! Keep spreading the love 💛")
        }
    }

    /**
     * Generates relationship spending insights.
     *
     * Insights are always framed positively:
     * - High spending → "generous"
     * - Low spending → "mindful"
     * - Never guilt-tripping or shaming
     *
     * @return List of generated insights
     */
    fun generateInsights(): List<RelationshipInsight> {
        val insights = mutableListOf<RelationshipInsight>()

        // Social spending percentage insight
        val income = monthlyIncome
        val currentMonthSpend = _monthlySpend.value

        if (income != null && income > 0 && currentMonthSpend != null) {
            val socialPercent = (currentMonthSpend.totalSocialSpend / income) * 100

            if (socialPercent > config.nationalAverageSocialSpendPercent) {
                insights.add(RelationshipInsight(
                    title = "Social Butterfly! 🦋",
                    description = "You spend ${socialPercent.toInt()}% of monthly income on social activities with friends. " +
                            "National average: ${config.nationalAverageSocialSpendPercent.toInt()}%. " +
                            "You're clearly someone who values relationships!",
                    category = RelationshipCategory.FRIEND,
                    isPositive = true,
                    suggestedAction = if (socialPercent > config.highSpendThresholdPercent) {
                        "Consider setting a social budget to balance saving and spending"
                    } else null
                ))
            } else {
                insights.add(RelationshipInsight(
                    title = "Mindful Spender 🧘",
                    description = "Your social spending is ${socialPercent.toInt()}% of income — " +
                            "below the ${config.nationalAverageSocialSpendPercent.toInt()}% average. " +
                            "You're keeping a good balance!",
                    category = null,
                    isPositive = true,
                    suggestedAction = null
                ))
            }
        }

        // Top person insight
        val topPerson = _profiles.value.values
            .filter { it.relationship != RelationshipCategory.SELF }
            .maxByOrNull { it.totalSpentOn }

        if (topPerson != null && topPerson.totalSpentOn > 0) {
            insights.add(RelationshipInsight(
                title = "Most cherished: ${topPerson.personName}",
                description = "You've spent the most on ${topPerson.personName} " +
                        "(${formatAmount(topPerson.totalSpentOn)} total). " +
                        "They must be special to you! 💝",
                category = topPerson.relationship,
                isPositive = true,
                suggestedAction = null
            ))
        }

        // Inactivity insight (gentle, not guilt-tripping)
        val now = System.currentTimeMillis()
        val inactiveProfiles = _profiles.value.values.filter { profile ->
            profile.relationship == RelationshipCategory.FRIEND &&
                    ChronoUnit.DAYS.between(
                        Instant.ofEpochMilli(profile.lastInteraction).atZone(ZoneId.systemDefault()).toLocalDate(),
                        LocalDate.now()
                    ) > config.inactivityAlertDays
        }

        if (inactiveProfiles.isNotEmpty()) {
            val names = inactiveProfiles.take(3).joinToString(", ") { it.personName }
            insights.add(RelationshipInsight(
                title = "Haven't caught up lately",
                description = "No transactions with $names in ${config.inactivityAlertDays}+ days. " +
                        "Maybe time for a coffee? ☕",
                category = RelationshipCategory.FRIEND,
                isPositive = true,
                suggestedAction = "Plan a casual meetup"
            ))
        }

        _insights.value = insights
        return insights
    }

    /**
     * Gets the annual per-person summary for budgeting.
     *
     * @param personName The person
     * @param year The year to summarize (defaults to current year)
     * @return Annual summary for this person
     */
    fun getAnnualSummary(personName: String, year: Int = Year.now().value): AnnualPersonSummary {
        val normalized = normalizeName(personName)
        val yearStart = LocalDate.of(year, 1, 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val yearEnd = LocalDate.of(year, 12, 31)
            .atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val yearGifts = allGifts.filter { gift ->
            normalizeName(gift.personName) == normalized &&
                    gift.isGiven &&
                    gift.date in yearStart..yearEnd
        }

        val yearSplits = allSplits.filter { split ->
            normalizeName(split.personName) == normalized &&
                    split.date in yearStart..yearEnd
        }

        val totalSpent = yearGifts.sumOf { it.amount } + yearSplits.sumOf { it.yourShare }

        val topOccasion = yearGifts
            .groupBy { it.occasion }
            .maxByOrNull { it.value.sumOf { g -> g.amount } }
            ?.key

        val avgGift = if (yearGifts.isNotEmpty()) yearGifts.map { it.amount }.average() else null

        return AnnualPersonSummary(
            personName = personName,
            year = year,
            totalSpent = totalSpent,
            giftCount = yearGifts.size,
            splitCount = yearSplits.size,
            topOccasion = topOccasion,
            averageGiftAmount = avgGift
        )
    }

    /**
     * Gets formatted annual summary text for display.
     *
     * @param personName The person
     * @return Multi-line formatted summary
     */
    fun getAnnualSummaryText(personName: String): String {
        val summary = getAnnualSummary(personName)

        return buildString {
            append("📊 Annual Summary: ${summary.personName} (${summary.year})")
            append("\n\nTotal spent: ${formatAmount(summary.totalSpent)}")
            append("\nGifts given: ${summary.giftCount}")
            if (summary.averageGiftAmount != null) {
                append(" (avg: ${formatAmount(summary.averageGiftAmount)})")
            }
            append("\nSplit expenses: ${summary.splitCount}")
            summary.topOccasion?.let {
                append("\nTop occasion: ${it.replaceFirstChar { c -> c.uppercase() }}")
            }
        }
    }

    /**
     * Gets spending by category for the current month.
     *
     * @return Map of category → total amount
     */
    fun getSpendByCategory(): Map<RelationshipCategory, Double> {
        val monthStart = YearMonth.now().atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return RelationshipCategory.entries.associateWith { category ->
            val personsInCategory = relationshipMap.entries
                .filter { it.value == category }
                .map { it.key }

            val giftSpend = allGifts.filter { gift ->
                normalizeName(gift.personName) in personsInCategory &&
                        gift.isGiven && gift.date >= monthStart
            }.sumOf { it.amount }

            val splitSpend = allSplits.filter { split ->
                normalizeName(split.personName) in personsInCategory &&
                        split.date >= monthStart
            }.sumOf { it.yourShare }

            giftSpend + splitSpend
        }
    }

    /**
     * Gets all profiles sorted by total spending (highest first).
     *
     * @param category Optional filter by relationship category
     * @return Sorted list of profiles
     */
    fun getProfilesBySpending(category: RelationshipCategory? = null): List<PersonExpenseProfile> {
        return _profiles.value.values
            .filter { category == null || it.relationship == category }
            .sortedByDescending { it.totalSpentOn }
    }

    // ─────────────────────────────────────────────────────────────
    // Internal Methods
    // ─────────────────────────────────────────────────────────────

    /**
     * Rebuilds the expense profile for a specific person.
     */
    private fun rebuildProfile(normalizedName: String) {
        val personGifts = allGifts.filter { normalizeName(it.personName) == normalizedName }
        val personSplits = allSplits.filter { normalizeName(it.personName) == normalizedName }

        val givenGifts = personGifts.filter { it.isGiven }
        val receivedGifts = personGifts.filter { !it.isGiven }

        val totalSpent = givenGifts.sumOf { it.amount } + personSplits.sumOf { it.yourShare }
        val totalReceived = receivedGifts.sumOf { it.amount }

        val lastInteraction = listOfNotNull(
            personGifts.maxByOrNull { it.date }?.date,
            personSplits.maxByOrNull { it.date }?.date
        ).maxOrNull() ?: 0L

        val displayName = personGifts.firstOrNull()?.personName
            ?: personSplits.firstOrNull()?.personName
            ?: normalizedName

        val relationship = relationshipMap[normalizedName] ?: RelationshipCategory.FRIEND

        val profile = PersonExpenseProfile(
            personName = displayName,
            relationship = relationship,
            totalSpentOn = totalSpent,
            totalReceivedFrom = totalReceived,
            giftHistory = personGifts,
            splitHistory = personSplits,
            lastInteraction = lastInteraction
        )

        _profiles.value = _profiles.value + (normalizedName to profile)
    }

    /**
     * Recalculates the current month's spending breakdown.
     */
    private fun recalculateMonthlySpend() {
        val currentMonth = YearMonth.now()
        val monthStart = currentMonth.atDay(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val categorySpend = getSpendByCategory()

        val familySpend = categorySpend[RelationshipCategory.FAMILY] ?: 0.0
        val friendSpend = categorySpend[RelationshipCategory.FRIEND] ?: 0.0
        val colleagueSpend = categorySpend[RelationshipCategory.COLLEAGUE] ?: 0.0
        val selfSpend = categorySpend[RelationshipCategory.SELF] ?: 0.0

        _monthlySpend.value = MonthlyRelationshipSpend(
            month = currentMonth,
            familySpend = familySpend,
            friendSpend = friendSpend,
            colleagueSpend = colleagueSpend,
            selfSpend = selfSpend,
            totalSocialSpend = familySpend + friendSpend + colleagueSpend,
            totalIncome = monthlyIncome
        )
    }

    /**
     * Normalizes a person name for consistent lookups.
     */
    private fun normalizeName(name: String): String {
        return name.lowercase().trim()
    }

    /**
     * Formats an amount with rupee symbol.
     */
    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            "₹${formatIndianNumber(amount.toLong())}"
        } else {
            "₹${"%.2f".format(amount)}"
        }
    }

    /**
     * Formats a number in Indian numbering system (lakhs, thousands).
     * Example: 150000 → "1,50,000"
     */
    private fun formatIndianNumber(number: Long): String {
        if (number < 1000) return number.toString()

        val str = number.toString()
        val lastThree = str.takeLast(3)
        val remaining = str.dropLast(3)

        val formatted = buildString {
            remaining.reversed().chunked(2).joinToString(",").reversed().let { append(it) }
            append(",")
            append(lastThree)
        }

        return formatted.trimStart(',')
    }

    /**
     * Cleans up resources.
     */
    fun destroy() {
        scope.cancel()
    }
}
