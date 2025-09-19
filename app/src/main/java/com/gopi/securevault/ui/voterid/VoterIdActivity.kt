package com.gopi.securevault.ui.voterid

import android.app.Activity
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
import com.gopi.securevault.data.entities.VoterIdEntity
import com.gopi.securevault.databinding.ActivityVoterIdBinding
import com.gopi.securevault.databinding.ItemVoterIdBinding
import com.gopi.securevault.util.AppConstants
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class VoterIdActivity : BaseActivity() {
    private lateinit var binding: ActivityVoterIdBinding
    private var selectedFileUri: Uri? = null
    private val dao by lazy { AppDatabase.get(this).voterIdDao() }
    private val adapter = VoterIdAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } },
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
        binding = ActivityVoterIdBinding.inflate(layoutInflater)
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

    private fun showCreateOrEditDialog(existing: VoterIdEntity?) {
        val dlgBinding = com.gopi.securevault.databinding.DialogVoterIdBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etName.setText(it.name ?: "")
            dlgBinding.etVoterIdNumber.setText(it.voterIdNumber ?: "")
            dlgBinding.etNotes.setText(it.notes ?: "")
        }

        dlgBinding.btnUpload.setOnClickListener {
            if (AppConstants.FEATURE_FLAG_PREMIUM == 1) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
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

                val entity = VoterIdEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    voterIdNumber = dlgBinding.etVoterIdNumber.text.toString(),
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
            val file = File(filesDir, "doc_${System.currentTimeMillis()}")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

class VoterIdAdapter(
    val onEdit: (VoterIdEntity) -> Unit,
    val onDelete: (VoterIdEntity) -> Unit,
    val onDownload: (String) -> Unit
) : RecyclerView.Adapter<VoterIdAdapter.VoterIdVH>() {

    private val items = mutableListOf<VoterIdEntity>()

    fun submit(list: List<VoterIdEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoterIdVH {
        val binding = ItemVoterIdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VoterIdVH(binding, onEdit, onDelete, onDownload)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VoterIdVH, position: Int) =
        holder.bind(items[position])

    class VoterIdVH(
        private val binding: ItemVoterIdBinding,
        val onEdit: (VoterIdEntity) -> Unit,
        val onDelete: (VoterIdEntity) -> Unit,
        val onDownload: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VoterIdEntity) {
            binding.tvTitle.text = item.name ?: "(No Name)"
            binding.tvVoterIdNumber.text = "Voter ID: ${item.voterIdNumber ?: ""}"
            binding.tvNotes.text = "Notes: ${item.notes ?: ""}"

            if (!item.documentPath.isNullOrEmpty()) {
                binding.btnDownload.visibility = android.view.View.VISIBLE
                binding.btnDownload.setOnClickListener { onDownload(item.documentPath) }
            } else {
                binding.btnDownload.visibility = android.view.View.GONE
            }

            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
