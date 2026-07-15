package com.mtd.domain.interfaceRepository

import com.mtd.domain.model.GaslessRoute

/**
 * Capability Platform (Android Gasless Routing Migration, Phase 1) — resolves the
 * data-driven [GaslessRoute] for a network, replacing the hardcoded `GaslessChain`
 * enum / `BSC_CHAIN_IDS` mapping.
 *
 * Single source of truth: routing derives from `networkId → capability → relayPrefix`
 * (execution family comes from the local network registry). The backend capability is
 * the authority for which relayer (if any) serves a network.
 *
 * Contract:
 *  - Returns `null` when the network has **no mounted relayer** (capability `relayPrefix`
 *    is null/blank) or its **family does not support gasless** (not EVM/TVM).
 *  - **Never throws** — a missing/offline capability simply yields `null` (no route),
 *    so the gasless feature degrades gracefully and the wallet core is unaffected.
 */
interface IGaslessRouteResolver {
    suspend fun resolve(networkId: String): GaslessRoute?
}
