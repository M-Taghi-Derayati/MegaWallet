package com.mtd.core.registry

import android.content.Context
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.network.bitcoin.BitcoinNetwork
import com.mtd.core.network.bitcoin.UtxoNetworkParametersResolver
import com.mtd.core.network.evm.GenericEvmNetwork
import com.mtd.core.network.tron.TronNetwork
import com.mtd.core.utils.AddressRegexUtils
import com.mtd.core.utils.loadNetworkConfigs
import com.mtd.domain.interfaceRepository.INetworkCatalog
import com.mtd.domain.interfaceRepository.ITestnetVisibilityProvider
import com.mtd.domain.interfaceRepository.NetworkInfo
import com.mtd.domain.model.core.NetworkConfig
import com.mtd.domain.model.core.NetworkName
import com.mtd.domain.model.core.NetworkType
import fr.acinq.bitcoin.Base58
import org.bitcoinj.base.Address
import org.web3j.crypto.WalletUtils
import timber.log.Timber
import javax.inject.Inject


/**
 * Strategy-style factory for creating [BlockchainNetwork] instances from [com.mtd.domain.model.core.NetworkConfig].
 * این اینترفیس کمک می‌کند از whenهای بزرگ وابسته به [com.mtd.domain.model.core.NetworkType] دور شویم
 * و منطق ساخت هر شبکه را در یک کلاس جداگانه نگه داریم.
 */
interface NetworkFactory {
    fun supports(networkType: NetworkType, config: NetworkConfig): Boolean
    fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork
}

class EvmNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.EVM
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        return GenericEvmNetwork(config)
    }
}

class BitcoinNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.BITCOIN
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        // TASK-53 — زنجیره‌های UTXO هنوز به پارامترهای bitcoinjِ مخصوصِ خودشان نیاز دارند، پس
        // alias برای این خانواده اجباری است. اگر ناشناخته بود شبکه ساخته نمی‌شود (خطا پرتاب
        // می‌شود و رجیستری آن را رد می‌کند) — برخلاف EVM که کاملاً داده‌محور است.
        val networkName = NetworkName.fromConfigName(config.name)
            ?: throw IllegalArgumentException(
                "UTXO network '${config.id}' has no known NetworkName alias; " +
                    "a new UTXO chain still needs bitcoinj parameters in code."
            )
        val params = UtxoNetworkParametersResolver.resolve(networkName)
        return BitcoinNetwork(config, params)
    }
}

class UtxoNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.UTXO
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        // TASK-53 — زنجیره‌های UTXO هنوز به پارامترهای bitcoinjِ مخصوصِ خودشان نیاز دارند، پس
        // alias برای این خانواده اجباری است. اگر ناشناخته بود شبکه ساخته نمی‌شود (خطا پرتاب
        // می‌شود و رجیستری آن را رد می‌کند) — برخلاف EVM که کاملاً داده‌محور است.
        val networkName = NetworkName.fromConfigName(config.name)
            ?: throw IllegalArgumentException(
                "UTXO network '${config.id}' has no known NetworkName alias; " +
                    "a new UTXO chain still needs bitcoinj parameters in code."
            )
        val params = UtxoNetworkParametersResolver.resolve(networkName)
        return BitcoinNetwork(config, params)
    }
}

class TronNetworkFactory : NetworkFactory {
    override fun supports(networkType: NetworkType, config: NetworkConfig): Boolean {
        return networkType == NetworkType.TVM
    }

    override fun create(networkType: NetworkType, config: NetworkConfig): BlockchainNetwork {
        return TronNetwork(config)
    }
}


class BlockchainRegistry @Inject constructor(
    private val testnetVisibility: ITestnetVisibilityProvider
) : INetworkCatalog {


    /**
     * TASK-53 — همهٔ ایندکس‌ها در یک snapshotِ **تغییرناپذیر** نگهداری می‌شوند و یکجا جابه‌جا
     * می‌شوند.
     *
     * دلیل: با [applyConfig] کاتالوگ در زمان اجرا (روی ترد IO، هنگام رسیدنِ باندلِ امضاشده)
     * بازسازی می‌شود، در حالی که ViewModelها هم‌زمان از آن می‌خوانند. با mapهای تغییرپذیر این
     * یعنی `ConcurrentModificationException` یا بدتر، خواندنِ نیمه‌کاره در میانهٔ پاک‌سازی —
     * جایی که خروجی به کلیدسازی و اعتبارسنجی آدرس می‌رود. خواننده‌ها بدون قفل یک snapshot
     * سازگار می‌بینند؛ فقط نویسنده‌ها [writeLock] می‌گیرند.
     */
    private class Catalog(
        val byId: Map<String, BlockchainNetwork> = emptyMap(),
        val byChainId: Map<Long, BlockchainNetwork> = emptyMap(),
        val defaultByType: Map<NetworkType, BlockchainNetwork> = emptyMap(),
        val regexById: Map<String, Regex> = emptyMap(),
        val regexByType: Map<NetworkType, List<Regex>> = emptyMap()
    )

    @Volatile
    private var catalog = Catalog()
    private val writeLock = Any()

    /**
     * مجموعهٔ factoryهای موجود برای ساخت شبکه‌ها.
     * در حال حاضر به‌صورت داخلی مقداردهی می‌شود، اما در آینده می‌تواند از DI تزریق شود.
     */
    private val networkFactories: List<NetworkFactory> = listOf(
        EvmNetworkFactory(),
        BitcoinNetworkFactory(),
        UtxoNetworkFactory(),
        TronNetworkFactory()
    )


    fun registerNetwork(network: BlockchainNetwork) {
        synchronized(writeLock) {
            catalog = catalog.withNetwork(network)
        }
    }

    /**
     * یک شبکه را روی snapshot می‌نشاند و snapshotِ تازه را برمی‌گرداند (بدونِ تغییرِ قبلی).
     */
    private fun Catalog.withNetwork(network: BlockchainNetwork): Catalog {
        val newById = byId + (network.id to network)

        var newByChainId = byChainId
        val chainId = network.chainId
        if (chainId != null) {
            // TASK-53 — تصادفِ chainId را بی‌صدا بازنویسی نکن.
            //
            // `getNetworkByChainId` مسیرِ مسیریابیِ ارسال است (ChainDataSourceFactory.create(chainId))،
            // پس بازنویسیِ خاموش یعنی امضای تراکنش با پارامترهای زنجیرهٔ اشتباه. تا وقتی فیلترِ
            // «فقط تست‌نت» فعال بود این تصادف پنهان می‌ماند؛ حالا که همهٔ شبکه‌ها ثبت می‌شوند
            // باید صریح باشد. اولین ثبت برنده است تا نتیجه قطعی و مستقل از ترتیبِ اجرا بماند.
            val existing = byChainId[chainId]
            if (existing != null && existing.id != network.id) {
                Timber.e(
                    "chainId collision: '%s' and '%s' both declare chainId=%d. Keeping '%s' for " +
                        "chainId-keyed lookups; both remain resolvable by networkId. Fix the " +
                        "catalog — chainId-routed sends for these networks are ambiguous.",
                    existing.id, network.id, chainId, existing.id
                )
            } else {
                newByChainId = byChainId + (chainId to network)
            }
        }

        // Register as default for type
        val newDefaultByType =
            if (!defaultByType.containsKey(network.networkType) || !network.isTestnet) {
                defaultByType + (network.networkType to network)
            } else {
                defaultByType
            }

        return Catalog(newById, newByChainId, newDefaultByType, regexById, regexByType)
    }


    fun getNetworkByName(name: NetworkName): BlockchainNetwork? {
        return catalog.byId.values.find { it.name == name }
    }

    fun getNetworkById(id: String): BlockchainNetwork? {
        return catalog.byId[id]
    }


    fun getNetworkByChainId(chainId: Long): BlockchainNetwork? {
        return catalog.byChainId[chainId]
    }


    /**
     * TASK-53 — **هر شبکهٔ ثبت‌شده، بدون فیلتر.**
     *
     * این API هویتی است، نه نمایشی: [com.mtd.core.keymanager.KeyManager] از روی آن کلیدِ همهٔ
     * شبکه‌ها را می‌سازد و [AssetRegistry] با آن دارایی‌های مجاز را تعیین می‌کند. اگر خروجیِ
     * این متد به ترجیحِ «نمایش تست‌نت» گره بخورد، خاموش‌کردنِ آن کلیدهای آن شبکه‌ها را از کیف‌پول
     * حذف می‌کند — یعنی کاربر دسترسی به آدرس‌ها و موجودی‌اش را از دست می‌دهد. هرگز فیلتر نکنید.
     *
     * برای فهرست‌های UI از [getAllNetworkInfos] استفاده کنید.
     */
    fun getAllNetworks(): List<BlockchainNetwork> {
        return catalog.byId.values.toList()
    }

    /**
     * TASK-53 — **فهرستِ نمایشی**: شبکه‌های تست بر اساس ترجیحِ کاربر حذف می‌شوند.
     *
     * چون فیلتر این‌جا (زمانِ خواندن) اعمال می‌شود نه زمانِ ثبت، تغییرِ ترجیح بلافاصله اثر
     * می‌کند: نه ری‌استارت لازم است و نه networks.json دوباره پارس می‌شود.
     * جست‌وجوهای هویتی ([getNetworkById]، [getNetworkByChainId]، [getNetworkInfoById]) هرگز
     * فیلتر نمی‌شوند، پس شبکهٔ پنهان همچنان کاملاً قابلِ resolve است.
     */
    override fun getAllNetworkInfos(): List<NetworkInfo> {
        val showTestnets = testnetVisibility.showTestnets()
        return getAllNetworks()
            .filter { showTestnets || !it.isTestnet }
            .map { it.toNetworkInfo() }
    }

    override fun getNetworkInfoByName(name: NetworkName): NetworkInfo? {
        return getNetworkByName(name)?.toNetworkInfo()
    }

    override fun getNetworkInfoById(id: String): NetworkInfo? {
        return getNetworkById(id)?.toNetworkInfo()
    }

    fun getNetworkByType(type: NetworkType): BlockchainNetwork? {
        return catalog.defaultByType[type]
    }

    fun getDefaultNetworkByType(type: NetworkType): BlockchainNetwork? {
        return getAllNetworks().firstOrNull { it.networkType == type }
    }


    private fun clearAll() {
        synchronized(writeLock) { catalog = Catalog() }
    }

    /**
     * TASK-53 — کاتالوگ را از یک باندلِ **تأییدشده** بازمی‌سازد.
     *
     * فراخوان (ConfigCatalogBootstrapper) موظف است فقط باندلی را به این‌جا بدهد که یا امضای
     * secp256k1 آن تأیید شده، یا از کشِ رمزنگاری‌شدهٔ آخرین-وضعیتِ-خوب آمده، یا همان seed محلیِ
     * داخل APK است. این متد **خودش امضا را بررسی نمی‌کند** و نباید با ورودیِ شبکه‌ایِ خام صدا زده شود.
     *
     * محافظِ هویت: برای شبکه‌ای که در seed محلی وجود دارد، تغییرِ `chainId`، `derivationPath` یا
     * `regex` از سمت سرور **رد** می‌شود و نسخهٔ محلی نگه داشته می‌شود. این سه فیلد تعیین می‌کنند
     * کلید روی کدام زنجیره ساخته و آدرس چطور اعتبارسنجی می‌شود؛ عوض‌شدنشان یعنی امضا برای زنجیرهٔ
     * اشتباه یا پذیرفتنِ آدرسی که کاربر کنترلش را ندارد. شبکه‌های تازه آزادانه پذیرفته می‌شوند —
     * که دقیقاً همان قابلیتی است که این تسک می‌خواهد.
     *
     * جایگزینی اتمی است: snapshotِ کامل ساخته و یکجا نشانده می‌شود، پس خواننده‌های هم‌زمان هرگز
     * کاتالوگِ نیمه‌ساخته نمی‌بینند.
     *
     * @param configs شبکه‌های باندل.
     * @param trustedBaseline شبکه‌های seed محلی که هویتشان مرجع است.
     * @return تعداد شبکه‌های ثبت‌شده.
     */
    fun applyConfig(
        configs: List<NetworkConfig>,
        trustedBaseline: List<NetworkConfig> = emptyList()
    ): Int {
        val baselineById = trustedBaseline.associateBy { it.id.trim().lowercase() }

        val accepted = configs.mapNotNull { config ->
            val baseline = baselineById[config.id.trim().lowercase()]
                ?: return@mapNotNull config // شبکهٔ تازه — همین است که می‌خواهیم

            val violations = buildList {
                if (baseline.chainId != config.chainId) add("chainId ${baseline.chainId}->${config.chainId}")
                if (baseline.derivationPath != config.derivationPath) add("derivationPath")
                if (baseline.regex != config.regex) add("addressRegex")
            }
            if (violations.isEmpty()) {
                config
            } else {
                Timber.e(
                    "Rejected bundle override of security-critical fields for '%s' (%s); " +
                        "keeping the locally shipped definition.",
                    config.id, violations.joinToString()
                )
                baseline
            }
        }

        synchronized(writeLock) {
            clearAll()
            indexAddressRegex(accepted)
            accepted.forEach { registerFromConfig(it) }
        }
        Timber.i("Catalog applied: %d/%d networks registered", catalog.byId.size, configs.size)
        return catalog.byId.size
    }


    override fun getNetworkTypeForAddress(address: String): NetworkType? {
        try {
            val normalized = address.trim()
            if (normalized.isBlank()) return null

            val snapshot = catalog
            NetworkType.values().forEach { type ->
                val match = snapshot.regexByType[type]?.any { regex ->
                    regex.matches(normalized)
                } == true
                if (match) return type
            }

            if (WalletUtils.isValidAddress(normalized)) {
                return NetworkType.EVM
            }

            // TRON addresses are Base58 too, so detect before generic Base58 checks.
            if (normalized.startsWith("T") && normalized.length == 34) {
                try {
                    Base58.decode(normalized)
                    return NetworkType.TVM
                } catch (_: Exception) {
                    // ignore
                }
            }

            if (normalized.startsWith("bc1", true) || normalized.startsWith("tb1", true)) {
                return NetworkType.BITCOIN
            }

            val utxoCandidates = listOf(
                NetworkName.BITCOIN to NetworkType.BITCOIN,
                NetworkName.BITCOINTESTNET to NetworkType.BITCOIN,
                NetworkName.LITECOIN to NetworkType.UTXO,
                NetworkName.LTCTESTNET to NetworkType.UTXO,
                NetworkName.DOGE to NetworkType.UTXO,
                NetworkName.DOGETESTNET to NetworkType.UTXO
            )
            utxoCandidates.forEach { (networkName, type) ->
                runCatching {
                    Address.fromString(UtxoNetworkParametersResolver.resolve(networkName), normalized)
                }.onSuccess {
                    return type
                }
            }

            return null
        } catch (e: Exception) {
            return null
        }
    }

    override fun getNetworkTypeForNetworkId(networkId: String): NetworkType? {
        return getNetworkType(networkId = networkId)
    }

    fun getNetworkType(address: String? = null, networkId: String? = null): NetworkType? {
        address?.let { getNetworkTypeForAddress(it) }?.let { return it }

        val normalizedNetworkId = networkId?.trim()?.lowercase().orEmpty()
        if (normalizedNetworkId.isBlank()) return null

        return getNetworkById(normalizedNetworkId)?.networkType
            ?: inferNetworkTypeFromNetworkId(normalizedNetworkId)
    }

    override fun isValidAddressForNetworkId(address: String, networkId: String): Boolean {
        val normalizedAddress = address.trim()
        val normalizedNetworkId = networkId.trim().lowercase()
        if (normalizedAddress.isBlank() || normalizedNetworkId.isBlank()) return false

        catalog.regexById[normalizedNetworkId]?.let { regex ->
            return regex.matches(normalizedAddress)
        }

        val targetType = getNetworkType(networkId = normalizedNetworkId) ?: return false
        return getNetworkTypeForAddress(normalizedAddress) == targetType
    }

    private fun inferNetworkTypeFromNetworkId(networkId: String): NetworkType? {
        if (
            networkId.contains("tron") ||
            networkId.contains("shasta") ||
            networkId.contains("nile") ||
            networkId.contains("tvm")
        ) return NetworkType.TVM

        if (
            networkId.contains("bitcoin") ||
            networkId == "btc" ||
            networkId.startsWith("btc_")
        ) return NetworkType.BITCOIN

        if (
            networkId.contains("doge") ||
            networkId.contains("dogecoin") ||
            networkId.contains("litecoin") ||
            networkId == "ltc" ||
            networkId.startsWith("ltc_")
        ) return NetworkType.UTXO

        val evmHints = listOf(
            "ethereum", "sepolia", "evm", "bsc", "binance", "polygon", "matic",
            "arbitrum", "optimism", "base", "avalanche", "avax", "fantom", "linea",
            "zksync", "scroll", "opbnb"
        )
        if (evmHints.any { networkId.contains(it) }) return NetworkType.EVM

        return null
    }


    fun loadNetworksFromAssets(
        context: Context,
        fileName: String = "networks.json",
    ) {

        // TASK-53 — هیچ فیلتری در زمانِ ثبت. قبلاً این‌جا `filter { it.isTestnet == true }` بود،
        // یعنی هر شبکهٔ mainnet حتی از فایل محلی هم حذف می‌شد. جست‌وجوی هویتی باید همیشه جواب
        // بدهد؛ انتخابِ «نمایش تست‌نت» فقط در [getAllNetworkInfos] اعمال می‌شود.
        //
        // همان مسیرِ [applyConfig] استفاده می‌شود تا seed محلی و باندلِ سرور دقیقاً یک منطقِ
        // ساخت داشته باشند (بدون baseline، چون خودِ این فایل مرجع است).
        applyConfig(loadNetworkConfigs(context, fileName))
    }

    /**
     * TASK-53 — ساختِ یک شبکه از روی کانفیگ، با ایزوله‌سازیِ خطا.
     *
     * یک ورودیِ خرابِ منفرد (خانوادهٔ ناشناخته، یا زنجیرهٔ UTXO بدون پارامترهای bitcoinj) فقط
     * خودش رد می‌شود و بقیهٔ کاتالوگ سالم بار می‌آید. قبلاً `NetworkName.valueOf` روی هر نامِ
     * ناشناخته استثنا پرتاب می‌کرد و کلِ بارگذاری را می‌ترکاند.
     *
     * @return شبکهٔ ثبت‌شده، یا `null` اگر ورودی قابل ساخت نبود.
     */
    private fun registerFromConfig(config: NetworkConfig): BlockchainNetwork? {
        val networkType =
            runCatching { NetworkType.valueOf(config.networkType.uppercase()) }.getOrNull()
                ?: return null
        val factory = networkFactories.firstOrNull { it.supports(networkType, config) } ?: return null
        val network = runCatching { factory.create(networkType, config) }.getOrNull() ?: return null
        registerNetwork(network)
        return network
    }

    private fun indexAddressRegex(configs: List<NetworkConfig>) {
        val byId = mutableMapOf<String, Regex>()
        val byType = mutableMapOf<NetworkType, MutableList<Regex>>()

        configs.forEach { config ->
            val normalizedId = config.id.trim().lowercase()
            if (normalizedId.isBlank()) return@forEach

            val networkType = runCatching {
                NetworkType.valueOf(config.networkType.uppercase())
            }.getOrNull() ?: return@forEach

            val compiledRegex =
                AddressRegexUtils.compileAddressRegex(config.regex) ?: return@forEach

            byId[normalizedId] = compiledRegex
            byType.getOrPut(networkType) { mutableListOf() }.add(compiledRegex)
        }

        synchronized(writeLock) {
            val current = catalog
            catalog = Catalog(
                byId = current.byId,
                byChainId = current.byChainId,
                defaultByType = current.defaultByType,
                regexById = byId.toMap(),
                regexByType = byType.mapValues { (_, v) -> v.toList() }
            )
        }
    }

    private fun BlockchainNetwork.toNetworkInfo(): NetworkInfo {
        return NetworkInfo(
            id = id,
            networkType = networkType,
            name = name,
            currencySymbol = currencySymbol,
            iconUrl = iconUrl,
            faName = faName,
            decimals = decimals,
            explorers = explorers,
            explorerTxUrl = explorerTxUrl,
            color = color
        )
    }
}







