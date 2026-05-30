package com.paisabrain.app.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.regex.Pattern

/**
 * ClipboardShield — Intelligent clipboard protection system.
 *
 * Features:
 * 1. Auto-detects sensitive content (OTP, card numbers, UPI IDs, passwords)
 * 2. Auto-clears clipboard after configurable timeout (30/60/90 seconds)
 * 3. Logs clipboard access history locally
 * 4. "Safe Copy" mode — copies with auto-destruct timer
 * 5. Shows which sensitive patterns were detected
 * 6. Works on Android 6.0+ (enhanced features on 12+)
 *
 * NO INTERNET. All processing on-device. History stored locally only.
 */
object ClipboardShield {

    // ==========================================
    // DATA CLASSES
    // ==========================================

    data class ClipboardEntry(
        val id: Long = System.currentTimeMillis(),
        val content: String,
        val sensitiveType: SensitiveType?,
        val timestamp: Long = System.currentTimeMillis(),
        val wasAutoCleared: Boolean = false,
        val clearedAfterSeconds: Int? = null,
        val sourceApp: String? = null // Available on Android 12+
    )

    enum class SensitiveType(val displayName: String, val emoji: String, val defaultClearSeconds: Int) {
        OTP("One-Time Password", "🔑", 30),
        CARD_NUMBER("Card Number", "💳", 15),
        UPI_ID("UPI ID", "📱", 60),
        IFSC_CODE("Bank Code", "🏦", 60),
        ACCOUNT_NUMBER("Account Number", "🏦", 30),
        PASSWORD_LIKE("Password/PIN", "🔒", 15),
        AADHAAR("Identity Number", "🪪", 15),
        PAN("Tax ID", "📋", 30),
        PHONE_NUMBER("Phone Number", "📞", 90),
        EMAIL("Email Address", "📧", 90),
        URL_WITH_TOKEN("Link with Token", "🔗", 45),
        NONE("General Text", "📋", 0)
    }

    data class ShieldConfig(
        val isEnabled: Boolean = true,
        val autoClearEnabled: Boolean = true,
        val defaultClearSeconds: Int = 60,
        val detectSensitive: Boolean = true,
        val clearOnlyIfSensitive: Boolean = true, // Only auto-clear if pattern matches
        val showNotificationOnClear: Boolean = true,
        val keepHistoryCount: Int = 50, // Local history for user review
        val vibrationOnClear: Boolean = true
    )

    data class ShieldStats(
        val totalCleared: Int,
        val otpsProtected: Int,
        val cardNumbersProtected: Int,
        val passwordsProtected: Int,
        val totalSensitiveDetected: Int,
        val lastClearedTimestamp: Long?,
        val shieldActiveSince: Long
    )

    // ==========================================
    // SENSITIVE PATTERN DETECTION
    // ==========================================

    // All patterns designed for Indian financial context
    private val PATTERNS = mapOf(
        SensitiveType.OTP to listOf(
            Pattern.compile("\\b\\d{4,8}\\b"), // 4-8 digit numbers (common OTP format)
            Pattern.compile("(?i)otp[:\\s]*\\d{4,8}"),
            Pattern.compile("(?i)verification\\s*code[:\\s]*\\d{4,8}"),
            Pattern.compile("(?i)one.?time.?password[:\\s]*\\d{4,8}")
        ),
        SensitiveType.CARD_NUMBER to listOf(
            Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), // 16-digit card
            Pattern.compile("\\b[4-6]\\d{3}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b") // Visa/MC/Rupay
        ),
        SensitiveType.UPI_ID to listOf(
            Pattern.compile("[a-zA-Z0-9.\\-_]+@[a-zA-Z]{2,}"), // user@bank format
            Pattern.compile("(?i)upi[:\\s]*[a-zA-Z0-9.]+@[a-z]+")
        ),
        SensitiveType.IFSC_CODE to listOf(
            Pattern.compile("\\b[A-Z]{4}0[A-Z0-9]{6}\\b") // IFSC format: 4 letters + 0 + 6 alphanumeric
        ),
        SensitiveType.ACCOUNT_NUMBER to listOf(
            Pattern.compile("\\b\\d{9,18}\\b") // Indian bank account: 9-18 digits
        ),
        SensitiveType.AADHAAR to listOf(
            Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b") // 12 digits in groups of 4
        ),
        SensitiveType.PAN to listOf(
            Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b") // PAN format: ABCDE1234F
        ),
        SensitiveType.PASSWORD_LIKE to listOf(
            // Mix of upper, lower, digits, special chars — likely a password
            Pattern.compile("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#\$%^&*]).{8,}"),
            Pattern.compile("(?i)password[:\\s]*.+"),
            Pattern.compile("(?i)pin[:\\s]*\\d{4,6}")
        ),
        SensitiveType.PHONE_NUMBER to listOf(
            Pattern.compile("\\b[6-9]\\d{9}\\b"), // Indian mobile: starts with 6-9, 10 digits
            Pattern.compile("\\+91[\\s-]?\\d{10}\\b")
        ),
        SensitiveType.EMAIL to listOf(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
        ),
        SensitiveType.URL_WITH_TOKEN to listOf(
            Pattern.compile("https?://.*[?&](token|key|auth|session|otp)=.+", Pattern.CASE_INSENSITIVE)
        )
    )

    // ==========================================
    // CORE FUNCTIONS
    // ==========================================

    /**
     * Detect sensitive content type in clipboard text.
     * Returns the MOST sensitive type found (shortest clear time = most sensitive).
     */
    fun detectSensitiveContent(text: String): SensitiveType {
        if (text.isBlank() || text.length > 500) return SensitiveType.NONE

        val detectedTypes = mutableListOf<SensitiveType>()

        for ((type, patterns) in PATTERNS) {
            for (pattern in patterns) {
                if (pattern.matcher(text).find()) {
                    detectedTypes.add(type)
                    break // One match per type is enough
                }
            }
        }

        if (detectedTypes.isEmpty()) return SensitiveType.NONE

        // Return most sensitive (shortest auto-clear time)
        return detectedTypes.minByOrNull { it.defaultClearSeconds } ?: SensitiveType.NONE
    }

    /**
     * Start clipboard monitoring. Call this from Application.onCreate().
     * Registers a clipboard change listener that:
     * 1. Detects sensitive content
     * 2. Schedules auto-clear after appropriate delay
     * 3. Logs to local history
     */
    fun startMonitoring(context: Context, config: ShieldConfig = ShieldConfig()) {
        if (!config.isEnabled) return

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val handler = Handler(Looper.getMainLooper())

        clipboardManager.addPrimaryClipChangedListener {
            val clip = clipboardManager.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener

            if (text.isBlank()) return@addPrimaryClipChangedListener

            val sensitiveType = detectSensitiveContent(text)

            // Log to local history
            logClipboardAccess(context, ClipboardEntry(
                content = maskForHistory(text, sensitiveType),
                sensitiveType = sensitiveType,
                timestamp = System.currentTimeMillis()
            ))

            // Schedule auto-clear if sensitive
            if (config.autoClearEnabled && sensitiveType != SensitiveType.NONE) {
                val clearDelay = if (config.clearOnlyIfSensitive) {
                    sensitiveType.defaultClearSeconds
                } else {
                    config.defaultClearSeconds
                }

                handler.postDelayed({
                    clearClipboard(context, clipboardManager)
                    incrementStats(context, sensitiveType)
                }, clearDelay * 1000L)
            }
        }
    }

    /**
     * Manually clear clipboard immediately.
     */
    fun clearClipboard(context: Context, clipboardManager: ClipboardManager? = null) {
        val cm = clipboardManager
            ?: context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            cm.clearPrimaryClip()
        } else {
            // Pre-Android 9: overwrite with empty text
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    /**
     * "Safe Copy" — copies text with auto-destruct timer.
     * User can call this instead of normal copy for sensitive data.
     */
    fun safeCopy(context: Context, text: String, clearAfterSeconds: Int = 30) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("safe_copy", text))

        Handler(Looper.getMainLooper()).postDelayed({
            clearClipboard(context, clipboardManager)
        }, clearAfterSeconds * 1000L)
    }

    /**
     * Get clipboard history (locally stored, masked).
     */
    fun getHistory(context: Context): List<ClipboardEntry> {
        val prefs = context.getSharedPreferences("clipboard_shield", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("history", null) ?: return emptyList()
        return parseHistoryJson(historyJson)
    }

    /**
     * Get protection statistics.
     */
    fun getStats(context: Context): ShieldStats {
        val prefs = context.getSharedPreferences("clipboard_shield_stats", Context.MODE_PRIVATE)
        return ShieldStats(
            totalCleared = prefs.getInt("total_cleared", 0),
            otpsProtected = prefs.getInt("otps_protected", 0),
            cardNumbersProtected = prefs.getInt("cards_protected", 0),
            passwordsProtected = prefs.getInt("passwords_protected", 0),
            totalSensitiveDetected = prefs.getInt("total_sensitive", 0),
            lastClearedTimestamp = prefs.getLong("last_cleared", 0L).takeIf { it > 0 },
            shieldActiveSince = prefs.getLong("active_since", System.currentTimeMillis())
        )
    }

    /**
     * Check if current clipboard contains sensitive data RIGHT NOW.
     * User can call this to check before sharing screen/phone.
     */
    fun isClipboardSafe(context: Context): Pair<Boolean, SensitiveType> {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()

        if (text.isNullOrBlank()) return Pair(true, SensitiveType.NONE)

        val type = detectSensitiveContent(text)
        return Pair(type == SensitiveType.NONE, type)
    }

    /**
     * Get user-friendly status for Privacy Guard screen.
     */
    fun getShieldStatus(context: Context): ShieldStatusUI {
        val stats = getStats(context)
        val (isSafe, currentType) = isClipboardSafe(context)

        return ShieldStatusUI(
            isActive = true,
            clipboardCurrentlySafe = isSafe,
            currentThreat = if (!isSafe) currentType?.displayName else null,
            totalProtected = stats.totalCleared,
            lifetimeSensitiveDetected = stats.totalSensitiveDetected,
            message = when {
                !isSafe -> "⚠️ Clipboard contains ${currentType?.displayName}. Will auto-clear in ${currentType?.defaultClearSeconds}s."
                stats.totalCleared > 0 -> "🛡️ Shield active. Protected ${stats.totalCleared} sensitive copies so far."
                else -> "🛡️ Clipboard Shield is active. Monitoring for sensitive content."
            }
        )
    }

    data class ShieldStatusUI(
        val isActive: Boolean,
        val clipboardCurrentlySafe: Boolean,
        val currentThreat: String?,
        val totalProtected: Int,
        val lifetimeSensitiveDetected: Int,
        val message: String
    )

    // ==========================================
    // PRIVATE HELPERS
    // ==========================================

    /**
     * Mask sensitive content for history display.
     * Shows type but not actual content (privacy within privacy!).
     */
    private fun maskForHistory(text: String, type: SensitiveType): String {
        return when (type) {
            SensitiveType.OTP -> "OTP: ****"
            SensitiveType.CARD_NUMBER -> "Card: ****-****-****-${text.takeLast(4)}"
            SensitiveType.UPI_ID -> "UPI: ${text.take(3)}***@***"
            SensitiveType.IFSC_CODE -> "IFSC: ${text.take(4)}*******"
            SensitiveType.ACCOUNT_NUMBER -> "A/C: *****${text.takeLast(4)}"
            SensitiveType.AADHAAR -> "ID: ****-****-${text.takeLast(4)}"
            SensitiveType.PAN -> "Tax ID: ${text.take(2)}***${text.takeLast(1)}"
            SensitiveType.PASSWORD_LIKE -> "Password: ********"
            SensitiveType.PHONE_NUMBER -> "Phone: ******${text.takeLast(4)}"
            SensitiveType.EMAIL -> "Email: ${text.take(2)}***@***"
            SensitiveType.URL_WITH_TOKEN -> "Link with auth token"
            SensitiveType.NONE -> text.take(30) + if (text.length > 30) "..." else ""
        }
    }

    private fun logClipboardAccess(context: Context, entry: ClipboardEntry) {
        val prefs = context.getSharedPreferences("clipboard_shield", Context.MODE_PRIVATE)
        val history = getHistory(context).toMutableList()
        history.add(0, entry)
        // Keep only last N entries
        val trimmed = history.take(50)
        prefs.edit().putString("history", serializeHistory(trimmed)).apply()
    }

    private fun incrementStats(context: Context, type: SensitiveType) {
        val prefs = context.getSharedPreferences("clipboard_shield_stats", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("total_cleared", prefs.getInt("total_cleared", 0) + 1)
            putInt("total_sensitive", prefs.getInt("total_sensitive", 0) + 1)
            putLong("last_cleared", System.currentTimeMillis())
            when (type) {
                SensitiveType.OTP -> putInt("otps_protected", prefs.getInt("otps_protected", 0) + 1)
                SensitiveType.CARD_NUMBER -> putInt("cards_protected", prefs.getInt("cards_protected", 0) + 1)
                SensitiveType.PASSWORD_LIKE -> putInt("passwords_protected", prefs.getInt("passwords_protected", 0) + 1)
                else -> {} // Other types just increment total
            }
            if (!prefs.contains("active_since")) {
                putLong("active_since", System.currentTimeMillis())
            }
            apply()
        }
    }

    // Simple JSON-like serialization (no external library needed)
    private fun serializeHistory(entries: List<ClipboardEntry>): String {
        return entries.joinToString("|||") { entry ->
            "${entry.id}::${entry.content}::${entry.sensitiveType?.name ?: "NONE"}::${entry.timestamp}::${entry.wasAutoCleared}"
        }
    }

    private fun parseHistoryJson(data: String): List<ClipboardEntry> {
        if (data.isBlank()) return emptyList()
        return try {
            data.split("|||").mapNotNull { item ->
                val parts = item.split("::")
                if (parts.size >= 5) {
                    ClipboardEntry(
                        id = parts[0].toLongOrNull() ?: 0L,
                        content = parts[1],
                        sensitiveType = try { SensitiveType.valueOf(parts[2]) } catch (e: Exception) { null },
                        timestamp = parts[3].toLongOrNull() ?: 0L,
                        wasAutoCleared = parts[4].toBooleanStrictOrNull() ?: false
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
