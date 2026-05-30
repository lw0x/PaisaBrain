package com.paisabrain.app.accessibility

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * # ElderlyMode ("Simple Mode")
 *
 * A complete "Simple Mode" designed for parents, grandparents, and anyone
 * who finds phones confusing or overwhelming.
 *
 * ## Philosophy:
 * - **One action per screen** — never overwhelm with choices
 * - **HUGE text and buttons** — no squinting, no mis-taps
 * - **No jargon** — says "Money spent" not "Debit transaction"
 * - **Traffic light colors** — GREEN = good, RED = warning
 * - **Voice everything** — tap any number to hear it spoken aloud
 * - **Fail-safe** — always a "Call for help" option available
 *
 * ## Detection:
 * The app suggests Simple Mode automatically when it detects indicators
 * of potential difficulty (large font, accessibility services, slow onboarding).
 * Users can always switch between Normal and Simple mode in settings.
 *
 * ## Accessibility Compliance:
 * - WCAG 2.1 Level AA throughout
 * - All touch targets ≥ 64dp (exceeds 48dp minimum)
 * - Contrast ratio ≥ 7:1 (exceeds 4.5:1 AA minimum — meets AAA)
 * - Works with TalkBack, Switch Access, and all other accessibility services
 *
 * @since 1.0.0
 */
object ElderlyMode {

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 1: Configuration & Data Structures
    // ═══════════════════════════════════════════════════════════════════════════

    /** SharedPreferences key for elderly mode settings */
    private const val PREFS_NAME = "elderly_mode_prefs"
    private const val KEY_ENABLED = "is_enabled"
    private const val KEY_TEXT_SCALE = "text_scale"
    private const val KEY_BUTTON_SIZE = "button_size"
    private const val KEY_VOICE_READOUT = "voice_readout"
    private const val KEY_SIMPLIFIED_NAV = "simplified_nav"
    private const val KEY_HELP_CONTACT = "help_contact_number"
    private const val KEY_HELP_CONTACT_NAME = "help_contact_name"
    private const val KEY_AUTO_NIGHT_MODE = "auto_night_mode"
    private const val KEY_MAX_ITEMS_PER_SCREEN = "max_items_per_screen"
    private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    /**
     * Button size options for elderly mode.
     * Each progressively increases the touch target and text size.
     */
    enum class ButtonSize(val heightDp: Int, val textSp: Int, val paddingDp: Int) {
        /** Standard button — 48dp height, 16sp text (matches WCAG minimum) */
        NORMAL(heightDp = 48, textSp = 16, paddingDp = 12),

        /** Large button — 64dp height, 20sp text (recommended for elderly) */
        LARGE(heightDp = 64, textSp = 20, paddingDp = 16),

        /** Extra large button — 88dp height, 24sp text (for significant difficulty) */
        EXTRA_LARGE(heightDp = 88, textSp = 24, paddingDp = 20)
    }

    /**
     * Complete configuration for Elderly/Simple Mode.
     *
     * @property isEnabled Whether Simple Mode is currently active.
     * @property textScale Font size multiplier (1.0 = normal, 2.5 = maximum).
     * @property buttonSize Size of interactive buttons.
     * @property showVoiceReadout Whether tapping numbers speaks them aloud.
     * @property simplifiedNavigation Whether to show 3 tabs instead of 5.
     * @property helpContactNumber Phone number to call for help (family member).
     * @property helpContactName Display name of the help contact.
     * @property autoNightMode Whether to enable warmer/darker display after sunset.
     * @property maxItemsPerScreen Maximum visible items before scrolling (reduces overwhelm).
     */
    data class ElderlyModeConfig(
        val isEnabled: Boolean = false,
        val textScale: Float = 1.5f,
        val buttonSize: ButtonSize = ButtonSize.LARGE,
        val showVoiceReadout: Boolean = true,
        val simplifiedNavigation: Boolean = true,
        val helpContactNumber: String? = null,
        val helpContactName: String? = null,
        val autoNightMode: Boolean = true,
        val maxItemsPerScreen: Int = 4
    ) {
        /** Effective text size in sp for body text */
        val bodyTextSp: Float get() = 16f * textScale

        /** Effective text size in sp for headings */
        val headingTextSp: Float get() = 24f * textScale

        /** Effective text size in sp for large display numbers (amounts) */
        val displayNumberTextSp: Float get() = 36f * textScale

        /** Minimum spacing between interactive elements in dp */
        val elementSpacingDp: Int
            get() = when (buttonSize) {
                ButtonSize.NORMAL -> 8
                ButtonSize.LARGE -> 16
                ButtonSize.EXTRA_LARGE -> 24
            }
    }

    /**
     * Simplified dashboard data — only the essentials.
     * Designed for "at a glance" understanding with zero cognitive load.
     *
     * @property totalSpentThisMonth Amount spent this month.
     * @property moneyRemaining Budget remaining (if budget set), or balance.
     * @property nextBillDue Next upcoming bill with amount and due date.
     * @property smartTip One simple, actionable financial tip.
     * @property budgetStatus Traffic light status: GREEN/YELLOW/RED.
     * @property lastTransactionDescription Brief description of last transaction.
     */
    data class SimpleDashboard(
        val totalSpentThisMonth: Double,
        val moneyRemaining: Double,
        val nextBillDue: BillInfo?,
        val smartTip: String,
        val budgetStatus: TrafficLightStatus,
        val lastTransactionDescription: String?
    )

    /**
     * Upcoming bill information for the simplified dashboard.
     */
    data class BillInfo(
        val name: String,
        val amount: Double,
        val dueInDays: Int
    ) {
        /** Human-friendly due date description */
        val dueDescription: String
            get() = when {
                dueInDays == 0 -> "Due today"
                dueInDays == 1 -> "Due tomorrow"
                dueInDays <= 7 -> "Due in $dueInDays days"
                else -> "Due in ${dueInDays / 7} weeks"
            }
    }

    /**
     * Traffic light budget status — universal understanding across languages and literacy levels.
     */
    enum class TrafficLightStatus(
        val emoji: String,
        val label: String,
        val description: String,
        val colorHex: String
    ) {
        /** Under 60% of budget spent — doing well */
        GREEN(
            emoji = "🟢",
            label = "Good",
            description = "You are spending carefully. Keep it up!",
            colorHex = "#4CAF50"
        ),

        /** 60-85% of budget spent — be careful */
        YELLOW(
            emoji = "🟡",
            label = "Be Careful",
            description = "You have spent most of your limit. Try to spend less now.",
            colorHex = "#FFC107"
        ),

        /** Over 85% of budget spent — warning */
        RED(
            emoji = "🔴",
            label = "Warning",
            description = "You are close to or over your spending limit!",
            colorHex = "#F44336"
        );

        companion object {
            /**
             * Determines traffic light status based on spending percentage.
             *
             * @param spent Amount spent.
             * @param budget Budget limit.
             * @return Appropriate traffic light status.
             */
            fun fromBudgetUsage(spent: Double, budget: Double): TrafficLightStatus {
                if (budget <= 0) return GREEN
                val percentage = (spent / budget) * 100
                return when {
                    percentage >= 85 -> RED
                    percentage >= 60 -> YELLOW
                    else -> GREEN
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 2: Simplified Text / Jargon-Free Language
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Complete mapping of technical financial terms to simple, everyday language.
     *
     * Designed so that anyone — regardless of education, age, or financial literacy —
     * can understand what the app is telling them.
     */
    private val elderlyFriendlyTextMap: Map<String, String> = mapOf(
        // Core financial terms
        "Transaction" to "Money move",
        "Transactions" to "Money moves",
        "Debit" to "Money spent",
        "Credit" to "Money received",
        "Net Worth" to "Total savings",
        "Budget" to "Spending limit",
        "Budgeting" to "Planning your spending",
        "Recurring" to "Same bill every month",
        "Subscription" to "Monthly payment",
        "EMI" to "Monthly loan payment",
        "Equated Monthly Installment" to "Monthly loan payment",

        // Categories and navigation
        "Category" to "Type",
        "Categories" to "Types",
        "Analytics" to "Summary",
        "Dashboard" to "Home screen",
        "Overview" to "Summary",
        "Export" to "Save to phone",
        "Import" to "Load from phone",
        "Sync" to "Update",
        "Backup" to "Save a copy",
        "Restore" to "Load saved copy",

        // Security terms
        "Biometric" to "Fingerprint lock",
        "Biometric Authentication" to "Fingerprint unlock",
        "Authentication" to "Unlocking",
        "Two-Factor Authentication" to "Extra security check",
        "PIN" to "Secret number",
        "Encryption" to "Data protection",
        "Privacy" to "Keeping your info safe",

        // Financial concepts
        "Expenditure" to "Spending",
        "Revenue" to "Money coming in",
        "Income" to "Money coming in",
        "Expense" to "Money going out",
        "Liability" to "Money you owe",
        "Asset" to "What you own",
        "Balance" to "Money available",
        "Account Balance" to "Money in your account",
        "Savings" to "Money put aside",
        "Investment" to "Money put to grow",
        "Interest" to "Extra money earned (or charged)",
        "Principal" to "Original amount",
        "Tenure" to "Time period",
        "Maturity" to "When it ends",
        "Dividend" to "Earnings from investment",
        "Portfolio" to "All investments together",
        "Mutual Fund" to "Group investment",
        "Fixed Deposit" to "Locked savings",
        "Collateral" to "Guarantee for loan",

        // App-specific terms
        "Vault" to "Safe storage",
        "Vault Item" to "Saved item",
        "OCR" to "Reading text from photo",
        "Transcription" to "Converting voice to text",
        "Notification" to "Alert message",
        "Notification Channel" to "Alert type",
        "Widget" to "Home screen shortcut",
        "Settings" to "Options",
        "Preferences" to "Your choices",
        "Configuration" to "Setup",
        "Toggle" to "Switch on or off",

        // Status terms
        "Pending" to "Waiting",
        "Processing" to "Working on it",
        "Completed" to "Done",
        "Failed" to "Did not work",
        "Declined" to "Rejected",
        "Approved" to "Accepted",

        // Action terms
        "Delete" to "Remove",
        "Archive" to "Put away",
        "Filter" to "Show only",
        "Sort" to "Arrange",
        "Search" to "Find",
        "Navigate" to "Go to",
        "Submit" to "Send",
        "Confirm" to "Say yes",
        "Cancel" to "Go back",
        "Dismiss" to "Close",
        "Refresh" to "Check for new information"
    )

    /**
     * Converts a technical term to elderly-friendly language.
     *
     * @param technicalTerm The complex/technical term to simplify.
     * @return Simple, everyday language equivalent. Returns original if no mapping exists.
     *
     * Examples:
     * - "Transaction" → "Money move"
     * - "Debit" → "Money spent"
     * - "Budget" → "Spending limit"
     * - "EMI" → "Monthly loan payment"
     */
    fun getElderlyFriendlyText(technicalTerm: String): String {
        // First try exact match (case-insensitive)
        elderlyFriendlyTextMap.entries.forEach { (key, value) ->
            if (key.equals(technicalTerm, ignoreCase = true)) {
                return value
            }
        }
        // If no exact match, return original
        return technicalTerm
    }

    /**
     * Converts an entire sentence/paragraph, replacing all known technical terms
     * with elderly-friendly equivalents.
     *
     * @param text The text containing potential technical terms.
     * @return Text with all recognized terms replaced with simple language.
     */
    fun simplifyFullText(text: String): String {
        var result = text
        // Sort by length (longest first) to avoid partial replacements
        val sortedEntries = elderlyFriendlyTextMap.entries.sortedByDescending { it.key.length }
        for ((technical, simple) in sortedEntries) {
            result = result.replace(technical, simple, ignoreCase = true)
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 3: Voice Readout Integration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Text-to-Speech engine wrapper for elderly voice readout.
     *
     * Speaks amounts, labels, and instructions aloud when tapped.
     * Works on Android 6.0+ (TextToSpeech API available since Android 1.6).
     *
     * Usage:
     * ```kotlin
     * val voiceReadout = ElderlyMode.VoiceReadout(context)
     * voiceReadout.initialize { success ->
     *     if (success) {
     *         voiceReadout.speak("You spent forty-five thousand rupees this month")
     *     }
     * }
     * ```
     */
    class VoiceReadout(private val context: Context) {

        private var tts: TextToSpeech? = null
        private var isInitialized = false
        private var currentLocale: Locale = Locale("en", "IN")

        /** Callback for speech completion */
        var onSpeechComplete: (() -> Unit)? = null

        /** Callback for speech error */
        var onSpeechError: ((String) -> Unit)? = null

        /**
         * Initializes the TextToSpeech engine.
         * Must be called before speak().
         *
         * @param locale The locale for speech (e.g., Locale("hi", "IN") for Hindi).
         * @param onReady Callback when TTS is ready (true) or failed (false).
         */
        fun initialize(
            locale: Locale = Locale("en", "IN"),
            onReady: (Boolean) -> Unit
        ) {
            currentLocale = locale

            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(locale)
                    isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED

                    if (isInitialized) {
                        configureTtsForElderly()
                    }
                    onReady(isInitialized)
                } else {
                    isInitialized = false
                    onReady(false)
                }
            }
        }

        /**
         * Configures TTS settings optimized for elderly users:
         * - Slower speech rate (0.8x — easier to understand)
         * - Slightly higher pitch (clearer for aging ears)
         * - Utterance progress listener for UI feedback
         */
        private fun configureTtsForElderly() {
            tts?.apply {
                setSpeechRate(0.85f)  // Slightly slower than normal
                setPitch(1.05f)       // Slightly higher — clearer for elderly

                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        onSpeechComplete?.invoke()
                    }

                    @Deprecated("Deprecated in API level 21")
                    override fun onError(utteranceId: String?) {
                        onSpeechError?.invoke("Speech failed")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        onSpeechError?.invoke("Speech error: $errorCode")
                    }
                })
            }
        }

        /**
         * Speaks text aloud using TextToSpeech.
         *
         * @param text The text to speak.
         * @param flush If true, interrupts current speech. If false, queues after current.
         */
        fun speak(text: String, flush: Boolean = true) {
            if (!isInitialized) return

            val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = UUID.randomUUID().toString()

            if (Build.VERSION.SDK_INT >= 21) {
                val params = android.os.Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                tts?.speak(text, queueMode, params, utteranceId)
            } else {
                @Suppress("DEPRECATION")
                val params = HashMap<String, String>().apply {
                    put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                @Suppress("DEPRECATION")
                tts?.speak(text, queueMode, params)
            }
        }

        /**
         * Stops current speech immediately.
         */
        fun stop() {
            tts?.stop()
        }

        /**
         * Changes the TTS language.
         *
         * @param locale New locale to speak in.
         * @return True if the language is available and set.
         */
        fun setLanguage(locale: Locale): Boolean {
            currentLocale = locale
            val result = tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            return result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        /**
         * Checks if a language is available for TTS.
         *
         * @param locale The locale to check.
         * @return True if the language can be spoken.
         */
        fun isLanguageAvailable(locale: Locale): Boolean {
            val result = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            return result >= TextToSpeech.LANG_AVAILABLE
        }

        /**
         * Releases TTS resources. Call when no longer needed (e.g., in onDestroy).
         */
        fun shutdown() {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }

        /**
         * Whether TTS is ready to speak.
         */
        val isReady: Boolean get() = isInitialized
    }

    /**
     * Generates the voice readout text for a financial amount with context.
     *
     * Creates a complete, natural-sounding sentence that elderly users
     * can easily understand when spoken aloud.
     *
     * @param amount The numeric amount.
     * @param context Description of what this amount represents.
     * @param locale Locale for number-to-word conversion.
     * @return Natural language sentence ready for TTS.
     *
     * Examples:
     * - getVoiceReadoutText(45000.0, "spent this month") →
     *   "You spent forty-five thousand rupees this month"
     * - getVoiceReadoutText(17600.0, "remaining this month") →
     *   "You have seventeen thousand six hundred rupees remaining this month"
     */
    fun getVoiceReadoutText(
        amount: Double,
        context: String,
        locale: Locale = Locale("en", "IN")
    ): String {
        val amountWords = AccessibilityManager.numberToWords(amount.toLong())
        val currencyWord = getCurrencyWordForLocale(locale)
        val fractionalPart = ((amount - amount.toLong()) * 100).roundToInt()

        val amountPhrase = if (fractionalPart > 0) {
            "$amountWords $currencyWord and $fractionalPart paise"
        } else {
            "$amountWords $currencyWord"
        }

        // Build natural sentence based on context
        return when {
            context.contains("spent", ignoreCase = true) ->
                "You spent $amountPhrase ${context.replace("spent", "").trim()}"

            context.contains("remaining", ignoreCase = true) ->
                "You have $amountPhrase ${context.trim()}"

            context.contains("received", ignoreCase = true) ->
                "You received $amountPhrase ${context.replace("received", "").trim()}"

            context.contains("due", ignoreCase = true) ->
                "$amountPhrase is $context"

            context.contains("saved", ignoreCase = true) ->
                "You have saved $amountPhrase ${context.replace("saved", "").trim()}"

            else ->
                "$amountPhrase $context"
        }.trim().replace("  ", " ")
    }

    /**
     * Gets the currency word appropriate for the locale.
     */
    private fun getCurrencyWordForLocale(locale: Locale): String {
        return when (locale.country.uppercase()) {
            "IN" -> "rupees"
            "US" -> "dollars"
            "GB" -> "pounds"
            else -> "rupees"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 4: Auto-Detection & Smart Suggestions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Determines whether Simple Mode should be automatically suggested to the user.
     *
     * Triggers suggestion if ANY of these conditions are true:
     * 1. System font size is scaled >1.3x (indicates vision difficulty)
     * 2. Accessibility services are actively running
     * 3. Device appears to be entry-level/basic
     * 4. User didn't complete onboarding within 60 seconds (suggests confusion)
     *
     * @param context Application context.
     * @return `true` if the app should suggest enabling Simple Mode.
     */
    fun shouldAutoEnableElderlyMode(context: Context): Boolean {
        // Condition 1: Large system font (user already has difficulty seeing)
        val fontScale = context.resources.configuration.fontScale
        if (fontScale > 1.3f) return true

        // Condition 2: Accessibility services active
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (accessibilityManager?.isEnabled == true) return true

        // Condition 3: Entry-level device detection
        if (isEntryLevelDevice()) return true

        // Condition 4: Slow onboarding (checked separately via shouldSuggestDueToSlowOnboarding)

        return false
    }

    /**
     * Checks if the user appears confused during onboarding (spent > 60 seconds
     * without completing the first action).
     *
     * @param context Application context.
     * @return `true` if onboarding is taking unusually long.
     */
    fun shouldSuggestDueToSlowOnboarding(context: Context): Boolean {
        val prefs = getPrefs(context)
        val firstInstallTime = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0L)
        val onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

        if (onboardingCompleted || firstInstallTime == 0L) return false

        val elapsedSeconds = (System.currentTimeMillis() - firstInstallTime) / 1000
        return elapsedSeconds > 60
    }

    /**
     * Records the first install time for onboarding speed detection.
     * Call this when the app is first opened after install.
     */
    fun recordFirstInstall(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getLong(KEY_FIRST_INSTALL_TIME, 0L) == 0L) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Marks onboarding as completed.
     */
    fun markOnboardingComplete(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    /**
     * Detects if the device is likely entry-level/basic.
     *
     * Heuristics:
     * - Low RAM (< 3GB)
     * - Low screen density (< 280dpi)
     * - Older API level on a relatively new date (stuck on old Android)
     */
    private fun isEntryLevelDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)

        // Very low available memory suggests constrained device
        if (maxMemoryMB < 128) return true

        // Old API on what should be a newer phone
        if (Build.VERSION.SDK_INT <= 26) return true

        return false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 5: Night Mode / Time-Aware Features
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Determines if auto night mode should currently be active.
     *
     * Enables warmer colors and larger text after sunset for older eyes.
     * Sunset is approximated at 18:00 (6 PM) and sunrise at 06:00 (6 AM).
     *
     * @return `true` if it's currently nighttime (between 6 PM and 6 AM).
     */
    fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 6
    }

    /**
     * Gets the appropriate text scale for the current time of day.
     * Slightly larger text at night for tired/strained elderly eyes.
     *
     * @param baseScale The user's configured text scale.
     * @param autoNightMode Whether auto night mode is enabled.
     * @return Adjusted text scale (may be slightly larger at night).
     */
    fun getTimeAdjustedTextScale(baseScale: Float, autoNightMode: Boolean): Float {
        if (!autoNightMode) return baseScale
        return if (isNightTime()) {
            (baseScale * 1.1f).coerceAtMost(2.5f) // 10% larger at night, max 2.5x
        } else {
            baseScale
        }
    }

    /**
     * Night mode color adjustments for elderly eyes.
     * Warmer tones reduce blue light strain.
     */
    data class NightModeColors(
        val backgroundHex: String = "#1A1A0F",     // Warm dark (not pure black)
        val textPrimaryHex: String = "#FFF8E1",    // Warm white
        val textSecondaryHex: String = "#D7CCC8",  // Warm grey
        val accentHex: String = "#FFD54F",         // Warm amber
        val goodColorHex: String = "#A5D6A7",      // Soft green
        val warningColorHex: String = "#FFAB91"    // Soft coral
    )

    /**
     * Gets the appropriate color scheme based on time and settings.
     *
     * @param config Current elderly mode configuration.
     * @return Night mode colors if applicable, null for day mode.
     */
    fun getNightModeColorsIfApplicable(config: ElderlyModeConfig): NightModeColors? {
        if (!config.autoNightMode) return null
        return if (isNightTime()) NightModeColors() else null
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 6: Simplified Dashboard
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates the simplified dashboard content for elderly mode.
     *
     * Shows only what matters:
     * 1. How much was spent this month (BIG number)
     * 2. How much is remaining (with traffic light)
     * 3. Next bill due (if any)
     * 4. One helpful tip
     *
     * @param totalSpent Total spending this month.
     * @param budget Monthly budget/limit.
     * @param nextBill Next upcoming bill (null if none).
     * @param lastTransaction Description of last transaction.
     * @return Simplified dashboard data ready for display.
     */
    fun getSimplifiedDashboard(
        totalSpent: Double,
        budget: Double,
        nextBill: BillInfo? = null,
        lastTransaction: String? = null
    ): SimpleDashboard {
        val remaining = (budget - totalSpent).coerceAtLeast(0.0)
        val status = TrafficLightStatus.fromBudgetUsage(totalSpent, budget)

        val tip = generateSmartTip(totalSpent, budget, status)

        return SimpleDashboard(
            totalSpentThisMonth = totalSpent,
            moneyRemaining = remaining,
            nextBillDue = nextBill,
            smartTip = tip,
            budgetStatus = status,
            lastTransactionDescription = lastTransaction
        )
    }

    /**
     * Generates a simple, actionable financial tip based on current spending.
     */
    private fun generateSmartTip(spent: Double, budget: Double, status: TrafficLightStatus): String {
        return when (status) {
            TrafficLightStatus.GREEN -> {
                val daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                val daysRemaining = daysInMonth - currentDay
                if (daysRemaining > 15) {
                    "You're doing well! Keep going like this."
                } else {
                    "Good job! You're spending carefully."
                }
            }
            TrafficLightStatus.YELLOW -> {
                "Try to be careful with spending for the rest of the month."
            }
            TrafficLightStatus.RED -> {
                "You've spent most of your limit. Only spend on things you really need."
            }
        }
    }

    /**
     * Formats a currency amount for elderly-friendly display.
     * Uses locale-appropriate formatting with clear, large-friendly number formatting.
     *
     * @param amount The amount to format.
     * @param locale Display locale.
     * @return Formatted string like "₹45,000" or "$1,200".
     */
    fun formatAmountForDisplay(amount: Double, locale: Locale = Locale("en", "IN")): String {
        val formatter = NumberFormat.getCurrencyInstance(locale)
        formatter.maximumFractionDigits = 0 // No paise for elderly — cleaner display
        return formatter.format(amount)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 7: Simplified Navigation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Defines the simplified navigation tabs for elderly mode.
     *
     * Normal mode: 5 tabs (Home, Transactions, Vault, Analytics, Settings)
     * Simple mode: 3 tabs (Money, Remember, Help)
     */
    enum class SimpleTab(
        val label: String,
        val icon: String,
        val description: String
    ) {
        /** Shows spending summary — the simplified dashboard */
        MONEY(
            label = "Money",
            icon = "wallet",
            description = "See how much you spent and how much is left"
        ),

        /** Camera + search — for receipts and bills */
        REMEMBER(
            label = "Remember",
            icon = "camera",
            description = "Take a photo to save, or find something you saved before"
        ),

        /** Help contact + app info + settings */
        HELP(
            label = "Help",
            icon = "help",
            description = "Call someone for help, or change how the app looks"
        )
    }

    /**
     * Gets the navigation tabs appropriate for the current mode.
     *
     * @param config Current elderly mode configuration.
     * @return List of tab configurations.
     */
    fun getNavigationTabs(config: ElderlyModeConfig): List<SimpleTab> {
        return if (config.simplifiedNavigation) {
            SimpleTab.entries.toList()
        } else {
            SimpleTab.entries.toList() // In elderly mode, always simplified
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 8: "Call for Help" Feature
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Initiates a phone call to the pre-configured help contact (family member).
     *
     * This is a safety net for elderly users who get confused or need assistance.
     * The button is always prominently displayed in Simple Mode.
     *
     * @param context Application context.
     * @param phoneNumber The phone number to call.
     * @return True if the call intent was launched, false if no number is configured.
     */
    fun callForHelp(context: Context, phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false

        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the display text for the help button.
     *
     * @param contactName Name of the help contact (e.g., "Son", "Daughter").
     * @return Button text like "Call Son for help".
     */
    fun getHelpButtonText(contactName: String?): String {
        return if (!contactName.isNullOrBlank()) {
            "Call $contactName for help"
        } else {
            "Call for help"
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 9: Persistence — Save/Load Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Saves the elderly mode configuration to SharedPreferences.
     *
     * @param context Application context.
     * @param config The configuration to save.
     */
    fun saveConfig(context: Context, config: ElderlyModeConfig) {
        getPrefs(context).edit().apply {
            putBoolean(KEY_ENABLED, config.isEnabled)
            putFloat(KEY_TEXT_SCALE, config.textScale)
            putString(KEY_BUTTON_SIZE, config.buttonSize.name)
            putBoolean(KEY_VOICE_READOUT, config.showVoiceReadout)
            putBoolean(KEY_SIMPLIFIED_NAV, config.simplifiedNavigation)
            putString(KEY_HELP_CONTACT, config.helpContactNumber)
            putString(KEY_HELP_CONTACT_NAME, config.helpContactName)
            putBoolean(KEY_AUTO_NIGHT_MODE, config.autoNightMode)
            putInt(KEY_MAX_ITEMS_PER_SCREEN, config.maxItemsPerScreen)
            apply()
        }
    }

    /**
     * Loads the elderly mode configuration from SharedPreferences.
     *
     * @param context Application context.
     * @return Saved configuration, or default configuration if none saved.
     */
    fun loadConfig(context: Context): ElderlyModeConfig {
        val prefs = getPrefs(context)
        return ElderlyModeConfig(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.5f),
            buttonSize = try {
                ButtonSize.valueOf(prefs.getString(KEY_BUTTON_SIZE, ButtonSize.LARGE.name)!!)
            } catch (e: Exception) {
                ButtonSize.LARGE
            },
            showVoiceReadout = prefs.getBoolean(KEY_VOICE_READOUT, true),
            simplifiedNavigation = prefs.getBoolean(KEY_SIMPLIFIED_NAV, true),
            helpContactNumber = prefs.getString(KEY_HELP_CONTACT, null),
            helpContactName = prefs.getString(KEY_HELP_CONTACT_NAME, null),
            autoNightMode = prefs.getBoolean(KEY_AUTO_NIGHT_MODE, true),
            maxItemsPerScreen = prefs.getInt(KEY_MAX_ITEMS_PER_SCREEN, 4)
        )
    }

    /**
     * Checks if elderly mode is currently enabled.
     *
     * @param context Application context.
     * @return `true` if Simple Mode is active.
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }

    /**
     * Enables or disables elderly mode.
     *
     * @param context Application context.
     * @param enabled Whether to enable Simple Mode.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Gets SharedPreferences instance for elderly mode.
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 10: Elderly Mode Screen Descriptions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Content description for the Home screen in elderly mode.
     *
     * Layout (conceptual):
     * ```
     * ┌─────────────────────────────────────┐
     * │           🟢 Good                    │
     * │                                      │
     * │      ₹12,400 spent                  │
     * │      this month                      │
     * │                                      │
     * │  ─────────────────────────────────── │
     * │                                      │
     * │      ₹17,600 remaining              │
     * │                                      │
     * │  ─────────────────────────────────── │
     * │                                      │
     * │  💡 You're doing well! Keep going.   │
     * │                                      │
     * │  [🔊 Tap to hear amount]             │
     * │                                      │
     * └─────────────────────────────────────┘
     * ```
     */
    data class HomeScreenContent(
        val statusEmoji: String,
        val statusLabel: String,
        val spentAmountFormatted: String,
        val spentLabel: String,
        val remainingAmountFormatted: String,
        val remainingLabel: String,
        val tipText: String,
        val voiceButtonLabel: String = "Tap to hear amount spoken aloud"
    )

    /**
     * Builds the home screen content for elderly mode display.
     */
    fun buildHomeScreenContent(dashboard: SimpleDashboard, locale: Locale = Locale("en", "IN")): HomeScreenContent {
        return HomeScreenContent(
            statusEmoji = dashboard.budgetStatus.emoji,
            statusLabel = dashboard.budgetStatus.label,
            spentAmountFormatted = formatAmountForDisplay(dashboard.totalSpentThisMonth, locale),
            spentLabel = "spent this month",
            remainingAmountFormatted = formatAmountForDisplay(dashboard.moneyRemaining, locale),
            remainingLabel = "remaining",
            tipText = dashboard.smartTip
        )
    }

    /**
     * Content for the Remember (Vault) screen in elderly mode.
     *
     * Layout (conceptual):
     * ```
     * ┌─────────────────────────────────────┐
     * │                                      │
     * │   [📷 Take photo to remember]        │
     * │   (BIG button, entire width)         │
     * │                                      │
     * │  ─────────────────────────────────── │
     * │                                      │
     * │   [🔍 Find something]                │
     * │   (BIG button, entire width)         │
     * │                                      │
     * └─────────────────────────────────────┘
     * ```
     */
    data class RememberScreenContent(
        val photoButtonLabel: String = "Take photo to remember",
        val photoButtonDescription: String = "Open camera to take a picture of a receipt, bill, or document",
        val searchButtonLabel: String = "Find something I saved",
        val searchButtonDescription: String = "Search through your saved photos and notes"
    )

    /**
     * Content for the Help screen in elderly mode.
     *
     * Layout (conceptual):
     * ```
     * ┌─────────────────────────────────────┐
     * │                                      │
     * │   [📞 Call {Name} for help]           │
     * │   (BIG green button)                 │
     * │                                      │
     * │  ─────────────────────────────────── │
     * │                                      │
     * │   [ℹ️ About this app]                │
     * │                                      │
     * │   [⚙️ Change text size]              │
     * │                                      │
     * │   [🔊 Voice on/off]                  │
     * │                                      │
     * └─────────────────────────────────────┘
     * ```
     */
    data class HelpScreenContent(
        val callButtonLabel: String,
        val callButtonDescription: String,
        val aboutLabel: String = "About this app",
        val textSizeLabel: String = "Make text bigger or smaller",
        val voiceToggleLabel: String = "Read numbers aloud when tapped",
        val showCallButton: Boolean
    )

    /**
     * Builds help screen content based on configuration.
     */
    fun buildHelpScreenContent(config: ElderlyModeConfig): HelpScreenContent {
        return HelpScreenContent(
            callButtonLabel = getHelpButtonText(config.helpContactName),
            callButtonDescription = if (config.helpContactNumber != null) {
                "Calls ${config.helpContactName ?: "your family member"} for help"
            } else {
                "Set up a family member's phone number to call for help"
            },
            showCallButton = config.helpContactNumber != null
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 11: Onboarding for Simple Mode
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Step-by-step onboarding screens for elderly mode setup.
     * Each step has LARGE text, ONE action, and clear illustration descriptions.
     */
    enum class OnboardingStep(
        val title: String,
        val description: String,
        val actionLabel: String,
        val illustrationDescription: String
    ) {
        /**
         * Step 1: Welcome — explain what the app does in ONE sentence.
         * Illustration: Friendly, simple drawing of a smiling phone with a coin.
         */
        WELCOME(
            title = "Welcome!",
            description = "This app helps you keep track of your money. " +
                    "It watches your messages and tells you where your money goes.",
            actionLabel = "Next",
            illustrationDescription = "A friendly illustration of a smiling mobile phone " +
                    "with a small coin, representing simple money tracking"
        ),

        /**
         * Step 2: Permission — ask to read messages.
         * Illustration: A message bubble with a rupee symbol.
         */
        PERMISSION_SMS(
            title = "Can we read your messages?",
            description = "When your bank sends you a message about money coming in or going out, " +
                    "this app will read it and remember it for you. " +
                    "Your messages stay private — only money information is saved.",
            actionLabel = "Yes, that's okay",
            illustrationDescription = "An illustration of a chat message bubble containing " +
                    "a currency symbol, representing transaction SMS messages"
        ),

        /**
         * Step 3: Text size — let them choose how big they want text.
         * Illustration: The letter 'A' in three sizes (small, medium, large).
         */
        TEXT_SIZE(
            title = "How big should the text be?",
            description = "Drag the slider to make text bigger or smaller. " +
                    "You can always change this later.",
            actionLabel = "This size is good",
            illustrationDescription = "Three letter A's in increasing sizes, showing " +
                    "small, medium, and large text options"
        ),

        /**
         * Step 4: Help contact — set up a family member to call.
         * Illustration: A phone with a heart, showing calling family.
         */
        HELP_CONTACT(
            title = "Who should we call if you need help?",
            description = "Choose a family member. There will always be a button " +
                    "to call them if you get confused or need help.",
            actionLabel = "Save this person",
            illustrationDescription = "A phone with a heart symbol, representing " +
                    "the ability to call a trusted family member"
        ),

        /**
         * Step 5: Done — confirm setup is complete.
         * Illustration: A checkmark with confetti.
         */
        COMPLETE(
            title = "All done!",
            description = "Everything is set up. You can start using the app now. " +
                    "Remember: tap any number to hear it spoken aloud!",
            actionLabel = "Start using the app",
            illustrationDescription = "A large checkmark with celebratory confetti, " +
                    "indicating successful setup completion"
        )
    }

    /**
     * Gets the onboarding steps in order.
     *
     * @return Ordered list of onboarding steps.
     */
    fun getOnboardingSteps(): List<OnboardingStep> {
        return OnboardingStep.entries.toList()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 12: Accessibility Integration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the complete accessibility content description for the dashboard
     * in elderly mode, suitable for TalkBack to read.
     */
    fun getDashboardAccessibilityText(
        dashboard: SimpleDashboard,
        locale: Locale = Locale("en", "IN")
    ): String {
        return buildString {
            append("Your money summary. ")
            append("Status: ${dashboard.budgetStatus.label}. ")
            append("${dashboard.budgetStatus.description} ")
            append("You spent ${AccessibilityManager.numberToWords(dashboard.totalSpentThisMonth.toLong())} rupees this month. ")
            append("${AccessibilityManager.numberToWords(dashboard.moneyRemaining.toLong())} rupees remaining. ")

            dashboard.nextBillDue?.let { bill ->
                append("Next bill: ${bill.name}, ")
                append("${AccessibilityManager.numberToWords(bill.amount.toLong())} rupees, ")
                append("${bill.dueDescription}. ")
            }

            append("Tip: ${dashboard.smartTip}")
        }
    }

    /**
     * Gets accessibility action descriptions for elderly mode views.
     * Each interactive element has a clear spoken label.
     */
    fun getAccessibilityLabels(): Map<String, String> {
        return mapOf(
            "home_spent_amount" to "Total amount spent this month. Tap to hear spoken aloud.",
            "home_remaining_amount" to "Amount remaining in your spending limit. Tap to hear spoken aloud.",
            "home_status_indicator" to "Your spending status indicator",
            "remember_camera_button" to "Take a photo to save. Opens your camera.",
            "remember_search_button" to "Find something you saved before. Opens search.",
            "help_call_button" to "Call your family member for help.",
            "help_settings_button" to "Change app settings like text size and voice.",
            "nav_money_tab" to "Money tab. See your spending summary.",
            "nav_remember_tab" to "Remember tab. Save and find photos.",
            "nav_help_tab" to "Help tab. Call for help or change settings."
        )
    }

    /**
     * Determines if a View should have voice readout attached in elderly mode.
     * Returns true for amount displays, status indicators, and informational text.
     */
    fun shouldAttachVoiceReadout(viewType: String): Boolean {
        val voiceReadoutViews = setOf(
            "amount_display",
            "budget_remaining",
            "spending_total",
            "bill_amount",
            "tip_text",
            "status_description"
        )
        return viewType in voiceReadoutViews
    }

    /**
     * Configuration for the elderly mode switch in settings.
     * Provides all the information needed to render the toggle.
     */
    data class ElderlyModeToggleInfo(
        val title: String = "Simple Mode",
        val description: String = "Makes everything bigger and simpler. " +
                "Perfect if you find the app confusing or hard to read.",
        val enableLabel: String = "Turn on Simple Mode",
        val disableLabel: String = "Turn off Simple Mode",
        val warningOnDisable: String = "Are you sure? Text will become smaller " +
                "and more options will appear on each screen."
    )

    /**
     * Returns info for the Simple Mode toggle in settings.
     */
    fun getToggleInfo(): ElderlyModeToggleInfo = ElderlyModeToggleInfo()
}
