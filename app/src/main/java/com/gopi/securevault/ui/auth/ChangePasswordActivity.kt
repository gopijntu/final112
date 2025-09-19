package com.gopi.securevault.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.gopi.securevault.ui.BaseActivity
import com.gopi.securevault.databinding.ActivityChangePasswordBinding
import com.gopi.securevault.data.db.AppDatabase
import com.gopi.securevault.util.CryptoPrefs
import com.gopi.securevault.util.PasswordUtils
import net.sqlcipher.database.SQLiteDatabase

class ChangePasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var prefs: CryptoPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = CryptoPrefs(this)

        binding.btnSave.setOnClickListener {
            val oldPassword = binding.etOldPassword.text.toString()
            val newPassword = binding.etNewPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            if (newPassword != confirmPassword) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!PasswordUtils.isPasswordValid(newPassword)) {
                Toast.makeText(this, "Password must be at least 8 characters with letters, numbers, and symbols.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val oldHash = prefs.getString("master_hash", null)
            val salt = prefs.getString("salt", null)

            if (oldHash == null || salt == null) {
                Toast.makeText(this, "Could not retrieve current password information.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (PasswordUtils.hashWithSalt(oldPassword, salt) == oldHash) {
                val newSalt = PasswordUtils.generateSalt()
                val newHash = PasswordUtils.hashWithSalt(newPassword, newSalt)

                try {
                    val dbFile = getDatabasePath("securevault.db")
                    if (dbFile.exists()) {
                        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, oldPassword.toCharArray(), null, SQLiteDatabase.OPEN_READWRITE)
                        db.execSQL("PRAGMA rekey = '$newHash';")
                        db.close()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Warning: could not rekey DB. Restore may require old password. Error: ${e.message}", Toast.LENGTH_LONG).show()
                }

                prefs.putString("salt", newSalt)
                prefs.putString("master_hash", newHash)

                // Close the database instance to force re-initialization with the new password
                AppDatabase.closeInstance()

                Toast.makeText(this, "Password changed successfully. Please log in again.", Toast.LENGTH_LONG).show()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Old password is incorrect", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
