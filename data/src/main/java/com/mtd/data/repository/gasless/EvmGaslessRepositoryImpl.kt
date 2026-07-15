package com.mtd.data.repository.gasless

import com.mtd.domain.interfaceRepository.IGaslessChainReader
import com.mtd.domain.interfaceRepository.IGaslessEvmRepository
import com.mtd.domain.model.EvmPrepareData
import com.mtd.domain.model.EvmQueuedTx
import com.mtd.domain.model.EvmQuoteData
import com.mtd.domain.model.EvmQuoteRequest
import com.mtd.domain.model.EvmRelayPayload
import com.mtd.domain.model.EvmApproveQuoteRequest
import com.mtd.domain.model.EvmApproveQuoteResult
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.EvmTxStatus
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.ResultResponse
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvmGaslessRepositoryImpl @Inject constructor(
    private val gaslessApiGateway: GaslessApiGateway,
    private val chainReader: IGaslessChainReader
) : IGaslessEvmRepository {

    override suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger?,
        relayPrefix: String
    ): ResultResponse<EvmPrepareData> {
        return gaslessApiGateway.prepare(
            userAddress = userAddress,
            startNonce = startNonce?.toString(),
            relayPrefix = relayPrefix
        )
    }

    override suspend fun quote(request: EvmQuoteRequest, relayPrefix: String): ResultResponse<EvmQuoteData> {
        return gaslessApiGateway.quote(networkType = NetworkType.EVM, request = request, relayPrefix = relayPrefix)
    }

    override suspend fun submitRelay(
        payload: EvmRelayPayload,
        idempotencyKey: String,
        relayPrefix: String
    ): ResultResponse<EvmQueuedTx> {
        return gaslessApiGateway.relay(
            payload = payload.copy(networkType = NetworkType.EVM),
            idempotencyKey = idempotencyKey,
            relayPrefix = relayPrefix
        )
    }

    override suspend fun getTxStatus(txId: String, relayPrefix: String): ResultResponse<EvmTxStatus> {
        return gaslessApiGateway.getTxStatus(txId, relayPrefix = relayPrefix)
    }

    override suspend fun sponsorApprove(
        request: EvmSponsorApproveRequest,
        relayPrefix: String
    ): ResultResponse<EvmSponsorApproveResult> {
        return gaslessApiGateway.sponsorEvmApprove(request, relayPrefix = relayPrefix)
    }

    override suspend fun quoteApprove(
        request: EvmApproveQuoteRequest,
        relayPrefix: String
    ): ResultResponse<EvmApproveQuoteResult> {
        return gaslessApiGateway.quoteEvmApprove(request, relayPrefix = relayPrefix)
    }

    override suspend fun getSupportedTokens(relayPrefix: String): ResultResponse<List<GaslessSupportedToken>> {
        return gaslessApiGateway.getSupportedTokens(NetworkType.EVM, relayPrefix = relayPrefix)
    }

    override suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String,
        relayPrefix: String
    ): ResultResponse<GaslessEligibilityResult> {
        return gaslessApiGateway.checkEligibility(
            networkType = NetworkType.EVM,
            service = service,
            userAddress = userAddress,
            tokenAddress = tokenAddress,
            relayPrefix = relayPrefix
        )
    }

    // On-chain reads are delegated to the connection-mode-aware chain reader
    // (DIRECT today; PROXY later), so the DIRECT/PROXY toggle governs gasless reads too.
    // EVM addresses are already in EVM-hex space, so no conversion is needed here.
    override suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger> =
        chainReader.getAllowance(networkId, tokenAddress, ownerAddress, spenderAddress)

    override suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String> =
        chainReader.getRelayerTreasury(networkId, relayerContractAddress)
}
