package com.ccs.ledgee.core.batch.jobs

import com.ccs.ledgee.core.configs.BatchJobProperties
import com.ccs.ledgee.core.repositories.ChangeType
import com.ccs.ledgee.core.repositories.ChangedFieldsEntity
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerAuditEntity
import com.ccs.ledgee.core.repositories.LedgerAuditRepository
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerEntryType
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger { }

@Service
class ReconcileStagedLedgerEntriesJob(
    private val ledgerRepository: LedgerRepository,
    private val ledgerAuditRepository: LedgerAuditRepository,
    private val batchJobProperties: BatchJobProperties
) {

    @Scheduled(fixedDelay = 50L)
    fun reconcileStagedLedgerEntries() {
        withLoggingContext(
            "stagedFetchInMinutes" to batchJobProperties.stagedFetchInMinutes.toString(),
            "isPending" to IsPending.Yes.name
        ) {
            log.info { "Retrieving ledger entries to reconcile" }
            val listOfPendingEntries = ledgerRepository.retrieveAllExternalIdsInThePastInMinutesAndPendingIs(
                pastMinutes = batchJobProperties.stagedFetchInMinutes,
                isPending = IsPending.Yes.ordinal.toShort(),
                page = Pageable.ofSize(batchJobProperties.stagedFetchBatchSize)
            )
            withLoggingContext("entriesToProcess" to listOfPendingEntries.size.toString()) {
                listOfPendingEntries.forEach { extRefId ->
                    withLoggingContext("externalReferenceId" to extRefId) {
                        val entries = ledgerRepository.findAllByExternalReferenceId(extRefId)

                        try {
                            val (debitRecord, creditRecord) = entries.validate()

                            debitRecord.recordStatus = LedgerRecordStatus.Balanced
                            creditRecord.recordStatus = LedgerRecordStatus.Balanced

                            ledgerRepository.saveAll(entries)
                            ledgerAuditRepository.saveAll(
                                entries.map { record ->
                                    LedgerAuditEntity(
                                        ledgerId = record.id,
                                        previousRecordStatus = LedgerRecordStatus.Staged,
                                        newRecordStatus = LedgerRecordStatus.Balanced,
                                        changeType = ChangeType.StatusChanged,
                                        changedFields = ChangedFieldsEntity(fieldsChanged = listOf("record_status")),
                                        createdBy = "reconciliation-from-staged-ledger-entries"
                                    )
                                }
                            )

                            log.info { "Reconciled staged ledger entries" }
                        } catch (e: LedgerException) {
                            withLoggingContext("ledgerError" to e.ledgerError.name) {
                                val newRecordStatus = when (e.ledgerError) {
                                    LedgerError.NO_DEBIT_RECORD,
                                    LedgerError.NO_CREDIT_RECORD -> LedgerRecordStatus.Unbalanced

                                    LedgerError.NOT_ZERO_SUM,
                                    LedgerError.EXCESS_DEBIT_RECORDS,
                                    LedgerError.EXCESS_CREDIT_RECORDS,
                                        -> LedgerRecordStatus.Error

                                    else -> LedgerRecordStatus.Error
                                }
                                val changeReason = e.message ?: "Unknown error"

                                entries.forEach { record -> record.recordStatus = newRecordStatus }
                                ledgerRepository.saveAll(entries)
                                ledgerAuditRepository.saveAll(
                                    entries.map { record ->
                                        LedgerAuditEntity(
                                            ledgerId = record.id,
                                            previousRecordStatus = LedgerRecordStatus.Staged,
                                            newRecordStatus = newRecordStatus,
                                            changeReason = changeReason,
                                            changeType = ChangeType.StatusChanged,
                                            changedFields = ChangedFieldsEntity(fieldsChanged = listOf("record_status")),
                                        )
                                    }
                                )

                                if (newRecordStatus == LedgerRecordStatus.Error) {
                                    log.error(e) { "Reconciliation error" }
                                } else {
                                    log.warn { "Reconciliation unbalanced" }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

fun List<LedgerEntity>.validate(): Pair<LedgerEntity, LedgerEntity> {
    val debitRecords = filter { it.entryType == LedgerEntryType.DebitRecord }
    val creditRecords = filter { it.entryType == LedgerEntryType.CreditRecord }

    if (size == 1) {
        throw LedgerException(
            if (debitRecords.isEmpty()) LedgerError.NO_DEBIT_RECORD else LedgerError.NO_CREDIT_RECORD
        )
    } else if (debitRecords.size > 1) {
        throw LedgerException(LedgerError.EXCESS_DEBIT_RECORDS)
    } else if (creditRecords.size > 1) {
        throw LedgerException(LedgerError.EXCESS_CREDIT_RECORDS)
    }

    val debitRecord = debitRecords.first()
    val creditRecord = creditRecords.first()

    if (debitRecord.amount != creditRecord.amount) {
        throw LedgerException(LedgerError.NOT_ZERO_SUM)
    }

    return debitRecord to creditRecord
}

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
) : IllegalStateException(message, cause)
