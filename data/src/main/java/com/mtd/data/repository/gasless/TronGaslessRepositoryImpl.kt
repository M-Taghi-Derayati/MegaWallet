package com.mtd.data.repository.gasless

import com.mtd.core.utils.TronAddressConverter
import com.mtd.domain.interfaceRepository.IGaslessChainReader
import com.mtd.domain.interfaceRepository.IGaslessTronRepository
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorApproveResult
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TronGaslessRepositoryImpl @Inject constructor(
    private val gaslessApiGateway: GaslessApiGateway,
    private val chainReader: IGaslessChainReader
) : IGaslessTronRepository {

    override suspend fun prepare(
        userAddress: String,
        startNonce: BigInteger?,
        relayPrefix: String
    ): ResultResponse<GaslessPrepareData> {
        return gaslessApiGateway.prepare(
            userAddress = userAddress,
            startNonce = startNonce?.toString(),
            relayPrefix = relayPrefix
        )
    }

    override suspend fun quote(request: GaslessQuoteRequest, relayPrefix: String): ResultResponse<GaslessQuoteData> {
        return gaslessApiGateway.quote(networkType = NetworkType.TVM, request = request, relayPrefix = relayPrefix)
    }

    override suspend fun submitRelay(
        payload: GaslessRelayPayload,
        idempotencyKey: String,
        relayPrefix: String
    ): ResultResponse<GaslessQueuedTx> {
        return gaslessApiGateway.relay(
            payload = payload.copy(networkType = NetworkType.TVM),
            idempotencyKey = idempotencyKey,
            relayPrefix = relayPrefix
        )
    }

    override suspend fun getTxStatus(txId: String, relayPrefix: String): ResultResponse<GaslessTxStatus> {
        return gaslessApiGateway.getTxStatus(txId, relayPrefix = relayPrefix)
    }

    override suspend fun getSupportedTokens(relayPrefix: String): ResultResponse<List<GaslessSupportedToken>> {
        return gaslessApiGateway.getSupportedTokens(NetworkType.TVM, relayPrefix = relayPrefix)
    }

    override suspend fun checkEligibility(
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String,
        relayPrefix: String
    ): ResultResponse<GaslessEligibilityResult> {
        return gaslessApiGateway.checkEligibility(
            networkType = NetworkType.TVM,
            service = service,
            userAddress = userAddress,
            tokenAddress = tokenAddress,
            relayPrefix = relayPrefix
        )
    }

    override suspend fun quoteApprove(request: TronApproveQuoteRequest, relayPrefix: String): ResultResponse<TronApproveQuoteResult> {
        return gaslessApiGateway.quoteTronApprove(request, relayPrefix = relayPrefix)
    }

    override suspend fun sponsorApprove(request: TronSponsorApproveRequest, relayPrefix: String): ResultResponse<TronSponsorApproveResult> {
        return gaslessApiGateway.sponsorTronApprove(request, relayPrefix = relayPrefix)
    }

    // Tron addresses are converted to EVM-hex at the boundary; the shared chain reader
    // performs the actual eth_call in EVM space (Tron exposes an EVM-compatible JSON-RPC).
    override suspend fun getAllowance(
        networkId: String,
        tokenAddress: String,
        ownerAddress: String,
        spenderAddress: String
    ): ResultResponse<BigInteger> {
        val tokenEvm: String
        val ownerEvm: String
        val spenderEvm: String
        try {
            tokenEvm = TronAddressConverter.tronToEvm(tokenAddress)
            ownerEvm = TronAddressConverter.tronToEvm(ownerAddress)
            spenderEvm = TronAddressConverter.tronToEvm(spenderAddress)
        } catch (e: Exception) {
            return ResultResponse.Error(e)
        }
        return chainReader.getAllowance(networkId, tokenEvm, ownerEvm, spenderEvm)
    }

    override suspend fun getRelayerTreasury(
        networkId: String,
        relayerContractAddress: String
    ): ResultResponse<String> {
        val relayerEvm = try {
            TronAddressConverter.tronToEvm(relayerContractAddress)
        } catch (e: Exception) {
            return ResultResponse.Error(e)
        }
        return when (val result = chainReader.getRelayerTreasury(networkId, relayerEvm)) {
            is ResultResponse.Success -> try {
                ResultResponse.Success(TronAddressConverter.evmToTron(result.data))
            } catch (e: Exception) {
                ResultResponse.Error(e)
            }
            is ResultResponse.Error -> ResultResponse.Error(result.exception)
        }
    }
}
