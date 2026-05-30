package com.paisabrain.app.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromVaultEntryType(type: VaultEntryType): String = type.name

    @TypeConverter
    fun toVaultEntryType(value: String): VaultEntryType = VaultEntryType.valueOf(value)
}
