package com.mtd.megawallet.viewmodel.tokens

import com.mtd.core.manager.ErrorManager
import com.mtd.core.manager.ErrorSeverity
import com.mtd.domain.interfaceRepository.IManageableAssetCatalog
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.ITokenDiscoveryRepository
import com.mtd.domain.interfaceRepository.IUserTokenRepository
import com.mtd.domain.interfaceRepository.ManageableAsset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.assets.DiscoveredToken
import com.mtd.domain.model.assets.UserToken
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.error.ErrorSurface
import com.mtd.domain.usecase.auth.EnsureAuthenticatedUseCase
import com.mtd.domain.usecase.wallet.GetActiveAddressForNetworkUseCase
import com.mtd.domain.usecase.wallet.ObserveActiveWalletIdUseCase
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject

/** از کدام منبع به فهرست رسیده — فقط برای گروه‌بندیِ نمایشی. */
enum class TokenRowSource { DEFAULT, CATALOG, SEARCH }

data class NetworkOption(
    val id: String,
    val label: String,
    val iconUrl: String?
)

data class TokenRowUi(
    /** کلیدِ یکتا در همهٔ منابع و همهٔ زنجیره‌ها: `networkId:contract` یا `native:<assetId>`. */
    val key: String,
    val assetId: String,
    val networkId: String,
    /** نامِ نمایشیِ شبکه — در نمای cross-network تنها چیزی است که دو ردیفِ `USDT` را از هم جدا می‌کند. */
    val networkLabel: String,
    val networkIconUrl: String?,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val contractAddress: String?,
    val iconUrl: String?,
    /** الان در لیستِ کیف پول دیده می‌شود؟ */
    val isAdded: Boolean,
    /** از فهرستِ خودِ کاربر آمده، نه از باندلِ امضاشده. */
    val isUserAdded: Boolean,
    /** کوینِ اصلیِ شبکه — فقط پنهان‌شدنی است، حذف‌شدنی نیست. */
    val isNative: Boolean,
    val source: TokenRowSource,
    /**
     * **نشانِ منشأ، نه نشانِ ایمنی.** `true` ⇒ نشانِ «ثبت‌شده در فهرست‌های معتبر»؛ `false` ⇒ هشدارِ
     * خنثی؛ `null` ⇒ هیچ‌کدام (سرور چیزی نگفته — همهٔ ردیف‌های باندل این‌طورند).
     *
     * هرگز import یا ارسال را گیت نمی‌کند.
     */
    val verified: Boolean?,
    /** `null` **عمدی**: قیمتِ نامعلوم «—» است، نه `$0`. */
    val priceUsd: BigDecimal?,
    /** در حالِ اعمالِ تغییر — برای غیرفعال‌کردنِ کلیدِ همان ردیف. */
    val isBusy: Boolean = false
)

data class ManageTokensUiState(
    val networks: List<NetworkOption> = emptyList(),
    /** `null` یعنی «همهٔ شبکه‌ها» — حالتِ پیش‌فرض. فیلتر است، نه پیش‌شرط. */
    val selectedNetworkId: String? = null,
    val query: String = "",
    val rows: List<TokenRowUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    /** خطای درون‌صفحه‌ای — این صفحه یک شیت است و نباید برای هر شکستِ جست‌وجو snackbar بیندازد. */
    val inlineError: String? = null,
    /** کوئری شبیهِ آدرسِ قرارداد است ⇒ مسیرِ resolve و راهنمای مربوط به آن. */
    val isContractQuery: Boolean = false,
    /** آدرسِ paste‌شده روی چند زنجیره پیدا شد و انتخاب با کاربر است. */
    val hasMultipleResolveMatches: Boolean = false
)

/**
 * صفحهٔ مدیریتِ توکن — §5 «کشفِ توکن».
 *
 * **فهرستِ پیش‌فرض با یک فراخوانی** از `GET /networks/{id}/tokens?address=` می‌آید و دیگر دستی از
 * (باندل + held) سرِ هم نمی‌شود. دلیلش کیفیت است، نه تعدادِ درخواست: فهرستِ قبلی سرِ آرایهٔ خودِ
 * aggregator بود که هیچ سیگنالِ کیفیتی ندارد — روی ترون سه جعلِ USDT با قیمت‌های ساختگی جلوتر از
 * تترِ واقعی می‌نشستند. **ترتیبِ آرایهٔ سرور حفظ می‌شود** (verified اول، بعد رتبهٔ ارزشِ بازار)؛
 * هیچ مرتب‌سازیِ محلی‌ای اعمال نمی‌شود.
 *
 * جست‌وجو **به‌صورت پیش‌فرض cross-network** است: کسی که `USDT` تایپ می‌کند می‌خواهد ببیند روی کدام
 * زنجیره‌ها وجود دارد، نه اینکه اول مجبور شود شبکه را حدس بزند. انتخابِ شبکه یک باریک‌کننده است؛
 * وقتی انتخاب شود مسیرِ per-network استفاده می‌شود که سبک‌تر است.
 *
 * یکتاسازی همه‌جا با `networkId:contractAddress`ِ lowercase انجام می‌شود — در نمای cross-network
 * همین کلید است که `USDT` روی اتریوم را از `USDT` روی BSC جدا نگه می‌دارد. توجه: lowercase فقط در
 * همین کلید است؛ خودِ آدرس هرجا فرستاده می‌شود عیناً می‌رود (base58ِ ترون حساس به حروف است).
 *
 * نوشتن همیشه از [IUserTokenRepository] رد می‌شود و خواندنِ وضعیت از [IManageableAssetCatalog] —
 * این صفحه ادغامِ خودش را ندارد.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ManageTokensViewModel @Inject constructor(
    private val networkCatalog: INetworkCatalog,
    private val manageableAssetCatalog: IManageableAssetCatalog,
    private val userTokenRepository: IUserTokenRepository,
    private val tokenDiscoveryRepository: ITokenDiscoveryRepository,
    private val getActiveAddressForNetworkUseCase: GetActiveAddressForNetworkUseCase,
    private val observeActiveWalletIdUseCase: ObserveActiveWalletIdUseCase,
    private val ensureAuthenticatedUseCase: EnsureAuthenticatedUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private companion object {
        /**
         * اندپوینتِ جست‌وجو سمتِ سرور mirror شده و ارزان است، ولی باتری نه: با هر کاراکتر یک
         * درخواست یعنی رادیو مدامِ بیدار. نسخهٔ cross-network سنگین‌تر هم هست.
         */
        const val SEARCH_DEBOUNCE_MS = 200L
        const val MIN_QUERY_LENGTH = 2
    }

    private val _uiState = MutableStateFlow(ManageTokensUiState())
    val uiState = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    /**
     * پاسخِ فهرستِ پیش‌فرض به تفکیکِ شبکه، **به ترتیبِ خودِ سرور**. یک بار در هر بازشدنِ شیت پر
     * می‌شود و بعد فقط خوانده.
     */
    private val defaultByNetwork = mutableMapOf<String, List<DiscoveredToken>>()
    private var searchResults: List<DiscoveredToken> = emptyList()

    /** شبکه‌های کاندیدِ نمایش — همان‌هایی که چیپ دارند. */
    private var candidateNetworkIds: List<String> = emptyList()

    /**
     * جست‌وجوی در جریان. هر کلید که زده می‌شود این cancel می‌شود؛ نتیجهٔ کهنه فقط دور ریخته نمی‌شود،
     * خودِ درخواست هم نیمه‌کاره رها می‌شود — که در نسخهٔ cross-network تفاوتِ واقعی در مصرف دارد.
     */
    private var searchJob: Job? = null
    private var defaultJobs: List<Job> = emptyList()

    /** کیف پولی که [defaultByNetwork] به آن تعلق دارد. */
    private var listWalletId: String? = null

    init {
        observeQuery()
        observeSelection()
        observeWalletSwitch()
    }

    /**
     * این ViewModel از شیت عمر بیشتری دارد و [defaultByNetwork] بین بازشدن‌ها کش می‌ماند، پس بدونِ
     * این، عوض‌کردنِ کیف پول فهرستِ کیف پولِ قبلی را روی صفحه نگه می‌داشت — و چون `address` در
     * فراخوانیِ فهرستِ پیش‌فرض می‌رود، آن فهرست واقعاً به کیف پول وابسته است.
     *
     * جریانِ [userTokenRepository.selection] برای این کار کافی نیست: اگر هر دو کیف پول انتخابِ
     * خالی داشته باشند مقدار یکسان است و `StateFlow` اصلاً emit نمی‌کند.
     */
    private fun observeWalletSwitch() {
        launchSafe(checkNetwork = false) {
            observeActiveWalletIdUseCase().collect { walletId ->
                if (walletId == listWalletId) return@collect
                listWalletId = walletId

                defaultJobs.forEach { it.cancel() }
                defaultByNetwork.clear()

                if (candidateNetworkIds.isNotEmpty()) {
                    val selected = _uiState.value.selectedNetworkId
                    loadDefaultTokens(selected?.let { listOf(it) } ?: candidateNetworkIds)
                }
                rebuildRows()
            }
        }
    }

    /**
     * شیت باز شد. شبکه‌ها از کاتالوگ خوانده می‌شوند (که خودش ترجیحِ نمایشِ تست‌نت را رعایت کرده)
     * و فقط خانواده‌های قرارداددار می‌مانند: روی UTXO چیزی به‌نامِ توکن وجود ندارد که مدیریت شود.
     */
    fun onOpened() {
        val options = networkCatalog.getAllNetworkInfos()
            .filter { it.networkType == NetworkType.EVM || it.networkType == NetworkType.TVM }
            .map { NetworkOption(id = it.id, label = it.faName ?: it.id, iconUrl = it.iconUrl) }

        candidateNetworkIds = options.map { it.id }

        // فیلترِ قبلی اگر هنوز معتبر است بماند؛ وگرنه به «همه» برگرد.
        val current = _uiState.value.selectedNetworkId?.takeIf { it in candidateNetworkIds }
        _uiState.update { it.copy(networks = options, selectedNetworkId = current) }

        loadDefaultTokens(current?.let { listOf(it) } ?: candidateNetworkIds)
        rebuildRows()
    }

    /** `null` = همهٔ شبکه‌ها. */
    fun selectNetwork(networkId: String?) {
        if (_uiState.value.selectedNetworkId == networkId) return
        _uiState.update { it.copy(selectedNetworkId = networkId, inlineError = null) }

        // فهرستِ هر شبکه کش می‌شود، پس باریک‌کردن یا بازکردنِ فیلتر درخواستِ تازه نمی‌خواهد
        // مگر برای شبکه‌هایی که هنوز نگرفته‌ایم.
        loadDefaultTokens(networkId?.let { listOf(it) } ?: candidateNetworkIds)

        // کوئری حفظ می‌شود — کاربر دارد همان جست‌وجو را باریک می‌کند، نه از نو شروع.
        val query = _uiState.value.query.trim()
        if (query.length >= MIN_QUERY_LENGTH) runSearch(query) else rebuildRows()
    }

    fun onQueryChange(value: String) {
        _uiState.update {
            it.copy(
                query = value,
                isContractQuery = looksLikeContractAddress(value),
                inlineError = null,
                hasMultipleResolveMatches = false
            )
        }
        queryFlow.value = value
    }

    private fun observeQuery() {
        launchSafe(checkNetwork = false) {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { raw ->
                    val query = raw.trim()
                    if (query.length < MIN_QUERY_LENGTH) {
                        searchJob?.cancel()
                        searchResults = emptyList()
                        _uiState.update { it.copy(isSearching = false) }
                        rebuildRows()
                    } else {
                        runSearch(query)
                    }
                }
        }
    }

    /** انتخابِ کاربر بیرون از این صفحه هم عوض می‌شود (سوئیچِ کیف پول)؛ فهرست باید دنبالش برود. */
    private fun observeSelection() {
        launchSafe(checkNetwork = false) {
            userTokenRepository.selection.collect { rebuildRows() }
        }
    }

    /**
     * فهرستِ پیش‌فرضِ هر شبکه — کاتالوگِ ما + رتبهٔ ارزشِ بازار + توکن‌های واقعاً داشتهٔ این آدرس، با
     * **یک** درخواست.
     *
     * `address` عمداً پاس داده می‌شود: بدونِ آن، توکنی که کاربر دارد ولی بیرون از ۱۰۰تای اول است از
     * فهرست غیب می‌شود. از تاریخچهٔ از قبل ایندکس‌شده خوانده می‌شود، پس فراخوانیِ اضافه‌ای به
     * provider نمی‌زند.
     */
    private fun loadDefaultTokens(networkIds: List<String>) {
        val missing = networkIds.filter { it !in defaultByNetwork }
        if (missing.isEmpty()) return

        defaultJobs.forEach { it.cancel() }
        _uiState.update { it.copy(isLoading = true) }

        defaultJobs = missing.map { networkId ->
            // شکستِ این فراخوانی صفحه را از کار نمی‌اندازد: فهرستِ باندل همچنان نمایش داده
            // می‌شود، پس خطا درون‌صفحه‌ای است نه snackbar.
            launchSafe(connectivitySurface = ErrorSurface.SILENT) {
                // آدرس اختیاری است — نبودنش فقط یعنی فهرست بدونِ توکن‌های خودِ کاربر، نه شکست.
                val address = getActiveAddressForNetworkUseCase(networkId)
                if (address.isNullOrBlank()) {
                    Timber.d("No active address for %s; fetching catalog+ranked list only", networkId)
                }

                // اندپوینت‌های کشفِ توکن سمتِ سرورند و JWT می‌خواهند — مستقل از اینکه کاربر روی
                // DIRECT است یا PROXY. بدونِ این، در حالتِ DIRECT همیشه ۴۰۱ می‌گرفتیم.
                // ۴۰۱ِ بعدی را RelayerTokenAuthenticator خودش replay می‌کند.
                ensureAuthenticatedUseCase()

                when (val result = tokenDiscoveryRepository.getDefaultTokens(networkId, address)) {
                    is ResultResponse.Success -> {
                        defaultByNetwork[networkId] = result.data
                        rebuildRows()
                    }

                    is ResultResponse.Error -> {
                        // شبکهٔ شکست‌خورده را خالی ثبت کن تا هر بار تعویضِ فیلتر دوباره تلاش نشود.
                        defaultByNetwork[networkId] = emptyList()
                        _uiState.update {
                            it.copy(inlineError = userMessageFor(result.exception, "دریافت فهرست توکن‌ها ناموفق بود"))
                        }
                        reportError(
                            throwable = result.exception,
                            userAction = "getDefaultTokens($networkId)",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
                    }
                }
            }
        }

        launchSafe(checkNetwork = false) {
            defaultJobs.forEach { it.join() }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * چهار مسیر، بر اساسِ شکلِ کوئری و اینکه فیلترِ شبکه فعال است یا نه:
     *  - آدرسِ قرارداد **با** فیلتر ⇒ `/networks/{id}/tokens/resolve/{address}` که تا خواندنِ
     *    on-chain پیش می‌رود، پس هر ERC-20ِ واقعی — حتی چیزی که هیچ فهرستی منتشرش نکرده — import
     *    می‌شود.
     *  - آدرسِ قرارداد بدونِ فیلتر ⇒ `/tokens/resolve/{address}` (می‌تواند چند زنجیره بدهد).
     *  - بدونِ فیلتر ⇒ `/tokens/search` سراسری.
     *  - با فیلتر ⇒ مسیرِ per-network که سبک‌تر است.
     */
    private fun runSearch(query: String) {
        searchJob?.cancel()
        val networkId = _uiState.value.selectedNetworkId
        val isAddress = looksLikeContractAddress(query)

        searchJob = launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            _uiState.update { it.copy(isSearching = true, inlineError = null, hasMultipleResolveMatches = false) }
            try {
                ensureAuthenticatedUseCase()

                val result: ResultResponse<List<DiscoveredToken>> = when {
                    isAddress && networkId != null -> resolveOnSelectedNetwork(networkId, query)
                    isAddress -> tokenDiscoveryRepository.resolveTokenByAddress(query)
                    networkId == null -> tokenDiscoveryRepository.searchTokensAllNetworks(query)
                    else -> tokenDiscoveryRepository.searchTokens(networkId, query)
                }

                when (result) {
                    is ResultResponse.Success -> {
                        searchResults = result.data
                        when {
                            result.data.isEmpty() && isAddress -> {
                                // عمداً اجازهٔ «وارد کردنِ دستی» نمی‌دهیم: تنها چیزی که کاربر
                                // می‌تواند تایپ کند آدرس است، و بدونِ decimalsِ معتبر هر مبلغی
                                // ۱۰^n برابر غلط می‌شود. پیدا نشدن یعنی نمی‌دانیم، نه حدس بزن.
                                _uiState.update {
                                    it.copy(inlineError = "این قرارداد توکنِ خواندنی نیست و بدون آن قابل افزودن نیست")
                                }
                            }

                            isAddress && result.data.size > 1 -> {
                                // همان آدرس روی چند زنجیره مستقر است. اولی را برنمی‌داریم —
                                // انتخابِ زنجیرهٔ اشتباه یعنی توکنی که موجودی‌اش هیچ‌وقت نمی‌آید.
                                _uiState.update { it.copy(hasMultipleResolveMatches = true) }
                            }
                        }
                        rebuildRows()
                    }

                    is ResultResponse.Error -> {
                        searchResults = emptyList()
                        _uiState.update {
                            it.copy(inlineError = userMessageFor(result.exception, "جست‌وجو ناموفق بود"))
                        }
                        rebuildRows()
                        reportError(
                            throwable = result.exception,
                            userAction = "searchTokens(network=$networkId, address=$isAddress)",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    /**
     * resolveِ per-network، با آدرسِ کیف پول تا همان‌جا برای مانیتورینگ ثبت شود.
     *
     * پاسخِ تکی به فهرست تبدیل می‌شود تا بقیهٔ مسیرِ جست‌وجو یک شکل بماند. `null` (۴۰۴
     * `ASSET_NOT_FOUND`) به فهرستِ خالی نگاشته می‌شود، که همان مسیرِ «قابلِ افزودن نیست» است.
     */
    private suspend fun resolveOnSelectedNetwork(
        networkId: String,
        address: String
    ): ResultResponse<List<DiscoveredToken>> {
        val wallet = getActiveAddressForNetworkUseCase(networkId)
        // ⚠️ آدرس عیناً همان‌طور که کاربر paste کرده می‌رود — trim تنها کاریست که مجاز است.
        val result = tokenDiscoveryRepository.resolveTokenOnNetwork(
            networkId = networkId,
            contractAddress = address.trim(),
            walletAddress = wallet
        )
        return when (result) {
            is ResultResponse.Success -> ResultResponse.Success(listOfNotNull(result.data))
            is ResultResponse.Error -> result
        }
    }

    /**
     * افزودن/حذفِ یک ردیف — همیشه با جفتِ (`networkId`, `contractAddress`)، پس دو ردیفِ هم‌نماد
     * روی دو زنجیره مستقل از هم عمل می‌کنند.
     *
     * `verified` این‌جا **هیچ نقشی ندارد** و نباید پیدا کند: یک توکنِ واقعیِ تازه هم
     * `verified: false` است، و گیت‌کردنِ افزودن روی آن یعنی کاربری که قراردادِ درست را paste کرده
     * نمی‌تواند دارایی خودش را ببیند.
     *
     * سه حالتِ متفاوت که همه در UI یک کلید هستند:
     *  - دارایی باندل ⇒ فقط پنهان/آشکار می‌شود (باندل امضا شده و واقعاً حذف‌شدنی نیست).
     *  - توکنِ کاربر که هست ⇒ از فهرست حذف می‌شود.
     *  - توکنی که نیست ⇒ به فهرست اضافه می‌شود.
     */
    fun toggle(row: TokenRowUi) {
        launchSafe(checkNetwork = false) {
            setRowBusy(row.key, true)
            try {
                when {
                    row.isAdded && !row.isUserAdded -> userTokenRepository.setHidden(row.assetId, true)

                    row.isAdded && row.isUserAdded ->
                        row.contractAddress?.let { userTokenRepository.remove(row.networkId, it) }

                    !row.isAdded && !row.isUserAdded && isBundleAsset(row) ->
                        userTokenRepository.setHidden(row.assetId, false)

                    else -> {
                        val contract = row.contractAddress
                        if (contract.isNullOrBlank()) {
                            _uiState.update { it.copy(inlineError = "این دارایی قرارداد ندارد و قابل افزودن نیست") }
                            return@launchSafe
                        }
                        userTokenRepository.add(
                            UserToken(
                                networkId = row.networkId,
                                symbol = row.symbol,
                                name = row.name,
                                decimals = row.decimals,
                                // عیناً همان چیزی که سرور برگرداند — نه lowercase، نه نرمال‌سازی.
                                contractAddress = contract,
                                iconUrl = row.iconUrl,
                                catalogId = null
                            )
                        )
                    }
                }
            } finally {
                setRowBusy(row.key, false)
                rebuildRows()
            }
        }
    }

    fun clearInlineError() {
        _uiState.update { it.copy(inlineError = null) }
    }

    /** شبکهٔ خودِ ردیف، نه فیلترِ فعال — در نمای cross-network این دو یکی نیستند. */
    private fun isBundleAsset(row: TokenRowUi): Boolean =
        manageableAssetCatalog.getManageableAssets(row.networkId).any { it.config.id == row.assetId }

    private fun setRowBusy(key: String, busy: Boolean) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.key == key) it.copy(isBusy = busy) else it })
        }
    }

    /**
     * فهرستِ نهایی، روی یک یا همهٔ شبکه‌ها.
     *
     * وقتی کوئری خالی است فهرستِ پیش‌فرضِ سرور **به ترتیبِ خودش** می‌آید و بعد هرچه از باندل در آن
     * نبوده. با تایپ، نتایجِ جست‌وجو جایگزین می‌شوند — آن‌ها هم به ترتیبِ سرور، که verified را اول
     * می‌گذارد.
     *
     * هیچ‌جا `sortedBy` نداریم و این عمدی است: هر مرتب‌سازیِ محلی همان رتبه‌بندی‌ای را خراب می‌کند
     * که این اندپوینت برای آن ساخته شد.
     *
     * در هر حالت وضعیتِ افزوده/پنهان از کاتالوگِ مدیریت‌پذیر خوانده می‌شود، نه از پاسخِ سرور —
     * سرور نمی‌داند کاربر چه چیزی را پنهان کرده.
     */
    private fun rebuildRows() {
        val state = _uiState.value
        val targetNetworks = state.selectedNetworkId?.let { listOf(it) } ?: candidateNetworkIds
        if (targetNetworks.isEmpty()) {
            _uiState.update { it.copy(rows = emptyList()) }
            return
        }

        val manageable = targetNetworks.flatMap { manageableAssetCatalog.getManageableAssets(it) }
        val manageableByKey = manageable.associateBy { it.dedupeKey }
        val hasQuery = state.query.trim().length >= MIN_QUERY_LENGTH

        val ordered = LinkedHashMap<String, TokenRowUi>()

        if (hasQuery) {
            // نتیجهٔ سراسری می‌تواند شبکه‌ای بیرون از فیلتر بدهد؛ فیلتر باید همچنان محترم بماند.
            searchResults
                .filter { state.selectedNetworkId == null || it.networkId == state.selectedNetworkId }
                .forEach { token ->
                    ordered[token.dedupeKey] = token.toRow(TokenRowSource.SEARCH, manageableByKey[token.dedupeKey])
                }
            // نتایجِ محلیِ منطبق هم بمانند: کاربر ممکن است دنبالِ توکنی بگردد که همین حالا دارد.
            manageable.filter { it.matches(state.query) }.forEach { asset ->
                ordered.getOrPut(asset.dedupeKey) { asset.toRow(TokenRowSource.CATALOG) }
            }
        } else {
            targetNetworks.forEach { networkId ->
                defaultByNetwork[networkId].orEmpty().forEach { token ->
                    ordered[token.dedupeKey] = token.toRow(TokenRowSource.DEFAULT, manageableByKey[token.dedupeKey])
                }
            }
            // هرچه در باندل هست ولی سرور در فهرستِ پیش‌فرض نیاورد — و همهٔ توکن‌های خودِ کاربر —
            // باید دیده شوند، وگرنه کاربر نمی‌تواند چیزی را که پنهان کرده برگرداند.
            manageable.forEach { asset ->
                ordered.getOrPut(asset.dedupeKey) { asset.toRow(TokenRowSource.CATALOG) }
            }
        }

        _uiState.update { it.copy(rows = ordered.values.toList()) }
    }

    private fun networkLabelFor(networkId: String): String =
        _uiState.value.networks.firstOrNull { it.id == networkId }?.label
            ?: networkCatalog.getNetworkInfoById(networkId)?.faName
            ?: networkId

    private fun networkIconFor(networkId: String): String? =
        _uiState.value.networks.firstOrNull { it.id == networkId }?.iconUrl
            ?: networkCatalog.getNetworkInfoById(networkId)?.iconUrl

    /**
     * وضعیت از [ManageableAsset] می‌آید وقتی این توکن را می‌شناسیم، وگرنه «افزوده‌نشده».
     * `decimals` هم از همان‌جا برداشته می‌شود، نه از پاسخِ سرور: اگر توکن در کاتالوگِ ماست،
     * تعریفِ محلی حرفِ آخر را می‌زند.
     */
    private fun DiscoveredToken.toRow(
        source: TokenRowSource,
        known: ManageableAsset?
    ): TokenRowUi = TokenRowUi(
        key = dedupeKey,
        assetId = known?.config?.id ?: UserToken(
            networkId = networkId,
            symbol = symbol,
            name = name,
            decimals = decimals,
            contractAddress = contractAddress,
            iconUrl = iconUrl,
            catalogId = catalogId
        ).assetId,
        networkId = networkId,
        networkLabel = networkLabelFor(networkId),
        networkIconUrl = networkIconFor(networkId),
        symbol = known?.config?.symbol ?: symbol,
        name = known?.config?.name ?: name,
        decimals = known?.config?.decimals ?: decimals,
        contractAddress = contractAddress,
        iconUrl = iconUrl ?: known?.config?.iconUrl,
        isAdded = known != null && !known.isHidden,
        isUserAdded = known?.config?.isUserAdded ?: false,
        isNative = false,
        source = source,
        verified = verified,
        priceUsd = priceUsd
    )

    /**
     * ردیفِ باندل. `verified = null` عمدی است: باندل امضاشدهٔ خودمان است و سرور دربارهٔ منشأش
     * چیزی نگفته، پس نه نشان می‌گیرد نه هشدار — نشانِ الکی روی همه‌چیز، خودِ نشان را بی‌معنا می‌کند.
     */
    private fun ManageableAsset.toRow(source: TokenRowSource): TokenRowUi = TokenRowUi(
        key = dedupeKey,
        assetId = config.id,
        networkId = config.networkId,
        networkLabel = networkLabelFor(config.networkId),
        networkIconUrl = networkIconFor(config.networkId),
        symbol = config.symbol,
        name = config.name,
        decimals = config.decimals,
        contractAddress = config.contractAddress,
        iconUrl = config.iconUrl,
        isAdded = !isHidden,
        isUserAdded = config.isUserAdded,
        isNative = config.contractAddress == null,
        source = source,
        verified = null,
        priceUsd = null
    )

    private fun ManageableAsset.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return config.symbol.contains(q, ignoreCase = true) ||
            config.name.contains(q, ignoreCase = true) ||
            config.contractAddress?.equals(q, ignoreCase = true) == true
    }

    /**
     * فقط برای انتخابِ مسیر (resolve در برابر search) و راهنمای UI — نه اعتبارسنجیِ امنیتی. هر دو
     * شکلِ EVM (`0x` + ۴۰ hex) و TRON (`T` + ۳۳ base58) پوشش داده می‌شود.
     */
    private fun looksLikeContractAddress(value: String): Boolean {
        val v = value.trim()
        return (v.length == 42 && v.startsWith("0x", ignoreCase = true) &&
            v.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) ||
            (v.length == 34 && v.startsWith("T"))
    }
}
