package com.mtd.megawallet.ui.swap.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.core.model.QuoteResponse.ReceivingOptionDto
import com.mtd.megawallet.databinding.ListItemNetworkOptionBinding
import com.mtd.megawallet.event.SwapUiState.ReceivingOptionUI

class ReceivingOptionsAdapter(
    private val onItemClicked: (ReceivingOptionDto) -> Unit
) : ListAdapter<ReceivingOptionUI, ReceivingOptionsAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ListItemNetworkOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClicked(getItem(adapterPosition).option)
                }
            }
        }

        fun bind(uiOption: ReceivingOptionUI) {
            val option = uiOption.option
            val feeDetails = option.fees.details
            

             binding.ivNetworkIcon.loaded(option.fees.details.iconUrl)

            binding.tvNetworkName.text = option.networkName
            binding.tvNetworkFee.text = "کارمزد شبکه: ~${option.fees.totalFeeInUsd} دلار" // نمایش مجموع هزینه برای این گزینه
            binding.tvFinalAmount.text = "دریافتی: ${option.finalAmount} ${feeDetails.exchangeFee.asset}"
            binding.rbSelected.isChecked = uiOption.isSelected
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemNetworkOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ReceivingOptionUI>() {
        override fun areItemsTheSame(oldItem: ReceivingOptionUI, newItem: ReceivingOptionUI): Boolean {
            return oldItem.option.networkId == newItem.option.networkId
        }
        override fun areContentsTheSame(oldItem: ReceivingOptionUI, newItem: ReceivingOptionUI): Boolean {
            return oldItem == newItem
        }
    }
}