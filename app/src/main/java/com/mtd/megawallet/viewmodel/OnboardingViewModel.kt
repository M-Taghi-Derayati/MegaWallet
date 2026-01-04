package com.mtd.megawallet.viewmodel


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
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.AccountInfo
import com.mtd.megawallet.event.OnboardingNavigationEvent
import com.mtd.megawallet.event.OnboardingUiState
import com.mtd.megawallet.event.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val backupRepository: IBackupRepository,
    private val keyManager: KeyManager,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val blockchainRegistry: BlockchainRegistry,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

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
        launchSafe {
            when (val result = walletRepository.createNewWallet("",0)) {
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
            return
        }
        _uiState.value = OnboardingUiState.Loading("در حال وارد کردن کیف پول...")
        val inputToImport = currentState.enteredWords.joinToString(" ")

        launchSafe {
            val result = if (currentState.isPrivateKey) {
                walletRepository.importWalletFromPrivateKey(CryptoUtils.validateAndExtractPrivateKey(inputToImport).privateKeyHex!!,"",0)
            } else {
                walletRepository.importWalletFromMnemonic(inputToImport,"",0)
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
        launchSafe {
            val keys = if (mnemonic.isEmpty()) {
                keyManager.generateWalletKeysFromPrivateKey(
                    CryptoUtils.validateAndExtractPrivateKey(privateKey).privateKeyHex!!
                )
            } else {
                keyManager.generateWalletKeysFromMnemonic(mnemonic)
            }

            val accountsInfo = mutableListOf<AccountInfo>()

            // هر account را مستقل fetch می‌کنیم
            keys.forEach { key ->
                launchSafe {
                    val dataSource = dataSourceFactory.create(key.chainId ?: -1)
                    val balanceResult = if (key.networkType == NetworkType.EVM) {
                        val evm = dataSource.getBalanceEVM(key.address)
                        if (evm is ResultResponse.Success) {
                            evm.data.find { itFind -> itFind.contractAddress == null }?.balance
                        } else {
                            BigInteger.ZERO
                        }
                    } else {
                        val network = dataSource.getBalance(key.address)
                        if (network is ResultResponse.Success) {
                            network.data
                        } else {
                            BigInteger.ZERO
                        }
                    }

                    val networkInfo = blockchainRegistry.getNetworkByName(key.networkName)
                    val formattedBalance = BalanceFormatter.formatBalance(
                        rawBalance = balanceResult ?: BigInteger.ZERO,
                        decimals = networkInfo?.decimals ?: 18,
                    )

                    val accountInfo = AccountInfo(
                        id = key.networkName.name,
                        networkName = networkInfo?.name?.name ?: "",
                        address = "${key.address.take(6)}...${key.address.takeLast(4)}",
                        balance = formattedBalance,
                        balanceUsd = "$0.00",
                        iconUrl = networkInfo?.iconUrl,
                        derivationPath = key.derivationPath ?: "N/A"
                    )

                    synchronized(accountsInfo) {
                        accountsInfo.add(accountInfo)
                        _uiState.value = OnboardingUiState.WalletsToImport(
                            isLoading = accountsInfo.size < keys.size,
                            accounts = accountsInfo.toList(),
                            selectedAccountIds = accountsInfo.map { it.id }.toSet()
                        )
                    }
                }
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

            launchSafe {
                val result=  if (mnemonic.isEmpty()){
                    walletRepository.importWalletFromPrivateKey(CryptoUtils.validateAndExtractPrivateKey(privateKey).privateKeyHex!!,"",0)
                }else{
                    walletRepository.importWalletFromMnemonic(mnemonic,"",0)
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
