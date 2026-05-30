package com.paisabrain.app.privacy

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * PrivacyGuard — Your Personal Privacy Dashboard 🛡️
 * 
 * Monitors which OTHER apps on your phone accessed:
 * - Camera 📷
 * - Microphone 🎤
 * - Location 📍
 * - Contacts 📇
 * - SMS 💬
 * - Phone 📞
 * - Storage 📁
 * 
 * REQUIRES: android.permission.PACKAGE_USAGE_STATS
 * (User must grant in Settings → Apps → Special access → Usage access)
 * 
 * THIS IS THE KILLER FEATURE that no other finance app has.
 * People install Paisa Brain for money tracking → STAY for privacy monitoring.
 */
object PrivacyGuard {

    data class PermissionAccessEvent(
        val appName: String,
        val packageName: String,
        val permission: PermissionType,
        val accessTime: Long,
        val duration: Long? = null, // milliseconds, if available
        val isBackground: Boolean = false
    )

    enum class PermissionType(val displayName: String, val emoji: String) {
        CAMERA("Camera", "📷"),
        MICROPHONE("Microphone", "🎤"),
        LOCATION("Location", "📍"),
        CONTACTS("Contacts", "📇"),
        SMS("SMS", "💬"),
        PHONE("Phone", "📞"),
        STORAGE("Storage", "📁"),
        BODY_SENSORS("Sensors", "💓")
    }

    data class PrivacySummary(
        val totalAccessesToday: Int,
        val cameraAccesses: List<PermissionAccessEvent>,
        val microphoneAccesses: List<PermissionAccessEvent>,
        val locationAccesses: List<PermissionAccessEvent>,
        val backgroundAccesses: Int,
        val topSpyApp: String?, // App with most accesses
        val topSpyAppCount: Int,
        val privacyScore: Int, // 0-100 (higher = more private)
        val alerts: List<PrivacyAlert>
    )

    data class PrivacyAlert(
        val severity: AlertSeverity,
        val title: String,
        val description: String,
        val appName: String,
        val emoji: String
    )

    enum class AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }

    /**
     * Get privacy summary for last 24 hours.
     * Uses AppOpsManager (Android 10+) for accurate permission tracking.
     */
    fun getPrivacySummary(context: Context): PrivacySummary {
        val events = getPermissionAccessHistory(context, hours = 24)
        
        val cameraAccesses = events.filter { it.permission == PermissionType.CAMERA }
        val micAccesses = events.filter { it.permission == PermissionType.MICROPHONE }
        val locationAccesses = events.filter { it.permission == PermissionType.LOCATION }
        val backgroundAccesses = events.count { it.isBackground }

        // Find top "spy" app
        val appAccessCounts = events.groupBy { it.packageName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
        
        val topSpy = appAccessCounts.firstOrNull()

        // Generate alerts
        val alerts = generateAlerts(events, context)

        // Privacy score (100 = no accesses, lower = more exposed)
        val score = calculatePrivacyScore(events)

        return PrivacySummary(
            totalAccessesToday = events.size,
            cameraAccesses = cameraAccesses,
            microphoneAccesses = micAccesses,
            locationAccesses = locationAccesses,
            backgroundAccesses = backgroundAccesses,
            topSpyApp = topSpy?.key?.let { getAppName(context, it) },
            topSpyAppCount = topSpy?.value ?: 0,
            privacyScore = score,
            alerts = alerts
        )
    }

    /**
     * Get detailed permission access history using AppOpsManager.
     * Works on Android 10+ (API 29+). On older versions, returns limited data.
     */
    fun getPermissionAccessHistory(context: Context, hours: Int = 24): List<PermissionAccessEvent> {
        val events = mutableListOf<PermissionAccessEvent>()
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 8-9: Limited access, use UsageStatsManager
            return getLegacyUsageData(context, hours)
        }

        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val packageManager = context.packageManager
        val startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours.toLong())

        // Map of AppOps to our PermissionType
        val opsToCheck = mapOf(
            AppOpsManager.OPSTR_CAMERA to PermissionType.CAMERA,
            AppOpsManager.OPSTR_RECORD_AUDIO to PermissionType.MICROPHONE,
            AppOpsManager.OPSTR_FINE_LOCATION to PermissionType.LOCATION,
            AppOpsManager.OPSTR_COARSE_LOCATION to PermissionType.LOCATION,
            AppOpsManager.OPSTR_READ_CONTACTS to PermissionType.CONTACTS,
            AppOpsManager.OPSTR_READ_SMS to PermissionType.SMS,
            AppOpsManager.OPSTR_READ_PHONE_STATE to PermissionType.PHONE
        )

        // Get all installed apps
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in installedApps) {
            // Skip system apps and our own app
            if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            if (appInfo.packageName == context.packageName) continue

            for ((op, permType) in opsToCheck) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+: Can get discrete access events
                        val ops = appOpsManager.getOpsForPackage(
                            appInfo.uid,
                            appInfo.packageName,
                            arrayOf(op)
                        )
                        ops?.forEach { packageOps ->
                            packageOps.ops.forEach { opEntry ->
                                // Check last access time
                                val lastAccess = opEntry.getLastAccessTime(
                                    AppOpsManager.OP_FLAGS_ALL
                                )
                                if (lastAccess > startTime) {
                                    events.add(
                                        PermissionAccessEvent(
                                            appName = getAppName(context, appInfo.packageName),
                                            packageName = appInfo.packageName,
                                            permission = permType,
                                            accessTime = lastAccess,
                                            isBackground = opEntry.getLastAccessBackgroundTime(
                                                AppOpsManager.OP_FLAGS_ALL
                                            ) > startTime
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Permission denied or package not found — skip
                }
            }
        }

        return events.sortedByDescending { it.accessTime }
    }

    private fun getLegacyUsageData(context: Context, hours: Int): List<PermissionAccessEvent> {
        // Fallback for Android 8-9: Only shows app usage, not specific permissions
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(hours.toLong())
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        
        return stats
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .take(20)
            .map { stat ->
                PermissionAccessEvent(
                    appName = getAppName(context, stat.packageName),
                    packageName = stat.packageName,
                    permission = PermissionType.STORAGE, // Generic — can't determine specific permission
                    accessTime = stat.lastTimeUsed,
                    duration = stat.totalTimeInForeground
                )
            }
    }

    /**
     * Generate privacy alerts based on suspicious patterns
     */
    private fun generateAlerts(events: List<PermissionAccessEvent>, context: Context): List<PrivacyAlert> {
        val alerts = mutableListOf<PrivacyAlert>()

        // Alert: Camera access in background
        val bgCameraAccess = events.filter { it.permission == PermissionType.CAMERA && it.isBackground }
        if (bgCameraAccess.isNotEmpty()) {
            alerts.add(PrivacyAlert(
                severity = AlertSeverity.CRITICAL,
                title = "Background Camera Access Detected!",
                description = "${bgCameraAccess.first().appName} accessed your camera while in background. This is suspicious.",
                appName = bgCameraAccess.first().appName,
                emoji = "🚨"
            ))
        }

        // Alert: Microphone access in background
        val bgMicAccess = events.filter { it.permission == PermissionType.MICROPHONE && it.isBackground }
        if (bgMicAccess.isNotEmpty()) {
            alerts.add(PrivacyAlert(
                severity = AlertSeverity.HIGH,
                title = "Background Mic Access!",
                description = "${bgMicAccess.first().appName} listened through your microphone in the background.",
                appName = bgMicAccess.first().appName,
                emoji = "🎤⚠️"
            ))
        }

        // Alert: Excessive location tracking
        val locationHeavyApps = events
            .filter { it.permission == PermissionType.LOCATION }
            .groupBy { it.packageName }
            .filter { it.value.size > 50 } // More than 50 location checks in 24h
        
        locationHeavyApps.forEach { (pkg, accesses) ->
            alerts.add(PrivacyAlert(
                severity = AlertSeverity.MEDIUM,
                title = "Excessive Location Tracking",
                description = "${accesses.first().appName} checked your location ${accesses.size} times today. That's a lot!",
                appName = accesses.first().appName,
                emoji = "📍"
            ))
        }

        // Alert: Late night camera/mic access (2am-5am)
        val lateNightAccess = events.filter { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.accessTime }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            hour in 2..5 && (event.permission == PermissionType.CAMERA || event.permission == PermissionType.MICROPHONE)
        }
        if (lateNightAccess.isNotEmpty()) {
            alerts.add(PrivacyAlert(
                severity = AlertSeverity.HIGH,
                title = "Late Night Sensor Access",
                description = "${lateNightAccess.first().appName} accessed ${lateNightAccess.first().permission.displayName} between 2-5am while you were likely sleeping.",
                appName = lateNightAccess.first().appName,
                emoji = "🌙⚠️"
            ))
        }

        return alerts.sortedByDescending { it.severity.ordinal }
    }

    /**
     * Calculate privacy score (0-100, higher is better)
     */
    private fun calculatePrivacyScore(events: List<PermissionAccessEvent>): Int {
        if (events.isEmpty()) return 100

        var score = 100
        
        // Deduct for each type of access
        score -= minOf(events.filter { it.permission == PermissionType.CAMERA }.size * 3, 20)
        score -= minOf(events.filter { it.permission == PermissionType.MICROPHONE }.size * 3, 20)
        score -= minOf(events.filter { it.permission == PermissionType.LOCATION }.size, 15)
        score -= minOf(events.filter { it.isBackground }.size * 5, 30)
        
        // Deduct for late night access
        val lateNight = events.count { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.accessTime }
            cal.get(Calendar.HOUR_OF_DAY) in 0..5
        }
        score -= minOf(lateNight * 5, 15)

        return score.coerceIn(0, 100)
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.split(".").lastOrNull() ?: packageName
        }
    }

    /**
     * Get daily privacy report for notification
     */
    fun getDailyPrivacyReport(context: Context): String {
        val summary = getPrivacySummary(context)
        return buildString {
            appendLine("🛡️ Daily Privacy Report")
            appendLine("Privacy Score: ${summary.privacyScore}/100")
            appendLine("")
            appendLine("📷 Camera accessed: ${summary.cameraAccesses.size} times")
            appendLine("🎤 Microphone accessed: ${summary.microphoneAccesses.size} times")
            appendLine("📍 Location accessed: ${summary.locationAccesses.size} times")
            appendLine("👀 Background accesses: ${summary.backgroundAccesses}")
            if (summary.topSpyApp != null) {
                appendLine("")
                appendLine("⚠️ Most active: ${summary.topSpyApp} (${summary.topSpyAppCount} accesses)")
            }
            if (summary.alerts.isNotEmpty()) {
                appendLine("")
                appendLine("🚨 ${summary.alerts.size} alert(s) detected!")
            }
        }
    }
}
