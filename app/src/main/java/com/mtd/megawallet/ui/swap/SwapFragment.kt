package com.mtd.megawallet.ui.swap

import android.os.Bundle
import android.os.CountDownTimer
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mtd.common_ui.mClick
import com.mtd.common_ui.textChange
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseActivity
import com.mtd.megawallet.databinding.FragmentSwapBinding
import com.mtd.megawallet.event.SendUiState
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.ui.swap.adapter.ReceivingOptionsAdapter
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.SendViewModel
import com.mtd.megawallet.viewmodel.SwapViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


@AndroidEntryPoint
class SwapFragment : BaseActivity<FragmentSwapBinding, SwapViewModel>() {

    override val viewModel: SwapViewModel by viewModels()
    override val binding by viewBinding(FragmentSwapBinding::inflate)

    val viewModelSend: SendViewModel by viewModels()
    private var loadingDialog: LoadingDialogFragment? = null

    private lateinit var receivingOptionsAdapter: ReceivingOptionsAdapter

    // زمان شروع تایمر: ۶۰ دقیقه به میلی‌ثانیه
    private val startTimeInMillis: Long =  30 * 1000 // 30 sec
    private var countDownTimer: CountDownTimer? = null

    private var currentState: SwapUiState? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }


    override fun observeData() {
        // مشاهده UiState
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderStateSwap(state) }
            }
        }

        // مشاهده Navigation Events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelSend.uiState.collect { event ->
                    renderStateSend(event)
                }
            }
        }
        
        // مشاهده Error Events از ViewModel دوم (SendViewModel)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModelSend.uiEvents.collect { event ->
                    handleUiEvent(event)
                }
            }
        }
    }

    override fun setupClickListeners() {

        binding.toolbar.mClick {
            finish()
        }

        // دکمه اصلی اقدام
        binding.btnSwapAction.mClick {
            val currentState = viewModel.uiState.value
            if (currentState is SwapUiState.Ready && currentState.isButtonEnabled) {
                viewModel.onProceedToConfirmation()
            }
        }

        binding.btnAssetFrom.mClick {

            viewModel.onAssetSelectionOpened(isSelectingFrom = true)
            val isBottomSheetShown =
                supportFragmentManager.findFragmentByTag("AssetSelectionBottomSheet") != null
            if (!isBottomSheetShown) {
                AssetSelectionBottomSheet().show(
                    supportFragmentManager,
                    "AssetSelectionBottomSheet"
                )
            }
        }

        binding.btnAssetTo.mClick {

            if ((viewModel.uiState.value as? SwapUiState.Ready)?.isToAssetSelectorEnabled == true) {
                viewModel.onAssetSelectionOpened(isSelectingFrom = false)
            }

            val isBottomSheetShown =
                supportFragmentManager.findFragmentByTag("AssetSelectionBottomSheet") != null
            if (!isBottomSheetShown) {
                AssetSelectionBottomSheet().show(
                    supportFragmentManager,
                    "AssetSelectionBottomSheet"
                )
            }


        }

        binding.ivSwapDirection.mClick {
            val currentState = viewModel.uiState.value
            if (currentState is SwapUiState.Ready && currentState.isButtonEnabled) {
                viewModel.onProceedToConfirmation()
            }
        }
    }


    override fun setupViews() {
        // جلوگیری از فراخوانی‌های مکرر با چک کردن متن فعلی
        binding.etAmountFrom.textChange {
            if (it.toString().isNotEmpty() &&  it.toString() != (viewModel.uiState.value as? SwapUiState.Ready)?.amountIn) {
                viewModel.onAmountInChanged(it.toString())
            }
        }

        receivingOptionsAdapter = ReceivingOptionsAdapter { selectedOption ->
            viewModel.onReceivingOptionSelected(selectedOption)
        }
        binding.rvReceivingOptions.adapter = receivingOptionsAdapter
    }

    private fun renderStateSend(state: SendUiState) {
        when (state) {
            is SendUiState.EnteringDetails -> {
                lifecycleScope.launch {
                    viewModelSend._uiState.update {
                        (it as SendUiState.EnteringDetails).copy(
                            amount = binding.etAmountFrom.text.toString()
                        )
                    }
                    viewModelSend.onContinueToConfirmation()//2
                }
            }

            is SendUiState.Confirmation -> {
                viewModelSend.confirmAndSendTransaction()//3
            }

            is SendUiState.Success -> {
                dismissLoadingDialog()
                Toast.makeText(this, "تراکنش بیت کوین ارسال شد", Toast.LENGTH_LONG).show()
                finish()
            }

            is SendUiState.Error -> {
                dismissLoadingDialog()
                showSnackbar(state.message)
            }

            else -> {
                dismissLoadingDialog()
            }
        }
    }

    private fun renderStateSwap(state: SwapUiState) {
        // مدیریت visibility کلی ویوها

        when (state) {
            is SwapUiState.InitialLoading -> {
                // ProgressBar توسط خطوط بالا مدیریت شد
            }
            is SwapUiState.Ready -> {
                // --- بخش‌های مدیریت پنل From و To (بدون تغییر) ---
                state.selectedFromAsset?.let {
                    binding.btnAssetFrom.text = it.symbol
                    binding.tvBalanceFrom.text = "موجودی: ${it.balance.toPlainString()}"
                } ?: run {
                    binding.btnAssetFrom.text = "انتخاب ارز پرداختی"
                    binding.tvBalanceFrom.text = "موجودی: -"
                }

                state.fromAssets.first().assetId

                // --- پنل To ---
                binding.btnAssetTo.isEnabled = state.isToAssetSelectorEnabled
                state.selectedToAsset?.let {
                    binding.btnAssetTo.text = it.symbol
                    binding.tvBalanceTo.text = "موجودی: ${it.balance.toPlainString()}"
                    //binding.ivAssetTo.load(it.iconUrl)
                } ?: run {
                    binding.btnAssetTo.text = "انتخاب ارز دریافتی"
                    binding.tvBalanceTo.text = "موجودی: -"
                }
                binding.etAmountFrom.isEnabled = state.isAmountInputEnabled
                if (binding.etAmountFrom.text.toString() != state.amountIn) {
                    binding.etAmountFrom.setText(state.amountIn)
                }
                binding.tvAmountTo.text = if (state.isQuoteLoading) "..." else state.amountOut.ifEmpty { "0.0" }

                // --- بخش جدید و بازنویسی شده: نمایش جزئیات Quote ---
                binding.layoutQuoteDetails.isVisible = state.quote != null && !state.isQuoteLoading


                val showDetails = state.quote != null && !state.isQuoteLoading
                binding.layoutQuoteDetails.isVisible = showDetails

                if (showDetails) {
                    val quote = state.quote
                    val selectedOption = state.selectedOption

                    // ۱. نمایش نرخ تبدیل
                    binding.tvRate.text =
                        "1 ${quote.fromAssetSymbol} ≈ ${quote.exchangeRate} ${state.selectedToAsset?.symbol}"
                    binding.tvExchange.text = quote.bestExchange


                    // ۲. نمایش کارمزد پردازش
                    val processingFees = selectedOption?.fees?.details ?: quote.fees?.details
                    if (processingFees != null) {
                        val feeAmount =
                            processingFees.exchangeFee.amount.toBigDecimal() + processingFees.ourFee.amount.toBigDecimal()
                        binding.tvProcessingFee.text =
                            "${feeAmount.toPlainString()} ${processingFees.exchangeFee.asset}"
                    }

                    // ۳. به‌روزرسانی RecyclerView
                    binding.rvReceivingOptions.isVisible = state.receivingOptionsForUI.isNotEmpty()
                    binding.tvNetworkOptionsTitle.isVisible =
                        state.receivingOptionsForUI.isNotEmpty()
                    receivingOptionsAdapter.submitList(state.receivingOptionsForUI)
                    startTimer()
                }
                binding.btnSwapAction.isEnabled = state.isButtonEnabled

                currentState = viewModel.uiState.value

            }
            is SwapUiState.Confirmation -> {
                // این حالت باید توسط یک Dialog یا BottomSheet مدیریت شود
                showConfirmationDialog(state)
                // برای جلوگیری از نمایش همزمان، محتوای اصلی را پنهان می‌کنیم
            }

            is SwapUiState.WaitingForDeposit -> {
                showLoadingDialog("در حال پردازش درخواست شما")
                viewModelSend._uiState.value = SendUiState.SelectingAsset(
                    recipientAddress = state.depositAddress,
                    arrayListOf(state.assetToDeposit)
                )
                viewModelSend.onAssetSelected(state.assetToDeposit)
            }
            is SwapUiState.InProgress -> {
                // این حالت هم باید توسط یک Dialog مدیریت شود
                showLoadingDialog(state.message)
            }
            is SwapUiState.Success -> {
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                dismissLoadingDialog()
                dismissAllDialogs() // بستن دیالوگ‌های قبلی
                finish()
            }
            is SwapUiState.Failure -> {
                dismissAllDialogs()
                dismissLoadingDialog()
                Timber.tag("TEST").e(state.errorMessage)
            }
            else->{}
        }
    }

    // --- توابع کمکی برای مدیریت Dialogها ---


    private fun showConfirmationDialog(state: SwapUiState.Confirmation) {
        AlertDialog.Builder(this)
            .setTitle("تایید معامله")
            .setMessage("آیا از تبدیل ${state.fromDisplay} به ${state.toDisplay} مطمئن هستید؟")
            .setPositiveButton("تایید") { it, click ->
                if (click == -1) {

                    viewModel.executeSwap()
                }

            }
            .setNegativeButton("لغو") { _, _ -> dismissAllDialogs() }
            .show()
    }

    private fun dismissAllDialogs() {
        // TODO: تمام دیالوگ‌های باز را ببندید
    }

    /**
     * دیالوگ Loading را نمایش می‌دهد یا اگر از قبل باز باشد، پیام آن را آپدیت می‌کند.
     */
    private fun showLoadingDialog(message: String) {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialogFragment.newInstance(message)
            loadingDialog?.show(supportFragmentManager, LoadingDialogFragment.TAG)
        } else {
            loadingDialog?.updateMessage(message)
        }
    }

    /**
     * دیالوگ Loading را می‌بندد.
     */
    private fun dismissLoadingDialog() {
        loadingDialog?.dismissAllowingStateLoss()
        loadingDialog = null
    }

    fun startTimer() {
        // لغو تایمر قبلی اگر وجود داشته باشد
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(startTimeInMillis, 1000) { // هر ۱ ثانیه تیک می‌زند
            override fun onTick(millisUntilFinished: Long) {
                binding.progressBar.setProgress(binding.progressBar.progress + 1, true)
            }

            override fun onFinish() {
                // وقتی تایمر تمام شد
                binding.progressBar.progress = 0

                if (binding.etAmountFrom.text.toString().isNotEmpty()) {
                    viewModel.onAmountInChanged(binding.etAmountFrom.text.toString())
                }
                // ریست کردن و شروع دوباره تایمر
                //  resetAndStartTimer()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

}