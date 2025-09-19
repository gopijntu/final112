package com.gopi.securevault.ui.cards

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
import com.gopi.securevault.data.entities.CardEntity
import com.gopi.securevault.databinding.ActivityCardsBinding
import com.gopi.securevault.databinding.ItemCardBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.widget.TooltipCompat

class CardsActivity : BaseActivity() {
    private lateinit var binding: ActivityCardsBinding
    private val dao by lazy { AppDatabase.get(this).cardDao() }
    private val adapter = CardAdapter(
        onEdit = { entity -> showCreateOrEditDialog(entity) },
        onDelete = { entity -> lifecycleScope.launch { dao.delete(entity) } },
        onCopy = { cardNumber -> copyToClipboard(cardNumber) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardsBinding.inflate(layoutInflater)
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

    private fun showCreateOrEditDialog(existing: CardEntity?) {
        val dlgBinding = com.gopi.securevault.databinding.DialogCardBinding.inflate(layoutInflater)

        existing?.let {
            dlgBinding.etBankName.editText?.setText(it.bankName ?: "")
            dlgBinding.etCardNumber.editText?.setText(it.cardNumber ?: "")
            dlgBinding.etCardType.editText?.setText(it.cardType ?: "")
            dlgBinding.etValidTill.editText?.setText(it.validTill ?: "")
            dlgBinding.etCVV.editText?.setText(it.cvv ?: "")
            dlgBinding.etCustomerId.editText?.setText(it.customerId ?: "")
            dlgBinding.etPin.editText?.setText(it.pin ?: "")
            dlgBinding.etNotes.editText?.setText(it.notes ?: "")
        }

        val dlg = AlertDialog.Builder(this)
            .setView(dlgBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val number = dlgBinding.etCardNumber.editText?.text.toString().trim()
                if (number.isBlank()) {
                    Toast.makeText(this, "Card number is mandatory", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val entity = CardEntity(
                    id = existing?.id ?: 0,
                    bankName = dlgBinding.etBankName.editText?.text.toString(),
                    cardType = dlgBinding.etCardType.editText?.text.toString(),
                    cardNumber = number,
                    cvv = dlgBinding.etCVV.editText?.text.toString(),
                    validTill = dlgBinding.etValidTill.editText?.text.toString(),
                    customerId = dlgBinding.etCustomerId.editText?.text.toString(),
                    pin = dlgBinding.etPin.editText?.text.toString(),
                    notes = dlgBinding.etNotes.editText?.text.toString()
                )
                lifecycleScope.launch {
                    if (existing == null) dao.insert(entity) else dao.update(entity)
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("card number", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}

class CardAdapter(
    val onEdit: (CardEntity) -> Unit,
    val onDelete: (CardEntity) -> Unit,
    val onCopy: (String) -> Unit
) : RecyclerView.Adapter<CardVH>() {
    private val items = mutableListOf<CardEntity>()

    fun submit(list: List<CardEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardVH(binding, onEdit, onDelete, onCopy)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: CardVH, position: Int) =
        holder.bind(items[position])
}

class CardVH(
    private val binding: ItemCardBinding,
    val onEdit: (CardEntity) -> Unit,
    val onDelete: (CardEntity) -> Unit,
    val onCopy: (String) -> Unit
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: CardEntity) {
        // Title and core fields
        binding.tvTitle.text = item.bankName ?: "(No Bank)"
        binding.tvCardNumber.text = item.cardNumber ?: ""
        binding.tvExpiry.text = "Expiry: ${item.validTill ?: ""}"

        // Mask CVV
        binding.tvCvv.text = "CVV: ${item.cvv ?: ""}"

        // Show bank name again if you want as subtitle
        binding.tvBankName.text = "Bank: ${item.bankName ?: ""}"

        // Card type / Note if available
        binding.tvCardType.text = item.cardType ?: ""
        binding.tvCustomerId.text = "Customer ID: ${item.customerId ?: ""}"
        binding.tvPin.text = "PIN: ${item.pin ?: ""}"
        binding.tvNotes.text = "Notes: ${item.notes ?: ""}"

        // Buttons
        binding.ivCopyCard.setOnClickListener { onCopy(item.cardNumber ?: "") }
        binding.btnEdit.setOnClickListener { onEdit(item) }
        binding.btnDelete.setOnClickListener { onDelete(item) }
    }
}
