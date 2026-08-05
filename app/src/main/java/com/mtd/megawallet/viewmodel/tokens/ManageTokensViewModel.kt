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
import com.mtd.megawallet.core.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/** از کدام منبع به فهرست رسیده — فقط برای گروه‌بندیِ نمایشی. */
enum class TokenRowSource { HELD, CURATED, SEARCH }

data class NetworkOption(
    val id: String,
    val label: String,
    val iconUrl: String?
)

data class TokenRowUi(
    /** کلیدِ یکتا در سه منبع: `networkId:contract` یا `native:<assetId>`. */
    val key: String,
    val assetId: String,
    val networkId: String,
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
    /** در حالِ اعمالِ تغییر — برای غیرفعال‌کردنِ کلیدِ همان ردیف. */
    val isBusy: Boolean = false
)

data class ManageTokensUiState(
    val networks: List<NetworkOption> = emptyList(),
    val selectedNetworkId: String? = null,
    val query: String = "",
    val rows: List<TokenRowUi> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    /** خطای درون‌صفحه‌ای — این صفحه یک شیت است و نباید برای هر شکستِ جست‌وجو snackbar بیندازد. */
    val inlineError: String? = null,
    /** کوئری شبیهِ آدرسِ قرارداد است ⇒ راهنمای «واردکردن با آدرس». */
    val isContractQuery: Boolean = false
)

/**
 * صفحهٔ مدیریتِ توکن — §5 «کشفِ توکن».
 *
 * سه منبع در یک فهرست: توکن‌هایی که کیف پول قبلاً با آن‌ها تراکنش داشته (`/tokens/held`)، بعد
 * دارایی‌های curatedِ باندل، و به‌محضِ تایپ، نتایجِ `/tokens/search`. یکتاسازی با
 * `networkId:contractAddress`ِ lowercase انجام می‌شود، چون همان قرارداد از هر سه منبع می‌آید و
 * checksum-caseِ متفاوت نباید ردیفِ دوم بسازد.
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
    private val ensureAuthenticatedUseCase: EnsureAuthenticatedUseCase,
    errorManager: ErrorManager
) : BaseViewModel(errorManager) {

    private companion object {
        /**
         * اندپوینتِ جست‌وجو سمتِ سرور mirror شده و ارزان است، ولی باتری نه: با هر کاراکتر یک
         * درخواست یعنی رادیو مدامِ بیدار.
         */
        const val SEARCH_DEBOUNCE_MS = 200L
        const val MIN_QUERY_LENGTH = 2
    }

    private val _uiState = MutableStateFlow(ManageTokensUiState())
    val uiState = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    /** پاسخِ `/tokens/held` برای شبکهٔ انتخاب‌شده؛ با عوض‌شدنِ شبکه دوباره گرفته می‌شود. */
    private var heldTokens: List<DiscoveredToken> = emptyList()
    private var searchResults: List<DiscoveredToken> = emptyList()

    init {
        observeQuery()
        observeSelection()
    }

    /**
     * شیت باز شد. شبکه‌ها از کاتالوگ خوانده می‌شوند (که خودش ترجیحِ نمایشِ تست‌نت را رعایت کرده)
     * و فقط خانواده‌های قرارداددار می‌مانند: روی UTXO چیزی به‌نامِ توکن وجود ندارد که مدیریت شود.
     */
    fun onOpened() {
        val options = networkCatalog.getAllNetworkInfos()
            .filter { it.networkType == NetworkType.EVM || it.networkType == NetworkType.TVM }
            .map { NetworkOption(id = it.id, label = it.faName ?: it.id, iconUrl = it.iconUrl) }

        val current = _uiState.value.selectedNetworkId
        val selected = current?.takeIf { id -> options.any { it.id == id } } ?: options.firstOrNull()?.id

        _uiState.update { it.copy(networks = options, selectedNetworkId = selected) }
        selected?.let { loadHeldTokens(it) }
        rebuildRows()
    }

    fun selectNetwork(networkId: String) {
        if (_uiState.value.selectedNetworkId == networkId) return
        heldTokens = emptyList()
        searchResults = emptyList()
        _uiState.update {
            it.copy(selectedNetworkId = networkId, query = "", inlineError = null, isContractQuery = false)
        }
        queryFlow.value = ""
        loadHeldTokens(networkId)
        rebuildRows()
    }

    fun onQueryChange(value: String) {
        _uiState.update {
            it.copy(query = value, isContractQuery = looksLikeContractAddress(value), inlineError = null)
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

    private fun loadHeldTokens(networkId: String) {
        // شکستِ این فراخوانی صفحه را از کار نمی‌اندازد: فهرستِ curated همچنان نمایش داده می‌شود،
        // پس خطا درون‌صفحه‌ای است نه snackbar.
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            _uiState.update { it.copy(isLoading = true, inlineError = null) }
            try {
                val address = getActiveAddressForNetworkUseCase(networkId)
                if (address.isNullOrBlank()) {
                    Timber.w("No active address for %s; skipping held-token lookup", networkId)
                    return@launchSafe
                }

                // اندپوینت‌های کشفِ توکن سمتِ سرورند و JWT می‌خواهند — مستقل از اینکه کاربر روی
                // DIRECT است یا PROXY. بدونِ این، در حالتِ DIRECT همیشه ۴۰۱ می‌گرفتیم.
                ensureAuthenticatedUseCase()

                when (val result = tokenDiscoveryRepository.getHeldTokens(networkId, address)) {
                    is ResultResponse.Success -> {
                        if (_uiState.value.selectedNetworkId != networkId) return@launchSafe
                        heldTokens = result.data
                        rebuildRows()
                    }

                    is ResultResponse.Error -> {
                        _uiState.update {
                            it.copy(inlineError = userMessageFor(result.exception, "دریافت توکن‌های شما ناموفق بود"))
                        }
                        reportError(
                            throwable = result.exception,
                            userAction = "getHeldTokens($networkId)",
                            surface = ErrorSurface.SILENT,
                            severity = ErrorSeverity.LOW
                        )
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun runSearch(query: String) {
        val networkId = _uiState.value.selectedNetworkId ?: return
        launchSafe(connectivitySurface = ErrorSurface.SILENT) {
            _uiState.update { it.copy(isSearching = true, inlineError = null) }
            try {
                ensureAuthenticatedUseCase()
                when (val result = tokenDiscoveryRepository.searchTokens(networkId, query)) {
                    is ResultResponse.Success -> {
                        // کوئری یا شبکه در این فاصله عوض شده ⇒ نتیجهٔ کهنه را ننشان.
                        if (_uiState.value.query.trim() != query ||
                            _uiState.value.selectedNetworkId != networkId
                        ) return@launchSafe

                        searchResults = result.data
                        if (result.data.isEmpty() && looksLikeContractAddress(query)) {
                            // عمداً اجازهٔ «وارد کردنِ دستی» نمی‌دهیم: تنها چیزی که کاربر می‌تواند
                            // تایپ کند آدرس است، و بدونِ decimalsِ معتبر هر مبلغی ۱۰^n برابر غلط
                            // می‌شود. نبودِ توکن در فهرستِ سرور یعنی نمی‌دانیم، نه اینکه حدس بزنیم.
                            _uiState.update {
                                it.copy(inlineError = "این قرارداد در فهرست سرور پیدا نشد و بدون آن قابل افزودن نیست")
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
                            userAction = "searchTokens($networkId)",
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
     * افزودن/حذفِ یک ردیف.
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

    private fun isBundleAsset(row: TokenRowUi): Boolean {
        val networkId = _uiState.value.selectedNetworkId ?: return false
        return manageableAssetCatalog.getManageableAssets(networkId).any { it.config.id == row.assetId }
    }

    private fun setRowBusy(key: String, busy: Boolean) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.key == key) it.copy(isBusy = busy) else it })
        }
    }

    /**
     * فهرستِ نهایی از سه منبع.
     *
     * وقتی کوئری خالی است: اول توکن‌های held (چیزی که کاربر واقعاً داشته)، بعد کاتالوگ. با تایپ،
     * نتایجِ جست‌وجو جایگزین می‌شوند. در هر سه حالت وضعیتِ افزوده/پنهان از کاتالوگِ مدیریت‌پذیر
     * خوانده می‌شود، نه از پاسخِ سرور — سرور نمی‌داند کاربر چه چیزی را پنهان کرده.
     */
    private fun rebuildRows() {
        val networkId = _uiState.value.selectedNetworkId
        if (networkId == null) {
            _uiState.update { it.copy(rows = emptyList()) }
            return
        }

        val manageable = manageableAssetCatalog.getManageableAssets(networkId)
        val manageableByKey = manageable.associateBy { it.dedupeKey }
        val hasQuery = _uiState.value.query.trim().length >= MIN_QUERY_LENGTH

        val ordered = LinkedHashMap<String, TokenRowUi>()

        if (hasQuery) {
            searchResults.forEach { token ->
                ordered[token.dedupeKey] = token.toRow(TokenRowSource.SEARCH, manageableByKey[token.dedupeKey])
            }
            // نتایجِ محلیِ منطبق هم بمانند: کاربر ممکن است دنبالِ توکنی بگردد که همین حالا دارد.
            manageable.filter { it.matches(_uiState.value.query) }.forEach { asset ->
                ordered.getOrPut(asset.dedupeKey) { asset.toRow(TokenRowSource.CURATED) }
            }
        } else {
            heldTokens.forEach { token ->
                ordered[token.dedupeKey] = token.toRow(TokenRowSource.HELD, manageableByKey[token.dedupeKey])
            }
            manageable.forEach { asset ->
                val existing = ordered[asset.dedupeKey]
                if (existing == null) {
                    ordered[asset.dedupeKey] = asset.toRow(TokenRowSource.CURATED)
                }
            }
        }

        _uiState.update { it.copy(rows = ordered.values.toList()) }
    }

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
        symbol = known?.config?.symbol ?: symbol,
        name = known?.config?.name ?: name,
        decimals = known?.config?.decimals ?: decimals,
        contractAddress = contractAddress,
        iconUrl = iconUrl ?: known?.config?.iconUrl,
        isAdded = known != null && !known.isHidden,
        isUserAdded = known?.config?.isUserAdded ?: false,
        isNative = false,
        source = source
    )

    private fun ManageableAsset.toRow(source: TokenRowSource): TokenRowUi = TokenRowUi(
        key = dedupeKey,
        assetId = config.id,
        networkId = config.networkId,
        symbol = config.symbol,
        name = config.name,
        decimals = config.decimals,
        contractAddress = config.contractAddress,
        iconUrl = config.iconUrl,
        isAdded = !isHidden,
        isUserAdded = config.isUserAdded,
        isNative = config.contractAddress == null,
        source = source
    )

    private fun ManageableAsset.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        return config.symbol.contains(q, ignoreCase = true) ||
            config.name.contains(q, ignoreCase = true) ||
            config.contractAddress?.equals(q, ignoreCase = true) == true
    }

    /**
     * فقط برای راهنمای UI و پیامِ «پیدا نشد» — نه برای اعتبارسنجیِ امنیتی. هر دو شکلِ EVM
     * (`0x` + ۴۰ hex) و TRON (`T` + ۳۳ base58) پوشش داده می‌شود.
     */
    private fun looksLikeContractAddress(value: String): Boolean {
        val v = value.trim()
        return (v.length == 42 && v.startsWith("0x", ignoreCase = true) &&
            v.drop(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) ||
            (v.length == 34 && v.startsWith("T"))
    }
}
