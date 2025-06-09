package com.ccs.ledgee.core.batch.jobs

import com.ccs.ledgee.core.repositories.LedgerRecordStatus

enum class LedgerError {
    NO_CREDIT_RECORD,
    NO_DEBIT_RECORD,
    EXCESS_DEBIT_RECORDS,
    EXCESS_CREDIT_RECORDS,
    NOT_ZERO_SUM,
    UNKNOWN
}

class LedgerException(
    val ledgerError: LedgerError = LedgerError.UNKNOWN,
    message: String = ledgerError.name,
    cause: Throwable? = null
) : IllegalStateException(message, cause) {

    fun recordStatus(): LedgerRecordStatus = when (ledgerError) {
        LedgerError.NO_DEBIT_RECORD,
        LedgerError.NO_CREDIT_RECORD -> LedgerRecordStatus.Unbalanced

        LedgerError.NOT_ZERO_SUM,
        LedgerError.EXCESS_DEBIT_RECORDS,
        LedgerError.EXCESS_CREDIT_RECORDS,
            -> LedgerRecordStatus.Error

        else -> LedgerRecordStatus.Error
    }
}
