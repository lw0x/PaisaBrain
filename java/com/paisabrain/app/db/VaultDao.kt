package com.paisabrain.app.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VaultEntry): Long

    @Delete
    suspend fun delete(entry: VaultEntry)

    @Query("SELECT * FROM vault_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE content_text LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE type = :type ORDER BY timestamp DESC")
    fun getByType(type: VaultEntryType): Flow<List<VaultEntry>>

    @Query("SELECT COUNT(*) FROM vault_entries")
    fun getEntryCount(): Flow<Int>

    @Query("SELECT * FROM vault_entries WHERE tags LIKE '%' || :tag || '%' ORDER BY timestamp DESC")
    fun getByTag(tag: String): Flow<List<VaultEntry>>
}
