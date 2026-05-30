package com.paisabrain.app.ai

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Helps users identify, evaluate, and cancel recurring subscriptions.
 *
 * Provides category-specific cancellation guidance, cost analysis,
 * savings tracking, and generates copyable cancellation request templates.
 * All references use generic category names — no brand or company names.
 */
class SubscriptionCancelHelper {

    // ─────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Represents a detected recurring subscription charge.
     *
     * @property id Unique identifier for the subscription.
     * @property category High-level category (e.g., MUSIC_STREAMING, VIDEO_PLATFORM).
     * @property monthlyAmount Monthly charge amount in ₹.
     * @property billingCycleDay Day of the month when billing occurs.
     * @property lastChargeDate Date of the most recent charge.
     * @property lastUsedDate Date the service was last actively used (nullable if unknown).
     * @property detectedFromSms Whether the subscription was detected via SMS parsing.
     * @property merchantLabel Generic label shown to user (e.g., "Music Streaming Service").
     * @property isActive Whether the subscription is currently active.
     */
    data class SubscriptionInfo(
        val id: String,
        val category: SubscriptionCategory,
        val monthlyAmount: Double,
        val billingCycleDay: Int,
        val lastChargeDate: LocalDate,
        val lastUsedDate: LocalDate? = null,
        val detectedFromSms: Boolean = true,
        val merchantLabel: String,
        val isActive: Boolean = true
    )

    /**
     * Step-by-step cancellation guidance for a subscription.
     *
     * @property subscriptionId Linked subscription identifier.
     * @property genericSteps Universal cancellation steps.
     * @property categorySteps Category-specific additional guidance.
     * @property daysUntilNextBilling Days remaining before next billing cycle.
     * @property daysSinceLastUsed Days since the service was last used (null if unknown).
     * @property annualCost Projected annual cost at current rate.
     * @property alternatives Suggested alternatives or cost-saving options.
     * @property cancellationEmailTemplate Copyable cancellation request text.
     * @property reminderDate Recommended date to cancel before next charge.
     */
    data class CancellationGuide(
        val subscriptionId: String,
        val genericSteps: List<String>,
        val categorySteps: List<String>,
        val daysUntilNextBilling: Long,
        val daysSinceLastUsed: Long?,
        val annualCost: Double,
        val alternatives: List<String>,
        val cancellationEmailTemplate: String,
        val reminderDate: LocalDate
    )

    /**
     * Tracks savings achieved from cancelled subscriptions.
     *
     * @property cancelledSubscriptions List of subscriptions the user has cancelled.
     * @property totalMonthlySaved Total monthly amount saved from all cancellations.
     * @property totalAnnualSaved Projected annual savings from all cancellations.
     * @property cancelledSinceDate Date tracking started.
     * @property lifetimeSaved Total amount saved since first cancellation.
     */
    data class SavingsFromCancellation(
        val cancelledSubscriptions: List<SubscriptionInfo>,
        val totalMonthlySaved: Double,
        val totalAnnualSaved: Double,
        val cancelledSinceDate: LocalDate,
        val lifetimeSaved: Double
    )

    /**
     * Categories of subscriptions with generic labels.
     */
    enum class SubscriptionCategory(val displayName: String) {
        VIDEO_PLATFORM("Video Streaming Service"),
        MUSIC_STREAMING("Music Streaming Service"),
        CLOUD_STORAGE("Cloud Storage Service"),
        FOOD_DELIVERY("Food Delivery Service"),
        FITNESS("Fitness/Wellness Service"),
        NEWS_MAGAZINE("News/Magazine Service"),
        PRODUCTIVITY("Productivity Tool"),
        GAMING("Gaming Service"),
        EDUCATION("Learning Platform"),
        SHOPPING_MEMBERSHIP("Shopping Membership"),
        COMMUNICATION("Communication Service"),
        OTHER("Subscription Service")
    }

    // ─────────────────────────────────────────────────────────────────────
    // In-Memory State
    // ─────────────────────────────────────────────────────────────────────

    private val activeSubscriptions = mutableListOf<SubscriptionInfo>()
    private val cancelledSubscriptions = mutableListOf<SubscriptionInfo>()
    private var trackingSinceDate: LocalDate = LocalDate.now()

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers a detected subscription for tracking.
     *
     * @param subscription The subscription information to track.
     */
    fun addDetectedSubscription(subscription: SubscriptionInfo) {
        if (activeSubscriptions.none { it.id == subscription.id }) {
            activeSubscriptions.add(subscription)
        }
    }

    /**
     * Returns all currently active subscriptions.
     */
    fun getActiveSubscriptions(): List<SubscriptionInfo> =
        activeSubscriptions.filter { it.isActive }.toList()

    /**
     * Calculates the total monthly spend on all active subscriptions.
     */
    fun getTotalMonthlySubscriptionCost(): Double =
        activeSubscriptions.filter { it.isActive }.sumOf { it.monthlyAmount }

    /**
     * Calculates the total annual spend on all active subscriptions.
     */
    fun getTotalAnnualSubscriptionCost(): Double =
        getTotalMonthlySubscriptionCost() * 12

    /**
     * Generates a comprehensive cancellation guide for a specific subscription.
     *
     * @param subscriptionId The ID of the subscription to generate guidance for.
     * @return CancellationGuide with steps, costs, and alternatives, or null if not found.
     */
    fun generateCancellationGuide(subscriptionId: String): CancellationGuide? {
        val subscription = activeSubscriptions.find { it.id == subscriptionId } ?: return null
        val today = LocalDate.now()

        val daysUntilNextBilling = calculateDaysUntilNextBilling(subscription, today)
        val daysSinceLastUsed = subscription.lastUsedDate?.let {
            ChronoUnit.DAYS.between(it, today)
        }
        val annualCost = subscription.monthlyAmount * 12
        val reminderDate = calculateReminderDate(subscription, today)

        return CancellationGuide(
            subscriptionId = subscriptionId,
            genericSteps = getGenericCancellationSteps(),
            categorySteps = getCategorySpecificSteps(subscription.category),
            daysUntilNextBilling = daysUntilNextBilling,
            daysSinceLastUsed = daysSinceLastUsed,
            annualCost = annualCost,
            alternatives = getAlternatives(subscription.category),
            cancellationEmailTemplate = generateCancellationEmail(subscription),
            reminderDate = reminderDate
        )
    }

    /**
     * Marks a subscription as cancelled and updates savings tracking.
     *
     * @param subscriptionId The ID of the subscription that was cancelled.
     * @return Updated savings information, or null if subscription not found.
     */
    fun markAsCancelled(subscriptionId: String): SavingsFromCancellation? {
        val subscription = activeSubscriptions.find { it.id == subscriptionId } ?: return null

        activeSubscriptions.removeAll { it.id == subscriptionId }
        cancelledSubscriptions.add(subscription.copy(isActive = false))

        return calculateSavings()
    }

    /**
     * Returns total savings from all cancelled subscriptions.
     */
    fun calculateSavings(): SavingsFromCancellation {
        val totalMonthly = cancelledSubscriptions.sumOf { it.monthlyAmount }
        val monthsSinceTracking = ChronoUnit.MONTHS.between(trackingSinceDate, LocalDate.now())
            .coerceAtLeast(1)

        return SavingsFromCancellation(
            cancelledSubscriptions = cancelledSubscriptions.toList(),
            totalMonthlySaved = totalMonthly,
            totalAnnualSaved = totalMonthly * 12,
            cancelledSinceDate = trackingSinceDate,
            lifetimeSaved = totalMonthly * monthsSinceTracking
        )
    }

    /**
     * Identifies subscriptions that appear unused based on last usage date.
     *
     * @param unusedThresholdDays Number of days without use to consider "unused" (default 30).
     * @return List of subscriptions not used within the threshold period.
     */
    fun getUnusedSubscriptions(unusedThresholdDays: Long = 30): List<SubscriptionInfo> {
        val today = LocalDate.now()
        return activeSubscriptions.filter { sub ->
            sub.lastUsedDate?.let { lastUsed ->
                ChronoUnit.DAYS.between(lastUsed, today) > unusedThresholdDays
            } ?: true // If usage is unknown, flag it
        }
    }

    /**
     * Generates a cost breakdown string for a subscription.
     * Example: "₹199/month = ₹2,388/year"
     *
     * @param subscription The subscription to format cost breakdown for.
     * @return Formatted cost string.
     */
    fun formatCostBreakdown(subscription: SubscriptionInfo): String {
        val monthly = subscription.monthlyAmount
        val annual = monthly * 12
        return "₹${formatAmount(monthly)}/month = ₹${formatAmount(annual)}/year"
    }

    /**
     * Generates a "last used" summary string.
     *
     * @param subscription The subscription to check usage for.
     * @return Human-readable last-used string, or "Usage unknown" if not tracked.
     */
    fun formatLastUsedSummary(subscription: SubscriptionInfo): String {
        val today = LocalDate.now()
        return subscription.lastUsedDate?.let { lastUsed ->
            val days = ChronoUnit.DAYS.between(lastUsed, today)
            when {
                days == 0L -> "Used today"
                days == 1L -> "Last used yesterday"
                days < 7L -> "Last used $days days ago"
                days < 30L -> "Last used ${days / 7} weeks ago"
                days < 365L -> "Last used ${days / 30} months ago"
                else -> "Last used over a year ago"
            }
        } ?: "Usage unknown — consider if you still need this"
    }

    /**
     * Prioritizes subscriptions by cancellation impact.
     * Higher scores indicate better candidates for cancellation.
     *
     * @return List of subscriptions sorted by cancellation priority (highest first).
     */
    fun getPrioritizedCancellationList(): List<Pair<SubscriptionInfo, Int>> {
        val today = LocalDate.now()
        return activeSubscriptions.map { sub ->
            val score = calculateCancellationPriority(sub, today)
            Pair(sub, score)
        }.sortedByDescending { it.second }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ─────────────────────────────────────────────────────────────────────

    private fun calculateDaysUntilNextBilling(
        subscription: SubscriptionInfo,
        today: LocalDate
    ): Long {
        val nextBillingDate = if (today.dayOfMonth >= subscription.billingCycleDay) {
            today.plusMonths(1).withDayOfMonth(
                subscription.billingCycleDay.coerceAtMost(
                    today.plusMonths(1).lengthOfMonth()
                )
            )
        } else {
            today.withDayOfMonth(
                subscription.billingCycleDay.coerceAtMost(today.lengthOfMonth())
            )
        }
        return ChronoUnit.DAYS.between(today, nextBillingDate)
    }

    private fun calculateReminderDate(
        subscription: SubscriptionInfo,
        today: LocalDate
    ): LocalDate {
        val daysUntilBilling = calculateDaysUntilNextBilling(subscription, today)
        // Remind 3 days before billing, or today if billing is within 3 days
        return today.plusDays((daysUntilBilling - 3).coerceAtLeast(0))
    }

    private fun calculateCancellationPriority(
        subscription: SubscriptionInfo,
        today: LocalDate
    ): Int {
        var score = 0

        // Higher cost = higher priority
        score += (subscription.monthlyAmount / 50).toInt().coerceAtMost(20)

        // Longer unused = higher priority
        subscription.lastUsedDate?.let { lastUsed ->
            val daysUnused = ChronoUnit.DAYS.between(lastUsed, today)
            score += when {
                daysUnused > 90 -> 30
                daysUnused > 60 -> 25
                daysUnused > 30 -> 20
                daysUnused > 14 -> 10
                daysUnused > 7 -> 5
                else -> 0
            }
        } ?: run {
            score += 15 // Unknown usage is moderately suspicious
        }

        // Billing coming soon = higher urgency
        val daysUntilBilling = calculateDaysUntilNextBilling(subscription, today)
        if (daysUntilBilling <= 5) score += 15
        else if (daysUntilBilling <= 10) score += 8

        return score
    }

    private fun getGenericCancellationSteps(): List<String> = listOf(
        "Open the service's app or website",
        "Navigate to Settings or Account section",
        "Find 'Subscription' or 'Membership' or 'Plan' section",
        "Look for 'Cancel Subscription' or 'Manage Plan' option",
        "Confirm cancellation (they may offer discounts — stay firm if you want to cancel)",
        "Take a screenshot of the cancellation confirmation",
        "Check for confirmation email/SMS",
        "Verify no charge on your next billing date"
    )

    private fun getCategorySpecificSteps(category: SubscriptionCategory): List<String> =
        when (category) {
            SubscriptionCategory.VIDEO_PLATFORM -> listOf(
                "Check if you have downloaded content — it will be lost after cancellation",
                "Note any shared profiles that will also lose access",
                "Consider downgrading to a lower tier first if available",
                "Check if content you want is available on free ad-supported platforms"
            )
            SubscriptionCategory.MUSIC_STREAMING -> listOf(
                "Export your playlists before cancelling (most services allow this)",
                "Downloaded music for offline use will no longer be accessible",
                "Check if your telecom plan includes a free music service",
                "Free tiers often exist with ads — consider switching to that"
            )
            SubscriptionCategory.CLOUD_STORAGE -> listOf(
                "IMPORTANT: Download all stored files before cancellation",
                "Check storage usage — files may be deleted after grace period",
                "Move important photos/documents to local device storage first",
                "Check if your device's built-in storage is sufficient"
            )
            SubscriptionCategory.FOOD_DELIVERY -> listOf(
                "Check if your membership has cashback benefits to use up",
                "Verify any prepaid wallet balance and use it before cancelling",
                "Calculate if delivery fees without membership exceed the cost",
                "Consider ordering directly from restaurants to save fees"
            )
            SubscriptionCategory.FITNESS -> listOf(
                "Check if you're in a lock-in period with early cancellation fees",
                "Download any workout history or progress data",
                "Free workout videos are widely available online",
                "Consider outdoor activities as a free alternative"
            )
            SubscriptionCategory.NEWS_MAGAZINE -> listOf(
                "Check if your library provides free digital access to publications",
                "Many sources offer free articles per month — track your usage",
                "Consider RSS feeds or free newsletters as alternatives",
                "Student/senior discounts may be available if applicable"
            )
            SubscriptionCategory.PRODUCTIVITY -> listOf(
                "Export all documents and data in standard formats (PDF, CSV)",
                "Check if free alternatives cover your actual usage",
                "Verify if your employer provides this tool already",
                "Many open-source alternatives exist for common tools"
            )
            SubscriptionCategory.GAMING -> listOf(
                "Check if you lose access to claimed free games after cancellation",
                "Online multiplayer may require active subscription on some platforms",
                "Download any cloud save data before cancelling",
                "Consider if you play enough to justify the cost"
            )
            SubscriptionCategory.EDUCATION -> listOf(
                "Download certificates and course completion records",
                "Check if courses remain accessible after cancellation",
                "Many platforms offer individual course purchases instead",
                "Free educational resources are available from major institutions"
            )
            SubscriptionCategory.SHOPPING_MEMBERSHIP -> listOf(
                "Use up any remaining cashback or reward points",
                "Check if pending orders will be affected",
                "Calculate if shipping savings justify the membership cost",
                "Compare prices — members don't always get the best deals"
            )
            SubscriptionCategory.COMMUNICATION -> listOf(
                "Inform contacts before switching away",
                "Export chat history if the feature is available",
                "Check if free tier meets your needs",
                "Verify if features you use are actually premium-only"
            )
            SubscriptionCategory.OTHER -> listOf(
                "Review what specific benefits you receive",
                "Check for any data export options before cancelling",
                "Look for free alternatives that cover your use case",
                "Verify cancellation terms and any early termination fees"
            )
        }

    private fun getAlternatives(category: SubscriptionCategory): List<String> =
        when (category) {
            SubscriptionCategory.VIDEO_PLATFORM -> listOf(
                "Free ad-supported streaming platforms available",
                "Share a family plan with household members?",
                "Rotate between services monthly instead of paying all at once",
                "Check if your internet provider bundles any streaming free"
            )
            SubscriptionCategory.MUSIC_STREAMING -> listOf(
                "Free tier with ads available on most services",
                "Radio apps are completely free",
                "Your telecom plan may include free music streaming",
                "Family plan sharing can reduce per-person cost significantly"
            )
            SubscriptionCategory.CLOUD_STORAGE -> listOf(
                "Free tier (5-15 GB) may be sufficient for essential files",
                "Use multiple free services to combine storage",
                "External hard drive is a one-time cost for unlimited local storage",
                "Regularly clean up old files to stay within free limits"
            )
            SubscriptionCategory.FOOD_DELIVERY -> listOf(
                "Order directly from restaurants (often cheaper)",
                "Cook at home — potential savings of ₹3,000-5,000/month",
                "Use the app without membership for occasional orders",
                "Compare delivery fees — sometimes membership isn't worth it"
            )
            SubscriptionCategory.FITNESS -> listOf(
                "Free workout videos available on video platforms",
                "Running/walking outdoors is free",
                "Community fitness groups often cost nothing",
                "Basic yoga/exercise can be done at home without equipment"
            )
            SubscriptionCategory.NEWS_MAGAZINE -> listOf(
                "Many quality free newsletters available",
                "Public library provides free digital magazine access",
                "Free articles per month from most publications",
                "Aggregator apps compile news for free"
            )
            else -> listOf(
                "Check if a free tier covers your needs",
                "Family/group plan to split costs?",
                "Annual billing often cheaper than monthly (if keeping)",
                "Open-source or free alternatives may exist"
            )
        }

    private fun generateCancellationEmail(subscription: SubscriptionInfo): String {
        return """
            |Subject: Request to Cancel My Subscription
            |
            |Dear Customer Support,
            |
            |I am writing to request the immediate cancellation of my subscription
            |to your ${subscription.category.displayName.lowercase()} service.
            |
            |Subscription Details:
            |- Service Type: ${subscription.category.displayName}
            |- Monthly Charge: ₹${formatAmount(subscription.monthlyAmount)}
            |- Billing Date: ${subscription.billingCycleDay} of each month
            |
            |Please process this cancellation effective immediately and confirm:
            |1. No further charges will be made to my account
            |2. The exact date my access will end
            |3. Any refund applicable for the current billing cycle
            |
            |Please send a written confirmation of cancellation to this email address.
            |
            |If cancellation cannot be processed via email, please provide the exact
            |steps I need to follow to complete this cancellation.
            |
            |Thank you for your prompt attention to this matter.
            |
            |Regards,
            |[Your Name]
        """.trimMargin()
    }

    private fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            String.format("%,d", amount.toLong())
        } else {
            String.format("%,.2f", amount)
        }
    }
}
