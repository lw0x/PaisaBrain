package com.paisabrain.app.emergency

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Lock-screen accessible emergency information card.
 *
 * Provides critical medical and contact information visible from the device lock screen
 * as a persistent notification. Designed to be accessible by first responders and medical
 * staff without requiring the device to be unlocked.
 *
 * ## Features
 * - Persistent lock-screen notification with emergency info
 * - One-tap emergency call buttons (primary contact + ambulance)
 * - Medical ID formatted for hospital staff
 * - Setup wizard support for guided data entry
 * - Bank card helpline numbers for financial emergencies
 * - Works on Android 6.0+ via notification channel system
 *
 * ## Usage
 * ```kotlin
 * val card = EmergencyInfoCard(context)
 * card.saveEmergencyInfo(myInfo)
 * card.showOnLockScreen(context)
 * ```
 *
 * @property context Application context for system service access
 */
class EmergencyInfoCard(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "emergency_info_channel"
        private const val CHANNEL_NAME = "Emergency Information"
        private const val NOTIFICATION_ID = 9911
        private const val PREFS_FILE = "emergency_info_prefs"
        private const val KEY_EMERGENCY_INFO = "emergency_info_data"
        private const val KEY_IS_ENABLED = "lock_screen_enabled"
        private const val KEY_CARD_HELPLINES = "card_helplines"

        /** Indian ambulance service number */
        private const val AMBULANCE_NUMBER_PRIMARY = "108"

        /** Unified emergency number */
        private const val EMERGENCY_NUMBER_UNIFIED = "112"
    }

    private val gson = Gson()

    // ─────────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Represents a single emergency contact person.
     *
     * @property name Full name of the contact
     * @property phone Phone number (with country code preferred)
     * @property relationship Relationship to the user (e.g., "Spouse", "Parent", "Sibling")
     */
    data class EmergencyContact(
        val name: String,
        val phone: String,
        val relationship: String
    )

    /**
     * Complete emergency information for the user.
     *
     * This data is displayed on the lock screen and formatted for medical staff.
     * All fields are optional to allow incremental filling, but completeness is encouraged.
     *
     * @property bloodGroup Blood group (e.g., "O+", "AB-")
     * @property allergies List of known allergies (medications, foods, etc.)
     * @property medicalConditions List of ongoing medical conditions (diabetes, asthma, etc.)
     * @property medications Current medications with dosages
     * @property emergencyContacts Up to 3 emergency contacts, ordered by priority
     * @property insurancePolicyNumber Health insurance policy number
     * @property insuranceHelpline Insurance company helpline number
     * @property homeAddress Home address for emergency services
     * @property doctorName Primary physician's name
     * @property doctorPhone Primary physician's contact number
     */
    data class EmergencyInfo(
        val bloodGroup: String = "",
        val allergies: List<String> = emptyList(),
        val medicalConditions: List<String> = emptyList(),
        val medications: List<String> = emptyList(),
        val emergencyContacts: List<EmergencyContact> = emptyList(),
        val insurancePolicyNumber: String = "",
        val insuranceHelpline: String = "",
        val homeAddress: String = "",
        val doctorName: String = "",
        val doctorPhone: String = ""
    )

    /**
     * Bank card helpline entry for quick access during card loss/fraud.
     *
     * @property bankName Display name of the bank (user-entered, generic)
     * @property helplineNumber The helpline phone number
     * @property cardLastFour Last 4 digits of card for identification (optional)
     */
    data class CardHelpline(
        val bankName: String,
        val helplineNumber: String,
        val cardLastFour: String = ""
    )

    /**
     * Represents a single step in the setup wizard.
     *
     * @property stepNumber Sequential step number (1-based)
     * @property title Display title for this step
     * @property description Helpful description of what to fill
     * @property fieldType Type of input expected
     * @property isCompleted Whether user has filled this step
     */
    data class SetupStep(
        val stepNumber: Int,
        val title: String,
        val description: String,
        val fieldType: FieldType,
        val isCompleted: Boolean = false
    )

    /**
     * Types of input fields in the setup wizard.
     */
    enum class FieldType {
        SINGLE_TEXT,
        MULTI_TEXT_LIST,
        CONTACT_ENTRY,
        ADDRESS,
        PHONE_NUMBER
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Storage
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns encrypted SharedPreferences for secure storage of medical data.
     */
    private fun getSecurePrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Saves emergency information to secure local storage.
     *
     * @param info The complete emergency information to store
     */
    fun saveEmergencyInfo(info: EmergencyInfo) {
        val json = gson.toJson(info)
        getSecurePrefs().edit().putString(KEY_EMERGENCY_INFO, json).apply()
    }

    /**
     * Retrieves stored emergency information.
     *
     * @return The stored [EmergencyInfo] or null if not yet configured
     */
    fun getEmergencyInfo(): EmergencyInfo? {
        val json = getSecurePrefs().getString(KEY_EMERGENCY_INFO, null) ?: return null
        return try {
            gson.fromJson(json, EmergencyInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves bank card helpline numbers for quick access.
     *
     * @param helplines List of card helpline entries
     */
    fun saveCardHelplines(helplines: List<CardHelpline>) {
        val json = gson.toJson(helplines)
        getSecurePrefs().edit().putString(KEY_CARD_HELPLINES, json).apply()
    }

    /**
     * Retrieves stored card helpline numbers.
     *
     * @return List of [CardHelpline] entries, or empty list if none configured
     */
    fun getCardHelplines(): List<CardHelpline> {
        val json = getSecurePrefs().getString(KEY_CARD_HELPLINES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<CardHelpline>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Lock Screen Notification
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Creates and shows a persistent notification on the lock screen with emergency info.
     *
     * The notification includes:
     * - Blood group and critical medical info in the expanded view
     * - Action button to call primary emergency contact
     * - Action button to call ambulance (108/112)
     *
     * Requires notification permission on Android 13+.
     *
     * @param context Context for notification manager access
     */
    fun showOnLockScreen(context: Context) {
        val info = getEmergencyInfo()
        if (info == null) {
            // Cannot show notification without emergency info
            return
        }

        createNotificationChannel(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Build the notification content
        val contentText = buildNotificationContent(info)

        // PendingIntent for calling primary emergency contact
        val callPrimaryIntent = if (info.emergencyContacts.isNotEmpty()) {
            createCallPendingIntent(context, info.emergencyContacts[0].phone, 1001)
        } else {
            null
        }

        // PendingIntent for calling ambulance
        val callAmbulanceIntent = createCallPendingIntent(context, AMBULANCE_NUMBER_PRIMARY, 1002)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call) // Generic system icon
            .setContentTitle("⚕️ Emergency Medical Info")
            .setContentText("Blood: ${info.bloodGroup} | Tap to expand")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
                    .setBigContentTitle("⚕️ Emergency Medical Information")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true) // Persistent — cannot be swiped away
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen
            .setShowWhen(false)

        // Add ambulance call action
        callAmbulanceIntent?.let { intent ->
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "🚑 Call Ambulance (108)",
                intent
            )
        }

        // Add primary contact call action
        callPrimaryIntent?.let { intent ->
            val contactName = info.emergencyContacts.firstOrNull()?.name ?: "Emergency Contact"
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "📞 Call $contactName",
                intent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())

        // Save enabled state
        getSecurePrefs().edit().putBoolean(KEY_IS_ENABLED, true).apply()
    }

    /**
     * Removes the emergency info notification from the lock screen.
     *
     * @param context Context for notification manager access
     */
    fun hideFromLockScreen(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        getSecurePrefs().edit().putBoolean(KEY_IS_ENABLED, false).apply()
    }

    /**
     * Checks whether the lock screen notification is currently enabled.
     *
     * @return true if the notification should be shown
     */
    fun isLockScreenEnabled(): Boolean {
        return getSecurePrefs().getBoolean(KEY_IS_ENABLED, false)
    }

    /**
     * Creates the notification channel required for Android 8.0+.
     * The channel is configured for high importance to ensure lock screen visibility.
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance = no sound, but persistent
            ).apply {
                description = "Shows your emergency medical information on the lock screen"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the expanded notification text from emergency info.
     */
    private fun buildNotificationContent(info: EmergencyInfo): String {
        val sb = StringBuilder()

        sb.appendLine("🩸 Blood Group: ${info.bloodGroup.ifEmpty { "Not set" }}")

        if (info.allergies.isNotEmpty()) {
            sb.appendLine("⚠️ Allergies: ${info.allergies.joinToString(", ")}")
        }

        if (info.medicalConditions.isNotEmpty()) {
            sb.appendLine("🏥 Conditions: ${info.medicalConditions.joinToString(", ")}")
        }

        if (info.medications.isNotEmpty()) {
            sb.appendLine("💊 Medications: ${info.medications.joinToString(", ")}")
        }

        if (info.emergencyContacts.isNotEmpty()) {
            sb.appendLine("📞 Emergency Contacts:")
            info.emergencyContacts.forEachIndexed { index, contact ->
                sb.appendLine("   ${index + 1}. ${contact.name} (${contact.relationship}): ${contact.phone}")
            }
        }

        if (info.doctorName.isNotEmpty()) {
            sb.appendLine("👨‍⚕️ Doctor: ${info.doctorName} — ${info.doctorPhone}")
        }

        if (info.insurancePolicyNumber.isNotEmpty()) {
            sb.appendLine("📋 Insurance: ${info.insurancePolicyNumber}")
            if (info.insuranceHelpline.isNotEmpty()) {
                sb.appendLine("   Helpline: ${info.insuranceHelpline}")
            }
        }

        return sb.toString().trimEnd()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Emergency Calling
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Initiates an emergency call to the specified contact.
     *
     * Falls back to dial intent if CALL_PHONE permission is not granted.
     *
     * @param context Context for starting the call activity
     * @param contactIndex Index of the contact in the emergency contacts list (0-based)
     */
    fun makeEmergencyCall(context: Context, contactIndex: Int) {
        val info = getEmergencyInfo() ?: return
        val contacts = info.emergencyContacts

        if (contactIndex < 0 || contactIndex >= contacts.size) return

        val phoneNumber = contacts[contactIndex].phone
        dialNumber(context, phoneNumber)
    }

    /**
     * Calls the ambulance service directly.
     *
     * @param context Context for starting the call activity
     * @param useUnifiedNumber If true, dials 112 instead of 108
     */
    fun callAmbulance(context: Context, useUnifiedNumber: Boolean = false) {
        val number = if (useUnifiedNumber) EMERGENCY_NUMBER_UNIFIED else AMBULANCE_NUMBER_PRIMARY
        dialNumber(context, number)
    }

    /**
     * Calls a specific card helpline number.
     *
     * @param context Context for starting the call activity
     * @param helplineIndex Index in the stored helplines list
     */
    fun callCardHelpline(context: Context, helplineIndex: Int) {
        val helplines = getCardHelplines()
        if (helplineIndex < 0 || helplineIndex >= helplines.size) return
        dialNumber(context, helplines[helplineIndex].helplineNumber)
    }

    /**
     * Dials a phone number, using direct call if permission granted, otherwise opening dialer.
     */
    private fun dialNumber(context: Context, number: String) {
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        context.startActivity(intent)
    }

    /**
     * Creates a PendingIntent for making a phone call from the notification.
     */
    private fun createCallPendingIntent(
        context: Context,
        phoneNumber: String,
        requestCode: Int
    ): PendingIntent? {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Medical ID Format
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates a formatted Medical ID string suitable for showing to hospital/medical staff.
     *
     * The format follows standard medical identification patterns:
     * - Patient identification (blood group prominently displayed)
     * - Allergies (critical for treatment decisions)
     * - Current medications (drug interaction awareness)
     * - Medical conditions
     * - Emergency contacts
     * - Insurance details
     *
     * @return Formatted string, or a message indicating info is not configured
     */
    fun getFormattedMedicalId(): String {
        val info = getEmergencyInfo()
            ?: return "⚠️ Emergency info not configured. Please complete setup."

        val sb = StringBuilder()

        sb.appendLine("╔══════════════════════════════════════╗")
        sb.appendLine("║        MEDICAL IDENTIFICATION        ║")
        sb.appendLine("╠══════════════════════════════════════╣")
        sb.appendLine("║")
        sb.appendLine("║ BLOOD GROUP: ${info.bloodGroup.ifEmpty { "UNKNOWN" }}")
        sb.appendLine("║")

        // Allergies — critical section
        sb.appendLine("║ ⚠️ ALLERGIES:")
        if (info.allergies.isEmpty()) {
            sb.appendLine("║   No known allergies (NKDA)")
        } else {
            info.allergies.forEach { allergy ->
                sb.appendLine("║   • ${allergy.uppercase()}")
            }
        }
        sb.appendLine("║")

        // Medical conditions
        sb.appendLine("║ MEDICAL CONDITIONS:")
        if (info.medicalConditions.isEmpty()) {
            sb.appendLine("║   None reported")
        } else {
            info.medicalConditions.forEach { condition ->
                sb.appendLine("║   • $condition")
            }
        }
        sb.appendLine("║")

        // Medications
        sb.appendLine("║ CURRENT MEDICATIONS:")
        if (info.medications.isEmpty()) {
            sb.appendLine("║   None reported")
        } else {
            info.medications.forEach { medication ->
                sb.appendLine("║   • $medication")
            }
        }
        sb.appendLine("║")

        // Doctor
        if (info.doctorName.isNotEmpty()) {
            sb.appendLine("║ PRIMARY PHYSICIAN:")
            sb.appendLine("║   Dr. ${info.doctorName}")
            if (info.doctorPhone.isNotEmpty()) {
                sb.appendLine("║   Tel: ${info.doctorPhone}")
            }
            sb.appendLine("║")
        }

        // Emergency contacts
        sb.appendLine("║ EMERGENCY CONTACTS:")
        if (info.emergencyContacts.isEmpty()) {
            sb.appendLine("║   Not configured")
        } else {
            info.emergencyContacts.forEachIndexed { index, contact ->
                sb.appendLine("║   ${index + 1}. ${contact.name} (${contact.relationship})")
                sb.appendLine("║      Tel: ${contact.phone}")
            }
        }
        sb.appendLine("║")

        // Insurance
        if (info.insurancePolicyNumber.isNotEmpty()) {
            sb.appendLine("║ INSURANCE:")
            sb.appendLine("║   Policy: ${info.insurancePolicyNumber}")
            if (info.insuranceHelpline.isNotEmpty()) {
                sb.appendLine("║   Helpline: ${info.insuranceHelpline}")
            }
            sb.appendLine("║")
        }

        // Home address
        if (info.homeAddress.isNotEmpty()) {
            sb.appendLine("║ HOME ADDRESS:")
            sb.appendLine("║   ${info.homeAddress}")
            sb.appendLine("║")
        }

        sb.appendLine("╚══════════════════════════════════════╝")

        return sb.toString()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Setup Wizard
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Returns the setup wizard steps with completion status based on current data.
     *
     * @return Ordered list of [SetupStep] items for the wizard UI
     */
    fun getSetupWizardSteps(): List<SetupStep> {
        val info = getEmergencyInfo()

        return listOf(
            SetupStep(
                stepNumber = 1,
                title = "Blood Group",
                description = "Your blood group is the first thing medical staff look for. Select yours (e.g., O+, A-, B+, AB-).",
                fieldType = FieldType.SINGLE_TEXT,
                isCompleted = info?.bloodGroup?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 2,
                title = "Allergies",
                description = "List any allergies — medicines, foods, or substances. This can prevent dangerous reactions during treatment.",
                fieldType = FieldType.MULTI_TEXT_LIST,
                isCompleted = info?.allergies?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 3,
                title = "Medical Conditions",
                description = "Any ongoing conditions like diabetes, asthma, heart disease, epilepsy, etc.",
                fieldType = FieldType.MULTI_TEXT_LIST,
                isCompleted = info?.medicalConditions?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 4,
                title = "Current Medications",
                description = "Medicines you take regularly. Include dosage if you remember (e.g., 'Metformin 500mg twice daily').",
                fieldType = FieldType.MULTI_TEXT_LIST,
                isCompleted = info?.medications?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 5,
                title = "Emergency Contacts",
                description = "Up to 3 people to call in an emergency. Add your closest family member first.",
                fieldType = FieldType.CONTACT_ENTRY,
                isCompleted = info?.emergencyContacts?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 6,
                title = "Your Doctor",
                description = "Your primary doctor's name and phone. Hospitals may want to consult them.",
                fieldType = FieldType.CONTACT_ENTRY,
                isCompleted = info?.doctorName?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 7,
                title = "Insurance Details",
                description = "Your health insurance policy number and the company's helpline. Speeds up hospital admission.",
                fieldType = FieldType.SINGLE_TEXT,
                isCompleted = info?.insurancePolicyNumber?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 8,
                title = "Home Address",
                description = "Your home address — useful if you're found unresponsive and someone needs to notify family.",
                fieldType = FieldType.ADDRESS,
                isCompleted = info?.homeAddress?.isNotEmpty() == true
            ),
            SetupStep(
                stepNumber = 9,
                title = "Bank Card Helplines",
                description = "Your bank's card-block helpline numbers. If your wallet is stolen, call immediately to block cards.",
                fieldType = FieldType.CONTACT_ENTRY,
                isCompleted = getCardHelplines().isNotEmpty()
            )
        )
    }

    /**
     * Calculates the setup completion percentage.
     *
     * @return Percentage (0–100) of wizard steps completed
     */
    fun getSetupCompletionPercentage(): Int {
        val steps = getSetupWizardSteps()
        if (steps.isEmpty()) return 0
        val completed = steps.count { it.isCompleted }
        return ((completed.toFloat() / steps.size.toFloat()) * 100).toInt()
    }

    /**
     * Checks if the emergency info is minimally configured (at least blood group + one contact).
     *
     * @return true if minimum viable information is present
     */
    fun isMinimallyConfigured(): Boolean {
        val info = getEmergencyInfo() ?: return false
        return info.bloodGroup.isNotEmpty() && info.emergencyContacts.isNotEmpty()
    }

    /**
     * Re-shows the notification if it was previously enabled.
     * Call this on app startup or device reboot to restore the persistent notification.
     *
     * @param context Context for notification access
     */
    fun restoreNotificationIfEnabled(context: Context) {
        if (isLockScreenEnabled() && isMinimallyConfigured()) {
            showOnLockScreen(context)
        }
    }
}
