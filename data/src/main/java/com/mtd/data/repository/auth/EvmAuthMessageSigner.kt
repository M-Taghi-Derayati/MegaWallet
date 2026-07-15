package com.mtd.data.repository.auth

import com.mtd.core.keymanager.KeyManager
import com.mtd.domain.interfaceRepository.IActiveWalletProvider
import com.mtd.domain.interfaceRepository.IAuthMessageSigner
import com.mtd.domain.model.AuthAccount
import com.mtd.domain.model.core.NetworkType
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 2 — [IAuthMessageSigner] that signs the Web3 auth challenge with the active wallet's EVM key.
 *
 * Reads the EVM [com.mtd.domain.model.core.WalletKey] (address + chainId) from the unlocked
 * [IActiveWalletProvider] and the in-memory web3j `Credentials` from [KeyManager] (only present after
 * unlock). Signs the challenge as an EIP-191 `personal_sign` (`signPrefixedMessage`) and returns the
 * canonical `0x` + r(32) + s(32) + v(1) = 65-byte hex the relayer's `personal_sign` recovery expects.
 *
 * All paths return `null` (never throw) when the wallet is locked or has no EVM key, so the sign-in
 * use case degrades to a clean [com.mtd.domain.model.ResultResponse.Error].
 */
@Singleton
class EvmAuthMessageSigner @Inject constructor(
    private val activeWalletProvider: IActiveWalletProvider,
    private val keyManager: KeyManager
) : IAuthMessageSigner {

    override fun activeEvmAccount(): AuthAccount? {
        val key = evmKey() ?: return null
        return AuthAccount(address = key.address, chain = AUTH_CHAIN)
    }

    override suspend fun signEvmMessage(message: String): String? {
        val key = evmKey() ?: return null
        val chainId = key.chainId ?: return null
        val credentials = keyManager.getCredentialsForChain(chainId) ?: run {
            Timber.w("[Auth] No unlocked credentials for EVM chainId=$chainId; cannot sign challenge.")
            return null
        }
        return try {
            val sig = Sign.signPrefixedMessage(message.toByteArray(Charsets.UTF_8), credentials.ecKeyPair)
            Numeric.toHexString(sig.r + sig.s + sig.v)
        } catch (e: Exception) {
            Timber.e(e, "[Auth] Failed to personal_sign the auth challenge.")
            null
        }
    }

    /** The first EVM key with a chainId on the active wallet (the EVM address is shared across EVM chains). */
    private fun evmKey() =
        activeWalletProvider.activeWallet.value?.keys
            ?.firstOrNull { it.networkType == NetworkType.EVM && it.chainId != null }

    private companion object {
        const val AUTH_CHAIN = "EVM"
    }
}
