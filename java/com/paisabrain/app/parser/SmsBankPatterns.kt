package com.paisabrain.app.parser

/**
 * SmsBankPatterns - Comprehensive regex patterns for Indian bank SMS parsing.
 * Covers 50+ banks and all major UPI/payment apps.
 *
 * Pattern groups extract: amount, transaction type (credit/debit), merchant/payee,
 * account last 4 digits, and available balance after transaction.
 *
 * NOTE: Internal technical file. Bank sender IDs and regex patterns are system
 * constants required for SMS parsing — never displayed to the user.
 */
object SmsBankPatterns {

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON AMOUNT PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Matches Indian currency amounts: Rs.1,234.56 / INR 1234.56 / Rs 1,23,456 */
    const val AMOUNT_PATTERN = """(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""

    /** Matches balance amounts */
    const val BALANCE_PATTERN = """(?:(?:Avl\.?\s*Bal|Available\s*(?:Bal(?:ance)?)|Bal(?:ance)?|A/c\s*bal|Avbl\s*Bal|Curr\s*Bal|Net\s*Bal)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)|(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is\s+)?(?:available|avl))"""

    /** Matches account/card last 4 digits */
    const val ACCOUNT_PATTERN = """(?:A/?c|Acct?|Card|a/c\s*no|account)[\s.*:]*(?:no\.?\s*)?[xX*]+\s*(\d{4})"""

    /** Alternate account pattern: XX1234 */
    const val ACCOUNT_ALT_PATTERN = """(?:XX|xx|[xX*]{2,})(\d{4})"""

    /** UPI reference pattern */
    const val UPI_REF_PATTERN = """(?:UPI\s*(?:Ref|ref|ID|id)[\s:]*(\d+))"""

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION TYPE INDICATORS
    // ═══════════════════════════════════════════════════════════════════════════

    val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "spent", "paid", "payment", "purchase",
        "withdrawn", "withdrawal", "transferred", "sent", "amt sent",
        "charged", "deducted", "dr", "used at", "emi of", "auto-debit",
        "bill payment", "txn of", "transaction of", "bought", "shopping"
    )

    val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "deposited", "refund",
        "reversed", "cashback", "cr", "added", "amt received",
        "salary", "incoming", "transferred to your", "money received",
        "settled", "reward"
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // BANK-SPECIFIC PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    data class BankPattern(
        val bankName: String,
        val senderIds: List<String>,
        val debitPatterns: List<Regex>,
        val creditPatterns: List<Regex>,
        val balancePattern: Regex? = null
    )

    /**
     * Master list of bank patterns. Each bank has sender IDs and transaction regex patterns.
     * Named groups used: amount, account, merchant, balance
     */
    val bankPatterns: List<BankPattern> = listOf(

        // ─── SBI (State Bank of India) ───
        BankPattern(
            bankName = "SBI",
            senderIds = listOf("SBIBNK", "SBIPSG", "SBIINB", "SBISMT", "ATMSBI", "SBI"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*spent\s*(?:on|at|via)\s*(?:your\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*at\s*(?<merchant>.+?)(?:\s*on|\s*Avl|\.)"""),
                Regex("""(?i)Your\s*(?:A/?c|account)\s*[xX*]*(?<account>\d{4})\s*(?:has been |is )?debited\s*(?:by|with|for)?\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)"""),
                Regex("""(?i)Amt\s*Sent\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:From\s*(?:A/?c)?\s*[xX*]*(?<account>\d{4}))?\s*To\s*(?<merchant>.+?)(?:\s*UPI|\s*Ref|\s*on|\.)"""),
                Regex("""(?i)ATM\s*(?:cash\s*)?(?:withdrawal|WDL)\s*(?:of\s*)?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:from\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:A/?c|account)\s*[xX*]*(?<account>\d{4})\s*(?:has been |is )?credited\s*(?:by|with)?\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:received|deposited)\s*(?:in\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})\s*(?:from|by)\s*(?<merchant>.+?)(?:\s*UPI|\s*Ref|\s*on|\.)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Avbl\s*Bal|Available\s*Bal(?:ance)?|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── HDFC Bank ───
        BankPattern(
            bankName = "HDFC Bank",
            senderIds = listOf("HDFCBK", "HDFCBN", "HDFCCC", "ABORHD", "HDFC"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?debited\s*(?:from\s*)?(?:your\s*)?(?:HDFC\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*spent\s*on\s*(?:your\s*)?(?:HDFC\s*Bank\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*at\s*(?<merchant>.+?)(?:\s*on|\s*Avl|\.)"""),
                Regex("""(?i)Money\s*Sent!\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*sent\s*to\s*(?<merchant>.+?)(?:\s*from|\s*A/?c)"""),
                Regex("""(?i)HDFC\s*Bank:\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:debited|spent|paid)\s*(?:from|on|to|at)\s*(?:A/?c\s*[xX*]*(?<account>\d{4}))?\s*(?:at|to|for)?\s*(?<merchant>.+?)(?:\s*on|\s*Avl|\.)"""),
                Regex("""(?i)Alert!\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?debited\s*from\s*(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*(?:to|at|for)\s*(?<merchant>.+?)(?:\s*on|\s*Avl|\.)"""),
                Regex("""(?i)Your\s*HDFC\s*Bank\s*Credit\s*Card\s*[xX*]*(?<account>\d{4})\s*(?:has been |was )?charged\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|on|for)\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?credited\s*(?:to\s*)?(?:your\s*)?(?:HDFC\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Money\s*Received!\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*from\s*(?<merchant>.+?)(?:\s*in|\s*to)"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*(?:in\s*)?(?:your\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*(?:from|by)\s*(?<merchant>.+?)(?:\s*UPI|\s*Ref|\.)"""),
                Regex("""(?i)Refund\s*of\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*(?:Bal|Lmt)|Available\s*(?:Bal(?:ance)?|Limit)|Bal(?:ance)?|Net\s*Avl\s*Bal)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── ICICI Bank ───
        BankPattern(
            bankName = "ICICI Bank",
            senderIds = listOf("ICICIB", "ICICBK", "ICICIT", "ICICCC", "ICICI"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:ICICI\s*Bank\s*)?(?:A/?c|Acct?)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:ICICI\s*Bank\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*(?:has been |was )?used\s*(?:for|at)\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*at\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*sent\s*to\s*(?<merchant>.+?)\s*from\s*(?:ICICI\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)ICICI\s*Bank\s*Acct\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:on\s*.+?)?\s*(?:Info:?\s*(?<merchant>.+?))?(?:\s*Avl|\.)"""),
                Regex("""(?i)EMI\s*of\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?debited\s*from\s*(?:your\s*)?(?:ICICI\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:ICICI\s*Bank\s*)?(?:A/?c|Acct?)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)ICICI\s*Bank\s*Acct\s*[xX*]*(?<account>\d{4})\s*credited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:on\s*.+?)?\s*(?:Info:?\s*(?<merchant>.+?))?(?:\s*Avl|\.)"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*from\s*(?<merchant>.+?)\s*(?:in|to)\s*(?:ICICI\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Available\s*Bal(?:ance)?|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Axis Bank ───
        BankPattern(
            bankName = "Axis Bank",
            senderIds = listOf("AXISBK", "AXISCR", "AXISCC", "UTIBKK", "AXIS"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Axis\s*Bank\s*)?(?:A/?c|Acct?)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Txn\s*of\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*done\s*(?:on|from)\s*(?:Axis\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*(?:at|to|for)\s*(?<merchant>.+?)(?:\s*on|\s*Avl|\.)"""),
                Regex("""(?i)Your\s*(?:Axis\s*Bank\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*(?:was|has been)\s*(?:used|charged)\s*for\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*at\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:was |has been )?sent\s*to\s*(?<merchant>.+?)\s*from\s*(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Axis\s*Bank\s*)?(?:A/?c|Acct?)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*(?:in\s*)?(?:your\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*from\s*(?<merchant>.+?)(?:\s*Ref|\.)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Available\s*Bal(?:ance)?|Bal)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Kotak Mahindra Bank ───
        BankPattern(
            bankName = "Kotak Bank",
            senderIds = listOf("KOTAKB", "KOTKBK", "KOTAKC", "KOTAK"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Kotak\s*(?:Bank\s*)?)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Kotak\s*Bank\s*A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*for\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|to|for)?\s*(?<merchant>.+?)(?:\s*on|\s*Bal|\.)"""),
                Regex("""(?i)Your\s*(?:Kotak\s*Bank\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*.*?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*at\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)Sent\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?:Kotak\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*to\s*(?<merchant>.+?)(?:\s*UPI|\s*Ref|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Kotak\s*(?:Bank\s*)?)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Kotak\s*Bank\s*A/?c\s*[xX*]*(?<account>\d{4})\s*credited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:by|from)\s*(?<merchant>.+?)(?:\s*Bal|\.)"""),
                Regex("""(?i)Received\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:in\s*)?(?:Kotak\s*Bank\s*)?(?:A/?c)?\s*[xX*]*(?<account>\d{4})\s*from\s*(?<merchant>.+?)(?:\s*UPI|\.)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── PNB (Punjab National Bank) ───
        BankPattern(
            bankName = "PNB",
            senderIds = listOf("PNBSMS", "PUNBNK", "PNBBNK", "PNB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:PNB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:PNB\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*(?:has been |is )?debited\s*(?:by|with|for)\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to|at|for)?\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)Dear\s*Customer,?\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited.*?(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:PNB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:PNB\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*(?:has been |is )?credited\s*(?:by|with)\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Bank of Baroda (BOB) ───
        BankPattern(
            bankName = "Bank of Baroda",
            senderIds = listOf("BOBALR", "BOBSMS", "BABORB", "BOB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:BOB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:BOB\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:for|to|at)\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:BOB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:BOB\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*credited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── IDFC First Bank ───
        BankPattern(
            bankName = "IDFC First Bank",
            senderIds = listOf("IDFCFB", "IDFCBK", "IDFC"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:IDFC\s*(?:First\s*)?Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)IDFC\s*FIRST\s*Bank\s*A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:for\s*)?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|to|for)\s*(?<merchant>.+?)(?:\s*on|\s*Bal|\.)"""),
                Regex("""(?i)Your\s*(?:IDFC\s*FIRST\s*Bank\s*)?Card\s*[xX*]*(?<account>\d{4})\s*.*?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|to)\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:IDFC\s*(?:First\s*)?Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)IDFC\s*FIRST\s*Bank\s*A/?c\s*[xX*]*(?<account>\d{4})\s*credited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Yes Bank ───
        BankPattern(
            bankName = "Yes Bank",
            senderIds = listOf("YESBKL", "YESBNK", "YESBOK", "YES"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Yes\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:Yes\s*Bank\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:for|to)\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Yes\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── IndusInd Bank ───
        BankPattern(
            bankName = "IndusInd Bank",
            senderIds = listOf("INDBNK", "IDBKCC", "INDUSB", "INDUS"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:IndusInd\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:IndusInd\s*Bank\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(?<account>\d{4})\s*.*?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*at\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)IndusInd\s*Bank.*?debited.*?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?:A/?c)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:IndusInd\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Federal Bank ───
        BankPattern(
            bankName = "Federal Bank",
            senderIds = listOf("FEDBNK", "FEDBKL", "FEDRLB", "FEDERA"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Federal\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:Federal\s*Bank\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:for|to)\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Federal\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── RBL Bank ───
        BankPattern(
            bankName = "RBL Bank",
            senderIds = listOf("RBLBNK", "RATBKL", "RBLBCC", "RBL"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:RBL\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:RBL\s*Bank\s*)?Card\s*[xX*]*(?<account>\d{4})\s*.*?(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*at\s*(?<merchant>.+?)(?:\s*on|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:RBL\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Union Bank of India ───
        BankPattern(
            bankName = "Union Bank",
            senderIds = listOf("UNIONB", "UBIBNK", "UBINBK", "UNION"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Union\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Union\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Canara Bank ───
        BankPattern(
            bankName = "Canara Bank",
            senderIds = listOf("CANBKL", "CANBNK", "CANARA", "CNRBKL"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Canara\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Your\s*(?:Canara\s*Bank\s*)?A/?c\s*[xX*]*(?<account>\d{4})\s*debited\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Canara\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Bank of India (BOI) ───
        BankPattern(
            bankName = "Bank of India",
            senderIds = listOf("BOIIND", "BKIDNK", "BOIBNK", "BOI"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:BOI\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:BOI\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Indian Overseas Bank (IOB) ───
        BankPattern(
            bankName = "IOB",
            senderIds = listOf("IOBBNK", "IOBSMS", "IOB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:IOB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:IOB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Central Bank of India ───
        BankPattern(
            bankName = "Central Bank",
            senderIds = listOf("CBIBNK", "CBISMS", "CENTRL"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Indian Bank ───
        BankPattern(
            bankName = "Indian Bank",
            senderIds = listOf("INDBNK", "INDBKL", "INDIAN"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Indian\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Indian\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── UCO Bank ───
        BankPattern(
            bankName = "UCO Bank",
            senderIds = listOf("UCOBNK", "UCOBKL", "UCO"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:UCO\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:UCO\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── IDBI Bank ───
        BankPattern(
            bankName = "IDBI Bank",
            senderIds = listOf("IDBIBN", "IDBIBK", "IDBI"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:IDBI\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:IDBI\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Bandhan Bank ───
        BankPattern(
            bankName = "Bandhan Bank",
            senderIds = listOf("BANDHB", "BNDHAN", "BANDHN"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Bandhan\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Bandhan\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── South Indian Bank ───
        BankPattern(
            bankName = "South Indian Bank",
            senderIds = listOf("SIBBNK", "STHIND", "SIB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:SIB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:SIB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Karnataka Bank ───
        BankPattern(
            bankName = "Karnataka Bank",
            senderIds = listOf("KTKBNK", "KRNTKB", "KARNTK"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Karur Vysya Bank (KVB) ───
        BankPattern(
            bankName = "KVB",
            senderIds = listOf("KVBBNK", "KVBANK", "KVB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:KVB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:KVB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── City Union Bank ───
        BankPattern(
            bankName = "City Union Bank",
            senderIds = listOf("CUBBNK", "CITIUB", "CUB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:CUB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:CUB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── DCB Bank ───
        BankPattern(
            bankName = "DCB Bank",
            senderIds = listOf("DCBBNK", "DCB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:DCB\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:DCB\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Jammu & Kashmir Bank ───
        BankPattern(
            bankName = "J&K Bank",
            senderIds = listOf("JKBANK", "JKBSMS", "JKB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:J&?K\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:J&?K\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Tamilnad Mercantile Bank ───
        BankPattern(
            bankName = "TMB",
            senderIds = listOf("TMBBNK", "TMBANK", "TMB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:TMB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:TMB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── CSB Bank (Catholic Syrian Bank) ───
        BankPattern(
            bankName = "CSB Bank",
            senderIds = listOf("CSBBNK", "CSBANK", "CSB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:CSB\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:CSB\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Dhanlaxmi Bank ───
        BankPattern(
            bankName = "Dhanlaxmi Bank",
            senderIds = listOf("DHNLXM", "DLXBNK"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Punjab & Sind Bank ───
        BankPattern(
            bankName = "Punjab & Sind Bank",
            senderIds = listOf("PSBBNK", "PNSNDB", "PSB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Bank of Maharashtra ───
        BankPattern(
            bankName = "Bank of Maharashtra",
            senderIds = listOf("BOMBNK", "BOMSMS", "BOM"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── AU Small Finance Bank ───
        BankPattern(
            bankName = "AU Small Finance Bank",
            senderIds = listOf("AUSFBK", "AUBANK", "AUSFIN"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:AU\s*(?:Small\s*Finance\s*)?Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:AU\s*(?:Small\s*Finance\s*)?Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Equitas Small Finance Bank ───
        BankPattern(
            bankName = "Equitas SFB",
            senderIds = listOf("EQTSFB", "EQUITS", "EQTSMS"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Ujjivan Small Finance Bank ───
        BankPattern(
            bankName = "Ujjivan SFB",
            senderIds = listOf("UJJVNB", "UJJIVN"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Fino Payments Bank ───
        BankPattern(
            bankName = "Fino Payments Bank",
            senderIds = listOf("FINOPB", "FINOBN"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Paytm Payments Bank ───
        BankPattern(
            bankName = "Paytm Payments Bank",
            senderIds = listOf("PYTMPB", "PAYTMB", "PAYTM"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Paytm\s*)?(?:A/?c|account|wallet)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Paid\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to\s*)?(?<merchant>.+?)(?:\s*from|\s*UPI|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Paytm\s*)?(?:A/?c|account|wallet)?\s*[xX*]*(?<account>\d{4})"""),
                Regex("""(?i)Received\s*(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?<merchant>.+?)(?:\s*in|\.)""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Airtel Payments Bank ───
        BankPattern(
            bankName = "Airtel Payments Bank",
            senderIds = listOf("AIRPAY", "AIRTLB", "ARTLPB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:Airtel\s*Payments?\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:Airtel\s*Payments?\s*Bank\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── India Post Payments Bank ───
        BankPattern(
            bankName = "India Post Payments Bank",
            senderIds = listOf("IPPBNK", "IPPBSM", "IPPB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:IPPB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:IPPB\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Suryoday Small Finance Bank ───
        BankPattern(
            bankName = "Suryoday SFB",
            senderIds = listOf("SURYOD", "SRYDSF"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── Jana Small Finance Bank ───
        BankPattern(
            bankName = "Jana SFB",
            senderIds = listOf("JANABN", "JANASF"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        ),

        // ─── ESAF Small Finance Bank ───
        BankPattern(
            bankName = "ESAF SFB",
            senderIds = listOf("ESAFBK", "ESAFSF"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?debited\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(?<account>\d{4})""")
            ),
            balancePattern = Regex("""(?i)(?:Avl\.?\s*Bal|Bal(?:ance)?)[\s:]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // UPI APP PATTERNS
    // ═══════════════════════════════════════════════════════════════════════════

    data class UpiAppPattern(
        val appName: String,
        val senderIds: List<String>,
        val debitPatterns: List<Regex>,
        val creditPatterns: List<Regex>
    )

    val upiAppPatterns: List<UpiAppPattern> = listOf(

        // ─── PhonePe ───
        UpiAppPattern(
            appName = "PhonePe",
            senderIds = listOf("PHONEPE", "PHNEPE", "PHNPE"),
            debitPatterns = listOf(
                Regex("""(?i)Paid\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*to\s*(?<merchant>.+?)(?:\s*on|\s*from|\s*UPI|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*sent\s*to\s*(?<merchant>.+?)(?:\s*via|\s*UPI|\.)"""),
                Regex("""(?i)Payment\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to|for)\s*(?<merchant>.+?)(?:\s*successful|\s*done|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Received\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?<merchant>.+?)(?:\s*on|\s*in|\s*via|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*from\s*(?<merchant>.+?)(?:\s*via|\.)""")
            )
        ),

        // ─── Google Pay (GPay) ───
        UpiAppPattern(
            appName = "Google Pay",
            senderIds = listOf("GPAY", "GOOGPE", "TEZPAY", "GOOGLE"),
            debitPatterns = listOf(
                Regex("""(?i)(?:You|U)\s*(?:have\s*)?(?:paid|sent)\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*to\s*(?<merchant>.+?)(?:\s*on|\s*from|\.)"""),
                Regex("""(?i)Payment\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to|for)\s*(?<merchant>.+?)(?:\s*successful|\s*done|\.)"""),
                Regex("""(?i)Sent\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*to\s*(?<merchant>.+?)(?:\s*UPI|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:You|U)\s*(?:have\s*)?received\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*received\s*from\s*(?<merchant>.+?)(?:\s*via|\.)""")
            )
        ),

        // ─── Paytm UPI ───
        UpiAppPattern(
            appName = "Paytm",
            senderIds = listOf("PAYTM", "PYTM", "PAYTMS"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Paid|Sent)\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*to\s*(?<merchant>.+?)(?:\s*from|\s*UPI|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:paid|sent)\s*to\s*(?<merchant>.+?)(?:\s*via|\.)"""),
                Regex("""(?i)Payment\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:successful|done)\s*(?:to|for)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Received\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?<merchant>.+?)(?:\s*in|\.)"""),
                Regex("""(?i)Cashback\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|added)""")
            )
        ),

        // ─── CRED ───
        UpiAppPattern(
            appName = "CRED",
            senderIds = listOf("CREDPAY", "CRED", "CREDUB"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Paid|Payment\s*of)\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to|for|towards)\s*(?<merchant>.+?)(?:\s*via|\s*on|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:paid|debited)\s*(?:to|for|towards)\s*(?<merchant>.+?)(?:\s*CRED|\.)"""),
                Regex("""(?i)Credit\s*card\s*bill\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*paid\s*(?:to|for)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Cashback\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|received)"""),
                Regex("""(?i)CRED\s*coins?\s*.*?(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|added)""")
            )
        ),

        // ─── Amazon Pay ───
        UpiAppPattern(
            appName = "Amazon Pay",
            senderIds = listOf("AMZNPY", "AMAZONP", "AMZPAY", "AMAZON"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Paid|Sent)\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:to|for)\s*(?<merchant>.+?)(?:\s*from|\s*UPI|\.)"""),
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:debited|paid)\s*(?:to|for|from)\s*(?<merchant>.+?)(?:\s*Amazon|\.)"""),
                Regex("""(?i)Amazon\s*Pay\s*.*?(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:paid|sent|debited)\s*(?:to|for)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Received\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:from|in)\s*(?<merchant>.+?)(?:\.)"""),
                Regex("""(?i)Cashback\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|added)"""),
                Regex("""(?i)Refund\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|processed)""")
            )
        ),

        // ─── WhatsApp Pay ───
        UpiAppPattern(
            appName = "WhatsApp Pay",
            senderIds = listOf("WHATSP", "WAPAY"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Paid|Sent)\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*to\s*(?<merchant>.+?)(?:\s*via|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Received\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(?<merchant>.+?)(?:\.)""")
            )
        ),

        // ─── Jupiter (Fi.Money) ───
        UpiAppPattern(
            appName = "Jupiter",
            senderIds = listOf("JUPTER", "JUPITR", "FIMONY"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:debited|sent|paid)\s*(?:from|to|for)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:credited|received)\s*(?:to|from|in)\s*(?<merchant>.+?)(?:\.)""")
            )
        ),

        // ─── Slice / OneCard ───
        UpiAppPattern(
            appName = "Slice",
            senderIds = listOf("SLICEP", "SLICE", "ONECAR"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:spent|charged|debited)\s*(?:at|on|to)\s*(?<merchant>.+?)(?:\s*on|\.)"""),
                Regex("""(?i)Transaction\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|on)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)Refund\s*of\s*(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:processed|credited)""")
            )
        ),

        // ─── BHIM UPI ───
        UpiAppPattern(
            appName = "BHIM",
            senderIds = listOf("BHIMUPI", "BHIM", "NPCI"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:sent|paid|debited)\s*to\s*(?<merchant>.+?)(?:\s*from|\s*UPI|\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:received|credited)\s*from\s*(?<merchant>.+?)(?:\.)""")
            )
        ),

        // ─── MobiKwik ───
        UpiAppPattern(
            appName = "MobiKwik",
            senderIds = listOf("MOBIKW", "MBKWIK"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:paid|debited)\s*(?:to|at)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:added|credited|received)\s*(?:to|from|in)\s*(?<merchant>.+?)(?:\.)""")
            )
        ),

        // ─── Freecharge ───
        UpiAppPattern(
            appName = "Freecharge",
            senderIds = listOf("FRCHGE", "FRCHRG"),
            debitPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:paid|debited)\s*(?:to|for)\s*(?<merchant>.+?)(?:\.)""")
            ),
            creditPatterns = listOf(
                Regex("""(?i)(?:Rs\.?|₹|INR)\s*(?<amount>[0-9,]+(?:\.[0-9]{1,2})?)\s*(?:cashback|credited|received)""")
            )
        )
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // GENERIC PATTERNS (Fallback for unrecognized banks)
    // ═══════════════════════════════════════════════════════════════════════════

    object GenericPatterns {

        /** Generic debit patterns that work across most Indian bank SMS formats */
        val debitPatterns = listOf(
            // "Rs.XXX debited from A/c XX1234"
            Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?(?:debited|deducted)\s*(?:from\s*)?(?:your\s*)?(?:A/?c|account|Card)?\s*[xX*]*(\d{4})"""),
            // "A/c XX1234 debited by Rs.XXX"
            Regex("""(?i)(?:A/?c|Acct?|account)\s*[xX*]*(\d{4})\s*(?:has been |was |is )?(?:debited|deducted)\s*(?:by|with|for)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
            // "INR XXX spent on Card XX1234 at MERCHANT"
            Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*spent\s*(?:on|at|via)\s*(?:your\s*)?(?:Debit|Credit)?\s*Card\s*[xX*]*(\d{4})\s*at\s*(.+?)(?:\s*on|\.)"""),
            // "Amt Sent Rs.XXX To MERCHANT"
            Regex("""(?i)(?:Amt\s*)?(?:Sent|Paid|Transferred)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:To|to|for)\s*(.+?)(?:\s*from|\s*UPI|\s*Ref|\.)"""),
            // "EMI of Rs.XXX debited"
            Regex("""(?i)EMI\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?(?:debited|deducted|charged)"""),
            // "Transaction of Rs.XXX at MERCHANT"
            Regex("""(?i)(?:Txn|Transaction)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|to|for|done\s*at)\s*(.+?)(?:\s*on|\.)"""),
            // "Withdrawn Rs.XXX from ATM"
            Regex("""(?i)(?:Withdrawn|Withdrawal|ATM\s*(?:cash\s*)?(?:withdrawal|WDL))\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
            // "Card XX1234 used for Rs.XXX at MERCHANT"
            Regex("""(?i)Card\s*[xX*]*(\d{4})\s*(?:used|charged)\s*(?:for|at)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:at|to|for)\s*(.+?)(?:\s*on|\.)"""),
            // "Auto-debit of Rs.XXX"
            Regex("""(?i)(?:Auto[- ]?debit|Standing\s*instruction|SI|Mandate)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was )?(?:executed|processed|debited)"""),
            // "Bill payment of Rs.XXX for MERCHANT"
            Regex("""(?i)(?:Bill\s*payment|Payment)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:for|to|towards)\s*(.+?)(?:\s*successful|\s*done|\.)""")
        )

        /** Generic credit patterns */
        val creditPatterns = listOf(
            // "Rs.XXX credited to A/c XX1234"
            Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?credited\s*(?:to\s*)?(?:your\s*)?(?:A/?c|account)?\s*[xX*]*(\d{4})"""),
            // "A/c XX1234 credited with Rs.XXX"
            Regex("""(?i)(?:A/?c|Acct?|account)\s*[xX*]*(\d{4})\s*(?:has been |was |is )?credited\s*(?:by|with)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
            // "You have received Rs.XXX from SENDER"
            Regex("""(?i)(?:You\s*(?:have\s*)?)?(?:received|got)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*from\s*(.+?)(?:\s*in|\s*to|\s*via|\.)"""),
            // "Amt Received Rs.XXX From SENDER"
            Regex("""(?i)(?:Amt\s*)?(?:Received|Credited)\s*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:from|by)\s*(.+?)(?:\s*in|\s*to|\.)"""),
            // "Refund of Rs.XXX credited"
            Regex("""(?i)Refund\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?(?:credited|processed|initiated)"""),
            // "Salary of Rs.XXX credited"
            Regex("""(?i)(?:Salary|NEFT|IMPS|RTGS)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?credited"""),
            // "Cashback of Rs.XXX"
            Regex("""(?i)Cashback\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:has been |was |is )?(?:credited|added|received)"""),
            // "Money received Rs.XXX"
            Regex("""(?i)(?:Money\s*received|Fund\s*transfer)\s*(?:of\s*)?(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        )

        /** Generic balance extraction */
        val balancePatterns = listOf(
            Regex("""(?i)(?:Avl\.?\s*Bal|Available\s*Bal(?:ance)?|Avbl\s*Bal|Curr(?:ent)?\s*Bal|Net\s*Bal|Bal(?:ance)?)[\s:.]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)"""),
            Regex("""(?i)(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:is\s*)?(?:available|avl|avbl)"""),
            Regex("""(?i)(?:Available\s*(?:Limit|Credit))[\s:.]*(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        )

        /** Merchant extraction from various formats */
        val merchantPatterns = listOf(
            Regex("""(?i)(?:at|to|for|towards|merchant|payee|beneficiary)[\s:]+(.+?)(?:\s+on\s+\d|\s+(?:Ref|UPI|NEFT|IMPS|RTGS)|[.]|\s+(?:Avl|Bal))"""),
            Regex("""(?i)(?:VPA|UPI\s*ID)[\s:]+(.+?)(?:\s|[.]|$)"""),
            Regex("""(?i)Info[:\s]+(.+?)(?:\s+(?:Avl|Bal)|[.]|$)""")
        )

        /** Account number extraction */
        val accountPatterns = listOf(
            Regex("""(?i)(?:A/?c|Acct?|Card|account)\s*(?:no\.?\s*)?[xX*]+\s*(\d{4})"""),
            Regex("""(?i)[xX*]{2,}(\d{4})"""),
            Regex("""(?i)(?:ending|last\s*4\s*digits?)[\s:]*(\d{4})""")
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION MODE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════

    enum class TransactionMode {
        UPI, NEFT, IMPS, RTGS, DEBIT_CARD, CREDIT_CARD, ATM, EMI, AUTO_DEBIT, NET_BANKING, WALLET, UNKNOWN
    }

    val transactionModePatterns = mapOf(
        TransactionMode.UPI to Regex("""(?i)\b(?:UPI|VPA|PhonePe|GPay|Paytm|BHIM|@\w+)\b"""),
        TransactionMode.NEFT to Regex("""(?i)\b(?:NEFT)\b"""),
        TransactionMode.IMPS to Regex("""(?i)\b(?:IMPS)\b"""),
        TransactionMode.RTGS to Regex("""(?i)\b(?:RTGS)\b"""),
        TransactionMode.DEBIT_CARD to Regex("""(?i)\b(?:Debit\s*Card|DC|ATM\s*Card|ATM-CUM-DEBIT)\b"""),
        TransactionMode.CREDIT_CARD to Regex("""(?i)\b(?:Credit\s*Card|CC)\b"""),
        TransactionMode.ATM to Regex("""(?i)\b(?:ATM|cash\s*withdrawal|CW)\b"""),
        TransactionMode.EMI to Regex("""(?i)\b(?:EMI|equated\s*monthly)\b"""),
        TransactionMode.AUTO_DEBIT to Regex("""(?i)\b(?:Auto[- ]?debit|SI|Standing\s*Instruction|Mandate|NACH|ECS)\b"""),
        TransactionMode.NET_BANKING to Regex("""(?i)\b(?:Net\s*Banking|Internet\s*Banking|IB|Online)\b"""),
        TransactionMode.WALLET to Regex("""(?i)\b(?:Wallet|Paytm\s*Wallet|PhonePe\s*Wallet)\b""")
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SPAM / NON-TRANSACTIONAL FILTERS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Keywords that indicate an SMS is NOT a financial transaction */
    val nonTransactionalKeywords = listOf(
        "otp", "one time password", "verification code", "verify",
        "offer", "reward points", "apply now", "pre-approved",
        "loan offer", "credit limit increase", "upgrade", "activate",
        "reminder", "due date", "minimum amount due", "statement ready",
        "download app", "install", "rate us", "feedback",
        "dear customer", "important update", "kyc", "pan",
        "aadhaar", "link your", "mandatory", "blocked",
        "pin changed", "password changed", "login alert",
        "transaction declined", "failed", "unsuccessful",
        "insufficient", "limit exceeded"
    )

    /** Sender ID patterns that are typically financial */
    val financialSenderPattern = Regex(
        """(?i)^(?:[A-Z]{2}-)?(?:""" +
            bankPatterns.flatMap { it.senderIds }.joinToString("|") + "|" +
            upiAppPatterns.flatMap { it.senderIds }.joinToString("|") +
            """|[A-Z]*(?:BNK|BKL|BANK|PAY|FIN|CC|UPI|NBK|SBI|HDFC|ICICI|AXIS|KOTAK))"""
    )

    /** Quick check: does this SMS likely contain a financial transaction? */
    fun isLikelyFinancialSms(body: String, sender: String? = null): Boolean {
        // Check sender ID first (fastest check)
        if (sender != null && financialSenderPattern.containsMatchIn(sender)) {
            // Even if sender matches, filter out non-transactional messages
            val bodyLower = body.lowercase()
            val isNonTransactional = nonTransactionalKeywords.any { keyword ->
                bodyLower.contains(keyword) && !bodyLower.contains("debited") &&
                    !bodyLower.contains("credited") && !bodyLower.contains("spent") &&
                    !bodyLower.contains("received") && !bodyLower.contains("sent")
            }
            if (isNonTransactional) return false
            return true
        }

        // Fallback: check body content for transaction indicators
        val bodyLower = body.lowercase()
        val hasAmount = Regex("""(?:Rs\.?|INR|₹)\s*[0-9,]+""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val hasTransactionKeyword = (DEBIT_KEYWORDS + CREDIT_KEYWORDS).any { bodyLower.contains(it) }
        val hasAccount = Regex("""[xX*]{2,}\d{4}""").containsMatchIn(body)

        return hasAmount && (hasTransactionKeyword || hasAccount)
    }
}
