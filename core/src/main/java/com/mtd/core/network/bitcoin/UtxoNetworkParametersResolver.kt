package com.mtd.core.network.bitcoin

import com.mtd.domain.model.core.NetworkName
import org.bitcoinj.base.Coin
import org.bitcoinj.base.Monetary
import org.bitcoinj.base.Network
import org.bitcoinj.base.internal.ByteUtils
import org.bitcoinj.base.utils.MonetaryFormat
import org.bitcoinj.core.BitcoinSerializer
import org.bitcoinj.core.Block
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.StoredBlock
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.store.BlockStore
import java.time.Instant

object UtxoNetworkParametersResolver {
    private val cache = mutableMapOf<NetworkName, NetworkParameters>()

    @Synchronized
    fun resolve(networkName: NetworkName): NetworkParameters {
        return cache.getOrPut(networkName) {
            when (networkName) {
                NetworkName.BITCOIN -> MainNetParams.get()
                NetworkName.BITCOINTESTNET -> TestNet3Params.get()
                NetworkName.LITECOIN -> GenericUtxoNetworkParameters(litecoinMainnetSpec)
                NetworkName.LTCTESTNET -> GenericUtxoNetworkParameters(litecoinTestnetSpec)
                NetworkName.DOGE -> GenericUtxoNetworkParameters(dogecoinMainnetSpec)
                NetworkName.DOGETESTNET -> GenericUtxoNetworkParameters(dogecoinTestnetSpec)
                else -> throw IllegalArgumentException("Unsupported UTXO network: $networkName")
            }
        }
    }
}

private data class GenericUtxoSpec(
    val networkId: String,
    val uriScheme: String,
    val addressHeader: Int,
    val p2shHeader: Int,
    val dumpedPrivateKeyHeader: Int,
    val segwitHrp: String,
    val bip32P2pkhPub: Int,
    val bip32P2pkhPriv: Int,
    val bip32P2wpkhPub: Int,
    val bip32P2wpkhPriv: Int,
    val port: Int,
    val packetMagic: Int,
    val genesisEpochSeconds: Long,
    val genesisDifficultyBits: Long,
    val genesisNonce: Long,
    val maxTargetBits: Long,
    val hasMaxMoney: Boolean,
    val maxMoneySatoshis: Long
)

private class GenericUtxoNetwork(
    private val spec: GenericUtxoSpec
) : Network {
    override fun id(): String = spec.networkId
    override fun legacyAddressHeader(): Int = spec.addressHeader
    override fun legacyP2SHHeader(): Int = spec.p2shHeader
    override fun segwitAddressHrp(): String = spec.segwitHrp
    override fun uriScheme(): String = spec.uriScheme
    override fun hasMaxMoney(): Boolean = spec.hasMaxMoney
    override fun maxMoney(): Monetary = Coin.valueOf(spec.maxMoneySatoshis)
    override fun exceedsMaxMoney(monetary: Monetary): Boolean {
        return hasMaxMoney() && monetary.value > maxMoney().value
    }
}

private class GenericUtxoNetworkParameters(
    private val spec: GenericUtxoSpec
) : NetworkParameters(GenericUtxoNetwork(spec)) {

    private var genesisBlock: Block? = null

    init {
        targetTimespan = TARGET_TIMESPAN
        interval = INTERVAL
        maxTarget = ByteUtils.decodeCompactBits(spec.maxTargetBits)

        port = spec.port
        packetMagic = spec.packetMagic
        dumpedPrivateKeyHeader = spec.dumpedPrivateKeyHeader
        addressHeader = spec.addressHeader
        p2shHeader = spec.p2shHeader
        segwitAddressHrp = spec.segwitHrp
        spendableCoinbaseDepth = 100
        subsidyDecreaseBlockCount = 210_000

        bip32HeaderP2PKHpub = spec.bip32P2pkhPub
        bip32HeaderP2PKHpriv = spec.bip32P2pkhPriv
        bip32HeaderP2WPKHpub = spec.bip32P2wpkhPub
        bip32HeaderP2WPKHpriv = spec.bip32P2wpkhPriv

        majorityEnforceBlockUpgrade = 51
        majorityRejectBlockOutdated = 75
        majorityWindow = 100
    }

    @Deprecated("bitcoinj legacy API")
    override fun getPaymentProtocolId(): String = spec.networkId

    override fun checkDifficultyTransitions(
        storedPrev: StoredBlock,
        next: Block,
        blockStore: BlockStore
    ) {
        // Wallet-side operations don't validate alternative-chain difficulty transitions.
    }

    override fun getGenesisBlock(): Block {
        return genesisBlock ?: synchronized(this) {
            genesisBlock ?: Block.createGenesis(
                Instant.ofEpochSecond(spec.genesisEpochSeconds),
                spec.genesisDifficultyBits,
                spec.genesisNonce
            ).also { genesisBlock = it }
        }
    }

    @Deprecated("bitcoinj legacy API")
    override fun getMaxMoney(): Coin = Coin.valueOf(spec.maxMoneySatoshis)

    @Deprecated("bitcoinj legacy API")
    override fun getMonetaryFormat(): MonetaryFormat = MonetaryFormat()

    @Deprecated("bitcoinj legacy API")
    override fun getUriScheme(): String = spec.uriScheme

    @Deprecated("bitcoinj legacy API")
    override fun hasMaxMoney(): Boolean = spec.hasMaxMoney

    override fun getSerializer(): BitcoinSerializer = BitcoinSerializer(network())
}

private const val LTC_MAX_MONEY_SATS = 8_400_000_000_000_000L
private const val DOGE_VIRTUAL_MAX_MONEY_SATS = Long.MAX_VALUE

private val litecoinMainnetSpec = GenericUtxoSpec(
    networkId = "org.megawallet.litecoin.main",
    uriScheme = "litecoin",
    addressHeader = 48,
    p2shHeader = 50,
    dumpedPrivateKeyHeader = 176,
    segwitHrp = "ltc",
    bip32P2pkhPub = 0x019da462,
    bip32P2pkhPriv = 0x019d9cfe,
    bip32P2wpkhPub = 0x04b24746,
    bip32P2wpkhPriv = 0x04b2430c,
    port = 9333,
    packetMagic = 0xfbc0b6db.toInt(),
    genesisEpochSeconds = 1317972665L,
    genesisDifficultyBits = 0x1e0ffff0,
    genesisNonce = 2084524493L,
    maxTargetBits = 0x1e0fffff,
    hasMaxMoney = true,
    maxMoneySatoshis = LTC_MAX_MONEY_SATS
)

private val litecoinTestnetSpec = GenericUtxoSpec(
    networkId = "org.megawallet.litecoin.test",
    uriScheme = "litecoin",
    addressHeader = 111,
    p2shHeader = 58,
    dumpedPrivateKeyHeader = 239,
    segwitHrp = "tltc",
    bip32P2pkhPub = 0x043587cf,
    bip32P2pkhPriv = 0x04358394,
    bip32P2wpkhPub = 0x045f1cf6,
    bip32P2wpkhPriv = 0x045f18bc,
    port = 19335,
    packetMagic = 0xfcc1b7dc.toInt(),
    genesisEpochSeconds = 1296688602L,
    genesisDifficultyBits = 0x1d00ffff,
    genesisNonce = 414098458L,
    maxTargetBits = 0x1d00ffff,
    hasMaxMoney = true,
    maxMoneySatoshis = LTC_MAX_MONEY_SATS
)

private val dogecoinMainnetSpec = GenericUtxoSpec(
    networkId = "org.megawallet.dogecoin.main",
    uriScheme = "dogecoin",
    addressHeader = 30,
    p2shHeader = 22,
    dumpedPrivateKeyHeader = 158,
    segwitHrp = "doge",
    bip32P2pkhPub = 0x02facafd,
    bip32P2pkhPriv = 0x02fac398,
    bip32P2wpkhPub = 0x04b24746,
    bip32P2wpkhPriv = 0x04b2430c,
    port = 22556,
    packetMagic = 0xc0c0c0c0.toInt(),
    genesisEpochSeconds = 1386325540L,
    genesisDifficultyBits = 0x1e0ffff0,
    genesisNonce = 99943L,
    maxTargetBits = 0x1e0fffff,
    hasMaxMoney = false,
    maxMoneySatoshis = DOGE_VIRTUAL_MAX_MONEY_SATS
)

private val dogecoinTestnetSpec = GenericUtxoSpec(
    networkId = "org.megawallet.dogecoin.test",
    uriScheme = "dogecoin",
    addressHeader = 113,
    p2shHeader = 196,
    dumpedPrivateKeyHeader = 241,
    segwitHrp = "tdge",
    bip32P2pkhPub = 0x043587cf,
    bip32P2pkhPriv = 0x04358394,
    bip32P2wpkhPub = 0x045f1cf6,
    bip32P2wpkhPriv = 0x045f18bc,
    port = 44556,
    packetMagic = 0xfcc1b7dc.toInt(),
    genesisEpochSeconds = 1391503289L,
    genesisDifficultyBits = 0x1e0ffff0,
    genesisNonce = 997879L,
    maxTargetBits = 0x1e0fffff,
    hasMaxMoney = false,
    maxMoneySatoshis = DOGE_VIRTUAL_MAX_MONEY_SATS
)
