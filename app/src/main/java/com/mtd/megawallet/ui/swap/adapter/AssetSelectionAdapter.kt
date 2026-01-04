package com.mtd.megawallet.ui.swap.adapter // یا هر پکیج دیگری که برای آداپترها دارید

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ListItemAssetHeaderBinding
import com.mtd.megawallet.databinding.ListItemAssetSelectionBinding
import com.mtd.megawallet.event.AssetSelectionListItem
import com.mtd.megawallet.event.SwapUiState.AssetSelectItem

/**
 * آداپتر برای نمایش لیست ارزها در BottomSheet انتخاب ارز.
 *
 * @param onItemClicked یک لامبدا که وقتی کاربر روی یک آیتم کلیک می‌کند، فراخوانی می‌شود.
 */

// تعریف ViewType ها
private const val ITEM_VIEW_TYPE_HEADER = 0
private const val ITEM_VIEW_TYPE_ASSET = 1


class AssetSelectionAdapter(
    private val onItemClicked: (AssetSelectItem) -> Unit
) : ListAdapter<AssetSelectionListItem, RecyclerView.ViewHolder>(DiffCallback) {

    // --- ViewHolder برای هدر ---
    class HeaderViewHolder(private val binding: ListItemAssetHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: AssetSelectionListItem.Header) {
            binding.tvNetworkName.text = header.networkName
        }
    }

    /**
     * ViewHolder برای هر ردیف در لیست.
     * این کلاس View ها را نگه می‌دارد و داده‌ها را به آنها متصل می‌کند.
     */
    inner class AssetViewHolder(private val binding: ListItemAssetSelectionBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val item = getItem(adapterPosition)
                    if (item is AssetSelectionListItem.Asset) {
                        onItemClicked(item.item) // فقط آیتم‌های ارز قابل کلیک هستند
                    }
                }
            }
        }
        fun bind(assetItem: AssetSelectionListItem.Asset) {
            val asset = assetItem.item
            binding.tvAssetSymbol.text = asset.symbol
            binding.tvAssetNameNetwork.text = "${asset.name} on ${asset.networkName}"
            binding.tvAssetBalance.text = asset.balance

            // بارگذاری آیکون با استفاده از Coil
            binding.ivAssetIcon.loaded(asset.iconUrl?:"")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AssetSelectionListItem.Header -> ITEM_VIEW_TYPE_HEADER
            is AssetSelectionListItem.Asset -> ITEM_VIEW_TYPE_ASSET
        }
    }

    /**
     * فراخوانی می‌شود وقتی RecyclerView نیاز به یک ViewHolder جدید دارد.
     * در اینجا ما layout خود را inflate کرده و یک نمونه از AssetViewHolder می‌سازیم.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> {
                val binding = ListItemAssetHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeaderViewHolder(binding)
            }
            ITEM_VIEW_TYPE_ASSET -> {
                val binding = ListItemAssetSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AssetViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    /**
     * فراخوانی می‌شود وقتی RecyclerView می‌خواهد داده‌ها را به یک ViewHolder نمایش دهد.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is AssetSelectionListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is AssetSelectionListItem.Asset -> (holder as AssetViewHolder).bind(item)
        }
    }

    // DiffCallback هم باید آپدیت شود
    companion object DiffCallback : DiffUtil.ItemCallback<AssetSelectionListItem>() {
        override fun areItemsTheSame(oldItem: AssetSelectionListItem, newItem: AssetSelectionListItem): Boolean {
            return if (oldItem is AssetSelectionListItem.Asset && newItem is AssetSelectionListItem.Asset) {
                oldItem.item.assetId == newItem.item.assetId
            } else if (oldItem is AssetSelectionListItem.Header && newItem is AssetSelectionListItem.Header) {
                oldItem.networkName == newItem.networkName
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: AssetSelectionListItem, newItem: AssetSelectionListItem): Boolean {
            return oldItem == newItem
        }
    }
}