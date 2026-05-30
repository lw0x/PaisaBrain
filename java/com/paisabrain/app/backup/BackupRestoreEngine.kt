package com.paisabrain.app.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Offline Encrypted Backup & Restore Engine for the Paisa Brain app.
 *
 * Exports ALL app data (transactions, vault entries, categories, settings,
 * achievements, streaks, debts, goals, journal entries, documents, investments)
 * into a single AES-256-GCM encrypted ZIP file (.pbk extension).
 *
 * The user supplies a master password which is stretched via PBKDF2-HMAC-SHA256
 * (100,000 iterations) to derive a 256-bit encryption key. The resulting file
 * can be stored on external storage, USB, or any location the user chooses.
 *
 * NO cloud upload is performed — all data stays fully under user control.
 */
class BackupRestoreEngine(private val context: Context) {

    companion object {
        /** Custom file extension for recognizable backup files. */
        const val BACKUP_EXTENSION = "pbk"

        /** Current schema/format version for migration handling. */
        private const val CURRENT_BACKUP_FORMAT_VERSION = 1

        /** PBKDF2 iteration count — balance security vs. mobile performance. */
        private const val PBKDF2_ITERATIONS = 100_000

        /** Key length in bits for AES-256. */
        private const val KEY_LENGTH_BITS = 256

        /** Salt length in bytes for PBKDF2. */
        private const val SALT_LENGTH = 32

        /** GCM IV/nonce length in bytes. */
        private const val GCM_IV_LENGTH = 12

        /** GCM authentication tag length in bits. */
        private const val GCM_TAG_LENGTH = 128

        /** SharedPreferences key for last backup timestamp. */
        private const val PREF_LAST_BACKUP = "last_backup_timestamp"

        /** SharedPreferences key for backup reminder interval. */
        private const val PREF_REMINDER_INTERVAL = "backup_reminder_interval_days"

        /** Default reminder interval: 30 days. */
        private const val DEFAULT_REMINDER_INTERVAL_DAYS = 30

        /** Metadata file name inside the encrypted ZIP. */
        private const val METADATA_FILE = "backup_metadata.json"

        /** Checksum file inside the encrypted ZIP. */
        private const val CHECKSUM_FILE = "integrity_checksum.sha256"

        private const val PREFS_NAME = "paisa_brain_backup_prefs"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────────────────────────────────
    // Data Classes
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Metadata stored inside the encrypted backup describing its contents.
     *
     * @param appVersion The version code of the app when backup was created.
     * @param backupDate ISO-8601 timestamp of backup creation.
     * @param recordCounts Map of table/entity name to record count.
     * @param totalSizeBytes Uncompressed total data size.
     * @param encryptionMethod Description of encryption used (e.g., "AES-256-GCM + PBKDF2").
     * @param formatVersion Internal format version for migration handling.
     */
    data class BackupMetadata(
        val appVersion: Int,
        val backupDate: String,
        val recordCounts: Map<String, Int>,
        val totalSizeBytes: Long,
        val encryptionMethod: String,
        val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION
    )

    /**
     * Result of a backup creation operation.
     *
     * @param success Whether the backup completed without error.
     * @param filePath Output path of the created backup file.
     * @param metadata Metadata of the created backup.
     * @param fileSizeBytes Size of the final encrypted file.
     * @param durationMs Time taken to create the backup.
     * @param error Error message if success is false.
     */
    data class BackupResult(
        val success: Boolean,
        val filePath: String? = null,
        val metadata: BackupMetadata? = null,
        val fileSizeBytes: Long = 0L,
        val durationMs: Long = 0L,
        val error: String? = null
    )

    /**
     * Result of a restore operation.
     *
     * @param success Whether the restore completed (possibly with warnings).
     * @param recordsRestored Map of table/entity name to count of records restored.
     * @param warnings Non-fatal issues encountered during restore.
     * @param errors Fatal issues — if non-empty, success should be false.
     */
    data class RestoreResult(
        val success: Boolean,
        val recordsRestored: Map<String, Int> = emptyMap(),
        val warnings: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    )

    /**
     * Defines what subset of data to restore.
     */
    enum class RestoreType {
        /** Restore all data — full overwrite. */
        FULL,
        /** Restore only transaction records. */
        TRANSACTIONS_ONLY,
        /** Restore only vault/password entries. */
        VAULT_ONLY,
        /** Restore only app settings and preferences. */
        SETTINGS_ONLY
    }

    /**
     * Backup reminder interval options.
     */
    enum class ReminderInterval(val days: Int) {
        WEEKLY(7),
        MONTHLY(30),
        NEVER(-1)
    }

    /**
     * Estimation result before actually performing backup.
     */
    data class BackupSizeEstimate(
        val estimatedUncompressedBytes: Long,
        val estimatedCompressedBytes: Long,
        val recordCounts: Map<String, Int>,
        val estimatedDurationSeconds: Int
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a full encrypted backup of all app data.
     *
     * @param password User-provided master password for encryption.
     * @param outputPath Absolute path or SAF URI where the backup will be saved.
     * @return [BackupResult] describing outcome.
     */
    fun createBackup(password: String, outputPath: String): BackupResult {
        val startTime = System.currentTimeMillis()

        if (password.length < 4) {
            return BackupResult(
                success = false,
                error = "Password must be at least 4 characters for security."
            )
        }

        return try {
            // Step 1: Collect all data into an in-memory ZIP
            val rawZipBytes = buildRawBackupZip()

            // Step 2: Compute integrity checksum of the raw data
            val checksum = computeSha256(rawZipBytes)

            // Step 3: Prepend checksum entry into the zip
            val finalZipBytes = appendChecksumToZip(rawZipBytes, checksum)

            // Step 4: Encrypt the entire ZIP with AES-256-GCM
            val encryptedData = encrypt(finalZipBytes, password)

            // Step 5: Write to output file
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(encryptedData)

            // Step 6: Record backup timestamp
            prefs.edit().putLong(PREF_LAST_BACKUP, System.currentTimeMillis()).apply()

            val metadata = extractMetadataFromZip(rawZipBytes)
            val duration = System.currentTimeMillis() - startTime

            BackupResult(
                success = true,
                filePath = outputPath,
                metadata = metadata,
                fileSizeBytes = encryptedData.size.toLong(),
                durationMs = duration
            )
        } catch (e: Exception) {
            BackupResult(
                success = false,
                error = "Backup failed: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Creates an encrypted backup and writes it to a SAF [Uri].
     * Useful when user picks a destination via Android file picker.
     *
     * @param password User-provided master password.
     * @param outputUri SAF Uri from the document picker.
     * @return [BackupResult] describing outcome.
     */
    fun createBackup(password: String, outputUri: Uri): BackupResult {
        val startTime = System.currentTimeMillis()

        if (password.length < 4) {
            return BackupResult(
                success = false,
                error = "Password must be at least 4 characters for security."
            )
        }

        return try {
            val rawZipBytes = buildRawBackupZip()
            val checksum = computeSha256(rawZipBytes)
            val finalZipBytes = appendChecksumToZip(rawZipBytes, checksum)
            val encryptedData = encrypt(finalZipBytes, password)

            context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                stream.write(encryptedData)
            } ?: return BackupResult(success = false, error = "Cannot open output location.")

            prefs.edit().putLong(PREF_LAST_BACKUP, System.currentTimeMillis()).apply()

            val metadata = extractMetadataFromZip(rawZipBytes)
            val duration = System.currentTimeMillis() - startTime

            BackupResult(
                success = true,
                filePath = outputUri.toString(),
                metadata = metadata,
                fileSizeBytes = encryptedData.size.toLong(),
                durationMs = duration
            )
        } catch (e: Exception) {
            BackupResult(
                success = false,
                error = "Backup failed: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Restores data from an encrypted backup file.
     *
     * @param password Master password used during backup creation.
     * @param inputPath Path to the .pbk backup file.
     * @param restoreType What subset of data to restore.
     * @return [RestoreResult] describing what was restored.
     */
    fun restoreBackup(password: String, inputPath: String, restoreType: RestoreType): RestoreResult {
        return try {
            val encryptedBytes = File(inputPath).readBytes()
            performRestore(encryptedBytes, password, restoreType)
        } catch (e: FileNotFoundException) {
            RestoreResult(success = false, errors = listOf("Backup file not found: $inputPath"))
        } catch (e: Exception) {
            RestoreResult(success = false, errors = listOf("Restore failed: ${e.message}"))
        }
    }

    /**
     * Restores data from an encrypted backup via SAF [Uri].
     *
     * @param password Master password used during backup creation.
     * @param inputUri SAF Uri of the .pbk file.
     * @param restoreType What subset of data to restore.
     * @return [RestoreResult] describing what was restored.
     */
    fun restoreBackup(password: String, inputUri: Uri, restoreType: RestoreType): RestoreResult {
        return try {
            val encryptedBytes = context.contentResolver.openInputStream(inputUri)?.use {
                it.readBytes()
            } ?: return RestoreResult(success = false, errors = listOf("Cannot read backup file."))

            performRestore(encryptedBytes, password, restoreType)
        } catch (e: Exception) {
            RestoreResult(success = false, errors = listOf("Restore failed: ${e.message}"))
        }
    }

    /**
     * Reads backup metadata from an encrypted file WITHOUT decrypting all contents.
     * Actually requires password to decrypt, but only parses metadata entry.
     *
     * For a quick peek without password, reads file header for basic info
     * (file size, extension validation). Full metadata requires decryption.
     *
     * @param filePath Path to the .pbk file.
     * @return [BackupMetadata] if readable, null otherwise.
     */
    fun getBackupFileInfo(filePath: String): BackupMetadata? {
        return try {
            val file = File(filePath)
            if (!file.exists() || !file.name.endsWith(".$BACKUP_EXTENSION")) return null

            // Without password, we can only report file-level info
            // Return a metadata stub with file size
            BackupMetadata(
                appVersion = 0, // Unknown without decryption
                backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(file.lastModified())),
                recordCounts = emptyMap(),
                totalSizeBytes = file.length(),
                encryptionMethod = "AES-256-GCM + PBKDF2 (encrypted — password needed for full info)"
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads full backup metadata after decryption.
     *
     * @param filePath Path to the .pbk file.
     * @param password Master password.
     * @return [BackupMetadata] if decryption succeeds, null otherwise.
     */
    fun getBackupFileInfoDecrypted(filePath: String, password: String): BackupMetadata? {
        return try {
            val encryptedBytes = File(filePath).readBytes()
            val decryptedZip = decrypt(encryptedBytes, password)
            extractMetadataFromZip(decryptedZip)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the timestamp of the last successful backup, or null if never backed up.
     */
    fun getLastBackupDate(): Long? {
        val ts = prefs.getLong(PREF_LAST_BACKUP, -1L)
        return if (ts > 0) ts else null
    }

    /**
     * Checks whether the app should remind the user to perform a backup.
     *
     * @return true if the configured interval has elapsed since last backup.
     */
    fun shouldRemindBackup(): Boolean {
        val intervalDays = prefs.getInt(PREF_REMINDER_INTERVAL, DEFAULT_REMINDER_INTERVAL_DAYS)
        if (intervalDays < 0) return false // "Never" is set

        val lastBackup = getLastBackupDate() ?: return true // Never backed up → remind

        val daysSinceBackup = (System.currentTimeMillis() - lastBackup) / (1000L * 60 * 60 * 24)
        return daysSinceBackup >= intervalDays
    }

    /**
     * Sets how frequently the user should be reminded to back up.
     *
     * @param interval One of WEEKLY, MONTHLY, or NEVER.
     */
    fun setReminderInterval(interval: ReminderInterval) {
        prefs.edit().putInt(PREF_REMINDER_INTERVAL, interval.days).apply()
    }

    /**
     * Returns the currently configured reminder interval.
     */
    fun getReminderInterval(): ReminderInterval {
        val days = prefs.getInt(PREF_REMINDER_INTERVAL, DEFAULT_REMINDER_INTERVAL_DAYS)
        return when {
            days <= 7 -> ReminderInterval.WEEKLY
            days < 0 -> ReminderInterval.NEVER
            else -> ReminderInterval.MONTHLY
        }
    }

    /**
     * Estimates the backup file size without actually creating it.
     * Useful to warn user if storage is limited.
     *
     * @return [BackupSizeEstimate] with projected sizes and record counts.
     */
    fun estimateBackupSize(): BackupSizeEstimate {
        val recordCounts = collectRecordCounts()
        val totalRecords = recordCounts.values.sum()

        // Rough estimation: ~200 bytes per record on average (varies by type)
        val estimatedUncompressed = totalRecords * 200L
        // ZIP typically achieves 60-70% compression on JSON text
        val estimatedCompressed = (estimatedUncompressed * 0.35).toLong()
        // Encryption adds ~60 bytes overhead (salt + iv + tag)
        val estimatedFinal = estimatedCompressed + 60

        // Speed estimation: ~2MB/s on average device
        val estimatedSeconds = maxOf(1, (estimatedFinal / (2 * 1024 * 1024)).toInt())

        return BackupSizeEstimate(
            estimatedUncompressedBytes = estimatedUncompressed,
            estimatedCompressedBytes = estimatedFinal,
            recordCounts = recordCounts,
            estimatedDurationSeconds = estimatedSeconds
        )
    }

    /**
     * Generates a standardized backup filename with today's date.
     *
     * @return Filename like "PaisaBrain_Backup_20260519.pbk"
     */
    fun generateBackupFilename(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        return "PaisaBrain_Backup_$dateStr.$BACKUP_EXTENSION"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Encryption / Decryption
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Encrypts data using AES-256-GCM with PBKDF2-derived key.
     *
     * Output format: [salt (32 bytes)] [iv (12 bytes)] [ciphertext+tag]
     */
    private fun encrypt(plainData: ByteArray, password: String): ByteArray {
        val random = SecureRandom()

        // Generate random salt
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)

        // Derive key from password using PBKDF2
        val key = deriveKey(password, salt)

        // Generate random IV for GCM
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)

        // Encrypt with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherText = cipher.doFinal(plainData)

        // Concatenate: salt + iv + ciphertext (which includes GCM tag)
        val output = ByteArray(salt.size + iv.size + cipherText.size)
        System.arraycopy(salt, 0, output, 0, salt.size)
        System.arraycopy(iv, 0, output, salt.size, iv.size)
        System.arraycopy(cipherText, 0, output, salt.size + iv.size, cipherText.size)

        return output
    }

    /**
     * Decrypts data that was encrypted with [encrypt].
     *
     * @throws javax.crypto.AEADBadTagException if password is wrong.
     */
    private fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        if (encryptedData.size < SALT_LENGTH + GCM_IV_LENGTH + 16) {
            throw IllegalArgumentException("Encrypted data is too short — file may be corrupted.")
        }

        // Extract salt
        val salt = encryptedData.copyOfRange(0, SALT_LENGTH)

        // Extract IV
        val iv = encryptedData.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)

        // Extract ciphertext (includes GCM auth tag)
        val cipherText = encryptedData.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, encryptedData.size)

        // Derive key
        val key = deriveKey(password, salt)

        // Decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(cipherText)
    }

    /**
     * Derives a 256-bit key from a password using PBKDF2-HMAC-SHA256.
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return secretKey.encoded
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Backup Assembly
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds an unencrypted ZIP containing all exportable data as JSON files.
     * Each data table becomes a separate entry in the ZIP for selective restore.
     */
    private fun buildRawBackupZip(): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            // Export each data category
            val dataTables = mapOf(
                "transactions" to exportTransactions(),
                "vault_entries" to exportVaultEntries(),
                "categories" to exportCategories(),
                "settings" to exportSettings(),
                "achievements" to exportAchievements(),
                "streaks" to exportStreaks(),
                "debts" to exportDebts(),
                "goals" to exportGoals(),
                "journal_entries" to exportJournalEntries(),
                "documents" to exportDocuments(),
                "investments" to exportInvestments(),
                "merchant_rules" to exportMerchantRules(),
                "cash_wallets" to exportCashWallets(),
                "groups" to exportGroups(),
                "group_expenses" to exportGroupExpenses(),
                "saved_searches" to exportSavedSearches(),
                "budget_config" to exportBudgetConfig(),
                "recurring_transactions" to exportRecurringTransactions()
            )

            // Write each data table as a JSON file in the ZIP
            for ((name, jsonData) in dataTables) {
                val entry = ZipEntry("data/$name.json")
                zip.putNextEntry(entry)
                zip.write(jsonData.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }

            // Write metadata
            val metadata = buildMetadataJson(dataTables)
            val metaEntry = ZipEntry(METADATA_FILE)
            zip.putNextEntry(metaEntry)
            zip.write(metadata.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    /**
     * Appends a SHA-256 checksum entry to an existing ZIP byte array.
     */
    private fun appendChecksumToZip(zipBytes: ByteArray, checksum: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        buffer.write(zipBytes, 0, zipBytes.size)

        // Re-open zip to append (simplified: rebuild with checksum)
        val rebuilt = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            ZipOutputStream(rebuilt).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    zos.putNextEntry(ZipEntry(entry.name))
                    zis.copyTo(zos)
                    zos.closeEntry()
                    entry = zis.nextEntry
                }
                // Add checksum entry
                zos.putNextEntry(ZipEntry(CHECKSUM_FILE))
                zos.write(checksum.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return rebuilt.toByteArray()
    }

    /**
     * Computes SHA-256 hex digest of arbitrary data.
     */
    private fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Restore Logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Core restore logic after reading raw encrypted bytes.
     */
    private fun performRestore(
        encryptedBytes: ByteArray,
        password: String,
        restoreType: RestoreType
    ): RestoreResult {
        // Decrypt
        val decryptedZip: ByteArray
        try {
            decryptedZip = decrypt(encryptedBytes, password)
        } catch (e: Exception) {
            return RestoreResult(
                success = false,
                errors = listOf("Decryption failed. Wrong password or corrupted file.")
            )
        }

        // Verify integrity
        val integrityResult = verifyIntegrity(decryptedZip)
        if (!integrityResult.first) {
            return RestoreResult(
                success = false,
                errors = listOf("Integrity check failed: ${integrityResult.second}")
            )
        }

        // Parse metadata for version migration
        val metadata = extractMetadataFromZip(decryptedZip)
        val warnings = mutableListOf<String>()

        if (metadata != null && metadata.formatVersion > CURRENT_BACKUP_FORMAT_VERSION) {
            return RestoreResult(
                success = false,
                errors = listOf("This backup was made with a newer app version. Please update the app first.")
            )
        }

        if (metadata != null && metadata.formatVersion < CURRENT_BACKUP_FORMAT_VERSION) {
            warnings.add("Backup from older version (v${metadata.formatVersion}). Schema migration applied.")
        }

        // Extract and restore data based on restoreType
        val recordsRestored = mutableMapOf<String, Int>()
        val errors = mutableListOf<String>()

        ZipInputStream(ByteArrayInputStream(decryptedZip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("data/") && entry.name.endsWith(".json")) {
                    val tableName = entry.name.removePrefix("data/").removeSuffix(".json")
                    val jsonData = zis.readBytes().toString(StandardCharsets.UTF_8)

                    if (shouldRestore(tableName, restoreType)) {
                        try {
                            val count = restoreTable(tableName, jsonData, metadata?.formatVersion ?: 1)
                            recordsRestored[tableName] = count
                        } catch (e: Exception) {
                            errors.add("Failed to restore '$tableName': ${e.message}")
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }

        return RestoreResult(
            success = errors.isEmpty(),
            recordsRestored = recordsRestored,
            warnings = warnings,
            errors = errors
        )
    }

    /**
     * Determines whether a given table should be restored based on [RestoreType].
     */
    private fun shouldRestore(tableName: String, restoreType: RestoreType): Boolean {
        return when (restoreType) {
            RestoreType.FULL -> true
            RestoreType.TRANSACTIONS_ONLY -> tableName in listOf(
                "transactions", "categories", "recurring_transactions", "merchant_rules"
            )
            RestoreType.VAULT_ONLY -> tableName in listOf("vault_entries", "documents")
            RestoreType.SETTINGS_ONLY -> tableName in listOf(
                "settings", "budget_config", "saved_searches"
            )
        }
    }

    /**
     * Restores a single data table from JSON. Handles schema migration if needed.
     *
     * @return Number of records restored.
     */
    private fun restoreTable(tableName: String, jsonData: String, sourceVersion: Int): Int {
        // Apply migration if backup is from an older format version
        val migratedJson = if (sourceVersion < CURRENT_BACKUP_FORMAT_VERSION) {
            migrateTableData(tableName, jsonData, sourceVersion, CURRENT_BACKUP_FORMAT_VERSION)
        } else {
            jsonData
        }

        // In a real implementation, this would use Room DAO to insert records.
        // Each table handler parses JSON array and bulk-inserts into the database.
        return when (tableName) {
            "transactions" -> restoreTransactions(migratedJson)
            "vault_entries" -> restoreVaultEntries(migratedJson)
            "categories" -> restoreCategories(migratedJson)
            "settings" -> restoreSettings(migratedJson)
            "achievements" -> restoreAchievements(migratedJson)
            "streaks" -> restoreStreaks(migratedJson)
            "debts" -> restoreDebts(migratedJson)
            "goals" -> restoreGoals(migratedJson)
            "journal_entries" -> restoreJournalEntries(migratedJson)
            "documents" -> restoreDocuments(migratedJson)
            "investments" -> restoreInvestments(migratedJson)
            "merchant_rules" -> restoreMerchantRules(migratedJson)
            "cash_wallets" -> restoreCashWallets(migratedJson)
            "groups" -> restoreGroups(migratedJson)
            "group_expenses" -> restoreGroupExpenses(migratedJson)
            "saved_searches" -> restoreSavedSearches(migratedJson)
            "budget_config" -> restoreBudgetConfig(migratedJson)
            "recurring_transactions" -> restoreRecurringTransactions(migratedJson)
            else -> 0
        }
    }

    /**
     * Migrates table JSON data from one schema version to another.
     * Handles field additions, renames, type changes, etc.
     */
    private fun migrateTableData(
        tableName: String,
        jsonData: String,
        fromVersion: Int,
        toVersion: Int
    ): String {
        var data = jsonData
        // Apply migrations sequentially
        for (version in fromVersion until toVersion) {
            data = applyMigration(tableName, data, version, version + 1)
        }
        return data
    }

    /**
     * Applies a single version migration step to table data.
     */
    private fun applyMigration(
        tableName: String,
        jsonData: String,
        fromVersion: Int,
        toVersion: Int
    ): String {
        // Migration logic would be version-specific.
        // Example: Version 0→1 might add a "currency" field with default "INR"
        // For now, return data unchanged — migrations will be added as the schema evolves.
        return jsonData
    }

    /**
     * Verifies the integrity of a decrypted ZIP by checking the embedded checksum.
     *
     * @return Pair of (isValid, errorMessage)
     */
    private fun verifyIntegrity(zipBytes: ByteArray): Pair<Boolean, String> {
        var storedChecksum: String? = null

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == CHECKSUM_FILE) {
                    storedChecksum = zis.readBytes().toString(StandardCharsets.UTF_8)
                    break
                }
                entry = zis.nextEntry
            }
        }

        if (storedChecksum == null) {
            // Older backups might not have checksum — warn but allow
            return Pair(true, "No checksum found (older format) — skipping integrity check.")
        }

        // Rebuild ZIP without checksum entry and compare hash
        val withoutChecksum = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            ZipOutputStream(withoutChecksum).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name != CHECKSUM_FILE) {
                        zos.putNextEntry(ZipEntry(entry.name))
                        zis.copyTo(zos)
                        zos.closeEntry()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        val computedChecksum = computeSha256(withoutChecksum.toByteArray())
        return if (computedChecksum == storedChecksum) {
            Pair(true, "")
        } else {
            Pair(false, "Checksum mismatch — data may be corrupted.")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metadata Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts [BackupMetadata] from the raw (decrypted) ZIP bytes.
     */
    private fun extractMetadataFromZip(zipBytes: ByteArray): BackupMetadata? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == METADATA_FILE) {
                    val metaJson = zis.readBytes().toString(StandardCharsets.UTF_8)
                    return parseMetadataJson(metaJson)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    /**
     * Builds metadata JSON string from the data tables map.
     */
    private fun buildMetadataJson(dataTables: Map<String, String>): String {
        val recordCounts = mutableMapOf<String, Int>()
        var totalSize = 0L

        for ((name, json) in dataTables) {
            // Count records (simplistic: count array elements)
            val count = json.split("},{").size
            recordCounts[name] = if (json == "[]") 0 else count
            totalSize += json.length
        }

        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                @Suppress("DEPRECATION")
                it.versionCode
            }
        } catch (e: Exception) {
            1
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        // Build JSON manually to avoid external library dependency
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"appVersion\":$appVersion,")
        sb.append("\"backupDate\":\"${dateFormat.format(Date())}\",")
        sb.append("\"formatVersion\":$CURRENT_BACKUP_FORMAT_VERSION,")
        sb.append("\"encryptionMethod\":\"AES-256-GCM + PBKDF2-HMAC-SHA256\",")
        sb.append("\"totalSizeBytes\":$totalSize,")
        sb.append("\"recordCounts\":{")
        sb.append(recordCounts.entries.joinToString(",") { "\"${it.key}\":${it.value}" })
        sb.append("}}")

        return sb.toString()
    }

    /**
     * Parses metadata JSON string into [BackupMetadata].
     */
    private fun parseMetadataJson(json: String): BackupMetadata? {
        return try {
            // Simple JSON parsing without external dependencies
            val appVersion = extractJsonInt(json, "appVersion") ?: 1
            val backupDate = extractJsonString(json, "backupDate") ?: ""
            val formatVersion = extractJsonInt(json, "formatVersion") ?: 1
            val encryptionMethod = extractJsonString(json, "encryptionMethod") ?: "unknown"
            val totalSizeBytes = extractJsonLong(json, "totalSizeBytes") ?: 0L

            // Parse recordCounts map
            val recordCountsStr = json.substringAfter("\"recordCounts\":{").substringBefore("}")
            val recordCounts = mutableMapOf<String, Int>()
            if (recordCountsStr.isNotEmpty()) {
                val pairs = recordCountsStr.split(",")
                for (pair in pairs) {
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim().removeSurrounding("\"")
                        val value = parts[1].trim().toIntOrNull() ?: 0
                        recordCounts[key] = value
                    }
                }
            }

            BackupMetadata(
                appVersion = appVersion,
                backupDate = backupDate,
                recordCounts = recordCounts,
                totalSizeBytes = totalSizeBytes,
                encryptionMethod = encryptionMethod,
                formatVersion = formatVersion
            )
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON Parsing Helpers (no external dependency)
    // ─────────────────────────────────────────────────────────────────────────

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\":\"([^\"]*)\""
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val pattern = "\"$key\":(\\d+)"
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = "\"$key\":(\\d+)"
        val match = Regex(pattern).find(json)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Export Stubs (to be wired to Room DAOs)
    // ─────────────────────────────────────────────────────────────────────────
    // In production, these would query the Room database and serialize to JSON.

    private fun exportTransactions(): String = queryAndSerialize("transactions")
    private fun exportVaultEntries(): String = queryAndSerialize("vault_entries")
    private fun exportCategories(): String = queryAndSerialize("categories")
    private fun exportSettings(): String = queryAndSerialize("settings")
    private fun exportAchievements(): String = queryAndSerialize("achievements")
    private fun exportStreaks(): String = queryAndSerialize("streaks")
    private fun exportDebts(): String = queryAndSerialize("debts")
    private fun exportGoals(): String = queryAndSerialize("goals")
    private fun exportJournalEntries(): String = queryAndSerialize("journal_entries")
    private fun exportDocuments(): String = queryAndSerialize("documents")
    private fun exportInvestments(): String = queryAndSerialize("investments")
    private fun exportMerchantRules(): String = queryAndSerialize("merchant_rules")
    private fun exportCashWallets(): String = queryAndSerialize("cash_wallets")
    private fun exportGroups(): String = queryAndSerialize("groups")
    private fun exportGroupExpenses(): String = queryAndSerialize("group_expenses")
    private fun exportSavedSearches(): String = queryAndSerialize("saved_searches")
    private fun exportBudgetConfig(): String = queryAndSerialize("budget_config")
    private fun exportRecurringTransactions(): String = queryAndSerialize("recurring_transactions")

    /**
     * Placeholder for Room DB query + JSON serialization.
     * In production, inject the AppDatabase and call appropriate DAO methods.
     */
    private fun queryAndSerialize(tableName: String): String {
        // TODO: Wire to Room database
        // Example: val records = appDatabase.transactionDao().getAll()
        // return gson.toJson(records)
        return "[]"
    }

    private fun collectRecordCounts(): Map<String, Int> {
        // TODO: Wire to Room database to get COUNT(*) for each table
        return mapOf(
            "transactions" to 0,
            "vault_entries" to 0,
            "categories" to 0,
            "settings" to 0,
            "achievements" to 0,
            "streaks" to 0,
            "debts" to 0,
            "goals" to 0,
            "journal_entries" to 0,
            "documents" to 0,
            "investments" to 0,
            "merchant_rules" to 0,
            "cash_wallets" to 0,
            "groups" to 0,
            "group_expenses" to 0
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data Restore Stubs (to be wired to Room DAOs)
    // ─────────────────────────────────────────────────────────────────────────
    // In production, these would clear the table and bulk-insert from JSON.

    private fun restoreTransactions(json: String): Int = deserializeAndInsert("transactions", json)
    private fun restoreVaultEntries(json: String): Int = deserializeAndInsert("vault_entries", json)
    private fun restoreCategories(json: String): Int = deserializeAndInsert("categories", json)
    private fun restoreSettings(json: String): Int = deserializeAndInsert("settings", json)
    private fun restoreAchievements(json: String): Int = deserializeAndInsert("achievements", json)
    private fun restoreStreaks(json: String): Int = deserializeAndInsert("streaks", json)
    private fun restoreDebts(json: String): Int = deserializeAndInsert("debts", json)
    private fun restoreGoals(json: String): Int = deserializeAndInsert("goals", json)
    private fun restoreJournalEntries(json: String): Int = deserializeAndInsert("journal_entries", json)
    private fun restoreDocuments(json: String): Int = deserializeAndInsert("documents", json)
    private fun restoreInvestments(json: String): Int = deserializeAndInsert("investments", json)
    private fun restoreMerchantRules(json: String): Int = deserializeAndInsert("merchant_rules", json)
    private fun restoreCashWallets(json: String): Int = deserializeAndInsert("cash_wallets", json)
    private fun restoreGroups(json: String): Int = deserializeAndInsert("groups", json)
    private fun restoreGroupExpenses(json: String): Int = deserializeAndInsert("group_expenses", json)
    private fun restoreSavedSearches(json: String): Int = deserializeAndInsert("saved_searches", json)
    private fun restoreBudgetConfig(json: String): Int = deserializeAndInsert("budget_config", json)
    private fun restoreRecurringTransactions(json: String): Int = deserializeAndInsert("recurring_transactions", json)

    /**
     * Placeholder for JSON deserialization + Room bulk insert.
     * In production, would parse the JSON array and use @Insert(onConflict = REPLACE).
     *
     * @return Number of records inserted.
     */
    private fun deserializeAndInsert(tableName: String, json: String): Int {
        if (json == "[]" || json.isBlank()) return 0
        // TODO: Wire to Room database
        // Example:
        // val records: List<TransactionEntity> = gson.fromJson(json, typeToken)
        // appDatabase.transactionDao().deleteAll()
        // appDatabase.transactionDao().insertAll(records)
        // return records.size
        return 0
    }
}
