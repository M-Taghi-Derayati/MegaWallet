package com.mtd.megawallet.ui.welcome.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.databinding.ItemActivityBinding
import com.mtd.megawallet.event.HomeUiState.ActivityItem
import com.mtd.megawallet.event.HomeUiState.ActivityType
import com.mtd.common_ui.R as commonUiR

class ActivityAdapter : ListAdapter<ActivityItem, ActivityAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemActivityBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(activity: ActivityItem) {
            val context = binding.root.context
            binding.textActivityTitle.text = activity.title
            binding.textActivitySubtitle.text = activity.subtitle
            binding.textAmount.text = activity.amount


            // تنظیم رنگ بر اساس نوع تراکنش
            val amountColor = when (activity.type) {
                ActivityType.SEND -> ContextCompat.getColor(context, commonUiR.color.text_primary)
                ActivityType.RECEIVE -> ContextCompat.getColor(context, commonUiR.color.semantic_success)
                else -> ContextCompat.getColor(context, commonUiR.color.text_secondary)
            }

            binding.imageActivityIcon.loaded(activity.iconUrl?:"")
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ActivityItem>() {
        override fun areItemsTheSame(oldItem: ActivityItem, newItem: ActivityItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ActivityItem, newItem: ActivityItem) = oldItem == newItem
    }
}