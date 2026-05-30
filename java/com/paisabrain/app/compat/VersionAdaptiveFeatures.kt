package com.paisabrain.app.compat

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

/**
 * # VersionAdaptiveFeatures
 *
 * Provides intelligent feature gating across Android API levels 23 (6.0) through 37 (Android 17).
 * Ensures graceful degradation so the app never crashes on any supported version, while
 * taking advantage of modern platform capabilities when available.
 *
 * ## Design Principles:
 * - **No crashes**: Every modern API call is wrapped in version checks
 * - **Graceful degradation**: Unavailable features have manual/fallback alternatives
 * - **Transparent**: Users are informed when a feature requires a newer OS version
 * - **Inclusive**: Core functionality works identically on all supported versions
 *
 * ## Supported Range:
 * - Minimum: Android 6.0 (API 23) — Marshmallow
 * - Maximum: Android 17 (API 37) — latest
 *
 * @since 1.0.0
 */
object VersionAdaptiveFeatures {

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 1: Feature Enumeration
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * All features in the app that have version-dependent availability.
     *
     * Each feature documents its minimum API level and the fallback behavior
     * for devices running older versions.
     */
    enum class Feature(
        val minimumApi: Int,
        val displayName: String,
        val description: String
    ) {
        /**
         * Automatically reads incoming SMS messages to detect transactions.
         * Available on all supported versions (API 23+).
         */
        SMS_AUTO_READ(
            minimumApi = 23,
            displayName = "Automatic Message Reading",
            description = "Reads incoming messages to detect money transactions automatically"
        ),

        /**
         * Manual entry of income and expenses.
         * Core feature available on all versions.
         */
        MANUAL_EXPENSE_ENTRY(
            minimumApi = 23,
            displayName = "Manual Entry",
            description = "Add income and expenses by typing them in"
        ),

        /**
         * Photo OCR — take pictures of receipts/bills and extract text.
         * Uses CameraX + ML Kit, available on all supported versions.
         */
        VAULT_PHOTO_OCR(
            minimumApi = 23,
            displayName = "Photo Scanning",
            description = "Take photos of receipts and bills to save them"
        ),

        /**
         * Voice transcription for notes and memos.
         * Requires SpeechRecognizer improvements in API 26+.
         * Fallback: keyboard-only text entry on API 23-25.
         */
        VAULT_VOICE_TRANSCRIPTION(
            minimumApi = 26,
            displayName = "Voice Notes",
            description = "Speak to add notes — your words become text"
        ),

        /**
         * Biometric authentication (fingerprint, face).
         * Requires BiometricPrompt available from API 28, with compat library usable from API 23
         * but hardware fingerprint API reliable from API 26.
         * Fallback: PIN/pattern lock on API 23-25.
         */
        BIOMETRIC_LOCK(
            minimumApi = 26,
            displayName = "Fingerprint Lock",
            description = "Lock the app with your fingerprint or face"
        ),

        /**
         * Notification channels for categorizing alerts.
         * Introduced in API 26 (Oreo).
         * Fallback: single combined notification stream on API 23-25.
         */
        NOTIFICATION_CHANNELS(
            minimumApi = 26,
            displayName = "Notification Categories",
            description = "Separate controls for different types of alerts"
        ),

        /**
         * Privacy Guard using AppOps for monitoring app access.
         * Reliable from API 29+.
         * Fallback: feature unavailable with informative message on older versions.
         */
        PRIVACY_GUARD(
            minimumApi = 29,
            displayName = "Privacy Guard",
            description = "Monitor which apps access your data"
        ),

        /**
         * Per-app language selection using system API.
         * Introduced in API 33 (Android 13).
         * Fallback: manual locale switching via AppCompatDelegate on older versions.
         */
        PER_APP_LANGUAGE(
            minimumApi = 33,
            displayName = "App Language",
            description = "Choose a different language just for this app"
        ),

        /**
         * Material You dynamic color theming.
         * Requires API 31+ for wallpaper-based colors, but feature-rich from API 35.
         * Fallback: static color themes on older versions.
         */
        DYNAMIC_COLORS(
            minimumApi = 35,
            displayName = "Dynamic Colors",
            description = "App colors that match your phone's wallpaper"
        ),

        /**
         * Edge-to-edge UI extending content under system bars.
         * Fully supported and enforced from API 35.
         * Fallback: traditional system bar behavior on older versions.
         */
        EDGE_TO_EDGE_UI(
            minimumApi = 35,
            displayName = "Full Screen Layout",
            description = "Content extends to the edges of your screen"
        ),

        /**
         * Predictive back gesture with animations.
         * Available from API 34, fully enforced from API 35.
         * Fallback: standard back button/gesture behavior.
         */
        PREDICTIVE_BACK_GESTURE(
            minimumApi = 35,
            displayName = "Smooth Back Navigation",
            description = "See a preview when swiping back"
        ),

        /**
         * Reading call logs to correlate with financial contacts.
         * Available on all supported versions with appropriate permissions.
         */
        CALL_LOG_READING(
            minimumApi = 23,
            displayName = "Call History Reading",
            description = "Check call history to match contacts with transactions"
        );

        companion object {
            /**
             * Returns all features sorted by minimum API level (lowest first).
             */
            fun allSortedByMinApi(): List<Feature> =
                entries.sortedBy { it.minimumApi }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 2: Feature Availability Checks
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if a specific feature is available on the current device.
     *
     * @param feature The feature to check availability for.
     * @return `true` if the feature can be used on this device's Android version.
     *
     * Example:
     * ```kotlin
     * if (VersionAdaptiveFeatures.isFeatureAvailable(Feature.BIOMETRIC_LOCK)) {
     *     showBiometricPrompt()
     * } else {
     *     showPinEntry()
     * }
     * ```
     */
    fun isFeatureAvailable(feature: Feature): Boolean {
        return Build.VERSION.SDK_INT >= feature.minimumApi
    }

    /**
     * Returns all features available on the current device.
     *
     * @return List of features that can be used on this Android version.
     */
    fun getAvailableFeatures(): List<Feature> {
        return Feature.entries.filter { isFeatureAvailable(it) }
    }

    /**
     * Returns features NOT available on this device, paired with the reason
     * why they're unavailable and what alternative is offered.
     *
     * @return List of pairs: (unavailable feature, human-readable explanation).
     */
    fun getUnavailableFeatures(): List<Pair<Feature, String>> {
        return Feature.entries
            .filter { !isFeatureAvailable(it) }
            .map { feature ->
                val reason = getUnavailabilityReason(feature)
                Pair(feature, reason)
            }
    }

    /**
     * Returns a user-friendly description of the fallback/alternative for
     * a feature that is not available on this device.
     *
     * @param feature The unavailable feature.
     * @return Description of the alternative, or `null` if the feature IS available.
     */
    fun getAlternativeForUnavailable(feature: Feature): String? {
        if (isFeatureAvailable(feature)) return null

        return when (feature) {
            Feature.VAULT_VOICE_TRANSCRIPTION ->
                "Voice notes are not available on this version. You can type your notes using the keyboard instead."

            Feature.BIOMETRIC_LOCK ->
                "Fingerprint lock is not available on this version. You can protect the app with a PIN or pattern lock instead."

            Feature.NOTIFICATION_CHANNELS ->
                "Notification categories are not available on this version. All alerts will appear together in one notification stream."

            Feature.PRIVACY_GUARD ->
                "This feature requires a newer version of your phone's operating system. " +
                        "You can check app permissions manually in your phone's Settings."

            Feature.PER_APP_LANGUAGE ->
                "System language switching is not available on this version. " +
                        "You can change the language inside the app's settings instead."

            Feature.DYNAMIC_COLORS ->
                "Dynamic colors require a newer version of your phone's operating system. " +
                        "You can choose from preset color themes in the app settings."

            Feature.EDGE_TO_EDGE_UI ->
                "Full screen layout requires a newer version of your phone's operating system. " +
                        "The app will display with standard screen margins."

            Feature.PREDICTIVE_BACK_GESTURE ->
                "Smooth back navigation requires a newer version of your phone's operating system. " +
                        "Standard back navigation works normally."

            // Features available on all versions — should not reach here
            Feature.SMS_AUTO_READ,
            Feature.MANUAL_EXPENSE_ENTRY,
            Feature.VAULT_PHOTO_OCR,
            Feature.CALL_LOG_READING -> null
        }
    }

    /**
     * Generates the reason string for why a feature is unavailable.
     */
    private fun getUnavailabilityReason(feature: Feature): String {
        val currentApi = Build.VERSION.SDK_INT
        val requiredApi = feature.minimumApi
        val alternative = getAlternativeForUnavailable(feature) ?: ""

        return buildString {
            append("${feature.displayName} requires Android ")
            append(getAndroidVersionName(requiredApi))
            append(" or newer. ")
            append("Your phone is running Android ")
            append(getAndroidVersionName(currentApi))
            append(". ")
            if (alternative.isNotEmpty()) {
                append(alternative)
            }
        }
    }

    /**
     * Maps API level to user-friendly Android version name.
     */
    private fun getAndroidVersionName(apiLevel: Int): String = when (apiLevel) {
        23 -> "6.0 (Marshmallow)"
        24 -> "7.0 (Nougat)"
        25 -> "7.1 (Nougat)"
        26 -> "8.0 (Oreo)"
        27 -> "8.1 (Oreo)"
        28 -> "9.0 (Pie)"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> if (apiLevel > 37) "$apiLevel (newer)" else "$apiLevel"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 3: Permission Handling (API 23-25 specific flow)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Manages runtime permissions with version-specific flows.
     *
     * Android 6.0-7.1 (API 23-25) has a different permission model nuance:
     * - No "Don't ask again" detection on API 23
     * - Permission groups behave differently
     * - SMS permission requires special handling pre-Oreo
     */
    object PermissionHandler {

        /** All permissions the app may request */
        object Permissions {
            const val SMS_READ = Manifest.permission.READ_SMS
            const val SMS_RECEIVE = Manifest.permission.RECEIVE_SMS
            const val CAMERA = Manifest.permission.CAMERA
            const val CALL_LOG = Manifest.permission.READ_CALL_LOG
            const val PHONE_STATE = Manifest.permission.READ_PHONE_STATE
            const val CONTACTS = Manifest.permission.READ_CONTACTS

            // POST_NOTIFICATIONS only exists on API 33+
            val NOTIFICATIONS: String?
                get() = if (Build.VERSION.SDK_INT >= 33) {
                    "android.permission.POST_NOTIFICATIONS"
                } else null
        }

        /**
         * Checks if a permission is granted, handling version differences.
         *
         * @param context Application context.
         * @param permission The permission to check.
         * @return `true` if the permission is granted.
         */
        fun isPermissionGranted(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Requests permissions with appropriate flow for the current API level.
         *
         * On API 23-25:
         * - Requests each permission individually for clarity
         * - Shows rationale dialog before requesting
         * - Cannot detect "never ask again" reliably
         *
         * On API 26+:
         * - Can request permission groups
         * - "Don't ask again" can be detected via shouldShowRequestPermissionRationale
         *
         * @param activity The activity requesting permissions.
         * @param permissions List of permissions to request.
         * @param requestCode The request code for the result callback.
         * @param onRationaleNeeded Callback when rationale should be shown to user.
         */
        fun requestPermissions(
            activity: Activity,
            permissions: List<String>,
            requestCode: Int,
            onRationaleNeeded: ((List<String>) -> Unit)? = null
        ) {
            val ungrantedPermissions = permissions.filter {
                !isPermissionGranted(activity, it)
            }

            if (ungrantedPermissions.isEmpty()) return

            // Check if we need to show rationale
            val needsRationale = ungrantedPermissions.filter {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }

            if (needsRationale.isNotEmpty() && onRationaleNeeded != null) {
                onRationaleNeeded(needsRationale)
                return
            }

            // On API 23-25, request one at a time for older devices that may
            // have issues with batch permission requests
            if (Build.VERSION.SDK_INT <= 25) {
                ActivityCompat.requestPermissions(
                    activity,
                    ungrantedPermissions.take(1).toTypedArray(),
                    requestCode
                )
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    ungrantedPermissions.toTypedArray(),
                    requestCode
                )
            }
        }

        /**
         * Gets the list of permissions needed for SMS reading feature.
         * Pre-Oreo devices may need additional phone state permission.
         */
        fun getSmsPermissions(): List<String> {
            val permissions = mutableListOf(
                Permissions.SMS_READ,
                Permissions.SMS_RECEIVE
            )
            // Pre-Oreo: also need PHONE_STATE for SIM detection
            if (Build.VERSION.SDK_INT < 26) {
                permissions.add(Permissions.PHONE_STATE)
            }
            return permissions
        }

        /**
         * Checks if a permission has been permanently denied (user selected "Don't ask again").
         * This detection is only reliable on API 26+.
         *
         * @return `true` if permanently denied, `false` if can still ask, `null` if detection
         *         is unreliable (API 23-25).
         */
        fun isPermanentlyDenied(activity: Activity, permission: String): Boolean? {
            if (isPermissionGranted(activity, permission)) return false

            // On API 23-25, we cannot reliably detect this
            if (Build.VERSION.SDK_INT <= 25) return null

            // If shouldShowRationale is false AND permission is not granted,
            // the user has selected "Don't ask again"
            return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        /**
         * Opens the app's system settings page so the user can manually grant permissions.
         * This is the fallback when permissions are permanently denied.
         */
        fun openAppSettings(context: Context) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * Gets a user-friendly explanation for why a permission is needed.
         * Uses simple, non-technical language suitable for all users.
         */
        fun getPermissionRationale(permission: String): String = when (permission) {
            Permissions.SMS_READ, Permissions.SMS_RECEIVE ->
                "This app reads your messages to automatically find money transactions. " +
                        "Your messages stay private and are only checked for amounts and dates."

            Permissions.CAMERA ->
                "The camera is used to take photos of receipts and bills so you can save them."

            Permissions.CALL_LOG ->
                "Call history helps match phone numbers with people who send you money or bills."

            Permissions.PHONE_STATE ->
                "Phone information helps identify which SIM card received transaction messages."

            Permissions.CONTACTS ->
                "Contact names help label your transactions with real names instead of phone numbers."

            else -> "This permission helps the app work better for you."
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 4: Notification Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Version-adaptive notification system.
     *
     * - API 23-25: Basic notifications without channels (all alerts combined)
     * - API 26+: Full notification channel support with per-category controls
     * - API 33+: Requires POST_NOTIFICATIONS permission
     */
    object NotificationHelper {

        /** Notification channel IDs (used on API 26+) */
        object Channels {
            const val TRANSACTIONS = "channel_transactions"
            const val BUDGET_ALERTS = "channel_budget_alerts"
            const val WEEKLY_SUMMARY = "channel_weekly_summary"
            const val FRAUD_ALERTS = "channel_fraud_alerts"
            const val ACHIEVEMENTS = "channel_achievements"
            const val REMINDERS = "channel_reminders"
        }

        /** Channel display names for user-facing settings */
        private val channelNames = mapOf(
            Channels.TRANSACTIONS to "Transactions",
            Channels.BUDGET_ALERTS to "Spending Alerts",
            Channels.WEEKLY_SUMMARY to "Weekly Summary",
            Channels.FRAUD_ALERTS to "Suspicious Activity",
            Channels.ACHIEVEMENTS to "Achievements",
            Channels.REMINDERS to "Reminders"
        )

        /** Channel descriptions */
        private val channelDescriptions = mapOf(
            Channels.TRANSACTIONS to "Notifications when money comes in or goes out",
            Channels.BUDGET_ALERTS to "Alerts when you're spending more than planned",
            Channels.WEEKLY_SUMMARY to "Your weekly money summary and insights",
            Channels.FRAUD_ALERTS to "Urgent alerts for suspicious transactions",
            Channels.ACHIEVEMENTS to "Celebrations when you reach savings goals",
            Channels.REMINDERS to "Reminders for upcoming bills and payments"
        )

        /**
         * Creates all notification channels. Safe to call on any API level.
         * On API < 26, this is a no-op.
         */
        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return

            createChannelsApi26(context)
        }

        @RequiresApi(26)
        private fun createChannelsApi26(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                createChannel(
                    Channels.FRAUD_ALERTS,
                    NotificationManager.IMPORTANCE_HIGH
                ),
                createChannel(
                    Channels.BUDGET_ALERTS,
                    NotificationManager.IMPORTANCE_HIGH
                ),
                createChannel(
                    Channels.TRANSACTIONS,
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                createChannel(
                    Channels.WEEKLY_SUMMARY,
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                createChannel(
                    Channels.ACHIEVEMENTS,
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                createChannel(
                    Channels.REMINDERS,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )

            notificationManager.createNotificationChannels(channels)
        }

        @RequiresApi(26)
        private fun createChannel(channelId: String, importance: Int): NotificationChannel {
            val name = channelNames[channelId] ?: "General"
            val description = channelDescriptions[channelId] ?: ""

            return NotificationChannel(channelId, name, importance).apply {
                this.description = description
                enableLights(true)
                enableVibration(true)

                // Fraud alerts get special treatment
                if (channelId == Channels.FRAUD_ALERTS) {
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            }
        }

        /**
         * Builds a notification compatible with any supported API level.
         *
         * @param context Application context.
         * @param channelId The channel ID (used on API 26+, ignored on lower).
         * @param title Notification title.
         * @param body Notification body text.
         * @param priority Priority level (used on API < 26).
         * @return A NotificationCompat.Builder ready for customization and posting.
         */
        fun buildNotification(
            context: Context,
            channelId: String,
            title: String,
            body: String,
            priority: Int = NotificationCompat.PRIORITY_DEFAULT
        ): NotificationCompat.Builder {
            // NotificationCompat handles channel ID gracefully on pre-26
            return NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(priority)
                .setAutoCancel(true)
                .apply {
                    // On pre-26, set vibration and sound directly
                    if (Build.VERSION.SDK_INT < 26) {
                        setDefaults(NotificationCompat.DEFAULT_ALL)
                    }
                }
        }

        /**
         * Checks if notification permission is needed and not yet granted.
         * Only relevant on API 33+. Returns false on older versions (no permission needed).
         */
        fun needsNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return false
            return ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 5: Foreground Service Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles foreground service differences across API levels.
     *
     * - API 23-25: Basic foreground service, no type required
     * - API 26-28: Must call startForeground within 5 seconds
     * - API 29+: Foreground service type required in manifest
     * - API 34+: Must specify foregroundServiceType at runtime
     */
    object ForegroundServiceHelper {

        /**
         * Starts a foreground service using the appropriate method for the current API level.
         *
         * @param context Application context.
         * @param serviceIntent Intent for the service to start.
         */
        fun startForegroundServiceCompat(context: Context, serviceIntent: Intent) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        /**
         * Determines if foreground service type must be specified in the startForeground call.
         * Required on API 34+.
         */
        fun requiresForegroundServiceType(): Boolean {
            return Build.VERSION.SDK_INT >= 34
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 6: File Access Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles file URI access differences.
     *
     * - API 23: Direct file:// URIs allowed
     * - API 24+: Must use FileProvider content:// URIs
     * - API 29+: Scoped storage restrictions
     * - API 30+: MANAGE_EXTERNAL_STORAGE for broad access
     */
    object FileAccessHelper {

        /**
         * Gets a shareable URI for a file, using FileProvider on API 24+.
         *
         * @param context Application context.
         * @param file The file to get a URI for.
         * @return A content:// URI on API 24+ or file:// URI on API 23.
         */
        fun getFileUri(context: Context, file: java.io.File): Uri {
            return if (Build.VERSION.SDK_INT >= 24) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }
        }

        /**
         * Creates an intent to share/view a file with proper URI permissions.
         *
         * @param context Application context.
         * @param file The file to share.
         * @param mimeType The MIME type of the file.
         * @return An intent configured with proper URI access grants.
         */
        fun createShareIntent(context: Context, file: java.io.File, mimeType: String): Intent {
            val uri = getFileUri(context, file)

            return Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        /**
         * Checks if the app has storage access appropriate for the current API level.
         */
        fun hasStorageAccess(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT >= 30 -> {
                    // Scoped storage — app-specific directories are always accessible
                    true
                }
                Build.VERSION.SDK_INT >= 23 -> {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                else -> true
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 7: Biometric / Authentication Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Authentication method selection based on device capabilities.
     *
     * - API 23-25: PIN/Pattern only (no reliable biometric API)
     * - API 26-27: FingerprintManager (deprecated but functional)
     * - API 28+: BiometricPrompt via AndroidX
     */
    object AuthenticationHelper {

        /**
         * Determines the best authentication method available on this device.
         *
         * @param context Application context.
         * @return The recommended authentication method.
         */
        fun getBestAuthMethod(context: Context): AuthMethod {
            if (Build.VERSION.SDK_INT < 26) {
                return AuthMethod.PIN_PATTERN
            }

            // Use AndroidX BiometricManager for consistent API 23+ checking
            val biometricManager = BiometricManager.from(context)
            return when (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            )) {
                BiometricManager.BIOMETRIC_SUCCESS -> AuthMethod.BIOMETRIC
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AuthMethod.PIN_PATTERN
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> AuthMethod.PIN_PATTERN
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthMethod.BIOMETRIC_NOT_ENROLLED
                else -> AuthMethod.PIN_PATTERN
            }
        }

        /**
         * Gets a user-friendly description of the authentication method.
         */
        fun getAuthMethodDescription(method: AuthMethod): String = when (method) {
            AuthMethod.BIOMETRIC ->
                "Use your fingerprint or face to unlock the app"
            AuthMethod.PIN_PATTERN ->
                "Use a PIN number or pattern to lock the app"
            AuthMethod.BIOMETRIC_NOT_ENROLLED ->
                "Fingerprint lock is available but you haven't set up a fingerprint on your phone yet. " +
                        "You can add one in your phone's Settings, or use a PIN lock for now."
        }
    }

    /**
     * Available authentication methods.
     */
    enum class AuthMethod {
        /** Fingerprint or face authentication */
        BIOMETRIC,
        /** PIN code or pattern lock */
        PIN_PATTERN,
        /** Hardware supports biometric but no fingerprint/face enrolled */
        BIOMETRIC_NOT_ENROLLED
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 8: Language / Locale Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles per-app language switching across API levels.
     *
     * - API 33+: Use system per-app language API (LocaleManager)
     * - API 23-32: Use AppCompatDelegate.setApplicationLocales()
     */
    object LocaleHelper {

        /**
         * Sets the app language using the best available method.
         *
         * @param context Application context.
         * @param languageTag BCP 47 language tag (e.g., "hi", "ta", "en").
         */
        fun setAppLanguage(context: Context, languageTag: String) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(languageTag)
            // AppCompatDelegate handles the version check internally
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }

        /**
         * Gets the currently active app language.
         *
         * @return The BCP 47 language tag, or null if using system default.
         */
        fun getCurrentAppLanguage(): String? {
            val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) null
            else locales.toLanguageTags()
        }

        /**
         * Whether the system natively supports per-app language (API 33+).
         * On older versions, the app handles locale changes itself.
         */
        fun hasSystemPerAppLanguage(): Boolean {
            return Build.VERSION.SDK_INT >= 33
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 9: UI Compatibility
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles UI feature availability across API levels.
     */
    object UiCompatibility {

        /**
         * Whether dynamic colors (Material You) are available.
         * Available on API 31+ with full support from API 35.
         */
        fun isDynamicColorAvailable(): Boolean {
            return Build.VERSION.SDK_INT >= 31
        }

        /**
         * Whether full dynamic color with advanced palette extraction is available.
         */
        fun isFullDynamicColorAvailable(): Boolean {
            return Build.VERSION.SDK_INT >= 35
        }

        /**
         * Whether edge-to-edge mode should be enabled.
         * On API 35+ it's enforced, so we always enable it there.
         * On older versions, we use traditional insets.
         */
        fun shouldEnableEdgeToEdge(): Boolean {
            return Build.VERSION.SDK_INT >= 35
        }

        /**
         * Whether predictive back gesture should be opted into.
         */
        fun supportsPredictiveBack(): Boolean {
            return Build.VERSION.SDK_INT >= 34
        }

        /**
         * Gets the appropriate window inset handling strategy for the current API level.
         */
        fun getInsetStrategy(): InsetStrategy {
            return when {
                Build.VERSION.SDK_INT >= 35 -> InsetStrategy.EDGE_TO_EDGE
                Build.VERSION.SDK_INT >= 30 -> InsetStrategy.WINDOW_INSETS_COMPAT
                else -> InsetStrategy.LEGACY_SYSTEM_UI_FLAGS
            }
        }
    }

    /**
     * Strategy for handling window insets (status bar, navigation bar).
     */
    enum class InsetStrategy {
        /** Full edge-to-edge with WindowInsetsCompat (API 35+) */
        EDGE_TO_EDGE,
        /** WindowInsetsCompat for controlled inset handling (API 30-34) */
        WINDOW_INSETS_COMPAT,
        /** Legacy system UI flags for older devices (API 23-29) */
        LEGACY_SYSTEM_UI_FLAGS
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 10: Device Capability Assessment
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Provides a comprehensive assessment of device capabilities.
     * Useful for deciding which features to highlight to the user.
     */
    data class DeviceCapabilities(
        val apiLevel: Int,
        val androidVersionName: String,
        val availableFeatureCount: Int,
        val totalFeatureCount: Int,
        val hasFullBiometric: Boolean,
        val hasNotificationChannels: Boolean,
        val hasDynamicColors: Boolean,
        val hasEdgeToEdge: Boolean,
        val hasScopedStorage: Boolean,
        val hasPerAppLanguage: Boolean,
        val recommendedAuthMethod: AuthMethod,
        val unavailableFeaturesWithAlternatives: List<Pair<Feature, String>>
    )

    /**
     * Assesses the current device and returns a complete capability profile.
     *
     * @param context Application context.
     * @return A [DeviceCapabilities] object describing this device.
     */
    fun assessDeviceCapabilities(context: Context): DeviceCapabilities {
        val apiLevel = Build.VERSION.SDK_INT
        val available = getAvailableFeatures()
        val unavailable = getUnavailableFeatures()

        return DeviceCapabilities(
            apiLevel = apiLevel,
            androidVersionName = getAndroidVersionName(apiLevel),
            availableFeatureCount = available.size,
            totalFeatureCount = Feature.entries.size,
            hasFullBiometric = isFeatureAvailable(Feature.BIOMETRIC_LOCK),
            hasNotificationChannels = isFeatureAvailable(Feature.NOTIFICATION_CHANNELS),
            hasDynamicColors = isFeatureAvailable(Feature.DYNAMIC_COLORS),
            hasEdgeToEdge = isFeatureAvailable(Feature.EDGE_TO_EDGE_UI),
            hasScopedStorage = apiLevel >= 29,
            hasPerAppLanguage = isFeatureAvailable(Feature.PER_APP_LANGUAGE),
            recommendedAuthMethod = AuthenticationHelper.getBestAuthMethod(context),
            unavailableFeaturesWithAlternatives = unavailable
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SECTION 11: Safe API Execution Wrappers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Executes a block of code only if the device meets the minimum API level.
     * Returns the result of the block, or the fallback value if the API level is too low.
     *
     * @param minApi The minimum API level required.
     * @param fallback The value to return if the API level is too low.
     * @param block The code to execute if the API level is sufficient.
     * @return Result of [block] or [fallback].
     *
     * Example:
     * ```kotlin
     * val color = runOnApiOrFallback(31, defaultColor) {
     *     DynamicColors.getWallpaperPrimaryColor(context)
     * }
     * ```
     */
    inline fun <T> runOnApiOrFallback(minApi: Int, fallback: T, block: () -> T): T {
        return if (Build.VERSION.SDK_INT >= minApi) {
            try {
                block()
            } catch (e: Exception) {
                fallback
            }
        } else {
            fallback
        }
    }

    /**
     * Executes a block of code only if the device meets the minimum API level.
     * Does nothing if the API level is too low.
     *
     * @param minApi The minimum API level required.
     * @param block The code to execute.
     */
    inline fun runOnApi(minApi: Int, block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= minApi) {
            try {
                block()
            } catch (e: Exception) {
                // Silently fail — the feature is optional
            }
        }
    }

    /**
     * Returns a summary of the version compatibility status for diagnostics/support.
     */
    fun getCompatibilitySummary(context: Context): String {
        val capabilities = assessDeviceCapabilities(context)
        return buildString {
            appendLine("Device Compatibility Report")
            appendLine("═══════════════════════════")
            appendLine("Android Version: ${capabilities.androidVersionName}")
            appendLine("API Level: ${capabilities.apiLevel}")
            appendLine("Features Available: ${capabilities.availableFeatureCount}/${capabilities.totalFeatureCount}")
            appendLine()
            appendLine("Available Features:")
            getAvailableFeatures().forEach { feature ->
                appendLine("  ✓ ${feature.displayName}")
            }
            if (capabilities.unavailableFeaturesWithAlternatives.isNotEmpty()) {
                appendLine()
                appendLine("Unavailable (with alternatives):")
                capabilities.unavailableFeaturesWithAlternatives.forEach { (feature, reason) ->
                    appendLine("  ✗ ${feature.displayName}")
                    appendLine("    → ${getAlternativeForUnavailable(feature) ?: "No alternative"}")
                }
            }
            appendLine()
            appendLine("Authentication: ${capabilities.recommendedAuthMethod.name}")
        }
    }
}
