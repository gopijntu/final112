package com.gopi.securevault.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gopi.securevault.data.db.AppDatabase
import com.gopi.securevault.databinding.ActivityPasswordResetBinding
import com.gopi.securevault.util.CryptoPrefs
import com.gopi.securevault.util.PasswordUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sqlcipher.database.SQLiteDatabase

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordResetBinding
    private lateinit var prefs: CryptoPrefs

    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordResetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        prefs = CryptoPrefs(this)

        // Check if security questions exist
        val a1hash = prefs.getString("a1_hash", null)
        val a2hash = prefs.getString("a2_hash", null)
        val salt1 = prefs.getString("a1_salt", null)
        val salt2 = prefs.getString("a2_salt", null)

        if (a1hash.isNullOrEmpty() || a2hash.isNullOrEmpty() ||
            salt1.isNullOrEmpty() || salt2.isNullOrEmpty()) {
            Toast.makeText(this, "Security questions not set. Cannot reset password.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.tvQ1.text = prefs.getString("q1", "Question 1")
        binding.tvQ2.text = prefs.getString("q2", "Question 2")

        // Make all text & outlines visible
        forceVisibleInputs()

        binding.btnSubmit.setOnClickListener {
            val a1 = binding.etAnswer1.text.toString().trim()
            val a2 = binding.etAnswer2.text.toString().trim()
            val n1 = binding.etNewPassword.text.toString().trim()
            val n2 = binding.etConfirmPassword.text.toString().trim()

            if (n1 != n2) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!PasswordUtils.isPasswordValid(n1)) {
                Toast.makeText(this, "Password must be at least 8 characters with letters, numbers, and symbols.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val h1 = PasswordUtils.hashWithSalt(a1, salt1)
            val h2 = PasswordUtils.hashWithSalt(a2, salt2)

            if (h1 == a1hash && h2 == a2hash) {
                // ✅ Correct answers: change password
                val oldPassword = prefs.getString("master_hash", null) ?: "fallback-key"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppDatabase.changeDatabasePassword(this@PasswordResetActivity, oldPassword, n1)

                        // Update new salts and hashes
                        val newSalt = PasswordUtils.generateSalt()
                        val newHash = PasswordUtils.hashWithSalt(n1, newSalt)
                        prefs.putString("salt", newSalt)
                        prefs.putString("master_hash", newHash)

                        runOnUiThread {
                            Toast.makeText(this@PasswordResetActivity, "Password reset successfully", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@PasswordResetActivity, LoginActivity::class.java))
                            finishAffinity()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(this@PasswordResetActivity, "Error resetting password", Toast.LENGTH_LONG).show()
                        }
                    }
                }

            } else {
                // ❌ Wrong answers
                attempts++
                if (attempts >= 5) {
                    Toast.makeText(this, "Too many wrong attempts. Try again in 5 seconds.", Toast.LENGTH_LONG).show()
                    binding.btnSubmit.isEnabled = false
                    binding.btnSubmit.postDelayed({
                        binding.btnSubmit.isEnabled = true
                        attempts = 0
                    }, 5000)
                } else {
                    Toast.makeText(this, "Reset failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun forceVisibleInputs() {
        val textInputs = listOf(
            binding.tilAnswer1,
            binding.tilAnswer2,
            binding.tilNewPassword,
            binding.tilConfirmPassword
        )

        for (til in textInputs) {
            til.setBoxStrokeColorStateList(
                android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))
            )
            til.hintTextColor = android.content.res.ColorStateList.valueOf(getColor(android.R.color.darker_gray))
            til.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            til.setEndIconTintList(android.content.res.ColorStateList.valueOf(getColor(android.R.color.white)))
        }

        val edits = listOf(
            binding.etAnswer1,
            binding.etAnswer2,
            binding.etNewPassword,
            binding.etConfirmPassword
        )

        for (edit in edits) {
            edit.setTextColor(getColor(android.R.color.white))
            edit.setHintTextColor(getColor(android.R.color.darker_gray))
        }
    }
}
