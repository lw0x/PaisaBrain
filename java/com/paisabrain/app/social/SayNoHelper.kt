package com.paisabrain.app.social

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import kotlin.math.roundToInt

/**
 * Polite refusal scripts for social money pressure situations.
 *
 * Helps users navigate the uniquely Indian challenge of saying "no" to money
 * requests from family, friends, and colleagues while maintaining relationships.
 * Provides culturally appropriate, ready-to-use responses in English and Hindi.
 *
 * ## Design Philosophy
 * - **Culturally sensitive**: Respects Indian social dynamics around money
 * - **Non-preachy**: Never tells user they MUST say no — provides tools if they want to
 * - **Relationship-preserving**: Responses are designed to maintain the relationship
 * - **Bilingual**: Every response available in English and Hindi
 * - **Budget-aware**: Shows concrete impact of lending on the user's budget
 *
 * ## Features
 * - 8 common money pressure scenarios with graduated responses
 * - Gentle / Firm / Direct tone options
 * - Budget impact calculator for lending decisions
 * - Lending history tracker integration
 * - Monthly "saved by saying no" stats
 * - FOMO rational check
 *
 * ## Usage
 * ```kotlin
 * val helper = SayNoHelper(context)
 * val scenario = helper.getResponsesForScenario(
 *     type = ScenarioType.BORROW_MONEY,
 *     amount = 15000.0,
 *     relationship = "friend"
 * )
 * // Show scenario.responses to user — they pick the tone they're comfortable with
 * ```
 *
 * @property context Application context for persistence
 */
class SayNoHelper(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "say_no_helper_prefs"
        private const val KEY_LENDING_HISTORY = "lending_history"
        private const val KEY_DECLINED_REQUESTS = "declined_requests"
        private const val KEY_MONTHLY_STATS = "monthly_decline_stats"
    }

    private val gson = Gson()

    // ─────────────────────────────────────────────────────────────────────────────
    // Enums & Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Types of money pressure scenarios.
     */
    enum class ScenarioType {
        /** Friend/acquaintance asking to borrow money */
        BORROW_MONEY,
        /** Group wants to split equally but user spent less */
        UNEQUAL_SPLIT,
        /** Family expecting financial contribution beyond means */
        FAMILY_EXPECTATION,
        /** Pressure to join expensive outing */
        EXPENSIVE_OUTING,
        /** Colleague asking for repeated small loans */
        REPEATED_LOANS,
        /** Wedding/event contribution beyond budget */
        EVENT_CONTRIBUTION,
        /** Everyone buying something and user feels left out */
        FOMO_PURCHASE,
        /** Lending again to someone who didn't return last time */
        UNRETURNED_DEBT
    }

    /**
     * Response tone — user picks their comfort level.
     */
    enum class Tone {
        /** Softest approach — works for close relationships */
        GENTLE,
        /** Clear boundary while remaining polite */
        FIRM,
        /** Unambiguous — when gentle hasn't worked */
        DIRECT
    }

    /**
     * A complete refusal response with bilingual text.
     *
     * @property tone How assertive this response is
     * @property textEnglish The response in English (copyable)
     * @property textHindi The response in Hindi (copyable)
     * @property followUpAdvice What to do/say if they push back
     */
    data class SayNoResponse(
        val tone: Tone,
        val textEnglish: String,
        val textHindi: String,
        val followUpAdvice: String
    )

    /**
     * A complete scenario with context and responses.
     *
     * @property type The scenario type
     * @property situation Description of the social situation
     * @property responses Three responses (Gentle, Firm, Direct)
     * @property budgetImpact Budget impact if user says yes (null if not applicable)
     * @property culturalNote Cultural context note for this situation
     */
    data class SayNoScenario(
        val type: ScenarioType,
        val situation: String,
        val responses: List<SayNoResponse>,
        val budgetImpact: BudgetImpact? = null,
        val culturalNote: String? = null
    )

    /**
     * Impact on budget if the user agrees to the money request.
     *
     * @property lendingAmount Amount being requested
     * @property currentDailyBudget Current daily budget before lending
     * @property newDailyBudget Daily budget if they lend the money
     * @property daysAffected How many days the budget is reduced
     * @property impactMessage Human-readable impact summary
     */
    data class BudgetImpact(
        val lendingAmount: Double,
        val currentDailyBudget: Double,
        val newDailyBudget: Double,
        val daysAffected: Int,
        val impactMessage: String
    )

    /**
     * Record of money lent to someone (for tracking).
     *
     * @property personName Who was the money lent to
     * @property amount Amount lent
     * @property date When it was lent
     * @property returnedAmount How much has been returned
     * @property isFullyReturned Whether the full amount is back
     * @property expectedReturnDate When they promised to return
     * @property notes Any additional notes
     */
    data class LendingRecord(
        val personName: String,
        val amount: Double,
        val date: Long = System.currentTimeMillis(),
        val returnedAmount: Double = 0.0,
        val isFullyReturned: Boolean = false,
        val expectedReturnDate: Long? = null,
        val notes: String = ""
    )

    /**
     * Monthly stats for declined requests.
     *
     * @property month Month identifier (e.g., "2024-03")
     * @property declinedCount Number of requests declined
     * @property totalAmountDeclined Total amount of all declined requests
     * @property acceptedCount Number of requests accepted
     * @property totalAmountAccepted Total amount of accepted requests
     */
    data class MonthlyDeclineStats(
        val month: String,
        val declinedCount: Int = 0,
        val totalAmountDeclined: Double = 0.0,
        val acceptedCount: Int = 0,
        val totalAmountAccepted: Double = 0.0
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // Core — Get Responses for Scenario
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns culturally appropriate responses for a money pressure scenario.
     *
     * Provides three tone options (Gentle, Firm, Direct) for each situation,
     * with both English and Hindi versions ready to copy and use.
     *
     * @param type The type of social scenario
     * @param amount Amount of money involved (for budget impact calculation)
     * @param relationship Relationship to the person (for tailored responses)
     * @return [SayNoScenario] with situation context and response options
     */
    fun getResponsesForScenario(
        type: ScenarioType,
        amount: Double? = null,
        relationship: String? = null
    ): SayNoScenario {
        return when (type) {
            ScenarioType.BORROW_MONEY -> getBorrowMoneyScenario(amount, relationship)
            ScenarioType.UNEQUAL_SPLIT -> getUnequalSplitScenario(amount)
            ScenarioType.FAMILY_EXPECTATION -> getFamilyExpectationScenario(amount, relationship)
            ScenarioType.EXPENSIVE_OUTING -> getExpensiveOutingScenario(amount)
            ScenarioType.REPEATED_LOANS -> getRepeatedLoansScenario(amount, relationship)
            ScenarioType.EVENT_CONTRIBUTION -> getEventContributionScenario(amount)
            ScenarioType.FOMO_PURCHASE -> getFomoPurchaseScenario(amount)
            ScenarioType.UNRETURNED_DEBT -> getUnreturnedDebtScenario(amount, relationship)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Scenario Implementations
    // ─────────────────────────────────────────────────────────────────────────────

    private fun getBorrowMoneyScenario(amount: Double?, relationship: String?): SayNoScenario {
        val amountStr = amount?.let { formatCurrency(it) } ?: "the amount"
        val rel = relationship ?: "friend"

        return SayNoScenario(
            type = ScenarioType.BORROW_MONEY,
            situation = "A $rel is asking to borrow $amountStr from you.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "I really wish I could help, but I'm on a tight budget myself right now. I hope you understand — it's not about willingness, it's about capability at this moment. Is there any other way I can help you out?",
                    textHindi = "Yaar, kaash main help kar paata, lekin is waqt mera budget bhi tight chal raha hai. Please samajh jaana — mann toh hai lekin situation allow nahi kar rahi. Koi aur tarike se help kar sakta hoon?",
                    followUpAdvice = "If they push, repeat calmly: 'I understand it's urgent for you, but I genuinely don't have spare funds right now.' You don't owe an explanation of your finances."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I'm not in a position to lend money right now. I've made a personal rule to not mix money with relationships — it's caused me issues before. I hope you find another way to sort this out.",
                    textHindi = "Filhaal main paise udhar dene ki position mein nahi hoon. Maine ek personal rule banaya hai ki rishton mein paison ko beech mein nahi laata — pehle dikkat hui hai. Umeed hai tum koi aur rasta nikal loge.",
                    followUpAdvice = "The 'personal rule' approach is powerful — it's not about them specifically, it's about your policy. Harder to take personally."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "I won't be able to lend this amount. My financial situation doesn't allow it, and I need to prioritize my own commitments first. I'm sure you'll figure it out.",
                    textHindi = "Main yeh amount nahi de paunga/paungi. Meri financial situation allow nahi karti, aur mujhe apne commitments pehle dekhne hain. Tum koi na koi raasta nikal loge.",
                    followUpAdvice = "No is a complete sentence. You don't need to justify your financial boundaries to anyone."
                )
            ),
            culturalNote = "In Indian culture, saying no to money requests can feel like betraying the relationship. Remember: a true friend values your relationship more than your wallet. If they respect you, they'll accept your 'no' gracefully."
        )
    }

    private fun getUnequalSplitScenario(amount: Double?): SayNoScenario {
        return SayNoScenario(
            type = ScenarioType.UNEQUAL_SPLIT,
            situation = "The group wants to split the bill equally, but you ordered/spent significantly less than others.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "Hey, would it be okay if we did an itemized split this time? I only had [items] so my share would be around ₹X. Happy to add a bit for shared items like starters though!",
                    textHindi = "Arre, kya hum is baar apna-apna hissa daal dein? Maine sirf [items] liya tha toh mera around ₹X banta hai. Shared items ke liye thoda extra add kar dunga/dungi!",
                    followUpAdvice = "Suggest this BEFORE the bill arrives if possible. It's easier to set expectations early than to negotiate after."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I'd prefer to pay for what I ordered — comes to about ₹X. I'm watching my budget this month. You guys can split the rest however works for you!",
                    textHindi = "Main apna order ka hi pay karunga/karungi — lagbhag ₹X hoga. Is month budget tight hai. Baaki tum log jo comfortable ho waise split kar lena!",
                    followUpAdvice = "Most people respect this if said cheerfully. If someone makes it awkward, that's a them-problem, not a you-problem."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "I'll pay my share — ₹X for what I had. Equal split doesn't work when the orders are very different. Makes more sense to pay for what each person ordered.",
                    textHindi = "Main apna hissa — ₹X — de dunga/dungi jo maine order kiya. Jab orders bahut alag hain toh equal split fair nahi hota. Sabka apna-apna better hai.",
                    followUpAdvice = "For future outings, you can pre-empt this by saying 'let's do separate bills' when ordering, or choosing venues with simpler billing."
                )
            ),
            culturalNote = "Unequal splits are a common source of silent resentment. Most people actually prefer itemized splits but are too polite to suggest it. You might be doing the whole group a favor by speaking up!"
        )
    }

    private fun getFamilyExpectationScenario(amount: Double?, relationship: String?): SayNoScenario {
        val rel = relationship ?: "family member"

        return SayNoScenario(
            type = ScenarioType.FAMILY_EXPECTATION,
            situation = "Family is expecting money from you — possibly for household expenses, a relative's need, or a family obligation — but the amount is beyond what you can comfortably give.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "I want to help, and I will — but I can only contribute ₹X right now. I have my own rent/EMIs/expenses to manage. I'll increase it when my situation improves. Please don't take this as me not caring.",
                    textHindi = "Main help karna chahta/chahti hoon, aur karunga/karungi — lekin abhi ₹X se zyada dena possible nahi hai. Mera apna rent/EMI/kharcha bhi hai. Jaise hi situation improve hogi, badha dunga/dungi. Yeh mat samajhna ki mujhe fark nahi padta.",
                    followUpAdvice = "With family, offering SOMETHING (even less than asked) works better than a flat no. It shows willingness while setting a boundary."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I've thought about it carefully. I can give ₹X — that's genuinely the most I can manage right now without putting myself in difficulty. I have my own financial obligations I need to meet.",
                    textHindi = "Maine soch-samajh kar decide kiya hai. ₹X de sakta/sakti hoon — sach mein itna hi manage kar sakta/sakti hoon bina khud mushkil mein aaye. Mere apne financial obligations bhi hain.",
                    followUpAdvice = "Don't over-explain your expenses to family. A simple 'I have my own commitments' is enough. You don't need to prove your budget to anyone."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "I love you all, but I cannot give more than ₹X without hurting my own financial health. I'm not going to do that. This is the most I can offer, and I'm offering it with love.",
                    textHindi = "Mujhe aap sabse pyaar hai, lekin ₹X se zyada dena meri apni financial health kharab kar dega. Main aisa nahi kar sakta/sakti. Itna hi offer kar sakta/sakti hoon, aur yeh bhi dil se de raha/rahi hoon.",
                    followUpAdvice = "Family pressure can be the hardest to resist. Remember: you cannot pour from an empty cup. Hurting your own finances to please family helps no one long-term."
                )
            ),
            culturalNote = "Indian families often view a working member's income as partially communal. While supporting family is admirable, remember: putting yourself in financial distress to meet others' expectations isn't sustainable. It's okay to give what you can without guilt about what you can't."
        )
    }

    private fun getExpensiveOutingScenario(amount: Double?): SayNoScenario {
        return SayNoScenario(
            type = ScenarioType.EXPENSIVE_OUTING,
            situation = "Friends are planning an expensive outing (fancy restaurant, trip, club) that doesn't fit your budget.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "Sounds fun! But honestly, it doesn't fit my budget this month. How about I join you guys for [cheaper alternative] instead? Or if you're going, have a great time — I'll catch the next one!",
                    textHindi = "Mast plan hai! Lekin honestly, is month mere budget mein nahi hai. Kya [cheaper alternative] pe milein instead? Ya agar tum log ja rahe ho, enjoy karo — next time pakka join karunga/karungi!",
                    followUpAdvice = "Suggesting an alternative shows you value their company, not just saying no. If they're real friends, they'll accommodate or understand."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I'll skip this one — it's over my budget right now. Not in a bad way, just being disciplined this month. You guys enjoy! Send pictures 😄",
                    textHindi = "Is baar skip karunga/karungi — budget se bahar hai filhaal. Koi problem nahi, bas is month disciplined reh raha/rahi hoon. Tum log enjoy karo! Photos bhejnaa 😄",
                    followUpAdvice = "Being casual and cheerful about it removes the awkwardness. No one can make you feel bad about being responsible with money if you don't feel bad about it yourself."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "That's too expensive for me right now. I'm happy to join something more budget-friendly, or we can hang out at someone's place instead. Let me know what works.",
                    textHindi = "Yeh mere liye abhi expensive hai. Kuch budget-friendly ho toh batao, ya kisi ke ghar pe plan karte hain. Jo bhi finalize ho, batana.",
                    followUpAdvice = "Own your financial choices confidently. There's no shame in having a budget. The richest people in the world have budgets."
                )
            ),
            culturalNote = "FOMO is real, but so are bills. One missed outing won't end a friendship. If your friends only want to do expensive things and judge you for opting out, that's worth reflecting on."
        )
    }

    private fun getRepeatedLoansScenario(amount: Double?, relationship: String?): SayNoScenario {
        val rel = relationship ?: "colleague"

        return SayNoScenario(
            type = ScenarioType.REPEATED_LOANS,
            situation = "A $rel keeps asking for small loans — ₹500 here, ₹1000 there. It's becoming a pattern.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "Hey, I've noticed we've had quite a few of these requests lately. I'm not able to keep doing this — my budget doesn't have room for regular lending. Maybe we can figure out a different arrangement?",
                    textHindi = "Arre yaar, notice kiya maine ki kaafi baar yeh hota ja raha hai. Mera budget regular lending allow nahi karta. Koi aur arrangement sochte hain?",
                    followUpAdvice = "With repeated borrowers, the key is breaking the pattern. If you always say yes, they'll always ask. The first 'no' is the hardest but most important."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I'm going to say no today. I've been lending quite regularly and it's affecting my own budget. I'd suggest planning ahead for these expenses so you're not caught short repeatedly.",
                    textHindi = "Aaj no kahunga/kahungi. Main kaafi regularly lend kar raha/rahi hoon aur mera apna budget affect ho raha hai. Suggest karunga/karungi ki thoda plan karke chalo taaki baar-baar short na pado.",
                    followUpAdvice = "It's okay to point out the pattern. You're not being rude — you're being honest. They know they keep asking."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "No, I can't lend today. Frankly, this has become too frequent and I need to set a boundary. I'm happy to help with genuine one-time emergencies, but regular borrowing isn't something I can sustain.",
                    textHindi = "Nahi, aaj nahi de paunga/paungi. Sacchi baat hai, yeh bahut frequent ho gaya hai aur mujhe boundary set karni hogi. Genuine emergency mein help karunga/karungi, lekin regular borrowing sustain nahi kar sakta/sakti.",
                    followUpAdvice = "After setting this boundary, stick to it. Consistency matters. If you say no three times and yes the fourth, you've taught them that persistence works."
                )
            ),
            culturalNote = "Small repeated loans feel less significant individually but add up significantly. Track how much you've lent to this person total — the number might surprise you."
        )
    }

    private fun getEventContributionScenario(amount: Double?): SayNoScenario {
        return SayNoScenario(
            type = ScenarioType.EVENT_CONTRIBUTION,
            situation = "There's a wedding, event, or social function where the expected monetary contribution feels beyond your budget.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "So happy for [person]! I'll be there to celebrate. I can contribute ₹X as my gift — it's from the heart even if it's not the biggest amount. Hope that's okay!",
                    textHindi = "[Person] ke liye bahut khushi hai! Main celebrate karne zaroor aaunga/aaungi. Apni taraf se ₹X ka gift de sakta/sakti hoon — dil se hai, chahe amount bada na ho. Theek hai na!",
                    followUpAdvice = "Give what you can without guilt. A genuine gift at ₹2000 is worth more than a resentful one at ₹10000. No one remembers the exact shagun amount years later."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "My budget allows me to contribute ₹X for this occasion. I know others might give more, but everyone's financial situation is different. I'm contributing what works for me.",
                    textHindi = "Mere budget mein ₹X fit ho raha hai is occasion ke liye. Main jaanta/jaanti hoon log zyada bhi dete hain, lekin sabki financial situation alag hoti hai. Main wahi de raha/rahi hoon jo mere liye comfortable hai.",
                    followUpAdvice = "You don't need to match what others give. Your relationship with the person isn't measured in rupees."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "I can only give ₹X — that's what I'm comfortable with given my current financial situation. I'm not going to stretch beyond my means for social expectations.",
                    textHindi = "₹X de sakta/sakti hoon — apni current financial situation mein itna comfortable hai. Social expectations ke liye apni limit cross nahi karunga/karungi.",
                    followUpAdvice = "Wedding shagun culture can be a trap of escalation. Break the cycle. Most couples appreciate your presence more than your presents."
                )
            ),
            culturalNote = "In Indian weddings, shagun amounts can feel like a social obligation. Remember: the purpose is celebrating with someone, not proving your financial status. Close relationships survive small shagun amounts; superficial ones probably shouldn't drain your savings anyway."
        )
    }

    private fun getFomoPurchaseScenario(amount: Double?): SayNoScenario {
        val amountStr = amount?.let { formatCurrency(it) } ?: "something expensive"

        return SayNoScenario(
            type = ScenarioType.FOMO_PURCHASE,
            situation = "Everyone in your circle seems to be buying $amountStr and you feel left out or pressured to keep up.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "Cool that you got one! I'm going to wait on mine for now — saving for something else. Maybe in a few months. Enjoy yours though, tell me how it is!",
                    textHindi = "Badiya! Tumne le liya! Main apna filhaal wait karunga/karungi — kuch aur ke liye save kar raha/rahi hoon. Maybe kuch months mein. Enjoy karo, batana kaisa hai!",
                    followUpAdvice = "Deflect with 'saving for something else' — it sounds like a choice, not a limitation. You don't need to explain what you're saving for."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "Happy for you! I don't need one right now — I've thought about it and it's not a priority for me this year. Different things for different people, right?",
                    textHindi = "Tere liye happy hoon! Mujhe abhi zaroorat nahi hai — socha maine, aur is saal mere priority mein nahi hai. Har kisi ki priorities alag hoti hain, right?",
                    followUpAdvice = "Saying 'it's not a priority for me' is powerful. It positions it as a conscious choice, not deprivation."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "I don't make purchase decisions based on what others are buying. If and when I need one, I'll get it. Right now, my money has better uses.",
                    textHindi = "Main doosron ko dekh ke shopping nahi karta/karti. Jab zaroorat hogi tab le lunga/lungi. Abhi mere paison ka better use hai.",
                    followUpAdvice = "FOMO is marketing's best tool. Ask yourself: 'If NO ONE I knew had this, would I still want it?' If yes, it might be a real want. If no, it's pure social pressure."
                )
            ),
            culturalNote = "Comparison is the thief of joy AND money. Everyone's financial reality is different — some might be on EMIs they can barely afford, some might have family money, some might actually be able to afford it. You don't know their balance sheet; they don't know yours. Run your own race. 🏃"
        )
    }

    private fun getUnreturnedDebtScenario(amount: Double?, relationship: String?): SayNoScenario {
        val rel = relationship ?: "person"
        val amountStr = amount?.let { formatCurrency(it) } ?: "money"

        return SayNoScenario(
            type = ScenarioType.UNRETURNED_DEBT,
            situation = "The same $rel who hasn't returned previous $amountStr is asking to borrow again.",
            responses = listOf(
                SayNoResponse(
                    tone = Tone.GENTLE,
                    textEnglish = "I'd love to help, but I'm still waiting for the ₹X from last time. Once that comes back, happy to discuss again. No pressure — just wanted to mention it!",
                    textHindi = "Help karna chahunga/chahungi, lekin pichla ₹X abhi tak pending hai. Jab woh aa jaaye, fir baat karte hain. No pressure — bas yaad dila raha/rahi hoon!",
                    followUpAdvice = "This is the lightest way to bring up old debt. If they apologize and offer to return, great. If they get defensive, you have your answer about future lending."
                ),
                SayNoResponse(
                    tone = Tone.FIRM,
                    textEnglish = "I can't lend again while the previous amount is still outstanding. I don't want money to create awkwardness between us — let's clear the old account first, then we can talk about new ones.",
                    textHindi = "Pichla amount jab tak pending hai, naya nahi de paunga/paungi. Nahi chahta/chahti ki paison se beech mein awkwardness aaye — pehle purana clear karo, fir naye ki baat karte hain.",
                    followUpAdvice = "You're being completely fair. Asking someone to return what they owe before lending more is basic financial sense, not rudeness."
                ),
                SayNoResponse(
                    tone = Tone.DIRECT,
                    textEnglish = "No. You still owe me ₹X from [time]. I'm not lending more until that's returned. I value our relationship, which is exactly why I'm being honest instead of quietly resenting it.",
                    textHindi = "Nahi. [Time] ka ₹X abhi tak baaki hai tumhara. Jab tak woh nahi aata, aur nahi dunga/dungi. Mujhe humari friendship ki kadar hai — isliye honest bol raha/rahi hoon instead of andar hi andar bura maan na.",
                    followUpAdvice = "If they get upset, remember: THEY are the one who broke a financial promise. You simply stated facts. The discomfort they feel should motivate returning what's owed, not guilting you into lending more."
                )
            ),
            culturalNote = "There's a saying: 'If you lend money to a friend, you'll lose both.' If someone hasn't returned past loans and asks for more, they've already shown you their pattern. Trust patterns, not promises."
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Budget Impact Calculator
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the impact on the user's budget if they agree to lend.
     *
     * Shows concrete numbers: "If you lend ₹10,000, your daily budget drops
     * from ₹800 to ₹400 for the next 14 days."
     *
     * @param amount Amount being requested
     * @param monthlyBudget User's total monthly budget
     * @param daysRemaining Days left in the current month
     * @return [BudgetImpact] with concrete numbers
     */
    fun calculateLendingImpact(
        amount: Double,
        monthlyBudget: Double,
        daysRemaining: Int
    ): BudgetImpact {
        val safeDays = maxOf(daysRemaining, 1)
        val currentDaily = monthlyBudget / safeDays
        val remainingAfterLending = monthlyBudget - amount
        val newDaily = maxOf(remainingAfterLending / safeDays, 0.0)

        val message = when {
            newDaily <= 0 -> "⚠️ If you lend ${formatCurrency(amount)}, you'd go OVER your budget. " +
                    "You'd need to cut into savings or borrow yourself."
            newDaily < currentDaily * 0.3 -> "⚠️ Your daily budget would drop from ${formatCurrency(currentDaily)} " +
                    "to just ${formatCurrency(newDaily)} for the next $safeDays days. That's extremely tight."
            newDaily < currentDaily * 0.6 -> "🟡 Your daily budget drops from ${formatCurrency(currentDaily)} " +
                    "to ${formatCurrency(newDaily)} for the next $safeDays days. Doable but you'll feel it."
            else -> "✅ Your daily budget goes from ${formatCurrency(currentDaily)} to " +
                    "${formatCurrency(newDaily)} for the next $safeDays days. Manageable."
        }

        return BudgetImpact(
            lendingAmount = amount,
            currentDailyBudget = currentDaily,
            newDailyBudget = newDaily,
            daysAffected = safeDays,
            impactMessage = message
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Lending History
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Records a lending event (when user decides to lend despite everything).
     *
     * @param record The lending record to save
     */
    fun addLendingRecord(record: LendingRecord) {
        val history = getLendingHistory().toMutableList()
        history.add(record)
        val json = gson.toJson(history)
        getPrefs().edit().putString(KEY_LENDING_HISTORY, json).apply()
    }

    /**
     * Gets all lending history.
     *
     * @return List of all lending records
     */
    fun getLendingHistory(): List<LendingRecord> {
        val json = getPrefs().getString(KEY_LENDING_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LendingRecord>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets total amount lent that hasn't been returned.
     *
     * @return Total outstanding amount
     */
    fun getTotalOutstandingLent(): Double {
        return getLendingHistory()
            .filter { !it.isFullyReturned }
            .sumOf { it.amount - it.returnedAmount }
    }

    /**
     * Gets lending summary for the current year.
     *
     * @return Human-readable summary
     */
    fun getLendingSummary(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearStart = Calendar.getInstance().apply {
            set(currentYear, Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis

        val thisYear = getLendingHistory().filter { it.date >= yearStart }
        val totalLent = thisYear.sumOf { it.amount }
        val totalReturned = thisYear.sumOf { it.returnedAmount }
        val outstanding = totalLent - totalReturned

        return if (thisYear.isEmpty()) {
            "No lending recorded this year. 👏"
        } else {
            buildString {
                appendLine("📊 Lending Summary (This Year)")
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("Total lent: ${formatCurrency(totalLent)}")
                appendLine("Returned: ${formatCurrency(totalReturned)}")
                appendLine("Still outstanding: ${formatCurrency(outstanding)}")
                appendLine("Number of times lent: ${thisYear.size}")
                if (outstanding > 0) {
                    appendLine()
                    appendLine("💡 ${formatCurrency(outstanding)} is still out there. " +
                            "A gentle reminder to borrowers might be overdue.")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Decline Tracking & Motivation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Records that the user successfully declined a money request.
     *
     * @param amount Amount that was requested but declined
     * @param scenarioType Type of situation
     */
    fun recordDecline(amount: Double, scenarioType: ScenarioType) {
        val monthKey = getMonthKey()
        val stats = getMonthlyStats(monthKey)
        val updated = stats.copy(
            declinedCount = stats.declinedCount + 1,
            totalAmountDeclined = stats.totalAmountDeclined + amount
        )
        saveMonthlyStats(monthKey, updated)
    }

    /**
     * Records that the user accepted a money request.
     *
     * @param amount Amount that was given
     */
    fun recordAcceptance(amount: Double) {
        val monthKey = getMonthKey()
        val stats = getMonthlyStats(monthKey)
        val updated = stats.copy(
            acceptedCount = stats.acceptedCount + 1,
            totalAmountAccepted = stats.totalAmountAccepted + amount
        )
        saveMonthlyStats(monthKey, updated)
    }

    /**
     * Gets a motivational message about money saved by saying no.
     *
     * @return Fun, encouraging stat message
     */
    fun getMotivationalMessage(): String {
        val monthKey = getMonthKey()
        val stats = getMonthlyStats(monthKey)

        return if (stats.declinedCount == 0 && stats.acceptedCount == 0) {
            "No money requests tracked this month yet. When someone asks, come here for help with what to say! 💪"
        } else if (stats.declinedCount == 0) {
            "You've said yes to ${stats.acceptedCount} requests (${formatCurrency(stats.totalAmountAccepted)}) this month. " +
                    "Remember: it's okay to say no too! Your financial health matters."
        } else {
            buildString {
                appendLine("💪 This month's scorecard:")
                appendLine()
                appendLine("🚫 You said 'No' to ${stats.declinedCount} requests")
                appendLine("💰 Money protected: ${formatCurrency(stats.totalAmountDeclined)}")
                if (stats.acceptedCount > 0) {
                    appendLine("🤝 Requests you chose to help with: ${stats.acceptedCount} (${formatCurrency(stats.totalAmountAccepted)})")
                }
                appendLine()
                appendLine("Your future self thanks you! 🙏 That ${formatCurrency(stats.totalAmountDeclined)} " +
                        "is still in YOUR account, working for YOUR goals.")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Persistence Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private fun getPrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    private fun getMonthKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${String.format("%02d", cal.get(Calendar.MONTH) + 1)}"
    }

    private fun getMonthlyStats(monthKey: String): MonthlyDeclineStats {
        val json = getPrefs().getString("stats_$monthKey", null) ?: return MonthlyDeclineStats(month = monthKey)
        return try {
            gson.fromJson(json, MonthlyDeclineStats::class.java)
        } catch (e: Exception) {
            MonthlyDeclineStats(month = monthKey)
        }
    }

    private fun saveMonthlyStats(monthKey: String, stats: MonthlyDeclineStats) {
        val json = gson.toJson(stats)
        getPrefs().edit().putString("stats_$monthKey", json).apply()
    }

    private fun formatCurrency(amount: Double): String {
        return if (amount >= 10000000) {
            "₹${String.format("%.1f", amount / 10000000)} Cr"
        } else if (amount >= 100000) {
            "₹${String.format("%.1f", amount / 100000)} L"
        } else {
            "₹${String.format("%,.0f", amount)}"
        }
    }
}
