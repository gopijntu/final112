package com.gopi.securevault.ui.misc

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.gopi.securevault.ui.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gopi.securevault.data.db.AppDatabase
import com.gopi.securevault.data.entities.MiscEntity
import com.gopi.securevault.databinding.ActivityMiscBinding
import com.gopi.securevault.databinding.DialogMiscBinding
import com.gopi.securevault.databinding.ItemMiscBinding
import com.gopi.securevault.util.AppConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MiscActivity : BaseActivity() {
    private lateinit var binding: ActivityMiscBinding
    private var selectedFileUri: Uri? = null
    private val dao by lazy { AppDatabase.get(this).miscDao() }
    private val adapter = MiscAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } },
        onCopy = { number -> copyToClipboard(number) },
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
        binding = ActivityMiscBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showCreateOrEditDialog(null) }

        lifecycleScope.launch {
            dao.observeAll().collectLatest { list -> adapter.submit(list) }
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedFileUri = result.data?.data
        }
    }

    private fun showCreateOrEditDialog(existing: MiscEntity?) {
        val dlgBinding = DialogMiscBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etName.setText(it.name ?: "")
            dlgBinding.etNumber.setText(it.number ?: "")
            dlgBinding.etAmount.setText(it.amount ?: "")
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
                val name = dlgBinding.etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Name is mandatory", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                var documentPath: String? = existing?.documentPath
                selectedFileUri?.let { uri ->
                    documentPath = saveFileToInternalStorage(uri)
                }

                val entity = MiscEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    number = dlgBinding.etNumber.text.toString(),
                    amount = dlgBinding.etAmount.text.toString(),
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
            val file = File(filesDir, "doc_misc_${System.currentTimeMillis()}.pdf")
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
        val clip = ClipData.newPlainText("misc number", text)
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

class MiscAdapter(
    val onEdit: (MiscEntity) -> Unit,
    val onDelete: (MiscEntity) -> Unit,
    val onCopy: (String) -> Unit,
    val onDownload: (String) -> Unit
) : RecyclerView.Adapter<MiscAdapter.MiscVH>() {

    private val items = mutableListOf<MiscEntity>()

    fun submit(list: List<MiscEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiscVH {
        val binding = ItemMiscBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MiscVH(binding, onEdit, onDelete, onCopy, onDownload)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: MiscVH, position: Int) =
        holder.bind(items[position])

    class MiscVH(
        private val binding: ItemMiscBinding,
        val onEdit: (MiscEntity) -> Unit,
        val onDelete: (MiscEntity) -> Unit,
        val onCopy: (String) -> Unit,
        val onDownload: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MiscEntity) {
            binding.tvTitle.text = item.name ?: "(No Name)"
            binding.tvNumber.text = "Number: ${item.number ?: ""}"
            binding.tvAmount.text = "Amount: ${item.amount ?: ""}"
            binding.tvNotes.text = "Notes: ${item.notes ?: ""}"

            if (!item.documentPath.isNullOrEmpty()) {
                binding.btnDownload.visibility = android.view.View.VISIBLE
                binding.btnDownload.setOnClickListener { onDownload(item.documentPath) }
            } else {
                binding.btnDownload.visibility = android.view.View.GONE
            }

            binding.llNumber.setOnClickListener { onCopy(item.number ?: "") }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
