package com.mtd.megawallet.ui.importoption

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.blankj.utilcode.util.KeyboardUtils
import com.google.android.material.chip.Chip
import com.mtd.common_ui.gone
import com.mtd.common_ui.mClick
import com.mtd.common_ui.setTextColorAnim
import com.mtd.common_ui.show
import com.mtd.core.model.Bip39Words
import com.mtd.core.utils.Easings
import com.mtd.core.utils.Interpolators
import com.mtd.megawallet.R
import com.mtd.megawallet.core.BaseFragment
import com.mtd.megawallet.databinding.FragmentImportSeedPhraseBinding
import com.mtd.megawallet.event.OnboardingNavigationEvent
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.event.ValidationResult
import com.mtd.megawallet.viewbinding.viewBinding
import com.mtd.megawallet.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@AndroidEntryPoint
class ImportSeedPhraseFragment :
    BaseFragment<FragmentImportSeedPhraseBinding, OnboardingViewModel>(R.layout.fragment_import_seed_phrase) {

    override val viewModel: OnboardingViewModel by hiltNavGraphViewModels(R.id.onboarding_graph)
    override val binding by viewBinding(FragmentImportSeedPhraseBinding::bind)
    private var isWord: String? = null
    private var isPrivateKey = false

    private lateinit var isViewLastSelectMenuBasketStatus: AppCompatTextView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewLastSelectMenuBasketStatus = binding.txtMenuWords
        viewModel.resetStateForNewScreen(OnboardingUiState.EnteringSeed())
        KeyboardUtils.showSoftInput(binding.editTextNextWord)
    }


    override fun setupViews() {




        binding.editTextNextWord.doAfterTextChanged { editable ->
            val text = editable.toString()
            if (text.isNotBlank()) {
                val word = text.trim()
               val isValidWord = Bip39Words.English.find { itFi -> itFi == word }
                isPrivateKey = viewModel.isPrivateKey(word)
                if (isValidWord != null || isPrivateKey) {
                    // کلمه معتبره، اضافه کن و EditText رو خالی کن
                    isWord=word
                    addWord(word)
                }
            } else {
                // آپدیت لحظه‌ای برای نمایش پیشنهاد
                viewModel.onImportInputChanged(getCurrentMnemonicText() + text)
            }
        }

        binding.editTextNextWord.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                // اینجا بک‌اسپیس زده شد
                if (binding.editTextNextWord.text.isNullOrEmpty()  || isPrivateKey) {
                    viewModel.onLastWordRemoved()
                    true
                } else {
                   false
                }
            } else {
                false
            }
        }

    }

    override fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.buttonImport.mClick {
            // یک لحظه کوتاه صبر می‌کنیم تا State آپدیت شود و بعد import می‌کنیم
            viewLifecycleOwner.lifecycleScope.launch {
                delay(50) // Small delay to allow state to update
                viewModel.importFromInput()
            }
        }
        // با کلیک روی کل ناحیه، فوکوس به EditText داده می‌شود
        binding.flexboxLayout.mClick { KeyboardUtils.showSoftInput(binding.editTextNextWord) }

        binding.btnPaste.mClick {
            pasteFromClipboard()
        }

        binding.txtMenuWords.mClick {
            mAnimBasketVisualSelect(binding.txtMenuWords)
            binding.flexboxLayout.show()
            binding.editTextSeedPharse.gone()
        }

        binding.txtMenuSeedPharse.mClick {
            mAnimBasketVisualSelect(binding.txtMenuSeedPharse)
            binding.flexboxLayout.gone()
            binding.editTextSeedPharse.show()
        }

    }


    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // یک Coroutine جدا برای جمع‌آوری وضعیت UI
                launch {
                    viewModel.uiState.collect { state ->
                        handleUiState(state)
                    }
                }

                // یک Coroutine جدا برای جمع‌آوری ایونت‌های ناوبری
                launch {
                    viewModel.navigationEvent.collect { event ->
                        handleNavigationEvent(event)
                    }
                }
            }
        }
    }


    private fun pasteFromClipboard() {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!pasteData.isNullOrBlank()) {
            isPrivateKey = viewModel.isPrivateKey(pasteData)
            if (!isPrivateKey) isWord=pasteData
            viewModel.onImportInputChanged(pasteData)
        }
    }


    private fun getCurrentMnemonicText(): String {
        return (viewModel.uiState.value as? OnboardingUiState.EnteringSeed)?.enteredWords?.joinToString(
            " "
        ) ?: ""
    }


    private fun handleUiState(state: OnboardingUiState) {
        // مدیریت وضعیت Loading به صورت کلی
        val isLoading = state is OnboardingUiState.Loading
        binding.progressBar.isVisible = isLoading
        binding.buttonImport.isEnabled = !isLoading
        binding.btnPaste.isEnabled = !isLoading
        binding.editTextNextWord.isEnabled = !isLoading

        // مدیریت کادر خطا
        val errorReason =
            (state as? OnboardingUiState.EnteringSeed)?.validationResult as? ValidationResult.Invalid
        if (errorReason != null && errorReason.reason.isNotEmpty()) {
            binding.cardInputArea.strokeColor =
                ContextCompat.getColor(requireContext(), com.mtd.common_ui.R.color.semantic_error)
            // TODO: Show this error in a dedicated TextView for better UX
        } else {
            binding.cardInputArea.strokeColor =
                ContextCompat.getColor(requireContext(), android.R.color.transparent)
        }

        // مدیریت وضعیت‌های خاص
        when (state) {
            is OnboardingUiState.EnteringSeed -> {
                // دکمه "وارد کردن" فقط در صورت معتبر بودن State فعال می‌شود
                binding.buttonImport.isEnabled =
                    if (state.validationResult is ValidationResult.Valid && !isLoading) {
                        KeyboardUtils.hideSoftInput(requireActivity())
                        true
                    } else {
                        false
                    }
                // آپدیت کردن چیپ‌ها و EditText

                updateChipsAndEditText(
                    words = state.enteredWords,
                    currentWord = state.currentWord,
                    isPrivateKey = state.isPrivateKey
                )
            }

            is OnboardingUiState.Error -> {
                // نمایش خطاهای کلی (مثل خطای شبکه یا import)
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.errorShown() // اطلاع به ViewModel که خطا نمایش داده شد
            }
            // برای حالت‌های Idle و Loading، کارهای عمومی قبلاً انجام شده است
            else -> {
                // در حالت Idle یا Loading، ممکن است بخواهیم چیپ‌ها را پاک کنیم
                if (binding.flexboxLayout.childCount > 1) {
                    binding.flexboxLayout.removeViews(0, binding.flexboxLayout.childCount - 1)
                }
            }
        }
    }


    /**
     * مسئولیت این متد، اجرای دستورات ناوبری است.
     */
    private fun handleNavigationEvent(event: OnboardingNavigationEvent) {
        when (event) {
            is OnboardingNavigationEvent.NavigateToSelectWallets -> {
                val action = ImportSeedPhraseFragmentDirections.actionImportSeedToSelectWallets(event.mnemonic?:"",event.privateKey?:"")
                findNavController().navigate(action)
            }
            // بقیه ایونت‌های ناوبری (در این فرگمنت نیازی نیست)
            else -> {}
        }
    }

    private fun addWord(word: String) {
        if (viewModel.isPrivateKey(word)) {
            viewModel.onImportInputChanged(word)
        } else {
            val currentText = getCurrentMnemonicText()
            viewModel.onImportInputChanged(if (currentText.isEmpty()) word else "$currentText $word")
            binding.editTextNextWord.setText("")
        }


    }

    /**
     * آپدیت چیپ‌ها بر اساس کلمات و نوع ورودی (Mnemonic یا Private Key)
     */


    private fun updateChipsAndEditText(
        words: List<String>,
        currentWord: String,
        isPrivateKey: Boolean
    ) {
        // --- ۱. بازسازی چیپ‌ها ---

        // کلمات فعلی که به صورت چیپ نمایش داده شده‌اند را می‌خوانیم
        val currentChipWords = (0 until binding.flexboxLayout.childCount - 1).map { index ->
            (binding.flexboxLayout.getChildAt(index) as Chip).text.toString().substringAfter(". ")
        }


        // فقط در صورتی چیپ‌ها را از نو می‌سازیم که لیست کلمات تایید شده تغییر کرده باشد
        if (isPrivateKey){
            binding.editTextSeedPharse.setText(currentWord)
        }else{
            if (currentChipWords != words) {
                if (binding.flexboxLayout.childCount > 1) {
                    binding.flexboxLayout.removeViews(0, binding.flexboxLayout.childCount - 1)
                }
                words.forEachIndexed { index, word ->
                    val chip = layoutInflater.inflate(
                        R.layout.item_mnemonic_chip,
                        binding.flexboxLayout,
                        false
                    ) as Chip
                    chip.text = "${index + 1}. $word"
                    chip.setOnCloseIconClickListener {
                        viewModel.onWordRemoved(index)
                    }
                    binding.flexboxLayout.addView(chip, index)
                }
            }
            binding.editTextNextWord.setText("")
            binding.editTextNextWord.hint = "${words.size+1}..."
        }
    }


    private fun mAnimBasketVisualSelect(view: AppCompatTextView) {
        operator.translateLeftRight(binding.vSelectMenu, view.x, 500)
            .doOnSubscribe {
                mAnimMenuBasketStatus(view)
            }
            .subscribe()

    }

    private fun mAnimMenuBasketStatus(view: AppCompatTextView) {
        if (isViewLastSelectMenuBasketStatus != view) {
            isViewLastSelectMenuBasketStatus.setTextColorAnim(
                com.mtd.common_ui.R.color.text_primary,
                com.mtd.common_ui.R.color.text_secondary,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            view.setTextColorAnim(
                com.mtd.common_ui.R.color.text_secondary,
                com.mtd.common_ui.R.color.text_primary,
                Interpolators(Easings.CUBIC_IN_OUT)
            )
            isViewLastSelectMenuBasketStatus = view
        }
    }

}