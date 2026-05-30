package com.paisabrain.app.db

import androidx.room.*

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "keywords") val keywords: String, // comma-separated
    @ColumnInfo(name = "budget_limit") val budgetLimit: Double = 0.0,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false
)
