package com.mtd.megawallet.ui.swap

import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.widget.AppCompatTextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.mtd.common_ui.mClick
import com.mtd.common_ui.setTextColorAnim
import com.mtd.core.utils.Easings
import com.mtd.core.utils.Interpolators
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseActivity
import com.mtd.megawallet.databinding.ActivityTradeBinding
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.event.TradeUiState
import com.mtd.megawallet.ui.swap.adapter.OrderBookBuyAdapter
import com.mtd.megawallet.ui.swap.adapter.OrderBookSellAdapter
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.SwapViewModel
import com.mtd.megawallet.viewmodel.TradeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber


@AndroidEntryPoint
class TradeActivity : BaseActivity<ActivityTradeBinding, TradeViewModel>() {

    override val viewModel: TradeViewModel by viewModels()
    override val binding by viewBinding(ActivityTradeBinding::inflate)
    val swapViewModel: SwapViewModel by viewModels()

    private var isStatusOrderBuy = true

    private val adapterBuyHorizontalAdapter by lazy {
        OrderBookBuyAdapter()
    }
    private val adapterSellHorizontalAdapter by lazy {
        OrderBookSellAdapter()
    }

    private lateinit var isViewLastSelectMenuStatus: AppCompatTextView
    val markets=arrayListOf<String>("BTCUSDT","ETHUSDT","BNBUSDT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        viewModel.initialize(markets[0])
        isViewLastSelectMenuStatus = binding.txtMenuSell
        setupRecyclerViews()
        setupSpinnerTypeMarket()
    }


    override fun observeData() {

        lifecycleScope.launch {
            // repeatOnLifecycle تضمین می‌کند که collect فقط زمانی فعال است که Fragment قابل مشاهده باشد
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // --- بخش اصلاح شده: استفاده از collect به جای collectLatest ---
                viewModel.uiState.collect { state ->
                    // حالا این بلاک برای هر State به طور کامل اجرا خواهد شد
                    renderState(state) // تابع جداگانه برای رندر کردن
                }
            }
        }
        lifecycleScope.launch {
            swapViewModel.uiState.collect { state ->
                if (state is SwapUiState.Ready) {

                    if (isStatusOrderBuy){
                        if (state.bottomSheetTitle != "انتخاب ارز مقصد") {
                            state.fromAssets.find { itFind -> itFind.symbol == "ETH" }?.let {
                                swapViewModel.onFromAssetSelected(it.assetId)
                                swapViewModel.onAssetSelectionOpened(isSelectingFrom = false)
                            }
                        } else {
                            state.toAssets.find { itFind -> itFind.symbol == "USDT" }?.let {
                                swapViewModel.onToAssetSelected(it.assetId)
                            }
                        }
                    }else{
                        if (state.bottomSheetTitle != "انتخاب ارز مقصد") {
                            state.fromAssets.find { itFind -> itFind.symbol == "USDT" }?.let {
                                swapViewModel.onFromAssetSelected(it.assetId)
                                swapViewModel.onAssetSelectionOpened(isSelectingFrom = false)
                            }
                        } else {
                            state.toAssets.find { itFind -> itFind.symbol == "ETH" }?.let {
                                swapViewModel.onToAssetSelected(it.assetId)
                            }
                        }
                    }

                }
            }
        }
    }

    private fun setupSpinnerTypeMarket() {

        val adapterSpinner = ArrayAdapter(
            this,
            R.layout.layout_spinner_item,markets
        )
        binding.layManOrder.spTypeMarket.apply {
            this.adapter = adapterSpinner
            this.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    selectItem: Int,
                    p3: Long
                ) {
                    viewModel.initialize(markets[selectItem])
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }
    }

    private fun renderState(state: TradeUiState) {


        if (state is TradeUiState.Success) {
            // به‌روزرسانی لیست‌های Order Book
            Timber.tag("Order").d(state.asks.toString())

            adapterSellHorizontalAdapter.submitList(state.asks){
                binding.recOrderBookSell.scrollToPosition(0)
            }
            // لیست Asks را برعکس می‌کنیم تا بهترین قیمت (کمترین) در بالا باشد
            adapterBuyHorizontalAdapter.submitList(state.bids){
                binding.recOrderBookBuy.scrollToPosition(0)
            }

            // به‌روزرسانی قیمت لحظه‌ای و سایر اطلاعات
            binding.txtPrice.text = state.lastPrice
            //    supportActionBar?.title = state.marketSymbol // تنظیم عنوان Activity
        } else if (state is TradeUiState.Error) {
            // ...
        }
    }

    override fun setupClickListeners() {
        binding.txtMenuSell.mClick {
            if (isViewLastSelectMenuStatus != binding.txtMenuBuy) {
                mAnimBuySellSelect(binding.txtMenuBuy)
                binding.layManOrder.edtVolumeLimit.text?.clear()
                isStatusOrderBuy = false
                binding.layManOrder.btnOrder.text = "فروش"
                (swapViewModel.uiState.value as SwapUiState.Ready).fromAssets.find { itFind -> itFind.symbol == "USDT" }?.let {
                    swapViewModel.onFromAssetSelected(it.assetId)
                    swapViewModel.onAssetSelectionOpened(isSelectingFrom = false)
                }
            }
        }

        binding.txtMenuBuy.mClick {
            if (isViewLastSelectMenuStatus != binding.txtMenuSell) {
                mAnimBuySellSelect(binding.txtMenuSell)
                binding.layManOrder.edtAmount.text?.clear()
                isStatusOrderBuy = true
                binding.layManOrder.btnOrder.text = "خرید"
                (swapViewModel.uiState.value as SwapUiState.Ready).fromAssets.find { itFind -> itFind.symbol == "ETH" }?.let {
                    swapViewModel.onFromAssetSelected(it.assetId)
                    swapViewModel.onAssetSelectionOpened(isSelectingFrom = false)
                }
            }
        }


        binding.layManOrder.btnOrder.mClick {
            SwapBottomSheetFragment(binding.layManOrder.edtAmount.text.toString()).show(
                supportFragmentManager,
                ""
            )
        }

        // TODO: Listener برای EditText ها با استفاده از debounce برای فراخوانی onAmountChange
    }

    private fun setupRecyclerViews() {

        binding.recOrderBookBuy.apply {
            adapter = adapterBuyHorizontalAdapter
            setHasFixedSize(true)
            (this.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            layoutManager =
                LinearLayoutManager(this@TradeActivity, LinearLayoutManager.VERTICAL, false)
        }

        binding.recOrderBookSell.apply {
            layoutManager =
                LinearLayoutManager(this@TradeActivity, LinearLayoutManager.VERTICAL, true)
            adapter = adapterSellHorizontalAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(8)
            (this.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        }

        swapViewModel.onAssetSelectionOpened(isSelectingFrom = true)


    }

    private fun mAnimBuySellSelect(view: AppCompatTextView) {
        operator.translateLeftRight(binding.vSelectMenuBuySell, -view.x, 500)
            .doOnSubscribe {
                mAnimMenuStatus(view)
                (binding.layManOrder.btnOrder.background as TransitionDrawable).apply {
                    if (view == binding.txtMenuSell) {
                        this.reverseTransition(500)
                    } else {
                        this.startTransition(500)
                    }
                }
                (binding.vSelectMenuBuySell.background as TransitionDrawable).apply {
                    if (view == binding.txtMenuSell) {
                        this.reverseTransition(500)
                    } else {
                        this.startTransition(500)
                    }
                }
            }
            .subscribe()

    }

    private fun mAnimMenuStatus(view: AppCompatTextView) {
        if (isViewLastSelectMenuStatus != view) {
            isViewLastSelectMenuStatus.setTextColorAnim(
                com.mtd.common_ui.R.color.grayTv,
                com.mtd.common_ui.R.color.white,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            view.setTextColorAnim(
                com.mtd.common_ui.R.color.white,
                com.mtd.common_ui.R.color.grayTv,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            isViewLastSelectMenuStatus = view
        }
    }
}