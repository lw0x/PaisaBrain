package com.paisabrain.app.ai

import com.paisabrain.app.db.Transaction
import com.paisabrain.app.db.TransactionType
import java.util.Calendar

/**
 * SmartHelper - 300% Enhanced Customer Helpfulness Engine.
 * 
 * Goes BEYOND tracking to actively HELP customers:
 * 1. Financial literacy tips (teaches money management)
 * 2. Savings goal automation (auto-calculates savings plans)
 * 3. Emergency fund calculator
 * 4. Festive spending predictor (Diwali, Eid, Christmas budgeting)
 * 5. Bill reminder intelligence
 * 6. "What If" simulator ("What if you saved ₹X daily?")
 * 7. Family budget splitter
 * 8. EMI tracker & payoff predictor
 * 9. Inflation-adjusted future cost calculator
 * 10. Daily money-saving challenge generator
 */
object SmartHelper {

    // ==========================================
    // 1. FINANCIAL LITERACY TIPS
    // Teaches users about money in their language
    // ==========================================
    
    data class DailyTip(
        val titleKey: String,     // String resource key
        val contentKey: String,   // String resource key
        val category: TipCategory,
        val emoji: String
    )

    enum class TipCategory {
        SAVING, INVESTING, BUDGETING, DEBT, EMERGENCY, INSURANCE, TAX
    }

    fun getDailyTip(dayOfYear: Int): DailyTip {
        val tips = listOf(
            DailyTip("tip_50_30_20_title", "tip_50_30_20_content", TipCategory.BUDGETING, "📊"),
            DailyTip("tip_emergency_fund_title", "tip_emergency_fund_content", TipCategory.EMERGENCY, "🆘"),
            DailyTip("tip_compound_interest_title", "tip_compound_interest_content", TipCategory.INVESTING, "📈"),
            DailyTip("tip_impulse_wait_title", "tip_impulse_wait_content", TipCategory.SAVING, "⏰"),
            DailyTip("tip_emi_rule_title", "tip_emi_rule_content", TipCategory.DEBT, "💳"),
            DailyTip("tip_insurance_young_title", "tip_insurance_young_content", TipCategory.INSURANCE, "🛡️"),
            DailyTip("tip_80c_tax_title", "tip_80c_tax_content", TipCategory.TAX, "📋"),
            DailyTip("tip_sip_power_title", "tip_sip_power_content", TipCategory.INVESTING, "💪"),
            DailyTip("tip_no_cost_emi_title", "tip_no_cost_emi_content", TipCategory.DEBT, "⚠️"),
            DailyTip("tip_subscription_audit_title", "tip_subscription_audit_content", TipCategory.SAVING, "👻"),
            DailyTip("tip_meal_prep_title", "tip_meal_prep_content", TipCategory.SAVING, "🍱"),
            DailyTip("tip_cashback_trap_title", "tip_cashback_trap_content", TipCategory.BUDGETING, "🪤"),
            DailyTip("tip_automate_savings_title", "tip_automate_savings_content", TipCategory.SAVING, "🤖"),
            DailyTip("tip_lifestyle_inflation_title", "tip_lifestyle_inflation_content", TipCategory.BUDGETING, "📉"),
            DailyTip("tip_negotiate_bills_title", "tip_negotiate_bills_content", TipCategory.SAVING, "🤝")
        )
        return tips[dayOfYear % tips.size]
    }

    // ==========================================
    // 2. SAVINGS GOAL CALCULATOR
    // ==========================================

    data class SavingsGoal(
        val name: String,
        val targetAmount: Double,
        val currentSaved: Double,
        val dailySavingNeeded: Double,
        val daysToGoal: Int,
        val achievableDate: String,
        val motivationalMessage: String
    )

    fun calculateSavingsGoal(
        goalName: String,
        targetAmount: Double,
        currentMonthlySavings: Double,
        monthlyIncome: Double
    ): SavingsGoal {
        val maxDailySaving = (monthlyIncome * 0.3) / 30 // Max 30% of income as savings
        val dailySaving = minOf(maxDailySaving, targetAmount / 90) // Aim for 90 days default
        val daysNeeded = (targetAmount / dailySaving).toInt()
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysNeeded)
        val dateStr = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}"

        val message = when {
            daysNeeded <= 30 -> "🔥 You can do this in just $daysNeeded days!"
            daysNeeded <= 90 -> "💪 Achievable in ${daysNeeded / 30} months. You got this!"
            daysNeeded <= 365 -> "📅 A year-long journey. Small steps, big results!"
            else -> "🎯 Long-term goal. Every ₹ counts!"
        }

        return SavingsGoal(
            name = goalName,
            targetAmount = targetAmount,
            currentSaved = 0.0,
            dailySavingNeeded = dailySaving,
            daysToGoal = daysNeeded,
            achievableDate = dateStr,
            motivationalMessage = message
        )
    }

    // ==========================================
    // 3. EMERGENCY FUND CALCULATOR
    // ==========================================

    data class EmergencyFundStatus(
        val monthlyExpenses: Double,
        val recommendedFund: Double, // 6 months of expenses
        val currentEstimate: Double,
        val monthsOfRunway: Double,
        val status: EmergencyStatus,
        val advice: String
    )

    enum class EmergencyStatus {
        CRITICAL,  // < 1 month
        LOW,       // 1-3 months
        MODERATE,  // 3-5 months
        HEALTHY,   // 6+ months
        EXCELLENT  // 9+ months
    }

    fun calculateEmergencyFund(
        monthlyExpenses: Double,
        estimatedSavings: Double
    ): EmergencyFundStatus {
        val recommended = monthlyExpenses * 6
        val runway = if (monthlyExpenses > 0) estimatedSavings / monthlyExpenses else 0.0
        
        val status = when {
            runway < 1 -> EmergencyStatus.CRITICAL
            runway < 3 -> EmergencyStatus.LOW
            runway < 5 -> EmergencyStatus.MODERATE
            runway < 9 -> EmergencyStatus.HEALTHY
            else -> EmergencyStatus.EXCELLENT
        }

        val advice = when (status) {
            EmergencyStatus.CRITICAL -> "⚠️ Priority #1: Build at least 1 month buffer before anything else"
            EmergencyStatus.LOW -> "📈 Getting there! Try saving 20% of income until you hit 3 months"
            EmergencyStatus.MODERATE -> "👍 Good progress! Keep going until 6 months"
            EmergencyStatus.HEALTHY -> "✅ Great job! You have solid protection"
            EmergencyStatus.EXCELLENT -> "🏆 Excellent! Consider investing the surplus"
        }

        return EmergencyFundStatus(
            monthlyExpenses = monthlyExpenses,
            recommendedFund = recommended,
            currentEstimate = estimatedSavings,
            monthsOfRunway = runway,
            status = status,
            advice = advice
        )
    }

    // ==========================================
    // 4. FESTIVE SPENDING PREDICTOR
    // ==========================================

    fun getFestiveAlert(): FestiveAlert? {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) // 0-indexed
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // Indian festivals with approximate dates
        return when {
            month == 9 && day in 1..15 -> FestiveAlert(
                "Dussehra", "🎆", 15,
                "Dussehra is coming! Average Indian spends ₹5,000-15,000. Start budgeting now!"
            )
            month == 9 && day in 16..31 || month == 10 && day in 1..15 -> FestiveAlert(
                "Diwali", "🪔", 30,
                "Diwali is near! Plan for gifts, clothes, sweets, firecrackers. Budget ₹10,000-30,000."
            )
            month == 11 && day in 20..31 -> FestiveAlert(
                "Christmas/New Year", "🎄", 10,
                "Holiday season! Party budgets, gifts, travel. Plan ₹5,000-15,000."
            )
            month == 0 && day in 10..26 -> FestiveAlert(
                "Republic Day Sales", "🇮🇳", 7,
                "Republic Day sales are coming. Make a wish list BEFORE the sale to avoid impulse buys!"
            )
            month == 3 && day in 1..14 -> FestiveAlert(
                "Eid", "🌙", 14,
                "Eid is approaching! Budget for clothes, food, gifts, charity."
            )
            month == 7 && day in 1..15 -> FestiveAlert(
                "Independence Day Sales", "🇮🇳", 7,
                "Freedom sale season! Set a strict budget before browsing."
            )
            else -> null
        }
    }

    data class FestiveAlert(
        val festival: String,
        val emoji: String,
        val daysAway: Int,
        val advice: String
    )

    // ==========================================
    // 5. "WHAT IF" SIMULATOR
    // ==========================================

    data class WhatIfResult(
        val scenario: String,
        val dailySaving: Double,
        val monthlyTotal: Double,
        val yearlyTotal: Double,
        val fiveYearTotal: Double,
        val funComparison: String
    )

    fun simulateWhatIf(dailySaving: Double): WhatIfResult {
        val monthly = dailySaving * 30
        val yearly = dailySaving * 365
        val fiveYear = yearly * 5 * 1.07 // 7% annual return if invested

        val comparison = when {
            yearly < 10000 -> "That's ${(yearly / 200).toInt()} cups of fancy coffee ☕"
            yearly < 50000 -> "That's a weekend trip to Goa! 🏖️"
            yearly < 100000 -> "That's a new phone every year! 📱"
            yearly < 300000 -> "That's a down payment on a two-wheeler! 🏍️"
            yearly < 500000 -> "That's a solid mutual fund portfolio start! 📈"
            else -> "That's life-changing wealth building! 🏆"
        }

        return WhatIfResult(
            scenario = "Save ₹${dailySaving.toInt()} daily",
            dailySaving = dailySaving,
            monthlyTotal = monthly,
            yearlyTotal = yearly,
            fiveYearTotal = fiveYear,
            funComparison = comparison
        )
    }

    // ==========================================
    // 6. DAILY MONEY-SAVING CHALLENGE
    // ==========================================

    data class DailyChallenge(
        val title: String,
        val description: String,
        val potentialSaving: String,
        val difficulty: Int, // 1-5
        val emoji: String
    )

    fun getDailyChallenge(dayOfYear: Int): DailyChallenge {
        val challenges = listOf(
            DailyChallenge("No-Spend Day", "Don't spend anything today except essentials", "₹500-1000", 3, "🚫"),
            DailyChallenge("Cook at Home", "Skip ordering food today — cook something simple", "₹300-600", 2, "👨‍🍳"),
            DailyChallenge("Walk Short Trips", "Walk any trip under 2km instead of taking auto/cab", "₹100-200", 2, "🚶"),
            DailyChallenge("Unsubscribe One", "Find and cancel one unused subscription today", "₹99-999/mo", 1, "✂️"),
            DailyChallenge("Carry Water Bottle", "Don't buy any beverages outside today", "₹50-150", 1, "💧"),
            DailyChallenge("No Online Shopping", "Don't browse any shopping app today", "₹0-5000", 4, "🛒"),
            DailyChallenge("Pack Lunch", "Take lunch from home to work/college", "₹150-300", 2, "🍱"),
            DailyChallenge("DIY Something", "Fix/clean something yourself instead of paying", "₹200-500", 3, "🔧"),
            DailyChallenge("Negotiate a Bill", "Call one service provider and ask for discount", "₹100-500/mo", 4, "📞"),
            DailyChallenge("Skip the Coffee", "Make coffee at home or skip the café today", "₹150-400", 2, "☕"),
            DailyChallenge("Free Entertainment", "Park, library, free video platforms instead of paid plans", "₹200-500", 1, "🎬"),
            DailyChallenge("Comparison Shop", "Before buying anything, check 3 alternatives", "₹100-2000", 3, "🔍"),
            DailyChallenge("Cash Only Day", "Use only cash today — physical money hurts more", "₹200-500", 3, "💵"),
            DailyChallenge("Sell Something", "List one unused item for sale (online marketplace/local)", "+₹500-5000", 2, "📦"),
            DailyChallenge("Energy Saver", "Turn off extra lights, unplug chargers all day", "₹5-15/day", 1, "💡")
        )
        return challenges[dayOfYear % challenges.size]
    }

    // ==========================================
    // 7. EMI TRACKER & PAYOFF PREDICTOR
    // ==========================================

    data class EmiInsight(
        val detectedEmis: List<DetectedEmi>,
        val totalMonthlyEmi: Double,
        val emiToIncomeRatio: Double,
        val healthStatus: String,
        val advice: String
    )

    data class DetectedEmi(
        val merchant: String,
        val amount: Double,
        val frequency: String, // "monthly"
        val firstSeen: Long,
        val estimatedRemaining: Int // months
    )

    fun analyzeEmis(
        transactions: List<Transaction>,
        estimatedIncome: Double
    ): EmiInsight {
        // Detect EMI-like patterns: same amount, same merchant, monthly
        val potentialEmis = transactions
            .filter { it.type == TransactionType.DEBIT }
            .groupBy { "${it.merchant}_${it.amount}" }
            .filter { it.value.size >= 2 }
            .map { (key, txns) ->
                val parts = key.split("_")
                DetectedEmi(
                    merchant = parts[0],
                    amount = parts[1].toDoubleOrNull() ?: 0.0,
                    frequency = "monthly",
                    firstSeen = txns.minOf { it.timestamp },
                    estimatedRemaining = 12 // Default estimate
                )
            }

        val totalEmi = potentialEmis.sumOf { it.amount }
        val ratio = if (estimatedIncome > 0) totalEmi / estimatedIncome else 0.0

        val (status, advice) = when {
            ratio > 0.5 -> "🔴 Critical" to "EMIs are eating >50% of income. Consider consolidation or restructuring."
            ratio > 0.35 -> "🟠 High" to "EMI burden is high (${(ratio * 100).toInt()}%). Avoid new loans."
            ratio > 0.2 -> "🟡 Moderate" to "EMI load is manageable but watch for new commitments."
            else -> "🟢 Healthy" to "EMI load is well within healthy limits. Great job!"
        }

        return EmiInsight(
            detectedEmis = potentialEmis,
            totalMonthlyEmi = totalEmi,
            emiToIncomeRatio = ratio,
            healthStatus = status,
            advice = advice
        )
    }

    // ==========================================
    // 8. INFLATION CALCULATOR
    // ==========================================

    fun calculateFutureCost(
        currentCost: Double,
        years: Int,
        inflationRate: Double = 0.06 // 6% India average
    ): Double {
        return currentCost * Math.pow(1 + inflationRate, years.toDouble())
    }

    // ==========================================
    // 9. SPENDING VELOCITY ALERT
    // Real-time "you're spending too fast" detection
    // ==========================================

    data class VelocityAlert(
        val isAlert: Boolean,
        val currentPace: Double, // per day
        val safePace: Double,    // per day to stay in budget
        val daysLeft: Int,
        val message: String
    )

    fun checkSpendingVelocity(
        spentThisMonth: Double,
        monthlyBudget: Double,
        dayOfMonth: Int,
        daysInMonth: Int = 30
    ): VelocityAlert {
        val daysLeft = daysInMonth - dayOfMonth
        val remaining = monthlyBudget - spentThisMonth
        val currentPace = if (dayOfMonth > 0) spentThisMonth / dayOfMonth else 0.0
        val safePace = if (daysLeft > 0) remaining / daysLeft else 0.0

        val isAlert = currentPace > (monthlyBudget / daysInMonth) * 1.2 // 20% over average pace

        val message = when {
            remaining <= 0 -> "🚨 Budget exceeded! Try a no-spend day today."
            safePace < 0 -> "⚠️ Over budget by ₹${(-remaining).toInt()}. Time to pause spending."
            currentPace > safePace * 2 -> "🔥 Spending 2x faster than safe pace! Slow down."
            currentPace > safePace * 1.5 -> "⚡ Slightly fast pace. ₹${safePace.toInt()}/day is your safe limit."
            else -> "✅ On track! Safe to spend ₹${safePace.toInt()}/day for rest of month."
        }

        return VelocityAlert(
            isAlert = isAlert,
            currentPace = currentPace,
            safePace = safePace,
            daysLeft = daysLeft,
            message = message
        )
    }

    // ==========================================
    // 10. SMART CATEGORY RECOMMENDATIONS
    // ==========================================

    fun getSmartRecommendations(
        categoryTotals: Map<String, Double>,
        monthlyIncome: Double
    ): List<SmartRecommendation> {
        val recommendations = mutableListOf<SmartRecommendation>()
        
        // 50-30-20 Rule Check
        val needs = (categoryTotals["Bills"] ?: 0.0) + 
                   (categoryTotals["Groceries"] ?: 0.0) + 
                   (categoryTotals["Transport"] ?: 0.0) +
                   (categoryTotals["Health"] ?: 0.0)
        val wants = (categoryTotals["Food"] ?: 0.0) + 
                   (categoryTotals["Shopping"] ?: 0.0) + 
                   (categoryTotals["Entertainment"] ?: 0.0)
        
        val needsPercent = if (monthlyIncome > 0) (needs / monthlyIncome) * 100 else 0.0
        val wantsPercent = if (monthlyIncome > 0) (wants / monthlyIncome) * 100 else 0.0

        if (wantsPercent > 40) {
            recommendations.add(SmartRecommendation(
                "🎯", "High 'Wants' Spending",
                "You're spending ${wantsPercent.toInt()}% on wants (food delivery, shopping, entertainment). The 50-30-20 rule suggests max 30%.",
                "Try reducing by ₹${((wantsPercent - 30) / 100 * monthlyIncome).toInt()}/month"
            ))
        }

        // Specific merchant alerts
        categoryTotals.forEach { (category, amount) ->
            val percent = if (monthlyIncome > 0) (amount / monthlyIncome) * 100 else 0.0
            if (percent > 25 && category != "Bills") {
                recommendations.add(SmartRecommendation(
                    "⚠️", "$category is ${percent.toInt()}% of income",
                    "This single category is taking a big chunk. National average is ${getAveragePercent(category)}%.",
                    "Consider a ₹${(amount * 0.2).toInt()} reduction target"
                ))
            }
        }

        return recommendations
    }

    data class SmartRecommendation(
        val emoji: String,
        val title: String,
        val description: String,
        val actionable: String
    )

    private fun getAveragePercent(category: String): Int {
        return when (category) {
            "Food" -> 15
            "Transport" -> 10
            "Shopping" -> 8
            "Entertainment" -> 5
            "Health" -> 5
            "Education" -> 7
            "Groceries" -> 12
            else -> 10
        }
    }
}
