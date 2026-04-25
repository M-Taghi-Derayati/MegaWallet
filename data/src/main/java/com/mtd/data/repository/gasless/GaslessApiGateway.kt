package com.mtd.data.repository.gasless

import com.google.gson.internal.LinkedTreeMap
import com.mtd.data.dto.GaslessDisplayPolicyDto
import com.mtd.data.dto.GaslessDisplayPolicyItemDto
import com.mtd.data.dto.GaslessEligibilityParamsDto
import com.mtd.data.dto.GaslessEligibilityRequestDto
import com.mtd.core.utils.TronAddressConverter
import com.mtd.data.dto.EvmSponsorApproveParamsDto
import com.mtd.data.dto.EvmSponsorApproveRequestDto
import com.mtd.data.dto.GaslessQuoteParamsDto
import com.mtd.data.dto.GaslessQuoteRequestDto
import com.mtd.data.dto.GaslessRelayParamsDto
import com.mtd.data.dto.GaslessRelayRequestDto
import com.mtd.data.dto.TronApproveQuoteRequestDto
import com.mtd.data.dto.TronSponsorApproveParamsDto
import com.mtd.data.dto.TronSponsorApproveRequestDto
import com.mtd.data.service.GaslessApiService
import com.mtd.data.utils.safeApiCall
import com.mtd.domain.model.EvmSponsorApproveRequest
import com.mtd.domain.model.EvmSponsorApproveResult
import com.mtd.domain.model.GaslessApiException
import com.mtd.domain.model.GaslessCanonicalParams
import com.mtd.domain.model.GaslessChain
import com.mtd.domain.model.GaslessEligibilityReason
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessDisplayPolicy
import com.mtd.domain.model.GaslessDisplayPolicyBundle
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorApproveResult
import com.mtd.domain.model.TronSponsorMode
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GaslessApiGateway @Inject constructor(
    private val gaslessApiService: GaslessApiService
) {

    suspend fun getSupportedTokens(chain: GaslessChain): ResultResponse<List<GaslessSupportedToken>> {
        return safeApiCall {
            val response = gaslessApiService.getSupportedTokens(chain.apiPath)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "tokens ${chain.name} failed (${response.code()})"
                )
            }

            body.mapNotNull { item ->
                val token = item.token?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GaslessSupportedToken(
                    chain = chain,
                    token = token,
                    symbol = item.symbol,
                    gaslessEnabled = item.gaslessEnabled == true,
                    sponsorEnabled = item.sponsorEnabled == true,
                    note = item.note
                )
            }
        }
    }

    suspend fun checkEligibility(
        chain: GaslessChain,
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String
    ): ResultResponse<GaslessEligibilityResult> {
        return safeApiCall {
            val response = gaslessApiService.checkEligibility(
                chain = chain.apiPath,
                request = GaslessEligibilityRequestDto(
                    chain = chain.name,
                    service = service.apiValue,
                    params = GaslessEligibilityParamsDto(
                        user = userAddress,
                        token = tokenAddress
                    )
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "eligibility ${chain.name}/${service.apiValue} failed (${response.code()})"
                )
            }

            GaslessEligibilityResult(
                chain = chain,
                service = service,
                user = body.user ?: userAddress,
                token = body.token ?: tokenAddress,
                allowed = body.allowed == true,
                rollout = body.rollout?.let {
                    GaslessEligibilityReason(
                        allowed = it.allowed == true,
                        reasonCode = it.reasonCode,
                        reasonFa = it.reasonFa
                    )
                },
                tokenPolicy = body.tokenPolicy?.let {
                    GaslessEligibilityReason(
                        allowed = it.allowed == true,
                        reasonCode = it.reasonCode,
                        reasonFa = it.reasonFa
                    )
                }
            )
        }
    }

    suspend fun prepare(
        chain: GaslessChain,
        userAddress: String,
        startNonce: String? = null
    ): ResultResponse<GaslessPrepareData> {
        return safeApiCall {
            val response = gaslessApiService.prepareGasless(
                chain = chain.apiPath,
                userAddress = userAddress,
                startNonce = startNonce
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "prepare ${chain.name} failed (${response.code()})"
                )
            }

            GaslessPrepareData(
                userAddress = body.user ?: userAddress,
                nonce = body.nonce?.toBigIntegerOrNull()
                    ?: throw IllegalStateException("Missing or invalid nonce in prepare response"),
                deadline = body.deadline,
                chainId = body.chainId
                    ?: throw IllegalStateException("Missing chainId in prepare response"),
                relayerContract = body.relayerContract
                    ?: throw IllegalStateException("Missing relayerContract in prepare response"),
                treasuryAddress = body.treasury,
                prepareToken = body.prepareToken
                    ?: throw IllegalStateException("Missing prepareToken in prepare response"),
                prepareExpiresAt = body.prepareExpiresAt
            )
        }
    }

    suspend fun quote(chain: GaslessChain, request: GaslessQuoteRequest): ResultResponse<GaslessQuoteData> {
        return safeApiCall {
            val response = gaslessApiService.quoteGasless(
                chain = chain.apiPath,
                request = GaslessQuoteRequestDto(
                    chain = chain.name,
                    prepareToken = request.prepareToken,
                    params = GaslessQuoteParamsDto(
                        user = request.user,
                        token = request.token,
                        target = request.target,
                        amount = request.amount.toString()
                    ),
                    clientFeeAmount = request.clientFeeAmount?.toString()
                ),
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "quote ${chain.name} failed (${response.code()})"
                )
            }

            val canonical = body.canonicalParams
                ?: throw IllegalStateException("Missing canonicalParams in quote response")

            GaslessQuoteData(
                quoteToken = body.quoteToken
                    ?: throw IllegalStateException("Missing quoteToken in quote response"),
                canonicalParams = GaslessCanonicalParams(
                    user = canonical.user ?: request.user,
                    token = canonical.token ?: request.token,
                    target = canonical.target ?: request.target,
                    amount = canonical.amount.toBigIntOrThrow("canonical.amount"),
                    feeAmount = canonical.feeAmount.toBigIntOrThrow("canonical.feeAmount"),
                    nonce = canonical.nonce.toBigIntOrThrow("canonical.nonce"),
                    deadline = canonical.deadline
                        ?: throw IllegalStateException("Missing canonical.deadline in quote response"),
                    treasury = normalizeTreasuryAddress(
                        chain = chain,
                        treasury = canonical.treasury
                            ?: throw IllegalStateException("Missing canonical.treasury in quote response")
                    )
                ),
                serverFeeAmount = body.serverQuote?.feeAmount?.toBigIntegerOrNull(),
                displayPolicy = body.displayPolicy?.toDomain()
            )
        }
    }

    suspend fun relay(payload: GaslessRelayPayload, idempotencyKey: String): ResultResponse<GaslessQueuedTx> {
        return safeApiCall {
            require(idempotencyKey.isNotBlank()) { "x-idempotency-key must not be blank" }
            require(payload.quoteToken.isNotBlank()) { "quoteToken must not be blank" }

            val request = GaslessRelayRequestDto(
                chain = payload.chain.name,
                quoteToken = payload.quoteToken,
                params = GaslessRelayParamsDto(
                    user = payload.params.user,
                    token = payload.params.token,
                    target = payload.params.target,
                    amount = payload.params.amount.toString(),
                    feeAmount = payload.params.feeAmount.toString(),
                    nonce = payload.params.nonce.toString(),
                    deadline = payload.params.deadline
                ),
                permitSignature = payload.permitSignature,
                megaSignature = payload.megaSignature,
                signature = payload.signature
            )



            val response = gaslessApiService.relayGasless(
                chain = payload.chain.apiPath,
                idempotencyKey = idempotencyKey,
                request = request
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "relay ${payload.chain.name} failed (${response.code()})"
                )
            }

            GaslessQueuedTx(
                id = body.id ?: throw IllegalStateException("Missing tx id in relay response"),
                stage = body.stage ?: body.status
            )
        }
    }

    suspend fun getTxStatus(
        chain: GaslessChain,
        txId: String
    ): ResultResponse<GaslessTxStatus> {
        return safeApiCall {
            val response = gaslessApiService.getGaslessTxStatus(chain.apiPath, txId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "tx status ${chain.name} failed (${response.code()})"
                )
            }

            GaslessTxStatus(
                id = normalizeTxId(body.id) ?: txId,
                status = body.status ?: "UNKNOWN",
                txHash = body.txHash,
                lastError = body.lastError,
                rawStatus = body.status
            )
        }
    }

    suspend fun sponsorTronApprove(request: TronSponsorApproveRequest): ResultResponse<TronSponsorApproveResult> {
        return safeApiCall {
            val response = gaslessApiService.sponsorTronApprove(
                request = TronSponsorApproveRequestDto(
                    chain = GaslessChain.TRON.name,
                    params = TronSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    ),
                    mode = request.mode.apiValue
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "sponsor approve TRON failed (${response.code()})"
                )
            }

            TronSponsorApproveResult(
                funded = body.funded ?: false,
                mode = TronSponsorMode.fromApiValue(body.mode),
                amount = body.amount?.toBigIntegerOrNull(),
                reason = body.reason,
                txHash = body.txHash,
                sponsorDisplayPolicy = body.displayPolicy?.sponsorApprove?.toDomain()
            )
        }
    }

    suspend fun quoteTronApprove(chain: GaslessChain, request: TronApproveQuoteRequest): ResultResponse<TronApproveQuoteResult> {
        return safeApiCall {
            val response = gaslessApiService.quoteTronApprove(
                chain = chain.apiPath,
                request = TronApproveQuoteRequestDto(
                    chain = GaslessChain.TRON.name,
                    params = TronSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    )
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "quote approve TRON failed (${response.code()})"
                )
            }

            TronApproveQuoteResult(
                estimatedEnergy = body.estimatedEnergy?.toBigIntegerOrNull(),
                estimatedBandwidthBytes = body.estimatedBandwidthBytes?.toBigIntegerOrNull(),
                energyFeeSun = body.energyFeeSun?.toBigIntegerOrNull(),
                bandwidthFeeSun = body.bandwidthFeeSun?.toBigIntegerOrNull(),
                requiredSun = body.requiredSun?.toBigIntegerOrNull()
                    ?: throw IllegalStateException("Missing requiredSun in TRON approve quote response"),
                requiredTrx = body.requiredTrx,
                requiredUsdApprox = body.requiredUsdApprox,
                source = body.source,
                sponsorDisplayPolicy = body.displayPolicy?.sponsorApprove?.toDomain()
            )
        }
    }

    suspend fun sponsorEvmApprove(request: EvmSponsorApproveRequest): ResultResponse<EvmSponsorApproveResult> {
        return safeApiCall {
            val response = gaslessApiService.sponsorEvmApprove(
                request = EvmSponsorApproveRequestDto(
                    chain = GaslessChain.EVM.name,
                    params = EvmSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    ),
                    mode = request.mode.apiValue
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                throw GaslessApiException(
                    statusCode = response.code(),
                    responseBody = errorBody,
                    message = "sponsor approve EVM failed (${response.code()})"
                )
            }

            EvmSponsorApproveResult(
                funded = body.funded ?: false,
                mode = TronSponsorMode.fromApiValue(body.mode),
                amount = body.amount?.toBigIntegerOrNull(),
                reason = body.reason,
                txHash = body.txHash,
                sponsorDisplayPolicy = body.displayPolicy?.sponsorApprove?.toDomain()
            )
        }
    }

    private fun GaslessDisplayPolicyDto.toDomain(): GaslessDisplayPolicyBundle {
        return GaslessDisplayPolicyBundle(
            gasless = gasless?.toDomain(),
            sponsorApprove = sponsorApprove?.toDomain()
        )
    }

    private fun GaslessDisplayPolicyItemDto.toDomain(): GaslessDisplayPolicy {
        return GaslessDisplayPolicy(
            required = required,
            mode = mode,
            displayAmount = displayAmount,
            displayToken = displayToken,
            displayUsd = displayUsd,
            displayIrr = displayIrr,
            willDeductFromUser = willDeductFromUser,
            deductSource = deductSource,
            reasonFa = reasonFa
        )
    }

    private fun String?.toBigIntOrThrow(fieldName: String): BigInteger {
        return this?.toBigIntegerOrNull()
            ?: throw IllegalStateException("Missing or invalid $fieldName in quote response")
    }

    private fun normalizeTreasuryAddress(chain: GaslessChain, treasury: String): String {
        if (chain != GaslessChain.TRON) return treasury
        return runCatching { TronAddressConverter.evmToTron(treasury) }
            .getOrElse { treasury }
    }

    private fun normalizeTxId(raw: Any?): String? {
        return when (raw) {
            null -> null
            is String -> raw.ifBlank { null }
            is Number -> raw.toString()
            is LinkedTreeMap<*, *> -> {
                val oid = raw["\$oid"]?.toString()
                if (!oid.isNullOrBlank()) oid else raw.toString()
            }
            is Map<*, *> -> {
                val oid = raw["\$oid"]?.toString()
                if (!oid.isNullOrBlank()) oid else raw.toString()
            }
            else -> raw.toString()
        }
    }
}
