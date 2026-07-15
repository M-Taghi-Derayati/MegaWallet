package com.mtd.data.repository.gasless

import com.mtd.core.registry.BlockchainRegistry
import com.mtd.domain.interfaceRepository.ICapabilityProvider
import com.mtd.domain.interfaceRepository.IGaslessRouteResolver
import com.mtd.domain.model.GaslessRoute
import com.mtd.domain.model.core.NetworkType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capability Platform (Android Gasless Routing Migration, Phase 1) — implements
 * [IGaslessRouteResolver].
 *
 * Sources:
 *  - [relayPrefix] (API routing) ← [ICapabilityProvider] (`networkId → relayPrefix`),
 *    offline-first + fail-safe per the capability contract.
 *  - execution family ([NetworkType]) ← [BlockchainRegistry] (local catalog).
 *
 * Returns `null` (no route) when there is no mounted relayer for the network or the
 * family is not gasless-capable (not EVM/TVM). Never throws. This is a pure routing
 * lookup — it neither sends, prepares, nor relays, and it does not touch wallet core
 * or DIRECT/PROXY transport.
 *
 * Phase 1 is additive: nothing consumes this yet (repositories/coordinators are
 * threaded in Phase 3).
 */
@Singleton
class GaslessRouteResolver @Inject constructor(
    private val capabilityProvider: ICapabilityProvider,
    private val blockchainRegistry: BlockchainRegistry
) : IGaslessRouteResolver {

    override suspend fun resolve(networkId: String): GaslessRoute? {
        val networkType = runCatching { blockchainRegistry.getNetworkById(networkId)?.networkType }
            .getOrNull() ?: return null

        // Gasless execution exists only for EVM/TVM families; other families have no route.
        if (networkType != NetworkType.EVM && networkType != NetworkType.TVM) return null

        val relayPrefix = runCatching { capabilityProvider.getNetworkCapability(networkId).relayPrefix }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return GaslessRoute(relayPrefix = relayPrefix, networkType = networkType)
    }
}

/**
 * TASK-24 — the "resolve the relayer prefix, else fall back to a family default" rule, shared by the
 * EVM and TVM gasless coordinators (previously copy-pasted in each). [familyDefault] is the family path
 * used only when capability has no route yet (offline / not-yet-fetched): `"evm"` for EVM, `"tron"` for
 * TVM. Behavior is identical to the former per-coordinator helpers.
 */
suspend fun IGaslessRouteResolver.relayPrefixFor(networkId: String, familyDefault: String): String =
    resolve(networkId)?.relayPrefix ?: familyDefault
