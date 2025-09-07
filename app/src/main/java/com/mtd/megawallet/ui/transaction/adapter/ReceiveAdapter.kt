package com.mtd.megawallet.ui.transaction.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ItemAddressGroupHeaderBinding
import com.mtd.megawallet.databinding.ItemAddressRowBinding

import com.mtd.megawallet.event.ReceiveUiState.AddressGroup
import com.mtd.megawallet.event.ReceiveUiState.AddressItem

class ReceiveAdapter(
    private val onCopyClick: (address: String) -> Unit,
    private val onQrClick: (address: String, networkName: String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ADDRESS = 1
    }

    fun submitList(groups: List<AddressGroup>) {
        items.clear()
        groups.forEach { group ->
            items.add(group) // Add header
            items.addAll(group.items) // Add address items
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is AddressGroup) VIEW_TYPE_HEADER else VIEW_TYPE_ADDRESS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemAddressGroupHeaderBinding.inflate(inflater, parent, false))
        } else {
            AddressViewHolder(ItemAddressRowBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.bind(items[position] as AddressGroup)
        } else if (holder is AddressViewHolder) {
            holder.bind(items[position] as AddressItem)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(private val binding: ItemAddressGroupHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(group: AddressGroup) {
            binding.textGroupTitle.text = group.title
            binding.textGroupSubtitle.text = group.subtitle
            binding.textGroupSubtitle.isVisible = group.subtitle.isNotBlank()
        }
    }

    inner class AddressViewHolder(private val binding: ItemAddressRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AddressItem) {
            binding.textNetworkName.text = item.networkName
            binding.textAddress.text = "${item.address.take(6)}...${item.address.takeLast(4)}"
            binding.imageNetworkIcon.loaded(item.iconUrl?:"")

            binding.buttonCopy.setOnClickListener { onCopyClick(item.address) }
            binding.buttonShowQr.setOnClickListener { onQrClick(item.address, item.networkName) }
            itemView.setOnClickListener { onQrClick(item.address, item.networkName) }
        }
    }
}