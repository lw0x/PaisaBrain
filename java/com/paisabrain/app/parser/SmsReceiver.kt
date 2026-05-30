package com.paisabrain.app.parser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * SmsReceiver - BroadcastReceiver that intercepts incoming SMS messages,
 * filters for bank/financial SMS, parses them, and saves to Room database.
 *
 * Register in AndroidManifest.xml:
 * ```xml
 * <receiver
 *     android:name=".parser.SmsReceiver"
 *     android:exported="true"
 *     android:permission="android.permission.BROADCAST_SMS">
 *     <intent-filter android:priority="999">
 *         <action android:name="android.provider.Telephony.SMS_RECEIVED" />
 *     </intent-filter>
 * </receiver>
 * ```
 *
 * Required permissions:
 * ```xml
 * <uses-permission android:name="android.permission.RECEIVE_SMS" />
 * <uses-permission android:name="android.permission.READ_SMS" />
 * ```
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PaisaBrain.SmsReceiver"
        private const val SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED"
    }

    private val parser = SmsParser()
    private val categoryClassifier = CategoryClassifier()

    // Coroutine scope for background work - uses SupervisorJob so individual failures
    // don't cancel other processing
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != SMS_RECEIVED_ACTION) return

        Log.d(TAG, "SMS received broadcast intercepted")

        try {
            val messages = extractMessages(intent)
            if (messages.isEmpty()) {
                Log.d(TAG, "No messages extracted from intent")
                return
            }

            // Process each message
            for (smsData in messages) {
                processMessage(context, smsData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received SMS", e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MESSAGE EXTRACTION
    // ═══════════════════════════════════════════════════════════════════════════

    data class SmsData(
        val body: String,
        val sender: String,
        val timestamp: Long
    )

    private fun extractMessages(intent: Intent): List<SmsData> {
        val messages = mutableListOf<SmsData>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // API 19+: Use Telephony.Sms.Intents helper
                val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (smsMessages != null) {
                    // Group multi-part messages by sender
                    val grouped = mutableMapOf<String, StringBuilder>()
                    val timestamps = mutableMapOf<String, Long>()

                    for (sms in smsMessages) {
                        val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
                        val body = sms.displayMessageBody ?: sms.messageBody ?: continue
                        val timestamp = sms.timestampMillis

                        grouped.getOrPut(sender) { StringBuilder() }.append(body)
                        timestamps.putIfAbsent(sender, timestamp)
                    }

                    for ((sender, bodyBuilder) in grouped) {
                        messages.add(SmsData(
                            body = bodyBuilder.toString(),
                            sender = sender,
                            timestamp = timestamps[sender] ?: System.currentTimeMillis()
                        ))
                    }
                }
            } else {
                // Legacy extraction using PDU
                @Suppress("DEPRECATION")
                val pdus = intent.extras?.get("pdus") as? Array<*> ?: return messages
                val format = intent.extras?.getString("format")

                val grouped = mutableMapOf<String, StringBuilder>()
                val timestamps = mutableMapOf<String, Long>()

                for (pdu in pdus) {
                    val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format != null) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }

                    val sender = sms.displayOriginatingAddress ?: continue
                    val body = sms.displayMessageBody ?: continue
                    val timestamp = sms.timestampMillis

                    grouped.getOrPut(sender) { StringBuilder() }.append(body)
                    timestamps.putIfAbsent(sender, timestamp)
                }

                for ((sender, bodyBuilder) in grouped) {
                    messages.add(SmsData(
                        body = bodyBuilder.toString(),
                        sender = sender,
                        timestamp = timestamps[sender] ?: System.currentTimeMillis()
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting SMS messages from intent", e)
        }

        return messages
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MESSAGE PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    private fun processMessage(context: Context, smsData: SmsData) {
        // Quick filter: is this a financial SMS?
        if (!parser.isFinancialSms(smsData.body, smsData.sender)) {
            Log.d(TAG, "SMS from ${smsData.sender} is not financial, skipping")
            return
        }

        Log.d(TAG, "Financial SMS detected from ${smsData.sender}, parsing...")

        // Parse the transaction
        val parsedTransaction = parser.parse(smsData.body, smsData.sender, smsData.timestamp)
        if (parsedTransaction == null) {
            Log.w(TAG, "Financial SMS detected but parsing failed: ${smsData.body.take(80)}...")
            return
        }

        Log.i(TAG, "Transaction parsed: ${parsedTransaction.type} ${parsedTransaction.amount} " +
            "from ${parsedTransaction.bankName} to ${parsedTransaction.merchantName}")

        // Classify category
        val category = categoryClassifier.classify(
            merchantName = parsedTransaction.merchantName,
            transactionMode = parsedTransaction.transactionMode,
            body = smsData.body
        )

        Log.d(TAG, "Category classified: $category")

        // Save to database in background
        scope.launch {
            try {
                saveTransaction(context, parsedTransaction, category)
                Log.i(TAG, "Transaction saved to database successfully")

                // Notify UI if app is in foreground
                notifyTransactionReceived(context, parsedTransaction, category)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transaction to database", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DATABASE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private suspend fun saveTransaction(
        context: Context,
        transaction: SmsParser.ParsedTransaction,
        category: String
    ) {
        // Get database instance
        // NOTE: Replace with your actual Room database access
        val db = TransactionDatabase.getInstance(context)
        val dao = db.transactionDao()

        val entity = TransactionEntity(
            id = 0, // Auto-generated
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

        dao.insert(entity)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun notifyTransactionReceived(
        context: Context,
        transaction: SmsParser.ParsedTransaction,
        category: String
    ) {
        // Send local broadcast to update UI
        val notifyIntent = Intent(ACTION_TRANSACTION_RECEIVED).apply {
            putExtra(EXTRA_AMOUNT, transaction.amount)
            putExtra(EXTRA_TYPE, transaction.type.name)
            putExtra(EXTRA_MERCHANT, transaction.merchantName)
            putExtra(EXTRA_BANK, transaction.bankName)
            putExtra(EXTRA_CATEGORY, category)
            putExtra(EXTRA_TIMESTAMP, transaction.timestamp)
            // Use explicit package to comply with Android 14+ restrictions
            setPackage(context.packageName)
        }

        context.sendBroadcast(notifyIntent)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ═══════════════════════════════════════════════════════════════════════════

    object Actions {
        const val TRANSACTION_RECEIVED = "com.paisabrain.app.TRANSACTION_RECEIVED"
    }
}

// Intent action and extra keys for internal broadcasts
const val ACTION_TRANSACTION_RECEIVED = "com.paisabrain.app.TRANSACTION_RECEIVED"
const val EXTRA_AMOUNT = "extra_amount"
const val EXTRA_TYPE = "extra_type"
const val EXTRA_MERCHANT = "extra_merchant"
const val EXTRA_BANK = "extra_bank"
const val EXTRA_CATEGORY = "extra_category"
const val EXTRA_TIMESTAMP = "extra_timestamp"

// ═══════════════════════════════════════════════════════════════════════════════
// ROOM DATABASE ENTITIES & DAO (Companion definitions)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Room Entity for storing parsed transactions.
 * Place in your data/database package and annotate with @Entity.
 *
 * ```kotlin
 * @Entity(tableName = "transactions")
 * ```
 */
data class TransactionEntity(
    // @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "CREDIT" or "DEBIT"
    val merchantName: String?,
    val bankName: String?,
    val accountLastFour: String?,
    val balanceAfterTransaction: Double?,
    val transactionMode: String,
    val category: String,
    val referenceNumber: String?,
    val timestamp: Long,
    val rawSmsBody: String,
    val senderId: String?,
    val confidence: Float,
    val isManuallyEdited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Room DAO interface for transaction operations.
 * Annotate with @Dao.
 */
interface TransactionDao {
    // @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    // @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    // @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    // @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getTransactionsBetween(startTime: Long, endTime: Long): List<TransactionEntity>

    // @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getTransactionsByType(type: String): List<TransactionEntity>

    // @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getTransactionsByCategory(category: String): List<TransactionEntity>

    // @Query("SELECT * FROM transactions WHERE bankName = :bankName ORDER BY timestamp DESC")
    suspend fun getTransactionsByBank(bankName: String): List<TransactionEntity>

    // @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalSpentBetween(startTime: Long, endTime: Long): Double?

    // @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalReceivedBetween(startTime: Long, endTime: Long): Double?

    // @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'DEBIT' AND timestamp BETWEEN :startTime AND :endTime GROUP BY category ORDER BY total DESC")
    suspend fun getSpendingByCategory(startTime: Long, endTime: Long): List<CategoryTotal>

    // @Query("SELECT * FROM transactions WHERE rawSmsBody = :smsBody LIMIT 1")
    suspend fun findByRawBody(smsBody: String): TransactionEntity?

    // @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    // @Update
    suspend fun update(transaction: TransactionEntity)

    // @Delete
    suspend fun delete(transaction: TransactionEntity)
}

data class CategoryTotal(
    val category: String,
    val total: Double
)

/**
 * Room Database class.
 * Annotate with @Database and extend RoomDatabase.
 *
 * ```kotlin
 * @Database(entities = [TransactionEntity::class], version = 1, exportSchema = true)
 * abstract class TransactionDatabase : RoomDatabase() {
 *     abstract fun transactionDao(): TransactionDao
 *     // ...
 * }
 * ```
 */
abstract class TransactionDatabase {
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: TransactionDatabase? = null

        fun getInstance(context: Context): TransactionDatabase {
            return INSTANCE ?: synchronized(this) {
                // In real implementation:
                // val instance = Room.databaseBuilder(
                //     context.applicationContext,
                //     TransactionDatabase::class.java,
                //     "paisa_brain_transactions.db"
                // )
                // .addMigrations(/* migrations */)
                // .build()
                // INSTANCE = instance
                // instance
                throw NotImplementedError(
                    "Replace with actual Room database builder. " +
                    "See Room documentation for proper @Database annotation setup."
                )
            }
        }
    }
}
