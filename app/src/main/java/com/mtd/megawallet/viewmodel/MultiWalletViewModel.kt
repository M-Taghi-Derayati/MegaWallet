package com.mtd.megawallet.viewmodel

import android.content.Intent
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.core.utils.BalanceFormatter
import com.mtd.domain.interfaceRepository.IFiatCurrencyProvider
import com.mtd.domain.interfaceRepository.IUsdToIrrRateProvider
import com.mtd.domain.model.DriveBackupState
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.Wallet
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.domain.usecase.wallet.BackupWalletToCloudUseCase
import com.mtd.domain.usecase.wallet.DeleteWalletUseCase
import com.mtd.domain.usecase.wallet.GetAllWalletsUseCase
import com.mtd.domain.usecase.wallet.GetActiveWalletIdUseCase
import com.mtd.domain.usecase.wallet.GetCachedWalletBalanceUseCase
import com.mtd.domain.usecase.wallet.GetWalletMnemonicUseCase
import com.mtd.domain.usecase.wallet.HasCloudBackupUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletIdUseCase
import com.mtd.domain.usecase.wallet.SwitchActiveWalletUseCase
import com.mtd.domain.usecase.wallet.SyncWalletBalancesUseCase
import com.mtd.domain.usecase.wallet.UpdateWalletBackupStatusUseCase
import com.mtd.domain.usecase.wallet.UpdateWalletColorUseCase
import com.mtd.domain.usecase.wallet.UpdateWalletNameUseCase
import com.mtd.domain.usecase.wallet.importwallet.ConnectCloudBackupUseCase
import com.mtd.domain.usecase.wallet.importwallet.GetCloudSignInIntentUseCase
import com.mtd.domain.usecase.wallet.importwallet.IsCloudBackupConnectedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class MultiWalletViewModel @Inject constructor(
    private val getAllWalletsUseCase: GetAllWalletsUseCase,
    private val observeActiveWalletIdUseCase: ObserveActiveWalletIdUseCase,
    private val getActiveWalletIdUseCase: GetActiveWalletIdUseCase,
    private val getCachedWalletBalanceUseCase: GetCachedWalletBalanceUseCase,
    /** TASK-56 — selected fiat unit; the wallet totals below are rendered in it. */
    private val fiatCurrencyProvider: IFiatCurrencyProvider,
    /** TASK-56 — USD→تومان rate; `null` renders as a placeholder, never as zero. */
    private val usdToIrrRateProvider: IUsdToIrrRateProvider,
    private val syncWalletBalancesUseCase: SyncWalletBalancesUseCase,
    private val switchActiveWalletUseCase: SwitchActiveWalletUseCase,
    private val deleteWalletUseCase: DeleteWalletUseCase,
    private val updateWalletBackupStatusUseCase: UpdateWalletBackupStatusUseCase,
    private val updateWalletNameUseCase: UpdateWalletNameUseCase,
    private val updateWalletColorUseCase: UpdateWalletColorUseCase,
    private val getWalletMnemonicUseCase: GetWalletMnemonicUseCase,
    private val hasCloudBackupUseCase: HasCloudBackupUseCase,
    private val backupWalletToCloudUseCase: BackupWalletToCloudUseCase,
    private val isCloudBackupConnectedUseCase: IsCloudBackupConnectedUseCase,
    private val getCloudSignInIntentUseCase: GetCloudSignInIntentUseCase,
    private val connectCloudBackupUseCase: ConnectCloudBackupUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private val _wallets = MutableStateFlow<List<WalletUiItem>>(emptyList())
    val wallets = _wallets.asStateFlow()

    /**
     * TASK-56 — the wallet switcher was the one fiat surface the USD⇄تومان toggle could not reach: the
     * data layer returned an already-formatted `"$1,234"`, so the currency was fixed before this
     * ViewModel ever saw the number. Totals are now held raw in [WalletUiItem.totalUsd] and re-rendered
     * whenever the currency OR the rate changes — the rate matters too, because a تومان total is
     * wrong until the first Wallex value lands.
     */
    private fun observeFiatDisplayInputs() {
        launchSafe(checkNetwork = false) { fiatCurrencyProvider.ensurePrimed() }
        launchSafe(checkNetwork = false) {
            combine(fiatCurrencyProvider.currency, usdToIrrRateProvider.rate) { _, _ -> Unit }
                .collect { reformatTotals() }
        }
    }

    private fun reformatTotals() {
        _wallets.update { items -> items.map { it.copy(totalBalance = formatTotal(it.totalUsd)) } }
    }

    private fun formatTotal(totalUsd: BigDecimal): String = BalanceFormatter.formatFiatValue(
        usdAmount = totalUsd,
        currency = fiatCurrencyProvider.currency.value,
        rate = usdToIrrRateProvider.rate.value
    )

    val activeWalletId = observeActiveWalletIdUseCase()

    data class WalletUiItem(
        val wallet: Wallet,
        /**
         * TASK-56 — the raw **USD** total. Kept alongside the rendered [totalBalance] so a currency
         * switch or a fresh Wallex rate can re-format the list with no cache re-read.
         */
        val totalUsd: BigDecimal = BigDecimal.ZERO,
        val totalBalance: String = "$0",
        val isActive: Boolean = false,
        val isManualBackedUp: Boolean = false,
        val isCloudBackedUp: Boolean = false
    )

    init {
        launchSafe {
            loadWallets()
        }
        observeFiatDisplayInputs()
    }

    suspend fun loadWallets(forceRefresh: Boolean = false) {
        when (val result = getAllWalletsUseCase()) {
            is ResultResponse.Success -> {
                val walletList = result.data
                val currentActiveId = getActiveWalletIdUseCase()
                
                // 1. نمایش سریع اطلاعات از کش (بدون درنگ)
                updateWalletsUi(walletList, currentActiveId)

                // 2. آپدیت اطلاعات از شبکه به صورت Batch (یکجا)
                refreshAllWalletsBalances(walletList, forceRefresh)
            }
            is ResultResponse.Error -> {
                // User asked for the list and it is now empty on screen — worth a snackbar.
                reportError(
                    throwable = result.exception,
                    userAction = "loadWallets",
                    surface = ErrorSurface.SNACKBAR,
                    fallbackMessage = "خطا در دریافت لیست کیف پول‌ها"
                )
            }
        }
    }

    private suspend fun updateWalletsUi(walletList: List<Wallet>, activeId: String?) {
        // انتقال محاسبات سنگین به ترد Default برای جلوگیری از لگ UI
        val items = withContext(kotlinx.coroutines.Dispatchers.Default) {
            val deferredItems = walletList.map { wallet ->
                async {
                    val totalUsd = try {
                        getCachedWalletBalanceUseCase(wallet.id)
                    } catch (e: Exception) {
                        // Cache read — a miss just means we show zero until the network sync lands.
                        // Logged, never surfaced (ErrorSurface.SILENT).
                        reportError(
                            throwable = e,
                            userAction = "getCachedWalletBalance",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
                        BigDecimal.ZERO
                    }
                    WalletUiItem(
                        wallet = wallet,
                        totalUsd = totalUsd,
                        totalBalance = formatTotal(totalUsd),
                        isActive = wallet.id == activeId,
                        isManualBackedUp = wallet.isManualBackedUp,
                        isCloudBackedUp = wallet.isCloudBackedUp
                    )
                }
            }
            deferredItems.awaitAll()
        }
        _wallets.value = items
    }

    private val isRefreshing = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * دریافت موجودی تمام کیف پول‌ها از شبکه به صورت بهینه (Batch Request)
     * این متد تمام کیف پول‌ها را جمع کرده و در قالب یک درخواست به نود اتریوم (یا سایر) می‌فرستد.
     */
    private suspend fun refreshAllWalletsBalances(wallets: List<Wallet>, forceResync: Boolean) {
        if (wallets.isEmpty()) return

        if (isRefreshing.getAndSet(true)) {
            return
        }

        try {
            val activeId = getActiveWalletIdUseCase()
            syncWalletBalancesUseCase(wallets, activeId, forceResync)
            updateWalletsUi(wallets, activeId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Background balance refresh — the cached figures stay on screen and the next open
            // retries, so this must not nag (ErrorSurface.SILENT).
            reportError(
                throwable = e,
                userAction = "refreshAllWalletsBalances",
                surface = ErrorSurface.SILENT,
                severity = ErrorSeverity.LOW
            )
        } finally {
            isRefreshing.set(false)
        }
    }

    fun switchWallet(walletId: String) {
        launchSafe {
            when (val result = switchActiveWalletUseCase(walletId)) {
                is ResultResponse.Success -> {
                    // تغییر موفقیت‌آمیز بود
                }
                is ResultResponse.Error -> {
                    reportError(
                        throwable = result.exception,
                        userAction = "switchWallet",
                        surface = ErrorSurface.SNACKBAR,
                        fallbackMessage = "خطا در تغییر کیف پول"
                    )
                }
            }
        }
    }

    fun deleteWallet(walletId: String) {
        launchSafe {
            when (val result = deleteWalletUseCase(walletId)) {
                is ResultResponse.Success -> {
                    loadWallets(forceRefresh = true)
                    showSuccess("کیف پول حذف شد")
                }
                is ResultResponse.Error -> {
                    // TASK-57 — was `showSnackbar(result.toString())`: it printed the raw
                    // ResultResponse.Error object (exception text and all) straight to the user, and
                    // the `?:` fallback was dead because toString() is never null. Deletion is
                    // destructive, so a failure blocks rather than flashes past.
                    reportError(
                        throwable = result.exception,
                        userAction = "deleteWallet",
                        surface = ErrorSurface.BLOCKING,
                        severity = ErrorSeverity.HIGH,
                        fallbackMessage = "خطا در حذف کیف پول"
                    )
                }
            }
        }
    }

    fun updateBackupStatus(walletId: String, manual: Boolean? = null, cloud: Boolean? = null) {
        launchSafe {
            updateWalletBackupStatusUseCase(walletId, manual, cloud)
            loadWallets(forceRefresh = false) // رفرش لیست برای نمایش سریع تغییر در UI
        }
    }

    suspend fun updateWalletName(walletId: String, newName: String) {
        updateWalletNameUseCase(walletId, newName)
        loadWallets(forceRefresh = false)
    }

    suspend fun updateWalletColor(walletId: String, newColor: Int) {
        updateWalletColorUseCase(walletId, newColor)
        loadWallets(forceRefresh = false)
    }

    suspend fun getMnemonic(walletId: String): String? {
        return getWalletMnemonicUseCase(walletId)
    }

    suspend fun hasCloudBackup(): Boolean {
        return hasCloudBackupUseCase()
    }

    fun isCloudConnected(): Boolean = isCloudBackupConnectedUseCase()

    fun getCloudSignInIntent(): Intent = getCloudSignInIntentUseCase()

    suspend fun handleCloudGoogleSignInResult(data: Intent?): ResultResponse<Boolean> {
        return when (val result = connectCloudBackupUseCase(data)) {
            is ResultResponse.Success -> {
                ResultResponse.Success(result.data == DriveBackupState.BackupFound)
            }
            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }

    suspend fun backupWalletToCloud(walletId: String, password: String): ResultResponse<Unit> {
        return backupWalletToCloudUseCase(walletId, password)
    }
}
