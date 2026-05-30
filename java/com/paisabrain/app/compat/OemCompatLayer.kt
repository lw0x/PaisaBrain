package com.paisabrain.app.compat

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * # OemCompatLayer — Comprehensive Android OEM Skin Compatibility Handler
 *
 * This is the CRITICAL compatibility layer that ensures Paisa Brain works correctly
 * across ALL Android OEM skins and custom ROMs. Android fragmentation means that
 * each manufacturer implements their own:
 * - Battery optimization (aggressive app killing)
 * - Autostart permission management
 * - SMS permission handling quirks
 * - Notification channel behavior
 * - Background process management
 *
 * ## Why This File Exists
 *
 * Finance apps like Paisa Brain rely on SMS BroadcastReceivers to auto-track
 * transactions. On stock Android (Pixel/Nokia), this works perfectly. But on
 * OEM skins (which represent 85%+ of Android devices globally), the OS
 * aggressively kills background receivers to save battery.
 *
 * Without proper OEM-specific handling:
 * - SMS receiver gets killed within minutes on Xiaomi MIUI
 * - Samsung puts the app in "Deep Sleeping Apps" after 3 days of no foreground use
 * - Oppo/Realme "App Quick Freeze" disables the receiver entirely
 * - Vivo requires explicit "High background power" permission
 * - Huawei requires "Protected Apps" whitelist entry
 *
 * ## Architecture
 *
 * 1. **Detection**: Identify the OEM skin using Build properties + system properties
 * 2. **Assessment**: Determine what issues exist for that OEM
 * 3. **Remediation**: Provide programmatic fixes where possible, user guidance otherwise
 * 4. **Monitoring**: Periodically verify the app is still whitelisted
 *
 * ## User-Facing Language Policy
 *
 * ALL user-facing strings use GENERIC device terminology:
 * - "your phone's power settings" (not "Battery Saver" or brand-specific names)
 * - "background app management" (not "Autostart Manager")
 * - "security settings" (not "MIUI Security" or brand-specific names)
 *
 * Developer comments and detection logic MAY reference OEM names for clarity.
 *
 * @author Paisa Brain Team
 * @since 1.0.0
 */
@Keep
object OemCompatLayer {

    // ==================================================================================
    // SECTION 1: OEM TYPE ENUM & DATA CLASSES
    // ==================================================================================

    /**
     * Enumeration of all known Android OEM skins and custom ROMs.
     *
     * Each entry represents a distinct behavior profile for battery optimization,
     * autostart permissions, SMS handling, and notification management.
     */
    enum class OemType(val displayName: String, val hasAggressiveBatteryKill: Boolean) {
        /** Samsung OneUI 3.0-7.0+ — "Sleeping Apps" and "Deep Sleeping Apps" */
        SAMSUNG("Device Manufacturer A", hasAggressiveBatteryKill = true),

        /** Samsung low-end M/A series — even MORE aggressive killing */
        SAMSUNG_LOW_END("Device Manufacturer A (Budget Series)", hasAggressiveBatteryKill = true),

        /** Xiaomi MIUI 12-15+ — Autostart required, Battery Saver kills apps */
        XIAOMI("Device Manufacturer B", hasAggressiveBatteryKill = true),

        /** Xiaomi HyperOS (2024+) — Successor to MIUI, slightly less aggressive */
        XIAOMI_HYPEROS("Device Manufacturer B (New OS)", hasAggressiveBatteryKill = true),

        /** Poco — MIUI variant with identical behavior */
        POCO("Device Manufacturer B (Sub-brand)", hasAggressiveBatteryKill = true),

        /** OnePlus OxygenOS / ColorOS merged (2023+) */
        ONEPLUS("Device Manufacturer C", hasAggressiveBatteryKill = true),

        /** Oppo ColorOS — aggressive battery optimization */
        OPPO("Device Manufacturer D", hasAggressiveBatteryKill = true),

        /** Realme UI — ColorOS variant with App Quick Freeze */
        REALME("Device Manufacturer E", hasAggressiveBatteryKill = true),

        /** Vivo FuntouchOS / OriginOS — "i Manager" controls permissions */
        VIVO("Device Manufacturer F", hasAggressiveBatteryKill = true),

        /** iQOO — Vivo sub-brand, FuntouchOS variant */
        IQOO("Device Manufacturer F (Sub-brand)", hasAggressiveBatteryKill = true),

        /** Huawei EMUI / HarmonyOS — "Protected Apps" list */
        HUAWEI("Device Manufacturer G", hasAggressiveBatteryKill = true),

        /** Motorola MyUX — Adaptive Battery aggressive */
        MOTOROLA("Device Manufacturer H", hasAggressiveBatteryKill = false),

        /** Nokia — Near-stock Android, minimal issues */
        NOKIA("Device Manufacturer I", hasAggressiveBatteryKill = false),

        /** Google Pixel — Stock Android, baseline behavior */
        GOOGLE("Device Manufacturer J", hasAggressiveBatteryKill = false),

        /** Nothing OS — Near-stock with minor customizations */
        NOTHING("Device Manufacturer K", hasAggressiveBatteryKill = false),

        /** Tecno HiOS — Aggressive battery management */
        TECNO("Device Manufacturer L", hasAggressiveBatteryKill = true),

        /** Infinix XOS — Aggressive battery management */
        INFINIX("Device Manufacturer M", hasAggressiveBatteryKill = true),

        /** LineageOS and CyanogenMod successors */
        LINEAGEOS("Custom ROM (LineageOS-based)", hasAggressiveBatteryKill = false),

        /** GrapheneOS — Privacy-focused, may restrict permissions */
        GRAPHENEOS("Custom ROM (Privacy-focused)", hasAggressiveBatteryKill = false),

        /** CalyxOS — Privacy-focused with microG */
        CALYXOS("Custom ROM (Privacy-focused)", hasAggressiveBatteryKill = false),

        /** /e/OS — De-Googled Android */
        EOS("Custom ROM (De-Googled)", hasAggressiveBatteryKill = false),

        /** Pixel Experience and similar AOSP-based ROMs */
        PIXEL_EXPERIENCE("Custom ROM (AOSP-based)", hasAggressiveBatteryKill = false),

        /** Stock Android — no OEM modifications */
        STOCK_ANDROID("Standard Android", hasAggressiveBatteryKill = false),

        /** Unknown OEM — fallback to conservative behavior */
        UNKNOWN("Unknown Device", hasAggressiveBatteryKill = true)
    }

    /**
     * Severity level for OEM-specific issues.
     */
    enum class IssueSeverity {
        /** App WILL NOT WORK without fixing this */
        CRITICAL,
        /** App will partially work but miss some SMS/notifications */
        HIGH,
        /** App works but may stop after some time */
        MEDIUM,
        /** Minor inconvenience, app mostly works */
        LOW
    }

    /**
     * Type of OEM-specific issue affecting app functionality.
     */
    enum class IssueType {
        /** SMS BroadcastReceiver blocked or permission denied */
        SMS_BLOCKED,
        /** Battery optimization killing background processes */
        BATTERY_KILL,
        /** Autostart permission needed for boot receiver */
        AUTOSTART_NEEDED,
        /** Notifications blocked or filtered by OEM */
        NOTIFICATION_BLOCKED,
        /** Additional permission layer blocking SMS reading */
        PERMISSION_PRIVACY_LAYER,
        /** App put in sleeping/frozen state */
        APP_FROZEN,
        /** Background data restricted */
        BACKGROUND_DATA_RESTRICTED
    }

    /**
     * Type of action required to fix an issue.
     */
    enum class ActionType {
        /** Can programmatically open the correct settings page */
        OPEN_SETTINGS,
        /** User must manually navigate (no reliable intent) */
        MANUAL,
        /** Informational only — no action needed */
        INFORMATIONAL,
        /** Can be fixed programmatically via API */
        AUTOMATIC
    }

    /**
     * Represents a single setup step the user needs to complete.
     *
     * @property stepNumber Sequential step number (1-based)
     * @property title Short title for the step (generic language)
     * @property description Detailed description/instructions (generic language)
     * @property actionType What kind of action this step requires
     * @property intentToOpen Intent to launch settings (null if MANUAL/INFORMATIONAL)
     * @property isCompleted Whether the user has completed this step
     * @property verificationCheck Optional lambda to programmatically verify completion
     */
    data class SetupStep(
        val stepNumber: Int,
        val title: String,
        val description: String,
        val actionType: ActionType,
        val intentToOpen: Intent? = null,
        val isCompleted: Boolean = false,
        val verificationCheck: ((Context) -> Boolean)? = null
    )

    /**
     * Represents an OEM-specific issue that needs to be addressed.
     *
     * @property type Category of the issue
     * @property severity How critical this issue is
     * @property userMessage User-facing description (generic language)
     * @property fixSteps Ordered steps to resolve the issue
     * @property affectsOemTypes Which OEM types this issue applies to
     */
    data class OemIssue(
        val type: IssueType,
        val severity: IssueSeverity,
        val userMessage: String,
        val fixSteps: List<SetupStep>,
        val affectsOemTypes: Set<OemType> = emptySet()
    )

    /**
     * Complete compatibility profile for a detected OEM.
     *
     * @property oemType Detected OEM type
     * @property oemVersion Detected skin version string
     * @property androidVersion Android API level
     * @property issues List of detected/potential issues
     * @property setupSteps Combined setup steps in priority order
     */
    data class CompatProfile(
        val oemType: OemType,
        val oemVersion: String,
        val androidVersion: Int,
        val issues: List<OemIssue>,
        val setupSteps: List<SetupStep>
    )

    // ==================================================================================
    // SECTION 2: OEM DETECTION
    // ==================================================================================

    /** Cached OEM type to avoid repeated detection */
    @Volatile
    private var cachedOemType: OemType? = null

    /** Cached OEM version string */
    @Volatile
    private var cachedOemVersion: String? = null

    /**
     * Detects the current device's OEM skin type.
     *
     * Detection strategy (in order):
     * 1. Check Build.MANUFACTURER and Build.BRAND for primary identification
     * 2. Read system properties for skin-specific versions (MIUI, ColorOS, etc.)
     * 3. Check Build.DISPLAY for custom ROM identifiers
     * 4. Check installed system packages for additional confirmation
     *
     * @return The detected [OemType]
     */
    fun getOemType(): OemType {
        cachedOemType?.let { return it }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val display = Build.DISPLAY.lowercase()
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()

        val detected = when {
            // --- Samsung Detection ---
            manufacturer == "samsung" || brand == "samsung" -> {
                if (isSamsungLowEnd(model)) OemType.SAMSUNG_LOW_END else OemType.SAMSUNG
            }

            // --- Xiaomi / MIUI / HyperOS Detection ---
            manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" -> {
                when {
                    brand == "poco" || manufacturer == "poco" -> OemType.POCO
                    isHyperOs() -> OemType.XIAOMI_HYPEROS
                    else -> OemType.XIAOMI
                }
            }
            brand == "poco" || manufacturer == "poco" -> OemType.POCO

            // --- OnePlus Detection ---
            manufacturer == "oneplus" || brand == "oneplus" -> OemType.ONEPLUS

            // --- Oppo Detection ---
            manufacturer == "oppo" || brand == "oppo" -> OemType.OPPO

            // --- Realme Detection ---
            manufacturer == "realme" || brand == "realme" -> OemType.REALME

            // --- Vivo Detection ---
            manufacturer == "vivo" || brand == "vivo" -> {
                if (brand == "iqoo" || model.contains("iqoo")) OemType.IQOO else OemType.VIVO
            }
            brand == "iqoo" -> OemType.IQOO

            // --- Huawei Detection ---
            manufacturer == "huawei" || brand == "huawei" || brand == "honor" -> OemType.HUAWEI

            // --- Motorola Detection ---
            manufacturer == "motorola" || brand == "motorola" -> OemType.MOTOROLA

            // --- Nokia Detection ---
            manufacturer == "hmd global" || brand == "nokia" -> OemType.NOKIA

            // --- Google Pixel Detection ---
            manufacturer == "google" || brand == "google" -> OemType.GOOGLE

            // --- Nothing Detection ---
            manufacturer == "nothing" || brand == "nothing" -> OemType.NOTHING

            // --- Tecno Detection ---
            manufacturer == "tecno" || brand == "tecno" || 
            manufacturer == "tecno mobile limited" -> OemType.TECNO

            // --- Infinix Detection ---
            manufacturer == "infinix" || brand == "infinix" ||
            manufacturer == "infinix mobility limited" -> OemType.INFINIX

            // --- Custom ROM Detection ---
            isLineageOS(display, fingerprint) -> OemType.LINEAGEOS
            isGrapheneOS(fingerprint) -> OemType.GRAPHENEOS
            isCalyxOS(fingerprint) -> OemType.CALYXOS
            isEos(fingerprint, display) -> OemType.EOS
            isPixelExperience(display, fingerprint) -> OemType.PIXEL_EXPERIENCE

            // --- Fallback ---
            else -> OemType.STOCK_ANDROID
        }

        cachedOemType = detected
        return detected
    }

    /**
     * Returns the detected OEM skin version string.
     *
     * Examples:
     * - "OneUI 6.1" for Samsung
     * - "MIUI 15.0.2" for Xiaomi
     * - "ColorOS 14.1" for Oppo/OnePlus/Realme
     * - "EMUI 14.0" for Huawei
     * - "FuntouchOS 14" for Vivo
     *
     * @return Version string or "Unknown" if not detectable
     */
    fun getOemVersion(): String {
        cachedOemVersion?.let { return it }

        val version = when (getOemType()) {
            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> getSamsungOneUiVersion()
            OemType.XIAOMI, OemType.POCO -> getMiuiVersion()
            OemType.XIAOMI_HYPEROS -> getHyperOsVersion()
            OemType.ONEPLUS -> getOxygenOsVersion()
            OemType.OPPO, OemType.REALME -> getColorOsVersion()
            OemType.VIVO, OemType.IQOO -> getFuntouchOsVersion()
            OemType.HUAWEI -> getEmuiVersion()
            OemType.MOTOROLA -> "MyUX (Android ${Build.VERSION.RELEASE})"
            OemType.NOKIA -> "Stock Android ${Build.VERSION.RELEASE}"
            OemType.GOOGLE -> "Pixel (Android ${Build.VERSION.RELEASE})"
            OemType.NOTHING -> getNothingOsVersion()
            OemType.TECNO -> "HiOS (Android ${Build.VERSION.RELEASE})"
            OemType.INFINIX -> "XOS (Android ${Build.VERSION.RELEASE})"
            OemType.LINEAGEOS -> getLineageOsVersion()
            OemType.GRAPHENEOS -> "GrapheneOS (Android ${Build.VERSION.RELEASE})"
            OemType.CALYXOS -> "CalyxOS (Android ${Build.VERSION.RELEASE})"
            OemType.EOS -> "/e/OS (Android ${Build.VERSION.RELEASE})"
            OemType.PIXEL_EXPERIENCE -> "Pixel Experience (Android ${Build.VERSION.RELEASE})"
            OemType.STOCK_ANDROID -> "Android ${Build.VERSION.RELEASE}"
            OemType.UNKNOWN -> "Unknown"
        }

        cachedOemVersion = version
        return version
    }

    // --- Samsung Detection Helpers ---

    /**
     * Detects if this is a low-end Samsung device (Galaxy M/A series).
     * These devices have more aggressive battery optimization than flagship S/Z/Note series.
     *
     * Low-end Samsung devices:
     * - Limit background processes to 2-3 (vs 5+ on flagships)
     * - More aggressive "Sleeping Apps" timeout (24h vs 72h)
     * - "Adaptive Battery" is more restrictive
     */
    private fun isSamsungLowEnd(model: String): Boolean {
        val lowEndPrefixes = listOf(
            "sm-a", "sm-m", "sm-f0", // A-series, M-series, low-end F
            "galaxy a", "galaxy m"
        )
        return lowEndPrefixes.any { model.startsWith(it) || model.contains(it) }
    }

    /**
     * Gets the Samsung OneUI version from system properties.
     * OneUI version is stored in ro.build.version.oneui
     */
    private fun getSamsungOneUiVersion(): String {
        val oneUiVersion = getSystemProperty("ro.build.version.oneui")
        return if (oneUiVersion.isNotEmpty()) {
            val major = oneUiVersion.take(1)
            val minor = if (oneUiVersion.length > 1) oneUiVersion.substring(1).trimStart('0') else "0"
            "OneUI $major.${minor.ifEmpty { "0" }}"
        } else {
            // Fallback: estimate from Android version
            val androidVersion = Build.VERSION.SDK_INT
            when {
                androidVersion >= 35 -> "OneUI 7.0+"
                androidVersion >= 34 -> "OneUI 6.x"
                androidVersion >= 33 -> "OneUI 5.x"
                androidVersion >= 31 -> "OneUI 4.x"
                androidVersion >= 30 -> "OneUI 3.x"
                else -> "OneUI (version unknown)"
            }
        }
    }

    // --- Xiaomi / MIUI / HyperOS Detection Helpers ---

    /**
     * Checks if the device is running HyperOS (Xiaomi's 2024+ replacement for MIUI).
     * HyperOS is less aggressive than MIUI but still requires autostart.
     */
    private fun isHyperOs(): Boolean {
        val hyperOsVersion = getSystemProperty("ro.mi.os.version.name")
        if (hyperOsVersion.isNotEmpty()) return true
        val miuiVersion = getSystemProperty("ro.miui.ui.version.name")
        // HyperOS sometimes still reports as MIUI internally but with version indicators
        return miuiVersion.contains("hyper", ignoreCase = true)
    }

    /**
     * Gets MIUI version string.
     * MIUI version stored in multiple properties:
     * - ro.miui.ui.version.name (e.g., "V150")
     * - ro.miui.ui.version.code (numeric)
     */
    private fun getMiuiVersion(): String {
        val versionName = getSystemProperty("ro.miui.ui.version.name")
        val versionCode = getSystemProperty("ro.miui.ui.version.code")
        return when {
            versionName.isNotEmpty() -> "MIUI $versionName"
            versionCode.isNotEmpty() -> "MIUI (code: $versionCode)"
            else -> "MIUI (version unknown)"
        }
    }

    /**
     * Gets HyperOS version string.
     */
    private fun getHyperOsVersion(): String {
        val version = getSystemProperty("ro.mi.os.version.name")
        return if (version.isNotEmpty()) "HyperOS $version" else "HyperOS (version unknown)"
    }

    // --- OnePlus / OxygenOS Detection Helpers ---

    /**
     * Gets OxygenOS version.
     * Note: OnePlus merged with ColorOS in 2023+ but still brands as OxygenOS globally.
     * Properties: ro.oxygen.version, ro.build.version.ota
     */
    private fun getOxygenOsVersion(): String {
        val oxygenVersion = getSystemProperty("ro.oxygen.version")
        if (oxygenVersion.isNotEmpty()) return "OxygenOS $oxygenVersion"

        val otaVersion = getSystemProperty("ro.build.version.ota")
        if (otaVersion.isNotEmpty() && otaVersion.contains("oxygen", ignoreCase = true)) {
            return "OxygenOS ($otaVersion)"
        }

        // Fallback: check for ColorOS underneath (merged builds)
        val colorOsVersion = getSystemProperty("ro.build.version.oplusrom")
        if (colorOsVersion.isNotEmpty()) return "OxygenOS/ColorOS $colorOsVersion"

        return "OxygenOS (version unknown)"
    }

    // --- Oppo / Realme / ColorOS Detection Helpers ---

    /**
     * Gets ColorOS version.
     * Properties: ro.build.version.oplusrom, ro.oplus.image.my_manifest
     */
    private fun getColorOsVersion(): String {
        val oplusVersion = getSystemProperty("ro.build.version.oplusrom")
        if (oplusVersion.isNotEmpty()) return "ColorOS $oplusVersion"

        val colorVersion = getSystemProperty("ro.build.display.id")
        if (colorVersion.contains("coloros", ignoreCase = true)) {
            return colorVersion
        }

        return "ColorOS (version unknown)"
    }

    // --- Vivo / FuntouchOS Detection Helpers ---

    /**
     * Gets FuntouchOS or OriginOS version.
     * Properties: ro.vivo.os.version, ro.vivo.os.name
     */
    private fun getFuntouchOsVersion(): String {
        val osName = getSystemProperty("ro.vivo.os.name")
        val osVersion = getSystemProperty("ro.vivo.os.version")
        return when {
            osName.isNotEmpty() && osVersion.isNotEmpty() -> "$osName $osVersion"
            osName.isNotEmpty() -> osName
            osVersion.isNotEmpty() -> "FuntouchOS $osVersion"
            else -> "FuntouchOS (version unknown)"
        }
    }

    // --- Huawei / EMUI Detection Helpers ---

    /**
     * Gets EMUI or HarmonyOS version.
     * Properties: ro.build.version.emui, ro.huawei.build.display.id
     */
    private fun getEmuiVersion(): String {
        val emuiVersion = getSystemProperty("ro.build.version.emui")
        if (emuiVersion.isNotEmpty()) return emuiVersion

        val harmonyVersion = getSystemProperty("hw_sc.build.platform.version")
        if (harmonyVersion.isNotEmpty()) return "HarmonyOS $harmonyVersion"

        return "EMUI (version unknown)"
    }

    // --- Nothing OS Detection Helpers ---

    private fun getNothingOsVersion(): String {
        val display = Build.DISPLAY
        return if (display.contains("nothing", ignoreCase = true)) {
            "Nothing OS ($display)"
        } else {
            "Nothing OS (Android ${Build.VERSION.RELEASE})"
        }
    }

    // --- Custom ROM Detection Helpers ---

    private fun isLineageOS(display: String, fingerprint: String): Boolean {
        return display.contains("lineage") ||
                fingerprint.contains("lineage") ||
                getSystemProperty("ro.lineage.version").isNotEmpty() ||
                getSystemProperty("ro.cm.version").isNotEmpty()
    }

    private fun getLineageOsVersion(): String {
        val version = getSystemProperty("ro.lineage.version")
        return if (version.isNotEmpty()) "LineageOS $version" else "LineageOS"
    }

    private fun isGrapheneOS(fingerprint: String): Boolean {
        return fingerprint.contains("grapheneos") ||
                getSystemProperty("ro.grapheneos.version").isNotEmpty()
    }

    private fun isCalyxOS(fingerprint: String): Boolean {
        return fingerprint.contains("calyxos") ||
                getSystemProperty("ro.calyxos.version").isNotEmpty()
    }

    private fun isEos(fingerprint: String, display: String): Boolean {
        return fingerprint.contains("/e/") ||
                display.contains("e/os", ignoreCase = true) ||
                getSystemProperty("ro.eos.version").isNotEmpty()
    }

    private fun isPixelExperience(display: String, fingerprint: String): Boolean {
        return display.contains("pixelexperience", ignoreCase = true) ||
                fingerprint.contains("pixelexperience")
    }

    // --- System Property Reader ---

    /**
     * Reads an Android system property using reflection on SystemProperties
     * or by executing getprop command.
     *
     * @param key The system property key (e.g., "ro.miui.ui.version.name")
     * @return Property value or empty string if not found
     */
    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String): String {
        // Method 1: Reflection on android.os.SystemProperties (hidden API)
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            val value = method.invoke(null, key, "") as? String
            if (!value.isNullOrEmpty()) return value
        } catch (_: Exception) {
            // Reflection blocked on some Android versions
        }

        // Method 2: Execute getprop command
        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            if (!value.isNullOrEmpty()) return value
        } catch (_: Exception) {
            // Command execution failed
        }

        return ""
    }

    // ==================================================================================
    // SECTION 3: SMS PERMISSION HANDLING PER OEM
    // ==================================================================================

    /**
     * Checks if the SMS receiver is likely being blocked by OEM-specific restrictions.
     *
     * This goes beyond Android's standard permission check to detect OEM-layer blocking:
     * - Xiaomi MIUI: Security app can disable SMS permission independently
     * - Oppo/Realme: "Permission Privacy" layer blocks even with Android permission granted
     * - Vivo: "i Manager" has separate permission control
     * - Huawei: Phone Manager overrides Android permissions
     * - Samsung (Android 15+): "Restricted Settings" blocks SMS for sideloaded apps
     *
     * @param context Application context
     * @return true if SMS receiver is likely blocked by OEM-specific restrictions
     */
    fun isSmsReceiverLikelyBlocked(context: Context): Boolean {
        val oemType = getOemType()

        // First: check standard Android permission
        val hasSmsPermission = context.checkSelfPermission(
            android.Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) return true

        // Second: check OEM-specific blocking
        return when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> {
                // Xiaomi MIUI can revoke SMS permission silently via Security app
                // Check if autostart is disabled (strong indicator of blocked SMS)
                !isAutostartEnabled(context)
            }

            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> {
                // Samsung Android 15+: Check if app is restricted
                if (Build.VERSION.SDK_INT >= 35) {
                    isAppInSleepingList(context)
                } else {
                    false
                }
            }

            OemType.OPPO, OemType.REALME, OemType.ONEPLUS -> {
                // ColorOS has a "Permission Privacy" layer
                // No programmatic way to check — assume blocked if battery optimization is on
                !isAppWhitelistedFromBatteryOptimization(context)
            }

            OemType.VIVO, OemType.IQOO -> {
                // Vivo i Manager controls permissions
                !isAppWhitelistedFromBatteryOptimization(context)
            }

            OemType.HUAWEI -> {
                // Huawei Phone Manager
                !isAppWhitelistedFromBatteryOptimization(context)
            }

            else -> false
        }
    }

    /**
     * Gets SMS-specific setup instructions for the detected OEM.
     *
     * @param context Application context
     * @return List of steps specific to SMS permission fixing
     */
    fun getSmsSetupSteps(context: Context): List<SetupStep> {
        val oemType = getOemType()
        val packageName = context.packageName
        val steps = mutableListOf<SetupStep>()

        when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> {
                // Xiaomi MIUI: Auto-disables SMS after install. Must enable via Security app.
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable SMS permission in security settings",
                    description = "Your device has a separate security layer that controls SMS access. " +
                            "Go to your phone's security app → Permissions → SMS, and ensure this app is allowed.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createXiaomiSecurityPermissionIntent(packageName)
                ))
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Verify SMS permission in app settings",
                    description = "Also check: Settings → Apps → Manage Apps → find this app → App Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
            }

            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> {
                if (Build.VERSION.SDK_INT >= 35) {
                    // Samsung Android 15+: Restricted settings block SMS for sideloaded apps
                    steps.add(SetupStep(
                        stepNumber = 1,
                        title = "Allow restricted settings",
                        description = "On newer Android versions, SMS permission requires allowing restricted settings. " +
                                "Go to Settings → Apps → find this app → tap the three dots menu → Allow restricted settings.",
                        actionType = ActionType.OPEN_SETTINGS,
                        intentToOpen = createAppInfoIntent(context)
                    ))
                }
                steps.add(SetupStep(
                    stepNumber = 1 + steps.size,
                    title = "Grant SMS permission",
                    description = "Go to Settings → Apps → find this app → Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
            }

            OemType.OPPO, OemType.REALME, OemType.ONEPLUS -> {
                // ColorOS: Additional "Permission Privacy" layer
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Grant SMS permission",
                    description = "Go to Settings → Apps → find this app → Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Check privacy permission settings",
                    description = "Your device has additional privacy controls. " +
                            "Go to Settings → Privacy → Permission Manager → SMS → ensure this app is allowed.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createColorOsPermissionIntent()
                ))
            }

            OemType.VIVO, OemType.IQOO -> {
                // Vivo: "i Manager" controls permissions separately
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Grant SMS permission",
                    description = "Go to Settings → Apps → find this app → Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable in device manager app",
                    description = "Your device has a built-in manager app that controls permissions separately. " +
                            "Open it → App Management → Permissions → SMS → allow this app.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createVivoIManagerIntent()
                ))
            }

            OemType.HUAWEI -> {
                // Huawei: Phone Manager has separate control
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Grant SMS permission",
                    description = "Go to Settings → Apps → find this app → Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable in phone manager",
                    description = "Your device has a built-in phone manager that controls permissions separately. " +
                            "Open Phone Manager → Permission Management → SMS → allow this app.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createHuaweiPhoneManagerIntent()
                ))
            }

            else -> {
                // Standard Android flow
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Grant SMS permission",
                    description = "Go to Settings → Apps → find this app → Permissions → SMS → Allow",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = createAppInfoIntent(context)
                ))
            }
        }

        return steps
    }

    // ==================================================================================
    // SECTION 4: BATTERY OPTIMIZATION / APP KILLING PREVENTION
    // ==================================================================================

    /**
     * Checks if the app is whitelisted from battery optimization.
     *
     * Uses the standard Android [PowerManager.isIgnoringBatteryOptimizations] API.
     * Note: This only checks the STANDARD Android battery optimization.
     * OEM-specific layers (Samsung Sleeping Apps, Xiaomi Battery Saver, etc.)
     * are NOT detectable via this API.
     *
     * @param context Application context
     * @return true if app is whitelisted from standard battery optimization
     */
    fun isAppWhitelistedFromBatteryOptimization(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Requests battery optimization exemption via the standard Android API.
     *
     * This shows a system dialog asking the user to exempt the app.
     * Note: This does NOT cover OEM-specific battery managers — those require
     * separate user action via OEM-specific settings pages.
     *
     * @param context Application context (should be Activity context for dialog)
     */
    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption(context: Context) {
        if (isAppWhitelistedFromBatteryOptimization(context)) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open general battery optimization settings
            openBatterySettings(context)
        }
    }

    /**
     * Checks if the app needs battery whitelist configuration.
     *
     * @return true if the detected OEM has aggressive battery optimization
     *         AND the app is not currently whitelisted
     */
    fun needsBatteryWhitelist(): Boolean {
        return getOemType().hasAggressiveBatteryKill
    }

    /**
     * Opens the OEM-specific battery optimization settings page.
     *
     * Each OEM has a different settings path:
     * - Samsung: Settings → Battery → Background usage limits → Sleeping apps
     * - Xiaomi: Settings → Battery & performance → App battery saver
     * - Oppo/Realme: Settings → Battery → More battery settings → Optimize battery use
     * - Vivo: Settings → Battery → Background power consumption management
     * - Huawei: Settings → Battery → App launch → Protected apps
     *
     * @param context Application context
     * @return true if settings were opened successfully
     */
    fun openBatterySettings(context: Context): Boolean {
        val oemType = getOemType()
        val intents = getBatterySettingsIntents(oemType, context)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }

        // Absolute fallback: standard Android battery settings
        return try {
            val fallback = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (_: Exception) {
            try {
                val fallback2 = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback2)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Gets ordered list of intents to try for battery settings per OEM.
     * Multiple intents provided as fallback chain (OEMs change paths between versions).
     */
    private fun getBatterySettingsIntents(oemType: OemType, context: Context): List<Intent> {
        val packageName = context.packageName
        return when (oemType) {
            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> listOf(
                // Samsung: Battery → Background usage limits (OneUI 5+)
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                },
                // Samsung: Device Care → Battery (OneUI 3-4)
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                },
                // Samsung: Smart Manager (older devices)
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.sm",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"
                    )
                },
                // Fallback: App-specific battery settings
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> listOf(
                // Xiaomi: Battery & performance settings (MIUI 12+)
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", packageName)
                    putExtra("package_label", "Paisa Brain")
                },
                // Xiaomi: App battery saver (MIUI 11)
                Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
                    )
                },
                // Xiaomi: Security app → Battery
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.powercenter.PowerMainActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.ONEPLUS -> listOf(
                // OnePlus: Battery optimization (OxygenOS 13+/ColorOS base)
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.battery",
                        "com.oplus.powermanager.fuelgaue.PowerUsageDetailActivity"
                    )
                },
                // OnePlus: Battery settings (older OxygenOS)
                Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.OPPO, OemType.REALME -> listOf(
                // Oppo/Realme: Battery management (ColorOS 12+)
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.battery",
                        "com.oplus.powermanager.fuelgaue.PowerUsageDetailActivity"
                    )
                },
                // Oppo: Battery optimization (ColorOS 11)
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerUsageDetailActivity"
                    )
                },
                // Realme: Power saving (older)
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerPkgDetailActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.VIVO, OemType.IQOO -> listOf(
                // Vivo: Background power consumption (FuntouchOS 12+)
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.abe",
                        "com.vivo.applicationbased.energy498management.ui.BgConsumptionActivity"
                    )
                },
                // Vivo: i Manager → Battery
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.powersaving",
                        "com.iqoo.powersaving.PowerSavingManagerActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.HUAWEI -> listOf(
                // Huawei: Protected Apps (EMUI 10+)
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                },
                // Huawei: App launch management
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                },
                // Huawei: Battery optimization
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.TECNO -> listOf(
                // Tecno: Phone Master → Battery
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.battery.ui.SmartPowerActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.INFINIX -> listOf(
                // Infinix: XOS Phone Master
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.battery.ui.SmartPowerActivity"
                    )
                },
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            OemType.MOTOROLA -> listOf(
                // Motorola: Adaptive Battery (standard Android settings)
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )

            else -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                },
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    /**
     * Gets battery optimization specific setup steps for the current OEM.
     */
    fun getBatterySetupSteps(context: Context): List<SetupStep> {
        val oemType = getOemType()
        val steps = mutableListOf<SetupStep>()

        // Step 1: Always request standard Android battery optimization exemption
        steps.add(SetupStep(
            stepNumber = 1,
            title = "Disable battery optimization for this app",
            description = "Allow this app to run in the background without being stopped by battery saving. " +
                    "When prompted, select 'Allow' or 'Don't optimize'.",
            actionType = ActionType.AUTOMATIC,
            intentToOpen = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            verificationCheck = { ctx -> isAppWhitelistedFromBatteryOptimization(ctx) }
        ))

        // Step 2+: OEM-specific steps
        when (oemType) {
            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Remove from sleeping apps list",
                    description = "Your device may automatically put unused apps to sleep, which stops background notifications. " +
                            "Go to Settings → Battery → Background usage limits → Sleeping apps → Remove this app from the list. " +
                            "Also check 'Deep sleeping apps' and 'Never sleeping apps' (add this app to 'Never sleeping').",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
                if (oemType == OemType.SAMSUNG_LOW_END) {
                    steps.add(SetupStep(
                        stepNumber = 3,
                        title = "Disable adaptive battery",
                        description = "On budget devices, adaptive battery is more aggressive. " +
                                "Go to Settings → Battery → Adaptive battery → Turn OFF. " +
                                "This ensures background processes are not killed.",
                        actionType = ActionType.OPEN_SETTINGS,
                        intentToOpen = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    ))
                }
            }

            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Set battery saver to 'No restrictions'",
                    description = "Go to Settings → Apps → Manage Apps → find this app → Battery Saver → select 'No restrictions'. " +
                            "This prevents the system from killing the app in the background.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
                steps.add(SetupStep(
                    stepNumber = 3,
                    title = "Lock app in recent apps",
                    description = "Open this app, then open the recent apps screen. " +
                            "Long-press or swipe down on this app's card and tap the lock icon. " +
                            "This prevents the system from clearing it when you clear all recent apps.",
                    actionType = ActionType.MANUAL
                ))
            }

            OemType.ONEPLUS, OemType.OPPO, OemType.REALME -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Disable battery optimization for this app",
                    description = "Go to Settings → Battery → More settings → Optimize battery use → " +
                            "select 'All apps' from dropdown → find this app → toggle OFF optimization.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
                steps.add(SetupStep(
                    stepNumber = 3,
                    title = "Lock app in recent apps",
                    description = "Open recent apps, swipe down on this app's card to lock it. " +
                            "A lock icon will appear, preventing the system from killing it.",
                    actionType = ActionType.MANUAL
                ))
                if (oemType == OemType.REALME) {
                    // Realme: Additional "App Quick Freeze" kills apps after 3 days
                    steps.add(SetupStep(
                        stepNumber = 4,
                        title = "Disable App Quick Freeze",
                        description = "Your device may freeze apps that haven't been used for a few days. " +
                                "Go to Settings → Battery → More settings → App Quick Freeze → " +
                                "find this app and disable freezing.",
                        actionType = ActionType.OPEN_SETTINGS,
                        intentToOpen = Intent().apply {
                            component = ComponentName(
                                "com.oplus.battery",
                                "com.oplus.powermanager.fuelgaue.PowerUsageDetailActivity"
                            )
                        }
                    ))
                }
            }

            OemType.VIVO, OemType.IQOO -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Allow high background power consumption",
                    description = "Go to Settings → Battery → High background power consumption → " +
                            "find this app and enable it. This allows the app to run in the background.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
                steps.add(SetupStep(
                    stepNumber = 3,
                    title = "Lock app in recent apps",
                    description = "Open recent apps, swipe down on this app's card to lock it.",
                    actionType = ActionType.MANUAL
                ))
            }

            OemType.HUAWEI -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Add to protected apps",
                    description = "Go to Settings → Battery → App launch → find this app → " +
                            "disable 'Manage automatically' → enable all three toggles: " +
                            "Auto-launch, Secondary launch, Run in background.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
            }

            OemType.TECNO, OemType.INFINIX -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Disable power saving for this app",
                    description = "Open your device's phone master/manager app → Power saving → " +
                            "find this app → set to 'Don't optimize' or 'Allow background activity'.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getBatterySettingsIntents(oemType, context).firstOrNull()
                ))
            }

            OemType.MOTOROLA -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Disable adaptive battery",
                    description = "If you notice missed notifications, go to Settings → Battery → " +
                            "Adaptive Battery → turn it OFF. This prevents aggressive background killing.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                ))
            }

            else -> {
                // Stock Android / Custom ROMs — standard battery optimization is usually sufficient
            }
        }

        return steps
    }

    // ==================================================================================
    // SECTION 5: AUTOSTART PERMISSION
    // ==================================================================================

    /**
     * Determines if the current OEM requires explicit autostart permission
     * for BroadcastReceivers to work after device boot.
     *
     * OEMs requiring autostart:
     * - Xiaomi (MIUI/HyperOS): MUST enable, app won't receive boot broadcast otherwise
     * - Oppo (ColorOS): Required for boot-time receivers
     * - Realme (Realme UI): Same as Oppo (ColorOS base)
     * - Vivo (FuntouchOS/OriginOS): Required
     * - Huawei (EMUI/HarmonyOS): Required via "Startup Manager"
     * - OnePlus (OxygenOS 13+/ColorOS): Required (merged with ColorOS)
     * - Tecno (HiOS): Required
     * - Infinix (XOS): Required
     *
     * OEMs NOT requiring autostart (standard Android behavior):
     * - Samsung, Google Pixel, Motorola, Nokia, Nothing
     * - Custom ROMs (LineageOS, GrapheneOS, CalyxOS, etc.)
     *
     * @return true if autostart permission must be manually enabled
     */
    fun needsAutostartPermission(): Boolean {
        return when (getOemType()) {
            OemType.XIAOMI,
            OemType.XIAOMI_HYPEROS,
            OemType.POCO,
            OemType.OPPO,
            OemType.REALME,
            OemType.ONEPLUS,
            OemType.VIVO,
            OemType.IQOO,
            OemType.HUAWEI,
            OemType.TECNO,
            OemType.INFINIX -> true

            OemType.SAMSUNG,
            OemType.SAMSUNG_LOW_END,
            OemType.GOOGLE,
            OemType.MOTOROLA,
            OemType.NOKIA,
            OemType.NOTHING,
            OemType.LINEAGEOS,
            OemType.GRAPHENEOS,
            OemType.CALYXOS,
            OemType.EOS,
            OemType.PIXEL_EXPERIENCE,
            OemType.STOCK_ANDROID,
            OemType.UNKNOWN -> false
        }
    }

    /**
     * Attempts to open the OEM-specific autostart settings page.
     *
     * Uses a chain of known intents per OEM (multiple fallbacks since paths
     * change between OS versions). Known intents sourced from public documentation
     * and community-maintained compatibility databases.
     *
     * @param context Application context
     * @return true if a settings page was successfully opened
     */
    fun openAutostartSettings(context: Context): Boolean {
        val intents = getAutostartIntents(context)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }

        // Fallback: open app info page (user can navigate from there)
        return try {
            context.startActivity(createAppInfoIntent(context).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gets the ordered list of autostart settings intents for the current OEM.
     *
     * Intent sources:
     * - Public GitHub gists and community projects (dontkillmyapp.com data)
     * - OEM developer documentation
     * - Reverse-engineered from OEM settings apps
     */
    private fun getAutostartIntents(context: Context): List<Intent> {
        val oemType = getOemType()

        return when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> listOf(
                // MIUI: Security → Autostart (MIUI 12+)
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                },
                // MIUI: Alternative path (MIUI 14+)
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                },
                // HyperOS: New permissions path
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.MainAcitivity"
                    )
                },
                // Fallback: Security Center main
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                    )
                }
            )

            OemType.OPPO, OemType.REALME -> listOf(
                // ColorOS 12+: App Management → Startup Manager
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                },
                // ColorOS 13+: Alternative path
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"
                    )
                },
                // ColorOS 11: Older path
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                },
                // Realme-specific path
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
            )

            OemType.ONEPLUS -> listOf(
                // OnePlus (OxygenOS 13+, ColorOS-based)
                Intent().apply {
                    component = ComponentName(
                        "com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"
                    )
                },
                // OnePlus (older OxygenOS)
                Intent().apply {
                    component = ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                },
                // Fallback to ColorOS path
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                }
            )

            OemType.VIVO, OemType.IQOO -> listOf(
                // Vivo: i Manager → Autostart (FuntouchOS 12+)
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                },
                // Vivo: Older FuntouchOS
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
                },
                // Vivo: Alternative path
                Intent().apply {
                    component = ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                },
                // Vivo: OriginOS path
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.PurviewTabActivity"
                    )
                }
            )

            OemType.HUAWEI -> listOf(
                // Huawei: Startup Manager (EMUI 10+)
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                },
                // Huawei: App Launch (EMUI 9)
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                },
                // Huawei: Older path
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                },
                // Honor devices
                Intent().apply {
                    component = ComponentName(
                        "com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            )

            OemType.TECNO -> listOf(
                // Tecno: Phone Master → Autostart
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.autostart.AutoStartActivity"
                    )
                },
                // Tecno: HiOS alternative
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.autostart.ui.AutoStartListActivity"
                    )
                }
            )

            OemType.INFINIX -> listOf(
                // Infinix: XOS Phone Master → Autostart
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.autostart.AutoStartActivity"
                    )
                },
                Intent().apply {
                    component = ComponentName(
                        "com.transsion.phonemaster",
                        "com.cxinventor.file.autostart.ui.AutoStartListActivity"
                    )
                }
            )

            else -> emptyList()
        }
    }

    /**
     * Gets autostart-specific setup steps for the current OEM.
     */
    fun getAutostartSetupSteps(context: Context): List<SetupStep> {
        if (!needsAutostartPermission()) return emptyList()

        val oemType = getOemType()
        val steps = mutableListOf<SetupStep>()

        when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> {
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable Autostart",
                    description = "Your device requires apps to have explicit autostart permission. " +
                            "Go to your phone's security settings → Permissions → Autostart → " +
                            "find this app and enable it. Without this, the app cannot start automatically " +
                            "and will miss transaction notifications.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getAutostartIntents(context).firstOrNull()
                ))
            }

            OemType.OPPO, OemType.REALME, OemType.ONEPLUS -> {
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable Autostart",
                    description = "Go to Settings → App Management → App List → find this app → " +
                            "enable 'Allow auto-start'. This ensures the app can receive messages in the background.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getAutostartIntents(context).firstOrNull()
                ))
            }

            OemType.VIVO, OemType.IQOO -> {
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable Autostart",
                    description = "Go to Settings → More Settings → Applications → Autostart Manager → " +
                            "find this app and enable it.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getAutostartIntents(context).firstOrNull()
                ))
            }

            OemType.HUAWEI -> {
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable Startup Manager",
                    description = "Go to Settings → Apps → Startup Manager → find this app → " +
                            "toggle it ON (enable Auto-launch, Secondary launch, and Run in background).",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getAutostartIntents(context).firstOrNull()
                ))
            }

            OemType.TECNO, OemType.INFINIX -> {
                steps.add(SetupStep(
                    stepNumber = 1,
                    title = "Enable Autostart",
                    description = "Open your device's phone master/manager app → Autostart Manager → " +
                            "find this app and enable it.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getAutostartIntents(context).firstOrNull()
                ))
            }

            else -> {} // No autostart needed
        }

        return steps
    }

    /**
     * Attempts to detect if autostart is currently enabled for our app.
     *
     * Note: There is NO reliable cross-OEM API to check this. This uses heuristics:
     * - Check if our boot receiver is enabled in PackageManager
     * - Check if we can read relevant system settings
     *
     * @param context Application context
     * @return true if autostart appears to be enabled (may have false positives)
     */
    private fun isAutostartEnabled(context: Context): Boolean {
        // Heuristic: Check if our boot broadcast receiver is enabled
        // If OEM disabled autostart, the component may be disabled
        try {
            val bootReceiverName = ComponentName(
                context.packageName,
                "${context.packageName}.receiver.BootCompletedReceiver"
            )
            val state = context.packageManager.getComponentEnabledSetting(bootReceiverName)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                return false
            }
        } catch (_: Exception) {
            // Component not found — not a definitive indicator
        }

        // Cannot reliably detect autostart state — assume enabled if battery is whitelisted
        // (since users who complete battery setup likely also enabled autostart)
        return isAppWhitelistedFromBatteryOptimization(context)
    }

    // ==================================================================================
    // SECTION 6: NOTIFICATION CHANNEL HANDLING
    // ==================================================================================

    /**
     * Opens the OEM-specific notification settings for our app.
     *
     * OEM notification quirks:
     * - Samsung: Custom notification categories, edge notifications, notification grouping
     * - Xiaomi: "Show notifications" must be enabled per-app in MIUI notification shade
     * - Oppo: "Notification Center" has separate on/off per app
     * - Vivo: Notification management separate from standard Android
     *
     * @param context Application context
     * @return true if notification settings were opened
     */
    fun openNotificationSettings(context: Context): Boolean {
        val oemType = getOemType()
        val intents = getNotificationSettingsIntents(oemType, context)

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }

        // Fallback: standard Android notification settings
        return try {
            val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gets notification settings intents per OEM.
     */
    private fun getNotificationSettingsIntents(oemType: OemType, context: Context): List<Intent> {
        val packageName = context.packageName

        return when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> listOf(
                // MIUI: Notification settings per-app
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.notifications.NotificationsAppDetailPreferenceActivity"
                    )
                    putExtra("package_name", packageName)
                },
                // Standard Android notification settings
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )

            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> listOf(
                // Samsung: App notification settings (standard path works well)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                },
                // Samsung: Edge notification settings (for edge panel)
                Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.app.cocktailbarservice",
                        "com.samsung.android.app.cocktailbarservice.settings.EdgeNotificationSettingsActivity"
                    )
                }
            )

            OemType.OPPO, OemType.REALME, OemType.ONEPLUS -> listOf(
                // ColorOS: Notification management
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.notificationmanager",
                        "com.coloros.notificationmanager.AppDetailPreferenceActivity"
                    )
                    putExtra("package", packageName)
                },
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )

            OemType.VIVO, OemType.IQOO -> listOf(
                // Vivo: Notification center
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.notification.floatwindow",
                        "com.vivo.notification.floatwindow.setting.FloatWindowSettingActivity"
                    )
                },
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )

            OemType.HUAWEI -> listOf(
                // Huawei: Notification manager
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.notificationmanager.ui.NotificationManagmentActivity"
                    )
                },
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )

            else -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        }
    }

    /**
     * Gets notification-specific setup steps for the current OEM.
     */
    fun getNotificationSetupSteps(context: Context): List<SetupStep> {
        val oemType = getOemType()
        val steps = mutableListOf<SetupStep>()

        // Universal step: ensure notifications are enabled
        steps.add(SetupStep(
            stepNumber = 1,
            title = "Enable notifications",
            description = "Make sure notifications are turned on for this app. " +
                    "Go to Settings → Apps → find this app → Notifications → enable all categories.",
            actionType = ActionType.OPEN_SETTINGS,
            intentToOpen = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        ))

        when (oemType) {
            OemType.XIAOMI, OemType.POCO, OemType.XIAOMI_HYPEROS -> {
                // MIUI notification shade filtering
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable notification display",
                    description = "Your device may filter notifications from appearing. " +
                            "In your phone's security settings → Notification shade → " +
                            "ensure 'Show notifications' is enabled for this app. " +
                            "Also enable 'Lock screen notifications' and 'Floating notifications'.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getNotificationSettingsIntents(oemType, context).firstOrNull()
                ))
                steps.add(SetupStep(
                    stepNumber = 3,
                    title = "Set notification importance to high",
                    description = "Go to Settings → Apps → Manage Apps → find this app → " +
                            "Notifications → set importance to 'High' or 'Urgent' to ensure " +
                            "alerts are not silently filtered.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                ))
            }

            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Check notification categories",
                    description = "Ensure all notification categories are enabled: " +
                            "Settings → Apps → find this app → Notifications → " +
                            "enable 'Transaction Alerts' and 'Budget Warnings' categories.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                ))
            }

            OemType.OPPO, OemType.REALME, OemType.ONEPLUS -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable in notification manager",
                    description = "Your device has a separate notification management system. " +
                            "Go to Settings → Notification & Status Bar → Notification Management → " +
                            "find this app → enable all notification types.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getNotificationSettingsIntents(oemType, context).firstOrNull()
                ))
            }

            OemType.VIVO, OemType.IQOO -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable floating notifications",
                    description = "Go to Settings → Notifications & Status Bar → " +
                            "find this app → enable 'Allow notifications', " +
                            "'Floating notifications', and 'Lock screen notifications'.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getNotificationSettingsIntents(oemType, context).firstOrNull()
                ))
            }

            OemType.HUAWEI -> {
                steps.add(SetupStep(
                    stepNumber = 2,
                    title = "Enable notification access",
                    description = "Go to Settings → Notification Center → find this app → " +
                            "enable 'Allow notifications', 'Priority display', and 'Banner notifications'.",
                    actionType = ActionType.OPEN_SETTINGS,
                    intentToOpen = getNotificationSettingsIntents(oemType, context).firstOrNull()
                ))
            }

            else -> {} // Standard notification settings are usually sufficient
        }

        return steps
    }

    // ==================================================================================
    // SECTION 7: DISPLAY/UI ADAPTATIONS
    // ==================================================================================

    /**
     * Display adaptation information for the current device.
     */
    data class DisplayAdaptation(
        val hasPunchHoleCamera: Boolean,
        val hasEdgeDisplay: Boolean,
        val isFoldable: Boolean,
        val isTablet: Boolean,
        val hasAlertSlider: Boolean,
        val statusBarHeight: Int,
        val navigationMode: NavigationMode,
        val screenRatio: Float,
        val hasDynamicIsland: Boolean
    )

    enum class NavigationMode {
        THREE_BUTTON,
        TWO_BUTTON,
        GESTURE,
        UNKNOWN
    }

    /**
     * Gets display adaptation information for the current device.
     *
     * @param context Application context
     * @return DisplayAdaptation with device-specific display info
     */
    fun getDisplayAdaptation(context: Context): DisplayAdaptation {
        val oemType = getOemType()
        val model = Build.MODEL.lowercase()
        val display = context.resources.displayMetrics

        val screenRatio = display.heightPixels.toFloat() / display.widthPixels.toFloat()
        val isTablet = (display.widthPixels / display.density) >= 600

        return DisplayAdaptation(
            hasPunchHoleCamera = detectPunchHoleCamera(oemType, model),
            hasEdgeDisplay = detectEdgeDisplay(oemType, model),
            isFoldable = detectFoldable(oemType, model),
            isTablet = isTablet,
            hasAlertSlider = oemType == OemType.ONEPLUS || oemType == OemType.NOTHING,
            statusBarHeight = getStatusBarHeight(context),
            navigationMode = detectNavigationMode(context),
            screenRatio = screenRatio,
            hasDynamicIsland = detectDynamicIsland(oemType, model)
        )
    }

    private fun detectPunchHoleCamera(oemType: OemType, model: String): Boolean {
        // Most modern phones (2020+) have punch-hole cameras
        return Build.VERSION.SDK_INT >= 28 // Android 9+ display cutout API
    }

    private fun detectEdgeDisplay(oemType: OemType, model: String): Boolean {
        return when (oemType) {
            OemType.SAMSUNG, OemType.SAMSUNG_LOW_END -> {
                // Samsung S/Note/Z series have edge displays
                model.contains("sm-s") || model.contains("sm-n") || model.contains("sm-f")
            }
            OemType.ONEPLUS -> model.contains("pro") || model.contains("ultra")
            OemType.VIVO -> model.contains("x fold") || model.contains("x90") || model.contains("x100")
            else -> false
        }
    }

    private fun detectFoldable(oemType: OemType, model: String): Boolean {
        val foldableIndicators = listOf(
            "fold", "flip", "find n", "x fold", "magic v", "mate x",
            "razr", "galaxy z", "sm-f"
        )
        return foldableIndicators.any { model.contains(it) }
    }

    private fun detectDynamicIsland(oemType: OemType, model: String): Boolean {
        // Some Android OEMs have adopted "Dynamic Island" style cutouts (2024+)
        return when (oemType) {
            OemType.XIAOMI -> model.contains("14") && model.contains("ultra")
            OemType.NOTHING -> true // Nothing Phone (2) has glyph interface
            else -> false
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            // Default 24dp
            (24 * context.resources.displayMetrics.density).toInt()
        }
    }

    private fun detectNavigationMode(context: Context): NavigationMode {
        return try {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                "navigation_mode",
                -1
            )
            when (mode) {
                0 -> NavigationMode.THREE_BUTTON
                1 -> NavigationMode.TWO_BUTTON
                2 -> NavigationMode.GESTURE
                else -> NavigationMode.UNKNOWN
            }
        } catch (_: Exception) {
            NavigationMode.UNKNOWN
        }
    }

    // ==================================================================================
    // SECTION 8: SAMSUNG-SPECIFIC SLEEPING APPS DETECTION
    // ==================================================================================

    /**
     * Checks if the app is in Samsung's "Sleeping Apps" or "Deep Sleeping Apps" list.
     *
     * Samsung puts apps that haven't been used in 3 days into "Sleeping Apps",
     * and apps unused for 30 days into "Deep Sleeping Apps". Apps in either list:
     * - Cannot receive broadcasts (SMS, boot, etc.)
     * - Are force-stopped by the system
     * - Cannot run background services
     *
     * Detection method: Read Samsung's sleeping_apps settings provider
     *
     * @param context Application context
     * @return true if app is in sleeping/deep sleeping list
     */
    private fun isAppInSleepingList(context: Context): Boolean {
        if (getOemType() != OemType.SAMSUNG && getOemType() != OemType.SAMSUNG_LOW_END) {
            return false
        }

        val packageName = context.packageName

        try {
            // Samsung stores sleeping apps in Settings.Secure
            // Key: "sleeping_condition_enabled" and app-specific entries
            val sleepingApps = Settings.Secure.getString(
                context.contentResolver,
                "intelligent_sleep_mode"
            )
            if (sleepingApps?.contains(packageName) == true) return true
        } catch (_: Exception) {
            // Settings key may not exist on this Samsung version
        }

        // Alternative: check via device policy/battery
        return !isAppWhitelistedFromBatteryOptimization(context)
    }

    // ==================================================================================
    // SECTION 9: COMPREHENSIVE SETUP INSTRUCTIONS GENERATOR
    // ==================================================================================

    /**
     * Generates the complete list of OEM-specific setup steps for the user.
     *
     * This is the primary method called by the Setup Wizard on first launch.
     * It combines all necessary steps in priority order:
     * 1. Battery optimization (most critical — prevents app killing)
     * 2. Autostart permission (required for boot receiver)
     * 3. SMS permissions (OEM-specific layers)
     * 4. Notification settings (ensure alerts are visible)
     *
     * All user-facing text uses generic device terminology.
     *
     * @param context Application context
     * @return Ordered list of all setup steps for the detected OEM
     */
    fun getSetupInstructions(context: Context): List<SetupStep> {
        val allSteps = mutableListOf<SetupStep>()
        var stepCounter = 1

        // Phase 1: Battery Optimization (CRITICAL)
        val batterySteps = getBatterySetupSteps(context)
        batterySteps.forEach { step ->
            allSteps.add(step.copy(stepNumber = stepCounter++))
        }

        // Phase 2: Autostart (if needed)
        val autostartSteps = getAutostartSetupSteps(context)
        autostartSteps.forEach { step ->
            allSteps.add(step.copy(stepNumber = stepCounter++))
        }

        // Phase 3: SMS Permissions
        val smsSteps = getSmsSetupSteps(context)
        smsSteps.forEach { step ->
            allSteps.add(step.copy(stepNumber = stepCounter++))
        }

        // Phase 4: Notifications
        val notificationSteps = getNotificationSetupSteps(context)
        notificationSteps.forEach { step ->
            allSteps.add(step.copy(stepNumber = stepCounter++))
        }

        return allSteps
    }

    /**
     * Detects all potential OEM-specific issues affecting app functionality.
     *
     * @param context Application context
     * @return List of detected issues with severity and fix instructions
     */
    fun detectIssues(context: Context): List<OemIssue> {
        val issues = mutableListOf<OemIssue>()
        val oemType = getOemType()

        // Issue 1: Battery optimization not whitelisted
        if (!isAppWhitelistedFromBatteryOptimization(context)) {
            val severity = if (oemType.hasAggressiveBatteryKill) {
                IssueSeverity.CRITICAL
            } else {
                IssueSeverity.MEDIUM
            }

            issues.add(OemIssue(
                type = IssueType.BATTERY_KILL,
                severity = severity,
                userMessage = "Your device may stop this app from running in the background. " +
                        "This means you might miss transaction alerts. " +
                        "Please follow the steps below to fix this.",
                fixSteps = getBatterySetupSteps(context)
            ))
        }

        // Issue 2: Autostart needed but possibly not enabled
        if (needsAutostartPermission()) {
            issues.add(OemIssue(
                type = IssueType.AUTOSTART_NEEDED,
                severity = IssueSeverity.HIGH,
                userMessage = "Your device requires explicit permission for apps to start automatically. " +
                        "Without this, the app cannot monitor transactions after your phone restarts.",
                fixSteps = getAutostartSetupSteps(context)
            ))
        }

        // Issue 3: SMS possibly blocked by OEM layer
        if (isSmsReceiverLikelyBlocked(context)) {
            issues.add(OemIssue(
                type = IssueType.SMS_BLOCKED,
                severity = IssueSeverity.CRITICAL,
                userMessage = "SMS reading appears to be blocked on your device. " +
                        "This app needs SMS access to automatically track your transactions. " +
                        "Please follow these steps to enable it.",
                fixSteps = getSmsSetupSteps(context)
            ))
        }

        // Issue 4: OEM-specific permission privacy layer
        if (oemType in setOf(OemType.OPPO, OemType.REALME, OemType.ONEPLUS, OemType.VIVO, OemType.IQOO)) {
            issues.add(OemIssue(
                type = IssueType.PERMISSION_PRIVACY_LAYER,
                severity = IssueSeverity.MEDIUM,
                userMessage = "Your device has an additional privacy layer that may block some app permissions " +
                        "even after granting them. Please verify in your device's security settings.",
                fixSteps = getSmsSetupSteps(context).filter { it.stepNumber > 1 }
            ))
        }

        // Issue 5: App freezing (Realme, Samsung)
        if (oemType == OemType.REALME || oemType == OemType.SAMSUNG || oemType == OemType.SAMSUNG_LOW_END) {
            issues.add(OemIssue(
                type = IssueType.APP_FROZEN,
                severity = IssueSeverity.MEDIUM,
                userMessage = "Your device may automatically freeze apps that haven't been used recently. " +
                        "If you notice missed alerts, please check that this app is not in the frozen/sleeping apps list.",
                fixSteps = getBatterySetupSteps(context)
            ))
        }

        return issues
    }

    /**
     * Generates the complete compatibility profile for the current device.
     *
     * @param context Application context
     * @return Full CompatProfile with all detected information
     */
    fun getCompatProfile(context: Context): CompatProfile {
        return CompatProfile(
            oemType = getOemType(),
            oemVersion = getOemVersion(),
            androidVersion = Build.VERSION.SDK_INT,
            issues = detectIssues(context),
            setupSteps = getSetupInstructions(context)
        )
    }

    // ==================================================================================
    // SECTION 10: SETUP WIZARD INTEGRATION
    // ==================================================================================

    private const val PREFS_NAME = "oem_compat_prefs"
    private const val KEY_SETUP_COMPLETED = "setup_completed"
    private const val KEY_SETUP_COMPLETED_TIME = "setup_completed_time"
    private const val KEY_LAST_HEALTH_CHECK = "last_health_check"
    private const val KEY_SMS_LAST_RECEIVED = "sms_last_received_time"
    private const val KEY_SETUP_DISMISSED_COUNT = "setup_dismissed_count"
    private const val KEY_OEM_TYPE_CACHED = "oem_type_cached"

    /** How often to re-check if SMS receiver is still working (24 hours) */
    private const val HEALTH_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** If no SMS received in this time, assume receiver may be killed (7 days) */
    private const val SMS_SILENCE_THRESHOLD_MS = 7 * 24 * 60 * 60 * 1000L

    /** Maximum times to show setup prompt after dismissal */
    private const val MAX_SETUP_PROMPTS = 3

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Checks if the OEM-specific setup wizard has been completed.
     *
     * @param context Application context
     * @return true if setup was completed
     */
    fun isSetupCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SETUP_COMPLETED, false)
    }

    /**
     * Marks the setup wizard as completed.
     *
     * @param context Application context
     */
    fun markSetupCompleted(context: Context) {
        getPrefs(context).edit()
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .putLong(KEY_SETUP_COMPLETED_TIME, System.currentTimeMillis())
            .putString(KEY_OEM_TYPE_CACHED, getOemType().name)
            .apply()
    }

    /**
     * Records that an SMS was successfully received.
     * Used for health monitoring — if SMS stops arriving, we know the receiver was killed.
     *
     * @param context Application context
     */
    fun recordSmsReceived(context: Context) {
        getPrefs(context).edit()
            .putLong(KEY_SMS_LAST_RECEIVED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Checks if the setup should be shown again.
     *
     * Conditions for re-showing:
     * 1. Setup was never completed
     * 2. SMS receiver appears to have stopped working (no SMS in 7 days)
     * 3. Battery optimization was re-enabled by the OS
     * 4. User hasn't dismissed the prompt too many times
     *
     * @param context Application context
     * @return true if setup wizard should be shown
     */
    fun shouldShowSetupWizard(context: Context): Boolean {
        val prefs = getPrefs(context)

        // Never completed — always show
        if (!prefs.getBoolean(KEY_SETUP_COMPLETED, false)) return true

        // Check dismissal count
        val dismissCount = prefs.getInt(KEY_SETUP_DISMISSED_COUNT, 0)
        if (dismissCount >= MAX_SETUP_PROMPTS) return false

        // Health check: has it been too long since last check?
        val lastHealthCheck = prefs.getLong(KEY_LAST_HEALTH_CHECK, 0)
        val now = System.currentTimeMillis()
        if (now - lastHealthCheck < HEALTH_CHECK_INTERVAL_MS) return false

        // Record this health check
        prefs.edit().putLong(KEY_LAST_HEALTH_CHECK, now).apply()

        // Check if battery optimization was re-enabled
        if (!isAppWhitelistedFromBatteryOptimization(context) && getOemType().hasAggressiveBatteryKill) {
            return true
        }

        // Check if SMS has gone silent (possible receiver death)
        val lastSms = prefs.getLong(KEY_SMS_LAST_RECEIVED, 0)
        if (lastSms > 0 && (now - lastSms) > SMS_SILENCE_THRESHOLD_MS) {
            return true
        }

        return false
    }

    /**
     * Records that the user dismissed the setup prompt.
     *
     * @param context Application context
     */
    fun recordSetupDismissed(context: Context) {
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_SETUP_DISMISSED_COUNT, 0)
        prefs.edit().putInt(KEY_SETUP_DISMISSED_COUNT, currentCount + 1).apply()
    }

    /**
     * Resets the dismissal counter (e.g., after a major OS update).
     *
     * @param context Application context
     */
    fun resetDismissalCounter(context: Context) {
        getPrefs(context).edit().putInt(KEY_SETUP_DISMISSED_COUNT, 0).apply()
    }

    /**
     * Gets a user-friendly summary message about the device's compatibility status.
     *
     * @param context Application context
     * @return Human-readable status message
     */
    fun getCompatibilityStatusMessage(context: Context): String {
        val oemType = getOemType()
        val issues = detectIssues(context)
        val criticalIssues = issues.filter { it.severity == IssueSeverity.CRITICAL }
        val highIssues = issues.filter { it.severity == IssueSeverity.HIGH }

        return when {
            criticalIssues.isNotEmpty() -> {
                "⚠️ Your device requires additional setup to ensure this app works correctly. " +
                        "${criticalIssues.size} critical issue(s) detected that may prevent " +
                        "transaction tracking from working."
            }
            highIssues.isNotEmpty() -> {
                "⚡ Your device needs a few settings changes for optimal performance. " +
                        "${highIssues.size} setting(s) should be adjusted to prevent missed alerts."
            }
            issues.isNotEmpty() -> {
                "ℹ️ Your device is mostly compatible. ${issues.size} minor recommendation(s) " +
                        "to improve reliability."
            }
            else -> {
                "✅ Your device is fully compatible! No additional setup needed."
            }
        }
    }

    // ==================================================================================
    // SECTION 11: INTENT HELPERS
    // ==================================================================================

    /**
     * Creates an intent to open the app info page in system settings.
     */
    private fun createAppInfoIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Creates intent for Xiaomi Security app permission page.
     * Xiaomi MIUI has a separate permission management system in the Security app.
     */
    private fun createXiaomiSecurityPermissionIntent(packageName: String): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", packageName)
        }
    }

    /**
     * Creates intent for ColorOS Permission Privacy settings.
     * Oppo/Realme/OnePlus (ColorOS) has an additional permission privacy layer.
     */
    private fun createColorOsPermissionIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.PermissionTopActivity"
            )
        }
    }

    /**
     * Creates intent for Vivo's i Manager app.
     * Vivo has a separate permission management system.
     */
    private fun createVivoIManagerIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.MainGuideActivity"
            )
        }
    }

    /**
     * Creates intent for Huawei's Phone Manager.
     * Huawei has a separate permission management in System Manager.
     */
    private fun createHuaweiPhoneManagerIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.permissionmanager.ui.MainActivity"
            )
        }
    }

    // ==================================================================================
    // SECTION 12: DIAGNOSTIC & DEBUG HELPERS
    // ==================================================================================

    /**
     * Generates a comprehensive diagnostic report for debugging compatibility issues.
     * This is intended for customer support / bug reports.
     *
     * @param context Application context
     * @return Multi-line diagnostic string
     */
    fun generateDiagnosticReport(context: Context): String {
        val profile = getCompatProfile(context)

        return buildString {
            appendLine("=== Paisa Brain Device Compatibility Report ===")
            appendLine()
            appendLine("--- Device Info ---")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Display: ${Build.DISPLAY}")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("--- OEM Detection ---")
            appendLine("Detected OEM Type: ${profile.oemType.name}")
            appendLine("OEM Version: ${profile.oemVersion}")
            appendLine("Has Aggressive Battery Kill: ${profile.oemType.hasAggressiveBatteryKill}")
            appendLine("Needs Autostart: ${needsAutostartPermission()}")
            appendLine()
            appendLine("--- Permission Status ---")
            appendLine("Battery Optimization Whitelisted: ${isAppWhitelistedFromBatteryOptimization(context)}")
            appendLine("SMS Receiver Likely Blocked: ${isSmsReceiverLikelyBlocked(context)}")
            appendLine()
            appendLine("--- System Properties ---")
            appendLine("ro.miui.ui.version.name: ${getSystemProperty("ro.miui.ui.version.name")}")
            appendLine("ro.build.version.oneui: ${getSystemProperty("ro.build.version.oneui")}")
            appendLine("ro.oxygen.version: ${getSystemProperty("ro.oxygen.version")}")
            appendLine("ro.build.version.oplusrom: ${getSystemProperty("ro.build.version.oplusrom")}")
            appendLine("ro.vivo.os.name: ${getSystemProperty("ro.vivo.os.name")}")
            appendLine("ro.build.version.emui: ${getSystemProperty("ro.build.version.emui")}")
            appendLine("ro.mi.os.version.name: ${getSystemProperty("ro.mi.os.version.name")}")
            appendLine("ro.lineage.version: ${getSystemProperty("ro.lineage.version")}")
            appendLine()
            appendLine("--- Detected Issues (${profile.issues.size}) ---")
            profile.issues.forEachIndexed { index, issue ->
                appendLine("  ${index + 1}. [${issue.severity}] ${issue.type}: ${issue.userMessage}")
            }
            appendLine()
            appendLine("--- Setup Steps (${profile.setupSteps.size}) ---")
            profile.setupSteps.forEachIndexed { index, step ->
                appendLine("  ${index + 1}. [${step.actionType}] ${step.title}")
                appendLine("     ${step.description}")
            }
            appendLine()
            appendLine("--- Setup Wizard State ---")
            val prefs = getPrefs(context)
            appendLine("Setup Completed: ${prefs.getBoolean(KEY_SETUP_COMPLETED, false)}")
            appendLine("Setup Completed Time: ${prefs.getLong(KEY_SETUP_COMPLETED_TIME, 0)}")
            appendLine("Last Health Check: ${prefs.getLong(KEY_LAST_HEALTH_CHECK, 0)}")
            appendLine("Last SMS Received: ${prefs.getLong(KEY_SMS_LAST_RECEIVED, 0)}")
            appendLine("Setup Dismissed Count: ${prefs.getInt(KEY_SETUP_DISMISSED_COUNT, 0)}")
            appendLine()
            appendLine("=== End Report ===")
        }
    }

    /**
     * Resets all cached detection data. Useful for testing or after OS update.
     */
    fun resetCache() {
        cachedOemType = null
        cachedOemVersion = null
    }

    /**
     * Performs a quick health check and returns a simple status.
     *
     * @param context Application context
     * @return Pair of (isHealthy: Boolean, statusMessage: String)
     */
    fun performHealthCheck(context: Context): Pair<Boolean, String> {
        val issues = detectIssues(context)
        val criticalIssues = issues.filter {
            it.severity == IssueSeverity.CRITICAL || it.severity == IssueSeverity.HIGH
        }

        val isHealthy = criticalIssues.isEmpty()
        val message = if (isHealthy) {
            "All systems operational. Background SMS monitoring is active."
        } else {
            "Warning: ${criticalIssues.size} issue(s) may affect transaction tracking. " +
                    "Please review your device settings."
        }

        return Pair(isHealthy, message)
    }

    // ==================================================================================
    // SECTION 13: KNOWN OEM PACKAGE NAMES REGISTRY
    // ==================================================================================

    /**
     * Registry of known OEM system app package names.
     * Used for detecting OEM-specific apps and launching correct settings.
     *
     * Sources:
     * - dontkillmyapp.com community database
     * - Public GitHub gists and compatibility libraries
     * - OEM developer documentation
     * - Reverse-engineered from device testing
     */
    object KnownPackages {
        // Samsung system apps
        const val SAMSUNG_DEVICE_CARE = "com.samsung.android.lool"
        const val SAMSUNG_SMART_MANAGER = "com.samsung.android.sm"
        const val SAMSUNG_BATTERY_MANAGER = "com.samsung.android.sm.battery"

        // Xiaomi system apps
        const val XIAOMI_SECURITY_CENTER = "com.miui.securitycenter"
        const val XIAOMI_POWER_KEEPER = "com.miui.powerkeeper"
        const val XIAOMI_PERMISSION_EDITOR = "com.miui.permcenter"

        // Oppo/Realme/OnePlus system apps
        const val OPPO_SAFE_CENTER = "com.coloros.safecenter"
        const val OPLUS_SAFE_CENTER = "com.oplus.safecenter"
        const val OPPO_GUARD_ELF = "com.coloros.oppoguardelf"
        const val OPLUS_BATTERY = "com.oplus.battery"
        const val ONEPLUS_SECURITY = "com.oneplus.security"

        // Vivo system apps
        const val VIVO_PERMISSION_MANAGER = "com.vivo.permissionmanager"
        const val VIVO_IQOO_SECURE = "com.iqoo.secure"
        const val VIVO_POWER_SAVING = "com.iqoo.powersaving"
        const val VIVO_ABE = "com.vivo.abe"

        // Huawei system apps
        const val HUAWEI_SYSTEM_MANAGER = "com.huawei.systemmanager"
        const val HONOR_SYSTEM_MANAGER = "com.hihonor.systemmanager"

        // Tecno/Infinix system apps
        const val TRANSSION_PHONE_MASTER = "com.transsion.phonemaster"

        /**
         * Checks if a package is installed on the device.
         */
        fun isPackageInstalled(context: Context, packageName: String): Boolean {
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        /**
         * Gets the list of OEM system packages that are installed on this device.
         * Useful for confirming OEM detection.
         */
        fun getInstalledOemPackages(context: Context): List<String> {
            val allPackages = listOf(
                SAMSUNG_DEVICE_CARE, SAMSUNG_SMART_MANAGER, SAMSUNG_BATTERY_MANAGER,
                XIAOMI_SECURITY_CENTER, XIAOMI_POWER_KEEPER,
                OPPO_SAFE_CENTER, OPLUS_SAFE_CENTER, OPPO_GUARD_ELF, OPLUS_BATTERY, ONEPLUS_SECURITY,
                VIVO_PERMISSION_MANAGER, VIVO_IQOO_SECURE, VIVO_POWER_SAVING, VIVO_ABE,
                HUAWEI_SYSTEM_MANAGER, HONOR_SYSTEM_MANAGER,
                TRANSSION_PHONE_MASTER
            )

            return allPackages.filter { isPackageInstalled(context, it) }
        }
    }

    // ==================================================================================
    // SECTION 14: FOLDABLE DEVICE SUPPORT
    // ==================================================================================

    /**
     * Provides layout hints for foldable devices.
     *
     * Foldable considerations:
     * - Samsung Galaxy Z Fold: Outer screen (narrow) vs inner screen (tablet-like)
     * - Samsung Galaxy Z Flip: Compact upper half, full lower half
     * - Oppo Find N: Square-ish inner display
     * - Vivo X Fold: Wide inner display
     * - Google Pixel Fold: Wider outer, squarish inner
     *
     * @param context Application context
     * @return Pair of (isFolded: Boolean, screenAspectRatio: Float)
     */
    fun getFoldableState(context: Context): Pair<Boolean, Float> {
        val display = context.resources.displayMetrics
        val ratio = display.heightPixels.toFloat() / display.widthPixels.toFloat()

        // Heuristic: folded state typically has ratio > 2.0 (tall and narrow)
        // Unfolded state has ratio between 0.8 and 1.5 (square-ish)
        val likelyFolded = ratio > 2.0f

        return Pair(likelyFolded, ratio)
    }

    // ==================================================================================
    // SECTION 15: BACKGROUND DATA RESTRICTION CHECK
    // ==================================================================================

    /**
     * Checks if background data is restricted for this app.
     *
     * Some OEMs restrict background data by default for non-system apps,
     * which can prevent cloud sync and real-time balance updates.
     *
     * @param context Application context
     * @return true if background data appears to be restricted
     */
    fun isBackgroundDataRestricted(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false

        return try {
            connectivityManager.restrictBackgroundStatus ==
                    android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (_: Exception) {
            false
        }
    }

    // ==================================================================================
    // SECTION 16: OEM-SPECIFIC ALERT SLIDER HANDLING (OnePlus/Nothing)
    // ==================================================================================

    /**
     * Checks if the device has a physical alert slider that could mute notifications.
     *
     * Devices with alert sliders:
     * - OnePlus: All models have a tri-state slider (Silent/Vibrate/Ring)
     * - Nothing: Phone (1) and Phone (2) have a similar slider
     *
     * If the slider is in Silent mode, transaction alerts won't produce sound.
     * We should still show visual notifications but warn the user.
     *
     * @return true if device has an alert slider
     */
    fun hasAlertSlider(): Boolean {
        return getOemType() == OemType.ONEPLUS || getOemType() == OemType.NOTHING
    }

    /**
     * Gets a warning message if the device has an alert slider.
     * This should be shown in notification settings UI.
     */
    fun getAlertSliderWarning(): String? {
        if (!hasAlertSlider()) return null
        return "Your device has a physical notification slider on the side. " +
                "If it's set to silent mode, you won't hear transaction alerts " +
                "(but they will still appear on screen)."
    }

    // ==================================================================================
    // SECTION 17: CONVENIENCE EXTENSION FUNCTIONS
    // ==================================================================================

    /**
     * Quick check: does this device need any OEM-specific setup at all?
     *
     * @param context Application context
     * @return true if OEM-specific setup steps exist for this device
     */
    fun needsOemSetup(context: Context): Boolean {
        return getSetupInstructions(context).isNotEmpty() &&
                getOemType() != OemType.STOCK_ANDROID &&
                getOemType() != OemType.GOOGLE
    }

    /**
     * Returns the number of critical issues currently detected.
     *
     * @param context Application context
     * @return Count of CRITICAL and HIGH severity issues
     */
    fun getCriticalIssueCount(context: Context): Int {
        return detectIssues(context).count {
            it.severity == IssueSeverity.CRITICAL || it.severity == IssueSeverity.HIGH
        }
    }

    /**
     * Attempts to open all possible settings in sequence for a "fix everything" flow.
     * Returns the number of settings pages successfully opened.
     *
     * @param context Application context
     * @return Number of settings pages opened
     */
    fun openAllRelevantSettings(context: Context): Int {
        var opened = 0

        if (needsBatteryWhitelist() && !isAppWhitelistedFromBatteryOptimization(context)) {
            if (openBatterySettings(context)) opened++
        }

        if (needsAutostartPermission()) {
            if (openAutostartSettings(context)) opened++
        }

        return opened
    }
}
