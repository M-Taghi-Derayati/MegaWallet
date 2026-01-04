package com.mtd.data.service

import android.Manifest
import androidx.annotation.RequiresPermission
import com.mtd.core.di.ApplicationScope
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
import com.mtd.core.notification.NotificationService
import com.mtd.core.registry.AssetRegistry
import com.mtd.core.registry.BlockchainRegistry
import com.mtd.core.socket.IWebSocketClient
import com.mtd.core.utils.GlobalEvent
import com.mtd.core.utils.GlobalEventBus
import com.mtd.data.repository.IWalletRepository
import com.mtd.domain.model.ResultResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Transaction
import org.web3j.protocol.websocket.WebSocketService
import timber.log.Timber
import java.math.BigInteger
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.timerTask


@Singleton
class TransactionMonitorService @Inject constructor(
    private val walletRepository: IWalletRepository,
    private val blockchainRegistry: BlockchainRegistry,
    private val globalEventBus: GlobalEventBus,
    private val notificationService: NotificationService,
    private val assetRegistry: AssetRegistry,
    @ApplicationScope private val externalScope: CoroutineScope,
    private val webSocketClientFactory: @JvmSuppressWildcards () -> IWebSocketClient
) {
    private val activeSockets = ConcurrentHashMap<String, NetworkSocketController>()
    private val TAG = "TransactionMonitor"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun startMonitoring() {
        if (activeSockets.isNotEmpty()) {
            Timber.tag(TAG).d("Monitoring is already active.")
            return
        }

        externalScope.launch(Dispatchers.IO) {
            val walletResult = walletRepository.loadExistingWallet()
            if (walletResult !is ResultResponse.Success || walletResult.data == null) return@launch
            val userKeys = walletResult.data?.keys ?: return@launch

            userKeys.forEach { key ->
                val network = blockchainRegistry.getNetworkByName(key.networkName) ?: return@forEach
                val url = network.webSocketUrl ?: return@forEach

                val controller = NetworkSocketController(network, key.address, url)
                controller.start()
                activeSockets[network.id] = controller
            }
        }
    }

    fun stopMonitoring() {
        Timber.tag(TAG).d("Stopping all ${activeSockets.size} active monitors...")
        activeSockets.values.forEach { it.stop() }
        activeSockets.clear()
    }

    inner class NetworkSocketController(
        private val network: BlockchainNetwork,
        private val userAddress: String,
        private val url: String
    ) {
        // برای بیت‌کوین و سایر شبکه‌های غیر EVM
        private var rawClient: IWebSocketClient? = null
        private var rawPingTimer: Timer? = null
        private var keepAliveTimer: Timer? = null
        // فقط برای شبکه‌های EVM
        private var web3j: Web3j? = null

        private var reconnectTimer: Timer? = null
        private val messageQueue = ArrayDeque<String>() // فقط برای rawClient
        private var isConnecting = false

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        fun start() {
            connect()
        }

        fun stop() {
            reconnectTimer?.cancel()
            rawPingTimer?.cancel()
            keepAliveTimer?.cancel()
            rawClient?.disconnect()
            web3j?.shutdown()
            reconnectTimer = null
            rawPingTimer = null
            keepAliveTimer = null
            rawClient = null
            web3j = null
            isConnecting = false
            Timber.tag(TAG).d("Stopped monitor for ${network.name.name}")
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun connect() {
            if (isConnecting) return
            isConnecting = true
            Timber.tag(TAG).d("Connecting to ${network.name.name} via ${network.networkType}...")

            externalScope.launch(Dispatchers.IO) {
                try {
                    if (network.networkType == NetworkType.EVM) {
                        connectEvm()
                    } else {
                        connectRaw()
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Connection failed for ${network.name.name}")
                    scheduleReconnect()
                }
            }
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun connectEvm() {
            val wsService = WebSocketService(url, false)
            wsService.connect()
            web3j = Web3j.build(wsService)
            isConnecting = false
            Timber.tag(TAG).i("✅ Web3j connected for ${network.name.name}")
            startEvmKeepAlive()

            // 2. Subscribe to New Blocks for Native Transfers
            web3j?.newHeadsNotifications()?.subscribe({ notification ->
                val blockHeader = notification.params.result
                val blockNumberHex = blockHeader.number ?: return@subscribe
                val blockNumber = BigInteger(blockNumberHex.removePrefix("0x"), 16)
                checkNativeTransactionsInBlock(blockNumber)
                checkErc20TransactionsInBlock(blockNumber)
            }, { error ->
                Timber.tag(TAG).e(error, "Error on EVM newHeads subscription for ${network.name.name}")
                stopEvmKeepAlive() // قبل از اتصال مجدد، تایمر فعلی را متوقف کن
                scheduleReconnect()
            },{
                Timber.tag(TAG).w("newHeads subscription completed for ${network.name.name}. Scheduling reconnect.")
                stopEvmKeepAlive()
                scheduleReconnect()
            })
            Timber.tag(TAG).d("Subscribed to new blocks (Native ETH) for ${network.name.name}")
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun checkErc20TransactionsInBlock(blockNumber: BigInteger) {
            externalScope.launch(Dispatchers.IO) @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS) {
                try {
                    val web3 = web3j ?: return@launch
                    // آدرس کاربر را برای جستجو در لاگ‌ها آماده می‌کنیم (باید 64 کاراکتر باشد)
                    val paddedAddress = "0x" + "0".repeat(24) + userAddress.substring(2)
                    // هش استاندارد رویداد Transfer(address,address,uint256)
                    val transferEventTopic =
                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

                    val blockParam = DefaultBlockParameterNumber(blockNumber)

                    // یک فیلتر می‌سازیم که فقط در بلاک فعلی جستجو کند
                    val filter = EthFilter(blockParam, blockParam, emptyList<String>())
                        .addSingleTopic(transferEventTopic) // Topic 0: فقط رویدادهای Transfer
                        .addNullTopic()                     // Topic 1: از هر فرستنده‌ای
                        .addSingleTopic(paddedAddress)      // Topic 2: به آدرس کاربر ما

                    // از نود می‌خواهیم لاگ‌هایی که با فیلتر ما مطابقت دارند را برگرداند
                    val logs = web3.ethGetLogs(filter).send()

                    // اگر لیستی که برگردانده خالی نبود، یعنی یک تراکنش ERC20 پیدا شده
                    if (logs.logs.isNullOrEmpty()==false) {
                        Timber.tag(TAG)
                            .i("✅ ERC20 Transfer detected for $userAddress in block #$blockNumber on ${network.name.name}")
                        // ما آدرس قرارداد را از لاگ استخراج می‌کنیم تا نماد توکن را پیدا کنیم
                        val firstLog = logs.logs.first().get() as? org.web3j.protocol.core.methods.response.Log
                        val contractAddress = firstLog?.address
                        val assetConfig = assetRegistry.getAllAssets().find {
                            it.contractAddress.equals(contractAddress, ignoreCase = true)
                        }
                        val symbol = assetConfig?.symbol ?: "Token"

                        notificationService.showTradeNotification(
                            "دریافت وجه جدید",
                            "شما مقداری $symbol در شبکه ${network.name.name} دریافت کردید."
                        )
                        notifyUiToRefresh()
                    }

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error checking ERC20 logs on ${network.name.name}")
                }
            }
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun checkNativeTransactionsInBlock(blockNumber: BigInteger) {
            externalScope.launch(Dispatchers.IO) {
                try {
                    val web3 = web3j ?: return@launch
                    val blockResponse = web3.ethGetBlockByNumber(DefaultBlockParameterNumber(blockNumber), true).send()

                    val block = blockResponse?.block
                    val transactions = block?.transactions

                    if (transactions == null) {
                        return@launch
                    }

                    val foundTxResult = transactions.find { txResult ->
                        val tx = (txResult?.get() as? Transaction)
                        tx?.to?.equals(userAddress, ignoreCase = true) == true || tx?.from?.equals(userAddress, ignoreCase = true) == true
                    }
                    val foundTx = (foundTxResult?.get() as? Transaction)
                    /*val foundTx = block?.block?.transactions?.find { txResult ->
                        (txResult.get() as? Transaction)?.to?.equals(userAddress, ignoreCase = true) == true || (txResult.get() as? Transaction)?.from?.equals(userAddress, ignoreCase = true) == true
                    }*/
                    if (foundTx!=null) {
                        Timber.tag(TAG).i("Native transaction detected for $userAddress in block #$blockNumber on ${network.name.name}")

                        if (foundTx.from?.equals(userAddress, ignoreCase = true) == true){
                            notificationService.showTradeNotification(
                                "ارسال وجه جدید",
                                "شما مقداری ${network.currencySymbol} در شبکه ${network.name.name} ارسال کردید."
                            )
                        }else{
                            notificationService.showTradeNotification(
                                "دریافت وجه جدید",
                                "شما مقداری ${network.currencySymbol} در شبکه ${network.name.name} دریافت کردید."
                            )
                        }



                        notifyUiToRefresh()
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error checking native transactions on ${network.name.name}")
                }
            }
        }

        private fun connectRaw() {
            rawClient = webSocketClientFactory()
            rawClient?.connect(url, createRawListener())
        }

        @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
        private fun scheduleReconnect() {
            // فقط در صورتی زمان‌بندی کن که از قبل در حال اتصال نباشیم
            if (isConnecting) return

            // متوقف کردن تمام فعالیت‌های فعلی قبل از تلاش مجدد
            stopEvmKeepAlive()
            web3j?.shutdown()
            web3j = null

            isConnecting = false // ریست کردن فلگ برای تلاش بعدی
            reconnectTimer?.cancel()
            reconnectTimer = Timer().apply {
                schedule(timerTask {
                    Timber.tag(TAG).d("Attempting to reconnect to ${network.name.name}...")
                    connect()
                }, 5000) // ۵ ثانیه بعد تلاش کن
            }
        }


        private fun createRawListener(): WebSocketListener {
            return object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.tag(TAG).i("✅ Raw WebSocket connected for ${network.name.name}")
                    isConnecting = false
                    flushMessageQueue()
                    startRawPing()
                    subscribe()
                }

                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun onMessage(webSocket: WebSocket, text: String) {
                    Timber.tag(TAG).d("Raw message from ${network.name.name}: $text")
                    // Handle non-EVM messages (e.g., Bitcoin)
                    if (network.networkType == NetworkType.BITCOIN) {
                        try {
                            val json = JSONObject(text)

                            // --- مرحله ۱: بررسی تراکنش‌های جدید در ممپول (تأیید نشده) ---
                            val mempoolTxs = json.optJSONArray("address-transactions")
                            if (mempoolTxs != null && mempoolTxs.length() > 0) {
                                for (i in 0 until mempoolTxs.length()) {
                                    val tx = mempoolTxs.getJSONObject(i)
                                    // چک می‌کنیم آیا این تراکنش تأیید نشده به ما مربوط است یا نه
                                    if (isTransactionRelevant(tx, userAddress)) {
                                        Timber.tag(TAG).i("PENDING Bitcoin transaction detected for $userAddress in Mempool. TxID: ${tx.optString("txid")}")
                                        // TODO: در اینجا می‌توانید یک رویداد جداگانه برای UI بفرستید
                                        // مثلاً: notifyUiTransactionPending(tx.optString("txid"))

                                        notificationService.showTradeNotification("دریافت تراکنش جدید","شما یک تراکنش جدید ${network.currencySymbol} در شبکه ${network.name.name} دریافت کردید.")


                                    }
                                }
                            }

                            // --- مرحله ۲: بررسی تراکنش‌های تأیید شده در بلاک‌ها ---
                            val blockTxs = json.optJSONArray("block-transactions")
                            if (blockTxs != null && blockTxs.length() > 0) {
                                var confirmedTxFound = false
                                for (i in 0 until blockTxs.length()) {
                                    val tx = blockTxs.getJSONObject(i)
                                    if (isTransactionRelevant(tx, userAddress)) {
                                        Timber.tag(TAG).i("✅ CONFIRMED Bitcoin transaction detected for $userAddress. TxID: ${tx.optString("txid")}")
                                        confirmedTxFound = true
                                        break // اولین تراکنش تأیید شده کافی است
                                    }
                                }

                                if (confirmedTxFound) {
                                    // حالا که یک تراکنش تأیید شده داریم، UI را برای آپدیت موجودی با خبر می‌کنیم
                                    notificationService.showTradeNotification(
                                        "دریافت وجه جدید",
                                        "شما مقداری ${network.currencySymbol} در شبکه ${network.name.name} دریافت کردید."
                                    )
                                    notifyUiToRefresh()
                                }
                            }

                        } catch (e: org.json.JSONException) {
                            Timber.tag(TAG).e(e, "Error parsing Bitcoin WebSocket message")
                        }

                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.tag(TAG).w("🔌 Raw WebSocket closing for ${network.name.name}: $reason")
                    stopRawPing()
                }

                @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.tag(TAG).e("❌ Raw WebSocket failed for ${network.name.name}: ${t.message}")
                    stopRawPing()
                    scheduleReconnect()
                }
            }
        }

        private fun startRawPing() {
            stopRawPing()
            rawPingTimer = Timer().apply {
                schedule(timerTask {
                    // Ping message might be different for different services
                    val pingMessage = "{\"method\":\"ping\"}"
                    sendMessage(pingMessage)
                    Timber.tag(TAG).v("--> Sent Ping to ${network.name.name}")
                }, 20000, 20000)
            }
        }

        private fun stopRawPing() {
            rawPingTimer?.cancel()
            rawPingTimer = null
        }

        private fun sendMessage(message: String) {
            messageQueue.add(message)
            flushMessageQueue()
        }

        private fun flushMessageQueue() {
            val clientRef = rawClient ?: return
            while (messageQueue.isNotEmpty()) {
                val msg = messageQueue.firstOrNull() ?: break
                if (clientRef.send(msg)) {
                    messageQueue.removeFirst()
                } else break
            }
        }

        private fun subscribe() {
            when (network.networkType) {
                NetworkType.BITCOIN -> subscribeToBitcoinAddress()
                // other non-EVM networks
                else -> {}
            }
        }

        private fun notifyUiToRefresh() {
            externalScope.launch { globalEventBus.postEvent(GlobalEvent.WalletNeedsRefresh) }
        }

        private fun subscribeToBitcoinAddress() {
            // این بخش به شدت وابسته به سرویس‌دهنده شماست
            // مطمئن شوید این فرمت پیام صحیح است
            sendMessage(JSONObject().put("action", "want").put("data", JSONArray(arrayOf("address-transactions", "block-transactions"))).toString())
            // ۲. سپس آدرسی را که می‌خواهیم ردیابی کنیم، برای سرور ارسال می‌کنیم.
            sendMessage(JSONObject().put("track-address", userAddress).toString())



            Timber.tag(TAG).d("Subscribed to Bitcoin address transactions for $userAddress")
        }

        private fun startEvmKeepAlive() {
            stopEvmKeepAlive()
            keepAliveTimer = Timer().apply {
                schedule(timerTask {
                    externalScope.launch(Dispatchers.IO) {
                        try {
                            // یک درخواست سبک برای زنده نگه داشتن اتصال
                            val netVersion = web3j?.netVersion()?.send()?.netVersion
                            Timber.tag(TAG).v("--> Sent Keep-Alive Ping to ${network.name.name}. Net version: $netVersion")
                        } catch (e: Exception) {
                            // اگر پینگ ناموفق بود، یعنی اتصال احتمالا قطع شده است
                            Timber.tag(TAG).w(e, "Keep-alive ping failed for ${network.name.name}.")
                            // نیازی به اتصال مجدد در اینجا نیست، چون doOnError/doOnComplete آن را مدیریت می‌کند
                        }
                    }
                }, 30000, 30000) // هر ۳۰ ثانیه
            }
        }

        private fun stopEvmKeepAlive() {
            keepAliveTimer?.cancel()
            keepAliveTimer = null
        }

        /**
         * یک تابع کمکی تمیز برای بررسی اینکه آیا یک تراکنش به آدرس ما مربوط است یا نه.
         * @param tx آبجکت JSON تراکنش
         * @param address آدرس کاربر
         * @return true اگر تراکنش مرتبط باشد، در غیر این صورت false
         */
        private fun isTransactionRelevant(tx: JSONObject, address: String): Boolean {
            val outputs = tx.optJSONArray("vout")
            if (outputs != null) {
                for (i in 0 until outputs.length()) {
                    val output = outputs.getJSONObject(i)
                    if (output.optString("scriptpubkey_address") == address) {
                        return true // پیدا شد!
                    }
                }
            }
            return false
        }
    }

}