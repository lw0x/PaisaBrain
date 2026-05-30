package com.paisabrain.app.parser

/**
 * CategoryClassifier - Rule-based + keyword classifier that maps merchant names
 * and transaction context to spending categories.
 *
 * NOTE: This file contains merchant/brand names as internal lookup keys for
 * classification purposes. These names are NEVER displayed in the UI —
 * only the resolved CATEGORY (e.g., "Food & Dining") is shown to users.
 *
 * Categories:
 * - Food & Dining
 * - Groceries
 * - Transport
 * - Shopping
 * - Bills & Utilities
 * - Entertainment
 * - Health & Fitness
 * - Education
 * - Travel
 * - Subscriptions
 * - Transfers
 * - ATM
 * - Investments
 * - Insurance
 * - Rent
 * - Fuel
 * - Others
 *
 * Usage:
 * ```kotlin
 * val classifier = CategoryClassifier()
 * val category = classifier.classify("Swiggy", SmsBankPatterns.TransactionMode.UPI, smsBody)
 * // Returns: "Food & Dining"
 * ```
 */
class CategoryClassifier {

    companion object {
        // Category constants
        const val FOOD_DINING = "Food & Dining"
        const val GROCERIES = "Groceries"
        const val TRANSPORT = "Transport"
        const val SHOPPING = "Shopping"
        const val BILLS_UTILITIES = "Bills & Utilities"
        const val ENTERTAINMENT = "Entertainment"
        const val HEALTH_FITNESS = "Health & Fitness"
        const val EDUCATION = "Education"
        const val TRAVEL = "Travel"
        const val SUBSCRIPTIONS = "Subscriptions"
        const val TRANSFERS = "Transfers"
        const val ATM = "ATM"
        const val INVESTMENTS = "Investments"
        const val INSURANCE = "Insurance"
        const val RENT = "Rent"
        const val FUEL = "Fuel"
        const val OTHERS = "Others"

        val ALL_CATEGORIES = listOf(
            FOOD_DINING, GROCERIES, TRANSPORT, SHOPPING, BILLS_UTILITIES,
            ENTERTAINMENT, HEALTH_FITNESS, EDUCATION, TRAVEL, SUBSCRIPTIONS,
            TRANSFERS, ATM, INVESTMENTS, INSURANCE, RENT, FUEL, OTHERS
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Classify a transaction into a spending category.
     *
     * @param merchantName The merchant/payee name from parsed SMS
     * @param transactionMode How the transaction was made (UPI, ATM, etc.)
     * @param body The full SMS body for additional context
     * @return Category string
     */
    fun classify(
        merchantName: String?,
        transactionMode: SmsBankPatterns.TransactionMode = SmsBankPatterns.TransactionMode.UNKNOWN,
        body: String? = null
    ): String {
        // Rule 1: ATM withdrawals are always "ATM"
        if (transactionMode == SmsBankPatterns.TransactionMode.ATM) {
            return ATM
        }

        // Rule 2: Check if body indicates ATM
        if (body != null && isAtmTransaction(body)) {
            return ATM
        }

        // Rule 3: Try merchant name matching (primary classifier)
        if (!merchantName.isNullOrBlank()) {
            val categoryFromMerchant = classifyByMerchant(merchantName)
            if (categoryFromMerchant != OTHERS) {
                return categoryFromMerchant
            }
        }

        // Rule 4: Try SMS body keyword matching (fallback)
        if (!body.isNullOrBlank()) {
            val categoryFromBody = classifyByBody(body)
            if (categoryFromBody != OTHERS) {
                return categoryFromBody
            }
        }

        // Rule 5: Mode-based fallback
        return when (transactionMode) {
            SmsBankPatterns.TransactionMode.ATM -> ATM
            SmsBankPatterns.TransactionMode.EMI -> SHOPPING // EMIs are usually for purchases
            SmsBankPatterns.TransactionMode.AUTO_DEBIT -> BILLS_UTILITIES
            else -> OTHERS
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MERCHANT-BASED CLASSIFICATION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun classifyByMerchant(merchantName: String): String {
        val normalized = merchantName.lowercase().trim()

        // Exact and contains matching against known merchants
        for ((category, merchants) in merchantKeywords) {
            for (keyword in merchants) {
                if (normalized.contains(keyword)) {
                    return category
                }
            }
        }

        // Pattern-based matching for edge cases
        for ((category, patterns) in merchantPatterns) {
            for (pattern in patterns) {
                if (pattern.containsMatchIn(normalized)) {
                    return category
                }
            }
        }

        return OTHERS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY-BASED CLASSIFICATION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun classifyByBody(body: String): String {
        val normalized = body.lowercase()

        for ((category, keywords) in bodyKeywords) {
            for (keyword in keywords) {
                if (normalized.contains(keyword)) {
                    return category
                }
            }
        }

        return OTHERS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ATM DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    private fun isAtmTransaction(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("atm") ||
            lower.contains("cash withdrawal") ||
            lower.contains("cash wdl") ||
            lower.contains("withdrawn from atm")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MERCHANT KEYWORD DATABASE
    // ═══════════════════════════════════════════════════════════════════════════

    private val merchantKeywords: Map<String, List<String>> = mapOf(

        FOOD_DINING to listOf(
            // Food delivery apps
            "swiggy", "zomato", "dunzo", "eatsure", "box8", "faasos",
            "behrouz", "oven story", "freshmenu", "rebel foods",
            "burger king", "mcdonald", "mcdonalds", "dominos", "domino's",
            "pizza hut", "pizzahut", "kfc", "subway", "starbucks",
            "cafe coffee day", "ccd", "barista", "chaayos", "chai point",
            "barbeque nation", "haldiram", "haldirams", "bikanervala",
            "sagar ratna", "saravana bhavan", "paradise biryani",
            "wow momo", "mojo pizza", "la pino'z", "lapinoz",
            // Restaurant keywords
            "restaurant", "cafe", "dhaba", "bistro", "kitchen",
            "food", "biryani", "pizza", "burger", "bakery",
            "sweet", "mithai", "snacks", "eatery", "diner",
            "hotel", "mess", "canteen", "tiffin",
            // Specific Indian restaurants/chains
            "social", "impresario", "mainland china", "ohris",
            "barbeque", "bbq", "punjabi", "mughal", "tandoor"
        ),

        GROCERIES to listOf(
            // Online grocery
            "bigbasket", "big basket", "blinkit", "grofers", "jiomart",
            "swiggy instamart", "instamart", "zepto", "dunzo daily",
            "amazon fresh", "flipkart grocery", "dmart ready",
            "milkbasket", "supr daily", "country delight", "licious",
            "freshtohome", "fresh to home", "meatigo", "zappfresh",
            // Offline grocery/supermarkets
            "dmart", "d-mart", "big bazaar", "bigbazaar", "reliance fresh",
            "reliance smart", "more supermarket", "star bazaar", "spar",
            "nature's basket", "organic", "spencer", "spencers",
            "easy day", "heritage fresh", "ratnadeep", "nilgiris",
            "metro cash", "walmart", "vishal mega mart",
            // Keywords
            "grocery", "groceries", "supermarket", "kirana", "provision",
            "vegetables", "fruits", "dairy", "milk", "bread"
        ),

        TRANSPORT to listOf(
            // Ride-hailing
            "uber", "ola", "ola cabs", "rapido", "namma yatri",
            "meru", "mega cabs", "blu smart", "blusmart",
            // Auto/taxi
            "auto", "taxi", "cab", "rickshaw",
            // Public transport
            "metro", "dmrc", "bmrc", "mmrc", "irctc", "railways",
            "redbus", "red bus", "abhibus", "ksrtc", "tsrtc",
            "upsrtc", "msrtc", "gsrtc",
            // Parking
            "parking", "park plus", "parkplus", "get my parking",
            // Toll
            "fastag", "toll", "nhai"
        ),

        SHOPPING to listOf(
            // E-commerce
            "amazon", "flipkart", "myntra", "ajio", "nykaa",
            "meesho", "snapdeal", "shopclues", "tatacliq", "tata cliq",
            "firstcry", "bewakoof", "limeroad", "koovs",
            "purplle", "mamaearth", "sugar cosmetics", "plum",
            // Fashion/Lifestyle
            "zara", "h&m", "uniqlo", "lifestyle", "shoppers stop",
            "westside", "pantaloons", "max fashion", "fbb",
            "central", "brand factory", "reliance trends",
            "decathlon", "croma", "vijay sales", "reliance digital",
            // Electronics
            "samsung", "apple", "oneplus", "xiaomi", "realme",
            "boat", "noise", "lenovo", "hp", "dell",
            // Home/furniture
            "ikea", "pepperfry", "urban ladder", "hometown",
            "fabindia", "home centre", "at home",
            // Eyewear
            "lenskart", "titan eye", "john jacobs",
            // Generic
            "mall", "mart", "store", "shop", "emporium", "bazaar"
        ),

        BILLS_UTILITIES to listOf(
            // Electricity
            "electricity", "bescom", "tpddl", "bses", "msedcl",
            "tneb", "tangedco", "cesc", "torrent power", "adani gas",
            "mahadiscom", "ugvcl",
            // Water
            "water bill", "jal board", "water supply",
            // Gas
            "gas bill", "indane", "hp gas", "bharat gas",
            "mahanagar gas", "adani gas", "igl",
            // Telecom/Internet
            "airtel", "jio", "vi ", "vodafone", "idea",
            "bsnl", "mtnl", "act fibernet", "hathway",
            "tata play", "d2h", "dish tv", "airtel xstream",
            "tikona", "excitel", "spectra",
            // DTH
            "dth", "tatasky", "sun direct",
            // Housing society
            "society", "maintenance", "apartment",
            "mygate", "nobrokerhood", "apnacomplex",
            // Municipal
            "municipal", "corporation", "property tax", "bbmp",
            // Broadband
            "broadband", "internet", "wifi", "fiber"
        ),

        ENTERTAINMENT to listOf(
            // Streaming
            "netflix", "hotstar", "disney", "prime video",
            "sonyliv", "zee5", "voot", "mxplayer", "alt balaji",
            "discovery+", "apple tv", "youtube premium", "yt premium",
            // Music
            "spotify", "gaana", "jiosaavn", "saavn", "wynk",
            "apple music", "amazon music",
            // Movies/Events
            "bookmyshow", "book my show", "paytm movies", "pvr",
            "inox", "cinepolis", "carnival cinemas",
            // Gaming
            "steam", "playstation", "xbox", "epic games",
            "google play", "app store", "play store",
            // Events
            "insider", "zomato events", "event", "concert", "show"
        ),

        HEALTH_FITNESS to listOf(
            // Pharmacy
            "1mg", "tata 1mg", "pharmeasy", "netmeds", "medlife",
            "apollo pharmacy", "medplus", "wellness forever",
            // Healthcare
            "practo", "apollo", "max hospital", "fortis",
            "medanta", "manipal", "narayana health", "aster",
            "columbia asia", "cloudnine", "rainbow hospital",
            // Lab/diagnostic
            "thyrocare", "lal path labs", "dr lal", "srl diagnostics",
            "metropolis", "redcliffe",
            // Fitness
            "cult.fit", "cultfit", "cure.fit", "curefit",
            "gold gym", "anytime fitness", "fitness first",
            "healthifyme", "fitternity",
            // Eye care
            "lenskart", "titan eye",
            // Generic
            "hospital", "clinic", "doctor", "medical", "pharmacy",
            "chemist", "diagnostic", "lab ", "pathology",
            "dental", "dentist", "physiotherapy", "gym", "yoga"
        ),

        EDUCATION to listOf(
            // Ed-tech
            "byju", "byjus", "unacademy", "vedantu", "toppr",
            "upgrad", "upGrad", "simplilearn", "coursera", "udemy",
            "great learning", "scaler", "coding ninjas", "whitehat",
            "edx", "skillshare", "masterclass",
            // Schools/coaching
            "school", "college", "university", "institute",
            "academy", "coaching", "tuition", "tutorial",
            "fiitjee", "allen", "aakash", "resonance",
            // Books
            "amazon kindle", "kindle",
            // Exam
            "exam fee", "registration fee", "admission"
        ),

        TRAVEL to listOf(
            // Flight booking
            "makemytrip", "mmt", "goibibo", "cleartrip",
            "yatra", "easemytrip", "ixigo", "skyscanner",
            "indigo", "spicejet", "air india", "vistara", "akasa",
            "go first", "air asia",
            // Hotels
            "oyo", "treebo", "fabhotels", "zostel",
            "taj hotel", "oberoi", "itc hotel", "lemon tree",
            "marriott", "hyatt", "hilton", "radisson",
            "airbnb", "booking.com", "agoda",
            // Train
            "irctc", "confirmtkt", "trainman", "railyatri",
            // Bus
            "redbus", "abhibus", "bus ticket",
            // Cab for travel
            "airport", "flight",
            // Generic
            "hotel", "resort", "travel", "tour", "holiday",
            "visa", "passport"
        ),

        SUBSCRIPTIONS to listOf(
            // News
            "times prime", "amazon prime", "prime membership",
            // Software
            "microsoft 365", "office 365", "google one",
            "icloud", "dropbox", "notion", "canva",
            "adobe", "figma",
            // VPN
            "nordvpn", "expressvpn", "surfshark",
            // Others
            "subscription", "membership", "renewal",
            "annual plan", "monthly plan", "premium"
        ),

        TRANSFERS to listOf(
            // Self-transfer indicators
            "self transfer", "own account", "self a/c",
            "fund transfer", "neft", "imps", "rtgs",
            "bank transfer", "transferred to",
            // Payment to individuals (UPI IDs as merchants)
            "@ybl", "@paytm", "@oksbi", "@okhdfcbank",
            "@okicici", "@okaxis", "@upi", "@apl",
            "@axl", "@ibl", "@sbi", "@icici"
        ),

        ATM to listOf(
            "atm", "cash withdrawal", "cash wdl",
            "atm withdrawal", "atm-cum-debit"
        ),

        INVESTMENTS to listOf(
            // Mutual funds
            "groww", "zerodha", "kuvera", "coin by zerodha",
            "paytm money", "et money", "etmoney", "scripbox",
            "smallcase", "upstox", "angel one", "angel broking",
            "5paisa", "motilal oswal", "icicidirect", "hdfc securities",
            "kotak securities", "sbi securities",
            // Stocks/trading
            "demat", "trading", "share", "stock", "equity",
            "mutual fund", "mf", "sip",
            // Crypto
            "wazirx", "coinswitch", "coindcx", "zebpay",
            // Gold
            "digital gold", "sovereign gold", "gold bond",
            "jar app", "safegold"
        ),

        INSURANCE to listOf(
            "lic", "life insurance", "health insurance",
            "motor insurance", "car insurance", "bike insurance",
            "star health", "hdfc ergo", "icici lombard",
            "bajaj allianz", "tata aia", "max life",
            "sbi life", "acko", "digit insurance",
            "policybazaar", "policy bazaar",
            "insurance", "premium", "policy"
        ),

        RENT to listOf(
            "rent", "house rent", "flat rent", "pg ",
            "paying guest", "nobroker", "magicbricks",
            "99acres", "housing.com", "nestaway",
            "landlord", "owner"
        ),

        FUEL to listOf(
            // Petrol pumps
            "petrol", "diesel", "fuel", "hp petrol",
            "indian oil", "iocl", "bharat petroleum", "bpcl",
            "hindustan petroleum", "hpcl", "shell",
            "reliance petroleum", "nayara", "essar",
            // EV charging
            "ev charging", "ather", "tata power ez",
            "charge zone", "statiq"
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PATTERN-BASED MATCHING (for edge cases)
    // ═══════════════════════════════════════════════════════════════════════════

    private val merchantPatterns: Map<String, List<Regex>> = mapOf(

        FOOD_DINING to listOf(
            Regex("""(?i)\b(?:rest(?:aurant)?|cafe|kitchen|food|biryani|pizza|chai)\b"""),
            Regex("""(?i)\b(?:eat|dine|dining|meals?|lunch|dinner|breakfast)\b""")
        ),

        TRANSPORT to listOf(
            Regex("""(?i)\b(?:uber\s*(?:india|go|auto)|ola\s*(?:auto|mini|prime))\b"""),
            Regex("""(?i)\b(?:metro\s*(?:rail|card|recharge))\b"""),
            Regex("""(?i)\b(?:fastag|toll\s*(?:plaza|booth|charges?))\b""")
        ),

        BILLS_UTILITIES to listOf(
            Regex("""(?i)\b(?:electricity|power|energy)\s*(?:bill|board|dept)\b"""),
            Regex("""(?i)\b(?:recharge|prepaid|postpaid|plan)\b"""),
            Regex("""(?i)\b(?:bill\s*(?:pay(?:ment)?|desk))\b""")
        ),

        TRANSFERS to listOf(
            Regex("""(?i)\b\d{10}\b"""), // Phone number as merchant = likely P2P transfer
            Regex("""(?i)\b[a-z]+@(?:ybl|paytm|okaxis|oksbi|okhdfcbank|upi|apl|axl|ibl)\b""") // UPI VPA
        ),

        INVESTMENTS to listOf(
            Regex("""(?i)\b(?:sip|mutual\s*fund|mf|demat|stock|share|equity)\b"""),
            Regex("""(?i)\b(?:invest(?:ment)?|portfolio|nfo|nav)\b""")
        ),

        SHOPPING to listOf(
            Regex("""(?i)\b(?:amazon|flipkart|myntra)\.(?:in|com)\b"""),
            Regex("""(?i)\b(?:mall|mart|store|shop|retail|outlet)\b""")
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // BODY KEYWORD MATCHING (last resort)
    // ═══════════════════════════════════════════════════════════════════════════

    private val bodyKeywords: Map<String, List<String>> = mapOf(
        ATM to listOf("atm withdrawal", "atm cash", "cash withdrawn", "atm wdl"),
        FUEL to listOf("petrol pump", "fuel station", "hp pump", "iocl", "bpcl"),
        RENT to listOf("rent payment", "house rent", "monthly rent"),
        INSURANCE to listOf("insurance premium", "policy premium"),
        BILLS_UTILITIES to listOf(
            "bill payment", "electricity bill", "gas bill", "water bill",
            "recharge successful", "plan activated"
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // ADVANCED CLASSIFICATION UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Get confidence score for a classification (0.0 to 1.0).
     * Higher score = more confident about the category assignment.
     */
    fun getConfidence(
        merchantName: String?,
        category: String
    ): Float {
        if (merchantName.isNullOrBlank()) return 0.3f

        val normalized = merchantName.lowercase().trim()
        val merchants = merchantKeywords[category] ?: return 0.3f

        // Exact match of a well-known merchant = highest confidence
        for (keyword in merchants) {
            if (normalized == keyword || normalized.startsWith(keyword)) {
                return 0.95f
            }
        }

        // Contains match = moderate confidence
        for (keyword in merchants) {
            if (normalized.contains(keyword)) {
                return 0.80f
            }
        }

        // Pattern match = lower confidence
        val patterns = merchantPatterns[category]
        if (patterns != null) {
            for (pattern in patterns) {
                if (pattern.containsMatchIn(normalized)) {
                    return 0.65f
                }
            }
        }

        return 0.3f
    }

    /**
     * Get top N likely categories for a merchant, sorted by confidence.
     * Useful for letting users pick from suggestions.
     */
    fun suggestCategories(
        merchantName: String?,
        transactionMode: SmsBankPatterns.TransactionMode = SmsBankPatterns.TransactionMode.UNKNOWN,
        body: String? = null,
        topN: Int = 3
    ): List<Pair<String, Float>> {
        if (merchantName.isNullOrBlank() && body.isNullOrBlank()) {
            return listOf(OTHERS to 0.5f)
        }

        val scores = mutableMapOf<String, Float>()

        // Score based on merchant name
        if (!merchantName.isNullOrBlank()) {
            val normalized = merchantName.lowercase().trim()
            for ((category, merchants) in merchantKeywords) {
                var maxScore = 0f
                for (keyword in merchants) {
                    val score = when {
                        normalized == keyword -> 0.95f
                        normalized.startsWith(keyword) -> 0.90f
                        normalized.contains(keyword) -> 0.75f
                        else -> 0f
                    }
                    maxScore = maxOf(maxScore, score)
                }
                if (maxScore > 0f) {
                    scores[category] = maxOf(scores.getOrDefault(category, 0f), maxScore)
                }
            }
        }

        // Score based on body keywords
        if (!body.isNullOrBlank()) {
            val bodyLower = body.lowercase()
            for ((category, keywords) in bodyKeywords) {
                for (keyword in keywords) {
                    if (bodyLower.contains(keyword)) {
                        scores[category] = maxOf(scores.getOrDefault(category, 0f), 0.60f)
                    }
                }
            }
        }

        // Mode-based scoring
        when (transactionMode) {
            SmsBankPatterns.TransactionMode.ATM -> scores[ATM] = maxOf(scores.getOrDefault(ATM, 0f), 0.95f)
            SmsBankPatterns.TransactionMode.EMI -> scores[SHOPPING] = maxOf(scores.getOrDefault(SHOPPING, 0f), 0.60f)
            SmsBankPatterns.TransactionMode.AUTO_DEBIT -> scores[BILLS_UTILITIES] = maxOf(scores.getOrDefault(BILLS_UTILITIES, 0f), 0.55f)
            else -> { /* no-op */ }
        }

        // Always include "Others" as fallback
        scores.putIfAbsent(OTHERS, 0.3f)

        return scores.entries
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.key to it.value }
    }

    /**
     * Learn from user corrections: returns keywords to add for future improvement.
     * In production, persist these to SharedPreferences or database.
     */
    data class CorrectionSuggestion(
        val merchantName: String,
        val correctCategory: String,
        val suggestedKeyword: String
    )

    fun analyzeCorrectionForLearning(
        merchantName: String,
        incorrectCategory: String,
        correctCategory: String
    ): CorrectionSuggestion {
        // Extract the most identifying part of the merchant name
        val normalized = merchantName.lowercase().trim()
        val suggestedKeyword = when {
            normalized.length <= 10 -> normalized
            normalized.contains(" ") -> normalized.split(" ").first()
            else -> normalized.take(10)
        }

        return CorrectionSuggestion(
            merchantName = merchantName,
            correctCategory = correctCategory,
            suggestedKeyword = suggestedKeyword
        )
    }
}
