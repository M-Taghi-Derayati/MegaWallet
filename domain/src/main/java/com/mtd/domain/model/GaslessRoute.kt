package com.mtd.domain.model

import com.mtd.domain.model.core.NetworkType

/**
 * Capability Platform (Android Gasless Routing Migration, Phase 1).
 *
 * Replaces the overloaded `GaslessChain` enum, which conflated two unrelated concerns:
 *  - API routing (the `/api/<path>` segment), and
 *  - blockchain execution family (EVM vs TVM signing).
 *
 * [GaslessRoute] keeps them separate and data-driven:
 *  - [relayPrefix] — the backend API routing segment, sourced from the capability
 *    snapshot (`networkId → relayPrefix`, e.g. "evm", "bsc", "base", "tron"). Drives
 *    /tokens, /eligibility, /quote, /prepare, /relay, /sponsor-approve.
 *  - [networkType] — the EXECUTION family only (EVM or TVM): which signing/flow to use
 *    (EIP-712/Permit2 for EVM; TRON signing for TVM). Never used for API routing.
 *
 * Examples: BSC → ("bsc", EVM); Ethereum → ("evm", EVM); TRON → ("tron", TVM).
 */
data class GaslessRoute(
    val relayPrefix: String,
    val networkType: NetworkType
)
