package com.paisabrain.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    fun getByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    fun getByCategory(category: String): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT' AND timestamp >= :startTime")
    fun getTotalSpentSince(startTime: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT' AND timestamp >= :startTime")
    fun getTotalEarnedSince(startTime: Long): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'DEBIT' AND timestamp >= :startTime GROUP BY category ORDER BY total DESC")
    fun getCategoryTotals(startTime: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions WHERE is_recurring = 1 AND type = 'DEBIT' ORDER BY amount DESC")
    fun getRecurringCharges(): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE raw_sms = :sms")
    suspend fun isDuplicate(sms: String): Int

    @Query("SELECT AVG(amount) FROM transactions WHERE type = 'DEBIT' AND timestamp >= :startTime")
    fun getAverageDailySpend(startTime: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE merchant = :merchant ORDER BY timestamp DESC LIMIT 10")
    fun getByMerchant(merchant: String): Flow<List<Transaction>>
}

data class CategoryTotal(
    val category: String,
    val total: Double
)
