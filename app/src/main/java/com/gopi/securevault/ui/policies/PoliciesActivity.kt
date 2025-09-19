package com.gopi.securevault.ui.policies

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
import com.gopi.securevault.data.entities.PolicyEntity
import com.gopi.securevault.databinding.ActivityPoliciesBinding
import com.gopi.securevault.databinding.ItemPolicyBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PoliciesActivity : BaseActivity() {
    private lateinit var binding: ActivityPoliciesBinding
    private val dao by lazy { AppDatabase.get(this).policyDao() }
    private val adapter = PolicyAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoliciesBinding.inflate(layoutInflater)
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

    private fun showCreateOrEditDialog(existing: PolicyEntity?) {
        val dlgBinding = com.gopi.securevault.databinding.DialogPolicyBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etName.setText(it.name ?: "")
            dlgBinding.etAccount.setText(it.amount ?: "")
            dlgBinding.etCompany.setText(it.company ?: "")
            dlgBinding.etNextPremiumDate.setText(it.nextPremiumDate ?: "")
            dlgBinding.etPremiumValue.setText(it.premiumValue ?: "")
            dlgBinding.etMaturityValue.setText(it.maturityValue ?: "")
            dlgBinding.etNotes.setText(it.notes ?: "")
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
                val entity = PolicyEntity(
                    id = existing?.id ?: 0,
                    name = name,
                    amount = dlgBinding.etAccount.text.toString(),
                    company = dlgBinding.etCompany.text.toString(),
                    nextPremiumDate = dlgBinding.etNextPremiumDate.text.toString(),
                    premiumValue = dlgBinding.etPremiumValue.text.toString(),
                    maturityValue = dlgBinding.etMaturityValue.text.toString(),
                    notes = dlgBinding.etNotes.text.toString()
                )
                lifecycleScope.launch {
                    if (existing == null) dao.insert(entity) else dao.update(entity)
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }
}

class PolicyAdapter(
    private val onEdit: (PolicyEntity) -> Unit,
    private val onDelete: (PolicyEntity) -> Unit
) : RecyclerView.Adapter<PolicyVH>() {

    private val items = mutableListOf<PolicyEntity>()

    fun submit(list: List<PolicyEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PolicyVH {
        val binding = ItemPolicyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PolicyVH(binding, onEdit, onDelete)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: PolicyVH, position: Int) =
        holder.bind(items[position])
}

class PolicyVH(
    private val binding: ItemPolicyBinding,
    private val onEdit: (PolicyEntity) -> Unit,
    private val onDelete: (PolicyEntity) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: PolicyEntity) {
        binding.tvTitle.text = item.name ?: "(No Name)"
        binding.tvAccount.text = "Account: ${item.amount ?: "-"}"
        binding.tvCompany.text = "Company: ${item.company ?: "-"}"
        binding.tvNextPremiumDate.text = "Next Premium Date: ${item.nextPremiumDate ?: "-"}"
        binding.tvPremiumValue.text = "Premium Value: ${item.premiumValue ?: "-"}"
        binding.tvMaturityValue.text = "Maturity Value: ${item.maturityValue ?: "-"}"
        binding.tvNotes.text = "Notes: ${item.notes ?: "-"}"

        binding.btnEdit.setOnClickListener { onEdit(item) }
        binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
