package com.gopi.securevault.ui.backup

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.gopi.securevault.ui.BaseActivity
import androidx.lifecycle.lifecycleScope
import com.gopi.securevault.R
import com.gopi.securevault.backup.BackupManager
import com.gopi.securevault.databinding.ActivityBackupRestoreBinding
import com.gopi.securevault.ui.auth.LoginActivity
import com.gopi.securevault.util.CryptoPrefs
import com.gopi.securevault.util.PasswordUtils
import kotlinx.coroutines.launch

class BackupRestoreActivity : BaseActivity() {

    private lateinit var binding: ActivityBackupRestoreBinding
    private lateinit var backupManager: BackupManager
    private lateinit var prefs: CryptoPrefs


    private val backupJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                showPasswordDialog(isForDbRestore = false) { password ->
                    lifecycleScope.launch {
                        backupManager.backupToJson(password, uri)
                    }
                }
            }
        }
    }

    private val restoreJsonLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                showPasswordPrompt { password ->
                    lifecycleScope.launch {
                        backupManager.restoreFromJson(password, uri) {
                            restartApp()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        backupManager = BackupManager(this)
        prefs = CryptoPrefs(this)

        binding.btnBackup.setOnClickListener {
            showBackupOptions()
        }

        binding.btnRestore.setOnClickListener {
            showRestoreOptions()
        }
    }

    private fun showBackupOptions() {
        openFilePickerForJsonBackup()
    }

    private fun showRestoreOptions() {
        openFilePickerForJsonRestore()
    }

    private fun showPasswordDialog(isForDbRestore: Boolean = false, onPasswordEntered: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_prompt, null)
        val etPassword = dialogView.findViewById<android.widget.EditText>(R.id.etPassword)

        AlertDialog.Builder(this)
            .setTitle("Enter Master Password")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val password = etPassword.text.toString()
                if (password.isNotEmpty()) {
                    val salt = prefs.getString("salt", null)
                    val hash = prefs.getString("master_hash", null)
                    if (salt != null && hash != null && PasswordUtils.hashWithSalt(password, salt) == hash) {
                        if (isForDbRestore) {
                            onPasswordEntered(hash)
                        } else {
                            onPasswordEntered(password)
                        }
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Password required!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPasswordPrompt(onPasswordEntered: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_prompt, null)
        val etPassword = dialogView.findViewById<android.widget.EditText>(R.id.etPassword)

        AlertDialog.Builder(this)
            .setTitle("Enter Master Password")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val password = etPassword.text.toString()
                if (password.isNotEmpty()) {
                    onPasswordEntered(password)
                } else {
                    Toast.makeText(this, "Password required!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartApp() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }


    private fun openFilePickerForJsonBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, backupManager.getBackupFileName())
        }
        backupJsonLauncher.launch(intent)
    }

    private fun openFilePickerForJsonRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        restoreJsonLauncher.launch(intent)
    }
}
