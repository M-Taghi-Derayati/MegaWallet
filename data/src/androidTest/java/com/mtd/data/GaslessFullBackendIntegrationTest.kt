package com.mtd.data

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.mtd.core.encryption.SecureStorage
import com.mtd.domain.model.error.AppError
import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.model.core.NetworkType
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.utils.EvmAbiEncoder
import com.mtd.core.utils.TronAbiEncoder
import com.mtd.core.utils.TronAddressConverter
import com.mtd.core.utils.TypedDataSigner
import com.mtd.data.datasource.ChainDataSourceFactory
import com.mtd.data.di.NetworkConnectionInterceptor
import com.mtd.data.di.NetworkModule
import com.mtd.data.di.NetworkModule.httpLoggingInterceptorProvider
import com.mtd.data.di.NetworkModule.provideOkHttpClient
import com.mtd.data.di.NetworkModule.provideRetrofitBuilder
import com.mtd.data.dto.GaslessRelayParamsDto
import com.mtd.data.dto.GaslessRelayRequestDto
import com.mtd.data.repository.WalletRepositoryImpl
import com.mtd.data.repository.gasless.EvmGaslessRepositoryImpl
import com.mtd.data.repository.gasless.GaslessApiGateway
import com.mtd.data.repository.gasless.TronGaslessRepositoryImpl
import com.mtd.data.service.GaslessApiService
import com.mtd.domain.interfaceRepository.IWalletRepository
import com.mtd.domain.model.Asset
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.EvmRelayParams
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.GaslessApiException
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayParams
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TransactionParams
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorMode
import com.mtd.core.wallet.ActiveWalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.math.BigDecimal
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GaslessFullBackendIntegrationTest {
    //Android Studio Run Configuration (Instrumentation args)
    private lateinit var context: Context
    private lateinit var blockchainRegistry: BlockchainRegistry
    private lateinit var keyManager: KeyManager
    private lateinit var walletRepository: IWalletRepository
    private lateinit var dataSourceFactory: ChainDataSourceFactory
    private lateinit var gaslessApiService: GaslessApiService
    private lateinit var gaslessApiGateway: GaslessApiGateway
    private lateinit var evmRepository: EvmGaslessRepositoryImpl
    private lateinit var tronRepository: TronGaslessRepositoryImpl
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var backendBaseUrl: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val args = InstrumentationRegistry.getArguments()
        backendBaseUrl = args.getString("gasless_base_url")
            ?: "http://${NetworkModule.serverIp}:3000/"

        val networkInterceptor = NetworkConnectionInterceptor(context)
        okHttpClient = provideOkHttpClient(httpLoggingInterceptorProvider(), networkInterceptor)
        val gson = Gson()
        val retrofitBuilder = provideRetrofitBuilder(okHttpClient, gson)

        blockchainRegistry = BlockchainRegistry().apply { loadNetworksFromAssets(context) }
        keyManager = KeyManager(blockchainRegistry)
        val assetRegistry = AssetRegistry(blockchainRegistry).apply { loadAssetsFromAssets(context) }
        dataSourceFactory = ChainDataSourceFactory(
            blockchainRegistry = blockchainRegistry,
            retrofitBuilder = retrofitBuilder,
            assetRegistry = assetRegistry,
            okHttpClient = okHttpClient
        )

        val secureStorage = SecureStorage(context)
        val activeWalletManager = ActiveWalletManager(keyManager)
        walletRepository = WalletRepositoryImpl(
            keyManager = keyManager,
            secureStorage = secureStorage,
            activeWalletManager = activeWalletManager,
            blockchainRegistry = blockchainRegistry,
            dataSourceFactory = dataSourceFactory
        )

        gaslessApiService = Retrofit.Builder()
            .baseUrl(backendBaseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(50, TimeUnit.SECONDS)
                    .readTimeout(50, TimeUnit.SECONDS)
                    .writeTimeout(50, TimeUnit.SECONDS)
                    .build()
            )
            .build()
            .create(GaslessApiService::class.java)
        gaslessApiGateway = GaslessApiGateway(gaslessApiService)
        evmRepository = EvmGaslessRepositoryImpl(gaslessApiGateway, blockchainRegistry, okHttpClient)
        tronRepository = TronGaslessRepositoryImpl(gaslessApiGateway, blockchainRegistry, okHttpClient)
    }

    @Test
    fun evm_full_e2e_without_ui() = runBlocking {
        val cfg = loadEvmConfig()
        assumeTrue("Backend is unreachable: $backendBaseUrl", isBackendReachable(backendBaseUrl))

        val evmNetwork = blockchainRegistry.getNetworkById(cfg.networkId)
        assertNotNull("EVM network not found: ${cfg.networkId}", evmNetwork)
        assertEquals(NetworkType.EVM, evmNetwork!!.networkType)

        val wallet = importPrivateKeyWallet(cfg.privateKey, "e2e_evm_wallet")
        val userAddress = wallet.keys.find { it.networkName == evmNetwork.name }?.address
            ?: throw AssertionError("Failed to derive EVM address for ${evmNetwork.name}")
        val credentials = Credentials.create(cfg.privateKey)
        assertTrue(
            "Derived wallet address mismatch. expected=${credentials.address}, actual=$userAddress",
            credentials.address.equals(userAddress, ignoreCase = true)
        )

        val allowanceBefore = evmRepository.getAllowance(
            networkId = cfg.networkId,
            tokenAddress = cfg.tokenAddress,
            ownerAddress = userAddress,
            spenderAddress = cfg.permit2Address
        ).requireSuccess("evm.allowance.before")

        if (allowanceBefore < cfg.amount) {
            val sponsor = evmRepository.sponsorApprove(
                EvmSponsorApproveRequest(
                    userAddress = userAddress,
                    tokenAddress = cfg.tokenAddress,
                    mode = cfg.sponsorMode
                )
            ).requireSuccess("evm.sponsorApprove")
            assertNotNull("sponsor result should not be null", sponsor)

            assumeTrue(
                "Allowance is insufficient and auto approve is disabled",
                cfg.autoApprove
            )

            val feeOption = resolveEvmFeeOption(
                networkId = cfg.networkId,
                senderAddress = userAddress,
                targetAddress = cfg.targetAddress,
                tokenAddress = cfg.tokenAddress,
                tokenDecimals = cfg.tokenDecimals
            )

            assertEvmApproveCallPreflight(
                networkId = cfg.networkId,
                ownerAddress = userAddress,
                tokenAddress = cfg.tokenAddress,
                spenderAddress = cfg.permit2Address,
                amount = cfg.approveAmount
            )

            // Some ERC20 contracts (USDT-like) revert on non-zero -> non-zero approve.
            // Reset allowance to 0 first when there is any previous allowance.
            if (allowanceBefore > BigInteger.ZERO) {
                val resetTxHash = walletRepository.sendTransaction(
                    TransactionParams.Evm(
                        networkName = evmNetwork.name,
                        to = cfg.tokenAddress,
                        amount = BigInteger.ZERO,
                        data = EvmAbiEncoder.encodeApprove(cfg.permit2Address, BigInteger.ZERO),
                        gasPrice = feeOption.first,
                        gasLimit = feeOption.second
                    )
                ).requireSuccess("evm.approve.reset.tx")
                assertTrue("Invalid reset approve tx hash: $resetTxHash", resetTxHash.startsWith("0x"))

                waitForEvmAllowanceAtMost(
                    networkId = cfg.networkId,
                    tokenAddress = cfg.tokenAddress,
                    ownerAddress = userAddress,
                    spenderAddress = cfg.permit2Address,
                    expectedMax = BigInteger.ZERO,
                    timeoutMs = cfg.approveTimeoutMs
                )
            }

            val approveTxHash = walletRepository.sendTransaction(
                TransactionParams.Evm(
                    networkName = evmNetwork.name,
                    to = cfg.tokenAddress,
                    amount = BigInteger.ZERO,
                    data = EvmAbiEncoder.encodeApprove(cfg.permit2Address, cfg.approveAmount),
                    gasPrice = feeOption.first,
                    gasLimit = feeOption.second
                )
            ).requireSuccess("evm.approve.tx")
            assertTrue("Invalid approve tx hash: $approveTxHash", approveTxHash.startsWith("0x"))

            waitForEvmAllowance(
                networkId = cfg.networkId,
                tokenAddress = cfg.tokenAddress,
                ownerAddress = userAddress,
                spenderAddress = cfg.permit2Address,
                expectedMin = cfg.amount,
                timeoutMs = cfg.approveTimeoutMs
            )
        }

        // Approve can take long enough that an earlier prepare/quote session expires.
        val prepare = evmRepository.prepare(userAddress).requireSuccess("evm.prepare")

        val quote = evmRepository.quote(
            EvmQuoteRequest(
                prepareToken = prepare.prepareToken,
                user = userAddress,
                token = cfg.tokenAddress,
                target = cfg.targetAddress,
                amount = cfg.amount
            )
        ).requireSuccess("evm.quote")

        val permitSignature = TypedDataSigner.signTypedDataHex(
            credentials = credentials,
            primaryType = "PermitTransferFrom",
            types = permit2Types(),
            domain = mapOf(
                "name" to "Permit2",
                "chainId" to prepare.chainId,
                "verifyingContract" to cfg.permit2Address
            ),
            message = mapOf(
                "permitted" to mapOf(
                    "token" to cfg.tokenAddress,
                    "amount" to quote.canonicalParams.amount.toString()
                ),
                "spender" to prepare.relayerContract,
                "nonce" to quote.canonicalParams.nonce.toString(),
                "deadline" to quote.canonicalParams.deadline.toString()
            )
        )

        val megaSignature = TypedDataSigner.signTypedDataHex(
            credentials = credentials,
            primaryType = "MegaTransfer",
            types = megaTransferTypes(),
            domain = mapOf(
                "name" to "MegaRelayer",
                "version" to "1.0.0",
                "chainId" to prepare.chainId,
                "verifyingContract" to prepare.relayerContract
            ),
            message = mapOf(
                "user" to userAddress,
                "token" to cfg.tokenAddress,
                "amount" to quote.canonicalParams.amount.toString(),
                "feeAmount" to quote.canonicalParams.feeAmount.toString(),
                "target" to cfg.targetAddress,
                "treasury" to quote.canonicalParams.treasury,
                "nonce" to quote.canonicalParams.nonce.toString(),
                "deadline" to quote.canonicalParams.deadline.toString()
            )
        )

        val relayPayload = EvmRelayPayload(
            chain = GaslessChain.EVM,
            quoteToken = quote.quoteToken,
            params = EvmRelayParams(
                user = userAddress,
                token = cfg.tokenAddress,
                target = cfg.targetAddress,
                amount = quote.canonicalParams.amount,
                feeAmount = quote.canonicalParams.feeAmount,
                nonce = quote.canonicalParams.nonce,
                deadline = quote.canonicalParams.deadline
            ),
            permitSignature = permitSignature,
            megaSignature = megaSignature
        )

        val idempotencyKey = UUID.randomUUID().toString()
        val queued = evmRepository.submitRelay(relayPayload, idempotencyKey)
            .requireSuccess("evm.relay")

        val duplicateResponse = gaslessApiService.relayGasless(
            chain = GaslessChain.EVM.apiPath,
            idempotencyKey = idempotencyKey,
            request = relayPayload.toRelayRequestDto()
        )
        assertTrue(
            "Unexpected duplicate relay response code: ${duplicateResponse.code()}",
            duplicateResponse.code() == 200 || duplicateResponse.code() == 409
        )

        val final = pollUntilFinalEvm(queued = queued, timeoutMs = cfg.pollTimeoutMs)
        assertTrue(
            "EVM gasless final status should be SUCCESS. status=${final.status}, error=${final.lastError}",
            final.status.equals("SUCCESS", ignoreCase = true)
        )
    }

    @Test
    fun tron_full_e2e_without_ui() = runBlocking {
        val cfg = loadTronConfig()
        assumeTrue("Backend is unreachable: $backendBaseUrl", isBackendReachable(backendBaseUrl))

        val tronNetwork = blockchainRegistry.getNetworkById(cfg.networkId)
        assertNotNull("TRON network not found: ${cfg.networkId}", tronNetwork)
        assertEquals(NetworkType.TVM, tronNetwork!!.networkType)

        val wallet = importPrivateKeyWallet(cfg.privateKey, "e2e_tron_wallet")
        val userAddress = wallet.keys.find { it.networkName == tronNetwork.name }?.address
            ?: throw AssertionError("Failed to derive TRON address for ${tronNetwork.name}")

        val prepare = tronRepository.prepare(userAddress).requireSuccess("tron.prepare")

        val allowanceBefore = tronRepository.getAllowance(
            networkId = cfg.networkId,
            tokenAddress = cfg.tokenAddress,
            ownerAddress = userAddress,
            spenderAddress = prepare.relayerContract
        ).requireSuccess("tron.allowance.before")

        val approveQuote = tronRepository.quoteApprove(
            TronApproveQuoteRequest(
                userAddress = userAddress,
                tokenAddress = cfg.tokenAddress
            )
        ).requireSuccess("tron.quote.approve")
        assertTrue("tron.quote.approve requiredSun must be > 0", approveQuote.requiredSun > BigInteger.ZERO)

        if (allowanceBefore < cfg.amount) {
            val sponsor = tronRepository.sponsorApprove(
                TronSponsorApproveRequest(
                    userAddress = userAddress,
                    tokenAddress = cfg.tokenAddress,
                    mode = cfg.sponsorMode
                )
            ).requireSuccess("tron.sponsorApprove")
            assertNotNull("sponsor result should not be null", sponsor)

            assumeTrue(
                "Allowance is insufficient and auto approve is disabled",
                cfg.autoApprove
            )

            val approveTxHash = walletRepository.sendTransaction(
                TransactionParams.Tvm(
                    networkName = tronNetwork.name,
                    toAddress = prepare.relayerContract,
                    amount = cfg.approveAmount,
                    contractAddress = cfg.tokenAddress,
                    feeLimit = cfg.feeLimit,
                    contractFunction = "approve(address,uint256)",
                    contractParameter = TronAbiEncoder.encodeAddressAndUint256(
                        prepare.relayerContract,
                        cfg.approveAmount
                    )
                )
            ).requireSuccess("tron.approve.tx")
            assertTrue("Invalid TRON approve tx hash: $approveTxHash", approveTxHash.length >= 32)

            waitForTronAllowance(
                networkId = cfg.networkId,
                tokenAddress = cfg.tokenAddress,
                ownerAddress = userAddress,
                spenderAddress = prepare.relayerContract,
                expectedMin = cfg.amount,
                timeoutMs = cfg.approveTimeoutMs
            )
        }

        val quote = tronRepository.quote(
            GaslessQuoteRequest(
                prepareToken = prepare.prepareToken,
                user = userAddress,
                token = cfg.tokenAddress,
                target = cfg.targetAddress,
                amount = cfg.amount
            )
        ).requireSuccess("tron.quote")

        val credentials = Credentials.create(cfg.privateKey)
        val relayerEvm = TronAddressConverter.tronToEvm(prepare.relayerContract)
        val userEvm = TronAddressConverter.tronToEvm(userAddress)
        val tokenEvm = TronAddressConverter.tronToEvm(cfg.tokenAddress)
        val targetEvm = TronAddressConverter.tronToEvm(cfg.targetAddress)
        val treasuryEvm = TronAddressConverter.tronToEvm(quote.canonicalParams.treasury)

        val signature = TypedDataSigner.signTypedDataHex(
            credentials = credentials,
            primaryType = "MegaTransfer",
            types = megaTransferTypes(),
            domain = mapOf(
                "name" to "MegaRelayerTron",
                "version" to "1.0.0",
                "chainId" to prepare.chainId,
                "verifyingContract" to relayerEvm
            ),
            message = mapOf(
                "user" to userEvm,
                "token" to tokenEvm,
                "amount" to quote.canonicalParams.amount.toString(),
                "feeAmount" to quote.canonicalParams.feeAmount.toString(),
                "target" to targetEvm,
                "treasury" to treasuryEvm,
                "nonce" to quote.canonicalParams.nonce.toString(),
                "deadline" to quote.canonicalParams.deadline.toString()
            )
        )

        val relayPayload = GaslessRelayPayload(
            chain = GaslessChain.TRON,
            quoteToken = quote.quoteToken,
            params = GaslessRelayParams(
                user = userAddress,
                token = cfg.tokenAddress,
                target = cfg.targetAddress,
                amount = quote.canonicalParams.amount,
                feeAmount = quote.canonicalParams.feeAmount,
                nonce = quote.canonicalParams.nonce,
                deadline = quote.canonicalParams.deadline
            ),
            signature = signature
        )

        val idempotencyKey = UUID.randomUUID().toString()
        val queued = tronRepository.submitRelay(relayPayload, idempotencyKey)
            .requireSuccess("tron.relay")

        val duplicateResponse = gaslessApiService.relayGasless(
            chain = GaslessChain.TRON.apiPath,
            idempotencyKey = idempotencyKey,
            request = relayPayload.toRelayRequestDto()
        )
        assertTrue(
            "Unexpected duplicate relay response code: ${duplicateResponse.code()}",
            duplicateResponse.code() == 200 || duplicateResponse.code() == 409
        )

        val final = pollUntilFinalTron(queued = queued, timeoutMs = cfg.pollTimeoutMs)
        assertTrue(
            "TRON gasless final status should be SUCCESS. status=${final.status}, error=${final.lastError}",
            final.status.equals("SUCCESS", ignoreCase = true)
        )
    }

    private suspend fun importPrivateKeyWallet(privateKey: String, name: String) =
        run {
            clearExistingWallets()
            walletRepository.importWalletFromPrivateKey(privateKey, name, Color.RED)
                .requireSuccess("wallet.importPrivateKey")
        }

    private suspend fun clearExistingWallets() {
        while (walletRepository.hasWallet()) {
            walletRepository.deleteWallet()
        }
    }

    private suspend fun resolveEvmFeeOption(
        networkId: String,
        senderAddress: String,
        targetAddress: String,
        tokenAddress: String,
        tokenDecimals: Int
    ): Pair<BigInteger, BigInteger> {
        val network = blockchainRegistry.getNetworkById(networkId)
            ?: return Pair(BigInteger("2000000000"), BigInteger("150000"))
        val chainId = network.chainId
            ?: return Pair(BigInteger("2000000000"), BigInteger("150000"))

        val dataSource = dataSourceFactory.create(chainId)
        val feeOptions = dataSource.getFeeOptions(
            fromAddress = senderAddress,
            toAddress = targetAddress,
            asset = Asset(
                name = "Token",
                symbol = "TOKEN",
                decimals = tokenDecimals,
                contractAddress = tokenAddress,
                balance = BigDecimal.ZERO
            )
        )

        return when (feeOptions) {
            is ResultResponse.Success -> {
                val pick = feeOptions.data.firstOrNull()
                val gasPrice = pick?.gasPrice ?: BigInteger("2000000000")
                val gasLimit = pick?.gasLimit ?: BigInteger("150000")
                Pair(gasPrice, gasLimit)
            }
            is ResultResponse.Error -> Pair(BigInteger("2000000000"), BigInteger("150000"))
        }
    }

    private suspend fun waitForEvmAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String,
        expectedMin: BigInteger,
        timeoutMs: Long
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val allowance = evmRepository.getAllowance(
                networkId = networkId,
                tokenAddress = tokenAddress,
                ownerAddress = ownerAddress,
                spenderAddress = spenderAddress
            ).requireSuccess("evm.allowance.wait")
            if (allowance >= expectedMin) return
            delay(4_000L)
        }
        throw AssertionError("EVM allowance did not reach expected value in time")
    }

    private suspend fun assertEvmApproveCallPreflight(
        networkId: String,
        ownerAddress: String,
        tokenAddress: String,
        spenderAddress: String,
        amount: BigInteger
    ) {
        val network = blockchainRegistry.getNetworkById(networkId)
            ?: throw AssertionError("EVM network not found for preflight: $networkId")

        var lastError: Exception? = null
        for (rpcUrl in network.RpcUrlsEvm) {
            try {
                val web3j = Web3j.build(HttpService(rpcUrl, okHttpClient, false))
                val codeResponse = web3j.ethGetCode(tokenAddress, DefaultBlockParameterName.LATEST).send()
                if (codeResponse.hasError()) {
                    throw IllegalStateException("eth_getCode failed on $rpcUrl: ${codeResponse.error.message}")
                }
                val code = codeResponse.code ?: "0x"
                if (code == "0x" || code == "0x0") {
                    throw IllegalStateException("Token address has no bytecode on $rpcUrl: $tokenAddress")
                }

                val function = Function(
                    "approve",
                    listOf(Address(spenderAddress), Uint256(amount)),
                    listOf(object : TypeReference<Bool>() {})
                )
                val callResponse = web3j.ethCall(
                    Transaction.createEthCallTransaction(
                        ownerAddress,
                        tokenAddress,
                        FunctionEncoder.encode(function)
                    ),
                    DefaultBlockParameterName.LATEST
                ).send()
                if (callResponse.hasError()) {
                    throw IllegalStateException("approve eth_call reverted on $rpcUrl: ${callResponse.error.message}")
                }

                val decoded = FunctionReturnDecoder.decode(
                    callResponse.value,
                    function.outputParameters
                )
                if (decoded.isNotEmpty()) {
                    val approveResult = decoded.first().value as? Boolean
                    if (approveResult == false) {
                        throw IllegalStateException("approve eth_call returned false on $rpcUrl")
                    }
                }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw AssertionError(
            "EVM approve preflight failed for token=$tokenAddress spender=$spenderAddress",
            lastError
        )
    }

    private suspend fun waitForEvmAllowanceAtMost(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String,
        expectedMax: BigInteger,
        timeoutMs: Long
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val allowance = evmRepository.getAllowance(
                networkId = networkId,
                tokenAddress = tokenAddress,
                ownerAddress = ownerAddress,
                spenderAddress = spenderAddress
            ).requireSuccess("evm.allowance.wait.max")
            if (allowance <= expectedMax) return
            delay(4_000L)
        }
        throw AssertionError("EVM allowance did not drop to expected max value in time")
    }

    private suspend fun waitForTronAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String,
        expectedMin: BigInteger,
        timeoutMs: Long
    ) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val allowance = tronRepository.getAllowance(
                networkId = networkId,
                tokenAddress = tokenAddress,
                ownerAddress = ownerAddress,
                spenderAddress = spenderAddress
            ).requireSuccess("tron.allowance.wait")
            if (allowance >= expectedMin) return
            delay(4_000L)
        }
        throw AssertionError("TRON allowance did not reach expected value in time")
    }

    private suspend fun pollUntilFinalEvm(
        queued: GaslessQueuedTx,
        timeoutMs: Long
    ): GaslessTxStatus {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val status = evmRepository.getTxStatus(queued.id).requireSuccess("evm.txStatus")
            if (status.isFinal) return status
            delay(4_000L)
        }
        throw AssertionError("EVM tx status polling timed out for id=${queued.id}")
    }

    private suspend fun pollUntilFinalTron(
        queued: GaslessQueuedTx,
        timeoutMs: Long
    ): GaslessTxStatus {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val status = tronRepository.getTxStatus(queued.id).requireSuccess("tron.txStatus")
            if (status.isFinal) return status
            delay(4_000L)
        }
        throw AssertionError("TRON tx status polling timed out for id=${queued.id}")
    }

    private fun isBackendReachable(baseUrl: String): Boolean {
        return runCatching {
            val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val host = normalized
                .removePrefix("http://")
                .removePrefix("https://")
                .substringBefore("/")
                .substringBefore(":")
            val isHttps = normalized.startsWith("https://")
            val port = normalized
                .substringAfter(host, "")
                .substringAfter(":", "")
                .substringBefore("/")
                .toIntOrNull()
                ?: if (isHttps) 443 else 80

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2_000)
            }
            true
        }.getOrElse { false }
    }

    private fun loadEvmConfig(): EvmGaslessConfig {
        val args = InstrumentationRegistry.getArguments()
        val privateKey = requireArg(args, "gasless_evm_private_key")
        val token = requireArg(args, "gasless_evm_token")
        val target = requireArg(args, "gasless_evm_target")
        val amount = requireArg(args, "gasless_evm_amount").toBigInteger()
        val permit2 = args.getString("gasless_evm_permit2")
            ?: "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        return EvmGaslessConfig(
            privateKey = privateKey,
            networkId = args.getString("gasless_evm_network_id") ?: "sepolia",
            tokenAddress = token,
            targetAddress = target,
            amount = amount,
            tokenDecimals = args.getString("gasless_evm_token_decimals")?.toIntOrNull() ?: 6,
            permit2Address = permit2,
            sponsorMode = args.getString("gasless_evm_sponsor_mode")
                ?.let { TronSponsorMode.fromApiValue(it) }
                ?: TronSponsorMode.GIFT,
            autoApprove = args.getString("gasless_evm_auto_approve")?.toBooleanStrictOrNull() ?: true,
            approveAmount = args.getString("gasless_evm_approve_amount")?.toBigIntegerOrNull() ?: amount,
            approveTimeoutMs = (args.getString("gasless_evm_approve_timeout_sec")?.toLongOrNull() ?: 180L) * 1000L,
            pollTimeoutMs = (args.getString("gasless_evm_poll_timeout_sec")?.toLongOrNull() ?: 240L) * 1000L
        )
    }

    private fun loadTronConfig(): TronGaslessConfig {
        val args = InstrumentationRegistry.getArguments()
        val privateKey = requireArg(args, "gasless_tron_private_key")
        val token = requireArg(args, "gasless_tron_token")
        val target = requireArg(args, "gasless_tron_target")
        val amount = requireArg(args, "gasless_tron_amount").toBigInteger()
        return TronGaslessConfig(
            privateKey = privateKey,
            networkId = args.getString("gasless_tron_network_id") ?: "shasta_testnet",
            tokenAddress = token,
            targetAddress = target,
            amount = amount,
            sponsorMode = args.getString("gasless_tron_sponsor_mode")
                ?.let { TronSponsorMode.fromApiValue(it) }
                ?: TronSponsorMode.GIFT,
            autoApprove = args.getString("gasless_tron_auto_approve")?.toBooleanStrictOrNull() ?: true,
            approveAmount = args.getString("gasless_tron_approve_amount")?.toBigIntegerOrNull() ?: amount,
            feeLimit = args.getString("gasless_tron_fee_limit")?.toLongOrNull() ?: 30_000_000L,
            approveTimeoutMs = (args.getString("gasless_tron_approve_timeout_sec")?.toLongOrNull() ?: 180L) * 1000L,
            pollTimeoutMs = (args.getString("gasless_tron_poll_timeout_sec")?.toLongOrNull() ?: 240L) * 1000L
        )
    }

    private fun requireArg(
        args: android.os.Bundle,
        key: String
    ): String {
        val value = args.getString(key)?.trim()
        assumeTrue("Missing instrumentation arg: $key", !value.isNullOrBlank())
        return value!!
    }

    private fun permit2Types(): Map<String, List<Map<String, String>>> {
        return mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "TokenPermissions" to listOf(
                mapOf("name" to "token", "type" to "address"),
                mapOf("name" to "amount", "type" to "uint256")
            ),
            "PermitTransferFrom" to listOf(
                mapOf("name" to "permitted", "type" to "TokenPermissions"),
                mapOf("name" to "spender", "type" to "address"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )
    }

    private fun megaTransferTypes(): Map<String, List<Map<String, String>>> {
        return mapOf(
            "EIP712Domain" to listOf(
                mapOf("name" to "name", "type" to "string"),
                mapOf("name" to "version", "type" to "string"),
                mapOf("name" to "chainId", "type" to "uint256"),
                mapOf("name" to "verifyingContract", "type" to "address")
            ),
            "MegaTransfer" to listOf(
                mapOf("name" to "user", "type" to "address"),
                mapOf("name" to "token", "type" to "address"),
                mapOf("name" to "amount", "type" to "uint256"),
                mapOf("name" to "feeAmount", "type" to "uint256"),
                mapOf("name" to "target", "type" to "address"),
                mapOf("name" to "treasury", "type" to "address"),
                mapOf("name" to "nonce", "type" to "uint256"),
                mapOf("name" to "deadline", "type" to "uint256")
            )
        )
    }

    private fun GaslessRelayPayload.toRelayRequestDto(): GaslessRelayRequestDto {
        return GaslessRelayRequestDto(
            chain = chain.name,
            quoteToken = quoteToken,
            params = GaslessRelayParamsDto(
                user = params.user,
                token = params.token,
                target = params.target,
                amount = params.amount.toString(),
                feeAmount = params.feeAmount.toString(),
                nonce = params.nonce.toString(),
                deadline = params.deadline
            ),
            permitSignature = permitSignature,
            megaSignature = megaSignature,
            signature = signature
        )
    }

    private fun <T> ResultResponse<T>.requireSuccess(step: String): T {
        return when (this) {
            is ResultResponse.Success -> data
            is ResultResponse.Error -> {
                val detail = when (val ex = exception) {
                    is AppError.Unexpected -> {
                        val original = ex.originalException
                        when (original) {
                            is GaslessApiException -> {
                                val body = original.responseBody?.takeIf { it.isNotBlank() } ?: "<empty>"
                                "${original.message}; body=$body"
                            }
                            else -> original.message ?: original.toString()
                        }
                    }
                    is AppError.Network.Unknown -> ex.originalException.message ?: ex.originalException.toString()
                    else -> ex.message ?: ex.toString()
                }
                throw AssertionError("$step failed: $detail", exception)
            }
        }
    }

    private data class EvmGaslessConfig(
        val privateKey: String,
        val networkId: String,
        val tokenAddress: String,
        val targetAddress: String,
        val amount: BigInteger,
        val tokenDecimals: Int,
        val permit2Address: String,
        val sponsorMode: TronSponsorMode,
        val autoApprove: Boolean,
        val approveAmount: BigInteger,
        val approveTimeoutMs: Long,
        val pollTimeoutMs: Long
    )

    private data class TronGaslessConfig(
        val privateKey: String,
        val networkId: String,
        val tokenAddress: String,
        val targetAddress: String,
        val amount: BigInteger,
        val sponsorMode: TronSponsorMode,
        val autoApprove: Boolean,
        val approveAmount: BigInteger,
        val feeLimit: Long,
        val approveTimeoutMs: Long,
        val pollTimeoutMs: Long
    )
}
