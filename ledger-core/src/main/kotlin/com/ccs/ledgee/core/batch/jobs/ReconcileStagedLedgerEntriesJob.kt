package com.ccs.ledgee.core.batch.jobs

import com.ccs.ledgee.core.configs.BatchJobProperties
import com.ccs.ledgee.core.events.EventPublisherService
import com.ccs.ledgee.core.events.consumers.validate
import com.ccs.ledgee.core.repositories.ChangeType
import com.ccs.ledgee.core.repositories.IsPending
import com.ccs.ledgee.core.repositories.LedgerEntity
import com.ccs.ledgee.core.repositories.LedgerRecordStatus
import com.ccs.ledgee.core.repositories.LedgerRepository
import com.ccs.ledgee.core.services.eventDetail
import com.ccs.ledgee.events.LedgerAuditEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import java.time.OffsetDateTime

private const val JOB_NAME = "reconcile-job"
private val log = KotlinLogging.logger { }

class ReconcileStagedLedgerEntriesJob(
    private val ledgerRepository: LedgerRepository,
    private val eventPublisherService: EventPublisherService,
    private val batchJobProperties: BatchJobProperties
) {

    @Scheduled(fixedDelay = 20000L)
    fun reconcileStagedLedgerEntries() {
        withLoggingContext(
            "stagedFetchInMinutes" to batchJobProperties.stagedFetchInMinutes.toString(),
            "isPending" to IsPending.Yes.name
        ) {
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

                            debitRecord.setAsBalanced()
                            creditRecord.setAsBalanced()

                            ledgerRepository.saveAll(entries)
                            entries.forEach { entry ->
                                eventPublisherService.raiseAuditEvent(
                                    entry.account.publicId,
                                    entry.toBalancedAuditEvent()
                                )
                            }
                            log.info { "Reconciled staged ledger entries" }
                        } catch (e: LedgerException) {
                            withLoggingContext("ledgerError" to e.ledgerError.name) {
                                val newRecordStatus = e.recordStatus()
                                val changeReason = e.message ?: "Unknown error"

                                entries.forEach { record -> record.recordStatus = newRecordStatus }
                                ledgerRepository.saveAll(entries)
                                entries.forEach { entry ->
                                    eventPublisherService.raiseAuditEvent(
                                        entry.account.publicId,
                                        entry.toErrorAuditEvent(changeReason)
                                    )
                                }
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

private fun LedgerEntity.setAsBalanced() = apply {
    recordStatus = LedgerRecordStatus.Balanced
    reconciledBy = JOB_NAME
    reconciledOn = OffsetDateTime.now()
}

private fun LedgerEntity.toBalancedAuditEvent() = LedgerAuditEvent.newBuilder()
    .setEventDetail(eventDetail(eventBy = reconciledBy ?: JOB_NAME))
    .setLedgerId(id)
    .setPreviousRecordStatus(LedgerRecordStatus.Staged.ordinal)
    .setNewRecordStatus(recordStatus.ordinal)
    .setChangeType(ChangeType.StatusChanged.ordinal)
    .setChangeReason("Balanced")
    .setChangedFields(
        mapOf(
            "recordStatus" to recordStatus.name
        )
    )
    .setUserAgent(reconciledBy ?: JOB_NAME)
    .build()

private fun LedgerEntity.toErrorAuditEvent(reason: String) = LedgerAuditEvent.newBuilder()
    .setEventDetail(eventDetail(eventBy = JOB_NAME))
    .setLedgerId(id)
    .setPreviousRecordStatus(LedgerRecordStatus.Staged.ordinal)
    .setNewRecordStatus(recordStatus.ordinal)
    .setChangeType(ChangeType.StatusChanged.ordinal)
    .setChangeReason(reason)
    .setChangedFields(
        mapOf(
            "recordStatus" to recordStatus.name
        )
    )
    .setUserAgent(JOB_NAME)
    .build()

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
