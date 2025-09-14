package com.gopi.securevault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gopi.securevault.data.dao.*
import com.gopi.securevault.data.entities.*
import net.sqlcipher.database.SupportFactory
import net.sqlcipher.database.SQLiteDatabase
import com.gopi.securevault.util.CryptoPrefs

@Database(
    entities = [BankEntity::class, CardEntity::class, PolicyEntity::class, AadharEntity::class, PanEntity::class, VoterIdEntity::class, LicenseEntity::class, MiscEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bankDao(): BankDao
    abstract fun cardDao(): CardDao
    abstract fun policyDao(): PolicyDao
    abstract fun aadharDao(): AadharDao
    abstract fun panDao(): PanDao
    abstract fun voterIdDao(): VoterIdDao
    abstract fun licenseDao(): LicenseDao
    abstract fun miscDao(): MiscDao

    suspend fun clearAllTablesManually() {
        clearAllTables()
    }

    companion object {
        const val DATABASE_NAME = "securevault.db"
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val prefs = CryptoPrefs(context)
                val passphrase: CharArray = (prefs.getString("master_hash", null) ?: "fallback-key").toCharArray()
                val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))

                val inst = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = inst
                inst
            }
        }

        /**
         * Re-encrypt the database safely when changing password
         */
        fun changeDatabasePassword(context: Context, oldPassword: String, newPassword: String) {
            // Close current Room instance
            INSTANCE?.close()
            INSTANCE = null

            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                // Open SQLCipher DB with old password
                val db = SQLiteDatabase.openDatabase(
                    dbFile.path,
                    oldPassword.toCharArray(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                db.changePassword(newPassword.toCharArray())
                db.close()
            }

            // Update stored master password
            val prefs = CryptoPrefs(context)
            prefs.putString("master_hash", newPassword)

            // Reinitialize Room
            get(context)
        }

        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
