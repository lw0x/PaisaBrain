package com.paisabrain.app.db

import android.content.Context
import androidx.room.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Transaction::class, VaultEntry::class, Category::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PaisaBrainDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun vaultDao(): VaultDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: PaisaBrainDatabase? = null

        fun getInstance(context: Context): PaisaBrainDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PaisaBrainDatabase::class.java,
                    "paisa_brain_encrypted.db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }

        private fun getOrCreatePassphrase(context: Context): String {
            val prefs = context.getSharedPreferences("pb_secure", Context.MODE_PRIVATE)
            var passphrase = prefs.getString("db_key", null)
            if (passphrase == null) {
                passphrase = java.util.UUID.randomUUID().toString() +
                    System.currentTimeMillis().toString()
                prefs.edit().putString("db_key", passphrase).apply()
            }
            return passphrase
        }
    }
}
