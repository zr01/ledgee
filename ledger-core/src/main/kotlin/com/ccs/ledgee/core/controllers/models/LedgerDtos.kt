package com.ccs.ledgee.core.controllers.models

data class LedgerDto(
    val amount: Long,
    val accountId: String,
    val productCode: String,
    val isPending: Boolean,
    val externalReferenceId: String,
    val description: String,
    val createdBy: String,
    val parentPublicId: String? = null,
    val entryReferenceId: String? = null
)

typealias LedgerApiRequest = ApiRequest<LedgerDto>
typealias LedgerApiResponse = ApiResponse<LedgerDto>
