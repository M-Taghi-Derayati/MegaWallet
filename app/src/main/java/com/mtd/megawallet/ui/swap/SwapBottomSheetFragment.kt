package com.mtd.megawallet.ui.swap

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtd.common_ui.mClick
import com.mtd.megawallet.databinding.BottomSheetSwapBinding
import com.mtd.megawallet.event.SendUiState
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.ui.swap.adapter.ReceivingOptionsAdapter
import com.mtd.megawallet.viewmodel.SendViewModel
import com.mtd.megawallet.viewmodel.SwapViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import com.google.android.material.R as material

@AndroidEntryPoint
class SwapBottomSheetFragment(val amount: String) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSwapBinding? = null
    private val binding get() = _binding!!

    val SwapviewModel: SwapViewModel by activityViewModels()
    val viewModelSend: SendViewModel by activityViewModels()

    private var loadingDialog: LoadingDialogFragment? = null

    private var receivingOptionsAdapter: ReceivingOptionsAdapter? = null

    private var currentState: SwapUiState? = null


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(material.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                // --- ۲. از حالت نیمه‌باز رد شو (اختیاری ولی مفید) ---
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetSwapBinding.inflate(inflater, container, false)


        receivingOptionsAdapter = ReceivingOptionsAdapter { selectedOption ->
            SwapviewModel.onReceivingOptionSelected(selectedOption)
        }
        binding.rvReceivingOptions.adapter = receivingOptionsAdapter

        amount.let {
            if (it.toString().isNotEmpty()) {
                SwapviewModel.onAmountInChanged(it.toString())
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeData()
        binding.btnSwapAction.mClick {
            val currentState = SwapviewModel.uiState.value
            if (currentState is SwapUiState.Ready && currentState.isButtonEnabled) {
                SwapviewModel.onProceedToConfirmation()
            }
        }


    }


     fun observeData() {

         viewLifecycleOwner.lifecycleScope.launch {
             SwapviewModel.uiState.collect { state ->

                     renderStateSwap(state)

             }
         }


        // مشاهده Navigation Events
         viewLifecycleOwner.lifecycleScope.launch {
                viewModelSend.uiState.collect { event ->
                    renderStateSend(event)
                }
            }

    }


    private fun renderStateSend(state: SendUiState) {
        when (state) {
            is SendUiState.EnteringDetails -> {
                lifecycleScope.launch {
                    viewModelSend._uiState.update {
                        (it as SendUiState.EnteringDetails).copy(amount = amount)
                    }
                    viewModelSend.onContinueToConfirmation()//2
                }
            }

            is SendUiState.Confirmation -> {
                viewModelSend.confirmAndSendTransaction()//3
            }

            is SendUiState.Success -> {
                dismissLoadingDialog()
                Toast.makeText(requireActivity(), "تراکنش بیت کوین ارسال شد", Toast.LENGTH_LONG).show()
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
               /* state.selectedFromAsset?.let {
                    binding.btnAssetFrom.text = it.symbol
                    binding.tvBalanceFrom.text = "موجودی: ${it.balance.toPlainString()}"
                } ?: run {
                    binding.btnAssetFrom.text = "انتخاب ارز پرداختی"
                    binding.tvBalanceFrom.text = "موجودی: -"
                }



                if (binding.etAmountFrom.text.toString() != state.amountIn) {
                    binding.etAmountFrom.setText(state.amountIn)
                }*/
//                binding.tvAmountTo.text = if (state.isQuoteLoading) "..." else state.amountOut.ifEmpty { "0.0" }

                // --- بخش جدید و بازنویسی شده: نمایش جزئیات Quote ---


                val showDetails = state.quote != null && !state.isQuoteLoading

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
                    receivingOptionsAdapter?.submitList(state.receivingOptionsForUI)
                }
                binding.btnSwapAction.isEnabled = state.isButtonEnabled

                currentState = SwapviewModel.uiState.value

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
                Toast.makeText(requireActivity(), state.message, Toast.LENGTH_LONG).show()
                dismissLoadingDialog()
                dismiss()
            }
            is SwapUiState.Failure -> {

                dismissLoadingDialog()
                Timber.tag("TEST").e(state.errorMessage)
            }
            else->{}
        }
    }

    private fun showConfirmationDialog(state: SwapUiState.Confirmation) {
        AlertDialog.Builder(requireActivity())
            .setTitle("تایید معامله")
            .setMessage("آیا از تبدیل ${state.fromDisplay} به ${state.toDisplay} مطمئن هستید؟")
            .setPositiveButton("تایید") { it, click ->
                if (click == -1) {

                    SwapviewModel.executeSwap()
                }

            }
            .setNegativeButton("لغو") { _, _ -> this.dismiss() }
            .show()
    }


    /**
     * دیالوگ Loading را نمایش می‌دهد یا اگر از قبل باز باشد، پیام آن را آپدیت می‌کند.
     */
    private fun showLoadingDialog(message: String) {
        if (loadingDialog == null) {
            loadingDialog = LoadingDialogFragment.newInstance(message)
            loadingDialog?.show(parentFragmentManager, LoadingDialogFragment.TAG)
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



    override fun onDestroyView() {
        receivingOptionsAdapter = null
        _binding = null
        super.onDestroyView()

    }

}