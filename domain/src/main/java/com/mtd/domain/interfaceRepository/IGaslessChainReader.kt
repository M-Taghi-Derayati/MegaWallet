package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.ResultResponse
import java.math.BigInteger

/**
 * On-chain read seam for the gasless flow.
 *
 * This is the *only* blockchain access the gasless layer needs: the current ERC-20/TRC-20
 * token [getAllowance] granted to a spender, and the relayer contract's [getRelayerTreasury]
 * address. Splitting it behind this interface lets the same DIRECT/PROXY connection-mode
 * toggle that governs [IChainDataSource] also govern gasless traffic — otherwise gasless
 * sends would keep hitting public RPC nodes directly even in PROXY mode.
 *
 * All addresses are EVM-hex. Tron callers convert (Tron base58 ⇄ EVM hex) at the boundary,
 * since Tron exposes an EVM-compatible JSON-RPC for `eth_call`.
 */
interface IGaslessChainReader {

    suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger>

    suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String>
}
