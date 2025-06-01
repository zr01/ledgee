package com.ccs.ledgee.core.controllers.models

enum class DataEnvelopeTypes {
    LedgerEntry
}

data class ApiRequest<T>(
    val type: DataEnvelopeTypes,
    val data: T
)

data class ApiResponse<T>(
    val id: String,
    val type: DataEnvelopeTypes,
    val data: T
)