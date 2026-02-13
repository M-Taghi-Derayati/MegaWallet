package com.mtd.data.datasource

import com.mtd.core.model.NetworkConfig
import com.mtd.core.network.tron.TronNetwork
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class TronDataSourceTest {

    // Shasta Config
    private val shastaConfig = NetworkConfig(
        id = "shasta_testnet",
        name = "SHASTA",
        networkType = "TVM",
        chainId = 2494104990,
        derivationPath = "m/44'/195'/0'/0/0",
        rpcUrls = listOf("https://api.shasta.trongrid.io"), 
        currencySymbol = "TRX",
        webSocketUrl = "",
        decimals = 6,
        iconUrl = "",
        explorers = listOf("https://shasta.tronscan.org"),
        color = "#FF0013",
        faName = "Testnet Shasta",
        isTestnet = true
    )

    private val okHttpClient = OkHttpClient.Builder().build()
    private val retrofitBuilder = Retrofit.Builder().baseUrl("https://google.com")

    @Test
    fun `full tron integration test on shasta`() = runBlocking {
        println("=== Starting Tron Integration Test ===")
        
        // 1. Key Derivation
        val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about" 
        // This mnemonic generates deterministic addresses. 
        // TRON Address (m/44'/195'/0'/0/0): TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE (Example)
        
        val tronNetwork = TronNetwork(shastaConfig)
        val walletKey = tronNetwork.deriveKeyFromMnemonic(mnemonic)
        
        println("1. Generated Key:")
        println("   Mnemonic: $mnemonic")
        println("   Address: ${walletKey.address}")
        println("   Private Key: ${walletKey.address}")
        assertTrue(walletKey.address.startsWith("T"))

      /*  // 2. Data Source Connection
        val dataSource = TronDataSource(shastaConfig, retrofitBuilder, okHttpClient)
        
        // 3. Get Balance
        println("\n2. Fetching Balance...")
        val balanceResult = dataSource.getBalance(walletKey.address)
        if (balanceResult is com.mtd.domain.model.ResultResponse.Success) {
            val balanceTrx = BigDecimal(balanceResult.data).movePointLeft(6)
            println("   Balance: $balanceTrx TRX")
            
            // 4. Send Transaction (Only if balance > 10 TRX)
            if (balanceTrx > BigDecimal.TEN) {
                println("\n3. Sending Transaction (Self-Transfer)...")
                val params = TransactionParams.Tvm(
                    networkName = NetworkName.SHASTA,
                    toAddress = walletKey.address, // Send to self
                    amount = BigInteger.valueOf(1_000_000) // 1 TRX
                )
                
                val sendResult = dataSource.sendTransaction(params, walletKey.privateKeyHex)
                if (sendResult is com.mtd.domain.model.ResultResponse.Success) {
                    println("   Transaction Broadcasted! TXID: ${sendResult.data}")
                    println("   View on Explorer: https://shasta.tronscan.org/#/transaction/${sendResult.data}")
                    assertTrue(sendResult.data.isNotEmpty())
                } else {
                    val error = (sendResult as com.mtd.domain.model.ResultResponse.Error).exception
                    println("   Transaction Failed: ${error.message}")
                }
            } else {
                println("\n3. Skipping Transaction (Insufficient Balance)")
                println("   Please deposit testnet TRX to ${walletKey.address} via https://shasta.tronscan.org/#/faucet")
            }
            
        } else {
            println("   Failed to fetch balance.")
        }
        
        // 5. Get Fee Options
        println("\n4. Fetching Fee estimations...")
        // Native TRX
        val feeNative = dataSource.getFeeOptions(walletKey.address, "T...", null)
        if (feeNative is com.mtd.domain.model.ResultResponse.Success) {
            val fee = feeNative.data.first()
            println("   Estimated Fee (Native TRX): ${fee.feeInEth} TRX")
            assertTrue(fee.feeInEth!! > BigDecimal.ZERO)
        }
        
        // TRC20 Token
        val trc20Asset = Asset(
           symbol = "USDT", name = "Tether", balance = BigInteger.ZERO, decimals = 6, contractAddress = "")
        val feeToken = dataSource.getFeeOptions(walletKey.address, "T...", trc20Asset)
        if (feeToken is com.mtd.domain.model.ResultResponse.Success) {
             val fee = feeToken.data.first()
             println("   Estimated Fee (TRC20 Token): ${fee.feeInEth} TRX")
             assertTrue(fee.feeInEth!! > BigDecimal.ONE) // Should be higher than native
        }*/

        println("=== Test Finished ===")
    }
}
