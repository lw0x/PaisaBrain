package com.paisabrain.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * RoastGenerator - Weekly witty "roasts" about spending habits.
 *
 * Generates fun, non-judgmental, relatable roasts based on actual spending data.
 * Each roast uses real numbers and merchant names to make them personal and engaging.
 * The tone is that of a supportive friend who teases you about money — never mean,
 * always constructive under the humor.
 *
 * Contains 50+ roast templates organized by category, with dynamic placeholder
 * substitution for personalized delivery.
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
class RoastGenerator {

    // ============================================================
    // Data Models
    // ============================================================

    data class Transaction(
        val amount: Double,
        val merchant: String,
        val category: String,
        val timestamp: LocalDateTime,
        val isCredit: Boolean = false
    )

    data class Roast(
        val id: String = "roast_${System.currentTimeMillis()}_${++roastCounter}",
        val text: String,
        val emoji: String,
        val category: RoastCategory,
        val savageryLevel: Int, // 1-5 (1 = gentle, 5 = savage)
        val relatedAmount: Double? = null,
        val actionSuggestion: String? = null,
        val generatedAt: LocalDateTime = LocalDateTime.now()
    )

    enum class RoastCategory {
        FOOD_DELIVERY, SHOPPING, SUBSCRIPTIONS, TRANSPORTATION,
        OVERALL_SPENDING, LATE_NIGHT, WEEKEND, PATTERN,
        COMPARISON, SAVINGS, IMPULSE, MERCHANT_SPECIFIC
    }

    // ============================================================
    // Roast Templates (50+ with placeholders)
    // ============================================================

    /**
     * Template placeholders:
     * {amount} - Total amount spent
     * {merchant} - Merchant name
     * {count} - Number of transactions/orders
     * {category} - Spending category
     * {equivalent} - Fun equivalent comparison
     * {equivalent_count} - Number of equivalent items
     * {percent} - Percentage
     * {days} - Number of days
     * {time} - Time of day
     * {savings} - Amount that could be saved
     * {streak} - Number of consecutive days
     */

    private val foodDeliveryRoasts = listOf(
        RoastTemplate(
            template = "You spent ₹{amount} on {merchant} this month. That's {equivalent_count} plates of dal chawal your mom would've made for free 🫠",
            emoji = "🫠",
            savagery = 3,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "₹{amount} on food delivery. Your kitchen is starting to think you moved out 🍳",
            emoji = "🍳",
            savagery = 2,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "{count} orders from {merchant} this month. At this point, just marry the delivery guy 💍",
            emoji = "💍",
            savagery = 4,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "Your {merchant} bill is ₹{amount}. That's literally someone's rent in tier-2 cities 🏠",
            emoji = "🏠",
            savagery = 4,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "You ordered food {count} times. That's once every {days} days. Your stove filed a missing person report 🔥",
            emoji = "🔥",
            savagery = 3,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "₹{amount} on {merchant}. You know what that could've been? A really nice pressure cooker and a free cooking video channel subscription (₹0) 👨‍🍳",
            emoji = "👨‍🍳",
            savagery = 2,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "Food delivery apps combined: ₹{amount}. Sir/Ma'am, your bank account is crying 😭",
            emoji = "😭",
            savagery = 4,
            category = RoastCategory.FOOD_DELIVERY
        ),
        RoastTemplate(
            template = "Your food delivery spend could feed a family of 4 for a month. You fed yourself for... {days} days. Math isn't mathing 📐",
            emoji = "📐",
            savagery = 5,
            category = RoastCategory.FOOD_DELIVERY
        )
    )

    private val shoppingRoasts = listOf(
        RoastTemplate(
            template = "₹{amount} on {merchant}. Your 'Add to Cart' finger needs an intervention 🛒",
            emoji = "🛒",
            savagery = 3,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "You shopped {count} times on {merchant}. That's not retail therapy, that's retail addiction, bestie 🛍️",
            emoji = "🛍️",
            savagery = 4,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "Online shopping apps = ₹{amount}. The delivery guys in your area are thinking of unionizing just for you 📦",
            emoji = "📦",
            savagery = 4,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "₹{amount} on shopping this month. Flash sales aren't 'saving money' if you weren't gonna buy it anyway 🏷️",
            emoji = "🏷️",
            savagery = 3,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "Your online shopping: ₹{amount}. The 'No Cost EMI' isn't no-cost if you're buying stuff you don't need 🤡",
            emoji = "🤡",
            savagery = 5,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "You spent ₹{amount} on shopping. That's {equivalent_count} SIP installments that would've grown to 2x in 7 years 📈",
            emoji = "📈",
            savagery = 3,
            category = RoastCategory.SHOPPING
        ),
        RoastTemplate(
            template = "{count} shopping transactions this month. Your wardrobe is full but your savings account is on a diet 👗",
            emoji = "👗",
            savagery = 3,
            category = RoastCategory.SHOPPING
        )
    )

    private val subscriptionRoasts = listOf(
        RoastTemplate(
            template = "You're paying for {count} subscriptions (₹{amount}/mo). That's a whole SIP right there. The irony: you barely use {equivalent_count} of them 🔄",
            emoji = "🔄",
            savagery = 3,
            category = RoastCategory.SUBSCRIPTIONS
        ),
        RoastTemplate(
            template = "Streaming services + music apps = ₹{amount}/mo. You're funding more content than you'll watch in a lifetime 📺",
            emoji = "📺",
            savagery = 2,
            category = RoastCategory.SUBSCRIPTIONS
        ),
        RoastTemplate(
            template = "₹{amount}/month on subscriptions. That's ₹{equivalent_count}/year. You could've taken a Goa trip with that. Just saying ✈️",
            emoji = "✈️",
            savagery = 3,
            category = RoastCategory.SUBSCRIPTIONS
        ),
        RoastTemplate(
            template = "Gym membership: ₹{amount}/month. Times visited: {count}. Cost per visit: ₹{equivalent_count}. At that rate, buy dumbbells 💪",
            emoji = "💪",
            savagery = 5,
            category = RoastCategory.SUBSCRIPTIONS
        ),
        RoastTemplate(
            template = "That meditation app subscription for ₹{amount}/month... the real zen is cancelling it and saving money 🧘",
            emoji = "🧘",
            savagery = 4,
            category = RoastCategory.SUBSCRIPTIONS
        )
    )

    private val transportationRoasts = listOf(
        RoastTemplate(
            template = "₹{amount} on cab apps this month. That's {equivalent_count} monthly metro passes. But sure, AC is important 🚗",
            emoji = "🚗",
            savagery = 3,
            category = RoastCategory.TRANSPORTATION
        ),
        RoastTemplate(
            template = "You took {count} cab rides spending ₹{amount}. At this rate, the EMI on an Activa would've been cheaper 🏍️",
            emoji = "🏍️",
            savagery = 4,
            category = RoastCategory.TRANSPORTATION
        ),
        RoastTemplate(
            template = "Ride spend: ₹{amount}. Auto-rickshaw uncles are sending you Diwali wishes this year 🛺",
            emoji = "🛺",
            savagery = 3,
            category = RoastCategory.TRANSPORTATION
        ),
        RoastTemplate(
            template = "₹{amount} on cabs. You know what's free? Walking. Also, cardio. Two birds, one stone 🚶",
            emoji = "🚶",
            savagery = 2,
            category = RoastCategory.TRANSPORTATION
        )
    )

    private val lateNightRoasts = listOf(
        RoastTemplate(
            template = "You placed {count} orders after midnight. The delivery person knows your PJs better than your colleagues know your formals 🌙",
            emoji = "🌙",
            savagery = 4,
            category = RoastCategory.LATE_NIGHT
        ),
        RoastTemplate(
            template = "Late-night spending: ₹{amount}. Nothing good happens after midnight — especially to your bank balance 🕛",
            emoji = "🕛",
            savagery = 3,
            category = RoastCategory.LATE_NIGHT
        ),
        RoastTemplate(
            template = "₹{amount} spent between 11 PM and 3 AM. That's not snacking, that's a full-blown nocturnal financial event 🦇",
            emoji = "🦇",
            savagery = 4,
            category = RoastCategory.LATE_NIGHT
        ),
        RoastTemplate(
            template = "Your 2 AM self spent ₹{amount} this month. Morning-you needs to have a serious talk with night-you ☀️🌙",
            emoji = "☀️",
            savagery = 3,
            category = RoastCategory.LATE_NIGHT
        ),
        RoastTemplate(
            template = "Average order time: {time}. The only thing that should be working at that hour is your sleep cycle 😴",
            emoji = "😴",
            savagery = 3,
            category = RoastCategory.LATE_NIGHT
        )
    )

    private val weekendRoasts = listOf(
        RoastTemplate(
            template = "Weekend spending: ₹{amount} ({percent}% of your total). Weekdays are just you recovering financially from weekends 🎊",
            emoji = "🎊",
            savagery = 3,
            category = RoastCategory.WEEKEND
        ),
        RoastTemplate(
            template = "Fri + Sat + Sun = ₹{amount}. That's {equivalent_count}x your weekday average. The weekend warrior saga continues 🗡️",
            emoji = "🗡️",
            savagery = 3,
            category = RoastCategory.WEEKEND
        ),
        RoastTemplate(
            template = "You spend more on Saturday alone (₹{amount}) than Mon-Thu combined. Saturday said 'YOLO', your wallet said 'why tho' 💀",
            emoji = "💀",
            savagery = 4,
            category = RoastCategory.WEEKEND
        ),
        RoastTemplate(
            template = "Friday evening to Sunday night: {count} transactions, ₹{amount} spent. That's a transaction every {equivalent_count} hours. Impressive. Scary, but impressive 🫡",
            emoji = "🫡",
            savagery = 4,
            category = RoastCategory.WEEKEND
        )
    )

    private val overallSpendingRoasts = listOf(
        RoastTemplate(
            template = "Total monthly spend: ₹{amount}. If spending was cardio, you'd be an Olympic athlete 🏅",
            emoji = "🏅",
            savagery = 3,
            category = RoastCategory.OVERALL_SPENDING
        ),
        RoastTemplate(
            template = "You spent ₹{amount} this month. That's ₹{equivalent_count}/day. Every single day. Including the ones you 'didn't buy anything' 🙃",
            emoji = "🙃",
            savagery = 3,
            category = RoastCategory.OVERALL_SPENDING
        ),
        RoastTemplate(
            template = "Monthly spend up {percent}% from last month. At this growth rate, you'll outperform Nifty 50. Unfortunately, that's your expenses, not your portfolio 📉",
            emoji = "📉",
            savagery = 5,
            category = RoastCategory.OVERALL_SPENDING
        ),
        RoastTemplate(
            template = "Savings rate: {percent}%. The national average is 28%. You're... trying. We believe in you. Kind of. 🫂",
            emoji = "🫂",
            savagery = 4,
            category = RoastCategory.OVERALL_SPENDING
        ),
        RoastTemplate(
            template = "You made {count} transactions this month. That's {equivalent_count} per day. Your UPI app is more active than your social media 📱",
            emoji = "📱",
            savagery = 2,
            category = RoastCategory.OVERALL_SPENDING
        )
    )

    private val patternRoasts = listOf(
        RoastTemplate(
            template = "Salary credited on {days}th. By {equivalent_count}th, 60% was gone. The money speed-ran your account 💨",
            emoji = "💨",
            savagery = 4,
            category = RoastCategory.PATTERN
        ),
        RoastTemplate(
            template = "You've ordered from {merchant} {count} days in a row. That's not loyalty, that's dependency 🏆",
            emoji = "🏆",
            savagery = 3,
            category = RoastCategory.PATTERN
        ),
        RoastTemplate(
            template = "Your spending drops 70% in the last week of the month. Your wallet is basically running on fumes by day 25 ⛽",
            emoji = "⛽",
            savagery = 4,
            category = RoastCategory.PATTERN
        ),
        RoastTemplate(
            template = "Every Monday you spend {percent}% more than other days. Monday blues? More like Monday bills 💙",
            emoji = "💙",
            savagery = 2,
            category = RoastCategory.PATTERN
        ),
        RoastTemplate(
            template = "You have a spending spike every {days} days. Like clockwork. Your wallet has a menstrual cycle 📅",
            emoji = "📅",
            savagery = 5,
            category = RoastCategory.PATTERN
        )
    )

    private val comparisonRoasts = listOf(
        RoastTemplate(
            template = "You spend {equivalent_count}x the national average on {category}. You're not rich, you're just... generous with yourself 😅",
            emoji = "😅",
            savagery = 3,
            category = RoastCategory.COMPARISON
        ),
        RoastTemplate(
            template = "Your {category} spend of ₹{amount} is in the top {percent}% of Indian spenders. Congratulations? 🎉",
            emoji = "🎉",
            savagery = 3,
            category = RoastCategory.COMPARISON
        ),
        RoastTemplate(
            template = "You spent ₹{amount} on coffee/chai. An average Indian family's monthly tea budget is ₹{equivalent_count}. You're drinking for 4 people ☕",
            emoji = "☕",
            savagery = 4,
            category = RoastCategory.COMPARISON
        ),
        RoastTemplate(
            template = "Your food delivery bill equals {equivalent_count} months of groceries for a couple. The math is math-ing... against you 🧮",
            emoji = "🧮",
            savagery = 4,
            category = RoastCategory.COMPARISON
        )
    )

    private val savingsRoasts = listOf(
        RoastTemplate(
            template = "You saved ₹{amount} this month. That's... {equivalent_count} food delivery orders. Maybe save one more month and treat yourself? Or don't. 🤷",
            emoji = "🤷",
            savagery = 2,
            category = RoastCategory.SAVINGS
        ),
        RoastTemplate(
            template = "Savings this month: ₹{amount}. At this rate, you'll have ₹1 crore in only... {equivalent_count} years. Time flies! 🕐",
            emoji = "🕐",
            savagery = 5,
            category = RoastCategory.SAVINGS
        ),
        RoastTemplate(
            template = "If you'd invested your {merchant} spend (₹{amount}) monthly in Nifty 5 years ago, it'd be ₹{equivalent_count} today. But those biryanis tho 🍗",
            emoji = "🍗",
            savagery = 4,
            category = RoastCategory.SAVINGS
        ),
        RoastTemplate(
            template = "Your emergency fund can cover... {equivalent_count} days of your current lifestyle. That's not a fund, that's a suggestion 🆘",
            emoji = "🆘",
            savagery = 5,
            category = RoastCategory.SAVINGS
        )
    )

    private val impulseRoasts = listOf(
        RoastTemplate(
            template = "You made {count} purchases under ₹200 this month. Death by a thousand cuts — but make it UPI 🔪",
            emoji = "🔪",
            savagery = 3,
            category = RoastCategory.IMPULSE
        ),
        RoastTemplate(
            template = "{count} different merchants in one day. Was that shopping or a city tour? 🗺️",
            emoji = "🗺️",
            savagery = 2,
            category = RoastCategory.IMPULSE
        ),
        RoastTemplate(
            template = "You bought something within {equivalent_count} minutes of your last purchase. Your thumbs are speedrunning poverty 🏃",
            emoji = "🏃",
            savagery = 5,
            category = RoastCategory.IMPULSE
        ),
        RoastTemplate(
            template = "₹{amount} in transactions under ₹500 each. Individually? Nothing. Together? A whole SIP portfolio waving goodbye 👋",
            emoji = "👋",
            savagery = 4,
            category = RoastCategory.IMPULSE
        )
    )

    private val merchantSpecificRoasts = listOf(
        RoastTemplate(
            template = "₹{amount} at the fancy café. That's {equivalent_count} cutting chai from the tapri. Same caffeine, 95% less pretense ☕",
            emoji = "☕",
            savagery = 4,
            category = RoastCategory.MERCHANT_SPECIFIC
        ),
        RoastTemplate(
            template = "Quick delivery bill: ₹{amount}. The kirana store 2 minutes away is crying. And it doesn't charge delivery 🏪",
            emoji = "🏪",
            savagery = 3,
            category = RoastCategory.MERCHANT_SPECIFIC
        ),
        RoastTemplate(
            template = "₹{amount} on {merchant}. That's {equivalent_count} months of a SIP that would make you actually rich someday 💰",
            emoji = "💰",
            savagery = 3,
            category = RoastCategory.MERCHANT_SPECIFIC
        ),
        RoastTemplate(
            template = "You've spent ₹{amount} at {merchant}. They should name a table after you. Or at least give you a loyalty discount 🪑",
            emoji = "🪑",
            savagery = 2,
            category = RoastCategory.MERCHANT_SPECIFIC
        ),
        RoastTemplate(
            template = "₹{amount} on quick delivery for stuff you could've picked up on your way home. Convenience has a price: ₹{equivalent_count} in delivery fees alone 🛵",
            emoji = "🛵",
            savagery = 3,
            category = RoastCategory.MERCHANT_SPECIFIC
        )
    )

    private data class RoastTemplate(
        val template: String,
        val emoji: String,
        val savagery: Int,
        val category: RoastCategory
    )

    // Combined template pool
    private val allTemplates: List<RoastTemplate> by lazy {
        foodDeliveryRoasts + shoppingRoasts + subscriptionRoasts +
                transportationRoasts + lateNightRoasts + weekendRoasts +
                overallSpendingRoasts + patternRoasts + comparisonRoasts +
                savingsRoasts + impulseRoasts + merchantSpecificRoasts
    }

    // ============================================================
    // Roast Generation Engine
    // ============================================================

    /**
     * Generates the weekly roast set (3-5 roasts based on the week's spending).
     *
     * @param transactions This week's transactions
     * @param monthlyIncome User's monthly income
     * @param maxRoasts Maximum number of roasts to generate (default: 5)
     * @param maxSavagery Maximum savagery level (1-5, default: 4)
     * @return List of personalized roasts
     */
    fun generateWeeklyRoasts(
        transactions: List<Transaction>,
        monthlyIncome: Double,
        maxRoasts: Int = 5,
        maxSavagery: Int = 4
    ): List<Roast> {
        if (transactions.isEmpty()) return listOf(getEmptyWeekRoast())

        val debitTxns = transactions.filter { !it.isCredit }
        val roasts = mutableListOf<Roast>()

        // Analyze spending for template population
        val totalSpent = debitTxns.sumOf { it.amount }
        val merchantSpend = debitTxns.groupBy { it.merchant.lowercase().trim() }
            .mapValues { it.value.sumOf { txn -> txn.amount } to it.value.size }
        val categorySpend = debitTxns.groupBy { it.category.lowercase() }
            .mapValues { it.value.sumOf { txn -> txn.amount } to it.value.size }

        // 1. Top merchant roast
        val topMerchant = merchantSpend.maxByOrNull { it.value.first }
        if (topMerchant != null && topMerchant.value.first > 500) {
            val roast = generateMerchantRoast(topMerchant.key, topMerchant.value.first, topMerchant.value.second)
            if (roast != null) roasts.add(roast)
        }

        // 2. Food delivery roast
        val foodSpend = categorySpend["food_delivery"]
        if (foodSpend != null && foodSpend.first > 1000) {
            val roast = generateCategoryRoast(
                RoastCategory.FOOD_DELIVERY,
                foodSpend.first,
                foodSpend.second,
                debitTxns.filter { it.category.lowercase() == "food_delivery" }
            )
            if (roast != null) roasts.add(roast)
        }

        // 3. Late-night roast
        val lateNightTxns = debitTxns.filter { it.timestamp.hour >= 22 || it.timestamp.hour < 4 }
        if (lateNightTxns.size >= 3) {
            val roast = generateLateNightRoast(lateNightTxns)
            if (roast != null) roasts.add(roast)
        }

        // 4. Weekend roast
        val weekendTxns = debitTxns.filter {
            it.timestamp.dayOfWeek in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        val weekendPercent = if (totalSpent > 0) (weekendTxns.sumOf { it.amount } / totalSpent * 100).roundToInt() else 0
        if (weekendPercent > 55) {
            val roast = generateWeekendRoast(weekendTxns, weekendPercent, totalSpent)
            if (roast != null) roasts.add(roast)
        }

        // 5. Overall spending roast
        val overallRoast = generateOverallRoast(debitTxns, totalSpent, monthlyIncome)
        if (overallRoast != null) roasts.add(overallRoast)

        // 6. Impulse buying roast
        val smallTxns = debitTxns.filter { it.amount < 500 }
        if (smallTxns.size > debitTxns.size * 0.6) {
            val roast = generateImpulseRoast(smallTxns, debitTxns.size)
            if (roast != null) roasts.add(roast)
        }

        // 7. Savings roast
        val savingsRate = ((monthlyIncome - totalSpent * 4.33) / monthlyIncome * 100).roundToInt()
        if (savingsRate < 20) {
            val roast = generateSavingsRoast(totalSpent, monthlyIncome, savingsRate)
            if (roast != null) roasts.add(roast)
        }

        // Filter by savagery and limit
        return roasts
            .filter { it.savageryLevel <= maxSavagery }
            .distinctBy { it.category }
            .take(maxRoasts)
    }

    /**
     * Generates a single daily roast (lighter than weekly).
     */
    fun generateDailyRoast(
        todayTransactions: List<Transaction>,
        monthlyIncome: Double
    ): Roast? {
        val debitTxns = todayTransactions.filter { !it.isCredit }
        if (debitTxns.isEmpty()) return null

        val totalToday = debitTxns.sumOf { it.amount }
        val dailyBudget = monthlyIncome / 30.0

        return when {
            totalToday > dailyBudget * 3 -> Roast(
                text = "₹${formatAmount(totalToday)} today. That's ${(totalToday / dailyBudget).roundToInt()} days' worth of budget in ONE day. Speed run! 🏃💨",
                emoji = "🏃",
                category = RoastCategory.OVERALL_SPENDING,
                savageryLevel = 4,
                relatedAmount = totalToday
            )
            totalToday > dailyBudget * 2 -> Roast(
                text = "Double-budget day alert! ₹${formatAmount(totalToday)} spent. Tomorrow's budget is now on a diet 🥗",
                emoji = "⚠️",
                category = RoastCategory.OVERALL_SPENDING,
                savageryLevel = 3,
                relatedAmount = totalToday
            )
            totalToday > dailyBudget * 1.5 -> Roast(
                text = "₹${formatAmount(totalToday)} today. Slightly over budget but who's counting? Oh wait, we are 📊",
                emoji = "📊",
                category = RoastCategory.OVERALL_SPENDING,
                savageryLevel = 2,
                relatedAmount = totalToday
            )
            else -> null // Don't roast good behavior
        }
    }

    // ============================================================
    // Category-Specific Roast Generators
    // ============================================================

    private fun generateMerchantRoast(merchant: String, amount: Double, count: Int): Roast? {
        val templates = merchantSpecificRoasts.filter { it.savagery <= 4 }
        if (templates.isEmpty()) return null

        val template = templates.random()
        val equivalentCount = when {
            merchant.contains("starbucks", true) -> (amount / 10).roundToInt() // vs cutting chai
            merchant.contains("swiggy", true) || merchant.contains("zomato", true) ->
                (amount / 80).roundToInt() // vs home thali
            else -> (amount / 500).roundToInt() // vs SIP installments
        }

        val text = template.template
            .replace("{amount}", formatAmount(amount))
            .replace("{merchant}", merchant.capitalize())
            .replace("{count}", count.toString())
            .replace("{equivalent_count}", equivalentCount.toString())

        return Roast(
            text = text,
            emoji = template.emoji,
            category = template.category,
            savageryLevel = template.savagery,
            relatedAmount = amount
        )
    }

    private fun generateCategoryRoast(
        category: RoastCategory,
        amount: Double,
        count: Int,
        transactions: List<Transaction>
    ): Roast? {
        val templates = when (category) {
            RoastCategory.FOOD_DELIVERY -> foodDeliveryRoasts
            RoastCategory.SHOPPING -> shoppingRoasts
            RoastCategory.TRANSPORTATION -> transportationRoasts
            else -> return null
        }

        val template = templates.random()
        val topMerchant = transactions.groupBy { it.merchant.lowercase() }
            .maxByOrNull { it.value.sumOf { txn -> txn.amount } }?.key ?: "that app"
        val days = transactions.map { it.timestamp.toLocalDate() }.distinct().size
        val orderFrequency = if (count > 0) (30.0 / count).roundToInt() else 0

        val text = template.template
            .replace("{amount}", formatAmount(amount))
            .replace("{merchant}", topMerchant.capitalize())
            .replace("{count}", count.toString())
            .replace("{days}", orderFrequency.toString())
            .replace("{equivalent_count}", (amount / 80).roundToInt().toString()) // vs dal chawal

        return Roast(
            text = text,
            emoji = template.emoji,
            category = category,
            savageryLevel = template.savagery,
            relatedAmount = amount
        )
    }

    private fun generateLateNightRoast(lateNightTxns: List<Transaction>): Roast? {
        val template = lateNightRoasts.random()
        val amount = lateNightTxns.sumOf { it.amount }
        val count = lateNightTxns.size
        val avgHour = lateNightTxns.map {
            if (it.timestamp.hour < 4) it.timestamp.hour + 24 else it.timestamp.hour
        }.average().roundToInt().let { if (it >= 24) it - 24 else it }
        val timeStr = "${avgHour}:${(lateNightTxns.map { it.timestamp.minute }.average().roundToInt()).toString().padStart(2, '0')}"

        val text = template.template
            .replace("{amount}", formatAmount(amount))
            .replace("{count}", count.toString())
            .replace("{time}", timeStr)
            .replace("{equivalent_count}", (amount / 150).roundToInt().toString())

        return Roast(
            text = text,
            emoji = template.emoji,
            category = RoastCategory.LATE_NIGHT,
            savageryLevel = template.savagery,
            relatedAmount = amount
        )
    }

    private fun generateWeekendRoast(
        weekendTxns: List<Transaction>,
        weekendPercent: Int,
        totalSpent: Double
    ): Roast? {
        val template = weekendRoasts.random()
        val weekendAmount = weekendTxns.sumOf { it.amount }
        val weekdayAmount = totalSpent - weekendAmount
        val weekdayDays = 4 // Mon-Thu in a week
        val weekdayAvg = weekdayAmount / weekdayDays
        val weekendMultiple = if (weekdayAvg > 0) (weekendAmount / 3 / weekdayAvg).roundToInt() else 1
        val count = weekendTxns.size
        val hoursPerTxn = if (count > 0) (72.0 / count).roundToInt() else 0

        val text = template.template
            .replace("{amount}", formatAmount(weekendAmount))
            .replace("{percent}", weekendPercent.toString())
            .replace("{count}", count.toString())
            .replace("{equivalent_count}", when {
                template.template.contains("average") -> weekendMultiple.toString()
                template.template.contains("hours") -> hoursPerTxn.toString()
                else -> weekendMultiple.toString()
            })

        return Roast(
            text = text,
            emoji = template.emoji,
            category = RoastCategory.WEEKEND,
            savageryLevel = template.savagery,
            relatedAmount = weekendAmount
        )
    }

    private fun generateOverallRoast(
        transactions: List<Transaction>,
        totalSpent: Double,
        monthlyIncome: Double
    ): Roast? {
        val template = overallSpendingRoasts.random()
        val count = transactions.size
        val days = transactions.map { it.timestamp.toLocalDate() }.distinct().size.coerceAtLeast(1)
        val dailyAvg = totalSpent / days
        val txPerDay = (count.toDouble() / days).roundToInt()
        val monthlyProjected = totalSpent / days * 30
        val savingsRate = ((monthlyIncome - monthlyProjected) / monthlyIncome * 100).roundToInt()

        // Calculate month-over-month growth (approximation)
        val percentChange = 15 // placeholder, would need last month data

        val text = template.template
            .replace("{amount}", formatAmount(totalSpent))
            .replace("{count}", count.toString())
            .replace("{equivalent_count}", when {
                template.template.contains("per day") -> formatAmount(dailyAvg)
                template.template.contains("per day") -> txPerDay.toString()
                else -> txPerDay.toString()
            })
            .replace("{percent}", savingsRate.toString())

        return Roast(
            text = text,
            emoji = template.emoji,
            category = RoastCategory.OVERALL_SPENDING,
            savageryLevel = template.savagery,
            relatedAmount = totalSpent
        )
    }

    private fun generateImpulseRoast(smallTxns: List<Transaction>, totalCount: Int): Roast? {
        val template = impulseRoasts.random()
        val amount = smallTxns.sumOf { it.amount }
        val count = smallTxns.size
        val merchantsPerDay = smallTxns.groupBy { it.timestamp.toLocalDate() }
            .mapValues { it.value.map { txn -> txn.merchant }.distinct().size }
            .values.maxOrNull() ?: 1

        // Find minimum gap between transactions
        val sortedTxns = smallTxns.sortedBy { it.timestamp }
        val minGapMinutes = if (sortedTxns.size >= 2) {
            sortedTxns.zipWithNext { a, b ->
                java.time.Duration.between(a.timestamp, b.timestamp).toMinutes()
            }.filter { it > 0 }.minOrNull() ?: 60
        } else 60

        val text = template.template
            .replace("{amount}", formatAmount(amount))
            .replace("{count}", count.toString())
            .replace("{equivalent_count}", minGapMinutes.toString())

        return Roast(
            text = text,
            emoji = template.emoji,
            category = RoastCategory.IMPULSE,
            savageryLevel = template.savagery,
            relatedAmount = amount
        )
    }

    private fun generateSavingsRoast(weeklySpent: Double, monthlyIncome: Double, savingsRate: Int): Roast? {
        val template = savingsRoasts.random()
        val monthlySaved = monthlyIncome * savingsRate / 100
        val yearsToOneCrore = if (monthlySaved > 0) {
            (10000000.0 / (monthlySaved * 12)).roundToInt()
        } else 999

        val text = template.template
            .replace("{amount}", formatAmount(monthlySaved))
            .replace("{count}", savingsRate.toString())
            .replace("{equivalent_count}", yearsToOneCrore.toString())
            .replace("{merchant}", "Food Delivery")
            .replace("{percent}", savingsRate.toString())

        return Roast(
            text = text,
            emoji = template.emoji,
            category = RoastCategory.SAVINGS,
            savageryLevel = template.savagery,
            relatedAmount = monthlySaved,
            actionSuggestion = "Try saving just ₹100 more per day"
        )
    }

    // ============================================================
    // Utility Functions
    // ============================================================

    private fun getEmptyWeekRoast(): Roast {
        return Roast(
            text = "No transactions this week. Either you achieved financial nirvana or your phone died. We're guessing option B 📵",
            emoji = "📵",
            category = RoastCategory.OVERALL_SPENDING,
            savageryLevel = 2
        )
    }

    private fun formatAmount(amount: Double): String {
        return when {
            amount >= 100000 -> "${(amount / 100000).let { "%.1f".format(it) }}L"
            amount >= 1000 -> "${(amount / 1000).let { "%.1f".format(it) }}K"
            else -> "%.0f".format(amount)
        }
    }

    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    /**
     * Adjusts roast savagery based on user preference.
     * Some users want brutal honesty, others prefer gentle nudges.
     */
    fun adjustSavagery(roasts: List<Roast>, userPreference: SavageryPreference): List<Roast> {
        val maxLevel = when (userPreference) {
            SavageryPreference.GENTLE -> 2
            SavageryPreference.MODERATE -> 3
            SavageryPreference.SAVAGE -> 4
            SavageryPreference.BRUTAL -> 5
        }
        return roasts.filter { it.savageryLevel <= maxLevel }
    }

    enum class SavageryPreference {
        GENTLE, MODERATE, SAVAGE, BRUTAL
    }

    companion object {
        private var roastCounter = 0L
    }
}
