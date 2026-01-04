package com.mtd.megawallet.viewmodel

import com.mtd.core.model.NetworkType
import com.mtd.core.model.WalletKey
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.BalanceFormatter
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.repository.IWalletRepository
import com.mtd.data.repository.TransactionParams
import com.mtd.domain.model.Asset
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.UiAsset
import com.mtd.domain.repository.IMarketDataRepository
import com.mtd.megawallet.core.BaseViewModel
import com.mtd.megawallet.event.AssetItem
import com.mtd.megawallet.event.FeeOption
import com.mtd.megawallet.event.SendUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject


@HiltViewModel
class SendViewModel @Inject constructor(
    private val blockchainRegistry: BlockchainRegistry,
    private val walletRepository: IWalletRepository,
    private val marketDataRepository: IMarketDataRepository,
    private val dataSourceFactory: ChainDataSourceFactory,
    private val assetRegistry: AssetRegistry,
    private val globalEventBus: GlobalEventBus,
    private val activeWalletManager: com.mtd.domain.wallet.ActiveWalletManager,
    errorManager: com.mtd.core.manager.ErrorManager
) : BaseViewModel(errorManager) {

    val _uiState = MutableStateFlow<SendUiState>(SendUiState.EnteringAddress())
    val uiState = _uiState.asStateFlow()

    private var allCompatibleAssets = listOf<AssetItem>()

    // --- مرحله ۱: وارد کردن آدرس (بدون تغییر) ---
    fun onAddressChanged(address: String) {
        val networkType = blockchainRegistry.getNetworkTypeForAddress(address)
        _uiState.value = SendUiState.EnteringAddress(
            address = address,
            isValid = networkType != null,
            error = if (address.isNotEmpty() && networkType == null) "آدرس نامعتبر است" else null
        )
    }

    // --- مرحله ۱ (ادامه): تایید آدرس ---
    fun onAddressEntered() {
        val currentState = _uiState.value as? SendUiState.EnteringAddress ?: return
        if (!currentState.isValid) return

        _uiState.value = SendUiState.Loading("در حال یافتن دارایی‌های قابل ارسال...")
        launchSafe {
            // --- بخش اصلی و بازنویسی شده ---
            when (val assetsResult = fetchCompatibleAndFundedAssets(currentState.address)) {
                is ResultResponse.Success -> {
                    allCompatibleAssets = assetsResult.data
                    _uiState.value = SendUiState.SelectingAsset(
                        recipientAddress = currentState.address,
                        compatibleAssets = allCompatibleAssets
                    )
                }

                is ResultResponse.Error -> {
                    _uiState.value = SendUiState.Error(
                        assetsResult.exception.message ?: "خطا در دریافت دارایی‌ها"
                    )
                }
            }
        }
    }

    /**
     * متد جدید و بهینه: فقط دارایی‌های سازگار و با موجودی را دریافت می‌کند.
     */
    private suspend fun fetchCompatibleAndFundedAssets(address: String): ResultResponse<List<AssetItem>> {

            // ۱. تشخیص نوع شبکه مقصد
            val targetNetworkType =
                blockchainRegistry.getNetworkTypeForAddress(address) ?: return ResultResponse.Error(
                    Exception("نوع شبکه آدرس مقصد نامشخص است.")
                )

            // ۲. بارگذاری کیف پول فعال از ActiveWalletManager
            val activeWallet = activeWalletManager.activeWallet.value
            if (activeWallet == null) {
                return ResultResponse.Error(Exception("کیف پول فعال پیدا نشد."))
            }

            // ۳. **فقط** کلیدهای سازگار با شبکه مقصد را انتخاب کن
            val compatibleKeys = activeWallet.keys.filter { it.networkType == targetNetworkType }
            if (compatibleKeys.isEmpty()) {
                // کاربر کلیدی برای این نوع شبکه در کیف پولش ندارد
                return ResultResponse.Success(emptyList())
            }

            // ۴. دریافت موجودی فقط برای دارایی‌های روی این شبکه‌های سازگار
            val assetsWithBalance = fetchBalancesForKeys(compatibleKeys)

            // ۵. فیلتر کردن دارایی‌های با موجودی صفر
            /*  val fundedAssets = assetsWithBalance.filter { it.balance > BigInteger.ZERO }
              if (fundedAssets.isEmpty()) {
                  return ResultResponse.Success(emptyList())
              }*/

            // ۶. گرفتن قیمت فقط برای دارایی‌های با موجودی
            val assetIdsForPricing = assetsWithBalance.mapNotNull { it.coinGeckoId }.distinct()
            val pricesResult = if (assetIdsForPricing.isNotEmpty()) {
                marketDataRepository.getLatestPrices(assetIdsForPricing)
            } else {
                ResultResponse.Success(emptyList())
            }
            val priceMap =
                (pricesResult as? ResultResponse.Success)?.data?.associateBy { it.assetId }
                    ?: emptyMap()

            // ۷. تبدیل نهایی به AssetItem (بخش کامل شده)
            val finalAssetItems = assetsWithBalance.map { asset ->
                val priceInfo = priceMap[asset.coinGeckoId]
                val priceUsd = priceInfo?.priceUsd ?: BigDecimal.ZERO
                val balanceDecimal =
                    BigDecimal(asset.balance).divide(BigDecimal.TEN.pow(asset.decimals))
                val balanceUsd = balanceDecimal * priceUsd
                val networkInfo = blockchainRegistry.getNetworkById(asset.networkName)

                AssetItem(
                    id = asset.symbol,
                    name = asset.name,
                    symbol = asset.symbol,
                    networkName = "on ${networkInfo?.name?.name?.replaceFirstChar { it.titlecase() } ?: asset.networkName}",
                    networkId = asset.networkName,
                    iconUrl = asset.iconUrl,
                    balance = BalanceFormatter.formatBalance(asset.balance, asset.decimals),
                    balanceUsdt = BalanceFormatter.formatUsdValue(balanceUsd),
                    balanceRaw = balanceDecimal,
                    priceChange24h = priceInfo?.priceChanges24h?.toDouble() ?: 0.0,
                    priceUsdRaw = priceInfo?.priceUsd ?: BigDecimal.ZERO,
                    decimals = asset.decimals,
                    contractAddress = asset.contractAddress,
                    isNativeToken = asset.contractAddress == null,

                    )
            }

            return ResultResponse.Success(finalAssetItems.sortedByDescending {
                it.balanceUsdt.removePrefix(
                    "$"
                ).replace(",", "").toDouble()
            })

    }

    /**
     * موجودی تمام دارایی‌های پشتیبانی شده را برای لیستی از کلیدهای مشخص شده دریافت می‌کند.
     * @param keys لیستی از WalletKey ها که می‌خواهیم موجودی آنها را بررسی کنیم.
     * @return لیستی از UiAsset که شامل اطلاعات دارایی و موجودی آن است.
     */
    private suspend fun fetchBalancesForKeys(keys: List<WalletKey>): List<UiAsset> =
        coroutineScope {
            keys.map { key ->
                async {
                    // ۱. دریافت اطلاعات شبکه از رجیستری
                    val networkInfo = blockchainRegistry.getNetworkByChainId(key.chainId ?: 0L)
                    // اگر شبکه در رجیستری ما تعریف نشده بود، از این کلید رد شو
                    if (networkInfo == null) return@async emptyList<UiAsset>()

                    // ۲. گرفتن لیست تمام دارایی‌های پشتیبانی شده برای این شبکه خاص از AssetRegistry
                    val supportedAssetsOnNetwork = assetRegistry.getAssetsForNetwork(networkInfo.id)
                    if (supportedAssetsOnNetwork.isEmpty()) return@async emptyList<UiAsset>()

                    // ۳. ساخت DataSource برای این شبکه
                    val dataSource = dataSourceFactory.create(networkInfo.chainId ?: -1)

                    // ۴. دریافت موجودی‌ها بر اساس نوع شبکه
                    val balancesResult = if (networkInfo.networkType == NetworkType.EVM) {
                        // برای شبکه‌های EVM، متد getBalanceEVM را فراخوانی می‌کنیم
                        // که موجودی توکن اصلی و تمام توکن‌های ERC20 تعریف شده در assets.json را برمی‌گرداند.
                        dataSource.getBalanceEVM(key.address)
                    }
                    else {
                        // برای شبکه‌های غیر EVM (مثل Bitcoin)، فقط موجودی توکن اصلی را می‌گیریم.
                        val balance =
                            (dataSource.getBalance(key.address) as? ResultResponse.Success)?.data
                                ?: BigInteger.ZERO
                        // نتیجه را در همان فرمت List<Asset> بسته‌بندی می‌کنیم تا کد پایین‌تر یکسان باشد.
                        ResultResponse.Success(
                            listOf(
                                Asset(
                                    name = supportedAssetsOnNetwork.first().name,
                                    symbol = supportedAssetsOnNetwork.first().symbol,
                                    decimals = supportedAssetsOnNetwork.first().decimals,
                                    contractAddress = null,
                                    balance = balance
                                )
                            )
                        )
                    }

                    // ۵. تبدیل نتیجه به مدل داده داخلی UiAsset
                    if (balancesResult is ResultResponse.Success) {
                        balancesResult.data.mapNotNull { fetchedAsset ->
                            // پیدا کردن AssetConfig متناظر برای گرفتن اطلاعات اضافی (مثل coinGeckoId)
                            val assetConfig = supportedAssetsOnNetwork.find {
                                it.symbol == fetchedAsset.symbol &&
                                        (it.contractAddress == null || it.contractAddress.equals(
                                            fetchedAsset.contractAddress,
                                            true
                                        ))
                            }
                            assetConfig?.let { config ->
                                UiAsset(
                                    name = config.name,
                                    symbol = config.symbol,
                                    decimals = config.decimals,
                                    balance = fetchedAsset.balance,
                                    networkName = config.networkId,
                                    iconUrl = config.iconUrl,
                                    coinGeckoId = config.coinGeckoId,
                                    contractAddress = config.contractAddress
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }


    /**
     * فراخوانی می‌شود وقتی کاربر مقدار ارسالی را تغییر می‌دهد.
     */
    fun onAmountChanged(amountStr: String) {
        val currentState = _uiState.value as? SendUiState.EnteringDetails ?: return
        val selectedAsset = currentState.selectedAsset!!
        val selectedFee = currentState.selectedFee ?: return

        val amountDecimal = amountStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val amountUsd = amountDecimal * (selectedAsset.priceUsdRaw)

        // --- منطق جدید اعتبارسنجی ---
        val validationError = validateSendAmount(amountDecimal, selectedAsset, selectedFee)

        _uiState.update {
            (it as SendUiState.EnteringDetails).copy(
                amount = amountStr,
                amountUsd = BalanceFormatter.formatUsdValue(amountUsd),
                validationError = validationError
            )
        }
    }

    /**
     * یک تابع کمکی جدید برای اعتبارسنجی متمرکز.
     * این تابع هم موجودی توکن ارسالی و هم موجودی توکن اصلی شبکه برای کارمزد را بررسی می‌کند.
     *
     * @param amountToSend مقدار دارایی که کاربر قصد ارسال آن را دارد (به صورت BigDecimal).
     * @param assetToSend دارایی انتخاب شده برای ارسال (مثلاً USDC).
     * @param fee کارمزد انتخاب شده.
     * @return یک رشته خطا در صورت نامعتبر بودن، در غیر این صورت null.
     */
    private fun validateSendAmount(amountToSend: BigDecimal, assetToSend: AssetItem, fee: FeeOption): String? {
        // اگر مقدار صفر یا منفی است، خطایی وجود ندارد (دکمه ارسال غیرفعال خواهد بود).
        if (amountToSend <= BigDecimal.ZERO) {
            return null
        }

        // --- چک اول: آیا موجودی خود توکن ارسالی کافی است؟ ---
        if (amountToSend > assetToSend.balanceRaw) {
            return "موجودی ${assetToSend.symbol} کافی نیست."
        }

        // --- چک دوم: آیا برای پرداخت کارمزد، موجودی توکن اصلی شبکه کافی است؟ ---

        // اگر در حال ارسال خود توکن اصلی هستیم (مثلاً ETH)
        if (assetToSend.isNativeToken) {
            // کارمزد از همان موجودی کسر می‌شود.
            val feeInAssetUnit = BigDecimal(fee.feeInSmallestUnit)
                .divide(BigDecimal.TEN.pow(assetToSend.decimals))

            val totalDebit = amountToSend + feeInAssetUnit

            if (totalDebit > assetToSend.balanceRaw) {
                return "موجودی برای پوشش مقدار و کارمزد کافی نیست."
            }
        }
        // اگر در حال ارسال یک توکن قراردادی هستیم (مثلاً USDC)
        else {
            // ۱. پیدا کردن توکن اصلی شبکه از لیست دارایی‌هایی که قبلاً لود کردیم.
            val nativeAsset = allCompatibleAssets.find {
                it.networkId == assetToSend.networkId && it.isNativeToken
            }

            // اگر به هر دلیلی توکن اصلی پیدا نشد (نباید اتفاق بیفتد)
            if (nativeAsset == null) {
                return "خطای داخلی: موجودی ارز اصلی شبکه (${assetToSend.networkName}) یافت نشد."
            }

            // ۲. تبدیل موجودی توکن اصلی به کوچکترین واحد (Wei)
            val nativeAssetBalanceInSmallestUnit = nativeAsset.balanceRaw
                .multiply(BigDecimal.TEN.pow(nativeAsset.decimals))
                .toBigInteger()

            // ۳. مقایسه موجودی توکن اصلی با کارمزد مورد نیاز
            if (nativeAssetBalanceInSmallestUnit < fee.feeInSmallestUnit) {
                return "موجودی ${nativeAsset.symbol} برای پرداخت کارمزد کافی نیست."
            }
        }

        // اگر تمام چک‌ها موفقیت‌آمیز بود، هیچ خطایی وجود ندارد.
        return null
    }

    /**
     * فراخوانی می‌شود وقتی کاربر روی دکمه "Max" کلیک می‌کند.
     */
    fun onMaxButtonClicked() {
        val currentState = _uiState.value as? SendUiState.EnteringDetails ?: return
        val selectedAsset = currentState.selectedAsset!!
        val selectedFee = currentState.selectedFee ?: return

        // پیدا کردن موجودی توکن اصلی شبکه (مثلاً ETH برای شبکه Sepolia)
        val nativeAsset = allCompatibleAssets.find {
            it.networkId == selectedAsset?.networkId && it.isNativeToken
        }

        if (nativeAsset == null) {
            // این حالت نباید اتفاق بیفتد، اما برای اطمینان
            _uiState.update { (it as SendUiState.EnteringDetails).copy(validationError = "موجودی ارز اصلی شبکه یافت نشد.") }
            return
        }

        val maxAmount: BigDecimal

        if (selectedAsset.isNativeToken) {
            // حالت ۱: در حال ارسال توکن اصلی هستیم (e.g., ETH)
            // Max Amount = کل موجودی توکن اصلی - کارمزد (که آن هم با توکن اصلی پرداخت می‌شود)
            val feeInAssetUnit =
                BigDecimal(selectedFee.feeInSmallestUnit).divide(BigDecimal.TEN.pow(selectedAsset.decimals))
            maxAmount = selectedAsset.balanceRaw - feeInAssetUnit

        } else {
            // حالت ۲: در حال ارسال یک توکن ERC20 هستیم (e.g., USDC)
            // Max Amount = تمام موجودی آن توکن (کل USDC)
            maxAmount = selectedAsset.balanceRaw

            // حالا باید چک کنیم که آیا موجودی توکن اصلی (ETH) برای پرداخت کارمزد کافی است یا نه.
            val feeInNativeSmallestUnit = selectedFee.feeInSmallestUnit
            val nativeAssetBalanceSmallestUnit =
                nativeAsset.balanceRaw.multiply(BigDecimal.TEN.pow(nativeAsset.decimals))
                    .toBigInteger()

            if (nativeAssetBalanceSmallestUnit < feeInNativeSmallestUnit) {
                _uiState.update {
                    (it as SendUiState.EnteringDetails).copy(
                        validationError = "موجودی ${nativeAsset.symbol} برای پرداخت کارمزد کافی نیست."
                    )
                }
                // حتی اگر موجودی کافی نیست، ما مقدار Max رو در فیلد قرار میدیم تا کاربر ببینه
                // اما خطا هم نمایش داده میشه و دکمه "ادامه" غیرفعال خواهد بود.
            }
        }

        // اگر نتیجه منفی شد (یعنی موجودی حتی برای کارمزد هم کافی نیست)، مقدار را صفر قرار بده
        val finalMaxAmount = if (maxAmount < BigDecimal.ZERO) BigDecimal.ZERO else maxAmount

        // با فراخوانی onAmountChanged، هم مقدار در UI آپدیت می‌شود و هم اعتبارسنجی مجدد انجام می‌شود
        onAmountChanged(
            finalMaxAmount.setScale(selectedAsset.decimals, RoundingMode.DOWN).toPlainString()
        )
    }

    /**
     * فراخوانی می‌شود وقتی کاربر سطح کارمزد جدیدی را انتخاب می‌کند.
     */
    fun onFeeLevelSelected(selectedFeeText: String) {
        val currentState = _uiState.value as? SendUiState.EnteringDetails ?: return
        // پیدا کردن FeeOption کامل بر اساس متن نمایشی
        val selectedOption = currentState.feeOptions.find {
            "${it.level} - ${it.feeAmountDisplay}" == selectedFeeText
        } ?: return

        _uiState.update {
            (it as SendUiState.EnteringDetails).copy(selectedFee = selectedOption)
        }
        // اعتبارسنجی مجدد با کارمزد جدید
        onAmountChanged(currentState.amount)
    }

    /**
     * این متد در onAssetSelected فراخوانی می‌شود تا گزینه‌های کارمزد را دریافت کند.
     */
    private suspend fun fetchFeeOptionsForAsset(
        asset: AssetItem,
        recipientAddress: String
    ): List<FeeOption> {
        val networkInfo = blockchainRegistry.getNetworkById(asset.networkId) ?: return emptyList()
        val dataSource = dataSourceFactory.create(networkInfo.chainId ?: -1)

        // ساخت یک آبجکت Asset خام برای پاس دادن به DataSource
        val rawAsset = Asset(
            name = asset.name,
            symbol = asset.symbol,
            decimals = asset.decimals,
            contractAddress = if (asset.isNativeToken) null else asset.contractAddress, // <-- نیاز به این فیلدها در AssetItem داریم
            balance = BigInteger.ZERO // موجودی در اینجا مهم نیست
        )
        val address = networkInfo.chainId?.let { activeWalletManager.getAddressForNetwork(it) }
            ?: return emptyList()
        // فراخوانی متد قدرتمند DataSource
        val feeResult = dataSource.getFeeOptions(
            fromAddress = address,
            toAddress = recipientAddress,
            asset = rawAsset
        )

        if (feeResult is ResultResponse.Success) {
            return feeResult.data.map { feeData ->
                // محاسبه کارمزد به صورت Decimal یکبار برای همیشه
                // val feeInEth = BigDecimal(feeData.feeInSmallestUnit).divide(BigDecimal.TEN.pow(if (asset.contractAddress == null) asset.decimals else network.currencyDecimals))

                val symbol =
                    if (asset.contractAddress == null) asset.symbol else networkInfo.currencySymbol
                val usdPrice =
                    if (asset.contractAddress == null) asset.priceUsdRaw else BigDecimal.ZERO

                FeeOption(
                    level = feeData.level,
                    feeAmountDisplay = BalanceFormatter.formatBalance(
                        feeData.feeInSmallestUnit,
                        if (asset.contractAddress == null) asset.decimals else networkInfo.decimals
                    ) + " $symbol",

                    feeAmountUsdDisplay = BalanceFormatter.formatUsdValue(
                        (feeData.feeInEth ?: BigDecimal.ONE) * usdPrice
                    ),
                    estimatedTime = feeData.estimatedTime,

                    // داده خام
                    feeInSmallestUnit = feeData.feeInSmallestUnit,
                    gasPrice = feeData.gasPrice,
                    gasLimit = feeData.gasLimit,
                    feeRateInSatsPerByte = feeData.feeRateInSatsPerByte
                )

            }
        }
        return emptyList()
    }


    suspend fun onContinueToConfirmation() {
        val currentState = _uiState.value as? SendUiState.EnteringDetails ?: return
        if (currentState.validationError != null || (currentState.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) <= BigDecimal.ZERO) {
            return
        }

        // ساخت State برای صفحه تایید نهایی
        val asset = currentState.selectedAsset!!
        val fee = currentState.selectedFee!!
        val amountDecimal = currentState.amount.toBigDecimal()

        val totalDebitDecimal =
            amountDecimal + BigDecimal(fee.feeInSmallestUnit).divide(BigDecimal.TEN.pow(asset.decimals))
        val totalDebitUsd = totalDebitDecimal * asset.priceUsdRaw



        _uiState.value = SendUiState.Confirmation(
            asset = asset,
            amount = currentState.amount,
            amountDisplay = "${currentState.amount} ${asset.symbol}",
            amountUsd = currentState.amountUsd,
            recipientAddress = currentState.recipientAddress,
            fromAddress = blockchainRegistry.getNetworkById(asset.networkId)?.chainId?.let { 
                activeWalletManager.getAddressForNetwork(it) 
            } ?: "",
            fee = fee,
            totalDebit = BalanceFormatter.formatUsdValue(totalDebitUsd),
            totalDebitAsset = "${totalDebitDecimal.toPlainString()} ${asset.symbol}"
        )
    }

    fun confirmAndSendTransaction() {
        val currentState = _uiState.value as? SendUiState.Confirmation ?: return

        _uiState.value = SendUiState.Sending // ۱. نمایش وضعیت "در حال ارسال" به UI

        launchSafe {
            val asset = currentState.asset
            val fee = currentState.fee

            // ۱. گرفتن اطلاعات کامل شبکه از رجیستری
            val networkInfo = blockchainRegistry.getNetworkById(asset.networkId)
            if (networkInfo == null) {
                _uiState.value = SendUiState.Error("اطلاعات شبکه یافت نشد.")
                return@launchSafe
            }



            // ۲. ساخت آبجکت TransactionParams بر اساس نوع شبکه
            val params = when (networkInfo.networkType){
                NetworkType.EVM->{
                    when{
                        asset.isNativeToken->{
                            TransactionParams.Evm(
                                networkName = networkInfo.name,
                                to = currentState.recipientAddress, // گیرنده نهایی
                                amount = BigDecimal(currentState.amount).multiply(BigDecimal.TEN.pow(asset.decimals)).toBigInteger(), // مقدار ارسالی
                                gasPrice = fee.gasPrice!!,
                                gasLimit = fee.gasLimit!!,
                                data = null // برای توکن اصلی data نداریم
                            )

                        }
                        asset.contractAddress != null->{
                            // --- حالت صحیح برای ارسال توکن قراردادی (مثل USDC) ---
                            val amountInSmallestUnit = BigDecimal(currentState.amount).multiply(BigDecimal.TEN.pow(asset.decimals)).toBigInteger()

                            TransactionParams.Evm(
                                networkName = networkInfo.name,
                                to = asset.contractAddress, // <<<--- اصلاح ۱: `to` باید آدرس قرارداد باشه
                                amount = BigInteger.ZERO, // <<<--- اصلاح ۲: `amount` (value) باید صفر باشه
                                gasPrice = fee.gasPrice!!,
                                gasLimit = fee.gasLimit!!,
                                data = { // ساخت data برای توکن ERC20
                                    val function = Function(
                                        "transfer",
                                        listOf(
                                            Address(currentState.recipientAddress), // آدرس گیرنده نهایی
                                            Uint256(amountInSmallestUnit) // مقدار ارسالی
                                        ),
                                        emptyList()
                                    )
                                    FunctionEncoder.encode(function)
                                }()
                            )

                        }
                        else->{
                            _uiState.value = SendUiState.Error("نوع شبکه پشتیبانی نمی‌شود.")
                            return@launchSafe
                        }
                    }
                }
                NetworkType.BITCOIN->{
                    // Bitcoin and other UTXO networks
                    TransactionParams.Utxo(
                        chainId = networkInfo.chainId ?: -1,
                        toAddress = currentState.recipientAddress,
                        amountInSatoshi = BigDecimal(currentState.amount).multiply(
                            BigDecimal.TEN.pow(
                                asset.decimals
                            )
                        ).toLong(),
                        feeRateInSatsPerByte = fee.feeRateInSatsPerByte!! // از FeeOption خوانده می‌شود
                    )
                }
                else -> {
                    _uiState.value = SendUiState.Error("نوع شبکه پشتیبانی نمی‌شود.")
                    return@launchSafe
                }
            }

            // ۳. ارسال تراکنش به ریپازیتوری
            val result = walletRepository.sendTransaction(params)

            // ۴. آپدیت UI بر اساس نتیجه
            when (result) {
                is ResultResponse.Success -> {
                    _uiState.value = SendUiState.Success(result.data)
                    globalEventBus.postEvent(GlobalEvent.WalletNeedsRefresh)
                }

                is ResultResponse.Error -> {
                    _uiState.value =
                        SendUiState.Error(result.exception.message ?: "خطا در ارسال تراکنش")
                }
            }
        }
    }

    // --- مرحله ۲: انتخاب دارایی ---
    fun onSearchQueryChanged(query: String) {
        val currentState = _uiState.value
        if (currentState is SendUiState.SelectingAsset) {
            val filteredList = if (query.isBlank()) {
                allCompatibleAssets
            } else {
                allCompatibleAssets.filter {
                    it.name.contains(query, ignoreCase = true) ||
                            it.symbol.contains(query, ignoreCase = true)
                }
            }
            _uiState.value = currentState.copy(compatibleAssets = filteredList)
        }
    }

    fun onAssetSelected(asset: AssetItem) {
        val currentState = _uiState.value as? SendUiState.SelectingAsset ?: return
        _uiState.value = SendUiState.Loading("در حال دریافت اطلاعات کارمزد...")
        launchSafe {
            // آدرس گیرنده رو به متد پاس میدیم
            val feeOptions = fetchFeeOptionsForAsset(asset, currentState.recipientAddress)
            _uiState.value = SendUiState.EnteringDetails(
                recipientAddress = currentState.recipientAddress,
                selectedAsset = asset,
                feeOptions = feeOptions,
                selectedFee = feeOptions.getOrNull(1) // "Fast" as default
            )
        }
    }

    fun resetToEnteringAddress() {
        _uiState.value = SendUiState.EnteringAddress()
    }

}