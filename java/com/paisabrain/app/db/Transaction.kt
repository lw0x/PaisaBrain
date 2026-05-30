package com.paisabrain.app.db

import androidx.room.*

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "type") val type: TransactionType, // CREDIT or DEBIT
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "merchant") val merchant: String,
    @ColumnInfo(name = "bank") val bank: String,
    @ColumnInfo(name = "account_mask") val accountMask: String, // last 4 digits
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "hour_of_day") val hourOfDay: Int,
    @ColumnInfo(name = "is_recurring") val isRecurring: Boolean = false,
    @ColumnInfo(name = "raw_sms") val rawSms: String,
    @ColumnInfo(name = "balance_after") val balanceAfter: Double? = null
)

enum class TransactionType {
    CREDIT, DEBIT
}
