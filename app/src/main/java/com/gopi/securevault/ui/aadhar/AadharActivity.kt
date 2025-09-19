package com.gopi.securevault.ui.aadhar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.gopi.securevault.ui.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gopi.securevault.data.db.AppDatabase
import com.gopi.securevault.data.entities.AadharEntity
import com.gopi.securevault.databinding.ActivityAadharBinding
import com.gopi.securevault.databinding.ItemAadharBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.gopi.securevault.util.AppConstants
import java.io.File
import java.io.FileOutputStream

class AadharActivity : BaseActivity() {
    private lateinit var binding: ActivityAadharBinding
    private var selectedFileUri: Uri? = null
    private val dao by lazy { AppDatabase.get(this).aadharDao() }
    private val adapter = AadharAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } },
        onCopy = { aadharNumber -> copyToClipboard(aadharNumber) },
        onDownload = { path ->
            if (AppConstants.FEATURE_FLAG_PREMIUM == 1) {
                openFile(path)
            } else {
                Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAadharBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Setup RecyclerView
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        // Add new entry
        binding.fabAdd.setOnClickListener { showCreateOrEditDialog(null) }

        // Observe DB
        lifecycleScope.launch {
            dao.observeAll().collectLatest { list -> adapter.submit(list) }
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedFileUri = result.data?.data
            // You can optionally update the UI to show the selected file name
        }
    }

    private fun showCreateOrEditDialog(existing: AadharEntity?) {
        val dlgBinding = com.gopi.securevault.databinding.DialogAadharBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etName.setText(it.name ?: "")
            dlgBinding.etNumber.setText(it.number ?: "")
            dlgBinding.etDob.setText(it.dob ?: "")
            dlgBinding.etAddress.setText(it.address ?: "")
            dlgBinding.etNotes.setText(it.notes ?: "")
        }

        dlgBinding.btnUpload.setOnClickListener {
            if (AppConstants.FEATURE_FLAG_PREMIUM == 1) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                }
                filePickerLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show()
            }
        }

        val dlg = AlertDialog.Builder(this)
            .setView(dlgBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val number = dlgBinding.etNumber.text.toString().trim()
                if (number.isBlank()) {
                    Toast.makeText(this, "Aadhar number is mandatory", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                var documentPath: String? = existing?.documentPath
                selectedFileUri?.let { uri ->
                    documentPath = saveFileToInternalStorage(uri)
                }

                val entity = AadharEntity(
                    id = existing?.id ?: 0,
                    name = dlgBinding.etName.text.toString(),
                    number = number,
                    dob = dlgBinding.etDob.text.toString(),
                    address = dlgBinding.etAddress.text.toString(),
                    notes = dlgBinding.etNotes.text.toString(),
                    documentPath = documentPath
                )
                lifecycleScope.launch {
                    if (existing == null) dao.insert(entity) else dao.update(entity)
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun saveFileToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "doc_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("aadhar number", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun openFile(path: String) {
        try {
            val file = File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error opening file", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Adapter with nested ViewHolder
 */
class AadharAdapter(
    val onEdit: (AadharEntity) -> Unit,
    val onDelete: (AadharEntity) -> Unit,
    val onCopy: (String) -> Unit,
    val onDownload: (String) -> Unit
) : RecyclerView.Adapter<AadharAdapter.AadharVH>() {

    private val items = mutableListOf<AadharEntity>()

    fun submit(list: List<AadharEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AadharVH {
        val binding = ItemAadharBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AadharVH(binding, onEdit, onDelete, onCopy, onDownload)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: AadharVH, position: Int) =
        holder.bind(items[position])

    // ✅ Nested ViewHolder class
    class AadharVH(
        private val binding: ItemAadharBinding,
        val onEdit: (AadharEntity) -> Unit,
        val onDelete: (AadharEntity) -> Unit,
        val onCopy: (String) -> Unit,
        val onDownload: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AadharEntity) {
            binding.tvTitle.text = item.name ?: "(No Name)"
            binding.tvAadharNumber.text = item.number ?: ""
            binding.tvDob.text = "DOB: ${item.dob ?: ""}"
            binding.tvAddress.text = "Address: ${item.address ?: ""}"
            binding.tvNotes.text = "Notes: ${item.notes ?: ""}"

            if (!item.documentPath.isNullOrEmpty()) {
                binding.btnDownload.visibility = android.view.View.VISIBLE
                binding.btnDownload.setOnClickListener { onDownload(item.documentPath) }
            } else {
                binding.btnDownload.visibility = android.view.View.GONE
            }

            binding.llAadharNumber.setOnClickListener { onCopy(item.number ?: "") }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
