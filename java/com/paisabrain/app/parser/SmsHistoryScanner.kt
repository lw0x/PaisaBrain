package com.paisabrain.app.parser

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * SmsHistoryScanner - Reads all existing SMS on the phone (with READ_SMS permission),
 * filters for financial transactions, parses them, and bulk-inserts into the database.
 *
 * Designed for use on first app launch or when user requests a re-scan.
 *
 * Features:
 * - Progress reporting via Flow
 * - Cancellation support
 * - Duplicate detection (won't re-insert already parsed SMS)
 * - Batch database insertion for performance
 * - Memory-efficient cursor-based reading
 *
 * Required permissions:
 * ```xml
 * <uses-permission android:name="android.permission.READ_SMS" />
 * ```
 *
 * Usage:
 * ```kotlin
 * val scanner = SmsHistoryScanner(context)
 * scanner.scanAndParse().collect { progress ->
 *     updateProgressUI(progress)
 * }
 * ```
 */
class SmsHistoryScanner(private val context: Context) {

    companion object {
        private const val TAG = "PaisaBrain.SmsScanner"
        private const val BATCH_INSERT_SIZE = 50
        private const val SMS_CONTENT_URI = "content://sms/inbox"

        // Maximum age of SMS to scan (default: 1 year)
        private const val DEFAULT_MAX_AGE_DAYS = 365L
        private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L
    }

    private val parser = SmsParser()
    private val categoryClassifier = CategoryClassifier()
    private val isCancelled = AtomicBoolean(false)

    // ═══════════════════════════════════════════════════════════════════════════
    // DATA MODELS
    // ═══════════════════════════════════════════════════════════════════════════

    data class ScanProgress(
        val phase: ScanPhase,
        val totalMessages: Int,
        val processedMessages: Int,
        val parsedTransactions: Int,
        val currentMessage: String? = null,
        val isComplete: Boolean = false,
        val error: String? = null
    ) {
        val progressPercent: Float
            get() = if (totalMessages > 0) {
                (processedMessages.toFloat() / totalMessages) * 100f
            } else 0f
    }

    enum class ScanPhase {
        COUNTING,       // Counting total financial SMS
        READING,        // Reading SMS from content provider
        PARSING,        // Parsing financial transactions
        SAVING,         // Saving to database
        COMPLETE,       // Done
        ERROR           // An error occurred
    }

    data class ScanResult(
        val totalSmsRead: Int,
        val financialSmsFound: Int,
        val transactionsParsed: Int,
        val transactionsSaved: Int,
        val duplicatesSkipped: Int,
        val durationMillis: Long
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scan SMS history and parse transactions, emitting progress updates.
     *
     * @param maxAgeDays Maximum age of SMS to process (default: 365 days)
     * @return Flow of ScanProgress updates
     */
    fun scanAndParse(maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS): Flow<ScanProgress> = flow {
        val startTime = System.currentTimeMillis()
        val cutoffTime = startTime - (maxAgeDays * MILLIS_PER_DAY)

        isCancelled.set(false)

        val totalParsed = AtomicInteger(0)
        val totalSaved = AtomicInteger(0)
        val duplicatesSkipped = AtomicInteger(0)
        var totalRead = 0
        var financialCount = 0

        try {
            // Phase 1: Count financial SMS
            emit(ScanProgress(
                phase = ScanPhase.COUNTING,
                totalMessages = 0,
                processedMessages = 0,
                parsedTransactions = 0
            ))

            val financialSmsMessages = readFinancialSms(cutoffTime)
            financialCount = financialSmsMessages.size
            totalRead = financialCount // We only read financial ones after filtering

            Log.i(TAG, "Found $financialCount financial SMS messages to process")

            if (financialCount == 0) {
                emit(ScanProgress(
                    phase = ScanPhase.COMPLETE,
                    totalMessages = 0,
                    processedMessages = 0,
                    parsedTransactions = 0,
                    isComplete = true
                ))
                return@flow
            }

            // Phase 2 & 3: Parse and save in batches
            val batch = mutableListOf<TransactionEntity>()
            var processed = 0

            for (smsData in financialSmsMessages) {
                if (isCancelled.get()) {
                    Log.i(TAG, "Scan cancelled by user after processing $processed messages")
                    break
                }

                yield() // Allow coroutine cancellation

                processed++

                // Emit progress every 10 messages
                if (processed % 10 == 0 || processed == financialCount) {
                    emit(ScanProgress(
                        phase = ScanPhase.PARSING,
                        totalMessages = financialCount,
                        processedMessages = processed,
                        parsedTransactions = totalParsed.get(),
                        currentMessage = smsData.body.take(50) + "..."
                    ))
                }

                // Parse the SMS
                val transaction = try {
                    parser.parse(smsData.body, smsData.sender, smsData.timestamp)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing SMS: ${e.message}")
                    null
                }

                if (transaction == null) continue

                totalParsed.incrementAndGet()

                // Classify category
                val category = categoryClassifier.classify(
                    merchantName = transaction.merchantName,
                    transactionMode = transaction.transactionMode,
                    body = smsData.body
                )

                // Create entity
                val entity = TransactionEntity(
                    id = 0,
                    amount = transaction.amount,
                    type = transaction.type.name,
                    merchantName = transaction.merchantName,
                    bankName = transaction.bankName,
                    accountLastFour = transaction.accountLastFour,
                    balanceAfterTransaction = transaction.balanceAfterTransaction,
                    transactionMode = transaction.transactionMode.name,
                    category = category,
                    referenceNumber = transaction.referenceNumber,
                    timestamp = transaction.timestamp,
                    rawSmsBody = transaction.rawSmsBody,
                    senderId = transaction.senderId,
                    confidence = transaction.confidence,
                    isManuallyEdited = false,
                    createdAt = System.currentTimeMillis()
                )

                batch.add(entity)

                // Bulk insert when batch is full
                if (batch.size >= BATCH_INSERT_SIZE) {
                    val saved = saveBatch(batch, duplicatesSkipped)
                    totalSaved.addAndGet(saved)
                    batch.clear()

                    emit(ScanProgress(
                        phase = ScanPhase.SAVING,
                        totalMessages = financialCount,
                        processedMessages = processed,
                        parsedTransactions = totalParsed.get()
                    ))
                }
            }

            // Save remaining batch
            if (batch.isNotEmpty()) {
                val saved = saveBatch(batch, duplicatesSkipped)
                totalSaved.addAndGet(saved)
                batch.clear()
            }

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Scan complete: $totalRead SMS read, $financialCount financial, " +
                "${totalParsed.get()} parsed, ${totalSaved.get()} saved, " +
                "${duplicatesSkipped.get()} duplicates, ${duration}ms")

            emit(ScanProgress(
                phase = ScanPhase.COMPLETE,
                totalMessages = financialCount,
                processedMessages = processed,
                parsedTransactions = totalParsed.get(),
                isComplete = true
            ))

        } catch (e: SecurityException) {
            Log.e(TAG, "SMS permission not granted", e)
            emit(ScanProgress(
                phase = ScanPhase.ERROR,
                totalMessages = financialCount,
                processedMessages = totalRead,
                parsedTransactions = totalParsed.get(),
                error = "SMS read permission not granted. Please enable in Settings.",
                isComplete = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error during SMS scan", e)
            emit(ScanProgress(
                phase = ScanPhase.ERROR,
                totalMessages = financialCount,
                processedMessages = totalRead,
                parsedTransactions = totalParsed.get(),
                error = "Error scanning SMS: ${e.message}",
                isComplete = true
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Quick scan that returns a result without progress updates.
     * Useful for background sync.
     */
    suspend fun quickScan(maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS): ScanResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cutoffTime = startTime - (maxAgeDays * MILLIS_PER_DAY)
            var totalRead = 0
            var financialCount = 0
            var parsed = 0
            var saved = 0
            var duplicates = 0

            try {
                val financialSms = readFinancialSms(cutoffTime)
                financialCount = financialSms.size
                totalRead = financialCount

                val batch = mutableListOf<TransactionEntity>()
                val duplicatesCounter = AtomicInteger(0)

                for (smsData in financialSms) {
                    if (isCancelled.get()) break

                    val transaction = try {
                        parser.parse(smsData.body, smsData.sender, smsData.timestamp)
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    parsed++
                    val category = categoryClassifier.classify(
                        merchantName = transaction.merchantName,
                        transactionMode = transaction.transactionMode,
                        body = smsData.body
                    )

                    batch.add(TransactionEntity(
                        id = 0,
                        amount = transaction.amount,
                        type = transaction.type.name,
                        merchantName = transaction.merchantName,
                        bankName = transaction.bankName,
                        accountLastFour = transaction.accountLastFour,
                        balanceAfterTransaction = transaction.balanceAfterTransaction,
                        transactionMode = transaction.transactionMode.name,
                        category = category,
                        referenceNumber = transaction.referenceNumber,
                        timestamp = transaction.timestamp,
                        rawSmsBody = transaction.rawSmsBody,
                        senderId = transaction.senderId,
                        confidence = transaction.confidence,
                        isManuallyEdited = false,
                        createdAt = System.currentTimeMillis()
                    ))

                    if (batch.size >= BATCH_INSERT_SIZE) {
                        saved += saveBatch(batch, duplicatesCounter)
                        batch.clear()
                    }
                }

                if (batch.isNotEmpty()) {
                    saved += saveBatch(batch, duplicatesCounter)
                }

                duplicates = duplicatesCounter.get()
            } catch (e: Exception) {
                Log.e(TAG, "Error in quickScan", e)
            }

            ScanResult(
                totalSmsRead = totalRead,
                financialSmsFound = financialCount,
                transactionsParsed = parsed,
                transactionsSaved = saved,
                duplicatesSkipped = duplicates,
                durationMillis = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Cancel an ongoing scan.
     */
    fun cancel() {
        isCancelled.set(true)
        Log.i(TAG, "Scan cancellation requested")
    }

    /**
     * Get count of financial SMS without fully parsing them.
     * Useful for showing user what will be processed.
     */
    suspend fun getFinancialSmsCount(maxAgeDays: Long = DEFAULT_MAX_AGE_DAYS): Int {
        return withContext(Dispatchers.IO) {
            val cutoffTime = System.currentTimeMillis() - (maxAgeDays * MILLIS_PER_DAY)
            countFinancialSms(cutoffTime)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE - SMS READING
    // ═══════════════════════════════════════════════════════════════════════════

    private data class RawSms(
        val body: String,
        val sender: String,
        val timestamp: Long
    )

    /**
     * Read all financial SMS from the content provider.
     * Uses the pre-filter to only return likely financial messages.
     */
    private fun readFinancialSms(cutoffTime: Long): List<RawSms> {
        val results = mutableListOf<RawSms>()
        val contentResolver: ContentResolver = context.contentResolver

        val uri = Uri.parse(SMS_CONTENT_URI)
        val projection = arrayOf(
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        // Only read inbox messages newer than cutoff
        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(cutoffTime.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

            if (cursor == null) {
                Log.w(TAG, "SMS query returned null cursor")
                return results
            }

            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            Log.d(TAG, "Total SMS in inbox: ${cursor.count}")

            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex) ?: continue
                val sender = cursor.getString(addressIndex) ?: continue
                val timestamp = cursor.getLong(dateIndex)

                // Pre-filter: only keep financial SMS
                if (SmsBankPatterns.isLikelyFinancialSms(body, sender)) {
                    results.add(RawSms(body = body, sender = sender, timestamp = timestamp))
                }
            }

            Log.d(TAG, "Financial SMS found: ${results.size} out of ${cursor.count} total")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading SMS", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS", e)
        } finally {
            cursor?.close()
        }

        return results
    }

    /**
     * Count financial SMS without loading full body (lighter operation).
     */
    private fun countFinancialSms(cutoffTime: Long): Int {
        val contentResolver: ContentResolver = context.contentResolver
        val uri = Uri.parse(SMS_CONTENT_URI)
        val projection = arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS)

        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(cutoffTime.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())

        var count = 0
        var cursor: Cursor? = null

        try {
            cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor == null) return 0

            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)

            while (cursor.moveToNext()) {
                val body = cursor.getString(bodyIndex) ?: continue
                val sender = cursor.getString(addressIndex) ?: continue
                if (SmsBankPatterns.isLikelyFinancialSms(body, sender)) {
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting SMS", e)
        } finally {
            cursor?.close()
        }

        return count
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE - DATABASE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Save a batch of transactions, skipping duplicates.
     * Returns the number of actually saved (non-duplicate) transactions.
     */
    private suspend fun saveBatch(
        batch: List<TransactionEntity>,
        duplicatesCounter: AtomicInteger
    ): Int {
        val db = TransactionDatabase.getInstance(context)
        val dao = db.transactionDao()

        var savedCount = 0

        for (entity in batch) {
            try {
                // Check for duplicates based on raw SMS body
                val existing = dao.findByRawBody(entity.rawSmsBody)
                if (existing != null) {
                    duplicatesCounter.incrementAndGet()
                    continue
                }

                dao.insert(entity)
                savedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Error saving transaction: ${e.message}")
            }
        }

        return savedCount
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if SMS read permission is granted.
     */
    fun hasReadSmsPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.READ_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the timestamp of the most recent transaction in the database.
     * Useful for incremental scanning (only scan newer SMS).
     */
    suspend fun getLastScannedTimestamp(): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val db = TransactionDatabase.getInstance(context)
                val dao = db.transactionDao()
                val transactions = dao.getAllTransactions()
                transactions.maxOfOrNull { it.timestamp }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Incremental scan: only processes SMS newer than the last stored transaction.
     */
    fun incrementalScan(): Flow<ScanProgress> = flow {
        val lastTimestamp = getLastScannedTimestamp()

        if (lastTimestamp != null) {
            val daysSinceLastScan = (System.currentTimeMillis() - lastTimestamp) / MILLIS_PER_DAY
            if (daysSinceLastScan < 1) {
                emit(ScanProgress(
                    phase = ScanPhase.COMPLETE,
                    totalMessages = 0,
                    processedMessages = 0,
                    parsedTransactions = 0,
                    isComplete = true
                ))
                return@flow
            }

            // Scan only messages newer than the last stored one
            scanAndParse(maxAgeDays = daysSinceLastScan + 1).collect { emit(it) }
        } else {
            // No previous data - do full scan
            scanAndParse().collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)
}
