package com.mtd.megawallet.ui.transaction.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ItemAssetSelectableBinding
import com.mtd.megawallet.event.AssetItem


class SelectAssetToSendAdapter(
    private val onAssetClick: (AssetItem) -> Unit
) : ListAdapter<AssetItem, SelectAssetToSendAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssetSelectableBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val asset = getItem(position)
        holder.bind(asset)
        holder.itemView.setOnClickListener {
            onAssetClick(asset)
        }
    }

    class ViewHolder(private val binding: ItemAssetSelectableBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: AssetItem) {

            binding.textAssetName.text = asset.name
            binding.textNetworkName.text = asset.networkName
            binding.textBalance.text = asset.balanceUsdt
            binding.textBalanceUsd.text = asset.balance
            binding.imageAssetIcon.loaded(asset.iconUrl?:"")

        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AssetItem>() {
        override fun areItemsTheSame(oldItem: AssetItem, newItem: AssetItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AssetItem, newItem: AssetItem) = oldItem == newItem
    }
}