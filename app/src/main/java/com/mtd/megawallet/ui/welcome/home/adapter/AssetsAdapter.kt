
package com.mtd.megawallet.ui.welcome.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ItemAssetBinding
import com.mtd.megawallet.event.AssetItem
import com.mtd.common_ui.R as commonUiR

class AssetsAdapter : ListAdapter<AssetItem, AssetsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssetBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }


    class ViewHolder(private val binding: ItemAssetBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(asset: AssetItem) {
            val context = binding.root.context
            binding.textAssetName.text = asset.name
            binding.textNetworkName.text = asset.networkName
            binding.textBalance.text = asset.formattedDisplayBalance
            binding.textBalanceUsd.text = asset.balance

            // بارگذاری آیکون با Coil
            binding.imageAssetIcon.loaded(asset.iconUrl?:"")

            // مدیریت نمایش و رنگ درصد تغییرات
            if (asset.priceChange24h == 0.0) {
                binding.textPriceChange.visibility = View.GONE
            } else {
                binding.textPriceChange.visibility = View.VISIBLE
                val changeText = String.format("%.2f%%", asset.priceChange24h)
                val colorRes = if (asset.priceChange24h >= 0) {
                    binding.textPriceChange.text = "+$changeText"
                    commonUiR.color.semantic_success
                } else {
                    binding.textPriceChange.text = changeText
                    commonUiR.color.semantic_error
                }
                binding.textPriceChange.setTextColor(ContextCompat.getColor(context, colorRes))
            }
        }
        }

    class DiffCallback : DiffUtil.ItemCallback<AssetItem>() {
        override fun areItemsTheSame(oldItem: AssetItem, newItem: AssetItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AssetItem, newItem: AssetItem) = oldItem == newItem
    }
}