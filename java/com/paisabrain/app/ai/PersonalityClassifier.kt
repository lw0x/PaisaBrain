package com.paisabrain.app.ai

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * PersonalityClassifier - Assigns a "Money Personality" to users based on spending patterns.
 *
 * Analyzes 30-90 days of transaction history to classify users into one of
 * seven distinct money personality archetypes. Each personality includes a
 * confidence score, detailed traits, and personalized tips.
 *
 * Personalities:
 * 1. The Weekend Warrior - Lives for Fri-Sun spending sprees
 * 2. The Midnight Snacker - Late-night ordering champion
 * 3. The Impulse King/Queen - Many small, varied purchases
 * 4. The Subscription Collector - Digital service hoarder
 * 5. The Steady Eddie - Consistent, predictable spender
 * 6. The Feast-or-Famine - Salary-day spike, pre-payday drought
 * 7. The Minimalist - Below-average spending, high savings
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
class PersonalityClassifier {

    // ============================================================
    // Data Models
    // ============================================================

    /**
     * Represents a classified money personality with all its attributes.
     */
    data class MoneyPersonality(
        val type: PersonalityType,
        val title: String,
        val emoji: String,
        val tagline: String,
        val description: String,
        val confidence: Double, // 0.0 - 1.0
        val traits: List<String>,
        val tips: List<String>,
        val funFact: String,
        val spiritAnimal: String,
        val bollywoodCharacter: String,
        val secondaryType: PersonalityType? = null
    )

    enum class PersonalityType {
        WEEKEND_WARRIOR,
        MIDNIGHT_SNACKER,
        IMPULSE_ROYAL,
        SUBSCRIPTION_COLLECTOR,
        STEADY_EDDIE,
        FEAST_OR_FAMINE,
        MINIMALIST
    }

    data class PersonalityScore(
        val type: PersonalityType,
        val score: Double // 0.0 - 1.0
    )

    /**
     * Transaction data used for classification.
     * Mirrors InsightEngine.Transaction but decoupled for modularity.
     */
    data class Transaction(
        val amount: Double,
        val merchant: String,
        val category: String,
        val timestamp: LocalDateTime,
        val isCredit: Boolean = false
    )

    // ============================================================
    // Personality Definitions
    // ============================================================

    private val personalityDefinitions = mapOf(
        PersonalityType.WEEKEND_WARRIOR to PersonalityDefinition(
            title = "The Weekend Warrior",
            emoji = "🎉",
            tagline = "TGIF is not just a mood, it's a financial event",
            description = "You live for the weekend! Fri-Sun is when your wallet sees real action. " +
                    "Brunches, parties, shopping — Monday through Thursday is just waiting for the fun to begin.",
            traits = listOf(
                "72%+ of 'fun' spending happens Fri-Sun",
                "Restaurant visits spike 3x on weekends",
                "Shopping carts fill up on Saturday afternoons",
                "Entertainment spending concentrated in 3 days",
                "Weekday spending is bare minimum survival mode"
            ),
            tips = listOf(
                "Set a 'Weekend Fund' — transfer a fixed amount every Friday",
                "Try one free weekend activity per month (hikes, home movie nights)",
                "Use the 24-hour rule: add to cart Saturday, buy Sunday only if still wanted",
                "Challenge: one 'Zero-Spend Sunday' per month",
                "Pre-cook/meal-prep on Thursday to avoid weekend restaurant temptation"
            ),
            funFact = "Weekend Warriors spend 40% more per transaction on weekends vs weekdays",
            spiritAnimal = "Golden Retriever — boring during the week, EXCITED on weekends! 🐕",
            bollywoodCharacter = "Bunny from YJHD — living for the next adventure"
        ),
        PersonalityType.MIDNIGHT_SNACKER to PersonalityDefinition(
            title = "The Midnight Snacker",
            emoji = "🌙",
            tagline = "When the world sleeps, your UPI works overtime",
            description = "The night is young and so is your appetite! 40%+ of your food orders happen after 10 PM. " +
                    "Food delivery riders know your address by heart. " +
                    "Your phone screen time after midnight? Mostly food delivery apps.",
            traits = listOf(
                "40%+ food orders placed after 10 PM",
                "Average late-night order value is 30% higher than daytime",
                "Peak ordering time: 11 PM - 1 AM",
                "Frequently orders from the same 3-4 late-night restaurants",
                "Weekend late-night orders are even more frequent"
            ),
            tips = listOf(
                "Stock midnight snack alternatives at home (costs 80% less)",
                "Set a 'kitchen closes at 10 PM' rule for ordering apps",
                "Move food apps to a folder that requires extra taps after 10 PM",
                "Try: for every late-night order, transfer the same amount to savings",
                "Meal prep something microwaveable for late-night cravings"
            ),
            funFact = "Midnight Snackers spend ₹3,000-5,000/month more on food than average",
            spiritAnimal = "Owl — nocturnal, wise... except about spending 🦉",
            bollywoodCharacter = "Circuit from Munna Bhai — always ready for a midnight run"
        ),
        PersonalityType.IMPULSE_ROYAL to PersonalityDefinition(
            title = "The Impulse King/Queen",
            emoji = "👑",
            tagline = "See it. Like it. Buy it. Regret it. Repeat.",
            description = "Your spending is a beautiful chaos of many small purchases scattered throughout the day. " +
                    "Online shopping 'Add to Cart' is basically a reflex. Flash sales are your kryptonite. " +
                    "High number of transactions, high variance, maximum spontaneity.",
            traits = listOf(
                "Transaction count is 2x+ above average",
                "High spending variance (std dev > 50% of mean)",
                "Many small purchases (₹100-500 range)",
                "Multiple merchants in a single day",
                "Shopping category has highest transaction count (not necessarily amount)"
            ),
            tips = listOf(
                "Implement the 48-hour rule: wait 2 days before any non-essential purchase",
                "Unsubscribe from all sale/deal notification emails",
                "Remove saved cards from shopping apps (friction = fewer impulse buys)",
                "Set a weekly 'impulse budget' — once it's gone, it's gone",
                "Try a no-spend challenge: pick 3 days/week where you buy nothing non-essential"
            ),
            funFact = "Impulse buyers average 3.2x more transactions than Steady Eddies",
            spiritAnimal = "Magpie — attracted to every shiny object ✨",
            bollywoodCharacter = "Geet from Jab We Met — spontaneous, unstoppable energy"
        ),
        PersonalityType.SUBSCRIPTION_COLLECTOR to PersonalityDefinition(
            title = "The Subscription Collector",
            emoji = "🔄",
            tagline = "One more subscription won't hurt... right?",
            description = "Streaming services, music apps, video platforms, gym membership, " +
                    "that meditation app you used once... You've got them all! " +
                    "5+ recurring charges that silently drain your account every month.",
            traits = listOf(
                "5+ active recurring subscriptions detected",
                "Subscription spend exceeds ₹2,000/month",
                "At least 2 subscriptions overlap in purpose (e.g., multiple streaming)",
                "Some subscriptions haven't been actively used in 30+ days",
                "Monthly subscription cost has grown over time"
            ),
            tips = listOf(
                "Audit: List all subs and rate 1-10 how much you actually use each",
                "Cancel anything rated below 5. You can always resubscribe later",
                "Share family plans where possible (streaming, music, video platforms)",
                "Set calendar reminders before free trial ends",
                "Try: cancel everything for 1 month. Resubscribe only to what you genuinely miss"
            ),
            funFact = "The average Subscription Collector has 2-3 'ghost subscriptions' they forgot about",
            spiritAnimal = "Hamster — collecting and hoarding, even digitally 🐹",
            bollywoodCharacter = "Poo from K3G — only the best of everything, darling"
        ),
        PersonalityType.STEADY_EDDIE to PersonalityDefinition(
            title = "The Steady Eddie",
            emoji = "⚖️",
            tagline = "Boring? Maybe. Financially secure? Definitely.",
            description = "Your spending is as predictable as a Swiss watch. Low variance, consistent daily amounts, " +
                    "no wild spikes or droughts. Your bank statement is a flat line — and that's a compliment. " +
                    "Budget apps love users like you.",
            traits = listOf(
                "Daily spending variance is below 30%",
                "No single day exceeds 3x the daily average",
                "Consistent merchant patterns (same groceries, same routes)",
                "Spending distributed evenly across the month",
                "Rarely exceeds budget in any category"
            ),
            tips = listOf(
                "You're already great at spending control! Focus on growing income",
                "Channel your consistency into SIP investments (you'll love the discipline)",
                "Allow yourself a small 'spontaneity fund' — it's okay to splurge sometimes!",
                "Your steady habits make you ideal for aggressive long-term investing",
                "Consider automating investments — you clearly don't need the money sitting around"
            ),
            funFact = "Steady Eddies have the highest average savings rate at 35%+",
            spiritAnimal = "Tortoise — slow, steady, wins the financial race 🐢",
            bollywoodCharacter = "Rajesh from Hera Pheri — calculated, measured, sensible"
        ),
        PersonalityType.FEAST_OR_FAMINE to PersonalityDefinition(
            title = "The Feast-or-Famine",
            emoji = "🎢",
            tagline = "Salary day: king. Day 25: instant noodles.",
            description = "Your spending graph looks like a heart rate monitor — huge spike after salary day, " +
                    "followed by a slow descent into 'I'll just eat at home' territory. " +
                    "The first week is champagne dreams, the last week is budget beans.",
            traits = listOf(
                "First 7 days post-salary: 50%+ of monthly spending",
                "Last 7 days before salary: spending drops 60%+",
                "Large single-day purchases within 48 hours of salary credit",
                "Spending velocity decreases linearly through the month",
                "EMIs and bills cluster around salary date"
            ),
            tips = listOf(
                "Auto-transfer 40% to savings THE MOMENT salary hits (before you spend)",
                "Split salary into 4 weekly budgets — transfer weekly, not monthly",
                "Delay big purchases by 7 days post-salary (the 'cooling off' period)",
                "Set up a 'last week emergency fund' — ₹3K you can't touch until day 25",
                "Pre-pay bills on salary day so they don't haunt you later"
            ),
            funFact = "Feast-or-Famine spenders often have the same income as Steady Eddies but 20% less savings",
            spiritAnimal = "Bear — hibernates (financially) before the next feast 🐻",
            bollywoodCharacter = "Raju from 3 Idiots — 'Aaj mere paas paisa hai!'"
        ),
        PersonalityType.MINIMALIST to PersonalityDefinition(
            title = "The Minimalist",
            emoji = "🧘",
            tagline = "Less spending, more living. Your bank account agrees.",
            description = "You spend well below average and save like a pro. Whether by choice or circumstance, " +
                    "your lifestyle is lean. Few transactions, low totals, high savings rate. " +
                    "Marie Kondo would approve of your finances.",
            traits = listOf(
                "Total monthly spending below 50% of income",
                "Savings rate above 40%",
                "Transaction count below average",
                "Entertainment/shopping spending is minimal",
                "Majority of spend is on essentials (rent, groceries, bills)"
            ),
            tips = listOf(
                "You're saving great! Make sure your money is working hard (not in savings a/c)",
                "Consider: index funds, PPF, or NPS for your surplus",
                "It's okay to enjoy your money too — budget a 'guilt-free splurge' category",
                "Your discipline is rare — consider if you're depriving yourself unnecessarily",
                "Teach a friend! Your habits could help someone struggling with spending"
            ),
            funFact = "Minimalists reach financial independence 10 years earlier on average",
            spiritAnimal = "Cat — independent, low maintenance, secretly wealthy 🐱",
            bollywoodCharacter = "Rancho from 3 Idiots — 'Life mein settle hone ke liye paison ki zaroorat nahi'"
        )
    )

    private data class PersonalityDefinition(
        val title: String,
        val emoji: String,
        val tagline: String,
        val description: String,
        val traits: List<String>,
        val tips: List<String>,
        val funFact: String,
        val spiritAnimal: String,
        val bollywoodCharacter: String
    )

    // ============================================================
    // Classification Engine
    // ============================================================

    /**
     * Classifies the user's money personality based on transaction history.
     *
     * @param transactions List of transactions (minimum 30 days recommended)
     * @param monthlyIncome User's monthly income for savings rate calculation
     * @return MoneyPersonality with type, confidence, traits, and tips
     */
    fun classify(
        transactions: List<Transaction>,
        monthlyIncome: Double
    ): MoneyPersonality {
        if (transactions.isEmpty()) {
            return getDefaultPersonality()
        }

        val debitTxns = transactions.filter { !it.isCredit }
        val scores = calculateAllScores(debitTxns, monthlyIncome)

        // Primary personality = highest score
        val sortedScores = scores.sortedByDescending { it.score }
        val primary = sortedScores.first()
        val secondary = sortedScores.getOrNull(1)

        val definition = personalityDefinitions[primary.type]!!

        return MoneyPersonality(
            type = primary.type,
            title = if (primary.type == PersonalityType.IMPULSE_ROYAL) {
                detectGender(transactions)?.let { gender ->
                    if (gender == "female") "The Impulse Queen" else "The Impulse King"
                } ?: "The Impulse King/Queen"
            } else {
                definition.title
            },
            emoji = definition.emoji,
            tagline = definition.tagline,
            description = definition.description,
            confidence = primary.score,
            traits = definition.traits,
            tips = definition.tips,
            funFact = definition.funFact,
            spiritAnimal = definition.spiritAnimal,
            bollywoodCharacter = definition.bollywoodCharacter,
            secondaryType = if (secondary != null && secondary.score > 0.3) secondary.type else null
        )
    }

    /**
     * Returns all personality scores for displaying a radar/distribution chart.
     */
    fun getPersonalityDistribution(
        transactions: List<Transaction>,
        monthlyIncome: Double
    ): List<PersonalityScore> {
        if (transactions.isEmpty()) return emptyList()
        val debitTxns = transactions.filter { !it.isCredit }
        return calculateAllScores(debitTxns, monthlyIncome).sortedByDescending { it.score }
    }

    // ============================================================
    // Scoring Functions
    // ============================================================

    private fun calculateAllScores(
        transactions: List<Transaction>,
        monthlyIncome: Double
    ): List<PersonalityScore> {
        return listOf(
            PersonalityScore(PersonalityType.WEEKEND_WARRIOR, scoreWeekendWarrior(transactions)),
            PersonalityScore(PersonalityType.MIDNIGHT_SNACKER, scoreMidnightSnacker(transactions)),
            PersonalityScore(PersonalityType.IMPULSE_ROYAL, scoreImpulseRoyal(transactions)),
            PersonalityScore(PersonalityType.SUBSCRIPTION_COLLECTOR, scoreSubscriptionCollector(transactions)),
            PersonalityScore(PersonalityType.STEADY_EDDIE, scoreSteadyEddie(transactions)),
            PersonalityScore(PersonalityType.FEAST_OR_FAMINE, scoreFeastOrFamine(transactions)),
            PersonalityScore(PersonalityType.MINIMALIST, scoreMinimalist(transactions, monthlyIncome))
        )
    }

    /**
     * Weekend Warrior Score:
     * - % of fun category spending on Fri-Sun (weight: 0.5)
     * - Weekend transaction count ratio (weight: 0.3)
     * - Weekend average > weekday average (weight: 0.2)
     */
    private fun scoreWeekendWarrior(transactions: List<Transaction>): Double {
        val funCategories = setOf("food_delivery", "entertainment", "shopping", "travel", "personal_care")

        val funTxns = transactions.filter { it.category.lowercase() in funCategories }
        if (funTxns.isEmpty()) return 0.0

        val weekendFun = funTxns.filter {
            it.timestamp.dayOfWeek in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }

        // Score 1: Percentage of fun spending on weekends (expected: 43% for 3/7 days)
        val weekendFunRatio = weekendFun.sumOf { it.amount } / funTxns.sumOf { it.amount }
        val score1 = ((weekendFunRatio - 0.43) / 0.57).coerceIn(0.0, 1.0)

        // Score 2: Transaction count ratio
        val weekendAll = transactions.filter {
            it.timestamp.dayOfWeek in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        val weekendCountRatio = weekendAll.size.toDouble() / transactions.size
        val score2 = ((weekendCountRatio - 0.43) / 0.57).coerceIn(0.0, 1.0)

        // Score 3: Weekend average amount vs weekday
        val weekendAvg = if (weekendAll.isNotEmpty()) weekendAll.map { it.amount }.average() else 0.0
        val weekdayTxns = transactions.filter {
            it.timestamp.dayOfWeek !in listOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        val weekdayAvg = if (weekdayTxns.isNotEmpty()) weekdayTxns.map { it.amount }.average() else 1.0
        val score3 = ((weekendAvg / weekdayAvg - 1.0) / 2.0).coerceIn(0.0, 1.0)

        return (score1 * 0.5 + score2 * 0.3 + score3 * 0.2)
    }

    /**
     * Midnight Snacker Score:
     * - % of food orders after 10 PM (weight: 0.6)
     * - Frequency of late-night ordering (weight: 0.25)
     * - Late-night order value vs daytime (weight: 0.15)
     */
    private fun scoreMidnightSnacker(transactions: List<Transaction>): Double {
        val foodCategories = setOf("food_delivery", "groceries")
        val foodTxns = transactions.filter { it.category.lowercase() in foodCategories }
        if (foodTxns.size < 5) return 0.0

        val lateNightFood = foodTxns.filter {
            it.timestamp.hour >= 22 || it.timestamp.hour < 4
        }

        // Score 1: Percentage of food spending after 10 PM
        val lateRatio = lateNightFood.sumOf { it.amount } / foodTxns.sumOf { it.amount }
        val score1 = (lateRatio / 0.6).coerceIn(0.0, 1.0) // 60%+ = max score

        // Score 2: Frequency — how many days have late-night orders
        val daysWithLateOrders = lateNightFood.map { it.timestamp.toLocalDate() }.distinct().size
        val totalDays = transactions.map { it.timestamp.toLocalDate() }.distinct().size
        val frequencyRatio = daysWithLateOrders.toDouble() / totalDays.coerceAtLeast(1)
        val score2 = (frequencyRatio / 0.3).coerceIn(0.0, 1.0) // 30%+ days = max

        // Score 3: Late-night orders are higher value
        val lateAvg = if (lateNightFood.isNotEmpty()) lateNightFood.map { it.amount }.average() else 0.0
        val dayFood = foodTxns.filter { it.timestamp.hour in 6..21 }
        val dayAvg = if (dayFood.isNotEmpty()) dayFood.map { it.amount }.average() else 1.0
        val score3 = ((lateAvg / dayAvg - 1.0) / 1.0).coerceIn(0.0, 1.0)

        return (score1 * 0.6 + score2 * 0.25 + score3 * 0.15)
    }

    /**
     * Impulse King/Queen Score:
     * - High transaction count (weight: 0.3)
     * - High spending variance / coefficient of variation (weight: 0.3)
     * - Many small purchases (weight: 0.2)
     * - Multiple merchants per day (weight: 0.2)
     */
    private fun scoreImpulseRoyal(transactions: List<Transaction>): Double {
        if (transactions.size < 10) return 0.0

        val amounts = transactions.map { it.amount }
        val days = transactions.map { it.timestamp.toLocalDate() }.distinct().size

        // Score 1: Transaction density (transactions per day)
        val txPerDay = transactions.size.toDouble() / days.coerceAtLeast(1)
        val score1 = ((txPerDay - 2.0) / 6.0).coerceIn(0.0, 1.0) // 8+ tx/day = max

        // Score 2: Coefficient of variation (std dev / mean)
        val mean = amounts.average()
        val variance = amounts.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean
        val score2 = (cv / 2.0).coerceIn(0.0, 1.0) // CV of 2+ = max

        // Score 3: Percentage of small purchases (< ₹500)
        val smallPurchases = amounts.count { it < 500.0 }
        val smallRatio = smallPurchases.toDouble() / amounts.size
        val score3 = (smallRatio / 0.6).coerceIn(0.0, 1.0) // 60%+ small = max

        // Score 4: Merchant diversity per day
        val merchantsPerDay = transactions.groupBy { it.timestamp.toLocalDate() }
            .mapValues { it.value.map { txn -> txn.merchant.lowercase() }.distinct().size }
            .values.average()
        val score4 = ((merchantsPerDay - 1.5) / 3.5).coerceIn(0.0, 1.0) // 5+ merchants/day = max

        return (score1 * 0.3 + score2 * 0.3 + score3 * 0.2 + score4 * 0.2)
    }

    /**
     * Subscription Collector Score:
     * - Number of detected recurring charges (weight: 0.5)
     * - Total subscription spend vs income (weight: 0.3)
     * - Overlap detection (multiple services same category) (weight: 0.2)
     */
    private fun scoreSubscriptionCollector(transactions: List<Transaction>): Double {
        // Detect recurring charges (same merchant, similar amount, monthly intervals)
        val merchantGroups = transactions.groupBy { it.merchant.lowercase().trim() }
        var recurringCount = 0
        var recurringTotal = 0.0

        for ((_, txns) in merchantGroups) {
            if (txns.size < 2) continue
            val sortedByDate = txns.sortedBy { it.timestamp }
            val amounts = sortedByDate.map { it.amount }

            // Check if amounts are consistent (within 15%)
            val amountMean = amounts.average()
            val amountConsistent = amounts.all { abs(it - amountMean) / amountMean < 0.15 }
            if (!amountConsistent) continue

            // Check if intervals are monthly-ish (25-35 days)
            val dates = sortedByDate.map { it.timestamp.toLocalDate() }
            val intervals = dates.zipWithNext { a, b ->
                java.time.temporal.ChronoUnit.DAYS.between(a, b)
            }
            val isMonthly = intervals.isNotEmpty() && intervals.all { it in 20..40 }

            if (isMonthly) {
                recurringCount++
                recurringTotal += amountMean
            }
        }

        // Score 1: Number of subscriptions
        val score1 = (recurringCount.toDouble() / 8.0).coerceIn(0.0, 1.0) // 8+ = max

        // Score 2: Subscription spend ratio (of total spending)
        val totalSpend = transactions.sumOf { it.amount }
        val subRatio = if (totalSpend > 0) (recurringTotal * (transactions.size / 30.0)) / totalSpend else 0.0
        val score2 = (subRatio / 0.15).coerceIn(0.0, 1.0) // 15%+ of spend on subs = max

        // Score 3: Category overlap (multiple streaming, multiple music, etc.)
        val subCategories = setOf("subscriptions", "entertainment", "education")
        val subCatTxns = transactions.filter { it.category.lowercase() in subCategories }
        val uniqueSubMerchants = subCatTxns.map { it.merchant.lowercase() }.distinct().size
        val score3 = (uniqueSubMerchants.toDouble() / 6.0).coerceIn(0.0, 1.0) // 6+ unique = max

        return (score1 * 0.5 + score2 * 0.3 + score3 * 0.2)
    }

    /**
     * Steady Eddie Score:
     * - Low daily spending variance (weight: 0.4)
     * - No extreme spike days (weight: 0.3)
     * - Consistent merchant patterns (weight: 0.3)
     */
    private fun scoreSteadyEddie(transactions: List<Transaction>): Double {
        if (transactions.size < 14) return 0.0

        // Group by day and get daily totals
        val dailyTotals = transactions.groupBy { it.timestamp.toLocalDate() }
            .mapValues { it.value.sumOf { txn -> txn.amount } }
            .values.toList()

        if (dailyTotals.size < 7) return 0.0

        val mean = dailyTotals.average()
        val variance = dailyTotals.map { (it - mean) * (it - mean) }.average()
        val cv = sqrt(variance) / mean

        // Score 1: Low coefficient of variation (lower = steadier)
        val score1 = (1.0 - cv).coerceIn(0.0, 1.0) // CV of 0 = perfect, CV > 1 = 0 score

        // Score 2: No spike days (max day < 3x average)
        val maxDay = dailyTotals.maxOrNull() ?: 0.0
        val spikeRatio = maxDay / mean
        val score2 = (1.0 - (spikeRatio - 1.0) / 4.0).coerceIn(0.0, 1.0) // 5x spike = 0 score

        // Score 3: Merchant consistency (same merchants appearing regularly)
        val merchantFreq = transactions.groupBy { it.merchant.lowercase() }
            .mapValues { it.value.size }
        val topMerchantConcentration = merchantFreq.values.sortedDescending().take(5).sum().toDouble() /
                transactions.size
        val score3 = (topMerchantConcentration / 0.7).coerceIn(0.0, 1.0)

        return (score1 * 0.4 + score2 * 0.3 + score3 * 0.3)
    }

    /**
     * Feast-or-Famine Score:
     * - Spending concentration in first week (weight: 0.4)
     * - Spending drop in last week (weight: 0.3)
     * - High variance between week 1 and week 4 (weight: 0.3)
     */
    private fun scoreFeastOrFamine(transactions: List<Transaction>): Double {
        if (transactions.size < 20) return 0.0

        // Group by day of month
        val dayOfMonthGroups = transactions.groupBy { it.timestamp.dayOfMonth }
        val firstWeek = transactions.filter { it.timestamp.dayOfMonth in 1..7 }
        val lastWeek = transactions.filter { it.timestamp.dayOfMonth in 25..31 }

        val firstWeekSpend = firstWeek.sumOf { it.amount }
        val lastWeekSpend = lastWeek.sumOf { it.amount }
        val totalSpend = transactions.sumOf { it.amount }

        if (totalSpend == 0.0) return 0.0

        // Score 1: First week concentration (expected: ~23% for 7/30 days)
        val firstWeekRatio = firstWeekSpend / totalSpend
        val score1 = ((firstWeekRatio - 0.23) / 0.37).coerceIn(0.0, 1.0) // 60%+ in first week = max

        // Score 2: Last week drought (expected: ~20% for 6/30 days)
        val lastWeekRatio = lastWeekSpend / totalSpend
        val score2 = ((0.20 - lastWeekRatio) / 0.15).coerceIn(0.0, 1.0) // <5% in last week = max

        // Score 3: First week vs last week ratio
        val weekRatio = if (lastWeekSpend > 0) firstWeekSpend / lastWeekSpend else 5.0
        val score3 = ((weekRatio - 1.0) / 4.0).coerceIn(0.0, 1.0) // 5x difference = max

        return (score1 * 0.4 + score2 * 0.3 + score3 * 0.3)
    }

    /**
     * Minimalist Score:
     * - Total spending well below income (weight: 0.4)
     * - Low transaction count (weight: 0.2)
     * - Essentials dominate spending (weight: 0.2)
     * - Few entertainment/shopping transactions (weight: 0.2)
     */
    private fun scoreMinimalist(transactions: List<Transaction>, monthlyIncome: Double): Double {
        if (monthlyIncome <= 0) return 0.0

        val totalSpend = transactions.sumOf { it.amount }
        val days = transactions.map { it.timestamp.toLocalDate() }.distinct().size.coerceAtLeast(1)
        val monthlySpendEstimate = totalSpend / days * 30

        // Score 1: Savings rate (spending / income ratio)
        val spendRatio = monthlySpendEstimate / monthlyIncome
        val savingsRate = 1.0 - spendRatio
        val score1 = (savingsRate / 0.5).coerceIn(0.0, 1.0) // 50%+ savings = max

        // Score 2: Low transaction count per day
        val txPerDay = transactions.size.toDouble() / days
        val score2 = (1.0 - (txPerDay - 1.0) / 4.0).coerceIn(0.0, 1.0) // 1 tx/day = max

        // Score 3: Essential spending dominance
        val essentialCategories = setOf("groceries", "rent", "bills_utilities", "transportation", "health", "education")
        val essentialSpend = transactions.filter { it.category.lowercase() in essentialCategories }.sumOf { it.amount }
        val essentialRatio = if (totalSpend > 0) essentialSpend / totalSpend else 0.0
        val score3 = (essentialRatio / 0.8).coerceIn(0.0, 1.0) // 80%+ essential = max

        // Score 4: Low entertainment/shopping
        val discretionaryCategories = setOf("entertainment", "shopping", "personal_care", "food_delivery")
        val discretionarySpend = transactions.filter { it.category.lowercase() in discretionaryCategories }.sumOf { it.amount }
        val discretionaryRatio = if (totalSpend > 0) discretionarySpend / totalSpend else 0.0
        val score4 = (1.0 - discretionaryRatio / 0.4).coerceIn(0.0, 1.0) // <10% discretionary = max

        return (score1 * 0.4 + score2 * 0.2 + score3 * 0.2 + score4 * 0.2)
    }

    // ============================================================
    // Utility Functions
    // ============================================================

    /**
     * Detects potential gender from spending patterns (optional, for inclusive title).
     * Returns null if unable to determine. This is a soft heuristic, not definitive.
     */
    private fun detectGender(transactions: List<Transaction>): String? {
        // We don't make assumptions — return null to use inclusive title
        return null
    }

    private fun getDefaultPersonality(): MoneyPersonality {
        val definition = personalityDefinitions[PersonalityType.STEADY_EDDIE]!!
        return MoneyPersonality(
            type = PersonalityType.STEADY_EDDIE,
            title = definition.title,
            emoji = definition.emoji,
            tagline = "Not enough data yet — but we're watching! 👀",
            description = "We need more transaction data to classify your money personality. " +
                    "Keep using the app and we'll have your profile ready soon!",
            confidence = 0.0,
            traits = listOf("Insufficient data for trait analysis"),
            tips = listOf("Keep logging transactions for a personalized profile!"),
            funFact = "Did you know? The average Indian checks their bank balance 4.2 times per week.",
            spiritAnimal = "Mystery Animal — TBD 🎭",
            bollywoodCharacter = "Coming soon..."
        )
    }

    /**
     * Generates a shareable personality card text for social sharing.
     */
    fun generateShareableCard(personality: MoneyPersonality): String {
        return buildString {
            append("${personality.emoji} My Money Personality: ${personality.title}\n\n")
            append("\"${personality.tagline}\"\n\n")
            append("🎯 Confidence: ${(personality.confidence * 100).roundToInt()}%\n")
            append("🐾 Spirit Animal: ${personality.spiritAnimal}\n")
            append("🎬 Bollywood Match: ${personality.bollywoodCharacter}\n\n")
            append("Find yours on Paisa Brain! 💰\n")
            append("#PaisaBrain #MoneyPersonality")
        }
    }

    /**
     * Returns personalized challenges based on personality type.
     */
    fun getPersonalizedChallenges(personality: MoneyPersonality): List<Challenge> {
        return when (personality.type) {
            PersonalityType.WEEKEND_WARRIOR -> listOf(
                Challenge("Zero-Spend Saturday", "Go one Saturday without spending anything", 7, "🏆"),
                Challenge("Weekday Treat", "Move one 'weekend activity' to a weekday", 14, "🎯"),
                Challenge("Free Fun Finder", "Find 3 free weekend activities this month", 30, "🌟")
            )
            PersonalityType.MIDNIGHT_SNACKER -> listOf(
                Challenge("10 PM Lockout", "No food orders after 10 PM for 7 days", 7, "🌙"),
                Challenge("Snack Prepper", "Prep midnight snacks at home for a week", 7, "🍿"),
                Challenge("Early Bird Eater", "Have dinner before 9 PM every day this week", 7, "🐦")
            )
            PersonalityType.IMPULSE_ROYAL -> listOf(
                Challenge("48-Hour Rule", "Wait 48 hours before any purchase over ₹500", 14, "⏰"),
                Challenge("3-Item Limit", "Max 3 non-essential purchases this week", 7, "🎯"),
                Challenge("Cart Abandoner", "Add to cart but don't buy for a full week", 7, "🛒")
            )
            PersonalityType.SUBSCRIPTION_COLLECTOR -> listOf(
                Challenge("Sub Audit", "Cancel 2 subscriptions you haven't used in 30 days", 7, "✂️"),
                Challenge("Free Alternatives", "Replace one paid service with a free alternative", 14, "🆓"),
                Challenge("Share & Save", "Switch one subscription to a family/shared plan", 7, "👨‍👩‍👧‍👦")
            )
            PersonalityType.STEADY_EDDIE -> listOf(
                Challenge("Investment Start", "Set up a ₹500 daily SIP", 7, "📈"),
                Challenge("Splurge Day", "Have one guilt-free splurge day this month", 30, "🎉"),
                Challenge("Income Boost", "Find one way to earn ₹1000 extra this month", 30, "💡")
            )
            PersonalityType.FEAST_OR_FAMINE -> listOf(
                Challenge("Week 1 Lock", "Limit spending to ₹500/day for first 5 days post-salary", 5, "🔒"),
                Challenge("4-Envelope Method", "Split monthly budget into 4 weekly envelopes", 30, "✉️"),
                Challenge("Auto-Save First", "Auto-transfer 30% to savings on salary day", 1, "🏦")
            )
            PersonalityType.MINIMALIST -> listOf(
                Challenge("Invest the Gap", "Invest 80% of what you didn't spend this month", 30, "📊"),
                Challenge("Treat Yourself", "Buy one nice thing for yourself this week", 7, "🎁"),
                Challenge("Experience Fund", "Save ₹2000 for a fun experience end of month", 30, "🌈")
            )
        }
    }

    data class Challenge(
        val name: String,
        val description: String,
        val durationDays: Int,
        val emoji: String
    )
}
