package com.mtd.data.repository.gasless

import com.google.gson.JsonParser
import com.google.gson.internal.LinkedTreeMap
import com.mtd.data.dto.GaslessDisplayPolicyDto
import com.mtd.data.dto.GaslessDisplayPolicyItemDto
import com.mtd.data.dto.GaslessEligibilityParamsDto
import com.mtd.data.dto.GaslessEligibilityRequestDto
import com.mtd.core.utils.TronAddressConverter
import com.mtd.data.dto.EvmSponsorApproveParamsDto
import com.mtd.data.dto.EvmSponsorApproveRequestDto
import com.mtd.data.dto.EvmApproveQuoteRequestDto
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
import com.mtd.domain.model.EvmApproveQuoteRequest
import com.mtd.domain.model.EvmApproveQuoteResult
import com.mtd.domain.model.EvmApproveTxTemplate
import com.mtd.domain.model.GaslessCanonicalParams
import com.mtd.domain.model.core.NetworkType
import com.mtd.domain.model.GaslessEligibilityReason
import com.mtd.domain.model.GaslessFeeFundingSource
import com.mtd.domain.model.GaslessEligibilityResult
import com.mtd.domain.model.GaslessDisplayPolicy
import com.mtd.domain.model.GaslessDisplayPolicyBundle
import com.mtd.domain.model.GaslessPrepareData
import com.mtd.domain.model.GaslessQueuedTx
import com.mtd.domain.model.GaslessQuoteData
import com.mtd.domain.model.GaslessQuoteRequest
import com.mtd.domain.model.GaslessRelayPayload
import com.mtd.domain.model.GaslessServiceType
import com.mtd.domain.model.GaslessSmartFee
import com.mtd.domain.model.GaslessSupportedToken
import com.mtd.domain.model.GaslessTxStatus
import com.mtd.domain.model.ResultResponse
import com.mtd.domain.model.TronApproveQuoteRequest
import com.mtd.domain.model.TronApproveQuoteResult
import com.mtd.domain.model.TronApproveTxTemplate
import com.mtd.domain.model.TronSponsorApproveRequest
import com.mtd.domain.model.TronSponsorApproveResult
import com.mtd.domain.model.TronSponsorMode
import com.mtd.domain.model.error.ApiError
import com.mtd.domain.model.error.ApiException
import retrofit2.Response
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GaslessApiGateway @Inject constructor(
    private val gaslessApiService: GaslessApiService
) {

    // Phase 4: routing is the data-driven `relayPrefix` (networkId → capability →
    // relayPrefix; e.g. "evm","bsc","tron"). `networkType` carries the EVM/TVM execution
    // family (result labelling + TRON treasury conversion). The request-body `chain`
    // field equals the route prefix UPPERCASED (= backend `evmConfig.id`, e.g. "BSC");
    // see validateQuoteInput/Relay/Sponsor on the backend.
    suspend fun getSupportedTokens(
        networkType: NetworkType,
        relayPrefix: String
    ): ResultResponse<List<GaslessSupportedToken>> {
        return safeApiCall {
            val response = gaslessApiService.getSupportedTokens(relayPrefix)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "tokens $relayPrefix failed (${response.code()})")
            }

            body.mapNotNull { item ->
                val token = item.token?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                GaslessSupportedToken(
                    networkType = networkType,
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
        networkType: NetworkType,
        service: GaslessServiceType,
        userAddress: String,
        tokenAddress: String,
        relayPrefix: String
    ): ResultResponse<GaslessEligibilityResult> {
        return safeApiCall {
            val response = gaslessApiService.checkEligibility(
                chain = relayPrefix,
                request = GaslessEligibilityRequestDto(
                    chain = relayPrefix.uppercase(),
                    service = service.apiValue,
                    params = GaslessEligibilityParamsDto(
                        user = userAddress,
                        token = tokenAddress
                    )
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(
                    response,
                    "eligibility $relayPrefix/${service.apiValue} failed (${response.code()})"
                )
            }

            GaslessEligibilityResult(
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
                },
                networkType = networkType
            )
        }
    }

    suspend fun prepare(
        userAddress: String,
        startNonce: String? = null,
        relayPrefix: String
    ): ResultResponse<GaslessPrepareData> {
        return safeApiCall {
            val response = gaslessApiService.prepareGasless(
                chain = relayPrefix,
                userAddress = userAddress,
                startNonce = startNonce
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "prepare $relayPrefix failed (${response.code()})")
            }

            GaslessPrepareData(
                userAddress = body.user ?: userAddress,
                nonce = body.nonce
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

    suspend fun quote(
        networkType: NetworkType,
        request: GaslessQuoteRequest,
        relayPrefix: String
    ): ResultResponse<GaslessQuoteData> {
        return safeApiCall {
            val response = gaslessApiService.quoteGasless(
                chain = relayPrefix,
                request = GaslessQuoteRequestDto(
                    chain = relayPrefix.uppercase(),
                    prepareToken = request.prepareToken,
                    params = GaslessQuoteParamsDto(
                        user = request.user,
                        token = request.token,
                        target = request.target,
                        amount = request.amount
                    ),
                    feeFundingSource = request.feeFundingSource.apiValue,
                    clientFeeAmount = request.clientFeeAmount
                ),
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                // 400 INSUFFICIENT_GAS_CREDIT / 409 RACE_CONDITION_LOCK / REQUOTE_REQUIRED etc. are
                // surfaced as typed ApiError so the coordinator can branch (never auto-fallback).
                throw gaslessApiError(response, "quote $relayPrefix failed (${response.code()})")
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
                    amount = canonical.amount.orThrow("canonical.amount"),
                    feeAmount = canonical.feeAmount.orThrow("canonical.feeAmount"),
                    nonce = canonical.nonce.orThrow("canonical.nonce"),
                    deadline = canonical.deadline
                        ?: throw IllegalStateException("Missing canonical.deadline in quote response"),
                    treasury = normalizeTreasuryAddress(
                        networkType = networkType,
                        treasury = canonical.treasury
                            ?: throw IllegalStateException("Missing canonical.treasury in quote response")
                    )
                ),
                serverFeeAmount = body.serverQuote?.feeAmount,
                displayPolicy = body.displayPolicy?.toDomain(),
                smartFee = body.smartFee?.toDomain(),
                accepted = body.accepted,
                quoteId = body.quoteId,
                feeFundingSource = GaslessFeeFundingSource.fromApiValue(body.feeFundingSource),
                gasCreditApplied = body.gasCreditApplied,
                gasCredit = body.gasCredit,
                totalFee = body.totalFee,
                finalFee = body.finalFee
            )
        }
    }

    suspend fun relay(
        payload: GaslessRelayPayload,
        idempotencyKey: String,
        relayPrefix: String
    ): ResultResponse<GaslessQueuedTx> {
        return safeApiCall {
            require(idempotencyKey.isNotBlank()) { "x-idempotency-key must not be blank" }
            require(payload.quoteToken.isNotBlank()) { "quoteToken must not be blank" }

            val request = GaslessRelayRequestDto(
                chain = relayPrefix.uppercase(),
                quoteToken = payload.quoteToken,
                params = GaslessRelayParamsDto(
                    user = payload.params.user,
                    token = payload.params.token,
                    target = payload.params.target,
                    amount = payload.params.amount,
                    feeAmount = payload.params.feeAmount,
                    nonce = payload.params.nonce,
                    deadline = payload.params.deadline
                ),
                permitSignature = payload.permitSignature,
                megaSignature = payload.megaSignature,
                signature = payload.signature
            )



            val response = gaslessApiService.relayGasless(
                chain = relayPrefix,
                idempotencyKey = idempotencyKey,
                request = request
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "relay $relayPrefix failed (${response.code()})")
            }

            GaslessQueuedTx(
                id = body.id ?: throw IllegalStateException("Missing tx id in relay response"),
                stage = body.stage ?: body.status,
                idempotent = body.idempotent == true
            )
        }
    }

    suspend fun getTxStatus(
        txId: String,
        relayPrefix: String
    ): ResultResponse<GaslessTxStatus> {
        return safeApiCall {
            val response = gaslessApiService.getGaslessTxStatus(relayPrefix, txId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "tx status $relayPrefix failed (${response.code()})")
            }

            GaslessTxStatus(
                id = normalizeTxId(body.objectId)
                    ?: normalizeTxId(body.id)
                    ?: txId,
                chain = body.chain,
                status = body.publicStatus ?: body.status ?: "UNKNOWN",
                txHash = body.txHash,
                lastError = body.lastError,
                rawStatus = body.status,
                requestId = body.requestId,
                createdAt = normalizeFlexibleString(body.createdAt),
                updatedAt = normalizeFlexibleString(body.updatedAt)
            )
        }
    }

    suspend fun sponsorTronApprove(
        request: TronSponsorApproveRequest,
        relayPrefix: String
    ): ResultResponse<TronSponsorApproveResult> {
        return safeApiCall {
            val response = gaslessApiService.sponsorTronApprove(
                chain = relayPrefix,
                request = TronSponsorApproveRequestDto(
                    chain = relayPrefix.uppercase(),
                    params = TronSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    ),
                    mode = request.mode.apiValue
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "sponsor approve TRON failed (${response.code()})")
            }

            TronSponsorApproveResult(
                funded = body.funded ?: false,
                approveRequired = body.approveRequired,
                skipReason = body.skipReason,
                mode = TronSponsorMode.fromApiValue(body.mode),
                amount = body.amount?.toBigIntegerOrNull(),
                reason = body.reason,
                txHash = body.txHash,
                sponsorDisplayPolicy = body.displayPolicy?.sponsorApprove?.toDomain()
            )
        }
    }

    suspend fun quoteTronApprove(
        request: TronApproveQuoteRequest,
        relayPrefix: String
    ): ResultResponse<TronApproveQuoteResult> {
        return safeApiCall {
            val response = gaslessApiService.quoteTronApprove(
                chain = relayPrefix,
                request = TronApproveQuoteRequestDto(
                    chain = relayPrefix.uppercase(),
                    params = TronSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    )
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "quote approve TRON failed (${response.code()})")
            }

            val approveRequired = body.approveRequired ?: true
            TronApproveQuoteResult(
                approveRequired = approveRequired,
                approvalAmount = body.approvalAmount?.toBigIntegerOrNull(),
                approvalAmountMode = body.approvalAmountMode,
                approveTxTemplate = body.approveTxTemplate?.let {
                    TronApproveTxTemplate(
                        approvalAmount = it.approvalAmount?.toBigIntegerOrNull(),
                        approvalAmountMode = it.approvalAmountMode
                    )
                },
                requiredAllowance = body.requiredAllowance?.toBigIntegerOrNull(),
                estimatedEnergy = body.estimatedEnergy?.toBigIntegerOrNull(),
                estimatedBandwidthBytes = body.estimatedBandwidthBytes?.toBigIntegerOrNull(),
                energyFeeSun = body.energyFeeSun?.toBigIntegerOrNull(),
                bandwidthFeeSun = body.bandwidthFeeSun?.toBigIntegerOrNull(),
                requiredSun = body.requiredSun?.toBigIntegerOrNull()
                    ?: if (approveRequired) {
                        throw IllegalStateException("Missing requiredSun in TRON approve quote response")
                    } else {
                        BigInteger.ZERO
                    },
                requiredTrx = body.requiredTrx,
                requiredUsdApprox = body.requiredUsdApprox,
                source = body.source,
                sponsorDisplayPolicy = body.displayPolicy?.sponsorApprove?.toDomain()
            )
        }
    }

    suspend fun sponsorEvmApprove(
        request: EvmSponsorApproveRequest,
        relayPrefix: String
    ): ResultResponse<EvmSponsorApproveResult> {
        return safeApiCall {
            val response = gaslessApiService.sponsorEvmApprove(
                chain = relayPrefix,
                request = EvmSponsorApproveRequestDto(
                    chain = relayPrefix.uppercase(),
                    params = EvmSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    ),
                    mode = request.mode.apiValue
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "sponsor approve EVM failed (${response.code()})")
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

    suspend fun quoteEvmApprove(
        request: EvmApproveQuoteRequest,
        relayPrefix: String
    ): ResultResponse<EvmApproveQuoteResult> {
        return safeApiCall {
            val response = gaslessApiService.quoteEvmApprove(
                chain = relayPrefix,
                request = EvmApproveQuoteRequestDto(
                    chain = relayPrefix.uppercase(),
                    params = EvmSponsorApproveParamsDto(
                        user = request.userAddress,
                        token = request.tokenAddress
                    )
                )
            )
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                throw gaslessApiError(response, "quote approve EVM failed (${response.code()})")
            }

            val template = body.approveTxTemplate
            EvmApproveQuoteResult(
                approveRequired = body.approveRequired ?: true,
                approvalAmount = body.approvalAmount?.toBigIntegerOrNull()
                    ?: template?.approvalAmount?.toBigIntegerOrNull(),
                approvalAmountMode = body.approvalAmountMode ?: template?.approvalAmountMode,
                approveTxTemplate = template?.let {
                    EvmApproveTxTemplate(
                        to = it.to,
                        spender = it.spender,
                        data = it.data,
                        approvalAmount = it.approvalAmount?.toBigIntegerOrNull(),
                        approvalAmountMode = it.approvalAmountMode,
                        gasLimit = it.gasLimit?.toBigIntegerOrNull(),
                        gasPriceWei = it.gasPriceWei?.toBigIntegerOrNull(),
                        maxFeePerGasWei = it.maxFeePerGasWei?.toBigIntegerOrNull(),
                        maxPriorityFeePerGasWei = it.maxPriorityFeePerGasWei?.toBigIntegerOrNull(),
                        valueWei = it.valueWei?.toBigIntegerOrNull()
                    )
                },
                requiredAllowance = body.requiredAllowance?.toBigIntegerOrNull(),
                estimatedApproveGasLimit = body.estimatedApproveGasLimit?.toBigIntegerOrNull(),
                gasPriceWei = body.gasPriceWei?.toBigIntegerOrNull(),
                maxFeePerGasWei = body.maxFeePerGasWei?.toBigIntegerOrNull(),
                maxPriorityFeePerGasWei = body.maxPriorityFeePerGasWei?.toBigIntegerOrNull(),
                requiredApproveWei = body.requiredApproveWei?.toBigIntegerOrNull() ?: BigInteger.ZERO,
                requiredWithBufferWei = body.requiredWithBufferWei?.toBigIntegerOrNull(),
                source = body.source,
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

    private fun com.mtd.data.dto.GaslessSmartFeeDto.toDomain(): GaslessSmartFee {
        return GaslessSmartFee(
            decision = decision,
            reasonFa = reasonFa,
            feeAmount = feeAmount,
            feeUsd = feeUsd,
            directUserCostUsd = directUserCostUsd,
            moreExpensiveThanDirect = moreExpensiveThanDirect
        )
    }

    private fun BigInteger?.orThrow(fieldName: String): BigInteger {
        return this ?: throw IllegalStateException("Missing or invalid $fieldName in quote response")
    }

    /**
     * Maps a failed gasless [Response] to a typed [ApiException]. Parses the server's machine
     * `error.code` (BM-33 `{error:{code,message}}` or top-level `{code,message}`) into the Phase 1
     * [ApiError] taxonomy so callers branch on the code — never on the Persian `message`. HTTP status
     * + `Retry-After` are used as fallbacks (e.g. 409 → RaceConditionLock/RequoteRequired, 429 → RateLimited).
     */
    private fun gaslessApiError(response: Response<*>, fallbackMessage: String): ApiException {
        val httpStatus = response.code()
        val retryAfter = response.headers()["Retry-After"]?.trim()?.toLongOrNull()
        val (code, message) = parseGaslessError(response.errorBody()?.string())
        return ApiException(
            apiError = ApiError.from(code, httpStatus, retryAfter),
            httpStatus = httpStatus,
            reasonFa = message ?: fallbackMessage,
            retryAfterSec = retryAfter
        )
    }

    /** Pulls (`code`, `message`) from a gasless error body, tolerating both envelope shapes. */
    private fun parseGaslessError(raw: String?): Pair<String?, String?> {
        val body = raw?.takeIf { it.isNotBlank() } ?: return null to null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            val error = root.takeIf { it.has("error") && it.get("error").isJsonObject }
                ?.getAsJsonObject("error")
            val code = (error?.get("code") ?: root.get("code"))?.takeUnless { it.isJsonNull }?.asString
            val message = (error?.get("message") ?: root.get("message") ?: root.get("reasonFa"))
                ?.takeUnless { it.isJsonNull }?.asString
            code to message
        } catch (e: Exception) {
            null to null
        }
    }

    private fun normalizeTreasuryAddress(networkType: NetworkType, treasury: String): String {
        if (networkType != NetworkType.TVM) return treasury
        return runCatching { TronAddressConverter.evmToTron(treasury) }
            .getOrElse { treasury }
    }

    private fun normalizeTxId(raw: Any?): String? {
        return when (raw) {
            null -> null
            is String -> raw.takeUnless { it.isBlank() || it == "[object Object]" }
            is Number -> raw.toString()
            is LinkedTreeMap<*, *> -> {
                val oid = raw["\$oid"]?.toString()
                val bufferHex = raw["buffer"]?.let(::bufferObjectToHex)
                when {
                    !oid.isNullOrBlank() -> oid
                    !bufferHex.isNullOrBlank() -> bufferHex
                    raw.isEmpty() -> null
                    else -> raw.toString()
                }
            }
            is Map<*, *> -> {
                val oid = raw["\$oid"]?.toString()
                val bufferHex = raw["buffer"]?.let(::bufferObjectToHex)
                when {
                    !oid.isNullOrBlank() -> oid
                    !bufferHex.isNullOrBlank() -> bufferHex
                    raw.isEmpty() -> null
                    else -> raw.toString()
                }
            }
            else -> raw.toString()
        }
    }

    private fun normalizeFlexibleString(raw: Any?): String? {
        return when (raw) {
            null -> null
            is String -> raw.takeUnless { it.isBlank() || it == "[object Object]" }
            is Number -> raw.toString()
            is LinkedTreeMap<*, *> -> {
                raw["\$date"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw["date"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw["iso"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw.takeIf { it.isNotEmpty() }?.toString()
            }
            is Map<*, *> -> {
                raw["\$date"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw["date"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw["iso"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: raw.takeIf { it.isNotEmpty() }?.toString()
            }
            else -> raw.toString()
        }
    }

    private fun bufferObjectToHex(raw: Any?): String? {
        val buffer = raw as? Map<*, *> ?: return null
        if (buffer.isEmpty()) return null
        return buffer.entries
            .mapNotNull { entry ->
                val index = entry.key?.toString()?.toIntOrNull() ?: return@mapNotNull null
                val value = when (val item = entry.value) {
                    is Number -> item.toInt()
                    else -> item?.toString()?.toDoubleOrNull()?.toInt()
                } ?: return@mapNotNull null
                index to value.coerceIn(0, 255)
            }
            .sortedBy { it.first }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "") { (_, value) -> "%02x".format(value) }
    }
}
