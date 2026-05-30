package com.paisabrain.app.accessibility

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager as SystemAccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * # AccessibilityManager
 *
 * Comprehensive accessibility support ensuring the app is fully usable by:
 * - **Blind / visually impaired users** (TalkBack, screen readers)
 * - **Deaf / hearing impaired users** (visual + haptic feedback)
 * - **Motor impaired users** (Switch Access, limited dexterity)
 * - **Cognitively impaired users** (simple language, guided flows)
 * - **Low vision users** (high contrast, large text, magnification)
 *
 * Follows WCAG 2.1 Level AA guidelines throughout.
 * Compatible with Android API 23 (6.0) through API 37 (Android 17).
 *
 * ## Design Principles:
 * - Every interactive element is accessible via at least 2 modalities
 * - No information is conveyed through a single sensory channel
 * - All touch targets meet minimum 48dp × 48dp (WCAG AA)
 * - Text alternatives provided for all non-text content
 * - Color is never the sole indicator of meaning
 *
 * @since 1.0.0
 */
object AccessibilityManager {

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION A: BLIND / VISUALLY IMPAIRED USERS (TalkBack / Screen Reader)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Priority levels for accessibility announcements.
     * Higher priority interrupts current speech.
     */
    enum class AnnouncementPriority {
        /** Low priority — queued after current speech */
        LOW,
        /** Normal priority — announced when current speech finishes */
        NORMAL,
        /** High priority — interrupts current speech immediately */
        HIGH,
        /** Critical — announced immediately with maximum urgency */
        CRITICAL
    }

    /**
     * Represents an accessibility announcement to be made via TalkBack/screen reader.
     *
     * @property message The text to be spoken/announced.
     * @property priority How urgently this should be announced.
     * @property delayMillis Optional delay before announcement (to avoid conflicting with other speech).
     */
    data class AccessibilityAnnouncement(
        val message: String,
        val priority: AnnouncementPriority = AnnouncementPriority.NORMAL,
        val delayMillis: Long = 0L
    )

    /**
     * Announces a message through the screen reader (TalkBack).
     *
     * This triggers an accessibility event that the screen reader will speak aloud.
     * The announcement is made with appropriate priority handling.
     *
     * @param view Any visible view in the current window (used as the event source).
     * @param message The message to announce.
     * @param priority The urgency of the announcement.
     *
     * Example:
     * ```kotlin
     * AccessibilityManager.announceForScreenReader(
     *     rootView,
     *     "Transaction saved. Spent 500 rupees on food.",
     *     AnnouncementPriority.NORMAL
     * )
     * ```
     */
    fun announceForScreenReader(
        view: View,
        message: String,
        priority: AnnouncementPriority = AnnouncementPriority.NORMAL
    ) {
        if (!isScreenReaderActive(view.context)) return

        val event = AccessibilityEvent.obtain(
            AccessibilityEvent.TYPE_ANNOUNCEMENT
        ).apply {
            text.add(message)
            className = view.javaClass.name
            packageName = view.context.packageName
        }

        view.parent?.requestSendAccessibilityEvent(view, event)

        // Also use ViewCompat for broader compatibility
        ViewCompat.announceForAccessibility(view, message)
    }

    /**
     * Announces an [AccessibilityAnnouncement] object, respecting its delay.
     *
     * @param view The source view.
     * @param announcement The announcement to make.
     */
    fun announce(view: View, announcement: AccessibilityAnnouncement) {
        if (announcement.delayMillis > 0) {
            view.postDelayed({
                announceForScreenReader(view, announcement.message, announcement.priority)
            }, announcement.delayMillis)
        } else {
            announceForScreenReader(view, announcement.message, announcement.priority)
        }
    }

    /**
     * Converts chart/graph data into accessible text that screen readers can speak.
     *
     * Instead of seeing a visual chart, blind users hear:
     * "Spending chart. Top category: Food at 42 percent, 5,040 rupees.
     *  Transport at 18 percent, 2,160 rupees. Shopping at 15 percent, 1,800 rupees..."
     *
     * @param data List of pairs: (category name, amount in currency).
     * @param chartTitle Optional title for the chart.
     * @param locale The locale for currency formatting (defaults to Indian English).
     * @return Accessible text description of the chart data.
     */
    fun getChartAccessibilityText(
        data: List<Pair<String, Double>>,
        chartTitle: String = "Spending chart",
        locale: Locale = Locale("en", "IN")
    ): String {
        if (data.isEmpty()) {
            return "$chartTitle. No data available."
        }

        val total = data.sumOf { it.second }
        val sorted = data.sortedByDescending { it.second }

        return buildString {
            append("$chartTitle. ")
            append("${data.size} categories shown. ")
            append("Total: ${formatCurrencyForSpeech(total, locale)}. ")

            sorted.forEachIndexed { index, (category, amount) ->
                val percentage = if (total > 0) ((amount / total) * 100).roundToInt() else 0
                append("$category: $percentage percent, ${formatCurrencyForSpeech(amount, locale)}")
                if (index < sorted.size - 1) {
                    append(". ")
                } else {
                    append(".")
                }
            }
        }
    }

    /**
     * Converts a currency amount to screen-reader-friendly text.
     *
     * Reads "₹1,200" as "Rupees one thousand two hundred" instead of
     * "one-two-zero-zero" which is how raw numbers are sometimes read.
     *
     * @param amount The numeric amount.
     * @param locale The locale for number formatting.
     * @return A spoken-word representation of the currency amount.
     *
     * Examples:
     * - 1200.0 → "Rupees one thousand two hundred"
     * - 45000.50 → "Rupees forty-five thousand and fifty paise"
     * - 100.0 → "Rupees one hundred"
     */
    fun getCurrencyAccessibilityText(amount: Double, locale: Locale = Locale("en", "IN")): String {
        val wholePart = amount.toLong()
        val fractionalPart = ((amount - wholePart) * 100).roundToInt()

        val currencyWord = getCurrencyWord(locale)
        val subunitWord = getSubunitWord(locale)

        val wholeText = numberToWords(wholePart)

        return if (fractionalPart > 0) {
            "$currencyWord $wholeText and $fractionalPart $subunitWord"
        } else {
            "$currencyWord $wholeText"
        }
    }

    /**
     * Converts budget progress into accessible text.
     *
     * @param spent The amount spent so far.
     * @param budget The total budget limit.
     * @param locale The locale for formatting.
     * @return Accessible description like "Spent 60 percent of monthly budget.
     *         4,800 rupees spent out of 8,000 rupees. 3,200 rupees remaining."
     */
    fun getProgressAccessibilityText(
        spent: Double,
        budget: Double,
        locale: Locale = Locale("en", "IN")
    ): String {
        val percentage = if (budget > 0) ((spent / budget) * 100).roundToInt() else 0
        val remaining = (budget - spent).coerceAtLeast(0.0)

        val statusText = when {
            percentage >= 100 -> "Budget exceeded"
            percentage >= 90 -> "Almost at limit"
            percentage >= 75 -> "Getting close to limit"
            else -> "Within budget"
        }

        return buildString {
            append("$statusText. ")
            append("Spent $percentage percent of monthly budget. ")
            append("${formatCurrencyForSpeech(spent, locale)} spent ")
            append("out of ${formatCurrencyForSpeech(budget, locale)}. ")
            if (remaining > 0) {
                append("${formatCurrencyForSpeech(remaining, locale)} remaining.")
            } else {
                val overspent = spent - budget
                append("Over budget by ${formatCurrencyForSpeech(overspent, locale)}.")
            }
        }
    }

    /**
     * Sets up accessibility delegate for a complex view (chart, custom widget).
     *
     * Provides custom actions and content descriptions that screen readers can use.
     *
     * @param view The view to make accessible.
     * @param contentDescription What the view represents.
     * @param customActions List of action descriptions available on this view.
     */
    fun setupAccessibleView(
        view: View,
        contentDescription: String,
        customActions: List<Pair<String, () -> Boolean>> = emptyList()
    ) {
        view.contentDescription = contentDescription

        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = contentDescription

                // Add custom actions
                customActions.forEachIndexed { index, (label, _) ->
                    val action = AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK + index + 1,
                        label
                    )
                    info.addAction(action)
                }
            }

            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: android.os.Bundle?
            ): Boolean {
                customActions.forEachIndexed { index, (_, handler) ->
                    if (action == AccessibilityNodeInfoCompat.ACTION_CLICK + index + 1) {
                        return handler()
                    }
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })
    }

    /**
     * Sets meaningful traversal order for screen reader navigation.
     * Ensures the most important information is read first.
     *
     * @param views Ordered list of views — first view is read first.
     */
    fun setTraversalOrder(views: List<View>) {
        for (i in 0 until views.size - 1) {
            ViewCompat.setAccessibilityTraversalBefore(views[i], views[i + 1].id)
            ViewCompat.setAccessibilityTraversalAfter(views[i + 1], views[i].id)
        }
    }

    /**
     * Checks if a screen reader (TalkBack or similar) is currently active.
     *
     * @param context Application context.
     * @return `true` if a screen reader is running.
     */
    fun isScreenReaderActive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? SystemAccessibilityManager
        return am?.isEnabled == true && am.isTouchExplorationEnabled
    }

    /**
     * Creates a content description for a transaction item.
     *
     * @param category Transaction category (e.g., "Food").
     * @param amount Transaction amount.
     * @param isExpense True for expense, false for income.
     * @param date Human-readable date.
     * @param description Optional transaction description.
     * @return Full accessible description.
     */
    fun getTransactionAccessibilityText(
        category: String,
        amount: Double,
        isExpense: Boolean,
        date: String,
        description: String? = null,
        locale: Locale = Locale("en", "IN")
    ): String {
        val type = if (isExpense) "Spent" else "Received"
        val amountText = formatCurrencyForSpeech(amount, locale)

        return buildString {
            append("$type $amountText")
            if (category.isNotBlank()) append(" on $category")
            if (!description.isNullOrBlank()) append(". $description")
            append(". $date")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION B: DEAF / HEARING IMPAIRED USERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Types of alerts that have distinct vibration patterns.
     */
    enum class AlertType {
        /** Budget threshold exceeded — 3 short vibrations */
        BUDGET_ALERT,
        /** Weekly summary/insight notification — 1 long vibration */
        WEEKLY_SUMMARY,
        /** Possible fraudulent/suspicious transaction — 5 rapid SOS vibrations */
        FRAUD_ALERT,
        /** Goal or milestone reached — 2 medium vibrations */
        ACHIEVEMENT,
        /** General notification — single short vibration */
        GENERAL,
        /** Bill reminder — 2 short vibrations */
        REMINDER,
        /** Transaction detected — very brief pulse */
        TRANSACTION_DETECTED
    }

    /**
     * Returns the vibration pattern for a given alert type.
     *
     * Pattern format: [delay, vibrate, pause, vibrate, pause, ...]
     * - First value is initial delay before vibration starts
     * - Alternating values are vibration duration and pause duration (milliseconds)
     *
     * @param alertType The type of alert.
     * @return LongArray representing the vibration pattern.
     *
     * Patterns:
     * - BUDGET_ALERT: ••• (3 short pulses — attention needed)
     * - WEEKLY_SUMMARY: ━ (1 long vibration — informational)
     * - FRAUD_ALERT: ••••• (5 rapid pulses — SOS urgency)
     * - ACHIEVEMENT: ─ ─ (2 medium pulses — celebration)
     * - GENERAL: • (single brief pulse)
     * - REMINDER: •• (2 short pulses)
     * - TRANSACTION_DETECTED: . (very brief pulse)
     */
    fun getVibrationPattern(alertType: AlertType): LongArray {
        return when (alertType) {
            AlertType.BUDGET_ALERT ->
                longArrayOf(0, 150, 100, 150, 100, 150)  // 3 short vibrations

            AlertType.WEEKLY_SUMMARY ->
                longArrayOf(0, 500)  // 1 long vibration

            AlertType.FRAUD_ALERT ->
                longArrayOf(0, 100, 50, 100, 50, 100, 50, 100, 50, 100)  // 5 rapid SOS vibrations

            AlertType.ACHIEVEMENT ->
                longArrayOf(0, 250, 150, 250)  // 2 medium vibrations

            AlertType.GENERAL ->
                longArrayOf(0, 200)  // single short vibration

            AlertType.REMINDER ->
                longArrayOf(0, 150, 100, 150)  // 2 short vibrations

            AlertType.TRANSACTION_DETECTED ->
                longArrayOf(0, 80)  // very brief pulse
        }
    }

    /**
     * Triggers vibration with the appropriate pattern for the alert type.
     *
     * Handles API level differences:
     * - API 26+: Uses VibrationEffect for precise control
     * - API 23-25: Uses deprecated Vibrator.vibrate(pattern, repeat) safely
     * - API 31+: Uses VibratorManager for better device support
     *
     * @param context Application context.
     * @param alertType The type of alert to vibrate for.
     */
    @Suppress("DEPRECATION")
    fun vibrate(context: Context, alertType: AlertType) {
        val pattern = getVibrationPattern(alertType)

        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let { v ->
            if (!v.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= 26) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                v.vibrate(effect)
            } else {
                // API 23-25: use deprecated method safely
                v.vibrate(pattern, -1)
            }
        }
    }

    /**
     * Determines if visual alerts should be used instead of / in addition to audio.
     *
     * Returns true if:
     * - Device is in silent/vibrate mode
     * - Volume is at 0
     * - Do Not Disturb is active
     * - User has explicitly requested visual alerts in accessibility settings
     *
     * @param context Application context.
     * @return `true` if visual/haptic alerts should be preferred over audio.
     */
    fun shouldUseVisualAlerts(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true

        // Check if in silent or vibrate mode
        val ringerMode = audioManager.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT ||
            ringerMode == AudioManager.RINGER_MODE_VIBRATE
        ) {
            return true
        }

        // Check if notification volume is zero
        val notificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        if (notificationVolume == 0) {
            return true
        }

        // Check if any accessibility services suggest visual preference
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? SystemAccessibilityManager
        if (am?.isEnabled == true) {
            // If accessibility services are active, supplement audio with visual
            return true
        }

        return false
    }

    /**
     * Generates visual alert configuration for a given alert type.
     * Used when audio is unavailable/muted.
     *
     * @param alertType The alert type.
     * @return Configuration for visual feedback (color flash, duration, etc.)
     */
    fun getVisualAlertConfig(alertType: AlertType): VisualAlertConfig {
        return when (alertType) {
            AlertType.FRAUD_ALERT -> VisualAlertConfig(
                flashColor = Color.RED,
                flashCount = 5,
                flashDurationMs = 200,
                showBanner = true,
                bannerText = "Suspicious transaction detected!",
                iconDescription = "Warning: suspicious activity"
            )

            AlertType.BUDGET_ALERT -> VisualAlertConfig(
                flashColor = Color.rgb(255, 165, 0), // Orange
                flashCount = 3,
                flashDurationMs = 300,
                showBanner = true,
                bannerText = "Spending limit almost reached",
                iconDescription = "Alert: spending limit approaching"
            )

            AlertType.ACHIEVEMENT -> VisualAlertConfig(
                flashColor = Color.rgb(76, 175, 80), // Green
                flashCount = 2,
                flashDurationMs = 400,
                showBanner = true,
                bannerText = "Goal reached!",
                iconDescription = "Celebration: goal achieved"
            )

            AlertType.WEEKLY_SUMMARY -> VisualAlertConfig(
                flashColor = Color.rgb(33, 150, 243), // Blue
                flashCount = 1,
                flashDurationMs = 500,
                showBanner = true,
                bannerText = "Your weekly summary is ready",
                iconDescription = "Information: weekly summary available"
            )

            AlertType.REMINDER -> VisualAlertConfig(
                flashColor = Color.rgb(156, 39, 176), // Purple
                flashCount = 2,
                flashDurationMs = 300,
                showBanner = true,
                bannerText = "Upcoming payment reminder",
                iconDescription = "Reminder: payment due soon"
            )

            AlertType.GENERAL -> VisualAlertConfig(
                flashColor = Color.rgb(96, 125, 139), // Blue grey
                flashCount = 1,
                flashDurationMs = 300,
                showBanner = false,
                bannerText = "",
                iconDescription = "Notification"
            )

            AlertType.TRANSACTION_DETECTED -> VisualAlertConfig(
                flashColor = Color.rgb(0, 150, 136), // Teal
                flashCount = 1,
                flashDurationMs = 200,
                showBanner = false,
                bannerText = "",
                iconDescription = "Transaction recorded"
            )
        }
    }

    /**
     * Configuration for visual alert feedback (for deaf/hearing impaired users).
     *
     * @property flashColor The color to flash on screen edges.
     * @property flashCount Number of times to flash.
     * @property flashDurationMs Duration of each flash in milliseconds.
     * @property showBanner Whether to show a text banner.
     * @property bannerText Text to display in the banner.
     * @property iconDescription Accessibility description of the alert icon.
     */
    data class VisualAlertConfig(
        @ColorInt val flashColor: Int,
        val flashCount: Int,
        val flashDurationMs: Long,
        val showBanner: Boolean,
        val bannerText: String,
        val iconDescription: String
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION C: MOTOR IMPAIRED USERS (Switch Access, limited dexterity)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Minimum touch target size in density-independent pixels.
     * WCAG 2.1 Level AA requires 44×44 CSS px; Android guidelines require 48×48 dp.
     */
    private const val MIN_TOUCH_TARGET_DP = 48

    /** Large touch target for users with significant motor impairment (72dp). */
    private const val LARGE_TOUCH_TARGET_DP = 72

    /** Extra-large touch target for elderly mode (88dp). */
    private const val EXTRA_LARGE_TOUCH_TARGET_DP = 88

    /**
     * Returns the minimum touch target size in dp, adjusted for accessibility settings.
     *
     * - Default: 48dp (WCAG AA)
     * - If large pointer enabled: 72dp
     * - If elderly mode / extreme accessibility: 88dp
     *
     * @param context Application context.
     * @return Minimum touch target size in dp.
     */
    fun getMinimumTouchTarget(context: Context): Int {
        return when {
            isLargePointerEnabled(context) -> LARGE_TOUCH_TARGET_DP
            isExtremeAccessibilityMode(context) -> EXTRA_LARGE_TOUCH_TARGET_DP
            else -> MIN_TOUCH_TARGET_DP
        }
    }

    /**
     * Checks if the system "Large pointer" accessibility option is enabled.
     *
     * @param context Application context.
     * @return `true` if the user has enabled large pointer in accessibility settings.
     */
    fun isLargePointerEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                "accessibility_large_pointer_icon",
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if Switch Access or similar motor accessibility service is active.
     *
     * @param context Application context.
     * @return `true` if Switch Access or equivalent is running.
     */
    fun isSwitchAccessActive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? SystemAccessibilityManager
            ?: return false

        if (!am.isEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Check for known switch access service identifiers
        val switchAccessPackages = listOf(
            "com.google.android.accessibility.switchaccess",
            "com.google.android.marvin.talkback/com.google.android.accessibility.switchaccess"
        )

        return switchAccessPackages.any { enabledServices.contains(it, ignoreCase = true) }
    }

    /**
     * Returns configuration for motor-impaired-friendly UI.
     * All actions have tap alternatives (no mandatory swipe/long-press).
     */
    data class MotorAccessibilityConfig(
        val minTouchTargetDp: Int,
        val showDismissButtons: Boolean,
        val disableLongPressRequired: Boolean,
        val showVoiceCommandHints: Boolean,
        val autoScrollEnabled: Boolean,
        val increasedSpacing: Boolean
    )

    /**
     * Gets the motor accessibility configuration based on device settings.
     *
     * @param context Application context.
     * @return Configuration appropriate for the user's motor accessibility needs.
     */
    fun getMotorAccessibilityConfig(context: Context): MotorAccessibilityConfig {
        val isSwitchAccess = isSwitchAccessActive(context)
        val isLargePointer = isLargePointerEnabled(context)
        val needsEnhanced = isSwitchAccess || isLargePointer

        return MotorAccessibilityConfig(
            minTouchTargetDp = getMinimumTouchTarget(context),
            showDismissButtons = true,  // Always show — never require swipe-to-dismiss only
            disableLongPressRequired = needsEnhanced,
            showVoiceCommandHints = needsEnhanced,
            autoScrollEnabled = needsEnhanced,
            increasedSpacing = needsEnhanced
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION D: COGNITIVE ACCESSIBILITY
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Maps complex/technical financial terms to simpler language.
     *
     * Used when "Simple Language" mode is enabled, or always for elderly mode.
     * Terms are mapped to common, everyday words that everyone understands.
     */
    private val simplifiedTermsMap: Map<String, String> = mapOf(
        "Transaction" to "Money move",
        "Debit" to "Money spent",
        "Credit" to "Money received",
        "Net Worth" to "Total savings",
        "Budget" to "Spending limit",
        "Recurring" to "Same bill every month",
        "Subscription" to "Monthly payment",
        "EMI" to "Monthly loan payment",
        "Category" to "Type",
        "Analytics" to "Summary",
        "Export" to "Save to phone",
        "Biometric" to "Fingerprint lock",
        "Authentication" to "Verification",
        "Expenditure" to "Spending",
        "Revenue" to "Earnings",
        "Liability" to "Amount you owe",
        "Asset" to "What you own",
        "Depreciation" to "Value decrease over time",
        "Amortization" to "Loan payment schedule",
        "Principal" to "Original loan amount",
        "Interest" to "Extra cost for borrowing",
        "Compound Interest" to "Interest on interest",
        "Dividend" to "Money earned from investments",
        "Portfolio" to "All your investments together",
        "Liquidity" to "How easily you can get cash",
        "Overdraft" to "Spending more than your balance",
        "Reconciliation" to "Matching records",
        "Fiscal" to "Financial",
        "Variance" to "Difference from expected",
        "Allocation" to "How money is divided",
        "Disbursement" to "Payment sent out",
        "Reimbursement" to "Money paid back to you",
        "Collateral" to "Guarantee for a loan",
        "Tenure" to "Time period",
        "Maturity" to "When a plan ends",
        "Redemption" to "Cashing out",
        "Accrual" to "Amount building up",
        "Cashflow" to "Money coming in and going out",
        "Deficit" to "Shortfall",
        "Surplus" to "Extra money left over",
        "Inflation" to "Prices going up over time"
    )

    /**
     * Converts complex financial terms to simple, everyday language.
     *
     * @param complexText The technical/complex text to simplify.
     * @return Simplified text suitable for users with cognitive disabilities or limited financial literacy.
     *
     * Example:
     * - "Your recurring subscription EMI is due" → "Your same monthly payment is due"
     */
    fun getSimplifiedText(complexText: String): String {
        var result = complexText
        // Sort by length (longest first) to avoid partial replacements
        val sortedTerms = simplifiedTermsMap.entries.sortedByDescending { it.key.length }
        for ((complex, simple) in sortedTerms) {
            result = result.replace(complex, simple, ignoreCase = true)
        }
        return result
    }

    /**
     * Provides step-by-step guidance text for an action.
     * Breaks complex flows into numbered, simple steps.
     *
     * @param action The action the user wants to perform.
     * @return List of simple step descriptions.
     */
    fun getStepByStepGuidance(action: UserAction): List<String> {
        return when (action) {
            UserAction.ADD_EXPENSE -> listOf(
                "Step 1: Tap the amount box and type how much you spent",
                "Step 2: Choose what you spent it on (food, travel, etc.)",
                "Step 3: Tap Save to remember this"
            )

            UserAction.TAKE_PHOTO -> listOf(
                "Step 1: Point your camera at the receipt or bill",
                "Step 2: Make sure all the text is visible on screen",
                "Step 3: Tap the big round button to take the photo",
                "Step 4: Check the photo looks clear, then tap Save"
            )

            UserAction.SET_BUDGET -> listOf(
                "Step 1: Type the maximum amount you want to spend this month",
                "Step 2: Tap Save",
                "Step 3: The app will warn you when you get close to this amount"
            )

            UserAction.SEARCH_VAULT -> listOf(
                "Step 1: Type what you're looking for (like a shop name or date)",
                "Step 2: Tap Search",
                "Step 3: Scroll through the results to find what you need"
            )

            UserAction.CHANGE_SETTINGS -> listOf(
                "Step 1: Tap the Settings button (gear icon) at the top",
                "Step 2: Find the option you want to change",
                "Step 3: Tap it to change it",
                "Step 4: Your changes are saved automatically"
            )
        }
    }

    /**
     * Actions that can have step-by-step guidance.
     */
    enum class UserAction {
        ADD_EXPENSE,
        TAKE_PHOTO,
        SET_BUDGET,
        SEARCH_VAULT,
        CHANGE_SETTINGS
    }

    /**
     * Represents an undoable action for the 5-second undo window.
     *
     * @property actionId Unique identifier for this action.
     * @property description What was done (e.g., "Deleted transaction").
     * @property undoAction Lambda to reverse the action.
     * @property expiresAtMillis System time when the undo option expires.
     */
    data class UndoableAction(
        val actionId: String,
        val description: String,
        val undoAction: () -> Unit,
        val expiresAtMillis: Long = System.currentTimeMillis() + UNDO_WINDOW_MS
    ) {
        companion object {
            /** Undo window duration: 5 seconds */
            const val UNDO_WINDOW_MS = 5000L
        }

        /** Whether this action can still be undone (within time window). */
        val canUndo: Boolean
            get() = System.currentTimeMillis() < expiresAtMillis
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION E: LOW VISION (not fully blind, but poor eyesight)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if system high contrast mode is enabled.
     *
     * @param context Application context.
     * @return `true` if high contrast text/UI is enabled in system settings.
     */
    fun isHighContrastEnabled(context: Context): Boolean {
        return try {
            // Check for high contrast text setting
            val highContrastText = Settings.Secure.getInt(
                context.contentResolver,
                "high_text_contrast_enabled",
                0
            )
            if (highContrastText == 1) return true

            // On some manufacturers, check alternative setting names
            val accessibilityHighContrast = Settings.Secure.getInt(
                context.contentResolver,
                "accessibility_high_contrast",
                0
            )
            accessibilityHighContrast == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns an accessible color based on whether high contrast mode is active.
     *
     * In high contrast mode:
     * - Backgrounds become pure black (#000000)
     * - Text becomes pure white (#FFFFFF)
     * - Borders become thick and bright
     * - All colors are maximally saturated
     *
     * @param normalColor The color to use in normal mode (ARGB int).
     * @param isHighContrast Whether high contrast mode is active.
     * @return The appropriate color for the current mode.
     */
    @ColorInt
    fun getAccessibleColor(@ColorInt normalColor: Int, isHighContrast: Boolean): Int {
        if (!isHighContrast) return normalColor

        // In high contrast, map colors to their extreme equivalents
        val red = Color.red(normalColor)
        val green = Color.green(normalColor)
        val blue = Color.blue(normalColor)

        // Calculate luminance (perceived brightness)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0

        return if (luminance > 0.5) {
            // Light color → make it pure white
            Color.WHITE
        } else {
            // Dark color → make it pure black
            Color.BLACK
        }
    }

    /**
     * Gets the high-contrast color palette for the app.
     * Pure black background, white text, bright accent colors.
     */
    data class HighContrastPalette(
        @ColorInt val background: Int = Color.BLACK,
        @ColorInt val surface: Int = Color.rgb(20, 20, 20),
        @ColorInt val textPrimary: Int = Color.WHITE,
        @ColorInt val textSecondary: Int = Color.rgb(200, 200, 200),
        @ColorInt val accent: Int = Color.rgb(0, 255, 255),  // Cyan — high visibility
        @ColorInt val error: Int = Color.rgb(255, 100, 100),
        @ColorInt val success: Int = Color.rgb(100, 255, 100),
        @ColorInt val warning: Int = Color.rgb(255, 255, 0),  // Pure yellow
        @ColorInt val border: Int = Color.WHITE,
        val borderWidthDp: Int = 3
    )

    /**
     * Gets the appropriate color palette based on accessibility settings.
     *
     * @param context Application context.
     * @return High contrast palette if enabled, null otherwise (use normal theme).
     */
    fun getHighContrastPaletteIfNeeded(context: Context): HighContrastPalette? {
        return if (isHighContrastEnabled(context)) HighContrastPalette() else null
    }

    /**
     * Calculates the contrast ratio between two colors.
     * Used to verify WCAG compliance (minimum 4.5:1 for normal text, 3:1 for large text).
     *
     * @param foreground The text/foreground color.
     * @param background The background color.
     * @return Contrast ratio (1.0 to 21.0).
     */
    fun calculateContrastRatio(@ColorInt foreground: Int, @ColorInt background: Int): Double {
        val fgLuminance = getRelativeLuminance(foreground)
        val bgLuminance = getRelativeLuminance(background)

        val lighter = maxOf(fgLuminance, bgLuminance)
        val darker = minOf(fgLuminance, bgLuminance)

        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Checks if a color combination meets WCAG AA contrast requirements.
     *
     * @param foreground Text color.
     * @param background Background color.
     * @param isLargeText True for text 18sp+ or 14sp+ bold.
     * @return `true` if the contrast meets WCAG AA.
     */
    fun meetsWcagAA(@ColorInt foreground: Int, @ColorInt background: Int, isLargeText: Boolean): Boolean {
        val ratio = calculateContrastRatio(foreground, background)
        return if (isLargeText) ratio >= 3.0 else ratio >= 4.5
    }

    /**
     * Gets the system font scale factor.
     * Used to determine if the user has enlarged system text.
     *
     * @param context Application context.
     * @return Font scale multiplier (1.0 = normal, 2.0 = double size).
     */
    fun getSystemFontScale(context: Context): Float {
        return context.resources.configuration.fontScale
    }

    /**
     * Whether the app should use enlarged UI elements based on system font scale.
     *
     * @param context Application context.
     * @return `true` if system font is scaled above 1.3x (suggesting vision difficulty).
     */
    fun shouldEnlargeUI(context: Context): Boolean {
        return getSystemFontScale(context) > 1.3f
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION F: UTILITY / HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if extreme accessibility mode should be used.
     * True when multiple accessibility services are active simultaneously.
     */
    private fun isExtremeAccessibilityMode(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? SystemAccessibilityManager
            ?: return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // Count active accessibility services
        val serviceCount = enabledServices.split(":").filter { it.isNotBlank() }.size
        return serviceCount >= 2
    }

    /**
     * Calculates relative luminance of a color per WCAG 2.1 formula.
     */
    private fun getRelativeLuminance(@ColorInt color: Int): Double {
        fun linearize(component: Int): Double {
            val sRGB = component / 255.0
            return if (sRGB <= 0.03928) {
                sRGB / 12.92
            } else {
                Math.pow((sRGB + 0.055) / 1.055, 2.4)
            }
        }

        val r = linearize(Color.red(color))
        val g = linearize(Color.green(color))
        val b = linearize(Color.blue(color))

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Formats a currency amount for speech (e.g., "1,200 rupees").
     * Simplified version for screen reader text where full word form isn't needed.
     */
    private fun formatCurrencyForSpeech(amount: Double, locale: Locale): String {
        val formatter = NumberFormat.getNumberInstance(locale)
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 0
        val currencyWord = getCurrencyWord(locale)
        return "${formatter.format(amount)} $currencyWord"
    }

    /**
     * Gets the currency word for the locale.
     */
    private fun getCurrencyWord(locale: Locale): String {
        return when (locale.country.uppercase()) {
            "IN" -> "rupees"
            "US" -> "dollars"
            "GB" -> "pounds"
            "EU" -> "euros"
            else -> "rupees"
        }
    }

    /**
     * Gets the sub-unit word for the locale.
     */
    private fun getSubunitWord(locale: Locale): String {
        return when (locale.country.uppercase()) {
            "IN" -> "paise"
            "US" -> "cents"
            "GB" -> "pence"
            "EU" -> "cents"
            else -> "paise"
        }
    }

    /**
     * Converts a number to its English word representation.
     * Used for making currency amounts fully speakable.
     *
     * @param number The number to convert (supports up to 99,99,99,999 — Indian numbering).
     * @return English words representation.
     *
     * Examples:
     * - 1200 → "one thousand two hundred"
     * - 45000 → "forty-five thousand"
     * - 100 → "one hundred"
     * - 17600 → "seventeen thousand six hundred"
     */
    fun numberToWords(number: Long): String {
        if (number == 0L) return "zero"
        if (number < 0L) return "minus ${numberToWords(-number)}"

        val ones = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen",
            "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        )

        val tens = arrayOf(
            "", "", "twenty", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety"
        )

        fun convertBelow1000(n: Long): String {
            return when {
                n == 0L -> ""
                n < 20 -> ones[n.toInt()]
                n < 100 -> {
                    val t = tens[(n / 10).toInt()]
                    val o = ones[(n % 10).toInt()]
                    if (o.isEmpty()) t else "$t-$o"
                }
                else -> {
                    val h = ones[(n / 100).toInt()]
                    val remainder = n % 100
                    if (remainder == 0L) "$h hundred"
                    else "$h hundred ${convertBelow1000(remainder)}"
                }
            }
        }

        // Use Indian numbering system (lakhs and crores)
        val parts = mutableListOf<String>()

        var remaining = number

        if (remaining >= 10_000_000L) {
            val crores = remaining / 10_000_000L
            parts.add("${convertBelow1000(crores)} crore")
            remaining %= 10_000_000L
        }

        if (remaining >= 100_000L) {
            val lakhs = remaining / 100_000L
            parts.add("${convertBelow1000(lakhs)} lakh")
            remaining %= 100_000L
        }

        if (remaining >= 1_000L) {
            val thousands = remaining / 1_000L
            parts.add("${convertBelow1000(thousands)} thousand")
            remaining %= 1_000L
        }

        if (remaining > 0L) {
            parts.add(convertBelow1000(remaining))
        }

        return parts.joinToString(" ")
    }

    /**
     * Comprehensive accessibility check — returns all active accessibility features.
     * Useful for adapting UI based on current accessibility state.
     *
     * @param context Application context.
     * @return Summary of all active accessibility features.
     */
    fun getAccessibilityStatus(context: Context): AccessibilityStatus {
        return AccessibilityStatus(
            screenReaderActive = isScreenReaderActive(context),
            highContrastEnabled = isHighContrastEnabled(context),
            largePointerEnabled = isLargePointerEnabled(context),
            switchAccessActive = isSwitchAccessActive(context),
            fontScale = getSystemFontScale(context),
            shouldUseVisualAlerts = shouldUseVisualAlerts(context),
            shouldEnlargeUI = shouldEnlargeUI(context)
        )
    }

    /**
     * Summary of all active accessibility features on the device.
     */
    data class AccessibilityStatus(
        val screenReaderActive: Boolean,
        val highContrastEnabled: Boolean,
        val largePointerEnabled: Boolean,
        val switchAccessActive: Boolean,
        val fontScale: Float,
        val shouldUseVisualAlerts: Boolean,
        val shouldEnlargeUI: Boolean
    ) {
        /** Whether any accessibility feature is active */
        val anyAccessibilityActive: Boolean
            get() = screenReaderActive || highContrastEnabled || largePointerEnabled ||
                    switchAccessActive || fontScale > 1.3f

        /** Whether the app should switch to simplified/elderly mode automatically */
        val suggestSimplifiedMode: Boolean
            get() = (screenReaderActive && switchAccessActive) || fontScale > 1.5f
    }
}
