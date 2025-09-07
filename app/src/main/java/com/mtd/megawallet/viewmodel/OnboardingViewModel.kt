package com.mtd.megawallet.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtd.core.keymanager.KeyManager
import com.mtd.core.keymanager.MnemonicHelper
import com.mtd.core.model.Bip39Words
import com.mtd.core.model.NetworkType
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.CryptoUtils
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IBackupRepository
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.megawallet.event.AccountInfo
import com.mtd.megawallet.event.OnboardingNavigationEvent
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.event.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val backupRepository: IBackupRepository,
    private val keyManager: KeyManager,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val blockchainRegistry: BlockchainRegistry
) : ViewModel() {

    // --- State & Event Flows ---

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<OnboardingNavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    // --- User Actions ---

    /**
     * فراخوانی می‌شود وقتی کاربر روی دکمه "ساخت کیف پول جدید" کلیک می‌کند.
     */
    fun createNewWallet() {
        _uiState.value = OnboardingUiState.Loading("در حال ساخت کیف پول...")
        viewModelScope.launch {
            when (val result = walletRepository.createNewWallet()) {
                is ResultResponse.Success -> {
                    val mnemonic = result.data.mnemonic
                    if (mnemonic != null) {
                        _navigationEvent.emit(
                            OnboardingNavigationEvent.NavigateToShowMnemonic(
                                mnemonic.split(" ")
                            )
                        )
                    } else {
                        _uiState.value =
                            OnboardingUiState.Error("خطای ناشناخته در ساخت عبارت بازیابی")
                    }
                }

                is ResultResponse.Error -> {
                    _uiState.value = OnboardingUiState.Error(
                        result.exception.message ?: "خطایی در ساخت کیف پول رخ داد"
                    )
                }
            }
        }
    }

    /**
     * فراخوانی می‌شود وقتی متن در صفحه وارد کردن عبارت/کلید خصوصی تغییر می‌کند.
     */
    fun onImportInputChanged(input: String) {
        val trimmedInput = input.trim()
        val words = if (trimmedInput.isEmpty()) emptyList() else trimmedInput.split(Regex("\\s+"))

        val currentTypingWord = words.lastOrNull() ?: ""
        val suggestion = if (currentTypingWord.length > 1) {
            Bip39Words.English.firstOrNull { it.startsWith(currentTypingWord) }
        } else {
            null
        }

        val validation = validateImportInput(trimmedInput)
        _uiState.value = OnboardingUiState.EnteringSeed(
            enteredWords = words, // کلماتی که کامل شدن
            currentWord = currentTypingWord,
            suggestion = suggestion,
            isPrivateKey = isPrivateKey(trimmedInput),
            validationResult = validation
        )
    }

    /**
     * فراخوانی می‌شود وقتی کاربر روی دکمه "وارد کردن" کلیک می‌کند.
     */
    fun importFromInput() {
        val currentState = _uiState.value
        if (currentState !is OnboardingUiState.EnteringSeed || currentState.validationResult !is ValidationResult.Valid) {
            return // اگر وضعیت فعلی معتبر نیست، کاری انجام نده
        }
        _uiState.value = OnboardingUiState.Loading("در حال وارد کردن کیف پول...")
        val inputToImport = currentState.enteredWords.joinToString(" ")

        viewModelScope.launch {
            val result = if (currentState.isPrivateKey) {
                walletRepository.importWalletFromPrivateKey(CryptoUtils.validateAndExtractPrivateKey(inputToImport).privateKeyHex!!)
            } else {
                walletRepository.importWalletFromMnemonic(inputToImport)
            }

            when (result) {
                is ResultResponse.Success -> {
                    val privateKey = if (result.data.mnemonic == null) inputToImport else null
                    _navigationEvent.emit(
                        OnboardingNavigationEvent.NavigateToSelectWallets(
                            result.data.mnemonic,
                            privateKey
                        )
                    )
                }

                is ResultResponse.Error -> {
                    _uiState.value =
                        OnboardingUiState.Error(result.exception.message ?: "ورودی نامعتبر است")
                }
            }
        }
    }

    /**
     * فراخوانی می‌شود بعد از اینکه یک پیام خطا به کاربر نمایش داده شد.
     */
    fun errorShown() {
        _uiState.value = OnboardingUiState.Idle
    }

    // --- Private Helper Functions ---

    fun isPrivateKey(input: String): Boolean {
        // به جای چک کردن ساده طول، از فانکشن کامل و دقیق استفاده می‌کنیم.
        return CryptoUtils.validateAndExtractPrivateKey(input).isValid
    }

    private fun validateImportInput(input: String): ValidationResult {
        if (input.isEmpty()) {
            return ValidationResult.Invalid("") // هنوز ورودی‌ای وجود ندارد
        }

        if (isPrivateKey(input)) {
            return ValidationResult.Valid // فرمت کلید خصوصی صحیح است
        }

        val words = input.split(Regex("\\s+"))
        val wordCount = words.size

        if (wordCount > 24) {
            return ValidationResult.Invalid("عبارت بازیابی نمی‌تواند بیشتر از ۲۴ کلمه باشد.")
        }

        // فقط زمانی اعتبارسنجی نهایی را انجام می‌دهیم که تعداد کلمات کامل باشد
        if (wordCount == 12 || wordCount == 24) {
            return if (MnemonicHelper.isValidMnemonic(input)) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid("عبارت بازیابی وارد شده معتبر نیست.")
            }
        }

        return ValidationResult.Loading
    }

    fun onLastWordRemoved() {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.EnteringSeed && currentState.enteredWords.isNotEmpty()) {
            val lastWord = currentState.enteredWords.last()
            val remainingWords = currentState.enteredWords.dropLast(1)

            // آپدیت State با کلمات باقیمانده و قرار دادن کلمه حذف شده در EditText برای ویرایش
            _uiState.update {
                (it as OnboardingUiState.EnteringSeed).copy(
                    enteredWords = remainingWords,
                    currentWord = lastWord
                )
            }
        }
    }

    fun onWordRemoved(index: Int) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.EnteringSeed) {
            val currentWords = currentState.enteredWords.toMutableList()
            if (index in currentWords.indices) {
                currentWords.removeAt(index)
                onImportInputChanged(currentWords.joinToString(" "))
            }
        }
    }

    fun resetStateForNewScreen(targetState: OnboardingUiState = OnboardingUiState.EnteringSeed()) {
        _uiState.value = targetState
    }


    fun discoverAccountsFromMnemonic(mnemonic: String, privateKey: String) {
        _uiState.value = OnboardingUiState.WalletsToImport(isLoading = true)
        viewModelScope.launch {
            try {
                // ۱. ساخت تمام کلیدهای ممکن از Mnemonic
                val keys = if (mnemonic.isEmpty()) {
                    keyManager.generateWalletKeysFromPrivateKey(
                        CryptoUtils.validateAndExtractPrivateKey(
                            privateKey
                        ).privateKeyHex!!
                    )
                } else {
                    keyManager.generateWalletKeysFromMnemonic(mnemonic)
                }

                // ۲. گرفتن موجودی برای هر کلید به صورت موازی برای سرعت بیشتر
                val accountsInfoAsync = keys.map { key ->
                    async { // هر درخواست در یک Coroutine جداگانه اجرا می‌شود
                        val dataSource =
                            dataSourceFactory.create(key.chainId ?: -1) // -1 for Bitcoin
                        val balanceResult = if (key.networkType == NetworkType.EVM) {
                            val evm = dataSource.getBalanceEVM(key.address)
                            val balance = if (evm is ResultResponse.Success) {
                                evm.data.find { itFind -> itFind.contractAddress == null }?.balance
                            } else {
                                BigInteger.ZERO
                            }
                            balance
                        } else {
                            val network = dataSource.getBalance(key.address)
                            val balance = if (network is ResultResponse.Success) {
                                network.data
                            } else {
                                BigInteger.ZERO
                            }
                            balance
                        }

                        val networkInfo = blockchainRegistry.getNetworkByName(key.networkName)


                      val formattedBalance= BalanceFormatter.formatBalance(
                            rawBalance = balanceResult?: BigInteger.ZERO,
                            decimals = networkInfo?.decimals?:18,
                        )

                        AccountInfo(
                            id = key.networkName.name,
                            networkName = networkInfo?.name?.name?:"",
                            address = "${key.address.take(6)}...${key.address.takeLast(4)}",
                            balance = formattedBalance,
                            balanceUsd = "$0.00",
                            iconUrl = networkInfo?.iconUrl,
                            derivationPath = key.derivationPath ?: "N/A"
                        )
                    }
                }

                val accountsInfo = accountsInfoAsync.awaitAll()

                // آپدیت UI با لیست حساب‌های کشف شده
                _uiState.value = OnboardingUiState.WalletsToImport(
                    isLoading = false,
                    accounts = accountsInfo,
                    selectedAccountIds = accountsInfo.map { it.id }
                        .toSet() // انتخاب همه به صورت پیش‌فرض
                )
            } catch (e: Exception) {
                _uiState.value =
                    OnboardingUiState.Error("خطایی در هنگام بررسی حساب‌ها رخ داد: ${e.message}")
            }
        }
    }


    fun onAccountSelectionChanged(accountId: String, isSelected: Boolean) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.WalletsToImport) {

            // لیست جدید حساب‌ها را با وضعیت isSelected آپدیت شده می‌سازیم
            val updatedAccounts = currentState.accounts.map { account ->
                if (account.id == accountId) {
                    account.copy(isSelected = isSelected)
                } else {
                    account
                }
            }

            // لیست جدید ID های انتخاب شده را می‌سازیم
            val updatedSelectedIds = updatedAccounts
                .filter { it.isSelected }
                .map { it.id }
                .toSet()

            _uiState.value = currentState.copy(
                accounts = updatedAccounts,
                selectedAccountIds = updatedSelectedIds
            )
        }
    }

    /**
     * فراخوانی می‌شود وقتی کاربر روی دکمه "Import" نهایی کلیک می‌کند.
     */
    fun importSelectedAccounts(mnemonic: String,privateKey:String) {
        val currentState = _uiState.value
        if (currentState is OnboardingUiState.WalletsToImport) {
            if (currentState.selectedAccountIds.isEmpty()) {
                _uiState.value = OnboardingUiState.Error("حداقل یک حساب باید انتخاب شود.")
                return
            }

            _uiState.value = OnboardingUiState.Loading("در حال ذخیره کیف پول...")

            // ما فقط عبارت بازیابی را ذخیره می‌کنیم.
            // در آینده، می‌توانیم انتخاب‌های کاربر را هم در SharedPreferences ذخیره کنیم
            // تا در صفحه اصلی فقط شبکه‌های انتخاب شده نمایش داده شوند.
            viewModelScope.launch {
                val result=  if (mnemonic.isEmpty()){
                    walletRepository.importWalletFromPrivateKey(CryptoUtils.validateAndExtractPrivateKey(privateKey).privateKeyHex!!)
                }else{
                    walletRepository.importWalletFromMnemonic(mnemonic)
                }


                when (result) {
                    is ResultResponse.Success -> {
                        _navigationEvent.emit(OnboardingNavigationEvent.NavigateToHome)
                    }

                    is ResultResponse.Error -> {
                        _uiState.value = OnboardingUiState.Error(
                            result.exception.message ?: "خطایی در ذخیره کیف پول رخ داد."
                        )
                    }
                }
            }
        }
    }

}
