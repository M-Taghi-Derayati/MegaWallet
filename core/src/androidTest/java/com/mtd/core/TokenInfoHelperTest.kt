package com.mtd.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtd.core.utils.TokenInfoHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class TokenInfoHelperTest {

    private lateinit var web3j: Web3j
    
    // آدرس یک توکن USDT تستی واقعی در شبکه Sepolia
    private val USDT_SEPOLIA_ADDRESS = "0x186cca6904490818AB0DC409ca59D932A2366031"
    // آدرس یک کاربر که مقداری از این توکن را دارد
    private val TOKEN_HOLDER_ADDRESS = "0xD07F3994a98d35FEC2BCaa4A308F3Dd221cE369c" // آدرس تست ما

    @Before
    fun setUp() {
        // از یک RPC عمومی برای Sepolia استفاده می‌کنیم
        val rpcUrl = "https://sepolia.drpc.org"
        web3j = Web3j.build(HttpService(rpcUrl))
    }

    @Test
    fun getNonce() = runBlocking {
        // Act
        val nonce = TokenInfoHelper.getNonce(web3j, USDT_SEPOLIA_ADDRESS, TOKEN_HOLDER_ADDRESS)
        
        // Assert
        assertNotNull("Nonce should not be null", nonce)
        assertTrue("Nonce should be a non-negative integer", nonce >= BigInteger.ZERO)
        println("✅ Fetched Nonce: $nonce")
    }

    @Test
    fun getName() = runBlocking {
        // Act
        val name = TokenInfoHelper.getName(web3j, USDT_SEPOLIA_ADDRESS)
        
        // Assert
        // نام دقیق ممکن است کمی متفاوت باشد، پس چک می‌کنیم که خالی نباشد
        assertFalse("Token name should not be empty", name.isBlank())
        assertEquals("Token name should be Tether USD", "Tether USD", name)
        println("✅ Fetched Token Name: $name")
    }

    @Test
    fun getVersion () = runBlocking {
        // Act
        val version = TokenInfoHelper.getVersion(web3j, USDT_SEPOLIA_ADDRESS)
        
        // Assert
        // اکثر توکن‌ها این تابع را ندارند و ما انتظار مقدار پیش‌فرض "1" را داریم
        assertNotNull("Version should not be null", version)
        assertEquals("Default version should be '1'", "1", version)
        println("✅ Fetched Version: $version")
    }
}