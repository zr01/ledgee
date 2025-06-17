package com.ccs.ledgee.core.controllers.models

data class LedgerDto(
    val amount: Long,
    val accountId: String? = null,
    val productCode: String? = null,
    val isPending: Boolean,
    val externalReferenceId: String,
    val description: String,
    val createdBy: String,
    val parentPublicId: String? = null,
    val entryReferenceId: String? = null
)

typealias LedgerApiRequest = ApiRequest<LedgerDto>
typealias LedgerApiResponse = ApiResponse<LedgerDto>

data class LedgerCorrectionDto(
    val amount: Long,
    val createdBy: String,
)

typealias LedgerCorrectionApiRequest = ApiRequest<LedgerCorrectionDto>
typealias LedgerCorrectionApiResponse = ApiResponse<LedgerCorrectionDto>