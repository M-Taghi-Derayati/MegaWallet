package com.mtd.megawallet.ui.swap.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mtd.common_ui.loaded
import com.mtd.megawallet.R
import com.mtd.megawallet.databinding.ItemOrderBookSellBinding
import com.mtd.megawallet.event.OrderBookRow

class OrderBookSellAdapter() : ListAdapter<OrderBookRow, OrderBookSellAdapter.MyViewHolder>(DiffCallback) {

    inner class MyViewHolder(val binding: ItemOrderBookSellBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OrderBookRow) {
            binding.item = item
            when(item.exchangeIds[0]){
                "OMPFinex"->binding.imLogo.loaded(R.drawable.ic_omp)
                "Wallex"->binding.imLogo.loaded(R.drawable.ic_wallex)
                "bitpin"->binding.imLogo.loaded(R.drawable.image)
                "ramzinex"->binding.imLogo.loaded(R.drawable.ramz)
            }


        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding: ItemOrderBookSellBinding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.item_order_book_sell, // <<-- استفاده از یک layout عمومی
            parent,
            false
        )
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // getItem() متدی است که ListAdapter در اختیار ما قرار می‌دهد
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<OrderBookRow>() {
        override fun areItemsTheSame(oldItem: OrderBookRow, newItem: OrderBookRow): Boolean {
            return oldItem.price == newItem.price // قیمت به عنوان شناسه یکتا
        }

        override fun areContentsTheSame(oldItem: OrderBookRow, newItem: OrderBookRow): Boolean {
            return oldItem == newItem
        }
    }
}