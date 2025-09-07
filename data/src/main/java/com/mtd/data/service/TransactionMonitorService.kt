package com.mtd.data.service

import com.mtd.core.di.ApplicationScope
import com.mtd.core.model.NetworkType
import com.mtd.core.network.BlockchainNetwork
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
    @ApplicationScope private val externalScope: CoroutineScope,
    private val webSocketClientFactory: @JvmSuppressWildcards () -> IWebSocketClient
) {
    private val activeSockets = ConcurrentHashMap<String, NetworkSocketController>()
    private val TAG = "TransactionMonitor"

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
                Timber.tag(TAG).v("New block notification on ${network.name.name}: #${blockNumber}")
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

        private fun checkErc20TransactionsInBlock(blockNumber: BigInteger) {
            externalScope.launch(Dispatchers.IO) {
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
                    if (logs.logs.isNotEmpty()) {
                        Timber.tag(TAG)
                            .i("✅ ERC20 Transfer detected for $userAddress in block #$blockNumber on ${network.name.name}")
                        notifyUiToRefresh()
                    }

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error checking ERC20 logs on ${network.name.name}")
                }
            }
        }


        private fun checkNativeTransactionsInBlock(blockNumber: BigInteger) {
            externalScope.launch(Dispatchers.IO) {
                try {
                    val web3 = web3j ?: return@launch
                    val block = web3.ethGetBlockByNumber(DefaultBlockParameterNumber(blockNumber), true).send()
                    val foundTx = block.block.transactions.any { txResult ->
                        (txResult.get() as? Transaction)?.to?.equals(userAddress, ignoreCase = true) == true || (txResult.get() as? Transaction)?.from?.equals(userAddress, ignoreCase = true) == true
                    }
                    if (foundTx) {
                        Timber.tag(TAG).i("Native transaction detected for $userAddress in block #$blockNumber on ${network.name.name}")
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

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Timber.tag(TAG).d("Raw message from ${network.name.name}: $text")
                    // Handle non-EVM messages (e.g., Bitcoin)
                    if (network.networkType == NetworkType.BITCOIN) {
                        try {
                            val json = JSONObject(text)

                            // مرحله ۱: بررسی کنید که آیا این پیام، یک پیام تراکنش است یا نه.
                            // اکثر APIها برای تراکنش یک کلید مشخص دارند. فرض می‌کنیم اینجا کلید "x" است.
                            // شما باید این را با مستندات API خود چک کنید.
                            if (json.has("x")) {
                                val txObject = json.getJSONObject("x")

                                // مرحله ۲ (بسیار مهم): بررسی کنید که آیا این تراکنش به آدرس ما مربوط است یا نه.
                                // سرور باید فقط تراکنش‌های ما را بفرستد، اما همیشه بهتر است خودمان هم چک کنیم.
                                // ما باید خروجی‌های (outputs) تراکنش را بگردیم.
                                val outputs = txObject.optJSONArray("out")
                                var isRelevant = false
                                if (outputs != null) {
                                    for (i in 0 until outputs.length()) {
                                        val output = outputs.getJSONObject(i)
                                        // آیا آدرس این خروجی با آدرس کاربر ما یکی است؟
                                        if (output.optString("addr") == userAddress) {
                                            isRelevant = true
                                            break // یک خروجی مرتبط پیدا شد، جستجو کافی است
                                        }
                                    }
                                }

                                if (isRelevant) {
                                    Timber.tag(TAG).i("✅ Relevant Bitcoin transaction detected for $userAddress")
                                    notifyUiToRefresh()
                                } else {
                                    Timber.tag(TAG).d("Received a Bitcoin transaction message, but it was not for our address.")
                                }

                            } else {
                                // این یک پیام از نوع دیگر است (مثل قیمت). آن را نادیده می‌گیریم.
                                Timber.tag(TAG).d("Ignoring non-transaction Bitcoin message (e.g., price update).")
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
            sendMessage(JSONObject().put("action", "want").put("data", JSONArray(arrayOf("address-transactions"))).toString())
            sendMessage(JSONObject().put("address", userAddress).toString())
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
    }

}