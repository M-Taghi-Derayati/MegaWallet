package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.AuthAccount

/**
 * Phase 2/3 — signs the Web3 auth challenge with the **currently-unlocked** wallet key.
 *
 * The implementation lives in the data/core layer (it needs the in-memory derived credentials that
 * only exist after [com.mtd.domain.model.core.Wallet] unlock). The use case asks for the EVM account
 * up front (to build the `/challenge` request), then asks for the EIP-191 `personal_sign` of the
 * returned message. Both calls return `null` when no wallet is unlocked / no EVM key is present, so
 * the sign-in flow degrades to a clean error instead of throwing across layers.
 */
interface IAuthMessageSigner {

    /** The EVM account used for Web3 auth (address + `"EVM"` chain), or null if unavailable. */
    fun activeEvmAccount(): AuthAccount?

    /** EIP-191 `personal_sign` of [message] with the active EVM key as a `0x…`-prefixed 65-byte hex; null if unavailable. */
    suspend fun signEvmMessage(message: String): String?
}
