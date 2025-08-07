/*
package com.mtd.core.wallet


import com.mtd.core.keymanager.KeyManager
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionSigner @Inject constructor(
    private val keyManager: KeyManager
) {
    */
/**
     * یک تراکنش خام را با استفاده از کلید خصوصی ذخیره شده امضا می‌کند.
     * @param rawTransaction تراکنش خام برای امضا.
     * @param chainId شناسه زنجیره.
     * @return تراکنش امضا شده به صورت هگزادسیمال برای ارسال به نود.
     * @throws IllegalStateException اگر هیچ کیف پولی ذخیره نشده باشد.
     *//*

    fun signTransaction(rawTransaction: RawTransaction, chainId: Long): String {
        val credentials = keyManager.getCredentialsForChain(chainId)
            ?: throw IllegalStateException("Wallet is locked or key for chainId $chainId not found.")

        val signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials)
        return Numeric.toHexString(signedMessage)
    }

}*/
