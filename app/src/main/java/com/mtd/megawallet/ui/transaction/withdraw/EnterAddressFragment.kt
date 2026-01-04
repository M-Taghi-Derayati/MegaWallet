package com.mtd.megawallet.ui.transaction.withdraw

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtd.common_ui.gone
import com.mtd.common_ui.loaded
import com.mtd.common_ui.show
import com.mtd.common_ui.textChange
import com.mtd.common_ui.toStyledAddress
import com.mtd.megawallet.databinding.FragmentEnterAddressBinding
import com.mtd.megawallet.event.SendUiState
import com.mtd.megawallet.ui.transaction.adapter.SelectAssetToSendAdapter
import com.mtd.megawallet.viewmodel.SendViewModel
import com.ncorti.slidetoact.SlideToActView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import com.google.android.material.R as material


@AndroidEntryPoint
class EnterAddressFragment: BottomSheetDialogFragment()  {
    private val viewModel: SendViewModel by viewModels()

    private var _binding: FragmentEnterAddressBinding? = null
    private val binding get() = _binding!!
    private lateinit var assetsAdapter: SelectAssetToSendAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(material.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                // --- ۱. حالت اولیه را روی کاملاً باز تنظیم کن ---
                val topPaddingPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    60f,
                    resources.displayMetrics
                ).toInt()
                // behavior.expandedOffset=topPaddingPx
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                // --- ۲. از حالت نیمه‌باز رد شو (اختیاری ولی مفید) ---
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEnterAddressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // ریست کردن State برای شروع تمیز این صفحه
        viewModel.resetToEnteringAddress()

        setupViews()
        setupClickListeners()
        observeData()
    }

     fun setupViews() {
        binding.editTextAddress.doAfterTextChanged { text ->
            viewModel.onAddressChanged(text.toString())
        }

         assetsAdapter = SelectAssetToSendAdapter { selectedAsset ->
             viewModel.onAssetSelected(selectedAsset)//1
         }
         binding.recyclerViewAssets.adapter = assetsAdapter

         binding.editTextSearch.doAfterTextChanged { text ->
             viewModel.onSearchQueryChanged(text.toString())
         }

         binding.editTextAmount.textChange { text ->
             val currentState = viewModel.uiState.value
             // فقط اگر مقدار واقعاً توسط کاربر تغییر کرده، به ViewModel اطلاع بده
             if (currentState is SendUiState.EnteringDetails && text.toString() != currentState.amount) {
                 viewModel.onAmountChanged(text.toString())
             }
         }
    }

     fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonPaste.setOnClickListener {
            pasteFromClipboard()
        }


         binding.buttonContinueAddress.setOnClickListener {
            viewModel.onAddressEntered()
        }

         binding.buttonContinueSend.setOnClickListener {
             viewLifecycleOwner.lifecycleScope.launch {
                 viewModel.onContinueToConfirmation()//2
             }
         }

         binding.buttonMax.setOnClickListener { viewModel.onMaxButtonClicked() }

         // **مهم:** اینجا ما متن کامل رو به ViewModel پاس میدیم
         binding.autocompleteFee.setOnItemClickListener { parent, _, position, _ ->
             val selectedFeeText = parent.adapter.getItem(position) as String
             viewModel.onFeeLevelSelected(selectedFeeText)
         }

         binding.slideConfirmWithdraw.onSlideCompleteListener=object :SlideToActView.OnSlideCompleteListener{
             override fun onSlideComplete(view: SlideToActView) {
                 viewModel.confirmAndSendTransaction()//3
             }

         }
    }

     fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleUiState(state)
                }
            }
        }
    }

    private fun handleUiState(state: SendUiState) {
        when(state){
            is SendUiState.EnteringAddress->{
                binding.editTextAddress.error = state.error
            }
            is SendUiState.SelectingAsset->{
                assetsAdapter.submitList(state.compatibleAssets)
                binding.linShowAddress.gone()
                binding.linShowAssets.show()
            }
            is SendUiState.EnteringDetails->{
                state.validationError

                binding.linShowAddress.gone()
                binding.linShowAssets.gone()
                binding.linShowDetail.show()
                binding.toolbar.title = "ارسال ${state.selectedAsset?.symbol}"
                binding.textBalance.text = "موجودی: ${state.selectedAsset?.balance}"
                binding.textAmountUsd.text = state.amountUsd

                // آپدیت کردن منوی کارمزد با متن‌های نمایشی
                val feeOptionsDisplay = state.feeOptions.map { "${it.level} - ${it.feeAmountDisplay}" }
                val feeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, feeOptionsDisplay)
                binding.autocompleteFee.setAdapter(feeAdapter)
                if (binding.editTextAmount.text.toString() != state.amount) {
                    binding.editTextAmount.setText(state.amount)
                }
                state.selectedFee?.let {
                    val selectedText = "${it.level} - ${it.feeAmountDisplay}"
                    // جلوگیری از آپدیت‌های غیرضروری
                    if (binding.autocompleteFee.text.toString() != selectedText) {
                        binding.autocompleteFee.setText(selectedText, false)
                    }
                }

                // کنترل فعال بودن دکمه و نمایش خطا
                binding.buttonContinueAddress.isEnabled = state.validationError == null && (state.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO > BigDecimal.ZERO)
               // binding.linDetailSend.inputLayoutAmount.error = state.validationError // فرض می‌کنیم TextInputLayout یک id دارد
            }
            is SendUiState.Confirmation->{
                binding.linShowDetail.gone()
                binding.linConfirmation.show()
                // بخش هدر دارایی
                binding.imageAssetIcon.loaded(state.asset.iconUrl!!)
                binding.textAssetSymbol.text = state.asset.symbol
                binding.textAmountAsset.text = "-${state.amount}" // نمایش مقدار منفی

                // بخش From/To
                binding.textFromAddress.text = state.fromAddress.toShortenedAddress()
                binding.textToAddress.text = state.recipientAddress.toShortenedAddress()
                val highlightColor = ContextCompat.getColor(requireContext(), com.mtd.common_ui.R.color.text_primary)
                // بخش لیست جزئیات
                binding.textAmountDetailsValue.text = state.amountDisplay
                binding.textSendingToValue.text = state.recipientAddress.toStyledAddress(
                    context = requireContext(),
                    startChars = 6,
                    endChars = 6,
                    highlightColor = highlightColor
                ) // نمایش آدرس کامل
                binding.textNetworkFeeValue.text = "${state.fee.level} ${state.fee.feeAmountDisplay}"

            }
            is SendUiState.Success->{
                Timber.tag("TEST").d(state.txHash)
                dismiss()
            }
            is SendUiState.Error->{
                Timber.tag("TEST").d(state.message)
            }
            else->{
            }
        }

    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!pasteData.isNullOrBlank()) {
            binding.editTextAddress.setText(pasteData)
        }
    }

    fun String.toShortenedAddress(startChars: Int = 6, endChars: Int = 6): String {
        return if (this.length > startChars + endChars) {
            "${this.take(startChars)}...${this.takeLast(endChars)}"
        } else {
            this
        }
    }
}