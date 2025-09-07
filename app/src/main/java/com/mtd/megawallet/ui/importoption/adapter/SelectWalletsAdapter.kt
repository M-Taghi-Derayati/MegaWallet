package com.mtd.megawallet.ui.importoption.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ItemWalletToImportBinding
import com.mtd.megawallet.event.AccountInfo


class SelectWalletsAdapter(
    private val onAccountSelectionChanged: (accountId: String, isSelected: Boolean) -> Unit
) : ListAdapter<AccountInfo, SelectWalletsAdapter.ViewHolder>(DiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemWalletToImportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }



    inner class ViewHolder(private val binding: ItemWalletToImportBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(account: AccountInfo) {
            binding.textNetworkName.text = account.networkName
            binding.textAddress.text = account.address
            binding.textBalance.text = account.balance
            binding.textBalanceUsd.text = account.balanceUsd
            binding.imageNetworkIcon.loaded(account.iconUrl?:"")

            binding.checkboxSelect.setOnCheckedChangeListener(null) // Prevent listener firing on bind
            binding.checkboxSelect.isChecked = account.isSelected // A new field we'll add to AccountInfo
            binding.checkboxSelect.setOnCheckedChangeListener { _, isChecked ->
                onAccountSelectionChanged(account.id, isChecked)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AccountInfo>() {
        override fun areItemsTheSame(oldItem: AccountInfo, newItem: AccountInfo) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AccountInfo, newItem: AccountInfo) = oldItem == newItem
    }
}