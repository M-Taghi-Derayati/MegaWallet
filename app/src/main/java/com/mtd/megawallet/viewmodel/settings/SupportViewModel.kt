package com.mtd.megawallet.viewmodel.settings

import android.os.Build
import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IBlockchainConnectionModeProvider
import com.mtd.domain.interfaceRepository.ISupportRepository
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.model.support.SupportArea
import com.mtd.domain.model.support.SupportCategory
import com.mtd.domain.model.support.SupportClientInfo
import com.mtd.domain.model.support.SupportLimits
import com.mtd.domain.model.support.SupportReportRequest
import com.mtd.domain.usecase.wallet.ExpandWalletKeysToNetworksUseCase
import com.mtd.domain.usecase.wallet.GetAllWalletsUseCase
import com.mtd.domain.usecase.wallet.GetWalletKeysForWalletsUseCase
import com.mtd.megawallet.BuildConfig
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

/**
 * حالتِ فلویِ پشتیبانی: کیف‌پول‌ها با آدرسشان، و فرستادنِ گزارش.
 *
 * متنِ فرم (موضوع، شرح، نام، ایمیل) این‌جا نگه داشته می‌شود نه در `remember`ِ صفحه، چون فلو چند
 * گام دارد و چرخشِ صفحه وسطِ گامِ سوم نباید نوشته‌های گامِ دوم را ببرد.
 */
@HiltViewModel
class SupportViewModel @Inject constructor(
    private val getAllWalletsUseCase: GetAllWalletsUseCase,
    private val getWalletKeysForWalletsUseCase: GetWalletKeysForWalletsUseCase,
    private val expandWalletKeysToNetworksUseCase: ExpandWalletKeysToNetworksUseCase,
    private val activeWalletProvider: IActiveWalletProvider,
    private val connectionModeProvider: IBlockchainConnectionModeProvider,
    private val supportRepository: ISupportRepository,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    /**
     * یک کیف‌پول همان‌طور که در ردیفِ «کیف پول» دیده می‌شود.
     *
     * ⚠️ [evmAddress] **دست‌نخورده** است — نه کوچک می‌شود نه نرمال. کوتاه‌سازی فقط سرِ نمایش و در
     * خودِ صفحه انجام می‌شود. `null` یعنی این کیف‌پول اصلاً کلیدِ EVM ندارد و گزارشش بدونِ آدرس
     * می‌رود؛ این حالت باید در UI گفته شود، نه اینکه بی‌صدا رد شود.
     */
    data class SupportWallet(
        val id: String,
        val name: String,
        val color: Int,
        val evmAddress: String?
    )

    sealed interface SubmitState {
        data object Idle : SubmitState
        data object Submitting : SubmitState

        /**
         * ثبت شد.
         *
         * [ticketId] شمارهٔ پیگیری است و می‌تواند `null` باشد؛ گزارش در هر صورت ثبت شده و
         * نبودنش فقط یعنی جملهٔ کوتاه‌تری به کاربر نشان می‌دهیم.
         */
        data class Sent(val ticketId: String?, val duplicate: Boolean) : SubmitState

        /** [message] متنِ آمادهٔ نمایش است، نه متنِ خامِ استثنا. */
        data class Failed(val message: String) : SubmitState
    }

    private val _wallets = MutableStateFlow<List<SupportWallet>>(emptyList())
    val wallets = _wallets.asStateFlow()

    private val _selectedWalletId = MutableStateFlow<String?>(null)
    val selectedWalletId = _selectedWalletId.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState = _submitState.asStateFlow()

    private val _category = MutableStateFlow<SupportCategory?>(null)
    val category = _category.asStateFlow()

    private val _subject = MutableStateFlow("")
    val subject = _subject.asStateFlow()

    private val _description = MutableStateFlow("")
    val description = _description.asStateFlow()

    private val _areas = MutableStateFlow<Set<SupportArea>>(emptySet())
    val areas = _areas.asStateFlow()

    private val _name = MutableStateFlow("")
    val name = _name.asStateFlow()

    private val _email = MutableStateFlow("")
    val email = _email.asStateFlow()

    init {
        launchSafe(checkNetwork = false) { loadWallets() }
    }

    fun selectCategory(value: SupportCategory) {
        _category.value = value
    }

    // ⚠️ سقفِ طول همین‌جا اعمال می‌شود و نه در فیلدِ متنی: این تنها قیفی است که همهٔ ورودی از آن
    // رد می‌شود. سرور هم همین حدود را دارد، و اجازه دادن به تایپِ بیشتر یعنی کاربر چهار گام را
    // پر کند و آخرش `VALIDATION_ERROR` بگیرد.
    fun setSubject(value: String) {
        _subject.value = value.take(SupportLimits.SUBJECT)
    }

    fun setDescription(value: String) {
        _description.value = value.take(SupportLimits.DESCRIPTION)
    }

    fun toggleArea(area: SupportArea) {
        _areas.value = _areas.value.let { current ->
            if (area in current) current - area else current + area
        }
    }

    fun setName(value: String) {
        _name.value = value.take(SupportLimits.NAME)
    }

    fun setEmail(value: String) {
        _email.value = value.take(SupportLimits.EMAIL)
    }

    fun selectWallet(walletId: String) {
        _selectedWalletId.value = walletId
    }

    /** بعد از بسته‌شدنِ فلو، تا بازِ بعدی از صفر شروع شود. */
    fun reset() {
        _category.value = null
        _subject.value = ""
        _description.value = ""
        _areas.value = emptySet()
        _name.value = ""
        _email.value = ""
        _submitState.value = SubmitState.Idle
    }

    /** خطای قبلی پاک شود تا دکمه دوباره قابل زدن باشد. */
    fun clearSubmitFailure() {
        if (_submitState.value is SubmitState.Failed) _submitState.value = SubmitState.Idle
    }

    /**
     * فرستادنِ گزارش.
     *
     * ⚠️ هیچ موفقیتِ ساختگی‌ای این‌جا نیست: تنها مسیرِ [SubmitState.Sent] پاسخِ موفقِ سرور است و
     * هر شکستی همان‌جا به کاربر نشان داده می‌شود. نبودنِ آدرسِ EVM شکست حساب نمی‌شود و جلوی
     * ارسال را نمی‌گیرد — گزارشی که به‌خاطر یک فیلدِ جانبی ثبت نشود، معاملهٔ بدی است.
     */
    fun submit() {
        val selectedCategory = _category.value ?: return
        if (_submitState.value == SubmitState.Submitting) return

        val request = SupportReportRequest(
            category = selectedCategory,
            subject = _subject.value,
            description = _description.value,
            areas = _areas.value.toList(),
            name = _name.value,
            email = _email.value,
            walletAddress = selectedWallet()?.evmAddress,
            client = clientInfo()
        )

        // ⚠️ «در حال ارسال» **داخلِ** بلوک ست می‌شود، نه بیرونش: اگر دستگاه آفلاین باشد
        // `launchSafe` بلوک را اصلاً اجرا نمی‌کند، و ست‌کردنِ بیرونی دکمه را تا ابد در حالتِ
        // چرخان جا می‌گذاشت.
        launchSafe {
            _submitState.value = SubmitState.Submitting

            when (val result = supportRepository.submitReport(request)) {
                is ResultResponse.Success -> {
                    val receipt = result.data
                    _submitState.value = SubmitState.Sent(
                        ticketId = receipt.ticketId,
                        duplicate = receipt.duplicate
                    )
                    // شیت با رسیدنِ `Sent` بسته می‌شود، پس شمارهٔ پیگیری جایی جز اسنک‌بار برای
                    // دیده‌شدن ندارد.
                    showSuccess(receiptMessage(receipt.ticketId, receipt.duplicate))
                }

                is ResultResponse.Error -> {
                    _submitState.value = SubmitState.Failed(
                        userMessageFor(
                            throwable = result.exception,
                            fallbackMessage = "گزارش فرستاده نشد. اتصال‌تان را بررسی کنید و دوباره تلاش کنید."
                        )
                    )
                    reportError(
                        throwable = result.exception,
                        userAction = "submitSupportReport",
                        surface = ErrorSurface.SILENT,
                        severity = ErrorSeverity.LOW
                    )
                }
            }
        }
    }

    fun selectedWallet(): SupportWallet? =
        _wallets.value.firstOrNull { it.id == _selectedWalletId.value }

    /**
     * نسخه و دستگاه، همراهِ گزارش.
     *
     * ⚠️ هیچ شناسهٔ پایداری این‌جا نیست. اینها برای بازتولیدِ یک اشکال‌اند: بدونِ نسخهٔ برنامه
     * بیشترِ گزارش‌ها غیرقابلِ پیگیری‌اند، و [connectionMode] تعیین می‌کند خواندن‌ها از RPC
     * مستقیم آمده یا از پراکسی — که خودش نیمی از اشکال‌های «موجودی اشتباه است» را توضیح می‌دهد.
     */
    private fun clientInfo(): SupportClientInfo = SupportClientInfo(
        appVersion = BuildConfig.VERSION_NAME,
        appBuild = BuildConfig.VERSION_CODE,
        platform = "android",
        osVersion = Build.VERSION.RELEASE.orEmpty(),
        deviceModel = Build.MODEL.orEmpty(),
        locale = Locale.getDefault().toLanguageTag(),
        connectionMode = connectionModeProvider.currentMode().name
    )

    /**
     * جملهٔ رسید.
     *
     * ⚠️ حالتِ تکراری جملهٔ خودش را دارد. برای کاربر شکست نیست — گزارشش ثبت شده — ولی اگر همان
     * «گزارش ثبت شد» را ببیند، فکر می‌کند دو تیکت ساخته و دوباره پیگیری می‌کند.
     */
    private fun receiptMessage(ticketId: String?, duplicate: Boolean): String = when {
        ticketId.isNullOrBlank() -> "گزارش شما ثبت شد."
        duplicate -> "این گزارش پیش‌تر ثبت شده بود. شمارهٔ پیگیری: $ticketId"
        else -> "گزارش شما ثبت شد. شمارهٔ پیگیری: $ticketId"
    }

    /**
     * کیف‌پول‌ها با آدرسِ EVMشان.
     *
     * `wallet.keys` عکسِ لحظهٔ ساختِ کیف‌پول است و شبکه‌های بعدی در آن نیستند، پس کلیدها با
     * [ExpandWalletKeysToNetworksUseCase] روی کاتالوگِ فعلی باز می‌شوند — همان مسیری که صفحهٔ
     * انتخابِ گیرنده هم می‌رود. هیچ کلیدی این‌جا از نو مشتق نمی‌شود.
     *
     * آدرسِ EVM روی همهٔ زنجیره‌های EVM یکی است، پس اولین کلیدِ EVM هر کدام که باشد جوابِ درست
     * است و لازم نیست شبکهٔ خاصی انتخاب شود.
     */
    private suspend fun loadWallets() {
        val wallets = when (val result = getAllWalletsUseCase()) {
            is ResultResponse.Success -> result.data
            is ResultResponse.Error -> return reportLoadFailure(result.exception)
        }
        if (wallets.isEmpty()) return

        val keysByWalletId =
            when (val result = getWalletKeysForWalletsUseCase(wallets.map { it.id })) {
                is ResultResponse.Success -> result.data
                is ResultResponse.Error -> return reportLoadFailure(result.exception)
            }

        _wallets.value = wallets.map { wallet ->
            val keys = expandWalletKeysToNetworksUseCase(keysByWalletId[wallet.id].orEmpty())
            SupportWallet(
                id = wallet.id,
                name = wallet.name,
                color = wallet.color,
                evmAddress = keys.firstOrNull { it.networkType == NetworkType.EVM }?.address
            )
        }

        // کیف‌پولِ فعال از پیش انتخاب می‌شود؛ اگر قفل باشد، اولین کیف‌پول.
        _selectedWalletId.value = activeWalletProvider.activeWalletId.value
            ?.takeIf { id -> _wallets.value.any { it.id == id } }
            ?: _wallets.value.firstOrNull()?.id
    }

    /**
     * فهرست خالی می‌ماند و صفحه خودش می‌گوید کیف‌پولی پیدا نشد. گزارش همچنان فرستادنی است — فقط
     * بدونِ آدرس — پس اسنک‌بارِ خطا این‌جا فقط سر و صدا بود.
     */
    private suspend fun reportLoadFailure(throwable: Throwable) {
        reportError(
            throwable = throwable,
            userAction = "loadWalletsForSupportReport",
            surface = ErrorSurface.SILENT,
            severity = ErrorSeverity.LOW
        )
    }
}
