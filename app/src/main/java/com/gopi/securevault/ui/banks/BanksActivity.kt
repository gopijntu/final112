package com.gopi.securevault.ui.banks

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
import com.gopi.securevault.data.entities.BankEntity
import com.gopi.securevault.databinding.ActivityBanksBinding
import com.gopi.securevault.databinding.ItemBankBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.widget.TooltipCompat

class BanksActivity : BaseActivity() {
    private lateinit var binding: ActivityBanksBinding
    private val dao by lazy { AppDatabase.get(this).bankDao() }
    private val adapter = BankAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } },
        onCopy = { accountNumber -> copyToClipboard(accountNumber) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBanksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showCreateOrEditDialog(null) }
        TooltipCompat.setTooltipText(binding.fabAdd, "Click here to add new record")

        lifecycleScope.launch {
            dao.observeAll().collectLatest { list -> adapter.submit(list) }
        }
    }

    private fun showCreateOrEditDialog(existing: BankEntity?) {
        val dlgBinding = com.gopi.securevault.databinding.DialogBankBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etTitle.editText?.setText(it.title ?: "")
            dlgBinding.etAccountNo.editText?.setText(it.accountNo)
            dlgBinding.etBankName.editText?.setText(it.bankName ?: "")
            dlgBinding.etIFSC.editText?.setText(it.ifsc ?: "")
            dlgBinding.etCIF.editText?.setText(it.cifNo ?: "")
            dlgBinding.etUsername.editText?.setText(it.username ?: "")
            dlgBinding.etProfilePrivy.editText?.setText(it.profilePrivy ?: "")
            dlgBinding.etPrivy.editText?.setText(it.privy ?: "")
            dlgBinding.etMPin.editText?.setText(it.mPin ?: "")
            dlgBinding.etTPin.editText?.setText(it.tPin ?: "")
            dlgBinding.etNotes.editText?.setText(it.notes ?: "")
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Create Bank" else "Edit Bank")
            .setView(dlgBinding.root)
            .setPositiveButton("Save") { d, _ ->
                val acc = dlgBinding.etAccountNo.editText?.text.toString().trim()
                if (acc.isEmpty()) {
                    Toast.makeText(this, "Account No is mandatory", Toast.LENGTH_SHORT).show()
                } else {
                    val entity = BankEntity(
                        id = existing?.id ?: 0,
                        title = dlgBinding.etTitle.editText?.text.toString(),
                        accountNo = acc,
                        bankName = dlgBinding.etBankName.editText?.text.toString(),
                        ifsc = dlgBinding.etIFSC.editText?.text.toString(),
                        cifNo = dlgBinding.etCIF.editText?.text.toString(),
                        username = dlgBinding.etUsername.editText?.text.toString(),
                        profilePrivy = dlgBinding.etProfilePrivy.editText?.text.toString(),
                        privy = dlgBinding.etPrivy.editText?.text.toString(),
                        mPin = dlgBinding.etMPin.editText?.text.toString(),
                        tPin = dlgBinding.etTPin.editText?.text.toString(),
                        notes = dlgBinding.etNotes.editText?.text.toString()
                    )
                    lifecycleScope.launch {
                        if (existing == null) dao.insert(entity) else dao.update(entity)
                    }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("account number", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

private class BankAdapter(
    val onEdit: (BankEntity) -> Unit,
    val onDelete: (BankEntity) -> Unit,
    val onCopy: (String) -> Unit
) : RecyclerView.Adapter<BankVH>() {
    private val items = mutableListOf<BankEntity>()
    fun submit(list: List<BankEntity>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BankVH {
        val binding = ItemBankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BankVH(binding, onEdit, onDelete, onCopy)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: BankVH, position: Int) = holder.bind(items[position])
}

private class BankVH(
    private val binding: ItemBankBinding,
    val onEdit: (BankEntity) -> Unit,
    val onDelete: (BankEntity) -> Unit,
    val onCopy: (String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: BankEntity) {
        binding.tvTitle.text = item.title ?: "(No Title)"
        binding.tvAccountNo.text = item.accountNo
        binding.tvBankName.text = "Bank: ${item.bankName ?: ""}"
        binding.tvIFSC.text = "IFSC: ${item.ifsc ?: ""}"
        binding.tvCIF.text = "CIF: ${item.cifNo ?: ""}"
        binding.tvUsername.text = "Username: ${item.username ?: ""}"
        binding.tvProfilePrivy.text = "Profile Privy: ${item.profilePrivy ?: ""}"
        binding.tvPrivy.text = "Privy: ${item.privy ?: ""}"
        binding.tvMPin.text = "M Pin: ${item.mPin ?: ""}"
        binding.tvTPin.text = "T Pin: ${item.tPin ?: ""}"
        binding.tvNotes.text = "Notes: ${item.notes ?: ""}"

        binding.llAccountNo.setOnClickListener { onCopy(item.accountNo) }
        binding.btnEdit.setOnClickListener { onEdit(item) }
        binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}