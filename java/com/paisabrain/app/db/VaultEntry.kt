package com.paisabrain.app.db

import androidx.room.*

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: VaultEntryType,
    @ColumnInfo(name = "title") val title: String, // Auto-generated or user-given
    @ColumnInfo(name = "content_text") val contentText: String, // OCR text / transcription / note
    @ColumnInfo(name = "file_path") val filePath: String? = null, // Photo/audio file path
    @ColumnInfo(name = "tags") val tags: String = "", // Comma-separated auto-tags
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "embedding") val embedding: String? = null // Serialized vector for search
)

enum class VaultEntryType {
    PHOTO, VOICE, TEXT, SCREENSHOT
}
