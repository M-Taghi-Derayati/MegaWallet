package com.mtd.megawallet.ui.swap

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mtd.common_ui.mClick
import com.mtd.common_ui.textChange
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentSwapBinding
import com.mtd.megawallet.event.SwapNavigationEvent
import com.mtd.megawallet.event.SwapUiState
import com.mtd.megawallet.viewmodel.SwapViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode


@AndroidEntryPoint
class SwapFragment : BaseFragment<FragmentSwapBinding, SwapViewModel>(R.layout.fragment_swap) {

    override val viewModel: SwapViewModel by viewModels()
    private var _binding: FragmentSwapBinding? = null
    override val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSwapBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun observeData() {
        // مشاهده UiState
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> renderState(state) }
            }
        }

        // مشاهده Navigation Events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is SwapNavigationEvent.NavigateToSendScreen -> {
                            // استفاده از Navigation Component برای رفتن به SendFragment
                            // و پاس دادن آرگومان‌ها
                            /*val action = SwapFragmentDirections.actionSwapFragmentToSendFragment(
                                assetId = event.assetId,
                                recipientAddress = event.recipientAddress,
                                amount = event.amount,
                                isReadOnly = event.isReadOnly
                            )
                            findNavController().navigate(action)*/
                        }
                    }
                }
            }
        }
    }

    override fun setupClickListeners() {

        // دکمه اصلی اقدام
        binding.btnSwapAction.mClick {
            val currentState = viewModel.uiState.value
            if (currentState is SwapUiState.Ready && currentState.isButtonEnabled) {
                viewModel.onProceedToConfirmation()
            }
        }

        // TODO: در آینده برای باز کردن لیست انتخاب دارایی
        binding.btnAssetFrom.mClick {
            // TODO: یک BottomSheet برای انتخاب دارایی مبدا باز کن
            val currentState = viewModel.uiState.value as? SwapUiState.Ready
            viewModel.onFromAssetSelected(currentState?.fromAssets?.first()?.assetId?:"")
        }

        binding.btnAssetTo.mClick {
            val currentState = viewModel.uiState.value as? SwapUiState.Ready
            if (currentState?.isToAssetSelectorEnabled == true) {
                // TODO: یک BottomSheet برای انتخاب دارایی مقصد باز کن
               viewModel.onToAssetSelected(currentState.toAssets.first().assetId)
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
    }

    private fun renderState(state: SwapUiState) {
        // مدیریت visibility کلی ویوها
       // binding.progressBar.isVisible = state is SwapUiState.InitialLoading
       // binding.contentGroup.isVisible = state is SwapUiState.Ready

        when (state) {
            is SwapUiState.InitialLoading -> {
                // ProgressBar توسط خطوط بالا مدیریت شد
            }
            is SwapUiState.Ready -> {
                // --- پنل From ---
                state.selectedFromAsset?.let {
                    binding.btnAssetFrom.text = it.symbol
                    binding.tvBalanceFrom.text = "موجودی: ${it.balance.toPlainString()}"
                   // binding.ivAssetFrom.load(it.iconUrl) { error(R.drawable.ic_placeholder_coin) }
                } ?: run {
                    binding.btnAssetFrom.text = "انتخاب ارز"
                    binding.tvBalanceFrom.text = "موجودی: -"
                }

                state.fromAssets.first().assetId

                // --- پنل To ---
                binding.btnAssetTo.isEnabled = state.isToAssetSelectorEnabled
                state.selectedToAsset?.let {
                    binding.btnAssetTo.text = it.symbol
                    binding.tvBalanceTo.text = "موجودی: ${it.balance.toPlainString()}"
                   // binding.ivAssetTo.load(it.iconUrl) { error(R.drawable.ic_placeholder_coin) }
                } ?: run {
                    binding.btnAssetTo.text = "انتخاب ارز"
                    binding.tvBalanceTo.text = "موجودی: -"
                }

                binding.etAmountFrom.isEnabled = state.isAmountInputEnabled
                if (binding.etAmountFrom.text.toString() != state.amountIn) {
                    binding.etAmountFrom.setText(state.amountIn)
                }
                binding.tvAmountTo.text = if (state.isQuoteLoading) "..." else state.amountOut.ifEmpty { "0.0" }

                // --- جزئیات و دکمه ---
                binding.layoutQuoteDetails.isVisible = state.quote != null && !state.isQuoteLoading
                state.quote?.let { quote ->
                    // محاسبه نرخ تبدیل برای نمایش
                    val amountIn = quote.fromAmount.toBigDecimalOrNull()
                    val amountOut = quote.receiveAmount.toBigDecimalOrNull()

                    if (amountIn != null && amountOut != null && amountIn > BigDecimal.ZERO) {
                        val rate = amountOut.divide(amountIn, 6, RoundingMode.HALF_UP) // تا ۶ رقم اعشار
                        binding.tvRate.text = "1 ${quote.fromAssetSymbol} ≈ ${rate.toPlainString()} ${quote.receiveAssetSymbol}"
                    } else {
                        binding.tvRate.text = "-" // اگر محاسبه ممکن نبود
                    }

                    // محاسبه درصد کارمزد برای نمایش
                    val feeAmount = quote.feeAmount.toBigDecimalOrNull()
                    if (amountIn != null && feeAmount != null && amountIn > BigDecimal.ZERO) {
                        val feePercentage = (feeAmount.divide(amountIn, 4, RoundingMode.HALF_UP) * BigDecimal(100))
                        binding.tvFee.text = "${quote.feeAmount} ${quote.feeAssetSymbol} (${feePercentage.toPlainString()}%)"
                    } else {
                        binding.tvFee.text = "${quote.feeAmount} ${quote.feeAssetSymbol}"
                    }
                }
                binding.btnSwapAction.isEnabled = state.isButtonEnabled
                // ... (آپدیت کردن متن دکمه)
            }
            is SwapUiState.Confirmation -> {
                // این حالت باید توسط یک Dialog یا BottomSheet مدیریت شود
                showConfirmationDialog(state)
                // برای جلوگیری از نمایش همزمان، محتوای اصلی را پنهان می‌کنیم

            }
            is SwapUiState.InProgress -> {
                // این حالت هم باید توسط یک Dialog مدیریت شود
                showProgressDialog(state.message)
            }
            is SwapUiState.Success -> {
                dismissAllDialogs() // بستن دیالوگ‌های قبلی
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                // بازگشت به صفحه اصلی یا نمایش رسید
                // findNavController().popBackStack()
            }
            is SwapUiState.Failure -> {
                dismissAllDialogs()
                Timber.tag("TEST").e(state.errorMessage)
            }
            else->{}
        }
    }

    // --- توابع کمکی برای مدیریت Dialogها ---

    private fun showConfirmationDialog(state: SwapUiState.Confirmation) {
        // TODO: در اینجا یک BottomSheetDialogFragment سفارشی را با داده‌های state نمایش دهید.
        // مثال ساده با دیالوگ استاندارد:
        // AlertDialog.Builder(requireContext())
        //     .setTitle("تایید معامله")
        //     .setMessage("آیا از تبدیل ${state.fromDisplay} به ${state.toDisplay} مطمئن هستید؟")
        //     .setPositiveButton("تایید") { _, _ -> viewModel.executeSwap() }
        //     .setNegativeButton("لغو") { _, _ -> viewModel.navigateBackToReadyState() }
        //     .show()
        viewModel.executeSwap()
    }

    private fun showProgressDialog(message: String) {
        // TODO: یک دیالوگ لودینگ سفارشی نمایش دهید
    }

    private fun dismissAllDialogs() {
        // TODO: تمام دیالوگ‌های باز را ببندید
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}